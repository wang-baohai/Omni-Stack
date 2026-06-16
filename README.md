# Omni-Stack

> 基于 Spring Boot 4 + Vue 3 的微服务脚手架平台，采用 Harness 工业设计模式构建，为 AI 辅助开发提供行业最佳实践基础。

**[English](README.en.md)** | **[日本語](README.jp.md)**

---

## 特性

- **JDK 25** + Spring Boot 4.0.6 + Spring Cloud 2025.1.1 全栈最新技术
- **Spring Cloud Gateway 5.x** (WebFlux) 响应式网关，Nacos 服务发现与配置中心
- **Sentinel** 流控与熔断，**OpenFeign** 声明式服务调用
- **多提供商社交登录**：已实现 GitHub + Google + Gitee OAuth2 一键登录（策略模式 `OAuth2ProviderHandler` 可扩展），前端预留微信登录入口，HMAC-SHA256 state 签名防篡改，首次登录自动注册
- **Vue 3.5** + TypeScript 5.9 + Vite 8 + Element Plus 2.14 现代前端
- **Pinia 3** 状态管理 + **Vue Router 5** 路由守卫
- **Harness 工业设计模式**：三层高度模型（Architecture → Patterns → Code），docs/ 目录承载系统真相
- **AI 原生工程**：AGENTS.md 执行手册 + Skills 行为扩展，支持 AI 辅助开发工作流
- **三种用户创建途径**：用户自助注册（验证码 + 默认角色）、管理员后台创建、社交登录首次自动注册
- **三层 XSS 纵深防御**：Jackson 反序列化器自动清洗 `@RequestBody` + Servlet Filter 清洗查询参数 + Gateway 安全响应头，支持按租户配置全局开关和自定义黑名单规则（HTML 标签、事件处理器、危险协议、正则模式），Redis 缓存 + 数据库配置，前端管理界面完整可用
- **Maven Wrapper** 内置，克隆即可构建，无需全局安装 Maven

## 技术栈

| 层级       | 技术                                      | 版本           |
|-----------|------------------------------------------|---------------|
| JDK       | OpenJDK                                  | 25            |
| 后端框架   | Spring Boot                              | 4.0.6         |
| 微服务框架 | Spring Cloud                             | 2025.1.1      |
| 微服务框架 | Spring Cloud Alibaba                     | 2025.1.0.0    |
| API 网关   | Spring Cloud Gateway Server (WebFlux)    | 5.0.1         |
| 注册/配置  | Nacos Server                             | v3.1.1        |
| 流控/熔断  | Sentinel Dashboard                       | 1.8.8         |
| 前端框架   | Vue 3 + TypeScript                       | 3.5.35 / 5.9.3|
| 构建工具   | Vite 8 (Rolldown)                        | 8.0.14        |
| UI 框架   | Element Plus                             | 2.14.0        |
| 状态管理   | Pinia                                    | 3.0.4         |
| 路由      | Vue Router                               | 5.0.7         |
| Node.js   | Node.js LTS                              | >= 22.12.0    |

## 项目结构

```
Omni-Stack/
├── AGENTS.md                        # AI 执行手册（硬约束 + 构建命令 + 检查清单）
├── docker-compose.yml               # 中间件编排（MySQL, Redis, Nacos, Sentinel）
├── docs/                            # 系统真相文档（Architecture + Patterns + Contract）
│   ├── architecture.md                # 系统边界、模块地图、数据流、RBAC 权限体系
│   ├── api-contract.md                # 响应格式、错误码、分页、命名规范
│   ├── backend-patterns.md            # 后端分层、校验、异常、日志、安全权限、OOP 规约
│   ├── frontend-patterns.md           # 前端目录、API 层、状态管理、权限控制、组件约定
│   └── core-flows.md                  # 登录/OAuth2/RBAC 权限流程端到端追踪
├── scripts/
│   └── sql/
│       ├── init-all.sql               # 权威数据库初始化脚本（DDL + 种子数据）
│       └── init-nacos.sql           # Nacos v3.1.1 MySQL 持久化初始化脚本
├── omni-backend/                    # Maven 多模块后端
│   ├── mvnw / mvnw.cmd                # Maven Wrapper (3.9.16)
│   ├── pom.xml                        # 父 POM（依赖管理）
│   ├── omni-common/                   # 公共库：统一响应、全局异常、Jackson 配置
│   ├── omni-auth/                     # 认证服务：登录、验证码、JWT、OAuth2 (端口 8100)
│   └── omni-gateway/                  # API 网关 (WebFlux, 端口 8102)
├── omni-frontend/                   # Vue 3 SPA (开发服务器端口 3000)
│   ├── package.json
│   ├── vite.config.ts
│   ├── eslint.config.mjs
│   └── src/
│       ├── api/                       # API 层（按领域拆分文件）
│       ├── stores/                    # Pinia Store（Composition API 风格）
│       ├── router/                    # 路由定义 + 导航守卫
│       ├── views/                     # 页面组件
│       ├── layout/                    # 应用布局（侧边栏 + 顶栏 + 内容区）
│       ├── types/                     # 共享类型定义（ApiResponse, PageResult）
│       └── styles/                    # 全局样式
└── .qoder/
    └── skills/
        └── grill-me/SKILL.md          # AI Skill：方案压力测试
```

## 架构概览

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   omni-frontend  │────>│   omni-gateway    │────>│    omni-auth     │
│   Vue 3 SPA     │/api │  WebFlux :8102    │lb://│   Spring :8100  │
│   :3000         │────>│  StripPrefix=2    │────>│  Security+OAuth2│
└─────────────────┘     └──────────────────┘     └─────────────────┘
                            │
                    ┌───────┴────────┐
                    │  MySQL :3306   │  持久化存储
                    │  Redis :6379   │  缓存 + 验证码
                    │  Nacos :8848   │  服务发现 + 配置中心
                    │  Sentinel :8858│  流控 + 熔断
                    └────────────────┘
```

**请求流转**：

```
浏览器 :3000  --/api/**-->  Vite 代理  -->  Gateway :8102  --lb://-->  后端服务
```

- 前端通过 Vite 开发服务器将 `/api/**` 请求代理到 Gateway
- Gateway 通过 Nacos 服务发现自动为注册的服务创建路由

## 环境准备

### 必装软件

| 软件 | 版本要求 | 说明 |
|------|---------|------|
| JDK | 25 | 需设置 `JAVA_HOME` 环境变量 |
| Node.js | >= 22.12.0 | 含 npm |
| Docker Desktop | 任意稳定版 | 用于运行 Nacos 和 Sentinel |

> **注意**：项目内置 Maven Wrapper (3.9.16)，无需全局安装 Maven。所有 Maven 命令使用 `./mvnw` 执行。

### 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `JAVA_HOME` | - | **必须**指向 JDK 25 安装目录 |
| `NACOS_SERVER_ADDR` | `127.0.0.1:8848` | Nacos 服务器地址 |
| `NACOS_NAMESPACE` | (空) | Nacos 命名空间 |
| `SENTINEL_DASHBOARD` | `127.0.0.1:8858` | Sentinel Dashboard 地址 |
| `VITE_API_BASE_URL` | `/api` | 前端 API 基础路径 |
| `GITHUB_CLIENT_ID` | (内置) | GitHub OAuth App 的 Client ID |
| `GITHUB_CLIENT_SECRET` | (内置) | GitHub OAuth App 的 Client Secret |
| `GITHUB_REDIRECT_URI` | `http://localhost:8100/api/auth/oauth2/github/callback` | GitHub 授权回调地址 |
| `GITEE_CLIENT_ID` | (内置) | Gitee OAuth App 的 Client ID |
| `GITEE_CLIENT_SECRET` | (内置) | Gitee OAuth App 的 Client Secret |
| `GITEE_REDIRECT_URI` | `http://localhost:8100/api/auth/oauth2/gitee/callback` | Gitee 授权回调地址 |
| `GOOGLE_CLIENT_ID` | (内置) | Google Cloud Console OAuth 2.0 客户端的 Client ID |
| `GOOGLE_CLIENT_SECRET` | (内置) | Google Cloud Console OAuth 2.0 客户端的 Client Secret |
| `GOOGLE_REDIRECT_URI` | `http://localhost:8100/api/auth/oauth2/google/callback` | Google 授权回调地址 |
| `OAUTH2_STATE_SECRET` | (内置) | OAuth2 state 参数的 HMAC-SHA256 签名密钥，所有社交登录提供商共用 |

### 社交登录配置（GitHub / Google / Gitee）

系统采用 `OAuth2ProviderHandler` 策略模式，每个提供商实现该接口即可接入，新增提供商无需修改核心逻辑。

#### 1. 创建 OAuth App

**GitHub**：

1. 登录 GitHub → Settings → Developer settings → [OAuth Apps](https://github.com/settings/developers) → New OAuth App
2. 填写以下信息：
   - **Application name**: Omni-Stack（任意名称）
   - **Homepage URL**: `http://localhost:3000`
   - **Authorization callback URL**: `http://localhost:8100/api/auth/oauth2/github/callback`
3. 创建后复制 **Client ID** 和 **Client Secret**

**Google**：

1. 登录 [Google Cloud Console](https://console.cloud.google.com/) → APIs & Services → Credentials
2. 创建 OAuth 2.0 Client ID（应用类型选择 Web application）
3. 在 Authorized redirect URIs 中添加：`http://localhost:8100/api/auth/oauth2/google/callback`
4. 创建后复制 **Client ID** 和 **Client Secret**

**Gitee**：

1. 登录 Gitee → 设置 → [第三方应用](https://gitee.com/oauth/applications) → 创建应用
2. 填写以下信息：
   - **应用名称**: Omni-Stack（任意名称）
   - **应用主页**: `http://localhost:3000`
   - **应用回调地址**: `http://localhost:8100/api/auth/oauth2/gitee/callback`
3. 创建后复制 **Client ID** 和 **Client Secret**

#### 2. 配置凭证

通过环境变量或修改 `omni-auth/src/main/resources/application.yml`：

```yaml
auth:
  oauth2:
    github:
      client-id: ${GITHUB_CLIENT_ID:你的ClientID}
      client-secret: ${GITHUB_CLIENT_SECRET:你的ClientSecret}
      redirect-uri: ${GITHUB_REDIRECT_URI:http://localhost:8100/api/auth/oauth2/github/callback}
    google:
      client-id: ${GOOGLE_CLIENT_ID:你的ClientID}
      client-secret: ${GOOGLE_CLIENT_SECRET:你的ClientSecret}
      redirect-uri: ${GOOGLE_REDIRECT_URI:http://localhost:8100/api/auth/oauth2/google/callback}
    gitee:
      client-id: ${GITEE_CLIENT_ID:你的ClientID}
      client-secret: ${GITEE_CLIENT_SECRET:你的ClientSecret}
      redirect-uri: ${GITEE_REDIRECT_URI:http://localhost:8100/api/auth/oauth2/gitee/callback}
    state-secret: ${OAUTH2_STATE_SECRET:你的StateSecret}
```

> **注意**：`redirect_uri` 必须与对应 OAuth App 中设置的回调地址完全一致。`state-secret` 用于 HMAC-SHA256 签名 state 参数，建议设置一个随机字符串。

#### 3. 使用

前端登录页面点击 "GitHub"、"Google" 或 "Gitee" 按钮即可发起社交登录。首次登录会自动创建本地用户（用户名格式：GitHub 为 `gh_{login}`，Google 为 `go_{email_prefix}`，Gitee 为 `ge_{login}`）。

## 快速开始

### 第一步：启动中间件

```bash
# 一键启动所有中间件（MySQL, Redis, Nacos, Sentinel）
docker compose up -d

# 查看服务状态
docker compose ps
```

> 等待 Nacos 完全启动（约 30 秒）后再启动后端服务。访问 `http://127.0.0.1:8080/` 确认 Nacos 就绪（默认账号 nacos/nacos）。
> MySQL 容器首次启动时会自动执行 `scripts/sql/init-all.sql` 初始化数据库。

### 第二步：构建并启动后端

```bash
# 设置 JAVA_HOME（Spring Boot 4 构建插件需要 JDK 17+）
export JAVA_HOME="C:/APP/JDK25/jdk-25.0.2"   # Windows
# export JAVA_HOME="/path/to/jdk-25"           # macOS / Linux
export PATH="$JAVA_HOME/bin:$PATH"

# 进入后端目录，构建所有模块
cd omni-backend
./mvnw clean install

# 启动 Auth 服务（端口 8100）
cd omni-auth
./mvnw spring-boot:run

# 启动 Gateway（端口 8102，新开终端窗口）
cd omni-gateway
./mvnw spring-boot:run
```

**构建顺序说明**：`omni-common` 必须先安装，其他模块才能编译。

### 第三步：启动前端

```bash
cd omni-frontend

# 安装依赖
npm install

# 启动开发服务器（端口 3000，自动代理 /api 到 Gateway :8102）
npm run dev
```

### 第四步：验证服务

| 验证项 | 命令 / URL | 预期结果 |
|--------|----------|---------|
| 前端页面 | `http://localhost:3000` | 登录页面 |
| Gateway 路由 | `curl http://localhost:8102/actuator/gateway/routes` | 返回路由列表 JSON |
| Nacos 控制台 | `http://127.0.0.1:8080/` | Nacos 管理界面 |
| Sentinel 控制台 | `http://localhost:8858` | Sentinel Dashboard |

**启动顺序**：MySQL → Redis → Nacos → Sentinel → 后端服务（Auth, Gateway）→ 前端

## 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端开发服务器 | 3000 | Vite dev server，代理 /api 请求 |
| 认证服务 | 8100 | Spring Security + OAuth2 Authorization Server |
| API 网关 | 8102 | Spring Cloud Gateway (WebFlux) |
| MySQL | 3306 | 主数据库（omni_auth 库） |
| Redis | 6379 | 验证码缓存 |
| Nacos | 8080, 8848 | 管理界面 (8080) + 服务发现与配置中心 (8848) |
| Sentinel | 8858 | 流控仪表盘 |

## 模块说明

### omni-common（公共库）

所有后端模块共享的基础设施，**不可独立运行**：

| 组件 | 文件 | 职责 |
|------|------|------|
| 统一响应 | `R<T>` | 所有 API 返回 `{ code, message, data }` 格式 |
| 分页结构 | `PageResult<T>` | 分页响应 `{ records, total, size, current, pages }` |
| 业务异常 | `BusinessException` | 携带错误码的业务异常 |
| 全局异常处理 | `GlobalExceptionHandler` | 统一捕获异常并转换为 `R<Void>` 响应 |
| Jackson 配置 | `JacksonConfig` | Java 8 时间类型序列化（`yyyy-MM-dd HH:mm:ss`） |
| Web 配置 | `WebMvcConfig` | CORS 跨域配置 |
| 基础实体 | `BaseEntity` | 包含审计字段（id, createTime, updateTime, createBy, updateBy） |
| XSS 防护 | `XssFilter` / `XssSanitizer` / `XssStringDeserializer` | 三层纵深防御：Jackson 自动清洗 JSON + Servlet Filter 清洗查询参数 + ThreadLocal 规则持有 |
| XSS SPI | `XssConfigProvider` | 配置加载 SPI 接口，按租户从 Redis/DB 获取 XSS 开关和规则列表 |
| XSS 自动配置 | `XssAutoConfiguration` | 自动注册 Filter + Jackson Module，下游模块零配置继承防护能力 |

> `omni-common` 通过 Spring Boot 自动配置机制（`AutoConfiguration.imports`）注册 Bean，下游模块无需手动 `@ComponentScan`。

### omni-auth（认证服务）

基于 Spring Security 7 + OAuth2 Authorization Server 的认证微服务：

- **用户登录**：用户名 + 密码 + 图形验证码 + 多租户，签发 RS256 JWT
- **多提供商社交登录**：基于 `OAuth2ProviderHandler` 策略模式实现可扩展社交登录架构，已接入 GitHub、Google 和 Gitee 三个提供商，前端预留微信登录入口。HMAC-SHA256 state 签名防篡改，首次登录自动创建本地用户并关联第三方身份（`sys_user_oauth_provider` 表）
- **OAuth2 授权**：Authorization Code + PKCE 流程，支持第三方集成
- **设备授权码模式**（RFC 8628）：为 IoT 设备、CLI 工具等无浏览器场景提供授权能力，通过 `omni-device` 客户端实现，前端 `/device` 页面模拟设备端发起授权请求并轮询 token，`/device/verify` 页面供用户在另一台设备上扫码或输入验证码完成授权
- **客户端管理**：CRUD 操作 `oauth2_registered_client`，支持动态注册
- **多租户 RBAC**：基于 `tenantId:username` 格式的用户解析 + 角色权限树
- **RBAC 权限体系**：功能权限（动态菜单过滤 + `v-permission` 按钮级控制 + `@PreAuthorize` API 鉴权）+ 数据权限（MyBatis-Plus `DataPermissionInterceptor` SQL 自动拦截，六级 dataScope 零侵入过滤）
- **JWT 签名**：RSA 密钥对，JWK 端点供 Gateway 获取公钥验证
- **XSS 防护配置管理**：前端 `系统管理 → XSS防护配置` 页面支持全局开关切换和黑名单规则 CRUD，支持四种规则类型（HTML 标签、事件处理器、危险协议、自定义正则），配置按租户隔离，Redis 缓存 30 分钟 TTL + 写操作主动失效

### omni-gateway（API 网关）

基于 Spring Cloud Gateway Server (WebFlux) 的响应式网关：

- 路由转发：通过 Nacos 服务发现自动路由到注册的后端服务（StripPrefix=2）
- 服务发现：Nacos 自动路由注册的服务
- 认证过滤器：`AuthFilter`（JWT RS256 签名验证 + claims 提取 + 身份头注入）
- CORS 配置：`CorsConfig` 处理跨域请求

### omni-frontend（Vue 3 SPA）

| 层级 | 目录 | 职责 |
|------|------|------|
| API 层 | `src/api/` | 按领域拆分文件，统一使用 Axios 实例，类型安全 |
| Store 层 | `src/stores/` | Pinia Composition API 风格，一 Store 一领域 |
| 路由层 | `src/router/` | 懒加载路由 + 导航守卫（默认要求认证） |
| 视图层 | `src/views/` | 页面组件，SFC 顺序：script → template → style；包含 `device/` 子目录实现 OAuth2 设备授权码模式的前端交互 |
| 布局层 | `src/layout/` | 侧边栏 + 顶栏 + 内容区应用外壳 |
| 类型层 | `src/types/` | 共享类型定义（ApiResponse, PageResult 的唯一来源） |
| 样式层 | `src/styles/` | 全局重置 + 布局样式 |

## RBAC 权限体系

项目实现了完整的 RBAC 权限模型，分为功能权限和数据权限两个独立子系统。详细设计见 [`docs/architecture.md`](docs/architecture.md) 的 RBAC Permission System 章节，端到端流程见 [`docs/core-flows.md`](docs/core-flows.md) 的 Flow 5 和 Flow 6。

### 功能权限

三层防护控制用户"能做什么"：

| 层级 | 机制 | 实现 |
|------|------|------|
| 动态菜单 | 后端按用户权限递归过滤菜单树 | `MenuController` → `usePermissionStore` → 动态路由注册 |
| 按钮控制 | Vue 自定义指令控制 DOM 显隐 | `v-permission="'system:user:create'"` → `display:none` |
| API 鉴权 | Spring Security 方法级权限校验 | `@PreAuthorize("hasAuthority('system:user:create')")` |

### 数据权限

基于 MyBatis-Plus `DataPermissionInterceptor` 的 SQL 自动拦截，业务代码零侵入，控制用户"能看哪些数据"：

| dataScope | 含义 |
|-----------|------|
| `ALL` | 所有数据（跨租户） |
| `TENANT` | 本租户所有数据 |
| `DEPT_AND_BELOW` | 本部门及下级部门 |
| `DEPT` | 仅本部门 |
| `CUSTOM` | 自定义部门集合 |
| `SELF` | 仅自己 |

**核心流程**：请求到达 → `DataScopeResolveFilter` 解析角色 dataScope（最宽松优先） → 写入 `DataScopeContext`（ThreadLocal）→ `DataPermissionInterceptor` 自动追加 WHERE 条件 → 请求结束清除上下文。

## 用户创建

支持三种用户创建途径，所有途径均自动分配 `USER` 默认角色（`data_scope=SELF`，仅能查看自己的数据）：

| 途径 | 入口 | 认证要求 | 租户确定 | 密码 |
|------|------|---------|---------|------|
| 自助注册 | 注册页 `/register` | 无（公开端点） | 用户下拉选择 | 用户设置（BCrypt） |
| 管理员创建 | 用户管理页 | `system:user:create` | 管理员指定 | 管理员设置（BCrypt） |
| 社交登录 | OAuth2 回调 | 无（第三方认证） | HMAC state 参数 | 无（仅社交登录） |

详细流程见 [`docs/core-flows.md`](docs/core-flows.md) Flow 7。

## 权限协作模型

租户、组织、角色、功能权限、数据权限五要素如何协作完成完整的访问控制：

```
租户(Tenant) ─── 隔离边界：用户名租户内唯一，数据默认按租户隔离
  │
  ├── 用户(User) ─── 属于一个租户，可拥有多个角色
  │     │
  │     ├── 角色(Role) ─── 连接用户与权限的桥梁
  │     │     ├── 功能权限(Permission) ─── 控制"能做什么"（菜单/按钮/API）
  │     │     └── 数据范围(DataScope) ─── 控制"能看哪些数据"
  │     │
  │     └── 组织单元(OrgUnit) ─── 用户的部门归属，数据权限的锚点
  │
  └── 权限树(Permission Tree) ─── DIRECTORY → MENU → BUTTON → API 四层结构
```

**协作流程**：

1. **登录时**：根据 `(tenantId, username)` 查找用户 → 加载角色 → 加载权限 → 签发 JWT
2. **功能控制**：JWT `scope` claim 携带权限码 → 前端动态菜单 + `v-permission` 按钮隐藏 → 后端 `@PreAuthorize` API 鉴权
3. **数据控制**：角色 `data_scope` 决定可见范围 → `DataScopeResolveFilter` 解析最宽松范围 → MyBatis-Plus 自动追加 WHERE 条件
4. **组织关联**：用户的 `primaryUnitId` 作为数据权限锚点 → `DEPT`/`DEPT_AND_BELOW` 范围基于物化路径查询上下级

## 统一响应格式

所有 API 均使用 `R<T>` 包装，前后端保持严格一致的契约。详见 [`docs/api-contract.md`](docs/api-contract.md)。

**成功响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": { "id": 1, "username": "demo", "email": "demo@example.com" }
}
```

**失败响应**：
```json
{
  "code": 400,
  "message": "username: Username is required; email: Email is required"
}
```

**分页响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [{ "id": 1, "username": "demo" }],
    "total": 100,
    "size": 10,
    "current": 1,
    "pages": 10
  }
}
```

## 开发指南（新成员必读）

### 1. 先读文档，再写代码

项目采用 **Harness 工业设计模式**，将系统知识分为三层：

| 层级 | 内容 | 位置 |
|------|------|------|
| Layer 1: Architecture | 系统边界、模块职责、数据流、RBAC 权限体系、约束 | `docs/architecture.md` |
| Layer 2: Patterns | 后端/前端编码模式、API 契约、安全权限、核心流程 | `docs/backend-patterns.md`、`docs/frontend-patterns.md`、`docs/api-contract.md`、`docs/core-flows.md` |
| Layer 3: Code | 具体的函数、类、组件实现 | 源代码文件 |

**规则**：修改代码前，先确认对应的 docs/ 文档。如果架构或契约发生变化，必须先更新 docs/ 再改代码。

### 2. 后端开发规范

- **分层**：Controller → Service (接口) → ServiceImpl → Repository
- **依赖注入**：`@RequiredArgsConstructor` + `final` 字段，禁止 `@Autowired` 字段注入
- **返回值**：所有 Controller 方法返回 `R<T>`
- **异常**：业务异常抛 `BusinessException`，由 `GlobalExceptionHandler` 统一处理
- **日志**：`@Slf4j` + 参数化占位符，禁止 `System.out.println`
- **详细规约**：阅读 `docs/backend-patterns.md`

### 3. 前端开发规范

- **API 层**：按领域一文件（`src/api/user.ts`），统一使用 `request.ts` 的 Axios 实例
- **类型**：共享类型在 `src/types/api.ts`，禁止重复定义
- **Store**：Pinia Composition API 风格，`use` 前缀命名
- **组件**：SFC 顺序 `<script setup>` → `<template>` → `<style scoped>`
- **路由**：懒加载 + `meta` 声明（title, icon, requiresAuth）
- **详细规约**：阅读 `docs/frontend-patterns.md`

### 4. 提交代码前检查

```bash
# 后端编译验证
cd omni-backend && ./mvnw clean install

# 前端构建 + Lint 验证
cd omni-frontend && npm run build && npm run lint
```

完整检查清单见 `AGENTS.md` 的 Completion Checklist 章节。

### 5. 常见陷阱

| 陷阱 | 说明 | 解决方案 |
|------|------|---------|
| Gateway 路由不生效 | 5.x 版本配置前缀已变更 | 使用 `spring.cloud.gateway.server.webflux`，详见 `AGENTS.md` Important Notes |
| Maven 编译报 class version 错误 | JAVA_HOME 未指向 JDK 25 | 设置 `JAVA_HOME` 到 JDK 25 目录 |
| 前端类型不匹配 | `ApiResponse` 在多处定义 | 只从 `@/types/api` 导入，禁止重复定义 |
| Actuator gateway 端点 404 | 需显式启用 | 配置 `management.endpoint.gateway.enabled: true` |
| GitHub 社交登录回调 404 | OAuth App 未创建或 Client ID 是占位符 | 按上方"社交登录配置"创建 GitHub OAuth App 并填入真实凭证 |
| Google 社交登录回调 404 | Google Cloud Console OAuth 客户端未创建或 Client ID 是占位符 | 按上方"社交登录配置"在 Google Cloud Console 创建 OAuth 2.0 客户端并填入真实凭证 |
| Gitee 社交登录回调 404 | Gitee 第三方应用未创建或 Client ID 是占位符 | 按上方"社交登录配置"在 Gitee 创建第三方应用并填入真实凭证 |
| Google 登录后卡在回调页面 | 数据库缺少 `sys_user_oauth_provider` 表 | 确保 `init-all.sql` 已执行，该表同时存储所有提供商的绑定关系 |
| GitHub 登录后卡在回调页面 | 数据库缺少 `sys_user_oauth_provider` 表 | 确保 `init-all.sql` 已执行（包含该表），或手动创建 |
| Gitee 登录后卡在回调页面 | 同 GitHub，`sys_user_oauth_provider` 表缺失 | 确保 `init-all.sql` 已执行，该表同时存储所有提供商的绑定关系 |
| 社交登录 state 签名验证失败 | `OAUTH2_STATE_SECRET` 未配置或重启后变更 | 设置固定的 `OAUTH2_STATE_SECRET` 环境变量，确保签名密钥一致 |
| Nacos 重启后配置丢失 | 使用内嵌 Derby 数据库，无持久化 | 使用本项目的 `init-nacos.sql` 切换到 MySQL 外部存储 |

## AI 原生工程实践

本项目支持 AI 辅助开发工作流：

- **`AGENTS.md`**：AI 执行手册，定义硬约束、执行规则和完成检查清单
- **`docs/` 目录**：系统真相文档，AI 在修改代码前先阅读这些文档以理解系统上下文
- **`.qoder/skills/`**：AI 行为扩展单元（如 `/grill-me` 方案压力测试 Skill）

核心理念：**前两层（Architecture + Patterns）定住，第三层（Code）才能放心交给 AI 高速生产。**

## 许可证

[Apache License 2.0](LICENSE)
