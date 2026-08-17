package com.omni.procurement.controller;

import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import com.omni.procurement.dto.GoodsReceiptViews;
import com.omni.procurement.service.InternalAssetCandidateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Asset 历史补偿内部控制器租户绑定测试。 */
@ExtendWith(MockitoExtension.class)
class InternalAssetCandidateControllerTest {

    @Mock private InternalAssetCandidateService assetCandidateService;
    @InjectMocks private InternalAssetCandidateController controller;

    /** 请求头租户与查询租户一致时才允许执行回扫。 */
    @Test
    void shouldDelegateWhenTenantBindingMatches() {
        GoodsReceiptViews.AssetCandidate candidate = new GoodsReceiptViews.AssetCandidate();
        candidate.setGoodsReceiptLineId(911L);
        when(assetCandidateService.list(41L, 900L, 20))
                .thenReturn(List.of(candidate));

        R<List<GoodsReceiptViews.AssetCandidate>> result =
                controller.assetCandidates(41L, 41L, 900L, 20);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).singleElement()
                .extracting(GoodsReceiptViews.AssetCandidate::getGoodsReceiptLineId)
                .isEqualTo(911L);
        verify(assetCandidateService).list(41L, 900L, 20);
    }

    /** 请求头与查询参数租户不一致时必须失败关闭。 */
    @Test
    void shouldRejectTenantBindingMismatch() {
        assertThatThrownBy(() -> controller.assetCandidates(41L, 42L, 0L, 100))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(403);

        verify(assetCandidateService, never()).list(42L, 0L, 100);
    }
}
