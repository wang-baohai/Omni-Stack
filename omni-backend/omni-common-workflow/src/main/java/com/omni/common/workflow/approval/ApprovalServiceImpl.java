package com.omni.common.workflow.approval;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
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

        // 递增流程实例级计数器（用于多实例完成条件判断）。
        // 关键：必须使用 task.getExecutionId()（多实例子执行 ID）而非 procInstId，
        // 因为 setVariable 会沿执行树向上查找已有变量并就地更新——
        // approvedCount/rejectedCount 由 CandidateResolverBean 初始化在 MI 根执行上，
        // completionCondition 也在 MI 根执行上评估。若写入 procInstId（流程实例级），
        // 会在流程实例层创建独立副本，MI 根执行上的值不变，导致完成条件永远不满足。
        String execId = task.getExecutionId();
        String counterKey = approved ? "approvedCount" : "rejectedCount";
        Object countObj = runtimeService.getVariable(execId, counterKey);
        int currentCount = countObj instanceof Number ? ((Number) countObj).intValue() : 0;
        runtimeService.setVariable(execId, counterKey, currentCount + 1);

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
        Map<String, Object> variables = Map.of("assignee", newUserId);
        runtimeService.addMultiInstanceExecution(activityId, task.getProcessInstanceId(), variables);
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
        log.info("减签成功: taskId={}, targetUserId={}, executionId={}", taskId, targetUserId, targetTask.getExecutionId());
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
