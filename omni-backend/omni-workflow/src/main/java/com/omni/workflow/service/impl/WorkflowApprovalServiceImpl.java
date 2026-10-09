package com.omni.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.common.core.result.BusinessException;
import com.omni.common.workflow.approval.ApprovalService;
import com.omni.workflow.dto.ApprovalRequest;
import com.omni.workflow.dto.WorkflowCompletionResult;
import com.omni.workflow.entity.WfProcessInstanceExt;
import com.omni.workflow.mapper.WfProcessInstanceExtMapper;
import com.omni.workflow.metrics.WorkflowMetrics;
import com.omni.workflow.service.WorkflowApprovalService;
import com.omni.workflow.service.WorkflowCompletionEventService;
import com.omni.workflow.service.WorkflowTodoSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

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
    private final RuntimeService runtimeService;
    private final WfProcessInstanceExtMapper processInstanceExtMapper;
    private final WorkflowCompletionEventService workflowCompletionEventService;
    private final WorkflowTodoSyncService workflowTodoSyncService;

    /** {@inheritDoc} */
    @Override
    public void complete(String taskId, ApprovalRequest request, Long userId, Long tenantId) {
        long startedNanos = System.nanoTime();
        String result = "failure";
        try {
            Task task = requireCurrentAssigneeTask(taskId, userId, tenantId);
            approvalService.complete(taskId, request.isApproved(), request.getComment(), request.getVariables());
            publishIfCrossServiceProcessCompleted(task.getProcessInstanceId(), tenantId, request.isApproved());
            syncAfterCommit(task.getProcessInstanceId());
            result = "success";
        } finally {
            WorkflowMetrics.recordApproval(result, System.nanoTime() - startedNanos);
        }
    }

    /**
     * 当跨服务流程在本次审批后自然结束时，事务内写入唯一完成事件。
     *
     * @param processInstanceId 流程实例 ID
     * @param tenantId 租户 ID
     * @param approved 最终审批动作
     */
    private void publishIfCrossServiceProcessCompleted(String processInstanceId,
                                                       Long tenantId,
                                                       boolean approved) {
        boolean running = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .processInstanceTenantId(String.valueOf(tenantId))
                .singleResult() != null;
        if (running) {
            return;
        }
        WfProcessInstanceExt ext = processInstanceExtMapper.selectOne(
                new LambdaQueryWrapper<WfProcessInstanceExt>()
                        .eq(WfProcessInstanceExt::getTenantId, tenantId)
                        .eq(WfProcessInstanceExt::getProcessInstanceId, processInstanceId));
        if (ext == null || ext.getBusinessType() == null || ext.getBusinessType().isBlank()) {
            return;
        }
        WorkflowCompletionResult result = approved
                ? WorkflowCompletionResult.APPROVED : WorkflowCompletionResult.REJECTED;
        workflowCompletionEventService.publishCompletionEvent(
                tenantId, processInstanceId, result, LocalDateTime.now());
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
            throw new BusinessException(409, "审批任务已被处理或不存在，请刷新待办列表");
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
