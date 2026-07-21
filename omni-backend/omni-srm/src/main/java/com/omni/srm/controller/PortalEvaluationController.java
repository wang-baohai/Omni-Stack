package com.omni.srm.controller;

import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import com.omni.srm.dto.SrmViews;
import com.omni.srm.security.SrmDataScopeContext;
import com.omni.srm.service.EvaluationService;
import com.omni.srm.service.SupplierPortalService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SRM 供应商门户 - 绩效评估只读接口。
 * 仅允许当前登录供应商查看自己的绩效评估记录。
 */
@RestController
@RequestMapping("/api/srm/portal/evaluations")
@RequiredArgsConstructor
public class PortalEvaluationController {

    private final SupplierPortalService supplierPortalService;
    private final EvaluationService evaluationService;

    /** 分页查询当前供应商的绩效评估列表。 */
    @GetMapping
    @PreAuthorize("hasRole('SUPPLIER') and hasAuthority('srm:portal:evaluation')")
    public R<PageResult<SrmViews.EvaluationVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long supplierId = supplierPortalService.getCurrentSupplierId();
        return R.ok(SrmDataScopeContext.runAsPortal(() -> evaluationService.list(supplierId, page, size)));
    }

    /** 查询当前供应商的评估历史（全量）。 */
    @GetMapping("/history")
    @PreAuthorize("hasRole('SUPPLIER') and hasAuthority('srm:portal:evaluation')")
    public R<List<SrmViews.EvaluationVO>> history() {
        Long supplierId = supplierPortalService.getCurrentSupplierId();
        return R.ok(SrmDataScopeContext.runAsPortal(() -> evaluationService.supplierHistory(supplierId)));
    }
}
