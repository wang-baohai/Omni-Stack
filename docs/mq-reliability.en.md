# Reliable messaging (Transactional Outbox)

This reference defines enqueue, delivery, administration and consumption boundaries. Use the [messaging Wiki](wiki/outbox-development.en.md) for steps and real screenshots, and [source debugging](wiki/source-debugging.en.md#outbox) for breakpoints and concurrency tests.

## 1. Architecture

common-core defines ReliableMessageRelay; common-mqlog implements the local Outbox, XXL-JOB relay and send strategies. The Base administration aggregate currently covers Base and CRM only; it does not display every service's Outbox.

- Enqueue creates a unique msgId, serializes payload, validates a positive tenantId and inserts into the service's sys_mq_message. broker_type is currently fixed to rocketmq.
- The four-argument send uses REQUIRED. Atomic business/Outbox commit requires the same datasource and an effective local transaction. Controller @OperLog post-processing does not establish atomicity with a business transaction that has already ended.
- The template currently stores bindingName in topic. The Stream binding selects the actual RocketMQ destination; Base maps operlog-out-0 to operlog-topic.
- Delivery targets at least once. A crash after sending but before marking SENT can cause a duplicate; consumers must be idempotent. Backoff, backlog, downtime and dead-letter handling affect latency; there is no universal ten-second maximum.

## 2. Lifecycle

| State | Code | Allowed behavior |
|---|---|---|
| PENDING | 0 | Claim when due; administration resend rejects this state |
| SENT | 1 | Send strategy returned successfully; consumption may still be incomplete |
| FAILED | 2 | Claim again when due without first writing PENDING |
| DEAD_LETTER | 3 | Automatic retry stops; repair then resend, or skip |
| SKIPPED | 4 | Administrator stopped delivery; resend and skip reject it |
| PROCESSING | 5 | Atomic claim with a 120-second lease; expired leases can be reclaimed |

Each batch selects at most 100 records in creation order: due PENDING/FAILED and expired PROCESSING. Claim and outcome updates use CAS. An executor that did not claim must not send; an outcome update that matches no row must not overwrite newer state.

The database atomically increments the failure count. Default max_retry=3 means the third failure dead-letters the message: failure 1 schedules 20 seconds, failure 2 schedules 40 seconds, and failure 3 clears next_retry_time without an 80-second wait. Unknown broker_type follows the same state machine. Actual execution also depends on scheduler polling.

- `POST /api/base/mq-message/{msgId}/resend`: FAILED/DEAD_LETTER only; CAS resets PENDING, retry_count=0 and clears backoff/error. Requires base:mqmessage:resend and enabled asynchronous delivery.
- `POST /api/base/mq-message/{msgId}/skip`: DEAD_LETTER → SKIPPED only; requires base:mqmessage:skip.
- Changed state or repeated terminal actions return HTTP200/business400; missing permission returns HTTP403/business403. A confirmation dialog or submitted-request toast does not prove delivery completion.
- mqRelayHandler requires @XxlJob + @SystemJobMeta. The registrar creates and starts it after readiness with default cron `0/10 * * * * ?`, retrying registration every 10 seconds up to 12 times. Each service has its own executor AppName.

## 3. Tenant isolation

send requires an explicit positive Long tenantId, never inferred from ThreadLocal. External administration uses trusted X-Tenant-Id; authenticated internal queries accept tenantId explicitly. User-facing queries, resend and skip must constrain the tenant, including administrator requests.

The infrastructure relay intentionally scans all tenants. SRM/Procurement/Asset TenantLine must exclude sys_mq_message so other tenants' messages remain eligible.

## 4. Constraints

- [common-mq-0001-schema](../database/changelog/common/0001-mq-schema.yaml) is the sole formal DDL. Migration history and preconditions control repetition; never edit an applied changeSet. Legacy schema.sql is historical reference.
- MessageSender routes by broker_type. Adding a strategy also requires extending enqueue selection; existing send cannot select a non-rocketmq broker.
- StreamBridge does not guarantee business deduplication. Operation logs deduplicate by unique eventId; OperLogConsumer ignores DuplicateKeyException only and rethrows other failures for Broker retry. Domain Inbox contracts govern asset receipts and other consumers.
- producer_trace_id comes from enqueue-time MDC and may be null. Relay creates another Trace. Headers carry omniMessageId and available omniProducerTraceId; do not fabricate a synchronous parent/child Span.
- Metrics use fixed destination/result/status labels, excluding tenants, usernames, business keys and payload. See [observability](observability.en.md) for management ports, sampling and export.
- Outbox-only mode still accepts enqueue. Check runtime deliveryEnabled before diagnosing undelivered messages as loss.

## 5. Source and verification

| Symbol | Source |
|---|---|
| ReliableMessageTemplate.send | [ReliableMessageTemplate.java](../omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/template/ReliableMessageTemplate.java) |
| relayPendingMessages / relayOne / handleSendFailure | [MqMessageRelayService.java](../omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/relay/MqMessageRelayService.java) |
| claim / CAS | [SysMqMessageMapper.java](../omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/mapper/SysMqMessageMapper.java) |
| RocketMqMessageSender.send | [RocketMqMessageSender.java](../omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/sender/RocketMqMessageSender.java) |
| MqRelayJobRegistrar | [MqRelayJobRegistrar.java](../omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/relay/MqRelayJobRegistrar.java) |
| resend / skip | [MqMessageAggregationServiceImpl.java](../omni-backend/omni-base/src/main/java/com/omni/base/service/impl/MqMessageAggregationServiceImpl.java) |
| eventId | [OperLogConsumer.java](../omni-backend/omni-base/src/main/java/com/omni/base/consumer/OperLogConsumer.java), [OperLogServiceImpl.java](../omni-backend/omni-base/src/main/java/com/omni/base/service/impl/OperLogServiceImpl.java) |
| Trace / Metric | [MqLogMetrics.java](../omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/metrics/MqLogMetrics.java) |

[MqMessageRelayServiceTest](../omni-backend/omni-common-mqlog/src/test/java/com/omni/common/mqlog/relay/MqMessageRelayServiceTest.java) · [CAS](../omni-backend/omni-common-mqlog/src/test/java/com/omni/common/mqlog/relay/MqMessageRelayServiceCasTest.java) · [UnknownBroker](../omni-backend/omni-common-mqlog/src/test/java/com/omni/common/mqlog/relay/MqMessageRelayServiceUnknownBrokerTest.java) · [AggregationCas](../omni-backend/omni-base/src/test/java/com/omni/base/service/impl/MqMessageAggregationCasTest.java)

[mq-teaching.flows.spec.ts](../omni-frontend/e2e-docs/flows/mq-teaching.flows.spec.ts) and the [controlled fixture](../scripts/fixtures/run-mq-teaching-fixture.ps1) verify separate real lifecycle stages. Synthetic Outbox records do not prove business/enqueue transaction atomicity. Verify default retries, formal administration, completed Broker consumption and deduplication separately.
