# Omni-Stack

> A microservices scaffolding platform built with Spring Boot 4 + Vue 3, structured with the Harness Industrial Design Pattern to provide an industry best-practice foundation for AI-assisted development.

**[中文](README.md)** | **[日本語](README.jp.md)**

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
├── docker-compose.yml               # Middleware orchestration (MySQL, Redis, Nacos, Sentinel)
├── docs/                            # System truth documents (Architecture + Patterns + Contract)
│   ├── architecture.md                # System boundaries, module map, data flow, constraints
│   ├── api-contract.md                # Response format, error codes, pagination, naming
│   ├── backend-patterns.md            # Backend layering, validation, exceptions, logging, OOP
│   ├── frontend-patterns.md           # Frontend directory, API layer, state, components
│   └── core-flows.md                  # Login / query / submission end-to-end traces
├── scripts/
│   └── sql/
│       ├── init-all.sql               # Authoritative database initialization script (DDL + seed data)
│       └── init-nacos.sql           # Nacos v3.1.1 MySQL persistence initialization script
├── omni-backend/                    # Maven multi-module backend
│   ├── mvnw / mvnw.cmd                # Maven Wrapper (3.9.16)
│   ├── pom.xml                        # Parent POM (dependency management)
│   ├── omni-common/                   # Shared: unified response, global exception, Jackson config
│   ├── omni-auth/                     # Auth service: login, captcha, JWT, OAuth2 (port 8100)
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
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   omni-frontend  │────>│   omni-gateway    │────>│    omni-auth     │
│   Vue 3 SPA     │/api │  WebFlux :8102    │lb://│   Spring :8100  │
│   :3000         │────>│  StripPrefix=2    │────>│  Security+OAuth2│
└─────────────────┘     └──────────────────┘     └─────────────────┘
                            │
                    ┌───────┴────────┐
                    │  MySQL :3306   │  Persistence storage
                    │  Redis :6379   │  Cache + captcha
                    │  Nacos :8848   │  Discovery + Config
                    │  Sentinel :8858│  Flow Control
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
| Docker Desktop | Any stable | For running middleware (MySQL, Redis, Nacos, Sentinel) |

> **Note**: Maven Wrapper (3.9.16) is bundled. Use `./mvnw` instead of `mvn` for all Maven commands.

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `JAVA_HOME` | - | **Required** — path to JDK 25 installation |
| `NACOS_SERVER_ADDR` | `127.0.0.1:8848` | Nacos server address |
| `NACOS_NAMESPACE` | (empty) | Nacos namespace |
| `SENTINEL_DASHBOARD` | `127.0.0.1:8858` | Sentinel dashboard address |
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

```bash
# Start all middleware (MySQL, Redis, Nacos, Sentinel) with one command
docker compose up -d

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

# Start Gateway (port 8102, in a new terminal)
cd omni-gateway
./mvnw spring-boot:run
```

**Build order**: `omni-common` must be installed first.

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

**Start order**: MySQL → Redis → Nacos → Sentinel → Backend (Auth, Gateway) → Frontend

## Service Ports

| Service | Port | Description |
|---------|------|-------------|
| Frontend dev server | 3000 | Vite dev server, proxies /api requests |
| Auth service | 8100 | Spring Security + OAuth2 Authorization Server |
| API Gateway | 8102 | Spring Cloud Gateway (WebFlux) |
| MySQL | 3306 | Primary database (omni_auth) |
| Redis | 6379 | Captcha cache |
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

### omni-auth (Auth Service)

Authentication microservice built on Spring Security 7 + OAuth2 Authorization Server:

- **User login**: username + password + captcha + multi-tenant, issues RS256 JWT
- **Multi-provider Social Login**: extensible social login architecture based on `OAuth2ProviderHandler` Strategy Pattern, with GitHub, Google, and Gitee providers integrated; WeChat login entry reserved on frontend. HMAC-SHA256 state signing against tampering, auto-creates local user and links third-party identity on first login (`sys_user_oauth_provider` table)
- **OAuth2 authorization**: Authorization Code + PKCE flow for third-party integration
- **Device Authorization Grant** (RFC 8628): provides authorization for IoT devices, CLI tools, and other browserless scenarios via the `omni-device` client; frontend `/device` page simulates device-initiated authorization with token polling, and `/device/verify` page allows users to complete authorization by scanning or entering a code on another device
- **Client management**: CRUD on `oauth2_registered_client`, supports dynamic registration
- **Multi-tenant RBAC**: `tenantId:username` user resolution + role-permission tree
- **JWT signing**: RSA key pair, JWK endpoint for Gateway public key verification

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
| Views | `src/views/` | Page components, SFC order: script → template → style; includes `device/` subdirectory for OAuth2 Device Authorization Grant frontend interaction |
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

## AI-Native Engineering Practice

This project supports AI-assisted development workflows:

- **`AGENTS.md`**: AI execution manual defining hard constraints, execution rules, and completion checklists
- **`docs/` directory**: System truth documents — AI reads these first to understand system context before modifying code
- **`.qoder/skills/`**: AI behavioral extension units (e.g., `/grill-me` for design stress-testing)

Core principle: **Layers 1 & 2 (Architecture + Patterns) must be defined first, so Layer 3 (Code) can be safely delegated to AI at full speed.**

## License

[Apache License 2.0](LICENSE)
