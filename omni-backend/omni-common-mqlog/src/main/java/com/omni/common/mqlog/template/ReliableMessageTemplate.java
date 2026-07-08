package com.omni.common.mqlog.template;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.mqlog.entity.SysMqMessage;
import com.omni.common.mqlog.mapper.SysMqMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 可靠消息发送模板。
 * <p>
 * 实现 Transactional Outbox 模式：在调用方的当前事务中插入 PENDING 记录，
 * 事务提交后由 XXL-JOB relay 任务异步投递到 MQ。保证业务操作与消息记录的原子性。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 在业务 Service 的 @Transactional 方法中调用
 * reliableMessageTemplate.send("order-out-0", orderPayload);
 * reliableMessageTemplate.send("order-out-0", orderPayload, "order:12345");
 * }</pre>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@RequiredArgsConstructor
public class ReliableMessageTemplate implements ReliableMessageRelay {

    private final SysMqMessageMapper sysMqMessageMapper;
    private final ObjectMapper objectMapper;

    @Value("${spring.application.name:unknown}")
    private String serviceName;

    /**
     * 发送可靠消息（不传业务键）。
     *
     * @param bindingName Spring Cloud Stream binding name
     * @param payload     消息体对象（将被序列化为 JSON）
     * @param tenantId    租户 ID（用于消息记录的租户隔离）
     */
    @Override
    public void send(String bindingName, Object payload, Long tenantId) {
        send(bindingName, payload, tenantId, null);
    }

    /**
     * 发送可靠消息（可选业务键）。
     *
     * @param bindingName Spring Cloud Stream binding name
     * @param payload     消息体对象（将被序列化为 JSON）
     * @param tenantId    租户 ID（用于消息记录的租户隔离）
     * @param msgKey      业务键（可选，如 "order:12345"，用于运维排查）
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void send(String bindingName, Object payload, Long tenantId, String msgKey) {
        String msgId = UUID.randomUUID().toString();
        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("消息体 JSON 序列化失败: " + e.getMessage(), e);
        }

        SysMqMessage message = new SysMqMessage();
        message.setMsgId(msgId);
        message.setTopic(bindingName);
        message.setBindingName(bindingName);
        message.setMsgKey(msgKey);
        message.setPayload(jsonPayload);
        message.setBrokerType("rocketmq");
        message.setStatus(SysMqMessage.STATUS_PENDING);
        message.setRetryCount(0);
        message.setMaxRetry(3);
        message.setServiceName(serviceName);
        message.setTenantId(tenantId);
        message.setCreateTime(LocalDateTime.now());

        sysMqMessageMapper.insert(message);
        log.debug("可靠消息已落库：msgId={}, binding={}, key={}", msgId, bindingName, msgKey);
    }
}
