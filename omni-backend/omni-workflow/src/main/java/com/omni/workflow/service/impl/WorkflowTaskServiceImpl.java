package com.omni.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import com.omni.workflow.entity.WfTodoTask;
import com.omni.workflow.mapper.WfTodoTaskMapper;
import com.omni.workflow.service.WorkflowTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 工作流任务服务实现。
 * <p>
 * 待办列表基于 {@link WfTodoTask} 缓存表查询，表单数据通过
 * Flowable {@link TaskService} 读取流程变量。</p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowTaskServiceImpl implements WorkflowTaskService {

    private final WfTodoTaskMapper todoTaskMapper;
    private final TaskService taskService;

    /** {@inheritDoc} */
    @Override
    public PageResult<WfTodoTask> todoList(Long userId, Long tenantId, String title,
                                            int page, int size) {
        LambdaQueryWrapper<WfTodoTask> wrapper = new LambdaQueryWrapper<WfTodoTask>()
                .eq(WfTodoTask::getTenantId, tenantId)
                .eq(WfTodoTask::getAssigneeId, userId)
                .like(title != null && !title.isBlank(), WfTodoTask::getTitle, title)
                .orderByDesc(WfTodoTask::getCreateTime);

        Page<WfTodoTask> pageResult = todoTaskMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageResult<>(pageResult.getRecords(), pageResult.getTotal(),
                pageResult.getSize(), pageResult.getCurrent());
    }

    /** {@inheritDoc} */
    @Override
    public long todoCount(Long userId, Long tenantId) {
        LambdaQueryWrapper<WfTodoTask> wrapper = new LambdaQueryWrapper<WfTodoTask>()
                .eq(WfTodoTask::getTenantId, tenantId)
                .eq(WfTodoTask::getAssigneeId, userId);
        return todoTaskMapper.selectCount(wrapper);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> getTaskFormData(String taskId, Long userId, Long tenantId) {
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .taskTenantId(String.valueOf(tenantId))
                .singleResult();
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        if (!String.valueOf(userId).equals(task.getAssignee())) {
            throw new BusinessException(403, "无权查看该任务表单");
        }

        // 读取流程变量
        Map<String, Object> processVariables = taskService.getVariables(taskId);

        Map<String, Object> formData = new HashMap<>();
        formData.put("taskId", taskId);
        formData.put("taskName", task.getName());
        formData.put("processInstanceId", task.getProcessInstanceId());
        formData.put("processDefinitionId", task.getProcessDefinitionId());
        formData.put("variables", processVariables);

        return formData;
    }
}
