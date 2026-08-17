package com.omni.procurement.service;

import com.omni.procurement.workflow.RequisitionWorkflowCommand;

/**
 * 请购与 Workflow 启动相关的本地事务状态服务。
 *
 * @author Omni-Stack Team
 */
public interface RequisitionWorkflowStateService {

    /**
     * 创建新审批轮次并持久化 PENDING 启动快照。
     *
     * @param requisitionId 请购 ID
     * @param version 乐观锁版本
     * @return 已持久化的启动命令
     */
    RequisitionWorkflowCommand prepareSubmit(Long requisitionId, Integer version);

    /**
     * 将失败的同一审批轮次重新置为 PENDING。
     *
     * @param requisitionId 请购 ID
     * @param version 乐观锁版本
     * @return 原幂等启动命令
     */
    RequisitionWorkflowCommand prepareRetry(Long requisitionId, Integer version);

    /**
     * 仅在当前幂等快照仍匹配时标记 Workflow 已启动。
     *
     * @param command 启动快照
     * @param processInstanceId 流程实例 ID
     */
    void markStarted(RequisitionWorkflowCommand command, String processInstanceId);

    /**
     * 仅在当前幂等快照仍为 PENDING 时标记启动失败。
     *
     * @param command 启动快照
     */
    void markFailed(RequisitionWorkflowCommand command);
}
