package com.omni.common.operlog.producer;

import com.omni.common.core.operlog.OperLogMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;

/**
 * 操作日志 MQ 生产者。
 * <p>通过 {@link StreamBridge} 将操作日志消息发送至 RocketMQ。</p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@RequiredArgsConstructor
public class OperLogProducer {

    private static final String BINDING_NAME = "operlog-out-0";

    private final StreamBridge streamBridge;

    /**
     * 发送操作日志消息。
     *
     * @param message 操作日志消息
     */
    public void send(OperLogMessage message) {
        try {
            streamBridge.send(BINDING_NAME, message);
        } catch (Exception e) {
            log.warn("操作日志：MQ 消息发送失败: {}", e.getMessage());
        }
    }
}
