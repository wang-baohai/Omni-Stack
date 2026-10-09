package com.omni.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.common.core.result.BusinessException;
import com.omni.procurement.domain.GoodsReceiptStateMachine;
import com.omni.procurement.dto.GoodsReceiptViews;
import com.omni.procurement.entity.ProcGoodsReceipt;
import com.omni.procurement.entity.ProcGoodsReceiptLine;
import com.omni.procurement.entity.ProcPurchaseOrder;
import com.omni.procurement.entity.ProcPurchaseOrderLine;
import com.omni.procurement.mapper.ProcGoodsReceiptLineMapper;
import com.omni.procurement.mapper.ProcGoodsReceiptMapper;
import com.omni.procurement.mapper.ProcPurchaseOrderLineMapper;
import com.omni.procurement.mapper.ProcPurchaseOrderMapper;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.procurement.service.InternalAssetCandidateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Asset 历史补偿回扫内部服务实现。
 *
 * @author Omni-Stack Team
 */
@Service
@RequiredArgsConstructor
public class InternalAssetCandidateServiceImpl implements InternalAssetCandidateService {

    private final ProcGoodsReceiptLineMapper lineMapper;
    private final ProcGoodsReceiptMapper receiptMapper;
    private final ProcPurchaseOrderMapper orderMapper;
    private final ProcPurchaseOrderLineMapper orderLineMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<GoodsReceiptViews.AssetCandidate> list(
            Long tenantId, Long afterId, Integer size) {
        requirePositive(tenantId, "租户 ID");
        if (afterId == null || afterId < 0) {
            throw new BusinessException(400, "afterId 不能小于 0");
        }
        if (size == null || size < 1 || size > 100) {
            throw new BusinessException(400, "size 必须在 1 到 100 之间");
        }
        return runAsInternalTenant(tenantId, () -> doList(tenantId, afterId, size));
    }

    private List<GoodsReceiptViews.AssetCandidate> doList(
            Long tenantId, Long afterId, Integer size) {
        List<ProcGoodsReceiptLine> lines =
                lineMapper.selectAssetCandidateLines(tenantId, afterId, size);
        if (lines.isEmpty()) {
            return List.of();
        }
        Set<Long> receiptIds = lines.stream().map(ProcGoodsReceiptLine::getGoodsReceiptId)
                .collect(Collectors.toSet());
        Map<Long, ProcGoodsReceipt> receiptById = receiptMapper.selectList(
                new LambdaQueryWrapper<ProcGoodsReceipt>()
                        .eq(ProcGoodsReceipt::getTenantId, tenantId)
                        .in(ProcGoodsReceipt::getId, receiptIds)).stream()
                .collect(Collectors.toMap(ProcGoodsReceipt::getId, Function.identity()));
        Set<Long> poIds = receiptById.values().stream().map(ProcGoodsReceipt::getPoId)
                .collect(Collectors.toSet());
        Map<Long, ProcPurchaseOrder> orderById = orderMapper.selectList(
                new LambdaQueryWrapper<ProcPurchaseOrder>()
                        .eq(ProcPurchaseOrder::getTenantId, tenantId)
                        .in(ProcPurchaseOrder::getId, poIds)).stream()
                .collect(Collectors.toMap(ProcPurchaseOrder::getId, Function.identity()));
        Set<Long> poLineIds = lines.stream().map(ProcGoodsReceiptLine::getPoLineId)
                .collect(Collectors.toSet());
        Map<Long, ProcPurchaseOrderLine> orderLineById = orderLineMapper.selectList(
                new LambdaQueryWrapper<ProcPurchaseOrderLine>()
                        .eq(ProcPurchaseOrderLine::getTenantId, tenantId)
                        .in(ProcPurchaseOrderLine::getId, poLineIds)).stream()
                .collect(Collectors.toMap(ProcPurchaseOrderLine::getId, Function.identity()));

        return lines.stream().map(line -> toCandidate(line,
                receiptById.get(line.getGoodsReceiptId()),
                orderLineById.get(line.getPoLineId()), orderById)).toList();
    }

    private GoodsReceiptViews.AssetCandidate toCandidate(
            ProcGoodsReceiptLine line,
            ProcGoodsReceipt receipt,
            ProcPurchaseOrderLine orderLine,
            Map<Long, ProcPurchaseOrder> orderById) {
        if (receipt == null || orderLine == null) {
            throw new BusinessException(409, "历史资产候选缺少采购来源快照");
        }
        if (receipt.getOwnerUserId() == null || receipt.getOwnerUserId() <= 0
                || receipt.getOwnerUnitId() == null || receipt.getOwnerUnitId() <= 0) {
            throw new BusinessException(409, "历史资产候选缺少资产管理归属");
        }
        ProcPurchaseOrder order = orderById.get(receipt.getPoId());
        if (order == null || !order.getId().equals(orderLine.getPoId())) {
            throw new BusinessException(409, "历史资产候选采购订单关联无效");
        }
        if (!GoodsReceiptStateMachine.PASS.equals(line.getQualityStatus())
                || !Boolean.TRUE.equals(line.getAssetManaged())) {
            throw new BusinessException(409, "历史资产候选状态无效");
        }
        String eventId = resolveSourceEventId(line);
        long quantity;
        try {
            quantity = line.getReceivedQuantity().longValueExact();
        } catch (ArithmeticException exception) {
            throw new BusinessException(409, "历史资产候选数量不是有效正整数");
        }
        if (quantity <= 0) {
            throw new BusinessException(409, "历史资产候选数量不是有效正整数");
        }
        BigDecimal totalPrice = orderLine.getUnitPrice()
                .multiply(line.getReceivedQuantity()).setScale(4, RoundingMode.HALF_UP);
        GoodsReceiptViews.AssetCandidate result = new GoodsReceiptViews.AssetCandidate();
        result.setEventId(eventId);
        result.setGoodsReceiptId(receipt.getId());
        result.setGrNo(receipt.getGrNo());
        result.setPurchaseOrderId(order.getId());
        result.setPoNo(order.getPoNo());
        result.setSupplierId(order.getSupplierId());
        result.setSupplierNameSnapshot(order.getSupplierNameSnapshot());
        result.setPurchaseDate(receipt.getReceiveTime());
        result.setCurrencyCode(order.getCurrencyCode());
        result.setOwnerUserId(receipt.getOwnerUserId());
        result.setOwnerUnitId(receipt.getOwnerUnitId());
        result.setGoodsReceiptLineId(line.getId());
        result.setPurchaseOrderLineId(orderLine.getId());
        result.setMaterialId(line.getMaterialId());
        result.setMaterialCode(line.getMaterialCode());
        result.setMaterialNameSnapshot(line.getMaterialName());
        result.setCategoryCode(line.getCategoryCode());
        result.setUnit(line.getUnit());
        result.setReceivedQuantity(line.getReceivedQuantity());
        result.setQualityStatus(line.getQualityStatus());
        result.setAssetManaged(line.getAssetManaged());
        result.setAssetQuantity(quantity);
        result.setUnitPrice(orderLine.getUnitPrice());
        result.setTotalPrice(totalPrice);
        return result;
    }

    private String resolveSourceEventId(ProcGoodsReceiptLine line) {
        String confirmedEventId = trimToNull(line.getConfirmedEventId());
        String qualityPassedEventId = trimToNull(line.getQualityPassedEventId());
        if ((confirmedEventId == null) == (qualityPassedEventId == null)) {
            throw new BusinessException(409, "历史资产候选事件门闩无效");
        }
        return confirmedEventId == null ? qualityPassedEventId : confirmedEventId;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private <T> T runAsInternalTenant(Long tenantId, Supplier<T> action) {
        try {
            ServiceIdentityContext.set(new ServiceRequestIdentity(
                    0L, tenantId, "internal-asset"));
            ServiceDataScopeContext.set(new ServiceDataScopeContext.ScopeInfo(
                    0L, tenantId, "INTERNAL", null, "TENANT", Collections.emptySet(), null));
            return action.get();
        } finally {
            ServiceDataScopeContext.clear();
            ServiceIdentityContext.clear();
        }
    }

    private void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new BusinessException(400, field + "必须为正整数");
        }
    }
}
