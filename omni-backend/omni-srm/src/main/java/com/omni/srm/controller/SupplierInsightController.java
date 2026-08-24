package com.omni.srm.controller;

import com.omni.common.core.result.R;
import com.omni.srm.dto.SrmViews;
import com.omni.common.service.datascope.ServiceDataScope;
import com.omni.srm.service.EvaluationService;
import com.omni.srm.service.RiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 供应商评估和风险设计契约端点。
 *
 * @author Omni-Stack Team
 */
@RestController
@RequestMapping("/api/srm/supplier/{supplierId}")
@RequiredArgsConstructor
public class SupplierInsightController {

    private final EvaluationService evaluationService;
    private final RiskService riskService;

    /** 查询供应商评估历史。 */
    @GetMapping("/evaluation/history")
    @PreAuthorize("hasAuthority('srm:evaluation:list')")
    @ServiceDataScope(permissionCode = "srm:evaluation:list")
    public R<List<SrmViews.EvaluationVO>> evaluationHistory(@PathVariable Long supplierId) {
        return R.ok(evaluationService.supplierHistory(supplierId));
    }

    /** 查询供应商风险聚合详情。 */
    @GetMapping("/risk")
    @PreAuthorize("hasAuthority('srm:risk:list')")
    @ServiceDataScope(permissionCode = "srm:risk:list")
    public R<SrmViews.RiskProfileVO> risk(@PathVariable Long supplierId) {
        return R.ok(riskService.profile(supplierId));
    }
}
