# 4. Operation logs: record actions and protect sensitive data

Runtime logs explain technical processing; operation logs record business actions. The project declares collection points with @OperLog. An aspect collects necessary information, calculates changes, sanitizes content, and hands it to a producer. Auth writes authentication events to sys_audit_log and does not depend on the operation-log module.

## Why an annotation reduces repetition

The aspect provides shared processing around a business method; the original method still performs the action. Calls must pass through the Spring proxy. A direct call between methods in the same instance does not automatically trigger it. Missing snapshot configuration, unreadable entities, or no readable changes can leave an empty difference.

## How records reach the query page

This is the operation-log producer's complete send method:

<!-- omni:source path="omni-backend/omni-common-operlog/src/main/java/com/omni/common/operlog/producer/OperLogProducer.java" -->
```java
    public void send(OperLogMessage message) {
        try {
            Long tenantId = message.getTenantId();
            if (reliableRelay != null) {
                reliableRelay.send(BINDING_NAME, message, tenantId, message.getEventId());
            } else {
                streamBridge.send(BINDING_NAME, message);
            }
        } catch (Exception e) {
            log.warn("操作日志：MQ 消息发送失败: {}", e.getMessage(), e);
        }
    }
```

It prefers Outbox when the reliable-message component is present; direct sending is used only when that component is absent. Notice the catch: a sending failure is logged rather than automatically changing the business response. A failed Outbox insert does not secretly fall back to direct sending. This cannot prove that every successful business action has a log, or that controller post-processing is atomic with an already-ended business transaction.

## Redaction must happen before writing

The sanitizer explicitly lists fields that must be hidden:

<!-- omni:source path="omni-backend/omni-common-operlog/src/main/java/com/omni/common/operlog/sanitize/OperLogSanitizer.java" -->
```java
    private static final Set<String> REDACTED_FIELDS = Set.of(
            "password", "passwd", "pwd", "credential", "authorization", "token",
            "accesstoken", "refreshtoken", "secret", "clientsecret", "idcard",
            "identityno", "address", "content", "remark", "remarks", "note", "notes",
            "description", "customername", "contactname", "leadname", "companyname",
            "fullname", "opportunityname", "subject", "reason"
    );
```

It also handles phone numbers, email addresses, and common credential text. Snapshots, parameters, and exceptions must use the applicable sanitization path. A field list cannot recognize every business secret; new sensitive fields need maintained rules and negative tests. JSON formatting alone proves no redaction.

Do not log passwords, tokens, cookies, Authorization, or complete request/message bodies for convenience. Runtime logs, database audit, screenshots, and exports each have their own boundaries. Cleaning one output does not make another safe.

Next, use [the real page to understand queries and recovery](logging-practice.en.md).
