# supply-chain 预设：供应商、采购、资产与审批

supply-chain 预设在认证与基础平台之上，一次加入 SRM、采购、资产、工作流、消息与调度，覆盖“供应商准入 → 采购申请与审批 → 询价/订单/收货 → 资产建卡 → 调拨/处置”的协作链路。它适合需要验证跨角色业务闭环的团队，也是本仓库组件较多、业务前置复杂的预设。本页的模块、服务、端口与资源来自当前 `scaffold/presets/supply-chain.yaml`、`scaffold/catalog/modules.yaml` 与 `preset explain supply-chain` 的实际解析；运行结果按“已验证/未验证”分开标注。

本页运行结果是覆盖裁剪缺陷修复前的分阶段历史记录，不代表当前版本。早期失败或未完成不抹去，后续成功也不倒签早期记录。旧 G7 绿灯不能证明当前完整覆盖；当前发布必须用保留全部适用要求的 G6/G7 和必要的 G11 重新验收。

## 它适合谁

- 需要把供应商、采购、资产和审批串成一条真实链路的团队，而不是只看单个模块的列表页。
- 需要同时验证内部员工、采购/审批人、供应商门户三类身份与数据范围的平台。
- 需要 MQ 与调度参与业务（Outbox 中继、租户配置、用户任务）的团队。

它不适合：只需要客户关系管理（请用 CRM 预设）、只需要 BPMN 审批（workflow 预设），或资源受限、只想先验证登录与 RBAC 的环境。supply-chain 不是“多几张页面”，而是多套服务、多个身份域和多段前置数据。

## 从 7 个显式模块到 13 个闭包模块

`scaffold/presets/supply-chain.yaml` 的显式模块：

```text
asset, gateway, mysql, redis, nacos, rocketmq, xxl-job
```

`preset explain supply-chain` 当前解析出的闭包：

```text
platform, auth, base, workflow, srm, procurement, asset, nacos, xxl-job, gateway, mysql, redis, rocketmq
```

依赖链解释了为什么只写 `asset` 也会带入整条供应链：

```text
asset → procurement, srm
procurement → srm
srm → workflow
workflow → base
gateway → base
base → auth → platform
```

`platform` 提供迁移、前端与公共能力，`auth` 提供登录/租户/RBAC，`base` 提供基础管理与任务接口；`workflow` 是审批运行时，`srm → procurement → asset` 是业务主线。`rocketmq` 与 `xxl-job` 是显式选择的，不是可忽略的可选项：Outbox 中继、租户供给和用户任务依赖调度，业务事件依赖消息。

## 实际服务与端口

13 个模块映射到 15 个 Compose 服务：

| 服务 | 来源模块 | 作用 |
|---|---|---|
| `mysql` | mysql | 平台与各业务库数据 |
| `redis` | redis | 缓存与令牌相关数据 |
| `nacos` | nacos | 服务发现与配置中心 |
| `rocketmq-namesrv` | rocketmq | 消息路由 |
| `rocketmq-broker` | rocketmq | 消息存储与投递 |
| `xxl-job-admin` | xxl-job | 调度中心 |
| `omni-db-migrator` | platform | 一次性结构迁移与种子初始化 |
| `omni-auth` | auth | 登录、租户、RBAC、组织、字典、审计 |
| `omni-base` | base | 基础管理、日志、工作台与任务接口 |
| `omni-workflow` | workflow | BPMN 模型、待办、审批与流程实例 |
| `omni-srm` | srm | 供应商、准入、评估、风险与门户 |
| `omni-procurement` | procurement | 物料、申请、审批路由、RFQ、订单与收货 |
| `omni-asset` | asset | 资产台账、建卡、领用/退回、调拨与处置 |
| `omni-gateway` | gateway | 统一入口与路由 |
| `omni-frontend` | platform | Vue 前端 |

catalog 共声明 32 条端口记录、31 个唯一端口（`8080` 同时由 nacos 与 xxl-job 声明）：

| 模块 | 声明端口 |
|---|---|
| platform | 80, 3000 |
| auth | 8100, 19900, 9900 |
| base | 8101, 19901, 9901 |
| workflow | 8103, 19903, 9903 |
| srm | 8105, 19905, 9905 |
| procurement | 8106, 19906, 9906 |
| asset | 8107, 19907, 9907 |
| nacos | 8080, 8848, 9848 |
| xxl-job | 8080 |
| gateway | 8102, 19902 |
| mysql | 3306 |
| redis | 6379 |
| rocketmq | 9876, 10909, 10911, 10912 |

前端入口是 3000；业务服务分别是 workflow 8103、SRM 8105、采购 8106、资产 8107。`xxl-job` 与 nacos 都声明 8080，本仓库的示例环境通过 `OMNI_XXL_JOB_HOST_PORT=18080` 映射调度中心，通过 `OMNI_ROCKETMQ_NAMESRV_HOST_PORT=19876` 映射 NameServer；这些是 `.env.example` 中登记的显式映射，不是随机偏移。端口冲突时先释放占用或按部署文档调整显式变量，不要改预设闭包。

## 收益与代价

收益是一次获得完整协作链：供应商主数据与准入、采购申请与审批、询价/订单/收货、资产建卡与后续调拨/处置共享同一套租户、权限、数据范围与审计；MQ 与调度也已就位，可以直接验证 Outbox 中继和用户任务。

代价是资源与业务前置较多：

- 按 catalog 的 `resourceHints` 求和，闭包的最低内存提示是 6784 MB、推荐 11008 MB。这是规划提示，不是实测上限；构建峰值、MySQL/RocketMQ 缓存与 JVM 堆还会叠加，实际以本机 `docker stats` 和构建日志为准。
- 多一个工作流运行时、一个消息中间件（NameServer + Broker）和一个调度中心，端口、凭据、健康检查与日志面都显著增加。
- 业务不是“登录就能跑”：供应商需要准入/邀请与门户关联，采购申请需要审批路由与工作流模型，收货需要有权限的订单与质检结果，资产建卡还要求数量与资产管理标记满足条件。
- 身份分为内部用户与供应商门户用户。供应商账号即使能登录，也必须具备 `SUPPLIER` 角色与有效的供应商门户关联，才能访问门户档案和评估（`srm:portal:profile` / `srm:portal:evaluation`）；普通 `USER` 只能申请入驻。

## 前提与配置

- **Docker 与资源**：至少预留本页提示的内存与磁盘；确认 3000、8100、8101、8102、8103、8105、8106、8107、3306、6379、8080、8848、9848、9876 等端口可用。调度与 NameServer 的宿主映射按 `.env` 显式变量核对。
- **私有 `.env`**：数据库、Redis、Nacos 认证、网关断言、`XXL_JOB_ACCESS_TOKEN`、`XXL_JOB_ADMIN_PASSWORD` 等。不要提交真实值，也不要把示例值当秘密。
- **Nacos 配置**：`omni-common.yml`、`omni-auth.yml`、`omni-base.yml`、`omni-workflow.yml`、`omni-srm.yml`、`omni-procurement.yml`、`omni-asset.yml`、`omni-gateway.yml`；Nacos 认证开启时所有客户端使用同一套账号/密码。
- **迁移与种子**：`omni-db-migrator` 一次性执行。共享库已初始化时不要重复导入、重放迁移或清库。
- **内部身份**：管理员与 `SRM_ADMIN`、`PROCUREMENT_MANAGER`、`PROCUREMENT_STAFF` 等种子角色；审批动作还取决于工作流候选人与数据范围。
- **供应商门户身份**：`auth.sql`/`srm.sql` 为租户 1 提供 `supplier1`、`supplier2` 与供应商 1/2 的门户关联，角色为 `SUPPLIER`。门户入驻要求 `inviteToken` 与客户端 `requestId`；没有有效邀请或关联时，不应通过改库“造”一个门户用户。
- **审批模型**：审批运行时在 `omni-workflow`，采购/资产服务不内嵌 Flowable。发起审批前需要已发布的流程模型、可解析的候选人与对应角色。
- **消息与调度**：RocketMQ 与 `xxl-job` 在闭包内。Outbox 消息需要中继任务投递；调度中心未就绪时，不要用“消息写入了”代替“消息已送达”。
- **业务数据**：供应商准入/评估、物料目录、采购申请与审批路由、订单、收货、资产数量与质检结果。容器启动不等于这些前置已完成。

### 启动与业务准备是两件事

健康检查通过只说明服务能响应；它不证明：

- 供应商已通过准入，门户用户已绑定；
- 工作流模型已发布，审批人能收到待办；
- 采购申请已审批，订单可收货；
- 收货行满足“质检通过 + 资产管理 + 正整数数量”，因此会生成资产卡；
- Outbox 消息已被中继投递。

这些条件要按业务顺序逐项准备和验证，不能用直接改数据库状态绕过。

## 路径一：从源码启动 supply-chain

### 干净数据库首次安装

在共享数据尚未初始化的独立环境：

```bash
docker compose --parallel 1 --profile supply-chain up -d --build
docker compose ps
```

预期：migrator 执行后退出，mysql/redis/nacos/rocketmq/xxl-job 与 auth/base/workflow/srm/procurement/asset/gateway/frontend 启动，应用服务 healthy，前端 3000 可访问。该路径会执行迁移与种子，只适用于空库或明确允许初始化的环境。

### 已有共享环境的原位子集

如果数据库已由 full 初始化，不要再次运行 migrator。构建 8 个应用镜像，停用不在闭包内的 CRM，再原位替换：

```bash
docker compose --parallel 1 -p omni-stack build omni-auth omni-base omni-workflow omni-srm omni-procurement omni-asset omni-gateway omni-frontend
docker compose -p omni-stack stop omni-crm
docker compose -p omni-stack up -d --no-build --no-deps --force-recreate omni-auth omni-base omni-workflow omni-srm omni-procurement omni-asset omni-gateway omni-frontend
docker compose -p omni-stack ps
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:3000/
```

- `docker compose --parallel 1 ... build`：只为闭包内 8 个应用构建镜像；不启动 migrator，不重建 mysql/redis/nacos/rocketmq/xxl-job。
- `docker stop`：停用闭包外的 CRM，避免同一环境保留两套业务运行面；基础设施与数据卷保留。
- `up -d --no-build --no-deps --force-recreate`：用新镜像原位替换 8 个应用，不启动依赖、不执行迁移。
- `ps`：核对实际镜像与健康；healthy 只代表进程与健康检查通过，不等于业务链路已就绪。
- `curl`：确认前端 200。业务请求无令牌返回 401 只证明鉴权拒绝；服务与路由可用还需已授权成功请求、注册与健康证据。

停用列表按当前环境实际登记填写；mysql/redis/nacos/rocketmq/xxl-job 与数据保留，不执行 `down -v`、不清卷。

## 路径二：生成独立的 supply-chain 工程

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create supply-chain --output generated/learning-supply-chain
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset validate supply-chain --output generated/learning-supply-chain
```

- `preset create`：把 supply-chain 裁剪成独立工程，包含自己的 `scaffold.lock`、Compose、文档、Maven 与前端源码。
- `preset validate`：校验生成物的模块闭包、Compose、文档、AI 入口与裁剪边界；不启动应用。
- 生成物有自己的构建命令。进入生成物后使用它的命令；不要在原仓库目录启动生成物的 Compose 项目，也不要运行生成物的 migrator 覆盖共享数据库。

历史早期阶段：G6 结构 PASS（13 模块/15 服务）；G7 生成物自身后端 BUILD SUCCESS、前端 build/lint 通过（`VERIFY_PASS modules=13 services=15`）。交付角色隔离（不挂源仓/socket）与生成物镜像的 G11 原位替换尚未执行，因此不能写成“生成物镜像已在同一环境运行通过”。

## 首次使用（按业务顺序）

1. 打开 `http://localhost:3000`，选择开发租户，用 `admin` 与**本次显示的验证码**登录内部端。不要复用旧验证码或使用认证后门。
2. Wiki 首页保留在原页签；在独立页签打开“我的工作台”，通过应用目录分别以新页签进入 SRM、采购、资产和工作流。各业务页只展示当前模块的可访问菜单，审批待办集中在工作台。
3. 按顺序准备业务：供应商准入/评估 → 物料与审批路由 → 采购申请提交并审批 → 询价/订单 → 收货确认 → 资产建卡 →（可选）调拨/处置审批。每一步以真实接口返回的单号和状态为准。
4. 供应商门户：使用 `supplier1` 或 `supplier2` 登录门户（租户 1，`SUPPLIER` 角色，已关联供应商 1/2）。门户入驻仍需有效 `inviteToken` 与客户端 `requestId`；不要直接写 `srm_supplier_portal_user` 或改供应商归属来跳过。
5. 审批：待办为空时先检查流程模型是否已发布、当前用户是否在候选人范围、任务是否已创建；不要直接改任务或候选人表。
6. 收货建卡：只有 `qualityStatus=PASS`、`assetManaged=true` 且 `assetQuantity` 为正整数的收货行才会生成资产卡；不满足时“没有卡”是业务规则，不是服务故障。
7. 若菜单缺失，先查角色权限与 `/api/auth/menus`；若页面可开但列表为空，再查租户/数据范围与前置数据，不要先改数据库。

## 成功判据

- supply-chain 闭包应用服务 healthy，migrator 按预期退出；RocketMQ 与 xxl-job 健康。
- 前端 3000 返回 200；内部登录成功，SRM/采购/资产/工作流菜单按权限出现。
- 供应商门户身份可登录且只看到自己的门户数据；内部身份与门户身份分开验证。
- 按顺序完成一条真实链路：供应商/物料准备 → 申请审批 → 订单 → 收货 → 资产建卡；每步有真实接口结果。
- 没有新增项目、网络、卷或端口偏移；共享数据未被清空或重放迁移。

**菜单边界**：共享 full 库可能保留 CRM 权限码，即使 CRM 服务已停止；这不代表 supply-chain 闭包包含 CRM。要验证“只含供应链菜单”，需使用对应种子初始化的独立空库/租户。

## 停止与恢复

- 只停止本轮启动的应用；保留 mysql/redis/nacos/rocketmq/xxl-job 与数据卷。
- 恢复：`docker start` 登记的原服务集合，或对明确服务 `up -d --no-build --no-deps`；禁止 `down -v`、清卷或全局 prune。
- 替换失败时用替换前记录的旧镜像在同一服务位置恢复，不启动旁路服务。

## 常见失败

| 现象 | 先检查 | 不要做 |
|---|---|---|
| 端口冲突 | 3000/8100/8101/8102/8103/8105/8106/8107/3306/6379/8080/8848/9848/9876 | 改闭包或随机偏移端口 |
| Nacos 认证失败 | `.env` 与 Nacos 凭据是否一致 | 关闭认证或写入示例秘密 |
| 调度中心不可用 | `XXL_JOB_*` 凭据、18080 映射、admin 健康 | 把 Outbox 写入当成消息已投递 |
| 供应商门户登录后无权限 | `SUPPLIER` 角色、门户关联、邀请状态 | 直接写门户关联表或改归属 |
| 审批待办为空 | 模型已发布、候选人、角色、任务创建 | 直接改任务/候选人表 |
| 收货后没有资产卡 | 质检通过、资产管理标记、正整数数量、权限 | 直接插资产卡绕过规则 |
| 菜单缺失 | 角色权限、`/api/auth/menus`、auth/base 健康 | 直接改数据库菜单 |
| 迁移重复执行 | 共享库是否已初始化 | 清库重放或重复导入种子 |

## 历史阶段记录与未验证边界

- **已验证（源码路径）**：8 个应用镜像构建并在同一 `omni-stack` 项目原位替换；auth/base/workflow/srm/procurement/asset/gateway healthy，前端 200；4 条业务路由未带令牌均返回 401；`admin` 真实验证码登录 200，菜单 57 节点，其中 SRM 11、采购 8、资产 5、工作流 5 个权限码；只读查询供应商 2、门户用户 2，物料/申请/订单/资产为 0（空业务数据，不判失败）；结束后恢复 15 个容器。
- **已验证（生成路径）**：G6 结构 13 模块/15 服务 PASS；G7 生成物后端 BUILD SUCCESS、前端 build/lint PASS。
- **未验证**：供应商门户真实登录（`supplier1` 使用已知约定口令返回“密码错误”，未再猜测或绕过）；完整供应商准入 → 审批 → 订单 → 收货 → 建卡链路；交付角色隔离与生成物镜像替换；干净空库首次安装；审批待办、消息投递与调度执行的真实结果。

> 供应商门户的 seed 关联（`SUPPLIER` 角色与供应商 1/2）已确认存在，但“关联存在”不等于“已完成一次真实门户登录”。本页不把受控测试身份写成真实登录。

继续阅读[预设总览](presets-overview.md)、[SRM 事实文档](../srm.md)或[采购设计](../design/procurement-design.md)。
