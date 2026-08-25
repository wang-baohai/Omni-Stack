# Omni-Stack

> 基于 Spring Boot 4 + Vue 3 的微服务脚手架平台，采用 Harness 工业设计模式构建，为 AI 辅助开发提供行业最佳实践基础。
>
> **一条命令启动全家桶：中间件 + 数据迁移器 + 8 个微服务 + 前端，共 16 个 Docker 容器。**

**[English](README.en.md)** | **[日本語](README.jp.md)** | **[한국어](README.kr.md)**

**GitHub**: https://github.com/wang-baohai/Omni-Stack | **Gitee**: https://gitee.com/wang-baohai/Omni-Stack

**联系邮箱**: wangbaohai1993@gmail.com

---

## 特性亮点

- **JDK 25** + Spring Boot 4.0.6 + Spring Cloud 2025.1.1 + Spring Cloud Alibaba 2025.1.0.0 全栈最新技术
- **按需开发与一键部署**：Omni CLI 可启动 core/workflow/crm/supply-chain/full 最小组合，`start.bat` / `./start.sh` 默认启动完整 16 容器，详见 [Docker 部署指南](docs/docker-deployment.md)
- **CRM 销售前闭环**：独立 `omni-crm` 服务，覆盖线索、客户、联系人、商机、跟进、转换与概览，复用租户、RBAC、数据范围、XSS、审计和 Outbox 能力
- **SRM → Procurement → Asset 业务链**：供应商准入与报价、请购/RFQ/订单/收货、资产建卡/分配/调拨/处置按独立微服务闭环协作
- **业务化请购审批规则**：按品类与金额配置规则，支持真实匹配试算、默认兜底、覆盖断档/冲突检测、停用影响分析和安全审批图预览
- **多提供商社交登录**：GitHub + Google + Gitee OAuth2 一键登录（策略模式可扩展），首次登录自动注册
- **三层 XSS 纵深防御**：Jackson 反序列化器 + Servlet Filter + Gateway 安全响应头，按租户配置，前端管理界面完整可用
- **Common 生态**：10 个公共模块，新增 Servlet 业务组合 Starter，安全能力按配置失败关闭
- **双轨制定时任务**：XXL-JOB 3.3.1 系统任务 + 用户任务双模式，前端 Cron 编辑器 + 执行日志实时推送，详见 [docs/scheduling.md](docs/scheduling.md)
- **Transactional Outbox 可靠消息**：本地发件箱 + XXL-JOB 中继 + 指数退避重试 + 死信管理，详见 [docs/mq-reliability.md](docs/mq-reliability.md)
- **可选全栈可观测性**：OpenTelemetry + Prometheus + Pushgateway + Grafana + Tempo + Loki + Alloy，默认关闭、一条 CLI 参数开启，详见 [docs/observability.md](docs/observability.md)
- **可视化 BPMN 工作流**：Flowable 7.x 引擎，前端拖拽建模 + 双版本管理 + 多实例会签 + 动态候选人解析，详见 [docs/workflow.md](docs/workflow.md)
- **完整 RBAC 权限体系**：功能权限（动态菜单 + v-permission + @PreAuthorize）+ 数据权限（DataPermissionInterceptor 六级过滤），详见 [docs/architecture.md](docs/architecture.md)
- **AI 原生工程**：AGENTS.md 执行手册 + docs/ 系统真相 + Skills 行为扩展，前两层定住，第三层交给 AI 高速生产

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| JDK | OpenJDK | 25 |
| 后端框架 | Spring Boot | 4.0.6 |
| 微服务框架 | Spring Cloud + Spring Cloud Alibaba | 2025.1.1 / 2025.1.0.0 |
| API 网关 | Spring Cloud Gateway Server (WebFlux) | 5.0.1 |
| 注册/配置 | Nacos Server | v3.1.1 |
| 流控/熔断 | Spring Cloud Alibaba Sentinel 客户端 | 2025.1.0.0（Dashboard 可选，Compose 未内置） |
| 消息队列 | Apache RocketMQ | 5.3.2 |
| 任务调度 | XXL-JOB Admin | 3.3.1 |
| 工作流引擎 | Flowable BPMN | 7.x |
| 前端框架 | Vue 3 + TypeScript | 3.5.35 / 5.9.3 |
| 构建工具 | Vite 8 (Rolldown) | 8.2.1 |
| UI 框架 | Element Plus | 2.14.0 |
| 状态管理 | Pinia | 3.0.4 |
| Node.js | Node.js LTS | >= 22.12.0 |

## 架构概览

```
                                 ┌─────────────────┐
                                 │    omni-auth     │
                                 │   Spring :8100   │
                                 │  Security+OAuth2 │
                                 └─────────────────┘
                                        ▲
┌─────────────────┐     ┌──────────────────┐
│   omni-frontend  │────>│   omni-gateway    │lb://
│   Vue 3 SPA     │/api │  WebFlux :8102    │────>┌─────────────────┐
│   Nginx :3000   │────>│ 显式 /api 路由    │     │    omni-base     │
└─────────────────┘     └──────────────────┘     │   Spring :8101   │
                            │                    └─────────────────┘
                            │                    ┌─────────────────┐
                            │                    │  omni-workflow   │
                            │                    │  Flowable :8103  │
                            │                    └─────────────────┘
                            │                    ┌─────────────────┐
                            │                    │    omni-crm      │
                            │                    │   Sales :8104   │
                            │                    └─────────────────┘
                            │                    ┌─────────────────┐
                            │                    │    omni-srm      │
                            │                    │   SRM :8105     │
                            │                    └─────────────────┘
                            │                    ┌─────────────────┐
                            │                    │omni-procurement │
                            │                    │ Procurement:8106│
                            │                    └─────────────────┘
                            │                    ┌─────────────────┐
                            │                    │   omni-asset    │
                            │                    │   Asset :8107   │
                            │                    └─────────────────┘
                    ┌───────┴────────┐
                    │ MySQL :13306*  │  持久化存储（*宿主机；容器内 3306）
                    │  Redis :6379   │  缓存 + 验证码
                    │  Nacos :8848   │  服务发现 + 配置中心
                    │  RocketMQ      │  消息队列（异步投递）
                    │  XXL-JOB       │  分布式任务调度
                    └────────────────┘
```

**请求流转**：浏览器 `:3000` → Nginx 反代 → Gateway `:8102` → `lb://` → 后端服务

## 项目结构

```
Omni-Stack/
├── AGENTS.md                           # AI 执行手册（硬约束 + 构建命令 + 检查清单）
├── start.bat / start.sh                # Docker 全家桶一键启动脚本
├── stop.bat / stop.sh                  # 一键停止脚本
├── compose.yaml                        # Compose 统一入口（include 分层文件）
├── compose.infra.yaml                  # 数据库、注册中心、MQ、任务调度
├── compose.apps.yaml                   # 后端微服务与前端
├── compose.observability.yaml          # 可选指标、Trace、日志、Dashboard 与告警栈
├── observability/                      # 版本化观测配置、规则和 7 个 Dashboard
├── docker/
│   ├── backend/Dockerfile              # 后端多阶段构建（Maven 编译 + JRE 运行）
│   ├── frontend/Dockerfile             # 前端多阶段构建（npm 编译 + Nginx）
│   ├── frontend/nginx.conf             # Nginx 反代配置
│   └── rocketmq/broker-docker.conf     # RocketMQ Broker 配置
├── docs/                               # 系统真相文档（深度技术文档，支持多语言）
│   ├── architecture.md                 #   系统边界、模块地图、数据流、RBAC 权限体系
│   ├── api-contract.md                 #   响应格式、错误码、分页、命名规范
│   ├── backend-patterns.md             #   后端分层、校验、异常、日志、安全权限
│   ├── frontend-patterns.md            #   前端目录、API 层、状态管理、权限控制
│   ├── core-flows.md                   #   登录/OAuth2/RBAC 权限流程端到端追踪
│   ├── scheduling.md                   #   定时任务系统深度技术文档
│   ├── workflow.md                     #   工作流引擎深度技术文档
│   ├── mq-reliability.md              #   可靠消息发送深度技术文档
│   ├── crm.md                          #   CRM 销售管道系统真相（Harness 文档）
│   ├── srm.md                          #   SRM 供应商关系管理系统真相（Harness 文档）
│   ├── design/srm-design.md            #   SRM MVP 设计与实现基线
│   ├── design/procurement-design.md    #   Procurement MVP 设计与实现基线
│   ├── design/asset-design.md          #   Asset MVP 设计与实现基线
│   └── docker-deployment.md            #   Docker 全家桶部署深度指南
├── scripts/sql/                        # 数据库初始化脚本
│   ├── init-all.sql                    #   权威 DDL + 种子数据
│   ├── migrate-crm-mvp.sql             #   既有环境 CRM MVP 幂等迁移
│   ├── migrate-srm-mvp.sql             #   既有环境 SRM MVP 幂等迁移
│   ├── sp_init_tenant.sql              #   新租户初始化存储过程
│   ├── init-nacos.sql                  #   Nacos MySQL 持久化
│   └── init-xxl-job.sql               #   XXL-JOB 数据库
├── omni-backend/                       # Maven 多模块后端
│   ├── omni-common-core/               #   纯 POJO：R<T>, PageResult, XSS SPI
│   ├── omni-common/                    #   Web 自动配置：Jackson, CORS, XSS Filter
│   ├── omni-common-mybatis/            #   MyBatis-Plus Starter
│   ├── omni-common-redis/              #   阻塞式 Redis Starter
│   ├── omni-common-redis-reactive/     #   响应式 Redis Starter（Gateway 专用）
│   ├── omni-common-operlog/            #   操作日志 Starter
│   ├── omni-common-job/                #   定时任务 Starter
│   ├── omni-common-mqlog/              #   MQ 消息可靠性 Starter
│   ├── omni-common-workflow/           #   工作流 Starter
│   ├── omni-common-service/            #   Servlet 业务服务组合 Starter
│   ├── omni-auth/                      #   认证服务 (8100)
│   ├── omni-base/                      #   基础数据服务 (8101)
│   ├── omni-workflow/                  #   工作流引擎服务 (8103)
│   ├── omni-crm/                       #   CRM 销售前闭环服务 (8104)
│   ├── omni-srm/                       #   SRM 供应商关系管理服务 (8105)
│   ├── omni-procurement/               #   采购执行服务 (8106)
│   ├── omni-asset/                     #   资产生命周期服务 (8107)
│   └── omni-gateway/                   #   API 网关 (8102)
└── omni-frontend/                      # Vue 3 SPA (3000)
```

## Docker 一键部署（推荐）

可按预设或模块启动最小依赖闭包；完整模式包含中间件、迁移器、8 个后端微服务与前端。

### 前置条件

| 软件 | 版本要求 | 说明 |
|------|---------|------|
| Docker Desktop | 任意稳定版 | Windows 需 WSL2 后端 |
| Node.js | >= 22.12.0 | 运行 Omni CLI 与一键脚本 |
| Git | 任意 | 克隆项目 |

首次启动前复制 `.env.example` 为 `.env`，把其中每个 `replace-with-*` 占位值替换为独立随机密钥。
`OMNI_INTERNAL_API_TOKEN` 必须在所有 Servlet 服务中保持一致；Compose 对 MySQL、Redis、Nacos、
XXL-JOB、OAuth state、服务间令牌等变量均采用必填校验，缺少配置时直接拒绝启动。

> 容器启动无需本机 JDK 或 Maven；Omni CLI 需要 Node.js，应用编译仍在 Docker 容器内完成。

数据库结构与种子统一由一次性 `omni-db-migrator` 管理。新环境自动执行 fresh migration；已有数据卷必须先备份，
再按迁移器的指纹预检与 `adopt-current` 流程接管，禁止手工跳过版本记录。详见
[Docker 部署指南](docs/docker-deployment.md)。

### 启动

| 平台 | 命令 |
|------|------|
| Windows | `start.bat`（无需管理员权限） |
| Linux / macOS | `./start.sh` |

脚本会检查 Docker 与 Node.js，通过 Omni CLI 构建并启动指定预设；不修改 Windows 端口保留策略。

```bash
# 默认启动 full
./start.sh

# 启动 CRM 最小组合（不包含 SRM / Procurement / Asset）
./start.sh crm

# CLI 等价命令；也可按模块只启动公开前端
npm --prefix tools/omni-cli run dev -- dev up --preset crm --build
npm --prefix tools/omni-cli run dev -- dev up --module frontend
# 完整业务栈 + 本地可观测性（本地 100% Trace 采样）
npm --prefix tools/omni-cli run dev -- dev up --preset full --observability

# 查看服务状态
npm --prefix tools/omni-cli run dev -- dev status

# 停止全部服务
./stop.sh

# 删除命名卷必须双重确认
npm --prefix tools/omni-cli run dev -- dev down --volumes --confirm-delete-volumes
```

### 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| **前端** | **http://localhost:3000** | **访问入口，Nginx 反代到 Gateway** |
| 认证服务 | http://127.0.0.1:8100 | Spring Security + OAuth2（仅回环调试） |
| 基础数据服务 | http://127.0.0.1:8101 | 字典/组织/用户/日志/任务（仅回环调试） |
| API 网关 | http://localhost:8102 | Spring Cloud Gateway (WebFlux) |
| 工作流引擎 | http://127.0.0.1:8103 | Flowable BPMN（仅回环调试） |
| CRM 服务 | http://127.0.0.1:8104 | 线索、客户、商机与跟进（仅回环调试） |
| SRM 服务 | http://127.0.0.1:8105 | 供应商、门户、评估与风险（仅回环调试） |
| Procurement 服务 | http://127.0.0.1:8106 | 请购、询价、订单与收货（仅回环调试） |
| Asset 服务 | http://127.0.0.1:8107 | 资产台账、调拨与处置（仅回环调试） |
| MySQL | 127.0.0.1:13306 | root + `.env` 中的 `MYSQL_ROOT_PASSWORD`（仅回环） |
| Redis | 127.0.0.1:6379 | `.env` 中的 `REDIS_PASSWORD`（仅回环） |
| Nacos 控制台 | http://127.0.0.1:8080 | 凭据由 `.env` 注入（仅回环） |
| XXL-JOB 调度中心 | http://127.0.0.1:18080 | 本地初始化账号；执行器令牌由 `.env` 注入（仅回环） |
| RocketMQ NameServer | localhost:19876 | 宿主机映射端口（容器内 9876） |

以上后端直连地址仅用于本地开发和诊断。生产环境只发布 Frontend 与 Gateway，所有下游微服务端口必须保留在私有网络内。

### 验证

```bash
# 1. 访问前端
open http://localhost:3000

# 2. 验证验证码接口
curl http://localhost:3000/api/auth/captcha

# 3. 检查所有容器状态
docker compose ps
```

本地演示种子包含 `admin` / `admin123`，只用于首次本地联调。首次登录后必须立即修改；生产环境不得
直接使用仓库种子数据。通过管理端新建租户时必须显式设置初始管理员密码，后端不再生成公共默认口令。

### 常见问题

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| 镜像拉取失败 | 国内网络问题 | 配置 Docker 镜像加速：`"registry-mirrors": ["https://docker.1ms.run"]` |
| 端口绑定失败 (Windows) | 目标组合所需端口被占用 | 运行 `omni dev doctor`，只调整 `.env` 中对应的 `OMNI_*_HOST_PORT`，无需管理员权限 |
| RocketMQ 端口 9876 冲突 | Windows Hyper-V 保留端口范围 | 宿主机映射已改为 19876，容器内仍为 9876 |
| 502 Bad Gateway | Nginx 反代端口配置错误 | 确认 nginx.conf 中 proxy_pass 使用容器内部端口 `8080`（非宿主机端口 `8102`） |
| Nacos 启动失败 | 健康检查端点变更 | Nacos v3.1.1 使用 `GET /nacos/`（非 `/nacos/actuator/health`） |
| 构建超时 | Maven 依赖下载慢 | 后端 Dockerfile 已内置阿里云 Maven 镜像加速 |

> 深度故障排查指南见 [docs/docker-deployment.md](docs/docker-deployment.md)

## 本地开发

适合需要调试和修改代码的场景，中间件用 Docker，后端和前端在本地运行。

### 前置条件

| 软件 | 版本 | 说明 |
|------|------|------|
| JDK | 25 | 必须设置 `JAVA_HOME` |
| Node.js | >= 22.12.0 | 含 npm |
| Docker Desktop | 任意 | 仅运行中间件 |

### 步骤

```bash
# 1. 启动中间件（仅中间件，不启动应用容器）
./start.sh mysql redis nacos rocketmq-namesrv rocketmq-broker xxl-job-admin

# 等待 Nacos 就绪（约 30 秒），访问 http://localhost:8080 确认

# 2. 构建并启动后端
export JAVA_HOME="/path/to/jdk-25"
cd omni-backend && ./mvnw clean install
cd omni-auth && ./mvnw spring-boot:run       # 端口 8100（新终端窗口继续）
cd omni-base && ./mvnw spring-boot:run        # 端口 8101
cd omni-gateway && ./mvnw spring-boot:run     # 端口 8102
cd omni-workflow && ./mvnw spring-boot:run    # 端口 8103
cd omni-crm && ./mvnw spring-boot:run         # 端口 8104
cd omni-srm && ./mvnw spring-boot:run         # 端口 8105
cd omni-procurement && ./mvnw spring-boot:run # 端口 8106
cd omni-asset && ./mvnw spring-boot:run       # 端口 8107

# 3. 启动前端
cd omni-frontend && npm install && npm run dev  # 端口 3000
```

> Maven Wrapper 已内置（3.9.16），无需全局安装 Maven。构建顺序由 Maven reactor 自动解析。

### 社交登录配置

支持 GitHub、Google、Gitee 三个 OAuth2 提供商。凭证配置在 `application-local.yml`（被 `.gitignore` 排除），详见 [docs/core-flows.md](docs/core-flows.md)。

## 功能概览

### 认证与登录

| 登录页 | 注册页 |
|--------|--------|
| ![登录页](docs/images/login.png) | ![注册页](docs/images/register.png) |

| 数据看板 | 社交登录 |
|----------|----------|
| ![数据看板](docs/images/dashboard.png) | ![社交登录](docs/images/social-login-buttons.png) |

| 授权同意 | 设备码登录 |
|----------|------------|
| ![授权同意](docs/images/social-consent.png) | ![设备码登录](docs/images/social-device-init.png) |

| 设备码验证 | |
|------------|--|
| ![设备码验证](docs/images/social-device-verify.png) | |

### 系统管理

| 用户管理 | 字典管理 |
|----------|----------|
| ![用户管理](docs/images/system-user.png) | ![字典管理](docs/images/system-dict.png) |

| XSS 防护配置 | |
|--------------|--|
| ![XSS防护配置](docs/images/system-xss.png) | |

### 定时任务

| 系统任务 | 我的任务 |
|----------|----------|
| ![系统任务](docs/images/job-system.png) | ![我的任务](docs/images/job-workspace.png) |

### 运维监控

| 操作日志 | MQ 消息记录 |
|----------|-------------|
| ![操作日志](docs/images/monitor-operlog.png) | ![MQ消息记录](docs/images/monitor-mqmessage.png) |

### 工作流

| BPMN 设计器 | 审批流程 |
|-------------|----------|
| ![BPMN设计器](docs/images/workflow-designer.png) | ![审批流程](docs/images/workflow-approval.png) |

### CRM 销售管理

CRM 模块覆盖完整的售前闭环：线索获取 → 跟进培育 → 客户建档 → 商机推进 → 赢单/输单。六层安全纵深（Gateway JWT → 租户校验 → 功能权限 → 数据范围 → SQL 拦截 → 行级授权）保障多租户数据安全，详见 [CRM 系统真相](docs/crm.md)。

| 销售概览 | 线索管理 |
|----------|----------|
| ![销售概览](docs/images/crm-overview.png) | ![线索管理](docs/images/crm-lead-list.png) |
| 统计卡片 + 销售漏斗 + 待跟进列表，一屏掌握全局销售数据 | 线索列表支持搜索、筛选、分配和批量操作，是销售流程的起点 |

| 线索转换 | 客户管理 |
|----------|----------|
| ![线索转换](docs/images/crm-lead-convert.png) | ![客户管理](docs/images/crm-customer-list.png) |
| 合格线索一键转换为客户 + 联系人 + 商机，行锁幂等保障并发安全 | 客户列表支持转移、状态变更和黑名单管理 |

| 客户 360 视图 | 联系人管理 |
|---------------|------------|
| ![客户360](docs/images/crm-customer-360.png) | ![联系人管理](docs/images/crm-contact-list.png) |
| 单客户全维度视图：联系人、商机、跟进活动一站式展示 | 联系人与客户关联，支持主要联系人标记 |

| 商机管理 | 商机看板 |
|----------|----------|
| ![商机管理](docs/images/crm-opportunity-list.png) | ![商机看板](docs/images/crm-opportunity-board.png) |
| 商机表格展示阶段、金额、概率和预计成交日 | Kanban 看板按阶段分列，直观展示销售管道进展 |

| 跟进活动 | |
|----------|--|
| ![跟进活动](docs/images/crm-activity-timeline.png) | |
| 活动列表记录每次跟进，支持计划/完成/取消状态流转 | |

### SRM 供应商管理

SRM 模块覆盖供应商全生命周期管理闭环：供应商注册/准入 → 审核 → 分级分类 → 绩效评估 → 风险管控 → 淘汰退出。五层安全信任链（Gateway JWT → 租户校验 → 功能权限 → 数据范围 → 行级授权）保障多租户数据安全，详见 [SRM 系统真相](docs/srm.md)。

- **供应商主数据**：供应商信息库、联系人、资质、银行账户，支持准入审核、冻结恢复、黑名单和淘汰退出
- **供应商门户**：供应商自助注册入驻、企业信息维护、绩效查看，基于 Outbox/Saga 的跨服务角色分配
- **绩效评估**：加权评分卡（质量/交期/价格/服务），系统自动计算百分制总分并映射供应商等级
- **风险看板**：六维风险指标（财务/合规/供应/合作/质量/资质），红黄绿灯可视化，资质到期预警

| 供应商概览 | 供应商列表 |
|-----------|------------|
| ![供应商概览](docs/images/srm-overview.png) | ![供应商列表](docs/images/srm-supplier-list.png) |
| 统计卡片 + 供应商分布 + 等级概况，关键指标一屏纵览 | 供应商列表支持搜索、筛选、分配和批量操作，准入审核的起点 |

| 绩效评估 | 风险看板 |
|----------|----------|
| ![绩效评估](docs/images/srm-evaluation.png) | ![风险看板](docs/images/srm-risk.png) |
| 加权评分卡（质量/交期/价格/服务），自动计算百分制总分并映射等级 | 六维风险指标红黄绿灯可视化，资质到期预警，综合风险等级一目了然 |

| 邀请管理 | 供应商门户 |
|----------|------------|
| ![邀请管理](docs/images/srm-invite.png) | ![供应商门户](docs/images/srm-portal.png) |
| 邀请码发放与撤回，控制供应商准入入口 | 供应商自助入驻、企业信息维护、绩效查看 |

## 模块概览

### 后端微服务

| 模块 | 端口 | 职责 | 深度文档 |
|------|------|------|---------|
| omni-auth | 8100 | 认证授权：登录、JWT、OAuth2、RBAC、XSS 配置管理 | [core-flows.md](docs/core-flows.md) |
| omni-base | 8101 | 基础数据：字典、组织、用户、日志、定时任务、MQ 消息管理 | [scheduling.md](docs/scheduling.md) |
| omni-workflow | 8103 | 工作流引擎：BPMN 模型管理、审批、流程实例 | [workflow.md](docs/workflow.md) |
| omni-crm | 8104 | CRM：线索、客户、联系人、商机、跟进与销售概览 | [crm.md](docs/crm.md) |
| omni-srm | 8105 | SRM：供应商主档、准入、绩效、风险、邀请与供应商门户 | [srm.md](docs/srm.md) |
| omni-procurement | 8106 | Procurement：物料、请购审批、RFQ/报价、订单与收货 | [procurement-design.md](docs/design/procurement-design.md) |
| omni-asset | 8107 | Asset：采购建卡、台账、分配/退还、调拨、处置与概览 | [asset-design.md](docs/design/asset-design.md) |
| omni-gateway | 8102 | API 网关：路由转发、JWT 验证、CORS、安全响应头 | [architecture.md](docs/architecture.md) |

### Common 生态（10 模块）

新微服务引入依赖即获能力，`AutoConfiguration.imports` 零配置自动装配：

| 模块 | 能力 | 适用服务 |
|------|------|---------|
| `omni-common-core` | 纯 POJO：`R<T>`、`PageResult`、`BaseEntity`、XSS SPI、UserJobHandler SPI | 所有服务 |
| `omni-common` | Web 自动配置：Jackson、CORS、全局异常、XSS Filter | Servlet 服务 |
| `omni-common-mybatis` | MyBatis-Plus + MySQL 驱动 + 分页插件 | Servlet 服务 |
| `omni-common-redis` | 阻塞式 Redis + RedisTemplate 序列化 + RedisUtils | Servlet 服务 |
| `omni-common-redis-reactive` | 响应式 Redis（WebFlux 服务专用，**不可与阻塞式混用**） | Gateway |
| `omni-common-operlog` | 操作日志：@OperLog AOP + RocketMQ 异步 + 实体 diff + 热冷表归档 | 业务服务 |
| `omni-common-job` | 定时任务：XXL-JOB 自动装配 + @SystemJobMeta 双注解驱动 | 业务服务 |
| `omni-common-mqlog` | 可靠消息：Transactional Outbox + 中继投递 + 死信管理 | Servlet 服务 |
| `omni-common-workflow` | 工作流：Flowable 自动配置 + ApprovalService SPI | 工作流服务 |
| `omni-common-service` | Servlet 业务服务组合：预认证、租户、内部 API、DataScope、固定 SQL 拦截顺序、XSS 回退 | Servlet 业务服务 |

> 详细设计见 [docs/backend-patterns.md](docs/backend-patterns.md) 和 [docs/architecture.md](docs/architecture.md)

### 前端

Vue 3 + TypeScript + Vite 8 + Element Plus + Pinia 3，详细开发规范见 [docs/frontend-patterns.md](docs/frontend-patterns.md)。

| 层级 | 目录 | 职责 |
|------|------|------|
| API 层 | `src/api/` | 按领域拆分，统一 Axios 实例，类型安全 |
| Store 层 | `src/stores/` | Pinia Composition API，一 Store 一领域 |
| 路由层 | `src/router/` | 懒加载 + 导航守卫 |
| 视图层 | `src/views/` | SFC 顺序：script → template → style |
| 类型层 | `src/types/` | 共享类型唯一来源（禁止重复定义） |

## 开发指南（新成员必读）

项目采用 **Harness 工业设计模式**，系统知识分三层：**Architecture → Patterns → Code**。修改代码前，先读对应的 `docs/` 文档。

| 规则 | 说明 |
|------|------|
| 依赖注入 | `@RequiredArgsConstructor` + `final` 字段，禁止 `@Autowired` |
| 返回值 | 所有 Controller 返回 `R<T>`，分页用 `R<PageResult<T>>` |
| 异常 | 业务异常抛 `BusinessException`，`GlobalExceptionHandler` 统一处理 |
| 日志 | `@Slf4j` + 参数化占位符，禁止 `System.out.println` |
| 权限 | 写操作必须声明 `@PreAuthorize`，格式 `resource:action` |
| 前端类型 | `ApiResponse`/`PageResult` 只从 `src/types/api.ts` 导入 |
| 前端组件 | SFC 顺序：`<script setup>` → `<template>` → `<style scoped>` |

```bash
# 提交前验证
cd omni-backend && ./mvnw clean install        # 后端编译
cd omni-frontend && npm run build && npm run lint  # 前端构建 + Lint
```

> 完整规约见 [docs/backend-patterns.md](docs/backend-patterns.md) 和 [docs/frontend-patterns.md](docs/frontend-patterns.md)，API 契约见 [docs/api-contract.md](docs/api-contract.md)

## 常见陷阱

| 陷阱 | 说明 | 解决方案 |
|------|------|---------|
| Gateway 路由不生效 | 5.x 配置前缀已变更 | 使用 `spring.cloud.gateway.server.webflux` |
| Maven class version 错误 | JAVA_HOME 未指向 JDK 25 | 设置 `JAVA_HOME` 到 JDK 25 目录 |
| Redis Starter 混用 | 阻塞式引入 WebFlux 服务 | Gateway 只能用 `omni-common-redis-reactive` |
| Docker 502 错误 | Nginx proxy_pass 端口错误 | 容器间通信用内部端口 `8080`，非宿主机映射端口 |
| Docker 端口冲突 | Hyper-V/WSL2 保留端口 | `start.bat` 自动处理，需管理员权限运行 |
| Nacos 健康检查失败 | v3.1.1 端点变更 | 使用 `GET /nacos/`，非 `/nacos/actuator/health` |
| 前端类型不匹配 | `ApiResponse` 多处定义 | 只从 `@/types/api` 导入 |
| Stream 消费者 OFFLINE | function.definition 命名空间错误 | 放在 `spring.cloud.function` 下，非 `spring.cloud.stream.function` |

## AI 原生工程

- **`AGENTS.md`**：AI 执行手册，硬约束 + 执行规则 + 完成检查清单
- **`docs/`**：系统真相文档，AI 修改代码前先阅读以理解系统上下文
- **`.qoder/skills/`**：AI 行为扩展单元（如 `/grill-me` 方案压力测试）

> **前两层（Architecture + Patterns）定住，第三层（Code）才能放心交给 AI 高速生产。**

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

<!-- omni:preset-table:start -->
## 项目裁剪预设

| 预设 | 显式模块 | 依赖闭包 |
|---|---|---|
| core | base, gateway, mysql, redis, nacos | platform, auth, base, nacos, gateway, mysql, redis |
| crm | crm, gateway, mysql, redis, nacos | platform, auth, base, crm, nacos, gateway, mysql, redis |
| full | crm, asset, gateway, mysql, redis, nacos, rocketmq, xxl-job | platform, auth, base, workflow, crm, srm, procurement, asset, nacos, xxl-job, gateway, mysql, redis, rocketmq |
| supply-chain | asset, gateway, mysql, redis, nacos, rocketmq, xxl-job | platform, auth, base, workflow, srm, procurement, asset, nacos, xxl-job, gateway, mysql, redis, rocketmq |
| workflow | workflow, gateway, mysql, redis, nacos | platform, auth, base, workflow, nacos, gateway, mysql, redis |

[选择指南](docs/preset-quick-selection.md) · [预设依赖矩阵](docs/preset-dependency-matrix.md)
<!-- omni:preset-table:end -->
