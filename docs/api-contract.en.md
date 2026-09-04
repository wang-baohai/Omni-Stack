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
- [15. SRM MVP Contract](#15-srm-mvp-contract)
- [16. Workflow Cross-Service Contract](#16-workflow-cross-service-contract)
- [17. Procurement MVP Contract](#17-procurement-mvp-contract)
- [18. Asset MVP Contract](#18-asset-mvp-contract)

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
| 200 | 409 | Status/concurrency conflict | Optimistic-lock version mismatch or state machine rejects the transition | Frontend refreshes data and prompts the user to retry |
| 200 | 503 | Downstream dependency unavailable | CRM calls the Auth data-scope API and fails closed | Frontend shows the service is temporarily unavailable; never degrade into unauthorized data |
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
| 409 | Optimistic-lock or state conflict | "Data has been modified by another user, please refresh and retry" |
| 503 | Required dependency unavailable | "Authentication/authorization service is temporarily unavailable" |

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

**Gateway path prefix**: All frontend requests use `/api/<service>/<resource>` (e.g., `/api/auth/user/list`). Currently the Gateway does not use `StripPrefix` for the business routes of Auth, Base, Workflow, CRM, SRM, Procurement and Asset; downstream Controllers declare and receive the full `/api/**` path.

---

## 5. Gateway Route Configuration

### 5.1 Local Development Environment Routes

Gateway `application.yml` route configuration (`spring.cloud.gateway.server.webflux.routes`):

| Route ID | Path Match | Target Service | StripPrefix | Description |
|---------|---------|---------|-------------|------|
| `omni-auth-oauth2` | `/oauth2/**` | `lb://omni-auth` | None | OAuth2 authorization server endpoints |
| `omni-auth-wellknown` | `/.well-known/**` | `lb://omni-auth` | None | OpenID Connect discovery endpoint |
| `omni-auth` | `/api/auth/**` | `lb://omni-auth` | **None** | Auth service REST API (uses full path) |
| `omni-base` | `/api/base/**` | `lb://omni-base` | **None** | Base service (uses full path) |
| `omni-base-job` | `/api/job/**` | `lb://omni-base` | **None** | Scheduled job management |
| `omni-workflow` | `/api/workflow/**` | `lb://omni-workflow` | **None** | Workflow engine |

### 5.2 Docker Deployment Routes

In Docker deployment, the route configuration is the same, but the target service URIs are automatically resolved via Nacos service discovery:

| Frontend Request | Gateway Route | Downstream Received Path | Description |
|---------|-------------|-------------|------|
| `GET /api/auth/user/list` | `lb://omni-auth` no StripPrefix | `GET /api/auth/user/list` | Auth service retains full path |
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

### 8.3 Internal Service Request Headers

All service-to-service interfaces live under `/api/internal/**` and use shared-token authentication, not end-user JWT:

| Header | Required | Description |
|--------|------|------|
| `X-Internal-Token` | Yes | Shared service-to-service token; validated by `InternalApiAuthFilter` |
| `X-Tenant-Id` | Yes | Current business tenant; must match the `tenantId` in body/query |
| `Content-Type: application/json` | Required for JSON requests | JSON request body |

`InternalApiAuthFilter` acts as a container-level pre-filter that uniformly protects `/api/internal/**`; the service security chain must not require the Gateway user identity again. A missing or mismatched token returns HTTP 401; if the server has no token configured it fails closed with HTTP 503; a mismatch between the header tenant and the body/query tenant returns business code 403. Internal paths must not rely on `X-Gateway-Forwarded` or user permission headers.

### 8.4 Security Response Headers (Injected by Gateway)

`SecurityHeadersFilter` (WebFlux WebFilter) adds the following to all responses passing through the gateway:

| Response Header | Value | Purpose |
|--------|-----|------|
| `X-Content-Type-Options` | `nosniff` | Prevent browser MIME-type sniffing |
| `X-Frame-Options` | `DENY` | Forbid the page from being embedded in an iframe, preventing clickjacking |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Control Referer header leakage |
| `X-Trace-Id` | 32-char lowercase hexadecimal string | Correlates Gateway, Servlet, Feign and error feedback |

The Gateway always generates a new `X-Trace-Id` and does not trust a same-named header supplied by the public network; downstream Servlet services write the valid value into the MDC and the response header, and the common Feign interceptor keeps propagating it to internal calls. The frontend error panel can display the traceId from the response, and log troubleshooting should prefer searching the full call chain with the same value.

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

Base path: `/api/auth/xss-config` (the Gateway does not strip the prefix; the downstream keeps the full path)

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

### MQ Delivery Runtime Status

`GET /api/base/mq-message/runtime` requires the `base:mqmessage:list` permission and returns the current Outbox and background delivery capability:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "outboxWriteEnabled": true,
    "deliveryEnabled": false,
    "mode": "OUTBOX_ONLY"
  }
}
```

`OUTBOX_ONLY` means business transactions still write to the local Outbox, but the MQ relay/XXL-JOB is not running; the frontend must show a degraded-mode notice and disable resend operations. `FULL` means both writing and asynchronous delivery are enabled.

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

---

## 15. SRM MVP Contract

### 15.1 Suppliers and Sub-Resources

- Admin-side supplier lifecycle commands all carry `version`; blacklist recovery uses
  `POST /api/srm/supplier/{id}/restore-from-blacklist`.
- The `supplierId` in contact, qualification and bank-account paths must match the sub-resource's actual ownership; any mismatch returns 404 uniformly.
- `creditCode` is unique within the tenant; the pagination `size` is at most 100.
- Supplier 360 uses `GET /api/srm/supplier/{id}/overview`; the returned content is still trimmed by the caller's sub-resource and PII permissions.

### 15.2 Portal Enrollment

`POST /api/srm/portal/enroll` accepts only the tenant/user identity injected by the Gateway; the request must not carry tenantId or userId:

```json
{
  "requestId": "client-generated-uuid",
  "inviteToken": "raw-token-returned-once",
  "name": "Example Supplier Co., Ltd.",
  "creditCode": "91320000EXAMPLE"
}
```

The status in the response uses only: `PENDING_ROLE_ASSIGN`, `ROLE_ASSIGN_FAILED`, `COMPLETED`, `CANCELLED`.
The current user can query the status via `GET /api/srm/portal/enrollment` and, after a failure, call
`POST /api/srm/portal/enrollment/retry` to retry idempotently. No PortalUser is created and the enterprise-profile endpoints are not opened before role assignment completes.

### 15.3 Evaluation and Risk

- `GET /api/srm/evaluation/template/default/dimensions` returns the current tenant's default template and valid dimensions; the frontend must not hardcode database IDs.
- Scores range from 1-5, must cover all dimensions of the default template, and must not repeat.
- `GET /api/srm/risk/list` returns a paginated risk summary aggregated by each supplier's latest evaluation, filterable by `riskLevel`.
- `GET /api/srm/supplier/{id}/risk` returns an aggregated view of `indicators/latestAssessment/history`.
- Risk indicator updates carry `version`; `srm.risk.level-changed.v1` is produced only when the composite level changes from non-RED to RED.

### 15.4 Internal Supplier Summary

Downstream Procurement/Asset may call only with both `X-Internal-Token` and `X-Tenant-Id`:

- `GET /api/internal/supplier/{id}?tenantId={tenantId}`
- `GET /api/internal/supplier/search?tenantId={tenantId}&status=APPROVED&categoryCode={code}&limit=50`
- `POST /api/internal/supplier/batch`

The GET query tenantId and the batch body tenantId must exactly match `X-Tenant-Id`, otherwise 403 is returned. The batch request body is:

```json
{
  "tenantId": 1,
  "supplierIds": [101, 102, 101]
}
```

`supplierIds` must contain 1-100 positive integers; the server deduplicates by first occurrence and preserves the return order, omitting non-existent or deleted IDs from the result without returning 404 for the whole request due to a single missing item. The response contains only the supplier `id/supplierNo/name/status/levelCode/categoryCode`, not contacts, bank accounts or other PII.

### 15.5 Supplier Portal Quotation

The portal endpoints require `srm:portal:quotation`, the `SUPPLIER` role, and a valid
`srm_supplier_portal_user` association for the current user. This permission node is granted only to `SUPPLIER` and to `SUPER_ADMIN` (which owns the full permission tree by platform rules); having only the SUPER_ADMIN role does not satisfy the portal identity condition and cannot quote on behalf of a supplier:

- `GET /api/srm/portal/quotation/invitations`
- `GET /api/srm/portal/quotation/invitations/{rfqId}`
- `POST /api/srm/portal/quotation`

The invitation list uses `R<List<RfqInvitationVO>>`; a single item contains at least:

```json
{
  "rfqId": 1001,
  "rfqNo": "RFQ-202607-0001",
  "title": "Office Computer Procurement RFQ",
  "status": "SENT",
  "invitationStatus": "INVITED",
  "quotationDeadline": "2026-07-31 18:00:00",
  "currencyCode": "CNY",
  "invitedTime": "2026-07-21 10:00:00",
  "quotationId": 501,
  "quotationVersion": 2,
  "quotationStatus": "SUBMITTED",
  "totalAmount": "128000.0000",
  "validUntil": "2026-08-31 18:00:00"
}
```

The invitation detail returns an RFQ line snapshot on top of the above fields, and returns `currentQuotation` when a quotation already exists:

```json
{
  "rfqId": 1001,
  "rfqNo": "RFQ-202607-0001",
  "title": "Office Computer Procurement RFQ",
  "status": "SENT",
  "invitationStatus": "INVITED",
  "quotationDeadline": "2026-07-31 18:00:00",
  "currencyCode": "CNY",
  "lines": [
    {
      "rfqLineId": 10011,
      "materialCode": "IT-LAPTOP-001",
      "materialName": "Business Laptop",
      "unit": "unit",
      "quantity": "20.000000",
      "remark": "Includes three-year warranty"
    }
  ],
  "currentQuotation": null
}
```

Submit request:

```json
{
  "requestId": "f93b7342-9416-45bd-95f2-1e7e6045686d",
  "rfqId": 1001,
  "version": 0,
  "validUntil": "2026-08-31 18:00:00",
  "lines": [
    {
      "rfqLineId": 10011,
      "unitPrice": "6400.000000",
      "deliveryDays": 7,
      "remark": "Inspect after arrival"
    }
  ]
}
```

| Field | Type | Required | Constraint |
|------|------|------|------|
| `requestId` | String | Yes | Max 64; unique idempotency key within the tenant |
| `rfqId` | Long | Yes | Positive integer; a valid invitation for the current supplier must exist |
| `version` | Integer | Yes | 0 on first submit; on modification must equal the current quotation version |
| `validUntil` | LocalDateTime | Yes | `yyyy-MM-dd HH:mm:ss`; later than the current time and not earlier than quotationDeadline |
| `lines` | Array | Yes | Non-empty; the rfqLineId set must exactly match the RFQ line set of the invitation detail |
| `lines[].rfqLineId` | Long | Yes | Positive integer, no duplicates |
| `lines[].unitPrice` | Decimal String | Yes | Decimal string; `DECIMAL(19,6)`, greater than 0, at most 13 integer digits |
| `lines[].deliveryDays` | Integer | Yes | 0-3650 |
| `lines[].remark` | String | No | Max 500, plain text |

The request does not accept `tenantId/supplierId/rfqNo/material/quantity/currencyCode/lineAmount/totalAmount`. These fields are read respectively from the trusted identity headers, PortalUser and Procurement invitation detail, or computed by the server as
`unitPrice × quantity`; line amounts and the total amount are stored as `DECIMAL(19,4)`. The response is `R<QuotationVO>`, containing the quotation header, `version` and all line snapshots.

Idempotency and concurrency rules:

- `srm_quotation_request` permanently stores `(tenantId, requestId)`, the canonical request-body SHA-256, quotationId and targetVersion; a retry with the same requestId and requestHash returns the current quotation snapshot without writing the quotation or Outbox again.
- The same requestId bound to a different rfqId or request content returns business code 409.
- `(tenantId, rfqId, supplierId)` allows at most one non-deleted quotation; the first request uses the creation sentinel `version=0`, the first version is persisted and responded as `version=1`, and subsequent updates must carry the current version — an expired version or reusing 0 both return 409.
- Before submitting, the RFQ `status=SENT`, invitation `status IN (INVITED, QUOTED)`, deadline and line set must be re-validated; other RFQ statuses (`DRAFT/CLOSED/AWARDED/CANCELLED`) are rejected. When Procurement is unavailable, 503 is returned and offline writes are not allowed.

requestHash does not include requestId; at this stage the canonical input is
`rfqId/version/validUntil/lines`; lines are sorted ascending by `rfqLineId`, unit prices are normalized to 6 decimal places using non-scientific-notation strings, and remarks are trimmed with null/blank unified to null. The server must not hash the raw JSON bytes directly, to avoid misjudging the same intent due to field ordering.

### 15.6 SRM-Procurement Quotation Internal Contract

SRM calls Procurement when querying invitations:

- `GET /api/internal/procurement/rfq/invitations?supplierId={supplierId}`
- `GET /api/internal/procurement/rfq/{rfqId}/invitation?supplierId={supplierId}`

A list item contains at least `tenantId/rfqId/rfqNo/title/status/invitationStatus/supplierId/quotationDeadline/currencyCode/invitedTime`; the detail adds
`lines[{rfqLineId,materialCode,materialName,unit,quantity,remark}]`. SRM must use the supplierId obtained from the PortalUser association.

Procurement calls SRM during comparison/award:

```http
GET /api/internal/quotation/batch?tenantId=1&rfqId=1001
X-Internal-Token: <shared-token>
X-Tenant-Id: 1
```

The response is `R<List<QuotationVO>>`. `QuotationVO` contains
`id/rfqId/rfqNo/supplierId/supplierNameSnapshot/quotationTime/validUntil/totalAmount/currencyCode/status/version/lines`; a line contains
`id/rfqLineId/materialCode/materialName/unit/unitPrice/quantity/lineAmount/deliveryDays/remark`. Only non-deleted valid quotations of the specified tenant and RFQ whose supplier is currently still APPROVED are returned. In portal invitations, quotation responses and the internal batch, `totalAmount/unitPrice/quantity/lineAmount` always use JSON decimal strings; outputting JSON numbers is forbidden, to avoid losing high-precision JavaScript amounts or quantities.

The quotation header, details, `srm_quotation_request` and the `srm.quotation.submitted.v1` Outbox are committed in the same transaction. The event envelope follows
`eventId/eventType/occurredAt/tenantId/payload`, and the payload contains at least
`requestId/quotationId/quotationVersion/rfqId/rfqNo/supplierId/status/totalAmount/currencyCode/validUntil`. Procurement consumes idempotently by eventId Inbox and rejects overwriting a newer version with an older quotationVersion.

---

## 16. Workflow Cross-Service Contract

Workflow internal endpoints uniformly use the `X-Internal-Token` and `X-Tenant-Id` from §8.3, and responses continue to use the standard
`R<T>`. For detailed runtime mechanics, see [workflow.en.md](workflow.en.md#28-cross-service-internal-contract).

### 16.1 Idempotent Process Start

```http
POST /api/internal/workflow/process-instance/start
X-Internal-Token: <shared-token>
X-Tenant-Id: 1
Content-Type: application/json
```

Request:

```json
{
  "requestId": "6d2f4d1a-41d7-4f68-a60a-8a2e9425a703",
  "tenantId": 1,
  "modelVersionId": 42,
  "businessType": "PROCUREMENT_REQUISITION",
  "businessKey": "10001:1",
  "startUserId": 7,
  "startUserName": "buyer",
  "title": "Purchase Requisition PR-202607-0001",
  "variables": {
    "requisitionId": 10001,
    "approvalAttempt": 1,
    "materialCategory": "IT_EQUIPMENT",
    "totalAmount": "120000.0000",
    "requesterUnitId": 12
  }
}
```

| Field | Type | Required | Constraint |
|------|------|------|------|
| `requestId` | String | Yes | Non-empty, max 64; caller-generated idempotency key |
| `tenantId` | Long | Yes | Positive integer, must equal `X-Tenant-Id` |
| `modelVersionId` | Long | Yes | Positive integer; the model version must belong to the current tenant and have a `processDefinitionId` |
| `businessType` | String | Yes | Non-empty, max 100 |
| `businessKey` | String | Yes | Non-empty, max 255 |
| `startUserId` | Long | Yes | Positive integer |
| `startUserName` | String | No | Max 100 |
| `title` | String | No | Max 500; when empty, auto-generated as `{businessType}:{businessKey}` |
| `variables` | Object | No | Process variables; reserved fields `requestId/businessType/businessKey` are overridden by the service |

Response:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "requestId": "6d2f4d1a-41d7-4f68-a60a-8a2e9425a703",
    "businessType": "PROCUREMENT_REQUISITION",
    "businessKey": "10001:1",
    "processInstanceId": "22501",
    "replayed": false
  }
}
```

Idempotency is jointly guaranteed by two tenant-unique keys:

- `(tenantId, requestId)`: request-level idempotency; the same request ID must not be bound to different business.
- `(tenantId, businessType, businessKey)`: business-level idempotency; the same business must not start multiple processes.
- A retry of an already-succeeded same intent returns the original `processInstanceId` with `replayed = true`.
- In-progress, a request-ID conflict, or a changed `modelVersionId/startUserId` for the business key all return business code 409.

### 16.2 Task Handling Eligibility Validation

```http
POST /api/internal/workflow/task/assignment/validate
X-Internal-Token: <shared-token>
X-Tenant-Id: 1
Content-Type: application/json
```

Request:

```json
{
  "tenantId": 1,
  "taskId": "25017",
  "userId": 7,
  "businessType": "PROCUREMENT_REQUISITION",
  "businessKey": "10001:1"
}
```

| Field | Type | Required | Constraint |
|------|------|------|------|
| `tenantId` | Long | Yes | Positive integer, must equal `X-Tenant-Id` |
| `taskId` | String | Yes | Non-empty, max 64 |
| `userId` | Long | Yes | Positive integer |
| `businessType` | String | Yes | Non-empty, max 100 |
| `businessKey` | String | Yes | Non-empty, max 255 |

Response:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "valid": true,
    "processInstanceId": "22501",
    "assignmentType": "CANDIDATE",
    "message": "Validation passed"
  }
}
```

The service must simultaneously match the Flowable task tenant, the instance-extension-record tenant, and the `businessType + businessKey` business ownership,
and confirm that `userId` is the current `ASSIGNEE` or a `CANDIDATE` of an unclaimed task. `assignmentType` takes only
`ASSIGNEE`, `CANDIDATE`, `NONE`; when the task does not exist or any boundary mismatches, `valid = false` is returned.

### 16.3 Process Completed Event

| Attribute | Value |
|------|----|
| Event type | `workflow.process.completed.v1` |
| Producer | `omni-workflow` |
| Stream binding | `workflow-domain-out-0` |
| Destination | `workflow-domain-event` |

```json
{
  "eventId": "3f206832-9dc1-4422-870a-a286a979404d",
  "eventType": "workflow.process.completed.v1",
  "occurredAt": "2026-07-21 10:30:00",
  "tenantId": 1,
  "producer": "omni-workflow",
  "businessType": "PROCUREMENT_REQUISITION",
  "businessKey": "10001:1",
  "processInstanceId": "22501",
  "result": "APPROVED",
  "completedTime": "2026-07-21 10:30:00"
}
```

| Field | Type | Description |
|------|------|------|
| `eventId` | String(UUID) | Event ID, Outbox `msgKey`, consumption idempotency key |
| `eventType` | String | Fixed as `workflow.process.completed.v1` |
| `occurredAt` | LocalDateTime | Time the event record was produced, format `yyyy-MM-dd HH:mm:ss` |
| `tenantId` | Long | Tenant ID |
| `producer` | String | Fixed as `omni-workflow` |
| `businessType` | String | Caller business type |
| `businessKey` | String | Caller business key |
| `processInstanceId` | String | Flowable process instance ID |
| `result` | Enum | `APPROVED`, `REJECTED`, `CANCELLED` |
| `completedTime` | LocalDateTime | Actual process completion or termination time |

The conditional update of the completion status and `completionEventId` and the PENDING Outbox record are committed in the same local transaction;
`completion_event_id IS NULL` is the database latch ensuring only one logical completion event is generated per process instance.
After commit, the reliable message relay delivers and retries asynchronously with at-least-once semantics, so consumers must handle idempotently by `eventId`.

### 16.4 Query Published Model Version

```http
GET /api/internal/workflow/model-version/{modelVersionId}
X-Internal-Token: <shared-token>
X-Tenant-Id: 1
```

Success response:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 301,
    "modelId": 30,
    "modelKey": "asset-transfer-approval",
    "category": "ASSET_TRANSFER",
    "version": 2,
    "processDefinitionId": "asset-transfer-approval:2:8801",
    "status": "PUBLISHED"
  }
}
```

The endpoint returns only models and versions still available within the requested tenant. `modelKey` is the model identifier, unique within the tenant and consistent with the BPMN process id;
`category` is the cross-service business classification. Besides validating `PUBLISHED` and a non-empty
`processDefinitionId`, the caller can also bind stable business types to `category`. Asset strictly requires the transfer model category to be
`ASSET_TRANSFER` and the disposal model category to be `ASSET_DISPOSAL`, without cross-reuse or use of other business models;
Workflow performs the same validation again before actually creating the asset approval instance, closing the model-change window after pre-validation.

### 16.5 Approval-Rule Read-Only Model Aggregation

| Method | Path | Constraint |
|---|---|---|
| GET | `/api/internal/workflow/model-versions/published?category=purchase` | Returns only records of the current tenant with an exact category match, a valid main model, and a currentPublishedVersionId pointing to a deployable published version |
| POST | `/api/internal/workflow/model-versions/resolve` | body is `{ "modelVersionIds": [1, 2] }`, 1-200 positive integers per call, returned in request order |
| GET | `/api/internal/workflow/model-version/{id}/preview` | Returns a safe approval diagram, not BPMN XML or designerJson |

The batch-resolved `availability` takes only `AVAILABLE/NOT_CURRENT/UNAVAILABLE/MODEL_ARCHIVED/NOT_FOUND`.
The safe preview contains only nodes, desensitized edges and model metadata; a UserTask may include `roleCode/approvalMode`, and a conditional expression returns only
"condition configured (content hidden)". When there are branches or cycles, `linearSummary=null`, and the frontend must indicate that the actual path is determined by business data,
and must not resolve the current organization into future actual approvers.

---

## 17. Procurement MVP Contract

### 17.1 General Boundaries

- The external Base path is `/api/procurement`; the Gateway keeps the full path and does not use `StripPrefix`.
- All requests use the `X-User-Id`, `X-Tenant-Id` and permission headers injected by the Gateway; business tables are constrained by both TenantLine and permission-aware DataScope.
- Quantity and unit price use `DECIMAL(19,6)`, amounts use `DECIMAL(19,4)`; all quantities, unit prices and amounts in responses are JSON strings, and the frontend must not compute business amounts with JavaScript `number`.
- Update bodies and delete queries must carry `version`; a version conflict returns business code 409.
- Internal endpoints uniformly use `/api/internal/procurement/**`, require `X-Internal-Token` and `X-Tenant-Id`, and must not be exposed through the Gateway.

### 17.2 Materials and Categories

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/procurement/material/category/list` | `procurement:material:list` | Returns a category tree of at most two levels |
| POST | `/api/procurement/material/category` | `procurement:material:create` | Create a category; `categoryCode` cannot be modified after creation |
| PUT | `/api/procurement/material/category/{id}` | `procurement:material:update` | body carries `version` |
| DELETE | `/api/procurement/material/category/{id}?version={version}` | `procurement:material:delete` | Returns 409 when sub-categories or materials exist |
| GET | `/api/procurement/material/list` | `procurement:material:list` | `keyword/categoryId/status/assetManaged/page/size` |
| GET | `/api/procurement/material/{id}` | `procurement:material:list` | Query material detail |
| POST | `/api/procurement/material` | `procurement:material:create` | Create a material; `materialCode` cannot be modified after creation |
| PUT | `/api/procurement/material/{id}` | `procurement:material:update` | body carries `version` |
| DELETE | `/api/procurement/material/{id}?version={version}` | `procurement:material:delete` | Logical delete |

When `assetManaged=true`, `unit` allows only `EA/PCS/UNIT/SET`; a requisition may reference only materials with status `ACTIVE` and an enabled category.

### 17.3 Approval Routes

| Method | Path | Permission |
|---|---|---|
| GET | `/api/procurement/approval-route/list` | `procurement:approval-route:list` |
| GET | `/api/procurement/approval-route/workflow-options` | `procurement:approval-route:list` |
| POST | `/api/procurement/approval-route/match-preview` | `procurement:approval-route:list` |
| GET | `/api/procurement/approval-route/coverage` | `procurement:approval-route:list` |
| GET | `/api/procurement/approval-route/impact?routeId={id}` | `procurement:approval-route:list` |
| POST | `/api/procurement/approval-route` | `procurement:approval-route:create` |
| PUT | `/api/procurement/approval-route/{id}` | `procurement:approval-route:update` |
| DELETE | `/api/procurement/approval-route/{id}?version={version}` | `procurement:approval-route:delete` |

The new-UI create request contains `routeName/categoryCode/minAmount/maxAmount/modelVersionId/status`. `routeCode` is generated by the server as
`APR-{ULID}` and cannot be modified after creation; within one compatibility release cycle, an old create request may pass `routeCode` as a fallback for a missing
`routeName`, and the server logs a deprecation warning. `priority` is retained only for compatible advanced callers; when a new creation omits it, the server takes
the maximum value of the same category plus 10 within the tenant-config lock, and the frontend does not display this field.

`minAmount/maxAmount` must use JSON decimal strings (except `maxAmount=null`); a JSON number returns 400.
The active interval uses `minAmount <= amount < maxAmount`, and `maxAmount=null` means no upper bound. Active intervals of the same category must not overlap,
and the write transaction serializes validation with the tenant-config row lock. When creating or changing `modelVersionId`, only the current published version of the current tenant with `category=purchase` and
`availability=AVAILABLE` is allowed; a legacy non-purchase reference is marked `LEGACY_CATEGORY` in the list and cannot be silently migrated.

The `match-preview` request is `{ "categoryCode": "IT_DEVICE", "totalAmount": "10000.0000" }`, and the response
`outcome` takes only `MATCHED/NO_MATCH/AMBIGUOUS/WORKFLOW_UNAVAILABLE`. It calls
`ApprovalRouteResolver.evaluate` together with requisition submission; the submission path converts a non-MATCHED result into the original 409, so the browser does not compute the hit.
`coverage` outputs `COVERED/GAP/AMBIGUOUS` half-open segments from 0 to infinity for all enabled categories, and marks the default fallback,
invalid models and Workflow unavailability. `impact` reuses the same algorithm after excluding the specified rule in memory, without modifying the database.

### 17.4 Requisitions

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/procurement/requisition/list` | `procurement:requisition:list` | `keyword/status/categoryCode/page/size` |
| GET | `/api/procurement/requisition/{id}` | `procurement:requisition:list` | Ordinary detail is still constrained by the requester DataScope |
| GET | `/api/procurement/requisition/{id}/approval-view?taskId={taskId}` | `procurement:requisition:approve` | Workflow first validates that the task belongs to the current user and this requisition |
| POST | `/api/procurement/requisition` | `procurement:requisition:create` | Create a DRAFT |
| PUT | `/api/procurement/requisition/{id}` | `procurement:requisition:update` | DRAFT/REJECTED only; a REJECTED update returns to DRAFT |
| DELETE | `/api/procurement/requisition/{id}?version={version}` | `procurement:requisition:delete` | DRAFT only |
| POST | `/api/procurement/requisition/{id}/submit` | `procurement:requisition:submit` | body `{ "version": 0 }` |
| POST | `/api/procurement/requisition/{id}/retry-start` | `procurement:requisition:submit` | `SUBMITTED + FAILED` only, reusing the original Workflow idempotent snapshot |
| POST | `/api/procurement/requisition/{id}/cancel` | `procurement:requisition:cancel` | DRAFT or `SUBMITTED + FAILED` only |

Create/update request example:

```json
{
  "title": "R&D Laptop Procurement",
  "reason": "New employee onboarding",
  "lines": [
    {
      "materialId": 101,
      "quantity": "2.000000",
      "estimatedUnitPrice": "8500.000000",
      "remark": "16GB memory or above"
    }
  ]
}
```

`lines[].quantity` and `lines[].estimatedUnitPrice` accept only JSON decimal strings; even if the value is within the JavaScript safe range, a JSON number returns 400.

MVP requires all lines to belong to the same category. In the submit transaction the service batch re-checks active materials and categories, refreshes the material code, name, category and unit snapshots, and recomputes line amounts and the total amount; the client cannot pass the total amount or `modelVersionId`. Each new submission increments `approvalAttempt + 1`, and the Workflow `businessKey={requisitionId}:{approvalAttempt}`; after an uncertain start failure, a retry must reuse the persisted `requestId/businessKey/modelVersionId`.

The Workflow completion event enters `proc_event_inbox` by `eventId`. The current-round event updates the requisition only when tenant, businessKey, processInstanceId and the `APPROVING` status all match; an old-round event is idempotently ignored, an event earlier than the local start confirmation rolls back the Inbox and triggers a message retry, and the same eventId bound to a different full payload returns 409.

### 17.5 RFQ, Comparison and Award

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/procurement/rfq/supplier-options` | `procurement:rfq:create` or `procurement:rfq:list` | Query the current tenant's SRM qualified supplier options |
| GET | `/api/procurement/rfq/list` | `procurement:rfq:list` | `keyword/status/deadlineFrom/deadlineTo/page/size` |
| GET | `/api/procurement/rfq/{id}` | `procurement:rfq:list` | Query the RFQ, lines and invitation snapshot |
| GET | `/api/procurement/rfq/{id}/comparison` | `procurement:rfq:list` | Re-read the current valid quotations and full line snapshots from SRM |
| POST | `/api/procurement/rfq` | `procurement:rfq:create` | Create a DRAFT from an approved requisition |
| PUT | `/api/procurement/rfq/{id}` | `procurement:rfq:update` | DRAFT only; body carries `version` |
| DELETE | `/api/procurement/rfq/{id}?version={version}` | `procurement:rfq:delete` | DRAFT only |
| POST | `/api/procurement/rfq/{id}/send` | `procurement:rfq:send` | body `{ "version": 0 }`, publish to invited suppliers |
| POST | `/api/procurement/rfq/{id}/award` | `procurement:rfq:award` | Lock the quotation version and atomically generate a purchase order |
| POST | `/api/procurement/rfq/{id}/cancel` | `procurement:rfq:cancel` | body `{ "version": 0 }`; DRAFT/SENT only |

Create and update requests contain `requisitionId/title/quotationDeadline/supplierIds`; the time format on update is unified as
`yyyy-MM-dd HH:mm:ss`. Only a `SENT` RFQ can be compared and awarded. Invitation statuses are
`INVITED/QUOTED/EXPIRED/AWARDED/REJECTED`; after award the winning invitation becomes `AWARDED` and the rest become
`REJECTED`, and these terminal states are only for supplier-portal history viewing and cannot continue quoting.

Award request example:

```json
{
  "rfqVersion": 2,
  "quotationId": 501,
  "quotationVersion": 3,
  "title": "R&D Laptop Purchase Order",
  "expectedDeliveryDate": "2026-08-15",
  "deliveryAddress": "No. 1 Example Road, Pudong New Area, Shanghai",
  "contactName": "Zhang San",
  "contactPhone": "13800000000"
}
```

In the same transaction the server locks the RFQ and invitations, then re-checks from SRM the current version, tenant, supplier,
currency, validity and full line set of `quotationId`; a mismatch of either `rfqVersion` or `quotationVersion` returns 409. The success response is
`{ "rfq": ..., "purchaseOrder": ... }`, and an immutable quotation amount/delivery snapshot is saved; subsequent SRM quotation changes must not alter
an existing award or purchase order. Quantities, unit prices and amounts in the quotation comparison response are all JSON decimal strings.

### 17.6 Purchase Orders

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/procurement/purchase-order/list` | `procurement:purchase-order:list` | `keyword/status/expectedDeliveryFrom/expectedDeliveryTo/page/size` |
| GET | `/api/procurement/purchase-order/{id}` | `procurement:purchase-order:list` | Query the order and immutable quotation line snapshot |
| PUT | `/api/procurement/purchase-order/{id}` | `procurement:purchase-order:update` | DRAFT only, may modify the title and delivery info |
| DELETE | `/api/procurement/purchase-order/{id}?version={version}` | `procurement:purchase-order:delete` | DRAFT only |
| POST | `/api/procurement/purchase-order/{id}/send` | `procurement:purchase-order:send` | DRAFT → SENT, body carries `version` |
| POST | `/api/procurement/purchase-order/{id}/confirm` | `procurement:purchase-order:confirm` | SENT → CONFIRMED, body carries `version` |
| POST | `/api/procurement/purchase-order/{id}/cancel` | `procurement:purchase-order:cancel` | Cancel before any goods receipt, body carries `version` |

The external API provides no purchase-order creation endpoint; an MVP order can only be generated by the RFQ award transaction, and the client cannot forge the supplier, quotation or
order lines. Statuses are `DRAFT/SENT/CONFIRMED/PARTIAL_RECEIVED/RECEIVED/CLOSED/CANCELLED`.
Addresses, contacts and phone numbers in the list are desensitized by default, and the detail is still constrained by the owner DataScope; quantities, unit prices, line amounts and the total amount
are always returned as JSON decimal strings.

### 17.7 Goods Receipt and Quality Check

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/procurement/goods-receipt/list` | `procurement:goods-receipt:list` | `keyword/status/receiveTimeFrom/receiveTimeTo/page/size` |
| GET | `/api/procurement/goods-receipt/{id}` | `procurement:goods-receipt:list` | Query goods-receipt detail |
| POST | `/api/procurement/goods-receipt` | `procurement:goods-receipt:create` | Create a DRAFT for a CONFIRMED/PARTIAL_RECEIVED order |
| POST | `/api/procurement/goods-receipt/{id}/confirm` | `procurement:goods-receipt:confirm` | body `{ "version": 0 }`, confirm receipt and update the order's cumulative status |
| POST | `/api/procurement/goods-receipt/{id}/quality-result` | `procurement:goods-receipt:confirm` | Change only a confirmed-receipt PENDING line to PASS/FAIL |

The create request's `receiveTime` uses `yyyy-MM-dd HH:mm:ss`, and each line contains
`poLineId/receivedQuantity/qualityStatus/remark`. `receivedQuantity` accepts only JSON decimal strings; a JSON
number returns 400. Creating a DRAFT does not occupy the received quantity; the confirm transaction locks the purchase order and re-accumulates validation with all CONFIRMED receipt lines, forbidding concurrent over-receipt. Partial and full receipt advance the order to `PARTIAL_RECEIVED` and `RECEIVED` respectively.

Only lines with `qualityStatus=PASS`, material `assetManaged=true` and a positive integer quantity enter the asset candidates. On confirmation,
`procurement.goods-receipt.confirmed.v1` is published; when a PENDING line first becomes PASS afterwards,
`procurement.goods-receipt.quality-passed.v1` is published, and the newly passed lines of the same batch share one event ID. Historical compensation reads use the
`X-Internal-Token`-protected
`GET /api/internal/procurement/goods-receipt/asset-candidates?tenantId={tenantId}&afterId={id}&size={size}`;
both real-time consumption and backfill are idempotent by `tenantId + goodsReceiptLineId + unitSequence`.

The two events and the historical candidates must carry the goods-receipt management ownership `ownerUserId/ownerUnitId`, which Asset inherits as the new asset's
management ownership; when a field is missing or not a positive integer, it fails closed. The event line's `receivedQuantity/unitPrice/totalPrice`
continue to use JSON decimal strings, and only the unit-level count `assetQuantity` uses a positive integer. The Asset real-time consumer also establishes an Inbox idempotency latch by
`consumerName + eventId`; when the same event ID or source unit is bound to a different full business intent,
a conflict is returned and an already-created asset must not be overwritten.

### 17.8 Procurement Overview

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/procurement/overview/summary` | `procurement:overview:list` | Procurement to-dos, order statuses and committed amounts by currency |
| GET | `/api/procurement/overview/spend-analysis?dimension={dimension}&limit={limit}` | `procurement:overview:list` | Aggregate confirmed procurement spend by dimension and currency |

`dimension` is required and allows only `CATEGORY`, `SUPPLIER`, `DEPARTMENT`; `limit` defaults to 20, range 1-100.
DEPARTMENT means the purchase order's responsible department `ownerUnitId`. Spend counts only
`CONFIRMED/PARTIAL_RECEIVED/RECEIVED/CLOSED` purchase orders, excluding draft, sent-only or cancelled orders.

Summary response example:

```json
{
  "pendingApprovalRequisitionCount": 3,
  "waitingQuotationRfqCount": 2,
  "purchaseOrderStatusCounts": [
    { "status": "DRAFT", "count": 1 },
    { "status": "SENT", "count": 2 },
    { "status": "CONFIRMED", "count": 4 },
    { "status": "PARTIAL_RECEIVED", "count": 1 },
    { "status": "RECEIVED", "count": 5 },
    { "status": "CLOSED", "count": 8 },
    { "status": "CANCELLED", "count": 1 }
  ],
  "draftGoodsReceiptCount": 2,
  "committedAmountsByCurrency": [
    { "currencyCode": "CNY", "amount": "120000.0000" },
    { "currencyCode": "USD", "amount": "8500.0000" }
  ]
}
```

A spend-analysis item contains `dimension/dimensionKey/dimensionName/currencyCode/amount`, sorted first by
`currencyCode` ascending, then by `amount` descending within the same currency. `amount` is always a JSON decimal string;
different currencies must remain separate records, and neither the server nor the frontend may add them directly. Each aggregate SQL of the summary directly hits
the corresponding requisition, RFQ, purchase-order or goods-receipt aggregate root, and applies the same requester/owner DataScope and
TenantLine as an ordinary list; spend analysis uses the purchase-order owner scope and must not bypass data permissions via the aggregate query.

---

## 18. Asset MVP Contract

### 18.1 General Boundaries

- The external Base path is `/api/asset`; the Gateway keeps the full path and does not use `StripPrefix`.
- External requests use the `X-User-Id`, `X-Tenant-Id`, `X-Username`, role and permission headers injected by the Gateway. Business tables are always constrained by TenantLine, and management lists, sub-resources and the overview are additionally constrained by permission-aware DataScope.
- Management lists filter by `owner_user_id/owner_unit_id`; `GET /api/asset/asset/my` always queries by `current_user_id` and cannot be broadened because the same user holds a management role.
- Write commands carry `version` and perform optimistic-lock validation; a version or active-operation-occupancy mismatch returns a business conflict.
- Asset original value, residual value and aggregate amounts use `DECIMAL(18,2)`, and both requests and responses use JSON decimal strings; a JSON number returns 400. Currency uses a three-letter ISO 4217 code.
- Asset statuses are `IN_STOCK/ALLOCATED/IN_USE/MAINTENANCE/TRANSFER/DISPOSAL_PENDING/DISPOSED/SCRAPPED`.
- Internal endpoints use `/api/internal/asset/**`, must carry `X-Internal-Token` and `X-Tenant-Id`, and are explicitly blocked by the Gateway.

### 18.2 Asset Ledger and Commands

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/asset/asset/list` | `asset:asset:list` | `keyword/status/categoryCode/ownerUnitId/locationCode/page/size`, queried by management ownership |
| GET | `/api/asset/asset/my` | `asset:asset:self` | `keyword/status/categoryCode/page/size`, always queries the current user |
| GET | `/api/asset/asset/{id}` | `asset:asset:list` | Query asset detail within the management scope |
| GET | `/api/asset/asset/{id}/history` | `asset:asset:list` | `page/size`, query the immutable status history |
| POST | `/api/asset/asset` | `asset:asset:create` | Manually create an `IN_STOCK` asset |
| PUT | `/api/asset/asset/{id}` | `asset:asset:update` | Update basic info; cannot directly update status, user or location |
| DELETE | `/api/asset/asset/{id}?version={version}` | `asset:asset:delete` | Delete only a manual in-stock asset with no business action yet |
| POST | `/api/asset/asset/{id}/allocate` | `asset:asset:allocate` | `IN_STOCK → ALLOCATED` |
| POST | `/api/asset/asset/{id}/accept` | `asset:asset:accept` | The current user performs `ALLOCATED → IN_USE` |
| POST | `/api/asset/asset/{id}/return` | `asset:asset:return` | The current user returns it, restoring `IN_STOCK` and clearing usage ownership |
| POST | `/api/asset/asset/{id}/maintenance/start` | `asset:asset:maintenance` | `IN_USE → MAINTENANCE` |
| POST | `/api/asset/asset/{id}/maintenance/complete` | `asset:asset:maintenance` | `MAINTENANCE → IN_USE` |
| GET | `/api/asset/options/users` | one of the asset ledger/allocation/transfer/disposal permissions | Enabled user candidates of the current tenant, returning the primary org, without phone/email |
| GET | `/api/asset/options/suppliers` | `asset:asset:create/update` | Approved supplier keyword candidates of the current tenant |
| GET | `/api/asset/options/transfer-assets` | `asset:transfer:create` | Assets within the current DataScope with no active occupancy and a transferable status |
| GET | `/api/asset/options/disposal-assets` | `asset:disposal:create` | Assets within the current DataScope with no active occupancy and a disposable status |

The manual create request contains
`name/categoryCode/specification/brand/model/supplierId/supplierNameSnapshot/purchaseDate/purchaseAmount/currencyCode/locationCode/warrantyExpiryDate/expectedLifeYears/remark/ownerUserId/ownerUnitId`.
`purchaseAmount` may default to `null`, and when non-null must be a JSON decimal string; `currencyCode`, `ownerUserId` and `ownerUnitId` are required. The update request adds a required `version` but does not accept `locationCode`.

The allocation request is:

```json
{
  "version": 0,
  "targetUserId": 101,
  "targetUnitId": 12,
  "remark": "R&D equipment requisition"
}
```

Accept, return and maintenance commands use `{ "version": 0, "remark": "..." }`. Besides permission validation, `accept/return` must also verify that the asset's
`current_user_id` equals the current user; the management scope cannot replace this per-row ownership validation.

### 18.3 Procurement Receipt Linkage

Asset consumes `procurement.goods-receipt.confirmed.v1` and
`procurement.goods-receipt.quality-passed.v1`. The event envelope and receipt-line fields are authoritative per 17.7; Asset handles only
unit-level assets with `qualityStatus=PASS && assetManaged=true && assetQuantity>0`.

- Real-time consumption writes `ast_inbox_event` by `consumerName + eventId`, and validates that the same event ID cannot be bound to a different full business intent.
- Real-time consumption and historical backfill together establish a source unique key by
  `tenantId + goodsReceiptLineId + unitSequence`, so no entry point can create a duplicate asset.
- A new asset inherits the goods-receipt management ownership `ownerUserId/ownerUnitId`, and saves the PO, GR, supplier, material, category, currency and amount snapshots.
- The internal controlled compensation endpoint is
  `POST /api/internal/asset/procurement/backfill?tenantId={tenantId}&afterId={id}&size={size}`; the request header
  `X-Tenant-Id` must exactly match the query `tenantId`. `size` ranges 1-100, and the response returns this page's processing result and the next cursor.

### 18.4 Transfer

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/asset/transfer/list` | `asset:transfer:list` | `keyword/status/page/size`, inherits the management DataScope through the associated asset |
| GET | `/api/asset/transfer/{id}` | `asset:transfer:list` | Query transfer detail |
| GET | `/api/asset/transfer/{id}/approval-view?taskId={taskId}` | `asset:transfer:approve` | After Workflow validates the current task assignment, read the read-only approval view by tenant |
| POST | `/api/asset/transfer` | `asset:transfer:create` | Create the request, atomically occupy the asset and start Workflow |
| POST | `/api/asset/transfer/{id}/retry-start` | `asset:transfer:retry` | For a `PENDING_APPROVAL + PENDING` or `START_FAILED + FAILED` request, start by reusing the original idempotent snapshot |
| POST | `/api/asset/transfer/{id}/cancel` | `asset:transfer:cancel` | Cancel only an explicitly failed `START_FAILED + FAILED` request and restore the asset |
| POST | `/api/asset/transfer/{id}/complete` | `asset:transfer:complete` | After approval, complete the handover; the asset enters `IN_USE` |

The create request is:

```json
{
  "assetId": 10001,
  "toUserId": 102,
  "toUnitId": 12,
  "toLocation": "SH-A-03-021",
  "reason": "Position adjustment"
}
```

Creation is allowed only when the asset is in `IN_STOCK/ALLOCATED/IN_USE` with no active operation. The request saves the original usage ownership, location and
`previousAssetStatus`. The server auto-resolves a published and startable Workflow model version by the current tenant and `category=ASSET_TRANSFER` and persists an idempotent snapshot; the client must not provide or select `modelVersionId`.
The `retry-start/cancel/complete` bodies are all `{ "version": 0 }`.

### 18.5 Discard and Scrap Disposal

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/asset/disposal/list` | `asset:disposal:list` | `keyword/disposalType/status/page/size`, inherits the management DataScope through the associated asset |
| GET | `/api/asset/disposal/{id}` | `asset:disposal:list` | Query disposal detail |
| GET | `/api/asset/disposal/{id}/approval-view?taskId={taskId}` | `asset:disposal:approve` | After Workflow validates the current task assignment, read the read-only approval view by tenant |
| POST | `/api/asset/disposal` | `asset:disposal:create` | Create the request, atomically occupy the asset and start Workflow |
| POST | `/api/asset/disposal/{id}/retry-start` | `asset:disposal:retry` | For a `PENDING_APPROVAL + PENDING` or `START_FAILED + FAILED` request, start by reusing the original idempotent snapshot |
| POST | `/api/asset/disposal/{id}/cancel` | `asset:disposal:cancel` | Cancel only an explicitly failed `START_FAILED + FAILED` request and restore the asset |
| POST | `/api/asset/disposal/{id}/complete` | `asset:disposal:complete` | After approval, complete the physical disposal |

The create request contains
`assetId/disposalType/reason/residualValue/disposalMethod`; `disposalType` allows only
`DISCARD/SCRAP`, and `residualValue` when non-null must be a JSON decimal string. The request uses
`ASSET_DISPOSAL + businessKey` to start the server-auto-resolved `category=ASSET_DISPOSAL` Workflow model,
and the client must not provide `modelVersionId`. After completing
`DISCARD` the asset enters `DISPOSED`, and after completing `SCRAP`
it enters `SCRAPPED`; both are irreversible terminal states.

### 18.6 Workflow Completion Event and Operation Status

Transfer and disposal statuses are unified as
`PENDING_APPROVAL/START_FAILED/APPROVED/REJECTED/COMPLETED/CANCELLED`, and the Workflow start status is
`PENDING/STARTED/FAILED`.

Workflow publishes the approval result with `workflow.process.completed.v1` from 16.3. The Asset consumer must:

1. Validate `eventId/eventType/producer/tenantId/businessType/businessKey/processInstanceId/result`;
2. Establish an Inbox idempotency latch by `consumerName + eventId`, and reject the same event ID bound to a different full payload;
3. Accept only events that exactly match the current request, the confirmed process instance and the active status;
4. For an event arriving earlier than the local start confirmation, roll back the Inbox and trigger a message retry;
5. `APPROVED` only advances the request to pending business completion; `REJECTED/CANCELLED` restore
   `previousAssetStatus`, close the request and clear the asset `active_operation_*` in the same transaction.

The Workflow start call happens after the local create transaction commits. A network exception, a 409/other non-200 response whose result cannot be determined, or a local confirmation failure
may all correspond to a remotely accepted request, so keep `PENDING_APPROVAL + PENDING`, forbid local cancellation, and allow a retry with the same idempotent snapshot.
A Workflow business response 404 means the model version is no longer startable and the remote transaction did not create an instance, so the request enters
`START_FAILED + FAILED`; this explicit failure state allows a retry or local cancellation.
Both kinds of retry must reuse the idempotent snapshot: `businessType` is fixedly derived from the transfer/disposal aggregate type, and the persisted
`requestId/businessKey/modelVersionId/workflowStartUserId/workflowStartUserName` are reused,
including continuing to use the original initiator identity when a different user performs the retry, forbidding creation of a second process instance.

### 18.7 Asset Overview

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/asset/overview/summary` | `asset:overview:list` | Count per status and original value by currency within the management scope |
| GET | `/api/asset/overview/distribution?dimension={dimension}&limit={limit}` | `asset:overview:list` | Aggregate by status, category, management department or location |

`dimension` is required and allows only `STATUS/CATEGORY/DEPARTMENT/LOCATION`; `limit` defaults to 20, range 1-100.
All aggregate SQL must apply the same owner DataScope and TenantLine as the management ledger. Amounts remain separate records per currency and are output as decimal strings; different currencies must not be added directly.
