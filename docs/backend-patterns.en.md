# Backend Patterns & Conventions

> This document owns backend layering, coding conventions and common capability boundaries. Requirements apply to new or modified code; they do not certify all historical code or business flows.
> See [Architecture](architecture.en.md) for modules and trust, [API Contract](api-contract.en.md) for wire behavior, and [Docker Deployment](docker-deployment.en.md) for deployment variables. Wiki pages and development guides own complete procedures.

## Layering

```text
Controller --> Service --> Mapper (DAO)
HTTP/validation   Domain rules   SQL/data access
                  Local transaction boundary
```

### Controller Layer

- Receive HTTP input, trigger declared constraints, enforce entry permissions and identity, then call a Service. Services own domain transitions and persistence rules.
- Ordinary business APIs use `R<T>` or `R<PageResult<T>>`. OAuth/OIDC, redirects and Actuator endpoints retain their protocol responses.
- `@Valid` triggers declared DTO constraints; it does not establish tenant ownership, visibility, uniqueness or valid business state. DTOs may be separate classes or static nested classes.
- Actual Controllers define paths, methods and fields. Illustrations and excerpts are not complete callable APIs. New write endpoints require matching permission seeds, migration checks and frontend permissions.

### Service Layer

- Use `XxxService` and `XxxServiceImpl`, mark business implementations `@Service`, and inject dependencies through constructors.
- Declare transactions at local boundaries that need atomicity. Choose read-only transactions according to consistency requirements; this convention does not mean every existing method has a transaction.
- New domain Services should accept explicit identity, tenant and business parameters, keeping HTTP objects in adapters. Handle historical exceptions according to the module contract.

### Repository / DAO Layer

- Business persistence primarily uses MyBatis-Plus and `XxxMapper`. Flowable manages its own persistence; this does not establish a general project JPA integration.
- Mappers access data; Services make domain decisions. Bind values with `#{}`. Dynamic table, column and ordering identifiers must come from controlled mappings, never raw user input.
- Check tenant and data scope explicitly for custom SQL, child joins and cross-tenant background tasks. Adding a Starter does not prove every SQL path is protected.

## Dependency Injection (DI)

This constructor-injection illustration omits imports and does not introduce a production Controller.

```java
// 构造器注入示意，省略 imports
@RequiredArgsConstructor
@RestController
public class UserController {
    private final UserService userService;
}
```

Do not use `@Autowired` field injection. Prefer `@RequiredArgsConstructor` with `final` fields. Explicit constructors, `@Bean` methods or `ObjectProvider` can express `@Lazy`, optional dependencies or initialization branches; see OperLogProducer and OperLogAutoConfiguration.

### Design Rationale: Why @RequiredArgsConstructor Instead of @Autowired

| Consideration | Benefit and limit |
|---|---|
| Visible dependencies | Constructors show what an object needs; tests can supply mocks directly |
| Stable fields | `final` prevents reassignment, not mutation or concurrent misuse of the referenced object |
| Failure diagnosis | Missing arguments in manual construction can fail compilation; missing or ambiguous Spring beans and configuration can still fail at startup |
| Optional capabilities | Optionality needs explicit handling; Lombok does not remove runtime conditions |

## Validation

Use Jakarta Bean Validation with `@Valid @RequestBody`. This CreateDictTypeRequest excerpt omits other fields and imports. The four language versions preserve actual Chinese diagnostics in source excerpts.

```java
/** 字典类型编码 */
@NotBlank(message = "字典类型编码不能为空")
private String typeCode;

/** 字典类型名称 */
@NotBlank(message = "字典类型名称不能为空")
private String typeName;
```

Matching MVC advice handles validation failures, missing parameters and type mismatches. Services still validate tenant, permissions, resources and state. Nested objects, collection elements and method parameters depend on their actual declarations and invocation paths.

## Exception Handling

### BusinessException

These are two independent examples. The single-argument constructor uses business code 500; the two-argument constructor uses the supplied code. Record business codes separately from HTTP status.

```java
// 两个独立示例：默认业务码与显式业务码
throw new BusinessException("资源不存在");
throw new BusinessException(404, "资源不存在");
```

### GlobalExceptionHandler

omni-common auto-configuration imports GlobalExceptionHandler. This table applies when an exception reaches that MVC advice; Gateway, Security Filters, OAuth protocols and errors after response commitment have separate boundaries.

| Exception | HTTP status | `R.code` and handling |
|---|---|---|
| `BusinessException` | Handler sets no status; an otherwise unchanged ordinary response is usually 200 | Carried business code; WARN |
| `MethodArgumentNotValidException` / `BindException` | 400 | 400; aggregate field errors |
| `MissingServletRequestParameterException` / `MethodArgumentTypeMismatchException` | 400 | 400; parameter error |
| `AccessDeniedException` / `AuthorizationDeniedException` | 403 | 403; insufficient permission |
| `NoResourceFoundException` | 404 | 404; unmatched resource inside a service |
| `HttpRequestMethodNotSupportedException` | 405 | 405 |
| `HttpMediaTypeNotSupportedException` | 415 | 415 |
| Catch-all `Exception` | 500 | 500; ERROR with stack, generic response message |

[API Contract](api-contract.en.md) owns complete error, null, date and monetary serialization behavior. JSON parsing failures are not universally mapped to 400.

### Rules

- Handle exceptions that need action. Distinguish expected rejection from unexpected failure. Deliberate fallback catches must explain logs, results and recovery.
- Check nulls at boundaries; Optional and Objects.requireNonNull do not replace business error contracts.
- Preserve required stacks through logger exception arguments, not e.printStackTrace(). Keep credentials and personal data out of logs and responses.

## Logging

Use @Slf4j and parameterized placeholders. Select levels by effect: ERROR for failure, WARN for expected rejection or degradation, INFO for important business milestones and DEBUG for diagnosis.

```java
// 使用占位符，示意日志不包含凭据
log.info("用户 {} 从 {} 登录", userId, ip);
```

Do not use System.out.println or concatenate log strings. Passwords, Bearer/Refresh Tokens, client secrets and complete personal information must not enter ordinary logs. OperLog masking rules do not prove arbitrary custom fields are redacted; business code still owns field selection and access. See [Observability](observability.en.md) for tracing, metrics and correlation.

## OOP Conventions

- Choose Lombok according to the model. Classes implementing Serializable must declare serialVersionUID; not every DTO, record or domain class needs that interface.
- Prefer constants, static variables, fields, constructors, public methods, then private methods. Make invariants explicit through encapsulation and responsibilities.
- A constant equals receiver avoids a null receiver; use valueOf for boxing. Monetary BigDecimal operations need explicit precision, rounding and comparison rules. Approximate floating-point comparisons require domain tolerance.

## Collection & Concurrency

- Size collections according to expected use rather than assigning a fixed capacity everywhere. CollectionUtils.isEmpty handles null; isEmpty() is valid for a non-null collection.
- Modify during iteration through an appropriate Iterator, removeIf or separate phases. Prefer entrySet() when both keys and values are needed.
- New thread pools need explicit queues, thread limits, rejection policies and shutdown lifecycles. Avoid uncontrolled Executors.newXxx() defaults.
- ConcurrentHashMap and atomic variables do not protect invariants across objects. Compound updates, database concurrency and approval occupancy follow module locking, versioning or transaction protocols; a concurrent collection is not a concurrency test.

## Naming Conventions (Java)

These are conventions for new code; existing names are source facts. OperType has no Enum suffix, for example. Do not rename public types merely for formatting.

| Type | Convention or example |
|---|---|
| Package / class / method and variable | Lowercase dot-separated package; UpperCamelCase; lowerCamelCase |
| Constant / boolean | UPPER_SNAKE_CASE; normally avoid an is field prefix |
| Abstract class / exception | AbstractXxx / XxxException |
| Enum / DTO | Meaningful type name; Request, Response or VO according to responsibility |
| Feign / Service / implementation / Mapper | Follow module naming; XxxService / XxxServiceImpl / XxxMapper |

## Code Format (Java)

Use four-space indentation, K&R braces, spacing around operators and commas, and blank lines between methods in new or modified code. Treat 120 characters and short parameter lists as readability targets; consider a parameter object when appropriate. Existing Controllers can exceed five parameters.

Group imports as java, jakarta, third-party and com.omni. Avoid wildcard imports except Controller annotation packages. The current Maven POM scan found no Checkstyle, Spotless or PMD declaration; successful compilation does not verify these formatting targets.

## Comments

Document new public classes and methods with Javadoc; explain key fields and non-obvious logic as needed. Code comments are Chinese and should add meaning. Use `// TODO: [module] description`; FIXME must identify the issue and boundary. Conventions are not proof that all historical code complies.

## Security & Permission

### Functional Permission (API Authorization)

Ordinary protected business requests rely on Gateway JWT validation, then service verification of trusted identity assertions, Authentication construction and method permissions. Do not trust identity/permission headers supplied directly by clients. [Architecture](architecture.en.md) owns signature coverage and network trust limits.

These two annotations come from the Base dictionary-type update endpoint and do not replace ownership checks:

```java
@PutMapping("/{id}")
@PreAuthorize("hasAuthority('dict:type:update')")
```

Permission codes use resource:action hierarchies, such as dict:type:update. New writes need matching @PreAuthorize, scripts/sql/seed/auth.sql, database/seed/manifest.yaml and frontend v-permission updates. Public registration, OAuth/OIDC, internal Token protocols and MyJobController row ownership follow their own contracts. Ordinary method denial returns 403; classify Filter errors by their actual path.

### Data Permission (DataPermission)

```text
Trusted identity --> Request context --> Permission-aware scope
    --> Domain table/aggregate policy --> SQL + explicit tenant predicates
    --> finally: restore/clear context
```

Auth MybatisPlusConfig orders DataPermission → OptimisticLock → Pagination, with no general TenantLine. DataPermissionHandlerImpl maps sys_user, sys_org_unit and sys_role. Relevant Mappers/Services still own explicit tenant predicates.

| dataScope | Auth sys_user example |
|---|---|
| ALL / TENANT | This handler adds no condition; that does not authorize every call across tenants |
| DEPT_AND_BELOW / DEPT / CUSTOM | Filter primary_unit_id by resolved organization IDs |
| SELF | Filter id by the current user |

With missing context this Auth handler may also add no predicate; interceptor presence cannot replace entry authorization. Ordinary two-argument role resolution differs from three-argument permission-specific resolution. The latter merges only roles granting the target permission and rejects when no grant matches. [Core Flows](core-flows.en.md) owns all six scopes and precedence.

When common-service persistence is enabled, CRM/SRM/Procurement/Asset use TenantLine → DataPermission → OptimisticLock → Pagination. Domains own table policy, owner mappings, child inheritance through aggregate roots and AccessGuard. TenantLine applies only to domain prefixes; sys_mq_message remains available for cross-tenant relay.

For new tables update the owning service DataScopeTablePolicy/handler. Distinguish owner, current holder, tenant-shared data and child relationships. Do not append owner predicates to tables without those columns or treat organization IDs as user foreign keys. DataPermission precedes Pagination.

OnlineUserServiceImpl checks Redis online users explicitly: ALL retains cross-tenant behavior; other scopes first establish tenant membership, then SELF or organization scope. Force logout checks targets before Redis changes and success audit. Missing identity, tenant or valid scope produces business code 403. This specific behavior is not a rule for all SQL.

### ThreadLocal Usage Guidelines

Auth DataScopeResolveFilter resolves and sets context inside try, then clears it in finally; internal paths have a separate branch. common-service identity Filters and the method DataScope Aspect own separate lifecycles and must restore or clear their context.

DataScopeContext, identity context and XssRuleHolder do not automatically propagate to async work. Pass identity/tenantId explicitly and manage task lifecycles. Reliable messaging must not infer tenants from ThreadLocal.

### XSS Protection (Three-Layer Defense Architecture)

```text
JSON String conversion --> Jackson 2/3 XSS modules + request rules
Query/form access --> XssHttpServletRequestWrapper
Gateway response --> SecurityHeadersFilter (no input sanitization)
```

In a Servlet environment, omni-common XssAutoConfiguration registers the /* Filter when an XssConfigProvider bean exists, and Jackson 2/3 String modules according to classpath conditions. JSON cleaning needs the actual converter to use its module and request-thread rules. Implementing an unregistered interface is insufficient.

XssFilter loads settings from the tenant header. Missing/unparseable tenants, disabled settings or empty rules pass through. When enabled, it wraps getParameter/getParameterValues and clears rules in finally. It does not override getParameterMap, so not every input access mechanism is covered.

| ruleType | Current handling |
|---|---|
| HTML_TAG | Remove configured tags/paired content; escape pipe-separated tag names as literals |
| EVENT_HANDLER | Remove event attributes matching the configured pattern |
| DANGEROUS_PROTOCOL | Remove configured protocols case-insensitively; treat pipe-separated values literally |
| CUSTOM_PATTERN | Regex replacement; WARN and skip invalid regex rules |

Rule filtering does not replace output encoding or trusted rich-text policies. Gateway headers X-Content-Type-Options: nosniff, X-Frame-Options: DENY and Referrer-Policy are response defenses, not input sanitization.

Auth XssConfigProviderImpl uses xss:enabled:{tenantId} and xss:rules:{tenantId}, with a 30-minute TTL. XssConfigServiceImpl actively deletes both keys after setting/rule writes; waiting for TTL is insufficient. Deletion is inside the local transaction, not proof of race-free commit/cache refill. Auth Provider also does not catch every Redis failure for automatic database fallback.

common-service CachedServiceXssConfigProvider tries Auth after cache failure, then the enabled DefaultXssSettingsFallback baseline after failure or no result; authoritative disabled settings remain disabled. Verify Provider auto-configuration order, actual Jackson modules and Filter beans together. See [Development Guide](guides/scaffold-development.en.md).

## MQ Message Sending Records & Compensation Management (omni-common-mqlog)

Reliable business sending uses omni-common-mqlog with DB and Job capabilities. ReliableMessageRelay/ReliableMessageTemplate send(bindingName, payload, tenantId[, msgKey]) require an explicit positive tenantId. Outbox and business updates share a commit only when the call has an actual local transaction; it does not include remote services or Broker transactions.

database/changelog/common/0001-mq-schema.yaml solely owns sys_mq_message DDL; historical schema.sql is not the primary creation path. mqRelayHandler declares both @XxlJob and @SystemJobMeta. Relay scans PENDING/FAILED across tenants; external/internal management queries filter tenantId. MessageSender routes by broker_type, with RocketMqMessageSender as the current implementation.

[MQ Reliability](mq-reliability.en.md) owns states, exponential backoff, dead-letter resend/skip and extensions; [Outbox Development](wiki/outbox-development.en.md) owns breakpoints and recovery. Dependencies and annotations do not prove the executor, Broker, consumer and compensation are currently running.

## Operation Log (OperLog)

@OperLog AOP records operation metadata and optional snapshots for annotated endpoints, then sends them through MQ for Base persistence. This does not cover every event or guarantee comprehensive auditing, losslessness or compliance.

### Core Recording Flow

```text
@OperLog --> OperLogAspect --> OperLogProducer
    --> ReliableMessageRelay / Outbox (if available)
    --> StreamBridge direct send (fallback)
    --> Base OperLogConsumer --> sys_oper_log
XXL-JOB operLogArchiveHandler --> sys_oper_log_archive
```

OperLogProducer uses Outbox when ReliableMessageRelay is available and StreamBridge otherwise; it catches send exceptions and logs WARN. Direct sending does not inspect the returned boolean, so it is not a reliable delivery receipt. Sharing the business transaction also depends on the call boundary.

The Base consumer ignores DuplicateKeyException under event_id uniqueness and rethrows other failures for middleware handling. OperLogService derives a deterministic legacy ID when eventId is absent. This is not proof of exactly-once processing for all business consumers.

OperLogArchiver is operLogArchiveHandler with both @XxlJob/@SystemJobMeta. Metadata defaults are cron 0 0 2 * * ?, retentionDays 180 and batch size 1000. Actual scheduling needs XXL-JOB configuration and execution. It inserts cold rows before deleting hot rows and stops after a failed batch. archiveBatch is invoked within the same bean; its @Transactional annotation alone does not establish proxy transaction protection on this path. A cold table does not prove retention policy or compliance acceptance.

### @OperLog Annotation Usage

This existing DictTypeController.update excerpt omits imports/Javadoc; Chinese module text and parameters match source. Auth user management is unsuitable as an OperLog onboarding example.

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

| Parameter | Current meaning |
|---|---|
| module / operType | module defaults to empty; project convention requires an explicit name. operType has no default |
| entityClass / idExpr | Defaults Object.class / empty; snapshot collection uses a Mapper and parameter/result SpEL to obtain the ID |
| recordParams / recordSnapshot | Default true; may be disabled for sensitive or large-text operations |
| excludeFields | Additional excluded fields; default empty array; masking normalizes names |

### OperType Enum

| Type | Behavior when snapshots and configuration permit |
|---|---|
| CREATE | Extract result ID and query newValue |
| UPDATE | Query before/after and compare top-level field values |
| DELETE | Query oldValue; newValue stays null |
| QUERY / EXPORT / IMPORT | This snapshot branch does not read entities |

### Development Constraints

- Configure new writes that need operation logs according to sensitivity. **omni-auth must not depend on omni-common-operlog or use @OperLog**. Authentication events belong to sys_audit_log; login records are its read-only projection, not a separate sys_login_log.
- Mapper lookup and SpEL must actually resolve. Entities with tenantId are retained only when matching the current positive tenantId; global entities without that property keep existing behavior. Snapshot checking is not write authorization.
- OperLogSanitizer masks matched fields, phones, emails and credential fragments using rules, not all arbitrary text. Long snapshot/parameter strings retain the first 4000 characters and append ...[TRUNCATED], so total length can exceed 4000. Truncation can invalidate JSON while database snapshot columns are JSON; successful oversized persistence is not guaranteed.
- Target exceptions propagate. Post-processing catches Exception; SpEL and Mapper lookup have local fallbacks. Pre-target snapshot queries and ID casts are not all protected, so collection failures are not universally isolated from business execution.
- Logged responseStatus is 200 on normal return and 500 on target exception, not final MVC HTTP status or R.code.

### Module Responsibilities

| Module | Components |
|---|---|
| omni-common-core | OperLog, OperType, OperLogMessage; production code has no Spring dependency |
| omni-common-operlog | Aspect, Producer, Sanitizer, EntityDiffer and conditional auto-configuration |
| omni-base | Consumer, OperLogService, Archiver and hot/cold tables |

## Common Starter Onboarding Specification

### Starter Module Overview

There are ten common modules: core supplies base types and nine modules compose capabilities. [Architecture](architecture.en.md) is the authoritative module/dependency entry point.

| Module | Boundary |
|---|---|
| omni-common-core | Base types; no production Spring auto-configuration |
| omni-common | Servlet Web/MVC, Jackson, exception advice and conditional XSS |
| omni-common-mybatis | Default OptimisticLock → MySQL Pagination; YAML mapping/deletion/ID conventions |
| omni-common-redis | Blocking RedisTemplate, RedisUtils and connection defaults |
| omni-common-redis-reactive | Reactive dependency/timeout YAML; no custom AutoConfiguration.imports in this module |
| omni-common-job | Conditional XXL-JOB executor and system task registration |
| omni-common-mqlog | Outbox, relay, sender strategies and management; needs DB/Job/Broker configuration |
| omni-common-operlog | Annotation AOP and sending; matching Servlet/AspectJ/Mapper/StreamBridge dependencies required |
| omni-common-workflow | Shared Workflow capabilities; do not introduce engine runtime into domain services |
| omni-common-service | Servlet identity, internal APIs, DataScope, persistence and XSS composition |

Eight capability modules have custom AutoConfiguration.imports. Entries establish registration points, not satisfied conditions or active beans; not every module uses the same custom mechanism.

### Steps to Onboard a New Service

Prefer common-service for Servlet domain services and select OperLog/Job/MQ as needed. Gateway, Auth, Base and Workflow retain their own boundaries. Identity settings, GATEWAY_ASSERTION_SECRET and OMNI_INTERNAL_API_TOKEN come from private configuration; enabled capabilities must satisfy startup validation.

Enabled tenant/data scope capabilities need unique domain-matching TenantTablePolicy/DataScopeTablePolicy implementations. Assertion verification precedes AuthorizationFilter; identity context follows pre-authentication. Avoid duplicate Servlet registration. Configure XSS Providers before conditional consumers and verify FilterRegistration and Jackson 2/3 beans together. [Development Guide](guides/scaffold-development.en.md) owns complete onboarding, generator and Docker steps.

### Override Mechanism

- The default MyBatis interceptor is conditional on the absence of the MybatisPlusInterceptor type. Follow project naming for a replacement bean and preserve interceptor order.
- RedisTemplate is conditional on the name redisTemplate; RedisUtils uses a return-type condition. These are different replacement rules.
- XSS depends on a Provider bean and Servlet/classpath conditions. Several OperLog beans have no ConditionalOnMissingBean. Read the configuration and verify the container context instead of claiming all Starter beans support identical overrides.

### Redis Starter Mutual Exclusion

WebFlux Gateway uses only omni-common-redis-reactive; Servlet services use the blocking module. Do not mix them in one service or run blocking calls on the Netty event loop. A reactive dependency does not convert arbitrary blocking business code.

## Configuration Reference

### application.yml Key Configuration Items

This distinguishes repository YAML from formal Compose. [Deployment](docker-deployment.en.md) and actual configuration own complete settings.

| Item | Current convention or formal Docker value |
|---|---|
| server.port / SERVER_PORT | Host entry points 8100–8107; application containers HTTP 8080; Frontend 3000 |
| spring.application.name | omni-auth/base/gateway/workflow/crm/srm/procurement/asset, per service |
| spring.datasource.* | Formal mysql:3306, omni_app, password from OMNI_DB_PASSWORD; no business datasource in Gateway |
| spring.data.redis.* | Formal redis:6379; service-specific database and serialization |
| spring.cloud.nacos.discovery.server-addr | Formal nacos:8848; Auth/Base/Workflow and domain variable mappings follow their respective configuration |
| spring.cloud.nacos.discovery.ip | Compose leaves it empty for container discovery, not a fixed host loopback |
| auth.jwks.uri / AUTH_JWKS_URI | Gateway YAML `http://localhost:8100/oauth2/jwks`; Compose `http://omni-auth:8080/oauth2/jwks` |
| auth.jwks.cache-ttl | Gateway default 5m |
| spring.profiles.active | Distinguish Spring Profile, Compose profile and CLI preset |

Passwords, Nacos identity/signing secrets, Gateway assertion secrets and internal Tokens use private configuration. Historical source fallbacks are not recommended deployment credentials.

### MyBatis-Plus Configuration

common-mybatis YAML declares map-underscore-to-camel-case=true, logic-delete-field=deleted (deleted 1, active 0) and id-type=auto. Entities/services may have separate mappings or policies; defaults do not establish columns on every table. Custom interceptors must retain DataPermission before Pagination.

### XXL-JOB Configuration (omni-base)

| Item | Current Base or formal Docker value |
|---|---|
| xxl.job.admin.addresses | YAML http://127.0.0.1:18080/xxl-job-admin; Compose http://xxl-job-admin:8080/xxl-job-admin |
| xxl.job.executor.appname | omni-base |
| xxl.job.executor.port | Base YAML 9901; property-class general fallback 9999; other services configure their own ports |
| xxl.job.executor.address / ip | Compose http://omni-base:9901/ / omni-base |
| xxl.job.access-token | General property default empty; Compose requires private XXL_JOB_ACCESS_TOKEN matching both ends |
| xxl.job.executor.enabled | Auto-configuration defaults enabled; Compose uses XXL_JOB_ENABLED |

Admin credentials come from private configuration. Default cron, registration success and actual execution are distinct. System handlers require both @XxlJob and @SystemJobMeta; [Scheduling](scheduling.en.md) owns user routing and compensation rules.

## Service Layer Design Patterns

### Interface + Implementation Separation

```text
XxxService (contract)
    ^ implements
XxxServiceImpl (business behavior; local transactions where configured)
```

Interfaces isolate callers and facilitate mocks. Replacement implementations must preserve semantics and transactions. Feign is a separate remote protocol adapter; an arbitrary Service interface is not automatically an available Feign client.

### Transaction Management Strategy

| Scenario | Convention and limit |
|---|---|
| Read-only | @Transactional(readOnly = true) is transaction configuration/a hint, not authorization or universal proof that writes are prohibited |
| Local writes | @Transactional defaults to REQUIRED; verify actual proxy invocation, rollback rules and database boundary |
| Remote effects | Local transactions/Outbox can record pending messages, not roll back completed HTTP/Broker/external-account effects |
| Independent transaction | REQUIRES_NEW needs an applicable proxy and successful commit; it does not guarantee logs survive |

Do not infer proxy interception of same-class calls from annotations alone or describe every log sending path as an independent transaction. Domain facts and regressions define critical transitions, occupancy and idempotency.

### Exception Handling Chain

```text
Controller --> Service --> matching MVC advice --> R
Gateway / Security Filter / protocol endpoint --> own response boundary
```

Auth depends on both common-core and common. common imports register global advice; AuthExceptionHandler is scoped to com.omni.auth.controller and coexists. Old Javadoc saying Auth does not depend on common cannot override current POM/imports. Matching handlers or Filter/protocol layers decide actual responses. Do not translate generic Chinese advice messages as if translated values were sent by the backend.

## Troubleshooting Guide

[Troubleshooting](guides/troubleshooting.en.md) owns reproduction, and [Source Debugging](wiki/source-debugging.en.md) owns call paths, variables and breakpoints. Inspect assertions, method permissions, tenant, visibility and transactions separately. Empty lists, 403, 404 and connection errors are different findings.

Follow [AGENTS.md](../AGENTS.md) for local Docker builds, tests and execution. Record container/session handles, exit codes and existing effects; protect logs/configuration. Check active work before continuing. Do not manufacture success through data resets, disabled authorization or shared-resource deletion.

## Testing

JUnit 5/Mockito isolate logic. MockMvc can validate specific MVC wire behavior; mocked identity, Services or engines do not prove real Gateway, database, Flowable, Broker or browser flows. SpringBootTest scope depends on active dependencies, environment and assertions, not its class name.

Put tests in module src/test/java and describe behavior/preconditions through names, following module conventions. Existing tests cover multiple business/common modules, not only Auth/CRM. Conditional skips are not passes.

After backend implementation changes, run the Maven Wrapper reactor in Docker:

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install
```

The tools container/reactor supplies JDK 25, JAVA_HOME and module order. For documentation-only changes, reuse evidence only after exact implementation/build-input checks, disclosing source, passes, skips and limits. Markdown rendering, unit tests and successful builds do not certify every business flow.
