package com.omni.procurement.controller;

import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import com.omni.procurement.dto.GoodsReceiptViews;
import com.omni.procurement.service.InternalAssetCandidateService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 受内部令牌保护的 Asset 历史补偿回扫接口。
 *
 * @author Omni-Stack Team
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/procurement/goods-receipt")
public class InternalAssetCandidateController {

    private final InternalAssetCandidateService assetCandidateService;

    /**
     * 游标查询当前租户全部历史资产候选行。
     *
     * @param headerTenantId 租户请求头
     * @param tenantId 租户查询参数
     * @param afterId 起始收货行 ID（不含）
     * @param size 返回上限
     * @return 资产候选行
     */
    @GetMapping("/asset-candidates")
    public R<List<GoodsReceiptViews.AssetCandidate>> assetCandidates(
            @RequestHeader("X-Tenant-Id") @Positive Long headerTenantId,
            @RequestParam @Positive Long tenantId,
            @RequestParam(defaultValue = "0") @Min(0) Long afterId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) Integer size) {
        if (!headerTenantId.equals(tenantId)) {
            throw new BusinessException(403, "请求头与查询参数的租户 ID 不一致");
        }
        return R.ok(assetCandidateService.list(tenantId, afterId, size));
    }
}
