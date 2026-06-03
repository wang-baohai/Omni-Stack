# Core Flows

This document traces the essential user-facing flows end-to-end, from browser interaction to backend processing and back. Use this as a reference when implementing or modifying features.

## Flow 1: User Login (Username + Password + Captcha + JWT)

### Overview

用户通过前端登录页面提交用户名、密码和验证码，经 Gateway 转发到 Auth 服务完成认证，
返回 JWT Token 用于后续请求的身份认证。支持多租户登录（`tenantId:username` 格式）。

### Sequence

```mermaid
sequenceDiagram
    participant B as Browser
    participant F as Frontend :3000
    participant G as Gateway :8102
    participant A as Auth :8100
    participant R as Redis
    participant M as MySQL

    B->>F: 1. Open login page
    F->>G: 2. GET /api/auth/captcha
    G->>A: 3. Proxy to Auth
    A->>A: 4. SpecCaptcha.generate()
    A->>R: 5. SET captcha:{key} = text (TTL=300s)
    A-->>F: 6. {captchaKey, captchaImage(base64)}
    F-->>B: 7. Show captcha

    F->>G: 8. GET /api/auth/tenants
    G->>A: 9. Proxy to Auth
    A->>M: 10. SELECT * FROM sys_tenant WHERE status=1
    M-->>A: tenant list
    A-->>F: 11. [{id, name, code}]
    F-->>B: 12. Tenant dropdown

    B->>F: 13. Fill form & Click Login
    F->>G: 14. POST /api/auth/login {username, password, tenantId, captchaKey, captchaCode}
    G->>A: 15. Proxy to Auth
    A->>R: 16. GET + DEL captcha (one-time use)
    A->>A: 17. Build "tenantId:username"
    A->>M: 18. LoadUserByUsername (WHERE tenant_id=? AND username=? AND status=1)
    M-->>A: user record
    A->>A: 19. BCrypt password check
    A->>M: 20. Load roles & permissions
    M-->>A: roles/permissions
    A->>A: 21. Generate JWT (RS256 sign)
    A-->>F: 22. {accessToken, tokenType, expiresIn}
    F->>F: 23. Store token + username to localStorage
    F-->>B: 24. Redirect to dashboard
```

<details>
<summary>ASCII 版本（点击展开）</summary>

```
Browser            Frontend :3000          Gateway :8102          Auth :8100           Redis              MySQL
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

</details>

### Post-Login: Authenticated Request Flow

登录成功后，前端所有 API 请求自动携带 JWT Token，Gateway 负责验证：

```
Browser            Frontend               Gateway :8102           Downstream Service
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
| Vite proxy | `vite.config.ts` | `/api` -> `http://localhost:8102` (Gateway) |
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

## Flow 2: OAuth2 Authorization Code + PKCE Login

### Overview

前端作为 OAuth2 公共客户端（SPA），通过 Spring Authorization Server 的 OAuth2 授权端点完成 PKCE 授权码流程。
用户在 Auth 服务的授权确认页面同意后，前端用授权码 + code_verifier 换取 access_token 和 id_token。
适用于第三方集成或需要 OAuth2 标准化认证的场景。

### Sequence

```mermaid
sequenceDiagram
    participant B as Browser
    participant F as Frontend :3000
    participant G as Gateway :8102
    participant A as Auth :8100 (Authorization Server)
    participant M as MySQL

    B->>F: 1. Click "OAuth2 Login"
    F->>F: 2. Generate PKCE: code_verifier + code_challenge (SHA256)
    F->>F: 3. Store {pkce_verifier, pkce_state} in sessionStorage
    F->>G: 4. Redirect to /api/oauth2/authorize?response_type=code&client_id=...&code_challenge=...&code_challenge_method=S256
    G->>A: 5. Proxy to Auth authorization endpoint
    A-->>B: 6. Login form page (or session-based redirect if already logged in)
    B->>A: 7. Submit credentials (username + password)
    A->>M: 8. Authenticate user (multi-tenant)
    M-->>A: user record
    A->>A: 9. Create authenticated session
    A-->>B: 10. Consent page (if required) or auto-approve
    B->>A: 11. User approves scopes
    A-->>F: 12. Redirect to callback: ?code=XXX&state=YYY
    F->>F: 13. Validate state matches sessionStorage
    F->>G: 14. POST /api/oauth2/token {grant_type=authorization_code, code, code_verifier}
    G->>A: 15. Proxy to token endpoint
    A->>A: 16. Validate code + verify PKCE (SHA256(code_verifier) == code_challenge)
    A->>M: 17. Store authorization record
    A-->>F: 18. {access_token, id_token, token_type, expires_in, refresh_token}
    F->>F: 19. Store tokens in localStorage
    F-->>B: 20. Redirect to dashboard
```

### Key Components

| Step | File | Logic |
|------|------|-------|
| PKCE generator | `src/utils/oauth2.ts` | 生成 code_verifier（43-128 字符随机串）+ SHA256 code_challenge |
| PKCE storage | `src/utils/oauth2.ts` | sessionStorage 存储 `pkce_verifier` 和 `pkce_state` |
| Authorization redirect | `src/utils/oauth2.ts` | 构造 `/oauth2/authorize` URL，携带 PKCE 参数 |
| Token exchange | `src/api/auth.ts` | POST `/oauth2/token`，code_verifier 发送给 Auth 服务验证 |
| Token storage | `src/stores/user.ts` | 存储 access_token + id_token 到 localStorage |
| Authorization endpoint | Spring Authorization Server | `/oauth2/authorize` — 登录表单 + 授权确认 |
| Token endpoint | Spring Authorization Server | `/oauth2/token` — 授权码换 Token |
| PKCE validator | Spring Authorization Server | SHA256(code_verifier) 与存储的 code_challenge 比对 |

### PKCE Storage Keys

| sessionStorage Key | Description | Lifecycle |
|--------------------|-------------|-----------|
| `pkce_verifier` | 随机 code_verifier 字符串 | 授权发起时写入，token 换取后删除 |
| `pkce_state` | CSRF 防护 state 参数 | 授权发起时写入，callback 验证后删除 |

### Current Status

- **Authorization Server**: Spring Authorization Server 7.x 已配置，RS256 JWK 签名
- **OAuth2 客户端**: `omni-spa` 客户端已注册（authorization_code + PKCE grant type）
- **前端 PKCE 工具**: `src/utils/oauth2.ts` 已实现 verifier/challenge 生成和 token 交换
- **Token 类型**: access_token (opaque) + id_token (JWT, 包含用户信息)
