# 4. 중계와 소비: 재시도와 중복 모두에 대비하기

XXL-JOB의 mqRelayHandler는 기본 스케줄상 약 10초마다 실행됩니다. 처리 시간이 된 PENDING/FAILED와 임대가 만료된 PROCESSING을 배치당 최대 100개 조회합니다. 실행 주기는 전달 기한이 아니며 적체, 장애, 백오프가 대기를 늘립니다.

## 전송보다 선점이 먼저다

조회는 후보를 찾는 것뿐입니다. 실제 선점 코드는 다음과 같습니다.

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

조건부 갱신에 성공한 실행만 계속할 수 있고 PROCESSING 임대는 120초입니다. CAS는 “상태가 아직 예상과 같을 때만 변경”한다는 의미입니다. 성공·실패 갱신에도 조건이 있어야 이전 실행기가 새 결과를 덮어쓰지 않습니다.

임대는 동시 충돌을 줄이지만 중복을 없애지는 못합니다. Broker가 받은 뒤 SENT 저장 전에 프로세스가 종료되면 임대 만료 후 다시 전달될 수 있습니다. 최소 한 번 전달을 목표로 하므로 소비자는 멱등이어야 합니다.

## 실패 후 계속 빠르게 보내지 않는 이유

일반 실패 분기의 실제 백오프 계산입니다.

<!-- omni:source path="omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/relay/MqMessageRelayService.java" -->
```java
            int backoffSeconds = (int) Math.pow(2, newRetryCount) * BACKOFF_BASE_SECONDS;
            LocalDateTime nextRetryTime = now.plusSeconds(backoffSeconds);
            sysMqMessageMapper.casApplyFailure(msg.getId(), SysMqMessage.STATUS_FAILED,
                    nextRetryTime, sanitized, now);
```

기본 maxRetry=3이면 첫 실패 후 20초, 두 번째 후 40초를 기다리고 세 번째는 DEAD_LETTER가 되어 80초 대기를 잡지 않습니다. 시간이 되어도 다음 스케줄 선점을 기다려야 하므로 “10초 이내 전달” 보장은 없습니다. 알 수 없는 broker_type도 실패 상태 처리로 들어가며 거짓 PENDING에 머물지 않습니다.

## 전송 성공 후에도 소비자는 실패할 수 있다

Base 작업 로그 소비자의 전체 진입점입니다.

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

eventId 고유 제약이 중복 삽입을 거부합니다. DuplicateKeyException만 이미 처리한 것으로 보며 다른 예외는 Broker 재시도를 위해 다시 던집니다. Outbox 중계 재시도와 소비자 재시도는 다른 단계입니다. SENT만으로 최종 작업 기록 저장을 증명할 수 없습니다.

다음은 [관리 화면과 안전한 복구](mq-practice.kr.md)입니다.
