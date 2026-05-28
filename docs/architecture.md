# System Architecture

## Goal

Omni-Stack is a microservices scaffolding platform providing a ready-to-use Spring Cloud + Vue 3 full-stack development environment. It enables teams to rapidly build business systems on a standardized, production-grade infrastructure.

## System Boundaries

| Boundary | Omni-Stack Frontend (omni-frontend) | Omni-Stack Backend (omni-backend) |
|----------|--------------------------------------|-----------------------------------|
| Responsibility | Presentation, interaction, routing, form UX, user state rendering | Business rules, permission checks, data consistency, persistence, audit |
| Prohibited | Must not contain business logic affecting data correctness | Must not contain presentation logic or UI concerns |
| Validation | Client-side UX validation (required fields, format hints) | Server-side authoritative validation (Jakarta Bean Validation) |

## Module Map

| Module | Role | Port | Technology | Boundary Constraint |
|--------|------|------|------------|---------------------|
| `omni-common` | Shared models, utils, exception handling, Jackson config | N/A (library) | Spring Boot Web (optional), Validation (optional), Lombok | No business logic; only cross-cutting concerns |
| `omni-gateway` | API Gateway, request routing, authentication filter | 8090 | Spring Cloud Gateway Server (WebFlux) | No business logic; routing and cross-cutting filters only |
| `omni-business` | Business microservice | 8081 | Spring Boot Web, OpenFeign, Sentinel | Business logic lives here; no direct HTTP/response manipulation in Service layer |
| `omni-frontend` | Vue 3 SPA | 3000 (dev) | Vue 3, Pinia, Vue Router, Element Plus, Axios | Presentation layer only; no data-authoritative business rules |

## Dependency Graph

```
omni-common  (shared library, no Spring Boot main class)
    ^                ^
    |                |
omni-business    (omni-gateway does NOT depend on omni-common;
    |             it uses the reactive WebFlux stack independently)
    |
    +-- registers with Nacos --+
                               |
omni-gateway --- routes via lb:// ---> omni-business
    |
omni-frontend --- /api proxy :3000 ---> omni-gateway :8090
```

**Build dependency**: `omni-common` must be `mvn install`-ed before `omni-business` or `omni-gateway` can compile.

## Data Flow

```
Browser (Vue SPA)
    |  HTTP request (e.g., GET /api/business/user/list?page=1&size=10)
    v
Vite Dev Server (:3000)  -- proxy /api/** -->
    |
Gateway (:8090)
    |  1. AuthFilter checks Authorization header (stub: pass-through)
    |  2. Route matching: Path=/api/business/** -> lb://omni-business
    |  3. StripPrefix=2: /api/business/user/list -> /user/list
    v
Business Service (:8081)
    |  1. UserController receives /user/list
    |  2. UserService processes business logic
    |  3. (Future) Repository queries database
    |  4. Response wrapped in R<T>
    v
JSON Response: { code: 200, message: "success", data: { ... } }
    |
Browser renders result
```

## External Dependencies

| Service | Purpose | Version | Docker Command |
|---------|---------|---------|----------------|
| Nacos Server | Service discovery + configuration center | v3.1.1 | `docker run -d --name nacos -p 8848:8848 -p 9848:9848 -e MODE=standalone nacos/nacos-server:v3.1.1` |
| Sentinel Dashboard | Flow control + circuit breaking dashboard | 1.8.8 | `docker run -d --name sentinel -p 8858:8858 bladex/sentinel-dashboard:1.8.8` |

**Start order**: Nacos -> Sentinel -> Backend services (Gateway, Business) -> Frontend

## Key Constraints

1. **JDK 25 required**: Spring Boot 4.x Maven plugin requires Java 17+; this project targets JDK 25. `JAVA_HOME` must be set before running any Maven commands.
2. **Gateway 5.x config prefix**: Routes and settings must be under `spring.cloud.gateway.server.webflux`, NOT `spring.cloud.gateway` (the old prefix is silently ignored).
3. **Build order**: `omni-common` must be installed first. Use `./mvnw install -N && ./mvnw install -pl omni-common` from the parent POM if needed.
4. **No direct service-to-service calls**: Inter-service communication must go through OpenFeign clients, never raw HTTP calls.
5. **Gateway is reactive**: `omni-gateway` runs on WebFlux, not Servlet. It cannot depend on `omni-common` (which includes `spring-boot-starter-web`).

## Extension Points

### Adding a New Microservice

1. Create a new Maven module under `omni-backend/` (e.g., `omni-order`)
2. Add dependency on `omni-common` in the new module's `pom.xml`
3. Register the module in the parent `pom.xml` `<modules>` section
4. Add `@ComponentScan(basePackages = {"com.omni.order", "com.omni.common"})` to the application class (until auto-config is set up)
5. Configure `application.yml` with Nacos discovery and a unique port
6. Add a Gateway route in `omni-gateway/application.yml` for the new service

### Adding a New Frontend View

1. Create `src/views/<feature>/index.vue` following the SFC order: `<script setup>` -> `<template>` -> `<style scoped>`
2. Add a route in `src/router/index.ts` with `meta: { title, icon, requiresAuth }`
3. Create API functions in `src/api/<domain>.ts` using the shared Axios instance
4. If needed, create a Pinia store in `src/stores/<domain>.ts` using Composition API style
