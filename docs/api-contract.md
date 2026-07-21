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

**Gateway 路径前缀**：所有前端请求使用 `/api/<service>/<resource>`（如 `/api/auth/user/list`）。Gateway 去除 `/api/<service>`（StripPrefix=2），下游服务接收 `/<resource>`。

**例外**：Base 服务的 `/api/base/**` 路由**没有** StripPrefix 过滤器，Base 服务控制器使用完整路径（如 `@RequestMapping("/api/base/dict/type")`）。

---

## 5. Gateway 路由配置

### 5.1 本地开发环境路由

Gateway `application.yml` 中的路由配置（`spring.cloud.gateway.server.webflux.routes`）：

| 路由 ID | 路径匹配 | 目标服务 | StripPrefix | 说明 |
|---------|---------|---------|-------------|------|
| `omni-auth-oauth2` | `/oauth2/**` | `lb://omni-auth` | 无 | OAuth2 授权服务器端点 |
| `omni-auth-wellknown` | `/.well-known/**` | `lb://omni-auth` | 无 | OpenID Connect 发现端点 |
| `omni-auth` | `/api/auth/**` | `lb://omni-auth` | 2 | Auth 服务 REST API |
| `omni-base` | `/api/base/**` | `lb://omni-base` | **无** | Base 服务（使用完整路径） |
| `omni-base-job` | `/api/job/**` | `lb://omni-base` | **无** | 定时任务管理 |
| `omni-workflow` | `/api/workflow/**` | `lb://omni-workflow` | **无** | 工作流引擎 |

### 5.2 Docker 部署路由

Docker 部署时，路由配置相同，但目标服务的 URI 通过 Nacos 服务发现自动解析：

| 前端请求 | Gateway 路由 | 下游接收路径 | 说明 |
|---------|-------------|-------------|------|
| `GET /api/auth/user/list` | `lb://omni-auth` + StripPrefix=2 | `GET /user/list` | Auth 服务去除前缀 |
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

### 8.3 安全响应头（Gateway 注入）

`SecurityHeadersFilter`（WebFlux WebFilter）为所有经过网关的响应添加：

| 响应头 | 值 | 用途 |
|--------|-----|------|
| `X-Content-Type-Options` | `nosniff` | 防止浏览器 MIME 类型嗅探 |
| `X-Frame-Options` | `SAMEORIGIN` | 防止点击劫持 |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | 控制 Referer 头泄露 |

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
| Docker 部署 | `http://<宿主机IP>:8100/api/auth/oauth2/github/callback` |
| 生产环境 | `https://your-domain.com/api/auth/oauth2/github/callback` |

> **注意**：Docker 部署中，Auth 服务容器内部端口是 8080，但 OAuth2 回调 URL 必须使用宿主机映射端口 8100（因为第三方平台需要回调到宿主机的公网/局域网可达地址）。

### 前端回调页面

`/callback` 页面（`src/views/callback/index.vue`）负责：
1. 解析 URL fragment 中的 `token` 和 `username`
2. 存储到 `localStorage`（通过 `useUserStore`）
3. 重定向到 Dashboard

> 完整流程时序图详见 [core-flows.md](core-flows.md) Flow 4。

---

## 11. XSS 配置管理端点

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

后续 Procurement/Asset 只能携带 `X-Internal-Token` 调用：

- `GET /api/internal/supplier/{id}?tenantId={tenantId}`
- `GET /api/internal/supplier/search?tenantId={tenantId}&status=APPROVED&categoryCode={code}&limit=50`

响应仅包含供应商 ID、编号、名称、状态、等级和品类，不返回联系人或银行账户 PII。
