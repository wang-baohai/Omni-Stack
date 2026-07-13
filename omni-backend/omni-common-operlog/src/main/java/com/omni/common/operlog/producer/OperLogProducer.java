package com.omni.common.operlog.producer;

import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.operlog.OperLogMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;

/**
 * 操作日志 MQ 生产者。
 * <p>支持两种发送模式：</p>
 * <ul>
 *   <li><b>Outbox 模式</b>（推荐）：当 {@link ReliableMessageRelay} Bean 存在时，
 *       通过 Transactional Outbox 写入 {@code sys_mq_message} 表，
 *       由中继任务异步投递到 RocketMQ，具备补偿重试能力。</li>
 *   <li><b>直发模式</b>（回退）：当 {@link ReliableMessageRelay} 不可用时，
 *       直接通过 {@link StreamBridge} 发送至 RocketMQ。
 *       发送失败仅记录 WARN 日志，不影响业务逻辑。</li>
 * </ul>
 *
 * @author Omni-Stack Team
 * @see OperLogMessage
 * @see ReliableMessageRelay
 */
@Slf4j
public class OperLogProducer {

    private static final String BINDING_NAME = "operlog-out-0";

    private final StreamBridge streamBridge;
    private final ReliableMessageRelay reliableRelay;

    /**
     * 构造操作日志生产者。
     *
     * @param streamBridge    Spring Cloud Stream 桥接器（必选）
     * @param reliableRelay   可靠消息中继（可选，为 null 时回退到直发模式）
     */
    public OperLogProducer(StreamBridge streamBridge, ReliableMessageRelay reliableRelay) {
        this.streamBridge = streamBridge;
        this.reliableRelay = reliableRelay;
        if (reliableRelay != null) {
            log.info("操作日志生产者：使用 Transactional Outbox 模式");
        } else {
            log.info("操作日志生产者：使用直发模式（ReliableMessageRelay 未接入）");
        }
    }

    /**
     * 发送操作日志消息到 RocketMQ。
     * <p>优先通过 Outbox 模式可靠投递，若未接入则直接通过 StreamBridge 发送。
     * 发送异常仅记录日志，不抛出异常，避免影响业务流程。</p>
     *
     * @param message 操作日志消息，不为 null
     */
    public void send(OperLogMessage message) {
        try {
            Long tenantId = message.getTenantId();
            if (reliableRelay != null) {
                reliableRelay.send(BINDING_NAME, message, tenantId, message.getEventId());
            } else {
                streamBridge.send(BINDING_NAME, message);
            }
        } catch (Exception e) {
            log.warn("操作日志：MQ 消息发送失败: {}", e.getMessage(), e);
        }
    }
}
