package com.omni.workflow.controller;

import com.omni.common.core.result.R;
import com.omni.workflow.service.WorkflowStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 工作流统计控制器。
 * <p>提供工作台统计和管理端统计看板接口。</p>
 *
 * @author Omni-Stack Team
 */
@RestController
@RequestMapping("/api/workflow/stats")
@RequiredArgsConstructor
public class WorkflowStatsController {

    private final WorkflowStatsService workflowStatsService;

    /**
     * 工作台统计（当前用户视角）。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @return 统计数据
     */
    @GetMapping("/workspace")
    public R<Map<String, Object>> workspaceStats(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestHeader("X-User-Id") Long userId) {
        return R.ok(workflowStatsService.workspaceStats(userId, tenantId));
    }

    /**
     * 管理端统计看板。
     *
     * @param tenantId 租户 ID
     * @return 统计数据
     */
    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('workflow:stats:admin')")
    public R<Map<String, Object>> adminStats(
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return R.ok(workflowStatsService.adminStats(tenantId));
    }
}
