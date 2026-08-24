package com.omni.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import com.omni.procurement.client.SrmInternalClient;
import com.omni.procurement.domain.RequisitionStateMachine;
import com.omni.procurement.domain.RfqStateMachine;
import com.omni.procurement.dto.PurchaseOrderContracts;
import com.omni.procurement.dto.PurchaseOrderRequests;
import com.omni.procurement.dto.PurchaseOrderViews;
import com.omni.procurement.dto.RfqContracts;
import com.omni.procurement.dto.RfqRequests;
import com.omni.procurement.dto.RfqViews;
import com.omni.procurement.dto.SrmSupplierContracts;
import com.omni.procurement.entity.ProcRequisition;
import com.omni.procurement.entity.ProcRequisitionLine;
import com.omni.procurement.entity.ProcRfq;
import com.omni.procurement.entity.ProcRfqLine;
import com.omni.procurement.entity.ProcRfqSupplier;
import com.omni.procurement.mapper.ProcRequisitionLineMapper;
import com.omni.procurement.mapper.ProcRequisitionMapper;
import com.omni.procurement.mapper.ProcRfqLineMapper;
import com.omni.procurement.mapper.ProcRfqMapper;
import com.omni.procurement.mapper.ProcRfqSupplierMapper;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.procurement.service.PurchaseOrderService;
import com.omni.procurement.service.RfqService;
import com.omni.procurement.service.support.ProcAuditSupport;
import com.omni.procurement.service.support.ProcRecordAccessGuard;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 询价单服务实现。
 *
 * @author Omni-Stack Team
 */
@Service
@RequiredArgsConstructor
public class RfqServiceImpl implements RfqService {

    private static final String DOMAIN_BINDING = "procurement-domain-out-0";
    private static final String RFQ_SENT_EVENT = "procurement.rfq.sent.v1";
    private static final String RFQ_AWARDED_EVENT = "procurement.rfq.awarded.v1";

    private final ProcRfqMapper rfqMapper;
    private final ProcRfqLineMapper lineMapper;
    private final ProcRfqSupplierMapper supplierMapper;
    private final ProcRequisitionMapper requisitionMapper;
    private final ProcRequisitionLineMapper requisitionLineMapper;
    private final SrmInternalClient srmInternalClient;
    private final PurchaseOrderService purchaseOrderService;
    private final ReliableMessageRelay reliableMessageRelay;
    private final ProcRecordAccessGuard accessGuard;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<RfqViews.SupplierOption> supplierOptions(
            RfqRequests.SupplierOptionQuery query) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        String categoryCode = trimToNull(query.getCategoryCode());
        if (categoryCode != null) {
            categoryCode = categoryCode.toUpperCase(Locale.ROOT);
        }
        R<List<SrmSupplierContracts.Summary>> response;
        try {
            response = srmInternalClient.search(
                    tenantId, tenantId, "APPROVED", categoryCode, 100);
        } catch (FeignException exception) {
            throw new BusinessException(503, "SRM 供应商搜索服务暂时不可用");
        }
        if (response == null || response.getCode() != 200 || response.getData() == null) {
            throw new BusinessException(503, "SRM 供应商搜索服务暂时不可用");
        }
        String keyword = trimToNull(query.getKeyword());
        String normalizedKeyword = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        int requestedLimit = Math.max(1, Math.min(query.getLimit(), 100));
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        List<RfqViews.SupplierOption> options = new ArrayList<>();
        for (SrmSupplierContracts.Summary summary : response.getData()) {
            if (summary == null || summary.getId() == null || summary.getId() <= 0
                    || !"APPROVED".equals(summary.getStatus())
                    || !seen.add(summary.getId())) {
                continue;
            }
            String name = trimToNull(summary.getName());
            String supplierNo = trimToNull(summary.getSupplierNo());
            String supplierCategory = trimToNull(summary.getCategoryCode());
            if (name == null || supplierNo == null) {
                continue;
            }
            if (categoryCode != null
                    && (supplierCategory == null
                    || !categoryCode.equalsIgnoreCase(supplierCategory))) {
                continue;
            }
            if (normalizedKeyword != null
                    && !name.toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                    && !supplierNo.toLowerCase(Locale.ROOT).contains(normalizedKeyword)) {
                continue;
            }
            RfqViews.SupplierOption option = new RfqViews.SupplierOption();
            option.setId(summary.getId());
            option.setSupplierNo(supplierNo);
            option.setName(name);
            option.setLevelCode(summary.getLevelCode());
            option.setCategoryCode(supplierCategory);
            options.add(option);
            if (options.size() >= requestedLimit) {
                break;
            }
        }
        return List.copyOf(options);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PageResult<RfqViews.Summary> page(RfqRequests.Query query) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        LambdaQueryWrapper<ProcRfq> wrapper = new LambdaQueryWrapper<ProcRfq>()
                .eq(ProcRfq::getTenantId, tenantId);
        String keyword = trimToNull(query.getKeyword());
        if (keyword != null) {
            wrapper.and(nested -> nested.like(ProcRfq::getRfqNo, keyword)
                    .or().like(ProcRfq::getTitle, keyword));
        }
        if (query.getRequisitionId() != null) {
            wrapper.eq(ProcRfq::getRequisitionId, query.getRequisitionId());
        }
        if (trimToNull(query.getStatus()) != null) {
            wrapper.eq(ProcRfq::getStatus, query.getStatus().trim().toUpperCase());
        }
        if (query.getDeadlineFrom() != null) {
            wrapper.ge(ProcRfq::getQuotationDeadline, query.getDeadlineFrom());
        }
        if (query.getDeadlineTo() != null) {
            wrapper.le(ProcRfq::getQuotationDeadline, query.getDeadlineTo());
        }
        if (query.getDeadlineFrom() != null && query.getDeadlineTo() != null
                && query.getDeadlineFrom().isAfter(query.getDeadlineTo())) {
            throw new BusinessException(400, "报价截止时间范围无效");
        }
        wrapper.orderByDesc(ProcRfq::getCreateTime).orderByDesc(ProcRfq::getId);
        Page<ProcRfq> page = rfqMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);
        List<RfqViews.Summary> records = page.getRecords().stream()
                .map(this::toSummary).toList();
        return new PageResult<>(records, page.getTotal(), page.getSize(), page.getCurrent());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public RfqViews.Detail get(Long id) {
        return loadVisibleDetail(ServiceIdentityContext.requireTenantId(), id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<PurchaseOrderContracts.QuotationSnapshot> comparison(Long id) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        ProcRfq rfq = requireVisible(tenantId, id);
        RfqStateMachine.requireComparable(rfq.getStatus());
        List<ProcRfqLine> lines = requireRfqLines(tenantId, id);
        List<ProcRfqSupplier> invitations = requireInvitations(
                tenantId, id, loadInvitations(tenantId, id));
        List<PurchaseOrderContracts.QuotationSnapshot> quotations =
                fetchCurrentQuotations(tenantId, rfq.getId());
        for (PurchaseOrderContracts.QuotationSnapshot quotation : quotations) {
            validateQuotationSnapshot(rfq, lines, invitations, quotation, 503);
        }
        return List.copyOf(quotations);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public RfqViews.Detail create(RfqRequests.CreateRequest request) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        ServiceDataScopeContext.ScopeInfo scope = requireOwnerScope();
        requireFutureDeadline(request.getQuotationDeadline());
        ProcRequisition requisition = accessGuard.requireVisible(
                requisitionMapper.selectOne(new LambdaQueryWrapper<ProcRequisition>()
                        .eq(ProcRequisition::getTenantId, tenantId)
                        .eq(ProcRequisition::getId, request.getRequisitionId())),
                "来源请购申请不存在");
        if (!RequisitionStateMachine.APPROVED.equals(requisition.getStatus())) {
            throw new BusinessException(409, "仅已审批请购申请可以创建询价单");
        }
        List<ProcRequisitionLine> sourceLines = requisitionLineMapper.selectList(
                new LambdaQueryWrapper<ProcRequisitionLine>()
                        .eq(ProcRequisitionLine::getTenantId, tenantId)
                        .eq(ProcRequisitionLine::getRequisitionId, requisition.getId())
                        .orderByAsc(ProcRequisitionLine::getLineNo));
        if (sourceLines.isEmpty()) {
            throw new BusinessException(409, "已审批请购申请缺少有效明细");
        }
        List<SupplierSnapshot> suppliers = validateSuppliers(tenantId, request.getSupplierIds());

        ProcRfq rfq = new ProcRfq();
        rfq.setTenantId(tenantId);
        rfq.setRfqNo("TMP-" + UUID.randomUUID());
        rfq.setRequisitionId(requisition.getId());
        rfq.setTitle(requiredText(request.getTitle(), "询价标题", 200));
        rfq.setQuotationDeadline(request.getQuotationDeadline());
        rfq.setCurrencyCode(requiredText(requisition.getCurrencyCode(), "请购币种", 16));
        rfq.setStatus(RfqStateMachine.DRAFT);
        rfq.setOwnerUserId(scope.userId());
        rfq.setOwnerUnitId(scope.primaryUnitId());
        rfq.setVersion(0);
        rfq.setDeleted(0);
        ProcAuditSupport.created(rfq);
        rfqMapper.insert(rfq);

        String rfqNo = "RFQ-" + tenantId + "-" + rfq.getId();
        int numbered = rfqMapper.update(null, new LambdaUpdateWrapper<ProcRfq>()
                .eq(ProcRfq::getTenantId, tenantId)
                .eq(ProcRfq::getId, rfq.getId())
                .eq(ProcRfq::getDeleted, 0)
                .set(ProcRfq::getRfqNo, rfqNo));
        accessGuard.requireAffected(numbered, "生成询价单号失败");
        rfq.setRfqNo(rfqNo);
        List<ProcRfqLine> lines = copyLines(tenantId, rfq.getId(), sourceLines);
        List<ProcRfqSupplier> invitations = insertInvitations(tenantId, rfq.getId(), suppliers);
        return toDetail(rfq, lines, invitations);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public RfqViews.Detail update(Long id, RfqRequests.UpdateRequest request) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        ProcRfq visible = requireVisible(tenantId, id);
        RfqStateMachine.requireEditable(visible.getStatus());
        requireVersion(visible, request.getVersion());
        requireFutureDeadline(request.getQuotationDeadline());
        List<SupplierSnapshot> suppliers = validateSuppliers(tenantId, request.getSupplierIds());

        ProcRfq current = requireLocked(tenantId, id);
        RfqStateMachine.requireEditable(current.getStatus());
        requireVersion(current, request.getVersion());
        LambdaUpdateWrapper<ProcRfq> update = versioned(current, request.getVersion())
                .set(ProcRfq::getTitle, requiredText(request.getTitle(), "询价标题", 200))
                .set(ProcRfq::getQuotationDeadline, request.getQuotationDeadline());
        audit(update);
        accessGuard.requireAffected(rfqMapper.update(null, update), "询价单已被其他请求修改");
        softDeleteInvitations(tenantId, id);
        insertInvitations(tenantId, id, suppliers);
        return loadVisibleDetail(tenantId, id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(Long id, Integer version) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        ProcRfq current = requireLocked(tenantId, id);
        RfqStateMachine.requireDeletable(current.getStatus());
        requireVersion(current, version);
        softDeleteLines(tenantId, id);
        softDeleteInvitations(tenantId, id);
        LambdaUpdateWrapper<ProcRfq> update = versioned(current, version)
                .set(ProcRfq::getDeleted, 1);
        audit(update);
        accessGuard.requireAffected(rfqMapper.update(null, update), "询价单已被其他请求修改");
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public RfqViews.Detail send(Long id, Integer version) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        ProcRfq visible = requireVisible(tenantId, id);
        RfqStateMachine.requireSendable(visible.getStatus());
        requireVersion(visible, version);
        requireFutureDeadline(visible.getQuotationDeadline());
        List<ProcRfqSupplier> preflightInvitations = loadInvitations(tenantId, id);
        if (preflightInvitations.isEmpty()) {
            throw new BusinessException(409, "询价单至少需要一个供应商邀请");
        }
        validateSuppliers(tenantId, preflightInvitations.stream()
                .map(ProcRfqSupplier::getSupplierId).toList());

        ProcRfq current = requireLocked(tenantId, id);
        RfqStateMachine.requireSendable(current.getStatus());
        requireVersion(current, version);
        requireFutureDeadline(current.getQuotationDeadline());
        List<ProcRfqLine> lines = loadLines(tenantId, id);
        List<ProcRfqSupplier> invitations = loadInvitations(tenantId, id);
        if (lines.isEmpty()) {
            throw new BusinessException(409, "询价单缺少有效行快照");
        }
        if (invitations.isEmpty()) {
            throw new BusinessException(409, "询价单至少需要一个供应商邀请");
        }

        LocalDateTime sentTime = LocalDateTime.now();
        LambdaUpdateWrapper<ProcRfq> update = versioned(current, version)
                .set(ProcRfq::getStatus, RfqStateMachine.SENT)
                .set(ProcRfq::getSentTime, sentTime);
        audit(update);
        accessGuard.requireAffected(rfqMapper.update(null, update), "询价单已被其他请求修改");
        int invited = supplierMapper.update(null, new LambdaUpdateWrapper<ProcRfqSupplier>()
                .eq(ProcRfqSupplier::getTenantId, tenantId)
                .eq(ProcRfqSupplier::getRfqId, id)
                .eq(ProcRfqSupplier::getDeleted, 0)
                .set(ProcRfqSupplier::getStatus, RfqStateMachine.INVITED)
                .set(ProcRfqSupplier::getInvitedTime, sentTime)
                .set(ProcRfqSupplier::getUpdateTime, sentTime)
                .set(ProcRfqSupplier::getUpdateBy, operator())
                .setSql("version = version + 1"));
        if (invited != invitations.size()) {
            throw new BusinessException(409, "供应商邀请已被其他请求修改");
        }
        publishSentEvent(current, lines, invitations, sentTime);
        return loadVisibleDetail(tenantId, id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public RfqViews.AwardResult award(Long id, RfqRequests.AwardRequest request) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        requireAwardSelection(request);
        ProcRfq current = requireLocked(tenantId, id);
        RfqStateMachine.requireAwardable(current.getStatus());
        requireVersion(current, request.getRfqVersion());
        List<ProcRfqLine> lines = requireRfqLines(tenantId, id);
        List<ProcRfqSupplier> invitations = requireInvitations(
                tenantId, id, supplierMapper.selectForUpdateByRfq(tenantId, id));

        List<PurchaseOrderContracts.QuotationSnapshot> quotations =
                fetchCurrentQuotations(tenantId, id);
        PurchaseOrderContracts.QuotationSnapshot selected = quotations.stream()
                .filter(Objects::nonNull)
                .filter(quotation -> request.getQuotationId().equals(quotation.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(409, "所选报价已失效，请刷新比价结果"));
        if (!request.getQuotationVersion().equals(selected.getVersion())) {
            throw new BusinessException(409, "所选报价已更新，请刷新比价结果");
        }
        validateQuotationSnapshot(current, lines, invitations, selected, 409);
        ProcRfqSupplier winner = invitations.stream()
                .filter(invitation -> selected.getSupplierId().equals(invitation.getSupplierId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(409, "所选报价供应商未受邀"));
        if (!RfqStateMachine.isActiveInvitation(winner.getStatus())) {
            throw new BusinessException(409, "所选供应商邀请已失效");
        }
        if (winner.getQuotationId() != null
                && !winner.getQuotationId().equals(selected.getId())) {
            throw new BusinessException(409, "供应商邀请已绑定其他报价");
        }
        if (winner.getQuotationVersion() != null
                && winner.getQuotationVersion() > selected.getVersion()) {
            throw new BusinessException(409, "供应商邀请报价版本晚于当前报价，请稍后重试");
        }

        PurchaseOrderRequests.AwardTerms terms = toAwardTerms(request);
        PurchaseOrderViews.Detail purchaseOrder = purchaseOrderService.createFromAward(
                current, lines, selected, terms);
        LocalDateTime awardedTime = LocalDateTime.now();
        LambdaUpdateWrapper<ProcRfq> update = versioned(current, request.getRfqVersion())
                .set(ProcRfq::getStatus, RfqStateMachine.AWARDED)
                .set(ProcRfq::getAwardedSupplierId, selected.getSupplierId())
                .set(ProcRfq::getAwardedQuotationId, selected.getId())
                .set(ProcRfq::getAwardedQuotationVersion, selected.getVersion())
                .set(ProcRfq::getAwardedTime, awardedTime);
        audit(update);
        accessGuard.requireAffected(rfqMapper.update(null, update), "询价单已被其他请求修改");
        updateInvitationAwards(
                new InvitationAwardContext(tenantId, id, selected, awardedTime),
                invitations, winner);
        publishAwardedEvent(current, selected, purchaseOrder, awardedTime);

        RfqViews.AwardResult result = new RfqViews.AwardResult();
        result.setRfq(loadVisibleDetail(tenantId, id));
        result.setPurchaseOrder(purchaseOrder);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public RfqViews.Detail cancel(Long id, Integer version) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        ProcRfq current = requireLocked(tenantId, id);
        RfqStateMachine.requireCancellable(current.getStatus());
        requireVersion(current, version);
        LambdaUpdateWrapper<ProcRfq> update = versioned(current, version)
                .set(ProcRfq::getStatus, RfqStateMachine.CANCELLED);
        audit(update);
        accessGuard.requireAffected(rfqMapper.update(null, update), "询价单已被其他请求修改");
        LocalDateTime now = LocalDateTime.now();
        supplierMapper.update(null, new LambdaUpdateWrapper<ProcRfqSupplier>()
                .eq(ProcRfqSupplier::getTenantId, tenantId)
                .eq(ProcRfqSupplier::getRfqId, id)
                .eq(ProcRfqSupplier::getDeleted, 0)
                .set(ProcRfqSupplier::getStatus, RfqStateMachine.EXPIRED)
                .set(ProcRfqSupplier::getUpdateTime, now)
                .set(ProcRfqSupplier::getUpdateBy, operator())
                .setSql("version = version + 1"));
        return loadVisibleDetail(tenantId, id);
    }

    private List<SupplierSnapshot> validateSuppliers(Long tenantId, List<Long> supplierIds) {
        if (supplierIds == null || supplierIds.isEmpty()) {
            throw new BusinessException(400, "受邀供应商不能为空");
        }
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long supplierId : supplierIds) {
            if (supplierId == null || supplierId <= 0) {
                throw new BusinessException(400, "供应商 ID 必须为正整数");
            }
            if (!normalized.add(supplierId)) {
                throw new BusinessException(400, "受邀供应商不能重复");
            }
        }
        if (normalized.size() > 100) {
            throw new BusinessException(400, "受邀供应商单次最多 100 个");
        }
        SrmSupplierContracts.BatchRequest request = new SrmSupplierContracts.BatchRequest();
        request.setTenantId(tenantId);
        request.setSupplierIds(List.copyOf(normalized));
        List<SrmSupplierContracts.Summary> summaries;
        try {
            R<List<SrmSupplierContracts.Summary>> response = srmInternalClient.batch(tenantId, request);
            if (response == null || response.getCode() != 200 || response.getData() == null) {
                throw new BusinessException(503, "SRM 供应商校验服务暂时不可用");
            }
            summaries = response.getData();
        } catch (BusinessException exception) {
            throw exception;
        } catch (FeignException exception) {
            throw new BusinessException(503, "SRM 供应商校验服务暂时不可用");
        }
        Map<Long, SrmSupplierContracts.Summary> byId = new LinkedHashMap<>();
        for (SrmSupplierContracts.Summary summary : summaries) {
            if (summary == null || summary.getId() == null
                    || !normalized.contains(summary.getId())
                    || byId.putIfAbsent(summary.getId(), summary) != null) {
                throw new BusinessException(503, "SRM 返回了无效的供应商批量结果");
            }
        }
        List<SupplierSnapshot> result = new ArrayList<>(normalized.size());
        for (Long supplierId : normalized) {
            SrmSupplierContracts.Summary summary = byId.get(supplierId);
            if (summary == null) {
                throw new BusinessException(409, "受邀供应商不存在或不属于当前租户");
            }
            if (!"APPROVED".equals(summary.getStatus())) {
                throw new BusinessException(409, "仅可邀请当前为 APPROVED 的供应商");
            }
            String supplierName = requiredText(summary.getName(), "供应商名称", 200);
            result.add(new SupplierSnapshot(supplierId, supplierName));
        }
        return List.copyOf(result);
    }

    private List<PurchaseOrderContracts.QuotationSnapshot> fetchCurrentQuotations(
            Long tenantId, Long rfqId) {
        try {
            R<List<PurchaseOrderContracts.QuotationSnapshot>> response =
                    srmInternalClient.listValidQuotations(tenantId, tenantId, rfqId);
            if (response == null || response.getCode() != 200 || response.getData() == null) {
                throw new BusinessException(503, "SRM 报价查询服务暂时不可用");
            }
            return response.getData();
        } catch (BusinessException exception) {
            throw exception;
        } catch (FeignException exception) {
            throw new BusinessException(503, "SRM 报价查询服务暂时不可用");
        }
    }

    private void validateQuotationSnapshot(
            ProcRfq rfq,
            List<ProcRfqLine> rfqLines,
            List<ProcRfqSupplier> invitations,
            PurchaseOrderContracts.QuotationSnapshot quotation,
            int errorCode) {
        if (quotation == null || quotation.getId() == null || quotation.getId() <= 0
                || quotation.getVersion() == null || quotation.getVersion() <= 0
                || quotation.getSupplierId() == null || quotation.getSupplierId() <= 0
                || !Objects.equals(rfq.getId(), quotation.getRfqId())
                || !Objects.equals(rfq.getRfqNo(), quotation.getRfqNo())
                || !Objects.equals(rfq.getCurrencyCode(), quotation.getCurrencyCode())
                || !"SUBMITTED".equals(quotation.getStatus())
                || quotation.getQuotationTime() == null
                || quotation.getValidUntil() == null
                || !quotation.getValidUntil().isAfter(LocalDateTime.now())
                || trimToNull(quotation.getSupplierNameSnapshot()) == null) {
            throw quotationError(errorCode, "报价头快照与 RFQ 不一致或已失效");
        }
        boolean invited = invitations.stream().anyMatch(invitation ->
                quotation.getSupplierId().equals(invitation.getSupplierId()));
        if (!invited) {
            throw quotationError(errorCode, "报价供应商未受当前 RFQ 邀请");
        }
        Map<Long, ProcRfqLine> expectedById = new LinkedHashMap<>();
        for (ProcRfqLine line : rfqLines) {
            if (line == null || line.getId() == null
                    || expectedById.putIfAbsent(line.getId(), line) != null) {
                throw new BusinessException(409, "RFQ 行快照无效或重复");
            }
        }
        if (quotation.getLines() == null || quotation.getLines().size() != expectedById.size()) {
            throw quotationError(errorCode, "报价行集合与 RFQ 不一致");
        }
        LinkedHashSet<Long> quotedLineIds = new LinkedHashSet<>();
        BigDecimal total = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        for (PurchaseOrderContracts.QuotationLineSnapshot quoted : quotation.getLines()) {
            if (quoted == null || quoted.getRfqLineId() == null
                    || !quotedLineIds.add(quoted.getRfqLineId())) {
                throw quotationError(errorCode, "报价行快照无效或重复");
            }
            ProcRfqLine expected = expectedById.get(quoted.getRfqLineId());
            if (expected == null
                    || !Objects.equals(expected.getMaterialCode(), quoted.getMaterialCode())
                    || !Objects.equals(expected.getMaterialName(), quoted.getMaterialName())
                    || !Objects.equals(expected.getUnit(), quoted.getUnit())
                    || expected.getQuantity() == null || quoted.getQuantity() == null
                    || expected.getQuantity().compareTo(quoted.getQuantity()) != 0
                    || quoted.getUnitPrice() == null
                    || quoted.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0
                    || quoted.getLineAmount() == null
                    || quoted.getDeliveryDays() == null
                    || quoted.getDeliveryDays() < 0 || quoted.getDeliveryDays() > 3650) {
                throw quotationError(errorCode, "报价行快照与 RFQ 不一致");
            }
            BigDecimal lineAmount = quoted.getUnitPrice().multiply(expected.getQuantity())
                    .setScale(4, RoundingMode.HALF_UP);
            if (lineAmount.compareTo(quoted.getLineAmount()) != 0) {
                throw quotationError(errorCode, "报价行金额校验失败");
            }
            total = total.add(lineAmount);
        }
        if (quotation.getTotalAmount() == null
                || total.setScale(4, RoundingMode.HALF_UP)
                .compareTo(quotation.getTotalAmount()) != 0) {
            throw quotationError(errorCode, "报价总金额校验失败");
        }
    }

    private BusinessException quotationError(int errorCode, String detail) {
        String prefix = errorCode == 503 ? "SRM 返回的" : "所选";
        return new BusinessException(errorCode, prefix + detail);
    }

    private List<ProcRfqLine> requireRfqLines(Long tenantId, Long rfqId) {
        List<ProcRfqLine> lines = loadLines(tenantId, rfqId);
        if (lines.isEmpty()) {
            throw new BusinessException(409, "询价单缺少有效行快照");
        }
        LinkedHashSet<Long> lineIds = new LinkedHashSet<>();
        for (ProcRfqLine line : lines) {
            if (line == null || line.getId() == null || line.getId() <= 0
                    || !tenantId.equals(line.getTenantId())
                    || !rfqId.equals(line.getRfqId())
                    || !lineIds.add(line.getId())) {
                throw new BusinessException(409, "询价单行快照无效或重复");
            }
        }
        return lines;
    }

    private List<ProcRfqSupplier> requireInvitations(
            Long tenantId, Long rfqId, List<ProcRfqSupplier> invitations) {
        if (invitations == null || invitations.isEmpty()) {
            throw new BusinessException(409, "询价单缺少供应商邀请");
        }
        LinkedHashSet<Long> supplierIds = new LinkedHashSet<>();
        for (ProcRfqSupplier invitation : invitations) {
            if (invitation == null || invitation.getId() == null
                    || !tenantId.equals(invitation.getTenantId())
                    || !rfqId.equals(invitation.getRfqId())
                    || invitation.getSupplierId() == null
                    || !supplierIds.add(invitation.getSupplierId())) {
                throw new BusinessException(409, "询价单供应商邀请无效或重复");
            }
        }
        return invitations;
    }

    private void requireAwardSelection(RfqRequests.AwardRequest request) {
        if (request == null
                || request.getQuotationId() == null || request.getQuotationId() <= 0
                || request.getQuotationVersion() == null || request.getQuotationVersion() <= 0) {
            throw new BusinessException(400, "定点报价 ID 与版本必须为正整数");
        }
    }

    private PurchaseOrderRequests.AwardTerms toAwardTerms(RfqRequests.AwardRequest request) {
        PurchaseOrderRequests.AwardTerms terms = new PurchaseOrderRequests.AwardTerms();
        terms.setTitle(request.getTitle());
        terms.setExpectedDeliveryDate(request.getExpectedDeliveryDate());
        terms.setDeliveryAddress(request.getDeliveryAddress());
        terms.setContactName(request.getContactName());
        terms.setContactPhone(request.getContactPhone());
        return terms;
    }

    private void updateInvitationAwards(
            InvitationAwardContext context,
            List<ProcRfqSupplier> invitations,
            ProcRfqSupplier winner) {
        PurchaseOrderContracts.QuotationSnapshot quotation = context.quotation();
        int winnerUpdated = supplierMapper.update(null,
                new LambdaUpdateWrapper<ProcRfqSupplier>()
                        .eq(ProcRfqSupplier::getTenantId, context.tenantId())
                        .eq(ProcRfqSupplier::getId, winner.getId())
                        .eq(ProcRfqSupplier::getRfqId, context.rfqId())
                        .eq(ProcRfqSupplier::getSupplierId, winner.getSupplierId())
                        .eq(ProcRfqSupplier::getDeleted, 0)
                        .in(ProcRfqSupplier::getStatus,
                                RfqStateMachine.INVITED, RfqStateMachine.QUOTED)
                        .set(ProcRfqSupplier::getStatus, RfqStateMachine.AWARDED)
                        .set(ProcRfqSupplier::getQuotationId, quotation.getId())
                        .set(ProcRfqSupplier::getQuotationVersion, quotation.getVersion())
                        .set(ProcRfqSupplier::getQuotationTime, quotation.getQuotationTime())
                        .set(ProcRfqSupplier::getUpdateTime, context.awardedTime())
                        .set(ProcRfqSupplier::getUpdateBy, operator())
                        .setSql("version = version + 1"));
        if (winnerUpdated != 1) {
            throw new BusinessException(409, "中标供应商邀请已被其他请求修改");
        }
        int expectedLosers = invitations.size() - 1;
        if (expectedLosers == 0) {
            return;
        }
        int losersUpdated = supplierMapper.update(null,
                new LambdaUpdateWrapper<ProcRfqSupplier>()
                        .eq(ProcRfqSupplier::getTenantId, context.tenantId())
                        .eq(ProcRfqSupplier::getRfqId, context.rfqId())
                        .ne(ProcRfqSupplier::getSupplierId, winner.getSupplierId())
                        .eq(ProcRfqSupplier::getDeleted, 0)
                        .set(ProcRfqSupplier::getStatus, RfqStateMachine.REJECTED)
                        .set(ProcRfqSupplier::getUpdateTime, context.awardedTime())
                        .set(ProcRfqSupplier::getUpdateBy, operator())
                        .setSql("version = version + 1"));
        if (losersUpdated != expectedLosers) {
            throw new BusinessException(409, "未中标供应商邀请已被其他请求修改");
        }
    }

    private void publishAwardedEvent(
            ProcRfq rfq,
            PurchaseOrderContracts.QuotationSnapshot quotation,
            PurchaseOrderViews.Detail purchaseOrder,
            LocalDateTime awardedTime) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rfqId", rfq.getId());
        payload.put("rfqNo", rfq.getRfqNo());
        payload.put("requisitionId", rfq.getRequisitionId());
        payload.put("status", RfqStateMachine.AWARDED);
        payload.put("supplierId", quotation.getSupplierId());
        payload.put("quotationId", quotation.getId());
        payload.put("quotationVersion", quotation.getVersion());
        payload.put("purchaseOrderId", purchaseOrder.getId());
        payload.put("poNo", purchaseOrder.getPoNo());
        payload.put("totalAmount", quotation.getTotalAmount().toPlainString());
        payload.put("currencyCode", quotation.getCurrencyCode());
        payload.put("awardedTime", awardedTime);
        RfqContracts.DomainEvent event = RfqContracts.DomainEvent.builder()
                .eventId(eventId)
                .eventType(RFQ_AWARDED_EVENT)
                .occurredAt(awardedTime)
                .tenantId(rfq.getTenantId())
                .payload(payload)
                .build();
        reliableMessageRelay.send(DOMAIN_BINDING, event, rfq.getTenantId(), eventId);
    }

    private List<ProcRfqLine> copyLines(Long tenantId, Long rfqId,
                                        List<ProcRequisitionLine> sourceLines) {
        List<ProcRfqLine> result = new ArrayList<>(sourceLines.size());
        for (ProcRequisitionLine source : sourceLines) {
            ProcRfqLine line = new ProcRfqLine();
            line.setTenantId(tenantId);
            line.setRfqId(rfqId);
            line.setLineNo(source.getLineNo());
            line.setMaterialId(source.getMaterialId());
            line.setMaterialCode(source.getMaterialCode());
            line.setMaterialName(source.getMaterialName());
            line.setCategoryCode(source.getCategoryCode());
            line.setUnit(source.getUnit());
            line.setQuantity(source.getQuantity());
            line.setRemark(source.getRemark());
            line.setVersion(0);
            line.setDeleted(0);
            ProcAuditSupport.created(line);
            lineMapper.insert(line);
            result.add(line);
        }
        return List.copyOf(result);
    }

    private List<ProcRfqSupplier> insertInvitations(Long tenantId, Long rfqId,
                                                     List<SupplierSnapshot> suppliers) {
        List<ProcRfqSupplier> result = new ArrayList<>(suppliers.size());
        for (SupplierSnapshot snapshot : suppliers) {
            ProcRfqSupplier invitation = new ProcRfqSupplier();
            invitation.setTenantId(tenantId);
            invitation.setRfqId(rfqId);
            invitation.setSupplierId(snapshot.id());
            invitation.setSupplierNameSnapshot(snapshot.name());
            invitation.setStatus(RfqStateMachine.INVITED);
            invitation.setVersion(0);
            invitation.setDeleted(0);
            ProcAuditSupport.created(invitation);
            supplierMapper.insert(invitation);
            result.add(invitation);
        }
        return List.copyOf(result);
    }

    private void publishSentEvent(ProcRfq rfq, List<ProcRfqLine> lines,
                                  List<ProcRfqSupplier> invitations, LocalDateTime sentTime) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rfqId", rfq.getId());
        payload.put("rfqNo", rfq.getRfqNo());
        payload.put("requisitionId", rfq.getRequisitionId());
        payload.put("status", RfqStateMachine.SENT);
        payload.put("quotationDeadline", rfq.getQuotationDeadline());
        payload.put("currencyCode", rfq.getCurrencyCode());
        payload.put("sentTime", sentTime);
        payload.put("supplierIds", invitations.stream()
                .map(ProcRfqSupplier::getSupplierId).toList());
        payload.put("lineCount", lines.size());
        RfqContracts.DomainEvent event = RfqContracts.DomainEvent.builder()
                .eventId(eventId)
                .eventType(RFQ_SENT_EVENT)
                .occurredAt(sentTime)
                .tenantId(rfq.getTenantId())
                .payload(payload)
                .build();
        reliableMessageRelay.send(DOMAIN_BINDING, event, rfq.getTenantId(), eventId);
    }

    private RfqViews.Detail loadVisibleDetail(Long tenantId, Long id) {
        ProcRfq rfq = requireVisible(tenantId, id);
        return toDetail(rfq, loadLines(tenantId, id), loadInvitations(tenantId, id));
    }

    private ProcRfq requireVisible(Long tenantId, Long id) {
        ProcRfq rfq = accessGuard.requireVisible(
                rfqMapper.selectOne(new LambdaQueryWrapper<ProcRfq>()
                .eq(ProcRfq::getTenantId, tenantId)
                .eq(ProcRfq::getId, id)), "询价单不存在");
        if (!tenantId.equals(rfq.getTenantId()) || !id.equals(rfq.getId())) {
            throw new BusinessException(404, "询价单不存在");
        }
        return rfq;
    }

    private ProcRfq requireLocked(Long tenantId, Long id) {
        ProcRfq rfq = accessGuard.requireVisible(
                rfqMapper.selectForUpdate(tenantId, id), "询价单不存在");
        if (!tenantId.equals(rfq.getTenantId()) || !id.equals(rfq.getId())) {
            throw new BusinessException(404, "询价单不存在");
        }
        return rfq;
    }

    private List<ProcRfqLine> loadLines(Long tenantId, Long rfqId) {
        return lineMapper.selectList(new LambdaQueryWrapper<ProcRfqLine>()
                .eq(ProcRfqLine::getTenantId, tenantId)
                .eq(ProcRfqLine::getRfqId, rfqId)
                .orderByAsc(ProcRfqLine::getLineNo));
    }

    private List<ProcRfqSupplier> loadInvitations(Long tenantId, Long rfqId) {
        return supplierMapper.selectList(new LambdaQueryWrapper<ProcRfqSupplier>()
                .eq(ProcRfqSupplier::getTenantId, tenantId)
                .eq(ProcRfqSupplier::getRfqId, rfqId)
                .orderByAsc(ProcRfqSupplier::getId));
    }

    private void softDeleteLines(Long tenantId, Long rfqId) {
        lineMapper.update(null, new LambdaUpdateWrapper<ProcRfqLine>()
                .eq(ProcRfqLine::getTenantId, tenantId)
                .eq(ProcRfqLine::getRfqId, rfqId)
                .eq(ProcRfqLine::getDeleted, 0)
                .set(ProcRfqLine::getDeleted, 1)
                .set(ProcRfqLine::getUpdateTime, LocalDateTime.now())
                .set(ProcRfqLine::getUpdateBy, operator())
                .setSql("version = version + 1"));
    }

    private void softDeleteInvitations(Long tenantId, Long rfqId) {
        supplierMapper.update(null, new LambdaUpdateWrapper<ProcRfqSupplier>()
                .eq(ProcRfqSupplier::getTenantId, tenantId)
                .eq(ProcRfqSupplier::getRfqId, rfqId)
                .eq(ProcRfqSupplier::getDeleted, 0)
                .set(ProcRfqSupplier::getDeleted, 1)
                .set(ProcRfqSupplier::getUpdateTime, LocalDateTime.now())
                .set(ProcRfqSupplier::getUpdateBy, operator())
                .setSql("version = version + 1"));
    }

    private LambdaUpdateWrapper<ProcRfq> versioned(ProcRfq current, Integer version) {
        return new LambdaUpdateWrapper<ProcRfq>()
                .eq(ProcRfq::getTenantId, current.getTenantId())
                .eq(ProcRfq::getId, current.getId())
                .eq(ProcRfq::getVersion, version)
                .eq(ProcRfq::getStatus, current.getStatus())
                .eq(ProcRfq::getDeleted, 0)
                .setSql("version = version + 1");
    }

    private void audit(LambdaUpdateWrapper<ProcRfq> update) {
        update.set(ProcRfq::getUpdateTime, LocalDateTime.now())
                .set(ProcRfq::getUpdateBy, operator());
    }

    private ServiceDataScopeContext.ScopeInfo requireOwnerScope() {
        ServiceDataScopeContext.ScopeInfo scope = ServiceDataScopeContext.require();
        if (scope.userId() == null || scope.userId() <= 0
                || scope.primaryUnitId() == null || scope.primaryUnitId() <= 0) {
            throw new BusinessException(403, "当前用户缺少有效的负责人或主组织上下文");
        }
        return scope;
    }

    private void requireVersion(ProcRfq current, Integer version) {
        if (version == null || version < 0) {
            throw new BusinessException(400, "乐观锁版本不能为空且不能小于 0");
        }
        if (!version.equals(current.getVersion())) {
            throw new BusinessException(409, "询价单已被其他请求修改");
        }
    }

    private void requireFutureDeadline(LocalDateTime deadline) {
        if (deadline == null) {
            throw new BusinessException(400, "报价截止时间不能为空");
        }
        if (!deadline.isAfter(LocalDateTime.now())) {
            throw new BusinessException(409, "报价截止时间必须晚于当前时间");
        }
    }

    private String requiredText(String value, String field, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException(400, field + "不能为空");
        }
        if (normalized.length() > maxLength) {
            throw new BusinessException(400, field + "不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String operator() {
        ServiceRequestIdentity identity = ServiceIdentityContext.require();
        return identity.username() == null || identity.username().isBlank()
                ? String.valueOf(identity.userId()) : identity.username();
    }

    private RfqViews.Summary toSummary(ProcRfq rfq) {
        RfqViews.Summary view = new RfqViews.Summary();
        copySummary(rfq, view);
        return view;
    }

    private RfqViews.Detail toDetail(ProcRfq rfq, List<ProcRfqLine> lines,
                                     List<ProcRfqSupplier> invitations) {
        RfqViews.Detail view = new RfqViews.Detail();
        copySummary(rfq, view);
        view.setLines(lines.stream().map(this::toLine).toList());
        view.setSuppliers(invitations.stream().map(this::toSupplier).toList());
        return view;
    }

    private void copySummary(ProcRfq source, RfqViews.Summary target) {
        target.setId(source.getId());
        target.setRfqNo(source.getRfqNo());
        target.setRequisitionId(source.getRequisitionId());
        target.setTitle(source.getTitle());
        target.setQuotationDeadline(source.getQuotationDeadline());
        target.setCurrencyCode(source.getCurrencyCode());
        target.setStatus(source.getStatus());
        target.setSentTime(source.getSentTime());
        target.setAwardedSupplierId(source.getAwardedSupplierId());
        target.setAwardedQuotationId(source.getAwardedQuotationId());
        target.setAwardedQuotationVersion(source.getAwardedQuotationVersion());
        target.setAwardedTime(source.getAwardedTime());
        target.setOwnerUserId(source.getOwnerUserId());
        target.setOwnerUnitId(source.getOwnerUnitId());
        target.setVersion(source.getVersion());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
    }

    private RfqViews.Line toLine(ProcRfqLine source) {
        RfqViews.Line view = new RfqViews.Line();
        view.setId(source.getId());
        view.setLineNo(source.getLineNo());
        view.setMaterialId(source.getMaterialId());
        view.setMaterialCode(source.getMaterialCode());
        view.setMaterialName(source.getMaterialName());
        view.setCategoryCode(source.getCategoryCode());
        view.setUnit(source.getUnit());
        view.setQuantity(source.getQuantity());
        view.setRemark(source.getRemark());
        view.setVersion(source.getVersion());
        return view;
    }

    private RfqViews.SupplierInvitation toSupplier(ProcRfqSupplier source) {
        RfqViews.SupplierInvitation view = new RfqViews.SupplierInvitation();
        view.setId(source.getId());
        view.setSupplierId(source.getSupplierId());
        view.setSupplierName(source.getSupplierNameSnapshot());
        view.setInvitedTime(source.getInvitedTime());
        view.setQuotationId(source.getQuotationId());
        view.setQuotationVersion(source.getQuotationVersion());
        view.setQuotationTime(source.getQuotationTime());
        view.setStatus(source.getStatus());
        view.setVersion(source.getVersion());
        return view;
    }

    private record SupplierSnapshot(Long id, String name) {
    }

    /**
     * 询价邀请中标更新上下文。
     */
    private record InvitationAwardContext(
            Long tenantId,
            Long rfqId,
            PurchaseOrderContracts.QuotationSnapshot quotation,
            LocalDateTime awardedTime) {
    }
}
