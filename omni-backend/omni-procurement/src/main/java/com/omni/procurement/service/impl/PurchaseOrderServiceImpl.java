package com.omni.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import com.omni.procurement.domain.PurchaseOrderStateMachine;
import com.omni.procurement.domain.RfqStateMachine;
import com.omni.procurement.dto.GoodsReceiptContracts;
import com.omni.procurement.dto.GoodsReceiptContracts.ReceivedTotal;
import com.omni.procurement.dto.PurchaseOrderContracts;
import com.omni.procurement.dto.PurchaseOrderRequests;
import com.omni.procurement.dto.PurchaseOrderViews;
import com.omni.procurement.entity.ProcPurchaseOrder;
import com.omni.procurement.entity.ProcPurchaseOrderLine;
import com.omni.procurement.entity.ProcRfq;
import com.omni.procurement.entity.ProcRfqLine;
import com.omni.procurement.mapper.ProcGoodsReceiptLineMapper;
import com.omni.procurement.mapper.ProcPurchaseOrderLineMapper;
import com.omni.procurement.mapper.ProcPurchaseOrderMapper;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.procurement.service.PurchaseOrderService;
import com.omni.procurement.service.support.ProcAuditSupport;
import com.omni.procurement.service.support.ProcRecordAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 采购订单服务实现。
 *
 * @author Omni-Stack Team
 */
@Service
@RequiredArgsConstructor
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private static final String DOMAIN_BINDING = "procurement-domain-out-0";
    private static final String CREATED_EVENT = "procurement.purchase-order.created.v1";
    private static final String CONFIRMED_EVENT = "procurement.purchase-order.confirmed.v1";
    private static final BigDecimal ZERO_QUANTITY = new BigDecimal("0.000000");

    private final ProcPurchaseOrderMapper orderMapper;
    private final ProcPurchaseOrderLineMapper lineMapper;
    private final ProcGoodsReceiptLineMapper receiptLineMapper;
    private final ReliableMessageRelay reliableMessageRelay;
    private final ProcRecordAccessGuard accessGuard;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PageResult<PurchaseOrderViews.Summary> page(PurchaseOrderRequests.Query query) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        LambdaQueryWrapper<ProcPurchaseOrder> wrapper = new LambdaQueryWrapper<ProcPurchaseOrder>()
                .eq(ProcPurchaseOrder::getTenantId, tenantId);
        String keyword = trimToNull(query.getKeyword());
        if (keyword != null) {
            wrapper.and(nested -> nested.like(ProcPurchaseOrder::getPoNo, keyword)
                    .or().like(ProcPurchaseOrder::getTitle, keyword)
                    .or().like(ProcPurchaseOrder::getSupplierNameSnapshot, keyword));
        }
        if (query.getRfqId() != null) {
            wrapper.eq(ProcPurchaseOrder::getRfqId, query.getRfqId());
        }
        if (query.getSupplierId() != null) {
            wrapper.eq(ProcPurchaseOrder::getSupplierId, query.getSupplierId());
        }
        if (trimToNull(query.getStatus()) != null) {
            wrapper.eq(ProcPurchaseOrder::getStatus, query.getStatus());
        }
        if (query.getExpectedDeliveryFrom() != null) {
            wrapper.ge(ProcPurchaseOrder::getExpectedDeliveryDate,
                    query.getExpectedDeliveryFrom());
        }
        if (query.getExpectedDeliveryTo() != null) {
            wrapper.le(ProcPurchaseOrder::getExpectedDeliveryDate,
                    query.getExpectedDeliveryTo());
        }
        if (query.getExpectedDeliveryFrom() != null && query.getExpectedDeliveryTo() != null
                && query.getExpectedDeliveryFrom().isAfter(query.getExpectedDeliveryTo())) {
            throw new BusinessException(400, "预计交付日期范围无效");
        }
        wrapper.orderByDesc(ProcPurchaseOrder::getCreateTime)
                .orderByDesc(ProcPurchaseOrder::getId);
        Page<ProcPurchaseOrder> page = orderMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);
        List<PurchaseOrderViews.Summary> records = page.getRecords().stream()
                .map(this::toSummary).toList();
        return new PageResult<>(records, page.getTotal(), page.getSize(), page.getCurrent());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PurchaseOrderViews.Detail get(Long id) {
        return loadVisibleDetail(ServiceIdentityContext.requireTenantId(), id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public PurchaseOrderViews.Detail createFromAward(
            ProcRfq rfq,
            List<ProcRfqLine> rfqLines,
            PurchaseOrderContracts.QuotationSnapshot quotation,
            PurchaseOrderRequests.AwardTerms terms) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        requireAwardInput(tenantId, rfq, rfqLines, quotation, terms);
        ProcPurchaseOrder existing = orderMapper.selectForUpdateByRfq(tenantId, rfq.getId());
        LocalDateTime awardTime = resolveAwardTime(existing);
        PreparedAward prepared = prepareAward(
                rfqLines, quotation, terms, awardTime.toLocalDate());
        if (existing != null) {
            requireSameAward(
                    new ExistingAward(existing, loadLines(tenantId, existing.getId())),
                    rfq, quotation, prepared, terms);
            return loadVisibleDetail(tenantId, existing.getId());
        }

        ProcPurchaseOrder order = new ProcPurchaseOrder();
        order.setTenantId(tenantId);
        order.setPoNo("TMP-" + UUID.randomUUID());
        order.setRfqId(rfq.getId());
        order.setSupplierId(quotation.getSupplierId());
        order.setSupplierNameSnapshot(requiredText(
                quotation.getSupplierNameSnapshot(), "供应商名称", 200));
        order.setQuotationId(quotation.getId());
        order.setQuotationVersion(quotation.getVersion());
        order.setTitle(requiredText(terms.getTitle(), "订单标题", 200));
        order.setTotalAmount(prepared.totalAmount());
        order.setCurrencyCode(rfq.getCurrencyCode());
        order.setStatus(PurchaseOrderStateMachine.DRAFT);
        order.setExpectedDeliveryDate(prepared.headerExpectedDeliveryDate());
        order.setDeliveryAddress(requiredText(terms.getDeliveryAddress(), "收货地址", 500));
        order.setContactName(requiredText(terms.getContactName(), "收货联系人", 100));
        order.setContactPhone(requiredText(terms.getContactPhone(), "收货联系电话", 50));
        order.setOwnerUserId(rfq.getOwnerUserId());
        order.setOwnerUnitId(rfq.getOwnerUnitId());
        order.setVersion(0);
        order.setDeleted(0);
        ProcAuditSupport.created(order);
        order.setCreateTime(awardTime);
        order.setUpdateTime(awardTime);
        orderMapper.insert(order);

        String poNo = "PO-" + tenantId + "-" + order.getId();
        int numbered = orderMapper.update(null, new LambdaUpdateWrapper<ProcPurchaseOrder>()
                .eq(ProcPurchaseOrder::getTenantId, tenantId)
                .eq(ProcPurchaseOrder::getId, order.getId())
                .eq(ProcPurchaseOrder::getDeleted, 0)
                .set(ProcPurchaseOrder::getPoNo, poNo));
        accessGuard.requireAffected(numbered, "生成采购订单号失败");
        order.setPoNo(poNo);
        List<ProcPurchaseOrderLine> lines = insertAwardLines(
                tenantId, order.getId(), prepared.lines());
        publishCreated(order, lines);
        return toDetail(order, lines, Map.of());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public PurchaseOrderViews.Detail update(Long id, PurchaseOrderRequests.UpdateRequest request) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        ProcPurchaseOrder current = requireLocked(tenantId, id);
        PurchaseOrderStateMachine.requireEditable(current.getStatus());
        requireVersion(current, request.getVersion());
        LambdaUpdateWrapper<ProcPurchaseOrder> update = versioned(current, request.getVersion())
                .set(ProcPurchaseOrder::getTitle,
                        requiredText(request.getTitle(), "订单标题", 200))
                .set(ProcPurchaseOrder::getExpectedDeliveryDate,
                        request.getExpectedDeliveryDate())
                .set(ProcPurchaseOrder::getDeliveryAddress,
                        requiredText(request.getDeliveryAddress(), "收货地址", 500))
                .set(ProcPurchaseOrder::getContactName,
                        requiredText(request.getContactName(), "收货联系人", 100))
                .set(ProcPurchaseOrder::getContactPhone,
                        requiredText(request.getContactPhone(), "收货联系电话", 50));
        audit(update);
        accessGuard.requireAffected(orderMapper.update(null, update), "采购订单已被其他请求修改");
        return loadVisibleDetail(tenantId, id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(Long id, Integer version) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        ProcPurchaseOrder current = requireLocked(tenantId, id);
        PurchaseOrderStateMachine.requireDeletable(current.getStatus());
        requireVersion(current, version);
        lineMapper.update(null, new LambdaUpdateWrapper<ProcPurchaseOrderLine>()
                .eq(ProcPurchaseOrderLine::getTenantId, tenantId)
                .eq(ProcPurchaseOrderLine::getPoId, id)
                .eq(ProcPurchaseOrderLine::getDeleted, 0)
                .set(ProcPurchaseOrderLine::getDeleted, 1)
                .set(ProcPurchaseOrderLine::getUpdateTime, LocalDateTime.now())
                .set(ProcPurchaseOrderLine::getUpdateBy, operator())
                .setSql("version = version + 1"));
        LambdaUpdateWrapper<ProcPurchaseOrder> update = versioned(current, version)
                .set(ProcPurchaseOrder::getDeleted, 1);
        audit(update);
        accessGuard.requireAffected(orderMapper.update(null, update), "采购订单已被其他请求修改");
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public PurchaseOrderViews.Detail send(Long id, Integer version) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        ProcPurchaseOrder current = requireLocked(tenantId, id);
        PurchaseOrderStateMachine.requireSendable(current.getStatus());
        requireVersion(current, version);
        LambdaUpdateWrapper<ProcPurchaseOrder> update = versioned(current, version)
                .set(ProcPurchaseOrder::getStatus, PurchaseOrderStateMachine.SENT)
                .set(ProcPurchaseOrder::getOrderTime, LocalDateTime.now());
        audit(update);
        accessGuard.requireAffected(orderMapper.update(null, update), "采购订单已被其他请求修改");
        return loadVisibleDetail(tenantId, id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public PurchaseOrderViews.Detail confirm(Long id, Integer version) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        ProcPurchaseOrder current = requireLocked(tenantId, id);
        PurchaseOrderStateMachine.requireConfirmable(current.getStatus());
        requireVersion(current, version);
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<ProcPurchaseOrder> update = versioned(current, version)
                .set(ProcPurchaseOrder::getStatus, PurchaseOrderStateMachine.CONFIRMED);
        audit(update);
        accessGuard.requireAffected(orderMapper.update(null, update), "采购订单已被其他请求修改");
        publishConfirmed(current, now);
        return loadVisibleDetail(tenantId, id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public PurchaseOrderViews.Detail cancel(Long id, Integer version) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        ProcPurchaseOrder current = requireLocked(tenantId, id);
        PurchaseOrderStateMachine.requireCancellable(current.getStatus());
        requireVersion(current, version);
        LambdaUpdateWrapper<ProcPurchaseOrder> update = versioned(current, version)
                .set(ProcPurchaseOrder::getStatus, PurchaseOrderStateMachine.CANCELLED);
        audit(update);
        accessGuard.requireAffected(orderMapper.update(null, update), "采购订单已被其他请求修改");
        return loadVisibleDetail(tenantId, id);
    }

    private PreparedAward prepareAward(
            List<ProcRfqLine> rfqLines,
            PurchaseOrderContracts.QuotationSnapshot quotation,
            PurchaseOrderRequests.AwardTerms terms,
            LocalDate awardDate) {
        Map<Long, PurchaseOrderContracts.QuotationLineSnapshot> quotedByRfqLine =
                new LinkedHashMap<>();
        if (quotation.getLines() == null) {
            throw new BusinessException(409, "中标报价缺少有效行快照");
        }
        for (PurchaseOrderContracts.QuotationLineSnapshot line : quotation.getLines()) {
            if (line == null || line.getRfqLineId() == null
                    || quotedByRfqLine.putIfAbsent(line.getRfqLineId(), line) != null) {
                throw new BusinessException(409, "中标报价行快照无效或重复");
            }
        }
        if (quotedByRfqLine.size() != rfqLines.size()) {
            throw new BusinessException(409, "中标报价行与 RFQ 行集合不一致");
        }

        BigDecimal total = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        List<PreparedLine> preparedLines = new ArrayList<>(rfqLines.size());
        LocalDate maxExpectedDate = awardDate;
        List<ProcRfqLine> orderedRfqLines = rfqLines.stream()
                .sorted(Comparator.comparing(ProcRfqLine::getLineNo))
                .toList();
        for (ProcRfqLine rfqLine : orderedRfqLines) {
            PurchaseOrderContracts.QuotationLineSnapshot quoted = quotedByRfqLine.get(rfqLine.getId());
            if (quoted == null) {
                throw new BusinessException(409, "中标报价缺少 RFQ 行 " + rfqLine.getId());
            }
            requireSameSnapshot(rfqLine, quoted);
            BigDecimal unitPrice = requirePositiveDecimal(quoted.getUnitPrice(), "中标单价");
            BigDecimal lineTotal = unitPrice.multiply(rfqLine.getQuantity())
                    .setScale(4, RoundingMode.HALF_UP);
            requireAmountFits(lineTotal, "中标报价行金额");
            if (quoted.getLineAmount() == null
                    || lineTotal.compareTo(quoted.getLineAmount()) != 0) {
                throw new BusinessException(409, "中标报价行金额校验失败");
            }
            int deliveryDays = quoted.getDeliveryDays() == null ? -1 : quoted.getDeliveryDays();
            if (deliveryDays < 0 || deliveryDays > 3650) {
                throw new BusinessException(409, "中标报价交付天数无效");
            }
            LocalDate expectedDate = awardDate.plusDays(deliveryDays);
            if (expectedDate.isAfter(maxExpectedDate)) {
                maxExpectedDate = expectedDate;
            }
            preparedLines.add(new PreparedLine(rfqLine, unitPrice, lineTotal,
                    deliveryDays, expectedDate, normalizeRemark(quoted.getRemark())));
            total = total.add(lineTotal);
        }
        total = total.setScale(4, RoundingMode.HALF_UP);
        requireAmountFits(total, "中标报价总金额");
        if (quotation.getTotalAmount() == null || total.compareTo(quotation.getTotalAmount()) != 0) {
            throw new BusinessException(409, "中标报价总金额校验失败");
        }
        LocalDate headerExpected = terms.getExpectedDeliveryDate() == null
                ? maxExpectedDate : terms.getExpectedDeliveryDate();
        if (headerExpected.isBefore(maxExpectedDate)) {
            throw new BusinessException(409, "订单预计交付日期不得早于报价最长交付日期");
        }
        return new PreparedAward(List.copyOf(preparedLines), total, headerExpected);
    }

    private LocalDateTime resolveAwardTime(ProcPurchaseOrder existing) {
        if (existing == null) {
            return LocalDateTime.now();
        }
        if (existing.getCreateTime() == null) {
            throw new BusinessException(409, "既有采购订单缺少创建时间快照");
        }
        return existing.getCreateTime();
    }

    private void requireAwardInput(
            Long tenantId,
            ProcRfq rfq,
            List<ProcRfqLine> rfqLines,
            PurchaseOrderContracts.QuotationSnapshot quotation,
            PurchaseOrderRequests.AwardTerms terms) {
        if (rfq == null || !tenantId.equals(rfq.getTenantId()) || rfq.getId() == null) {
            throw new BusinessException(403, "RFQ 不属于当前租户");
        }
        if (rfq.getOwnerUserId() == null || rfq.getOwnerUserId() <= 0
                || rfq.getOwnerUnitId() == null || rfq.getOwnerUnitId() <= 0) {
            throw new BusinessException(409, "RFQ 缺少有效负责人快照");
        }
        if (!RfqStateMachine.SENT.equals(rfq.getStatus())) {
            throw new BusinessException(409, "仅已发送 RFQ 可以定点生成采购订单");
        }
        if (rfqLines == null || rfqLines.isEmpty()
                || rfqLines.stream().anyMatch(line -> line == null
                || line.getId() == null || line.getId() <= 0
                || !tenantId.equals(line.getTenantId())
                || !rfq.getId().equals(line.getRfqId()))) {
            throw new BusinessException(409, "RFQ 行快照无效");
        }
        if (new LinkedHashSet<>(rfqLines.stream().map(ProcRfqLine::getId).toList()).size()
                != rfqLines.size()) {
            throw new BusinessException(409, "RFQ 行快照重复");
        }
        if (rfqLines.stream().anyMatch(line -> line.getLineNo() == null
                || line.getLineNo() <= 0)
                || new LinkedHashSet<>(rfqLines.stream()
                .map(ProcRfqLine::getLineNo).toList()).size() != rfqLines.size()) {
            throw new BusinessException(409, "RFQ 行号快照无效或重复");
        }
        if (quotation == null || quotation.getId() == null || quotation.getId() <= 0
                || quotation.getVersion() == null || quotation.getVersion() <= 0
                || quotation.getSupplierId() == null || quotation.getSupplierId() <= 0
                || !rfq.getId().equals(quotation.getRfqId())
                || !Objects.equals(rfq.getRfqNo(), quotation.getRfqNo())
                || !Objects.equals(rfq.getCurrencyCode(), quotation.getCurrencyCode())
                || !"SUBMITTED".equals(quotation.getStatus())) {
            throw new BusinessException(409, "中标报价与 RFQ 不匹配");
        }
        if (quotation.getValidUntil() == null
                || !quotation.getValidUntil().isAfter(LocalDateTime.now())) {
            throw new BusinessException(409, "中标报价已失效");
        }
        if (terms == null) {
            throw new BusinessException(400, "订单交付条款不能为空");
        }
        requiredText(terms.getTitle(), "订单标题", 200);
        requiredText(terms.getDeliveryAddress(), "收货地址", 500);
        requiredText(terms.getContactName(), "收货联系人", 100);
        requiredText(terms.getContactPhone(), "收货联系电话", 50);
    }

    private void requireSameSnapshot(
            ProcRfqLine rfqLine,
            PurchaseOrderContracts.QuotationLineSnapshot quoted) {
        if (!Objects.equals(rfqLine.getMaterialCode(), quoted.getMaterialCode())
                || !Objects.equals(rfqLine.getMaterialName(), quoted.getMaterialName())
                || !Objects.equals(rfqLine.getUnit(), quoted.getUnit())
                || quoted.getQuantity() == null
                || rfqLine.getQuantity() == null
                || rfqLine.getQuantity().compareTo(quoted.getQuantity()) != 0) {
            throw new BusinessException(409, "中标报价行快照与 RFQ 不一致");
        }
    }

    private void requireSameAward(
            ExistingAward existingAward,
            ProcRfq rfq,
            PurchaseOrderContracts.QuotationSnapshot quotation,
            PreparedAward prepared,
            PurchaseOrderRequests.AwardTerms terms) {
        ProcPurchaseOrder existing = existingAward.order();
        if (!Objects.equals(existing.getQuotationId(), quotation.getId())
                || !Objects.equals(existing.getQuotationVersion(), quotation.getVersion())
                || !Objects.equals(existing.getSupplierId(), quotation.getSupplierId())
                || !Objects.equals(existing.getSupplierNameSnapshot(),
                trimToNull(quotation.getSupplierNameSnapshot()))
                || !Objects.equals(existing.getCurrencyCode(), rfq.getCurrencyCode())
                || existing.getTotalAmount().compareTo(prepared.totalAmount()) != 0
                || !Objects.equals(existing.getExpectedDeliveryDate(),
                prepared.headerExpectedDeliveryDate())
                || !Objects.equals(existing.getTitle(), trimToNull(terms.getTitle()))
                || !Objects.equals(existing.getDeliveryAddress(),
                trimToNull(terms.getDeliveryAddress()))
                || !Objects.equals(existing.getContactName(), trimToNull(terms.getContactName()))
                || !Objects.equals(existing.getContactPhone(), trimToNull(terms.getContactPhone()))) {
            throw new BusinessException(409, "该 RFQ 已绑定不同的采购订单快照");
        }
        Map<Long, ProcPurchaseOrderLine> existingByRfqLine = new LinkedHashMap<>();
        for (ProcPurchaseOrderLine line : existingAward.lines()) {
            if (line == null || line.getRfqLineId() == null
                    || existingByRfqLine.putIfAbsent(line.getRfqLineId(), line) != null) {
                throw new BusinessException(409, "既有采购订单行快照无效或重复");
            }
        }
        if (existingByRfqLine.size() != prepared.lines().size()) {
            throw new BusinessException(409, "既有采购订单与中标报价行集合不一致");
        }
        for (PreparedLine expected : prepared.lines()) {
            ProcRfqLine source = expected.rfqLine();
            ProcPurchaseOrderLine actual = existingByRfqLine.get(source.getId());
            if (actual == null
                    || !Objects.equals(actual.getLineNo(), source.getLineNo())
                    || !Objects.equals(actual.getMaterialId(), source.getMaterialId())
                    || !Objects.equals(actual.getMaterialCode(), source.getMaterialCode())
                    || !Objects.equals(actual.getMaterialName(), source.getMaterialName())
                    || !Objects.equals(actual.getCategoryCode(), source.getCategoryCode())
                    || !Objects.equals(actual.getUnit(), source.getUnit())
                    || !sameDecimal(actual.getQuantity(), source.getQuantity())
                    || !sameDecimal(actual.getUnitPrice(), expected.unitPrice())
                    || !sameDecimal(actual.getTotalPrice(), expected.totalPrice())
                    || !Objects.equals(actual.getDeliveryDays(), expected.deliveryDays())
                    || !Objects.equals(actual.getExpectedDeliveryDate(),
                    expected.expectedDeliveryDate())
                    || !Objects.equals(actual.getRemark(), expected.remark())) {
                throw new BusinessException(409, "既有采购订单与中标报价行快照不一致");
            }
        }
    }

    private List<ProcPurchaseOrderLine> insertAwardLines(
            Long tenantId, Long poId, List<PreparedLine> preparedLines) {
        List<ProcPurchaseOrderLine> result = new ArrayList<>(preparedLines.size());
        for (PreparedLine prepared : preparedLines) {
            ProcRfqLine source = prepared.rfqLine();
            ProcPurchaseOrderLine line = new ProcPurchaseOrderLine();
            line.setTenantId(tenantId);
            line.setPoId(poId);
            line.setLineNo(source.getLineNo());
            line.setRfqLineId(source.getId());
            line.setMaterialId(source.getMaterialId());
            line.setMaterialCode(source.getMaterialCode());
            line.setMaterialName(source.getMaterialName());
            line.setCategoryCode(source.getCategoryCode());
            line.setUnit(source.getUnit());
            line.setQuantity(source.getQuantity());
            line.setUnitPrice(prepared.unitPrice());
            line.setTotalPrice(prepared.totalPrice());
            line.setDeliveryDays(prepared.deliveryDays());
            line.setExpectedDeliveryDate(prepared.expectedDeliveryDate());
            line.setRemark(prepared.remark());
            line.setVersion(0);
            line.setDeleted(0);
            ProcAuditSupport.created(line);
            lineMapper.insert(line);
            result.add(line);
        }
        return List.copyOf(result);
    }

    private PurchaseOrderViews.Detail loadVisibleDetail(Long tenantId, Long id) {
        ProcPurchaseOrder order = accessGuard.requireVisible(orderMapper.selectOne(
                new LambdaQueryWrapper<ProcPurchaseOrder>()
                        .eq(ProcPurchaseOrder::getTenantId, tenantId)
                        .eq(ProcPurchaseOrder::getId, id)), "采购订单不存在");
        List<ProcPurchaseOrderLine> lines = loadLines(tenantId, id);
        Map<Long, BigDecimal> received = receivedTotals(tenantId, id);
        return toDetail(order, lines, received);
    }

    private ProcPurchaseOrder requireLocked(Long tenantId, Long id) {
        return accessGuard.requireVisible(orderMapper.selectForUpdate(tenantId, id),
                "采购订单不存在");
    }

    private List<ProcPurchaseOrderLine> loadLines(Long tenantId, Long poId) {
        return lineMapper.selectList(new LambdaQueryWrapper<ProcPurchaseOrderLine>()
                .eq(ProcPurchaseOrderLine::getTenantId, tenantId)
                .eq(ProcPurchaseOrderLine::getPoId, poId)
                .orderByAsc(ProcPurchaseOrderLine::getLineNo));
    }

    private Map<Long, BigDecimal> receivedTotals(Long tenantId, Long poId) {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (ReceivedTotal total : receiptLineMapper.selectConfirmedTotals(tenantId, poId)) {
            if (total != null && total.getPoLineId() != null && total.getTotalQuantity() != null) {
                result.put(total.getPoLineId(), total.getTotalQuantity());
            }
        }
        return result;
    }

    private PurchaseOrderViews.Summary toSummary(ProcPurchaseOrder order) {
        PurchaseOrderViews.Summary result = new PurchaseOrderViews.Summary();
        fillSummary(result, order);
        return result;
    }

    private PurchaseOrderViews.Detail toDetail(
            ProcPurchaseOrder order,
            List<ProcPurchaseOrderLine> lines,
            Map<Long, BigDecimal> receivedTotals) {
        PurchaseOrderViews.Detail result = new PurchaseOrderViews.Detail();
        fillSummary(result, order);
        result.setQuotationId(order.getQuotationId());
        result.setQuotationVersion(order.getQuotationVersion());
        result.setDeliveryAddress(order.getDeliveryAddress());
        result.setContactName(order.getContactName());
        result.setContactPhone(order.getContactPhone());
        result.setLines(lines.stream().map(line -> toLine(line, receivedTotals)).toList());
        return result;
    }

    private void fillSummary(PurchaseOrderViews.Summary result, ProcPurchaseOrder order) {
        result.setId(order.getId());
        result.setPoNo(order.getPoNo());
        result.setRfqId(order.getRfqId());
        result.setSupplierId(order.getSupplierId());
        result.setSupplierNameSnapshot(order.getSupplierNameSnapshot());
        result.setTitle(order.getTitle());
        result.setTotalAmount(order.getTotalAmount());
        result.setCurrencyCode(order.getCurrencyCode());
        result.setStatus(order.getStatus());
        result.setOrderTime(order.getOrderTime());
        result.setExpectedDeliveryDate(order.getExpectedDeliveryDate());
        result.setActualDeliveryDate(order.getActualDeliveryDate());
        result.setDeliveryAddressMasked(maskAddress(order.getDeliveryAddress()));
        result.setContactNameMasked(maskName(order.getContactName()));
        result.setContactPhoneMasked(maskPhone(order.getContactPhone()));
        result.setOwnerUserId(order.getOwnerUserId());
        result.setOwnerUnitId(order.getOwnerUnitId());
        result.setVersion(order.getVersion());
        result.setCreateTime(order.getCreateTime());
        result.setUpdateTime(order.getUpdateTime());
    }

    private PurchaseOrderViews.Line toLine(
            ProcPurchaseOrderLine line, Map<Long, BigDecimal> receivedTotals) {
        PurchaseOrderViews.Line result = new PurchaseOrderViews.Line();
        result.setId(line.getId());
        result.setLineNo(line.getLineNo());
        result.setRfqLineId(line.getRfqLineId());
        result.setMaterialId(line.getMaterialId());
        result.setMaterialCode(line.getMaterialCode());
        result.setMaterialName(line.getMaterialName());
        result.setCategoryCode(line.getCategoryCode());
        result.setUnit(line.getUnit());
        result.setQuantity(line.getQuantity());
        result.setUnitPrice(line.getUnitPrice());
        result.setTotalPrice(line.getTotalPrice());
        result.setDeliveryDays(line.getDeliveryDays());
        result.setExpectedDeliveryDate(line.getExpectedDeliveryDate());
        BigDecimal received = receivedTotals.getOrDefault(line.getId(), ZERO_QUANTITY);
        result.setReceivedQuantity(received.setScale(6, RoundingMode.UNNECESSARY));
        BigDecimal remaining = line.getQuantity().subtract(received);
        if (remaining.signum() < 0) {
            remaining = BigDecimal.ZERO;
        }
        result.setRemainingQuantity(remaining.setScale(6, RoundingMode.UNNECESSARY));
        result.setRemark(line.getRemark());
        result.setVersion(line.getVersion());
        return result;
    }

    private void publishCreated(ProcPurchaseOrder order, List<ProcPurchaseOrderLine> lines) {
        LocalDateTime now = LocalDateTime.now();
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("purchaseOrderId", order.getId());
        payload.put("poNo", order.getPoNo());
        payload.put("rfqId", order.getRfqId());
        payload.put("supplierId", order.getSupplierId());
        payload.put("quotationId", order.getQuotationId());
        payload.put("quotationVersion", order.getQuotationVersion());
        payload.put("totalAmount", order.getTotalAmount().toPlainString());
        payload.put("currencyCode", order.getCurrencyCode());
        payload.put("status", order.getStatus());
        payload.put("lineCount", lines.size());
        reliableMessageRelay.send(DOMAIN_BINDING,
                event(eventId, CREATED_EVENT, now, order.getTenantId(), payload),
                order.getTenantId(), eventId);
    }

    private void publishConfirmed(ProcPurchaseOrder order, LocalDateTime occurredAt) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("purchaseOrderId", order.getId());
        payload.put("poNo", order.getPoNo());
        payload.put("rfqId", order.getRfqId());
        payload.put("supplierId", order.getSupplierId());
        payload.put("status", PurchaseOrderStateMachine.CONFIRMED);
        payload.put("confirmedTime", occurredAt);
        reliableMessageRelay.send(DOMAIN_BINDING,
                event(eventId, CONFIRMED_EVENT, occurredAt, order.getTenantId(), payload),
                order.getTenantId(), eventId);
    }

    private GoodsReceiptContracts.DomainEvent event(
            String eventId, String eventType, LocalDateTime occurredAt,
            Long tenantId, Map<String, Object> payload) {
        return GoodsReceiptContracts.DomainEvent.builder()
                .eventId(eventId)
                .eventType(eventType)
                .occurredAt(occurredAt)
                .tenantId(tenantId)
                .payload(payload)
                .build();
    }

    private LambdaUpdateWrapper<ProcPurchaseOrder> versioned(
            ProcPurchaseOrder current, Integer version) {
        return new LambdaUpdateWrapper<ProcPurchaseOrder>()
                .eq(ProcPurchaseOrder::getTenantId, current.getTenantId())
                .eq(ProcPurchaseOrder::getId, current.getId())
                .eq(ProcPurchaseOrder::getVersion, version)
                .eq(ProcPurchaseOrder::getStatus, current.getStatus())
                .eq(ProcPurchaseOrder::getDeleted, 0)
                .setSql("version = version + 1");
    }

    private void audit(LambdaUpdateWrapper<ProcPurchaseOrder> update) {
        update.set(ProcPurchaseOrder::getUpdateTime, LocalDateTime.now())
                .set(ProcPurchaseOrder::getUpdateBy, operator());
    }

    private void requireVersion(ProcPurchaseOrder current, Integer version) {
        if (version == null || version < 0) {
            throw new BusinessException(400, "乐观锁版本不能为空且不能小于 0");
        }
        if (!version.equals(current.getVersion())) {
            throw new BusinessException(409, "采购订单已被其他请求修改");
        }
    }

    private BigDecimal requirePositiveDecimal(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0 || value.precision() - value.scale() > 13
                || Math.max(value.scale(), 0) > 6) {
            throw new BusinessException(409, field + "格式或范围无效");
        }
        return value.setScale(6, RoundingMode.UNNECESSARY);
    }

    private boolean sameDecimal(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    private void requireAmountFits(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0
                || value.precision() - value.scale() > 15
                || Math.max(value.scale(), 0) > 4) {
            throw new BusinessException(409, field + "超出允许范围");
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

    private String normalizeRemark(String value) {
        String normalized = trimToNull(value);
        if (normalized != null && normalized.length() > 500) {
            throw new BusinessException(409, "中标报价行备注不能超过 500 个字符");
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

    private String maskAddress(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        return normalized.length() <= 2 ? "**" : normalized.substring(0, 2) + "***";
    }

    private String maskName(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        return normalized.substring(0, 1) + "**";
    }

    private String maskPhone(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() <= 7) {
            return "***";
        }
        return normalized.substring(0, 3) + "****"
                + normalized.substring(normalized.length() - 4);
    }

    private String operator() {
        ServiceRequestIdentity identity = ServiceIdentityContext.require();
        return identity.username() == null || identity.username().isBlank()
                ? String.valueOf(identity.userId()) : identity.username();
    }

    private record PreparedAward(
            List<PreparedLine> lines,
            BigDecimal totalAmount,
            LocalDate headerExpectedDeliveryDate) {
    }

    private record PreparedLine(
            ProcRfqLine rfqLine,
            BigDecimal unitPrice,
            BigDecimal totalPrice,
            Integer deliveryDays,
            LocalDate expectedDeliveryDate,
            String remark) {
    }

    /**
     * 已存在采购订单及其明细快照。
     */
    private record ExistingAward(
            ProcPurchaseOrder order,
            List<ProcPurchaseOrderLine> lines) {
    }
}
