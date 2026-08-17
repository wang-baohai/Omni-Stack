package com.omni.workflow.service;

import com.omni.workflow.dto.WorkflowCompletionResult;

import java.time.LocalDateTime;

/**
 * 工作流完成事件服务。
 *
 * @author Omni-Stack Team
 */
public interface WorkflowCompletionEventService {

    /**
     * 首次记录流程完成结果并写入可靠事件 Outbox。
     *
     * @param tenantId 租户 ID
     * @param processInstanceId Flowable 流程实例 ID
     * @param result 完成结果
     * @param completedTime 完成时间
     * @return {@code true} 表示本次写入并发布，{@code false} 表示该实例此前已发布
     */
    boolean publishCompletionEvent(Long tenantId, String processInstanceId,
                                   WorkflowCompletionResult result, LocalDateTime completedTime);
}
