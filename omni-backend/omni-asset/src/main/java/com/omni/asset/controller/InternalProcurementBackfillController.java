package com.omni.asset.controller;

import com.omni.asset.dto.ProcurementAssetContracts;
import com.omni.asset.service.ProcurementAssetBackfillService;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 受内部令牌保护的 Procurement 资产候选补偿端点。
 *
 * @author Omni-Stack Team
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/asset/procurement")
public class InternalProcurementBackfillController {

    private final ProcurementAssetBackfillService backfillService;

    /**
     * 受控回扫一页历史收货资产候选。
     *
     * @param headerTenantId 租户请求头
     * @param tenantId 租户查询参数
     * @param afterId 起始收货行 ID（不含）
     * @param size 页大小
     * @return 补偿结果与下一游标
     */
    @PostMapping("/backfill")
    public R<ProcurementAssetContracts.BackfillResult> backfill(
            @RequestHeader("X-Tenant-Id") @Positive Long headerTenantId,
            @RequestParam @Positive Long tenantId,
            @RequestParam(defaultValue = "0") @Min(0) Long afterId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) Integer size) {
        if (!headerTenantId.equals(tenantId)) {
            throw new BusinessException(403, "请求头与查询参数的租户 ID 不一致");
        }
        return R.ok(backfillService.backfill(tenantId, afterId, size));
    }
}
