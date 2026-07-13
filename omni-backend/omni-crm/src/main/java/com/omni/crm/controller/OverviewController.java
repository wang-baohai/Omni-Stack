package com.omni.crm.controller;

import com.omni.common.core.result.R;
import com.omni.crm.dto.CrmViews;
import com.omni.crm.security.CrmDataScope;
import com.omni.crm.service.OverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** CRM 看板控制器。 */
@RestController
@RequestMapping("/api/crm/overview")
@RequiredArgsConstructor
public class OverviewController {
    private final OverviewService overviewService;

    /** 查询看板摘要。 */
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('crm:overview:list')")
    @CrmDataScope(permissionCode = "crm:overview:list")
    public R<CrmViews.OverviewSummaryVO> summary() { return R.ok(overviewService.summary()); }

    /** 查询销售漏斗。 */
    @GetMapping("/funnel")
    @PreAuthorize("hasAuthority('crm:overview:list')")
    @CrmDataScope(permissionCode = "crm:overview:list")
    public R<List<CrmViews.FunnelItemVO>> funnel(@RequestParam(required = false) Long pipelineId) {
        return R.ok(overviewService.funnel(pipelineId));
    }

    /** 查询今日和逾期待跟进。 */
    @GetMapping("/follow-ups")
    @PreAuthorize("hasAuthority('crm:overview:list')")
    @CrmDataScope(permissionCode = "crm:overview:list")
    public R<List<CrmViews.FollowupVO>> followups(@RequestParam(defaultValue = "50") int limit) {
        return R.ok(overviewService.followups(limit));
    }
}
