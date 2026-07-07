package com.omni.workflow.service;

import java.util.Map;

/**
 * 工作流统计服务接口。
 *
 * @author Omni-Stack Team
 */
public interface WorkflowStatsService {

    /**
     * 工作台统计（当前用户视角）。
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @return 统计数据（待办数、发起数、已办数等）
     */
    Map<String, Object> workspaceStats(Long userId, Long tenantId);

    /**
     * 管理端统计看板。
     *
     * @param tenantId 租户 ID
     * @return 统计数据（流程定义数、运行中实例数、完成率等）
     */
    Map<String, Object> adminStats(Long tenantId);
}
