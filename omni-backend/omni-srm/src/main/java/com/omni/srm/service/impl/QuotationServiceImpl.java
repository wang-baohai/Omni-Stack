package com.omni.srm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import com.omni.srm.client.ProcurementInternalClient;
import com.omni.srm.dto.DomainEventEnvelope;
import com.omni.srm.dto.quotation.ProcurementRfqInvitationDetail;
import com.omni.srm.dto.quotation.ProcurementRfqInvitationLine;
import com.omni.srm.dto.quotation.ProcurementRfqInvitationSummary;
import com.omni.srm.dto.quotation.QuotationInvitationDetailVO;
import com.omni.srm.dto.quotation.QuotationInvitationSummaryVO;
import com.omni.srm.dto.quotation.QuotationLineRequest;
import com.omni.srm.dto.quotation.QuotationLineVO;
import com.omni.srm.dto.quotation.QuotationSubmitRequest;
import com.omni.srm.dto.quotation.QuotationVO;
import com.omni.srm.entity.SrmQuotation;
import com.omni.srm.entity.SrmQuotationLine;
import com.omni.srm.entity.SrmQuotationRequest;
import com.omni.srm.entity.SrmSupplier;
import com.omni.srm.mapper.SrmQuotationLineMapper;
import com.omni.srm.mapper.SrmQuotationMapper;
import com.omni.srm.mapper.SrmQuotationRequestMapper;
import com.omni.srm.mapper.SrmSupplierMapper;
import com.omni.srm.security.SrmDataScopeContext;
import com.omni.srm.security.SrmTenantContext;
import com.omni.srm.service.QuotationService;
import com.omni.srm.service.SupplierPortalService;
import com.omni.srm.service.support.SrmAuditSupport;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import com.omni.common.web.TraceIdFilter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * SRM 供应商报价服务实现。
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotationServiceImpl implements QuotationService {

    private static final String QUOTATION_SUBMITTED = "SUBMITTED";
    private static final String REQUEST_RESERVED = "RESERVED";
    private static final String REQUEST_COMPLETED = "COMPLETED";
    private static final Set<String> OPEN_RFQ_STATUSES = Set.of("SENT");
    private static final Set<String> OPEN_INVITATION_STATUSES = Set.of("INVITED", "QUOTED");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999999.9999");

    private final SrmQuotationMapper quotationMapper;
    private final SrmQuotationLineMapper quotationLineMapper;
    private final SrmQuotationRequestMapper quotationRequestMapper;
    private final SrmSupplierMapper supplierMapper;
    private final SupplierPortalService supplierPortalService;
    private final ProcurementInternalClient procurementInternalClient;
    private final ReliableMessageRelay reliableMessageRelay;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<QuotationInvitationSummaryVO> listPortalInvitations() {
        return SrmDataScopeContext.runAsPortal(() -> {
            Long tenantId = SrmTenantContext.requireTenantId();
            Long supplierId = supplierPortalService.getCurrentSupplierId();
            requireApprovedSupplier(tenantId, supplierId);
            List<ProcurementRfqInvitationSummary> invitations = fetchInvitations(tenantId, supplierId);
            Map<Long, SrmQuotation> quotations = loadPortalQuotations(
                    tenantId, supplierId, invitations.stream()
                            .map(ProcurementRfqInvitationSummary::getRfqId).toList());
            return invitations.stream()
                    .map(invitation -> invitationSummary(invitation, quotations.get(invitation.getRfqId())))
                    .toList();
        });
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public QuotationInvitationDetailVO getPortalInvitation(Long rfqId) {
        return SrmDataScopeContext.runAsPortal(() -> {
            Long tenantId = SrmTenantContext.requireTenantId();
            Long supplierId = supplierPortalService.getCurrentSupplierId();
            requireApprovedSupplier(tenantId, supplierId);
            ProcurementRfqInvitationDetail invitation = fetchInvitation(
                    tenantId, supplierId, rfqId, false);
            SrmQuotation quotation = findPortalQuotation(tenantId, supplierId, rfqId);
            QuotationInvitationDetailVO view = new QuotationInvitationDetailVO();
            view.setRfqId(invitation.getRfqId());
            view.setRfqNo(invitation.getRfqNo());
            view.setTitle(invitation.getTitle());
            view.setStatus(invitation.getStatus());
            view.setInvitationStatus(invitation.getInvitationStatus());
            view.setQuotationDeadline(invitation.getQuotationDeadline());
            view.setCurrencyCode(invitation.getCurrencyCode());
            view.setInvitedTime(invitation.getInvitedTime());
            view.setLines(invitation.getLines() == null ? List.of() : invitation.getLines());
            view.setCurrentQuotation(quotation == null ? null : toQuotationView(quotation));
            return view;
        });
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public QuotationVO submit(QuotationSubmitRequest request) {
        return SrmDataScopeContext.runAsPortal(() -> doSubmit(request));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<QuotationVO> listValidByRfq(Long tenantId, Long rfqId) {
        requirePositive(tenantId, "tenantId");
        requirePositive(rfqId, "rfqId");
        return runAsInternalTenant(tenantId, () -> {
            List<SrmQuotation> quotations = quotationMapper.selectList(
                    new LambdaQueryWrapper<SrmQuotation>()
                            .eq(SrmQuotation::getTenantId, tenantId)
                            .eq(SrmQuotation::getRfqId, rfqId)
                            .eq(SrmQuotation::getStatus, QUOTATION_SUBMITTED)
                            .ge(SrmQuotation::getValidUntil, LocalDateTime.now())
                            .eq(SrmQuotation::getDeleted, 0)
                            .orderByAsc(SrmQuotation::getSupplierId));
            if (quotations.isEmpty()) {
                return List.of();
            }
            Set<Long> approvedSupplierIds = supplierMapper.selectList(
                            new LambdaQueryWrapper<SrmSupplier>()
                                    .in(SrmSupplier::getId, quotations.stream()
                                            .map(SrmQuotation::getSupplierId).distinct().toList())
                                    .eq(SrmSupplier::getStatus, "APPROVED")
                                    .eq(SrmSupplier::getDeleted, 0))
                    .stream().map(SrmSupplier::getId).collect(Collectors.toSet());
            return toQuotationViews(quotations.stream()
                    .filter(quotation -> approvedSupplierIds.contains(quotation.getSupplierId()))
                    .toList());
        });
    }

    private QuotationVO doSubmit(QuotationSubmitRequest request) {
        Long tenantId = SrmTenantContext.requireTenantId();
        Long supplierId = supplierPortalService.getCurrentSupplierId();
        String requestId = normalizeRequestId(request.getRequestId());
        String requestHash = hashRequest(request);

        SrmQuotationRequest historicalRequest = findRequest(tenantId, requestId);
        if (historicalRequest != null) {
            return replayCompletedRequest(historicalRequest, requestHash, tenantId, supplierId, request.getRfqId());
        }
        SrmSupplier supplier = requireApprovedSupplierForUpdate(tenantId, supplierId);

        SrmQuotationRequest reservation = reserveRequest(
                tenantId, supplierId, request.getRfqId(), requestId, requestHash);
        if (reservation.getId() == null) {
            SrmQuotationRequest concurrentRequest = findRequest(tenantId, requestId);
            if (concurrentRequest == null) {
                throw new BusinessException(409, "报价请求正在并发处理，请稍后按同一 requestId 重试");
            }
            return replayCompletedRequest(
                    concurrentRequest, requestHash, tenantId, supplierId, request.getRfqId());
        }

        ProcurementRfqInvitationDetail invitation = fetchInvitation(
                tenantId, supplierId, request.getRfqId(), true);
        validateValidityPeriod(request.getValidUntil(), invitation.getQuotationDeadline());
        PreparedQuotation prepared = prepareQuotation(request.getLines(), invitation.getLines());
        String currencyCode = normalizeCurrency(invitation.getCurrencyCode());
        QuotationWriteValues writeValues = new QuotationWriteValues(
                requestId, request.getValidUntil(), prepared.totalAmount(), currencyCode);

        SrmQuotation quotation = quotationMapper.selectForUpdate(tenantId, request.getRfqId(), supplierId);
        List<SrmQuotationLine> savedLines;
        if (quotation == null) {
            if (request.getVersion() == null || request.getVersion() != 0) {
                throw new BusinessException(409, "首次报价的 version 必须为 0");
            }
            quotation = insertQuotation(tenantId, supplier, invitation, writeValues);
            savedLines = insertQuotationLines(tenantId, quotation.getId(), prepared.lines());
        } else {
            requireUpdatableQuotation(quotation, request.getVersion());
            updateQuotation(quotation, supplier, invitation, writeValues);
            replaceQuotationLines(tenantId, quotation.getId());
            savedLines = insertQuotationLines(tenantId, quotation.getId(), prepared.lines());
        }

        completeReservation(reservation, quotation);
        sendQuotationSubmittedEvent(quotation, requestId);
        return toQuotationView(quotation, savedLines);
    }

    private List<ProcurementRfqInvitationSummary> fetchInvitations(Long tenantId, Long supplierId) {
        try {
            R<List<ProcurementRfqInvitationSummary>> response =
                    procurementInternalClient.listInvitations(tenantId, supplierId);
            if (response == null || response.getCode() != 200 || response.getData() == null) {
                throw new BusinessException(503, "采购询价服务暂时不可用");
            }
            for (ProcurementRfqInvitationSummary invitation : response.getData()) {
                if (invitation == null || !tenantId.equals(invitation.getTenantId())
                        || !supplierId.equals(invitation.getSupplierId()) || invitation.getRfqId() == null) {
                    throw new BusinessException(503, "采购询价服务返回了不一致的邀请数据");
                }
            }
            return response.getData();
        } catch (FeignException exception) {
            logFeignFailure("查询 RFQ 邀请列表", exception);
            throw new BusinessException(503, "采购询价服务暂时不可用");
        }
    }

    private ProcurementRfqInvitationDetail fetchInvitation(Long tenantId, Long supplierId,
                                                             Long rfqId, boolean forSubmission) {
        requirePositive(rfqId, "rfqId");
        try {
            R<ProcurementRfqInvitationDetail> response =
                    procurementInternalClient.getInvitation(tenantId, rfqId, supplierId);
            if (response == null || response.getCode() != 200 || response.getData() == null) {
                int code = response != null && response.getCode() == 404 ? 404 : 503;
                throw new BusinessException(code, code == 404 ? "RFQ 邀请不存在" : "采购询价服务暂时不可用");
            }
            ProcurementRfqInvitationDetail invitation = response.getData();
            validateInvitationIdentity(invitation, tenantId, supplierId, rfqId);
            if (forSubmission) {
                validateInvitationOpen(invitation);
            }
            return invitation;
        } catch (FeignException.NotFound exception) {
            throw new BusinessException(404, "RFQ 邀请不存在");
        } catch (FeignException exception) {
            logFeignFailure("查询 RFQ 邀请详情", exception);
            throw new BusinessException(503, "采购询价服务暂时不可用");
        }
    }

    private void logFeignFailure(String operation, FeignException exception) {
        log.warn("{}失败: traceId={}, status={}, url={}, cause={}", operation,
                MDC.get(TraceIdFilter.MDC_KEY), exception.status(),
                exception.request() == null ? "-" : exception.request().url(),
                exception.getClass().getName(), exception);
    }

    private void validateInvitationIdentity(ProcurementRfqInvitationDetail invitation,
                                              Long tenantId, Long supplierId, Long rfqId) {
        if (!tenantId.equals(invitation.getTenantId())
                || !supplierId.equals(invitation.getSupplierId())
                || !rfqId.equals(invitation.getRfqId())) {
            throw new BusinessException(503, "采购询价服务返回了不一致的邀请数据");
        }
    }

    private void validateInvitationOpen(ProcurementRfqInvitationDetail invitation) {
        if (!OPEN_RFQ_STATUSES.contains(normalizeStatus(invitation.getStatus()))) {
            throw new BusinessException(409, "RFQ 当前状态不允许报价");
        }
        if (!OPEN_INVITATION_STATUSES.contains(normalizeStatus(invitation.getInvitationStatus()))) {
            throw new BusinessException(409, "当前供应商邀请状态不允许报价");
        }
        if (invitation.getQuotationDeadline() == null) {
            throw new BusinessException(503, "采购询价服务未返回报价截止时间");
        }
        if (!invitation.getQuotationDeadline().isAfter(LocalDateTime.now())) {
            throw new BusinessException(409, "RFQ 报价已截止");
        }
    }

    private void validateValidityPeriod(LocalDateTime validUntil, LocalDateTime quotationDeadline) {
        if (validUntil == null || !validUntil.isAfter(LocalDateTime.now())) {
            throw new BusinessException(400, "报价有效期必须晚于当前时间");
        }
        if (quotationDeadline != null && validUntil.isBefore(quotationDeadline)) {
            throw new BusinessException(400, "报价有效期不能早于 RFQ 报价截止时间");
        }
    }

    private PreparedQuotation prepareQuotation(List<QuotationLineRequest> requestLines,
                                                List<ProcurementRfqInvitationLine> invitationLines) {
        if (requestLines == null || requestLines.isEmpty()) {
            throw new BusinessException(400, "报价明细不能为空");
        }
        if (requestLines.size() > 200) {
            throw new BusinessException(400, "报价明细不能超过 200 行");
        }
        if (invitationLines == null || invitationLines.isEmpty()) {
            throw new BusinessException(503, "采购询价服务未返回 RFQ 行快照");
        }
        if (invitationLines.size() > 200) {
            throw new BusinessException(503, "采购询价服务返回的 RFQ 行超过 200 行");
        }
        Map<Long, ProcurementRfqInvitationLine> expected = new LinkedHashMap<>();
        for (ProcurementRfqInvitationLine line : invitationLines) {
            validateInvitationLine(line);
            if (expected.putIfAbsent(line.getRfqLineId(), line) != null) {
                throw new BusinessException(503, "采购询价服务返回了重复的 RFQ 行");
            }
        }
        Map<Long, QuotationLineRequest> submitted = new HashMap<>();
        for (QuotationLineRequest line : requestLines) {
            validateRequestLine(line);
            if (submitted.putIfAbsent(line.getRfqLineId(), line) != null) {
                throw new BusinessException(400, "同一 RFQ 行不能重复报价");
            }
        }
        if (submitted.size() != expected.size() || !submitted.keySet().equals(expected.keySet())) {
            throw new BusinessException(400, "报价明细必须完整且仅覆盖本次 RFQ 的全部行");
        }

        List<PreparedLine> preparedLines = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO.setScale(4, RoundingMode.UNNECESSARY);
        for (ProcurementRfqInvitationLine source : invitationLines) {
            QuotationLineRequest input = submitted.get(source.getRfqLineId());
            BigDecimal unitPrice = normalizeUnitPrice(input.getUnitPrice());
            BigDecimal quantity = normalizeQuantity(source.getQuantity());
            BigDecimal lineAmount = unitPrice.multiply(quantity).setScale(4, RoundingMode.HALF_UP);
            requireAmountFits(lineAmount, "报价行金额超出允许范围");
            totalAmount = totalAmount.add(lineAmount);
            requireAmountFits(totalAmount, "报价总金额超出允许范围");
            preparedLines.add(new PreparedLine(source, unitPrice, quantity, lineAmount,
                    input.getDeliveryDays(), normalizeRemark(input.getRemark())));
        }
        return new PreparedQuotation(List.copyOf(preparedLines), totalAmount);
    }

    private void validateInvitationLine(ProcurementRfqInvitationLine line) {
        if (line == null || line.getRfqLineId() == null || line.getRfqLineId() <= 0
                || isBlank(line.getMaterialCode()) || isBlank(line.getMaterialName())
                || isBlank(line.getUnit()) || line.getQuantity() == null
                || line.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(503, "采购询价服务返回了不完整的 RFQ 行快照");
        }
        if (line.getMaterialCode().trim().length() > 64
                || line.getMaterialName().trim().length() > 200
                || line.getUnit().trim().length() > 32) {
            throw new BusinessException(503, "采购询价服务返回的 RFQ 行快照长度无效");
        }
    }

    private void validateRequestLine(QuotationLineRequest line) {
        if (line == null || line.getRfqLineId() == null || line.getRfqLineId() <= 0
                || line.getUnitPrice() == null || line.getDeliveryDays() == null
                || line.getDeliveryDays() < 0 || line.getDeliveryDays() > 3650) {
            throw new BusinessException(400, "报价行参数无效");
        }
        normalizeUnitPrice(line.getUnitPrice());
        if (line.getRemark() != null && line.getRemark().length() > 500) {
            throw new BusinessException(400, "报价行备注不能超过 500 个字符");
        }
    }

    private BigDecimal normalizeUnitPrice(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new BusinessException(400, "报价单价必须大于 0");
        }
        try {
            BigDecimal normalized = value.setScale(6, RoundingMode.UNNECESSARY);
            if (normalized.precision() - normalized.scale() > 13) {
                throw new BusinessException(400, "报价单价超出允许范围");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new BusinessException(400, "报价单价最多保留 6 位小数");
        }
    }

    private BigDecimal normalizeQuantity(BigDecimal value) {
        try {
            BigDecimal normalized = value.setScale(6, RoundingMode.UNNECESSARY);
            if (normalized.signum() <= 0 || normalized.precision() - normalized.scale() > 13) {
                throw new BusinessException(503, "采购询价数量超出允许范围");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new BusinessException(503, "采购询价数量精度无效");
        }
    }

    private void requireAmountFits(BigDecimal amount, String message) {
        if (amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessException(400, message);
        }
    }

    private SrmQuotation insertQuotation(Long tenantId, SrmSupplier supplier,
                                          ProcurementRfqInvitationDetail invitation,
                                          QuotationWriteValues values) {
        SrmQuotation quotation = new SrmQuotation();
        quotation.setTenantId(tenantId);
        quotation.setSupplierId(supplier.getId());
        quotation.setRfqId(invitation.getRfqId());
        quotation.setRfqNo(requireSnapshot(invitation.getRfqNo(), "RFQ 编号", 64));
        quotation.setSupplierNameSnapshot(requireSnapshot(supplier.getName(), "供应商名称", 200));
        quotation.setRequestId(values.requestId());
        quotation.setQuotationTime(LocalDateTime.now());
        quotation.setValidUntil(values.validUntil());
        quotation.setTotalAmount(values.totalAmount());
        quotation.setCurrencyCode(values.currencyCode());
        quotation.setStatus(QUOTATION_SUBMITTED);
        quotation.setVersion(1);
        quotation.setDeleted(0);
        SrmAuditSupport.created(quotation);
        try {
            quotationMapper.insert(quotation);
        } catch (DuplicateKeyException exception) {
            BusinessException conflict = new BusinessException(409, "该 RFQ 已存在报价，请刷新后按当前版本更新");
            conflict.initCause(exception);
            throw conflict;
        }
        return quotation;
    }

    private void requireUpdatableQuotation(SrmQuotation quotation, Integer requestVersion) {
        if (!QUOTATION_SUBMITTED.equals(quotation.getStatus())) {
            throw new BusinessException(409, "当前报价状态不允许更新");
        }
        if (requestVersion == null || !requestVersion.equals(quotation.getVersion())) {
            throw new BusinessException(409, "报价已被其他请求修改，请刷新后重试");
        }
    }

    private void updateQuotation(SrmQuotation quotation, SrmSupplier supplier,
                                  ProcurementRfqInvitationDetail invitation,
                                  QuotationWriteValues values) {
        LocalDateTime now = LocalDateTime.now();
        int rows = quotationMapper.update(null, new LambdaUpdateWrapper<SrmQuotation>()
                .eq(SrmQuotation::getTenantId, quotation.getTenantId())
                .eq(SrmQuotation::getId, quotation.getId())
                .eq(SrmQuotation::getSupplierId, quotation.getSupplierId())
                .eq(SrmQuotation::getRfqId, quotation.getRfqId())
                .eq(SrmQuotation::getVersion, quotation.getVersion())
                .eq(SrmQuotation::getStatus, QUOTATION_SUBMITTED)
                .eq(SrmQuotation::getDeleted, 0)
                .set(SrmQuotation::getRfqNo, requireSnapshot(invitation.getRfqNo(), "RFQ 编号", 64))
                .set(SrmQuotation::getSupplierNameSnapshot,
                        requireSnapshot(supplier.getName(), "供应商名称", 200))
                .set(SrmQuotation::getRequestId, values.requestId())
                .set(SrmQuotation::getQuotationTime, now)
                .set(SrmQuotation::getValidUntil, values.validUntil())
                .set(SrmQuotation::getTotalAmount, values.totalAmount())
                .set(SrmQuotation::getCurrencyCode, values.currencyCode())
                .set(SrmQuotation::getUpdateTime, now)
                .set(SrmQuotation::getUpdateBy, currentOperator())
                .setSql("version = version + 1"));
        if (rows != 1) {
            throw new BusinessException(409, "报价已被其他请求修改，请刷新后重试");
        }
        quotation.setRfqNo(invitation.getRfqNo().trim());
        quotation.setSupplierNameSnapshot(supplier.getName().trim());
        quotation.setRequestId(values.requestId());
        quotation.setQuotationTime(now);
        quotation.setValidUntil(values.validUntil());
        quotation.setTotalAmount(values.totalAmount());
        quotation.setCurrencyCode(values.currencyCode());
        quotation.setVersion(quotation.getVersion() + 1);
        quotation.setUpdateTime(now);
        quotation.setUpdateBy(currentOperator());
    }

    private void replaceQuotationLines(Long tenantId, Long quotationId) {
        quotationLineMapper.delete(new LambdaQueryWrapper<SrmQuotationLine>()
                .eq(SrmQuotationLine::getTenantId, tenantId)
                .eq(SrmQuotationLine::getQuotationId, quotationId)
                .eq(SrmQuotationLine::getDeleted, 0));
    }

    private List<SrmQuotationLine> insertQuotationLines(Long tenantId, Long quotationId,
                                                         List<PreparedLine> lines) {
        List<SrmQuotationLine> saved = new ArrayList<>();
        for (PreparedLine prepared : lines) {
            SrmQuotationLine line = new SrmQuotationLine();
            line.setTenantId(tenantId);
            line.setQuotationId(quotationId);
            line.setRfqLineId(prepared.source().getRfqLineId());
            line.setMaterialCode(prepared.source().getMaterialCode().trim());
            line.setMaterialName(prepared.source().getMaterialName().trim());
            line.setUnit(prepared.source().getUnit().trim());
            line.setUnitPrice(prepared.unitPrice());
            line.setQuantity(prepared.quantity());
            line.setLineAmount(prepared.lineAmount());
            line.setDeliveryDays(prepared.deliveryDays());
            line.setRemark(prepared.remark());
            line.setVersion(0);
            line.setDeleted(0);
            SrmAuditSupport.created(line);
            quotationLineMapper.insert(line);
            saved.add(line);
        }
        return List.copyOf(saved);
    }

    private SrmQuotationRequest reserveRequest(Long tenantId, Long supplierId, Long rfqId,
                                                String requestId, String requestHash) {
        SrmQuotationRequest reservation = new SrmQuotationRequest();
        reservation.setTenantId(tenantId);
        reservation.setRequestId(requestId);
        reservation.setRfqId(rfqId);
        reservation.setSupplierId(supplierId);
        reservation.setRequestHash(requestHash);
        reservation.setStatus(REQUEST_RESERVED);
        SrmAuditSupport.created(reservation);
        try {
            quotationRequestMapper.insert(reservation);
            return reservation;
        } catch (DuplicateKeyException exception) {
            reservation.setId(null);
            return reservation;
        }
    }

    private void completeReservation(SrmQuotationRequest reservation, SrmQuotation quotation) {
        int rows = quotationRequestMapper.update(null,
                new LambdaUpdateWrapper<SrmQuotationRequest>()
                        .eq(SrmQuotationRequest::getTenantId, reservation.getTenantId())
                        .eq(SrmQuotationRequest::getId, reservation.getId())
                        .eq(SrmQuotationRequest::getStatus, REQUEST_RESERVED)
                        .set(SrmQuotationRequest::getQuotationId, quotation.getId())
                        .set(SrmQuotationRequest::getTargetVersion, quotation.getVersion())
                        .set(SrmQuotationRequest::getStatus, REQUEST_COMPLETED)
                        .set(SrmQuotationRequest::getUpdateTime, LocalDateTime.now())
                        .set(SrmQuotationRequest::getUpdateBy, currentOperator()));
        if (rows != 1) {
            throw new BusinessException(409, "报价幂等请求状态已变化，请按同一 requestId 重试");
        }
    }

    private SrmQuotationRequest findRequest(Long tenantId, String requestId) {
        return quotationRequestMapper.selectOne(new LambdaQueryWrapper<SrmQuotationRequest>()
                .eq(SrmQuotationRequest::getTenantId, tenantId)
                .eq(SrmQuotationRequest::getRequestId, requestId)
                .last("LIMIT 1"));
    }

    private QuotationVO replayCompletedRequest(SrmQuotationRequest historicalRequest,
                                                String requestHash, Long tenantId,
                                                Long supplierId, Long rfqId) {
        if (!requestHash.equals(historicalRequest.getRequestHash())
                || !supplierId.equals(historicalRequest.getSupplierId())
                || !rfqId.equals(historicalRequest.getRfqId())) {
            throw new BusinessException(409, "requestId 已被其他报价请求使用");
        }
        if (REQUEST_RESERVED.equals(historicalRequest.getStatus())) {
            throw new BusinessException(409, "报价请求正在处理，请稍后按同一 requestId 重试");
        }
        if (!REQUEST_COMPLETED.equals(historicalRequest.getStatus())
                || historicalRequest.getQuotationId() == null
                || historicalRequest.getTargetVersion() == null) {
            throw new BusinessException(409, "报价幂等请求状态无效");
        }
        SrmQuotation quotation = quotationMapper.selectOne(new LambdaQueryWrapper<SrmQuotation>()
                .eq(SrmQuotation::getTenantId, tenantId)
                .eq(SrmQuotation::getId, historicalRequest.getQuotationId())
                .eq(SrmQuotation::getSupplierId, supplierId)
                .eq(SrmQuotation::getRfqId, rfqId)
                .eq(SrmQuotation::getDeleted, 0));
        if (quotation == null || quotation.getVersion() < historicalRequest.getTargetVersion()) {
            throw new BusinessException(409, "报价幂等记录与当前报价不一致");
        }
        return toQuotationView(quotation);
    }

    String hashRequest(QuotationSubmitRequest request) {
        if (request == null || request.getRfqId() == null || request.getVersion() == null
                || request.getValidUntil() == null || request.getLines() == null
                || request.getLines().isEmpty()) {
            throw new BusinessException(400, "报价请求参数不完整");
        }
        if (request.getRfqId() <= 0 || request.getVersion() < 0 || request.getLines().size() > 200) {
            throw new BusinessException(400, "报价请求参数无效");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, request.getRfqId());
            updateDigest(digest, request.getVersion());
            updateDigest(digest, request.getValidUntil());
            List<QuotationLineRequest> lines = request.getLines().stream()
                    .sorted(Comparator.comparing(QuotationLineRequest::getRfqLineId,
                            Comparator.nullsFirst(Long::compareTo)))
                    .toList();
            for (QuotationLineRequest line : lines) {
                validateRequestLine(line);
                updateDigest(digest, line.getRfqLineId());
                updateDigest(digest, normalizeUnitPrice(line.getUnitPrice()).toPlainString());
                updateDigest(digest, line.getDeliveryDays());
                updateDigest(digest, normalizeRemark(line.getRemark()));
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 不支持 SHA-256", exception);
        }
    }

    private void updateDigest(MessageDigest digest, Object value) {
        byte[] bytes = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private SrmSupplier requireApprovedSupplier(Long tenantId, Long supplierId) {
        SrmSupplier supplier = supplierMapper.selectOne(new LambdaQueryWrapper<SrmSupplier>()
                .eq(SrmSupplier::getTenantId, tenantId)
                .eq(SrmSupplier::getId, supplierId)
                .eq(SrmSupplier::getDeleted, 0));
        if (supplier == null) {
            throw new BusinessException(404, "供应商不存在");
        }
        if (!"APPROVED".equals(supplier.getStatus())) {
            throw new BusinessException(409, "仅已准入供应商可以报价");
        }
        return supplier;
    }

    private SrmSupplier requireApprovedSupplierForUpdate(Long tenantId, Long supplierId) {
        SrmSupplier supplier = supplierMapper.selectVisibleForUpdate(supplierId);
        if (supplier == null || !tenantId.equals(supplier.getTenantId())) {
            throw new BusinessException(404, "供应商不存在");
        }
        if (!"APPROVED".equals(supplier.getStatus())) {
            throw new BusinessException(409, "仅已准入供应商可以报价");
        }
        return supplier;
    }

    private Map<Long, SrmQuotation> loadPortalQuotations(Long tenantId, Long supplierId,
                                                          List<Long> rfqIds) {
        List<Long> normalizedIds = rfqIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (normalizedIds.isEmpty()) {
            return Map.of();
        }
        return quotationMapper.selectList(new LambdaQueryWrapper<SrmQuotation>()
                        .eq(SrmQuotation::getTenantId, tenantId)
                        .eq(SrmQuotation::getSupplierId, supplierId)
                        .in(SrmQuotation::getRfqId, normalizedIds)
                        .eq(SrmQuotation::getDeleted, 0))
                .stream().collect(Collectors.toMap(SrmQuotation::getRfqId, item -> item,
                        (first, ignored) -> first));
    }

    private SrmQuotation findPortalQuotation(Long tenantId, Long supplierId, Long rfqId) {
        return quotationMapper.selectOne(new LambdaQueryWrapper<SrmQuotation>()
                .eq(SrmQuotation::getTenantId, tenantId)
                .eq(SrmQuotation::getSupplierId, supplierId)
                .eq(SrmQuotation::getRfqId, rfqId)
                .eq(SrmQuotation::getDeleted, 0)
                .last("LIMIT 1"));
    }

    private QuotationInvitationSummaryVO invitationSummary(ProcurementRfqInvitationSummary source,
                                                            SrmQuotation quotation) {
        QuotationInvitationSummaryVO view = new QuotationInvitationSummaryVO();
        view.setRfqId(source.getRfqId());
        view.setRfqNo(source.getRfqNo());
        view.setTitle(source.getTitle());
        view.setStatus(source.getStatus());
        view.setInvitationStatus(source.getInvitationStatus());
        view.setQuotationDeadline(source.getQuotationDeadline());
        view.setCurrencyCode(source.getCurrencyCode());
        view.setInvitedTime(source.getInvitedTime());
        if (quotation != null) {
            view.setQuotationId(quotation.getId());
            view.setQuotationVersion(quotation.getVersion());
            view.setQuotationStatus(quotation.getStatus());
            view.setTotalAmount(quotation.getTotalAmount());
            view.setValidUntil(quotation.getValidUntil());
        }
        return view;
    }

    private List<QuotationVO> toQuotationViews(List<SrmQuotation> quotations) {
        if (quotations.isEmpty()) {
            return List.of();
        }
        List<Long> quotationIds = quotations.stream().map(SrmQuotation::getId).toList();
        Map<Long, List<SrmQuotationLine>> linesByQuotation = quotationLineMapper.selectList(
                        new LambdaQueryWrapper<SrmQuotationLine>()
                                .in(SrmQuotationLine::getQuotationId, quotationIds)
                                .eq(SrmQuotationLine::getDeleted, 0)
                                .orderByAsc(SrmQuotationLine::getId))
                .stream().collect(Collectors.groupingBy(SrmQuotationLine::getQuotationId));
        return quotations.stream()
                .map(quotation -> toQuotationView(
                        quotation, linesByQuotation.getOrDefault(quotation.getId(), List.of())))
                .toList();
    }

    private QuotationVO toQuotationView(SrmQuotation quotation) {
        List<SrmQuotationLine> lines = quotationLineMapper.selectList(
                new LambdaQueryWrapper<SrmQuotationLine>()
                        .eq(SrmQuotationLine::getQuotationId, quotation.getId())
                        .eq(SrmQuotationLine::getDeleted, 0)
                        .orderByAsc(SrmQuotationLine::getId));
        return toQuotationView(quotation, lines);
    }

    private QuotationVO toQuotationView(SrmQuotation quotation, List<SrmQuotationLine> lines) {
        QuotationVO view = new QuotationVO();
        view.setId(quotation.getId());
        view.setRfqId(quotation.getRfqId());
        view.setRfqNo(quotation.getRfqNo());
        view.setSupplierId(quotation.getSupplierId());
        view.setSupplierNameSnapshot(quotation.getSupplierNameSnapshot());
        view.setQuotationTime(quotation.getQuotationTime());
        view.setValidUntil(quotation.getValidUntil());
        view.setTotalAmount(quotation.getTotalAmount());
        view.setCurrencyCode(quotation.getCurrencyCode());
        view.setStatus(quotation.getStatus());
        view.setVersion(quotation.getVersion());
        view.setLines(lines.stream().map(this::toQuotationLineView).toList());
        return view;
    }

    private QuotationLineVO toQuotationLineView(SrmQuotationLine line) {
        QuotationLineVO view = new QuotationLineVO();
        view.setId(line.getId());
        view.setRfqLineId(line.getRfqLineId());
        view.setMaterialCode(line.getMaterialCode());
        view.setMaterialName(line.getMaterialName());
        view.setUnit(line.getUnit());
        view.setUnitPrice(line.getUnitPrice());
        view.setQuantity(line.getQuantity());
        view.setLineAmount(line.getLineAmount());
        view.setDeliveryDays(line.getDeliveryDays());
        view.setRemark(line.getRemark());
        return view;
    }

    private void sendQuotationSubmittedEvent(SrmQuotation quotation, String requestId) {
        String eventId = UUID.randomUUID().toString();
        DomainEventEnvelope envelope = DomainEventEnvelope.builder()
                .eventId(eventId)
                .eventType("srm.quotation.submitted.v1")
                .occurredAt(LocalDateTime.now())
                .tenantId(quotation.getTenantId())
                .producer("omni-srm")
                .aggregateType("QUOTATION")
                .aggregateId(quotation.getId())
                .aggregateVersion(quotation.getVersion())
                .actorUserId(SrmTenantContext.require().userId())
                .correlationId(requestId)
                .payload(Map.of(
                        "requestId", requestId,
                        "quotationId", quotation.getId(),
                        "quotationVersion", quotation.getVersion(),
                        "rfqId", quotation.getRfqId(),
                        "rfqNo", quotation.getRfqNo(),
                        "supplierId", quotation.getSupplierId(),
                        "status", quotation.getStatus(),
                        "totalAmount", quotation.getTotalAmount().toPlainString(),
                        "currencyCode", quotation.getCurrencyCode(),
                        "validUntil", quotation.getValidUntil()))
                .build();
        reliableMessageRelay.send("srm-domain-out-0", envelope,
                quotation.getTenantId(), eventId);
    }

    private <T> T runAsInternalTenant(Long tenantId, Supplier<T> action) {
        try {
            SrmTenantContext.set(new SrmTenantContext.RequestIdentity(0L, tenantId, "internal-service"));
            SrmDataScopeContext.set(new SrmDataScopeContext.ScopeInfo(
                    0L, tenantId, "INTERNAL", null, "TENANT", Collections.emptySet()));
            return action.get();
        } finally {
            SrmDataScopeContext.clear();
            SrmTenantContext.clear();
        }
    }

    private String normalizeRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new BusinessException(400, "requestId 不能为空");
        }
        String normalized = requestId.trim();
        if (normalized.length() > 64) {
            throw new BusinessException(400, "requestId 不能超过 64 个字符");
        }
        return normalized;
    }

    private String normalizeCurrency(String currencyCode) {
        if (currencyCode == null) {
            throw new BusinessException(503, "采购询价服务未返回币种");
        }
        String normalized = currencyCode.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new BusinessException(503, "采购询价服务返回的币种无效");
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeRemark(String remark) {
        if (remark == null || remark.isBlank()) {
            return null;
        }
        return remark.trim();
    }

    private String requireSnapshot(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(503, label + "快照不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BusinessException(503, label + "快照长度无效");
        }
        return normalized;
    }

    private String currentOperator() {
        String username = SrmTenantContext.require().username();
        return username == null || username.isBlank()
                ? String.valueOf(SrmTenantContext.require().userId()) : username;
    }

    private void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new BusinessException(400, field + " 必须为正整数");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 服务端校验并计算后的报价。 */
    private record PreparedQuotation(List<PreparedLine> lines, BigDecimal totalAmount) {
    }

    /**
     * 报价主表写入值。
     *
     * @param requestId 幂等请求 ID
     * @param validUntil 报价有效期
     * @param totalAmount 报价总金额
     * @param currencyCode 币种代码
     */
    private record QuotationWriteValues(String requestId,
                                        LocalDateTime validUntil,
                                        BigDecimal totalAmount,
                                        String currencyCode) {
    }

    /** 服务端校验并计算后的报价行。 */
    private record PreparedLine(ProcurementRfqInvitationLine source, BigDecimal unitPrice,
                                BigDecimal quantity, BigDecimal lineAmount,
                                Integer deliveryDays, String remark) {
    }
}
