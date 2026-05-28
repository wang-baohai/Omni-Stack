# Omni-Stack

> 基于 Spring Boot 4 + Vue 3 的微服务脚手架平台，采用 Harness 工业设计模式构建，为 AI 辅助开发提供行业最佳实践基础。

**[English](README.en.md)** | **[日本語](README.jp.md)**

---

## 特性

- **JDK 25** + Spring Boot 4.0.6 + Spring Cloud 2025.1.1 全栈最新技术
- **Spring Cloud Gateway 5.x** (WebFlux) 响应式网关，Nacos 服务发现与配置中心
- **Sentinel** 流控与熔断，**OpenFeign** 声明式服务调用
- **Vue 3.5** + TypeScript 5.9 + Vite 8 + Element Plus 2.14 现代前端
- **Pinia 3** 状态管理 + **Vue Router 5** 路由守卫
- **Harness 工业设计模式**：三层高度模型（Architecture → Patterns → Code），docs/ 目录承载系统真相
- **AI 原生工程**：AGENTS.md 执行手册 + Skills 行为扩展，支持 AI 辅助开发工作流
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
├── docs/                            # 系统真相文档（Architecture + Patterns + Contract）
│   ├── architecture.md                # 系统边界、模块地图、数据流、约束
│   ├── api-contract.md                # 响应格式、错误码、分页、命名规范
│   ├── backend-patterns.md            # 后端分层、校验、异常、日志、OOP 规约
│   ├── frontend-patterns.md           # 前端目录、API 层、状态管理、组件约定
│   └── core-flows.md                  # 登录/查询/提交流程端到端追踪
├── omni-backend/                    # Maven 多模块后端
│   ├── mvnw / mvnw.cmd                # Maven Wrapper (3.9.16)
│   ├── pom.xml                        # 父 POM（依赖管理）
│   ├── omni-common/                   # 公共库：统一响应、全局异常、Jackson 配置
│   ├── omni-gateway/                  # API 网关 (WebFlux, 端口 8090)
│   └── omni-business/                 # 业务服务 (端口 8081)
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
│   omni-frontend  │────>│   omni-gateway    │────>│  omni-business  │
│   Vue 3 SPA     │/api │  WebFlux :8090    │ lb  │   :8081         │
│   :3000         │────>│  StripPrefix=2    │────>│  Controller     │
└─────────────────┘     └──────────────────┘     │  Service (接口)  │
                            │                     │  ServiceImpl    │
                            │                     └─────────────────┘
                    ┌───────┴────────┐
                    │  Nacos :8848   │  服务发现 + 配置中心
                    │  Sentinel :8858│  流控 + 熔断
                    └────────────────┘
```

**请求流转**：

```
浏览器 :3000  --/api/**-->  Vite 代理  -->  Gateway :8090  --lb://-->  Business :8081
```

- 前端通过 Vite 开发服务器将 `/api/**` 请求代理到 Gateway
- Gateway 将 `/api/business/**` 路由到 `omni-business`（StripPrefix=2 去除前缀）
- Gateway 通过 Nacos 服务发现自动创建注册服务的路由

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

## 快速开始

### 第一步：启动中间件

```bash
# Nacos 服务发现与配置中心 (端口 8080, 8848, 9848)
# Nacos v3.x 需要配置认证参数才能启动
docker run -d --name nacos \
  -p 8080:8080 -p 8848:8848 -p 9848:9848 \
  -e MODE=standalone \
  -e NACOS_AUTH_TOKEN=U2VjcmV0S2V5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5 \
  -e NACOS_AUTH_IDENTITY_KEY=nacos \
  -e NACOS_AUTH_IDENTITY_VALUE=nacos \
  nacos/nacos-server:v3.1.1

# Sentinel 流控仪表盘 (端口 8858)
docker run -d --name sentinel -p 8858:8858 bladex/sentinel-dashboard:1.8.8
```

> 等待 Nacos 完全启动（约 30 秒）后再启动后端服务。访问 `http://127.0.0.1:8080/` 确认 Nacos 就绪（默认账号 nacos/nacos）。

### 第二步：构建并启动后端

```bash
# 设置 JAVA_HOME（Spring Boot 4 构建插件需要 JDK 17+）
export JAVA_HOME="C:/APP/JDK25/jdk-25.0.2"   # Windows
# export JAVA_HOME="/path/to/jdk-25"           # macOS / Linux
export PATH="$JAVA_HOME/bin:$PATH"

# 进入后端目录，构建所有模块
cd omni-backend
./mvnw clean install

# 启动 Gateway（端口 8090）
cd omni-gateway
./mvnw spring-boot:run

# 新开终端，启动 Business 服务（端口 8081）
cd omni-backend/omni-business
./mvnw spring-boot:run
```

**构建顺序说明**：`omni-common` 必须先安装，其他模块才能编译。如需单独构建某个模块，使用：
```bash
./mvnw clean install -pl omni-business -am
```

### 第三步：启动前端

```bash
cd omni-frontend

# 安装依赖
npm install

# 启动开发服务器（端口 3000，自动代理 /api 到 Gateway :8090）
npm run dev
```

### 第四步：验证服务

| 验证项 | 命令 / URL | 预期结果 |
|--------|----------|---------|
| 前端页面 | `http://localhost:3000` | 登录页面 |
| Gateway 路由 | `curl http://localhost:8090/actuator/gateway/routes` | 返回路由列表 JSON |
| Nacos 控制台 | `http://127.0.0.1:8080/` | Nacos 管理界面 |
| Sentinel 控制台 | `http://localhost:8858` | Sentinel Dashboard |

**启动顺序**：Nacos → Sentinel → 后端服务（Gateway, Business）→ 前端

## 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端开发服务器 | 3000 | Vite dev server，代理 /api 请求 |
| API 网关 | 8090 | Spring Cloud Gateway (WebFlux) |
| 业务服务 | 8081 | omni-business 微服务 |
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

> `omni-common` 通过 Spring Boot 自动配置机制（`AutoConfiguration.imports`）注册 Bean，下游模块无需手动 `@ComponentScan`。

### omni-gateway（API 网关）

基于 Spring Cloud Gateway Server (WebFlux) 的响应式网关：

- 路由转发：`/api/business/**` → `lb://omni-business`（StripPrefix=2）
- 服务发现：Nacos 自动路由注册的服务
- 认证过滤器：`AuthFilter`（当前为存根，预留 token 校验扩展点）
- CORS 配置：`CorsConfig` 处理跨域请求

### omni-business（业务服务）

业务微服务示例，展示标准的分层架构：

```
Controller  →  Service (接口)  →  ServiceImpl (实现)  →  Repository (未来)
  参数校验       业务定义           业务逻辑              数据访问
  结果封装       @Transactional    事务管理              SQL / ORM
```

- `UserController`：RESTful API 端点，返回 `R<T>`
- `UserService`（接口）+ `UserServiceImpl`（实现）：Service 层接口化
- `RemoteServiceFeignClient`：OpenFeign 远程调用示例

### omni-frontend（Vue 3 SPA）

| 层级 | 目录 | 职责 |
|------|------|------|
| API 层 | `src/api/` | 按领域拆分文件，统一使用 Axios 实例，类型安全 |
| Store 层 | `src/stores/` | Pinia Composition API 风格，一 Store 一领域 |
| 路由层 | `src/router/` | 懒加载路由 + 导航守卫（默认要求认证） |
| 视图层 | `src/views/` | 页面组件，SFC 顺序：script → template → style |
| 布局层 | `src/layout/` | 侧边栏 + 顶栏 + 内容区应用外壳 |
| 类型层 | `src/types/` | 共享类型定义（ApiResponse, PageResult 的唯一来源） |
| 样式层 | `src/styles/` | 全局重置 + 布局样式 |

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
| Layer 1: Architecture | 系统边界、模块职责、数据流、约束 | `docs/architecture.md` |
| Layer 2: Patterns | 后端/前端编码模式、API 契约、核心流程 | `docs/backend-patterns.md`、`docs/frontend-patterns.md`、`docs/api-contract.md`、`docs/core-flows.md` |
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
| `omni-business` 编译失败 | `omni-common` 未先安装 | 先运行 `./mvnw install -pl omni-common` |
| 前端类型不匹配 | `ApiResponse` 在多处定义 | 只从 `@/types/api` 导入，禁止重复定义 |
| Actuator gateway 端点 404 | 需显式启用 | 配置 `management.endpoint.gateway.enabled: true` |

## AI 原生工程实践

本项目支持 AI 辅助开发工作流：

- **`AGENTS.md`**：AI 执行手册，定义硬约束、执行规则和完成检查清单
- **`docs/` 目录**：系统真相文档，AI 在修改代码前先阅读这些文档以理解系统上下文
- **`.qoder/skills/`**：AI 行为扩展单元（如 `/grill-me` 方案压力测试 Skill）

核心理念：**前两层（Architecture + Patterns）定住，第三层（Code）才能放心交给 AI 高速生产。**

## 许可证

[Apache License 2.0](LICENSE)
