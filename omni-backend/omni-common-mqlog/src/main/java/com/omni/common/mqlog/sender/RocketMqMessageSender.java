package com.omni.common.mqlog.sender;

import com.omni.common.mqlog.entity.SysMqMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;

/**
 * RocketMQ 消息发送策略。
 * <p>基于 Spring Cloud Stream {@link StreamBridge} 发送消息，broker 无关的抽象层。</p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@RequiredArgsConstructor
public class RocketMqMessageSender implements MessageSender {

    private final StreamBridge streamBridge;

    @Override
    public String brokerType() {
        return "rocketmq";
    }

    @Override
    public void send(SysMqMessage message) {
        boolean success = streamBridge.send(message.getBindingName(), message.getPayload());
        if (!success) {
            throw new RuntimeException("StreamBridge 发送返回 false，binding=" + message.getBindingName());
        }
        log.debug("MQ 消息投递成功：msgId={}, binding={}", message.getMsgId(), message.getBindingName());
    }
}
