package com.omni.workflow.service;

import com.omni.common.core.result.PageResult;
import com.omni.workflow.entity.WfTodoTask;

import java.util.Map;

/**
 * 工作流任务服务接口。
 * <p>
 * 提供待办任务查询、任务表单数据获取等能力。</p>
 *
 * @author Omni-Stack Team
 */
public interface WorkflowTaskService {

    /**
     * 查询"待我审批"的待办任务列表（分页）。
     *
     * @param userId   处理人用户 ID
     * @param tenantId 租户 ID
     * @param title    流程标题（模糊查询，可选）
     * @param page     页码
     * @param size     每页数量
     * @return 分页结果
     */
    PageResult<WfTodoTask> todoList(Long userId, Long tenantId, String title,
                                     int page, int size);

    /**
     * 查询当前用户的待办数量。
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @return 待办数量
     */
    long todoCount(Long userId, Long tenantId);

    /**
     * 获取任务的表单数据。
     * <p>
     * 读取流程变量中的 JSON Schema 表单数据，以及关联的业务表单数据。</p>
     *
     * @param taskId Flowable 任务 ID
     * @return 表单数据（流程变量 + 业务数据）
     */
    Map<String, Object> getTaskFormData(String taskId, Long userId, Long tenantId);
}
