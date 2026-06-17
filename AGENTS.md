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
| `docs/architecture.md` | System boundaries, module map, data flow, RBAC permission system, constraints |
| `docs/api-contract.md` | Response format, error codes, pagination, naming |
| `docs/backend-patterns.md` | Java layering, validation, exceptions, logging, security & data permission, OOP rules |
| `docs/frontend-patterns.md` | Vue/TS patterns, state management, routing, permission control, component conventions |
| `docs/core-flows.md` | End-to-end traces of login (password + captcha, GitHub social, Gitee social, device code), RBAC functional permission (Flow 5), data permission (Flow 6), XSS defense (Flow 8) |

## Entry Points

**Backend:**
- Auth service: `omni-backend/omni-auth/src/main/java/com/omni/auth/AuthApplication.java`
- Base service: `omni-backend/omni-base/src/main/java/com/omni/base/BaseApplication.java`
- Gateway: `omni-backend/omni-gateway/src/main/java/com/omni/gateway/GatewayApplication.java`
- Common library: `omni-backend/omni-common/src/main/java/com/omni/common/`
- Common core (POJO): `omni-backend/omni-common-core/src/main/java/com/omni/common/core/`
- Common MyBatis-Plus starter: `omni-backend/omni-common-mybatis/src/main/java/com/omni/common/mybatis/`
- Common Redis starter (blocking): `omni-backend/omni-common-redis/src/main/java/com/omni/common/redis/`
- Common Redis starter (reactive): `omni-backend/omni-common-redis-reactive/src/main/java/com/omni/common/redis/reactive/`

**Frontend:**
- App bootstrap: `omni-frontend/src/main.ts`
- Router: `omni-frontend/src/router/index.ts`
- Shared types: `omni-frontend/src/types/api.ts`

**Configuration:**
- Auth config: `omni-backend/omni-auth/src/main/resources/application.yml`
- Gateway config: `omni-backend/omni-gateway/src/main/resources/application.yml`
- Base config: `omni-backend/omni-base/src/main/resources/application.yml`
- Vite config: `omni-frontend/vite.config.ts`

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
| Base             | 8101  |
| Gateway          | 8102  |
| MySQL            | 3306  |
| Redis            | 6379  |
| Nacos            | 8080, 8848  |
| Sentinel         | 8858  |

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

## Execution Rules

- Before writing backend code: read `docs/backend-patterns.md`.
- Before writing frontend code: read `docs/frontend-patterns.md`.
- Before designing or modifying an API: read `docs/api-contract.md`.
- Before modifying module structure or data flow: read `docs/architecture.md` and `docs/core-flows.md`.
- After backend changes: run `cd omni-backend && ./mvnw clean install` to verify compilation.
- After frontend changes: run `npm run build` and `npm run lint` in `omni-frontend/`.
- Use `./mvnw` (not `mvn`) for all Maven commands.
- Before adding new write-operation endpoints: declare `@PreAuthorize` with `resource:action` permission codes and update `sys_permission` seed data in `scripts/sql/init-all.sql`.
- Before adding data permission to a new table: update `DataPermissionHandlerImpl` with the target table name and column mapping; ensure `DataPermissionInterceptor` is registered before `PaginationInnerInterceptor`.
- Before adding frontend buttons: add `v-permission` directive with the corresponding permission code.
- Before adding a new microservice: implement `XssConfigProvider` SPI in the new service module; `omni-common` dependency auto-registers XSS filter chain.
- Before modifying XSS rules or toggle: invalidate Redis cache in `XssConfigServiceImpl` write methods — never rely on TTL expiry for consistency.

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
真实凭证存放在 `application-local.yml`（已被 `.gitignore` 排除），通过 `spring.profiles.group.dev.include: local` 在 dev 环境下自动加载。

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
| `OAUTH2_STATE_SECRET` | OAuth2 state 参数的 HMAC-SHA256 签名密钥，所有提供商共用 |

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

项目提供 3 个自动装配的 Common Starter，新微服务引入即用：

| 模块 | 职责 | 适用服务类型 |
|------|------|-------------|
| `omni-common-mybatis` | MyBatis-Plus + MySQL 驱动 + 分页插件 + YAML 默认配置 | Servlet 服务 |
| `omni-common-redis` | 阻塞式 Redis + RedisTemplate 序列化 + RedisUtils | Servlet 服务 |
| `omni-common-redis-reactive` | 响应式 Redis + ReactiveRedisTemplate + ReactiveRedisUtils | WebFlux 服务（Gateway） |

**新服务接入步骤**：
1. POM 中依赖 `omni-common-mybatis` + `omni-common-redis`（或 `omni-common-redis-reactive`）
2. `application.yml` 中配置 `spring.datasource.*` 和 `spring.data.redis.host/port/database`
3. 启动类添加 `@MapperScan("com.omni.xxx.mapper")`
4. 分页、序列化、RedisUtils 自动生效

**覆盖机制**：服务定义同名 `mybatisPlusInterceptor` Bean 即可覆盖 common-mybatis 的默认分页配置（`@ConditionalOnMissingBean`）。

## Testing

No tests exist yet. Test directories (`src/test/`) have not been created in any module.
