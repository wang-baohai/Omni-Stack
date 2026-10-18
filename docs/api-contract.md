# API 契约

> 本文维护响应、字段、路由、身份头及跨服务消息的当前线格式。源码、配置和 Controller/DTO 定义实际行为；示例中的 ID、日期、地址与状态值只用于说明结构，不代表当前环境数据或验收结果。
> 业务状态机与实现边界见各领域事实/设计文档；配置、登录、浏览器操作及失败恢复见 [Wiki](wiki/index.md) 和[认证指南](guides/authentication.md)。本地协议授权与外部社交登录是不同链路。

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

常规业务 Controller 返回 `R<T>`；分页为 `R<PageResult<T>>`。OAuth2/OIDC 协议端点、社交登录重定向、Actuator 及过滤器/框架产生的响应不能一概视为同一封装。

```json
{"code":200,"message":"success","data":{"id":1}}
```

```json
{"code":500,"message":"操作失败","data":null}
```

```json
{"code":400,"message":"username: 用户名不能为空","data":null}
```

`R.ok(data)` 的业务码为 200、message 为 `success`；`R.ok()` 与失败响应的 data 为 null。`R.fail(message)` 默认业务码为 500，显式重载使用调用方给出的码。JSON 字段顺序不是契约。

后端唯一类型为 [R.java](../omni-backend/omni-common-core/src/main/java/com/omni/common/core/result/R.java)。前端唯一声明为 [src/types/api.ts](../omni-frontend/src/types/api.ts) 中的 `ApiResponse<T>`；类型声明 `data: T` 不会在运行时消除 null。本文不复制可编译类型，调用方应按端点实际空值处理。

---

## 2. 错误码速查表

### 2.1 系统级错误码

HTTP 状态和 `R.code` 必须分别读取：

| HTTP 状态 | 业务码 | 当前映射 |
|---|---|---|
| 200 | 200 | 常规业务成功 |
| 200 | 抛出时指定的码，默认 500 | `BusinessException`；例如业务 400/403/404/409/503 |
| 400 | 400 | 已由对应 advice 处理的 Bean 校验、绑定、缺少 query 参数或类型不匹配 |
| 403 | 403 | `AccessDeniedException/AuthorizationDeniedException` |
| 404 / 405 / 415 | 同 HTTP 状态 | advice 处理的资源、方法、媒体类型错误 |
| 500 | 500 | 未被专门处理的异常 |

JSON 读取错误的映射存在服务差异：SRM/Procurement 的专用 advice 把 `HttpMessageNotReadableException` 映射为 400；Asset 当前通用处理器会落入 500。受控 MockMvc 已观察到 Asset 金额传 JSON number 或非法金额字符串返回 500；这不表示请求有效，也不保证所有服务均返回相同状态。

### 2.2 业务级错误码

业务码由当前抛出点决定，不能从 message 推断。验证码、账号、租户等失败有使用默认 500 的路径；400、403、404、409、503 也可能出现在 HTTP 200 的业务失败体中。前端为 410、423、429、502 等码提供展示分类，不代表每个端点都实现这些业务分支。

### 2.3 Gateway 级错误码

| HTTP 状态 / code | 条件 | 当前 message |
|---|---|---|
| 401 / 401 | 缺少 Bearer 认证 | 请先登录 |
| 401 / 401 | JWT 过期 | 登录已过期，请重新登录 |
| 401 / 401 | 其他 JWT/安全校验失败，包括撤销命中 | 认证失败，请重新登录 |
| 503 / 503 | 非 test 配置下身份签名密钥缺失、过弱或签名生成失败 | 网关身份签名未就绪，请联系管理员 |

以上是 `AuthFilter` 的分支；其他过滤器、限流器、下游或框架错误仍有自己的映射。message 供诊断，不能作为前端认证状态判断条件。

### 2.4 社交登录错误码

社交回调的 `user_denied/invalid_callback/social_login_failed` 是 302 Location 中的 query 值，不是 `R.code`；条件与地址见第 10 节。

### 2.5 前端错误处理流程

[request.ts](../omni-frontend/src/api/request.ts) 和 [error-display.ts](../omni-frontend/src/utils/error-display.ts) 以 HTTP 状态、业务码和无响应时的 Axios 传输错误码分类。HTTP 401/403 优先于响应体业务码；其余优先业务码，再用 HTTP 状态。

- 业务失败抛出携带 `businessCode/httpStatus/traceId/i18nKey/displayMessage` 的 `ApiError`；HTTP 失败保留并增强 AxiosError，不把两类都描述成同一构造函数。
- 原始后端 message 与 Axios message 不直接上屏。只显示码值驱动的四语安全文案；400 的字段提示只接受经过校验的 ASCII 字段名。
- 401：凭据端点保留会话和输入；其他请求按是否有会话区分未登录/会话过期，两者的 `shouldLogout` 均为 true，再安全清理会话并跳登录。凭据端点识别包括 `/auth/login`、`/auth/session-login`、`/auth/register`、`/auth/social-login`、`/srm/portal/auth/login`；这份前端分类不等于 Gateway 白名单。
- 403 保持会话；400/409/5xx、网络与超时使用各自本地化文案，不根据英文/中文技术串猜语义。业务冲突的处理由调用方刷新并决定是否重试。

---

## 3. 分页契约

后端唯一类型为 [PageResult.java](../omni-backend/omni-common-core/src/main/java/com/omni/common/core/result/PageResult.java)，前端为 [src/types/api.ts](../omni-frontend/src/types/api.ts)。响应 data 包含：

| 字段 | 含义 |
|---|---|
| `records` | 当前页记录 |
| `total` | 总记录数 |
| `size` | 每页数量 |
| `current` | 当前页码 |
| `pages` | `(total + size - 1) / size`，由构造器计算 |

通常 query 使用 `page=1&size=10`；范围、默认值、过滤字段和上限以具体 Controller/查询 DTO 为准，不能把示例默认值当作所有端点的统一限制。构造器保留传入的 records，未提供全局 null→[] 转换或分页参数校验；调用方必须提供有效的 size，并按端点约定处理空记录。

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

**Gateway 路径前缀**：常规前端业务资源请求使用 `/api/<service>/<resource>`，例如 /api/auth/user/list。Auth、Base、Workflow、CRM、SRM、Procurement、Asset 的业务路由保留完整路径，不使用 StripPrefix。协议与内部路径按各自端点定义。

上表是业务资源的命名约定；动作型命令、OAuth2/OIDC 与内部接口按正式 Controller 使用各自路径，不能据此推导出尚未实现的端点。

---

## 5. Gateway 路由配置

### 5.1 本地开发环境路由

当前 [Gateway application.yml](../omni-backend/omni-gateway/src/main/resources/application.yml) 的 `spring.cloud.gateway.server.webflux.routes` 有 12 个显式路由，服务发现自动路由关闭：

| 路由 ID | Path 匹配 | 目标 |
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

普通业务路由保留完整路径，没有 `StripPrefix`。两个 `no://op` 路由配置 `SetStatus=404`，阻断内部路径；认证或其他前置过滤器也可能先返回自己的错误。

### 5.2 Docker 部署路由

正式 Compose 通过 Nacos 解析相同的 `lb://` 服务名；部署环境、配置覆盖与实际启动的模块决定能否访问。路由配置本身不证明下游健康。例如 `/api/base/dict/type/list` 原样到达 Base，`/api/job/**` 也到 Base。

### 5.3 AuthFilter 白名单路径

精确匹配：`/api/auth/login`、`/api/auth/session-login`、`/api/auth/register`、`/api/auth/captcha`、`/api/auth/tenants`、`/favicon.ico`。

前缀匹配：`/api/auth/oauth2/`、`/actuator/`、`/oauth2/`、`/.well-known/`、`/login`、`/error`。这里的前缀判断是 startsWith，不能改写为全部精确匹配；也不代表整族 `/api/auth/**` 公开。

白名单仅跳过该过滤器的 JWT 校验，不创建路由、控制器或下游权限。公开路径仍清除客户端伪造的可信身份头和 Bearer Authorization，保留 OAuth2 协议需要的 Basic Authorization，并设置 `X-Gateway-Forwarded=true`。

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

受保护路径通过 JWT 校验后，Gateway 清除客户端 Authorization 和同名可信头，再设置：

| 请求头 | 来源/含义 |
|---|---|
| `X-User-Id` | JWT 用户 ID，要求正整数 |
| `X-Tenant-Id` | JWT 租户 ID，要求正整数 |
| `X-User-Name` | JWT 用户名；不是 `X-Username` |
| `X-User-Roles` | 角色 claim |
| `X-User-Scopes` | 权限 claim |
| `X-Gateway-Forwarded` | `true` |
| `X-Gateway-Assertion` | Gateway 生成的身份断言 |

角色/权限 claim 为 List 时用逗号连接，其他非空值转为字符串，缺失为空串。断言对 `userId|tenantId|username|issuedAt` 做 HMAC-SHA256，格式为无 padding 的 `base64url(payload):base64url(signature)`。非 test 配置要求足够强的私有密钥；服务端必须通过自己的预认证链校验，不能因存在任意同名头就信任身份。

### 8.2 前端发送的请求头

Axios 从 `useUserStore().token` 设置 `Authorization: Bearer <JWT>`，并由 `getTenantIdFromToken(token)` 解析租户头；不是独立信任可编辑的 store 租户字段。Gateway 对受保护请求会覆盖可信身份头。JSON 请求按实际载荷设置 Content-Type；共享 Axios 实例没有显式配置全局 Content-Type 默认头。

### 8.3 内部服务请求头

本文列出的业务内部接口使用 `X-Internal-Token` 与显式 `X-Tenant-Id`，不使用终端用户 JWT 代替内部认证。带 JSON body 时使用 `Content-Type: application/json`；需要 body/query tenantId 的端点还校验与请求头一致，具体不一致错误见各端点。

令牌校验器按服务配置生效：`omni-common-service` 的内部认证过滤器拒绝无效请求令牌为 HTTP 401，配置校验器在启用相关能力但私有令牌缺失/过弱时阻止启动；兼容 MQ 的 `InternalApiAuthFilter` 在服务端令牌未配置时返回 HTTP 503，在请求令牌缺失/不匹配时返回 401。不能把这两条实现写成所有服务统一的运行时 503 分支。

内部路径被 Gateway 路由显式阻断，不依赖客户端 `X-Gateway-Forwarded` 或角色/权限头；可信内部调用者仍需为每次请求提供正确业务租户，令牌不自动决定租户。

### 8.4 安全响应头（Gateway 注入）

| 响应头 | 当前值 |
|---|---|
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |
| `X-Trace-Id` | 实际 tracer 的 trace ID；无 span 时为 UUID 去连字符的 32 位值 |

[SecurityHeadersFilter](../omni-backend/omni-gateway/src/main/java/com/omni/gateway/config/SecurityHeadersFilter.java) 清除公网传入的同名 trace 头。存在实际 tracer span 时将其 ID 放入下游请求及响应；无 span 时只给响应生成 fallback ID，不把该 fallback 注入下游请求。因此仅有响应头不能证明 Gateway、Servlet 与 Feign 完整关联。前端只接受 1–128 位安全 ASCII traceId 供诊断，拒绝异常值。

---

## 9. 认证头

`Authorization: Bearer <token>` 由前端请求拦截器设置，Gateway 校验 JWT 签名、有效期、正整数用户/租户及适用的撤销记录，再注入身份。当前撤销检查依赖 JWT 的 jti；不能把缺少 jti 的令牌也描述为已查黑名单。精确/前缀公开范围统一见第 5.3 节；用户、角色等管理接口仍要求有效认证和端点权限。

---

## 10. 社交登录端点

这两个浏览器导航端点在正常授权链路返回 HTTP 302，不使用 `R<T>` 包装。提供商标识为 `github`、`google`、`gitee`：

| 方法 | URL / 参数 | 合同 |
|---|---|---|
| GET | `/api/auth/oauth2/{provider}?tenant_id=1` | `tenant_id` 必填；校验提供商与租户后，302 到提供商授权页 |
| GET | `/api/auth/oauth2/{provider}/callback`；`code`、`state`、`error` 均为可选绑定参数 | 按下表处理拒绝、缺参、业务失败或成功；参数可选不表示成功分支可省略 code/state |

| 回调条件 | HTTP 302 Location |
|---|---|
| 提供商返回非空 `error` | 前端 `/login?error=user_denied` |
| `code` 或 `state` 缺失/空白 | 前端 `/login?error=invalid_callback` |
| `BusinessException`，包括 state 或提供商换码失败 | 前端 `/login?error=social_login_failed&message=<URL编码消息>` |
| 成功 | 配置的前端 `/callback#token=<URL编码JWT>&username=<URL编码用户名>` |

`OAuth2StateUtils` 的 state 格式为 `tenantId|timestamp|hex(HMAC-SHA256(tenantId|timestamp, secret))`；回调验证完整性并拒绝已超过 10 分钟的时间戳，再还原租户。当前实现没有一次消费标记、随机 nonce 或浏览器会话绑定，不能据此声称完整的一次性 CSRF/重放防护。应用凭据本身也不能代替外部用户登录与授权。

正式 Docker Compose 的回调入口为 `http://localhost:8102/api/auth/oauth2/{provider}/callback`，由 Gateway 转发；生产回调使用配置的 Frontend/Gateway HTTPS 域名。提供商登记值必须与实际 redirect_uri 一致，不能将私有容器地址登记为公网入口。发起阶段参数/提供商/租户校验失败仍按 Auth 的错误映射处理，不保证返回 302。

源码入口：[SocialLoginController](../omni-backend/omni-auth/src/main/java/com/omni/auth/controller/SocialLoginController.java)、[OAuth2StateUtils](../omni-backend/omni-auth/src/main/java/com/omni/auth/util/OAuth2StateUtils.java)、[Compose 回调配置](../compose.apps.yaml)。配置、浏览器操作和失败复现统一见[认证指南](guides/authentication.md)；本地授权码/PKCE 与设备码是独立协议链路。

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
PUT /api/auth/xss-config/toggle?enabled=true
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
| PUT | `/api/auth/xss-config/rules/{id}/toggle?enabled=true` | `system:xssconfig:update` | 切换规则启用状态 |

**ruleType 枚举值**：`HTML_TAG` | `EVENT_HANDLER` | `DANGEROUS_PROTOCOL` | `CUSTOM_PATTERN`

### 权限码

| 权限码 | 说明 |
|--------|------|
| `system:xssconfig:list` | 查看 XSS 配置和规则 |
| `system:xssconfig:update` | 切换全局开关、更新规则、切换规则状态 |
| `system:xssconfig:create` | 创建规则 |
| `system:xssconfig:delete` | 删除规则 |

规则更新、删除与状态开关均使用 Gateway 注入的 `X-Tenant-Id` 检查规则所属租户。不存在或属于其他租户时，在写入与缓存失效前返回 HTTP 200 + `R.code=404`；正整数租户校验失败抛业务码 400。三处权限码保持不变。

开关接口的 `enabled` 为必填查询参数。创建时 `ruleName`、`ruleType`、`pattern` 非空；更新只处理非 null 字段，提交 pattern 时执行正则语法校验。支持的 ruleType 见上表，当前 DTO 不校验枚举，运行时未知类型不执行净化。净化触发条件、Jackson 2/3、权威读取与缓存边界见 [核心流程](core-flows.md)。

---

## 12. Base 服务字典管理端点

Base 通过 Gateway 的 `/api/base/**` 路由提供完整路径接口，身份链和默认端口见核心流程。类型与数据各 6 个端点；所有入口都使用已验证的当前租户上下文。

### 字典类型端点

基础路径: `/api/base/dict/type`

| 方法 | 路径 | 权限 | 行为 |
| --- | --- | --- | --- |
| GET | `/api/base/dict/type/list` | `dict:type:list` | 按租户分页；typeCode/typeName 模糊过滤、status 可选，sort/id 升序 |
| GET | `/api/base/dict/type/{id}` | `dict:type:list` | 查当前租户的类型，缺失或外租户为业务码 404 |
| POST | `/api/base/dict/type` | `dict:type:create` | 要求 typeCode/typeName 非空；租户内类型编码唯一 |
| PUT | `/api/base/dict/type/{id}` | `dict:type:update` | 只更新非 null 字段，typeCode 不可在此变更 |
| DELETE | `/api/base/dict/type/{id}` | `dict:type:delete` | 归属检查后级联删除同租户同类型数据 |
| PUT | `/api/base/dict/type/{id}/status?status=...` | `dict:type:update` | status 为必填查询参数，写后失效缓存 |

分页参数 `page=1,size=10`；类型查询可选 `typeCode/typeName/status`。创建类型可选 `remark/sort`；创建数据可选 `tagType/remark/sort`。创建时服务设置 `status=1`，sort 未提供时为 0。

### 字典数据端点

基础路径: `/api/base/dict/data`

| 方法 | 路径 | 权限 | 行为 |
| --- | --- | --- | --- |
| GET | `/api/base/dict/data/options?typeCode=...` | 已认证；无单独动作权限 | 仅要求认证，返回已启用数据的 value/label 选项并使用缓存 |
| GET | `/api/base/dict/data/list?typeCode=...` | `dict:data:list` | 管理分页直接查库；typeCode 必填，sort/id 升序 |
| POST | `/api/base/dict/data` | `dict:data:create` | typeCode/dictValue/dictLabel 非空；校验当前租户父类型存在 |
| PUT | `/api/base/dict/data/{id}` | `dict:data:update` | 只更新非 null 字段；typeCode 不在更新 DTO 中 |
| DELETE | `/api/base/dict/data/{id}` | `dict:data:delete` | 归属检查后删除并失效缓存 |
| POST | `/api/base/dict/data/refresh-cache?typeCode=...` | `dict:data:refresh` | typeCode 为必填查询参数；删除后回源启用数据并回填 |

更新 DTO 仅处理非 null 字段；这不提供“提交 null 清空字段”的语义。`/options` 返回 `R<List<DictOptionVO>>`，对象只有 `value` 与 `label`；管理分页返回 `R<PageResult<SysDictType>>` 或 `R<PageResult<SysDictData>>`。

### 字典权限码

| 权限码 | 用途 |
| --- | --- |
| `dict:type:list` | 类型列表及详情 |
| `dict:type:create` | 创建类型 |
| `dict:type:update` | 更新类型及切换状态 |
| `dict:type:delete` | 级联删除类型 |
| `dict:data:list` | 管理数据列表 |
| `dict:data:create` | 创建数据 |
| `dict:data:update` | 更新数据 |
| `dict:data:delete` | 删除数据 |
| `dict:data:refresh` | 刷新缓存 |

### 租户隔离

列表、创建、选项与刷新都显式传递租户。六个按 ID 的读写入口也将 Gateway 注入的 `X-Tenant-Id` 传入服务，先要求正租户 ID 并检查实体归属，再执行返回、写入或缓存操作。类型唯一约束为 `(tenant_id,type_code)`。

不存在或属于其他租户的 ID 资源均抛 `BusinessException(404)`，由公共异常处理返回 HTTP 200 + `R.code=404`；缺失或非正数的租户 ID 抛业务码 400。网关与方法权限验证仍独立执行。缓存、客户端生命周期和源码入口统一见核心流程及前端规范。

[Flow 9](core-flows.md) / [Composable](frontend-patterns.md)

### MQ 投递运行状态

`GET /api/base/mq-message/runtime` 使用 `base:mqmessage:list` 权限，返回当前 Outbox 与后台投递能力：

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

该结果按配置计算：`outboxWriteEnabled=true`，`deliveryEnabled = omni.mqlog.relay.enabled && xxl.job.executor.enabled`（两项读取默认 true），模式据此为 `FULL/OUTBOX_ONLY`。它不探测 relay 进程、XXL-JOB 调度、Broker、数据库或消息是否已送达；`FULL` 仅表示相关开关启用，`OUTBOX_ONLY` 表示至少一个投递开关关闭。真实可靠性与恢复见 [MQ 可靠性](mq-reliability.md)。

---

## 13. API 版本管理策略

当前路由使用无版本前缀的 `/api/...`，例如 `/api/auth/user/list`；Gateway 与 Controller 没有实现 `/api/v1/...` 或 `/api/v2/...` 的并行版本路由。

未来 URL 版本、破坏性变更迁移和旧版保留期限尚未形成已实现契约。需要时先在设计与 ADR 中确定消费者、兼容范围、迁移与支持期限，再同步实现和文档；本文不把未实现的前缀或六个月维护期作为承诺。当前字段、错误与状态语义继续以本契约和源码为准。

---

## 14. Null 语义

`R.ok()` 与失败响应的 data 为 null；常规当前 mapper 输出这些 null 字段。空集合必须由生产者实际返回空 List 才成为 []；`PageResult` 会保留 null records，没有全局转换保证。可选字段的 null、空字符串或省略行为以具体 DTO、serializer 和端点为准。

JSON 绑定、业务允许字段与身份来源是三个不同约束。未知属性不保证统一返回 400：当前受控 Asset DTO 绑定忽略了额外属性。未在 DTO 暴露的 tenant、user、金额快照等不会因此成为可控业务输入，但也不能宣称所有伪造字段都被解析器显式拒绝。调用方只发送端点列出的字段。

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

门户入驻 DTO 要求 requestId（非空、最长64）、inviteToken（非空、最长256）、name（非空、最长200）、creditCode（非空、最长50）。creditCode 会 trim 并转大写；示例不是已登记的真实企业代码。与普通未知属性行为不同，此 DTO 的 JsonAnySetter 显式拒绝任何未声明属性，包括 tenantId/userId；SRM 的 JSON 读取异常由专用 advice 映射为400。

### 15.3 评估与风险

- `GET /api/srm/evaluation/template/default/dimensions` 返回当前租户默认模板及有效维度，前端不得硬编码数据库 ID。
- 评分范围为 1–5，必须覆盖默认模板的全部维度且不得重复。
- `GET /api/srm/risk/list` 返回按供应商最新评估聚合的风险摘要分页，可按 `riskLevel` 筛选。
- `GET /api/srm/supplier/{id}/risk` 返回 `indicators/latestAssessment/history` 聚合视图。
- 风险指标更新携带 `version`；仅综合等级从非 RED 变为 RED 时产生 `srm.risk.level-changed.v1`。

评估创建由服务选择当前租户已启用的默认模板，DTO 没有 templateId；items 为 1–100 项，必须完整覆盖启用维度且不重复。score 为 1–5，最多一位小数；dimensionId 从默认模板接口取得。模板、维度名和权重由服务端确定。

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
      "unit": "EA",
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
| `lines[].remark` | String | 否 | 最长 500；服务 trim 并归一化空白 |

`tenantId/supplierId/rfqNo/material/quantity/currencyCode/lineAmount/totalAmount` 不是此请求 DTO 的可控输入；服务从可信身份、PortalUser、Procurement 邀请快照读取或计算。额外 JSON 属性可能被忽略，不能承诺一律显式报错。响应为 `R<QuotationVO>`，包含报价头、version 和行快照；金额/数量的线格式见下节。

幂等与并发规则：

- `srm_quotation_request` 保存租户内 requestId、规范化 SHA-256、quotationId 与 targetVersion。历史 COMPLETED 记录在新请求的供应商状态、RFQ、截止时间和邀请行校验之前重放；返回当前报价快照且不再写报价/Outbox。不能把成功重放描述成再次执行全部新提交校验。
- 同 requestId 的 hash、supplierId 或 rfqId 不一致，记录仍 RESERVED、状态无效或当前报价版本落后于 targetVersion，返回业务码 409。
- 新请求必须由当前已准入供应商提交，重新读取 `SENT` RFQ、`INVITED/QUOTED` 邀请及未过期截止时间；有效期晚于当前时间且不早于 RFQ 截止。报价行须完整且仅覆盖全部 RFQ 行，1–200 行且不能重复；依赖不可用时失败关闭为 503。
- `(tenantId, rfqId, supplierId)` 最多一条未删除报价；首次请求 version=0，持久化首版为 1；修改使用当前 version，冲突为 409。
- requestHash 不含 requestId；输入为 `rfqId/version/validUntil/lines`。行按 rfqLineId 排序，单价统一为 6 位非科学计数十进制字符串，备注 trim，null/空白统一；不对原始 JSON 字节直接求 hash。行金额按 `unitPrice × quantity` 以 4 位 HALF_UP 计算再求总额。

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
`R<T>`。详细运行机制见 [workflow.md](workflow.md#6-内部启动与完成事件)。

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

幂等记录有租户内 `(tenantId, requestId)` 与 `(tenantId, businessType, businessKey)` 双唯一键。冲突比较业务类型/主键、modelVersionId 与 startUserId，不比较 title、variables、startUserName，也没有完整 payload hash。

成功记录返回原 processInstanceId 和 replayed=true，但响应 requestId 使用本次请求值。正在处理、不同业务/模型/发起人冲突返回业务码 409。FAILED 的同 requestId 分支可原子抢占重试；换 requestId 命中同业务键的 FAILED 记录不会自动走相同重试分支。

真正新启动重新校验 PUBLISHED 版本与非空 processDefinitionId，并对 `ASSET_TRANSFER/ASSET_DISPOSAL/SRM_SUPPLIER_ONBOARDING` 强制分类相等。title 空白时使用 `{businessType}:{businessKey}`。这些检查不把任意 variables 变成可信身份；发起人、租户、受保护变量及审批路由约束见 [Workflow](workflow.md)。

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

请求头与 body 的 tenantId 不一致先返回业务码 403。其余资格检查匹配 Flowable 任务租户、实例扩展租户及 businessType/businessKey，再确认当前 ASSIGNEE 或未签收的 CANDIDATE。任务不存在、业务不匹配或无资格返回 `R.code=200,data.valid=false`；assignmentType 为 `ASSIGNEE/CANDIDATE/NONE`。false 不能代替调用方执行审批命令时的权限和并发校验。

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

由已接入的审批完成与显式终止路径更新完成状态、completionEventId，并在同一本地事务写入 PENDING Outbox；`completion_event_id IS NULL` 防止重复逻辑完成事件。当前不是所有 Flowable engine-end/瞬时结束分支均有通用事件监听器。响应成功或 Outbox 提交不等于 Broker 投递或消费完成；中继在投递能力启用且依赖可用时异步投递/重试，消费者按 eventId 幂等。

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

查询按租户约束，要求版本为 PUBLISHED 且 processDefinitionId 非空；不存在或不可用为业务 404。modelKey 与 BPMN process id 的一致性由模型发布校验维护，不能仅凭返回字符串推断 engine 当前可执行。

按分类获取当前版本还可调用 `GET /api/internal/workflow/model-version/current?category={category}`。新启动对 `ASSET_TRANSFER/ASSET_DISPOSAL/SRM_SUPPLIER_ONBOARDING` 做分类绑定；同意图成功重放复用既有实例。内部终止为 `POST /api/internal/workflow/process-instance/{processInstanceId}/terminate?reason={reason}`，除内部令牌外显式携带 X-Tenant-Id、X-User-Id；原因可选。

### 16.5 审批规则只读模型聚合

| 方法 | 路径 | 当前约束 |
|---|---|---|
| GET | `/api/internal/workflow/model-versions/published?category=purchase` | 当前租户、精确分类、启用主模型、当前已发布版本且 processDefinitionId 非空 |
| POST | `/api/internal/workflow/model-versions/resolve` | body 为 `{ "modelVersionIds": [1, 2] }`；1–200 个正整数，按首次出现顺序去重返回 |
| GET | `/api/internal/workflow/model-version/{id}/preview` | 安全审批图与元数据，不返回原始 BPMN XML/designerJson |

availability 为 `AVAILABLE/NOT_CURRENT/UNAVAILABLE/MODEL_ARCHIVED/NOT_FOUND`，来自模型/版本元数据；不是对 Flowable 部署、挂起或实际执行能力的实时探测。

预览根字段为 `approvalPreviewVersion/modelVersion/nodes/edges/hasBranches/linearSummary`。nodes 含 `id/name/type/roleCode/approvalMode/description`，type 为 `START/END/APPROVAL/GATEWAY/SERVICE`；edges 含 `id/name/source/target/defaultBranch/conditionSummary`。条件只返回“已配置条件（内容已隐藏）”。分支或不能生成单路径摘要时 linearSummary 为 null；不得把当前组织预览当作未来实际审批人。

### 16.6 设计器候选人预览

`POST /api/workflow/identity/resolve-preview` 需要 `workflow:identity:list` 和可信租户头。请求字段为 `assignmentType/roleCode/anchorType/anchorParams/scopeMode/simulateUserId`，assignmentType 必填；响应 data 为 `candidateCount/candidates`，候选项含 `userId/username/nickname/unitName`。解析失败/兼容模式与锚点语义统一见 [Workflow 第 3 节](workflow.md#3-候选人监听器与会签)，不与内部安全审批图 DTO 混用。

### 16.7 实例读取租户边界

外部 `GET /api/workflow/process-instance/{id}/progress` 和 `GET /api/workflow/process-instance/{id}/approval-records` 显式使用 Gateway 的 X-Tenant-Id。缺失/非法租户失败，非正租户为业务 400；同租户历史实例不存在或属其他租户均为业务 404，先检查再读取活动、评论或候选详情。这项隔离不增加参与人 ACL，不能据此推断所有历史流程定义接口均有相同保护。

## 17. Procurement MVP 契约

### 17.1 通用边界

- 外部 Base path 为 `/api/procurement`，保留完整路径。使用 Gateway 身份及权限；TenantLine 仅覆盖 proc_* 业务表，Outbox 不通过该租户拦截器扫描。
- DataScope：请购按 requester_user_id/requester_unit_id；RFQ、PO、GR 按 owner_user_id/owner_unit_id；子行继承聚合根。物料、品类、审批路由与配置在租户内共享，不附加不存在的 owner 列。
- 数量/单价为 DECIMAL(19,6)，金额为 DECIMAL(19,4)，响应为 JSON 十进制字符串。请求也按各 DTO 的十进制字符串约束，不能用 JavaScript number 计算业务金额。
- 更新/删除和版本状态命令按 DTO 提供版本；RFQ 定点使用 rfqVersion/quotationVersion，不能统称所有写请求都带 version。创建没有已有行版本。
- 内部路径为 `/api/internal/procurement/**`，需要 X-Internal-Token 与 X-Tenant-Id，被 Gateway 阻断。详细状态与事务边界见 [Procurement 设计](design/procurement-design.md)。

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
| GET | `/api/procurement/approval-route/workflow-options` | `procurement:approval-route:list` |
| POST | `/api/procurement/approval-route/match-preview` | `procurement:approval-route:list` |
| GET | `/api/procurement/approval-route/coverage` | `procurement:approval-route:list` |
| GET | `/api/procurement/approval-route/impact?routeId={id}` | `procurement:approval-route:list` |
| POST | `/api/procurement/approval-route` | `procurement:approval-route:create` |
| PUT | `/api/procurement/approval-route/{id}` | `procurement:approval-route:update` |
| DELETE | `/api/procurement/approval-route/{id}?version={version}` | `procurement:approval-route:delete` |

新 UI 创建请求包含 `routeName/categoryCode/minAmount/maxAmount/modelVersionId/status`。`routeCode` 由服务端生成
`APR-{ULID}` 且创建后不可修改；一个兼容发布周期内，旧创建请求可以传 `routeCode` 作为缺失
`routeName` 的回退，服务端记录弃用日志。`priority` 只为兼容高级调用方保留；新建未传时在租户配置锁内取
同品类最大值加 10，前端不显示该字段。

`minAmount/maxAmount` 必须使用 JSON 十进制字符串（`maxAmount=null` 除外），JSON number 返回 400。
活动区间使用 `minAmount <= amount < maxAmount`，`maxAmount=null` 表示无上限。同品类活动区间不得重叠，
写事务以租户配置行锁串行化校验。新建或更换 `modelVersionId` 时只允许当前租户、`category=purchase`、
`availability=AVAILABLE` 的当前已发布版本；遗留非 purchase 引用在列表标记 `LEGACY_CATEGORY`，不能静默迁移。

`match-preview` 请求为 `{ "categoryCode": "IT_DEVICE", "totalAmount": "10000.0000" }`，响应
`outcome` 仅取 `MATCHED/NO_MATCH/AMBIGUOUS/WORKFLOW_UNAVAILABLE`。它与请购提交共同调用
`ApprovalRouteResolver.evaluate`；提交路径把非 MATCHED 结果转换为原有 409，因此浏览器不计算命中。
`coverage` 按全部启用品类输出从 0 到无穷的 `COVERED/GAP/AMBIGUOUS` 半开片段，并标记默认兜底、
失效模型和 Workflow 不可用。`impact` 在内存中排除指定规则后复用同一算法，不修改数据库。

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

创建包含 `requisitionId/title/quotationDeadline/supplierIds`；草稿更新包含 `version/title/quotationDeadline/supplierIds`，不更换 requisitionId。quotationDeadline 使用 `yyyy-MM-dd HH:mm:ss` 且晚于当前时间；supplierIds 为 1–100 个正整数。仅 SENT RFQ 可以比价/定点；邀请状态为 `INVITED/QUOTED/EXPIRED/AWARDED/REJECTED`，后两种终态只允许历史查看，不能继续报价。

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

- 外部 Base path 为 `/api/asset`，保留完整路径。身份头为 `X-User-Id/X-Tenant-Id/X-User-Name`、角色及权限；不是 X-Username。TenantLine 仅作用于 ast_* 表，Outbox 中继跨租户扫描。
- 管理列表按 owner_user_id/owner_unit_id；子资源继承资产范围。我的资产、接收与退还固定 current_user_id，不因管理权限扩大个人资产查询。
- 更新/删除及已有资产命令提供 version；创建没有已有版本。活动操作占位和版本冲突拒绝写入。
- 原值、残值及金额聚合为 DECIMAL(18,2)，非空请求/响应使用 JSON 十进制字符串。数字或非法字符串会被拒绝，但当前通用 JSON 读取异常映射为 500，不能承诺400。currencyCode 校验为三位英文字母，不能据此声称校验 ISO 4217 注册表。
- 状态为 `IN_STOCK/ALLOCATED/IN_USE/MAINTENANCE/TRANSFER/DISPOSAL_PENDING/DISPOSED/SCRAPPED`。
- 内部路径为 `/api/internal/asset/**`，需要 X-Internal-Token、X-Tenant-Id，被 Gateway 阻断。详细生命周期、接收入账与审批事务见 [Asset 设计](design/asset-design.md)。

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
