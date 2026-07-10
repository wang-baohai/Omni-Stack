# Backend Patterns & Conventions

> This document defines the internal organization of the Omni-Stack backend. All backend code must follow these patterns.  
> For an architecture overview, see [architecture.en.md](architecture.en.md). For Docker deployment configuration, see [docker-deployment.en.md](docker-deployment.en.md).

## Layering

```
Controller --> Service --> Repository (DAO)
     |            |            |
  Param check   Business     Data access
  Result wrap   logic        SQL / ORM
                Transaction
```

### Controller Layer

- **Responsibility**: Receive HTTP requests, validate parameters, call Service, wrap response
- **No business logic** in Controller
- All methods return `R<T>` (success) or `R<PageResult<T>>` (paginated)
- Request DTOs validated with `@Valid` (Jakarta Bean Validation)
- DTOs can be inner static classes of the Controller or standalone files
- RESTful style: `GET /user/{id}`, `POST /user`, `GET /user/list`

### Service Layer

- **Interface + Implementation**: `XxxService` (interface) + `XxxServiceImpl` (class)
- `@Service` annotation on the implementation class
- `@Transactional` on implementation methods:
  - Read operations: `@Transactional(readOnly = true)`
  - Write operations: `@Transactional`
- No `HttpServletRequest` / `HttpServletResponse` in Service layer
- Constructor injection via `@RequiredArgsConstructor` + `final` fields

### Repository / DAO Layer

- Use MyBatis-Plus or JPA; Mapper interface naming: `XxxMapper`
- No business logic in Mapper
- SQL parameters: always `#{}`, never `${}` (prevent SQL injection)

## Dependency Injection (DI)

```java
// CORRECT: Constructor injection via Lombok
@RequiredArgsConstructor
@RestController
public class UserController {
    private final UserService userService;
}

// FORBIDDEN: Field injection
@Autowired
private UserService userService;
```

**Rule**: All dependency injection must use `@RequiredArgsConstructor` + `final` fields. `@Autowired` field injection is forbidden.

### Design Rationale: Why @RequiredArgsConstructor Instead of @Autowired

| Consideration | Rationale |
|---------------|-----------|
| **Immutability** | `final` fields ensure dependencies cannot be changed once constructed, preventing accidental replacement at runtime |
| **Compile-time safety** | Missing dependencies cause compile errors, not runtime `NullPointerException` |
| **Test-friendliness** | Constructor injection allows passing Mock objects directly in tests, without a Spring container or reflection utilities |
| **Clarity** | All dependencies are visible in the class constructor, no need to scan all fields for `@Autowired` |
| **Spring official recommendation** | The Spring team has recommended constructor injection since 4.x; field injection was marked as discouraged after 5.x |

## Validation

- Use Jakarta Bean Validation annotations on request DTOs
- Trigger validation with `@Valid` on Controller method parameters
- `MethodArgumentNotValidException` and `BindException` are caught globally by `GlobalExceptionHandler`

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
// Business rule violation
throw new BusinessException("User not found");           // code: 500
throw new BusinessException(404, "User not found");      // code: 404
```

### GlobalExceptionHandler

Located in `omni-common`, catches all exceptions and converts them to `R<Void>`:

| Exception | Handling |
|-----------|----------|
| `BusinessException` | `log.warn` + `R.fail(code, message)` |
| `MethodArgumentNotValidException` | HTTP 400 + aggregate field errors + `R.fail(400, ...)` |
| `BindException` | HTTP 400 + aggregate field errors + `R.fail(400, ...)` |
| `Exception` (catch-all) | `log.error` with full stack + `R.fail("Internal server error")` |

### Rules

- Never use empty `catch` blocks
- Never use exceptions for flow control
- NPE defense: use `Optional`, `Objects.requireNonNull()`, early null checks
- Log exceptions with `log.error("msg", e)` preserving the full stack trace; never `e.printStackTrace()`

## Logging

Use Lombok `@Slf4j` and the `log` object:

| Level | Usage |
|-------|-------|
| `ERROR` | System-level errors requiring immediate attention |
| `WARN` | Business exceptions, recoverable issues |
| `INFO` | Key business flow checkpoints |
| `DEBUG` | Development and debugging |

```java
// CORRECT: parameterized placeholder
log.info("User {} logged in from {}", userId, ip);

// FORBIDDEN: string concatenation
log.info("User " + userId + " logged in");

// FORBIDDEN: console output
System.out.println("debug info");
```

- Never log sensitive information (passwords, tokens, ID numbers)

## OOP Conventions

- All POJO classes implement `Serializable` and declare `serialVersionUID`
- Use Lombok: `@Data`, `@Getter`, `@Slf4j`, `@RequiredArgsConstructor`
- Class member order: static constants -> static variables -> instance variables -> constructors -> public methods -> private methods
- `equals`: put constants/deterministic values on the left: `"success".equals(status)`
- Wrapper types: use `valueOf()`, never `new Integer()`
- Float comparison: use `BigDecimal` or specify an epsilon

## Collection & Concurrency

- Initialize collections with capacity: `new ArrayList<>(16)`, `new HashMap<>(16)`
- Empty check: use `CollectionUtils.isEmpty()`, not `== null` or `size() == 0`
- No `remove` during iteration; use `Iterator` or `removeIf()`
- Map traversal: use `entrySet()`, not `keySet()` then `get()`
- Thread pools: use `ThreadPoolExecutor`, never `Executors.newXxx()`
- Concurrent modifications: `ConcurrentHashMap`, `AtomicXxx`; avoid manual locking unless necessary

## Naming Conventions (Java)

| Type | Style | Example |
|------|-------|---------|
| Package | lowercase, dot-separated | `com.omni.business.controller` |
| Class | UpperCamelCase | `UserController`, `BusinessException` |
| Method / Variable | lowerCamelCase | `getUserById`, `createTime` |
| Constant | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT`, `DEFAULT_PAGE_SIZE` |
| Boolean variable | No `is` prefix | `deleted`, `enabled` (not `isDeleted`) |
| Abstract class | `Abstract` prefix | `AbstractEntity` |
| Exception class | `Exception` suffix | `BusinessException` |
| Enum class | `Enum` suffix | `OrderStatusEnum` |
| DTO class | `Request` / `Response` / `VO` suffix | `CreateUserRequest`, `UserVO` |
| Feign interface | `FeignClient` suffix | `RemoteServiceFeignClient` |
| Service interface | `XxxService` | `UserService` |
| Service implementation | `XxxServiceImpl` | `UserServiceImpl` |
| Mapper interface | `XxxMapper` | `UserMapper` |

## Code Format (Java)

- Indent: 4 spaces, no tabs
- Max line length: 120 characters
- Braces: K&R style (opening brace on same line)
- One blank line between methods
- Spaces around operators: `a + b`, `if (x == y)`
- Space after commas: `method(a, b, c)`
- Max 5 method parameters; encapsulate as Request object if exceeded
- Import order: `java.*` -> `jakarta.*` -> third-party -> `com.omni.*`, blank line between groups
- No wildcard imports (`import xxx.*`), except for Controller annotation packages

## Comments

- Classes, class attributes, and class methods must have Javadoc (`/** ... */`)
- Use `//` for inline comments explaining key logic
- No meaningless comments (e.g., `// get name` for `getName()`)
- TODO format: `// TODO: [module] description`, clean up regularly
- FIXME format: `// FIXME: description` for known issues

## Security & Permission

### Functional Permission (API Authorization)

Controller methods use `@PreAuthorize` to declare required permission codes:

```java
@PreAuthorize("hasAuthority('system:user:create')")
@PostMapping
public R<Void> create(@Valid @RequestBody CreateUserRequest request) {
    userService.createUser(request);
    return R.ok();
}
```

- Permission code format: `resource:action` (e.g., `system:user:update`, `system:role:delete`)
- Permission set is carried in JWT Claims; Spring Security validates before method invocation
- Returns 403 Forbidden when unauthorized

### Data Permission (DataPermission)

Implements row-level data filtering based on MyBatis-Plus `DataPermissionInterceptor`, with zero intrusion to business code.

**Core Component Collaboration**:

```
Gateway → X-User-Id/X-Tenant-Id Header
    ↓
DataScopeResolveFilter (@Order(0), OncePerRequestFilter)
    ↓ Query roles → Merge dataScope (most permissive wins) → Resolve accessible unit IDs
    ↓
DataScopeContext (ThreadLocal stores userId, tenantId, primaryUnitId, effectiveScope, accessibleUnitIds)
    ↓
DataPermissionInterceptor (MyBatis-Plus InnerInterceptor)
    ↓ Calls DataPermissionHandlerImpl.getSqlSegment(Table, Expression, String)
    ↓ Appends WHERE clause only to sys_user table
    ↓
Business SQL execution (automatically filtered result set)
    ↓
DataScopeContext.clear() (finally block, prevents ThreadLocal leaks)
```

**Six-Level Data Scope**:

| dataScope | SQL Behavior |
|-----------|-------------|
| `ALL` | No additional condition (cross-tenant visible) |
| `TENANT` | No additional condition (existing tenant_id filter already satisfies) |
| `DEPT_AND_BELOW` | `WHERE sys_user.primary_unit_id IN (current dept and descendant IDs)` |
| `DEPT` | `WHERE sys_user.primary_unit_id IN (current dept ID)` |
| `CUSTOM` | `WHERE sys_user.primary_unit_id IN (custom dept + descendant IDs)` |
| `SELF` | `WHERE sys_user.id = {current user ID}` |

**Multi-Role Merging**: When a user has multiple roles, the highest-priority dataScope is used (ALL > TENANT > DEPT_AND_BELOW > DEPT > CUSTOM > SELF).

**Extending Data Permission to New Tables**:

1. Add the target table name and corresponding column mapping in `DataPermissionHandlerImpl`
2. The target table must contain a foreign key column linking to `sys_user` (e.g., `primary_unit_id`, `create_by`)
3. `DataPermissionInterceptor` must be registered before `PaginationInnerInterceptor`

**In-Memory Filtering Mode**: For data not from database queries (e.g., online user list from Redis), the Controller reads the data scope from `DataScopeContext.get()` and manually filters by `primaryUnitId`.

### ThreadLocal Usage Guidelines

- `DataScopeContext` uses `ThreadLocal<DataScopeInfo>` to store request-scoped context
- **Must** be cleared in a `try/finally` block to prevent leaks in thread pool scenarios
- Write timing: Before the `try` block in `DataScopeResolveFilter.doFilterInternal()`
- Clear timing: In the `finally` block of `DataScopeResolveFilter.doFilterInternal()`

### XSS Protection (Three-Layer Defense Architecture)

XSS protection employs three layers of defense, with configuration managed via database + Redis cache, supporting per-tenant isolation.

```
Layer 1: Jackson StringDeserializer — Automatically sanitizes String fields in @RequestBody JSON
Layer 2: Servlet Filter + HttpServletRequestWrapper — Sanitizes query parameters
Layer 3: Gateway WebFilter — Adds security response headers
```

**Core Components**:

| Component | Module | Responsibility |
|-----------|--------|----------------|
| `XssConfigProvider` | omni-common-core | SPI interface, implemented by specific service modules |
| `XssSettings` / `XssRule` | omni-common-core | Configuration value objects (enabled + rule list) |
| `XssSanitizer` | omni-common | Core sanitization logic (HTML_TAG / EVENT_HANDLER / DANGEROUS_PROTOCOL / CUSTOM_PATTERN) |
| `XssStringDeserializer` | omni-common | Jackson String deserializer wrapper, auto-sanitizes |
| `XssFilter` | omni-common | OncePerRequestFilter, loads config + sets ThreadLocal |
| `XssHttpServletRequestWrapper` | omni-common | Overrides getParameter/getParameterValues |
| `XssAutoConfiguration` | omni-common | Auto-registers Filter + Jackson SimpleModule |
| `XssConfigProviderImpl` | omni-auth | Implements config loading (Redis cache + DB fallback) |
| `SecurityHeadersFilter` | omni-gateway | Adds X-Content-Type-Options / X-Frame-Options / Referrer-Policy |

**Rule Types**:

| ruleType | Matching & Sanitization Method |
|----------|-------------------------------|
| `HTML_TAG` | Strips paired tags and self-closing tags |
| `EVENT_HANDLER` | Strips `on*` attributes |
| `DANGEROUS_PROTOCOL` | Replaces `javascript:` / `vbscript:` / `data:` protocol strings |
| `CUSTOM_PATTERN` | Custom regex replacement |

**Extending to New Services**: Implement the `XssConfigProvider` interface to automatically gain XSS protection. After importing `omni-common` as a dependency, it auto-configures via `AutoConfiguration.imports`.

**Cache Strategy**: Redis keys `xss:enabled:{tenantId}` + `xss:rules:{tenantId}`, TTL 30 minutes. All write operations (toggle switches, rule CRUD) proactively invalidate the cache.

## MQ Message Sending Records & Compensation Management (omni-common-mqlog)

The MQ message sending records and compensation management system is built on a Transactional Outbox + XXL-JOB async delivery architecture, providing reliable message sending capabilities for all microservices — plug-and-play with zero business code.

### Core Architecture

```
Business transaction (@Transactional)
    ↓
ReliableMessageTemplate.send(bindingName, payload)
    ↓ INSERT sys_mq_message (status=PENDING) -- within the same local transaction
    ↓
XXL-JOB mqRelayHandler (10s polling)
    ↓
MqMessageRelayService.relayAll()
    ↓ Batch query PENDING/FAILED with next_retry_time <= NOW()
    ↓
MessageSender.send(message) -- strategy pattern, routed by broker_type
    ↓
Success → status=SENT | Failure → retry_count++, exponential backoff | Exceeded → DEAD_LETTER
```

### Core Components

| Component | Responsibility |
|-----------|----------------|
| `ReliableMessageTemplate` | Provides `send(bindingName, payload)` / `send(bindingName, payload, msgKey)` overloads; INSERTs message record within the caller's transaction |
| `MqMessageRelayService` | Polls pending messages, invokes `MessageSender` strategy for delivery, handles retry backoff and dead letter marking |
| `MqMessageRelayJob` | XXL-JOB handler (`@XxlJob("mqRelayHandler")` + `@SystemJobMeta`), triggers relay logic |
| `MessageSender` | Strategy interface, routed by `broker_type`. Current implementation: `RocketMqMessageSender` (based on StreamBridge); extensible for `KafkaMessageSender` |
| `MqMessageInternalController` | Feign internal query API (`/api/internal/mq-message`), for aggregated query services |

### Steps to Onboard a New Service

1. Add `omni-common-mqlog` dependency in POM
2. Ensure `omni-common-mybatis` (database) and `omni-common-job` (XXL-JOB) are already included
3. For RocketMQ sending capability, add `spring-cloud-starter-stream-rocketmq` dependency
4. `sys_mq_message` table is auto-created (`schema.sql` + `CREATE TABLE IF NOT EXISTS`)
5. `mqRelayHandler` auto-registers with XXL-JOB (each service executor has a different AppName, so handler names are naturally isolated)
6. Inject `ReliableMessageTemplate` in business code and call `send()`

### Exponential Backoff Strategy

Retry interval: `2^retryCount × 10s`. 1st retry: 20s, 2nd: 40s, 3rd: 80s. Exceeding `max_retry` (default 3) transitions to DEAD_LETTER status.

### Dead Letter Handling

- **Resend**: Resets PENDING/FAILED/DEAD_LETTER status to PENDING, clears `retry_count`; relay task re-delivers on next poll
- **Ignore**: DEAD_LETTER → SKIPPED, a terminal state confirming no further delivery is needed

## Operation Log (OperLog)

The operation log system is based on an AOP + RocketMQ async architecture, automatically capturing request context and entity change snapshots from Controller methods to provide comprehensive audit trails for business operations.

### Core Recording Flow

```
Controller method (@OperLog annotation)
    ↓
OperLogAspect (AOP @Around advice)
    ↓ Capture request context: username, tenantId, IP, URL, request parameters
    ↓ Entity change snapshot: query oldValue before UPDATE/DELETE, query newValue after operation
    ↓ EntityDiffer.diff(): field-level diff comparison (UPDATE only)
    ↓
OperLogProducer.send(OperLogMessage)
    ↓ RocketMQ async send
    ↓
omni-base consumer
    ↓ INSERT INTO sys_oper_log (hot table)
    ↓
OperLogArchiver (@Scheduled daily at 02:00)
    ↓ Migrates records older than 180 days from hot table to sys_oper_log_archive (cold table)
    ↓ Batch processing (1000 per batch), deletes from hot table after migration
```

### @OperLog Annotation Usage

```java
@OperLog(module = "User Management", operType = OperType.CREATE, entityClass = SysUser.class, idExpr = "#result.data.id")
@PreAuthorize("hasAuthority('system:user:create')")
@PostMapping
public R<UserVO> create(@Valid @RequestBody CreateUserRequest request) {
    return R.ok(userService.createUser(request));
}
```

**Annotation Parameter Reference**:

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `module` | String | Yes | Business module name, e.g., "User Management", "Dictionary Type Management" |
| `operType` | OperType | Yes | Operation type enum (see table below) |
| `entityClass` | Class<?> | Conditional | Target entity class, used by AOP for automatic diff snapshots. Not required for QUERY/EXPORT/IMPORT types |
| `idExpr` | String | Conditional | SpEL expression to extract entity ID from method parameters or return value. e.g., `"#id"`, `"#result.data.id"` |

### OperType Enum

| Enum Value | Meaning | Requires entityClass | Description |
|------------|---------|---------------------|-------------|
| `CREATE` | Create | Recommended | AOP extracts new entity ID from return value and queries newValue |
| `UPDATE` | Update | Yes | AOP queries oldValue before operation, queries newValue after, performs field-level diff |
| `DELETE` | Delete | Yes | AOP queries oldValue before operation, newValue is null |
| `QUERY` | Query | No | Records query behavior only, no entity snapshot |
| `EXPORT` | Export | No | Records export behavior only, no entity snapshot |
| `IMPORT` | Import | No | Records import behavior only, no entity snapshot |

### Development Constraints

1. **All new write-operation Controller methods must be annotated with `@OperLog`**, specifying `module` and `operType`; for entity changes, also specify `entityClass` and `idExpr`
2. **omni-auth module disables `@OperLog`**: Authentication behavior is fully recorded by login logs (`sys_login_log`) and audit logs (`sys_audit_log`); omni-auth does not include the `omni-common-operlog` dependency and does not use `@OperLog` on controller methods
3. **Onboarding new microservices**: Add `omni-common-operlog` dependency in `pom.xml` and configure RocketMQ; the module auto-registers AOP aspects and MQ producers via `AutoConfiguration.imports`
4. **`entityClass` purpose**: AOP uses `ApplicationContext` to find the `BaseMapper` for the corresponding entity type, automatically executing `selectById` to obtain pre/post-change snapshots
5. **`idExpr` SpEL syntax**: Supports referencing method parameters (`#id`, `#request.id`) and return values (`#result.data.id`); resolution failure logs a warning but does not affect business logic
6. **JSON snapshot limit**: Max 4000 characters per oldValue/newValue; auto-truncated if exceeded
7. **Hot/Cold table separation**: Hot table `sys_oper_log` retains recent 180 days of data for fast queries; cold table `sys_oper_log_archive` provides long-term retention for compliance. Archival job runs daily at 02:00, 1000 records per batch; stops current archival on failure

### Module Responsibilities

| Module | Component | Responsibility |
|--------|-----------|----------------|
| `omni-common-core` | `OperLog` annotation, `OperType` enum, `OperLogMessage` POJO | Pure POJO layer, no Spring dependency |
| `omni-common-operlog` | `OperLogAspect`, `OperLogProducer`, `EntityDiffer`, `OperLogAutoConfiguration` | AOP aspect + MQ producer + entity diff + auto-configuration |
| `omni-base` | `OperLogConsumer`, `OperLogArchiver` | MQ consumer writes to hot table + scheduled archival to cold table |

## Common Starter Onboarding Specification

The project splits shared capabilities into 6 modules. New microservices simply add Maven dependencies — no manual configuration required.

### Starter Module Overview

| Module | Responsibility | Auto-Configuration | Target Service Type |
|--------|----------------|--------------------|--------------------|
| `omni-common-core` | Pure POJO layer | None (no Spring dependency) | All modules |
| `omni-common` | Web auto-configuration | `JacksonConfig` (time serialization), `WebMvcConfig` (CORS), `GlobalExceptionHandler`, `XssAutoConfiguration` (Filter + Jackson Module) | Servlet services |
| `omni-common-mybatis` | Database capabilities | `MybatisPlusAutoConfiguration`: `MybatisPlusInterceptor` (MySQL `PaginationInnerInterceptor`) + YAML defaults (camelCase mapping, logical delete, auto-increment ID) | Servlet services |
| `omni-common-redis` | Blocking Redis | `RedisAutoConfiguration`: `RedisTemplate<String, Object>` (Jackson serialization) + `RedisUtils` utility + Lettuce connection pool config | Servlet services |
| `omni-common-redis-reactive` | Reactive Redis | `spring-boot-starter-data-redis-reactive` + YAML default timeout config | WebFlux services (e.g., Gateway) |
| `omni-common-mqlog` | Reliable MQ sending | `ReliableMessageTemplate` (Transactional Outbox), `MqMessageRelayService` (XXL-JOB async delivery), `MessageSender` strategy interface, `MqMessageInternalController` (Feign internal query), `schema.sql` (auto-create table) | Servlet services (requiring MQ) |

**Auto-Configuration Registration**: All starters register via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — the standard mechanism for Spring Boot 3+/4+ (replacing the legacy `spring.factories`).

### Steps to Onboard a New Service

1. **POM Dependencies**: Declare required starters in `pom.xml`:
   ```xml
   <dependency><groupId>com.omni</groupId><artifactId>omni-common-core</artifactId></dependency>
   <dependency><groupId>com.omni</groupId><artifactId>omni-common</artifactId></dependency>
   <dependency><groupId>com.omni</groupId><artifactId>omni-common-mybatis</artifactId></dependency>
   <dependency><groupId>com.omni</groupId><artifactId>omni-common-redis</artifactId></dependency>
   ```
2. **application.yml**: Only configure datasource and Redis connection info:
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
3. **Main class**: Add `@MapperScan("com.omni.xxx.mapper")`
4. **XSS Protection**: Implement the `XssConfigProvider` SPI interface (`omni-common` auto-registers Filter + Jackson Module)
5. **Auto-applied**: Pagination plugin, Jackson time serialization, CORS config, `GlobalExceptionHandler`, `RedisUtils` utility are all auto-configured — no additional setup needed

### Override Mechanism

All starter auto-configuration beans use `@ConditionalOnMissingBean`, allowing services to override as needed:

- **MybatisPlusInterceptor**: Define a `mybatisPlusInterceptor` bean to override default pagination config. Typical scenario: adding `DataPermissionInterceptor` (**must be registered before `PaginationInnerInterceptor`**)
- **RedisTemplate**: Define `@Bean(name = "redisTemplate")` to replace the default serialization strategy
- **XSS Configuration**: `XssAutoConfiguration` conditionally depends on `XssConfigProvider` bean; if the SPI is not implemented, the XSS filter chain does not activate

### Redis Starter Mutual Exclusion

`omni-common-redis` (blocking) and `omni-common-redis-reactive` (reactive) **must not be mixed in the same service**:

- **WebFlux services** (e.g., Gateway): Can only depend on `omni-common-redis-reactive`; blocking Redis calls will starve the Netty event loop threads
- **Servlet services**: Use `omni-common-redis` (blocking); `RedisUtils` provides synchronous APIs

## Configuration Reference

### application.yml Key Configuration Items

The following are the core configuration items in backend microservice `application.yml`:

| Config Key | Description | Default | Docker Env Override |
|------------|-------------|---------|-------------------|
| `server.port` | Service port | 8100/8101/8102/8103 | `SERVER_PORT=8080` |
| `spring.application.name` | Nacos service name | omni-auth/base/gateway/workflow | — |
| `spring.datasource.url` | Database connection | `jdbc:mysql://127.0.0.1:3306/omni_xxx` | `SPRING_DATASOURCE_URL` |
| `spring.datasource.username` | Database user | `root` | `SPRING_DATASOURCE_USERNAME` |
| `spring.datasource.password` | Database password | `root` | `SPRING_DATASOURCE_PASSWORD` |
| `spring.data.redis.host` | Redis host | `127.0.0.1` | `SPRING_DATA_REDIS_HOST` |
| `spring.data.redis.port` | Redis port | `6379` | `SPRING_DATA_REDIS_PORT` |
| `spring.data.redis.database` | Redis DB index | 0 | `SPRING_DATA_REDIS_DATABASE` |
| `spring.cloud.nacos.discovery.server-addr` | Nacos address | `127.0.0.1:8848` | `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR` |
| `spring.cloud.nacos.discovery.ip` | Registration IP | `127.0.0.1` | `SPRING_CLOUD_NACOS_DISCOVERY_IP` |
| `auth.jwks.uri` | JWKS endpoint | `http://localhost:8100/oauth2/jwks` | `AUTH_JWKS_URI` |
| `auth.jwks.cache-ttl` | Public key cache TTL | `5m` | — |
| `spring.profiles.active` | Active profile | `default` | `SPRING_PROFILES_ACTIVE` |

### MyBatis-Plus Configuration

| Config Key | Description | Default |
|------------|-------------|---------|
| `mybatis-plus.configuration.map-underscore-to-camel-case` | Underscore to camelCase | `true` |
| `mybatis-plus.global-config.db-config.logic-delete-field` | Logical delete field | `deleted` |
| `mybatis-plus.global-config.db-config.logic-delete-value` | Logical delete value | `1` |
| `mybatis-plus.global-config.db-config.logic-not-delete-value` | Logical not-delete value | `0` |
| `mybatis-plus.global-config.db-config.id-type` | ID strategy | `AUTO` (database auto-increment) |

### XXL-JOB Configuration (omni-base)

| Config Key | Description | Default |
|------------|-------------|---------|
| `xxl.job.admin.addresses` | Admin address | `http://127.0.0.1:18080/xxl-job-admin` |
| `xxl.job.executor.appname` | Executor name | `omni-base` |
| `xxl.job.executor.port` | Executor port | `9999` |
| `xxl.job.accessToken` | Communication token | `default_token` |

## Service Layer Design Patterns

### Interface + Implementation Separation

```
UserService (interface)     — Defines business method signatures
    ↑ implements
UserServiceImpl (@Service)  — Implements business logic + @Transactional
```

**Design Rationale**:
- Interface layer can be reused by OpenFeign clients
- Unit tests can directly Mock interfaces without depending on implementation classes
- Implementation classes can be replaced without affecting callers

### Transaction Management Strategy

| Scenario | Annotation | Description |
|----------|------------|-------------|
| Read-only query | `@Transactional(readOnly = true)` | Optimizes DB query performance, forbids write operations |
| Write operations | `@Transactional` | Default REQUIRED propagation level |
| Cross-service calls | `@Transactional` + Outbox pattern | Local transaction writes to Outbox table; does not participate in remote transactions |
| Independent transaction | `@Transactional(propagation = REQUIRES_NEW)` | e.g., logging — ensures log is recorded even if the main transaction rolls back |

### Exception Handling Chain

```
Controller method
    │ Calls Service method
    ▼
Service layer
    │ Business validation failure → throw new BusinessException(400, "Username already exists")
    │ Resource not found         → throw new BusinessException(404, "User not found")
    ▼
GlobalExceptionHandler (@RestControllerAdvice)
    │ @ExceptionHandler(BusinessException.class)
    │   → log.warn + R.fail(code, message)
    │ @ExceptionHandler(MethodArgumentNotValidException.class)
    │   → HTTP 400 + aggregate field errors + R.fail(400, "field: message")
    │ @ExceptionHandler(AccessDeniedException.class)
    │   → HTTP 403 + R.fail(403, "Insufficient permissions")
    │ @ExceptionHandler(Exception.class)
    │   → log.error (full stack) + R.fail("Internal server error")
    ▼
Unified R<Void> response
```

**Special handling for omni-auth**: The Auth module depends on `omni-common-core` (not `omni-common`), so `GlobalExceptionHandler` is not in its component scan scope. The Auth module provides equivalent exception handling via `AuthExceptionHandler` (scoped `@RestControllerAdvice`).

## Troubleshooting Guide

### Common Issues

| Issue | Possible Cause | Solution |
|-------|---------------|----------|
| `@PreAuthorize` not working | `GatewayPreAuthFilter` not registered | Check SecurityConfig for `addFilterBefore(new GatewayPreAuthFilter(), AuthorizationFilter.class)` |
| Paginated query returns empty | `PaginationInnerInterceptor` not registered | Check interceptor registration order in `MybatisPlusConfig` |
| `@Transactional` ineffective | Method not public / self-invocation | `@Transactional` only works on public methods; intra-class method calls bypass the proxy |
| Redis connection timeout | Container network unreachable | `docker compose exec omni-auth ping redis` to verify connectivity |
| Nacos registration failure | Incorrect address config | Ensure `server-addr` uses container name `nacos:8848` instead of `localhost` |
| JWT verification failure | Public key cache expired | Check `auth.jwks.cache-ttl` config, or restart Gateway to clear cache |
| XXL-JOB registration failure | Admin not started | Ensure `xxl-job-admin` container is healthy |
| MQ messages not delivered | Relay Job not triggered | Check `mqRelayHandler` scheduling status in XXL-JOB Admin console |

### Debugging Tips

```bash
# View backend service logs
docker compose logs -f omni-auth

# Check environment variables
docker compose exec omni-auth env | grep SPRING

# Test database connection
docker compose exec omni-auth sh -c 'nc -zv mysql 3306'

# Check Nacos registered instances
curl -s http://localhost:8848/nacos/v1/ns/instance/list?serviceName=omni-auth
```

## Testing (Future Scaffold)

No tests exist yet. When adding:

- **Unit tests**: JUnit 5 + Mockito, placed in `src/test/java/`
- **Integration tests**: `@SpringBootTest` + embedded database or Test Containers
- Test class naming: `XxxTest` (unit) or `XxxIntegrationTest` (integration)
- Test method naming: `should_<expected>_when_<condition>`
