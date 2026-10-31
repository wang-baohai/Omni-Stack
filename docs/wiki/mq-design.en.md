# 2. Framework choices: who handles each stage?

This project uses RocketMQ as the intermediary, Spring Cloud Stream bindings between code and destinations, and Outbox plus XXL-JOB for relaying. This describes the current choice rather than comparing or promising every MQ product's capabilities.

## Why separate the layers?

Spring Cloud Stream provides a common sending interface. A binding is an application-side channel name; destination identifies the actual configured destination. The project's topic field currently stores bindingName. Do not assume that field is the final Broker Topic: inspect the service's Stream configuration.

Outbox covers the gap between local commit and later sending. RocketMQ accepts and transports messages. Consumers perform business work. Success at one stage does not prove the other stages succeeded.

## Read one actual send

This is the complete send method in the current RocketMQ strategy:

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

MessageBuilder combines the body and correlation headers; StreamBridge sends through the channel configured for bindingName. A false return throws an exception so the relay handles a failure. A successful return still does not mean the consumer committed its work. SENT describes sending-side success.

omniMessageId identifies an Outbox record, while omniProducerTraceId helps find the producer's logs. Neither header is an authorization credential or automatic business deduplication. Historical records without a producer trace ID can still be sent.

## Why keep a relay and a sender strategy?

The relay owns claiming, states, backoff, and dead letters. The sender strategy handles the transport framework. The current enqueue template fixes broker_type to rocketmq. Supporting another Broker needs not only MessageSender but also the enqueue contract, binding configuration, failure handling, and verification. One new class is not a completed switch.

Next, examine [Outbox and transactions](mq-outbox.en.md).
