package com.omni.workflow.service;

/**
 * 工作流待办缓存同步服务接口。
 *
 * @author Omni-Stack Team
 */
public interface WorkflowTodoSyncService {

    /**
     * 按 Flowable 当前运行态任务重建指定流程实例的待办缓存。
     *
     * @param processInstanceId 流程实例 ID
     */
    void syncProcessTodos(String processInstanceId);

    /**
     * 删除指定流程实例的待办缓存。
     *
     * @param processInstanceId 流程实例 ID
     */
    void deleteProcessTodos(String processInstanceId);
}
