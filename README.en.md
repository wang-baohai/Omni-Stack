# Omni-Stack

> A microservices scaffolding platform built with Spring Boot 4 + Vue 3, structured with the Harness Industrial Design Pattern to provide an industry best-practice foundation for AI-assisted development.

**[中文](README.md)** | **[日本語](README.jp.md)** | **[한국어](README.kr.md)**

**GitHub**: https://github.com/wang-baohai/Omni-Stack | **Gitee**: https://gitee.com/wang-baohai/Omni-Stack

**Contact**: wangbaohai1993@gmail.com

---

## Features

- **JDK 25** + Spring Boot 4.0.6 + Spring Cloud 2025.1.1 — full latest-gen stack
- **Spring Cloud Gateway 5.x** (WebFlux) reactive gateway with Nacos service discovery and configuration
- **Sentinel** flow control and circuit breaking, **OpenFeign** declarative service calls
- **Multi-provider Social Login**: GitHub + Google + Gitee OAuth2 one-click login (Strategy Pattern `OAuth2ProviderHandler`, extensible), WeChat login entry reserved on frontend, HMAC-SHA256 state signing against tampering, auto-registration on first login
- **Vue 3.5** + TypeScript 5.9 + Vite 8 + Element Plus 2.14 modern frontend
- **Pinia 3** state management + **Vue Router 5** navigation guards
- **Harness Industrial Design Pattern**: Three-Layer Height Model (Architecture → Patterns → Code), with `docs/` holding system truth
- **AI-Native Engineering**: AGENTS.md execution manual + Skills behavioral extensions for AI-assisted workflows
- **Three User Creation Paths**: Self-registration (captcha + default role), admin backend creation, social login auto-registration on first login
- **Three-Layer XSS Defense**: Jackson deserializer auto-sanitizes `@RequestBody` + Servlet Filter sanitizes query parameters + Gateway security response headers, with per-tenant configurable global toggle and custom blacklist rules (HTML tags, event handlers, dangerous protocols, regex patterns), Redis-cached configuration, and a full frontend management UI
- **Common Starter Ecosystem**: `omni-common` split into 7 modules (core / common / mybatis / redis / redis-reactive / operlog / job) — new services gain MyBatis-Plus pagination, Redis caching, XSS protection, operation log collection, scheduled task management via Maven dependency alone, `AutoConfiguration.imports` zero-config auto-assembly
- **Base Data & Task Management**: `omni-base` service (port 8101) provides data dictionary management, system task management, user task management, and operation log viewing, with Redis cache-aside caching and complete frontend management pages
- **Operation Log Audit Trail**: `@OperLog` annotation + AOP aspect for non-intrusive collection, automatically records who/when/what/changed with full audit information, entity change snapshot auto-diff (oldValue vs newValue) for data traceability, RocketMQ async delivery without blocking business requests, hot/cold table separation archival strategy (180-day retention + cold table long-term preservation) balancing query performance and compliance requirements, complementing audit logs (`sys_audit_log`) and login logs (`sys_login_log`) to form a complete audit trail system
- **Dual-Track Scheduled Task Scheduling**: system tasks (`@XxlJob` + `@SystemJobMeta` dual annotations, auto-registered to scheduling center) and user tasks (SPI pattern, `UserJobHandler` interface + JSON parameter routing) based on XXL-JOB 3.3.1, with frontend Cron editor, dynamic parameter forms, and real-time execution log push
- **Visual BPMN Workflow Engine**: built on Flowable 7.x, `omni-workflow` standalone microervice (port 8103), frontend BPMN visual designer with drag-and-drop modeling, dual-version management (business version DRAFT → PUBLISHED → ARCHIVED + Flowable engine version), multi-instance countersign with ALL/ANY approval modes, dynamic candidate resolution (`omni:assignment` JSON extension + `ScopedRoleAssignmentListener` runtime parsing), approval records + process progress diagram + CC notifications
- **Maven Wrapper** bundled — clone and build, no system Maven installation needed

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| JDK | OpenJDK | 25 |
| Backend | Spring Boot | 4.0.6 |
| Cloud | Spring Cloud | 2025.1.1 |
| Cloud Alibaba | Spring Cloud Alibaba | 2025.1.0.0 |
| Gateway | Spring Cloud Gateway Server (WebFlux) | 5.0.1 |
| Discovery / Config | Nacos Server | v3.1.1 |
| Flow Control | Sentinel Dashboard | 1.8.8 |
| Message Queue | Apache RocketMQ | 5.3.2 |
| Task Scheduling | XXL-JOB Admin | 3.3.1 |
| Workflow Engine | Flowable BPMN | 7.x |
| Frontend | Vue 3 + TypeScript | 3.5.35 / 5.9.3 |
| Bundler | Vite 8 (Rolldown) | 8.0.14 |
| UI Framework | Element Plus | 2.14.0 |
| State | Pinia | 3.0.4 |
| Router | Vue Router | 5.0.7 |
| Node.js | Node.js LTS | >= 22.12.0 |

## Project Structure

```
Omni-Stack/
├── AGENTS.md                        # AI execution manual (constraints + build commands + checklist)
├── start.bat / start.sh              # One-click startup (auto-start Docker + port protection + containers)
├── stop.bat / stop.sh                # One-click stop
├── docker-compose.yml               # Middleware orchestration (MySQL, Redis, Nacos, RocketMQ, XXL-JOB)
├── docker/
│   └── rocketmq/broker.conf          # RocketMQ Broker configuration
├── docs/                            # System truth documents (Architecture + Patterns + Contract)
│   ├── architecture.md                # System boundaries, module map, data flow, RBAC permission system
│   ├── api-contract.md                # Response format, error codes, pagination, naming
│   ├── backend-patterns.md            # Backend layering, validation, exceptions, logging, security, OOP
│   ├── frontend-patterns.md           # Frontend directory, API layer, state, permission control, components
│   └── core-flows.md                  # Login / OAuth2 / RBAC permission end-to-end traces
├── scripts/
│   └── sql/
│       ├── init-all.sql               # Authoritative database initialization script (DDL + seed data)
│       ├── init-nacos.sql           # Nacos v3.1.1 MySQL persistence initialization script
│       └── init-xxl-job.sql          # XXL-JOB v3.3.1 database initialization script
├── omni-backend/                    # Maven multi-module backend
│   ├── mvnw / mvnw.cmd                # Maven Wrapper (3.9.16)
│   ├── pom.xml                        # Parent POM (dependency management)
│   ├── omni-common-core/              # Pure POJO: R<T>, PageResult, BaseEntity, XSS SPI
│   ├── omni-common/                   # Web auto-config: Jackson, CORS, global exception, XSS Filter
│   ├── omni-common-mybatis/           # MyBatis-Plus Starter: pagination plugin, MySQL driver
│   ├── omni-common-redis/             # Blocking Redis Starter: RedisTemplate, RedisUtils
│   ├── omni-common-redis-reactive/    # Reactive Redis Starter: for WebFlux services
│   ├── omni-common-operlog/             # Operation Log Starter: AOP aspect + MQ producer + entity diff
│   ├── omni-common-job/                 # Scheduled Task Starter: XXL-JOB auto-config + Admin Client + system task registry
│   ├── omni-common-workflow/            # Workflow Starter: Flowable auto-config + Approval SPI + Tenant filter
│   ├── omni-auth/                     # Auth service: login, captcha, JWT, OAuth2 (port 8100)
│   ├── omni-base/                     # Base data service: dictionary management (port 8101)
│   ├── omni-workflow/                   # Workflow engine service: Flowable BPMN (port 8103)
│   └── omni-gateway/                  # API Gateway (WebFlux, port 8102)
├── omni-frontend/                   # Vue 3 SPA (dev server port 3000)
│   ├── package.json
│   ├── vite.config.ts
│   ├── eslint.config.mjs
│   └── src/
│       ├── api/                       # API layer (one file per domain)
│       ├── stores/                    # Pinia stores (Composition API style)
│       ├── router/                    # Route definitions + navigation guard
│       ├── views/                     # Page components
│       ├── layout/                    # App shell (sidebar + header + content)
│       ├── types/                     # Shared type definitions (ApiResponse, PageResult)
│       └── styles/                    # Global styles
└── .qoder/
    └── skills/
        └── grill-me/SKILL.md          # AI Skill: design stress-testing
```

## Architecture Overview

```
                                 ┌─────────────────┐
                                 │    omni-auth     │
                                 │   Spring :8100  │
                                 │  Security+OAuth2│
                                 └─────────────────┘
                                        ▲
┌─────────────────┐     ┌──────────────────┐
│   omni-frontend  │────>│   omni-gateway    │lb://
│   Vue 3 SPA     │/api │  WebFlux :8102    │────>┌─────────────────┐
│   :3000         │────>│  StripPrefix=2    │     │    omni-base     │
└─────────────────┘     └──────────────────┘     │   Spring :8101  │
                            │                    │  Dictionary Mgmt│
                            │                    └─────────────────┘
                            │                    ┌─────────────────┐
                            │                    │  omni-workflow   │
                            │                    │  Flowable :8103  │
                            │                    └─────────────────┘
                    ┌───────┴────────┐
                    │  MySQL :3306   │  Persistence storage
                    │  Redis :6379   │  Cache + captcha + dict cache
                    │  Nacos :8848   │  Discovery + Config
                    │  Sentinel :8858│  Flow Control
                    │  RocketMQ :9876│  Message queue (async log delivery)
                    │  XXL-JOB :18080│  Distributed task scheduling
                    └────────────────┘
```

**Request Flow**:

```
Browser :3000  --/api/**-->  Vite Proxy  -->  Gateway :8102  --lb://-->  Backend Services
```

- Frontend proxies `/api/**` to Gateway via Vite dev server
- Gateway discovery locator auto-creates routes for all Nacos-registered services

## Prerequisites

### Required Software

| Software | Version | Notes |
|----------|---------|-------|
| JDK | 25 | Must set `JAVA_HOME` environment variable |
| Node.js | >= 22.12.0 | Includes npm |
| Docker Desktop | Any stable | For running middleware (MySQL, Redis, Nacos, Sentinel, RocketMQ, XXL-JOB) |

> **Note**: Maven Wrapper (3.9.16) is bundled. Use `./mvnw` instead of `mvn` for all Maven commands.

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `JAVA_HOME` | - | **Required** — path to JDK 25 installation |
| `NACOS_SERVER_ADDR` | `127.0.0.1:8848` | Nacos server address |
| `NACOS_NAMESPACE` | (empty) | Nacos namespace |
| `SENTINEL_DASHBOARD` | `127.0.0.1:8858` | Sentinel dashboard address |
| `ROCKETMQ_NAME_SERVER` | `127.0.0.1:9876` | RocketMQ NameServer address |
| `XXL_JOB_ADMIN_ADDRESSES` | `http://127.0.0.1:18080/xxl-job-admin` | XXL-JOB Admin address |
| `VITE_API_BASE_URL` | `/api` | Frontend API base URL |
| `GITHUB_CLIENT_ID` | (built-in) | GitHub OAuth App Client ID |
| `GITHUB_CLIENT_SECRET` | (built-in) | GitHub OAuth App Client Secret |
| `GITHUB_REDIRECT_URI` | `http://localhost:8100/api/auth/oauth2/github/callback` | GitHub authorization callback URL |
| `GITEE_CLIENT_ID` | (built-in) | Gitee OAuth App Client ID |
| `GITEE_CLIENT_SECRET` | (built-in) | Gitee OAuth App Client Secret |
| `GITEE_REDIRECT_URI` | `http://localhost:8100/api/auth/oauth2/gitee/callback` | Gitee authorization callback URL |
| `GOOGLE_CLIENT_ID` | (built-in) | Google Cloud Console OAuth 2.0 Client ID |
| `GOOGLE_CLIENT_SECRET` | (built-in) | Google Cloud Console OAuth 2.0 Client Secret |
| `GOOGLE_REDIRECT_URI` | `http://localhost:8100/api/auth/oauth2/google/callback` | Google authorization callback URL |
| `OAUTH2_STATE_SECRET` | (built-in) | HMAC-SHA256 signing key for OAuth2 state parameter, shared across all social login providers |

### Social Login Configuration (GitHub / Google / Gitee)

The system uses the `OAuth2ProviderHandler` Strategy Pattern — each provider implements the interface to plug in, and adding a new provider requires no changes to core logic.

#### 1. Create OAuth Apps

**GitHub**:

1. Log in to GitHub → Settings → Developer settings → [OAuth Apps](https://github.com/settings/developers) → New OAuth App
2. Fill in the following:
   - **Application name**: Omni-Stack (any name)
   - **Homepage URL**: `http://localhost:3000`
   - **Authorization callback URL**: `http://localhost:8100/api/auth/oauth2/github/callback`
3. Copy the **Client ID** and **Client Secret** after creation

**Google**:

1. Log in to [Google Cloud Console](https://console.cloud.google.com/) → APIs & Services → Credentials
2. Create an OAuth 2.0 Client ID (select Web application as the application type)
3. Add the following to Authorized redirect URIs: `http://localhost:8100/api/auth/oauth2/google/callback`
4. Copy the **Client ID** and **Client Secret** after creation

**Gitee**:

1. Log in to Gitee → Settings → [Third-party Applications](https://gitee.com/oauth/applications) → Create Application
2. Fill in the following:
   - **Application name**: Omni-Stack (any name)
   - **Application homepage**: `http://localhost:3000`
   - **Application callback URL**: `http://localhost:8100/api/auth/oauth2/gitee/callback`
3. Copy the **Client ID** and **Client Secret** after creation

#### 2. Configure Credentials

Set environment variables or edit `omni-auth/src/main/resources/application.yml`:

```yaml
auth:
  oauth2:
    github:
      client-id: ${GITHUB_CLIENT_ID:your-client-id}
      client-secret: ${GITHUB_CLIENT_SECRET:your-client-secret}
      redirect-uri: ${GITHUB_REDIRECT_URI:http://localhost:8100/api/auth/oauth2/github/callback}
    google:
      client-id: ${GOOGLE_CLIENT_ID:your-client-id}
      client-secret: ${GOOGLE_CLIENT_SECRET:your-client-secret}
      redirect-uri: ${GOOGLE_REDIRECT_URI:http://localhost:8100/api/auth/oauth2/google/callback}
    gitee:
      client-id: ${GITEE_CLIENT_ID:your-client-id}
      client-secret: ${GITEE_CLIENT_SECRET:your-client-secret}
      redirect-uri: ${GITEE_REDIRECT_URI:http://localhost:8100/api/auth/oauth2/gitee/callback}
    state-secret: ${OAUTH2_STATE_SECRET:your-state-secret}
```

> **Note**: `redirect_uri` must exactly match the callback URL configured in the corresponding OAuth App. `state-secret` is used for HMAC-SHA256 signing of the state parameter — use a random string.

#### 3. Usage

Click the "GitHub", "Google", or "Gitee" button on the frontend login page to initiate social login. On first login, a local user is automatically created (username format: `gh_{login}` for GitHub, `go_{email_prefix}` for Google, `ge_{login}` for Gitee).

## Quick Start

### Step 1: Start Middleware

The project provides one-click startup scripts that automatically handle Docker Desktop, port protection, and container deployment:

| Platform | Start | Stop |
|----------|-------|------|
| Windows | Right-click `start.bat` → Run as Administrator | Right-click `stop.bat` → Run as Administrator |
| Linux / macOS | `./start.sh` | `./stop.sh` |

**Startup script automatically**:

1. **Detects Docker Desktop** — prompts download and opens the download page if not installed
2. **Starts Docker engine** — auto-launches if not running, waits until ready
3. **Port protection** (Windows) — prevents Hyper-V/WSL2 from dynamically occupying project ports (3306, 6379, 8080, 8848, 9848, 9876, 10909, 10911, 10912, 18080)
4. **Starts containers** — runs `docker compose up -d`

```bash
# Start all middleware
./start.sh                          # Linux / macOS
# or Windows: right-click start.bat → Run as Administrator

# Start specific services only
./start.sh mysql redis

# Check service status
docker compose ps
```

> Wait ~30 seconds for Nacos to fully start before launching backend services. Visit `http://127.0.0.1:8080/` to confirm (default credentials: nacos/nacos).
> MySQL container automatically runs `scripts/sql/init-all.sql` on first start to initialize the database.

### Step 2: Build and Start Backend

```bash
# Set JAVA_HOME (Spring Boot 4 plugin requires JDK 17+)
export JAVA_HOME="/path/to/jdk-25"
export PATH="$JAVA_HOME/bin:$PATH"

# Build all modules
cd omni-backend
./mvnw clean install

# Start Auth service (port 8100)
cd omni-auth
./mvnw spring-boot:run

# Start Base service (port 8101, in a new terminal)
cd omni-base
./mvnw spring-boot:run

# Start Gateway (port 8102, in a new terminal)
cd omni-gateway
./mvnw spring-boot:run
```

**Build order**: `omni-common-core` must be installed first, then `omni-common`, `omni-common-mybatis`, `omni-common-redis`, `omni-common-redis-reactive` before `omni-auth`, `omni-base`, or `omni-gateway` can compile. Maven reactor resolves this automatically from `<modules>` declaration order.

### Step 3: Start Frontend

```bash
cd omni-frontend

# Install dependencies
npm install

# Start dev server (port 3000, auto-proxies /api to Gateway :8102)
npm run dev
```

### Step 4: Verify Services

| Check | Command / URL | Expected Result |
|-------|--------------|-----------------|
| Frontend | `http://localhost:3000` | Login page |
| Gateway routes | `curl http://localhost:8102/actuator/gateway/routes` | JSON route list |
| Nacos console | `http://127.0.0.1:8080/` | Nacos admin UI |
| Sentinel console | `http://localhost:8858` | Sentinel Dashboard |
| XXL-JOB Admin | `http://localhost:18080/xxl-job-admin` | XXL-JOB Admin Web UI (admin/123456) |
| RocketMQ | `telnet localhost 9876` | NameServer connectivity check |

**Start order**: MySQL → Redis → Nacos → Sentinel → RocketMQ → XXL-JOB → Backend (Auth, Base, Gateway) → Frontend

## Service Ports

| Service | Port | Description |
|---------|------|-------------|
| Frontend dev server | 3000 | Vite dev server, proxies /api requests |
| Auth service | 8100 | Spring Security + OAuth2 Authorization Server |
| Base data service | 8101 | Dictionary management, Redis cache-aside caching |
| API Gateway | 8102 | Spring Cloud Gateway (WebFlux) |
| Workflow engine | 8103 | Flowable BPMN process engine |
| MySQL | 3306 | Primary database (omni_auth + omni_base + xxl_job) |
| Redis | 6379 | Captcha cache + dict cache + XSS config cache |
| Nacos | 8080, 8848 | Management UI (8080) + Discovery & Config (8848) |
| Sentinel | 8858 | Flow control dashboard |
| XXL-JOB Admin | 18080 | Distributed task scheduling center (Web UI), default credentials admin/123456 |
| RocketMQ NameServer | 9876 | Message queue naming server |
| RocketMQ Broker | 10909, 10911, 10912 | Message queue broker node |

## Module Details

### Common Starter Ecosystem (7 Modules)

`omni-common` has been split into 7 single-responsibility modules forming the Common Starter ecosystem. New services gain capabilities by adding Maven dependencies alone — **none can run independently**:

| Module | Responsibility | Target Service Type |
|--------|---------------|-------------------|
| `omni-common-core` | Pure POJO: `R<T>`, `PageResult<T>`, `BaseEntity`, `BusinessException`, `XssConfigProvider` SPI, `UserJobHandler` SPI | All services |
| `omni-common` | Web auto-config: Jackson time serialization, CORS, global exception handler, XSS Filter + Jackson Module auto-registration | Servlet services |
| `omni-common-mybatis` | MyBatis-Plus + MySQL driver + pagination plugin + YAML defaults, `@ConditionalOnMissingBean` override support | Servlet services |
| `omni-common-redis` | Blocking Redis + RedisTemplate serialization + RedisUtils | Servlet services |
| `omni-common-redis-reactive` | Reactive Redis + ReactiveRedisTemplate + ReactiveRedisUtils | WebFlux services (Gateway) |
| `omni-common-operlog` | Operation Log Starter: `@OperLog` AOP aspect + RocketMQ producer + entity change diff | Business services |
| `omni-common-job` | Scheduled Task Starter: XXL-JOB auto-config + Admin Client + system task registry + `@SystemJobMeta` dual annotation | Business services |
| `omni-common-workflow` | Workflow Starter: Flowable auto-configuration, `ApprovalService` SPI, `UserGroupLookup`, `TenantInfoFilter` | Workflow service |

> All starters use Spring Boot auto-configuration (`AutoConfiguration.imports`) to register beans. Downstream modules don't need manual `@ComponentScan`.
> `omni-common-redis` and `omni-common-redis-reactive` must not be mixed — WebFlux services can only depend on the reactive version.

### omni-auth (Auth Service)

Authentication microservice built on Spring Security 7 + OAuth2 Authorization Server:

- **User login**: username + password + captcha + multi-tenant, issues RS256 JWT
- **Multi-provider Social Login**: extensible social login architecture based on `OAuth2ProviderHandler` Strategy Pattern, with GitHub, Google, and Gitee providers integrated; WeChat login entry reserved on frontend. HMAC-SHA256 state signing against tampering, auto-creates local user and links third-party identity on first login (`sys_user_oauth_provider` table)
- **OAuth2 authorization**: Authorization Code + PKCE flow for third-party integration
- **Device Authorization Grant** (RFC 8628): provides authorization for IoT devices, CLI tools, and other browserless scenarios via the `omni-device` client; frontend `/device` page simulates device-initiated authorization with token polling, and `/device/verify` page allows users to complete authorization by scanning or entering a code on another device
- **Client management**: CRUD on `oauth2_registered_client`, supports dynamic registration
- **Multi-tenant RBAC**: `tenantId:username` user resolution + role-permission tree
- **RBAC Permission System**: Functional permissions (dynamic menu filtering + `v-permission` button-level control + `@PreAuthorize` API authorization) + Data permissions (MyBatis-Plus `DataPermissionInterceptor` SQL auto-interception, six-level dataScope zero-intrusion filtering)
- **JWT signing**: RSA key pair, JWK endpoint for Gateway public key verification
- **XSS Protection Config Management**: Frontend `System Management → XSS Protection Config` page with global toggle and blacklist rule CRUD (HTML tags, event handlers, dangerous protocols, custom regex), per-tenant isolation, Redis cache 30-min TTL with active invalidation on writes

### omni-common-operlog (Operation Log Starter)

AOP + RocketMQ based operation log collection framework providing non-intrusive audit trail for business services:

- **Non-intrusive collection**: `@OperLog` annotation + `OperLogAspect` AOP aspect automatically captures request context (username, tenantId, IP, request parameters) and entity change snapshots
- **Entity change diff**: `EntityDiffer` field-level diff comparison — UPDATE operations record only changed fields, enabling data traceability
- **RocketMQ async**: `OperLogProducer` asynchronously delivers log messages without blocking business request responses
- **Hot/cold table separation**: Hot table `sys_oper_log` retains recent 180-day data for fast queries; cold table `sys_oper_log_archive` preserves long-term records for compliance. `OperLogArchiver` runs daily at 02:00 for automated archival
- **Complementary to audit logs**: Operation logs record business data changes (who/when/what/changed), audit logs (`sys_audit_log`) record security events, and login logs (`sys_login_log`) record login behavior — together forming a complete audit trail system
- **Disabled in omni-auth**: Auth module does not depend on this module; authentication behavior is covered by `sys_login_log` + `sys_audit_log`

### omni-base (Base Data & Task Service)

Base data and task management microservice covering data dictionary, scheduled tasks, and operation log capabilities:

- **Dictionary Type Management**: `sys_dict_type` table — list, get, create, update, delete, status toggle; 11 API endpoints fully implemented
- **Dictionary Data Management**: `sys_dict_data` table — linked by type code, supports list, create, update, delete, cache refresh
- **Redis cache-aside caching**: 30-minute TTL, write-through invalidation, `dict:{typeCode}` key format
- **System Task Management**: merges `SystemJobRegistry` metadata with XXL-JOB runtime status, providing register/start/stop/trigger/unregister lifecycle operations, `job:system-job:*` permission codes
- **User Task Management**: SPI-based task types + task instances + execution logs, supporting user self-service creation, Cron scheduling, and ownership verification
- **Operation Log Viewing**: hot table query + paginated filtering by module, operation type, operator, and time range
- **Frontend management pages**: dictionary management (master-detail layout), system tasks, task types, workspace my-tasks, `base:dict` / `job:*` permission codes
- **XSS protection inherited**: implements `XssConfigProvider` SPI to automatically gain three-layer XSS defense

### omni-gateway (API Gateway)

Reactive gateway based on Spring Cloud Gateway Server (WebFlux):

- Route forwarding: auto-routes to Nacos-registered backend services (StripPrefix=2)
- Service discovery: auto-routes Nacos-registered services
- Auth filter: `AuthFilter` (JWT RS256 signature verification + claims extraction + identity header injection)
- CORS handling: `CorsConfig` for cross-origin requests

### omni-frontend (Vue 3 SPA)

| Layer | Directory | Responsibility |
|-------|-----------|----------------|
| API | `src/api/` | One file per domain, shared Axios instance, type-safe |
| Store | `src/stores/` | Pinia Composition API style, one store per domain |
| Router | `src/router/` | Lazy-loaded routes + navigation guard (auth by default) |
| Views | `src/views/` | Page components, SFC order: script → template → style; includes `device/` (device authorization), `job/` (task management), `system/` (system management) subdirectories |
| Layout | `src/layout/` | App shell (sidebar + header + content area) |
| Types | `src/types/` | Shared type definitions (single source for ApiResponse, PageResult) |
| Styles | `src/styles/` | Global reset + layout styles |

## Scheduled Task System

The project implements a dual-track scheduled task architecture based on **XXL-JOB 3.3.1**, supporting both system tasks and user tasks. See [`docs/scheduling.md`](docs/scheduling.md) for in-depth technical details.

### Architecture Overview

- **omni-common-job**: Encapsulates `XxlJobAutoConfiguration`, `XxlJobAdminClient`, and `SystemJobRegistry` for unified task registration and management
- **omni-common-core**: Defines the `UserJobHandler` SPI interface and `UserJobMessage` POJO
- **omni-base**: Business layer implementing concrete system and user task handlers

### System Tasks

Driven by `@XxlJob` + `@SystemJobMeta` dual annotations. `SystemJobRegistry` auto-discovers and registers them with XXL-JOB Admin at startup. Example: `OperLogArchiver` (operation log archival) — Bean registration → auto-discovery → REST API management → XXL-JOB scheduling. Management endpoints require `job:system-job:*` permissions.

### User Tasks

SPI-based: implement the `UserJobHandler` interface and register as a Spring Bean; `UserJobHandlerRegistry` auto-discovers handlers. All user tasks share a single `@XxlJob("userJobExecuteHandler")` entry point, routing to specific handlers via JSON `executorParam`. `MyJobController` uses ownership verification (not `@PreAuthorize`) to ensure users can only manage their own tasks.

### Dependencies

| Component | Description |
|-----------|-------------|
| XXL-JOB Admin (`:18080`) | Distributed scheduling center, Docker-deployed |
| `omni-common-job` module | Auto-configuration, Admin Client, system task registration |
| `sys_user_job_type` / `sys_user_job` / `sys_user_job_log` | User task types, task instances, execution logs |

### Adding a New Task Type

Using `DrinkWaterRemindHandler` (water drinking reminder) as an example: ① Register type in `sys_user_job_type` table → ② Implement `UserJobHandler` interface with `@Component` → ③ Users create tasks via workspace → ④ Verify XXL-JOB scheduling. See [`docs/scheduling.md` Chapter 4](docs/scheduling.md) for detailed steps.

### Frontend Integration

Three entry points: System Job Management (`SystemJob`), Task Type Management (`UserJobType`), and My Jobs on the workspace (`MyJob`). Features include a Cron expression editor, `DynamicFormRenderer` for dynamic parameter forms, and 10-second polling of active task logs with `ElNotification` push for execution results.

## Workflow Engine

The project provides a visual BPMN workflow engine built on **Flowable 7.x**, supporting model design, version management, multi-instance countersign approval, and more. Technical details are documented in [`docs/workflow.md`](docs/workflow.md).

### Architecture Overview

- **omni-workflow**: standalone microservice (port 8103), integrating Flowable BPMN engine with 7 controllers covering model management, process definitions, instance monitoring, approval processing, and statistics dashboards
- **omni-common-workflow**: shared starter providing `FlowableAutoConfiguration`, `ApprovalService` SPI, `UserGroupLookup`, `TenantInfoFilter`, and other infrastructure

### Core Capabilities

- **Visual Model Designer**: frontend BPMN designer with drag-and-drop modeling, XML editing, and validation preview. `BpmnXmlBuilder` converts designer JSON to BPMN 2.0 XML
- **Dual-Version Management**: business versions (DRAFT → PUBLISHED → ARCHIVED) tracked in `wf_process_model_version`, engine versions managed by Flowable deployment
- **Multi-Instance Countersign**: ALL (everyone approves) and ANY (any one approves) modes controlled via MI `completionCondition`, with instant rejection shortcut
- **Dynamic Candidate Resolution**: `omni:assignment` JSON extension element + `ScopedRoleAssignmentListener` runtime parsing, supporting multiple anchor types (initiator's primary unit / parent org / absolute org, etc.)
- **Approval Records + Process Progress + CC Notifications**: complete process tracing with `HistoricTaskInstance`-level precision

### Database Tables (omni_workflow)

| Table | Description |
|-------|-------------|
| `wf_process_model` | Process model registry, `model_key` unique per tenant |
| `wf_process_model_version` | Version history: BPMN XML + deployment info |
| `wf_process_instance_ext` | Instance extension: links model version to Flowable instance |
| `wf_todo_task` | Pending task cache |
| `wf_cc_record` | CC notification records |

### Frontend Integration

7 pages/components covering the full workflow experience: Model Management (`ModelDesigner`), Version History (`VersionHistoryDialog`), Validation Results (`ValidateResultDialog`), Process Definitions, Process Instances, Approval Records (`ApprovalRecordsDialog`), Process Progress (`ProcessProgressDialog`), and Statistics Dashboard.

## RBAC Permission System

The project implements a complete RBAC permission model, split into two independent subsystems: functional permissions and data permissions. See [`docs/architecture.md`](docs/architecture.md) (RBAC Permission System section) for detailed design, and [`docs/core-flows.md`](docs/core-flows.md) (Flow 5 & 6) for end-to-end flows.

### Functional Permissions

Three-layer defense controlling what users "can do":

| Layer | Mechanism | Implementation |
|-------|-----------|---------------|
| Dynamic menus | Backend recursively filters menu tree by user permissions | `MenuController` -> `usePermissionStore` -> dynamic route registration |
| Button control | Vue custom directive controls DOM visibility | `v-permission="'system:user:create'"` -> `display:none` |
| API authorization | Spring Security method-level permission check | `@PreAuthorize("hasAuthority('system:user:create')")` |

### Data Permissions

SQL auto-interception based on MyBatis-Plus `DataPermissionInterceptor` — zero intrusion on business code, controlling what data users "can see":

| dataScope | Description |
|-----------|-------------|
| `ALL` | All data (cross-tenant) |
| `TENANT` | All data within own tenant |
| `DEPT_AND_BELOW` | Own department and sub-departments |
| `DEPT` | Own department only |
| `CUSTOM` | Custom department set |
| `SELF` | Own data only |

**Core flow**: Request arrives -> `DataScopeResolveFilter` resolves role dataScope (most permissive wins) -> writes to `DataScopeContext` (ThreadLocal) -> `DataPermissionInterceptor` auto-appends WHERE conditions -> context cleared on request completion.

## User Creation

Three user creation paths are supported. All paths automatically assign the `USER` default role (`data_scope=SELF`, can only view own data):

| Path | Entry Point | Auth Required | Tenant | Password |
|------|-------------|---------------|--------|----------|
| Self-Registration | Register page `/register` | None (public) | User selects from dropdown | User sets (BCrypt) |
| Admin Creation | User management page | `system:user:create` | Admin specifies | Admin sets (BCrypt) |
| Social Login | OAuth2 callback | None (3rd-party auth) | HMAC state param | None (social-only) |

See [`docs/core-flows.md`](docs/core-flows.md) Flow 7 for detailed flows.

## Permission Collaboration Model

How the five elements — Tenant, Organization, Role, Functional Permission, and Data Permission — collaborate to enforce complete access control:

```
Tenant ─── Isolation boundary: usernames unique per tenant, data isolated by default
  │
  ├── User ─── Belongs to one tenant, can have multiple roles
  │     │
  │     ├── Role ─── Bridge between users and permissions
  │     │     ├── Functional Permission ─── Controls "what you can do" (menu/button/API)
  │     │     └── Data Scope ─── Controls "what data you can see"
  │     │
  │     └── Org Unit ─── User's department affiliation, anchor for data permissions
  │
  └── Permission Tree ─── DIRECTORY → MENU → BUTTON → API four-level structure
```

**Collaboration flow**:

1. **At login**: Look up user by `(tenantId, username)` → load roles → load permissions → issue JWT
2. **Functional control**: JWT `scope` claim carries permission codes → frontend dynamic menus + `v-permission` button hiding → backend `@PreAuthorize` API authorization
3. **Data control**: Role `data_scope` determines visibility → `DataScopeResolveFilter` resolves the widest scope → MyBatis-Plus auto-appends WHERE conditions
4. **Organization link**: User's `primaryUnitId` serves as data permission anchor → `DEPT`/`DEPT_AND_BELOW` scopes use materialized path queries for hierarchy

## Unified Response Format

All APIs use the `R<T>` wrapper. Frontend and backend maintain strict contract consistency. See [`docs/api-contract.md`](docs/api-contract.md) for details.

**Success**:
```json
{
  "code": 200,
  "message": "success",
  "data": { "id": 1, "username": "demo", "email": "demo@example.com" }
}
```

**Error**:
```json
{
  "code": 400,
  "message": "username: Username is required; email: Email is required"
}
```

**Paginated**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [{ "id": 1, "username": "demo" }],
    "total": 100,
    "size": 10,
    "current": 1,
    "pages": 10
  }
}
```

## Developer Guide (New Members — Start Here)

### 1. Read Documentation Before Writing Code

This project follows the **Harness Industrial Design Pattern**, organizing system knowledge into three layers:

| Layer | Content | Location |
|-------|---------|----------|
| Layer 1: Architecture | System boundaries, module responsibilities, data flow, RBAC permission system, constraints | `docs/architecture.md` |
| Layer 2: Patterns | Backend/frontend coding patterns, API contracts, security, core flows | `docs/backend-patterns.md`, `docs/frontend-patterns.md`, `docs/api-contract.md`, `docs/core-flows.md` |
| Layer 3: Code | Concrete functions, classes, component implementations | Source files |

**Rule**: Before modifying code, check the corresponding `docs/` document. If architecture or contracts change, update `docs/` first, then modify code.

### 2. Backend Conventions

- **Layering**: Controller → Service (interface) → ServiceImpl → Repository
- **DI**: `@RequiredArgsConstructor` + `final` fields; no `@Autowired` field injection
- **Returns**: All Controller methods return `R<T>`
- **Exceptions**: Throw `BusinessException` for business errors, handled by `GlobalExceptionHandler`
- **Logging**: `@Slf4j` + parameterized placeholders; no `System.out.println`
- **Full conventions**: Read `docs/backend-patterns.md`

### 3. Frontend Conventions

- **API layer**: One file per domain (`src/api/user.ts`), shared Axios instance from `request.ts`
- **Types**: Shared types in `src/types/api.ts` only — never duplicate
- **Store**: Pinia Composition API style, `use` prefix naming
- **Components**: SFC order `<script setup>` → `<template>` → `<style scoped>`
- **Router**: Lazy loading + `meta` declaration (title, icon, requiresAuth)
- **Full conventions**: Read `docs/frontend-patterns.md`

### 4. Pre-commit Checklist

```bash
# Backend compilation check
cd omni-backend && ./mvnw clean install

# Frontend build + lint check
cd omni-frontend && npm run build && npm run lint
```

See the Completion Checklist section in `AGENTS.md` for the full checklist.

### 5. Common Pitfalls

| Pitfall | Cause | Solution |
|---------|-------|----------|
| Gateway routes not loading | 5.x changed the config prefix | Use `spring.cloud.gateway.server.webflux` — see AGENTS.md Important Notes |
| Maven class version error | JAVA_HOME not pointing to JDK 25 | Set `JAVA_HOME` to your JDK 25 directory |
| Frontend type mismatch | `ApiResponse` defined in multiple places | Import only from `@/types/api` — never duplicate |
| Actuator gateway endpoint 404 | Requires explicit enablement | Configure `management.endpoint.gateway.enabled: true` |
| GitHub social login callback 404 | OAuth App not created or Client ID is a placeholder | Create a GitHub OAuth App per "Social Login Configuration" above and fill in real credentials |
| Google social login callback 404 | Google Cloud Console OAuth client not created or Client ID is a placeholder | Create an OAuth 2.0 client in Google Cloud Console per "Social Login Configuration" above and fill in real credentials |
| Gitee social login callback 404 | Gitee third-party application not created or Client ID is a placeholder | Create a third-party application on Gitee per "Social Login Configuration" above and fill in real credentials |
| Google login stuck on callback page | `sys_user_oauth_provider` table missing from database | Ensure `init-all.sql` has been executed; this table stores bindings for all providers |
| GitHub login stuck on callback page | `sys_user_oauth_provider` table missing from database | Ensure `init-all.sql` has been executed (includes this table), or create it manually |
| Gitee login stuck on callback page | Same as GitHub — `sys_user_oauth_provider` table missing | Ensure `init-all.sql` has been executed; this table stores bindings for all providers |
| Social login state signature verification failure | `OAUTH2_STATE_SECRET` not configured or changed after restart | Set a fixed `OAUTH2_STATE_SECRET` environment variable to keep the signing key consistent |
| Nacos loses configuration after restart | Uses embedded Derby database, no persistence | Use `init-nacos.sql` from this project to switch to external MySQL storage |
| Maven build order error | `omni-common-core` not installed first, causing downstream module compilation failure | Run `./mvnw clean install` from parent POM — Maven reactor auto-resolves `<modules>` declaration order |
| Redis Starter mix causes thread starvation | Blocking `omni-common-redis` imported into WebFlux service | WebFlux services (e.g., Gateway) must only depend on `omni-common-redis-reactive` — never mix the two |
| Spring Cloud Stream consumer not receiving messages (RocketMQ consumer group OFFLINE) | When multiple `Consumer<T>` beans exist, `spring.cloud.function.definition` is missing or placed under wrong namespace (`spring.cloud.stream.function.definition`) | Add `spring.cloud.function.definition: beanName1;beanName2` under `spring.cloud.function` — **NOT** under `spring.cloud.stream.function`. Example: `spring.cloud.function.definition: operlogConsumer;userJobConsumer` |

## AI-Native Engineering Practice

This project supports AI-assisted development workflows:

- **`AGENTS.md`**: AI execution manual defining hard constraints, execution rules, and completion checklists
- **`docs/` directory**: System truth documents — AI reads these first to understand system context before modifying code
- **`.qoder/skills/`**: AI behavioral extension units (e.g., `/grill-me` for design stress-testing)

Core principle: **Layers 1 & 2 (Architecture + Patterns) must be defined first, so Layer 3 (Code) can be safely delegated to AI at full speed.**

## License

[Apache License 2.0](LICENSE)

---

## Support

If this project helps you, please Star it!

**GitHub**: [https://github.com/wang-baohai/Omni-Stack](https://github.com/wang-baohai/Omni-Stack)
**Gitee**: [https://gitee.com/wang-baohai/Omni-Stack](https://gitee.com/wang-baohai/Omni-Stack)

[PRs](https://github.com/wang-baohai/Omni-Stack/pulls) welcome!

---

**© Wang Baohai**
