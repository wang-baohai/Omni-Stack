# Omni-Stack

> A microservice scaffold platform built on Spring Boot 4 + Vue 3, designed with the Harness industrial design pattern to provide an industry best-practice foundation for AI-assisted development.
>
> **One command to launch the full stack: middleware + 4 microservices + frontend — 12 Docker containers in total.**

**[中文](README.md)** | **[日本語](README.jp.md)** | **[한국어](README.kr.md)**

**GitHub**: https://github.com/wang-baohai/Omni-Stack | **Gitee**: https://gitee.com/wang-baohai/Omni-Stack

**Contact**: wangbaohai1993@gmail.com

---

## Highlights

- **JDK 25** + Spring Boot 4.0.6 + Spring Cloud 2025.1.1 + Spring Cloud Alibaba 2025.1.0.0 — cutting-edge stack across the board
- **One-click Docker deployment**: `start.bat` / `./start.sh` launches 12 containers (MySQL, Redis, Nacos, RocketMQ, XXL-JOB, 4 backend microservices, frontend) — see [Docker Deployment Guide](docs/docker-deployment.en.md)
- **Multi-provider social login**: GitHub + Google + Gitee OAuth2 one-click login (strategy pattern for extensibility), auto-registration on first login
- **Three-layer XSS defense in depth**: Jackson deserializer + Servlet Filter + Gateway security headers, per-tenant configuration, fully functional admin UI
- **Common Starter ecosystem**: 8 auto-configuration modules (mybatis / redis / operlog / job / mqlog / workflow) — add a dependency and gain capabilities instantly, zero config
- **Dual-track scheduling**: XXL-JOB 3.3.1 with system-task + user-task modes, frontend Cron editor + live execution log streaming — see [docs/scheduling.en.md](docs/scheduling.en.md)
- **Transactional Outbox reliable messaging**: local outbox + XXL-JOB relay + exponential backoff retry + dead-letter management — see [docs/mq-reliability.en.md](docs/mq-reliability.en.md)
- **Visual BPMN workflow**: Flowable 7.x engine, drag-and-drop modeling + dual-version management + multi-instance countersign + dynamic candidate resolution — see [docs/workflow.en.md](docs/workflow.en.md)
- **Full RBAC permission system**: functional permissions (dynamic menus + v-permission + @PreAuthorize) + data permissions (DataPermissionInterceptor six-level filter) — see [docs/architecture.en.md](docs/architecture.en.md)
- **AI-native engineering**: AGENTS.md execution manual + docs/ system truth + Skills behavior extensions — lock down the first two layers, then let AI produce code at full speed

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| JDK | OpenJDK | 25 |
| Backend Framework | Spring Boot | 4.0.6 |
| Microservice Framework | Spring Cloud + Spring Cloud Alibaba | 2025.1.1 / 2025.1.0.0 |
| API Gateway | Spring Cloud Gateway Server (WebFlux) | 5.0.1 |
| Registry / Config | Nacos Server | v3.1.1 |
| Rate Limiting / Circuit Breaking | Sentinel Dashboard | 1.8.8 |
| Message Queue | Apache RocketMQ | 5.3.2 |
| Job Scheduling | XXL-JOB Admin | 3.3.1 |
| Workflow Engine | Flowable BPMN | 7.x |
| Frontend Framework | Vue 3 + TypeScript | 3.5.35 / 5.9.3 |
| Build Tool | Vite 8 (Rolldown) | 8.0.14 |
| UI Framework | Element Plus | 2.14.0 |
| State Management | Pinia | 3.0.4 |
| Node.js | Node.js LTS | >= 22.12.0 |

## Architecture Overview

```
                                 ┌─────────────────┐
                                 │    omni-auth     │
                                 │   Spring :8100   │
                                 │  Security+OAuth2 │
                                 └─────────────────┘
                                        ▲
┌─────────────────┐     ┌──────────────────┐
│   omni-frontend  │────>│   omni-gateway    │lb://
│   Vue 3 SPA     │/api │  WebFlux :8102    │────>┌─────────────────┐
│   Nginx :3000   │────>│  StripPrefix=2    │     │    omni-base     │
└─────────────────┘     └──────────────────┘     │   Spring :8101   │
                            │                    └─────────────────┘
                            │                    ┌─────────────────┐
                            │                    │  omni-workflow   │
                            │                    │  Flowable :8103  │
                            │                    └─────────────────┘
                    ┌───────┴────────┐
                    │  MySQL :3306   │  Persistent Storage
                    │  Redis :6379   │  Cache + Captcha
                    │  Nacos :8848   │  Service Discovery + Config Center
                    │  RocketMQ      │  Message Queue (Async Delivery)
                    │  XXL-JOB       │  Distributed Job Scheduling
                    └────────────────┘
```

**Request flow**: Browser `:3000` → Nginx reverse proxy → Gateway `:8102` → `lb://` → Backend services

## Project Structure

```
Omni-Stack/
├── AGENTS.md                           # AI execution manual (hard constraints + build commands + checklists)
├── start.bat / start.sh                # One-click Docker full-stack startup script
├── stop.bat / stop.sh                  # One-click stop script
├── docker-compose.yml                  # 12-container full-stack orchestration
├── docker/
│   ├── backend/Dockerfile              # Backend multi-stage build (Maven compile + JRE runtime)
│   ├── frontend/Dockerfile             # Frontend multi-stage build (npm compile + Nginx)
│   ├── frontend/nginx.conf             # Nginx reverse proxy config
│   └── rocketmq/broker-docker.conf     # RocketMQ Broker config
├── docs/                               # System truth documentation (in-depth technical docs, multi-language)
│   ├── architecture.md                 #   System boundaries, module map, data flow, RBAC permission system
│   ├── api-contract.md                 #   Response format, error codes, pagination, naming conventions
│   ├── backend-patterns.md             #   Backend layering, validation, exceptions, logging, security & permissions
│   ├── frontend-patterns.md            #   Frontend directory structure, API layer, state management, access control
│   ├── core-flows.md                   #   End-to-end trace of login / OAuth2 / RBAC permission flows
│   ├── scheduling.md                   #   Scheduled task system in-depth technical documentation
│   ├── workflow.md                     #   Workflow engine in-depth technical documentation
│   ├── mq-reliability.md              #   Reliable message delivery in-depth technical documentation
│   └── docker-deployment.md            #   Docker full-stack deployment in-depth guide
├── scripts/sql/                        # Database initialization scripts
│   ├── init-all.sql                    #   Authoritative DDL + seed data
│   ├── init-nacos.sql                  #   Nacos MySQL persistence
│   └── init-xxl-job.sql               #   XXL-JOB database
├── omni-backend/                       # Maven multi-module backend
│   ├── omni-common-core/               #   Pure POJOs: R<T>, PageResult, XSS SPI
│   ├── omni-common/                    #   Web auto-config: Jackson, CORS, XSS Filter
│   ├── omni-common-mybatis/            #   MyBatis-Plus Starter
│   ├── omni-common-redis/              #   Blocking Redis Starter
│   ├── omni-common-redis-reactive/     #   Reactive Redis Starter (Gateway only)
│   ├── omni-common-operlog/            #   Operation log Starter
│   ├── omni-common-job/                #   Scheduled task Starter
│   ├── omni-common-mqlog/              #   MQ message reliability Starter
│   ├── omni-common-workflow/           #   Workflow Starter
│   ├── omni-auth/                      #   Auth service (8100)
│   ├── omni-base/                      #   Base data service (8101)
│   ├── omni-workflow/                  #   Workflow engine service (8103)
│   └── omni-gateway/                   #   API Gateway (8102)
└── omni-frontend/                      # Vue 3 SPA (3000)
```

## Docker One-Click Deployment (Recommended)

One command launches all 12 containers: middleware (MySQL, Redis, Nacos, RocketMQ, XXL-JOB) + 4 backend microservices + frontend.

### Prerequisites

| Software | Version | Notes |
|----------|---------|-------|
| Docker Desktop | Any stable release | Windows requires WSL2 backend |
| Git | Any | To clone the project |

> No need to install JDK, Node.js, or Maven — everything is built and runs inside Docker containers.

### Startup

| Platform | Command |
|----------|---------|
| Windows | Right-click `start.bat` → **Run as Administrator** |
| Linux / macOS | `./start.sh` |

The script handles everything automatically: detect Docker → start Docker engine (if not running) → port protection (Windows Hyper-V/WSL2) → pull middleware images → build application images → launch all containers.

```bash
# Start all services
./start.sh

# Start specific services only (e.g., middleware only)
./start.sh mysql redis

# Check service status
docker compose ps

# Stop all services
./stop.sh
```

### Service Ports

| Service | Port | Description |
|---------|------|-------------|
| **Frontend** | **http://localhost:3000** | **Entry point, Nginx reverse proxy to Gateway** |
| Auth Service | http://localhost:8100 | Spring Security + OAuth2 |
| Base Data Service | http://localhost:8101 | Dict / Org / User / Log / Job |
| API Gateway | http://localhost:8102 | Spring Cloud Gateway (WebFlux) |
| Workflow Engine | http://localhost:8103 | Flowable BPMN |
| MySQL | localhost:3306 | root/root |
| Redis | localhost:6379 | No password |
| Nacos Console | http://localhost:8080 | nacos/nacos |
| XXL-JOB Admin | http://localhost:18080 | admin/123456 |
| RocketMQ NameServer | localhost:19876 | Host-mapped port (container internal: 9876) |

### Verification

```bash
# 1. Open the frontend
open http://localhost:3000

# 2. Verify the captcha endpoint
curl http://localhost:3000/api/auth/captcha

# 3. Check all container status
docker compose ps
```

**Default login credentials**: `admin` / `admin123`

### Troubleshooting

| Issue | Cause | Solution |
|-------|-------|----------|
| Image pull failure | Network issues | Configure Docker mirror: `"registry-mirrors": ["https://docker.1ms.run"]` |
| Port binding failure (Windows) | Hyper-V/WSL2 port reservation conflict | `start.bat` handles port protection automatically — run as Administrator |
| RocketMQ port 9876 conflict | Windows Hyper-V reserved port range | Host-mapped to 19876, container internal remains 9876 |
| 502 Bad Gateway | Nginx reverse proxy port misconfiguration | Ensure nginx.conf proxy_pass uses the container-internal port `8080` (not host port `8102`) |
| Nacos startup failure | Health check endpoint changed | Nacos v3.1.1 uses `GET /nacos/` (not `/nacos/actuator/health`) |
| Build timeout | Slow Maven dependency downloads | Backend Dockerfile includes Alibaba Cloud Maven mirror |

> For in-depth troubleshooting, see [docs/docker-deployment.en.md](docs/docker-deployment.en.md)

## Local Development

Ideal for debugging and modifying code — middleware runs in Docker, backend and frontend run locally.

### Prerequisites

| Software | Version | Notes |
|----------|---------|-------|
| JDK | 25 | `JAVA_HOME` must be set |
| Node.js | >= 22.12.0 | Includes npm |
| Docker Desktop | Any | Middleware only |

### Steps

```bash
# 1. Start middleware only (no application containers)
./start.sh mysql redis nacos rocketmq-namesrv rocketmq-broker xxl-job-admin

# Wait for Nacos to be ready (~30s), verify at http://localhost:8080

# 2. Build and start the backend
export JAVA_HOME="/path/to/jdk-25"
cd omni-backend && ./mvnw clean install
cd omni-auth && ./mvnw spring-boot:run       # Port 8100 (continue in new terminal windows)
cd omni-base && ./mvnw spring-boot:run        # Port 8101
cd omni-gateway && ./mvnw spring-boot:run     # Port 8102
cd omni-workflow && ./mvnw spring-boot:run    # Port 8103

# 3. Start the frontend
cd omni-frontend && npm install && npm run dev  # Port 3000
```

> Maven Wrapper (3.9.16) is included — no global Maven installation required. Build order is automatically resolved by the Maven reactor.

### Social Login Configuration

Supports three OAuth2 providers: GitHub, Google, and Gitee. Credentials are configured in `application-local.yml` (excluded via `.gitignore`) — see [docs/core-flows.en.md](docs/core-flows.en.md) for details.

## Feature Overview

| Login Page | Dashboard |
|------------|-----------|
| ![Login Page](docs/images/login.png) | ![Dashboard](docs/images/dashboard.png) |

| User Management | Dictionary Management |
|-----------------|----------------------|
| ![User Management](docs/images/system-user.png) | ![Dictionary Management](docs/images/system-dict.png) |

## Module Overview

### Backend Microservices

| Module | Port | Responsibility | In-depth Docs |
|--------|------|---------------|--------------|
| omni-auth | 8100 | Authentication & authorization: login, JWT, OAuth2, RBAC, XSS config management | [core-flows.en.md](docs/core-flows.en.md) |
| omni-base | 8101 | Base data: dictionaries, organizations, users, logs, scheduled tasks, MQ message management | [scheduling.en.md](docs/scheduling.en.md) |
| omni-workflow | 8103 | Workflow engine: BPMN model management, approvals, process instances | [workflow.en.md](docs/workflow.en.md) |
| omni-gateway | 8102 | API Gateway: route forwarding, JWT validation, CORS, security headers | [architecture.en.md](docs/architecture.en.md) |

### Common Starter Ecosystem (8 Modules)

New microservices gain capabilities simply by adding a dependency — `AutoConfiguration.imports` provides zero-config auto-configuration:

| Module | Capability | Target Services |
|--------|-----------|----------------|
| `omni-common-core` | Pure POJOs: `R<T>`, `PageResult`, `BaseEntity`, XSS SPI, UserJobHandler SPI | All services |
| `omni-common` | Web auto-config: Jackson, CORS, global exception handling, XSS Filter | Servlet services |
| `omni-common-mybatis` | MyBatis-Plus + MySQL driver + pagination plugin | Servlet services |
| `omni-common-redis` | Blocking Redis + RedisTemplate serialization + RedisUtils | Servlet services |
| `omni-common-redis-reactive` | Reactive Redis (WebFlux services only, **do not mix with blocking variant**) | Gateway |
| `omni-common-operlog` | Operation log: @OperLog AOP + RocketMQ async + entity diff + hot/cold table archiving | Business services |
| `omni-common-job` | Scheduled tasks: XXL-JOB auto-config + @SystemJobMeta dual-annotation driven | Business services |
| `omni-common-mqlog` | Reliable messaging: Transactional Outbox + relay delivery + dead-letter management | Servlet services |
| `omni-common-workflow` | Workflow: Flowable auto-config + ApprovalService SPI | Workflow services |

> See [docs/backend-patterns.en.md](docs/backend-patterns.en.md) and [docs/architecture.en.md](docs/architecture.en.md) for detailed design.

### Frontend

Vue 3 + TypeScript + Vite 8 + Element Plus + Pinia 3 — see [docs/frontend-patterns.en.md](docs/frontend-patterns.en.md) for development conventions.

| Layer | Directory | Responsibility |
|-------|-----------|---------------|
| API Layer | `src/api/` | Domain-based split, unified Axios instance, type-safe |
| Store Layer | `src/stores/` | Pinia Composition API, one store per domain |
| Router Layer | `src/router/` | Lazy loading + navigation guards |
| View Layer | `src/views/` | SFC order: script → template → style |
| Type Layer | `src/types/` | Single source of truth for shared types (no duplicate definitions) |

## Developer Guide (Must-read for New Members)

The project follows the **Harness industrial design pattern** — system knowledge is organized in three layers: **Architecture → Patterns → Code**. Before modifying code, read the corresponding `docs/` document first.

| Rule | Description |
|------|-------------|
| Dependency Injection | `@RequiredArgsConstructor` + `final` fields — `@Autowired` is forbidden |
| Return Values | All controllers return `R<T>`, use `R<PageResult<T>>` for pagination |
| Exceptions | Throw `BusinessException` for business errors, handled uniformly by `GlobalExceptionHandler` |
| Logging | `@Slf4j` + parameterized placeholders — `System.out.println` is forbidden |
| Permissions | Write operations must declare `@PreAuthorize` in `resource:action` format |
| Frontend Types | `ApiResponse`/`PageResult` must only be imported from `src/types/api.ts` |
| Frontend Components | SFC order: `<script setup>` → `<template>` → `<style scoped>` |

```bash
# Pre-commit verification
cd omni-backend && ./mvnw clean install        # Backend compilation
cd omni-frontend && npm run build && npm run lint  # Frontend build + lint
```

> Full conventions: [docs/backend-patterns.en.md](docs/backend-patterns.en.md) and [docs/frontend-patterns.en.md](docs/frontend-patterns.en.md). API contract: [docs/api-contract.en.md](docs/api-contract.en.md)

## Common Pitfalls

| Pitfall | Description | Solution |
|---------|-------------|----------|
| Gateway routes not taking effect | 5.x config prefix changed | Use `spring.cloud.gateway.server.webflux` |
| Maven class version error | JAVA_HOME not pointing to JDK 25 | Set `JAVA_HOME` to the JDK 25 directory |
| Redis Starter mix-up | Blocking variant pulled into WebFlux service | Gateway must use `omni-common-redis-reactive` only |
| Docker 502 error | Nginx proxy_pass port misconfigured | Use container-internal port `8080` for inter-container communication, not the host-mapped port |
| Docker port conflicts | Hyper-V/WSL2 reserved ports | `start.bat` handles this automatically — requires Administrator privileges |
| Nacos health check failure | v3.1.1 endpoint changed | Use `GET /nacos/`, not `/nacos/actuator/health` |
| Frontend type mismatch | `ApiResponse` defined in multiple places | Import only from `@/types/api` |
| Stream consumer OFFLINE | function.definition namespace error | Place under `spring.cloud.function`, not `spring.cloud.stream.function` |

## AI-Native Engineering

- **`AGENTS.md`**: AI execution manual — hard constraints + execution rules + completion checklists
- **`docs/`**: System truth documents — AI reads these first to understand system context before modifying code
- **`.qoder/skills/`**: AI behavior extension units (e.g., `/grill-me` for design stress-testing)

> **Lock down the first two layers (Architecture + Patterns), then let AI produce code at full speed with confidence.**

## License

[Apache License 2.0](LICENSE)

---

## Support the Project

If this project has been helpful to you, feel free to give it a Star!

**GitHub**: [https://github.com/wang-baohai/Omni-Stack](https://github.com/wang-baohai/Omni-Stack)
**Gitee**: [https://gitee.com/wang-baohai/Omni-Stack](https://gitee.com/wang-baohai/Omni-Stack)

Pull requests are welcome — see [PRs](https://github.com/wang-baohai/Omni-Stack/pulls)!

---

**© Wang Baohai**
