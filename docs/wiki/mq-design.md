# 2. 框架选择：每个工具负责哪一段

本工程使用RocketMQ作为消息中间站，以Spring Cloud Stream的binding连接业务代码和实际目的地，再用Outbox与XXL-JOB处理中继。下面讲的是当前选择，不是在比较或承诺所有MQ产品的能力。

## 为什么分成这些层

Spring Cloud Stream提供统一发送接口。binding是应用侧的通道名称，destination才是配置里的实际目的地。项目的topic字段当前写入bindingName，不能把这个字段直接当成Broker中最终的Topic名称；应结合对应服务的Stream配置判断。

Outbox处理本地提交与后续发送之间的间隙，RocketMQ负责接受和传递消息，消费者负责真正执行业务。一个工具不能包办其他几段的成功。

## 从工程源码看一次发送

这是当前RocketMQ发送策略的完整方法：

<!-- omni:source path="omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/sender/RocketMqMessageSender.java" -->
```java
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
```

MessageBuilder把正文和关联头组合起来；StreamBridge按bindingName交给配置的绑定通道。返回false会抛异常，使中继进入失败处理。成功返回仍不等于消费者已提交业务，页面的SENT表示发送侧成功。

omniMessageId用于找同一条Outbox记录，omniProducerTraceId用于追查生成时的日志；这些头不是权限凭证，也不自动提供业务去重。历史记录没有生产追踪编号时仍可发送。

## 为什么保留中继和发送策略

中继统一处理领取、状态、退避和死信；发送策略集中处理具体发送框架。当前入队模板把broker_type固定为rocketmq。以后接其他Broker时，除了实现MessageSender，还要扩展入队契约、绑定配置、错误处理和验证，不能只加一个类就宣称切换完成。

下一篇把“先记下来”的部分拆开看：[Outbox与事务](mq-outbox.md)。
