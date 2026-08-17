# API 契约

> 本文档定义了前端与后端之间的权威 API 契约。双方必须遵守这些结构。任何偏离需要团队明确批准。  
> 社交登录完整流程详见 [core-flows.md](core-flows.md)。数据字典和工作流端点详见各自文档。

---

## 目录

- [1. 响应封装](#1-响应封装)
- [2. 错误码速查表](#2-错误码速查表)
- [3. 分页契约](#3-分页契约)
- [4. RESTful URL 规范](#4-restful-url-规范)
- [5. Gateway 路由配置](#5-gateway-路由配置)
- [6. 命名约定](#6-命名约定)
- [7. 时间格式](#7-时间格式)
- [8. 请求头约定](#8-请求头约定)
- [9. 认证头](#9-认证头)
- [10. 社交登录端点](#10-社交登录端点)
- [11. XSS 配置管理端点](#11-xss-配置管理端点)
- [12. Base 服务字典管理端点](#12-base-服务字典管理端点)
- [13. API 版本管理策略](#13-api-版本管理策略)
- [14. Null 语义](#14-null-语义)
- [15. SRM MVP 契约](#15-srm-mvp-契约)
- [16. Workflow 跨服务契约](#16-workflow-跨服务契约)
- [17. Procurement MVP 契约](#17-procurement-mvp-契约)
- [18. Asset MVP 契约](#18-asset-mvp-契约)

---

## 1. 响应封装

所有 API 响应使用统一的 `R<T>` 封装。

```json
// 成功
{
  "code": 200,
  "message": "success",
  "data": { ... }
}

// 失败（业务错误）
{
  "code": 500,
  "message": "操作失败"
}

// 失败（验证错误）
{
  "code": 400,
  "message": "username: 用户名不能为空; email: 邮箱不能为空"
}
```

### 后端类型：`R<T>`

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

**位置**：`omni-common-core` 模块，`com.omni.common.core.result.R`。

### 前端类型：`ApiResponse<T>`

```typescript
interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}
```

**权威位置**：`src/types/api.ts`（唯一真实来源；不要在其他文件中重复定义）。

---

## 2. 错误码速查表

### 2.1 系统级错误码

| HTTP 状态码 | 业务码 | 场景 | 触发条件 | 处理方 |
|------------|--------|------|---------|--------|
| 200 | 200 | 成功 | `R.ok(data)` | — |
| 400 | 400 | 参数校验失败 | `MethodArgumentNotValidException` / `BindException` 被 `GlobalExceptionHandler` 捕获 | 前端显示 `message` 中的字段错误 |
| 401 | 401 | 未认证 | Gateway `AuthFilter` 返回 401 JSON | 前端自动跳转登录页 |
| 403 | 403 | 权限不足 | `AccessDeniedException` / `AuthorizationDeniedException` 被 `GlobalExceptionHandler` 捕获 | 前端显示"权限不足"提示 |
| 200 | 404 | 资源不存在 | `throw new BusinessException(404, "xxx不存在")` | 前端显示错误信息 |
| 200 | 409 | 状态/并发冲突 | 乐观锁版本不一致或状态机拒绝迁移 | 前端刷新数据后提示用户重试 |
| 200 | 503 | 下游依赖不可用 | CRM 调用 Auth 数据范围接口失败关闭 | 前端提示服务暂不可用，不降级为越权数据 |
| 200 | 500 | 业务异常 | `BusinessException` 被 `GlobalExceptionHandler` 捕获 | 前端显示错误信息 |
| 500 | 500 | 未知系统错误 | 兜底 `Exception` 处理器 | 前端显示"服务器内部错误" |

### 2.2 业务级错误码

| 业务码 | 场景 | 消息示例 |
|--------|------|---------|
| 500 | 验证码无效/过期 | "验证码已过期" |
| 500 | 认证失败 | "用户名或密码错误" |
| 500 | 账号被禁用 | "账号已被禁用" |
| 500 | 账号被锁定 | "账号已锁定，请 N 分钟后重试" |
| 500 | 租户不存在/禁用 | "租户不存在或已禁用" |
| 400 | 唯一性冲突 | "用户名已存在" / "任务类型编码已存在" |
| 404 | 资源不存在 | "组织单元不存在" / "字典数据不存在" |
| 403 | 权限不足 | "权限不足，拒绝访问" |
| 409 | 乐观锁或状态冲突 | "数据已被其他用户修改，请刷新后重试" |
| 503 | 必需依赖不可用 | "认证授权服务暂不可用" |

### 2.3 Gateway 级错误码

| HTTP 状态码 | 场景 | 响应格式 |
|------------|------|---------|
| 401 | JWT 签名无效 | `{"code":401,"message":"Invalid JWT signature","data":null}` |
| 401 | JWT 已过期 | `{"code":401,"message":"JWT token expired","data":null}` |
| 401 | Token 已被撤销 | `{"code":401,"message":"Token has been revoked","data":null}` |
| 401 | 缺少 Authorization 头 | `{"code":401,"message":"Missing Authorization header","data":null}` |

### 2.4 社交登录错误码

| error 参数 | 含义 | 触发条件 |
|------------|------|---------|
| `user_denied` | 用户拒绝授权 | 第三方平台回调携带 `error=access_denied` |
| `invalid_callback` | 回调参数缺失 | code 或 state 为空 |
| `social_login_failed` | 登录流程异常 | state 验证失败、第三方 API 错误、用户信息获取失败、用户已禁用 |

### 2.5 前端错误处理流程

Axios 响应拦截器（`src/api/request.ts`）检查 `res.code !== 200`：
1. 显示 `ElMessage.error(res.message)` 错误提示
2. 当 code 为 `401` 时：调用 `userStore.logout()` 并重定向到 `/login`
3. 返回 `Promise.reject(new Error(res.message))`

**HTTP 状态码处理**：
- HTTP 401（Gateway JWT 验证失败）：Axios `onError` 拦截器捕获，清除 Token 并跳转登录页
- HTTP 403（权限不足）：显示 `ElMessage.error("权限不足")` 并返回上一页
- HTTP 400（参数校验失败）：显示 `GlobalExceptionHandler` 返回的字段级错误信息

---

## 3. 分页契约

### 后端类型：`PageResult<T>`

```java
@Data
public class PageResult<T> implements Serializable {
    private List<T> records;
    private long total;
    private long size;
    private long current;
    private long pages;   // 自动计算: (total + size - 1) / size

    public PageResult(List<T> records, long total, long size, long current) { ... }
}
```

### 前端类型：`PageResult<T>`

```typescript
interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}
```

**权威位置**：`src/types/api.ts`。

### 使用模式

```java
// 后端 Controller
@GetMapping("/list")
public R<PageResult<UserVO>> listUsers(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int size) {
    return R.ok(userService.listUsers(page, size));
}
```

```typescript
// 前端 API 调用
export function listUsers(page: number, size: number) {
  return request.get<ApiResponse<PageResult<UserInfo>>>(
    `/auth/user/list?page=${page}&size=${size}`,
  )
}
```

### 分页参数约定

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `page` | int | 1 | 当前页码（从 1 开始） |
| `size` | int | 10 | 每页条数 |
| `records` | List | — | 当前页数据列表 |
| `total` | long | — | 总记录数 |
| `pages` | long | — | 总页数（自动计算） |

---

## 4. RESTful URL 规范

| 操作 | HTTP 方法 | URL 模式 | 示例 |
|------|-----------|---------|------|
| 按 ID 查询 | GET | `/{resource}/{id}` | `GET /user/1` |
| 分页列表 | GET | `/{resource}/list` | `GET /user/list?page=1&size=10` |
| 创建 | POST | `/{resource}` | `POST /user` |
| 更新 | PUT | `/{resource}/{id}` | `PUT /user/1` |
| 删除 | DELETE | `/{resource}/{id}` | `DELETE /user/1` |
| 批量操作 | POST | `/{resource}/batch` | `POST /user/batch` |

**Gateway 路径前缀**：所有前端请求使用 `/api/<service>/<resource>`（如 `/api/auth/user/list`）。当前 Gateway 对 Auth、Base、Workflow、CRM、SRM、Procurement 与 Asset 等业务路由均不使用 `StripPrefix`，下游 Controller 声明并接收完整 `/api/**` 路径。

---

## 5. Gateway 路由配置

### 5.1 本地开发环境路由

Gateway `application.yml` 中的路由配置（`spring.cloud.gateway.server.webflux.routes`）：

| 路由 ID | 路径匹配 | 目标服务 | StripPrefix | 说明 |
|---------|---------|---------|-------------|------|
| `omni-auth-oauth2` | `/oauth2/**` | `lb://omni-auth` | 无 | OAuth2 授权服务器端点 |
| `omni-auth-wellknown` | `/.well-known/**` | `lb://omni-auth` | 无 | OpenID Connect 发现端点 |
| `omni-auth` | `/api/auth/**` | `lb://omni-auth` | **无** | Auth 服务 REST API（使用完整路径） |
| `omni-base` | `/api/base/**` | `lb://omni-base` | **无** | Base 服务（使用完整路径） |
| `omni-base-job` | `/api/job/**` | `lb://omni-base` | **无** | 定时任务管理 |
| `omni-workflow` | `/api/workflow/**` | `lb://omni-workflow` | **无** | 工作流引擎 |

### 5.2 Docker 部署路由

Docker 部署时，路由配置相同，但目标服务的 URI 通过 Nacos 服务发现自动解析：

| 前端请求 | Gateway 路由 | 下游接收路径 | 说明 |
|---------|-------------|-------------|------|
| `GET /api/auth/user/list` | `lb://omni-auth` 无 StripPrefix | `GET /api/auth/user/list` | Auth 服务保留完整路径 |
| `GET /api/base/dict/type/list` | `lb://omni-base` 无 StripPrefix | `GET /api/base/dict/type/list` | Base 服务保留完整路径 |
| `POST /api/workflow/model` | `lb://omni-workflow` 无 StripPrefix | `POST /api/workflow/model` | Workflow 服务保留完整路径 |
| `GET /api/job/type/list` | `lb://omni-base` 无 StripPrefix | `GET /api/job/type/list` | Job 路由到 Base 服务 |

### 5.3 AuthFilter 白名单路径

以下路径跳过 JWT 验证（`AuthFilter` 不拦截）：

```
/api/auth/login          — 登录
/api/auth/register       — 注册
/api/auth/captcha        — 验证码
/api/auth/tenants        — 租户列表
/api/auth/oauth2/        — 社交登录
/actuator/               — 健康检查
/oauth2/                 — OAuth2 端点
/.well-known/            — OIDC 发现
/login                   — Spring Security 登录
/error                   — 错误页面
```

---

## 6. 命名约定

### 请求/响应 DTO

| 类型 | 后缀 | 示例 |
|------|------|------|
| 创建请求 | `CreateXxxRequest` | `CreateUserRequest` |
| 更新请求 | `UpdateXxxRequest` | `UpdateUserRequest` |
| 视图对象 | `XxxVO` | `UserVO` |
| 查询参数 | `XxxQuery` | `UserQuery` |

DTO 可以定义为 Controller 的静态内部类（简单场景）或独立文件（复杂场景）。

### 字段命名

- Java 字段：`lowerCamelCase`（如 `createTime`、`userName`）
- JSON 序列化：`lowerCamelCase`（直接匹配 Java 字段名）
- URL 路径段：`kebab-case` 或单个词（如 `/user/list`，不是 `/user/getAllUsers`）

---

## 7. 时间格式

在 `JacksonConfig.java` 中配置：

| Java 类型 | JSON 格式 | 示例 |
|-----------|----------|------|
| `LocalDateTime` | `yyyy-MM-dd HH:mm:ss` | `2026-05-28 14:30:00` |
| `LocalDate` | `yyyy-MM-dd` | `2026-05-28` |

时间戳序列化为字符串，不是数字时间戳（`WRITE_DATES_AS_TIMESTAMPS` 已禁用）。

**配置位置**：`omni-common` 模块的 `JacksonConfig`，通过 `AutoConfiguration.imports` 自动生效。所有依赖 `omni-common` 的服务自动获得一致的时间格式。

---

## 8. 请求头约定

### 8.1 Gateway 注入的请求头

Gateway 的 `AuthFilter` 在 JWT 验证成功后，向下游请求注入以下请求头：

| 请求头 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| `X-User-Id` | String | 用户 ID | `"1"` |
| `X-User-Name` | String | 用户名 | `"admin"` |
| `X-Tenant-Id` | String | 租户 ID | `"1"` |
| `X-User-Roles` | String | 逗号分隔的角色编码 | `"SUPER_ADMIN,DEPT_LEADER"` |
| `X-User-Scopes` | String | 空格/逗号分隔的权限编码 | `"dict:type:list dict:data:create"` |

### 8.2 前端发送的请求头

| 请求头 | 来源 | 说明 |
|--------|------|------|
| `Authorization: Bearer <JWT>` | Axios 拦截器自动注入 | 从 `useUserStore()` 获取 Token |
| `X-Tenant-Id` | Axios 拦截器自动注入 | 从 `useUserStore()` 获取租户 ID |
| `Content-Type: application/json` | Axios 默认 | JSON 请求体 |

### 8.3 内部服务请求头

所有服务间接口统一放在 `/api/internal/**` 下，使用共享令牌认证，不使用终端用户 JWT：

| 请求头 | 必填 | 说明 |
|--------|------|------|
| `X-Internal-Token` | 是 | 服务间共享令牌；由 `InternalApiAuthFilter` 校验 |
| `X-Tenant-Id` | 是 | 当前业务租户；必须与 body/query 中的 `tenantId` 一致 |
| `Content-Type: application/json` | JSON 请求必填 | JSON 请求体 |

`InternalApiAuthFilter` 作为容器级前置过滤器统一保护 `/api/internal/**`，服务安全链不得再次要求
Gateway 用户身份。令牌缺失或不匹配返回 HTTP 401；服务端未配置令牌时失败关闭并返回 HTTP 503；
请求头与 body/query 租户不一致返回业务码 403。内部路径不得依赖 `X-Gateway-Forwarded` 或用户权限头。

### 8.4 安全响应头（Gateway 注入）

`SecurityHeadersFilter`（WebFlux WebFilter）为所有经过网关的响应添加：

| 响应头 | 值 | 用途 |
|--------|-----|------|
| `X-Content-Type-Options` | `nosniff` | 防止浏览器 MIME 类型嗅探 |
| `X-Frame-Options` | `DENY` | 禁止页面被 iframe 嵌套，防止点击劫持 |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | 控制 Referer 头泄露 |
| `X-Trace-Id` | 32 位小写十六进制字符串 | 关联 Gateway、Servlet、Feign 与错误反馈 |

Gateway 始终生成新的 `X-Trace-Id`，不信任公网客户端提供的同名头；下游 Servlet 服务把合法值写入
MDC 和响应头，公共 Feign 拦截器继续向内部调用传播。前端错误面板可展示响应中的 traceId，日志排查
应优先使用同一值检索完整调用链。

---

## 9. 认证头

```
Authorization: Bearer <token>
```

- 由 Axios 请求拦截器（`src/api/request.ts`）使用 `useUserStore()` 中的 Token 设置
- 由 `omni-gateway` 中的 `AuthFilter` 验证（JWT RS256 签名验证 + claims 提取 + 身份头注入）
- 公开路径免认证：`/api/auth/**`、`/actuator/**`、`/favicon.ico`

---

## 10. 社交登录端点

社交登录端点返回 HTTP 302 重定向（非标准 `R<T>` 响应），因为前端通过 `window.location.href` 触发浏览器导航。

| HTTP 方法 | URL | 说明 |
|-----------|-----|------|
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
# GitHub/Google/Gitee 回调
GET /api/auth/oauth2/{provider}/callback?code=XXX&state=YYY

→ 成功: 302 Location: /callback#token=<JWT>&username=<username>
→ 失败: 302 Location: /login?error=<error_code>&message=<message>
```

### Docker 部署下的 OAuth2 回调 URL 配置

Docker 部署时，社交登录的 `redirect_uri` 需要使用**宿主机可访问的 URL**：

| 部署环境 | redirect_uri 示例 |
|---------|------------------|
| 本地开发 | `http://localhost:8100/api/auth/oauth2/github/callback` |
| Docker 部署 | `http://<宿主机IP>:8102/api/auth/oauth2/github/callback` |
| 生产环境 | `https://your-domain.com/api/auth/oauth2/github/callback` |

> **注意**：Docker 部署中，第三方回调统一进入 Gateway 的宿主机端口 8102；生产环境使用 Frontend/Gateway 的 HTTPS 域名。不要把私有 Auth 容器端口注册为公网回调。

### 前端回调页面

`/callback` 页面（`src/views/callback/index.vue`）负责：
1. 解析 URL fragment 中的 `token` 和 `username`
2. 存储到 `localStorage`（通过 `useUserStore`）
3. 重定向到 Dashboard

> 完整流程时序图详见 [core-flows.md](core-flows.md) Flow 4。

---

## 11. XSS 配置管理端点

Base path: `/api/auth/xss-config`（Gateway 不做 StripPrefix，下游保持完整路径）

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

### 规则 CRUD

| HTTP 方法 | URL | 权限码 | 说明 |
|-----------|-----|--------|------|
| GET | `/api/auth/xss-config/rules/list?page=1&size=10` | `system:xssconfig:list` | 分页列表 |
| POST | `/api/auth/xss-config/rules` | `system:xssconfig:create` | 创建规则 |
| PUT | `/api/auth/xss-config/rules/{id}` | `system:xssconfig:update` | 更新规则 |
| DELETE | `/api/auth/xss-config/rules/{id}` | `system:xssconfig:delete` | 删除规则 |
| PUT | `/api/auth/xss-config/rules/{id}/toggle` | `system:xssconfig:update` | 切换规则启用状态 |

**ruleType 枚举值**：`HTML_TAG` | `EVENT_HANDLER` | `DANGEROUS_PROTOCOL` | `CUSTOM_PATTERN`

### 权限码

| 权限码 | 说明 |
|--------|------|
| `system:xssconfig:list` | 查看 XSS 配置和规则 |
| `system:xssconfig:update` | 切换全局开关、更新规则、切换规则状态 |
| `system:xssconfig:create` | 创建规则 |
| `system:xssconfig:delete` | 删除规则 |

---

## 12. Base 服务字典管理端点

Base 服务（`omni-base :8101`）提供数据字典管理，采用「类型 + 数据」两级结构。

**路由说明**：Gateway 路由 `Path=/api/base/**` **无** StripPrefix 过滤器，Base 服务控制器使用完整路径（如 `@RequestMapping("/api/base/dict/type")`）。

### Dictionary Type 端点

Base path: `/api/base/dict/type`

| HTTP 方法 | URL | 权限码 | 说明 |
|-----------|-----|--------|------|
| GET | `/api/base/dict/type/list?page=1&size=10&typeCode=&typeName=&status=` | `dict:type:list` | 分页列表，支持过滤 |
| GET | `/api/base/dict/type/{id}` | `dict:type:list` | 按 ID 查询 |
| POST | `/api/base/dict/type` | `dict:type:create` | 创建（验证 tenant 内 typeCode 唯一） |
| PUT | `/api/base/dict/type/{id}` | `dict:type:update` | 更新（部分更新） |
| DELETE | `/api/base/dict/type/{id}` | `dict:type:delete` | 删除（级联删除关联数据） |
| PUT | `/api/base/dict/type/{id}/status` | `dict:type:update` | 切换启用/禁用 |

**示例请求**：

```
GET /api/base/dict/type/list?page=1&size=10
Authorization: Bearer <token>
X-Tenant-Id: 1

Response 200:
{
  "code": 200,
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

### Dictionary Data 端点

Base path: `/api/base/dict/data`

| HTTP 方法 | URL | 权限码 | 说明 |
|-----------|-----|--------|------|
| GET | `/api/base/dict/data/list?typeCode=sys_user_gender&page=1&size=10` | `dict:data:list` | 按 typeCode 分页查询 |
| POST | `/api/base/dict/data` | `dict:data:create` | 创建（验证父类型存在） |
| PUT | `/api/base/dict/data/{id}` | `dict:data:update` | 更新（部分更新） |
| DELETE | `/api/base/dict/data/{id}` | `dict:data:delete` | 删除单条 |
| POST | `/api/base/dict/data/refresh-cache` | `dict:data:refresh` | 手动刷新 Redis 缓存 |

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

### 字典权限码

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

### 租户隔离

所有列表查询和创建操作要求 `X-Tenant-Id` 请求头（前端从 JWT Token 提取，Gateway 注入）。数据在 SQL 查询层按 `tenant_id` 隔离。字典类型唯一性约束范围为 `(tenant_id, type_code)`。

---

## 13. API 版本管理策略

### 当前决策

脚手架阶段不使用 URL 版本号。API 稳定后且有多个消费者时，引入前缀版本控制。

### 未来演进路径

| 阶段 | 版本策略 | URL 示例 |
|------|---------|---------|
| **当前（脚手架）** | 无版本号 | `/api/auth/user/list` |
| **V1（API 稳定后）** | URL 前缀版本 | `/api/v1/auth/user/list` |
| **V2（Breaking Change）** | URL 前缀版本 | `/api/v2/auth/user/list` |

**版本规则**：
- 新增字段（向后兼容）：不需要版本号变更
- 删除/重命名字段：需要新版本
- 变更请求/响应结构：需要新版本
- 旧版本至少维护 6 个月

---

## 14. Null 语义

- `null` 字段包含在 JSON 输出中（不省略）
- 空集合返回为 `[]`，不是 `null`
- 可选的单个值使用 `null` 表示不存在，不使用空字符串

---

## 15. SRM MVP 契约

### 15.1 供应商与子资源

- 管理端供应商生命周期命令均携带 `version`；黑名单恢复使用
  `POST /api/srm/supplier/{id}/restore-from-blacklist`。
- 联系人、资质、银行账户路径中的 `supplierId` 必须与子资源实际归属一致；不一致统一返回 404。
- `creditCode` 在租户内唯一；分页 `size` 最大为 100。
- 供应商 360 使用 `GET /api/srm/supplier/{id}/overview`，返回内容仍按调用人的子资源权限和 PII 权限裁剪。

### 15.2 门户入驻

`POST /api/srm/portal/enroll` 仅接受 Gateway 注入的 tenant/user 身份，请求不得携带 tenantId 或 userId：

```json
{
  "requestId": "client-generated-uuid",
  "inviteToken": "raw-token-returned-once",
  "name": "示例供应商有限公司",
  "creditCode": "91320000EXAMPLE"
}
```

响应中的状态只使用：`PENDING_ROLE_ASSIGN`、`ROLE_ASSIGN_FAILED`、`COMPLETED`、`CANCELLED`。
当前用户可通过 `GET /api/srm/portal/enrollment` 查询状态，并在失败后调用
`POST /api/srm/portal/enrollment/retry` 幂等重试。角色分配完成前不创建 PortalUser，也不开放企业资料接口。

### 15.3 评估与风险

- `GET /api/srm/evaluation/template/default/dimensions` 返回当前租户默认模板及有效维度，前端不得硬编码数据库 ID。
- 评分范围为 1–5，必须覆盖默认模板的全部维度且不得重复。
- `GET /api/srm/risk/list` 返回按供应商最新评估聚合的风险摘要分页，可按 `riskLevel` 筛选。
- `GET /api/srm/supplier/{id}/risk` 返回 `indicators/latestAssessment/history` 聚合视图。
- 风险指标更新携带 `version`；仅综合等级从非 RED 变为 RED 时产生 `srm.risk.level-changed.v1`。

### 15.4 内部供应商摘要

后续 Procurement/Asset 只能同时携带 `X-Internal-Token` 与 `X-Tenant-Id` 调用：

- `GET /api/internal/supplier/{id}?tenantId={tenantId}`
- `GET /api/internal/supplier/search?tenantId={tenantId}&status=APPROVED&categoryCode={code}&limit=50`
- `POST /api/internal/supplier/batch`

GET 的 query tenantId、batch 的 body tenantId 必须与 `X-Tenant-Id` 完全一致，否则返回 403。batch 请求体为：

```json
{
  "tenantId": 1,
  "supplierIds": [101, 102, 101]
}
```

`supplierIds` 必须包含 1–100 个正整数；服务端按首次出现顺序去重并保持返回顺序，不存在或已删除的 ID 从结果中省略，不因单项缺失使整个请求返回 404。响应仅包含供应商 `id/supplierNo/name/status/levelCode/categoryCode`，不返回联系人、银行账户或其他 PII。

### 15.5 供应商门户报价

门户端点需要 `srm:portal:quotation`、`SUPPLIER` 角色，并且当前用户必须存在有效的
`srm_supplier_portal_user` 关联。该权限节点只授予 `SUPPLIER` 与按平台规则拥有全部权限树的 `SUPER_ADMIN`；仅有 SUPER_ADMIN 角色不满足门户身份条件，不能代供应商报价：

- `GET /api/srm/portal/quotation/invitations`
- `GET /api/srm/portal/quotation/invitations/{rfqId}`
- `POST /api/srm/portal/quotation`

邀请列表使用 `R<List<RfqInvitationVO>>`，单项至少包含：

```json
{
  "rfqId": 1001,
  "rfqNo": "RFQ-202607-0001",
  "title": "办公电脑采购询价",
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

邀请详情在上述字段基础上返回 RFQ 行快照，并在已有报价时返回 `currentQuotation`：

```json
{
  "rfqId": 1001,
  "rfqNo": "RFQ-202607-0001",
  "title": "办公电脑采购询价",
  "status": "SENT",
  "invitationStatus": "INVITED",
  "quotationDeadline": "2026-07-31 18:00:00",
  "currencyCode": "CNY",
  "lines": [
    {
      "rfqLineId": 10011,
      "materialCode": "IT-LAPTOP-001",
      "materialName": "商务笔记本",
      "unit": "台",
      "quantity": "20.000000",
      "remark": "含三年保修"
    }
  ],
  "currentQuotation": null
}
```

提交请求：

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
      "remark": "到货后验收"
    }
  ]
}
```

| 字段 | 类型 | 必填 | 约束 |
|------|------|------|------|
| `requestId` | String | 是 | 最长 64；租户内唯一幂等键 |
| `rfqId` | Long | 是 | 正整数；必须存在当前供应商的有效邀请 |
| `version` | Integer | 是 | 首次提交为 0；修改时必须等于当前报价版本 |
| `validUntil` | LocalDateTime | 是 | `yyyy-MM-dd HH:mm:ss`；晚于当前时间且不得早于 quotationDeadline |
| `lines` | Array | 是 | 非空；rfqLineId 集合必须与邀请详情的 RFQ 行集合完全一致 |
| `lines[].rfqLineId` | Long | 是 | 正整数且不得重复 |
| `lines[].unitPrice` | Decimal String | 是 | 十进制字符串；`DECIMAL(19,6)`，大于 0，整数位最多 13 |
| `lines[].deliveryDays` | Integer | 是 | 0–3650 |
| `lines[].remark` | String | 否 | 最长 500，纯文本 |

请求不接受 `tenantId/supplierId/rfqNo/material/quantity/currencyCode/lineAmount/totalAmount`。这些字段分别从可信身份头、PortalUser、Procurement 邀请详情读取或由服务端按
`unitPrice × quantity` 计算；行金额与总金额按 `DECIMAL(19,4)` 保存。响应为 `R<QuotationVO>`，包含报价头、`version` 和所有行快照。

幂等与并发规则：

- `srm_quotation_request` 永久保存 `(tenantId, requestId)`、规范化请求体 SHA-256、quotationId 和 targetVersion；同 requestId、同 requestHash 重试返回当前报价快照，不再次写报价或 Outbox。
- 同 requestId 绑定不同 rfqId 或请求内容返回业务码 409。
- `(tenantId, rfqId, supplierId)` 最多一条未删除报价；首次请求使用创建哨兵 `version=0`，首版持久化并响应为 `version=1`，后续更新必须携带当前 version，过期版本或再次使用 0 均返回 409。
- 提交前必须重新验证 RFQ `status=SENT`、邀请 `status IN (INVITED, QUOTED)`、截止时间和行集合；其他 RFQ 状态（`DRAFT/CLOSED/AWARDED/CANCELLED`）拒绝。Procurement 不可用时返回 503，不允许离线写入。

requestHash 不包含 requestId，本阶段规范化输入为
`rfqId/version/validUntil/lines`；lines 按 `rfqLineId` 升序，单价归一到 6 位小数并使用非科学计数法字符串，备注 trim 后将 null/空白统一为 null。服务端不得直接对原始 JSON 字节求 hash，以免字段顺序导致同意图误判。

### 15.6 SRM 与 Procurement 报价内部契约

SRM 查询邀请时调用 Procurement：

- `GET /api/internal/procurement/rfq/invitations?supplierId={supplierId}`
- `GET /api/internal/procurement/rfq/{rfqId}/invitation?supplierId={supplierId}`

列表项至少包含 `tenantId/rfqId/rfqNo/title/status/invitationStatus/supplierId/quotationDeadline/currencyCode/invitedTime`；详情增加
`lines[{rfqLineId,materialCode,materialName,unit,quantity,remark}]`。SRM 必须使用 PortalUser 关联得到的 supplierId。

Procurement 比价/定点时调用 SRM：

```http
GET /api/internal/quotation/batch?tenantId=1&rfqId=1001
X-Internal-Token: <shared-token>
X-Tenant-Id: 1
```

响应为 `R<List<QuotationVO>>`。`QuotationVO` 包含
`id/rfqId/rfqNo/supplierId/supplierNameSnapshot/quotationTime/validUntil/totalAmount/currencyCode/status/version/lines`；行包含
`id/rfqLineId/materialCode/materialName/unit/unitPrice/quantity/lineAmount/deliveryDays/remark`。只返回指定 tenant、RFQ 且供应商当前仍为 APPROVED 的未删除有效报价。门户邀请、报价响应及内部 batch 中的 `totalAmount/unitPrice/quantity/lineAmount` 一律使用 JSON 十进制字符串，禁止输出 JSON number，以免 JavaScript 高精度金额或数量丢失。

报价头、明细、`srm_quotation_request` 与 `srm.quotation.submitted.v1` Outbox 在同一事务提交。事件信封遵循
`eventId/eventType/occurredAt/tenantId/payload`，payload 至少包含
`requestId/quotationId/quotationVersion/rfqId/rfqNo/supplierId/status/totalAmount/currencyCode/validUntil`。Procurement 以 eventId Inbox 幂等消费，并拒绝用旧 quotationVersion 覆盖新版本。

---

## 16. Workflow 跨服务契约

Workflow 内部端点统一使用 §8.3 的 `X-Internal-Token` 与 `X-Tenant-Id`，响应继续使用标准
`R<T>`。详细运行机制见 [workflow.md](workflow.md#28-跨服务内部契约)。

### 16.1 幂等启动流程

```http
POST /api/internal/workflow/process-instance/start
X-Internal-Token: <shared-token>
X-Tenant-Id: 1
Content-Type: application/json
```

请求：

```json
{
  "requestId": "6d2f4d1a-41d7-4f68-a60a-8a2e9425a703",
  "tenantId": 1,
  "modelVersionId": 42,
  "businessType": "PROCUREMENT_REQUISITION",
  "businessKey": "10001:1",
  "startUserId": 7,
  "startUserName": "buyer",
  "title": "采购申请 PR-202607-0001",
  "variables": {
    "requisitionId": 10001,
    "approvalAttempt": 1,
    "materialCategory": "IT_EQUIPMENT",
    "totalAmount": "120000.0000",
    "requesterUnitId": 12
  }
}
```

| 字段 | 类型 | 必填 | 约束 |
|------|------|------|------|
| `requestId` | String | 是 | 非空，最长 64；调用方生成的幂等键 |
| `tenantId` | Long | 是 | 正整数，必须等于 `X-Tenant-Id` |
| `modelVersionId` | Long | 是 | 正整数，模型版本必须属于当前租户并存在 `processDefinitionId` |
| `businessType` | String | 是 | 非空，最长 100 |
| `businessKey` | String | 是 | 非空，最长 255 |
| `startUserId` | Long | 是 | 正整数 |
| `startUserName` | String | 否 | 最长 100 |
| `title` | String | 否 | 最长 500；空值自动生成为 `{businessType}:{businessKey}` |
| `variables` | Object | 否 | 流程变量；保留字段 `requestId/businessType/businessKey` 由服务覆盖 |

响应：

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

幂等性由两个租户内唯一键共同保证：

- `(tenantId, requestId)`：请求级幂等；同一请求 ID 不得绑定不同业务。
- `(tenantId, businessType, businessKey)`：业务级幂等；同一业务不得启动多个流程。
- 已成功的同意图重试返回原 `processInstanceId`，`replayed = true`。
- 正在处理、请求 ID 冲突、或业务键对应的 `modelVersionId/startUserId` 改变均返回业务码 409。

### 16.2 任务处理资格校验

```http
POST /api/internal/workflow/task/assignment/validate
X-Internal-Token: <shared-token>
X-Tenant-Id: 1
Content-Type: application/json
```

请求：

```json
{
  "tenantId": 1,
  "taskId": "25017",
  "userId": 7,
  "businessType": "PROCUREMENT_REQUISITION",
  "businessKey": "10001:1"
}
```

| 字段 | 类型 | 必填 | 约束 |
|------|------|------|------|
| `tenantId` | Long | 是 | 正整数，必须等于 `X-Tenant-Id` |
| `taskId` | String | 是 | 非空，最长 64 |
| `userId` | Long | 是 | 正整数 |
| `businessType` | String | 是 | 非空，最长 100 |
| `businessKey` | String | 是 | 非空，最长 255 |

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "valid": true,
    "processInstanceId": "22501",
    "assignmentType": "CANDIDATE",
    "message": "校验通过"
  }
}
```

服务必须同时匹配 Flowable 任务租户、实例扩展记录租户、`businessType + businessKey` 业务归属，
并确认 `userId` 是当前 `ASSIGNEE` 或未签收任务的 `CANDIDATE`。`assignmentType` 仅取
`ASSIGNEE`、`CANDIDATE`、`NONE`；任务不存在或任一边界不匹配时返回 `valid = false`。

### 16.3 流程完成事件

| 属性 | 值 |
|------|----|
| 事件类型 | `workflow.process.completed.v1` |
| 生产者 | `omni-workflow` |
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

| 字段 | 类型 | 说明 |
|------|------|------|
| `eventId` | String(UUID) | 事件 ID、Outbox `msgKey`、消费幂等键 |
| `eventType` | String | 固定为 `workflow.process.completed.v1` |
| `occurredAt` | LocalDateTime | 事件记录产生时间，格式 `yyyy-MM-dd HH:mm:ss` |
| `tenantId` | Long | 租户 ID |
| `producer` | String | 固定为 `omni-workflow` |
| `businessType` | String | 调用方业务类型 |
| `businessKey` | String | 调用方业务主键 |
| `processInstanceId` | String | Flowable 流程实例 ID |
| `result` | Enum | `APPROVED`、`REJECTED`、`CANCELLED` |
| `completedTime` | LocalDateTime | 流程实际完成或终止时间 |

完成状态和 `completionEventId` 的条件更新与 PENDING Outbox 记录在同一本地事务内提交；
`completion_event_id IS NULL` 是同一流程实例仅生成一次逻辑完成事件的数据库门闩。
提交后由可靠消息中继异步投递和重试，传输语义为至少一次，因此消费者必须按 `eventId` 幂等处理。

### 16.4 查询已发布模型版本

```http
GET /api/internal/workflow/model-version/{modelVersionId}
X-Internal-Token: <shared-token>
X-Tenant-Id: 1
```

成功响应：

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

端点只返回请求租户内仍可用的模型和版本。`modelKey` 是租户内唯一且与 BPMN process id
一致的模型标识；`category` 是跨服务业务分类。调用方除校验 `PUBLISHED` 和非空
`processDefinitionId` 外，还可以把稳定业务类型绑定到 `category`。Asset 强制要求调拨模型分类为
`ASSET_TRANSFER`，处置模型分类为 `ASSET_DISPOSAL`，不得交叉复用或使用其他业务模型；
Workflow 在实际创建资产审批实例前再次执行相同校验，关闭预校验后的模型变更窗口。

## 17. Procurement MVP 契约

### 17.1 通用边界

- 外部 Base path 为 `/api/procurement`，Gateway 保留完整路径，不使用 `StripPrefix`。
- 所有请求使用 Gateway 注入的 `X-User-Id`、`X-Tenant-Id` 和权限头；业务表同时受 TenantLine 与 permission-aware DataScope 约束。
- 数量、单价使用 `DECIMAL(19,6)`，金额使用 `DECIMAL(19,4)`；所有响应中的数量、单价和金额均为 JSON string，前端不得用 JavaScript `number` 计算业务金额。
- 更新 body 和删除 query 必须携带 `version`；版本冲突返回业务码 409。
- 内部端点统一使用 `/api/internal/procurement/**`，要求 `X-Internal-Token` 与 `X-Tenant-Id`，不得经 Gateway 暴露。

### 17.2 物料与品类

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/procurement/material/category/list` | `procurement:material:list` | 返回最多两级的品类树 |
| POST | `/api/procurement/material/category` | `procurement:material:create` | 创建品类；`categoryCode` 创建后不可修改 |
| PUT | `/api/procurement/material/category/{id}` | `procurement:material:update` | body 携带 `version` |
| DELETE | `/api/procurement/material/category/{id}?version={version}` | `procurement:material:delete` | 存在子品类或物料时返回 409 |
| GET | `/api/procurement/material/list` | `procurement:material:list` | `keyword/categoryId/status/assetManaged/page/size` |
| GET | `/api/procurement/material/{id}` | `procurement:material:list` | 查询物料详情 |
| POST | `/api/procurement/material` | `procurement:material:create` | 创建物料；`materialCode` 创建后不可修改 |
| PUT | `/api/procurement/material/{id}` | `procurement:material:update` | body 携带 `version` |
| DELETE | `/api/procurement/material/{id}?version={version}` | `procurement:material:delete` | 逻辑删除 |

`assetManaged=true` 时 `unit` 仅允许 `EA/PCS/UNIT/SET`；请购只能引用状态为 `ACTIVE` 且品类启用的物料。

### 17.3 审批路由

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/api/procurement/approval-route/list` | `procurement:approval-route:list` |
| POST | `/api/procurement/approval-route` | `procurement:approval-route:create` |
| PUT | `/api/procurement/approval-route/{id}` | `procurement:approval-route:update` |
| DELETE | `/api/procurement/approval-route/{id}?version={version}` | `procurement:approval-route:delete` |

路由请求包含 `routeCode/categoryCode/minAmount/maxAmount/modelVersionId/priority/status`；更新不接受 `routeCode`。`minAmount/maxAmount` 必须使用 JSON 十进制字符串（`maxAmount=null` 除外），JSON number 返回 400。活动区间使用 `minAmount <= amount < maxAmount`，`maxAmount=null` 表示无上限。同品类活动区间不得重叠，写事务以租户配置行锁串行化校验。`modelVersionId` 写入前必须属于当前租户、状态为 `PUBLISHED` 且已有非空 `processDefinitionId`，创建新流程实例前 Workflow 再次校验。提交请购时优先选择精确 `categoryCode`，无匹配才回退 `*`；匹配零条或多条均返回 409。

### 17.4 请购申请

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/procurement/requisition/list` | `procurement:requisition:list` | `keyword/status/categoryCode/page/size` |
| GET | `/api/procurement/requisition/{id}` | `procurement:requisition:list` | 普通详情仍受 requester DataScope 约束 |
| GET | `/api/procurement/requisition/{id}/approval-view?taskId={taskId}` | `procurement:requisition:approve` | 先由 Workflow 校验任务属于当前用户和本请购 |
| POST | `/api/procurement/requisition` | `procurement:requisition:create` | 创建 DRAFT |
| PUT | `/api/procurement/requisition/{id}` | `procurement:requisition:update` | 仅 DRAFT/REJECTED；REJECTED 更新后回到 DRAFT |
| DELETE | `/api/procurement/requisition/{id}?version={version}` | `procurement:requisition:delete` | 仅 DRAFT |
| POST | `/api/procurement/requisition/{id}/submit` | `procurement:requisition:submit` | body `{ "version": 0 }` |
| POST | `/api/procurement/requisition/{id}/retry-start` | `procurement:requisition:submit` | 仅 `SUBMITTED + FAILED`，复用原 Workflow 幂等快照 |
| POST | `/api/procurement/requisition/{id}/cancel` | `procurement:requisition:cancel` | 仅 DRAFT 或 `SUBMITTED + FAILED` |

创建/更新请求示例：

```json
{
  "title": "研发笔记本采购",
  "reason": "新员工入职",
  "lines": [
    {
      "materialId": 101,
      "quantity": "2.000000",
      "estimatedUnitPrice": "8500.000000",
      "remark": "16GB 内存以上"
    }
  ]
}
```

`lines[].quantity` 和 `lines[].estimatedUnitPrice` 只接受 JSON 十进制字符串；即使数值未超出 JavaScript 安全范围，JSON number 也返回 400。

MVP 要求所有行属于同一品类。服务在提交事务中批量回查活动物料和品类，刷新物料编码、名称、品类、单位快照并重算行金额与总金额；客户端不能传总金额或 `modelVersionId`。每次新提交令 `approvalAttempt + 1`，Workflow `businessKey={requisitionId}:{approvalAttempt}`；启动不确定失败后 retry 必须复用已持久化的 `requestId/businessKey/modelVersionId`。

Workflow 完成事件按 `eventId` 进入 `proc_event_inbox`。当前轮次事件只有在 tenant、businessKey、processInstanceId 和 `APPROVING` 状态全部匹配时才更新请购；旧轮次事件幂等忽略，早于本地启动确认的事件回滚 Inbox 并触发消息重试，同 eventId 绑定不同完整 payload 返回 409。

### 17.5 询价、比价与定点

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/procurement/rfq/supplier-options` | `procurement:rfq:create` 或 `procurement:rfq:list` | 查询当前租户 SRM 合格供应商选项 |
| GET | `/api/procurement/rfq/list` | `procurement:rfq:list` | `keyword/status/deadlineFrom/deadlineTo/page/size` |
| GET | `/api/procurement/rfq/{id}` | `procurement:rfq:list` | 查询 RFQ、行与邀请快照 |
| GET | `/api/procurement/rfq/{id}/comparison` | `procurement:rfq:list` | 从 SRM 重新读取当前有效报价与完整行快照 |
| POST | `/api/procurement/rfq` | `procurement:rfq:create` | 从一张已审批请购创建 DRAFT |
| PUT | `/api/procurement/rfq/{id}` | `procurement:rfq:update` | 仅 DRAFT；body 携带 `version` |
| DELETE | `/api/procurement/rfq/{id}?version={version}` | `procurement:rfq:delete` | 仅 DRAFT |
| POST | `/api/procurement/rfq/{id}/send` | `procurement:rfq:send` | body `{ "version": 0 }`，向受邀供应商发布 |
| POST | `/api/procurement/rfq/{id}/award` | `procurement:rfq:award` | 锁定报价版本并原子生成采购订单 |
| POST | `/api/procurement/rfq/{id}/cancel` | `procurement:rfq:cancel` | body `{ "version": 0 }`；仅 DRAFT/SENT |

创建和更新请求包含 `requisitionId/title/quotationDeadline/supplierIds`；更新时间格式统一为
`yyyy-MM-dd HH:mm:ss`。只有 `SENT` RFQ 可以比价和定点。邀请状态为
`INVITED/QUOTED/EXPIRED/AWARDED/REJECTED`；定点后中标邀请变为 `AWARDED`，其余邀请变为
`REJECTED`，这些终态仅供供应商门户历史查看，不能继续报价。

定点请求示例：

```json
{
  "rfqVersion": 2,
  "quotationId": 501,
  "quotationVersion": 3,
  "title": "研发笔记本采购订单",
  "expectedDeliveryDate": "2026-08-15",
  "deliveryAddress": "上海市浦东新区示例路 1 号",
  "contactName": "张三",
  "contactPhone": "13800000000"
}
```

服务端在同一事务中锁定 RFQ 与邀请，再从 SRM 回查 `quotationId` 的当前版本、tenant、供应商、
币种、有效期及完整行集合；`rfqVersion` 或 `quotationVersion` 任一不匹配均返回 409。成功响应为
`{ "rfq": ..., "purchaseOrder": ... }`，并保存不可变报价金额/交期快照；SRM 后续报价变化不得改变
既有定点或采购订单。报价比较响应中的数量、单价和金额均为 JSON 十进制字符串。

### 17.6 采购订单

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/procurement/purchase-order/list` | `procurement:purchase-order:list` | `keyword/status/expectedDeliveryFrom/expectedDeliveryTo/page/size` |
| GET | `/api/procurement/purchase-order/{id}` | `procurement:purchase-order:list` | 查询订单及不可变报价行快照 |
| PUT | `/api/procurement/purchase-order/{id}` | `procurement:purchase-order:update` | 仅 DRAFT，可修改标题和交付信息 |
| DELETE | `/api/procurement/purchase-order/{id}?version={version}` | `procurement:purchase-order:delete` | 仅 DRAFT |
| POST | `/api/procurement/purchase-order/{id}/send` | `procurement:purchase-order:send` | DRAFT → SENT，body 携带 `version` |
| POST | `/api/procurement/purchase-order/{id}/confirm` | `procurement:purchase-order:confirm` | SENT → CONFIRMED，body 携带 `version` |
| POST | `/api/procurement/purchase-order/{id}/cancel` | `procurement:purchase-order:cancel` | 未发生收货前取消，body 携带 `version` |

外部 API 不提供采购订单创建端点；MVP 订单只能由 RFQ 定点事务生成，客户端不能伪造供应商、报价或
订单行。状态为 `DRAFT/SENT/CONFIRMED/PARTIAL_RECEIVED/RECEIVED/CLOSED/CANCELLED`。
列表中的地址、联系人和手机默认脱敏，详情仍受 owner DataScope 约束；数量、单价、行金额和总金额
始终以 JSON 十进制字符串返回。

### 17.7 收货与质检

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/procurement/goods-receipt/list` | `procurement:goods-receipt:list` | `keyword/status/receiveTimeFrom/receiveTimeTo/page/size` |
| GET | `/api/procurement/goods-receipt/{id}` | `procurement:goods-receipt:list` | 查询收货详情 |
| POST | `/api/procurement/goods-receipt` | `procurement:goods-receipt:create` | 为 CONFIRMED/PARTIAL_RECEIVED 订单创建 DRAFT |
| POST | `/api/procurement/goods-receipt/{id}/confirm` | `procurement:goods-receipt:confirm` | body `{ "version": 0 }`，确认收货并更新订单累计状态 |
| POST | `/api/procurement/goods-receipt/{id}/quality-result` | `procurement:goods-receipt:confirm` | 仅把已确认收货的 PENDING 行改为 PASS/FAIL |

创建请求的 `receiveTime` 使用 `yyyy-MM-dd HH:mm:ss`，每行包含
`poLineId/receivedQuantity/qualityStatus/remark`。`receivedQuantity` 只接受 JSON 十进制字符串，JSON
number 返回 400。创建 DRAFT 不占用已收数量；确认事务锁定采购订单，并以全部 CONFIRMED 收货行重新
累计校验，禁止并发超收。部分和全部收货分别把订单推进为 `PARTIAL_RECEIVED` 和 `RECEIVED`。

只有 `qualityStatus=PASS`、物料 `assetManaged=true` 且数量为正整数的行进入资产候选。确认时发布
`procurement.goods-receipt.confirmed.v1`；PENDING 后续首次变为 PASS 时发布
`procurement.goods-receipt.quality-passed.v1`，同一批新通过行共享一个事件 ID。历史补偿读取使用受
`X-Internal-Token` 保护的
`GET /api/internal/procurement/goods-receipt/asset-candidates?tenantId={tenantId}&afterId={id}&size={size}`；
实时消费和回扫均以 `tenantId + goodsReceiptLineId + unitSequence` 幂等。

两个事件及历史候选必须携带收货管理归属 `ownerUserId/ownerUnitId`，Asset 将其继承为新资产的
管理归属；字段缺失或不是正整数时失败关闭。事件行的 `receivedQuantity/unitPrice/totalPrice`
继续使用 JSON 十进制字符串，只有单位级计数 `assetQuantity` 使用正整数。Asset 实时消费者还以
`consumerName + eventId` 建立 Inbox 幂等门闩；同一事件 ID 或来源单位绑定不同完整业务意图时
返回冲突，不得覆盖已创建资产。

### 17.8 采购概览

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/procurement/overview/summary` | `procurement:overview:list` | 采购流程待办、订单状态和分币种承诺金额 |
| GET | `/api/procurement/overview/spend-analysis?dimension={dimension}&limit={limit}` | `procurement:overview:list` | 按维度与币种聚合已确认采购支出 |

`dimension` 必填，只允许 `CATEGORY`、`SUPPLIER`、`DEPARTMENT`；`limit` 默认 20，范围 1–100。
DEPARTMENT 表示采购订单负责部门 `ownerUnitId`。支出仅统计
`CONFIRMED/PARTIAL_RECEIVED/RECEIVED/CLOSED` 采购订单，不包含草稿、仅发送或已取消订单。

摘要响应示例：

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

支出分析项包含 `dimension/dimensionKey/dimensionName/currencyCode/amount`，先按
`currencyCode` 升序，再按同币种 `amount` 降序排列。`amount` 始终为 JSON 十进制字符串；
不同币种必须保持独立记录，服务端和前端均不得直接相加。摘要的每一块聚合 SQL 都直接命中
对应请购、RFQ、采购订单或收货聚合根，并应用与普通列表相同的 requester/owner DataScope 与
TenantLine；支出分析使用采购订单 owner 范围，不得因聚合查询绕过数据权限。

## 18. Asset MVP 契约

### 18.1 通用边界

- 外部 Base path 为 `/api/asset`；Gateway 保留完整路径，不使用 `StripPrefix`。
- 外部请求使用 Gateway 注入的 `X-User-Id`、`X-Tenant-Id`、`X-Username`、角色和权限头。业务表始终受 TenantLine 约束，管理列表、子资源和概览还受 permission-aware DataScope 约束。
- 管理列表按 `owner_user_id/owner_unit_id` 过滤；`GET /api/asset/asset/my` 固定按 `current_user_id` 查询，不能因同一用户拥有管理角色而扩大。
- 写命令携带 `version` 并执行乐观锁校验；版本或活动操作占位不匹配返回业务冲突。
- 资产原值、残值和聚合金额使用 `DECIMAL(18,2)`，请求与响应均使用 JSON 十进制字符串；JSON number 返回 400。币种使用三位 ISO 4217 编码。
- 资产状态为 `IN_STOCK/ALLOCATED/IN_USE/MAINTENANCE/TRANSFER/DISPOSAL_PENDING/DISPOSED/SCRAPPED`。
- 内部端点使用 `/api/internal/asset/**`，必须携带 `X-Internal-Token` 和 `X-Tenant-Id`，并被 Gateway 显式阻断。

### 18.2 资产台账与命令

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/asset/asset/list` | `asset:asset:list` | `keyword/status/categoryCode/ownerUnitId/locationCode/page/size`，按管理归属查询 |
| GET | `/api/asset/asset/my` | `asset:asset:self` | `keyword/status/categoryCode/page/size`，固定查询当前使用人 |
| GET | `/api/asset/asset/{id}` | `asset:asset:list` | 查询管理范围内资产详情 |
| GET | `/api/asset/asset/{id}/history` | `asset:asset:list` | `page/size`，查询不可变状态历史 |
| POST | `/api/asset/asset` | `asset:asset:create` | 手工创建 `IN_STOCK` 资产 |
| PUT | `/api/asset/asset/{id}` | `asset:asset:update` | 更新基础资料；不能直接更新状态、使用人或位置 |
| DELETE | `/api/asset/asset/{id}?version={version}` | `asset:asset:delete` | 仅删除未发生业务动作的手工在库资产 |
| POST | `/api/asset/asset/{id}/allocate` | `asset:asset:allocate` | `IN_STOCK → ALLOCATED` |
| POST | `/api/asset/asset/{id}/accept` | `asset:asset:accept` | 当前使用人执行 `ALLOCATED → IN_USE` |
| POST | `/api/asset/asset/{id}/return` | `asset:asset:return` | 当前使用人退还，恢复 `IN_STOCK` 并清空使用归属 |
| POST | `/api/asset/asset/{id}/maintenance/start` | `asset:asset:maintenance` | `IN_USE → MAINTENANCE` |
| POST | `/api/asset/asset/{id}/maintenance/complete` | `asset:asset:maintenance` | `MAINTENANCE → IN_USE` |
| GET | `/api/asset/options/users` | 资产台账/分配/调拨/处置相关权限之一 | 当前租户启用用户候选，返回主组织，不含手机号/邮箱 |
| GET | `/api/asset/options/suppliers` | `asset:asset:create/update` | 当前租户已批准供应商关键词候选 |
| GET | `/api/asset/options/transfer-assets` | `asset:transfer:create` | 当前 DataScope 内无活动占位且状态可调拨的资产 |
| GET | `/api/asset/options/disposal-assets` | `asset:disposal:create` | 当前 DataScope 内无活动占位且状态可处置的资产 |

手工创建请求包含
`name/categoryCode/specification/brand/model/supplierId/supplierNameSnapshot/purchaseDate/purchaseAmount/currencyCode/locationCode/warrantyExpiryDate/expectedLifeYears/remark/ownerUserId/ownerUnitId`。
`purchaseAmount` 缺省可为 `null`，非空时必须为 JSON 十进制字符串；`currencyCode`、`ownerUserId` 和 `ownerUnitId` 必填。更新请求增加必填 `version`，但不接受 `locationCode`。

分配请求为：

```json
{
  "version": 0,
  "targetUserId": 101,
  "targetUnitId": 12,
  "remark": "研发设备领用"
}
```

领用、退还和维修命令使用 `{ "version": 0, "remark": "..." }`。`accept/return` 除权限校验外还必须验证资产的
`current_user_id` 等于当前用户；管理范围不能替代这一逐行归属校验。

### 18.3 Procurement 收货联动

Asset 消费 `procurement.goods-receipt.confirmed.v1` 与
`procurement.goods-receipt.quality-passed.v1`。事件信封和收货行字段以 17.7 为权威；Asset 只处理
`qualityStatus=PASS && assetManaged=true && assetQuantity>0` 的单位级资产。

- 实时消费以 `consumerName + eventId` 写入 `ast_inbox_event`，并校验同一事件 ID 不能绑定不同完整业务意图。
- 实时消费和历史回扫共同以
  `tenantId + goodsReceiptLineId + unitSequence` 建立来源唯一键，任何入口都不能重复创建资产。
- 新资产继承收货管理归属 `ownerUserId/ownerUnitId`，并保存 PO、GR、供应商、物料、品类、币种和金额快照。
- 内部受控补偿端点为
  `POST /api/internal/asset/procurement/backfill?tenantId={tenantId}&afterId={id}&size={size}`；请求头
  `X-Tenant-Id` 必须与 query `tenantId` 完全一致。`size` 范围为 1–100，响应返回本页处理结果和下一游标。

### 18.4 调拨

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/asset/transfer/list` | `asset:transfer:list` | `keyword/status/page/size`，通过关联资产继承管理 DataScope |
| GET | `/api/asset/transfer/{id}` | `asset:transfer:list` | 查询调拨详情 |
| GET | `/api/asset/transfer/{id}/approval-view?taskId={taskId}` | `asset:transfer:approve` | Workflow 校验当前任务分配后按 tenant 读取只读审批视图 |
| POST | `/api/asset/transfer` | `asset:transfer:create` | 创建申请、原子占用资产并启动 Workflow |
| POST | `/api/asset/transfer/{id}/retry-start` | `asset:transfer:retry` | 对 `PENDING_APPROVAL + PENDING` 或 `START_FAILED + FAILED` 申请复用原幂等快照启动 |
| POST | `/api/asset/transfer/{id}/cancel` | `asset:transfer:cancel` | 仅取消 `START_FAILED + FAILED` 的明确失败申请并恢复资产 |
| POST | `/api/asset/transfer/{id}/complete` | `asset:transfer:complete` | 审批通过后完成交接，资产进入 `IN_USE` |

创建请求为：

```json
{
  "assetId": 10001,
  "toUserId": 102,
  "toUnitId": 12,
  "toLocation": "SH-A-03-021",
  "reason": "岗位调整"
}
```

创建只允许资产处于 `IN_STOCK/ALLOCATED/IN_USE` 且没有活动操作。申请保存原使用归属、位置和
`previousAssetStatus`。服务端按当前租户和 `category=ASSET_TRANSFER` 自动解析已发布且可启动的
Workflow 模型版本并持久化幂等快照；客户端不得提供或选择 `modelVersionId`。
`retry-start/cancel/complete` body 均为 `{ "version": 0 }`。

### 18.5 丢弃与报废处置

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/asset/disposal/list` | `asset:disposal:list` | `keyword/disposalType/status/page/size`，通过关联资产继承管理 DataScope |
| GET | `/api/asset/disposal/{id}` | `asset:disposal:list` | 查询处置详情 |
| GET | `/api/asset/disposal/{id}/approval-view?taskId={taskId}` | `asset:disposal:approve` | Workflow 校验当前任务分配后按 tenant 读取只读审批视图 |
| POST | `/api/asset/disposal` | `asset:disposal:create` | 创建申请、原子占用资产并启动 Workflow |
| POST | `/api/asset/disposal/{id}/retry-start` | `asset:disposal:retry` | 对 `PENDING_APPROVAL + PENDING` 或 `START_FAILED + FAILED` 申请复用原幂等快照启动 |
| POST | `/api/asset/disposal/{id}/cancel` | `asset:disposal:cancel` | 仅取消 `START_FAILED + FAILED` 的明确失败申请并恢复资产 |
| POST | `/api/asset/disposal/{id}/complete` | `asset:disposal:complete` | 审批通过后完成实物处置 |

创建请求包含
`assetId/disposalType/reason/residualValue/disposalMethod`；`disposalType` 只允许
`DISCARD/SCRAP`，`residualValue` 非空时必须为 JSON 十进制字符串。申请使用
`ASSET_DISPOSAL + businessKey` 启动服务端自动解析的 `category=ASSET_DISPOSAL` Workflow 模型，
客户端不得提供 `modelVersionId`。完成
`DISCARD` 后资产进入 `DISPOSED`，完成 `SCRAP`
后进入 `SCRAPPED`，二者都是不可恢复终态。

### 18.6 Workflow 完成事件与操作状态

调拨与处置状态统一为
`PENDING_APPROVAL/START_FAILED/APPROVED/REJECTED/COMPLETED/CANCELLED`，Workflow 启动状态为
`PENDING/STARTED/FAILED`。

Workflow 以 16.3 的 `workflow.process.completed.v1` 发布审批结果。Asset 消费者必须：

1. 校验 `eventId/eventType/producer/tenantId/businessType/businessKey/processInstanceId/result`；
2. 以 `consumerName + eventId` 建立 Inbox 幂等门闩，并拒绝同一事件 ID 绑定不同完整 payload；
3. 只接受与当前申请、已确认流程实例和活动状态完全匹配的事件；
4. 对早于本地启动确认到达的事件回滚 Inbox 并触发消息重试；
5. `APPROVED` 只把申请推进为待业务完成；`REJECTED/CANCELLED` 在同一事务中恢复
   `previousAssetStatus`、关闭申请并清空资产 `active_operation_*`。

Workflow 启动调用发生在本地创建事务提交之后。网络异常、409/其他无法确定结果的非 200 响应或本地确认失败
都可能对应远端已受理，因此保持 `PENDING_APPROVAL + PENDING`，禁止本地取消，并允许使用同一幂等快照重试。
Workflow 业务响应 404 表示模型版本已不可启动且远端事务未创建实例，申请进入
`START_FAILED + FAILED`；该明确失败状态允许重试或本地取消。
两类重试均必须复用幂等快照：`businessType` 由调拨/处置聚合类型固定推导，并复用已持久化的
`requestId/businessKey/modelVersionId/workflowStartUserId/workflowStartUserName`，
包括由不同用户执行重试时也继续使用原始发起人身份，禁止创建第二个流程实例。

### 18.7 资产概览

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/asset/overview/summary` | `asset:overview:list` | 管理范围内各状态数量及按币种原值 |
| GET | `/api/asset/overview/distribution?dimension={dimension}&limit={limit}` | `asset:overview:list` | 按状态、品类、管理部门或位置聚合 |

`dimension` 必填，只允许 `STATUS/CATEGORY/DEPARTMENT/LOCATION`；`limit` 默认 20，范围 1–100。
所有聚合 SQL 都必须应用与管理台账相同的 owner DataScope 与 TenantLine。金额按币种保持独立记录并输出十进制字符串，不同币种不得直接相加。
