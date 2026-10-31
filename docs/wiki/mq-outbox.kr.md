# 3. Outbox: 발송 의도를 먼저 확실히 저장하기

Outbox는 업무 서비스 자신의 발송 대기 목록입니다. 업무와 함께 저장하는 것은 발송 의도이지 도착 증명이 아닙니다. 각 서비스는 로컬 sys_mq_message 테이블을 유지하고 정식 스키마는 Liquibase 마이그레이션이 관리합니다. 앱이 실행될 때마다 임의로 테이블을 만드는 방식이 아닙니다.

## 1단계: 트랜잭션과 테넌트 명시하기

현재 템플릿의 선언과 테넌트 검증입니다. 발췌는 테넌트 검사 끝까지입니다.

<!-- omni:source path="omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/template/ReliableMessageTemplate.java" -->
```java
    @Transactional(propagation = Propagation.REQUIRED)
    public void send(String bindingName, Object payload, Long tenantId, String msgKey) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("可靠消息 tenantId 必须为正整数");
        }
```

REQUIRED는 기존 트랜잭션에 참여하고 없으면 자체 트랜잭션을 시작합니다. 업무 원자성은 같은 데이터 소스의 유효한 업무 트랜잭션과 실제 Spring 트랜잭션 프록시 호출을 전제로 합니다. 서로 다른 DB 쓰기, 트랜잭션 종료 후처리, 직접 내부 호출은 이 조건을 대신하지 못합니다.

양의 정수 tenantId를 명시적으로 전달하고 ThreadLocal로 추측하지 않습니다. 중계는 원래 요청 스레드가 사라진 뒤 여러 테넌트를 처리하므로 메시지가 올바른 테넌트를 직접 담아야 합니다.

## 2단계: 초기 상태 기록하기

삽입 직전의 실제 대입문입니다.

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

새 기록은 PENDING, 실패 횟수 0, 기본 최대 실패 횟수 3으로 시작합니다. serviceName은 출처를, producerTraceId는 가능한 연결 단서를 남깁니다. 삽입 성공은 목록에 기록됐다는 뜻일 뿐입니다.

msgId는 이번 메시지 기록의 ID입니다. msgKey는 조사용 업무 키이며 시스템이 자동으로 강제하는 고유 멱등 제약이 아닙니다. send를 반복 호출하면 새 기록이 생길 수 있으므로 업무 진입점은 자체 요청 멱등성이 필요합니다.

## 3단계: 커밋과 전달을 따로 검증하기

적법한 업무 트랜잭션에서 롤백 시 업무 변경과 대기 메시지가 모두 남지 않고 커밋 시 기록이 존재하는지 확인합니다. 그다음 중계 전송, 소비 처리, 중복 소비를 따로 검증합니다. 예시 행 하나를 테이블에 넣는 것만으로 업무 트랜잭션 원자성이 증명되지는 않습니다.

다음은 [선점, 재시도, 소비](mq-relay.kr.md)입니다.
