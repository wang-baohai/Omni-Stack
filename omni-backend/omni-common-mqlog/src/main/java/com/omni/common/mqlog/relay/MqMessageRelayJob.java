package com.omni.common.mqlog.relay;

import com.omni.common.job.SystemJobMeta;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MQ 消息投递定时任务。
 * <p>
 * 通过 XXL-JOB 定时轮询 PENDING/FAILED 状态的消息并投递到 MQ。
 * 各服务执行器 AppName 不同，handler name 天然隔离，不冲突。
 * </p>
 * <p>
 * 引入 {@code omni-common-mqlog} starter 的服务自动注册此系统任务，
 * 在 XXL-JOB 管理后台中可见并配置调度频率。
 * </p>
 *
 * @author Omni-Stack Team
 * @see MqMessageRelayService
 */
@Slf4j
@RequiredArgsConstructor
public class MqMessageRelayJob {

    private final MqMessageRelayService mqMessageRelayService;

    /**
     * MQ 消息中继投递 Handler。
     * <p>默认每 10 秒执行一次，路由策略为"第一个"。</p>
     */
    @XxlJob("mqRelayHandler")
    @SystemJobMeta(
            name = "MQ消息投递",
            description = "轮询 PENDING/待重试 状态的消息并投递到 MQ，支持指数退避重试",
            defaultCron = "0/10 * * * * ?",
            routeStrategy = "FIRST"
    )
    public void relay() {
        log.debug("MQ 中继任务开始执行");
        mqMessageRelayService.relayPendingMessages();
    }
}
