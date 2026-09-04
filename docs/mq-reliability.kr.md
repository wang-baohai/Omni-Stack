# 신뢰성 있는 메시지 전송 (Transactional Outbox)

> 본 문서는 Omni-Stack의 Transactional Outbox 패턴 구현을 설명하며, 메시지의 최소 1회 전달을 보장합니다.  
> 아키텍처 개요는 [architecture.kr.md](architecture.kr.md)를 참조하십시오. Docker 배포 구성은 [docker-deployment.kr.md](docker-deployment.kr.md)를 참조하십시오.

Omni-Stack은 **Transactional Outbox** 패턴 + **XXL-JOB** 릴레이 스케줄링을 통해 메시지의 최소 1회 전달을 보장합니다. 시스템은 MQ에 직접 메시지를 전송하는 대신(트랜잭션 롤백 시 데이터 손실을 방지하기 위해), PENDING 레코드를 로컬 `sys_mq_message` 테이블에 기록하고(동일한 데이터베이스 트랜잭션 내에서), 백그라운드 릴레이 작업이 비동기로 MQ Broker에 전달합니다.

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

**모듈 의존관계**:

| 모듈 | 역할 |
|--------|------|
| `omni-common-core` | `ReliableMessageRelay` 인터페이스 정의 (순수 POJO, Spring 의존성 없음) |
| `omni-common-mqlog` | Outbox 패턴 구현: `ReliableMessageTemplate`, `MqMessageRelayService`, `MqMessageRelayJob`, `MessageSender`, 자동 구성 |
| `omni-common-operlog` | 선택적 호출자: `OperLogProducer`는 사용 가능할 때 `ReliableMessageRelay`를 사용하며, 그렇지 않으면 직접 `StreamBridge`로 폴백 |
| `omni-base` | 프론트엔드 관리 UI용 외부 관리 컨트롤러 `MqMessageController` |

**주요 설계 결정**:

- **왜 직접 전송 대신 Outbox인가?** 트랜잭션 내 직접 MQ 전송은 분산 트랜잭션 문제를 발생시킵니다 — MQ 전송이 성공하더라도 DB 트랜잭션이 롤백되면, 컨슈머는 유령 메시지를 수신합니다. Outbox 패턴은 모든 것을 단일 로컬 트랜잭션에 유지합니다.
- **왜 릴레이 엔진으로 XXL-JOB을 사용하는가?** 프로젝트는 이미 스케줄링에 XXL-JOB을 사용하고 있습니다. 메시지 릴레이에 재사용함으로써 별도의 폴링 데몬을 도입할 필요가 없습니다. 각 서비스의 실행자 AppName이 다르므로, 동일한 `mqRelayHandler` 이름은 서비스 간에 자연스럽게 격리됩니다.
- **왜 명시적 tenantId 파라미터인가?** 테넌트 격리는 API 수준에서 보장되어야 합니다 — 암시적 ThreadLocal 해석은 취약하고 오류가 발생하기 쉽습니다. 모든 호출자는 `tenantId`를 명시적으로 전달해야 합니다.

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

| 상태 | 코드 | 설명 |
|--------|------|-------------|
| PENDING | 0 | 전달 대기 또는 재시도 준비 완료 |
| SENT | 1 | MQ로 성공적으로 전달 (종단 상태) |
| FAILED | 2 | 전달 실패, 다음 재시도 대기 중 (백오프) |
| DEAD_LETTER | 3 | 최대 재시도 횟수 초과 (종단 상태, 수동 조치 필요) |
| SKIPPED | 4 | 관리자에 의해 수동으로 무시 표시 (종단 상태) |

### 2.2 Write Path

`ReliableMessageTemplate`은 `ReliableMessageRelay` 인터페이스를 구현합니다:

```java
// OperLogProducer 또는 비즈니스 코드에서 호출됨
reliableMessageRelay.send("oper-log-out-0", operLogMessage, tenantId);
reliableMessageRelay.send("order-out-0", orderPayload, tenantId, "order:12345");
```

내부 처리:
1. UUID를 `msgId`(중복 제거 키)로 생성
2. `ObjectMapper`를 통해 페이로드를 JSON으로 직렬화
3. `SysMqMessage` 레코드 구성: `status = PENDING`, `brokerType = "rocketmq"`, `tenantId`, `serviceName`
4. MyBatis-Plus를 통해 `sys_mq_message`에 INSERT (`@Transactional(REQUIRED)` 내에서)

### 2.3 Relay Path

`MqMessageRelayJob`은 XXL-JOB에 의해 스케줄링됩니다 (기본값: 10초마다, FIRST 라우팅 전략):

1. `fetchPendingMessages()` — `status IN (PENDING, FAILED)` 그리고 (`next_retry_time IS NULL` 또는 `next_retry_time <= NOW()`)인 레코드를 SELECT, `create_time ASC` 순으로 정렬, LIMIT 100
2. 각 메시지에 대해 sender 맵에서 `broker_type`으로 `MessageSender`를 조회
3. `sender.send(msg)` 호출 — 성공 시 SENT로 표시, 실패 시 재시도 카운트 증가 및 지수 백오프 적용
4. `retryCount >= maxRetry`인 경우 DEAD_LETTER로 표시

### 2.4 Retry Strategy

지수 백오프 공식: **2^retryCount × 10초**

| 재시도 | 백오프 | 다음 재시도까지 시간 |
|-------|---------|-----------------|
| 1 | 2^1 × 10 = 20s | 약 20초 |
| 2 | 2^2 × 10 = 40s | 약 40초 |
| 3 | 2^3 × 10 = 80s | 약 80초 |

기본값 `max_retry = 3`. 3회 실패 후 메시지는 DEAD_LETTER 상태가 됩니다.

### 2.5 Dead Letter Handling

데드 레터는 프론트엔드 관리 UI(`운영 모니터링 → 메시지 기록`)를 통해 관리자의 수동 개입이 필요합니다:

- **재전송** (`POST /api/base/mq-message/{msgId}/resend`): 상태를 PENDING으로 초기화하고, 재시도 카운트와 백오프 타이머를 초기화합니다. 릴레이 작업이 다음 폴링에서 처리합니다.
- **건너뛰기** (`POST /api/base/mq-message/{msgId}/skip`): DEAD_LETTER → SKIPPED로 전환하여, 메시지가 전달되지 않음을 확인합니다.

## 3. Tenant Isolation

### 3.1 Design: Explicit Parameter (No ThreadLocal)

`ReliableMessageRelay.send()` 메서드는 명시적인 `Long tenantId` 파라미터를 요구합니다. 이는 의도적인 설계 선택입니다:

- **ThreadLocal 마법 없음**: ThreadLocal 기반 테넌트 해석은 취약합니다 — 비동기 경계, 스레드 풀 전달, 또는 스케줄 작업에서 손실될 수 있습니다.
- **컴파일 시 안전성**: 파라미터가 누락되면 런타임 버그가 아닌 컴파일 오류가 발생합니다.
- **호출자 책임**: 각 호출자는 자체 컨텍스트에서 tenantId를 추출합니다 (예: `OperLogMessage.getTenantId()`, `@RequestHeader("X-Tenant-Id")`).

```java
// 올바른 사용: 명시적 tenantId
reliableMessageRelay.send("oper-log-out-0", message, message.getTenantId());

// 잘못된 사용: tenantId를 생략하면 컴파일 실패
reliableMessageRelay.send("oper-log-out-0", message);
```

### 3.2 Write: tenantId in Outbox Record

`ReliableMessageTemplate.send()`는 `sys_mq_message`에 INSERT하기 전에 `message.setTenantId(tenantId)`를 설정합니다. `tenant_id` 컬럼에는 효율적인 테넌트 범위 쿼리를 위한 인덱스(`idx_tenant_time`)가 있습니다.

### 3.3 Read: All Query Controllers Filter by tenantId

- **외부 컨트롤러** (`omni-base`의 `MqMessageController`): 테넌트 ID 획득에 `@RequestHeader("X-Tenant-Id")`를 사용합니다. 모든 쿼리에 `.eq(SysMqMessage::getTenantId, tenantId)`가 포함됩니다.
- **내부 컨트롤러** (`omni-common-mqlog`의 `MqMessageInternalController`): Feign 기반 크로스 서비스 집계에 `@RequestParam Long tenantId`를 사용합니다.

### 3.4 Relay: No Tenant Filter (Intentional)

`MqMessageRelayService`는 `tenant_id`에 관계없이 모든 PENDING/FAILED 메시지를 스캔합니다. 이는 설계에 따른 것입니다 — 릴레이는 모든 메시지를 전달해야 하는 백그라운드 인프라 프로세스입니다. 테넌트 격리는 사용자 대상 읽기/쓰기 작업에만 적용됩니다.

## 4. Constraints & Pitfalls

### 4.1 tenantId Must Be Explicit

`ReliableMessageRelay.send()`에 ThreadLocal 또는 SecurityContext 기반 테넌트 해석을 도입하지 마십시오. 명시적 파라미터가 계약입니다 — 자동 NULL tenantId 버그를 방지합니다.

### 4.2 All Query Interfaces Must Filter

`sys_mq_message`를 조회하는 모든 새 엔드포인트는 `tenantId` 필터를 포함해야 합니다. 예외 없음 — "관리자" 또는 "내부" 엔드포인트도 테넌트 경계를 준수해야 합니다.

### 4.3 Idempotent DDL

`schema.sql`은 안전하고 멱등인 테이블 생성을 위해 `CREATE TABLE IF NOT EXISTS`를 사용합니다. `omni-common-mqlog`가 클래스패스에 있을 때 서비스 시작 시 테이블이 자동 생성됩니다. 수동 DDL 실행이 필요하지 않습니다.

### 4.4 MessageSender Strategy Pattern

새 MQ 브로커(예: Kafka)를 추가하려면:
1. `MessageSender` 인터페이스 구현: `brokerType()`은 `"kafka"`를 반환하고, `send(SysMqMessage)`는 실제 전달을 처리합니다.
2. Spring Bean으로 등록 — `MqLogAutoConfiguration`은 모든 `MessageSender` Bean을 `brokerType`을 키로 하는 맵에 수집합니다.
3. `ReliableMessageTemplate.send()` 호출 시 `broker_type = "kafka"`를 설정합니다 (또는 적절한 바인딩을 사용하십시오).

`MqMessageRelayService`나 릴레이 로직의 변경은 필요하지 않습니다.

## 5. New Service Onboarding (Tutorial)

새 서비스(예: `omni-order`)에 신뢰성 있는 MQ 메시지 전송을 추가하려면:

### Step 1: Add Dependency

`omni-order/pom.xml`에 추가:

```xml
<dependency>
    <groupId>com.omni</groupId>
    <artifactId>omni-common-mqlog</artifactId>
    <version>${project.version}</version>
</dependency>
```

이를 통해 `omni-common-core`(`ReliableMessageRelay` 인터페이스용)와 `omni-common-mybatis`(`SysMqMessageMapper`용)가 자동으로 포함됩니다.

### Step 2: Table Auto-Creation

`schema.sql`은 시작 시 자동 실행됩니다 (`CREATE TABLE IF NOT EXISTS sys_mq_message`). 확인:

```sql
SELECT COUNT(*) FROM sys_mq_message;
```

### Step 3: Inject and Use

비즈니스 서비스에서:

```java
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final ReliableMessageRelay reliableMessageRelay;

    @Transactional
    public void createOrder(OrderDTO dto, Long tenantId) {
        // ... 비즈니스 로직 ...
        Order order = orderMapper.insert(entity);

        // Outbox에 기록 — 동일한 트랜잭션, 원자성 보장
        reliableMessageRelay.send("order-out-0", order, tenantId, "order:" + order.getId());
    }
}
```

### Step 4: Verify Relay Job Registration

서비스를 시작하고 XXL-JOB 관리 콘솔(`http://localhost:18080`)을 확인하십시오:
- 실행자: 서비스의 AppName이 실행자 목록에 표시되어야 합니다
- 작업: `mqRelayHandler`가 cron `0/10 * * * * ?`로 등록되어야 합니다
- `MqRelayJobRegistrar`는 애플리케이션 준비 후 비동기로 작업을 등록·시작합니다. 스케줄러를 일시적으로 사용할 수 없으면 10초마다 최대 12회 재시도합니다
- 아직 실행 중이 아니면 작업을 시작하십시오

### Step 5: Check Frontend Admin UI

프론트엔드의 `운영 모니터링 → 메시지 기록`으로 이동하십시오:
- 새 메시지가 `status = PENDING` (0)으로 표시된 후 릴레이 이후 `SENT` (1)로 전환되어야 합니다
- `tenantId`, `status`, `topic`, `serviceName` 또는 시간 범위로 필터링
- 실패한 메시지 재전송 또는 데드 레터 건너뛰기

Base 관리 인터페이스는 `X-Internal-Token`을 포함한 Feign 호출로 온보딩된 서비스의 로컬 Outbox를 집계합니다. 현재는 `omni-base`와 `omni-crm`을 집계하며, 새 서비스를 추가할 때는 해당 서비스의 내부 `/api/internal/mq-message/**` 클라이언트를 집계에 포함해야 합니다. 포함하지 않으면 메시지는 안정적으로 전달되지만 통합 운영 페이지에는 표시되지 않습니다.

## 6. Extension Guide

### 6.1 Adding a New MQ Broker

`MessageSender` 인터페이스를 구현하십시오:

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

`MqLogAutoConfiguration`은 모든 `MessageSender` Bean을 자동 수집합니다. 릴레이 서비스는 `broker_type` 컬럼으로 라우팅합니다 — 릴레이 코드 변경이 필요하지 않습니다.

### 6.2 Customizing Retry Strategy

현재 재시도 파라미터는 `MqMessageRelayService`에 하드코딩되어 있습니다:

| 파라미터 | 위치 | 기본값 |
|-----------|----------|---------|
| `BATCH_SIZE` | `MqMessageRelayService` | 100 |
| `BACKOFF_BASE_SECONDS` | `MqMessageRelayService` | 10 |
| `max_retry` | `SysMqMessage.maxRetry` | 3 |

메시지 단위로 커스터마이징: `ReliableMessageTemplate.send()` 호출 시 `maxRetry`를 설정하십시오 (인터페이스 확장 필요). 전역으로 커스터마이징: `MqMessageRelayService` Bean을 커스텀 파라미터로 오버라이드하십시오.

### 6.3 Customizing Relay Schedule

릴레이 작업의 cron 표현식은 XXL-JOB 관리 콘솔에서 구성 가능합니다. 기본값은 `0/10 * * * * ?` (10초마다)입니다. 처리량과 지연 시간 요구사항에 따라 조정하십시오.

---

## 7. 기술 선정 고려: Outbox 패턴 vs 직접 전송

| 고려사항 | Outbox 패턴 (Omni-Stack 채택) | 직접 MQ 전송 |
|------|------------------------------|------------|
| **트랜잭션 일관성** | 비즈니스 데이터와 메시지가 동일한 DB 트랜잭션에 기록되어 원자성 보장 | 분산 트랜잭션 문제: DB 커밋 성공이지만 MQ 전송 실패, 또는 그 반대 |
| **신뢰성** | 메시지가 DB에 영속화되어 서비스 재시작 후 전달 재개 가능 | MQ 전송 실패 시 메시지 손실, 재시도 불가 |
| **결합도** | 비즈니스 코드는 Outbox 테이블에만 기록하며, MQ Broker 가용성에 의존하지 않음 | 비즈니스 코드가 MQ 연결에 직접 의존하며, Broker 사용 불가 시 차단됨 |
| **지연 시간** | 최대 지연 시간 = 릴레이 작업 스케줄 간격 (10초) + 전달 시간 | 실시간 전송, 최소 지연 시간 |
| **복잡성** | Outbox 테이블 + 릴레이 작업 + 상태 머신이 필요 | 간단하지만 분산 트랜잭션 처리가 필요 |
| **관측성** | 메시지 상태 시각화 (PENDING/SENT/FAILED/DEAD_LETTER) | MQ Broker 로그만 확인 가능 |

**결론**: Outbox 패턴은 소량의 지연 시간과 맞바꿔 강한 일관성과 관측성을 확보하며, 메시지 신뢰성에 대한 요구가 높은 비즈니스 시나리오에 적합합니다.

### 직접 전송의 위험 시나리오

```
시나리오 1: DB 커밋 전 서비스 장애
  직접 전송: MQ는 전송되었지만 DB 트랜잭션이 커밋되지 않음 → 컨슈머가 "유령 메시지" 수신
  Outbox: 메시지가 Outbox에 기록되지 않음 → 전송되지 않음

시나리오 2: DB 커밋 후 MQ 전송 실패
  직접 전송: DB는 커밋되었지만 MQ 실패 → 메시지 손실
  Outbox: 메시지가 Outbox에 기록됨 (PENDING) → 릴레이 작업이 전달 재시도

시나리오 3: MQ Broker 장애
  직접 전송: 비즈니스 코드가 차단되거나 예외 발생 → 사용자 경험에 영향
  Outbox: 메시지의 Outbox 기록은 영향 없음 → Broker 복구 후 자동 전달
```

## 8. RocketMQ Docker 배포 구성 가이드

### 컨테이너 구성

```yaml
# docker-compose.yml
rocketmq-namesrv:
  image: apache/rocketmq:5.3.1
  container_name: omni-rocketmq-namesrv
  ports:
    - "9876:9876"
  command: sh mqnamesrv
  networks:
    - omni-network

rocketmq-broker:
  image: apache/rocketmq:5.3.1
  container_name: omni-rocketmq-broker
  ports:
    - "10911:10911"
    - "10909:10909"
  depends_on:
    - rocketmq-namesrv
  environment:
    NAMESRV_ADDR: rocketmq-namesrv:9876
  command: sh mqbroker -n rocketmq-namesrv:9876
  networks:
    - omni-network
```

### 주요 구성 상세

| 구성 항목 | 값 | 설명 |
|---------|-----|------|
| NameServer 포트 | 9876 | RocketMQ 서비스 디스커버리 포트 |
| Broker 포트 | 10911 | Broker 메인 포트 |
| Broker VIP 포트 | 10909 | Broker VIP 채널 (빠른 응답) |
| 네트워크 | omni-network | 다른 서비스와 동일한 Bridge 네트워크 |

### Spring Cloud Stream 구성

```yaml
# application.yml
spring:
  cloud:
    stream:
      rocketmq:
        binder:
          name-server: ${ROCKETMQ_NAMESRV:rocketmq-namesrv:9876}
      bindings:
        oper-log-out-0:
          destination: omni-oper-log-topic
          content-type: application/json
```

**환경 변수 오버라이드**: Docker 배포 시 `SPRING_CLOUD_STREAM_ROCKETMQ_BINDER_NAME_SERVER` 환경 변수로 NameServer 주소를 오버라이드하십시오.

## 9. 문제 해결 가이드

| 문제 | 가능한 원인 | 확인 방법 |
|------|---------|----------|
| **메시지가 PENDING 상태에 머무름** | 릴레이 작업 자동 등록 실패 또는 작업 미기동 | `MqRelayJobRegistrar` 재시도 로그, XXL-JOB 자격 증명, `mqRelayHandler` 상태 확인 |
| **메시지 전송 실패하여 FAILED 진입** | RocketMQ Broker가 시작되지 않음 | RocketMQ 컨테이너 상태 확인; `spring.cloud.stream.rocketmq.binder.name-server` 구성이 올바른지 확인 |
| **메시지가 DEAD_LETTER에 진입** | 최대 재시도 횟수 (3회) 초과 | 프론트엔드 관리 페이지에서 메시지 상세 및 오류 정보 확인; 문제 해결 후 수동 재전송 |
| **컨슈머가 메시지를 수신하지 못함** | Topic이 생성되지 않았거나 컨슈머가 구독하지 않음 | RocketMQ 콘솔에서 Topic과 Consumer Group 확인; 컨슈머 서비스가 시작되었는지 확인 |
| **테넌트 격리 실패** | 쿼리가 tenantId로 필터링되지 않음 | Controller에 `.eq(SysMqMessage::getTenantId, tenantId)`가 포함되어 있는지 확인 |
| **중복 전달** | 릴레이 작업이 동일한 메시지를 여러 번 스캔 | `msgId` 고유성 제약 확인; `StreamBridge.send()` 멱등성 확인 |

## 10. 메시지 상세 화면 스크린샷(4개 언어)

문서 전용 Playwright 케이스 `omni-frontend/e2e-docs/flows/detail-overlays.flows.spec.ts` 에 의해 실제 실행 스택에서 생성되며, §5 단계 5 「프론트엔드 관리 화면 확인」에 대응.

- 전제 조건: 로컬 Compose 전체 스택 실행 중, `omni-base` 헬스 및 `sys_mq_message` 에 실제 메시지 존재(수집 시 base DB 87 행).
- 조작자: `admin`(메시지 조회 권한 필요; 컨트롤러는 `X-Tenant-Id` 로 필터).
- 조작: 메시지 기록 페이지에 진입해 첫 행에서 「상세 보기」를 클릭해 읽기 전용 상세 오버레이를 엽니다.
- 기대 상태: 오버레이는 메시지 ID, Topic, Binding Name, Tag, 비즈니스 키, 미들웨어 유형, 상태, 재시도 횟수, 소스 서비스, 생성 시간과 다음 재시도 시간을 표시하며, 내용은 §2.1 상태 머신, §2.4 재시도 전략의 필드와 일치.
- 본 그룹은 **읽기 전용 수집**: 어떤 메시지도 재전송·건너뛰기·수정하지 않으므로, 쓰기 스위치가 불필요하고 데이터 마무리도 없습니다.

| 페이지 | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| 메시지 상세 오버레이(message-status) | ![메시지 상세(간체 중국어)](images/zh-CN/monitor-mq-message-detail.png) | ![메시지 상세(영어)](images/en-US/monitor-mq-message-detail.png) | ![메시지 상세(일본어)](images/ja-JP/monitor-mq-message-detail.png) | ![메시지 상세(한국어)](images/ko-KR/monitor-mq-message-detail.png) |

등록된 번역 완전도 문제: 이 오버레이는 **en-US/ja-JP/ko-KR 에서 제목과 모든 필드 레이블이 실측으로 영어**(ja/ko 미번역, zh-CN 만 현지화).
`npm run ui:i18n:parity`(4개 언어 각 2319 키, 0 누락)와 `npm run ui:i18n:check`(0/0 항목)가 모두 통과하므로, 언어 팩 값 문제이며 하드코딩 결함이 아님; 이미지와 manifest 는 실제 렌더링 값으로 그대로 등록하고 미화하지 않음.

아직 커버되지 않은 프로세스(모두 개별 승인 필요, 본 라운드에서 임의로 구성하지 않음):

- `retry` 와 `dead-letter`: 실측으로 5개 DB의 `sys_mq_message` 는 총 809 행(base 87, procurement 644, srm 34, workflow 23, crm 21), **status 는 모두 1, FAILED / DEAD_LETTER 기록은 전혀 없음**. 이 두 상태를 생성하려면 크로스 테넌트 공유 outbox 에 반드시 실패하는 메시지를 주입하고 릴레이가 §2.4 의 `2^retryCount × 10s` 백오프로 반복 재시도·오류하게 해야 하며——공유 인프라에서 장애를 제조하는 것으로, 명시적 승인 후에만 실행 가능.
- `trace-diagnosis`: 실제 Trace ID 진단 체인 증거가 필요하며, 관측 스택([docs/observability.md](observability.md))과 함께 설계 예정.
