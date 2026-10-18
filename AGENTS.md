# AGENTS.md

## Project Identity

Omni-Stack is a microservices scaffolding platform built with Spring Boot 4 + Vue 3, organized as a monorepo with a Maven multi-module backend and a standalone npm frontend.

| Layer       | Technology                                  | Version        |
|-------------|---------------------------------------------|----------------|
| JDK         | OpenJDK                                     | 25             |
| Backend     | Spring Boot                                 | 4.0.6          |
| Cloud       | Spring Cloud                                | 2025.1.1       |
| Cloud Alibaba | Spring Cloud Alibaba                     | 2025.1.0.0     |
| Gateway     | Spring Cloud Gateway Server (WebFlux)       | 5.0.1          |
| Service Discovery / Config | Nacos Server              | v3.1.1         |
| Circuit Breaker / Flow Control | Spring Cloud Alibaba Sentinel client | 2025.1.0.0 (Dashboard optional) |
| Frontend    | Vue 3 + TypeScript                          | 3.5.35 / 5.9.3 |
| Bundler     | Vite 8 (Rolldown)                           | 8.2.1          |
| UI Framework| Element Plus                                | 2.14.0         |
| State       | Pinia                                       | 3.0.4          |
| Router      | Vue Router                                  | 5.0.7          |
| Node.js     | Node.js LTS                                 | >= 22.12.0     |

## System Truth

Architecture, patterns, API contracts, and core flows are documented in `docs/`. **Read those first.** This file contains only execution rules and build commands.

| Document | Purpose |
|----------|---------|
| `docs/architecture.md` | System boundaries, module map, data flow, RBAC permission system, constraints |
| `docs/api-contract.md` | Response format, error codes, pagination, naming |
| `docs/backend-patterns.md` | Java layering, validation, exceptions, logging, security & data permission, OOP rules |
| `docs/frontend-patterns.md` | Vue/TS patterns, state management, routing, permission control, component conventions |
| `docs/core-flows.md` | End-to-end traces of login (password + captcha, GitHub social, Gitee social, device code), RBAC functional permission (Flow 5), data permission (Flow 6), XSS defense (Flow 8) |
| `docs/scheduling.md` | Scheduled task system: dual-track architecture (system tasks + user tasks), XXL-JOB integration, creating new task types |
| `docs/workflow.md` | Workflow engine: Flowable integration, dual-version model management, multi-instance countersign, candidate resolution, approval flows |
| `docs/crm.md` | CRM sales pipeline: domain model, 6-layer security, state machine, lead conversion, extension guide |
| `docs/srm.md` | Current SRM lifecycle, portal Saga, evaluation/risk, data scope and API contracts |
| `docs/design/srm-design.md` | SRM design decisions, reviewed implementation snapshot and history |
| `docs/design/procurement-design.md` | Procurement: material catalog, requisition approval, RFQ/quotation, purchase order and goods receipt boundaries |
| `docs/design/asset-design.md` | Asset MVP: asset ledger, procurement receipt ingestion, allocation/return, transfer/disposal approval and data scope |
| `docs/mq-reliability.md` | Reliable message sending: Transactional Outbox pattern, status machine, retry strategy, tenant isolation, new service onboarding |
| `docs/observability.md` | Metrics, W3C tracing, structured logs, local observability stack, dashboards, alerts and SLO template |

## Source Navigation

Read [source entry index](docs/reference/source-map.md) when locating classes. Read [Wiki](docs/wiki/index.md) for tutorials and [shared AI assets](ai/README.md) for skills and adapters.

## Build & Run Commands

### Prerequisites

- **JAVA_HOME** must be set to JDK 25 before running any Maven commands
- **Maven Wrapper** bundled (3.9.16) — no system Maven required
- **Node.js** >= 22.12.0 with npm

### Backend

Run from the repository root. The runner supplies JDK 25 and JAVA_HOME; the Maven reactor resolves module ordering.

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install
```

**Build order**: `omni-common-core` must be installed first, then `omni-common`, `omni-common-mybatis`, `omni-common-redis`, `omni-common-redis-reactive` before `omni-auth`, `omni-base`, or `omni-gateway` can compile. Maven reactor resolves this automatically from `<modules>` declaration order.

### Frontend

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run build
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run lint
```

### Local Applications and External Services (Docker)

**Primary method** — Docker Compose (recommended):

```bash
# Start the full repository stack (MySQL, Redis, Nacos, RocketMQ, XXL-JOB, services, frontend)
docker compose --profile full up -d

# Start a minimal preset through the repository CLI
docker compose -p omni-stack-tools -f compose.tools.yaml build cli
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- dev up --preset crm --project-name omni-stack

# Check service health
docker compose ps
```

Nacos is initialized by the formal migrator and Compose with server authentication enabled. Set the private `NACOS_PASSWORD` and server identity/signing secrets in `.env`; all discovery/config clients use the same `NACOS_USERNAME=nacos` and password. See `docs/docker-deployment.md` for credential changes and persistent volumes.

**Optional Sentinel Dashboard:**

```bash
# Optional Sentinel Dashboard (not included in the base Compose model, port 8858)
docker run -d --name sentinel -p 8858:8858 bladex/sentinel-dashboard:1.8.8
```

Start order: MySQL/Redis/RocketMQ -> Nacos/XXL-JOB -> Backend services -> Frontend.
Start the optional Sentinel Dashboard separately only when interactive rule monitoring is required.

### Service Ports

| Service          | Port  |
|------------------|-------|
| Frontend (dev)   | 3000  |
| Auth             | 8100  |
| Base             | 8101  |
| Gateway          | 8102  |
| Workflow         | 8103  |
| CRM              | 8104  |
| SRM              | 8105  |
| Procurement      | 8106  |
| Asset            | 8107  |
| MySQL            | 3306  |
| Redis            | 6379  |
| Nacos            | 8080, 8848  |
| Sentinel Dashboard (optional) | 8858  |

## Hard Constraints

- JDK 25 required. `JAVA_HOME` must be set before any Maven commands.
- Gateway 5.x uses `spring.cloud.gateway.server.webflux` prefix — NOT `spring.cloud.gateway`.
- `omni-common-core` must be `mvn install`-ed before other modules can compile. Maven reactor handles ordering automatically.
- `omni-common-redis`（阻塞式）和 `omni-common-redis-reactive`（响应式）不可混用。WebFlux 服务（如 Gateway）只能依赖 `omni-common-redis-reactive`，否则阻塞调用会导致 Netty 事件循环线程饥饿。
- Servlet 服务引入 `omni-common-mybatis` + `omni-common-redis` 即可获得数据库和 Redis 能力，无需手动声明 MyBatis-Plus / MySQL / Redis 依赖。
- 服务如需自定义 `MybatisPlusInterceptor`（如添加数据权限），定义同名 Bean 即可覆盖 common-mybatis 的默认分页配置（`@ConditionalOnMissingBean`）。
- All controllers return `R<T>`. Paginated responses use `R<PageResult<T>>`.
- No `@Autowired` field injection. Use `@RequiredArgsConstructor` + `final` fields.
- No `System.out.println` or `e.printStackTrace()`. Use `@Slf4j` with parameterized placeholders.
- No wildcard imports (`import xxx.*`), except Controller annotation packages.
- All `Serializable` classes must declare `serialVersionUID`.
- Service layer: `XxxService` interface + `XxxServiceImpl` implementation.
- Frontend shared types (`ApiResponse`, `PageResult`) defined only in `src/types/api.ts` — never duplicate.
- Vue SFC order: `<script setup>` -> `<template>` -> `<style scoped>`.
- TODO format: `// TODO: [module] description`.
- All code comments in Chinese (backend Javadoc, frontend JSDoc).
- `DataPermissionInterceptor` must be registered before `PaginationInnerInterceptor` in `MyBatisPlusConfig`.
- `DataScopeContext` ThreadLocal must be cleared in `finally` block to prevent memory leaks.
- Write operations on Controller must declare `@PreAuthorize` with `resource:action` format permission codes.
- `v-permission` directive uses `display:none` (not `removeChild`) for Vue reactivity compatibility.
- Self-registration endpoint (`POST /api/auth/register`) is public — no `@PreAuthorize`. Must validate captcha and username uniqueness within tenant.
- All user creation paths must assign the default `USER` role via `assignDefaultRole()`. Role assignment failure must not block user creation (log warning only).
- Social login auto-creation must use provider-prefixed usernames (`gh_`, `go_`, `ge_`) with fallback on collision.
- New backend services with `@RequestBody` endpoints must implement `XssConfigProvider` SPI to inherit three-layer XSS defense. `omni-common` auto-configures filter + Jackson deserializer via `AutoConfiguration.imports`.
- XSS config write operations (toggle, rule CRUD) must invalidate Redis cache keys `xss:enabled:{tenantId}` and `xss:rules:{tenantId}` to maintain consistency.
- Gateway `SecurityHeadersFilter` must add `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, and `Referrer-Policy` on all responses.
- omni-auth 模块不记录操作日志（@OperLog）。认证行为由 sys_audit_log 安全审计事件留存，登录记录是其登录事件的只读投影（见 docs/core-flows.md），omni-auth 不引入 `omni-common-operlog` 依赖，不在控制器方法上使用 `@OperLog` 注解。
- All date-time values must use `yyyy-MM-dd HH:mm:ss` format consistently. Frontend `el-date-picker` must use `value-format="YYYY-MM-DD HH:mm:ss"`. Backend `LocalDateTime` query params must declare `@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")`.
<!-- omni:modules:srm -->
- SRM default `USER` role may only enroll; `srm:portal:profile/evaluation` require the `SUPPLIER` role and an active `srm_supplier_portal_user` association.
<!-- omni:modules:end -->
<!-- omni:modules:srm -->
- SRM Portal enrollment requires inviteToken and client requestId. Supplier Portal user IDs must never be written into internal `owner_user_id/owner_unit_id` fields.
<!-- omni:modules:end -->
<!-- omni:modules:srm -->
- SRM child-resource DataScope must inherit visibility through Supplier/Evaluation relations; never append owner columns to tables that do not contain them.
<!-- omni:modules:end -->
<!-- omni:modules:srm -->
- Current SRM lifecycle, evaluation, risk and Portal Saga rules are maintained in `docs/srm.md`; `docs/design/srm-design.md` preserves design decisions and its reviewed snapshot. Changes require matching SQL seed/migration, backend permission and frontend `v-permission` updates.
<!-- omni:modules:end -->
<!-- omni:modules:procurement -->
- Procurement TenantLine only applies to `proc_*` tables; `sys_mq_message` must remain outside tenant interception so the relay can scan all tenants.
<!-- omni:modules:end -->
<!-- omni:modules:procurement -->
- Procurement data permission mappings are aggregate-specific: requisition uses requester columns, RFQ/PO/GR use owner columns, and child rows inherit through their aggregate root. Material/category/approval route/config are tenant-shared.
<!-- omni:modules:end -->
<!-- omni:modules:procurement -->
- Procurement approval runtime remains in `omni-workflow`; never add Flowable or `omni-common-workflow` to `omni-procurement`.
<!-- omni:modules:end -->
<!-- omni:modules:asset -->
- Asset TenantLine only applies to `ast_*` tables; `sys_mq_message` must remain outside tenant interception so the relay can scan all tenants.
<!-- omni:modules:end -->
<!-- omni:modules:asset -->
- Asset management views use `owner_user_id/owner_unit_id`; “my asset”, accept and return use fixed `current_user_id`; transfer/disposal child rows inherit scope through `ast_asset`.
<!-- omni:modules:end -->
<!-- omni:modules:asset -->
- Asset receipt ingestion only creates cards for `qualityStatus=PASS && assetManaged=true` with an exact positive integer `assetQuantity`. Inbox event ID plus source receipt-line/unit sequence provide dual idempotency.
<!-- omni:modules:end -->
<!-- omni:modules:asset -->
- Asset transfer and disposal share the atomic `active_operation_type/active_operation_id` occupancy on `ast_asset`; cancellation or rejection must restore `previous_asset_status` and clear occupancy in the same transaction.
<!-- omni:modules:end -->
<!-- omni:modules:asset -->
- Asset approval runtime remains in `omni-workflow`; never add Flowable or `omni-common-workflow` to `omni-asset`.
<!-- omni:modules:end -->
<!-- omni:modules:asset -->
- Asset monetary JSON fields (`purchaseAmount`, `residualValue`, event prices) use decimal strings and must never be transported as JSON numbers.
<!-- omni:modules:end -->
- `omni-common-job` dependency is required for any service that needs scheduling. `XxlJobAutoConfiguration` activates via `@ConditionalOnClass(XxlJobSpringExecutor.class)` and auto-registers the executor and system job registry.
- `omni-common-mqlog` provides reliable MQ message sending via Transactional Outbox pattern. `ReliableMessageRelay.send(bindingName, payload, tenantId)` inserts a PENDING record into `sys_mq_message` in the same local transaction. `mqRelayHandler` (XXL-JOB, `@XxlJob` + `@SystemJobMeta` dual annotation) asynchronously delivers messages. Each service's executor AppName is different, so handler names are naturally isolated.
- `ReliableMessageRelay.send()` requires explicit `Long tenantId` parameter. NEVER use ThreadLocal or implicit tenant resolution for MQ outbox writes. All callers must pass tenantId from their context (e.g., `OperLogMessage.getTenantId()`).
- All MQ message query controllers (`MqMessageController`, `MqMessageInternalController`) MUST filter by `tenantId`. External controller uses `@RequestHeader("X-Tenant-Id")`, internal controller uses `@RequestParam Long tenantId`.
- `MqMessageRelayService` (relay job) scans ALL PENDING/FAILED messages regardless of tenant — this is intentional, relay is a background process not subject to tenant isolation.
- `MessageSender` uses strategy pattern routed by `broker_type`. Current implementation: `RocketMqMessageSender` (StreamBridge). New MQ brokers (e.g., Kafka) require implementing `MessageSender` interface — no changes to relay logic needed.
- MQ message retry uses exponential backoff: `2^retryCount × 10s`. Messages exceeding `max_retry` enter DEAD_LETTER status. Dead letters can be manually resent or skipped via the admin UI.
- `sys_mq_message` DDL 唯一所有者为 Liquibase 迁移 `database/changelog/common/0001-mq-schema.yaml`，服务启动时由 Liquibase 自动执行。`omni-common-mqlog/src/main/resources/schema.sql` 为历史兼容保留，不再作为建表主路径。
- System tasks MUST use dual annotation: `@XxlJob("handlerName")` + `@SystemJobMeta(...)`. Missing either annotation makes the handler invisible to `SystemJobRegistry`.
- User task handler `@Component` Bean name MUST exactly match `sys_user_job_type.type_code`. A mismatch causes silent routing failure in `UserJobHandlerRegistry`.
- `MyJobController` uses `verifyOwnership()` instead of `@PreAuthorize`. Never add `@PreAuthorize` to `MyJobController` endpoints — ownership check is per-row (createBy == currentUser), not per-endpoint.
- All user tasks share a single `@XxlJob("userJobExecuteHandler")`. Individual task routing is via JSON `executorParam` containing `UserJobMessage`, not separate XXL-JOB handlers.
- XXL-JOB registration failure in `UserJobServiceImpl.createJob()` MUST rollback the DB record (`sysUserJobMapper.deleteById`). Never leave orphaned `sys_user_job` rows without a corresponding XXL-JOB entry.
- `XxlJobAdminClient` session cookie is `volatile` and must not be persisted across restarts. The client auto-re-logins when the cookie is null or expired.
<!-- omni:modules:workflow -->
- `omni-workflow` is a standalone microservice (port 8103) — do NOT merge it into `omni-base` or `omni-auth`.
<!-- omni:modules:end -->
<!-- omni:modules:workflow -->
- MI completionCondition triggers task skip with `deleteReason = "MI_END"` (not `"deleted"`). Always use `HistoricTaskInstance.getDeleteReason()` for approval result determination, never `HistoricActivityInstance` parent lookup (putIfAbsent pitfall).
<!-- omni:modules:end -->
<!-- omni:modules:workflow -->
- `omni:assignment` JSON extension element is the sole configuration entry for candidate resolution. `ScopedRoleAssignmentListener` parses it on task `start` event.
<!-- omni:modules:end -->
<!-- omni:modules:workflow -->
- `wf_process_model.model_key` MUST be unique per tenant and match BPMN `<process id>`. `BpmnXmlValidator` enforces this.
<!-- omni:modules:end -->
<!-- omni:modules:workflow -->
- Model publish (`publishModel()`) uses `SELECT FOR UPDATE` pessimistic lock. Never deploy to Flowable without validation — always call `BpmnXmlValidator.validate()` first.
<!-- omni:modules:end -->

## Execution Rules

- 所有应用、服务、前端和业务运行验证均在本机 Docker 内进行；不得在宿主机启动 Spring Boot、Vite 或其他应用服务器。构建与测试也优先在容器中执行。标准本地项目为 `omni-stack`；默认端口固定，MySQL 使用 3306，不自动漂移。

- Before writing backend code: read `docs/backend-patterns.md`.
- Before writing frontend code: read `docs/frontend-patterns.md`.
- Before designing or modifying an API: read `docs/api-contract.md`.
- Before modifying module structure or data flow: read `docs/architecture.md` and `docs/core-flows.md`.
- After backend changes: run `cd omni-backend && ./mvnw clean install` to verify compilation.
- After frontend changes: run `npm run build` and `npm run lint` in `omni-frontend/`.
- Use `./mvnw` (not `mvn`) for all Maven commands.
- Before adding new write-operation endpoints: declare `@PreAuthorize` with `resource:action` permission codes, update `sys_permission` in `scripts/sql/seed/auth.sql`, refresh its SHA-256 in `database/seed/manifest.yaml`, and add/update seed assertions.
- Before adding data permission to a new table: update `DataPermissionHandlerImpl` with the target table name and column mapping; ensure `DataPermissionInterceptor` is registered before `PaginationInnerInterceptor`.
- Before adding frontend buttons: add `v-permission` directive with the corresponding permission code.
- Before adding a new microservice: implement `XssConfigProvider` SPI in the new service module; `omni-common` dependency auto-registers XSS filter chain.
- Before modifying XSS rules or toggle: invalidate Redis cache in `XssConfigServiceImpl` write methods — never rely on TTL expiry for consistency.
- Before creating a new user task type: read `docs/scheduling.md` Section 4 for constraints and `docs/guides/scheduling.md` Section 9 for the implementation tutorial.
- Before modifying system task annotations or adding new system tasks: read `docs/scheduling.md` Chapter 2.
<!-- omni:modules:workflow -->
- Before writing workflow engine or approval logic: read `docs/workflow.md`.
<!-- omni:modules:end -->
<!-- omni:modules:workflow -->
- Before adding a new candidate resolution strategy or anchor type: read `docs/workflow.md` Section 3.6 for component boundaries and `docs/wiki/workflow-development.md` Section 4 for extension steps.
<!-- omni:modules:end -->
<!-- omni:modules:crm -->
- Before adding CRM aggregate roots, stages, or permission codes: read `docs/crm.md` (domain model, state machines, hard constraints, extension guide).
<!-- omni:modules:end -->
<!-- omni:modules:srm -->
- Before modifying SRM lifecycle, portal, evaluation, risk, permission codes or schema: read `docs/srm.md` and `docs/design/srm-design.md`; add a forward-only changeSet under `database/changelog/srm/`, update `scripts/sql/seed/srm.sql` and/or `scripts/sql/seed/auth.sql`, then refresh `database/seed/manifest.yaml` checksums and assertions.
<!-- omni:modules:end -->
<!-- omni:modules:procurement -->
- Before modifying Procurement material, requisition, RFQ, purchase order, goods receipt, permission codes or schema: read `docs/design/procurement-design.md`; add a forward-only changeSet under `database/changelog/procurement/`, update `scripts/sql/seed/procurement.sql` and/or `scripts/sql/seed/auth.sql`, then refresh `database/seed/manifest.yaml` checksums and assertions.
<!-- omni:modules:end -->
<!-- omni:modules:asset -->
- Before modifying Asset lifecycle, receipt ingestion, transfer, disposal, permission codes or schema: read `docs/design/asset-design.md`; add a forward-only changeSet under `database/changelog/asset/`, update `scripts/sql/seed/auth.sql` when RBAC changes, then refresh `database/seed/manifest.yaml` checksums and assertions.
<!-- omni:modules:end -->
- Before adding MQ message sending to a new service: depend on `omni-common-mqlog` (auto-registers `ReliableMessageTemplate`, `MqMessageRelayService`, `MqMessageRelayJob`, and `MqMessageInternalController`), `sys_mq_message` 表由 Liquibase `database/changelog/common/0001-mq-schema.yaml` 自动创建，无需手动 DDL。Call `ReliableMessageRelay.send(bindingName, payload, tenantId)` with explicit tenantId. Read `docs/mq-reliability.md` for onboarding details.

## Completion Checklist

- [ ] Backend compiles: `./mvnw clean install` succeeds with no new warnings
- [ ] Frontend builds: `npm run build` succeeds (vue-tsc + vite build)
- [ ] Frontend lints: `npm run lint` passes with zero errors
- [ ] New code follows patterns from `docs/backend-patterns.md` or `docs/frontend-patterns.md`
- [ ] API responses conform to `docs/api-contract.md`
- [ ] Javadoc on all new public classes and methods
- [ ] No TODO without `[module]` prefix
- [ ] Updated `docs/` if architecture or contracts changed

## Testing

Backend tests cover the Maven reactor modules. Use JUnit 5 + Mockito for unit tests, keep security and tenant-boundary regressions alongside the affected module, and run the Maven reactor clean install in Docker after backend changes.
