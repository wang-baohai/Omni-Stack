package com.omni.common.mqlog.sender;

import com.omni.common.mqlog.entity.SysMqMessage;

/**
 * 消息发送策略接口。
 * <p>
 * 每种 MQ 中间件实现此接口（如 {@code RocketMqMessageSender}），
 * {@link com.omni.common.mqlog.relay.MqMessageRelayService} 按
 * {@link SysMqMessage#getBrokerType()} 路由到对应实现。
 * </p>
 * <p>后续新增 Kafka 时实现 {@code KafkaMessageSender} 即可。</p>
 *
 * @author Omni-Stack Team
 */
public interface MessageSender {

    /**
     * 返回此策略支持的 broker 类型标识。
     *
     * @return broker 类型字符串，如 "rocketmq"
     */
    String brokerType();

    /**
     * 将消息投递到 MQ。
     *
     * @param message 消息记录实体
     */
    void send(SysMqMessage message);
}
