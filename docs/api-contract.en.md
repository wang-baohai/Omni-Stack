# API Contract

> This document defines the authoritative API contract between the frontend and backend. Both sides must comply with these structures. Any deviation requires explicit team approval.
> For the complete social login flow, see [core-flows.en.md](core-flows.en.md). Data dictionary and workflow endpoints are documented separately.

---

## Table of Contents

- [1. Response Envelope](#1-response-envelope)
- [2. Error Code Reference](#2-error-code-reference)
- [3. Pagination Contract](#3-pagination-contract)
- [4. RESTful URL Conventions](#4-restful-url-conventions)
- [5. Gateway Route Configuration](#5-gateway-route-configuration)
- [6. Naming Conventions](#6-naming-conventions)
- [7. Time Format](#7-time-format)
- [8. Request Header Conventions](#8-request-header-conventions)
- [9. Authentication Header](#9-authentication-header)
- [10. Social Login Endpoints](#10-social-login-endpoints)
- [11. XSS Config Management Endpoints](#11-xss-config-management-endpoints)
- [12. Base Service Dictionary Management Endpoints](#12-base-service-dictionary-management-endpoints)
- [13. API Versioning Strategy](#13-api-versioning-strategy)
- [14. Null Semantics](#14-null-semantics)

---

## 1. Response Envelope

All API responses use the unified `R<T>` wrapper.

```json
// Success
{
  "code": 200,
  "message": "success",
  "data": { ... }
}

// Failure (business error)
{
  "code": 500,
  "message": "Operation failed"
}

// Failure (validation error)
{
  "code": 400,
  "message": "username: Username is required; email: Email is required"
}
```

### Backend Type: `R<T>`

```java
@Data
public class R<T> implements Serializable {
    private int code;
    private String message;
    private T data;

    public static <T> R<T> ok(T data) { ... }
    public static <T> R<T> fail(String message) { ... }
    public static <T> R<T> fail(int code, String message) { ... }
}
```

**Location**: `omni-common-core` module, `com.omni.common.core.result.R`.

### Frontend Type: `ApiResponse<T>`

```typescript
interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}
```

**Canonical location**: `src/types/api.ts` (single source of truth; do not duplicate in other files).

---

## 2. Error Code Reference

### 2.1 System-Level Error Codes

| HTTP Status | Business Code | Scenario | Trigger | Handler |
|------------|--------|------|---------|--------|
| 200 | 200 | Success | `R.ok(data)` | — |
| 400 | 400 | Parameter validation failure | `MethodArgumentNotValidException` / `BindException` caught by `GlobalExceptionHandler` | Frontend displays field errors from `message` |
| 401 | 401 | Unauthenticated | Gateway `AuthFilter` returns 401 JSON | Frontend redirects to login page |
| 403 | 403 | Insufficient permissions | `AccessDeniedException` / `AuthorizationDeniedException` caught by `GlobalExceptionHandler` | Frontend displays "Insufficient permissions" notification |
| 200 | 404 | Resource not found | `throw new BusinessException(404, "xxx not found")` | Frontend displays error message |
| 200 | 500 | Business exception | `BusinessException` caught by `GlobalExceptionHandler` | Frontend displays error message |
| 500 | 500 | Unknown system error | Catch-all `Exception` handler | Frontend displays "Internal server error" |

### 2.2 Business-Level Error Codes

| Business Code | Scenario | Example Message |
|--------|------|---------|
| 500 | Invalid/expired captcha | "Captcha has expired" |
| 500 | Authentication failed | "Incorrect username or password" |
| 500 | Account disabled | "Account has been disabled" |
| 500 | Account locked | "Account locked, please try again in N minutes" |
| 500 | Tenant not found/disabled | "Tenant does not exist or is disabled" |
| 400 | Uniqueness conflict | "Username already exists" / "Job type code already exists" |
| 404 | Resource not found | "Organization unit not found" / "Dictionary data not found" |
| 403 | Insufficient permissions | "Insufficient permissions, access denied" |

### 2.3 Gateway-Level Error Codes

| HTTP Status | Scenario | Response Format |
|------------|------|---------|
| 401 | Invalid JWT signature | `{"code":401,"message":"Invalid JWT signature","data":null}` |
| 401 | JWT expired | `{"code":401,"message":"JWT token expired","data":null}` |
| 401 | Token revoked | `{"code":401,"message":"Token has been revoked","data":null}` |
| 401 | Missing Authorization header | `{"code":401,"message":"Missing Authorization header","data":null}` |

### 2.4 Social Login Error Codes

| error Parameter | Meaning | Trigger |
|------------|------|---------|
| `user_denied` | User denied authorization | Third-party platform callback carries `error=access_denied` |
| `invalid_callback` | Missing callback parameters | code or state is empty |
| `social_login_failed` | Login flow exception | State verification failed, third-party API error, user info retrieval failed, user disabled |

### 2.5 Frontend Error Handling Flow

The Axios response interceptor (`src/api/request.ts`) checks `res.code !== 200`:
1. Displays `ElMessage.error(res.message)` error notification
2. When code is `401`: calls `userStore.logout()` and redirects to `/login`
3. Returns `Promise.reject(new Error(res.message))`

**HTTP Status Code Handling**:
- HTTP 401 (Gateway JWT validation failure): Caught by Axios `onError` interceptor, clears token and redirects to login page
- HTTP 403 (Insufficient permissions): Displays `ElMessage.error("Insufficient permissions")` and navigates back
- HTTP 400 (Parameter validation failure): Displays field-level error messages returned by `GlobalExceptionHandler`

---

## 3. Pagination Contract

### Backend Type: `PageResult<T>`

```java
@Data
public class PageResult<T> implements Serializable {
    private List<T> records;
    private long total;
    private long size;
    private long current;
    private long pages;   // auto-calculated: (total + size - 1) / size

    public PageResult(List<T> records, long total, long size, long current) { ... }
}
```

### Frontend Type: `PageResult<T>`

```typescript
interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}
```

**Canonical location**: `src/types/api.ts`.

### Usage Pattern

```java
// Backend Controller
@GetMapping("/list")
public R<PageResult<UserVO>> listUsers(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int size) {
    return R.ok(userService.listUsers(page, size));
}
```

```typescript
// Frontend API call
export function listUsers(page: number, size: number) {
  return request.get<ApiResponse<PageResult<UserInfo>>>(
    `/auth/user/list?page=${page}&size=${size}`,
  )
}
```

### Pagination Parameter Conventions

| Parameter | Type | Default | Description |
|------|------|--------|------|
| `page` | int | 1 | Current page number (starting from 1) |
| `size` | int | 10 | Number of items per page |
| `records` | List | — | Data list for the current page |
| `total` | long | — | Total number of records |
| `pages` | long | — | Total number of pages (auto-calculated) |

---

## 4. RESTful URL Conventions

| Operation | HTTP Method | URL Pattern | Example |
|------|-----------|---------|------|
| Get by ID | GET | `/{resource}/{id}` | `GET /user/1` |
| Paginated list | GET | `/{resource}/list` | `GET /user/list?page=1&size=10` |
| Create | POST | `/{resource}` | `POST /user` |
| Update | PUT | `/{resource}/{id}` | `PUT /user/1` |
| Delete | DELETE | `/{resource}/{id}` | `DELETE /user/1` |
| Batch operation | POST | `/{resource}/batch` | `POST /user/batch` |

**Gateway path prefix**: All frontend requests use `/api/<service>/<resource>` (e.g., `/api/auth/user/list`). The Gateway strips `/api/<service>` (StripPrefix=2), and the downstream service receives `/<resource>`.

**Exception**: The Base service's `/api/base/**` route does **not** have a StripPrefix filter. Base service controllers use the full path (e.g., `@RequestMapping("/api/base/dict/type")`).

---

## 5. Gateway Route Configuration

### 5.1 Local Development Environment Routes

Gateway `application.yml` route configuration (`spring.cloud.gateway.server.webflux.routes`):

| Route ID | Path Match | Target Service | StripPrefix | Description |
|---------|---------|---------|-------------|------|
| `omni-auth-oauth2` | `/oauth2/**` | `lb://omni-auth` | None | OAuth2 authorization server endpoints |
| `omni-auth-wellknown` | `/.well-known/**` | `lb://omni-auth` | None | OpenID Connect discovery endpoint |
| `omni-auth` | `/api/auth/**` | `lb://omni-auth` | 2 | Auth service REST API |
| `omni-base` | `/api/base/**` | `lb://omni-base` | **None** | Base service (uses full path) |
| `omni-base-job` | `/api/job/**` | `lb://omni-base` | **None** | Scheduled job management |
| `omni-workflow` | `/api/workflow/**` | `lb://omni-workflow` | **None** | Workflow engine |

### 5.2 Docker Deployment Routes

In Docker deployment, the route configuration is the same, but the target service URIs are automatically resolved via Nacos service discovery:

| Frontend Request | Gateway Route | Downstream Received Path | Description |
|---------|-------------|-------------|------|
| `GET /api/auth/user/list` | `lb://omni-auth` + StripPrefix=2 | `GET /user/list` | Auth service strips prefix |
| `GET /api/base/dict/type/list` | `lb://omni-base` no StripPrefix | `GET /api/base/dict/type/list` | Base service retains full path |
| `POST /api/workflow/model` | `lb://omni-workflow` no StripPrefix | `POST /api/workflow/model` | Workflow service retains full path |
| `GET /api/job/type/list` | `lb://omni-base` no StripPrefix | `GET /api/job/type/list` | Job route to Base service |

### 5.3 AuthFilter Whitelist Paths

The following paths skip JWT verification (`AuthFilter` does not intercept):

```
/api/auth/login          — Login
/api/auth/register       — Registration
/api/auth/captcha        — Captcha
/api/auth/tenants        — Tenant list
/api/auth/oauth2/        — Social login
/actuator/               — Health check
/oauth2/                 — OAuth2 endpoints
/.well-known/            — OIDC discovery
/login                   — Spring Security login
/error                   — Error page
```

---

## 6. Naming Conventions

### Request/Response DTOs

| Type | Suffix | Example |
|------|------|------|
| Create request | `CreateXxxRequest` | `CreateUserRequest` |
| Update request | `UpdateXxxRequest` | `UpdateUserRequest` |
| View object | `XxxVO` | `UserVO` |
| Query parameters | `XxxQuery` | `UserQuery` |

DTOs can be defined as static inner classes of the Controller (for simple cases) or as standalone files (for complex cases).

### Field Naming

- Java fields: `lowerCamelCase` (e.g., `createTime`, `userName`)
- JSON serialization: `lowerCamelCase` (directly matches Java field names)
- URL path segments: `kebab-case` or single words (e.g., `/user/list`, not `/user/getAllUsers`)

---

## 7. Time Format

Configured in `JacksonConfig.java`:

| Java Type | JSON Format | Example |
|-----------|----------|------|
| `LocalDateTime` | `yyyy-MM-dd HH:mm:ss` | `2026-05-28 14:30:00` |
| `LocalDate` | `yyyy-MM-dd` | `2026-05-28` |

Timestamps are serialized as strings, not numeric timestamps (`WRITE_DATES_AS_TIMESTAMPS` is disabled).

**Configuration location**: `JacksonConfig` in the `omni-common` module, automatically activated via `AutoConfiguration.imports`. All services that depend on `omni-common` automatically get consistent time formatting.

---

## 8. Request Header Conventions

### 8.1 Request Headers Injected by Gateway

After successful JWT verification, the Gateway's `AuthFilter` injects the following headers into downstream requests:

| Header | Type | Description | Example |
|--------|------|------|------|
| `X-User-Id` | String | User ID | `"1"` |
| `X-User-Name` | String | Username | `"admin"` |
| `X-Tenant-Id` | String | Tenant ID | `"1"` |
| `X-User-Roles` | String | Comma-separated role codes | `"SUPER_ADMIN,DEPT_LEADER"` |
| `X-User-Scopes` | String | Space/comma-separated permission codes | `"dict:type:list dict:data:create"` |

### 8.2 Request Headers Sent by Frontend

| Header | Source | Description |
|--------|------|------|
| `Authorization: Bearer <JWT>` | Automatically injected by Axios interceptor | Token obtained from `useUserStore()` |
| `X-Tenant-Id` | Automatically injected by Axios interceptor | Tenant ID obtained from `useUserStore()` |
| `Content-Type: application/json` | Axios default | JSON request body |

### 8.3 Security Response Headers (Injected by Gateway)

`SecurityHeadersFilter` (WebFlux WebFilter) adds the following to all responses passing through the gateway:

| Response Header | Value | Purpose |
|--------|-----|------|
| `X-Content-Type-Options` | `nosniff` | Prevent browser MIME-type sniffing |
| `X-Frame-Options` | `SAMEORIGIN` | Prevent clickjacking |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Control Referer header leakage |

---

## 9. Authentication Header

```
Authorization: Bearer <token>
```

- Set by the Axios request interceptor (`src/api/request.ts`) using the token from `useUserStore()`
- Verified by `AuthFilter` in `omni-gateway` (JWT RS256 signature verification + claims extraction + identity header injection)
- Public paths exempt from authentication: `/api/auth/**`, `/actuator/**`, `/favicon.ico`

---

## 10. Social Login Endpoints

Social login endpoints return HTTP 302 redirects (not standard `R<T>` responses), because the frontend triggers browser navigation via `window.location.href`.

| HTTP Method | URL | Description |
|-----------|-----|------|
| GET | `/api/auth/oauth2/{provider}?tenant_id=1` | Initiate third-party login, 302 redirect to third-party authorization page |
| GET | `/api/auth/oauth2/{provider}/callback?code=XXX&state=YYY` | Handle third-party callback, 302 redirect to frontend callback page on success |

### Initiating Login

```
# GitHub
GET /api/auth/oauth2/github?tenant_id=1
→ 302 Location: https://github.com/login/oauth/authorize?client_id=...&redirect_uri=...&scope=...&state=...

# Google
GET /api/auth/oauth2/google?tenant_id=1
→ 302 Location: https://accounts.google.com/o/oauth2/v2/auth?client_id=...&redirect_uri=...&response_type=code&scope=openid+profile+email&state=...

# Gitee
GET /api/auth/oauth2/gitee?tenant_id=1
→ 302 Location: https://gitee.com/oauth/authorize?client_id=...&redirect_uri=...&response_type=code&scope=user_info&state=...
```

- `{provider}` supports `github`, `google`, and `gitee`
- `tenant_id` is required, specifying the target tenant for login
- The state parameter contains an HMAC-SHA256 signature (`tenantId|timestamp|hmac`) to prevent CSRF attacks

### Callback Handling

```
# GitHub/Google/Gitee callback
GET /api/auth/oauth2/{provider}/callback?code=XXX&state=YYY

→ Success: 302 Location: /callback#token=<JWT>&username=<username>
→ Failure: 302 Location: /login?error=<error_code>&message=<message>
```

### OAuth2 Callback URL Configuration for Docker Deployment

When deploying with Docker, the social login `redirect_uri` must use a **URL accessible from the host machine**:

| Deployment Environment | redirect_uri Example |
|---------|------------------|
| Local development | `http://localhost:8100/api/auth/oauth2/github/callback` |
| Docker deployment | `http://<host-ip>:8100/api/auth/oauth2/github/callback` |
| Production | `https://your-domain.com/api/auth/oauth2/github/callback` |

> **Note**: In Docker deployment, the Auth service container's internal port is 8080, but the OAuth2 callback URL must use the host-mapped port 8100 (because third-party platforms need to call back to a publicly/LAN-reachable address on the host machine).

### Frontend Callback Page

The `/callback` page (`src/views/callback/index.vue`) is responsible for:
1. Parsing `token` and `username` from the URL fragment
2. Storing them in `localStorage` (via `useUserStore`)
3. Redirecting to the Dashboard

> For the complete flow sequence diagram, see [core-flows.en.md](core-flows.en.md) Flow 4.

---

## 11. XSS Config Management Endpoints

Base path: `/api/auth/xss-config` (Gateway StripPrefix=2 → downstream `/xss-config/...`)

### Get Current XSS Configuration

```
GET /api/auth/xss-config/settings
Authorization: Bearer <token>
X-Tenant-Id: 1

Response 200:
{
  "code": 200,
  "data": {
    "enabled": false,
    "rules": [
      { "id": 1, "ruleType": "HTML_TAG", "pattern": "script" }
    ]
  }
}
```

### Toggle Global Switch

```
PUT /api/auth/xss-config/toggle
Authorization: Bearer <token>
X-Tenant-Id: 1

@PreAuthorize("hasAuthority('system:xssconfig:update')")
Response 200: { "code": 200, "message": "success" }
```

### Rule CRUD

| HTTP Method | URL | Permission Code | Description |
|-----------|-----|--------|------|
| GET | `/api/auth/xss-config/rules/list?page=1&size=10` | `system:xssconfig:list` | Paginated list |
| POST | `/api/auth/xss-config/rules` | `system:xssconfig:create` | Create rule |
| PUT | `/api/auth/xss-config/rules/{id}` | `system:xssconfig:update` | Update rule |
| DELETE | `/api/auth/xss-config/rules/{id}` | `system:xssconfig:delete` | Delete rule |
| PUT | `/api/auth/xss-config/rules/{id}/toggle` | `system:xssconfig:update` | Toggle rule enabled status |

**ruleType enum values**: `HTML_TAG` | `EVENT_HANDLER` | `DANGEROUS_PROTOCOL` | `CUSTOM_PATTERN`

### Permission Codes

| Permission Code | Description |
|--------|------|
| `system:xssconfig:list` | View XSS configuration and rules |
| `system:xssconfig:update` | Toggle global switch, update rules, toggle rule status |
| `system:xssconfig:create` | Create rule |
| `system:xssconfig:delete` | Delete rule |

---

## 12. Base Service Dictionary Management Endpoints

The Base service (`omni-base :8101`) provides data dictionary management with a two-level "type + data" structure.

**Route note**: The Gateway route `Path=/api/base/**` has **no** StripPrefix filter. Base service controllers use the full path (e.g., `@RequestMapping("/api/base/dict/type")`).

### Dictionary Type Endpoints

Base path: `/api/base/dict/type`

| HTTP Method | URL | Permission Code | Description |
|-----------|-----|--------|------|
| GET | `/api/base/dict/type/list?page=1&size=10&typeCode=&typeName=&status=` | `dict:type:list` | Paginated list with filtering |
| GET | `/api/base/dict/type/{id}` | `dict:type:list` | Get by ID |
| POST | `/api/base/dict/type` | `dict:type:create` | Create (validates typeCode uniqueness within tenant) |
| PUT | `/api/base/dict/type/{id}` | `dict:type:update` | Update (partial update) |
| DELETE | `/api/base/dict/type/{id}` | `dict:type:delete` | Delete (cascading delete of associated data) |
| PUT | `/api/base/dict/type/{id}/status` | `dict:type:update` | Toggle enabled/disabled |

**Example request**:

```
GET /api/base/dict/type/list?page=1&size=10
Authorization: Bearer <token>
X-Tenant-Id: 1

Response 200:
{
  "code": 200,
  "data": {
    "records": [
      { "id": 1, "typeCode": "sys_user_gender", "typeName": "User Gender", "status": 1, "sort": 0 }
    ],
    "total": 3,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
```

### Dictionary Data Endpoints

Base path: `/api/base/dict/data`

| HTTP Method | URL | Permission Code | Description |
|-----------|-----|--------|------|
| GET | `/api/base/dict/data/list?typeCode=sys_user_gender&page=1&size=10` | `dict:data:list` | Paginated query by typeCode |
| POST | `/api/base/dict/data` | `dict:data:create` | Create (validates parent type exists) |
| PUT | `/api/base/dict/data/{id}` | `dict:data:update` | Update (partial update) |
| DELETE | `/api/base/dict/data/{id}` | `dict:data:delete` | Delete single entry |
| POST | `/api/base/dict/data/refresh-cache` | `dict:data:refresh` | Manually refresh Redis cache |

**Example request**:

```
POST /api/base/dict/data
Authorization: Bearer <token>
X-Tenant-Id: 1
Content-Type: application/json

{
  "typeCode": "sys_user_gender",
  "dictValue": "3",
  "dictLabel": "Confidential",
  "tagType": "warning",
  "sort": 3
}

@PreAuthorize("hasAuthority('dict:data:create')")
Response 200: { "code": 200, "data": { "id": 8, ... } }
```

### Dictionary Permission Codes

| Permission Code | Description |
|--------|------|
| `dict:type:list` | View dictionary type list |
| `dict:type:create` | Create dictionary type |
| `dict:type:update` | Update/toggle dictionary type status |
| `dict:type:delete` | Delete dictionary type (cascading) |
| `dict:data:list` | View dictionary data list |
| `dict:data:create` | Create dictionary data |
| `dict:data:update` | Update dictionary data |
| `dict:data:delete` | Delete dictionary data |
| `dict:data:refresh` | Manually refresh dictionary cache |

### Tenant Isolation

All list queries and create operations require the `X-Tenant-Id` header (extracted from the JWT token by the frontend, injected by the Gateway). Data is isolated by `tenant_id` at the SQL query layer. The dictionary type uniqueness constraint scope is `(tenant_id, type_code)`.

---

## 13. API Versioning Strategy

### Current Decision

No URL-based versioning during the scaffolding phase. When the API stabilizes and multiple consumers exist, introduce prefix versioning.

### Future Evolution Path

| Phase | Version Strategy | URL Example |
|------|---------|---------|
| **Current (scaffolding)** | No version number | `/api/auth/user/list` |
| **V1 (after API stabilization)** | URL prefix version | `/api/v1/auth/user/list` |
| **V2 (breaking change)** | URL prefix version | `/api/v2/auth/user/list` |

**Version rules**:
- Adding new fields (backward compatible): no version change required
- Removing/renaming fields: requires a new version
- Changing request/response structures: requires a new version
- Legacy versions maintained for at least 6 months

---

## 14. Null Semantics

- `null` fields are included in JSON output (not suppressed)
- Empty collections are returned as `[]`, not `null`
- Optional single values use `null` to indicate absence, not empty strings
