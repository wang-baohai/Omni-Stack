package com.omni.common.core.job;

/**
 * 用户自定义任务处理器 SPI 接口。
 * <p>
 * 开发者实现此接口并将其注册为 Spring Bean（Bean 名称即为任务类型编码 {@code type_code}），
 * 即可为系统新增一种可调度执行的任务类型。
 * {@link com.omni.common.core.job.UserJobHandlerRegistry UserJobHandlerRegistry}
 * 在启动时自动扫描所有实现类并建立 type_code → Handler 的映射关系。
 * </p>
 *
 * <p>执行链路：XXL-JOB 调度 → {@code userJobExecuteHandler} →
 * JSON 反序列化 {@link UserJobMessage} → 按 {@code jobType} 路由到对应 Handler 的
 * {@link #execute(UserJobMessage)} 方法。</p>
 *
 * <p>示例：</p>
 * <pre>{@code
 * @Component("log_cleanup_remind")
 * @RequiredArgsConstructor
 * public class LogCleanupRemindHandler implements UserJobHandler {
 *     @Override
 *     public void execute(UserJobMessage message) {
 *         // 业务逻辑
 *     }
 * }
 * }</pre>
 *
 * @author Omni-Stack Team
 * @see UserJobMessage
 */
public interface UserJobHandler {

    /**
     * 执行用户任务。
     * <p>由 {@code UserJobExecuteHandler} 在 XXL-JOB 触发时调用。
     * 实现类应在此方法中完成具体业务逻辑，如发送提醒、执行数据清洗等。
     * 方法正常返回视为执行成功；抛出异常视为执行失败，异常信息将记录到 XXL-JOB 调度日志。</p>
     *
     * @param message 任务消息（含租户 ID、任务 ID、用户自定义参数等），不为 null
     * @throws Exception 执行过程中的任何异常，由上层统一捕获并标记任务执行失败
     * @see UserJobMessage#getJobParams() 获取用户创建任务时填写的自定义参数
     */
    void execute(UserJobMessage message) throws Exception;

    /**
     * 获取执行结果消息（可选覆写）。
     * <p>
     * 返回的消息将存储在 {@code sys_user_job_log.result_message} 字段中，
     * 前端通过轮询 {@code sys_user_job_log} 表展示为 {@code ElNotification} 通知弹窗。
     * 默认返回 {@code null}，表示不生成结果消息。
     * </p>
     * <p>典型用法：返回 "已成功清理 120 条过期日志" 等人类可读的执行摘要。</p>
     *
     * @param message 任务消息，不为 null
     * @return 用户可读的结果消息字符串，或 {@code null} 表示不记录结果消息
     */
    default String getResultMessage(UserJobMessage message) {
        return null;
    }
}
