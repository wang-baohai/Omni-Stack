package com.omni.asset.service.impl;

import com.omni.asset.domain.AssetStateMachine;
import com.omni.asset.dto.AssetOverviewRequests;
import com.omni.asset.dto.AssetOverviewViews;
import com.omni.asset.mapper.AssetOverviewMapper;
import com.omni.asset.security.AssetDataScopeContext;
import com.omni.asset.service.AssetOverviewService;
import com.omni.common.core.result.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资产概览服务实现。
 *
 * @author Omni-Stack Team
 */
@Service
@RequiredArgsConstructor
public class AssetOverviewServiceImpl implements AssetOverviewService {

    private static final List<String> STATUSES = List.of(
            AssetStateMachine.IN_STOCK, AssetStateMachine.ALLOCATED,
            AssetStateMachine.IN_USE, AssetStateMachine.MAINTENANCE,
            AssetStateMachine.TRANSFER, AssetStateMachine.DISPOSAL_PENDING,
            AssetStateMachine.DISPOSED, AssetStateMachine.SCRAPPED);

    private final AssetOverviewMapper overviewMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public AssetOverviewViews.Summary summary() {
        AssetDataScopeContext.require();
        Map<String, Long> counts = completeCounts(overviewMapper.selectStatusCounts());
        AssetOverviewViews.Summary result = new AssetOverviewViews.Summary();
        result.setTotalCount(counts.values().stream().mapToLong(Long::longValue).sum());
        result.setInStockCount(counts.get(AssetStateMachine.IN_STOCK));
        result.setAllocatedCount(counts.get(AssetStateMachine.ALLOCATED));
        result.setInUseCount(counts.get(AssetStateMachine.IN_USE));
        result.setMaintenanceCount(counts.get(AssetStateMachine.MAINTENANCE));
        result.setTransferCount(counts.get(AssetStateMachine.TRANSFER));
        result.setDisposalPendingCount(counts.get(AssetStateMachine.DISPOSAL_PENDING));
        result.setTerminalCount(Math.addExact(counts.get(AssetStateMachine.DISPOSED),
                counts.get(AssetStateMachine.SCRAPPED)));
        List<AssetOverviewViews.CurrencyAmount> amounts = overviewMapper.selectAmountsByCurrency();
        result.setAmountsByCurrency(amounts == null ? List.of() : amounts);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<AssetOverviewViews.DistributionItem> distribution(
            AssetOverviewRequests.DistributionQuery query) {
        AssetDataScopeContext.require();
        if (query == null || query.getDimension() == null) {
            throw new BusinessException(400, "资产分布维度不能为空");
        }
        int limit = query.getLimit() == null ? 20 : query.getLimit();
        if (limit < 1 || limit > 100) {
            throw new BusinessException(400, "返回条数必须在 1 到 100 之间");
        }
        List<AssetOverviewViews.DistributionItem> rows = switch (query.getDimension()) {
            case STATUS -> overviewMapper.selectStatusDistribution(limit);
            case CATEGORY -> overviewMapper.selectCategoryDistribution(limit);
            case DEPARTMENT -> overviewMapper.selectDepartmentDistribution(limit);
            case LOCATION -> overviewMapper.selectLocationDistribution(limit);
        };
        List<AssetOverviewViews.DistributionItem> safeRows = rows == null ? List.of() : rows;
        safeRows.forEach(row -> row.setDimension(query.getDimension()));
        return safeRows;
    }

    private Map<String, Long> completeCounts(List<AssetOverviewViews.StatusCount> rows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        STATUSES.forEach(status -> counts.put(status, 0L));
        if (rows != null) {
            rows.stream().filter(row -> row != null && counts.containsKey(row.getStatus()))
                    .forEach(row -> counts.put(row.getStatus(),
                            row.getCount() == null ? 0L : row.getCount()));
        }
        return counts;
    }
}
