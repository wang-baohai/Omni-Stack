package com.omni.common.workflow.approval;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.Execution;
import org.flowable.task.api.Task;

import java.util.Map;

/**
 * 审批服务默认实现。
 * <p>
 * 基于 Flowable {@link TaskService} 和 {@link RuntimeService} 封装
 * 企业审批流的高级操作（通过/驳回、加签、减签、委托）。</p>
 * <p>
 * 加签/减签依赖 Flowable 的 Multi-Instance（多实例）机制，
 * 要求 BPMN 流程中的审批节点配置为并行多实例。</p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@RequiredArgsConstructor
public class ApprovalServiceImpl implements ApprovalService {

    private final TaskService taskService;
    private final RuntimeService runtimeService;

    /**
     * 审批通过或驳回。
     * <p>
     * 设置 {@code approved} 和 {@code comment} 流程变量后完成任务，
     * 流程引擎根据条件网关自动路由到下一节点。</p>
     *
     * @param taskId    Flowable 任务 ID
     * @param approved  是否通过
     * @param comment   审批意见
     * @param variables 附加流程变量（可选，可为 {@code null}）
     */
    @Override
    public void complete(String taskId, boolean approved, String comment, Map<String, Object> variables) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        // 添加审批意见
        if (comment != null && !comment.isBlank()) {
            taskService.addComment(taskId, task.getProcessInstanceId(), comment);
        }

        // 将审批结果设置为 task-local 变量（多实例场景下记录每个审批人的结果）
        taskService.setVariableLocal(taskId, "approved", approved);
        taskService.setVariableLocal(taskId, "comment", comment);

        // 通过 RuntimeService 显式写入流程实例根作用域（多实例完成后
        // 排他网关等后续节点需要访问该变量）
        String procInstId = task.getProcessInstanceId();
        runtimeService.setVariable(procInstId, "approved", approved);

        // 递增审批计数器（用于多实例完成条件判断）。
        // WP-015 FR04：计数器必须写在**本 MI 活动的根执行本地**。上溯定位成功时
        // 用 setVariableLocal 精确落在 MI 根；定位失败（非多实例节点或旧定义将变量
        // 放在流程实例级）时保持原有行为，仍沿执行树就地更新，保证向后兼容。
        String execId = task.getExecutionId();
        String miRoot = resolveMultiInstanceRoot(procInstId, execId);
        String scope = miRoot != null ? miRoot : execId;
        String counterKey = approved ? "approvedCount" : "rejectedCount";
        Object countObj = runtimeService.getVariableLocal(scope, counterKey);
        if (countObj == null) {
            // 兼容：变量不在该作用域本地（例如历史定义写在流程实例级）
            countObj = runtimeService.getVariable(execId, counterKey);
        }
        int currentCount = countObj instanceof Number ? ((Number) countObj).intValue() : 0;
        if (miRoot != null) {
            runtimeService.setVariableLocal(miRoot, counterKey, currentCount + 1);
        } else {
            runtimeService.setVariable(execId, counterKey, currentCount + 1);
        }

        // 附加变量也分别写入
        if (variables != null) {
            variables.forEach((k, v) -> {
                taskService.setVariableLocal(taskId, k, v);
                runtimeService.setVariable(procInstId, k, v);
            });
        }

        taskService.complete(taskId);
        log.info("审批完成: taskId={}, approved={}, processInstanceId={}", taskId, approved, task.getProcessInstanceId());
    }

    /**
     * 加签：动态增加审批人。
     * <p>
     * 通过 {@link RuntimeService#addMultiInstanceExecution} 在当前多实例活动中
     * 添加新的执行实例，新审批人会收到一个待办任务。</p>
     * <p>
     * WP-015 FR04（Codex RF01）：加签后 **ALL 模式的有效阈值必须随当前应参与人数
     * 同步**，否则加签人会被完成条件提前跳过。阈值按“已同意 + 仍在待办”重算，
     * 既不重置已有票数，也不改变已完成的那一票。</p>
     *
     * @param taskId    当前任务 ID
     * @param newUserId 新增审批人用户 ID
     */
    @Override
    public void addSigner(String taskId, String newUserId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        String activityId = task.getTaskDefinitionKey();
        Map<String, Object> variables = Map.of("userId", newUserId);
        runtimeService.addMultiInstanceExecution(activityId, task.getProcessInstanceId(), variables);
        refreshRequiredApprovals(task.getProcessInstanceId(), task.getExecutionId(), activityId);
        log.info("加签成功: taskId={}, activityId={}, newUserId={}", taskId, activityId, newUserId);
    }

    /**
     * 减签：移除指定审批人。
     * <p>
     * 查找目标用户的任务执行并删除多实例执行。</p>
     *
     * @param taskId       当前任务 ID
     * @param targetUserId 要移除的审批人用户 ID
     */
    @Override
    public void removeSigner(String taskId, String targetUserId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        // 查找目标用户的任务
        Task targetTask = taskService.createTaskQuery()
                .processInstanceId(task.getProcessInstanceId())
                .taskDefinitionKey(task.getTaskDefinitionKey())
                .taskAssignee(targetUserId)
                .singleResult();

        if (targetTask == null) {
            throw new IllegalArgumentException("目标审批人无待办任务: " + targetUserId);
        }

        // 通过执行 ID 删除多实例
        runtimeService.deleteMultiInstanceExecution(targetTask.getExecutionId(), false);
        refreshRequiredApprovals(task.getProcessInstanceId(), task.getExecutionId(), task.getTaskDefinitionKey());
        log.info("减签成功: taskId={}, targetUserId={}, executionId={}", taskId, targetUserId, targetTask.getExecutionId());
    }

    /**
     * 重算 ALL 模式下的有效全员阈值：已同意票数 + 本活动仍在待办的任务数。
     * <p>
     * ANY 模式阈值恒为 1，不重算；非多实例节点（定位不到 MI 根）也不重算，
     * 保持原有行为。</p>
     *
     * @param processInstanceId 流程实例 ID
     * @param executionId       发起变动的子执行 ID
     * @param activityId        多实例活动的任务定义 KEY
     */
    private void refreshRequiredApprovals(String processInstanceId, String executionId, String activityId) {
        String miRoot = resolveMultiInstanceRoot(processInstanceId, executionId);
        if (miRoot == null) {
            return;
        }
        Object mode = runtimeService.getVariableLocal(miRoot, "approvalMode");
        if (mode == null) {
            mode = runtimeService.getVariable(executionId, "approvalMode");
        }
        if ("ANY".equals(mode)) {
            return;
        }
        Object approvedObj = runtimeService.getVariableLocal(miRoot, "approvedCount");
        int approved = approvedObj instanceof Number ? ((Number) approvedObj).intValue() : 0;
        long pending = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskDefinitionKey(activityId)
                .count();
        int effective = approved + (int) pending;
        runtimeService.setVariableLocal(miRoot, "requiredApprovals", effective);
        log.info("重算 ALL 阈值: miRoot={}, activityId={}, approved={}, pending={}, requiredApprovals={}",
                miRoot, activityId, approved, pending, effective);
    }

    /**
     * 沿执行树向上定位多实例根执行。
     * <p>
     * 以 MI 根本地变量 {@code nrOfInstances} 为识别凭据：Flowable 在多实例根执行上
     * 以 local 变量维护 {@code nrOfInstances}，而子执行与流程实例根都没有。
     * 最多上溯 32 层；定位失败返回 {@code null}，调用方保持原有行为。</p>
     *
     * @param processInstanceId 流程实例 ID
     * @param executionId       起始执行 ID（通常为任务所在的子执行）
     * @return MI 根执行 ID；非多实例时返回 {@code null}
     */
    private String resolveMultiInstanceRoot(String processInstanceId, String executionId) {
        String current = executionId;
        for (int depth = 0; depth < 32 && current != null; depth++) {
            if (runtimeService.getVariableLocal(current, "nrOfInstances") instanceof Number) {
                return current;
            }
            if (current.equals(processInstanceId)) {
                return null;
            }
            Execution parentScope = runtimeService.createExecutionQuery().executionId(current).singleResult();
            if (parentScope == null) {
                return null;
            }
            current = parentScope.getParentId();
        }
        return null;
    }

    /**
     * 委托：转交任务给其他用户。
     * <p>
     * 直接修改任务的 {@code assignee} 属性，被委托人即可看到此任务。</p>
     *
     * @param taskId       当前任务 ID
     * @param targetUserId 被委托人用户 ID
     */
    @Override
    public void delegate(String taskId, String targetUserId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        taskService.setAssignee(taskId, targetUserId);
        log.info("委托成功: taskId={}, targetUserId={}", taskId, targetUserId);
    }
}
