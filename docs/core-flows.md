# Core Business Flows

This document traces the essential user-facing flows end-to-end, from browser interaction to backend processing and back. Use this as a reference when implementing or modifying features.

## Flow 1: User Login (Username + Password + Captcha + JWT)

### Overview

用户通过前端登录页面提交用户名、密码和验证码，经 Gateway 转发到 Auth 服务完成认证，
返回 JWT Token 用于后续请求的身份认证。支持多租户登录（`tenantId:username` 格式）。

### Sequence

```
Browser            Frontend :3000          Gateway :8090          Auth :9000           Redis              MySQL
  |                    |                       |                     |                   |                  |
  | 1. Open login page |                       |                     |                   |                  |
  |                    |                       |                     |                   |                  |
  |                    | 2. GET /api/auth/captcha                   |                   |                  |
  |                    |---------------------->|                     |                   |                  |
  |                    |                       | 3. Proxy to Auth    |                   |                  |
  |                    |                       |-------------------->|                   |                  |
  |                    |                       |                     | 4. SpecCaptcha    |                  |
  |                    |                       |                     |    generate()     |                  |
  |                    |                       |                     | 5. SET captcha:   |                  |
  |                    |                       |                     |    {key} = text   |                  |
  |                    |                       |                     |    TTL=300s ----->|                  |
  |                    |                       |                     |                   |                  |
  |                    | 6. {captchaKey, captchaImage(base64)}      |                   |                  |
  |                    |<----------------------|<--------------------|                   |                  |
  | 7. Show captcha    |                       |                     |                   |                  |
  |<-------------------|                       |                     |                   |                  |
  |                    |                       |                     |                   |                  |
  |                    | 8. GET /api/auth/tenants                    |                   |                  |
  |                    |---------------------->|                     |                   |                  |
  |                    |                       | 9. Proxy to Auth    |                   |                  |
  |                    |                       |-------------------->|                   |                  |
  |                    |                       |                     | 10. SELECT * FROM |                  |
  |                    |                       |                     |     sys_tenant    |                  |
  |                    |                       |                     |     WHERE status=1|                  |
  |                    |                       |                     |-------------------------------------->|
  |                    |                       |                     |                   |    tenant list    |
  |                    |                       |                     |<--------------------------------------|
  |                    | 11. [{id,name,code}]  |                     |                   |                  |
  |                    |<----------------------|<--------------------|                   |                  |
  | 12. Tenant dropdown|                       |                     |                   |                  |
  |<-------------------|                       |                     |                   |                  |
  |                    |                       |                     |                   |                  |
  | 13. Fill form      |                       |                     |                   |                  |
  |     (username,     |                       |                     |                   |                  |
  |      password,     |                       |                     |                   |                  |
  |      captcha,      |                       |                     |                   |                  |
  |      tenant)       |                       |                     |                   |                  |
  | 14. Click Login -->|                       |                     |                   |                  |
  |                    |                       |                     |                   |                  |
  |                    | 15. POST /api/auth/login                   |                   |                  |
  |                    |   {username, password, |                   |                   |                  |
  |                    |    tenantId, captchaKey,                   |                   |                  |
  |                    |    captchaCode} ------>|                   |                   |                  |
  |                    |                       | 16. Proxy to Auth   |                   |                  |
  |                    |                       |-------------------->|                   |                  |
  |                    |                       |                     |                   |                  |
  |                    |                       |                     | 17. Validate captcha                  |
  |                    |                       |                     |    GET + DEL ---->|                  |
  |                    |                       |                     |    (one-time use) |                  |
  |                    |                       |                     |                   |                  |
  |                    |                       |                     | 18. Build username as                 |
  |                    |                       |                     |     "tenantId:username"               |
  |                    |                       |                     |     (e.g. "1:admin")                  |
  |                    |                       |                     |                   |                  |
  |                    |                       |                     | 19. LoadUserByUsername                |
  |                    |                       |                     |     Parse tenant + username           |
  |                    |                       |                     |     WHERE tenant_id=? AND             |
  |                    |                       |                     |           username=? AND status=1     |
  |                    |                       |                     |-------------------------------------->|
  |                    |                       |                     |                   |    user record    |
  |                    |                       |                     |<--------------------------------------|
  |                    |                       |                     |                   |                  |
  |                    |                       |                     | 20. BCrypt password check             |
  |                    |                       |                     |                   |                  |
  |                    |                       |                     | 21. Load roles & permissions          |
  |                    |                       |                     |-------------------------------------->|
  |                    |                       |                     |                   |   roles/permissions
  |                    |                       |                     |<--------------------------------------|
  |                    |                       |                     |                   |                  |
  |                    |                       |                     | 22. Generate JWT   |                  |
  |                    |                       |                     |     RS256 sign     |                  |
  |                    |                       |                     |     (RSA private   |                  |
  |                    |                       |                     |      key from JWK) |                  |
  |                    |                       |                     |                   |                  |
  |                    | 23. {accessToken, tokenType:"Bearer",       |                   |                  |
  |                    |      expiresIn:900}   |                     |                   |                  |
  |                    |<----------------------|<--------------------|                   |                  |
  |                    |                       |                     |                   |                  |
  |                    | 24. Store token + username to localStorage |                   |                  |
  |                    |                       |                     |                   |                  |
  | 25. Redirect to    |                       |                     |                   |                  |
  |     dashboard      |                       |                     |                   |                  |
  |<-------------------|                       |                     |                   |                  |
  |                    |                       |                     |                   |                  |
```

### Post-Login: Authenticated Request Flow

登录成功后，前端所有 API 请求自动携带 JWT Token，Gateway 负责验证：

```
Browser            Frontend               Gateway :8090           Downstream Service
  |                    |                       |                        |
  | 1. Navigate to     |                       |                        |
  |    protected page  |                       |                        |
  |                    | 2. Axios GET          |                        |
  |                    |    Authorization:      |                        |
  |                    |    Bearer <JWT> ------>|                        |
  |                    |                       |                        |
  |                    |                       | 3. AuthFilter:         |
  |                    |                       |    - Extract Bearer token
  |                    |                       |    - Get RSA public key |
  |                    |                       |      (from JwkKeyProvider,
  |                    |                       |       cached 5min TTL)  |
  |                    |                       |    - RSASSAVerifier:   |
  |                    |                       |      verify RS256 sig  |
  |                    |                       |    - Check exp claim   |
  |                    |                       |                        |
  |                    |                       | 4. Inject headers:     |
  |                    |                       |    X-User-Id: 1        |
  |                    |                       |    X-Tenant-Id: 1      |
  |                    |                       |    X-User-Name: admin  |
  |                    |                       |    X-User-Roles: admin |
  |                    |                       |    X-User-Scopes: read |
  |                    |                       |                        |
  |                    |                       | 5. Forward request --->|
  |                    |                       |                        |
  |                    |                       |    6. Response <-------|
  |                    | 7. JSON data <--------|                        |
  | 8. Render page     |                       |                        |
  |<-------------------|                       |                        |
```

### Key Components

| Step | File | Logic |
|------|------|-------|
| Form UI | `src/views/login/LoginForm.vue` | Element Plus form: username, password, captcha, tenant dropdown |
| Captcha load | `src/views/login/LoginForm.vue` | `GET /api/auth/captcha` -> display base64 PNG, store captchaKey |
| Tenant load | `src/views/login/LoginForm.vue` | `GET /api/auth/tenants` -> populate `<el-select>` dropdown |
| Login submit | `src/views/login/LoginForm.vue` | `handleLogin()`: validate form -> POST `/api/auth/login` |
| Token storage | `src/stores/user.ts` | `setToken()` + `setUsername()` persist to `localStorage` |
| Request auth | `src/api/request.ts` | Axios request interceptor: `Authorization: Bearer <token>` |
| Vite proxy | `vite.config.ts` | `/api` -> `http://localhost:8090` (Gateway) |
| Gateway filter | `AuthFilter.java` | JWT RS256 签名验证 + claims 提取 + 身份头注入 |
| JWK provider | `JwkKeyProvider.java` | 从 Auth `/oauth2/jwks` 获取 RSA 公钥，缓存 5 分钟 |
| Captcha service | `CaptchaServiceImpl.java` | SpecCaptcha 生成 + Redis 存储（TTL 300s，一次性使用） |
| Auth controller | `AuthController.java` | `POST /login`: captcha -> authenticate -> roles -> JWT |
| User details | `OmniUserDetailsService.java` | 多租户解析 `tenantId:username` + BCrypt 密码校验 |
| JWT service | `JwtTokenServiceImpl.java` | RSA 私钥签名，生成包含用户身份和权限的 JWT |

### JWT Token Structure

Auth 服务签发的 JWT 包含以下 claims：

```json
{
  "header": {
    "alg": "RS256",
    "kid": "<key-id-from-jwk>"
  },
  "payload": {
    "sub": "1",
    "tenant_id": "1",
    "username": "admin",
    "roles": ["admin"],
    "scope": "read write",
    "iat": 1748000000,
    "exp": 1748000900
  }
}
```

| Claim | Description |
|-------|-------------|
| `sub` | 用户 ID（`sys_user.id`） |
| `tenant_id` | 租户 ID（`sys_tenant.id`） |
| `username` | 登录用户名 |
| `roles` | 用户角色列表（如 `["admin", "editor"]`） |
| `scope` | 权限范围，空格分隔 |
| `iat` | 签发时间（Unix timestamp） |
| `exp` | 过期时间（`iat` + 900 秒 = 15 分钟） |

### Multi-Tenant Login Mechanism

登录时通过 `tenantId` 参数指定租户，Auth 服务内部将用户名格式化为 `tenantId:username`（如 `1:admin`），
由 `OmniUserDetailsService.loadUserByUsername()` 解析：

```
前端提交: { username: "admin", tenantId: 1 }
  -> AuthController 构造: "1:admin"
  -> OmniUserDetailsService 解析: tenantId=1, actualUsername="admin"
  -> SQL: SELECT * FROM sys_user WHERE tenant_id=1 AND username='admin' AND status=1
```

如果不包含 `:`（直接用户名登录），默认 `tenantId=1`，保证向后兼容。

### Captcha Lifecycle

```
1. 生成: SpecCaptcha -> base64 PNG
2. 存储: Redis SET captcha:{uuid} = "a3f8" EX 300
3. 验证: Redis GET captcha:{uuid} -> DELETE captcha:{uuid}（一次性使用，防重放）
   - key 不存在 -> "Captcha expired"（过期或已使用）
   - 值不匹配   -> "Invalid captcha"
```

### Current Status

- **Login**: 完整实现，验证码 + 多租户 + JWT Token 签发
- **Gateway JWT 验证**: 完整实现，RS256 签名检查 + claims 提取 + 身份头注入
- **前端**: 所有 mock 代码已移除，对接真实 API
- **Token 有效期**: 15 分钟（900 秒），暂无 refresh token 机制

---

## Flow 2: List Query (Pagination)

### Sequence

```
Browser                    Frontend                     Gateway :8090            Business :8081
  |                           |                              |                        |
  |  1. View list page        |                              |                        |
  |                           |  2. Call listUsers(1, 10)    |                        |
  |                           |     from api/user.ts         |                        |
  |                           |                              |                        |
  |                           |  3. Axios GET                |                        |
  |                           |  /api/business/user/list     |                        |
  |                           |  ?page=1&size=10 ----------->|                        |
  |                           |                              |                        |
  |                           |                              |  4. Route match:       |
  |                           |                              |     Path=/api/business/**
  |                           |                              |     StripPrefix=2      |
  |                           |                              |                        |
  |                           |                              |  5. lb://omni-business |
  |                           |                              |  GET /user/list ------->|
  |                           |                              |                        |
  |                           |                              |                        |  6. UserController
  |                           |                              |                        |     .listUsers()
  |                           |                              |                        |        |
  |                           |                              |                        |  7. UserService
  |                           |                              |                        |     .listUsers()
  |                           |                              |                        |     returns PageResult
  |                           |                              |                        |        |
  |                           |                              |  8. R.ok(pageResult) <-|
  |                           |                              |<-----------------------|
  |                           |                              |                        |
  |                           |  9. Response interceptor     |                        |
  |                           |     checks code === 200      |                        |
  |                           |                              |                        |
  |  10. Render table <-------|                              |                        |
```

### Key Components

| Step | File | Logic |
|------|------|-------|
| API function | `src/api/user.ts` | `listUsers(page, size)` -> `GET /business/user/list` |
| Axios instance | `src/api/request.ts` | Base URL `/api`, timeout 15s, auth header |
| Vite proxy | `vite.config.ts` | `/api` -> `http://localhost:8090` |
| Gateway route | `application.yml` (gateway) | `Path=/api/business/**` -> `lb://omni-business`, `StripPrefix=2` |
| Load balancer | Spring Cloud LoadBalancer | Resolves `omni-business` via Nacos service registry |
| Controller | `UserController.java` | `@GetMapping("/list")` -> calls `UserService.listUsers()` |
| Service | `UserService.java` / `UserServiceImpl.java` | Returns `PageResult<Map<String, Object>>` (stub data) |
| Response wrapper | `R.java` | `R.ok(pageResult)` -> `{ code: 200, message: "success", data: {...} }` |
| Response interceptor | `src/api/request.ts` | Checks `code === 200`, rejects on error |

### Response Shape

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      { "id": 1, "username": "demo", "email": "demo@example.com" }
    ],
    "total": 1,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
```

### Current Status

- **Stub data**: `UserService` returns hardcoded `HashMap` data, no database integration
- **TODO**: `[business] Replace with actual database query` in `UserServiceImpl.java`

---

## Flow 3: Form Submission (Create)

### Sequence

```
Browser                    Frontend                     Gateway :8090            Business :8081
  |                           |                              |                        |
  |  1. Fill form             |                              |                        |
  |  2. Click "Submit" ------>|                              |                        |
  |                           |  3. Client-side validation   |                        |
  |                           |     (Element Plus rules)     |                        |
  |                           |                              |                        |
  |                           |  4. Axios POST               |                        |
  |                           |  /api/business/user          |                        |
  |                           |  { username, email } ------->|                        |
  |                           |                              |                        |
  |                           |                              |  5. StripPrefix=2      |
  |                           |                              |  POST /user ----------->|
  |                           |                              |                        |
  |                           |                              |                        |  6. UserController
  |                           |                              |                        |     @Valid CreateUserRequest
  |                           |                              |                        |        |
  |                           |                              |                        |  [A] Validation OK:
  |                           |                              |                        |     UserService.createUser()
  |                           |                              |                        |     R.ok()
  |                           |                              |                        |        |
  |                           |                              |                        |  [B] Validation FAIL:
  |                           |                              |                        |     MethodArgumentNotValidException
  |                           |                              |                        |     -> GlobalExceptionHandler
  |                           |                              |                        |     -> R.fail(400, fieldErrors)
  |                           |                              |                        |        |
  |                           |                              |  7. R<Void> <----------|
  |                           |                              |<-----------------------|
  |                           |                              |                        |
  |                           |  8. Response interceptor     |                        |
  |                           |     [A] code=200: success    |                        |
  |                           |     [B] code=400: show error |                        |
  |                           |                              |                        |
  |  9. Show result <---------|                              |                        |
```

### Key Components

| Step | File | Logic |
|------|------|-------|
| Form validation | Frontend view | Element Plus `FormRules`: required fields, format checks |
| API call | `src/api/user.ts` | `createUser(data)` -> `POST /business/user` |
| Gateway routing | `application.yml` (gateway) | Same as Flow 2 |
| Controller validation | `UserController.java` | `@Valid @RequestBody CreateUserRequest` triggers Jakarta Validation |
| Validation failure | `GlobalExceptionHandler.java` | Catches `MethodArgumentNotValidException`, aggregates field errors into message |
| Service call | `UserServiceImpl.java` | `createUser(username, email)` (stub: no-op) |
| Success response | `R.java` | `R.ok()` -> `{ code: 200, message: "success", data: null }` |
| Error display | `src/api/request.ts` | Interceptor shows `ElMessage.error(res.message)` |

### Validation Error Response

```json
{
  "code": 400,
  "message": "username: Username is required; email: Email is required"
}
```

### Current Status

- **Create is a no-op**: `UserServiceImpl.createUser()` does nothing (no database)
- **TODO**: `[business] Replace with actual database insert` in `UserServiceImpl.java`
