package com.omni.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.workflow.entity.WfProcessInstanceExt;
import com.omni.workflow.entity.WfTodoTask;
import com.omni.workflow.mapper.WfProcessInstanceExtMapper;
import com.omni.workflow.mapper.WfTodoTaskMapper;
import com.omni.workflow.service.WorkflowTodoSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 工作流待办缓存同步服务实现。
 * <p>
 * 待办表是查询缓存，真实状态以 Flowable 当前运行任务为准。同步时先删除同一流程实例旧缓存，再写入当前任务。
 * </p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowTodoSyncServiceImpl implements WorkflowTodoSyncService {

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final WfTodoTaskMapper todoTaskMapper;
    private final WfProcessInstanceExtMapper processInstanceExtMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncProcessTodos(String processInstanceId) {
        WfProcessInstanceExt ext = processInstanceExtMapper.selectOne(
                new LambdaQueryWrapper<WfProcessInstanceExt>()
                        .eq(WfProcessInstanceExt::getProcessInstanceId, processInstanceId));
        if (ext == null) {
            deleteProcessTodos(processInstanceId);
            log.warn("同步待办时未找到流程扩展记录: processInstanceId={}", processInstanceId);
            return;
        }

        deleteProcessTodos(processInstanceId);

        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskTenantId(String.valueOf(ext.getTenantId()))
                .list();
        for (Task task : tasks) {
            insertTodoTask(task, ext);
        }

        syncCompletedStatus(processInstanceId, ext);
    }

    /** {@inheritDoc} */
    @Override
    public void deleteProcessTodos(String processInstanceId) {
        todoTaskMapper.delete(new LambdaQueryWrapper<WfTodoTask>()
                .eq(WfTodoTask::getProcessInstanceId, processInstanceId));
    }

    /**
     * 写入单条待办任务缓存。
     *
     * @param task Flowable 当前任务
     * @param ext  流程扩展记录
     */
    private void insertTodoTask(Task task, WfProcessInstanceExt ext) {
        Long assigneeId = parseAssigneeId(task);
        if (assigneeId == null) {
            log.warn("跳过无有效处理人的待办任务: taskId={}, assignee={}", task.getId(), task.getAssignee());
            return;
        }

        WfTodoTask todoTask = new WfTodoTask();
        todoTask.setTenantId(ext.getTenantId());
        todoTask.setTaskId(task.getId());
        todoTask.setProcessInstanceId(task.getProcessInstanceId());
        todoTask.setProcessKey(ext.getProcessKey());
        todoTask.setTaskName(task.getName());
        todoTask.setAssigneeId(assigneeId);
        todoTask.setAssigneeName(task.getAssignee());
        todoTask.setTitle(ext.getTitle());
        todoTask.setCategory(ext.getCategory());
        if (task.getCreateTime() != null) {
            todoTask.setCreateTime(LocalDateTime.ofInstant(
                    task.getCreateTime().toInstant(), ZoneId.systemDefault()));
        } else {
            todoTask.setCreateTime(LocalDateTime.now());
        }
        todoTaskMapper.insert(todoTask);
    }

    /**
     * 将已自然结束的流程实例扩展状态同步为已完成。
     *
     * @param processInstanceId 流程实例 ID
     * @param ext              流程扩展记录
     */
    private void syncCompletedStatus(String processInstanceId, WfProcessInstanceExt ext) {
        ProcessInstance runningInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .processInstanceTenantId(String.valueOf(ext.getTenantId()))
                .singleResult();
        if (runningInstance == null && Integer.valueOf(1).equals(ext.getStatus())) {
            ext.setStatus(2);
            ext.setUpdateTime(LocalDateTime.now());
            processInstanceExtMapper.updateById(ext);
        }
    }

    /**
     * 解析 Flowable 任务处理人 ID。
     *
     * @param task Flowable 当前任务
     * @return 处理人 ID，无法解析时返回 {@code null}
     */
    private Long parseAssigneeId(Task task) {
        if (task.getAssignee() == null || task.getAssignee().isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(task.getAssignee());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
