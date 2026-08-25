package com.omni.common.mqlog.sender;

import com.omni.common.mqlog.entity.SysMqMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * RocketMQ 消息发送策略。
 * <p>基于 Spring Cloud Stream {@link StreamBridge} 发送消息，broker 无关的抽象层。</p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@RequiredArgsConstructor
public class RocketMqMessageSender implements MessageSender {

    /** 原始业务事务 Trace，用于消费者日志关联，不作为指标标签。 */
    public static final String PRODUCER_TRACE_HEADER = "omniProducerTraceId";
    /** Outbox 消息 ID，用于跨异步边界检索日志，不作为指标标签。 */
    public static final String MESSAGE_ID_HEADER = "omniMessageId";

    private final StreamBridge streamBridge;

    @Override
    public String brokerType() {
        return "rocketmq";
    }

    @Override
    public void send(SysMqMessage message) {
        Message<String> outbound = MessageBuilder.withPayload(message.getPayload())
                .setHeader(MESSAGE_ID_HEADER, message.getMsgId())
                .setHeaderIfAbsent(PRODUCER_TRACE_HEADER, message.getProducerTraceId())
                .build();
        boolean success = streamBridge.send(message.getBindingName(), outbound);
        if (!success) {
            throw new RuntimeException("StreamBridge 发送返回 false，binding=" + message.getBindingName());
        }
        log.debug("MQ 消息投递成功：msgId={}, binding={}", message.getMsgId(), message.getBindingName());
    }
}
