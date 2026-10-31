# core 预设：只含认证与基础平台

core 适合“先要一套能登录、能管租户与权限、暂时不做业务流程”的场景。它的价值不是功能多，而是闭包小、边界清楚。本页的组件、端口和资源均从当前预设文件与 catalog 解析得到；运行结果按“已验证/未验证”分开标注，不把结构解析写成启动成功。

本页运行结果是覆盖裁剪缺陷修复前的历史快照，菜单数量与待审状态均为当时数据。旧 G7 和交付门绿灯不能证明当前完整覆盖；当前版本必须用保留全部适用要求的 G6/G7 和必要的 G11 重新验收。仍有效的构建、启动、角色隔离证据可按输入摘要复用。

## 它适合谁

- 想先验证登录、租户、RBAC、组织、字典和日志的团队。
- 需要一套最小后台骨架，再按业务域逐个增加模块的开发者。
- 需要生成一个不含 CRM、SRM、采购、资产与工作流的独立工程。

它不适合：需要审批流、销售链路、供应商/采购/资产协作，或需要调度中心执行定时任务的场景。core 的闭包里没有这些业务服务，也没有 `xxl-job` 调度中心。

## 从 5 个显式模块到 7 个闭包模块

`scaffold/presets/core.yaml` 的显式模块：

```text
base, gateway, mysql, redis, nacos
```

`preset explain core` 当前解析出的闭包：

```text
platform, auth, base, nacos, gateway, mysql, redis
```

多出的 `platform` 与 `auth` 来自依赖：`base` 依赖 `auth`，`auth` 和 `nacos` 依赖 `platform`。这解释了一个常见疑问：没有手写 `auth`，为什么仍有登录和权限服务。

## 实际服务与端口

七个模块映射到以下 8 个 Compose 服务：

| 服务 | 来源模块 | 作用 |
|---|---|---|
| `mysql` | mysql | 业务与平台数据 |
| `redis` | redis | 缓存、会话/令牌相关数据 |
| `nacos` | nacos | 服务发现与配置中心 |
| `omni-db-migrator` | platform | 一次性结构迁移与种子初始化 |
| `omni-auth` | auth | 登录、租户、RBAC、组织、字典、审计等 |
| `omni-base` | base | 基础管理、监控、日志、工作台与任务接口 |
| `omni-gateway` | gateway | 统一入口与路由 |
| `omni-frontend` | platform | Vue 前端 |

catalog 声明的端口如下（同一模块的多个端口原样列出，不推断未声明的用途）：

| 模块 | 声明端口 |
|---|---|
| platform | 80, 3000 |
| auth | 8100, 19900, 9900 |
| base | 8101, 19901, 9901 |
| gateway | 8102, 19902 |
| nacos | 8080, 8848, 9848 |
| mysql | 3306 |
| redis | 6379 |

前端入口按平台声明的 3000 端口访问。端口被占用时，不要改预设闭包来“避开”；先释放端口或按部署文档调整本机映射。Nacos 3.x 的 9848 是 gRPC 端口，属于标准端口，不是随机偏移。

## 前提与配置

core 不是“零配置”。启动前至少准备：

1. **本机 Docker**，并确认 3000、8100、8101、8102、3306、6379、8080、8848、9848 未被其他进程占用。
2. **`.env` 私有配置**：数据库、Redis、Nacos 认证、网关断言等秘密值。不要提交真实值，也不要把示例值当成可用秘密。
3. **Nacos 配置**：`omni-common.yml`、`omni-auth.yml`、`omni-base.yml`、`omni-gateway.yml`。Nacos 开启认证时，所有客户端使用同一套 `NACOS_USERNAME` / `NACOS_PASSWORD`；密码不一致会表现为服务反复重启或鉴权失败。
4. **数据库结构与种子**：由 `omni-db-migrator` 执行。它是一次性进程，不是常驻服务；共享数据库已经初始化时不要重复导入或清空。
5. **租户与管理员身份**：来自认证种子。首次登录按教程选择租户和种子账号，并人工输入本次验证码。

网关断言密钥只从受控配置注入。缺少或与下游不一致时，接口会返回鉴权/网关错误；先核对配置来源，不要先改业务代码。

## 路径一：从源码启动 core

### 首次安装（干净数据库）

在共享数据尚未初始化的独立环境：

```bash
docker compose --parallel 1 --profile core up -d --build
docker compose ps
```

预期：`omni-db-migrator` 执行完成后退出，mysql/redis/nacos/auth/base/gateway/frontend 启动；`docker compose ps` 中应用服务为 healthy，前端 3000 可访问。该路径会执行迁移与种子，只适用于空库或明确允许初始化的环境。**本页未实测该路径**：当前验证环境使用已由 full 初始化的共享库，空库首次安装保持“未验证”标注，不以共享库结果冒充。

### 已有共享环境的原位子集（本页实际验证的路径）

如果数据库已经由 full 初始化，不要再次运行 migrator。先构建本轮源码镜像，再停用不在 core 闭包内的业务应用，最后只替换 core 应用：

```bash
docker compose --parallel 1 -p omni-stack build omni-auth omni-base omni-gateway omni-frontend
docker compose -p omni-stack stop omni-workflow omni-crm omni-srm omni-procurement omni-asset xxl-job-admin rocketmq-broker rocketmq-namesrv
docker compose -p omni-stack up -d --no-build --no-deps --force-recreate omni-auth omni-base omni-gateway omni-frontend
docker compose -p omni-stack ps
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:3000/
```

停用列表按当前环境实际登记的服务填写，不要照抄其他预设；core 不包含 workflow、CRM、SRM、采购、资产、xxl-job 与 RocketMQ。mysql/redis/nacos 与数据保留，不执行 `down -v`、不清卷。验证结束后按登记集合 `up -d` 恢复全部服务。

## 路径二：生成独立的 core 工程

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create core --output generated/learning-core
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset validate core --output generated/learning-core
```

生成物有自己的 `scaffold.lock`、Compose、文档与 AI 入口。进入生成物后使用它自己的构建命令；不要在原仓库目录里启动生成物的 Compose 项目，也不要运行生成物的 migrator 覆盖共享数据库。

历史记录：G6 结构（7 模块/8 服务）、生成物自身后端构建和前端 build/lint 通过；交付角色仅挂生成物与缓存子路径，无源仓、无 Docker socket，自构建通过。四个生成镜像曾在同一环境原位替换，经登录、菜单与工作台核验后恢复仓库镜像。当时 `verify-preset-delivery core` 报 `automated-delivery-pass`、`release_ready=false`，但旧覆盖裁剪会遗漏要求，不能据此宣称当前 G7 或发布验收完成，也不能把当前缺口归结为只差译文审校。

## 首次访问与登录

1. 打开 `http://localhost:3000`，确认 Wiki 首页和登录入口可加载。
2. 选择教程指定的开发租户，输入种子账号与**本次显示的验证码**；不要复用旧验证码或使用认证后门。
3. 登录成功后检查：
   - 从首页打开独立页签中的“我的工作台”，原 Wiki 页签保留；
   - 系统管理、用户/角色/权限、组织、租户、日志等入口按角色出现；
   - 基础管理、字典、任务接口等 `base` 权限按角色出现。
4. 如果菜单一直加载失败，先查 auth/base/gateway 健康、Nacos 配置和 `/api/auth/menus`，再看前端；不要直接改数据库菜单表。

## 成功判据

- `docker compose ps` 中 core 闭包的应用服务健康，migrator 已按预期退出。
- 前端 3000 返回 200，静态资源可加载。
- 真实验证码登录返回成功，菜单接口返回当前身份可见的节点。
- core 的认证、RBAC、组织、字典、日志入口可用。
- 没有新增项目、网络、卷或端口偏移；共享数据未被清空或重放迁移。

**菜单边界**：菜单接口只返回“租户权限树 ∩ 当前身份权限码”（`MenuController` 按 JWT 权限过滤 DIRECTORY/MENU 节点），与服务是否运行无关。在按 full 初始化的共享库上，业务权限码仍然授予给管理员等角色，因此业务服务停止后菜单仍会出现业务入口——实测 core 窗口下管理员菜单 57 个节点中 36 个为业务 DIRECTORY/MENU 码，与共享库授权查询一致；这是共享数据的状态，不是 core 闭包包含了业务模块。core 的裁剪种子不含任何业务权限行（源仓种子 141 行业务权限，生成 core 为 0），因此**用 core 种子初始化的空库首次安装不会出现业务入口**；该结论由菜单派生机制与种子裁剪共同证明，运行时实测仍需在独立空库/独立租户环境完成。不要在现有共享库上删权限或改角色来模拟。

## 停止与恢复

- 只停止本轮启动的应用：按容器名 `docker stop`，或使用 Compose 对明确服务执行停止；保留 mysql/redis/nacos 与数据卷。
- 恢复：按登记的原服务集合 `docker start`，或对明确服务执行 `up -d --no-build --no-deps`；不要整栈 `down -v`、清卷或全局 prune。
- 替换失败时，用替换前记录的旧镜像在同一服务位置恢复，不启动旁路服务。

## 常见失败

| 现象 | 先检查 | 不要做 |
|---|---|---|
| 端口被占用 | 3000/8100/8101/8102/3306/6379/8080/8848/9848 | 改预设闭包或随机偏移端口 |
| Nacos 认证失败 | `.env` 与 Nacos 的账号/密码是否一致 | 关闭认证或写入示例秘密 |
| 网关 503/鉴权失败 | 断言密钥、下游服务注册、路由配置 | 只改前端请求地址 |
| 菜单加载失败 | auth/base/gateway 健康、`/api/auth/menus`、Nacos 配置 | 直接改数据库菜单 |
| 任务入口存在但任务不执行 | core 是否包含 `xxl-job`（不包含） | 把任务代码存在写成调度可用 |
| 迁移重复执行 | 共享库是否已初始化 | 清库重放或重复导入种子 |
| Maven 下载 `Remote host terminated the handshake` | 企业网络 TLS 抖动；稍后重试同一命令 | 改依赖版本或换仓库坐标 |
| `npm ci` ECONNRESET | 网络抖动；重试并保留离线缓存 | 删除 lockfile 或跳过 lint |
| 容器内下载全部失败（DNS/握手） | 为本机代理设置 `HTTP_PROXY/HTTPS_PROXY`（容器内用 `host.docker.internal:<混合端口>`），镜像构建加同名 `--build-arg` | 把失败归因于产品或预设 |
| 验证容器缺 `docker`/BuildKit | 安装 docker CLI 与 buildx 插件并 `DOCKER_BUILDKIT=1` | 用宿主临时目录复制生成物构建 |
| Maven 本地仓每次容器重建后重新下载 | 将 `-Dmaven.repo.local` 指向固定验证卷缓存 | 在镜像里烘焙私有仓库配置 |

## 已验证与未验证

- **已验证**：core 闭包解析、8 服务清单、端口与资源提示；源码应用镜像构建；在同一 `omni-stack` 项目内原位替换 auth/base/gateway/frontend；服务健康、前端 200、真实验证码登录 200；core 窗口菜单与工作台界面（含业务残留为共享库授权驱动的结论）；恢复 full 服务集合后健康；生成物 G6/G7、交付角色自足（无源仓/无 socket）、生成物四镜像原位替换与真实入口核验、替换后恢复仓库镜像；交付门 `automated-delivery-pass`。
- **未验证**：干净空库首次安装；core 种子下“无业务菜单”的运行时实测（机制与种子裁剪已证明，运行时待独立空库环境）；发布就绪（`release_ready=false`，译文待指定审校）；调度任务执行（core 不含调度中心）。

继续阅读[预设总览](presets-overview.md)或[首次启动教程](../guides/quick-start.md)。
