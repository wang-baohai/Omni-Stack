package com.omni.common.mqlog.relay;

import com.omni.common.job.XxlJobAdminClient;
import com.omni.common.job.XxlJobProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

import java.util.List;
import java.util.Map;

/**
 * MQ 中继任务自动注册器。
 * <p>应用就绪后在后台确保当前服务执行器下存在且启动了 {@code mqRelayHandler}，
 * 从而保证 Transactional Outbox 不依赖人工到调度中心创建任务。</p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
public class MqRelayJobRegistrar implements ApplicationListener<ApplicationReadyEvent> {

    private static final String HANDLER_NAME = "mqRelayHandler";
    private static final String DEFAULT_CRON = "0/10 * * * * ?";
    private static final int MAX_RETRIES = 12;
    private static final long RETRY_INTERVAL_MILLIS = 10_000L;

    private final XxlJobProperties properties;
    private final String applicationName;

    /**
     * 创建 MQ 中继任务注册器。
     *
     * @param properties      XXL-JOB 配置
     * @param applicationName 当前应用名称
     */
    public MqRelayJobRegistrar(XxlJobProperties properties, String applicationName) {
        this.properties = properties;
        this.applicationName = applicationName;
    }

    /**
     * 应用就绪后异步注册中继任务，避免调度中心暂时不可用时阻塞健康检查。
     *
     * @param event 应用就绪事件
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Thread.ofVirtual()
                .name(applicationName + "-mq-relay-registrar")
                .start(this::registerWithRetry);
    }

    private void registerWithRetry() {
        String appName = properties.getExecutor().getAppname().isBlank()
                ? applicationName : properties.getExecutor().getAppname();
        XxlJobAdminClient client = new XxlJobAdminClient(
                properties.getAdmin().getAddresses(),
                properties.getAdmin().getUsername(),
                properties.getAdmin().getPassword());

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                ensureRelayJob(client, appName);
                log.info("MQ 中继任务已就绪: appname={}, handler={}", appName, HANDLER_NAME);
                return;
            } catch (Exception ex) {
                log.warn("MQ 中继任务自动注册失败，稍后重试: appname={}, attempt={}/{}, error={}",
                        appName, attempt, MAX_RETRIES, ex.getMessage());
                if (attempt < MAX_RETRIES && !waitBeforeRetry()) {
                    return;
                }
            }
        }
        log.error("MQ 中继任务自动注册失败，已耗尽重试次数: appname={}", appName);
    }

    private void ensureRelayJob(XxlJobAdminClient client, String appName) {
        int groupId = client.ensureExecutorGroup(appName, appName);
        if (groupId < 0) {
            throw new IllegalStateException("无法取得 XXL-JOB 执行器组");
        }

        List<Map<String, Object>> jobs = client.pageList(groupId, HANDLER_NAME);
        Map<String, Object> existing = jobs.stream()
                .filter(job -> HANDLER_NAME.equals(String.valueOf(job.get("executorHandler"))))
                .findFirst()
                .orElse(null);
        if (existing == null) {
            String jobId = client.addJob(groupId, "MQ消息投递", DEFAULT_CRON,
                    "FIRST", HANDLER_NAME, null);
            if (jobId == null || jobId.isBlank()) {
                throw new IllegalStateException("XXL-JOB 未返回任务 ID");
            }
            client.startJob(parseJobId(jobId));
            return;
        }

        Object triggerStatus = existing.get("triggerStatus");
        if (!(triggerStatus instanceof Number number) || number.intValue() != 1) {
            client.startJob(parseJobId(existing.get("id")));
        }
    }

    private int parseJobId(Object jobId) {
        if (jobId instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(jobId));
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("XXL-JOB 返回了无效的任务 ID", ex);
        }
    }

    private boolean waitBeforeRetry() {
        try {
            Thread.sleep(RETRY_INTERVAL_MILLIS);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("MQ 中继任务自动注册等待被中断");
            return false;
        }
    }
}
