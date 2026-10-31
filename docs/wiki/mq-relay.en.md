# 4. Relay and consumption: retry safely

XXL-JOB triggers mqRelayHandler, whose default schedule runs about every 10 seconds. It scans due PENDING/FAILED records and PROCESSING records with expired leases, up to 100 candidates per batch. The schedule is not a delivery deadline: backlog, failures, and backoff add waiting time.

## Claim before sending

Scanning only finds candidates. The actual claim is:

<!-- omni:source path="omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/relay/MqMessageRelayService.java" -->
```java
        int claimed = sysMqMessageMapper.claimForRelay(msg.getId(), LocalDateTime.now(),
                LocalDateTime.now().plusSeconds(CLAIM_LEASE_SECONDS));
        if (claimed == 0) {
            // 抢占失败：消息已被其他 executor 持有（租约未到期）或状态已变化，本轮直接跳过。
            log.debug("MQ 中继：抢占失败（已被其他执行流持有或状态已变），跳过 msgId={}", msg.getMsgId());
            return;
        }
```

Only a successful conditional update lets that execution continue. PROCESSING has a 120-second lease. CAS means “change it only if the state still matches.” Success and failure updates also require preconditions so an old executor cannot overwrite a newer result.

Leases reduce concurrent conflicts but cannot remove all duplicates. If the Broker accepted a message and the process died before recording SENT, an expired lease can lead to another delivery. The target is at-least-once delivery, so consumers must be idempotent.

## Why failed sends do not retry continuously

The ordinary failure branch computes backoff as follows:

<!-- omni:source path="omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/relay/MqMessageRelayService.java" -->
```java
            int backoffSeconds = (int) Math.pow(2, newRetryCount) * BACKOFF_BASE_SECONDS;
            LocalDateTime nextRetryTime = now.plusSeconds(backoffSeconds);
            sysMqMessageMapper.casApplyFailure(msg.getId(), SysMqMessage.STATUS_FAILED,
                    nextRetryTime, sanitized, now);
```

With default maxRetry=3, the first failure waits 20 seconds, the second 40, and the third enters DEAD_LETTER without scheduling an 80-second wait. Once due, it still waits for a scheduled claim. There is no “delivered within 10 seconds” guarantee. An unknown broker_type also enters the failure state machine rather than staying deceptively PENDING.

## Consumers can fail after sending succeeds

The complete Base operation-log consumer entry point is:

<!-- omni:source path="omni-backend/omni-base/src/main/java/com/omni/base/consumer/OperLogConsumer.java" -->
```java
    @Bean
    public Consumer<OperLogMessage> operlogConsumer() {
        return message -> {
            try {
                operLogService.save(message);
            } catch (DuplicateKeyException e) {
                log.debug("操作日志事件已消费，忽略重复消息: {}", message.getEventId());
            } catch (Exception e) {
                log.error("操作日志消费失败，交由消息中间件重试: {}", e.getMessage(), e);
                throw e;
            }
        };
    }
```

A unique eventId constraint rejects duplicate inserts. Only DuplicateKeyException is treated as already processed; other errors are rethrown for Broker retry. Relay retries and consumer retries are separate stages. SENT alone does not prove the final operation record was saved.

Next, map the states to [management and safe recovery](mq-practice.en.md).
