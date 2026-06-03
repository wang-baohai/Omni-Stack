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
| Circuit Breaker / Flow Control | Sentinel Dashboard            | 1.8.8          |
| Frontend    | Vue 3 + TypeScript                          | 3.5.35 / 5.9.3 |
| Bundler     | Vite 8 (Rolldown)                           | 8.0.14         |
| UI Framework| Element Plus                                | 2.14.0         |
| State       | Pinia                                       | 3.0.4          |
| Router      | Vue Router                                  | 5.0.7          |
| Node.js     | Node.js LTS                                 | >= 22.12.0     |

## System Truth

Architecture, patterns, API contracts, and core flows are documented in `docs/`. **Read those first.** This file contains only execution rules and build commands.

| Document | Purpose |
|----------|---------|
| `docs/architecture.md` | System boundaries, module map, data flow, constraints |
| `docs/api-contract.md` | Response format, error codes, pagination, naming |
| `docs/backend-patterns.md` | Java layering, validation, exceptions, logging, OOP rules |
| `docs/frontend-patterns.md` | Vue/TS patterns, state management, routing, component conventions |
| `docs/core-flows.md` | End-to-end traces of login, list query, and form submission |

## Entry Points

**Backend:**
- Auth service: `omni-backend/omni-auth/src/main/java/com/omni/auth/AuthApplication.java`
- Business service: `omni-backend/omni-business/src/main/java/com/omni/business/BusinessApplication.java`
- Gateway: `omni-backend/omni-gateway/src/main/java/com/omni/gateway/GatewayApplication.java`
- Common library: `omni-backend/omni-common/src/main/java/com/omni/common/`

**Frontend:**
- App bootstrap: `omni-frontend/src/main.ts`
- Router: `omni-frontend/src/router/index.ts`
- Shared types: `omni-frontend/src/types/api.ts`

**Configuration:**
- Auth config: `omni-backend/omni-auth/src/main/resources/application.yml`
- Gateway config: `omni-backend/omni-gateway/src/main/resources/application.yml`
- Business config: `omni-backend/omni-business/src/main/resources/application.yml`
- Vite config: `omni-frontend/vite.config.ts`

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
./mvnw clean install -pl omni-business -am

# Run Auth service (port 8100)
cd omni-backend/omni-auth
./mvnw spring-boot:run

# Run Gateway (port 8102)
cd omni-backend/omni-gateway
./mvnw spring-boot:run

# Run Business service (port 8101)
cd omni-backend/omni-business
./mvnw spring-boot:run
```

**Build order**: `omni-common` must be installed before `omni-business` or `omni-gateway` can compile. Use `./mvnw install -N && ./mvnw install -pl omni-common` from the parent if needed.

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
# Start all middleware (MySQL, Redis, Nacos, Sentinel)
docker compose up -d

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

# Sentinel Dashboard (port 8858)
docker run -d --name sentinel -p 8858:8858 bladex/sentinel-dashboard:1.8.8
```

Start order: Nacos -> Sentinel -> Backend services -> Frontend

### Service Ports

| Service          | Port  |
|------------------|-------|
| Frontend (dev)   | 3000  |
| Auth             | 8100  |
| Gateway          | 8102  |
| MySQL            | 3306  |
| Redis            | 6379  |
| Nacos            | 8080, 8848  |
| Sentinel         | 8858  |

## Hard Constraints

- JDK 25 required. `JAVA_HOME` must be set before any Maven commands.
- Gateway 5.x uses `spring.cloud.gateway.server.webflux` prefix — NOT `spring.cloud.gateway`.
- `omni-common` must be `mvn install`-ed before other modules can compile.
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

## Execution Rules

- Before writing backend code: read `docs/backend-patterns.md`.
- Before writing frontend code: read `docs/frontend-patterns.md`.
- Before designing or modifying an API: read `docs/api-contract.md`.
- Before modifying module structure or data flow: read `docs/architecture.md` and `docs/core-flows.md`.
- After backend changes: run `cd omni-backend && ./mvnw clean install` to verify compilation.
- After frontend changes: run `npm run build` and `npm run lint` in `omni-frontend/`.
- Use `./mvnw` (not `mvn`) for all Maven commands.

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

## Testing

No tests exist yet. Test directories (`src/test/`) have not been created in any module.
