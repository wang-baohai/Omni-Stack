# 메시지 신뢰성(Transactional Outbox)

이 문서는 현재 등록, 전달, 관리, 소비 경계를 정의합니다. 단계와 실제 화면은 [메시지 Wiki](wiki/outbox-development.kr.md), 중단점과 경합 테스트는 [소스 디버깅](wiki/source-debugging.kr.md#outbox)을 참고하세요.

## 1. 구성

common-core는 ReliableMessageRelay를 정의하고 common-mqlog는 로컬 Outbox, XXL-JOB 중계와 전송 전략을 제공합니다. Base 관리 집계는 현재 Base와 CRM만 포함하며 모든 서비스의 Outbox를 표시하지 않습니다.

- 등록 시 고유 msgId 생성, payload 직렬화, 양수 tenantId 검증 후 해당 서비스의 sys_mq_message에 저장합니다. 현재 broker_type은 rocketmq로 고정됩니다.
- 인자 4개 send는 REQUIRED를 사용합니다. 업무와 Outbox 원자 커밋에는 같은 데이터소스와 유효한 로컬 트랜잭션이 필요합니다. Controller @OperLog 후처리가 이미 종료된 업무 트랜잭션과의 원자성을 보장하지는 않습니다.
- 현재 템플릿은 topic에 bindingName을 저장합니다. 실제 RocketMQ destination은 Stream binding이 선택하며 Base의 operlog-out-0는 operlog-topic에 연결됩니다.
- 최소 1회 전달을 목표로 합니다. 전송 후 SENT 갱신 전 중단되면 중복될 수 있으므로 소비자 멱등성이 필요합니다. 백오프, 적체, 중단, 데드레터 처리가 지연에 영향을 주며 일률적인 최대10초는 보장하지 않습니다.

## 2. 수명주기

| 상태 | 코드 | 허용 동작 |
|---|---|---|
| PENDING | 0 | 기한 후 획득 가능. 관리 재전송은 거부 |
| SENT | 1 | 전송 전략 성공. 업무 소비 완료와 별개 |
| FAILED | 2 | 기한 후 직접 재획득. 먼저 PENDING으로 바꾸지 않음 |
| DEAD_LETTER | 3 | 자동 재시도 중지. 수정 후 재전송 또는 건너뛰기 |
| SKIPPED | 4 | 관리자가 전달 중지. 재전송과 반복 건너뛰기 거부 |
| PROCESSING | 5 | 원자 획득 후 120초 임대. 만료되면 재획득 가능 |

배치당 최대 100개를 생성순으로 선택합니다. 기한이 지난 PENDING/FAILED와 임대가 만료된 PROCESSING이 대상입니다. 획득과 결과 갱신은 CAS를 사용합니다. 획득 실패 실행자는 전송하지 않으며 갱신 0건이면 새 상태를 덮어쓰지 않습니다.

실패 횟수는 데이터베이스에서 원자적으로 증가합니다. 기본 max_retry=3은 세 번째 실패에서 데드레터로 전환한다는 뜻입니다. 첫 번째 실패는 20초, 두 번째는 40초, 세 번째는 next_retry_time을 비우며 80초를 기다리지 않습니다. 알 수 없는 broker_type도 같은 상태 머신을 따르고 실제 실행은 스케줄러 순회도 기다립니다.

- `POST /api/base/mq-message/{msgId}/resend`: FAILED/DEAD_LETTER만 허용. CAS로 PENDING, retry_count=0을 설정하고 백오프와 오류를 제거합니다. base:mqmessage:resend와 비동기 전달 활성화가 필요합니다.
- `POST /api/base/mq-message/{msgId}/skip`: DEAD_LETTER → SKIPPED만 허용. base:mqmessage:skip이 필요합니다.
- 상태 변경 또는 종결 동작 반복은 HTTP200/업무400, 권한 부족은 HTTP403/업무403입니다. 확인창이나 요청 접수 알림은 전달 완료를 뜻하지 않습니다.
- mqRelayHandler에는 @XxlJob + @SystemJobMeta가 필요합니다. 준비 완료 후 기본 cron `0/10 * * * * ?`로 등록·시작하며 등록 실패 시 10초마다 최대 12회 재시도합니다. 서비스마다 executor AppName이 다릅니다.

## 3. 테넌트 격리

send는 명시적인 양수 Long tenantId가 필요하며 ThreadLocal에서 추정하지 않습니다. 외부 관리는 신뢰된 X-Tenant-Id, 인증된 내부 조회는 명시적 tenantId를 사용합니다. 관리자도 사용자 대상 조회, 재전송, 건너뛰기에 테넌트 조건을 적용해야 합니다.

인프라 중계는 모든 테넌트를 조회합니다. SRM/조달/자산 TenantLine은 sys_mq_message를 제외하여 다른 테넌트 전달을 막지 않아야 합니다.

## 4. 제약

- 유일한 공식 DDL은 [common-mq-0001-schema](../database/changelog/common/0001-mq-schema.yaml)입니다. 이력과 사전조건으로 재실행을 제어하며 실행한 changeSet은 수정하지 않습니다. 이전 schema.sql은 역사 참고용입니다.
- MessageSender는 broker_type으로 선택합니다. 새 전략뿐 아니라 등록 선택 계약도 확장해야 하며 기존 send는 rocketmq 외의 브로커를 선택할 수 없습니다.
- StreamBridge는 업무 중복 제거를 보장하지 않습니다. 작업 로그는 고유 eventId로 중복 제거합니다. OperLogConsumer는 DuplicateKeyException만 무시하고 나머지 실패는 Broker 재시도를 위해 다시 던집니다. 자산 수령 등은 각 도메인의 Inbox 계약을 따릅니다.
- producer_trace_id는 등록 당시 MDC 값이며 null일 수 있습니다. 중계는 별도 Trace를 만들고 omniMessageId와 사용 가능한 omniProducerTraceId를 헤더로 전달합니다. 동기 부모/자식 Span을 꾸미지 않습니다.
- 지표 태그는 고정 destination/result/status만 사용하며 테넌트, 사용자, 업무 키, payload는 넣지 않습니다. 관리 포트, 샘플링, 내보내기는 [관측성](observability.kr.md)을 참고하세요.
- 비동기 전달을 꺼도 Outbox 등록은 가능합니다. 미전달을 유실로 판단하기 전에 runtime의 deliveryEnabled를 확인하세요.

## 5. 소스와 검증

| 심벌 | 소스 |
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

[mq-teaching.flows.spec.ts](../omni-frontend/e2e-docs/flows/mq-teaching.flows.spec.ts)와 [통제 fixture](../scripts/fixtures/run-mq-teaching-fixture.ps1)가 실제 단계를 검증합니다. 합성 Outbox 행은 업무와 등록 트랜잭션 원자성을 증명하지 않습니다. 기본 재시도, 공식 관리 동작, Broker 소비 완료, 중복 제거를 각각 확인해야 합니다.
