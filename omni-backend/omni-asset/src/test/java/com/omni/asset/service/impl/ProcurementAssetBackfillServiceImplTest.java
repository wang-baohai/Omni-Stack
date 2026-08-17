package com.omni.asset.service.impl;

import com.omni.asset.client.ProcurementInternalClient;
import com.omni.asset.dto.ProcurementAssetContracts;
import com.omni.asset.security.AssetDataScopeContext;
import com.omni.asset.security.AssetTenantContext;
import com.omni.asset.service.ProcurementAssetImportService;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Procurement 历史资产候选补偿服务测试。
 *
 * @author Omni-Stack Team
 */
@ExtendWith(MockitoExtension.class)
class ProcurementAssetBackfillServiceImplTest {

    @Mock private ProcurementInternalClient procurementClient;
    @Mock private ProcurementAssetImportService importService;

    /** 清理可能残留的租户上下文。 */
    @AfterEach
    void tearDown() {
        AssetTenantContext.clear();
        AssetDataScopeContext.clear();
    }

    /** 验证按游标导入一页并返回下一游标。 */
    @Test
    void should_import_page_and_return_next_cursor() {
        ProcurementAssetBackfillServiceImpl service = service();
        ProcurementAssetContracts.AssetCandidate first = candidate(901L);
        ProcurementAssetContracts.AssetCandidate second = candidate(902L);
        when(procurementClient.listAssetCandidates(41L, 41L, 900L, 2))
                .thenReturn(R.ok(List.of(first, second)));
        when(importService.importCandidate(41L, first)).thenAnswer(invocation -> {
            assertThat(AssetTenantContext.requireTenantId()).isEqualTo(41L);
            assertThat(AssetDataScopeContext.require().tenantId()).isEqualTo(41L);
            assertThat(AssetDataScopeContext.require().effectiveScope()).isEqualTo("TENANT");
            return importResult(2, 0);
        });
        when(importService.importCandidate(41L, second))
                .thenReturn(importResult(0, 1));

        ProcurementAssetContracts.BackfillResult result =
                service.backfill(41L, 900L, 2);

        assertThat(result.getFetchedCount()).isEqualTo(2);
        assertThat(result.getCreatedCount()).isEqualTo(2);
        assertThat(result.getDuplicateCount()).isEqualTo(1);
        assertThat(result.getNextAfterId()).isEqualTo(902L);
        assertThat(result.isHasMore()).isTrue();
        assertThatThrownBy(AssetTenantContext::requireTenantId)
                .hasMessageContaining("上下文");
        assertThatThrownBy(AssetDataScopeContext::require)
                .hasMessageContaining("上下文");
    }

    /** 验证乱序或重复游标页在写资产前失败关闭。 */
    @Test
    void should_reject_non_monotonic_candidate_page_before_import() {
        ProcurementAssetBackfillServiceImpl service = service();
        ProcurementAssetContracts.AssetCandidate first = candidate(902L);
        ProcurementAssetContracts.AssetCandidate second = candidate(901L);
        when(procurementClient.listAssetCandidates(41L, 41L, 900L, 100))
                .thenReturn(R.ok(List.of(first, second)));

        assertThatThrownBy(() -> service.backfill(41L, 900L, 100))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("游标顺序");

        verify(importService, never()).importCandidate(any(), any());
        assertThatThrownBy(AssetTenantContext::requireTenantId)
                .hasMessageContaining("上下文");
        assertThatThrownBy(AssetDataScopeContext::require)
                .hasMessageContaining("上下文");
    }

    /** 验证 Procurement 非成功响应映射为失败关闭的 503。 */
    @Test
    void should_fail_closed_when_procurement_response_is_invalid() {
        ProcurementAssetBackfillServiceImpl service = service();
        when(procurementClient.listAssetCandidates(41L, 41L, 0L, 100))
                .thenReturn(R.fail(500, "upstream failed"));

        assertThatThrownBy(() -> service.backfill(41L, 0L, 100))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("响应无效");
    }

    private ProcurementAssetBackfillServiceImpl service() {
        return new ProcurementAssetBackfillServiceImpl(
                procurementClient, importService);
    }

    private ProcurementAssetContracts.AssetCandidate candidate(Long lineId) {
        ProcurementAssetContracts.AssetCandidate candidate =
                new ProcurementAssetContracts.AssetCandidate();
        candidate.setGoodsReceiptLineId(lineId);
        return candidate;
    }

    private ProcurementAssetContracts.ImportResult importResult(
            int createdCount, int duplicateCount) {
        return ProcurementAssetContracts.ImportResult.builder()
                .createdAssetIds(List.of())
                .createdCount(createdCount)
                .duplicateCount(duplicateCount)
                .ignoredLineCount(0)
                .replayed(false)
                .build();
    }
}
