package com.omni.srm.workflow;

/**
 * 供应商与 Workflow 启动相关的本地事务状态服务。
 *
 * @author Omni-Stack Team
 */
public interface SupplierWorkflowStateService {

    /**
     * 仅在当前幂等快照仍匹配时标记 Workflow 已启动。
     *
     * @param command           启动快照
     * @param processInstanceId 流程实例 ID
     */
    void markStarted(SupplierWorkflowCommand command, String processInstanceId);

    /**
     * 仅在当前幂等快照仍为 PENDING 时标记启动失败。
     *
     * @param command 启动快照
     */
    void markFailed(SupplierWorkflowCommand command);
}
