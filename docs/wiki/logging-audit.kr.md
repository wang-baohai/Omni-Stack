# 4. 작업 로그: 작업을 기록하고 민감정보 보호하기

실행 로그는 기술적 처리 과정을 설명하고 작업 로그는 업무 작업을 기록합니다. 프로젝트는 @OperLog로 수집 위치를 선언합니다. 애스펙트가 필요한 정보와 변경 차이를 모으고 내용을 정제한 다음 생산자에게 넘깁니다. Auth의 인증 사건은 sys_audit_log로 기록하며 작업 로그 모듈에 의존하지 않습니다.

## 애너테이션이 중복 코드를 줄이는 이유

애스펙트는 업무 메서드 앞뒤의 공통 처리를 담당하고 실제 업무는 원래 메서드가 실행합니다. Spring 프록시를 통과해야 하므로 같은 인스턴스 안에서 직접 호출하면 자동으로 동작하지 않습니다. 스냅샷 설정 부족, 읽을 수 없는 엔티티, 읽을 변경이 없는 경우 차이가 비어 있을 수 있습니다.

## 조회 화면까지 전달되는 과정

작업 로그 생산자의 전체 전송 메서드입니다.

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

신뢰성 메시지 구성요소가 있으면 Outbox를 우선하고 없을 때만 직접 전송합니다. catch를 보세요. 전송 실패는 기록되며 업무 응답을 자동으로 실패로 바꾸지 않습니다. Outbox 쓰기가 실패해도 몰래 직접 전송으로 전환하지 않습니다. 따라서 모든 업무 성공에 로그가 반드시 남거나, 이미 끝난 업무 트랜잭션과 컨트롤러 후처리가 원자적으로 커밋된다고 주장할 수 없습니다.

## 쓰기 전에 민감정보 숨기기

정제기가 명시적으로 가리는 실제 필드 목록입니다.

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

전화번호, 이메일, 일반적인 자격 증명 문자열도 처리합니다. 스냅샷, 파라미터, 예외는 적용되는 정제 경로를 거쳐야 합니다. 모든 업무 비밀을 자동으로 인식할 수는 없으므로 새 민감 필드를 추가하면 규칙과 거부 사례도 유지해야 합니다. JSON 형식만으로 마스킹이 증명되지는 않습니다.

문제 해결 편의를 위해 비밀번호, 토큰, Cookie, Authorization, 전체 요청·메시지 본문을 기록하지 마세요. 실행 로그, DB 감사, 이미지, 내보내기는 각각 다른 출력 경계입니다. 한 곳을 정제했다고 다른 곳도 안전한 것은 아닙니다.

다음은 [실제 화면과 복구](logging-practice.kr.md)를 알아봅니다.
