package com.omni.common.core.mq;

/**
 * 可靠消息发送接口。
 * <p>定义 Transactional Outbox 模式的发送契约，由具体实现模块（如 {@code omni-common-mqlog}）
 * 提供基于本地消息表的可靠投递能力。</p>
 *
 * <p>设计目的：让生产者模块（如操作日志）可以可选地接入 Outbox 模式，
 * 无需直接依赖具体的 MQ 实现模块。当实现 Bean 不存在时，调用方可回退到直发模式。</p>
 *
 * @author Omni-Stack Team
 * @see com.omni.common.mqlog.template.ReliableMessageTemplate
 */
public interface ReliableMessageRelay {

    /**
     * 可靠发送消息（写入本地 Outbox 表，由中继任务异步投递）。
     *
     * @param bindingName Spring Cloud Stream binding 名称
     * @param payload     消息体对象（将被序列化为 JSON）
     * @param tenantId    租户 ID（用于消息记录的租户隔离）
     */
    void send(String bindingName, Object payload, Long tenantId);

    /**
     * 可靠发送消息（可选业务键）。
     *
     * @param bindingName Spring Cloud Stream binding 名称
     * @param payload     消息体对象（将被序列化为 JSON）
     * @param tenantId    租户 ID（用于消息记录的租户隔离）
     * @param msgKey      业务键（可选，如 "order:12345"，用于运维排查）
     */
    void send(String bindingName, Object payload, Long tenantId, String msgKey);
}
