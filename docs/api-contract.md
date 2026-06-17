# API Contract

This document defines the definitive API contract between frontend and backend. Both sides must conform to these structures. Any deviation requires explicit team approval.

## Response Envelope

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

### Frontend Type: `ApiResponse<T>`

```typescript
interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}
```

**Canonical location**: `src/types/api.ts` (single source of truth; do not duplicate in other files).

## Error Code Mapping

| HTTP Status | Business Code | Scenario | Trigger |
|-------------|---------------|----------|---------|
| 200 | 200 | Success | `R.ok(data)` |
| 200 | 400 | Validation failure | `MethodArgumentNotValidException` / `BindException` caught by `GlobalExceptionHandler` |
| 200 | 401 | Authentication required | Future: `AuthFilter` returns 401 |
| 200 | 500 | Business exception | `BusinessException` caught by `GlobalExceptionHandler` |
| 200 | 500 | Unexpected error | Catch-all `Exception` handler |

**Note**: The HTTP status is always 200 for business responses (the error code is in the `code` field). The exceptions are validation errors which return HTTP 400 via `@ResponseStatus`.

### Frontend Error Handling

The Axios response interceptor in `src/api/request.ts` checks `res.code !== 200`:
- Shows `ElMessage.error(res.message)`
- On code `401`: calls `userStore.logout()` and redirects to `/login`
- Returns `Promise.reject(new Error(res.message))`

## Pagination Contract

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
// Backend controller
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

## RESTful URL Conventions

| Operation | HTTP Method | URL Pattern | Example |
|-----------|-------------|-------------|---------|
| Get by ID | GET | `/{resource}/{id}` | `GET /user/1` |
| List (paginated) | GET | `/{resource}/list` | `GET /user/list?page=1&size=10` |
| Create | POST | `/{resource}` | `POST /user` |
| Update | PUT | `/{resource}/{id}` | `PUT /user/1` |
| Delete | DELETE | `/{resource}/{id}` | `DELETE /user/1` |

**Gateway path prefix**: All frontend requests use `/api/<service>/<resource>` (e.g., `/api/auth/user/list`). The Gateway strips `/api/<service>` (StripPrefix=2), so the downstream service receives `/<resource>`.

## Naming Conventions

### Request/Response DTOs

| Type | Suffix | Example |
|------|--------|---------|
| Create request | `CreateXxxRequest` | `CreateUserRequest` |
| Update request | `UpdateXxxRequest` | `UpdateUserRequest` |
| View object | `XxxVO` | `UserVO` |
| Query parameters | `XxxQuery` | `UserQuery` |

DTOs can be defined as static inner classes of the Controller (for simple cases) or as standalone files (for complex cases).

### Field Naming

- Java fields: `lowerCamelCase` (e.g., `createTime`, `userName`)
- JSON serialization: `lowerCamelCase` (matches Java field names directly)
- URL path segments: `kebab-case` or single words (e.g., `/user/list`, not `/user/getAllUsers`)

## Time Format

Configured in `JacksonConfig.java`:

| Java Type | JSON Format | Example |
|-----------|-------------|---------|
| `LocalDateTime` | `yyyy-MM-dd HH:mm:ss` | `2026-05-28 14:30:00` |
| `LocalDate` | `yyyy-MM-dd` | `2026-05-28` |

Timestamps are serialized as strings, not numeric timestamps (`WRITE_DATES_AS_TIMESTAMPS` is disabled).

## Social Login Endpoints

社交登录端点返回 HTTP 302 重定向（非标准 `R<T>` 响应），因为前端通过 `window.location.href` 触发浏览器导航。

| HTTP Method | URL | Description |
|-------------|-----|-------------|
| GET | `/api/auth/oauth2/{provider}?tenant_id=1` | 发起第三方登录，302 重定向到第三方授权页面 |
| GET | `/api/auth/oauth2/{provider}/callback?code=XXX&state=YYY` | 处理第三方回调，成功时 302 重定向到前端回调页面 |

### 发起登录

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

- `{provider}` 支持 `github`、`google` 和 `gitee`
- `tenant_id` 必填，指定登录的目标租户
- State 参数包含 HMAC-SHA256 签名（`tenantId|timestamp|hmac`），防止 CSRF 攻击

### 回调处理

```
# GitHub 回调
GET /api/auth/oauth2/github/callback?code=XXX&state=YYY

# Google 回调
GET /api/auth/oauth2/google/callback?code=XXX&state=YYY

# Gitee 回调
GET /api/auth/oauth2/gitee/callback?code=XXX&state=YYY

→ 成功: 302 Location: /callback#token=<JWT>&username=<username>
→ 失败: 302 Location: /login?error=<error_code>&message=<message>
```

### 错误码

| error 参数 | 含义 | 触发条件 |
|------------|------|---------|
| `user_denied` | 用户拒绝授权 | 第三方平台回调携带 `error=access_denied`（GitHub / Google / Gitee 均适用） |
| `invalid_callback` | 回调参数缺失 | code 或 state 为空 |
| `social_login_failed` | 登录流程异常 | state 验证失败、第三方 API 错误（GitHub access_token / Google token / Gitee token 接口）、用户信息获取失败、用户已禁用等 |

### 前端回调页面

`/callback` 页面（`src/views/callback/index.vue`）负责：
1. 解析 URL fragment 中的 `token` 和 `username`
2. 存储到 `localStorage`（通过 `useUserStore`）
3. 重定向到 Dashboard

## Authentication Header

```
Authorization: Bearer <token>
```

- Set by the Axios request interceptor in `src/api/request.ts` using the token from `useUserStore()`
- Checked by `AuthFilter` in `omni-gateway` (JWT RS256 签名验证 + claims 提取 + 身份头注入)
- Public paths exempt from auth: `/api/auth/**`, `/actuator/**`, `/favicon.ico`

## Versioning Strategy

**Current decision**: No URL-based versioning during the scaffolding phase. When the API stabilizes and multiple consumers exist, introduce prefix versioning: `/api/v1/auth/user/list`.

## XSS Config Management Endpoints

Base path: `/api/auth/xss-config`（Gateway StripPrefix=2 → 下游 `/xss-config/...`）

### 获取当前 XSS 配置

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

### 切换全局开关

```
PUT /api/auth/xss-config/toggle
Authorization: Bearer <token>
X-Tenant-Id: 1

@PreAuthorize("hasAuthority('system:xssconfig:update')")
Response 200: { "code": 200, "message": "success" }
```

### 规则列表（分页）

```
GET /api/auth/xss-config/rules/list?page=1&size=10
Authorization: Bearer <token>
X-Tenant-Id: 1

Response 200:
{
  "code": 200,
  "data": {
    "records": [...],
    "total": 10,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
```

### 创建规则

```
POST /api/auth/xss-config/rules
Authorization: Bearer <token>
X-Tenant-Id: 1
Content-Type: application/json

{
  "ruleName": "Script标签",
  "ruleType": "HTML_TAG",
  "pattern": "script",
  "description": "拦截<script>标签",
  "sortOrder": 1
}

@PreAuthorize("hasAuthority('system:xssconfig:create')")
Response 200: { "code": 200, "data": { "id": 1, ... } }
```

**ruleType 枚举值**：`HTML_TAG` | `EVENT_HANDLER` | `DANGEROUS_PROTOCOL` | `CUSTOM_PATTERN`

### 更新规则

```
PUT /api/auth/xss-config/rules/{id}
@PreAuthorize("hasAuthority('system:xssconfig:update')")
```

### 删除规则

```
DELETE /api/auth/xss-config/rules/{id}
@PreAuthorize("hasAuthority('system:xssconfig:delete')")
```

### 切换单条规则启用状态

```
PUT /api/auth/xss-config/rules/{id}/toggle
@PreAuthorize("hasAuthority('system:xssconfig:update')")
```

### 权限码

| 权限码 | 说明 |
|--------|------|
| `system:xssconfig:list` | 查看 XSS 配置和规则 |
| `system:xssconfig:update` | 切换全局开关、更新规则、切换规则状态 |
| `system:xssconfig:create` | 创建规则 |
| `system:xssconfig:delete` | 删除规则 |

## Base Service Dictionary Management Endpoints

Base 服务（`omni-base :8101`）提供数据字典管理功能，采用「类型 + 数据」两级结构。

**路由说明**：Gateway 路由 `Path=/api/base/**` 无 StripPrefix 过滤器，Base 服务控制器使用完整路径（如 `@RequestMapping("/api/base/dict/type")`），与 Auth 服务的 `StripPrefix=2` 模式不同。

### Dictionary Type Endpoints

Base path: `/api/base/dict/type`

| HTTP Method | URL | Permission Code | Description |
|-------------|-----|-----------------|-------------|
| GET | `/api/base/dict/type/list?page=1&size=10&typeCode=&typeName=&status=` | `dict:type:list` | 分页列表，支持 typeCode / typeName / status 过滤 |
| GET | `/api/base/dict/type/{id}` | `dict:type:list` | 按 ID 查询 |
| POST | `/api/base/dict/type` | `dict:type:create` | 创建字典类型（验证 tenant 内 typeCode 唯一性） |
| PUT | `/api/base/dict/type/{id}` | `dict:type:update` | 更新字典类型（部分更新：typeName, remark, sort, status） |
| DELETE | `/api/base/dict/type/{id}` | `dict:type:delete` | 删除字典类型（级联删除关联的字典数据） |
| PUT | `/api/base/dict/type/{id}/status` | `dict:type:update` | 切换启用/禁用状态 |

**示例请求**：

```
GET /api/base/dict/type/list?page=1&size=10
Authorization: Bearer <token>
X-Tenant-Id: 1

Response 200:
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      { "id": 1, "typeCode": "sys_user_gender", "typeName": "用户性别", "status": 1, "sort": 0 }
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
|-------------|-----|-----------------|-------------|
| GET | `/api/base/dict/data/list?typeCode=sys_user_gender&page=1&size=10` | `dict:data:list` | 按 typeCode 分页查询字典数据 |
| POST | `/api/base/dict/data` | `dict:data:create` | 创建字典数据（验证父类型存在） |
| PUT | `/api/base/dict/data/{id}` | `dict:data:update` | 更新字典数据（部分更新：dictValue, dictLabel, tagType, remark, sort, status） |
| DELETE | `/api/base/dict/data/{id}` | `dict:data:delete` | 删除单条字典数据 |
| POST | `/api/base/dict/data/refresh-cache` | `dict:data:refresh` | 手动刷新指定 typeCode 的 Redis 缓存 |

**示例请求**：

```
POST /api/base/dict/data
Authorization: Bearer <token>
X-Tenant-Id: 1
Content-Type: application/json

{
  "typeCode": "sys_user_gender",
  "dictValue": "3",
  "dictLabel": "保密",
  "tagType": "warning",
  "sort": 3
}

@PreAuthorize("hasAuthority('dict:data:create')")
Response 200: { "code": 200, "data": { "id": 8, ... } }
```

### Permission Codes

| 权限码 | 说明 |
|--------|------|
| `dict:type:list` | 查看字典类型列表 |
| `dict:type:create` | 创建字典类型 |
| `dict:type:update` | 更新/切换字典类型状态 |
| `dict:type:delete` | 删除字典类型（级联） |
| `dict:data:list` | 查看字典数据列表 |
| `dict:data:create` | 创建字典数据 |
| `dict:data:update` | 更新字典数据 |
| `dict:data:delete` | 删除字典数据 |
| `dict:data:refresh` | 手动刷新字典缓存 |

### Tenant Isolation

所有列表查询和创建操作要求 `X-Tenant-Id` 请求头（前端从 JWT Token 提取，Gateway 注入）。数据在 SQL 查询层按 `tenant_id` 隔离。字典类型唯一性约束范围为 `(tenant_id, type_code)`。

## Null Semantics

- `null` fields are included in JSON output (not suppressed)
- Empty collections should be returned as `[]`, not `null`
- Optional single values should use `null` to indicate absence, not empty strings
