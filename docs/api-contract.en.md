# API Contract

> This document maintains the current wire format for responses, fields, routes, identity headers and cross-service messages. Source, configuration and Controller/DTO definitions determine actual behavior. Example IDs, dates, addresses and states illustrate structure; they are not current environment data or acceptance results.
> Domain fact/design documents own state machines and implementation boundaries. See the [Wiki](wiki/index.en.md) and [authentication guide](guides/authentication.en.md) for configuration, login, browser actions and recovery. Local protocol authorization and external social login are separate flows.

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

Regular business controllers return `R<T>`; pagination uses `R<PageResult<T>>`. OAuth2/OIDC protocol endpoints, social-login redirects, Actuator and responses produced by filters/frameworks do not all share this envelope.

```json
{"code":200,"message":"success","data":{"id":1}}
```

```json
{"code":500,"message":"操作失败","data":null}
```

```json
{"code":400,"message":"username: 用户名不能为空","data":null}
```

`R.ok(data)` uses business code 200 and message `success`; `R.ok()` and failure responses have null data. `R.fail(message)` defaults to business code 500; its explicit overload uses the supplied code. JSON property order is not part of the contract.

The sole backend type is [R.java](../omni-backend/omni-common-core/src/main/java/com/omni/common/core/result/R.java). The sole frontend declaration is `ApiResponse<T>` in [src/types/api.ts](../omni-frontend/src/types/api.ts). Declaring `data: T` does not remove runtime nulls. This document does not duplicate compilable types; consumers must handle each endpoint's actual null behavior.

---

## 2. Error Code Reference

### 2.1 System Error Codes

Read HTTP status and `R.code` separately:

| HTTP status | Business code | Current mapping |
|---|---|---|
| 200 | 200 | Regular business success |
| 200 | Code supplied at the throw site; default 500 | `BusinessException`, including business 400/403/404/409/503 |
| 400 | 400 | Bean validation, binding, missing query parameters or type mismatches handled by the applicable advice |
| 403 | 403 | `AccessDeniedException/AuthorizationDeniedException` |
| 404 / 405 / 415 | Same as HTTP status | Resource, method or media-type errors handled by advice |
| 500 | 500 | Exceptions without a specific handler |

JSON-read error mapping differs by service. SRM/Procurement advice maps `HttpMessageNotReadableException` to 400; Asset's current general handler falls back to 500. Controlled MockMvc observed 500 for an Asset amount sent as a JSON number or invalid decimal string. The request remains invalid; this does not establish one status for all services.

### 2.2 Business Error Codes

The current throw site determines the business code; message text does not. Some CAPTCHA, account and tenant failures use default 500. Business 400, 403, 404, 409 and 503 can appear inside HTTP 200 responses. Frontend display categories for 410, 423, 429 or 502 do not establish that every endpoint implements those branches.

### 2.3 Gateway Error Codes

| HTTP status / code | Condition | Current message |
|---|---|---|
| 401 / 401 | Missing Bearer authentication | 请先登录 |
| 401 / 401 | Expired JWT | 登录已过期，请重新登录 |
| 401 / 401 | Other JWT/security validation failure, including a revocation match | 认证失败，请重新登录 |
| 503 / 503 | Identity-signing key missing/weak outside test configuration, or signing failure | 网关身份签名未就绪，请联系管理员 |

These are `AuthFilter` branches. Other filters, rate limiters, downstream services and framework failures have their own mappings. Messages are diagnostic values, not frontend authentication-state signals.

### 2.4 Social Login Error Codes

Callback values `user_denied/invalid_callback/social_login_failed` are query values in a 302 Location, not `R.code`. Conditions and destinations are defined in section 10.

### 2.5 Frontend Error Handling Flow

[request.ts](../omni-frontend/src/api/request.ts) and [error-display.ts](../omni-frontend/src/utils/error-display.ts) classify HTTP status, business code and Axios transport codes when no response exists. HTTP 401/403 takes precedence over the body business code; otherwise use the business code, then HTTP status.

- Business failures throw `ApiError` carrying `businessCode/httpStatus/traceId/i18nKey/displayMessage`. HTTP failures retain and enhance AxiosError; the two paths do not use one common constructor.
- Raw server and Axios messages are not displayed directly. The UI uses code-based localized safe text; field hints for 400 only accept validated ASCII field names.
- For 401, credential endpoints preserve the session and input. Other requests distinguish unauthenticated from expired sessions using session presence; both have `shouldLogout=true`, followed by safe session cleanup and login navigation. Credential matching includes `/auth/login`, `/auth/session-login`, `/auth/register`, `/auth/social-login` and `/srm/portal/auth/login`. This frontend classification is not the Gateway allowlist.
- 403 preserves the session. 400/409/5xx, network failures and timeouts use their respective localized text, without guessing meaning from technical messages. The caller refreshes conflicting data and decides whether to retry.

---

## 3. Pagination Contract

The sole backend type is [PageResult.java](../omni-backend/omni-common-core/src/main/java/com/omni/common/core/result/PageResult.java); frontend types live in [src/types/api.ts](../omni-frontend/src/types/api.ts). Response data contains:

| Field | Meaning |
|---|---|
| `records` | Current-page records |
| `total` | Total record count |
| `size` | Page size |
| `current` | Current page number |
| `pages` | `(total + size - 1) / size`, calculated by the constructor |

Queries commonly use `page=1&size=10`. Ranges, defaults, filters and caps belong to each Controller/query DTO; example defaults are not universal endpoint limits. The constructor preserves supplied records and provides neither global null→[] conversion nor pagination validation. Callers must supply a valid size and handle empty records according to the endpoint.

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

**Gateway path prefix**: regular frontend business-resource requests use `/api/<service>/<resource>`, for example /api/auth/user/list. Business routes for Auth, Base, Workflow, CRM, SRM, Procurement and Asset retain the full path without StripPrefix. Protocol and internal paths follow their endpoint definitions.

The table gives business-resource naming conventions. Action commands, OAuth2/OIDC and internal APIs use their actual Controller routes; these examples do not create unimplemented endpoints.

---

## 5. Gateway Route Configuration

### 5.1 Local Development Routes

Current `spring.cloud.gateway.server.webflux.routes` in [Gateway application.yml](../omni-backend/omni-gateway/src/main/resources/application.yml) defines 12 explicit routes; discovery locator routing is disabled:

| Route ID | Path match | Target |
|---|---|---|
| `block-internal-api` | `/internal/**,/api/internal/**` | `no://op` |
| `block-internal-api-services` | `/omni-auth/internal/**,/omni-auth/api/internal/**,/omni-base/internal/**,/omni-base/api/internal/**,/omni-workflow/internal/**,/omni-workflow/api/internal/**,/omni-crm/internal/**,/omni-crm/api/internal/**,/omni-srm/internal/**,/omni-srm/api/internal/**,/omni-procurement/internal/**,/omni-procurement/api/internal/**,/omni-asset/internal/**,/omni-asset/api/internal/**,/omni-gateway/internal/**` | `no://op` |
| `omni-auth-oauth2` | `/oauth2/**` | `lb://omni-auth` |
| `omni-auth-wellknown` | `/.well-known/**` | `lb://omni-auth` |
| `omni-auth` | `/api/auth/**` | `lb://omni-auth` |
| `omni-base` | `/api/base/**` | `lb://omni-base` |
| `omni-base-job` | `/api/job/**` | `lb://omni-base` |
| `omni-workflow` | `/api/workflow/**` | `lb://omni-workflow` |
| `omni-crm` | `/api/crm/**` | `lb://omni-crm` |
| `omni-srm` | `/api/srm/**` | `lb://omni-srm` |
| `omni-procurement` | `/api/procurement/**` | `lb://omni-procurement` |
| `omni-asset` | `/api/asset/**` | `lb://omni-asset` |

Normal business routes retain the full path without `StripPrefix`. The two `no://op` routes configure `SetStatus=404` to block internal paths; authentication or other preceding filters may return their own errors first.

### 5.2 Docker Deployment Routes

Official Compose resolves the same `lb://` service names through Nacos. Environment, configuration overrides and started modules determine accessibility; route configuration alone does not prove downstream health. For example, `/api/base/dict/type/list` reaches Base unchanged, and `/api/job/**` also targets Base.

### 5.3 AuthFilter Allowlist Paths

Exact matches: `/api/auth/login`, `/api/auth/session-login`, `/api/auth/register`, `/api/auth/captcha`, `/api/auth/tenants`, `/favicon.ico`.

Prefix matches: `/api/auth/oauth2/`, `/actuator/`, `/oauth2/`, `/.well-known/`, `/login`, `/error`. Prefix checking uses startsWith, not exact equality. It does not make all `/api/auth/**` public.

The allowlist only skips this filter's JWT validation. It does not create routes, controllers or downstream authorization. Public requests still lose forged trusted identity headers and Bearer Authorization, retain Basic Authorization required by OAuth2, and receive `X-Gateway-Forwarded=true`.

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

### 8.1 Gateway-Injected Request Headers

After a protected request passes JWT validation, Gateway removes client Authorization and trusted header names, then sets:

| Header | Source/meaning |
|---|---|
| `X-User-Id` | JWT user ID; positive integer |
| `X-Tenant-Id` | JWT tenant ID; positive integer |
| `X-User-Name` | JWT username; not `X-Username` |
| `X-User-Roles` | Role claim |
| `X-User-Scopes` | Permission claim |
| `X-Gateway-Forwarded` | `true` |
| `X-Gateway-Assertion` | Gateway-generated identity assertion |

List role/permission claims are comma-joined; other non-null values become strings, and absent claims become empty strings. The assertion signs `userId|tenantId|username|issuedAt` with HMAC-SHA256 and uses unpadded `base64url(payload):base64url(signature)`. Non-test configuration requires a strong private key. Each service must validate its preauthentication chain; the presence of a same-named header does not establish trusted identity.

### 8.2 Frontend Request Headers

Axios sets `Authorization: Bearer <JWT>` from `useUserStore().token` and obtains the tenant header via `getTenantIdFromToken(token)`, rather than trusting an independently editable tenant field. Gateway overwrites trusted identity headers for protected requests. JSON Content-Type follows the actual payload; the shared Axios instance sets no explicit global Content-Type default.

### 8.3 Internal Service Request Headers

The business internal APIs listed here use `X-Internal-Token` and explicit `X-Tenant-Id`; an end-user JWT does not replace internal authentication. JSON bodies use `Content-Type: application/json`. Endpoints requiring a body/query tenantId also compare it with the header; endpoint sections specify mismatch errors.

Validators depend on service configuration. The `omni-common-service` internal filter returns HTTP 401 for invalid request tokens, while its configuration validator prevents startup when relevant features require a missing/weak private token. The compatibility MQ `InternalApiAuthFilter` returns HTTP 503 when its server token is unconfigured and 401 when the request token is missing/mismatched. These implementations are not one universal runtime-503 branch.

Gateway explicitly blocks internal routes. They do not trust client `X-Gateway-Forwarded` or role/permission headers. Trusted internal callers still provide the correct business tenant for each request; the token does not select that tenant.

### 8.4 Security Response Headers (Gateway-Injected)

| Response header | Current value |
|---|---|
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |
| `X-Trace-Id` | Actual tracer trace ID; without a span, a 32-character UUID with hyphens removed |

[SecurityHeadersFilter](../omni-backend/omni-gateway/src/main/java/com/omni/gateway/config/SecurityHeadersFilter.java) removes the inbound public trace header. With an actual tracer span, it sets that ID on the downstream request and response. Without a span, it generates a response-only fallback and does not inject that fallback downstream. A response header alone therefore does not prove correlation across Gateway, Servlet and Feign. The frontend accepts only safe ASCII trace IDs of 1–128 characters and rejects abnormal values.

---

## 9. Authentication Header

The frontend interceptor sets `Authorization: Bearer <token>`. Gateway validates the JWT signature, expiry, positive user/tenant IDs and applicable revocation records, then injects identity. Current revocation checking depends on JWT jti; tokens without jti cannot be described as having passed a blacklist lookup. Section 5.3 owns exact/prefix public scope. User/role administration still requires valid authentication and endpoint permissions.

---

## 10. Social Login Endpoints

These browser navigation endpoints return HTTP 302 on the normal authorization path, without an `R<T>` wrapper. Provider identifiers are `github`, `google` and `gitee`:

| Method | URL / parameters | Contract |
|---|---|---|
| GET | `/api/auth/oauth2/{provider}?tenant_id=1` | Required `tenant_id`; validate provider/tenant, then redirect to the provider authorization page |
| GET | `/api/auth/oauth2/{provider}/callback`; `code`, `state` and `error` are optional parameter bindings | Handle denial, missing parameters, business failure or success below; optional binding does not make code/state optional for success |

| Callback condition | HTTP 302 Location |
|---|---|
| Nonblank provider `error` | Frontend `/login?error=user_denied` |
| Missing/blank `code` or `state` | Frontend `/login?error=invalid_callback` |
| `BusinessException`, including state validation or provider code-exchange failure | Frontend `/login?error=social_login_failed&message=<URL-encoded message>` |
| Success | Configured frontend `/callback#token=<URL-encoded JWT>&username=<URL-encoded username>` |

`OAuth2StateUtils` uses `tenantId|timestamp|hex(HMAC-SHA256(tenantId|timestamp, secret))`. The callback checks integrity, rejects timestamps older than 10 minutes and restores the tenant. Current code has no consumption marker, random nonce or browser-session binding; it does not establish complete one-time CSRF/replay protection. Application credentials do not replace external user login and consent.

Formal Docker Compose callbacks enter `http://localhost:8102/api/auth/oauth2/{provider}/callback` through Gateway. Production uses the configured Frontend/Gateway HTTPS domain. Provider registration must match the actual redirect_uri; private container addresses are not public callbacks. Initiation parameter/provider/tenant validation failures still use Auth error mapping and are not guaranteed to return 302.

Source: [SocialLoginController](../omni-backend/omni-auth/src/main/java/com/omni/auth/controller/SocialLoginController.java), [OAuth2StateUtils](../omni-backend/omni-auth/src/main/java/com/omni/auth/util/OAuth2StateUtils.java), [Compose callback configuration](../compose.apps.yaml). The [authentication guide](guides/authentication.en.md) owns configuration, browser procedures and failure reproduction. Local authorization-code/PKCE and device-code flows are separate protocols.

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
PUT /api/auth/xss-config/toggle?enabled=true
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
| PUT | `/api/auth/xss-config/rules/{id}/toggle?enabled=true` | `system:xssconfig:update` | Toggle rule enabled status |

**ruleType enum values**: `HTML_TAG` | `EVENT_HANDLER` | `DANGEROUS_PROTOCOL` | `CUSTOM_PATTERN`

### Permission Codes

| Permission Code | Description |
|--------|------|
| `system:xssconfig:list` | View XSS configuration and rules |
| `system:xssconfig:update` | Toggle global switch, update rules, toggle rule status |
| `system:xssconfig:create` | Create rule |
| `system:xssconfig:delete` | Delete rule |

Rule update, delete and status toggle check ownership using the Gateway-injected `X-Tenant-Id`. A missing or foreign-tenant rule returns HTTP 200 + `R.code=404` before mutation or cache invalidation; an invalid positive-integer tenant check throws business code 400. Existing permission codes are unchanged.

Switch endpoints require the `enabled` query parameter. Creation requires nonblank `ruleName`, `ruleType` and `pattern`; updates process non-null fields and validate a supplied pattern's regex syntax. Supported rule types are listed above; the current DTO does not validate the enum, and unknown runtime types perform no sanitization. See [core flows](core-flows.en.md) for activation conditions, Jackson 2/3, authoritative reads and cache boundaries.

---

## 12. Base Service Dictionary Management Endpoints

Base exposes full-path endpoints through Gateway's `/api/base/**` route; core flows describe the identity chain and default ports. Types and data each have 6 endpoints, all using verified current-tenant context.

### Dictionary Type Endpoints

Base path: `/api/base/dict/type`

| Method | Path | Permission | Behavior |
| --- | --- | --- | --- |
| GET | `/api/base/dict/type/list` | `dict:type:list` | Tenant pagination; optional typeCode/typeName substring filters and status, ascending sort/id |
| GET | `/api/base/dict/type/{id}` | `dict:type:list` | Get the current tenant's type; missing or foreign ID gives business code 404 |
| POST | `/api/base/dict/type` | `dict:type:create` | Nonblank typeCode/typeName; code unique within tenant |
| PUT | `/api/base/dict/type/{id}` | `dict:type:update` | Update non-null fields only; typeCode cannot change here |
| DELETE | `/api/base/dict/type/{id}` | `dict:type:delete` | After ownership check, cascade-delete that tenant's data for the type |
| PUT | `/api/base/dict/type/{id}/status?status=...` | `dict:type:update` | Required status query parameter; invalidate cache after update |

Pagination defaults to `page=1,size=10`; type queries optionally filter `typeCode/typeName/status`. Type creation accepts optional `remark/sort`; data creation accepts `tagType/remark/sort`. The service sets `status=1` and defaults sort to 0.

### Dictionary Data Endpoints

Base path: `/api/base/dict/data`

| Method | Path | Permission | Behavior |
| --- | --- | --- | --- |
| GET | `/api/base/dict/data/options?typeCode=...` | Authenticated; no separate action permission | Authentication only; cached enabled-data options containing value/label |
| GET | `/api/base/dict/data/list?typeCode=...` | `dict:data:list` | Administrative pagination reads DB; typeCode required, ascending sort/id |
| POST | `/api/base/dict/data` | `dict:data:create` | Nonblank typeCode/dictValue/dictLabel; parent must exist in current tenant |
| PUT | `/api/base/dict/data/{id}` | `dict:data:update` | Update non-null fields only; update DTO has no typeCode |
| DELETE | `/api/base/dict/data/{id}` | `dict:data:delete` | Check ownership, delete and invalidate cache |
| POST | `/api/base/dict/data/refresh-cache?typeCode=...` | `dict:data:refresh` | Required typeCode query parameter; delete key, query enabled rows and refill |

Update DTOs process non-null fields only; submitting null does not clear a field. `/options` returns `R<List<DictOptionVO>>` with only `value` and `label`. Administrative pagination returns `R<PageResult<SysDictType>>` or `R<PageResult<SysDictData>>`.

### Dictionary Permission Codes

| Permission code | Purpose |
| --- | --- |
| `dict:type:list` | Type list and details |
| `dict:type:create` | Create type |
| `dict:type:update` | Update type and status |
| `dict:type:delete` | Cascade-delete type |
| `dict:data:list` | Administrative data list |
| `dict:data:create` | Create data |
| `dict:data:update` | Update data |
| `dict:data:delete` | Delete data |
| `dict:data:refresh` | Refresh cache |

### Tenant Isolation

List, creation, options and refresh explicitly pass the tenant. The six ID-based read/write endpoints also pass Gateway's `X-Tenant-Id` to the service, require a positive tenant ID and verify ownership before returning, writing or invalidating cache. Type uniqueness is `(tenant_id,type_code)`.

Missing and foreign-tenant ID resources throw `BusinessException(404)`, mapped by the common handler to HTTP 200 with `R.code=404`; a missing or nonpositive tenant ID produces business code 400. Gateway and method-permission checks remain independent. Core flows and frontend patterns own cache behavior, client lifecycle and source navigation.

[Flow 9](core-flows.en.md) / [Composable](frontend-patterns.en.md)

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

The result is configuration-derived: `outboxWriteEnabled=true` and `deliveryEnabled = omni.mqlog.relay.enabled && xxl.job.executor.enabled` (both reads default true); mode is `FULL/OUTBOX_ONLY` accordingly. It probes neither relay processes, XXL-JOB scheduling, Broker, database nor delivered messages. `FULL` means the switches are enabled; `OUTBOX_ONLY` means at least one delivery switch is off. See [MQ reliability](mq-reliability.en.md) for actual reliability and recovery.

---

## 13. API Versioning Strategy

Current Gateway and Controller routes use unversioned `/api/...` paths such as `/api/auth/user/list`. Parallel `/api/v1/...` or `/api/v2/...` routes are not implemented.

Future URL versioning, breaking-change migration and old-version retention are not implemented contracts. Define consumers, compatibility, migration and support periods in design/ADR first, then synchronize implementation and documentation. Unimplemented prefixes and six-month maintenance are not commitments. Current fields, errors and states continue to follow this contract and source.

---

## 14. Null Semantics

`R.ok()` and failure responses have null data; the current regular mapper includes those null fields. Collections become [] only when producers actually return an empty List. `PageResult` preserves null records; there is no global conversion guarantee. Optional-field null, empty-string and omission behavior depends on the DTO, serializer and endpoint.

JSON binding, accepted business fields and identity sources are separate constraints. Unknown properties do not universally produce 400: controlled current Asset DTO binding ignored an extra property. Tenant/user/amount snapshots not exposed by the DTO do not become controllable business inputs, but the parser does not necessarily reject every forged property explicitly. Send only the fields documented for the endpoint.

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

Enrollment DTO requires requestId (nonblank,max64), inviteToken (nonblank,max256), name (nonblank,max200) and creditCode (nonblank,max50). creditCode is trimmed and uppercased; the example is not a registered real company code. Unlike ordinary unknown-property behavior, this DTO explicitly rejects every undeclared property, including tenantId/userId, via JsonAnySetter. SRM advice maps JSON-read exceptions to400.

### 15.3 Evaluation and Risk

- `GET /api/srm/evaluation/template/default/dimensions` returns the current tenant's default template and valid dimensions; the frontend must not hardcode database IDs.
- Scores range from 1-5, must cover all dimensions of the default template, and must not repeat.
- `GET /api/srm/risk/list` returns a paginated risk summary aggregated by each supplier's latest evaluation, filterable by `riskLevel`.
- `GET /api/srm/supplier/{id}/risk` returns an aggregated view of `indicators/latestAssessment/history`.
- Risk indicator updates carry `version`; `srm.risk.level-changed.v1` is produced only when the composite level changes from non-RED to RED.

Evaluation creation selects the tenant's enabled default template; the DTO has no templateId. items contains 1–100 entries covering exactly all enabled dimensions without duplicates. score is 1–5 with at most one decimal place. Obtain dimensionId from the default-template endpoint. The service determines template, dimension names and weights.

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
      "unit": "EA",
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
| `lines[].remark` | String | No | Max500; service trims and normalizes blank values |

`tenantId/supplierId/rfqNo/material/quantity/currencyCode/lineAmount/totalAmount` are not controllable inputs of this request DTO. The service obtains or computes them from trusted identity, PortalUser and Procurement invitation snapshots. Extra JSON properties may be ignored; an explicit error is not guaranteed. The `R<QuotationVO>` response contains header, version and line snapshots; the next section defines decimal wire values.

Idempotency and concurrency rules:

- `srm_quotation_request` stores tenant-scoped requestId, normalized SHA-256, quotationId and targetVersion. A historical COMPLETED request replays before the new-request supplier/RFQ/deadline/invitation-line checks. It returns the current quotation snapshot without another quotation/Outbox write; replay does not repeat all new-submission checks.
- A different hash, supplierId or rfqId for the same requestId, a RESERVED/invalid record, or a current quotation older than targetVersion returns business 409.
- A new request requires the currently approved supplier and freshly reads the SENT RFQ, INVITED/QUOTED invitation and unexpired deadline. validUntil must be future and no earlier than the RFQ deadline. The 1–200 lines must cover exactly all RFQ lines without duplicates. Unavailable dependencies fail closed with 503.
- `(tenantId, rfqId, supplierId)` permits at most one undeleted quotation. Initial request version=0 persists version 1; updates use the current version, with 409 on conflicts.
- requestHash excludes requestId and covers `rfqId/version/validUntil/lines`. Lines sort by rfqLineId; prices use six-decimal non-scientific strings; remarks are trimmed and null/blank normalized. It is not a hash of raw JSON bytes. Each `unitPrice × quantity` is rounded HALF_UP to four decimals before summing.

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
`R<T>`. For detailed runtime mechanics, see [workflow.en.md](workflow.en.md#6-internal-starts-and-completion-events).

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

Idempotency uses tenant-scoped unique keys `(tenantId, requestId)` and `(tenantId, businessType, businessKey)`. Conflict checks compare business type/key, modelVersionId and startUserId. They do not compare title, variables or startUserName, and there is no full payload hash.

A successful record returns the original processInstanceId with replayed=true, but echoes the current requestId. Processing or conflicting business/model/starter intent returns business 409. The same-requestId FAILED branch can atomically reserve a retry; a different requestId matching a FAILED business key does not automatically take that retry branch.

A real new start revalidates PUBLISHED metadata and a nonblank processDefinitionId, with exact category binding for `ASSET_TRANSFER/ASSET_DISPOSAL/SRM_SUPPLIER_ONBOARDING`. A blank title becomes `{businessType}:{businessKey}`. These checks do not make arbitrary variables trusted identity. [Workflow](workflow.en.md) owns starter, tenant, protected-variable and routing constraints.

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

A header/body tenantId mismatch first returns business 403. Other eligibility checks match the Flowable task tenant, extension-record tenant and businessType/businessKey, then the current ASSIGNEE or an unclaimed task's CANDIDATE. Missing tasks, business mismatch or ineligibility return `R.code=200,data.valid=false`; assignmentType is `ASSIGNEE/CANDIDATE/NONE`. false does not replace permission and concurrency checks when the caller executes approval commands.

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

Integrated approval-completion and explicit-termination paths update completion state/completionEventId and insert a PENDING Outbox record in the same local transaction. `completion_event_id IS NULL` prevents duplicate logical completion events. There is no universal listener covering every Flowable engine-end or instant-end branch. Success/Outbox commit does not mean Broker delivery or consumption completed. Relay delivery/retry is asynchronous when enabled and dependencies are available; consumers deduplicate by eventId.

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

The tenant-scoped query requires PUBLISHED status and a nonblank processDefinitionId. Missing/unavailable versions return business 404. Publication validation maintains modelKey/BPMN process-id consistency; returned strings alone do not establish current engine executability.

The current version by category is also available at `GET /api/internal/workflow/model-version/current?category={category}`. New starts bind `ASSET_TRANSFER/ASSET_DISPOSAL/SRM_SUPPLIER_ONBOARDING` categories; successful same-intent replay reuses the instance. Internal termination uses `POST /api/internal/workflow/process-instance/{processInstanceId}/terminate?reason={reason}` with explicit X-Tenant-Id and X-User-Id in addition to the internal token; reason is optional.

### 16.5 Approval-Rule Read-Only Model Aggregation

| Method | Path | Current constraints |
|---|---|---|
| GET | `/api/internal/workflow/model-versions/published?category=purchase` | Current tenant, exact category, enabled model, current published version and nonblank processDefinitionId |
| POST | `/api/internal/workflow/model-versions/resolve` | Body `{ "modelVersionIds": [1, 2] }`; 1–200 positive integers, deduplicated in first-occurrence order |
| GET | `/api/internal/workflow/model-version/{id}/preview` | Safe approval graph/metadata; no raw BPMN XML/designerJson |

availability is `AVAILABLE/NOT_CURRENT/UNAVAILABLE/MODEL_ARCHIVED/NOT_FOUND`, derived from model/version metadata. It does not probe live Flowable deployment, suspension or executability.

Preview root fields are `approvalPreviewVersion/modelVersion/nodes/edges/hasBranches/linearSummary`. nodes has `id/name/type/roleCode/approvalMode/description`, with type `START/END/APPROVAL/GATEWAY/SERVICE`. edges has `id/name/source/target/defaultBranch/conditionSummary`. Conditions expose only “已配置条件（内容已隐藏）”. linearSummary is null for branches or when no single-path summary can be produced. Current organization previews are not future actual approvers.

### 16.6 Designer Candidate Preview

`POST /api/workflow/identity/resolve-preview` requires `workflow:identity:list` and a trusted tenant header. Request fields are `assignmentType/roleCode/anchorType/anchorParams/scopeMode/simulateUserId`; assignmentType is required. Response data has `candidateCount/candidates`; candidates contain `userId/username/nickname/unitName`. See [Workflow section 3](workflow.en.md#3-candidates-listeners-and-countersigning) for resolver failure/compatibility and anchor semantics. Do not substitute the internal safe-graph DTO.

### 16.7 Instance Read Tenant Boundary

External `GET /api/workflow/process-instance/{id}/progress` and `GET /api/workflow/process-instance/{id}/approval-records` explicitly use Gateway X-Tenant-Id. Missing/invalid tenants fail; nonpositive tenants produce business 400. A missing same-tenant historical instance and a foreign-tenant instance both produce business 404 before activities, comments or candidate details are read. This isolation adds no participant ACL and does not establish equivalent protection on all historical definition APIs.

## 17. Procurement MVP Contract

### 17.1 General Boundaries

- External base path is `/api/procurement`, retaining the full path and Gateway identity/permissions. TenantLine covers only proc_* business tables; it does not tenant-filter the Outbox scan.
- DataScope uses requester_user_id/requester_unit_id for requisitions and owner_user_id/owner_unit_id for RFQ/PO/GR. Child rows inherit their aggregate root. Materials, categories, approval routes and configuration are shared within a tenant; do not append nonexistent owner columns.
- Quantity/price use DECIMAL(19,6), amount DECIMAL(19,4), with decimal-string responses. Requests obey each DTO's decimal-string constraints. Do not use JavaScript number for business-money calculations.
- Updates, deletes and versioned state commands supply versions as defined by their DTO. RFQ awards use rfqVersion/quotationVersion; not every write has a field named version. Creates have no existing-row version.
- Internal `/api/internal/procurement/**` requires X-Internal-Token/X-Tenant-Id and is Gateway-blocked. [Procurement design](design/procurement-design.en.md) owns detailed state/transaction boundaries.

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

Create accepts `requisitionId/title/quotationDeadline/supplierIds`. Draft update accepts `version/title/quotationDeadline/supplierIds` and does not change requisitionId. quotationDeadline uses `yyyy-MM-dd HH:mm:ss` and must be future; supplierIds contains 1–100 positive integers. Only SENT RFQs permit comparison/award. Invitation states are `INVITED/QUOTED/EXPIRED/AWARDED/REJECTED`; the last two permit historical viewing only, not further quotation.

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

- External base path is `/api/asset`, retaining full paths. Identity headers are `X-User-Id/X-Tenant-Id/X-User-Name` plus roles/permissions, not X-Username. TenantLine applies only to ast_* tables; Outbox relay scans across tenants.
- Management uses owner_user_id/owner_unit_id and children inherit asset scope. My-assets, accept and return use fixed current_user_id; management permission does not expand personal-asset queries.
- Updates, deletes and commands on existing assets supply version; creates have no existing version. Active-operation occupancy or version conflicts reject the write.
- Purchase/residual and aggregate amounts use DECIMAL(18,2); non-null requests/responses use decimal JSON strings. Numbers or invalid strings are rejected, but the current general JSON-read handler maps this to 500, not a guaranteed 400. currencyCode checks three English letters; this does not validate the ISO 4217 registry.
- States are `IN_STOCK/ALLOCATED/IN_USE/MAINTENANCE/TRANSFER/DISPOSAL_PENDING/DISPOSED/SCRAPPED`.
- Internal `/api/internal/asset/**` requires X-Internal-Token/X-Tenant-Id and is Gateway-blocked. [Asset design](design/asset-design.en.md) owns lifecycle, receipt ingestion and approval transactions.

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
