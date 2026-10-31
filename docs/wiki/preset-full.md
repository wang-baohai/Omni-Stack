# full 预设：当前仓库的全部核心与业务能力

full 预设是 Omni-Stack 当前能力的全集：认证与基础平台、工作流、CRM、SRM、采购、资产、消息与调度同时启用。它适合端到端验收、跨模块演示和需要一次看到全部入口的团队；不适合资源受限、只验证单一业务域，或把“容器全部启动”误当成“全部业务已闭环”的场景。本页组件来自当前 `scaffold/presets/full.yaml`、`scaffold/catalog/modules.yaml` 与 `preset explain full` 的实际解析；运行结果按已验证/未验证分开标注。

本页运行结果是覆盖裁剪缺陷修复前的分阶段历史记录，不代表当前版本。早期失败或未完成不抹去，后续成功也不倒签早期记录。旧 G7 绿灯不能证明当前完整覆盖；当前发布必须用保留全部适用要求的 G6/G7 和必要的 G11 重新验收。

## 它适合谁

- 需要一次验证认证、租户、RBAC、工作流、CRM、供应链与资产全部入口的团队。
- 需要以 full 初始化共享数据库，再用其他预设做原位子集验证的维护者。
- 需要检查跨模块菜单、权限、数据范围与消息/调度协作的验收人员。

它不适合：只想先跑通登录与基础管理（用 core）、只做销售（CRM）、只做供应商/采购/资产（supply-chain）或只做审批（workflow）的场景。full 的资源占用、启动时间与故障面都是最大的。

## 从 8 个显式模块到 14 个闭包模块

`scaffold/presets/full.yaml` 的显式模块：

```text
crm, asset, gateway, mysql, redis, nacos, rocketmq, xxl-job
```

`preset explain full` 当前解析出的闭包：

```text
platform, auth, base, workflow, crm, srm, procurement, asset, nacos, xxl-job, gateway, mysql, redis, rocketmq
```

多出的 `srm`、`procurement`、`workflow` 来自 `asset` 的依赖链（`asset → procurement, srm → workflow → base`）；`auth` 与 `platform` 来自 `base` 的依赖。也就是说，写 `asset` 就会把整条供应链与审批运行时带入 full。`crm` 是显式加入的独立业务域。

## 实际服务与端口

14 个模块映射到 16 个 Compose 服务：

| 服务 | 来源模块 | 作用 |
|---|---|---|
| `mysql` | mysql | 平台与各业务数据 |
| `redis` | redis | 缓存与令牌相关数据 |
| `nacos` | nacos | 服务发现与配置中心 |
| `rocketmq-namesrv` | rocketmq | 消息路由 |
| `rocketmq-broker` | rocketmq | 消息存储与投递 |
| `xxl-job-admin` | xxl-job | 调度中心 |
| `omni-db-migrator` | platform | 一次性结构迁移与种子初始化 |
| `omni-auth` | auth | 登录、租户、RBAC、组织、字典、审计 |
| `omni-base` | base | 基础管理、日志、工作台与任务接口 |
| `omni-workflow` | workflow | BPMN 模型、待办、审批与流程实例 |
| `omni-crm` | crm | 线索、客户、联系人、商机、跟进与销售管道 |
| `omni-srm` | srm | 供应商、准入、评估、风险与门户 |
| `omni-procurement` | procurement | 物料、申请、审批路由、RFQ、订单与收货 |
| `omni-asset` | asset | 资产台账、建卡、领用/退回、调拨与处置 |
| `omni-gateway` | gateway | 统一入口与路由 |
| `omni-frontend` | platform | Vue 前端 |

catalog 共声明 35 条端口记录、34 个唯一端口（`8080` 同时由 nacos 与 xxl-job 声明）：

| 模块 | 声明端口 |
|---|---|
| platform | 80, 3000 |
| auth | 8100, 19900, 9900 |
| base | 8101, 19901, 9901 |
| workflow | 8103, 19903, 9903 |
| crm | 8104, 19904, 9904 |
| srm | 8105, 19905, 9905 |
| procurement | 8106, 19906, 9906 |
| asset | 8107, 19907, 9907 |
| nacos | 8080, 8848, 9848 |
| xxl-job | 8080 |
| gateway | 8102, 19902 |
| mysql | 3306 |
| redis | 6379 |
| rocketmq | 9876, 10909, 10911, 10912 |

前端入口是 3000，业务服务为 CRM 8104、SRM 8105、采购 8106、资产 8107、工作流 8103。`xxl-job` 与 nacos 都声明 8080；本仓库示例环境通过 `OMNI_XXL_JOB_HOST_PORT=18080` 映射调度中心，通过 `OMNI_ROCKETMQ_NAMESRV_HOST_PORT=19876` 映射 NameServer（见 `.env.example` 的显式变量）。这些不是随机偏移。

## 收益与代价

收益是一次获得全部业务域：同一套租户、RBAC、数据范围、审计、工作流、消息与调度覆盖 CRM 销售链和供应链资产链；适合验收跨模块菜单、权限与数据隔离，也适合作为其他预设的共享数据库初始化来源。

代价是最大：

- 按 catalog `resourceHints` 求和，最低内存提示 7296 MB、推荐 11776 MB。Quick Start 对 full 的初始建议是 12–16 GiB，这是准备预算，不是实测上限；构建峰值、MySQL/RocketMQ 缓存与 JVM 堆会叠加。
- 16 个服务、34 个唯一端口、5 个业务应用、1 个消息中间件（NameServer + Broker）和 1 个调度中心；启动、健康检查、日志与故障定位面最大。
- 业务前置最多：CRM 的销售数据、SRM 的供应商准入与门户、采购的审批路由/订单/收货、资产的建卡与调拨/处置规则、工作流的模型发布与候选人，以及消息中继和调度执行。
- “全部容器 healthy”只说明服务在响应，不证明跨模块业务链路已闭环。

## 前提与配置

- **Docker 与资源**：按 12–16 GiB 准备内存，预留足够磁盘；确认 3000、8100–8107、3306、6379、8080、8848、9848、9876 等端口可用（调度与 NameServer 的宿主映射按 `.env` 显式变量核对）。
- **私有 `.env`**：数据库、Redis、Nacos 认证、网关断言、`XXL_JOB_ACCESS_TOKEN`、`XXL_JOB_ADMIN_PASSWORD` 等。不要提交真实值。
- **Nacos 配置**：`omni-common.yml`、`omni-auth.yml`、`omni-base.yml`、`omni-workflow.yml`、`omni-crm.yml`、`omni-srm.yml`、`omni-procurement.yml`、`omni-asset.yml`、`omni-gateway.yml`；认证开启时所有客户端使用同一套账号/密码。
- **迁移与种子**：`omni-db-migrator` 一次性执行。共享库已初始化时不要重复导入、重放迁移或清库。
- **身份**：管理员、`CRM_ADMIN`/`SALES_MANAGER`/`SALES_REP`/`CRM_VIEWER`、`SRM_ADMIN`/`PROCUREMENT_MANAGER`/`PROCUREMENT_STAFF`、`SUPPLIER`，以及资产与工作流相关角色。供应商门户还要求门户关联与邀请。
- **审批与消息**：审批运行时在 `omni-workflow`；Outbox 中继依赖 `xxl-job`，业务事件依赖 RocketMQ。调度未就绪时，消息写入不等于已投递。
- **业务数据**：CRM 销售链、供应商准入、采购申请/审批/订单/收货、资产建卡与调拨/处置。容器启动不等于这些前置已完成。

## 路径一：从源码启动 full

### 干净数据库首次安装

```bash
docker compose --parallel 1 --profile full up -d --build
docker compose ps
```

预期：migrator 执行后退出，mysql/redis/nacos/rocketmq/xxl-job 与全部 9 个应用服务启动，应用 healthy，前端 3000 可访问。该路径会执行迁移与种子，只适用于空库或明确允许初始化的环境。**本页未实测该路径**：验证环境使用 full 已初始化的共享库，空库首次安装保持未验证标注。

### 已有共享环境的原位全量替换

```bash
docker compose --parallel 1 -p omni-stack build omni-auth omni-base omni-workflow omni-crm omni-srm omni-procurement omni-asset omni-gateway omni-frontend
docker compose -p omni-stack up -d --no-build --no-deps --force-recreate omni-auth omni-base omni-workflow omni-crm omni-srm omni-procurement omni-asset omni-gateway omni-frontend
docker compose -p omni-stack ps
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:3000/
```

- `docker compose --parallel 1 ... build`：为 9 个应用构建镜像；不启动 migrator，不重建基础设施。
- `up -d --no-build --no-deps --force-recreate`：原位替换 9 个应用；不启动依赖、不执行迁移。
- `ps`：核对实际镜像与健康；healthy 只代表服务响应。
- `curl`：确认前端 200；网关业务路由无令牌应为 401，不是 503。

full 不包含“闭包外应用”，但仍要按实际登记的服务集合核对；不要为了“干净”而停掉基础设施或清卷。

## 路径二：生成独立的 full 工程

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create full --output generated/learning-full
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset validate full --output generated/learning-full
```

生成物有自己的 `scaffold.lock`、Compose、文档与 AI 入口。进入生成物后使用它自己的构建命令；不要在原仓库目录启动生成物的 Compose 项目，也不要运行生成物的 migrator 覆盖共享数据库。

历史早期阶段：源码路径已完成 9 个应用镜像构建与原位替换、健康/前端 200/真实验证码登录/全域菜单核对与停止恢复；生成路径已完成固定位生成（G6 14 模块/16 服务）、G7 生成物自构建、交付角色（仅挂生成物与缓存子路径、无源仓、无 docker socket）自足检查、9 个生成镜像原位替换与真实登录/菜单/工作台核验、替换后恢复仓库镜像；交付门 `verify-preset-delivery full` 为 `automated-delivery-pass`（modules=14），但 `release_ready=false`：生成物文档译文待指定审校。干净空库首次安装仍未实测。

## 首次使用

1. 打开 `http://localhost:3000`，选择开发租户，用 `admin` 与**本次显示的验证码**登录。不要复用旧验证码或使用认证后门。
2. Wiki 首页保留原页签，在独立页签打开“我的工作台”；应用目录与搜索可查全部可访问菜单。由目录以新页签打开 CRM、SRM、采购、资产、工作流及系统/基础模块；业务模块只展示和搜索当前模块菜单，没有全局“应用”按钮。
3. 按域验证，不要一次平铺所有页面：
   - CRM：线索 → 跟进 → 转换 → 商机阶段 → 赢单/输单。
   - 供应链：供应商准入/物料 → 申请审批 → 订单 → 收货 → 资产建卡。
   - 审批：模型发布 → 发起 → 待办处理 → 实例历史。
   - 门户：供应商门户身份以**正式邀请 → 自助注册 → 入驻 Saga** 取得（本轮已实测：入驻 25 秒内 COMPLETED、门户关联 ACTIVE、重新登录、`/supplier-portal` 三页签可用、内部管理端 403）。`supplier1`/`supplier2` 的口令只以哈希存在于种子、无法从仓库恢复，不猜测、不绕过；需要第二个门户身份时重走邀请/注册/入驻链路。
4. 菜单可见但列表为空时，先查租户/数据范围与业务前置；菜单缺失时查角色权限与 `/api/auth/menus`。不要直接改数据库菜单或角色。
5. 消息与调度：在 `xxl-job` 就绪后验证 Outbox 中继；不要用“消息表有记录”代替“已投递”。

## 成功判据

- full 闭包的 9 个应用与基础设施 healthy，migrator 按预期退出。
- 前端 3000 返回 200；真实登录成功，各业务菜单按权限出现。
- CRM、SRM、采购、资产、工作流各至少一个真实入口可打开；有权限的身份可读取种子数据。
- 跨模块链路以真实接口结果为准；健康检查不等于业务闭环。
- 没有新增项目、网络、卷或端口偏移；共享数据未被清空或重放迁移。

**菜单边界**：菜单接口只返回“租户权限树 ∩ 当前身份权限码”，与服务是否运行无关。full 初始化的共享库上看到所有业务菜单是预期；若切换到其他预设，业务权限码仍授予给管理员等角色，菜单会残留业务入口（实测 core 窗口 57 节点中 36 个业务码，与共享库授权查询一致）——这是共享数据状态，不是该预设闭包包含全部业务；core 的裁剪种子不含业务权限行；workflow、CRM、supply-chain 只移除未选业务模块的权限，保留所选业务的权限。空库首次安装不应残留未选模块菜单。验证子集“无其他菜单”的运行时结果需独立空库/租户；不要在共享库删权限或改角色来模拟。

## 停止与恢复

- 只停止本轮启动的应用；保留 mysql/redis/nacos/rocketmq/xxl-job 与数据卷。
- 恢复：`docker start` 登记的原服务集合，或对明确服务 `up -d --no-build --no-deps`；禁止 `down -v`、清卷、全局 prune。
- 替换失败时用替换前记录的旧镜像在同一服务位置恢复，不启动旁路服务。

## 常见失败

| 现象 | 先检查 | 不要做 |
|---|---|---|
| 端口冲突 | 3000/8100–8107/3306/6379/8080/8848/9848/9876 | 改闭包或随机偏移端口 |
| Nacos 认证失败 | `.env` 与 Nacos 凭据是否一致 | 关闭认证或写入示例秘密 |
| 网关 503 | 断言密钥、下游注册、路由配置 | 只改前端地址 |
| 某业务菜单缺失 | 角色权限、`/api/auth/menus`、对应服务健康 | 直接改数据库菜单/角色 |
| 页面可开但列表为空 | 租户、数据范围、业务前置 | 把空数据当启动失败或手工造数 |
| 审批待办为空 | 模型已发布、候选人、角色、任务创建 | 直接改任务/候选人表 |
| 收货后无资产卡 | 质检通过、资产管理标记、正整数数量 | 直接插资产卡 |
| 消息长期 PENDING | `xxl-job` 中继、RocketMQ、max_retry | 把 PENDING 写成已投递 |
| 迁移重复执行 | 共享库是否已初始化 | 清库重放或重复导入种子 |
| Maven 下载 `Remote host terminated the handshake` | 企业网络 TLS 抖动；稍后重试同一命令 | 改依赖版本或换仓库坐标 |
| `npm ci` ECONNRESET | 网络抖动；重试并保留离线缓存 | 删除 lockfile 或跳过 lint |
| 容器内下载全部失败（DNS/握手） | 为本机代理设置 `HTTP_PROXY/HTTPS_PROXY`（容器内用 `host.docker.internal:<混合端口>`），镜像构建加同名 `--build-arg` | 把失败归因于产品或预设 |
| 验证容器缺 `docker`/BuildKit | 安装 docker CLI 与 buildx 插件并 `DOCKER_BUILDKIT=1` | 用宿主临时目录复制生成物构建 |
| Maven 本地仓每次容器重建后重新下载 | 将 `-Dmaven.repo.local` 指向固定验证卷缓存 | 在镜像里烘焙私有仓库配置 |

## 历史阶段记录与未验证边界

- **已验证**：`preset explain full` 解析出 8 个显式模块与 14 个闭包模块；16 个服务、35 条端口声明/34 个唯一端口来自 catalog；资源提示按模块求和为 7296/11776 MB；源码路径 9 应用镜像构建/原位替换/健康/前端 200/真实登录/全域菜单/停止恢复；生成路径 G6/G7、交付角色自足、9 生成镜像原位替换与真实入口核验、恢复仓库镜像；交付门 `automated-delivery-pass`；Outbox 中继与调度执行由门户入驻 Saga（25 秒内角色分配 COMPLETED）真实佐证。
- **未验证**：干净空库首次安装；跨模块业务链路深度流程（CRM 转换/赢单、供应链订单收货建卡、审批多级会签的端到端）；发布就绪（`release_ready=false`，译文待指定审校）。

继续阅读[预设总览](presets-overview.md)、[功能导览](feature-tour.md)或[架构事实](../architecture.md)。
