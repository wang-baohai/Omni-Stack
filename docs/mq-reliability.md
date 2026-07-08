# Reliable Message Sending (Transactional Outbox)

Omni-Stack guarantees at-least-once message delivery through a **Transactional Outbox** pattern backed by **XXL-JOB** relay scheduling. Instead of sending messages directly to MQ (which risks data loss on transaction rollback), the system writes a PENDING record to a local `sys_mq_message` table within the same database transaction. A background relay job then asynchronously delivers the message to the actual MQ broker.

## 1. Architecture Overview

```
┌────────────────────────────────────────────────────────────────────────────┐
│                        Business Service (e.g., omni-auth)                 │
│                                                                            │
│  @OperLog method ──> OperLogAspect ──> OperLogProducer                     │
│                                            │                               │
│                                            ▼                               │
│                                    ReliableMessageRelay (interface)         │
│                                            │                               │
│  ┌─────────────────────────────────────────┼─────────────────────────────┐ │
│  │            omni-common-mqlog            │                             │ │
│  │                                         ▼                             │ │
│  │                              ReliableMessageTemplate                   │ │
│  │                                  │                                     │ │
│  │                    INSERT sys_mq_message (PENDING)                      │ │
│  │                    [same local transaction]                             │ │
│  └─────────────────────────────────────────┼─────────────────────────────┘ │
└────────────────────────────────────────────┼───────────────────────────────┘
                                             │
┌────────────────────────────────────────────┼───────────────────────────────┐
│  XXL-JOB Scheduler (every 10s)            ▼                               │
│                                                                            │
│  MqMessageRelayJob (@XxlJob + @SystemJobMeta)                              │
│       │                                                                    │
│       ▼                                                                    │
│  MqMessageRelayService                                                     │
│       │ fetchPendingMessages()  ──> PENDING / FAILED & backoff expired     │
│       │                                                                    │
│       ▼ relayOne(msg)                                                      │
│  MessageSender strategy ──> RocketMqMessageSender (StreamBridge)          │
│       │                                                                    │
│       ├── Success  ──> status = SENT                                       │
│       └── Failure  ──> retry_count++, exponential backoff                  │
│                         └── exceeds max_retry ──> DEAD_LETTER              │
└────────────────────────────────────────────────────────────────────────────┘
```

**Module dependencies**:

| Module | Role |
|--------|------|
| `omni-common-core` | Defines `ReliableMessageRelay` interface (pure POJO, zero Spring dependencies) |
| `omni-common-mqlog` | Implements Outbox pattern: `ReliableMessageTemplate`, `MqMessageRelayService`, `MqMessageRelayJob`, `MessageSender`, auto-configuration |
| `omni-common-operlog` | Optional caller: `OperLogProducer` uses `ReliableMessageRelay` when available, falls back to direct `StreamBridge` otherwise |
| `omni-base` | External admin controller `MqMessageController` for frontend management UI |

**Key design decisions**:

- **Why Outbox over direct send?** Direct MQ send inside a transaction creates a distributed transaction problem — if the MQ send succeeds but the DB transaction rolls back, the consumer receives a phantom message. Outbox pattern keeps everything in a single local transaction.
- **Why XXL-JOB as relay engine?** The project already uses XXL-JOB for scheduling. Reusing it for message relay avoids introducing a separate polling daemon. Each service's executor AppName is different, so the same `mqRelayHandler` name is naturally isolated across services.
- **Why explicit tenantId parameter?** Tenant isolation must be guaranteed at the API level — implicit ThreadLocal resolution is fragile and error-prone. Every caller must pass `tenantId` explicitly.

## 2. Message Lifecycle

### 2.1 Status Machine

```
                         ┌──────────┐
                INSERT   │          │   relay success
  ─────────────────────> │ PENDING  │ ──────────────────> SENT
                         │  (0)     │                      (1)
                         │          │
                         └────┬─────┘
                              │ relay failure
                              ▼
                         ┌──────────┐
                         │          │   retry < max_retry
                         │ FAILED   │ ──────────────────> back to PENDING
                         │  (2)     │   (with next_retry_time = now + 2^count × 10s)
                         │          │
                         └────┬─────┘
                              │ retry >= max_retry
                              ▼
                         ┌──────────┐
                         │DEAD_LETTER│
                         │  (3)     │
                         └────┬─────┘
                              │
                    ┌─────────┴─────────┐
                    │ resend             │ skip
                    ▼                    ▼
               PENDING (0)         SKIPPED (4)
```

| Status | Code | Description |
|--------|------|-------------|
| PENDING | 0 | Awaiting delivery or ready for retry |
| SENT | 1 | Successfully delivered to MQ (terminal) |
| FAILED | 2 | Delivery failed, waiting for next retry (backoff) |
| DEAD_LETTER | 3 | Exceeded max retry count (terminal, manual action required) |
| SKIPPED | 4 | Manually marked as ignored by admin (terminal) |

### 2.2 Write Path

`ReliableMessageTemplate` implements the `ReliableMessageRelay` interface:

```java
// Called by OperLogProducer or any business code
reliableMessageRelay.send("oper-log-out-0", operLogMessage, tenantId);
reliableMessageRelay.send("order-out-0", orderPayload, tenantId, "order:12345");
```

Under the hood:
1. Generate UUID as `msgId` (deduplication key)
2. Serialize payload to JSON via `ObjectMapper`
3. Build `SysMqMessage` record with `status = PENDING`, `brokerType = "rocketmq"`, `tenantId`, `serviceName`
4. INSERT into `sys_mq_message` via MyBatis-Plus (within `@Transactional(REQUIRED)`)

### 2.3 Relay Path

`MqMessageRelayJob` is scheduled by XXL-JOB (default: every 10 seconds, FIRST route strategy):

1. `fetchPendingMessages()` — SELECT records where `status IN (PENDING, FAILED)` AND (`next_retry_time IS NULL` OR `next_retry_time <= NOW()`), ordered by `create_time ASC`, LIMIT 100
2. For each message, look up the `MessageSender` by `broker_type` from the sender map
3. Call `sender.send(msg)` — on success, mark SENT; on failure, increment retry count and apply exponential backoff
4. If `retryCount >= maxRetry`, mark as DEAD_LETTER

### 2.4 Retry Strategy

Exponential backoff formula: **2^retryCount × 10 seconds**

| Retry | Backoff | Next Retry After |
|-------|---------|-----------------|
| 1 | 2^1 × 10 = 20s | ~20 seconds |
| 2 | 2^2 × 10 = 40s | ~40 seconds |
| 3 | 2^3 × 10 = 80s | ~80 seconds |

Default `max_retry = 3`. After 3 failures the message enters DEAD_LETTER status.

### 2.5 Dead Letter Handling

Dead letters require manual admin intervention via the frontend management UI (`运维监控 → 消息记录`):

- **Resend** (`POST /api/base/mq-message/{msgId}/resend`): Resets status to PENDING, clears retry count and backoff timer. The relay job will pick it up on the next poll.
- **Skip** (`POST /api/base/mq-message/{msgId}/skip`): Transitions DEAD_LETTER → SKIPPED, acknowledging the message will not be delivered.

## 3. Tenant Isolation

### 3.1 Design: Explicit Parameter (No ThreadLocal)

The `ReliableMessageRelay.send()` method requires an explicit `Long tenantId` parameter. This is a deliberate design choice:

- **No ThreadLocal magic**: ThreadLocal-based tenant resolution is fragile — it can be lost across async boundaries, thread pool handoffs, or scheduled tasks.
- **Compile-time safety**: Missing the parameter causes a compilation error, not a silent runtime bug.
- **Caller responsibility**: Each caller extracts tenantId from its own context (e.g., `OperLogMessage.getTenantId()`, `@RequestHeader("X-Tenant-Id")`).

```java
// Correct: explicit tenantId
reliableMessageRelay.send("oper-log-out-0", message, message.getTenantId());

// WRONG: would fail to compile if tenantId is omitted
reliableMessageRelay.send("oper-log-out-0", message);
```

### 3.2 Write: tenantId in Outbox Record

`ReliableMessageTemplate.send()` sets `message.setTenantId(tenantId)` before inserting into `sys_mq_message`. The `tenant_id` column has an index (`idx_tenant_time`) for efficient tenant-scoped queries.

### 3.3 Read: All Query Controllers Filter by tenantId

- **External controller** (`MqMessageController` in `omni-base`): Uses `@RequestHeader("X-Tenant-Id")` to get the tenant ID. All queries include `.eq(SysMqMessage::getTenantId, tenantId)`.
- **Internal controller** (`MqMessageInternalController` in `omni-common-mqlog`): Uses `@RequestParam Long tenantId` for Feign-based cross-service aggregation.

### 3.4 Relay: No Tenant Filter (Intentional)

`MqMessageRelayService` scans ALL PENDING/FAILED messages regardless of `tenant_id`. This is by design — the relay is a background infrastructure process that must deliver all messages. Tenant isolation applies only to user-facing read/write operations.

## 4. Constraints & Pitfalls

### 4.1 tenantId Must Be Explicit

Never introduce a ThreadLocal or SecurityContext-based tenant resolution for `ReliableMessageRelay.send()`. The explicit parameter is the contract — it prevents silent NULL tenantId bugs.

### 4.2 All Query Interfaces Must Filter

Every new endpoint that queries `sys_mq_message` MUST include a `tenantId` filter. No exceptions — even "admin" or "internal" endpoints must respect tenant boundaries.

### 4.3 Idempotent DDL

`schema.sql` uses `CREATE TABLE IF NOT EXISTS` for safe, idempotent table creation. The table is auto-created on service startup when `omni-common-mqlog` is on the classpath. No manual DDL execution is required.

### 4.4 MessageSender Strategy Pattern

Adding a new MQ broker (e.g., Kafka) requires:
1. Implement the `MessageSender` interface: `brokerType()` returns `"kafka"`, `send(SysMqMessage)` handles the actual delivery.
2. Register as a Spring Bean — `MqLogAutoConfiguration` collects all `MessageSender` beans into a map keyed by `brokerType`.
3. Set `broker_type = "kafka"` when calling `ReliableMessageTemplate.send()` (or use the appropriate binding).

No changes to `MqMessageRelayService` or relay logic are needed.

## 5. New Service Onboarding (Tutorial)

To add reliable MQ message sending to a new service (e.g., `omni-order`):

### Step 1: Add Dependency

In `omni-order/pom.xml`:

```xml
<dependency>
    <groupId>com.omni</groupId>
    <artifactId>omni-common-mqlog</artifactId>
    <version>${project.version}</version>
</dependency>
```

This automatically pulls in `omni-common-core` (for the `ReliableMessageRelay` interface) and `omni-common-mybatis` (for `SysMqMessageMapper`).

### Step 2: Table Auto-Creation

`schema.sql` runs automatically on startup (`CREATE TABLE IF NOT EXISTS sys_mq_message`). Verify:

```sql
SELECT COUNT(*) FROM sys_mq_message;
```

### Step 3: Inject and Use

In your business service:

```java
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final ReliableMessageRelay reliableMessageRelay;

    @Transactional
    public void createOrder(OrderDTO dto, Long tenantId) {
        // ... business logic ...
        Order order = orderMapper.insert(entity);

        // Write to outbox — same transaction, guaranteed atomicity
        reliableMessageRelay.send("order-out-0", order, tenantId, "order:" + order.getId());
    }
}
```

### Step 4: Verify Relay Job Registration

Start the service and check XXL-JOB admin console (`http://localhost:18080`):
- Executor: your service's AppName should appear in the executor list
- Task: `mqRelayHandler` should be registered with cron `0/10 * * * * ?`
- Start the task if not already running

### Step 5: Check Frontend Admin UI

Navigate to `运维监控 → 消息记录` in the frontend:
- New messages should appear with `status = PENDING` (0) then transition to `SENT` (1) after relay
- Filter by `tenantId`, `status`, `topic`, `serviceName`, or time range
- Resend failed messages or skip dead letters

## 6. Extension Guide

### 6.1 Adding a New MQ Broker

Implement the `MessageSender` interface:

```java
@Component
public class KafkaMessageSender implements MessageSender {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public String brokerType() {
        return "kafka";
    }

    @Override
    public void send(SysMqMessage msg) {
        kafkaTemplate.send(msg.getTopic(), msg.getMsgKey(), msg.getPayload()).get();
    }
}
```

`MqLogAutoConfiguration` auto-collects all `MessageSender` beans. The relay service routes by `broker_type` column — no relay code changes needed.

### 6.2 Customizing Retry Strategy

Current retry parameters are hardcoded in `MqMessageRelayService`:

| Parameter | Location | Default |
|-----------|----------|---------|
| `BATCH_SIZE` | `MqMessageRelayService` | 100 |
| `BACKOFF_BASE_SECONDS` | `MqMessageRelayService` | 10 |
| `max_retry` | `SysMqMessage.maxRetry` | 3 |

To customize per-message: set `maxRetry` when calling `ReliableMessageTemplate.send()` (requires extending the interface). To customize globally: override `MqMessageRelayService` bean with custom parameters.

### 6.3 Customizing Relay Schedule

The relay job's cron expression is configurable in XXL-JOB admin console. Default is `0/10 * * * * ?` (every 10 seconds). Adjust based on your throughput and latency requirements.
