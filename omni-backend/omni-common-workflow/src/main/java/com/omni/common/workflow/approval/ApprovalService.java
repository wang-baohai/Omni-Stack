package com.omni.common.workflow.approval;

import java.util.Map;

/**
 * 审批服务 SPI 接口。
 * <p>
 * 封装 Flowable Multi-Instance 机制，提供企业审批流的高级操作：
 * 审批通过/驳回、加签、减签、委托等。业务层直接调用此接口，
 * 无需了解 Flowable 内部的多实例执行机制。</p>
 *
 * @author Omni-Stack Team
 */
public interface ApprovalService {

    /**
     * 审批通过或驳回。
     * <p>
     * 完成当前任务节点，设置审批结果变量，流程引擎根据变量值
     * 自动流转到下一个节点或回退到上一个节点。</p>
     *
     * @param taskId    Flowable 任务 ID
     * @param approved  是否通过（true=通过, false=驳回）
     * @param comment   审批意见
     * @param variables 附加流程变量（可选）
     */
    void complete(String taskId, boolean approved, String comment, Map<String, Object> variables);

    /**
     * 加签：在当前审批节点动态增加审批人。
     * <p>
     * 基于 Flowable {@code addMultiInstanceExecution} API，
     * 在运行时向多实例活动中添加一个新的执行实例。</p>
     *
     * @param taskId       当前任务 ID（用于定位活动节点）
     * @param newUserId    新增审批人的用户 ID
     */
    void addSigner(String taskId, String newUserId);

    /**
     * 减签：从当前审批节点移除指定审批人。
     * <p>
     * 基于 Flowable {@code deleteMultiInstanceExecution} API。</p>
     *
     * @param taskId        当前任务 ID
     * @param targetUserId  要移除的审批人用户 ID
     */
    void removeSigner(String taskId, String targetUserId);

    /**
     * 委托：将任务转交给其他用户审批。
     * <p>
     * 修改任务的 {@code assignee} 为目标用户，
     * 原审批人不再能看到此任务。</p>
     *
     * @param taskId         当前任务 ID
     * @param targetUserId   被委托人的用户 ID
     */
    void delegate(String taskId, String targetUserId);
}
