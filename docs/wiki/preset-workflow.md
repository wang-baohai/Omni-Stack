# workflow 预设：核心平台加 BPMN 与审批

workflow 在 core 的认证与基础平台之上加入独立工作流服务，适合需要 BPMN 建模、流程发布、待办、审批和流程实例，但暂时不需要 CRM、SRM、采购或资产业务的场景。本页组件来自当前预设与 catalog 解析，运行结果按“已验证/未验证”分开标注。

本页运行结果是覆盖裁剪缺陷修复前的分阶段历史记录，不代表当前版本。早期失败或未完成不抹去，后续成功也不倒签早期记录。旧 G7 绿灯不能证明当前完整覆盖；当前发布必须用保留全部适用要求的 G6/G7 和必要的 G11 重新验收。

## 从 5 个显式模块到 8 个闭包模块

`scaffold/presets/workflow.yaml` 的显式模块：

```text
workflow, gateway, mysql, redis, nacos
```

`preset explain workflow` 当前解析出的闭包：

```text
platform, auth, base, workflow, nacos, gateway, mysql, redis
```

`workflow` 依赖 `base`，`gateway` 依赖 `base`，`base` 依赖 `auth`。所以没有手写 `auth`/`base`，登录、权限、组织、字典与基础管理仍在闭包内。

## 实际服务与端口

8 个模块映射到 9 个 Compose 服务：

| 服务 | 来源模块 | 作用 |
|---|---|---|
| `mysql` | mysql | 业务、平台与 Flowable 数据 |
| `redis` | redis | 缓存与令牌相关数据 |
| `nacos` | nacos | 服务发现与配置中心 |
| `omni-db-migrator` | platform | 一次性结构与种子初始化 |
| `omni-auth` | auth | 登录、租户、RBAC、组织、字典、审计 |
| `omni-base` | base | 基础管理、日志、工作台与任务接口 |
| `omni-workflow` | workflow | BPMN 模型、部署、待办、审批与流程实例 |
| `omni-gateway` | gateway | 统一入口与路由 |
| `omni-frontend` | platform | Vue 前端 |

catalog 声明的端口：

| 模块 | 声明端口 |
|---|---|
| platform | 80, 3000 |
| auth | 8100, 19900, 9900 |
| base | 8101, 19901, 9901 |
| workflow | 8103, 19903, 9903 |
| gateway | 8102, 19902 |
| nacos | 8080, 8848, 9848 |
| mysql | 3306 |
| redis | 6379 |

`omni-workflow` 是独立服务（8103），不并入 `omni-base` 或 `omni-auth`；Flowable 运行时只存在于 `omni-workflow`，业务服务不应引入 Flowable 依赖。

## 前提与配置

- **Docker 与端口**：3000、8100、8101、8102、8103、3306、6379、8080、8848、9848 不可被占用。
- **私有 `.env`**：数据库、Redis、Nacos 认证、网关断言等秘密值；不要提交真实值。
- **Nacos 配置**：`omni-common.yml`、`omni-auth.yml`、`omni-base.yml`、`omni-workflow.yml`、`omni-gateway.yml`；Nacos 认证账号/密码必须一致。
- **迁移与种子**：`omni-db-migrator` 一次性执行；共享库已初始化时不要重复迁移或清库。
- **BPMN 模型**：`wf_process_model.model_key` 在租户内唯一，并与 BPMN `<process id>` 一致；发布前必须通过 `BpmnXmlValidator`。演示模型可来自种子，实际业务号以本次运行创建/查询的 ID 为准。
- **候选人与角色**：`omni:assignment` JSON 扩展是候选人解析的唯一入口；任务开始时由监听器解析。登录账号不等于流程候选人，需有对应角色与数据范围。

## 路径一：从源码启动 workflow

### 干净数据库首次安装

```bash
docker compose --parallel 1 --profile workflow up -d --build
docker compose ps
```

预期：migrator 完成并退出，mysql/redis/nacos/auth/base/workflow/gateway/frontend 启动，`docker compose ps` 应用 healthy，前端 3000 可访问。该路径会执行迁移与种子，只适用于空库或明确允许初始化的环境。

### 已有共享环境的原位子集

数据库已由 full 初始化时，不运行 migrator。构建本轮镜像，停用不在 workflow 闭包内的业务应用，再替换 workflow 应用：

```bash
docker compose --parallel 1 -p omni-stack build omni-auth omni-base omni-workflow omni-gateway omni-frontend
docker compose -p omni-stack stop omni-crm omni-srm omni-procurement omni-asset xxl-job-admin rocketmq-broker rocketmq-namesrv
docker compose -p omni-stack up -d --no-build --no-deps --force-recreate omni-auth omni-base omni-workflow omni-gateway omni-frontend
docker compose -p omni-stack ps
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:3000/
```

停用列表按当前环境实际登记填写；保留 mysql/redis/nacos 与数据，不执行 `down -v`、不清卷。

## 路径二：生成独立 workflow 工程

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create workflow --output generated/learning-workflow
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset validate workflow --output generated/learning-workflow
```

生成物有自身 `scaffold.lock`、Compose、文档与 AI 入口；进入生成物后使用其自身构建，不在原仓库目录启动生成物的 Compose 项目，不运行生成物 migrator 覆盖共享库。

历史早期阶段：G6 结构（8 模块/9 服务）与 G7 生成物后端/前端构建、lint 已通过；交付角色隔离与生成物镜像原位替换未完成。

## 首次使用

1. 打开 `http://localhost:3000`，选择开发租户，输入种子账号与**本次显示的验证码**。
2. 首页展示 Wiki；从首页在独立页签打开“我的工作台”，审批待办与个人任务在同一界面处理。从应用目录以新页签进入工作流模块，按角色检查模型和实例入口。
3. 工作流操作建议顺序：模型设计/校验 → 发布（唯一 model_key）→ 发起流程 → 候选人在待办中处理 → 查看流程实例与历史。
4. 若待办为空，先确认当前身份是否在候选人范围、流程是否已发布、任务是否已创建；不要直接改任务表。

## 成功判据

- workflow 闭包应用服务 healthy，migrator 按预期退出。
- 前端 3000 返回 200。
- 真实验证码登录成功，workflow 菜单/待办入口按权限出现。
- 已发布模型可发起流程，候选人能在待办中看到并处理任务。
- 没有新增项目、网络、卷或端口偏移；共享数据未清空或重放迁移。

**菜单边界**：共享 full 库可能保留 CRM/SRM/采购/资产权限码；这是共享数据状态，不是 workflow 闭包包含这些业务。要验证“workflow 无其他业务菜单”，需用 workflow 种子初始化的独立空库/租户。

## 停止与恢复

- 只停止本轮启动的应用（按容器名或明确服务），保留 mysql/redis/nacos 与数据卷。
- 恢复：`docker start` 登记的原服务集合，或对明确服务 `up -d --no-build --no-deps`；禁止 `down -v`、清卷、全局 prune。
- 替换失败时用替换前记录的旧镜像在同一服务位置恢复，不启动旁路服务。

## 常见失败

| 现象 | 先检查 | 不要做 |
|---|---|---|
| 端口冲突 | 8103/3000/8100/8101/8102/3306/6379/8080/8848/9848 | 改闭包或随机偏移端口 |
| Nacos 认证失败 | `.env` 与 Nacos 凭据是否一致 | 关闭认证或写入示例秘密 |
| 模型发布失败 | model_key 是否租户内唯一、是否匹配 BPMN process id、XML 是否通过校验 | 直接写数据库绕过校验 |
| 待办为空 | 候选人扩展、角色、任务是否创建、流程是否发布 | 直接改任务/候选人表 |
| 网关 503 | 断言密钥、下游注册、路由配置 | 只改前端地址 |
| 菜单加载失败 | auth/base/workflow/gateway 健康、Nacos、`/api/auth/menus` | 直接改菜单表 |

## 历史阶段记录与未验证边界

- **已验证**：workflow 闭包 8 模块/9 服务/18 端口；5 个应用镜像构建并原位替换；auth/base/workflow/gateway healthy、前端 200；生成物 G6/G7 构建通过。
- **未验证**：workflow 真实验证码登录（本轮两次目视验证码失败）；workflow 菜单/待办/审批真实操作；干净空库首次安装；无业务菜单；生成物镜像替换与交付角色隔离；定时任务（workflow 不含 xxl-job）。

继续阅读[预设总览](presets-overview.md)或[工作流事实文档](../workflow.md)。
