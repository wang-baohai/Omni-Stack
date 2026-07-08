# Omni-Stack

> 基于 Spring Boot 4 + Vue 3 的微服务脚手架平台，采用 Harness 工业设计模式构建，为 AI 辅助开发提供行业最佳实践基础。

**[English](README.en.md)** | **[日本語](README.jp.md)** | **[한국어](README.kr.md)**

**GitHub**: https://github.com/wang-baohai/Omni-Stack | **Gitee**: https://gitee.com/wang-baohai/Omni-Stack

**联系邮箱**: wangbaohai1993@gmail.com

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
- **Common Starter 生态**：`omni-common` 拆分为 8 个模块（core / common / mybatis / redis / redis-reactive / operlog / job / mqlog），新服务通过 Maven 依赖即可获得 MyBatis-Plus 分页、Redis 缓存、XSS 防护、操作日志采集、定时任务调度、可靠消息发送等能力，`AutoConfiguration.imports` 零配置自动装配
- **基础数据与任务管理**：`omni-base` 服务（端口 8101）提供数据字典管理、系统任务管理、用户任务管理、操作日志查看，Redis cache-aside 缓存，前端完整管理页面
- **操作日志审计追踪**：基于 `@OperLog` 注解 + AOP 切面无侵入采集，自动记录 who/when/what/changed 完整审计信息，实体变更快照自动 diff（oldValue vs newValue）支持数据回溯，RocketMQ 异步发送不阻塞业务请求，热冷表分离归档策略（180 天保留 + 冷表长期留存）兼顾查询性能与合规要求，与审计日志（`sys_audit_log`）和登录日志（`sys_login_log`）形成互补，共同构成完整的审计追踪体系
- **双轨制定时任务调度**：基于 XXL-JOB 3.3.1 实现系统任务（`@XxlJob` + `@SystemJobMeta` 双注解驱动，自动注册调度中心）和用户任务（SPI 模式，`UserJobHandler` 接口 + JSON 参数路由）两种模式，前端支持 Cron 编辑器、动态参数表单、执行日志实时推送
- **Transactional Outbox 可靠消息**：基于本地发件箱模式，业务操作与消息记录写入同一事务保证原子性，XXL-JOB 定时中继异步投递，指数退避重试 + 死信管理，前端运维监控页面支持消息查看、重发、忽略操作
- **可视化 BPMN 工作流引擎**：基于 Flowable 7.x 实现，`omni-workflow` 独立微服务（端口 8103），前端 BPMN 可视化设计器支持拖拽建模，双版本管理（业务版本 DRAFT → PUBLISHED → ARCHIVED + Flowable 引擎版本），多实例会签支持 ALL/ANY 审批模式，动态候选人解析（`omni:assignment` JSON 扩展 + `ScopedRoleAssignmentListener` 运行时解析），审批记录 + 流程进度图 + 抄送通知完整可用
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
| 消息队列   | Apache RocketMQ                        | 5.3.2         |
| 任务调度   | XXL-JOB Admin                          | 3.3.1         |
| 工作流引擎 | Flowable BPMN                          | 7.x           |
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
├── start.bat / start.sh              # 一键启动脚本（自动启动 Docker + 端口保护 + 容器）
├── stop.bat / stop.sh                # 一键停止脚本
├── docker-compose.yml               # 中间件编排（MySQL, Redis, Nacos, RocketMQ, XXL-JOB）
├── docker/
│   └── rocketmq/broker.conf          # RocketMQ Broker 配置文件
├── docs/                            # 系统真相文档（Architecture + Patterns + Contract）
│   ├── architecture.md                # 系统边界、模块地图、数据流、RBAC 权限体系
│   ├── api-contract.md                # 响应格式、错误码、分页、命名规范
│   ├── backend-patterns.md            # 后端分层、校验、异常、日志、安全权限、OOP 规约
│   ├── frontend-patterns.md           # 前端目录、API 层、状态管理、权限控制、组件约定
│   └── core-flows.md                  # 登录/OAuth2/RBAC 权限流程端到端追踪
├── scripts/
│   └── sql/
│       ├── init-all.sql               # 权威数据库初始化脚本（DDL + 种子数据）
│       ├── init-nacos.sql           # Nacos v3.1.1 MySQL 持久化初始化脚本
│       └── init-xxl-job.sql          # XXL-JOB v3.3.1 数据库初始化脚本
├── omni-backend/                    # Maven 多模块后端
│   ├── mvnw / mvnw.cmd                # Maven Wrapper (3.9.16)
│   ├── pom.xml                        # 父 POM（依赖管理）
│   ├── omni-common-core/              # 纯 POJO：R<T>, PageResult, BaseEntity, XSS SPI
│   ├── omni-common/                   # Web 自动配置：Jackson, CORS, 全局异常, XSS Filter
│   ├── omni-common-mybatis/           # MyBatis-Plus Starter：分页插件, MySQL 驱动
│   ├── omni-common-redis/             # 阻塞式 Redis Starter：RedisTemplate, RedisUtils
│   ├── omni-common-redis-reactive/    # 响应式 Redis Starter：WebFlux 服务专用
│   ├── omni-common-operlog/             # 操作日志 Starter：AOP 切面 + MQ 生产者 + 实体 diff
│   ├── omni-common-job/                 # 定时任务 Starter：XXL-JOB 自动装配 + Admin Client + 系统任务注册
│   ├── omni-common-workflow/            # 工作流 Starter：Flowable 自动装配 + 审批 SPI + 租户过滤
│   ├── omni-auth/                     # 认证服务：登录、验证码、JWT、OAuth2 (端口 8100)
│   ├── omni-base/                     # 基础数据服务：数据字典管理 (端口 8101)
│   ├── omni-workflow/                   # 工作流引擎服务：Flowable BPMN (端口 8103)
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
                                 ┌─────────────────┐
                                 │    omni-auth     │
                                 │   Spring :8100  │
                                 │  Security+OAuth2│
                                 └─────────────────┘
                                        ▲
┌─────────────────┐     ┌──────────────────┐
│   omni-frontend  │────>│   omni-gateway    │lb://
│   Vue 3 SPA     │/api │  WebFlux :8102    │────>┌─────────────────┐
│   :3000         │────>│  StripPrefix=2    │     │    omni-base     │
└─────────────────┘     └──────────────────┘     │   Spring :8101  │
                            │                    │  数据字典管理    │
                            │                    └─────────────────┘
                            │                    ┌─────────────────┐
                            │                    │  omni-workflow   │
                            │                    │  Flowable :8103  │
                            │                    └─────────────────┘
                    ┌───────┴────────┐
                    │  MySQL :3306   │  持久化存储
                    │  Redis :6379   │  缓存 + 验证码 + 字典缓存
                    │  Nacos :8848   │  服务发现 + 配置中心
                    │  Sentinel :8858│  流控 + 熔断
                    │  RocketMQ :9876│  消息队列（操作日志异步投递）
                    │  XXL-JOB :18080│  分布式任务调度中心
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
| Docker Desktop | 任意稳定版 | 用于运行 MySQL、Redis、Nacos、Sentinel、RocketMQ、XXL-JOB |

> **注意**：项目内置 Maven Wrapper (3.9.16)，无需全局安装 Maven。所有 Maven 命令使用 `./mvnw` 执行。

### 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `JAVA_HOME` | - | **必须**指向 JDK 25 安装目录 |
| `NACOS_SERVER_ADDR` | `127.0.0.1:8848` | Nacos 服务器地址 |
| `NACOS_NAMESPACE` | (空) | Nacos 命名空间 |
| `SENTINEL_DASHBOARD` | `127.0.0.1:8858` | Sentinel Dashboard 地址 |
| `ROCKETMQ_NAME_SERVER` | `127.0.0.1:9876` | RocketMQ NameServer 地址 |
| `XXL_JOB_ADMIN_ADDRESSES` | `http://127.0.0.1:18080/xxl-job-admin` | XXL-JOB Admin 地址 |
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

项目提供一键启动脚本，自动完成 Docker Desktop 启动、端口保护和容器部署：

| 平台 | 启动 | 停止 |
|------|------|------|
| Windows | 右键 `start.bat` → 以管理员身份运行 | 右键 `stop.bat` → 以管理员身份运行 |
| Linux / macOS | `./start.sh` | `./stop.sh` |

**启动脚本自动完成**：

1. **检测 Docker Desktop** — 未安装时提示下载并自动打开下载页面
2. **启动 Docker 引擎** — 如未运行则自动拉起，等待就绪后继续
3. **端口保护** (Windows) — 防止 Hyper-V/WSL2 动态占用项目端口（3306、6379、8080、8848、9848、9876、10909、10911、10912、18080）
4. **启动容器** — 执行 `docker compose up -d`

```bash
# 启动所有中间件
./start.sh                          # Linux / macOS
# 或 Windows: 右键 start.bat → 以管理员身份运行

# 仅启动指定服务
./start.sh mysql redis

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

# 启动 Base 服务（端口 8101，新开终端窗口）
cd omni-base
./mvnw spring-boot:run

# 启动 Gateway（端口 8102，新开终端窗口）
cd omni-gateway
./mvnw spring-boot:run
```

**构建顺序说明**：`omni-common-core` 必须先安装，然后 `omni-common`、`omni-common-mybatis`、`omni-common-redis`、`omni-common-redis-reactive`，最后才能编译 `omni-auth`、`omni-base`、`omni-gateway`。Maven reactor 会根据 `<modules>` 声明顺序自动解析。

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
| XXL-JOB 调度中心 | `http://localhost:18080/xxl-job-admin` | XXL-JOB Admin Web UI（admin/123456） |
| RocketMQ | `telnet localhost 9876` | NameServer 连通性验证 |

**启动顺序**：MySQL → Redis → Nacos → Sentinel → RocketMQ → XXL-JOB → 后端服务（Auth, Base, Gateway）→ 前端

## 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端开发服务器 | 3000 | Vite dev server，代理 /api 请求 |
| 认证服务 | 8100 | Spring Security + OAuth2 Authorization Server |
| 基础数据服务 | 8101 | 数据字典管理，Redis cache-aside 缓存 |
| API 网关 | 8102 | Spring Cloud Gateway (WebFlux) |
| 工作流引擎服务 | 8103 | Flowable BPMN 流程引擎 |
| MySQL | 3306 | 主数据库（omni_auth + omni_base + xxl_job 库） |
| Redis | 6379 | 验证码缓存 + 字典缓存 + XSS 配置缓存 |
| Nacos | 8080, 8848 | 管理界面 (8080) + 服务发现与配置中心 (8848) |
| Sentinel | 8858 | 流控仪表盘 |
| XXL-JOB Admin | 18080 | 分布式任务调度中心（Web UI），默认账号 admin/123456 |
| RocketMQ NameServer | 9876 | 消息队列命名服务器 |
| RocketMQ Broker | 10909, 10911, 10912 | 消息队列代理节点 |

## 模块说明

### Common Starter 生态（8 模块）

`omni-common` 已拆分为 8 个职责单一的模块，形成 Common Starter 生态。新微服务引入即用，**均不可独立运行**：

| 模块 | 职责 | 适用服务类型 |
|------|------|-------------|
| `omni-common-core` | 纯 POJO：`R<T>`、`PageResult<T>`、`BaseEntity`、`BusinessException`、`XssConfigProvider` SPI、`UserJobHandler` SPI | 所有服务 |
| `omni-common` | Web 自动配置：Jackson 时间序列化、CORS、全局异常处理、XSS Filter + Jackson Module 自动注册 | Servlet 服务 |
| `omni-common-mybatis` | MyBatis-Plus + MySQL 驱动 + 分页插件 + YAML 默认配置，`@ConditionalOnMissingBean` 支持覆盖 | Servlet 服务 |
| `omni-common-redis` | 阻塞式 Redis + RedisTemplate 序列化 + RedisUtils | Servlet 服务 |
| `omni-common-redis-reactive` | 响应式 Redis + ReactiveRedisTemplate + ReactiveRedisUtils | WebFlux 服务（Gateway） |
| `omni-common-operlog` | 操作日志 Starter：`@OperLog` AOP 切面 + RocketMQ 生产者 + 实体变更 diff | 业务服务 |
| `omni-common-job` | 定时任务 Starter：XXL-JOB 自动装配 + Admin Client + 系统任务注册表 + `@SystemJobMeta` 双注解驱动 | 业务服务 |
| `omni-common-mqlog` | MQ 消息可靠性 Starter：Transactional Outbox + 中继投递 + 死信管理 + 租户隔离 | Servlet 服务 |
| `omni-common-workflow` | 工作流 Starter：Flowable 自动配置、`ApprovalService` SPI、`UserGroupLookup`、`TenantInfoFilter` | 工作流服务 |

> 所有 Starter 通过 Spring Boot 自动配置机制（`AutoConfiguration.imports`）注册 Bean，下游模块无需手动 `@ComponentScan`。
> `omni-common-redis` 和 `omni-common-redis-reactive` 不可混用，WebFlux 服务只能依赖 reactive 版本。

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

### omni-common-operlog（操作日志 Starter）

基于 AOP + RocketMQ 的操作日志采集框架，为业务服务提供无侵入式审计追踪能力：

- **无侵入采集**：`@OperLog` 注解 + `OperLogAspect` AOP 切面，自动采集请求上下文（username、tenantId、IP、请求参数）和实体变更快照
- **实体变更 diff**：`EntityDiffer` 字段级差异比对，UPDATE 操作仅记录变更字段，支持数据回溯
- **RocketMQ 异步**：`OperLogProducer` 异步发送日志消息，不阻塞业务请求响应
- **热冷表分离**：热表 `sys_oper_log` 保留近 180 天数据供快速查询，冷表 `sys_oper_log_archive` 长期留存满足合规要求，`OperLogArchiver` 每日 02:00 自动归档
- **与审计日志互补**：操作日志记录业务数据变更（who/when/what/changed），审计日志（`sys_audit_log`）记录安全事件，登录日志（`sys_login_log`）记录登录行为，三者共同构成完整审计追踪体系
- **omni-auth 禁用**：认证模块不引入该依赖，认证行为由 `sys_login_log` + `sys_audit_log` 覆盖

### omni-common-mqlog（MQ 消息可靠性 Starter）

基于 Transactional Outbox 模式的可靠消息发送框架，保证业务操作与消息记录的原子性：

- **本地发件箱**：`ReliableMessageTemplate` 在业务事务中写入 `sys_mq_message` 表（PENDING 状态），与业务操作保持事务一致性
- **定时中继**：`MqMessageRelayJob`（XXL-JOB）每 10 秒轮询待投递消息，通过 `MessageSender` 策略模式发送到 MQ
- **指数退避重试**：失败消息按 `2^retryCount × 10s` 退避等待，超过最大重试次数进入死信状态
- **租户隔离**：tenantId 显式传参，查询接口按租户过滤，中继任务不区分租户（后台进程）
- **多 MQ 扩展**：`MessageSender` 策略接口，当前实现 `RocketMqMessageSender`，新增 Kafka 等只需实现接口
- **前端管理**：运维监控菜单下的消息记录页面，支持分页查询、详情查看、手动重发、标记忽略

### omni-base（基础数据服务）

基础数据与任务管理微服务，涵盖数据字典、定时任务、操作日志等能力：

- **字典类型管理**：`sys_dict_type` 表，支持列表查询、详情获取、创建、更新、删除、状态切换，11 个 API 端点完整实现
- **字典数据管理**：`sys_dict_data` 表，按类型编码关联，支持列表查询、创建、更新、删除、缓存刷新
- **Redis cache-aside 缓存**：TTL 30 分钟，写操作主动失效（write-through invalidation），`dict:{typeCode}` 键格式
- **系统任务管理**：合并 `SystemJobRegistry` 元数据与 XXL-JOB 运行状态，提供注册/启动/停止/触发/注销全生命周期操作，`job:system-job:*` 权限码
- **用户任务管理**：SPI 模式任务类型 + 任务实例 + 执行日志，支持用户自助创建、Cron 调度、所有权校验
- **操作日志查看**：热表查询 + 分页筛选，支持按模块、操作类型、操作人和时间范围过滤
- **前端管理页面**：字典管理（master-detail 布局）、系统任务、任务类型、工作台我的任务，`base:dict` / `job:*` 权限码
- **XSS 防护继承**：实现 `XssConfigProvider` SPI，自动获得三层 XSS 防御能力

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
| 视图层 | `src/views/` | 页面组件，SFC 顺序：script → template → style；包含 `device/`（设备授权）、`job/`（定时任务管理）、`system/`（系统管理）等子目录 |
| 布局层 | `src/layout/` | 侧边栏 + 顶栏 + 内容区应用外壳 |
| 类型层 | `src/types/` | 共享类型定义（ApiResponse, PageResult 的唯一来源） |
| 样式层 | `src/styles/` | 全局重置 + 布局样式 |

## 定时任务系统

项目基于 **XXL-JOB 3.3.1** 实现了双轨制定时任务调度架构，支持系统任务和用户任务两种模式。深度技术细节见 [`docs/scheduling.md`](docs/scheduling.md)。

### 架构概览

- **omni-common-job**：封装 `XxlJobAutoConfiguration`、`XxlJobAdminClient`、`SystemJobRegistry`，提供统一的任务注册与管理能力
- **omni-common-core**：定义 `UserJobHandler` SPI 接口和 `UserJobMessage` POJO
- **omni-base**：业务层，实现具体的系统任务和用户任务 Handler

### 系统任务

通过 `@XxlJob` + `@SystemJobMeta` 双注解驱动，`SystemJobRegistry` 在启动时自动扫描并注册到 XXL-JOB Admin。以 `OperLogArchiver`（操作日志归档）为例：Bean 注册 → 自动发现 → REST API 管理 → XXL-JOB 调度执行。管理接口需要 `job:system-job:*` 权限。

### 用户任务

采用 SPI 模式：业务方实现 `UserJobHandler` 接口并注册为 Spring Bean，`UserJobHandlerRegistry` 自动发现。所有用户任务共享一个 `@XxlJob("userJobExecuteHandler")` 入口，通过 JSON `executorParam` 路由到具体 Handler。`MyJobController` 使用所有权校验（非 `@PreAuthorize`），确保用户只能管理自己创建的任务。

### 依赖组件

| 组件 | 说明 |
|------|------|
| XXL-JOB Admin (`:18080`) | 分布式调度中心，Docker 容器部署 |
| `omni-common-job` 模块 | 自动装配、Admin Client、系统任务注册 |
| `sys_user_job_type` / `sys_user_job` / `sys_user_job_log` | 用户任务类型、任务实例、执行日志 |

### 新增任务类型指南

以 `DrinkWaterRemindHandler`（喝水提醒）为例：① 注册类型到 `sys_user_job_type` 表 → ② 实现 `UserJobHandler` 接口并添加 `@Component` → ③ 用户通过工作台创建任务 → ④ 验证 XXL-JOB 调度执行。详细步骤见 [`docs/scheduling.md` 第 4 章](docs/scheduling.md)。

### 前端集成

三个入口：系统任务管理（`SystemJob`）、任务类型管理（`UserJobType`）、工作台我的任务（`MyJob`）。支持 Cron 表达式编辑器、`DynamicFormRenderer` 动态参数表单、以及每 10 秒轮询活跃任务日志并通过 `ElNotification` 推送执行结果。

## 工作流引擎

项目基于 **Flowable 7.x** 构建了可视化 BPMN 工作流引擎，支持模型设计、版本管理、多实例会签审批等能力。深度技术细节见 [`docs/workflow.md`](docs/workflow.md)。

### 架构概览

- **omni-workflow**：独立微服务（端口 8103），集成 Flowable BPMN 引擎，提供模型管理、流程定义、实例监控、审批处理、统计看板等 7 个控制器
- **omni-common-workflow**：共享 Starter，提供 `FlowableAutoConfiguration`、`ApprovalService` SPI、`UserGroupLookup`、`TenantInfoFilter` 等基础设施

### 核心能力

- **可视化模型设计**：前端 BPMN 设计器支持拖拽建模、XML 编辑、校验预览，`BpmnXmlBuilder` 将设计器 JSON 转换为 BPMN 2.0 XML
- **双版本管理**：业务版本（DRAFT → PUBLISHED → ARCHIVED）在 `wf_process_model_version` 表中管理，引擎版本由 Flowable deployment 机制管理
- **多实例会签**：支持 ALL（全员通过）和 ANY（任一通过）两种审批模式，通过 MI `completionCondition` 控制，任一驳回立即终止
- **动态候选人解析**：`omni:assignment` JSON 扩展元素 + `ScopedRoleAssignmentListener` 运行时解析，支持多种锚点类型（发起人主组织 / 上级组织 / 绝对组织等）
- **审批记录 + 流程进度图 + 抄送通知**：完整的流程追踪能力，`HistoricTaskInstance` 级精度判定审批结果

### 数据库表（omni_workflow 库）

| 表 | 说明 |
|----|------|
| `wf_process_model` | 流程模型主表，`model_key` 租户内唯一 |
| `wf_process_model_version` | 模型版本表，存储 BPMN XML + 部署信息 |
| `wf_process_instance_ext` | 流程实例扩展表，关联模型版本与 Flowable 实例 |
| `wf_todo_task` | 待办任务缓存表 |
| `wf_cc_record` | 抄送记录表 |

### 前端集成

7 个页面/组件覆盖完整工作流场景：模型管理（`ModelDesigner`）、版本历史（`VersionHistoryDialog`）、校验结果（`ValidateResultDialog`）、流程定义、流程实例、审批记录（`ApprovalRecordsDialog`）、流程进度（`ProcessProgressDialog`）、统计看板。

## MQ 消息可靠性

项目基于 **Transactional Outbox** 模式构建了可靠消息发送体系，保证业务操作与消息记录的原子性。深度技术细节见 [`docs/mq-reliability.md`](docs/mq-reliability.md)。

### 架构概览

- **omni-common-core**：定义 `ReliableMessageRelay` 接口（纯 POJO，零 Spring 依赖）
- **omni-common-mqlog**：实现 Transactional Outbox 模式，提供 `ReliableMessageTemplate`、`MqMessageRelayService`、`MqMessageRelayJob`、`MessageSender` 策略和自动配置
- **omni-common-operlog**：可选调用方，`OperLogProducer` 在 `ReliableMessageRelay` 存在时自动切换到 Outbox 模式
- **omni-base**：外部管理控制器 `MqMessageController`，提供前端运维管理 API

### 消息生命周期

`sys_mq_message` 表的状态机：PENDING(0) → SENT(1)（成功）/ FAILED(2)（失败待重试）→ DEAD_LETTER(3)（超过最大重试次数）/ SKIPPED(4)（人工忽略）。失败消息按 `2^retryCount × 10s` 指数退避等待，默认最多 3 次重试。

### 租户隔离

写入时 `ReliableMessageRelay.send()` 要求显式传入 `Long tenantId`，不使用 ThreadLocal 隐式解析。查询时所有控制器接口按 tenantId 过滤（外部用 `@RequestHeader`，内部用 `@RequestParam`）。中继任务作为后台基础设施进程，不区分租户扫描所有待投递消息。

### 前端集成

运维监控菜单下的消息记录页面支持：分页查询（按状态、Topic、服务名、时间范围筛选）、消息详情查看、死信手动重发、死信标记忽略。权限码 `base:mqmessage:list` / `base:mqmessage:resend` / `base:mqmessage:skip`。

### 新服务接入指南

新服务引入 `omni-common-mqlog` 依赖即可获得可靠消息发送能力：`sys_mq_message` 表通过 `schema.sql` 自动创建，`ReliableMessageTemplate`、中继任务、内部查询 API 均通过 `AutoConfiguration.imports` 自动注册。业务代码注入 `ReliableMessageRelay` 接口调用 `send(bindingName, payload, tenantId)` 即可。

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
| Maven 构建顺序错误 | `omni-common-core` 未先安装导致下游模块编译失败 | 使用 `./mvnw clean install` 从父 POM 构建，Maven reactor 自动按 `<modules>` 声明顺序解析 |
| Redis Starter 混用导致线程饥饿 | 阻塞式 `omni-common-redis` 引入 WebFlux 服务 | WebFlux 服务（如 Gateway）只能依赖 `omni-common-redis-reactive`，不可混用 |
| Spring Cloud Stream 消费者不接收消息（RocketMQ 消费者组 OFFLINE） | 多个 `Consumer<T>` Bean 存在时，`spring.cloud.function.definition` 缺失或放在了错误的命名空间（`spring.cloud.stream.function.definition`） | 将 `spring.cloud.function.definition: beanName1;beanName2` 放在 `spring.cloud.function` 下，**不是** `spring.cloud.stream.function` 下。示例：`spring.cloud.function.definition: operlogConsumer;userJobConsumer` |

## AI 原生工程实践

本项目支持 AI 辅助开发工作流：

- **`AGENTS.md`**：AI 执行手册，定义硬约束、执行规则和完成检查清单
- **`docs/` 目录**：系统真相文档，AI 在修改代码前先阅读这些文档以理解系统上下文
- **`.qoder/skills/`**：AI 行为扩展单元（如 `/grill-me` 方案压力测试 Skill）

核心理念：**前两层（Architecture + Patterns）定住，第三层（Code）才能放心交给 AI 高速生产。**

## 许可证

[Apache License 2.0](LICENSE)

---

## 支持项目

如果这个项目对你有帮助，欢迎 Star 支持！

**GitHub**: [https://github.com/wang-baohai/Omni-Stack](https://github.com/wang-baohai/Omni-Stack)
**Gitee**: [https://gitee.com/wang-baohai/Omni-Stack](https://gitee.com/wang-baohai/Omni-Stack)

期待你的 [PR](https://github.com/wang-baohai/Omni-Stack/pulls)！

---

**© Wang Baohai**
