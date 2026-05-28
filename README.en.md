# Omni-Stack

> A microservices scaffolding platform built with Spring Boot 4 + Vue 3, structured with the Harness Industrial Design Pattern to provide an industry best-practice foundation for AI-assisted development.

**[中文](README.md)** | **[日本語](README.jp.md)**

---

## Features

- **JDK 25** + Spring Boot 4.0.6 + Spring Cloud 2025.1.1 — full latest-gen stack
- **Spring Cloud Gateway 5.x** (WebFlux) reactive gateway with Nacos service discovery and configuration
- **Sentinel** flow control and circuit breaking, **OpenFeign** declarative service calls
- **Vue 3.5** + TypeScript 5.9 + Vite 8 + Element Plus 2.14 modern frontend
- **Pinia 3** state management + **Vue Router 5** navigation guards
- **Harness Industrial Design Pattern**: Three-Layer Height Model (Architecture → Patterns → Code), with `docs/` holding system truth
- **AI-Native Engineering**: AGENTS.md execution manual + Skills behavioral extensions for AI-assisted workflows
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
├── docs/                            # System truth documents (Architecture + Patterns + Contract)
│   ├── architecture.md                # System boundaries, module map, data flow, constraints
│   ├── api-contract.md                # Response format, error codes, pagination, naming
│   ├── backend-patterns.md            # Backend layering, validation, exceptions, logging, OOP
│   ├── frontend-patterns.md           # Frontend directory, API layer, state, components
│   └── core-flows.md                  # Login / query / submission end-to-end traces
├── omni-backend/                    # Maven multi-module backend
│   ├── mvnw / mvnw.cmd                # Maven Wrapper (3.9.16)
│   ├── pom.xml                        # Parent POM (dependency management)
│   ├── omni-common/                   # Shared: unified response, global exception, Jackson config
│   ├── omni-gateway/                  # API Gateway (WebFlux, port 8090)
│   └── omni-business/                 # Business service (port 8081)
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
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   omni-frontend  │────>│   omni-gateway    │────>│  omni-business  │
│   Vue 3 SPA     │/api │  WebFlux :8090    │ lb  │   :8081         │
│   :3000         │────>│  StripPrefix=2    │────>│  Controller     │
└─────────────────┘     └──────────────────┘     │  Service (intf)  │
                            │                     │  ServiceImpl    │
                            │                     └─────────────────┘
                    ┌───────┴────────┐
                    │  Nacos :8848   │  Discovery + Config
                    │  Sentinel :8858│  Flow Control
                    └────────────────┘
```

**Request Flow**:

```
Browser :3000  --/api/**-->  Vite Proxy  -->  Gateway :8090  --lb://-->  Business :8081
```

- Frontend proxies `/api/**` to Gateway via Vite dev server
- Gateway routes `/api/business/**` to `omni-business` via Nacos load balancer (StripPrefix=2)
- Gateway discovery locator auto-creates routes for all Nacos-registered services

## Prerequisites

### Required Software

| Software | Version | Notes |
|----------|---------|-------|
| JDK | 25 | Must set `JAVA_HOME` environment variable |
| Node.js | >= 22.12.0 | Includes npm |
| Docker Desktop | Any stable | For running Nacos and Sentinel |

> **Note**: Maven Wrapper (3.9.16) is bundled. Use `./mvnw` instead of `mvn` for all Maven commands.

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `JAVA_HOME` | - | **Required** — path to JDK 25 installation |
| `NACOS_SERVER_ADDR` | `127.0.0.1:8848` | Nacos server address |
| `NACOS_NAMESPACE` | (empty) | Nacos namespace |
| `SENTINEL_DASHBOARD` | `127.0.0.1:8858` | Sentinel dashboard address |
| `VITE_API_BASE_URL` | `/api` | Frontend API base URL |

## Quick Start

### Step 1: Start Middleware

```bash
# Nacos — service discovery and configuration center (ports 8080, 8848, 9848)
# Nacos v3.x requires auth configuration to start
docker run -d --name nacos \
  -p 8080:8080 -p 8848:8848 -p 9848:9848 \
  -e MODE=standalone \
  -e NACOS_AUTH_TOKEN=U2VjcmV0S2V5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5 \
  -e NACOS_AUTH_IDENTITY_KEY=nacos \
  -e NACOS_AUTH_IDENTITY_VALUE=nacos \
  nacos/nacos-server:v3.1.1

# Sentinel — flow control dashboard (port 8858)
docker run -d --name sentinel -p 8858:8858 bladex/sentinel-dashboard:1.8.8
```

> Wait ~30 seconds for Nacos to fully start before launching backend services. Visit `http://127.0.0.1:8080/` to confirm (default credentials: nacos/nacos).

### Step 2: Build and Start Backend

```bash
# Set JAVA_HOME (Spring Boot 4 plugin requires JDK 17+)
export JAVA_HOME="/path/to/jdk-25"
export PATH="$JAVA_HOME/bin:$PATH"

# Build all modules
cd omni-backend
./mvnw clean install

# Start Gateway (port 8090)
cd omni-gateway
./mvnw spring-boot:run

# In a new terminal, start Business service (port 8081)
cd omni-backend/omni-business
./mvnw spring-boot:run
```

**Build order**: `omni-common` must be installed first. To build a specific module with its dependencies:
```bash
./mvnw clean install -pl omni-business -am
```

### Step 3: Start Frontend

```bash
cd omni-frontend

# Install dependencies
npm install

# Start dev server (port 3000, auto-proxies /api to Gateway :8090)
npm run dev
```

### Step 4: Verify Services

| Check | Command / URL | Expected Result |
|-------|--------------|-----------------|
| Frontend | `http://localhost:3000` | Login page |
| Gateway routes | `curl http://localhost:8090/actuator/gateway/routes` | JSON route list |
| Nacos console | `http://127.0.0.1:8080/` | Nacos admin UI |
| Sentinel console | `http://localhost:8858` | Sentinel Dashboard |

**Start order**: Nacos → Sentinel → Backend (Gateway, Business) → Frontend

## Service Ports

| Service | Port | Description |
|---------|------|-------------|
| Frontend dev server | 3000 | Vite dev server, proxies /api requests |
| API Gateway | 8090 | Spring Cloud Gateway (WebFlux) |
| Business service | 8081 | omni-business microservice |
| Nacos | 8080, 8848 | Management UI (8080) + Discovery & Config (8848) |
| Sentinel | 8858 | Flow control dashboard |

## Module Details

### omni-common (Shared Library)

Shared infrastructure for all backend modules. **Cannot run independently**:

| Component | File | Responsibility |
|-----------|------|----------------|
| Unified Response | `R<T>` | All APIs return `{ code, message, data }` |
| Pagination | `PageResult<T>` | Paginated response `{ records, total, size, current, pages }` |
| Business Exception | `BusinessException` | Business exception with error code |
| Exception Handler | `GlobalExceptionHandler` | Catches all exceptions, converts to `R<Void>` |
| Jackson Config | `JacksonConfig` | Java 8 time serialization (`yyyy-MM-dd HH:mm:ss`) |
| Web Config | `WebMvcConfig` | CORS configuration |
| Base Entity | `BaseEntity` | Audit fields (id, createTime, updateTime, createBy, updateBy) |

> `omni-common` uses Spring Boot auto-configuration (`AutoConfiguration.imports`) to register beans. Downstream modules don't need manual `@ComponentScan`.

### omni-gateway (API Gateway)

Reactive gateway based on Spring Cloud Gateway Server (WebFlux):

- Route forwarding: `/api/business/**` → `lb://omni-business` (StripPrefix=2)
- Service discovery: auto-routes Nacos-registered services
- Auth filter: `AuthFilter` (stub — extension point for token validation)
- CORS handling: `CorsConfig` for cross-origin requests

### omni-business (Business Service)

Business microservice demonstrating standard layered architecture:

```
Controller  →  Service (interface)  →  ServiceImpl  →  Repository (future)
  Param check    Business definition    Business logic    Data access
  Result wrap    @Transactional         Transaction mgmt  SQL / ORM
```

- `UserController`: RESTful API endpoints returning `R<T>`
- `UserService` (interface) + `UserServiceImpl` (implementation): interface-based Service layer
- `RemoteServiceFeignClient`: OpenFeign remote call example

### omni-frontend (Vue 3 SPA)

| Layer | Directory | Responsibility |
|-------|-----------|----------------|
| API | `src/api/` | One file per domain, shared Axios instance, type-safe |
| Store | `src/stores/` | Pinia Composition API style, one store per domain |
| Router | `src/router/` | Lazy-loaded routes + navigation guard (auth by default) |
| Views | `src/views/` | Page components, SFC order: script → template → style |
| Layout | `src/layout/` | App shell (sidebar + header + content area) |
| Types | `src/types/` | Shared type definitions (single source for ApiResponse, PageResult) |
| Styles | `src/styles/` | Global reset + layout styles |

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
| Layer 1: Architecture | System boundaries, module responsibilities, data flow, constraints | `docs/architecture.md` |
| Layer 2: Patterns | Backend/frontend coding patterns, API contracts, core flows | `docs/backend-patterns.md`, `docs/frontend-patterns.md`, `docs/api-contract.md`, `docs/core-flows.md` |
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
| `omni-business` compile fails | `omni-common` not installed first | Run `./mvnw install -pl omni-common` first |
| Frontend type mismatch | `ApiResponse` defined in multiple places | Import only from `@/types/api` — never duplicate |
| Actuator gateway endpoint 404 | Requires explicit enablement | Configure `management.endpoint.gateway.enabled: true` |

## AI-Native Engineering Practice

This project supports AI-assisted development workflows:

- **`AGENTS.md`**: AI execution manual defining hard constraints, execution rules, and completion checklists
- **`docs/` directory**: System truth documents — AI reads these first to understand system context before modifying code
- **`.qoder/skills/`**: AI behavioral extension units (e.g., `/grill-me` for design stress-testing)

Core principle: **Layers 1 & 2 (Architecture + Patterns) must be defined first, so Layer 3 (Code) can be safely delegated to AI at full speed.**

## License

[Apache License 2.0](LICENSE)
