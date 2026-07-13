package com.omni.crm.controller;

import com.omni.common.core.result.R;
import com.omni.crm.dto.CrmViews;
import com.omni.crm.security.CrmDataScope;
import com.omni.crm.service.PipelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** CRM 销售管道只读控制器。 */
@RestController
@RequestMapping("/api/crm/pipeline")
@RequiredArgsConstructor
public class PipelineController {
    private final PipelineService pipelineService;

    /** 查询管道。 */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('crm:opportunity:list')")
    @CrmDataScope(permissionCode = "crm:opportunity:list")
    public R<List<CrmViews.PipelineVO>> list() { return R.ok(pipelineService.list()); }

    /** 查询管道阶段。 */
    @GetMapping("/{id}/stages")
    @PreAuthorize("hasAuthority('crm:opportunity:list')")
    @CrmDataScope(permissionCode = "crm:opportunity:list")
    public R<List<CrmViews.PipelineStageVO>> stages(@PathVariable Long id) {
        return R.ok(pipelineService.stages(id));
    }
}
