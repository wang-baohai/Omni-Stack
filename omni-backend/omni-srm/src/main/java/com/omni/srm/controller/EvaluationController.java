package com.omni.srm.controller;

import com.omni.common.core.operlog.OperLog;
import com.omni.common.core.operlog.OperType;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import com.omni.srm.dto.SrmRequests;
import com.omni.srm.dto.SrmViews;
import com.omni.srm.security.SrmDataScope;
import com.omni.srm.service.EvaluationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/** SRM 绩效评估控制器。 */
@RestController
@RequestMapping("/api/srm/evaluation")
@RequiredArgsConstructor
@Validated
public class EvaluationController {
    private final EvaluationService evaluationService;

    /** 分页查询评估列表。 */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('srm:evaluation:list')")
    @SrmDataScope(permissionCode = "srm:evaluation:list")
    public R<PageResult<SrmViews.EvaluationVO>> list(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return R.ok(evaluationService.list(supplierId, page, size));
    }

    /** 查询评估详情。 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('srm:evaluation:list')")
    @SrmDataScope(permissionCode = "srm:evaluation:list")
    public R<SrmViews.EvaluationVO> get(@PathVariable Long id) {
        return R.ok(evaluationService.get(id));
    }

    /** 查询供应商评估历史。 */
    @GetMapping("/supplier/{supplierId}")
    @PreAuthorize("hasAuthority('srm:evaluation:list')")
    @SrmDataScope(permissionCode = "srm:evaluation:list")
    public R<List<SrmViews.EvaluationVO>> supplierHistory(@PathVariable Long supplierId) {
        return R.ok(evaluationService.supplierHistory(supplierId));
    }

    /** 查询默认模板及评分维度。 */
    @GetMapping("/template/default/dimensions")
    @PreAuthorize("hasAuthority('srm:evaluation:list')")
    @SrmDataScope(permissionCode = "srm:evaluation:list")
    public R<SrmViews.EvaluationTemplateVO> defaultTemplate() {
        return R.ok(evaluationService.defaultTemplate());
    }

    /** 创建评估。 */
    @PostMapping
    @PreAuthorize("hasAuthority('srm:evaluation:create')")
    @SrmDataScope(permissionCode = "srm:evaluation:create")
    @OperLog(module = "SRM绩效评估", operType = OperType.CREATE, recordSnapshot = false)
    public R<SrmViews.EvaluationVO> create(@Valid @RequestBody SrmRequests.CreateEvaluationRequest request) {
        return R.ok(evaluationService.create(request));
    }
}
