# 백엔드 패턴 및 컨벤션

> 이 문서는 백엔드 계층, 코딩 규칙과 공통 기능 도입 경계를 관리합니다. 요구사항은 새 코드와 변경 코드에 적용하며 기존 코드 전체의 준수나 업무 검증 완료를 뜻하지 않습니다.
> 모듈과 신뢰 경계는 [아키텍처](architecture.kr.md), 통신 규약은 [API 계약](api-contract.kr.md), 환경 변수는 [Docker 배포](docker-deployment.kr.md)를 참조하세요. 전체 실행 절차는 Wiki와 개발 가이드에서 관리합니다.

## Layering

```text
Controller --> Service --> Mapper (DAO)
HTTP/validation   Domain rules   SQL/data access
                  Local transaction boundary
```

### Controller Layer

- HTTP 입력을 받고 선언된 제약을 검증하며 진입 권한과 신원을 확인한 뒤 Service를 호출합니다. 도메인 상태 전이와 영속화 규칙은 Service가 담당합니다.
- 일반 업무 API는 `R<T>` / `R<PageResult<T>>`를 사용합니다. OAuth/OIDC, 리다이렉트와 Actuator 등은 각 프로토콜의 응답을 유지합니다.
- @Valid는 DTO에 선언된 제약만 실행합니다. 테넌트 소속, 가시성, 유일성이나 업무 상태 검증을 대신하지 않습니다. DTO는 독립 클래스나 static 내부 클래스로 정의할 수 있습니다.
- 경로, HTTP 메서드와 필드는 실제 Controller가 정의합니다. 예시와 발췌는 바로 호출할 수 있는 완전한 API가 아닙니다. 쓰기 진입점 추가 시 권한 시드, 마이그레이션 검증과 프런트엔드 권한도 갱신합니다.

### Service Layer

- XxxService와 XxxServiceImpl을 분리하고 업무 구현에 @Service를 붙이며 생성자로 의존성을 전달합니다.
- 원자성이 필요한 로컬 업무 경계에 트랜잭션을 선언합니다. 읽기 전용 트랜잭션은 일관성 요구에 따라 선택하며 모든 기존 메서드에 이미 적용됐다고 추정하지 않습니다.
- 새 도메인 Service는 신원, tenantId와 업무 인자를 명시적으로 받고 HTTP 객체는 어댑터에 둡니다. 기존 예외는 모듈 계약에 따라 처리합니다.

### Repository / DAO Layer

- 업무 영속화는 주로 MyBatis-Plus의 XxxMapper를 사용합니다. Flowable 내부 영속화는 엔진이 관리하며 이를 프로젝트의 범용 JPA 도입 근거로 삼지 않습니다.
- Mapper는 데이터 접근, Service는 도메인 판단을 담당합니다. 값은 #{}로 바인딩합니다. 동적 테이블·열·정렬 식별자는 통제된 매핑에서 가져오며 사용자 입력을 직접 연결하지 않습니다.
- 사용자 정의 SQL, 하위 테이블 조인과 테넌트 전체 백그라운드 작업은 테넌트·데이터 범위를 별도로 확인합니다. Starter 의존성만으로 모든 SQL 보호가 증명되지는 않습니다.

## 의존성 주입(DI)

아래는 imports를 생략한 생성자 주입 예시이며 새로운 운영 Controller가 아닙니다.

```java
// 构造器注入示意，省略 imports
@RequiredArgsConstructor
@RestController
public class UserController {
    private final UserService userService;
}
```

@Autowired 필드 주입은 금지합니다. 보통 @RequiredArgsConstructor와 final을 사용합니다. @Lazy, 선택 의존성이나 초기화 분기는 명시적 생성자, @Bean 메서드 또는 ObjectProvider로 표현할 수 있습니다. 기존 OperLogProducer / OperLogAutoConfiguration을 참고하세요.

### 기술 선택 고려사항: @Autowired 대신 @RequiredArgsConstructor를 사용하는 이유

| 관점 | 이점과 한계 |
|---|---|
| 명시적 의존성 | 생성 인자에서 필요한 의존성을 확인하고 테스트에서 Mock을 직접 전달 |
| 필드 고정 | final은 재할당을 막지만 참조 객체의 불변성이나 스레드 안전성은 보장하지 않음 |
| 실패 진단 | 직접 생성할 때 인자 누락은 컴파일 오류가 될 수 있으나 Spring Bean 누락·모호성·설정 오류는 시작 시 실패할 수 있음 |
| 선택 기능 | 선택 의존성은 명시적 처리가 필요하며 Lombok이 실행 조건을 없애지 않음 |

## Validation

@Valid @RequestBody와 Jakarta Bean Validation을 사용합니다. 아래 CreateDictTypeRequest 발췌는 다른 필드와 imports를 생략합니다. 네 언어 모두 실제 소스의 중국어 진단 메시지를 유지합니다.

```java
/** 字典类型编码 */
@NotBlank(message = "字典类型编码不能为空")
private String typeCode;

/** 字典类型名称 */
@NotBlank(message = "字典类型名称不能为空")
private String typeName;
```

해당 MVC advice가 제약 실패, 필수 인자 누락과 타입 불일치를 처리합니다. Service는 테넌트, 권한, 리소스와 상태를 계속 검증해야 합니다. 중첩 객체·컬렉션 원소·메서드 인자 검증은 실제 선언과 호출 경로에 달려 있습니다.

## Exception Handling

### BusinessException

다음은 독립된 두 예시입니다. 단일 인자 생성자는 업무 코드 500을, 두 인자 생성자는 전달된 코드를 사용합니다. 업무 코드와 HTTP 상태는 따로 기록합니다.

```java
// 两个独立示例：默认业务码与显式业务码
throw new BusinessException("资源不存在");
throw new BusinessException(404, "资源不存在");
```

### GlobalExceptionHandler

omni-common 자동 구성이 GlobalExceptionHandler를 가져옵니다. 아래 표는 예외가 해당 MVC advice까지 도달한 경로에 적용합니다. Gateway, Security Filter, OAuth 프로토콜과 응답 확정 이후 오류는 별도 경계입니다.

| 예외 | HTTP 상태 | R.code와 처리 |
|---|---|---|
| BusinessException | handler가 상태를 설정하지 않음. 별도 변경 없는 일반 응답은 보통 200 | 예외의 업무 코드, WARN |
| MethodArgumentNotValidException / BindException | 400 | 400, 필드 오류 집계 |
| MissingServletRequestParameterException / MethodArgumentTypeMismatchException | 400 | 400, 인자 오류 |
| AccessDeniedException / AuthorizationDeniedException | 403 | 403, 권한 부족 |
| NoResourceFoundException | 404 | 404, 서비스 내부 리소스 미일치 |
| HttpRequestMethodNotSupportedException | 405 | 405 |
| HttpMediaTypeNotSupportedException | 415 | 415 |
| 나머지 Exception | 500 | 500, ERROR에 스택 보관, 응답에는 일반 메시지 |

오류, null, 날짜·금액 직렬화 전체는 [API 계약](api-contract.kr.md)에서 관리합니다. 모든 JSON 파싱 실패가 400으로 매핑되지는 않습니다.

### 규칙

- 처리가 필요한 예외를 삼키지 않고 예상된 거부와 비정상 실패를 구분합니다. 의도적 폴백 catch는 로그, 반환값과 복구 동작을 설명해야 합니다.
- 경계에서 null을 검사합니다. Optional / Objects.requireNonNull은 업무 오류 계약의 대체물이 아닙니다.
- e.printStackTrace() 대신 logger 예외 인자로 필요한 스택을 남깁니다. 로그와 응답에 자격 증명·개인정보가 노출되지 않게 합니다.

## Logging

@Slf4j와 인자 플레이스홀더를 사용합니다. 실제 영향에 따라 실패는 ERROR, 예상된 거부·폴백은 WARN, 주요 업무 지점은 INFO, 진단은 DEBUG로 기록합니다.

```java
// 使用占位符，示意日志不包含凭据
log.info("用户 {} 从 {} 登录", userId, ip);
```

System.out.println이나 로그 문자열 연결을 사용하지 않습니다. 비밀번호, Bearer/Refresh Token, 클라이언트 시크릿과 완전한 개인정보를 일반 로그에 남기지 않습니다. OperLog 마스킹 규칙만으로 모든 사용자 정의 필드의 마스킹을 증명할 수 없으며 필드 선택과 접근은 업무에서 책임집니다. 추적·지표·연결은 [관측성](observability.kr.md)을 참조하세요.

## OOP 관례

- 모델 목적에 맞게 Lombok을 선택합니다. Serializable을 구현하는 클래스는 serialVersionUID를 선언하되 모든 DTO, record와 도메인 클래스에 해당 인터페이스를 강제하지 않습니다.
- 보통 상수, 정적 변수, 필드, 생성자, public, private 순으로 구성하고 캡슐화와 책임으로 불변 조건을 드러냅니다.
- 상수를 equals 수신자로 쓰면 그 수신자의 null 문제를 피합니다. 박싱은 valueOf를 사용합니다. BigDecimal 금액은 정밀도·반올림·비교 규칙을 명시하고 부동소수점 근사 비교에는 업무 허용 오차를 정합니다.

## Collection & Concurrency

- 용량은 예상 규모로 선택하며 모든 컬렉션에 같은 고정 용량을 지정하지 않습니다. CollectionUtils.isEmpty는 null을 처리하며 null이 아닌 컬렉션에는 isEmpty()도 유효합니다.
- 순회 중 변경은 적합한 Iterator, removeIf 또는 별도 단계로 처리합니다. 키와 값이 모두 필요하면 entrySet()을 우선합니다.
- 새 스레드 풀은 큐, 스레드 상한, 거부 정책과 종료 생명주기를 명시하고 통제되지 않는 Executors.newXxx() 기본 설정을 피합니다.
- ConcurrentHashMap과 원자 변수는 객체 간 불변 조건을 자동 보호하지 않습니다. 복합 갱신, DB 동시성과 승인 점유는 모듈의 잠금·버전·트랜잭션 규약에 따르며 동시성 컬렉션만으로 검증됐다고 하지 않습니다.

## 명명 관례 (Java)

다음은 새 코드 규칙이며 기존 이름은 소스가 기준입니다. 예를 들어 OperType에는 Enum 접미사가 없습니다. 형식만 맞추려고 공개 타입 이름을 바꾸지 않습니다.

| 타입 | 규칙 또는 예시 |
|---|---|
| 패키지 / 클래스 / 메서드·변수 | 소문자 점 구분, UpperCamelCase, lowerCamelCase |
| 상수 / boolean | UPPER_SNAKE_CASE, 보통 필드 is 접두사 지양 |
| 추상 클래스 / 예외 | AbstractXxx / XxxException |
| enum / DTO | 의미가 명확한 이름, 책임에 따라 Request·Response·VO |
| Feign / Service / 구현 / Mapper | 모듈 명명 유지, XxxService / XxxServiceImpl / XxxMapper |

## 코드 형식 (Java)

새 코드와 변경 코드에 4칸 공백, K&R 중괄호, 연산자·쉼표 간격과 메서드 사이 빈 줄을 사용합니다. 120자와 짧은 인자 목록을 가독성 목표로 삼고 필요하면 인자 객체를 검토합니다. 기존 Controller에는 5개를 넘는 인자가 있을 수 있습니다.

imports는 java, jakarta, 서드파티, com.omni로 묶습니다. Controller 어노테이션 패키지 외 wildcard는 금지합니다. 현재 Maven POM 검색에서 Checkstyle / Spotless / PMD 선언은 발견되지 않았습니다. 컴파일 성공이 형식 목표의 자동 검증을 뜻하지 않습니다.

## 주석

새 public 클래스·메서드에 Javadoc을 쓰고 핵심 필드와 직관적이지 않은 로직은 필요에 따라 설명합니다. 코드 주석은 중국어로 작성하며 이름만 반복하지 않습니다. TODO는 `// TODO: [module] description`, FIXME는 문제와 경계를 명시합니다. 규칙을 기존 코드 전체 준수의 증거로 삼지 않습니다.

## 보안 및 권한

### 기능 권한(API 인증)

일반 보호 업무 API는 Gateway JWT 검증 후 서비스에서 신뢰된 신원 assertion을 검증하고 Authentication을 구성한 뒤 메서드 권한을 확인합니다. 클라이언트가 직접 보낸 신원·권한 Header를 믿지 않습니다. 서명 대상과 네트워크 신뢰 한계는 [아키텍처](architecture.kr.md)에서 관리합니다.

다음 두 어노테이션은 Base 사전 타입 갱신 진입점에서 발췌했으며 리소스 소속 검사를 대신하지 않습니다.

```java
@PutMapping("/{id}")
@PreAuthorize("hasAuthority('dict:type:update')")
```

권한 코드는 dict:type:update처럼 resource:action 계층으로 명명합니다. 새 쓰기는 @PreAuthorize, scripts/sql/seed/auth.sql, database/seed/manifest.yaml과 frontend v-permission을 동기화합니다. 공개 가입, OAuth/OIDC, 내부 Token과 MyJobController의 행 ownership은 각 계약에 따릅니다. 일반 메서드 거부는 403이며 Filter 오류는 실제 경로로 판단합니다.

### 데이터 권한(DataPermission)

```text
Trusted identity --> Request context --> Permission-aware scope
    --> Domain table/aggregate policy --> SQL + explicit tenant predicates
    --> finally: restore/clear context
```

Auth MybatisPlusConfig는 DataPermission → OptimisticLock → Pagination 순서이며 범용 TenantLine이 없습니다. DataPermissionHandlerImpl은 sys_user, sys_org_unit, sys_role을 매핑하고 테넌트 조건은 해당 Mapper/Service가 명시적으로 담당합니다.

| dataScope | Auth sys_user 예시 |
|---|---|
| ALL / TENANT | 이 handler는 조건을 추가하지 않음. 모든 호출에 테넌트 간 접근을 허용한다는 뜻은 아님 |
| DEPT_AND_BELOW / DEPT / CUSTOM | 해석된 조직 ID 집합으로 primary_unit_id 필터링 |
| SELF | 현재 사용자 id로 필터링 |

Auth handler는 컨텍스트가 없을 때도 조건을 생략할 수 있으므로 인터셉터 존재만으로 진입 권한을 대신할 수 없습니다. 일반 두 인자 역할 해석과 permission을 지정한 세 인자 해석은 다릅니다. 후자는 해당 권한을 부여하는 역할만 병합하며 일치하는 권한이 없으면 거부합니다. 전체 여섯 범위와 우선순위는 [핵심 흐름](core-flows.kr.md)을 참조하세요.

common-service 영속화가 켜진 CRM/SRM/Procurement/Asset은 TenantLine → DataPermission → OptimisticLock → Pagination을 사용합니다. 테이블 정책, owner 매핑, 루트 집계를 통한 하위 상속과 AccessGuard는 각 도메인이 소유합니다. TenantLine은 업무 접두사에만 적용하고 sys_mq_message는 테넌트 전체 중계 대상으로 유지합니다.

새 테이블은 소유 서비스의 DataScopeTablePolicy / handler를 갱신하고 owner, 현재 사용자, 테넌트 공유와 하위 관계를 구분합니다. owner 열이 없는 테이블에 조건을 추가하거나 조직 ID를 사용자 외래키로 취급하지 않습니다. DataPermission은 Pagination보다 앞에 둡니다.

OnlineUserServiceImpl은 Redis 온라인 사용자를 명시적으로 검사합니다. ALL은 테넌트 전체 의미를 유지하고 나머지는 소속 확인 후 SELF나 조직 범위를 적용합니다. 강제 로그아웃은 Redis 변경과 성공 감사 전에 대상을 확인하며 신원·테넌트·유효 범위가 없으면 업무 코드 403입니다. 이 특정 동작을 모든 SQL에 일반화하지 않습니다.

### ThreadLocal 사용 규범

Auth DataScopeResolveFilter는 try 안에서 해석·설정하고 finally에서 정리하며 내부 경로는 별도 분기를 가집니다. common-service 신원 Filter와 메서드 DataScope Aspect는 각 생명주기에서 컨텍스트를 복구하거나 정리합니다.

DataScopeContext, 신원 컨텍스트와 XssRuleHolder는 비동기 작업에 자동 전파되지 않습니다. 필요한 신원·tenantId를 명시하고 작업에서 생명주기를 관리합니다. 신뢰성 메시지 전송은 ThreadLocal로 테넌트를 추정하지 않습니다.

### XSS 방어(3계층 방어 아키텍처)

```text
JSON String conversion --> Jackson 2/3 XSS modules + request rules
Query/form access --> XssHttpServletRequestWrapper
Gateway response --> SecurityHeadersFilter (no input sanitization)
```

omni-common XssAutoConfiguration은 Servlet 환경과 XssConfigProvider Bean 조건이 맞을 때 /* Filter를 등록하고 classpath에 따라 Jackson 2/3 String 모듈을 등록합니다. JSON 정제는 실제 converter가 해당 모듈과 요청 스레드 규칙을 써야 합니다. 등록되지 않은 구현체만으로는 충분하지 않습니다.

XssFilter는 테넌트 Header로 설정을 가져옵니다. 테넌트 누락·파싱 불가, 비활성 설정이나 빈 규칙은 통과합니다. 활성 시 getParameter / getParameterValues를 감싸고 finally에서 규칙을 정리합니다. getParameterMap은 재정의하지 않아 모든 입력 접근 방식을 보장하지 않습니다.

| ruleType | 현재 처리 |
|---|---|
| HTML_TAG | 지정 태그와 짝 내용 제거, 세로줄로 구분한 태그 이름을 리터럴로 이스케이프 |
| EVENT_HANDLER | 설정 패턴과 일치하는 이벤트 속성 제거 |
| DANGEROUS_PROTOCOL | 대소문자 구분 없이 지정 프로토콜 제거, 세로줄로 구분한 값을 리터럴로 처리 |
| CUSTOM_PATTERN | 정규식 치환, 잘못된 정규식은 WARN 후 해당 규칙 건너뜀 |

규칙 필터링은 출력 인코딩이나 리치 텍스트 신뢰 정책을 대체하지 않습니다. Gateway의 X-Content-Type-Options: nosniff, X-Frame-Options: DENY와 Referrer-Policy는 응답 방어이며 입력 정제를 수행하지 않습니다.

Auth XssConfigProviderImpl은 xss:enabled:{tenantId} / xss:rules:{tenantId}를 TTL 30분으로 사용합니다. 스위치·규칙 변경 후 XssConfigServiceImpl이 두 키를 능동 삭제하며 TTL만 기다리지 않습니다. 삭제가 로컬 트랜잭션 안에 있으므로 commit과 동시 캐시 재적재 간 경쟁이 없다고 증명하지 않습니다. Auth Provider가 모든 Redis 장애를 잡아 DB로 폴백하는 것도 아닙니다.

common-service CachedServiceXssConfigProvider는 캐시 실패 후 Auth를 시도하고 실패나 결과 부재 시 DefaultXssSettingsFallback의 활성 기준 규칙을 사용합니다. 권위 있는 비활성 설정은 유지합니다. Provider 순서, 실제 Jackson 모듈과 Filter Bean을 함께 검증합니다. [개발 가이드](guides/scaffold-development.kr.md)를 참조하세요.

## MQ 메시지 전송 기록 및 보상 관리(omni-common-mqlog)

신뢰성 전송에는 omni-common-mqlog와 DB·Job 기능을 사용합니다. ReliableMessageRelay / ReliableMessageTemplate의 send(bindingName, payload, tenantId[, msgKey])는 양의 tenantId를 명시합니다. Outbox와 업무 갱신이 함께 commit되는 것은 실제 로컬 트랜잭션이 있을 때이며 원격 서비스나 Broker 트랜잭션을 포함하지 않습니다.

sys_mq_message DDL의 유일한 소유자는 database/changelog/common/0001-mq-schema.yaml이며 과거 schema.sql은 주 생성 경로가 아닙니다. mqRelayHandler에는 @XxlJob과 @SystemJobMeta를 함께 선언합니다. 중계는 PENDING/FAILED를 모든 테넌트에서 검색하고 내외부 관리 조회는 tenantId로 제한합니다. MessageSender는 broker_type으로 선택하며 현재 구현은 RocketMqMessageSender입니다.

상태, 지수 백오프, 데드레터 재전송·건너뛰기와 확장은 [MQ 신뢰성](mq-reliability.kr.md), 중단점·복구는 [Outbox 개발](wiki/outbox-development.kr.md)에서 관리합니다. 의존성이나 어노테이션만으로 실행기, Broker, 소비와 보상이 현재 동작한다고 증명할 수 없습니다.

## 운영 로그(OperLog)

@OperLog AOP는 표시된 진입점의 메타데이터와 선택 스냅샷을 MQ로 보내 Base에 저장합니다. 모든 이벤트를 포함하거나 완전한 감사, 무손실·규정 준수를 보장하지 않습니다.

### 핵심 기록 흐름

```text
@OperLog --> OperLogAspect --> OperLogProducer
    --> ReliableMessageRelay / Outbox (if available)
    --> StreamBridge direct send (fallback)
    --> Base OperLogConsumer --> sys_oper_log
XXL-JOB operLogArchiveHandler --> sys_oper_log_archive
```

OperLogProducer는 ReliableMessageRelay가 있으면 Outbox, 없으면 StreamBridge를 사용하며 전송 예외를 WARN으로 기록합니다. 직접 전송은 반환 boolean을 확인하지 않아 신뢰할 수 있는 전달 영수증이 아닙니다. 업무와 같은 트랜잭션인지도 호출 경계에 달려 있습니다.

Base 소비자는 event_id 유일 제약의 DuplicateKeyException을 무시하고 다른 오류는 미들웨어로 전파합니다. eventId가 없는 과거 메시지는 서비스가 결정적 legacy ID를 만듭니다. 전체 업무 소비의 exactly-once 증거는 아닙니다.

OperLogArchiver는 @XxlJob / @SystemJobMeta가 있는 operLogArchiveHandler입니다. 기본 메타데이터는 cron 0 0 2 * * ?, retentionDays 180, 배치 크기 1000입니다. 실제 스케줄은 XXL-JOB 설정과 실행이 필요합니다. 콜드 테이블에 먼저 삽입한 뒤 핫 테이블에서 삭제하고 배치 실패 시 중단합니다. 같은 Bean 내부에서 archiveBatch를 호출하므로 해당 @Transactional만으로 이 경로의 proxy 트랜잭션을 보장할 수 없습니다. 콜드 테이블 존재는 장기 보존 정책이나 규정 준수 검증이 아닙니다.

### @OperLog 어노테이션 사용

다음은 실제 DictTypeController.update에서 imports/Javadoc을 생략한 발췌입니다. 중국어 module과 인자는 소스 그대로입니다. Auth 사용자 관리를 OperLog 도입 예시로 삼지 않습니다.

```java
@OperLog(module = "字典类型管理", operType = OperType.UPDATE, entityClass = SysDictType.class, idExpr = "#id")
@PutMapping("/{id}")
@PreAuthorize("hasAuthority('dict:type:update')")
public R<SysDictType> update(@PathVariable Long id,
                              @RequestHeader("X-Tenant-Id") Long tenantId,
                              @Valid @RequestBody UpdateDictTypeRequest request) {
    String operator = SecurityContextHolder.getContext().getAuthentication().getName();
    return R.ok(dictTypeService.updateType(id, tenantId, request, operator));
}
```

| 인자 | 현재 의미 |
|---|---|
| module / operType | module 기본값은 빈 문자열이나 규칙상 명시, operType 기본값 없음 |
| entityClass / idExpr | 기본 Object.class / 빈 문자열, 스냅샷 활성 시 Mapper와 인자·결과 SpEL로 ID 추출 |
| recordParams / recordSnapshot | 기본 true, 민감 정보·대용량 본문 작업에서는 비활성 가능 |
| excludeFields | 추가 제외 필드, 기본 빈 배열, 마스킹 시 이름 정규화 |

### OperType 열거

| 타입 | 스냅샷과 구성 조건이 맞을 때 |
|---|---|
| CREATE | 결과 ID로 newValue 조회 |
| UPDATE | 전후를 조회하고 최상위 필드 값을 비교 |
| DELETE | oldValue 조회, newValue는 null |
| QUERY / EXPORT / IMPORT | 이 스냅샷 분기는 엔티티를 읽지 않음 |

### 개발 제약사항

- 운영 로그가 필요한 새 쓰기는 민감도에 맞게 설정합니다. **omni-auth는 omni-common-operlog 의존성과 @OperLog를 금지합니다**. 인증 이벤트는 sys_audit_log에 저장하며 로그인 기록은 읽기 전용 투영입니다. 별도 sys_login_log를 만들지 않습니다.
- Mapper와 SpEL이 실제로 해석돼야 합니다. tenantId 속성이 있는 스냅샷은 현재의 양의 tenantId와 일치해야 남고 속성이 없는 전역 엔티티는 기존 동작을 유지합니다. 이는 쓰기 권한 검사와 별개입니다.
- OperLogSanitizer는 일치 필드, 전화, 이메일과 자격 증명 조각을 규칙으로 마스킹하며 임의의 모든 텍스트를 보호하지 않습니다. 긴 문자열은 앞 4000자에 ...[TRUNCATED]를 붙여 총길이가 4000을 넘을 수 있습니다. JSON이 무효가 될 수 있고 DB 스냅샷 열은 JSON이므로 긴 스냅샷 저장 성공을 보장하지 않습니다.
- 대상 메서드 예외는 전파합니다. 후처리는 Exception을 잡고 SpEL/Mapper 검색은 일부 폴백을 갖지만 실행 전 스냅샷 조회와 ID cast가 모두 보호 구간에 있지는 않습니다. 모든 수집 오류가 업무에 영향 없다고 단정하지 않습니다.
- 로그 responseStatus는 정상 반환 시 200, 대상 예외 시 500이며 최종 MVC HTTP 상태나 R.code가 아닙니다.

### 모듈 책임

| 모듈 | 컴포넌트 |
|---|---|
| omni-common-core | OperLog, OperType, OperLogMessage, 운영 코드에 Spring 의존성 없음 |
| omni-common-operlog | Aspect, Producer, Sanitizer, EntityDiffer와 조건부 자동 구성 |
| omni-base | Consumer, OperLogService, Archiver와 핫·콜드 테이블 |

## Common Starter 온보딩 규범

### Starter 모듈 개요

common 모듈은 10개이며 core가 기본 타입, 나머지 9개가 조합 기능입니다. 전체 의존성과 모듈 표의 유일한 기준은 [아키텍처](architecture.kr.md)입니다.

| 모듈 | 경계 |
|---|---|
| omni-common-core | 기본 타입, 운영 Spring 자동 구성 없음 |
| omni-common | Servlet Web/MVC, Jackson, 예외 advice와 조건부 XSS |
| omni-common-mybatis | 기본 OptimisticLock → MySQL Pagination, YAML 매핑·논리 삭제·ID 규칙 |
| omni-common-redis | 블로킹 RedisTemplate, RedisUtils와 연결 기본값 |
| omni-common-redis-reactive | reactive 의존성·timeout YAML, 이 모듈의 자체 AutoConfiguration.imports 없음 |
| omni-common-job | 조건부 XXL-JOB 실행기와 시스템 작업 등록 등 |
| omni-common-mqlog | Outbox, 중계, 전송 전략과 관리, DB/Job/Broker 설정 필요 |
| omni-common-operlog | 어노테이션 AOP와 전송, Servlet/AspectJ/Mapper/StreamBridge 등 의존성 필요 |
| omni-common-workflow | 공통 Workflow 기능, 업무 도메인에 엔진 runtime을 넣지 않음 |
| omni-common-service | Servlet 신원, 내부 API, DataScope, 영속화와 XSS 조합 |

8개 기능 모듈에 자체 AutoConfiguration.imports가 있습니다. 항목은 등록 진입점을 뜻하며 조건 충족이나 Bean 활성의 증거는 아닙니다. 모든 모듈이 같은 구조라고 하지 않습니다.

### 신규 서비스 온보딩 절차

Servlet 도메인은 common-service를 우선하고 OperLog/Job/MQ를 필요에 따라 선택합니다. Gateway/Auth/Base/Workflow는 각 경계를 유지합니다. 신원 설정, GATEWAY_ASSERTION_SECRET과 OMNI_INTERNAL_API_TOKEN은 비공개 환경에서 가져오며 활성 기능은 시작 검증을 충족해야 합니다.

테넌트·데이터 범위 기능을 켜면 도메인 테이블과 맞는 유일한 TenantTablePolicy/DataScopeTablePolicy를 제공합니다. assertion Filter는 AuthorizationFilter 앞, 신원 컨텍스트는 사전 인증 뒤에 놓고 Servlet 중복 등록을 피합니다. XSS Provider는 조건 소비보다 먼저 구성하고 FilterRegistration과 Jackson 2/3 Bean을 함께 검증합니다. 전체 도입·생성기·Docker 절차는 [개발 가이드](guides/scaffold-development.kr.md)에서 관리합니다.

### 오버라이드 메커니즘

- MyBatis 기본 인터셉터는 MybatisPlusInterceptor 타입 부재 조건입니다. 프로젝트 명명에 맞는 Bean과 올바른 순서를 유지합니다.
- RedisTemplate은 redisTemplate 이름, RedisUtils는 반환 타입 부재 조건입니다. 교체 조건을 혼동하지 않습니다.
- XSS는 Provider Bean과 Servlet/classpath 조건에 따릅니다. 일부 OperLog Bean에는 ConditionalOnMissingBean이 없습니다. 모든 Starter Bean이 동일하게 교체된다고 하지 말고 구성 소스와 컨테이너 컨텍스트를 검증합니다.

### Redis Starter 상호 배타 제약

WebFlux Gateway는 omni-common-redis-reactive만, Servlet은 블로킹 모듈을 사용하며 같은 서비스에서 혼용하지 않습니다. Netty event loop에서 블로킹 호출을 실행하지 않습니다. reactive 의존성이 업무의 블로킹 작업을 자동 변환하지 않습니다.

## 구성 참조 표

### application.yml 주요 구성 항목

다음은 저장소 YAML과 공식 Compose를 구분합니다. 전체 설정은 [배포](docker-deployment.kr.md)와 현재 구성이 기준입니다.

| 항목 | 현재 규칙 또는 공식 Docker 값 |
|---|---|
| server.port / SERVER_PORT | 호스트 진입점 8100–8107, 앱 컨테이너 HTTP 8080, Frontend 3000 |
| spring.application.name | omni-auth/base/gateway/workflow/crm/srm/procurement/asset, 서비스별 구성 |
| spring.datasource.* | 공식 mysql:3306, omni_app, 비밀번호 OMNI_DB_PASSWORD, Gateway 업무 데이터소스 없음 |
| spring.data.redis.* | 공식 redis:6379, DB와 직렬화는 서비스별 |
| spring.cloud.nacos.discovery.server-addr | 공식 nacos:8848, Auth/Base/Workflow와 도메인 변수 매핑은 각 구성 참조 |
| spring.cloud.nacos.discovery.ip | Compose는 컨테이너 발견용 빈 값, 호스트 loopback으로 고정하지 않음 |
| auth.jwks.uri / AUTH_JWKS_URI | Gateway YAML `http://localhost:8100/oauth2/jwks`, Compose `http://omni-auth:8080/oauth2/jwks` |
| auth.jwks.cache-ttl | Gateway 기본 5m |
| spring.profiles.active | Spring Profile, Compose profile, CLI preset 구분 |

비밀번호, Nacos 신원·서명 시크릿, Gateway assertion 시크릿과 내부 Token은 비공개 설정을 사용합니다. 과거 소스 fallback은 권장 배포 자격 증명이 아닙니다.

### MyBatis-Plus 구성

common-mybatis YAML은 map-underscore-to-camel-case=true, 논리 삭제 열 deleted(삭제 1, 유효 0)와 id-type=auto를 선언합니다. 실제 엔티티·서비스는 별도 매핑·정책을 가질 수 있으므로 모든 테이블의 열을 기본값으로 추정하지 않습니다. 사용자 정의 인터셉터도 DataPermission을 Pagination 앞에 유지합니다.

### XXL-JOB 구성(omni-base)

| 항목 | 현재 Base 또는 공식 Docker 값 |
|---|---|
| xxl.job.admin.addresses | YAML http://127.0.0.1:18080/xxl-job-admin, Compose http://xxl-job-admin:8080/xxl-job-admin |
| xxl.job.executor.appname | omni-base |
| xxl.job.executor.port | Base YAML 9901, 속성 클래스 일반 fallback 9999, 다른 서비스는 개별 설정 |
| xxl.job.executor.address / ip | Compose http://omni-base:9901/ / omni-base |
| xxl.job.access-token | 일반 속성 기본값은 빈 값, Compose는 양쪽 일치하는 비공개 XXL_JOB_ACCESS_TOKEN 필수 |
| xxl.job.executor.enabled | 자동 구성 기본 활성, Compose는 XXL_JOB_ENABLED 사용 |

관리자 자격 증명은 비공개 환경으로 제공합니다. 기본 cron, 등록 성공과 실제 실행은 서로 다릅니다. 시스템 handler에는 @XxlJob과 @SystemJobMeta가 모두 필요하며 사용자 작업·보상 규칙은 [스케줄링](scheduling.kr.md)에서 관리합니다.

## Service 계층 설계 패턴

### 인터페이스 + 구현 분리

```text
XxxService (contract)
    ^ implements
XxxServiceImpl (business behavior; local transactions where configured)
```

인터페이스는 호출 분리와 Mock에 도움이 되지만 구현 교체도 의미와 트랜잭션 계약을 유지해야 합니다. Feign은 별도 원격 프로토콜 어댑터이며 임의 Service 인터페이스가 자동으로 사용 가능한 Feign 클라이언트가 되지는 않습니다.

### 트랜잭션 관리 전략

| 상황 | 규칙과 한계 |
|---|---|
| 읽기 전용 | @Transactional(readOnly = true)는 트랜잭션 설정·힌트이며 권한이나 일반적인 쓰기 금지의 증거가 아님 |
| 로컬 쓰기 | @Transactional 기본 REQUIRED, 실제 proxy 호출·rollback 규칙·DB 경계 확인 |
| 원격 효과 | 로컬 트랜잭션·Outbox는 보낼 메시지를 기록할 수 있으나 완료된 HTTP/Broker/외부 계정 효과를 취소하지 못함 |
| 독립 트랜잭션 | REQUIRES_NEW는 적용 가능한 proxy와 성공 commit이 필요하며 로그 보존 자체를 보장하지 않음 |

어노테이션만으로 같은 클래스 내부 호출이 proxy를 지난다고 추정하거나 모든 로그 전송을 독립 트랜잭션으로 설명하지 않습니다. 주요 전이, 점유와 멱등성은 도메인 사실 문서와 회귀가 기준입니다.

### 예외 처리 체인

```text
Controller --> Service --> matching MVC advice --> R
Gateway / Security Filter / protocol endpoint --> own response boundary
```

Auth는 common-core와 common 모두에 의존합니다. common imports가 전역 advice를 등록하고 AuthExceptionHandler는 com.omni.auth.controller로 범위가 제한되어 공존합니다. common에 의존하지 않는다는 과거 Javadoc보다 현재 POM/imports가 우선합니다. 실제 응답은 해당 handler 또는 Filter/프로토콜이 처리합니다. 중국어 오류 메시지를 번역된 실제 백엔드 반환값처럼 제시하지 않습니다.

## 문제 해결 가이드

[문제 해결](guides/troubleshooting.kr.md)은 재현 절차, [소스 디버깅](wiki/source-debugging.kr.md)은 호출·변수·중단점을 관리합니다. assertion, 메서드 권한, 테넌트, 가시성과 트랜잭션을 따로 확인하며 빈 목록, 403, 404와 연결 실패를 같은 결과로 취급하지 않습니다.

[AGENTS.md](../AGENTS.md)에 따라 로컬 Docker에서 빌드·테스트·실행합니다. container/session 핸들, 종료 코드와 기존 부작용을 기록하고 로그·비공개 설정을 보호합니다. 실행 중 작업을 먼저 확인하며 데이터 초기화·권한 비활성화·공유 자원 삭제로 성공을 만들지 않습니다.

## Testing

JUnit 5 / Mockito는 로직을 격리합니다. MockMvc는 특정 MVC wire를 검증할 수 있지만 신원·Service·엔진 Mock으로 실제 Gateway, DB, Flowable, Broker나 브라우저 흐름을 증명하지 않습니다. SpringBootTest도 실제 의존성·환경·assertion이 범위를 정하며 클래스 이름만으로 통합 검증이 되지 않습니다.

테스트는 모듈 src/test/java에 두고 동작·전제를 드러내는 이름으로 모듈 규칙을 따릅니다. 기존 테스트는 Auth/CRM뿐 아니라 여러 업무·공통 모듈을 다룹니다. 조건부 skip은 pass가 아닙니다.

백엔드 구현 변경 후 Docker에서 Maven Wrapper reactor를 실행합니다.

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install
```

JDK 25, JAVA_HOME과 모듈 순서는 tools 컨테이너/reactor가 제공합니다. 문서만 바뀌면 구현·빌드 입력의 정확한 일치를 확인한 뒤 기존 기록을 재사용할 수 있으며 출처, pass, skip과 한계를 밝힙니다. Markdown 렌더링, 단위 테스트나 빌드 성공을 전체 업무 검증으로 간주하지 않습니다.
