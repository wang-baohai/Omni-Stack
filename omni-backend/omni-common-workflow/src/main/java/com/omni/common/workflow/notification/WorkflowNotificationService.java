package com.omni.common.workflow.notification;

/**
 * 工作流通知服务 SPI 接口。
 * <p>
 * 当流程流转到新的审批节点时，通过此接口通知审批人。
 * 脚手架预留此扩展点，V1 仅实现站内待办（查询 {@code wf_todo_task} 表），
 * 业务方可自行实现 WebSocket / 邮件 / 钉钉 / 企微等通知渠道。</p>
 *
 * @author Omni-Stack Team
 */
public interface WorkflowNotificationService {

    /**
     * 通知审批人有新的待审批任务。
     *
     * @param userId             审批人用户 ID
     * @param taskId             Flowable 任务 ID
     * @param processInstanceId  流程实例 ID
     * @param title              流程标题（如"张三的请假申请"）
     */
    void notifyPendingTask(String userId, String taskId, String processInstanceId, String title);

    /**
     * 清除待办通知（任务完成后调用）。
     *
     * @param taskId Flowable 任务 ID
     */
    void clearPendingTask(String taskId);
}
