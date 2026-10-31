# 3. Outbox：先把“需要发送”可靠记下来

Outbox可以理解为业务服务自己的待办清单。它随业务一起保存的是发送意图，而不是已经送达的证明。当前各服务维护本地sys_mq_message，正式表结构由Liquibase迁移管理，不是在应用每次启动时随意建表。

## 第一步：明确事务与租户

当前模板的事务声明与租户校验如下，片段结束于租户校验：

<!-- omni:source path="omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/template/ReliableMessageTemplate.java" -->
```java
    @Transactional(propagation = Propagation.REQUIRED)
    public void send(String bindingName, Object payload, Long tenantId, String msgKey) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("可靠消息 tenantId 必须为正整数");
        }
```

REQUIRED表示加入已有事务；没有事务时开启自己的事务。业务原子性要求调用方已经在同一数据源的有效业务事务内，且调用实际经过Spring事务代理。不能用跨库写入、事务结束后的日志后处理或内部自调用来代替这个前提。

租户ID必须显式传入正整数，不能靠ThreadLocal猜。后台中继跨租户工作时，原请求线程的上下文不在场，消息必须自己带上正确租户。

## 第二步：记录初始状态

模板中的这一段是当前落库前的实际赋值：

<!-- omni:source path="omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/template/ReliableMessageTemplate.java" -->
```java
        message.setBrokerType("rocketmq");
        message.setStatus(SysMqMessage.STATUS_PENDING);
        message.setRetryCount(0);
        message.setMaxRetry(3);
        message.setServiceName(serviceName);
        message.setProducerTraceId(MDC.get("traceId"));
        message.setTenantId(tenantId);
        message.setCreateTime(LocalDateTime.now());

        sysMqMessageMapper.insert(message);
```

新记录从PENDING开始，失败计数为0，默认最大失败次数为3。serviceName标记来源，producerTraceId保留可用关联线索；插入成功只表示清单已写入。

msgId是这次消息记录的标识。msgKey是排查用业务键，不应把它当成系统自动提供的唯一幂等约束。业务重复调用send可能创建新的记录，业务入口仍需要自己的请求幂等设计。

## 第三步：把提交与发送分开验

在合法业务事务中验证：业务回滚时Outbox也不留下待发记录；业务提交后清单存在。然后另验中继发送、消费者处理与重复消费。只向表里插一条演示消息，不能证明前面的业务事务原子性。

下一篇继续看[领取、重试和消费](mq-relay.md)。
