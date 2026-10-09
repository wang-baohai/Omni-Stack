package com.omni.common.mqlog.metrics;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.common.mqlog.entity.SysMqMessage;
import com.omni.common.mqlog.mapper.SysMqMessageMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 可靠消息低基数指标，禁止使用租户、用户、业务键和消息体作为标签。 */
@Slf4j
public class MqLogMetrics {

    private final MeterRegistry registry;
    private final SysMqMessageMapper mapper;
    private final Map<String, Counter> operationCounters = new ConcurrentHashMap<>();

    /** 创建无操作指标实例，用于未启用 Actuator 的运行环境。 */
    public MqLogMetrics() {
        this.registry = null;
        this.mapper = null;
    }

    /** 创建并注册 Outbox 指标。 */
    public MqLogMetrics(MeterRegistry registry, SysMqMessageMapper mapper) {
        this.registry = registry;
        this.mapper = mapper;
        registerStatusGauge("pending", SysMqMessage.STATUS_PENDING);
        registerStatusGauge("sent", SysMqMessage.STATUS_SENT);
        registerStatusGauge("failed", SysMqMessage.STATUS_FAILED);
        registerStatusGauge("dead_letter", SysMqMessage.STATUS_DEAD_LETTER);
        Gauge.builder("omni.mq.outbox.oldest.age", this, MqLogMetrics::oldestPendingAgeSeconds)
                .baseUnit("seconds")
                .description("最旧 PENDING/FAILED Outbox 消息年龄")
                .register(registry);
    }

    /** 记录入库或投递结果，标签仅允许固定 destination 与 result。 */
    public void recordOperation(String destination, String result) {
        if (registry == null) {
            return;
        }
        String safeDestination = destination == null || destination.isBlank() ? "unknown" : destination;
        String safeResult = result == null || result.isBlank() ? "unknown" : result;
        String key = safeDestination + '|' + safeResult;
        operationCounters.computeIfAbsent(key, ignored -> Counter.builder("omni.mq.outbox.operations")
                        .description("Outbox 入库与投递结果")
                        .tag("destination", safeDestination)
                        .tag("result", safeResult)
                        .register(registry))
                .increment();
    }

    /** 注册固定状态标签的消息数量 Gauge。 */
    private void registerStatusGauge(String statusName, int status) {
        Gauge.builder("omni.mq.outbox.messages", this, metrics -> metrics.countByStatus(status))
                .description("Outbox 按状态消息数量")
                .tag("status", statusName)
                .register(registry);
    }

    /** 查询指定状态数量；数据库尚未准备好时返回 NaN，避免阻断抓取。 */
    private double countByStatus(int status) {
        try {
            return mapper.selectCount(new LambdaQueryWrapper<SysMqMessage>()
                    .eq(SysMqMessage::getStatus, status)).doubleValue();
        } catch (RuntimeException exception) {
            log.debug("Outbox 状态指标暂不可用: {}", exception.getMessage());
            return Double.NaN;
        }
    }

    /** 查询最旧待处理消息年龄。 */
    private double oldestPendingAgeSeconds() {
        try {
            SysMqMessage oldest = mapper.selectOne(new LambdaQueryWrapper<SysMqMessage>()
                    .in(SysMqMessage::getStatus, SysMqMessage.STATUS_PENDING, SysMqMessage.STATUS_FAILED)
                    .orderByAsc(SysMqMessage::getCreateTime)
                    .last("LIMIT 1"));
            if (oldest == null || oldest.getCreateTime() == null) {
                return 0;
            }
            return Math.max(0, Duration.between(oldest.getCreateTime(), LocalDateTime.now()).toSeconds());
        } catch (RuntimeException exception) {
            log.debug("Outbox 年龄指标暂不可用: {}", exception.getMessage());
            return Double.NaN;
        }
    }
}
