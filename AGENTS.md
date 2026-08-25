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
| `docs/design/srm-design.md` | SRM MVP: supplier lifecycle, portal Saga, evaluation/risk, data scope, API and deployment constraints |
| `docs/design/procurement-design.md` | Procurement: material catalog, requisition approval, RFQ/quotation, purchase order and goods receipt boundaries |
| `docs/design/asset-design.md` | Asset MVP: asset ledger, procurement receipt ingestion, allocation/return, transfer/disposal approval and data scope |
| `docs/mq-reliability.md` | Reliable message sending: Transactional Outbox pattern, status machine, retry strategy, tenant isolation, new service onboarding |
| `docs/observability.md` | Metrics, W3C tracing, structured logs, local observability stack, dashboards, alerts and SLO template |

## Entry Points

**Backend:**
- Auth service: `omni-backend/omni-auth/src/main/java/com/omni/auth/AuthApplication.java`
- Base service: `omni-backend/omni-base/src/main/java/com/omni/base/BaseApplication.java`
- Gateway: `omni-backend/omni-gateway/src/main/java/com/omni/gateway/GatewayApplication.java`
- CRM service: `omni-backend/omni-crm/src/main/java/com/omni/crm/CrmApplication.java`
- SRM service: `omni-backend/omni-srm/src/main/java/com/omni/srm/SrmApplication.java`
- Procurement service: `omni-backend/omni-procurement/src/main/java/com/omni/procurement/ProcurementApplication.java`
- Asset service: `omni-backend/omni-asset/src/main/java/com/omni/asset/AssetApplication.java`
- Common library: `omni-backend/omni-common/src/main/java/com/omni/common/`
- Common core (POJO): `omni-backend/omni-common-core/src/main/java/com/omni/common/core/`
- Common MyBatis-Plus starter: `omni-backend/omni-common-mybatis/src/main/java/com/omni/common/mybatis/`
- Common Redis starter (blocking): `omni-backend/omni-common-redis/src/main/java/com/omni/common/redis/`
- Common Redis starter (reactive): `omni-backend/omni-common-redis-reactive/src/main/java/com/omni/common/redis/reactive/`
- Common MQ Log starter: `omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/`
- Common Servlet business starter: `omni-backend/omni-common-service/src/main/java/com/omni/common/service/`

**Frontend:**
- App bootstrap: `omni-frontend/src/main.ts`
- Router: `omni-frontend/src/router/index.ts`
- Shared types: `omni-frontend/src/types/api.ts`

**Configuration:**
- Auth config: `omni-backend/omni-auth/src/main/resources/application.yml`
- Gateway config: `omni-backend/omni-gateway/src/main/resources/application.yml`
- Base config: `omni-backend/omni-base/src/main/resources/application.yml`
- Vite config: `omni-frontend/vite.config.ts`
- CRM config: `omni-backend/omni-crm/src/main/resources/application.yml`
- SRM config: `omni-backend/omni-srm/src/main/resources/application.yml`
- Procurement config: `omni-backend/omni-procurement/src/main/resources/application.yml`
- Asset config: `omni-backend/omni-asset/src/main/resources/application.yml`

**RBAC & Permission:**
- Data scope filter: `omni-backend/omni-auth/src/main/java/com/omni/auth/security/DataScopeResolveFilter.java`
- Data permission handler: `omni-backend/omni-auth/src/main/java/com/omni/auth/security/DataPermissionHandlerImpl.java`
- Data scope context: `omni-backend/omni-auth/src/main/java/com/omni/auth/security/DataScopeContext.java`
- MyBatis-Plus config: `omni-backend/omni-auth/src/main/java/com/omni/auth/config/MyBatisPlusConfig.java`
- Dynamic menu controller: `omni-backend/omni-auth/src/main/java/com/omni/auth/controller/MenuController.java`
- Permission store: `omni-frontend/src/stores/permission.ts`
- v-permission directive: `omni-frontend/src/directives/permission.ts`

**XSS Defense:**
- XSS config provider SPI: `omni-backend/omni-common-core/src/main/java/com/omni/common/core/security/XssConfigProvider.java`
- XSS sanitizer: `omni-backend/omni-common/src/main/java/com/omni/common/security/xss/XssSanitizer.java`
- XSS filter + auto-config: `omni-backend/omni-common/src/main/java/com/omni/common/security/xss/XssFilter.java`
- XSS config implementation: `omni-backend/omni-auth/src/main/java/com/omni/auth/security/XssConfigProviderImpl.java`
- XSS config controller: `omni-backend/omni-auth/src/main/java/com/omni/auth/controller/XssConfigController.java`
- XSS management page: `omni-frontend/src/views/system/xssconfig/index.vue`
- Gateway security headers: `omni-backend/omni-gateway/src/main/java/com/omni/gateway/config/SecurityHeadersFilter.java`

**Scheduling & Tasks:**
- XXL-JOB auto-config: `omni-backend/omni-common-job/src/main/java/com/omni/common/job/config/XxlJobAutoConfiguration.java`
- XXL-JOB admin client: `omni-backend/omni-common-job/src/main/java/com/omni/common/job/XxlJobAdminClient.java`
- XXL-JOB properties: `omni-backend/omni-common-job/src/main/java/com/omni/common/job/XxlJobProperties.java`
- System job registry: `omni-backend/omni-common-job/src/main/java/com/omni/common/job/SystemJobRegistry.java`
- System job metadata: `omni-backend/omni-common-job/src/main/java/com/omni/common/job/SystemJobMeta.java`
- User job handler SPI: `omni-backend/omni-common-core/src/main/java/com/omni/common/core/job/UserJobHandler.java`
- User job message: `omni-backend/omni-common-core/src/main/java/com/omni/common/core/job/UserJobMessage.java`
- User job execute handler: `omni-backend/omni-base/src/main/java/com/omni/base/job/UserJobExecuteHandler.java`
- User job handler registry: `omni-backend/omni-base/src/main/java/com/omni/base/job/UserJobHandlerRegistry.java`
- Drink water handler: `omni-backend/omni-base/src/main/java/com/omni/base/job/handler/DrinkWaterRemindHandler.java`
- Oper log archiver: `omni-backend/omni-base/src/main/java/com/omni/base/service/OperLogArchiver.java`
- System job controller: `omni-backend/omni-base/src/main/java/com/omni/base/controller/SystemJobController.java`
- My job (workspace) controller: `omni-backend/omni-base/src/main/java/com/omni/base/controller/MyJobController.java`
- User job service: `omni-backend/omni-base/src/main/java/com/omni/base/service/impl/UserJobServiceImpl.java`
- Job type management: `omni-backend/omni-base/src/main/java/com/omni/base/controller/UserJobTypeController.java`
- Frontend system job page: `omni-frontend/src/views/job/system-job/index.vue`
- Frontend job type page: `omni-frontend/src/views/job/user-job-type/index.vue`
- Frontend workspace (my jobs): `omni-frontend/src/views/home/index.vue`
- Frontend API modules: `omni-frontend/src/api/myJob.ts`, `omni-frontend/src/api/systemJob.ts`, `omni-frontend/src/api/userJobType.ts`

**MQ Message Log:**
- Reliable message relay interface: `omni-backend/omni-common-core/src/main/java/com/omni/common/core/mq/ReliableMessageRelay.java`
- Reliable message template: `omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/template/ReliableMessageTemplate.java`
- Relay service: `omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/relay/MqMessageRelayService.java`
- Relay job handler: `omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/relay/MqMessageRelayJob.java`
- Message entity: `omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/entity/SysMqMessage.java`
- Message sender interface: `omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/sender/MessageSender.java`
- RocketMQ sender: `omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/sender/RocketMqMessageSender.java`
- Internal query API (Feign): `omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/controller/MqMessageInternalController.java`
- Auto-configuration: `omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/config/MqLogAutoConfiguration.java`
- Schema DDL: `omni-backend/omni-common-mqlog/src/main/resources/schema.sql`
- MQ message controller (external): `omni-backend/omni-base/src/main/java/com/omni/base/controller/MqMessageController.java`
- Frontend MQ message page: `omni-frontend/src/views/base/mqmessage/index.vue`
- Frontend operlog page: `omni-frontend/src/views/monitor/oper-log/index.vue`
- Frontend API: `omni-frontend/src/api/mqMessage.ts`

**Workflow:**
- Workflow service: `omni-backend/omni-workflow/src/main/java/com/omni/workflow/WorkflowApplication.java`
- Common workflow starter: `omni-backend/omni-common-workflow/src/main/java/com/omni/common/workflow/`
- Model controller: `omni-backend/omni-workflow/src/main/java/com/omni/workflow/controller/WorkflowModelController.java`
- Approval controller: `omni-backend/omni-workflow/src/main/java/com/omni/workflow/controller/ApprovalController.java`
- Process instance controller: `omni-backend/omni-workflow/src/main/java/com/omni/workflow/controller/ProcessInstanceController.java`
- Candidate resolver: `omni-backend/omni-workflow/src/main/java/com/omni/workflow/listener/ScopedRoleAssignmentListener.java`
- BPMN engine tools: `omni-backend/omni-workflow/src/main/java/com/omni/workflow/engine/`
- Frontend model designer: `omni-frontend/src/views/workflow/model/index.vue`
- Frontend API: `omni-frontend/src/api/workflow.ts`

**CRM:**
- CRM service: `omni-backend/omni-crm/src/main/java/com/omni/crm/CrmApplication.java`
- Domain model: `omni-backend/omni-crm/src/main/java/com/omni/crm/model/` (Lead, Customer, Contact, Opportunity, Activity, Overview)
- Security: `omni-backend/omni-crm/src/main/java/com/omni/crm/security/` (CrmDataScope, CrmRecordAccessGuard, CrmTenantFilter)
- Controllers: `omni-backend/omni-crm/src/main/java/com/omni/crm/controller/` (Lead/Customer/Contact/Opportunity/Activity/Overview)
- Frontend pages: `omni-frontend/src/views/crm/` (overview, lead, customer, contact, opportunity, activity)
- Frontend API: `omni-frontend/src/api/crm.ts` (lead, customer, contact, opportunity, activity, overview)

**SRM:**
- SRM service: `omni-backend/omni-srm/src/main/java/com/omni/srm/SrmApplication.java`
- Supplier lifecycle: `omni-backend/omni-srm/src/main/java/com/omni/srm/domain/SrmStateMachine.java`
- Tenant/DataScope: `omni-backend/omni-srm/src/main/java/com/omni/srm/security/`
- Portal Saga: `omni-backend/omni-srm/src/main/java/com/omni/srm/service/impl/SupplierPortalServiceImpl.java`, `omni-backend/omni-srm/src/main/java/com/omni/srm/consumer/PortalRoleResultConsumer.java`
- Auth role consumer: `omni-backend/omni-auth/src/main/java/com/omni/auth/consumer/PortalRoleAssignConsumer.java`
- Evaluation/Risk: `omni-backend/omni-srm/src/main/java/com/omni/srm/service/impl/EvaluationServiceImpl.java`, `omni-backend/omni-srm/src/main/java/com/omni/srm/service/impl/RiskServiceImpl.java`
- Frontend management pages: `omni-frontend/src/views/srm/`
- Supplier portal: `omni-frontend/src/views/supplier-portal/`
- SRM database source: `database/changelog/srm/`, `scripts/sql/seed/srm.sql`

**Procurement:**
- Procurement service: `omni-backend/omni-procurement/src/main/java/com/omni/procurement/ProcurementApplication.java`
- Tenant/DataScope: `omni-backend/omni-procurement/src/main/java/com/omni/procurement/security/`
- MyBatis interceptor chain: `omni-backend/omni-procurement/src/main/java/com/omni/procurement/config/MybatisPlusConfig.java`
- Material, approval route, requisition and RFQ aggregates: `omni-backend/omni-procurement/src/main/java/com/omni/procurement/entity/`
- Requisition workflow: `omni-backend/omni-procurement/src/main/java/com/omni/procurement/controller/RequisitionController.java`, `omni-backend/omni-procurement/src/main/java/com/omni/procurement/workflow/RequisitionWorkflowCoordinator.java`
- RFQ lifecycle and quotation integration: `omni-backend/omni-procurement/src/main/java/com/omni/procurement/controller/RfqController.java`, `omni-backend/omni-procurement/src/main/java/com/omni/procurement/consumer/QuotationSubmittedConsumer.java`
- Purchase order and goods receipt: `omni-backend/omni-procurement/src/main/java/com/omni/procurement/controller/PurchaseOrderController.java`, `omni-backend/omni-procurement/src/main/java/com/omni/procurement/controller/GoodsReceiptController.java`
- Procurement overview: `omni-backend/omni-procurement/src/main/java/com/omni/procurement/controller/OverviewController.java`, `omni-backend/omni-procurement/src/main/java/com/omni/procurement/mapper/ProcOverviewMapper.java`
- Procurement database source: `database/changelog/procurement/`, `scripts/sql/seed/procurement.sql`; RBAC seed: `scripts/sql/seed/auth.sql`

**Asset:**
- Asset service: `omni-backend/omni-asset/src/main/java/com/omni/asset/AssetApplication.java`
- Tenant/DataScope: `omni-backend/omni-asset/src/main/java/com/omni/asset/security/`
- MyBatis interceptor chain: `omni-backend/omni-asset/src/main/java/com/omni/asset/config/MybatisPlusConfig.java`
- Asset ledger and lifecycle commands: `omni-backend/omni-asset/src/main/java/com/omni/asset/controller/AssetController.java`
- Procurement receipt ingestion/backfill: `omni-backend/omni-asset/src/main/java/com/omni/asset/consumer/ProcurementGoodsReceiptConsumer.java`, `omni-backend/omni-asset/src/main/java/com/omni/asset/controller/InternalProcurementBackfillController.java`
- Transfer/disposal approval: `omni-backend/omni-asset/src/main/java/com/omni/asset/controller/AssetTransferController.java`, `omni-backend/omni-asset/src/main/java/com/omni/asset/controller/AssetDisposalController.java`
- Asset overview: `omni-backend/omni-asset/src/main/java/com/omni/asset/controller/AssetOverviewController.java`
- Frontend pages: `omni-frontend/src/views/asset/`
- Asset database source: `database/changelog/asset/`; RBAC seed: `scripts/sql/seed/auth.sql`

## Build & Run Commands

### Prerequisites

- **JAVA_HOME** must be set to JDK 25 before running any Maven commands
- **Maven Wrapper** bundled (3.9.16) — no system Maven required
- **Node.js** >= 22.12.0 with npm

### Backend

```bash
# Set JAVA_HOME (required for Spring Boot 4 plugin)
export JAVA_HOME="C:/APP/JDK25/jdk-25.0.2"
export PATH="$JAVA_HOME/bin:$PATH"

# Build all modules (run from omni-backend/)
cd omni-backend
./mvnw clean install

# Build a specific module with its dependencies
./mvnw clean install -pl omni-base -am

# Run Auth service (port 8100)
cd omni-backend/omni-auth
./mvnw spring-boot:run

# Run Base service (port 8101)
cd omni-backend/omni-base
./mvnw spring-boot:run

# Run Gateway (port 8102)
cd omni-backend/omni-gateway
./mvnw spring-boot:run

# Run Workflow service (port 8103)
cd omni-backend/omni-workflow
./mvnw spring-boot:run

# Run CRM service (port 8104)
cd omni-backend/omni-crm
./mvnw spring-boot:run

# Run SRM service (port 8105)
cd omni-backend/omni-srm
./mvnw spring-boot:run

# Run Procurement service (port 8106)
cd omni-backend/omni-procurement
./mvnw spring-boot:run

# Run Asset service (port 8107)
cd omni-backend/omni-asset
./mvnw spring-boot:run
```

**Build order**: `omni-common-core` must be installed first, then `omni-common`, `omni-common-mybatis`, `omni-common-redis`, `omni-common-redis-reactive` before `omni-auth`, `omni-base`, or `omni-gateway` can compile. Maven reactor resolves this automatically from `<modules>` declaration order.

### Frontend

```bash
cd omni-frontend

# Install dependencies
npm install

# Dev server (port 3000, proxies /api to gateway:8102)
npm run dev

# Type-check and build for production
npm run build

# Lint
npm run lint
```

### External Services (Docker)

**Primary method** — Docker Compose (recommended):

```bash
# Start the full repository stack (MySQL, Redis, Nacos, RocketMQ, XXL-JOB, services, frontend)
docker compose --profile full up -d

# Start a minimal preset through the repository CLI
npm --prefix tools/omni-cli run dev -- dev up --preset crm

# Check service health
docker compose ps
```

**Fallback** — individual `docker run` commands:

```bash
# Nacos Server (port 8080, 8848, 9848)
# Nacos v3.x requires auth configuration to start
docker run -d --name nacos \
  -p 8080:8080 -p 8848:8848 -p 9848:9848 \
  -e MODE=standalone \
  -e NACOS_AUTH_TOKEN=U2VjcmV0S2V5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5 \
  -e NACOS_AUTH_IDENTITY_KEY=nacos \
  -e NACOS_AUTH_IDENTITY_VALUE=nacos \
  nacos/nacos-server:v3.1.1

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
- omni-auth 模块不记录操作日志（@OperLog）。认证行为由登录日志（sys_login_log）完整留存，omni-auth 不引入 `omni-common-operlog` 依赖，不在控制器方法上使用 `@OperLog` 注解。
- All date-time values must use `yyyy-MM-dd HH:mm:ss` format consistently. Frontend `el-date-picker` must use `value-format="YYYY-MM-DD HH:mm:ss"`. Backend `LocalDateTime` query params must declare `@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")`.
- SRM default `USER` role may only enroll; `srm:portal:profile/evaluation` require the `SUPPLIER` role and an active `srm_supplier_portal_user` association.
- SRM Portal enrollment requires inviteToken and client requestId. Supplier Portal user IDs must never be written into internal `owner_user_id/owner_unit_id` fields.
- SRM child-resource DataScope must inherit visibility through Supplier/Evaluation relations; never append owner columns to tables that do not contain them.
- SRM lifecycle, evaluation, risk and Portal Saga rules are defined in `docs/design/srm-design.md`; changes require matching SQL seed/migration, backend permission and frontend `v-permission` updates.
- Procurement TenantLine only applies to `proc_*` tables; `sys_mq_message` must remain outside tenant interception so the relay can scan all tenants.
- Procurement data permission mappings are aggregate-specific: requisition uses requester columns, RFQ/PO/GR use owner columns, and child rows inherit through their aggregate root. Material/category/approval route/config are tenant-shared.
- Procurement approval runtime remains in `omni-workflow`; never add Flowable or `omni-common-workflow` to `omni-procurement`.
- Asset TenantLine only applies to `ast_*` tables; `sys_mq_message` must remain outside tenant interception so the relay can scan all tenants.
- Asset management views use `owner_user_id/owner_unit_id`; “my asset”, accept and return use fixed `current_user_id`; transfer/disposal child rows inherit scope through `ast_asset`.
- Asset receipt ingestion only creates cards for `qualityStatus=PASS && assetManaged=true` with an exact positive integer `assetQuantity`. Inbox event ID plus source receipt-line/unit sequence provide dual idempotency.
- Asset transfer and disposal share the atomic `active_operation_type/active_operation_id` occupancy on `ast_asset`; cancellation or rejection must restore `previous_asset_status` and clear occupancy in the same transaction.
- Asset approval runtime remains in `omni-workflow`; never add Flowable or `omni-common-workflow` to `omni-asset`.
- Asset monetary JSON fields (`purchaseAmount`, `residualValue`, event prices) use decimal strings and must never be transported as JSON numbers.
- `omni-common-job` dependency is required for any service that needs scheduling. `XxlJobAutoConfiguration` activates via `@ConditionalOnClass(XxlJobSpringExecutor.class)` and auto-registers the executor and system job registry.
- `omni-common-mqlog` provides reliable MQ message sending via Transactional Outbox pattern. `ReliableMessageRelay.send(bindingName, payload, tenantId)` inserts a PENDING record into `sys_mq_message` in the same local transaction. `mqRelayHandler` (XXL-JOB, `@XxlJob` + `@SystemJobMeta` dual annotation) asynchronously delivers messages. Each service's executor AppName is different, so handler names are naturally isolated.
- `ReliableMessageRelay.send()` requires explicit `Long tenantId` parameter. NEVER use ThreadLocal or implicit tenant resolution for MQ outbox writes. All callers must pass tenantId from their context (e.g., `OperLogMessage.getTenantId()`).
- All MQ message query controllers (`MqMessageController`, `MqMessageInternalController`) MUST filter by `tenantId`. External controller uses `@RequestHeader("X-Tenant-Id")`, internal controller uses `@RequestParam Long tenantId`.
- `MqMessageRelayService` (relay job) scans ALL PENDING/FAILED messages regardless of tenant — this is intentional, relay is a background process not subject to tenant isolation.
- `MessageSender` uses strategy pattern routed by `broker_type`. Current implementation: `RocketMqMessageSender` (StreamBridge). New MQ brokers (e.g., Kafka) require implementing `MessageSender` interface — no changes to relay logic needed.
- MQ message retry uses exponential backoff: `2^retryCount × 10s`. Messages exceeding `max_retry` enter DEAD_LETTER status. Dead letters can be manually resent or skipped via the admin UI.
- `sys_mq_message` auto-creates via `schema.sql` (`CREATE TABLE IF NOT EXISTS`) on service startup. No manual DDL required.
- System tasks MUST use dual annotation: `@XxlJob("handlerName")` + `@SystemJobMeta(...)`. Missing either annotation makes the handler invisible to `SystemJobRegistry`.
- User task handler `@Component` Bean name MUST exactly match `sys_user_job_type.type_code`. A mismatch causes silent routing failure in `UserJobHandlerRegistry`.
- `MyJobController` uses `verifyOwnership()` instead of `@PreAuthorize`. Never add `@PreAuthorize` to `MyJobController` endpoints — ownership check is per-row (createBy == currentUser), not per-endpoint.
- All user tasks share a single `@XxlJob("userJobExecuteHandler")`. Individual task routing is via JSON `executorParam` containing `UserJobMessage`, not separate XXL-JOB handlers.
- XXL-JOB registration failure in `UserJobServiceImpl.createJob()` MUST rollback the DB record (`sysUserJobMapper.deleteById`). Never leave orphaned `sys_user_job` rows without a corresponding XXL-JOB entry.
- `XxlJobAdminClient` session cookie is `volatile` and must not be persisted across restarts. The client auto-re-logins when the cookie is null or expired.
- `omni-workflow` is a standalone microservice (port 8103) — do NOT merge it into `omni-base` or `omni-auth`.
- MI completionCondition triggers task skip with `deleteReason = "MI_END"` (not `"deleted"`). Always use `HistoricTaskInstance.getDeleteReason()` for approval result determination, never `HistoricActivityInstance` parent lookup (putIfAbsent pitfall).
- `omni:assignment` JSON extension element is the sole configuration entry for candidate resolution. `ScopedRoleAssignmentListener` parses it on task `start` event.
- `wf_process_model.model_key` MUST be unique per tenant and match BPMN `<process id>`. `BpmnXmlValidator` enforces this.
- Model publish (`publishModel()`) uses `SELECT FOR UPDATE` pessimistic lock. Never deploy to Flowable without validation — always call `BpmnXmlValidator.validate()` first.

## Execution Rules

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
- Before creating a new user task type: read `docs/scheduling.md` Chapter 4 (tutorial with DrinkWater example).
- Before modifying system task annotations or adding new system tasks: read `docs/scheduling.md` Chapter 2.
- Before writing workflow engine or approval logic: read `docs/workflow.md`.
- Before adding a new candidate resolution strategy or anchor type: read `docs/workflow.md` Section 4 (Extension Guide).
- Before adding CRM aggregate roots, stages, or permission codes: read `docs/crm.md` (domain model, state machines, hard constraints, extension guide).
- Before modifying SRM lifecycle, portal, evaluation, risk, permission codes or schema: read `docs/design/srm-design.md`; add a forward-only changeSet under `database/changelog/srm/`, update `scripts/sql/seed/srm.sql` and/or `scripts/sql/seed/auth.sql`, then refresh `database/seed/manifest.yaml` checksums and assertions.
- Before modifying Procurement material, requisition, RFQ, purchase order, goods receipt, permission codes or schema: read `docs/design/procurement-design.md`; add a forward-only changeSet under `database/changelog/procurement/`, update `scripts/sql/seed/procurement.sql` and/or `scripts/sql/seed/auth.sql`, then refresh `database/seed/manifest.yaml` checksums and assertions.
- Before modifying Asset lifecycle, receipt ingestion, transfer, disposal, permission codes or schema: read `docs/design/asset-design.md`; add a forward-only changeSet under `database/changelog/asset/`, update `scripts/sql/seed/auth.sql` when RBAC changes, then refresh `database/seed/manifest.yaml` checksums and assertions.
- Before adding MQ message sending to a new service: depend on `omni-common-mqlog` (auto-registers `ReliableMessageTemplate`, `MqMessageRelayService`, `MqMessageRelayJob`, and `MqMessageInternalController`), ensure `sys_mq_message` table exists via `schema.sql`, and call `ReliableMessageRelay.send(bindingName, payload, tenantId)` with explicit tenantId. Read `docs/mq-reliability.md` for onboarding details.

## Completion Checklist

- [ ] Backend compiles: `./mvnw clean install` succeeds with no new warnings
- [ ] Frontend builds: `npm run build` succeeds (vue-tsc + vite build)
- [ ] Frontend lints: `npm run lint` passes with zero errors
- [ ] New code follows patterns from `docs/backend-patterns.md` or `docs/frontend-patterns.md`
- [ ] API responses conform to `docs/api-contract.md`
- [ ] Javadoc on all new public classes and methods
- [ ] No TODO without `[module]` prefix
- [ ] Updated `docs/` if architecture or contracts changed

## Important Notes

### Spring Cloud Gateway 5.x Config Prefix

Gateway 5.x changed the configuration prefix from `spring.cloud.gateway` to `spring.cloud.gateway.server.webflux`. Routes, discovery locator, and other gateway settings **must** be under the new prefix:

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: example
              uri: lb://service-name
              predicates:
                - Path=/api/example/**
              filters:
                - StripPrefix=2
```

### Gateway Actuator Endpoint

The `gateway` actuator endpoint requires explicit enablement:

```yaml
management:
  endpoint:
    gateway:
      enabled: true
```

### Maven JAVA_HOME

Spring Boot 4.x Maven plugin requires Java 17+. Always set `JAVA_HOME` to JDK 25 before running Maven commands. Without this, the build will fail with class version errors.

### Social Login Environment Variables

社交登录功能需要配置以下环境变量（支持 GitHub、Google 和 Gitee 三个提供商，基于 `OAuth2ProviderHandler` 策略模式可扩展）。
所有敏感凭证统一存放在项目根目录的 `.env` 文件中（已被 `.gitignore` 排除），本地开发和 Docker 部署共用同一份配置。

**配置步骤**：
1. 从 `.env.example` 复制一份为 `.env`
2. 填入真实的 OAuth2 凭证（从各平台 OAuth 应用管理页获取）
3. Docker 部署时 `compose.yaml` 及其 include 文件会自动读取 `.env` 并注入到容器
4. 本地开发时，在 IDE 的 Run Configuration 中配置 Environment variables，或手动加载 `.env` 文件

**配置项说明**：

| 变量 | 说明 |
|------|------|
| `GITHUB_CLIENT_ID` | GitHub OAuth App 的 Client ID |
| `GITHUB_CLIENT_SECRET` | GitHub OAuth App 的 Client Secret |
| `GITHUB_REDIRECT_URI` | GitHub 授权回调地址，默认 `http://localhost:8100/api/auth/oauth2/github/callback` |
| `GITEE_CLIENT_ID` | Gitee 第三方应用的 Client ID |
| `GITEE_CLIENT_SECRET` | Gitee 第三方应用的 Client Secret |
| `GITEE_REDIRECT_URI` | Gitee 授权回调地址，默认 `http://localhost:8100/api/auth/oauth2/gitee/callback` |
| `GOOGLE_CLIENT_ID` | Google Cloud Console OAuth 2.0 客户端的 Client ID |
| `GOOGLE_CLIENT_SECRET` | Google Cloud Console OAuth 2.0 客户端的 Client Secret |
| `GOOGLE_REDIRECT_URI` | Google 授权回调地址，默认 `http://localhost:8100/api/auth/oauth2/google/callback` |
| `OAUTH2_STATE_SECRET` | OAuth2 state 参数的 HMAC-SHA256 签名密钥，所有提供商共用（至少 32 字节） |
| `JWK_ENCRYPT_KEY` | JWK 密钥 AES-256-GCM 加密密钥（必须 32 字符，用于加密存入 Redis 的 RSA 密钥） |

### XSS 三层防御架构

项目采用三层纵深 XSS 防御，配置通过数据库 + Redis 缓存管理，按租户隔离：

| 层 | 组件 | 职责 |
|----|------|------|
| Layer 1 | `XssStringDeserializer` | Jackson String 反序列化器，自动清洗 `@RequestBody` JSON 中的 String 字段 |
| Layer 2 | `XssFilter` + `XssHttpServletRequestWrapper` | Servlet Filter，清洗查询参数 + 设置 ThreadLocal 规则 |
| Layer 3 | `SecurityHeadersFilter` | Gateway WebFilter，添加 `X-Content-Type-Options` / `X-Frame-Options` / `Referrer-Policy` |

**规则类型**：`HTML_TAG`（剥离标签）、`EVENT_HANDLER`（剥离 on* 属性）、`DANGEROUS_PROTOCOL`（替换危险协议）、`CUSTOM_PATTERN`（自定义正则）。

**缓存策略**：Redis 键 `xss:enabled:{tenantId}` + `xss:rules:{tenantId}`，TTL 30 分钟。写操作主动失效缓存。

**扩展新服务**：实现 `XssConfigProvider` SPI 接口即可自动获得 XSS 防护。`omni-common` 依赖引入后通过 `AutoConfiguration.imports` 自动装配。

**前端管理**：`系统管理 → XSS防护配置` 页面支持全局开关切换和黑名单规则 CRUD（`system:xssconfig` 权限码）。

### Common Starter 模块

项目提供底层 Starter 与 Servlet 业务组合 Starter，新微服务按运行模型选择：

| 模块 | 职责 | 适用服务类型 |
|------|------|-------------|
| `omni-common-mybatis` | MyBatis-Plus + MySQL 驱动 + 分页插件 + YAML 默认配置 | Servlet 服务 |
| `omni-common-redis` | 阻塞式 Redis + RedisTemplate 序列化 + RedisUtils | Servlet 服务 |
| `omni-common-redis-reactive` | 响应式 Redis + ReactiveRedisTemplate + ReactiveRedisUtils | WebFlux 服务（Gateway） |
| `omni-common-service` | Gateway 预认证、请求身份/租户、内部 API、DataScope、固定 MyBatis 顺序与 XSS 安全回退 | Servlet 业务服务 |

**新服务接入步骤**：
1. Servlet 业务服务优先依赖 `omni-common-service`；Gateway 只依赖 reactive 底层模块
2. `application.yml` 中配置 `spring.datasource.*` 和 `spring.data.redis.host/port/database`
3. 启动类添加 `@MapperScan("com.omni.xxx.mapper")`
4. 分页、序列化、RedisUtils 自动生效

**覆盖机制**：服务定义同名 `mybatisPlusInterceptor` Bean 即可覆盖 common-mybatis 的默认分页配置（`@ConditionalOnMissingBean`）。

## Testing

Backend tests now exist in `omni-auth`, `omni-crm`, and `omni-common-operlog`. Use JUnit 5 + Mockito for unit tests, keep security and tenant-boundary regressions alongside the affected module, and run the Maven reactor test phase after backend changes.
