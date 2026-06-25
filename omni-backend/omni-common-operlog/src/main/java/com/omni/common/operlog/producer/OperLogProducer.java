package com.omni.common.operlog.producer;

import com.omni.common.core.operlog.OperLogMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;

/**
 * 操作日志 MQ 生产者。
 * <p>通过 {@link StreamBridge} 将 {@link OperLogMessage} 发送至 RocketMQ 的
 * {@code operlog-out-0} binding，由消费端（{@code OperLogConsumer}）写入数据库。
 * 发送失败仅记录 WARN 日志，不影响业务逻辑。</p>
 *
 * @author Omni-Stack Team
 * @see OperLogMessage
 */
@Slf4j
@RequiredArgsConstructor
public class OperLogProducer {

    private static final String BINDING_NAME = "operlog-out-0";

    private final StreamBridge streamBridge;

    /**
     * 发送操作日志消息到 RocketMQ。
     * <p>通过 {@code operlog-out-0} binding 发送消息。
     * 发送异常仅记录日志，不抛出异常，避免影响业务流程。</p>
     *
     * @param message 操作日志消息，不为 null
     */
    public void send(OperLogMessage message) {
        try {
            streamBridge.send(BINDING_NAME, message);
        } catch (Exception e) {
            log.warn("操作日志：MQ 消息发送失败: {}", e.getMessage(), e);
        }
    }
}
