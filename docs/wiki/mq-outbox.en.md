# 3. Outbox: persist the intention to send

Think of Outbox as a business service's own pending-work list. It stores an intention alongside the business transaction, not a delivery receipt. Each service keeps a local sys_mq_message table. Formal schema ownership belongs to Liquibase migrations rather than arbitrary table creation at every application start.

## Step 1: make transaction and tenant explicit

The current template's transaction declaration and tenant check are shown below. The excerpt ends after tenant validation:

<!-- omni:source path="omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/template/ReliableMessageTemplate.java" -->
```java
    @Transactional(propagation = Propagation.REQUIRED)
    public void send(String bindingName, Object payload, Long tenantId, String msgKey) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("可靠消息 tenantId 必须为正整数");
        }
```

REQUIRED joins an existing transaction or opens its own when none exists. Business atomicity requires an effective business transaction on the same data source and an actual call through the Spring transaction proxy. Cross-database writes, post-processing after a transaction ended, or direct internal calls do not replace these conditions.

Pass a positive tenantId explicitly rather than guessing from ThreadLocal. The relay works across tenants after the request thread has gone, so the message must carry its own correct tenant.

## Step 2: store the initial state

These are the actual assignments before insertion:

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

A new record starts PENDING, with failure count 0 and default maximum failure count 3. serviceName identifies the source, and producerTraceId keeps an available correlation clue. Successful insertion only proves that the list contains the record.

msgId identifies this message record. msgKey is a business key for investigation, not an automatically enforced unique idempotency constraint. Repeated calls to send can create new records; business entry points still need request idempotency.

## Step 3: verify commit and sending separately

In a legitimate business transaction, verify that rollback leaves neither the business change nor a pending Outbox record, and that commit leaves the record. Then separately verify relay sending, consumer processing, and duplicate consumption. Inserting a demonstration row alone proves no business-transaction atomicity.

Next, follow [claiming, retries, and consumption](mq-relay.en.md).
