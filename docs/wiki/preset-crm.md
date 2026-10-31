# CRM 预设：核心平台加销售管道

CRM 预设在 core 的认证与基础平台之上加入客户关系管理服务，适合需要线索、客户、联系人、商机和跟进记录，但暂时不需要 SRM、采购、资产或审批流的团队。本页的模块、服务和端口来自当前 `scaffold/presets/crm.yaml`、`scaffold/catalog/modules.yaml` 与 `preset explain crm` 的实际解析；运行结果按“已验证/未验证”分开标注，结构解析不等于启动成功。

本页运行结果是覆盖裁剪缺陷修复前的分阶段历史记录，不代表当前版本。早期失败或未完成不抹去，后续成功也不倒签早期记录。旧 G7 绿灯不能证明当前完整覆盖；当前发布必须用保留全部适用要求的 G6/G7 和必要的 G11 重新验收。

## 它适合谁

- 想先跑通“线索 → 跟进 → 客户/联系人 → 商机 → 赢单/输单”销售链路的团队。
- 需要租户隔离、功能权限、数据范围和 PII 脱敏，但不想同时引入供应链或工作流的项目。
- 准备以 CRM 为业务示例，再按自己的域模型替换线索、客户或商机实现的开发者。

它不适合：需要供应商、采购、资产协作，需要 BPMN 审批，或需要调度中心执行定时任务的场景。CRM 预设不包含 SRM、采购、资产、workflow、RocketMQ 与 `xxl-job`。CRM 模块在 catalog 中把 RocketMQ/xxl-job 列为可选依赖，本预设没有选择它们，因此不要按“异步消息中继或调度已可用”来设计验收。

## 从 5 个显式模块到 8 个闭包模块

`scaffold/presets/crm.yaml` 的显式模块：

```text
crm, gateway, mysql, redis, nacos
```

`preset explain crm` 当前解析出的闭包：

```text
platform, auth, base, crm, nacos, gateway, mysql, redis
```

`crm` 依赖 `base`，`base` 依赖 `auth`，`auth` 与 `nacos` 依赖 `platform`，`gateway` 依赖 `base`。这解释了为什么没有手写 `auth`/`base`，登录、权限、组织、字典、日志与基础管理仍在闭包内；CRM 只是加在它们之上，不替代它们。

## 实际服务与端口

8 个模块映射到 9 个 Compose 服务：

| 服务 | 来源模块 | 作用 |
|---|---|---|
| `mysql` | mysql | 平台与 CRM 业务数据 |
| `redis` | redis | 缓存与令牌相关数据 |
| `nacos` | nacos | 服务发现与配置中心 |
| `omni-db-migrator` | platform | 一次性结构迁移与种子初始化 |
| `omni-auth` | auth | 登录、租户、RBAC、组织、字典、审计 |
| `omni-base` | base | 基础管理、日志、工作台与任务接口 |
| `omni-crm` | crm | 线索、客户、联系人、商机、跟进与销售管道 |
| `omni-gateway` | gateway | 统一入口与路由 |
| `omni-frontend` | platform | Vue 前端 |

catalog 声明的端口共 18 个：

| 模块 | 声明端口 |
|---|---|
| platform | 80, 3000 |
| auth | 8100, 19900, 9900 |
| base | 8101, 19901, 9901 |
| crm | 8104, 19904, 9904 |
| gateway | 8102, 19902 |
| nacos | 8080, 8848, 9848 |
| mysql | 3306 |
| redis | 6379 |

前端入口按平台声明的 3000 端口访问，CRM 后端对外是 8104。端口被占用时先释放占用或按部署文档调整本机映射，不要改预设闭包来“避开”，也不要使用随机偏移端口。

## 收益与代价

收益是边界清楚：认证、租户、RBAC 与基础管理来自平台，CRM 只增加自己的服务、权限树、角色和数据表；销售管道、线索转换和客户归属规则都集中在 `omni-crm`，可以单独替换或裁剪。

代价是组件与运维面明显大于 core：多一个 JVM 服务、一套 CRM 迁移与种子、四个 CRM 角色和一组 `crm:*` 权限码；数据范围同时涉及本人、部门、租户三种口径，PII 字段还受掩码规则约束。按 catalog 各模块 `resourceHints` 求和，闭包的最低内存提示是 3840 MB、推荐 6400 MB；这是规划提示，不是实测上限，实际占用以本机 `docker stats` 为准。

## 前提与配置

- **Docker 与端口**：3000、8100、8101、8102、8104、3306、6379、8080、8848、9848 不可被占用。
- **私有 `.env`**：数据库、Redis、Nacos 认证、网关断言等秘密值。不要提交真实值，也不要把示例值当成可用秘密。
- **Nacos 配置**：`omni-common.yml`、`omni-auth.yml`、`omni-base.yml`、`omni-crm.yml`、`omni-gateway.yml`。Nacos 开启认证时，所有客户端使用同一套 `NACOS_USERNAME` / `NACOS_PASSWORD`。
- **迁移与种子**：由 `omni-db-migrator` 执行。共享数据库已经初始化时不要重复导入、重放迁移或清库。
- **身份与角色**：`auth.sql` 提供租户、管理员和角色/权限种子；CRM 角色包括 `CRM_ADMIN`、`SALES_MANAGER`、`SALES_REP`、`CRM_VIEWER`。种子账号是否能看到 CRM 菜单，取决于其角色是否被授予 `crm` 与 `crm:*` 权限。

### 认证种子与业务示例数据不是一回事

- **认证/平台种子**（`scripts/sql/seed/auth.sql`、`base.sql`、`nacos.sql`）负责租户、用户、角色、权限、组织、字典等“能登录、能授权”的基础数据。
- **CRM 业务种子**（`scripts/sql/seed/crm.sql`）负责默认销售管道与阶段、CRM 租户配置，以及租户 1 的演示线索、客户、联系人、商机、跟进记录和转换记录。
- 业务示例数据是让首次进入页面不为空的样例，不是 CRM 启动的必要条件。没有线索或客户时，先判断“业务数据为空”还是“服务/权限故障”，不要为了演示直接改数据库状态。
- 其他租户由租户创建事件配置 CRM 角色、权限与默认配置；演示业务记录只存在于正式种子覆盖的租户 1。跨租户看不到这些数据是隔离生效，不是菜单丢失。

## 路径一：从源码启动 CRM

### 干净数据库首次安装

在共享数据尚未初始化的独立环境：

```bash
docker compose --parallel 1 --profile crm up -d --build
docker compose ps
```

预期：`omni-db-migrator` 执行完成后退出，mysql/redis/nacos/auth/base/crm/gateway/frontend 启动，`docker compose ps` 中应用服务 healthy，前端 3000 可访问。该路径会执行迁移与种子，只适用于空库或明确允许初始化的环境。

### 已有共享环境的原位子集

如果数据库已经由 full 初始化，不要再次运行 migrator。先构建本轮源码镜像，再停用不在 CRM 闭包内的业务应用，最后只替换 CRM 应用：

```bash
docker compose --parallel 1 -p omni-stack build omni-auth omni-base omni-crm omni-gateway omni-frontend
docker compose -p omni-stack stop omni-workflow omni-srm omni-procurement omni-asset xxl-job-admin rocketmq-broker rocketmq-namesrv
docker compose -p omni-stack up -d --no-build --no-deps --force-recreate omni-auth omni-base omni-crm omni-gateway omni-frontend
docker compose -p omni-stack ps
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:3000/
```

- `docker compose --parallel 1 ... build`：只为 CRM 闭包内的 5 个应用构建镜像；不会启动 migrator，也不会重建 mysql/redis/nacos。
- `docker stop`：停止不在 CRM 闭包内的业务应用，避免同一环境里同时存在多套业务运行面；基础设施与数据卷保留。
- `up -d --no-build --no-deps --force-recreate`：用刚构建的镜像原位替换 5 个应用，不启动依赖服务、不执行迁移。
- `ps`：核对容器实际状态与镜像；healthy 只代表进程与健康检查通过，不代表业务闭环。
- `curl`：确认前端入口返回 200。无令牌请求 `/api/crm/lead/list` 返回 401 只证明鉴权拒绝；路由与下游服务可用还需已授权请求成功及服务注册、健康证据，不能由 401 单独推断。

停用列表按当前环境实际登记的服务填写，不要照抄其他预设。mysql/redis/nacos 与数据保留，不执行 `down -v`、不清卷。

## 路径二：生成独立的 CRM 工程

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create crm --output generated/learning-crm
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset validate crm --output generated/learning-crm
```

- `preset create`：把 CRM 预设裁剪成独立工程，产物包含自己的 `scaffold.lock`、Compose、文档、Maven 与前端源码。
- `preset validate`：校验生成物的模块闭包、Compose、文档、AI 入口与裁剪边界；它不启动应用。
- 生成物有自己的构建命令。进入生成物后使用它的命令；不要在原仓库目录里启动生成物的 Compose 项目，也不要运行生成物的 migrator 覆盖共享数据库。

历史早期阶段：G6 结构已通过（8 模块/9 服务，固定产物位）；G7 中生成物后端 `./mvnw clean install` 已 BUILD SUCCESS（含 CRM 与网关测试），但前端 `npm run build` 尚未通过——首轮缺少 `@rolldown/binding-linux-x64-gnu`，清理验证卷 npm 缓存后重跑又在 Maven 下载阶段遇到 TLS 握手终止。交付角色隔离与生成物镜像原位替换尚未执行。因此“生成物后端可构建”不等于“生成物已在同一环境启动通过”，本页不把两者混写。

## 首次使用

1. 打开 `http://localhost:3000`，选择开发租户，输入种子账号与**本次显示的验证码**。本地演示管理员为 `admin`（口令见[首次启动教程](../guides/quick-start.md)），不要复用旧验证码或使用认证后门。
2. Wiki 首页保留在原页签；从首页打开独立的“我的工作台”，再从应用目录以新页签进入 CRM。CRM 页只显示当前模块的概览、线索、客户、联系人、商机、跟进菜单；权限仍来自 `/api/auth/menus`。
3. 建议操作顺序：线索新建 → 跟进记录 → 线索转换（生成客户/联系人/商机）→ 商机推进阶段 → 赢单或输单 → 客户与联系人维护。演示数据已包含部分转换结果，可直接查看，但新增记录以本次操作返回的编号为准。
4. 若菜单可见但列表为空，先确认当前租户与数据范围：`SALES_REP` 是本人数据，`SALES_MANAGER` 是部门及以下，`CRM_ADMIN`/`CRM_VIEWER` 是租户级。看不到别人的数据可能是数据权限生效，不是接口失败。
5. 若菜单缺失，检查当前角色是否被授予 CRM 权限；`SUPER_ADMIN`、`CRM_ADMIN`、`SALES_MANAGER` 拥有完整 CRM 权限，`SALES_REP` 拥有日常录入与转换权限，`CRM_VIEWER` 只有只读列表权限。不要直接改数据库角色表来“修菜单”。

## 成功判据

- CRM 闭包应用服务 healthy，migrator 按预期退出。
- 前端 3000 返回 200，静态资源可加载。
- 真实验证码登录成功，`/api/auth/menus` 返回当前身份可见的 CRM 菜单。
- CRM 概览、线索、客户、联系人、商机、跟进页面可打开；有权限的身份可以读取种子演示数据。
- 新增/转换等写操作以真实接口结果为准；健康检查通过不等于业务闭环通过。
- 没有新增项目、网络、卷或端口偏移；共享数据未被清空或重放迁移。

**菜单边界**：在按 full 初始化的共享库上，其他业务权限码可能仍然存在，即使对应服务已停止；这是共享数据的状态，不是 CRM 闭包包含了 SRM/采购/资产/工作流。若要验证“CRM 预设无其他业务菜单”，必须使用 CRM 种子初始化的独立空库或独立租户；不要在现有共享库上删权限或改角色来模拟。

## 停止与恢复

- 只停止本轮启动的应用：按容器名 `docker stop`，或使用 Compose 对明确服务执行停止；保留 mysql/redis/nacos 与数据卷。
- 恢复：按登记的原服务集合 `docker start`，或对明确服务执行 `up -d --no-build --no-deps`；不要整栈 `down -v`、清卷或全局 prune。
- 替换失败时，用替换前记录的旧镜像在同一服务位置恢复，不启动旁路服务。

## 常见失败

| 现象 | 先检查 | 不要做 |
|---|---|---|
| 端口被占用 | 3000/8100/8101/8102/8104/3306/6379/8080/8848/9848 | 改预设闭包或随机偏移端口 |
| Nacos 认证失败 | `.env` 与 Nacos 的账号/密码是否一致 | 关闭认证或写入示例秘密 |
| 网关 503/鉴权失败 | 断言密钥、`omni-crm` 是否注册、路由配置 | 只改前端请求地址 |
| CRM 菜单缺失 | 当前角色是否授予 `crm:*`、`/api/auth/menus`、auth/base 健康 | 直接改数据库菜单或角色 |
| 页面可开但列表为空 | 当前租户、数据范围、是否已有业务数据 | 把空数据当成启动失败，或手工改库造数 |
| 线索转换失败 | 线索状态、必填字段、权限码 `crm:lead:convert` | 绕过接口直接插入客户/商机 |
| 迁移重复执行 | 共享库是否已初始化 | 清库重放或重复导入种子 |

## 历史阶段记录与未验证边界

- **已验证（源码路径）**：5 个应用镜像构建成功并在同一 `omni-stack` 项目中原位替换；auth/base/crm/gateway healthy、前端 200；真实验证码登录返回 code 200；`/api/auth/menus` 返回 57 个节点，其中 CRM 目录与 6 个销售菜单齐全；只读查询 `omni_crm` 得到 lead=8、customer=5、opportunity=6、activity=13、pipeline_stage=6；结束后 15 个原登记容器恢复运行。
- **已验证（生成路径，部分）**：G6 结构 8 模块/9 服务通过；生成物后端 `./mvnw clean install` BUILD SUCCESS，CRM 45 个测试与网关测试通过。
- **未验证**：生成物前端 `npm run build`（原生绑定缺失与随后 Maven TLS 握手中断）、交付角色隔离、生成物镜像原位替换；干净空库首次安装；CRM 页面浏览器视觉渲染与业务写操作闭环；调度与异步中继（本预设不含 `xxl-job`/RocketMQ）。

继续阅读[预设总览](presets-overview.md)或 [CRM 事实文档](../crm.md)。
