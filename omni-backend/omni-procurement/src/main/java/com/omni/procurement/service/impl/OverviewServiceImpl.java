package com.omni.procurement.service.impl;

import com.omni.common.core.result.BusinessException;
import com.omni.procurement.domain.PurchaseOrderStateMachine;
import com.omni.procurement.dto.OverviewRequests;
import com.omni.procurement.dto.OverviewViews;
import com.omni.procurement.mapper.ProcOverviewMapper;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.procurement.service.OverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购概览服务实现。
 *
 * @author Omni-Stack Team
 */
@Service
@RequiredArgsConstructor
public class OverviewServiceImpl implements OverviewService {

    private static final List<String> PURCHASE_ORDER_STATUSES = List.of(
            PurchaseOrderStateMachine.DRAFT,
            PurchaseOrderStateMachine.SENT,
            PurchaseOrderStateMachine.CONFIRMED,
            PurchaseOrderStateMachine.PARTIAL_RECEIVED,
            PurchaseOrderStateMachine.RECEIVED,
            PurchaseOrderStateMachine.CLOSED,
            PurchaseOrderStateMachine.CANCELLED);

    private final ProcOverviewMapper overviewMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public OverviewViews.Summary summary() {
        ServiceDataScopeContext.require();
        OverviewViews.Summary summary = new OverviewViews.Summary();
        summary.setPendingApprovalRequisitionCount(
                safeCount(overviewMapper.countPendingApprovalRequisitions()));
        summary.setWaitingQuotationRfqCount(
                safeCount(overviewMapper.countWaitingQuotationRfqs()));
        summary.setPurchaseOrderStatusCounts(completePurchaseOrderStatuses(
                overviewMapper.selectPurchaseOrderStatusCounts()));
        summary.setDraftGoodsReceiptCount(safeCount(overviewMapper.countDraftGoodsReceipts()));
        List<OverviewViews.CurrencyAmount> amounts = overviewMapper.selectCommittedAmountsByCurrency();
        summary.setCommittedAmountsByCurrency(amounts == null ? List.of() : amounts);
        return summary;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<OverviewViews.SpendItem> spendAnalysis(
            OverviewRequests.SpendAnalysisQuery query) {
        ServiceDataScopeContext.require();
        if (query == null || query.getDimension() == null) {
            throw new BusinessException(400, "支出分析维度不能为空");
        }
        int limit = query.getLimit() == null ? 20 : query.getLimit();
        if (limit < 1 || limit > 100) {
            throw new BusinessException(400, "返回条数必须在 1 到 100 之间");
        }
        List<OverviewViews.SpendItem> items = switch (query.getDimension()) {
            case CATEGORY -> overviewMapper.selectCategorySpend(limit);
            case SUPPLIER -> overviewMapper.selectSupplierSpend(limit);
            case DEPARTMENT -> overviewMapper.selectDepartmentSpend(limit);
        };
        List<OverviewViews.SpendItem> safeItems = items == null ? List.of() : items;
        safeItems.forEach(item -> item.setDimension(query.getDimension()));
        return safeItems;
    }

    private List<OverviewViews.StatusCount> completePurchaseOrderStatuses(
            List<OverviewViews.StatusCount> rows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        PURCHASE_ORDER_STATUSES.forEach(status -> counts.put(status, 0L));
        if (rows != null) {
            rows.stream()
                    .filter(row -> row != null && counts.containsKey(row.getStatus()))
                    .forEach(row -> counts.put(row.getStatus(), safeCount(row.getCount())));
        }
        return counts.entrySet().stream().map(entry -> {
            OverviewViews.StatusCount value = new OverviewViews.StatusCount();
            value.setStatus(entry.getKey());
            value.setCount(entry.getValue());
            return value;
        }).toList();
    }

    private long safeCount(Long count) {
        return count == null ? 0L : count;
    }
}
