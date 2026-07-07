package com.omni.common.workflow.notification;

import lombok.extern.slf4j.Slf4j;

/**
 * 通知服务的默认空实现（NoOp）。
 * <p>
 * 当业务模块未提供自定义 {@link WorkflowNotificationService} 实现时，
 * Starter 自动注册此空实现，仅记录日志不发送通知。
 * 业务方实现自定义通知服务后，此 Bean 会被自动覆盖（{@code @ConditionalOnMissingBean}）。</p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
public class NoOpNotificationService implements WorkflowNotificationService {

    /**
     * 记录待办通知日志（不发送实际通知）。
     *
     * @param userId             审批人用户 ID
     * @param taskId             Flowable 任务 ID
     * @param processInstanceId  流程实例 ID
     * @param title              流程标题
     */
    @Override
    public void notifyPendingTask(String userId, String taskId, String processInstanceId, String title) {
        log.debug("NoOp 通知: userId={}, taskId={}, processInstanceId={}, title={}",
                userId, taskId, processInstanceId, title);
    }

    /**
     * 记录清除待办日志（不执行实际操作）。
     *
     * @param taskId Flowable 任务 ID
     */
    @Override
    public void clearPendingTask(String taskId) {
        log.debug("NoOp 清除待办: taskId={}", taskId);
    }
}
