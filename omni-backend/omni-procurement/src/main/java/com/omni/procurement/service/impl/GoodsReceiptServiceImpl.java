package com.omni.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import com.omni.procurement.domain.GoodsReceiptStateMachine;
import com.omni.procurement.domain.PurchaseOrderStateMachine;
import com.omni.procurement.dto.GoodsReceiptContracts;
import com.omni.procurement.dto.GoodsReceiptRequests;
import com.omni.procurement.dto.GoodsReceiptViews;
import com.omni.procurement.entity.ProcGoodsReceipt;
import com.omni.procurement.entity.ProcGoodsReceiptLine;
import com.omni.procurement.entity.ProcMaterial;
import com.omni.procurement.entity.ProcPurchaseOrder;
import com.omni.procurement.entity.ProcPurchaseOrderLine;
import com.omni.procurement.mapper.ProcGoodsReceiptLineMapper;
import com.omni.procurement.mapper.ProcGoodsReceiptMapper;
import com.omni.procurement.mapper.ProcMaterialMapper;
import com.omni.procurement.mapper.ProcPurchaseOrderLineMapper;
import com.omni.procurement.mapper.ProcPurchaseOrderMapper;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.procurement.service.GoodsReceiptService;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 收货单服务实现。
 *
 * @author Omni-Stack Team
 */
@Service
@RequiredArgsConstructor
public class GoodsReceiptServiceImpl implements GoodsReceiptService {

    private static final String DOMAIN_BINDING = "procurement-domain-out-0";
    private static final String CONFIRMED_EVENT = "procurement.goods-receipt.confirmed.v1";
    private static final String QUALITY_PASSED_EVENT =
            "procurement.goods-receipt.quality-passed.v1";
    private static final BigDecimal ZERO_QUANTITY = new BigDecimal("0.000000");

    private final ProcGoodsReceiptMapper receiptMapper;
    private final ProcGoodsReceiptLineMapper lineMapper;
    private final ProcPurchaseOrderMapper orderMapper;
    private final ProcPurchaseOrderLineMapper orderLineMapper;
    private final ProcMaterialMapper materialMapper;
    private final ReliableMessageRelay reliableMessageRelay;
    private final ProcRecordAccessGuard accessGuard;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PageResult<GoodsReceiptViews.Summary> page(GoodsReceiptRequests.Query query) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        LambdaQueryWrapper<ProcGoodsReceipt> wrapper = new LambdaQueryWrapper<ProcGoodsReceipt>()
                .eq(ProcGoodsReceipt::getTenantId, tenantId);
        String keyword = trimToNull(query.getKeyword());
        if (keyword != null) {
            List<Long> matchingPoIds = orderMapper.selectList(
                    new LambdaQueryWrapper<ProcPurchaseOrder>()
                            .eq(ProcPurchaseOrder::getTenantId, tenantId)
                            .like(ProcPurchaseOrder::getPoNo, keyword)
                            .select(ProcPurchaseOrder::getId)).stream()
                    .map(ProcPurchaseOrder::getId).toList();
            wrapper.and(nested -> {
                nested.like(ProcGoodsReceipt::getGrNo, keyword);
                if (!matchingPoIds.isEmpty()) {
                    nested.or().in(ProcGoodsReceipt::getPoId, matchingPoIds);
                }
            });
        }
        if (query.getPoId() != null) {
            wrapper.eq(ProcGoodsReceipt::getPoId, query.getPoId());
        }
        if (trimToNull(query.getStatus()) != null) {
            wrapper.eq(ProcGoodsReceipt::getStatus, query.getStatus());
        }
        if (query.getReceiveTimeFrom() != null) {
            wrapper.ge(ProcGoodsReceipt::getReceiveTime, query.getReceiveTimeFrom());
        }
        if (query.getReceiveTimeTo() != null) {
            wrapper.le(ProcGoodsReceipt::getReceiveTime, query.getReceiveTimeTo());
        }
        if (query.getReceiveTimeFrom() != null && query.getReceiveTimeTo() != null
                && query.getReceiveTimeFrom().isAfter(query.getReceiveTimeTo())) {
            throw new BusinessException(400, "收货时间范围无效");
        }
        wrapper.orderByDesc(ProcGoodsReceipt::getReceiveTime)
                .orderByDesc(ProcGoodsReceipt::getId);
        Page<ProcGoodsReceipt> page = receiptMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);
        Map<Long, String> poNumbers = loadPoNumbers(tenantId, page.getRecords().stream()
                .map(ProcGoodsReceipt::getPoId).collect(Collectors.toSet()));
        List<GoodsReceiptViews.Summary> records = page.getRecords().stream()
                .map(receipt -> toSummary(receipt, poNumbers.get(receipt.getPoId()))).toList();
        return new PageResult<>(records, page.getTotal(), page.getSize(), page.getCurrent());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public GoodsReceiptViews.Detail get(Long id) {
        return loadVisibleDetail(ServiceIdentityContext.requireTenantId(), id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public GoodsReceiptViews.Detail create(GoodsReceiptRequests.CreateRequest request) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        ServiceDataScopeContext.ScopeInfo scope = requireOwnerScope();
        if (request == null || request.getPoId() == null || request.getReceiveTime() == null) {
            throw new BusinessException(400, "采购订单和收货时间不能为空");
        }
        ProcPurchaseOrder order = accessGuard.requireVisible(orderMapper.selectOne(
                new LambdaQueryWrapper<ProcPurchaseOrder>()
                        .eq(ProcPurchaseOrder::getTenantId, tenantId)
                        .eq(ProcPurchaseOrder::getId, request.getPoId())), "采购订单不存在");
        PurchaseOrderStateMachine.requireReceivable(order.getStatus());
        List<ProcPurchaseOrderLine> orderLines = loadOrderLines(tenantId, order.getId());
        if (orderLines.isEmpty()) {
            throw new BusinessException(409, "采购订单缺少有效行快照");
        }
        Map<Long, ProcPurchaseOrderLine> orderLineById = orderLines.stream()
                .collect(Collectors.toMap(ProcPurchaseOrderLine::getId, Function.identity()));
        Map<Long, BigDecimal> confirmedTotals = confirmedTotals(tenantId, order.getId());
        List<PreparedReceiptLine> prepared = prepareCreateLines(
                tenantId, request.getLines(), orderLineById, confirmedTotals);

        ProcGoodsReceipt receipt = new ProcGoodsReceipt();
        receipt.setTenantId(tenantId);
        receipt.setGrNo("TMP-" + UUID.randomUUID());
        receipt.setPoId(order.getId());
        receipt.setReceiverUserId(scope.userId());
        receipt.setReceiveTime(request.getReceiveTime());
        receipt.setRemark(normalizeRemark(request.getRemark()));
        receipt.setStatus(GoodsReceiptStateMachine.DRAFT);
        receipt.setOwnerUserId(scope.userId());
        receipt.setOwnerUnitId(scope.primaryUnitId());
        receipt.setVersion(0);
        receipt.setDeleted(0);
        ProcAuditSupport.created(receipt);
        receiptMapper.insert(receipt);

        String grNo = "GR-" + tenantId + "-" + receipt.getId();
        int numbered = receiptMapper.update(null, new LambdaUpdateWrapper<ProcGoodsReceipt>()
                .eq(ProcGoodsReceipt::getTenantId, tenantId)
                .eq(ProcGoodsReceipt::getId, receipt.getId())
                .eq(ProcGoodsReceipt::getDeleted, 0)
                .set(ProcGoodsReceipt::getGrNo, grNo));
        accessGuard.requireAffected(numbered, "生成收货单号失败");
        receipt.setGrNo(grNo);
        List<ProcGoodsReceiptLine> lines = insertReceiptLines(
                tenantId, receipt.getId(), prepared);
        return toDetail(receipt, order.getPoNo(), lines);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public GoodsReceiptViews.Detail confirm(Long id, Integer version) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        ProcGoodsReceipt receipt = requireLocked(tenantId, id);
        GoodsReceiptStateMachine.requireConfirmable(receipt.getStatus());
        requireVersion(receipt, version);
        ProcPurchaseOrder order = accessGuard.requireVisible(
                orderMapper.selectForUpdate(tenantId, receipt.getPoId()), "采购订单不存在");
        PurchaseOrderStateMachine.requireReceivable(order.getStatus());
        List<ProcGoodsReceiptLine> receiptLines =
                lineMapper.selectForUpdateByReceipt(tenantId, receipt.getId());
        if (receiptLines.isEmpty()) {
            throw new BusinessException(409, "收货单缺少有效行");
        }
        List<ProcPurchaseOrderLine> orderLines = loadOrderLines(tenantId, order.getId());
        Map<Long, ProcPurchaseOrderLine> orderLineById = orderLines.stream()
                .collect(Collectors.toMap(ProcPurchaseOrderLine::getId, Function.identity()));
        Map<Long, BigDecimal> totals = confirmedTotals(tenantId, order.getId());
        applyAndValidateReceiptQuantities(receiptLines, orderLineById, totals);

        boolean fullyReceived = orderLines.stream().allMatch(orderLine ->
                totals.getOrDefault(orderLine.getId(), ZERO_QUANTITY)
                        .compareTo(orderLine.getQuantity()) >= 0);
        LocalDateTime now = LocalDateTime.now();
        String eventId = UUID.randomUUID().toString();
        List<ProcGoodsReceiptLine> assetLines = receiptLines.stream()
                .filter(this::isAssetCandidate).toList();
        markConfirmed(receipt, version, eventId, now);
        markConfirmedAssetLines(tenantId, assetLines, eventId, now);
        LocalDate actualDeliveryDate = resolveActualDeliveryDate(
                tenantId, order.getId(), fullyReceived);
        updateOrderReceiptStatus(order, fullyReceived, actualDeliveryDate, now);
        publishReceiptEvent(
                new ReceiptEventContext(CONFIRMED_EVENT, eventId, now, receipt, order),
                assetLines, orderLineById);
        return loadVisibleDetail(tenantId, id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public GoodsReceiptViews.Detail updateQualityResult(
            Long id, GoodsReceiptRequests.QualityResultCommand command) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        if (command == null) {
            throw new BusinessException(400, "质检结果命令不能为空");
        }
        ProcGoodsReceipt receipt = requireLocked(tenantId, id);
        GoodsReceiptStateMachine.requireQualityUpdatable(receipt.getStatus());
        requireVersion(receipt, command.getVersion());
        List<ProcGoodsReceiptLine> currentLines =
                lineMapper.selectForUpdateByReceipt(tenantId, receipt.getId());
        Map<Long, ProcGoodsReceiptLine> lineById = currentLines.stream()
                .collect(Collectors.toMap(ProcGoodsReceiptLine::getId, Function.identity()));
        List<QualityChange> changes = prepareQualityChanges(command.getLines(), lineById);
        String eventId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        List<ProcGoodsReceiptLine> passedAssetLines = new ArrayList<>();
        for (QualityChange change : changes) {
            boolean assetPassed = GoodsReceiptStateMachine.PASS.equals(change.targetStatus())
                    && isAssetManagedInteger(change.line());
            updateQualityLine(tenantId, change, assetPassed ? eventId : null, now);
            change.line().setQualityStatus(change.targetStatus());
            change.line().setQualityResultTime(now);
            if (assetPassed) {
                change.line().setQualityPassedEventId(eventId);
                passedAssetLines.add(change.line());
            }
        }
        touchReceipt(receipt, command.getVersion(), now);
        if (!passedAssetLines.isEmpty()) {
            ProcPurchaseOrder order = accessGuard.requireVisible(orderMapper.selectOne(
                    new LambdaQueryWrapper<ProcPurchaseOrder>()
                            .eq(ProcPurchaseOrder::getTenantId, tenantId)
                            .eq(ProcPurchaseOrder::getId, receipt.getPoId())), "采购订单不存在");
            Map<Long, ProcPurchaseOrderLine> orderLineById = loadOrderLines(tenantId, order.getId())
                    .stream().collect(Collectors.toMap(
                            ProcPurchaseOrderLine::getId, Function.identity()));
            publishReceiptEvent(
                    new ReceiptEventContext(QUALITY_PASSED_EVENT, eventId, now, receipt, order),
                    passedAssetLines, orderLineById);
        }
        return loadVisibleDetail(tenantId, id);
    }

    private List<PreparedReceiptLine> prepareCreateLines(
            Long tenantId,
            List<GoodsReceiptRequests.LineInput> inputs,
            Map<Long, ProcPurchaseOrderLine> orderLineById,
            Map<Long, BigDecimal> confirmedTotals) {
        if (inputs == null || inputs.isEmpty()) {
            throw new BusinessException(400, "收货行不能为空");
        }
        LinkedHashSet<Long> requestedIds = new LinkedHashSet<>();
        for (GoodsReceiptRequests.LineInput input : inputs) {
            if (input == null || input.getPoLineId() == null
                    || !requestedIds.add(input.getPoLineId())) {
                throw new BusinessException(400, "采购订单行不能为空且不能重复");
            }
        }
        Set<Long> materialIds = requestedIds.stream()
                .map(orderLineById::get)
                .filter(java.util.Objects::nonNull)
                .map(ProcPurchaseOrderLine::getMaterialId)
                .collect(Collectors.toSet());
        Map<Long, ProcMaterial> materialById = materialIds.isEmpty() ? Map.of()
                : materialMapper.selectList(new LambdaQueryWrapper<ProcMaterial>()
                        .eq(ProcMaterial::getTenantId, tenantId)
                        .in(ProcMaterial::getId, materialIds)).stream()
                .collect(Collectors.toMap(ProcMaterial::getId, Function.identity()));

        List<PreparedReceiptLine> result = new ArrayList<>(inputs.size());
        for (GoodsReceiptRequests.LineInput input : inputs) {
            ProcPurchaseOrderLine orderLine = orderLineById.get(input.getPoLineId());
            if (orderLine == null) {
                throw new BusinessException(409, "收货行不属于当前采购订单");
            }
            ProcMaterial material = materialById.get(orderLine.getMaterialId());
            if (material == null) {
                throw new BusinessException(409, "采购订单行对应物料的资产属性不可用");
            }
            BigDecimal quantity = normalizeQuantity(input.getReceivedQuantity());
            if (!GoodsReceiptStateMachine.PASS.equals(input.getQualityStatus())
                    && !GoodsReceiptStateMachine.FAIL.equals(input.getQualityStatus())
                    && !GoodsReceiptStateMachine.PENDING.equals(input.getQualityStatus())) {
                throw new BusinessException(400, "质检状态无效");
            }
            BigDecimal alreadyReceived = confirmedTotals.getOrDefault(
                    orderLine.getId(), ZERO_QUANTITY);
            if (alreadyReceived.add(quantity).compareTo(orderLine.getQuantity()) > 0) {
                throw new BusinessException(409, "收货数量超过订单剩余可收数量");
            }
            result.add(new PreparedReceiptLine(orderLine, quantity,
                    Boolean.TRUE.equals(material.getAssetManaged()), input.getQualityStatus(),
                    normalizeRemark(input.getRemark())));
        }
        return List.copyOf(result);
    }

    private List<ProcGoodsReceiptLine> insertReceiptLines(
            Long tenantId, Long receiptId, List<PreparedReceiptLine> preparedLines) {
        List<ProcGoodsReceiptLine> result = new ArrayList<>(preparedLines.size());
        LocalDateTime now = LocalDateTime.now();
        int lineNo = 1;
        for (PreparedReceiptLine prepared : preparedLines) {
            ProcPurchaseOrderLine source = prepared.orderLine();
            ProcGoodsReceiptLine line = new ProcGoodsReceiptLine();
            line.setTenantId(tenantId);
            line.setGoodsReceiptId(receiptId);
            line.setLineNo(lineNo++);
            line.setPoLineId(source.getId());
            line.setMaterialId(source.getMaterialId());
            line.setMaterialCode(source.getMaterialCode());
            line.setMaterialName(source.getMaterialName());
            line.setCategoryCode(source.getCategoryCode());
            line.setUnit(source.getUnit());
            line.setAssetManaged(prepared.assetManaged());
            line.setOrderedQuantity(source.getQuantity());
            line.setReceivedQuantity(prepared.receivedQuantity());
            line.setQualityStatus(prepared.qualityStatus());
            if (!GoodsReceiptStateMachine.PENDING.equals(prepared.qualityStatus())) {
                line.setQualityResultTime(now);
            }
            line.setRemark(prepared.remark());
            line.setVersion(0);
            line.setDeleted(0);
            ProcAuditSupport.created(line);
            lineMapper.insert(line);
            result.add(line);
        }
        return List.copyOf(result);
    }

    private void applyAndValidateReceiptQuantities(
            List<ProcGoodsReceiptLine> receiptLines,
            Map<Long, ProcPurchaseOrderLine> orderLineById,
            Map<Long, BigDecimal> totals) {
        for (ProcGoodsReceiptLine receiptLine : receiptLines) {
            ProcPurchaseOrderLine orderLine = orderLineById.get(receiptLine.getPoLineId());
            if (orderLine == null || !orderLine.getMaterialId().equals(receiptLine.getMaterialId())
                    || orderLine.getQuantity().compareTo(receiptLine.getOrderedQuantity()) != 0) {
                throw new BusinessException(409, "收货行与采购订单快照不一致");
            }
            BigDecimal cumulative = totals.getOrDefault(orderLine.getId(), ZERO_QUANTITY)
                    .add(receiptLine.getReceivedQuantity());
            if (cumulative.compareTo(orderLine.getQuantity()) > 0) {
                throw new BusinessException(409, "累计收货数量超过采购订单数量");
            }
            totals.put(orderLine.getId(), cumulative);
        }
    }

    private void markConfirmed(
            ProcGoodsReceipt receipt, Integer version, String eventId, LocalDateTime now) {
        LambdaUpdateWrapper<ProcGoodsReceipt> update = versioned(receipt, version)
                .set(ProcGoodsReceipt::getStatus, GoodsReceiptStateMachine.CONFIRMED)
                .set(ProcGoodsReceipt::getConfirmedTime, now)
                .set(ProcGoodsReceipt::getConfirmedEventId, eventId);
        audit(update, now);
        accessGuard.requireAffected(receiptMapper.update(null, update),
                "收货单已被其他请求修改");
    }

    private void markConfirmedAssetLines(
            Long tenantId, List<ProcGoodsReceiptLine> assetLines,
            String eventId, LocalDateTime now) {
        if (assetLines.isEmpty()) {
            return;
        }
        int affected = lineMapper.update(null,
                new LambdaUpdateWrapper<ProcGoodsReceiptLine>()
                        .eq(ProcGoodsReceiptLine::getTenantId, tenantId)
                        .in(ProcGoodsReceiptLine::getId,
                                assetLines.stream().map(ProcGoodsReceiptLine::getId).toList())
                        .eq(ProcGoodsReceiptLine::getDeleted, 0)
                        .isNull(ProcGoodsReceiptLine::getConfirmedEventId)
                        .isNull(ProcGoodsReceiptLine::getQualityPassedEventId)
                        .set(ProcGoodsReceiptLine::getConfirmedEventId, eventId)
                        .set(ProcGoodsReceiptLine::getUpdateTime, now)
                        .set(ProcGoodsReceiptLine::getUpdateBy, operator())
                        .setSql("version = version + 1"));
        if (affected != assetLines.size()) {
            throw new BusinessException(409, "收货资产候选行已被其他请求修改");
        }
    }

    private void updateOrderReceiptStatus(
            ProcPurchaseOrder order, boolean fullyReceived,
            LocalDate actualDeliveryDate, LocalDateTime now) {
        String targetStatus = PurchaseOrderStateMachine
                .receiptProgressStatus(fullyReceived);
        LambdaUpdateWrapper<ProcPurchaseOrder> update =
                new LambdaUpdateWrapper<ProcPurchaseOrder>()
                        .eq(ProcPurchaseOrder::getTenantId, order.getTenantId())
                        .eq(ProcPurchaseOrder::getId, order.getId())
                        .eq(ProcPurchaseOrder::getVersion, order.getVersion())
                        .eq(ProcPurchaseOrder::getStatus, order.getStatus())
                        .eq(ProcPurchaseOrder::getDeleted, 0)
                        .set(ProcPurchaseOrder::getStatus, targetStatus)
                        .set(ProcPurchaseOrder::getActualDeliveryDate,
                                actualDeliveryDate)
                        .set(ProcPurchaseOrder::getUpdateTime, now)
                        .set(ProcPurchaseOrder::getUpdateBy, operator())
                        .setSql("version = version + 1");
        accessGuard.requireAffected(orderMapper.update(null, update),
                "采购订单收货状态已被其他请求修改");
    }

    private LocalDate resolveActualDeliveryDate(
            Long tenantId, Long poId, boolean fullyReceived) {
        if (!fullyReceived) {
            return null;
        }
        LocalDateTime latestReceiveTime = receiptMapper
                .selectMaxConfirmedReceiveTime(tenantId, poId);
        if (latestReceiveTime == null) {
            throw new BusinessException(409, "采购订单缺少已确认收货时间");
        }
        return latestReceiveTime.toLocalDate();
    }

    private List<QualityChange> prepareQualityChanges(
            List<GoodsReceiptRequests.QualityResultLine> requested,
            Map<Long, ProcGoodsReceiptLine> lineById) {
        if (requested == null || requested.isEmpty()) {
            throw new BusinessException(400, "质检结果行不能为空");
        }
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        List<QualityChange> result = new ArrayList<>(requested.size());
        for (GoodsReceiptRequests.QualityResultLine item : requested) {
            if (item == null || item.getGoodsReceiptLineId() == null
                    || !seen.add(item.getGoodsReceiptLineId())) {
                throw new BusinessException(400, "质检结果行不能为空且不能重复");
            }
            ProcGoodsReceiptLine line = lineById.get(item.getGoodsReceiptLineId());
            if (line == null) {
                throw new BusinessException(404, "收货质检行不存在");
            }
            GoodsReceiptStateMachine.requirePendingQuality(line.getQualityStatus());
            if (!GoodsReceiptStateMachine.PASS.equals(item.getQualityStatus())
                    && !GoodsReceiptStateMachine.FAIL.equals(item.getQualityStatus())) {
                throw new BusinessException(400, "后续质检结果必须为 PASS 或 FAIL");
            }
            result.add(new QualityChange(line, item.getQualityStatus()));
        }
        return List.copyOf(result);
    }

    private void updateQualityLine(
            Long tenantId, QualityChange change, String assetEventId, LocalDateTime now) {
        LambdaUpdateWrapper<ProcGoodsReceiptLine> update =
                new LambdaUpdateWrapper<ProcGoodsReceiptLine>()
                        .eq(ProcGoodsReceiptLine::getTenantId, tenantId)
                        .eq(ProcGoodsReceiptLine::getId, change.line().getId())
                        .eq(ProcGoodsReceiptLine::getVersion, change.line().getVersion())
                        .eq(ProcGoodsReceiptLine::getQualityStatus,
                                GoodsReceiptStateMachine.PENDING)
                        .eq(ProcGoodsReceiptLine::getDeleted, 0)
                        .set(ProcGoodsReceiptLine::getQualityStatus, change.targetStatus())
                        .set(ProcGoodsReceiptLine::getQualityResultTime, now)
                        .set(ProcGoodsReceiptLine::getUpdateTime, now)
                        .set(ProcGoodsReceiptLine::getUpdateBy, operator())
                        .setSql("version = version + 1");
        if (assetEventId != null) {
            update.isNull(ProcGoodsReceiptLine::getConfirmedEventId)
                    .isNull(ProcGoodsReceiptLine::getQualityPassedEventId)
                    .set(ProcGoodsReceiptLine::getQualityPassedEventId, assetEventId);
        }
        accessGuard.requireAffected(lineMapper.update(null, update),
                "收货质检行已被其他请求修改");
    }

    private void touchReceipt(ProcGoodsReceipt receipt, Integer version, LocalDateTime now) {
        LambdaUpdateWrapper<ProcGoodsReceipt> update = versioned(receipt, version);
        audit(update, now);
        accessGuard.requireAffected(receiptMapper.update(null, update),
                "收货单已被其他请求修改");
    }

    private void publishReceiptEvent(
            ReceiptEventContext context,
            List<ProcGoodsReceiptLine> assetLines,
            Map<Long, ProcPurchaseOrderLine> orderLineById) {
        ProcGoodsReceipt receipt = context.receipt();
        ProcPurchaseOrder order = context.order();
        if (receipt.getOwnerUserId() == null || receipt.getOwnerUserId() <= 0
                || receipt.getOwnerUnitId() == null || receipt.getOwnerUnitId() <= 0) {
            throw new BusinessException(409, "资产候选缺少资产管理归属");
        }
        List<Map<String, Object>> linePayloads = assetLines.stream()
                .map(line -> assetLinePayload(line, orderLineById.get(line.getPoLineId())))
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("goodsReceiptId", receipt.getId());
        payload.put("grNo", receipt.getGrNo());
        payload.put("purchaseOrderId", order.getId());
        payload.put("poNo", order.getPoNo());
        payload.put("supplierId", order.getSupplierId());
        payload.put("supplierNameSnapshot", order.getSupplierNameSnapshot());
        payload.put("purchaseDate", receipt.getReceiveTime());
        payload.put("currencyCode", order.getCurrencyCode());
        payload.put("ownerUserId", receipt.getOwnerUserId());
        payload.put("ownerUnitId", receipt.getOwnerUnitId());
        payload.put("lines", linePayloads);
        GoodsReceiptContracts.DomainEvent event = GoodsReceiptContracts.DomainEvent.builder()
                .eventId(context.eventId())
                .eventType(context.eventType())
                .occurredAt(context.occurredAt())
                .tenantId(receipt.getTenantId())
                .payload(payload)
                .build();
        reliableMessageRelay.send(
                DOMAIN_BINDING, event, receipt.getTenantId(), context.eventId());
    }

    private Map<String, Object> assetLinePayload(
            ProcGoodsReceiptLine receiptLine, ProcPurchaseOrderLine orderLine) {
        if (orderLine == null) {
            throw new BusinessException(409, "资产候选缺少采购订单行快照");
        }
        long assetQuantity = assetQuantity(receiptLine.getReceivedQuantity());
        BigDecimal totalPrice = orderLine.getUnitPrice()
                .multiply(receiptLine.getReceivedQuantity())
                .setScale(4, RoundingMode.HALF_UP);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("goodsReceiptLineId", receiptLine.getId());
        payload.put("purchaseOrderLineId", orderLine.getId());
        payload.put("materialId", receiptLine.getMaterialId());
        payload.put("materialCode", receiptLine.getMaterialCode());
        payload.put("materialNameSnapshot", receiptLine.getMaterialName());
        payload.put("categoryCode", receiptLine.getCategoryCode());
        payload.put("unit", receiptLine.getUnit());
        payload.put("receivedQuantity", receiptLine.getReceivedQuantity().toPlainString());
        payload.put("qualityStatus", GoodsReceiptStateMachine.PASS);
        payload.put("assetManaged", true);
        payload.put("assetQuantity", assetQuantity);
        payload.put("unitPrice", orderLine.getUnitPrice().toPlainString());
        payload.put("totalPrice", totalPrice.toPlainString());
        return payload;
    }

    private boolean isAssetCandidate(ProcGoodsReceiptLine line) {
        return line != null
                && GoodsReceiptStateMachine.PASS.equals(line.getQualityStatus())
                && isAssetManagedInteger(line);
    }

    private boolean isAssetManagedInteger(ProcGoodsReceiptLine line) {
        return line != null
                && Boolean.TRUE.equals(line.getAssetManaged())
                && isPositiveInteger(line.getReceivedQuantity());
    }

    private boolean isPositiveInteger(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0
                || quantity.stripTrailingZeros().scale() > 0) {
            return false;
        }
        try {
            return quantity.longValueExact() > 0;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private long assetQuantity(BigDecimal quantity) {
        if (!isPositiveInteger(quantity)) {
            throw new BusinessException(409, "资产候选数量必须为正整数");
        }
        return quantity.longValueExact();
    }

    private GoodsReceiptViews.Detail loadVisibleDetail(Long tenantId, Long id) {
        ProcGoodsReceipt receipt = accessGuard.requireVisible(receiptMapper.selectOne(
                new LambdaQueryWrapper<ProcGoodsReceipt>()
                        .eq(ProcGoodsReceipt::getTenantId, tenantId)
                        .eq(ProcGoodsReceipt::getId, id)), "收货单不存在");
        ProcPurchaseOrder order = accessGuard.requireVisible(orderMapper.selectOne(
                new LambdaQueryWrapper<ProcPurchaseOrder>()
                        .eq(ProcPurchaseOrder::getTenantId, tenantId)
                        .eq(ProcPurchaseOrder::getId, receipt.getPoId())), "采购订单不存在");
        List<ProcGoodsReceiptLine> lines = lineMapper.selectList(
                new LambdaQueryWrapper<ProcGoodsReceiptLine>()
                        .eq(ProcGoodsReceiptLine::getTenantId, tenantId)
                        .eq(ProcGoodsReceiptLine::getGoodsReceiptId, id)
                        .orderByAsc(ProcGoodsReceiptLine::getLineNo));
        return toDetail(receipt, order.getPoNo(), lines);
    }

    private ProcGoodsReceipt requireLocked(Long tenantId, Long id) {
        return accessGuard.requireVisible(receiptMapper.selectForUpdate(tenantId, id),
                "收货单不存在");
    }

    private List<ProcPurchaseOrderLine> loadOrderLines(Long tenantId, Long poId) {
        return orderLineMapper.selectList(new LambdaQueryWrapper<ProcPurchaseOrderLine>()
                .eq(ProcPurchaseOrderLine::getTenantId, tenantId)
                .eq(ProcPurchaseOrderLine::getPoId, poId)
                .orderByAsc(ProcPurchaseOrderLine::getLineNo));
    }

    private Map<Long, BigDecimal> confirmedTotals(Long tenantId, Long poId) {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (GoodsReceiptContracts.ReceivedTotal total
                : lineMapper.selectConfirmedTotals(tenantId, poId)) {
            if (total != null && total.getPoLineId() != null && total.getTotalQuantity() != null) {
                result.put(total.getPoLineId(), total.getTotalQuantity());
            }
        }
        return result;
    }

    private Map<Long, String> loadPoNumbers(Long tenantId, Set<Long> poIds) {
        if (poIds == null || poIds.isEmpty()) {
            return Map.of();
        }
        return orderMapper.selectList(new LambdaQueryWrapper<ProcPurchaseOrder>()
                        .eq(ProcPurchaseOrder::getTenantId, tenantId)
                        .in(ProcPurchaseOrder::getId, poIds)
                        .select(ProcPurchaseOrder::getId, ProcPurchaseOrder::getPoNo)).stream()
                .collect(Collectors.toMap(ProcPurchaseOrder::getId,
                        ProcPurchaseOrder::getPoNo));
    }

    private GoodsReceiptViews.Summary toSummary(ProcGoodsReceipt receipt, String poNo) {
        GoodsReceiptViews.Summary result = new GoodsReceiptViews.Summary();
        fillSummary(result, receipt, poNo);
        return result;
    }

    private GoodsReceiptViews.Detail toDetail(
            ProcGoodsReceipt receipt, String poNo, List<ProcGoodsReceiptLine> lines) {
        GoodsReceiptViews.Detail result = new GoodsReceiptViews.Detail();
        fillSummary(result, receipt, poNo);
        result.setLines(lines.stream().map(this::toLine).toList());
        return result;
    }

    private void fillSummary(
            GoodsReceiptViews.Summary result, ProcGoodsReceipt receipt, String poNo) {
        result.setId(receipt.getId());
        result.setGrNo(receipt.getGrNo());
        result.setPoId(receipt.getPoId());
        result.setPoNo(poNo);
        result.setReceiverUserId(receipt.getReceiverUserId());
        result.setReceiveTime(receipt.getReceiveTime());
        result.setRemark(receipt.getRemark());
        result.setStatus(receipt.getStatus());
        result.setConfirmedTime(receipt.getConfirmedTime());
        result.setOwnerUserId(receipt.getOwnerUserId());
        result.setOwnerUnitId(receipt.getOwnerUnitId());
        result.setVersion(receipt.getVersion());
        result.setCreateTime(receipt.getCreateTime());
        result.setUpdateTime(receipt.getUpdateTime());
    }

    private GoodsReceiptViews.Line toLine(ProcGoodsReceiptLine line) {
        GoodsReceiptViews.Line result = new GoodsReceiptViews.Line();
        result.setId(line.getId());
        result.setLineNo(line.getLineNo());
        result.setPoLineId(line.getPoLineId());
        result.setMaterialId(line.getMaterialId());
        result.setMaterialCode(line.getMaterialCode());
        result.setMaterialName(line.getMaterialName());
        result.setCategoryCode(line.getCategoryCode());
        result.setUnit(line.getUnit());
        result.setAssetManaged(line.getAssetManaged());
        result.setOrderedQuantity(line.getOrderedQuantity());
        result.setReceivedQuantity(line.getReceivedQuantity());
        result.setQualityStatus(line.getQualityStatus());
        result.setQualityResultTime(line.getQualityResultTime());
        result.setRemark(line.getRemark());
        result.setVersion(line.getVersion());
        return result;
    }

    private LambdaUpdateWrapper<ProcGoodsReceipt> versioned(
            ProcGoodsReceipt current, Integer version) {
        return new LambdaUpdateWrapper<ProcGoodsReceipt>()
                .eq(ProcGoodsReceipt::getTenantId, current.getTenantId())
                .eq(ProcGoodsReceipt::getId, current.getId())
                .eq(ProcGoodsReceipt::getVersion, version)
                .eq(ProcGoodsReceipt::getStatus, current.getStatus())
                .eq(ProcGoodsReceipt::getDeleted, 0)
                .setSql("version = version + 1");
    }

    private void audit(LambdaUpdateWrapper<ProcGoodsReceipt> update, LocalDateTime now) {
        update.set(ProcGoodsReceipt::getUpdateTime, now)
                .set(ProcGoodsReceipt::getUpdateBy, operator());
    }

    private void requireVersion(ProcGoodsReceipt current, Integer version) {
        if (version == null || version < 0) {
            throw new BusinessException(400, "乐观锁版本不能为空且不能小于 0");
        }
        if (!version.equals(current.getVersion())) {
            throw new BusinessException(409, "收货单已被其他请求修改");
        }
    }

    private BigDecimal normalizeQuantity(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.precision() - value.scale() > 13
                || Math.max(value.scale(), 0) > 6) {
            throw new BusinessException(400, "收货数量格式或范围无效");
        }
        return value.setScale(6, RoundingMode.UNNECESSARY);
    }

    private String normalizeRemark(String value) {
        String normalized = trimToNull(value);
        if (normalized != null && normalized.length() > 500) {
            throw new BusinessException(400, "备注不能超过 500 个字符");
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

    private ServiceDataScopeContext.ScopeInfo requireOwnerScope() {
        ServiceDataScopeContext.ScopeInfo scope = ServiceDataScopeContext.require();
        if (scope.userId() == null || scope.userId() <= 0
                || scope.primaryUnitId() == null || scope.primaryUnitId() <= 0) {
            throw new BusinessException(403, "当前用户缺少有效的负责人或主组织上下文");
        }
        return scope;
    }

    private String operator() {
        ServiceRequestIdentity identity = ServiceIdentityContext.require();
        return identity.username() == null || identity.username().isBlank()
                ? String.valueOf(identity.userId()) : identity.username();
    }

    private record PreparedReceiptLine(
            ProcPurchaseOrderLine orderLine,
            BigDecimal receivedQuantity,
            Boolean assetManaged,
            String qualityStatus,
            String remark) {
    }

    private record QualityChange(ProcGoodsReceiptLine line, String targetStatus) {
    }

    /**
     * 采购收货资产事件上下文。
     */
    private record ReceiptEventContext(
            String eventType,
            String eventId,
            LocalDateTime occurredAt,
            ProcGoodsReceipt receipt,
            ProcPurchaseOrder order) {
    }
}
