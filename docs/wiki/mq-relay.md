# 4. 中继与消费：允许重试，也防止重复伤害

中继由XXL-JOB的mqRelayHandler触发，默认调度表达式约每10秒执行一轮。它读取已到时间的PENDING/FAILED，以及租约过期的PROCESSING候选；每批最多100条。调度周期不是送达时限，积压、故障和退避都会增加等待。

## 先领取，再发送

扫描只是找到候选，不能拿到记录就直接发。实际领取代码：

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

只有条件更新成功的一方能继续，PROCESSING有120秒租约。CAS可以理解为“状态仍符合预期才允许修改”。成功与失败写回也要带前置条件，避免旧执行器覆盖新结果。

租约减少并发冲突，但不消灭所有重复。例如Broker已接受、SENT写回前进程崩溃，租约过期后仍可能再投递。因此可靠性目标是至少一次，消费者必须幂等。

## 失败为什么不会一直快速重发

普通失败分支的实际退避计算如下：

<!-- omni:source path="omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/relay/MqMessageRelayService.java" -->
```java
            int backoffSeconds = (int) Math.pow(2, newRetryCount) * BACKOFF_BASE_SECONDS;
            LocalDateTime nextRetryTime = now.plusSeconds(backoffSeconds);
            sysMqMessageMapper.casApplyFailure(msg.getId(), SysMqMessage.STATUS_FAILED,
                    nextRetryTime, sanitized, now);
```

默认maxRetry=3时，第1次失败等20秒，第2次等40秒，第3次进入DEAD_LETTER，不再安排80秒。到期后还要等调度领取；这不是“最多10秒送达”。未知broker_type也走失败状态机，不能一直假装PENDING。

## 发送成功以后，消费者仍可能失败

Base操作日志消费者的完整入口：

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

eventId的唯一约束拒绝重复入库，只有DuplicateKeyException被当作已经处理；其他错误重新抛出，交给Broker重试。Outbox中继重试与消费者重试是不同阶段，不能只看SENT就断言最终记录已经保存。

下一篇把这些状态映射到[管理页面与安全恢复](mq-practice.md)。
