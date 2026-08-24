package com.omni.srm.controller;

import com.omni.common.core.operlog.OperLog;
import com.omni.common.core.operlog.OperType;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import com.omni.srm.dto.SrmRequests;
import com.omni.srm.dto.SrmViews;
import com.omni.common.service.datascope.ServiceDataScope;
import com.omni.srm.service.RiskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** SRM 风险指标与评估控制器。 */
@RestController
@RequestMapping("/api/srm/risk")
@RequiredArgsConstructor
@Validated
public class RiskController {
    private final RiskService riskService;

    /** 分页查询每个供应商的当前风险。 */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('srm:risk:list')")
    @ServiceDataScope(permissionCode = "srm:risk:list")
    public R<PageResult<SrmViews.RiskSupplierSummaryVO>> list(
            @RequestParam(required = false) String riskLevel,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return R.ok(riskService.listCurrentRisks(riskLevel, page, size));
    }

    /** 查询供应商风险指标列表。 */
    @GetMapping("/indicators/{supplierId}")
    @PreAuthorize("hasAuthority('srm:risk:list')")
    @ServiceDataScope(permissionCode = "srm:risk:list")
    public R<List<SrmViews.RiskIndicatorVO>> listIndicators(@PathVariable Long supplierId) {
        return R.ok(riskService.listIndicators(supplierId));
    }

    /** 更新风险指标。 */
    @PutMapping("/indicator/{id}")
    @PreAuthorize("hasAuthority('srm:risk:update')")
    @ServiceDataScope(permissionCode = "srm:risk:update")
    @OperLog(module = "SRM风险指标", operType = OperType.UPDATE, idExpr = "#id")
    public R<SrmViews.RiskIndicatorVO> updateIndicator(@PathVariable Long id,
                                                       @Valid @RequestBody SrmRequests.UpdateRiskIndicatorRequest request) {
        return R.ok(riskService.updateIndicator(id, request));
    }

    /** 创建综合风险评估。 */
    @PostMapping("/assessment/{supplierId}")
    @PreAuthorize("hasAuthority('srm:risk:assess')")
    @ServiceDataScope(permissionCode = "srm:risk:assess")
    @OperLog(module = "SRM风险评估", operType = OperType.CREATE, idExpr = "#supplierId", recordSnapshot = false)
    public R<SrmViews.RiskAssessmentVO> createAssessment(@PathVariable Long supplierId,
                                                         @Valid @RequestBody SrmRequests.CreateRiskAssessmentRequest request) {
        return R.ok(riskService.createAssessment(supplierId, request));
    }

    /** 查询供应商风险评估历史。 */
    @GetMapping("/assessment/history/{supplierId}")
    @PreAuthorize("hasAuthority('srm:risk:list')")
    @ServiceDataScope(permissionCode = "srm:risk:list")
    public R<List<SrmViews.RiskAssessmentVO>> assessmentHistory(@PathVariable Long supplierId) {
        return R.ok(riskService.assessmentHistory(supplierId));
    }
}
