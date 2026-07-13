# 백엔드 패턴 및 컨벤션

> 이 문서는 Omni-Stack 백엔드의 내부 조직 방식을 정의합니다. 모든 백엔드 코드는 이러한 패턴을 따라야 합니다.  
> 아키텍처 개요는 [architecture.kr.md](architecture.kr.md)를 참조하세요. Docker 배포 구성은 [docker-deployment.kr.md](docker-deployment.kr.md)를 참조하세요.

## Layering

```
Controller --> Service --> Repository (DAO)
     |            |            |
  Param check   Business     Data access
  Result wrap   logic        SQL / ORM
                Transaction
```

### Controller Layer

- **책임**: HTTP 요청 수신, 파라미터 검증, Service 호출, 응답 래핑
- Controller에 **비즈니스 로직 금지**
- 모든 메서드는 `R<T>`(성공) 또는 `R<PageResult<T>>`(페이지네이션)을 반환
- 요청 DTO는 `@Valid`(Jakarta Bean Validation)로 검증
- DTO는 Controller의 내부 정적 클래스 또는 독립 파일로 작성 가능
- RESTful 스타일: `GET /user/{id}`, `POST /user`, `GET /user/list`

### Service Layer

- **인터페이스 + 구현**: `XxxService`(인터페이스) + `XxxServiceImpl`(클래스)
- 구현 클래스에 `@Service` 어노테이션 적용
- 구현 메서드에 `@Transactional` 적용:
  - 읽기 작업: `@Transactional(readOnly = true)`
  - 쓰기 작업: `@Transactional`
- Service 레이어에서 `HttpServletRequest` / `HttpServletResponse` 사용 금지
- `@RequiredArgsConstructor` + `final` 필드를 통한 생성자 주입

### Repository / DAO Layer

- MyBatis-Plus 또는 JPA 사용; Mapper 인터페이스 명명: `XxxMapper`
- Mapper에 비즈니스 로직 금지
- SQL 파라미터: 항상 `#{}` 사용, `${}` 절대 사용 금지(SQL 인젝션 방지)

## 의존성 주입(DI)

```java
// 올바름: Lombok 생성자를 통한 주입
@RequiredArgsConstructor
@RestController
public class UserController {
    private final UserService userService;
}

// 금지: 필드 주입
@Autowired
private UserService userService;
```

**규칙**: 모든 의존성 주입은 반드시 `@RequiredArgsConstructor` + `final` 필드를 사용해야 합니다. `@Autowired` 필드 주입은 금지됩니다.

### 기술 선택 고려사항: @Autowired 대신 @RequiredArgsConstructor를 사용하는 이유

| 고려사항 | 이유 |
|------|------|
| **불변성** | `final` 필드는 의존성이 한 번 생성되면 변경될 수 없음을 보장하여 런타임에 예상치 못한 교체를 방지 |
| **컴파일 시 안전성** | 누락된 의존성이 런타임 `NullPointerException`이 아닌 컴파일 오류를 발생 |
| **테스트 친화성** | 생성자 주입은 Spring 컨테이너나 리플렉션 도구 없이 테스트에서 직접 Mock 객체를 전달 가능 |
| **명확성** | 모든 의존성이 클래스의 생성자에 한눈에 파악 가능하며, 모든 필드를 스캔하여 `@Autowired`를 찾을 필요 없음 |
| **Spring 공식 권장** | Spring 팀은 4.x부터 생성자 주입을 권장하며, 필드 주입은 5.x 이후 비권장으로 표시 |

## Validation

- 요청 DTO에 Jakarta Bean Validation 어노테이션 사용
- Controller 메서드 파라미터에 `@Valid`로 검증 트리거
- `MethodArgumentNotValidException`과 `BindException`은 `GlobalExceptionHandler`에서 전역적으로 처리

```java
@Data
public static class CreateUserRequest {
    @NotBlank(message = "Username is required")
    private String username;
    @NotBlank(message = "Email is required")
    private String email;
}
```

## Exception Handling

### BusinessException

```java
// 비즈니스 규칙 위반
throw new BusinessException("User not found");           // code: 500
throw new BusinessException(404, "User not found");      // code: 404
```

### GlobalExceptionHandler

`omni-common`에 위치하며, 모든 예외를 포착하여 `R<Void>`로 변환합니다:

| 예외 | 처리 방식 |
|-----------|----------|
| `BusinessException` | `log.warn` + `R.fail(code, message)` |
| `MethodArgumentNotValidException` | HTTP 400 + 필드 오류 집계 + `R.fail(400, ...)` |
| `BindException` | HTTP 400 + 필드 오류 집계 + `R.fail(400, ...)` |
| `Exception` (전체 포착) | `log.error` 전체 스택 트레이스 + `R.fail("Internal server error")` |

### 규칙

- 빈 `catch` 블록 절대 사용 금지
- 흐름 제어에 예외 사용 금지
- NPE 방어: `Optional`, `Objects.requireNonNull()`, 조기 null 체크 사용
- 예외 로깅 시 `log.error("msg", e)`로 전체 스택 트레이스 보존; `e.printStackTrace()` 절대 사용 금지

## Logging

Lombok `@Slf4j`와 `log` 객체 사용:

| 레벨 | 용도 |
|-------|-------|
| `ERROR` | 즉시 주의가 필요한 시스템 수준 오류 |
| `WARN` | 비즈니스 예외, 복구 가능한 문제 |
| `INFO` | 주요 비즈니스 흐름 체크포인트 |
| `DEBUG` | 개발 및 디버깅 |

```java
// 올바름: 파라미터화된 플레이스홀더
log.info("User {} logged in from {}", userId, ip);

// 금지: 문자열 연결
log.info("User " + userId + " logged in");

// 금지: 콘솔 출력
System.out.println("debug info");
```

- 민감한 정보(비밀번호, 토큰, 신분증 번호) 절대 로깅 금지

## OOP 관례

- 모든 POJO 클래스는 `Serializable`을 구현하고 `serialVersionUID`를 선언
- Lombok 사용: `@Data`, `@Getter`, `@Slf4j`, `@RequiredArgsConstructor`
- 클래스 멤버 순서: 정적 상수 -> 정적 변수 -> 인스턴스 변수 -> 생성자 -> public 메서드 -> private 메서드
- `equals`: 상수/결정적 값을 왼쪽에 배치: `"success".equals(status)`
- Wrapper 타입: `valueOf()` 사용, `new Integer()` 절대 사용 금지
- 부동소수점 비교: `BigDecimal` 사용 또는 epsilon 지정

## Collection & Concurrency

- 컬렉션 초기화 시 용량 지정: `new ArrayList<>(16)`, `new HashMap<>(16)`
- 빈 확인: `CollectionUtils.isEmpty()` 사용, `== null`이나 `size() == 0` 사용 금지
- 반복 중 `remove` 금지; `Iterator` 또는 `removeIf()` 사용
- Map 순회: `entrySet()` 사용, `keySet()` 후 `get()` 사용 금지
- 스레드 풀: `ThreadPoolExecutor` 사용, `Executors.newXxx()` 절대 사용 금지
- 동시 수정: `ConcurrentHashMap`, `AtomicXxx` 사용; 필요한 경우 외에는 수동 잠금 피하기

## 명명 관례 (Java)

| 유형 | 스타일 | 예시 |
|------|-------|---------|
| 패키지 | 소문자, 점 구분 | `com.omni.business.controller` |
| 클래스 | UpperCamelCase | `UserController`, `BusinessException` |
| 메서드 / 변수 | lowerCamelCase | `getUserById`, `createTime` |
| 상수 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT`, `DEFAULT_PAGE_SIZE` |
| Boolean 변수 | `is` 접두사 금지 | `deleted`, `enabled` (`isDeleted` 아님) |
| 추상 클래스 | `Abstract` 접두사 | `AbstractEntity` |
| 예외 클래스 | `Exception` 접미사 | `BusinessException` |
| Enum 클래스 | `Enum` 접미사 | `OrderStatusEnum` |
| DTO 클래스 | `Request` / `Response` / `VO` 접미사 | `CreateUserRequest`, `UserVO` |
| Feign 인터페이스 | `FeignClient` 접미사 | `RemoteServiceFeignClient` |
| Service 인터페이스 | `XxxService` | `UserService` |
| Service 구현 | `XxxServiceImpl` | `UserServiceImpl` |
| Mapper 인터페이스 | `XxxMapper` | `UserMapper` |

## 코드 형식 (Java)

- 들여쓰기: 4칸 공백, 탭 사용 금지
- 최대 줄 길이: 120자
- 중괄호: K&R 스타일(여는 중괄호는 같은 줄)
- 메서드 사이에 빈 줄 하나
- 연산자 주변 공백: `a + b`, `if (x == y)`
- 쉼표 뒤 공백: `method(a, b, c)`
- 메서드 파라미터 최대 5개; 초과 시 Request 객체로 캡슐화
- Import 순서: `java.*` -> `jakarta.*` -> 서드파티 -> `com.omni.*`, 그룹 간 빈 줄
- 와일드카드 import(`import xxx.*`) 금지, Controller 어노테이션 패키지 예외

## 주석

- 클래스, 클래스 속성, 클래스 메서드에는 반드시 Javadoc(`/** ... */`)이 있어야 함
- 핵심 로직을 설명하는 인라인 주석에는 `//` 사용
- 의미 없는 주석 금지 (예: `getName()`에 `// get name`)
- TODO 형식: `// TODO: [모듈] 설명`, 정기적으로 정리
- FIXME 형식: 알려진 문제에 대해 `// FIXME: 설명`

## 보안 및 권한

### 기능 권한(API 인증)

Controller 메서드는 `@PreAuthorize`를 사용하여 필요한 권한 코드를 선언합니다:

```java
@PreAuthorize("hasAuthority('system:user:create')")
@PostMapping
public R<Void> create(@Valid @RequestBody CreateUserRequest request) {
    userService.createUser(request);
    return R.ok();
}
```

- 권한 코드 형식: `resource:action` (예: `system:user:update`, `system:role:delete`)
- 권한 집합은 JWT Claims에 포함되며, Spring Security가 메서드 호출 전에 자동으로 검증
- 권한이 없을 경우 403 Forbidden 반환

### 데이터 권한(DataPermission)

MyBatis-Plus `DataPermissionInterceptor` 기반으로 행 수준 데이터 필터링을 구현하며, 비즈니스 코드에 대한 침입이 없습니다.

**핵심 컴포넌트 협력**:

```
Gateway → X-User-Id/X-Tenant-Id Header
    ↓
DataScopeResolveFilter（@Order(0), OncePerRequestFilter）
    ↓ 역할 조회 → dataScope 병합(가장 넓은 범위 우선) → 접근 가능한 조직 단위 ID 파싱
    ↓
DataScopeContext（ThreadLocal에 userId, tenantId, primaryUnitId, effectiveScope, accessibleUnitIds 저장）
    ↓
DataPermissionInterceptor（MyBatis-Plus InnerInterceptor）
    ↓ DataPermissionHandlerImpl.getSqlSegment(Table, Expression, String) 호출
    ↓ sys_user 테이블에만 WHERE 조건 추가
    ↓
비즈니스 SQL 실행(자동 필터링된 결과 집합)
    ↓
DataScopeContext.clear()（finally 블록, ThreadLocal 누수 방지）
```

**6단계 데이터 범위**:

| dataScope | SQL 동작 |
|-----------|---------|
| `ALL` | 조건 추가 없음(크로스 테넌트 표시 가능) |
| `TENANT` | 조건 추가 없음(기존 tenant_id 필터링으로 충분) |
| `DEPT_AND_BELOW` | `WHERE sys_user.primary_unit_id IN (해당 부서 및 하위 부서 ID)` |
| `DEPT` | `WHERE sys_user.primary_unit_id IN (해당 부서 ID)` |
| `CUSTOM` | `WHERE sys_user.primary_unit_id IN (사용자 정의 부서+하위 부서 ID)` |
| `SELF` | `WHERE sys_user.id = {현재 사용자 ID}` |

**다중 역할 병합**: 사용자가 여러 역할을 가지고 있을 때, 가장 높은 우선순위의 dataScope를 사용합니다(ALL > TENANT > DEPT_AND_BELOW > DEPT > CUSTOM > SELF).

**새 테이블에 대한 데이터 권한 확장**:

1. `DataPermissionHandlerImpl`에 대상 테이블 이름과 해당 열 매핑 추가
2. 대상 테이블은 반드시 `sys_user`에 대한 외래키 열을 포함해야 함(예: `primary_unit_id`, `create_by`)
3. `DataPermissionInterceptor` 등록 순서는 반드시 `PaginationInnerInterceptor` 이전이어야 함

**인메모리 필터링 모드**: 데이터베이스 쿼리가 아닌 데이터(예: Redis의 온라인 사용자 목록)의 경우, Controller가 `DataScopeContext.get()`에서 데이터 범위를 읽어 `primaryUnitId`로 수동 필터링합니다.

### ThreadLocal 사용 규범

- `DataScopeContext`는 `ThreadLocal<DataScopeInfo>`를 사용하여 요청 수준의 컨텍스트를 저장
- **반드시** `try/finally` 블록에서 정리하여 스레드 풀 시나리오에서 누수를 방지
- 쓰기 시점: `DataScopeResolveFilter.doFilterInternal()`에서 `try` 블록 이전
- 정리 시점: `DataScopeResolveFilter.doFilterInternal()`에서 `finally` 블록

### XSS 방어(3계층 방어 아키텍처)

XSS 방어는 3계층 종심 방어를 채택하며, 구성은 데이터베이스 + Redis 캐시로 관리되고 테넌트별 분리를 지원합니다.

```
Layer 1: Jackson StringDeserializer — @RequestBody JSON의 String 필드를 자동 정제
Layer 2: Servlet Filter + HttpServletRequestWrapper — 쿼리 파라미터 정제
Layer 3: Gateway WebFilter — 보안 응답 헤더 추가
```

**핵심 컴포넌트**:

| 컴포넌트 | 모듈 | 책임 |
|------|------|------|
| `XssConfigProvider` | omni-common-core | SPI 인터페이스, 구체적인 서비스 모듈에서 구현 |
| `XssSettings` / `XssRule` | omni-common-core | 구성 값 객체(enabled + 규칙 목록) |
| `XssSanitizer` | omni-common | 핵심 정제 로직(HTML_TAG / EVENT_HANDLER / DANGEROUS_PROTOCOL / CUSTOM_PATTERN) |
| `XssStringDeserializer` | omni-common | Jackson String 역직렬화 래퍼, 자동 정제 |
| `XssFilter` | omni-common | OncePerRequestFilter, 구성 로드 + ThreadLocal 설정 |
| `XssHttpServletRequestWrapper` | omni-common | getParameter/getParameterValues 재정의 |
| `XssAutoConfiguration` | omni-common | Filter + Jackson SimpleModule 자동 등록 |
| `XssConfigProviderImpl` | omni-auth | 구성 로드 구현(Redis 캐시 + 데이터베이스 폴백) |
| `SecurityHeadersFilter` | omni-gateway | X-Content-Type-Options / X-Frame-Options / Referrer-Policy 추가 |

**규칙 유형**:

| ruleType | 매칭 및 정제 방식 |
|----------|---------------|
| `HTML_TAG` | 쌍을 이루는 태그와 자체 닫히는 태그 제거 |
| `EVENT_HANDLER` | `on*` 속성 제거 |
| `DANGEROUS_PROTOCOL` | `javascript:` / `vbscript:` / `data:` 등의 프로토콜 문자열 대체 |
| `CUSTOM_PATTERN` | 사용자 정의 정규식 대체 |

**새 서비스 확장**: `XssConfigProvider` 인터페이스를 구현하면 자동으로 XSS 방어 기능을 획득합니다. `omni-common` 의존성 도입 후 `AutoConfiguration.imports`를 통해 자동으로 어셈블됩니다.

**캐시 전략**: Redis 키 `xss:enabled:{tenantId}` + `xss:rules:{tenantId}`, TTL 30분. 모든 쓰기 작업(스위치 전환, 규칙 CRUD) 후 능동적으로 캐시를 무효화합니다.

## MQ 메시지 전송 기록 및 보상 관리(omni-common-mqlog)

MQ 메시지 전송 기록 및 보상 관리 시스템은 Transactional Outbox + XXL-JOB 비동기 전달 아키텍처를 기반으로 하며, 각 마이크로서비스에 안정적인 메시지 전송 기능을 제공하고 도입 즉시 사용 가능하며 비즈니스 코드가 필요 없습니다.

### 핵심 아키텍처

```
비즈니스 트랜잭션（@Transactional）
    ↓
ReliableMessageTemplate.send(bindingName, payload)
    ↓ INSERT sys_mq_message (status=PENDING) -- 동일한 로컬 트랜잭션
    ↓
XXL-JOB mqRelayHandler (10초 폴링)
    ↓
MqMessageRelayService.relayAll()
    ↓ PENDING/FAILED이고 next_retry_time <= NOW()인 메시지 일괄 조회
    ↓
MessageSender.send(message) -- 전략 패턴, broker_type별 라우팅
    ↓
성공 → status=SENT | 실패 → retry_count++, 지수 백오프 | 한도 초과 → DEAD_LETTER
```

### 핵심 컴포넌트

| 컴포넌트 | 책임 |
|------|------|
| `ReliableMessageTemplate` | `send(bindingName, payload)` / `send(bindingName, payload, msgKey)` 두 가지 오버로드를 제공하며, 호출자의 트랜잭션에서 메시지 레코드를 INSERT |
| `MqMessageRelayService` | 전달 대기 메시지를 폴링하고 `MessageSender` 전략 구현을 호출하여 전송, 재시도 백오프 및 데드레터 마킹 처리 |
| `MqMessageRelayJob` | XXL-JOB handler（`@XxlJob("mqRelayHandler")` + `@SystemJobMeta`）, relay 로직 트리거 |
| `MessageSender` | 전략 인터페이스, `broker_type`별 라우팅. 현재 `RocketMqMessageSender`(StreamBridge 기반) 구현, 향후 `KafkaMessageSender` 확장 가능 |
| `MqMessageInternalController` | Feign 내부 조회 API(`/api/internal/mq-message`), 집계 조회 서비스에서 호출 |

### 새 서비스 온보딩 단계

1. POM에 `omni-common-mqlog` 의존성 추가
2. `omni-common-mybatis`(데이터베이스)와 `omni-common-job`(XXL-JOB) 의존성이 이미 있는지 확인
3. RocketMQ 전송 기능이 필요하면 `spring-cloud-starter-stream-rocketmq` 의존성 추가
4. `sys_mq_message` 테이블은 자동으로 생성됨(`schema.sql` + `CREATE TABLE IF NOT EXISTS`)
5. `mqRelayHandler`는 자동으로 XXL-JOB에 등록됨(각 서비스 실행자 AppName이 다르므로 handler name이 자연스럽게 분리)
6. 비즈니스 코드에서 `ReliableMessageTemplate`을 주입하고 `send()` 메서드를 호출하면 됨

### 지수 백오프 전략

재시도 간격: `2^retryCount × 10s`. 1번째 20초, 2번째 40초, 3번째 80초. `max_retry`(기본값 3)를 초과하면 데드레터 상태(DEAD_LETTER)로 전환됩니다.

### 데드레터 처리

- **재전송**: PENDING/FAILED/DEAD_LETTER 상태를 PENDING으로 재설정하고 `retry_count`를 초기화, relay 작업의 다음 폴링에서 재전송
- **무시**: DEAD_LETTER → SKIPPED, 더 이상 전송할 필요가 없는 최종 상태로 확인

## 운영 로그(OperLog)

운영 로그 시스템은 AOP + RocketMQ 비동기 아키텍처를 기반으로 하며, Controller 메서드의 요청 컨텍스트와 엔티티 변경 스냅샷을 자동으로 수집하여 비즈니스 운영에 대한 완전한 감사 추적을 구현합니다.

### 핵심 기록 흐름

```
Controller 메서드（@OperLog 어노테이션）
    ↓
OperLogAspect（AOP @Around 어드바이스）
    ↓ 요청 컨텍스트 수집: username, tenantId, IP, URL, 요청 파라미터
    ↓ 엔티티 변경 스냅샷: UPDATE/DELETE 작업 전 oldValue 조회, 작업 후 newValue 조회
    ↓ EntityDiffer.diff(): 필드 수준 차이 비교(UPDATE만)
    ↓
OperLogProducer.send(OperLogMessage)
    ↓ RocketMQ 비동기 전송
    ↓
omni-base 소비자
    ↓ INSERT INTO sys_oper_log(핫 테이블)
    ↓
OperLogArchiver（@Scheduled 매일 02:00）
    ↓ 180일이 초과된 핫 테이블 레코드를 sys_oper_log_archive(콜드 테이블)로 마이그레이션
    ↓ 배치 처리(배치당 1000건), 마이그레이션 후 핫 테이블에서 삭제
```

### @OperLog 어노테이션 사용

```java
@OperLog(module = "사용자 관리", operType = OperType.CREATE, entityClass = SysUser.class, idExpr = "#result.data.id")
@PreAuthorize("hasAuthority('system:user:create')")
@PostMapping
public R<UserVO> create(@Valid @RequestBody CreateUserRequest request) {
    return R.ok(userService.createUser(request));
}
```

**어노테이션 파라미터 설명**:

| 파라미터 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `module` | String | 예 | 비즈니스 모듈 이름, 예: "사용자 관리", "사전 타입 관리" |
| `operType` | OperType | 예 | 작업 타입 열거(아래 표 참조) |
| `entityClass` | Class<?> | 조건부 | 대상 엔티티 클래스, AOP가 자동으로 변경 스냅샷을 diff하는 데 사용. QUERY/EXPORT/IMPORT 타입은 지정 불필요 |
| `idExpr` | String | 조건부 | SpEL 표현식, 메서드 파라미터 또는 반환값에서 엔티티 ID를 추출. 예: `"#id"`, `"#result.data.id"` |

### OperType 열거

| 열거값 | 의미 | entityClass 필요 여부 | 설명 |
|--------|------|---------------------|------|
| `CREATE` | 생성 | 권장 지정 | AOP가 반환값에서 새 엔티티 ID를 추출하고 newValue 조회 |
| `UPDATE` | 수정 | 예 | AOP가 작업 전 oldValue 조회, 작업 후 newValue 조회, 필드 수준 diff 실행 |
| `DELETE` | 삭제 | 예 | AOP가 작업 전 oldValue 조회, newValue는 null |
| `QUERY` | 조회 | 아니오 | 조회 행위만 기록, 엔티티 스냅샷 없음 |
| `EXPORT` | 내보내기 | 아니오 | 내보내기 행위만 기록, 엔티티 스냅샷 없음 |
| `IMPORT` | 가져오기 | 아니오 | 가져오기 행위만 기록, 엔티티 스냅샷 없음 |

### 개발 제약사항

1. **새로운 쓰기 작업 Controller 메서드에는 반드시 `@OperLog`를 붙여야 하며**, `module`, `operType`을 지정하고, 엔티티 변경이 있을 경우 `entityClass`와 `idExpr`도 지정해야 함
2. **omni-auth 모듈에서는 `@OperLog` 사용 금지**: 인증 행위는 로그인 로그(`sys_login_log`)와 감사 로그(`sys_audit_log`)로 완전하게 기록되며, omni-auth는 `omni-common-operlog` 의존성을 도입하지 않고 Controller 메서드에 `@OperLog` 어노테이션을 사용하지 않음
3. **새 마이크로서비스 온보딩**: `pom.xml`에 `omni-common-operlog` 의존성을 추가하고 RocketMQ를 구성해야 하며, 이 모듈은 `AutoConfiguration.imports`를 통해 AOP 어드바이스와 MQ Producer를 자동 등록
4. **`entityClass` 용도**: AOP가 `ApplicationContext`를 통해 해당 엔티티 타입의 `BaseMapper`를 찾아 자동으로 `selectById`를 실행하여 변경 전후 스냅샷을 획득
5. **`idExpr` SpEL 문법**: 메서드 파라미터(`#id`, `#request.id`)와 반환값(`#result.data.id`) 참조를 지원하며, 파싱 실패 시 경고 로그를 기록하지만 비즈니스에 영향 없음
6. **JSON 스냅샷 제한**: 단일 oldValue/newValue 최대 4000자, 초과 시 자동 잘림
7. **핫/콜드 테이블 분리**: 핫 테이블 `sys_oper_log`은 최근 180일 데이터를 빠른 조회용으로 보관하고, 콜드 테이블 `sys_oper_log_archive`은 규정 준수를 위해 장기 보관. 아카이브 작업은 매일 02:00에 실행되며, 배치당 1000건, 실패 시 해당 아카이브 중단

### 모듈 책임

| 모듈 | 컴포넌트 | 책임 |
|------|------|------|
| `omni-common-core` | `OperLog` 어노테이션, `OperType` 열거, `OperLogMessage` POJO | 순수 POJO 계층, Spring 의존성 없음 |
| `omni-common-operlog` | `OperLogAspect`, `OperLogProducer`, `EntityDiffer`, `OperLogAutoConfiguration` | AOP 어드바이스 + MQ Producer + 엔티티 diff + 자동 구성 |
| `omni-base` | `OperLogConsumer`, `OperLogArchiver` | MQ 소비 후 핫 테이블 기록 + 예약된 콜드 테이블 아카이브 |

## Common Starter 온보딩 규범

프로젝트는 공통 기능을 6개 모듈로 분리하였으며, 새 마이크로서비스는 Maven 의존성으로 도입 즉시 사용 가능하며 수동 구성이 필요 없습니다.

### Starter 모듈 개요

| 모듈 | 책임 | 자동 구성 내용 | 대상 서비스 유형 |
|------|------|-------------|-------------|
| `omni-common-core` | 순수 POJO 계층 | 없음(Spring 의존성 없음) | 모든 모듈 |
| `omni-common` | Web 자동 구성 | `JacksonConfig`(시간 직렬화), `WebMvcConfig`(CORS), `GlobalExceptionHandler`, `XssAutoConfiguration`(Filter + Jackson Module) | Servlet 서비스 |
| `omni-common-mybatis` | 데이터베이스 기능 | `MybatisPlusAutoConfiguration`: `MybatisPlusInterceptor`(MySQL `PaginationInnerInterceptor`) + YAML 기본 구성(카멜 매핑, 논리 삭제, 자동 증가 ID) | Servlet 서비스 |
| `omni-common-redis` | 블로킹 Redis | `RedisAutoConfiguration`: `RedisTemplate<String, Object>`(Jackson 직렬화) + `RedisUtils` 유틸리티 클래스 + Lettuce 연결 풀 구성 | Servlet 서비스 |
| `omni-common-redis-reactive` | 리액티브 Redis | `spring-boot-starter-data-redis-reactive` + YAML 기본 타임아웃 구성 | WebFlux 서비스(예: Gateway) |
| `omni-common-mqlog` | 신뢰성 있는 MQ 메시지 전송 | `ReliableMessageTemplate`(Transactional Outbox), `MqMessageRelayService`(XXL-JOB 비동기 전달), `MessageSender` 전략 인터페이스, `MqMessageInternalController`(Feign 내부 조회), `schema.sql`(자동 테이블 생성) | Servlet 서비스(MQ 기능 필요) |

**자동 구성 등록 메커니즘**: 모든 starter는 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 파일을 통해 등록되며, 이는 Spring Boot 3+/4+의 표준 메커니즘입니다(구버전 `spring.factories`를 대체).

### 새 서비스 온보딩 단계

1. **POM 의존성**: `pom.xml`에 필요한 starter를 선언:
   ```xml
   <dependency><groupId>com.omni</groupId><artifactId>omni-common-core</artifactId></dependency>
   <dependency><groupId>com.omni</groupId><artifactId>omni-common</artifactId></dependency>
   <dependency><groupId>com.omni</groupId><artifactId>omni-common-mybatis</artifactId></dependency>
   <dependency><groupId>com.omni</groupId><artifactId>omni-common-redis</artifactId></dependency>
   ```
2. **application.yml**: 데이터소스와 Redis 연결 정보만 구성하면 됨:
   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://127.0.0.1:3306/omni_xxx?useSSL=false&serverTimezone=Asia/Shanghai
       username: root
       password: root
     data:
       redis:
         host: 127.0.0.1
         port: 6379
         database: 0
   ```
3. **시작 클래스**: `@MapperScan("com.omni.xxx.mapper")` 추가
4. **XSS 방어**: `XssConfigProvider` SPI 인터페이스 구현(`omni-common`이 Filter + Jackson Module을 자동 등록)
5. **자동 적용**: 페이지네이션 플러그인, Jackson 시간 직렬화, CORS 구성, `GlobalExceptionHandler`, `RedisUtils` 유틸리티 클래스가 모두 자동으로 어셈블되며 추가 구성 불필요

### 오버라이드 메커니즘

모든 starter의 자동 구성 Bean은 `@ConditionalOnMissingBean` 어노테이션을 사용하며, 서비스는 필요에 따라 오버라이드할 수 있습니다:

- **MybatisPlusInterceptor**: 서비스가 동일한 이름의 `mybatisPlusInterceptor` Bean을 정의하면 기본 페이지네이션 구성을 오버라이드할 수 있습니다. 일반적인 시나리오: `DataPermissionInterceptor` 추가(**반드시 `PaginationInnerInterceptor` 이전에 등록해야 함**)
- **RedisTemplate**: 서비스가 `@Bean(name = "redisTemplate")`을 정의하면 기본 직렬화 전략을 교체할 수 있습니다
- **XSS 구성**: `XssAutoConfiguration`은 `XssConfigProvider` Bean에 조건부 의존하며, SPI를 구현하지 않으면 XSS 필터 체인이 활성화되지 않음

### Redis Starter 상호 배타 제약

`omni-common-redis`(블로킹)와 `omni-common-redis-reactive`(리액티브)는 **동일한 서비스에서 혼용 불가**:

- **WebFlux 서비스**(예: Gateway): `omni-common-redis-reactive`만 의존 가능, 블로킹 Redis 호출은 Netty 이벤트 루프 스레드 기아를 초래
- **Servlet 서비스**: `omni-common-redis`(블로킹) 사용, `RedisUtils`가 동기 API를 제공

## 구성 참조 표

### application.yml 주요 구성 항목

다음은 백엔드 마이크로서비스 `application.yml`의 주요 구성 항목입니다:

| 구성 키 | 설명 | 기본값 | Docker 환경 변수 오버라이드 |
|--------|------|--------|-------------------|
| `server.port` | 서비스 포트 | 8100/8101/8102/8103 | `SERVER_PORT=8080` |
| `spring.application.name` | Nacos 서비스 이름 | omni-auth/base/gateway/workflow | — |
| `spring.datasource.url` | 데이터베이스 연결 | `jdbc:mysql://127.0.0.1:3306/omni_xxx` | `SPRING_DATASOURCE_URL` |
| `spring.datasource.username` | 데이터베이스 사용자 | `root` | `SPRING_DATASOURCE_USERNAME` |
| `spring.datasource.password` | 데이터베이스 비밀번호 | `root` | `SPRING_DATASOURCE_PASSWORD` |
| `spring.data.redis.host` | Redis 주소 | `127.0.0.1` | `SPRING_DATA_REDIS_HOST` |
| `spring.data.redis.port` | Redis 포트 | `6379` | `SPRING_DATA_REDIS_PORT` |
| `spring.data.redis.database` | Redis DB 인덱스 | 0 | `SPRING_DATA_REDIS_DATABASE` |
| `spring.cloud.nacos.discovery.server-addr` | Nacos 주소 | `127.0.0.1:8848` | `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR` |
| `spring.cloud.nacos.discovery.ip` | 등록 IP | `127.0.0.1` | `SPRING_CLOUD_NACOS_DISCOVERY_IP` |
| `auth.jwks.uri` | JWKS 엔드포인트 | `http://localhost:8100/oauth2/jwks` | `AUTH_JWKS_URI` |
| `auth.jwks.cache-ttl` | 공개키 캐시 시간 | `5m` | — |
| `spring.profiles.active` | 활성화된 Profile | `default` | `SPRING_PROFILES_ACTIVE` |

### MyBatis-Plus 구성

| 구성 키 | 설명 | 기본값 |
|--------|------|--------|
| `mybatis-plus.configuration.map-underscore-to-camel-case` | 언더스코어를 카멜케이스로 변환 | `true` |
| `mybatis-plus.global-config.db-config.logic-delete-field` | 논리 삭제 필드 | `deleted` |
| `mybatis-plus.global-config.db-config.logic-delete-value` | 논리 삭제 값 | `1` |
| `mybatis-plus.global-config.db-config.logic-not-delete-value` | 논리 미삭제 값 | `0` |
| `mybatis-plus.global-config.db-config.id-type` | ID 전략 | `AUTO`(데이터베이스 자동 증가) |

### XXL-JOB 구성(omni-base)

| 구성 키 | 설명 | 기본값 |
|--------|------|--------|
| `xxl.job.admin.addresses` | Admin 주소 | `http://127.0.0.1:18080/xxl-job-admin` |
| `xxl.job.executor.appname` | 실행자 이름 | `omni-base` |
| `xxl.job.executor.port` | 실행자 포트 | `9999` |
| `xxl.job.accessToken` | 통신 토큰 | `default_token` |

## Service 계층 설계 패턴

### 인터페이스 + 구현 분리

```
UserService (interface)     — 비즈니스 메서드 시그니처 정의
    ↑ implements
UserServiceImpl (@Service)  — 비즈니스 로직 구현 + @Transactional
```

**설계 이유**:
- 인터페이스 계층은 OpenFeign 클라이언트에서 재사용 가능
- 단위 테스트에서 인터페이스를 직접 Mock할 수 있으며 구현 클래스에 의존하지 않음
- 구현 클래스를 호출자에 영향 없이 교체 가능

### 트랜잭션 관리 전략

| 시나리오 | 어노테이션 | 설명 |
|------|------|------|
| 읽기 전용 쿼리 | `@Transactional(readOnly = true)` | 데이터베이스 쿼리 성능 최적화, 쓰기 작업 금지 |
| 쓰기 작업 | `@Transactional` | 기본 REQUIRED 전파 레벨 |
| 크로스 서비스 호출 | `@Transactional` + Outbox 패턴 | 로컬 트랜잭션으로 Outbox 테이블에 기록, 원격 트랜잭션에 직접 참여하지 않음 |
| 독립 트랜잭션 | `@Transactional(propagation = REQUIRES_NEW)` | 예: 로깅, 메인 트랜잭션이 롤백되어도 로그가 여전히 기록되도록 보장 |

### 예외 처리 체인

```
Controller 메서드
    │ Service 메서드 호출
    ▼
Service 계층
    │ 비즈니스 검증 실패 → throw new BusinessException(400, "사용자 이름이 이미 존재합니다")
    │ 리소스가 존재하지 않음   → throw new BusinessException(404, "사용자가 존재하지 않습니다")
    ▼
GlobalExceptionHandler (@RestControllerAdvice)
    │ @ExceptionHandler(BusinessException.class)
    │   → log.warn + R.fail(code, message)
    │ @ExceptionHandler(MethodArgumentNotValidException.class)
    │   → HTTP 400 + 필드 오류 집계 + R.fail(400, "field: message")
    │ @ExceptionHandler(AccessDeniedException.class)
    │   → HTTP 403 + R.fail(403, "권한 부족")
    │ @ExceptionHandler(Exception.class)
    │   → log.error(전체 스택 트레이스) + R.fail("서버 내부 오류")
    ▼
통합 R<Void> 응답
```

**omni-auth의 특수 처리**: Auth 모듈은 `omni-common-core`에 의존하며(`omni-common`이 아님), 따라서 `GlobalExceptionHandler`가 컴포넌트 스캔 범위에 없습니다. Auth 모듈은 `AuthExceptionHandler`(부분 `@RestControllerAdvice`)를 통해 동등한 예외 처리를 제공합니다.

## 문제 해결 가이드

### 일반적인 문제

| 문제 | 가능한 원인 | 해결 방법 |
|------|---------|----------|
| `@PreAuthorize`가 적용되지 않음 | `GatewayPreAuthFilter`가 등록되지 않음 | SecurityConfig에서 `addFilterBefore(new GatewayPreAuthFilter(), AuthorizationFilter.class)`가 있는지 확인 |
| 페이지네이션 쿼리가 빈 결과를 반환 | `PaginationInnerInterceptor`가 등록되지 않음 | `MybatisPlusConfig`에서 인터셉터 등록 순서 확인 |
| `@Transactional`이 작동하지 않음 | 메서드가 public이 아님 / 자체 호출 | `@Transactional`은 public 메서드에만 적용되며, 동일 클래스 내부 메서드 호출은 프록시를 트리거하지 않음 |
| Redis 연결 타임아웃 | 컨테이너 네트워크 연결 불가 | `docker compose exec omni-auth ping redis`로 네트워크 연결 확인 |
| Nacos 등록 실패 | 주소 구성 오류 | `server-addr`이 `localhost`가 아닌 컨테이너 이름 `nacos:8848`을 사용하는지 확인 |
| JWT 검증 실패 | 공개키 캐시 만료 | `auth.jwks.cache-ttl` 구성 확인 또는 Gateway 재시작하여 캐시 제거 |
| XXL-JOB 등록 실패 | Admin이 시작되지 않음 | `xxl-job-admin` 컨테이너가 정상 실행 중인지 확인 |
| MQ 메시지가 전달되지 않음 | Relay Job이 트리거되지 않음 | XXL-JOB Admin 콘솔에서 `mqRelayHandler`가 정상 스케줄링되는지 확인 |

### 디버깅 팁

```bash
# 백엔드 서비스 로그 확인
docker compose logs -f omni-auth

# 환경 변수 확인
docker compose exec omni-auth env | grep SPRING

# 데이터베이스 연결 테스트
docker compose exec omni-auth sh -c 'nc -zv mysql 3306'

# Nacos에 등록된 인스턴스 확인
curl -s http://localhost:8848/nacos/v1/ns/instance/list?serviceName=omni-auth
```

## Testing

Auth, CRM 및 일부 Common 모듈에는 이제 테스트가 있습니다. 백엔드 변경 시:

- **단위 테스트**: JUnit 5 + Mockito, `src/test/java/`에 배치
- **통합 테스트**: `@SpringBootTest` + 내장 데이터베이스 또는 Test Containers
- 테스트 클래스 명명: `XxxTest`(단위) 또는 `XxxIntegrationTest`(통합)
- 테스트 메서드 명명: `should_<expected>_when_<condition>`
