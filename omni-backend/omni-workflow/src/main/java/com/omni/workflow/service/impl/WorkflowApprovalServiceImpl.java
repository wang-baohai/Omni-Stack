package com.omni.workflow.service.impl;

import com.omni.common.core.result.BusinessException;
import com.omni.common.workflow.approval.ApprovalService;
import com.omni.workflow.dto.ApprovalRequest;
import com.omni.workflow.service.WorkflowApprovalService;
import com.omni.workflow.service.WorkflowTodoSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 工作流审批操作服务实现。
 * <p>
 * 对所有审批写操作执行租户和当前处理人校验，校验通过后委托通用审批服务执行 Flowable 操作。
 * </p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class WorkflowApprovalServiceImpl implements WorkflowApprovalService {

    private final ApprovalService approvalService;
    private final TaskService taskService;
    private final WorkflowTodoSyncService workflowTodoSyncService;

    /** {@inheritDoc} */
    @Override
    public void complete(String taskId, ApprovalRequest request, Long userId, Long tenantId) {
        Task task = requireCurrentAssigneeTask(taskId, userId, tenantId);
        approvalService.complete(taskId, request.isApproved(), request.getComment(), request.getVariables());
        syncAfterCommit(task.getProcessInstanceId());
    }

    /** {@inheritDoc} */
    @Override
    public void addSigner(String taskId, String newUserId, Long userId, Long tenantId) {
        requireNumericUserId(newUserId);
        Task task = requireCurrentAssigneeTask(taskId, userId, tenantId);
        approvalService.addSigner(taskId, newUserId);
        syncAfterCommit(task.getProcessInstanceId());
    }

    /** {@inheritDoc} */
    @Override
    public void removeSigner(String taskId, String targetUserId, Long userId, Long tenantId) {
        requireNumericUserId(targetUserId);
        Task task = requireCurrentAssigneeTask(taskId, userId, tenantId);
        approvalService.removeSigner(taskId, targetUserId);
        syncAfterCommit(task.getProcessInstanceId());
    }

    /** {@inheritDoc} */
    @Override
    public void delegate(String taskId, String targetUserId, Long userId, Long tenantId) {
        requireNumericUserId(targetUserId);
        Task task = requireCurrentAssigneeTask(taskId, userId, tenantId);
        approvalService.delegate(taskId, targetUserId);
        syncAfterCommit(task.getProcessInstanceId());
    }

    /**
     * 注册事务提交后回调：同步待办缓存。
     * <p>
     * Flowable 的 {@code taskService.complete()} 在同一事务内完成任务并创建下游任务，
     * 但新任务可能尚未 flush 到 JDBC 连接。将同步推迟到事务提交后，确保 Flowable 数据已持久化。</p>
     *
     * @param processInstanceId 流程实例 ID
     */
    private void syncAfterCommit(String processInstanceId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    workflowTodoSyncService.syncProcessTodos(processInstanceId);
                } catch (Exception e) {
                    log.error("事务提交后同步待办失败: processInstanceId={}", processInstanceId, e);
                }
            }
        });
    }

    /**
     * 校验任务属于当前租户且当前用户是处理人。
     *
     * @param taskId   Flowable 任务 ID
     * @param userId   当前用户 ID
     * @param tenantId 当前租户 ID
     * @return 当前任务
     */
    private Task requireCurrentAssigneeTask(String taskId, Long userId, Long tenantId) {
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .taskTenantId(String.valueOf(tenantId))
                .singleResult();
        if (task == null) {
            throw new BusinessException("审批任务不存在");
        }
        if (!String.valueOf(userId).equals(task.getAssignee())) {
            throw new BusinessException(403, "无权操作该审批任务");
        }
        return task;
    }

    /**
     * 校验审批处理人 ID 可写入待办表。
     *
     * @param targetUserId 目标用户 ID
     */
    private void requireNumericUserId(String targetUserId) {
        try {
            Long.valueOf(targetUserId);
        } catch (NumberFormatException e) {
            throw new BusinessException("审批处理人 ID 必须是数字");
        }
    }
}
