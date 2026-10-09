package com.omni.asset.service.impl;

import com.omni.asset.client.ProcurementInternalClient;
import com.omni.asset.dto.ProcurementAssetContracts;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.asset.service.ProcurementAssetBackfillService;
import com.omni.asset.service.ProcurementAssetImportService;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import com.omni.common.web.TraceIdFilter;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Procurement 历史资产候选补偿服务实现。
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcurementAssetBackfillServiceImpl implements ProcurementAssetBackfillService {

    private final ProcurementInternalClient procurementClient;
    private final ProcurementAssetImportService importService;

    /** {@inheritDoc} */
    @Override
    public ProcurementAssetContracts.BackfillResult backfill(
            Long tenantId, Long afterId, Integer size) {
        validateRequest(tenantId, afterId, size);
        try {
            ServiceIdentityContext.set(new ServiceRequestIdentity(
                    0L, tenantId, "procurement-backfill"));
            ServiceDataScopeContext.set(new ServiceDataScopeContext.ScopeInfo(
                    0L, tenantId, "asset:procurement:import",
                    null, "TENANT", Set.of(), null));
            List<ProcurementAssetContracts.AssetCandidate> candidates =
                    fetchCandidates(tenantId, afterId, size);
            validateCursorPage(candidates, afterId);
            int createdCount = 0;
            int duplicateCount = 0;
            long nextAfterId = afterId;
            for (ProcurementAssetContracts.AssetCandidate candidate : candidates) {
                ProcurementAssetContracts.ImportResult result =
                        importService.importCandidate(tenantId, candidate);
                createdCount += result.getCreatedCount();
                duplicateCount += result.getDuplicateCount();
                nextAfterId = candidate.getGoodsReceiptLineId();
            }
            return ProcurementAssetContracts.BackfillResult.builder()
                    .afterId(afterId)
                    .nextAfterId(nextAfterId)
                    .fetchedCount(candidates.size())
                    .createdCount(createdCount)
                    .duplicateCount(duplicateCount)
                    .hasMore(candidates.size() == size)
                    .build();
        } finally {
            ServiceDataScopeContext.clear();
            ServiceIdentityContext.clear();
        }
    }

    private List<ProcurementAssetContracts.AssetCandidate> fetchCandidates(
            Long tenantId, Long afterId, Integer size) {
        R<List<ProcurementAssetContracts.AssetCandidate>> response;
        try {
            response = procurementClient.listAssetCandidates(
                    tenantId, tenantId, afterId, size);
        } catch (FeignException exception) {
            log.warn("拉取 Procurement 资产候选失败: traceId={}, status={}, url={}, cause={}",
                    MDC.get(TraceIdFilter.MDC_KEY), exception.status(),
                    exception.request() == null ? "-" : exception.request().url(),
                    exception.getClass().getName(), exception);
            throw new BusinessException(503, "Procurement 历史资产候选服务暂不可用");
        }
        if (response == null || response.getCode() != 200 || response.getData() == null) {
            throw new BusinessException(503, "Procurement 历史资产候选响应无效");
        }
        return response.getData();
    }

    private void validateCursorPage(
            List<ProcurementAssetContracts.AssetCandidate> candidates, Long afterId) {
        Set<Long> seen = new HashSet<>();
        long previousId = afterId;
        for (ProcurementAssetContracts.AssetCandidate candidate : candidates) {
            Long lineId = candidate == null ? null : candidate.getGoodsReceiptLineId();
            if (lineId == null || lineId <= previousId || !seen.add(lineId)) {
                throw new BusinessException(409, "Procurement 历史资产候选游标顺序无效");
            }
            previousId = lineId;
        }
    }

    private void validateRequest(Long tenantId, Long afterId, Integer size) {
        if (tenantId == null || tenantId <= 0) {
            throw new BusinessException(400, "租户 ID 必须为正整数");
        }
        if (afterId == null || afterId < 0) {
            throw new BusinessException(400, "afterId 不能小于 0");
        }
        if (size == null || size < 1 || size > 100) {
            throw new BusinessException(400, "size 必须在 1 到 100 之间");
        }
    }
}
