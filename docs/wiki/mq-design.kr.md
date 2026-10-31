# 2. 프레임워크 선택: 각 단계는 누가 맡는가

이 프로젝트는 RocketMQ를 중간 기지로, Spring Cloud Stream의 binding을 코드와 목적지 연결에, Outbox와 XXL-JOB을 중계에 사용합니다. 현재 선택을 설명하며 모든 MQ 제품을 비교하거나 기능을 약속하는 내용은 아닙니다.

## 계층을 나누는 이유

Spring Cloud Stream은 공통 전송 인터페이스를 제공합니다. binding은 앱 쪽 채널 이름이고 destination은 설정된 실제 목적지입니다. 현재 프로젝트의 topic 필드는 bindingName을 저장합니다. 이를 Broker의 최종 Topic 이름으로 단정하지 말고 해당 서비스의 Stream 설정을 확인하세요.

Outbox는 로컬 커밋과 나중 전송 사이의 간격을 다루고, RocketMQ는 수락·전달을, 소비자는 업무 처리를 담당합니다. 한 단계 성공만으로 다른 단계까지 성공했다고 볼 수 없습니다.

## 실제 전송 읽기

현재 RocketMQ 전송 전략의 전체 메서드입니다.

<!-- omni:source path="omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/sender/RocketMqMessageSender.java" -->
```java
    public void send(SysMqMessage message) {
        Message<String> outbound = MessageBuilder.withPayload(message.getPayload())
                .setHeader(MESSAGE_ID_HEADER, message.getMsgId())
                .setHeaderIfAbsent(PRODUCER_TRACE_HEADER, message.getProducerTraceId())
                .build();
        boolean success = streamBridge.send(message.getBindingName(), outbound);
        if (!success) {
            throw new RuntimeException("StreamBridge 发送返回 false，binding=" + message.getBindingName());
        }
        log.debug("MQ 消息投递成功：msgId={}, binding={}", message.getMsgId(), message.getBindingName());
    }
```

MessageBuilder가 본문과 상관관계 헤더를 묶고 StreamBridge가 bindingName에 설정된 채널로 보냅니다. false를 반환하면 예외를 던져 중계 실패 처리로 들어갑니다. 성공 반환도 소비자 업무 커밋을 의미하지 않습니다. SENT는 전송 측 성공입니다.

omniMessageId는 Outbox 기록을, omniProducerTraceId는 생산 시점의 로그를 찾는 단서입니다. 권한 증명도 자동 업무 중복 제거 기능도 아닙니다. 생산자 추적 ID가 없는 과거 기록도 전송할 수 있습니다.

## 중계와 전송 전략을 유지하는 이유

중계는 선점, 상태, 백오프, 데드레터를 담당하고 전송 전략은 구체적인 프레임워크를 담당합니다. 현재 저장 템플릿은 broker_type을 rocketmq로 고정합니다. 다른 Broker를 지원하려면 MessageSender 구현뿐 아니라 저장 계약, binding 설정, 오류 처리, 검증도 확장해야 합니다. 클래스 하나 추가만으로 전환 완료는 아닙니다.

다음은 [Outbox와 트랜잭션](mq-outbox.kr.md)입니다.
