package com.omni.srm.controller;

import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import com.omni.srm.dto.InternalSupplierBatchRequest;
import com.omni.srm.dto.InternalSupplierSummary;
import com.omni.srm.service.InternalSupplierService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 受内部令牌保护的供应商查询接口，不经 Gateway 暴露。
 *
 * @author Omni-Stack Team
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/supplier")
public class InternalSupplierController {

    private final InternalSupplierService internalSupplierService;

    /**
     * 查询单个供应商摘要。
     *
     * @param headerTenantId 请求头租户 ID
     * @param supplierId 供应商 ID
     * @param tenantId 租户 ID
     * @return 供应商摘要
     */
    @GetMapping("/{supplierId}")
    public R<InternalSupplierSummary> get(
                                          @RequestHeader("X-Tenant-Id") @Positive Long headerTenantId,
                                          @PathVariable @Positive Long supplierId,
                                          @RequestParam @Positive Long tenantId) {
        requireMatchingTenant(headerTenantId, tenantId);
        return R.ok(internalSupplierService.get(tenantId, supplierId));
    }

    /**
     * 搜索供应商摘要。
     *
     * @param headerTenantId 请求头租户 ID
     * @param tenantId 租户 ID
     * @param status 生命周期状态
     * @param categoryCode 品类编码
     * @param keyword 供应商名称或编号关键词
     * @param limit 返回数量上限
     * @return 供应商摘要列表
     */
    @GetMapping("/search")
    public R<List<InternalSupplierSummary>> search(
            @RequestHeader("X-Tenant-Id") @Positive Long headerTenantId,
            @RequestParam @Positive Long tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        requireMatchingTenant(headerTenantId, tenantId);
        if (keyword != null && !keyword.isBlank()) {
            return R.ok(internalSupplierService.searchOptions(
                    tenantId, status, categoryCode, keyword, limit));
        }
        return R.ok(internalSupplierService.search(tenantId, status, categoryCode, limit));
    }

    /**
     * 按供应商 ID 批量查询摘要。
     *
     * @param headerTenantId 请求头租户 ID
     * @param request 批量查询请求
     * @return 去重后的供应商摘要列表
     */
    @PostMapping("/batch")
    public R<List<InternalSupplierSummary>> batch(
            @RequestHeader("X-Tenant-Id") @Positive Long headerTenantId,
            @Valid @RequestBody InternalSupplierBatchRequest request) {
        requireMatchingTenant(headerTenantId, request.getTenantId());
        return R.ok(internalSupplierService.batch(request.getTenantId(), request.getSupplierIds()));
    }

    private void requireMatchingTenant(Long headerTenantId, Long payloadTenantId) {
        if (!headerTenantId.equals(payloadTenantId)) {
            throw new BusinessException(403, "请求头与请求参数的租户 ID 不一致");
        }
    }
}
