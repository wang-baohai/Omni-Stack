# 使用 Harness 做二次开发：一个小任务的完整走法

本文用一个任务把 Harness 的五部分串起来：**在 supply-chain 学习工程中，用 CRUD 生成器为“物料品牌”生成一套可运行代码，并验证权限、菜单和构建。** 任务很小，但走法可以复用到其他业务对象。

下面命令来自[脚手架开发指南](../guides/scaffold-development.md)。本文不声称这些步骤已在你的环境执行过；每一步都给出预期结果和失败时应记录什么。

## 第 0 步：先确定边界

在动手前先回答：

- 目标预设：`supply-chain`（包含 SRM、采购、资产）。
- 生成位置：仓库内 `generated/learning-supply-chain`，不污染主工程。
- 不改共享数据库，不运行生成物的 migrator 或种子。
- 完成标准：后端构建、前端 lint/build、生成物自身校验通过；权限与菜单入口有实际证据。

这就是上下文和约束。没有它们，生成器可能把代码写到错误位置，或在本机起第二套环境。

## 第 1 步：读取当前事实

先读三处，而不是直接让模型改代码：

1. 根 [AGENTS.md](../../AGENTS.md) 的硬约束和完成清单；
2. [架构与工程事实](../architecture.md) 中 supply-chain 的模块边界；
3. [采购设计](../design/procurement-design.md) 中物料目录的表、权限和数据范围规则。

读完后应能说出：物料表属于哪个服务、租户拦截范围是什么、写操作需要什么权限码、失败时返回什么。说不出来就继续读，不要开始生成。

## 第 2 步：生成学习工程

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create supply-chain --output generated/learning-supply-chain
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset validate supply-chain --output generated/learning-supply-chain
```

预期：第一条输出 `preset created: supply-chain@...`，第二条输出 `generated preset valid`。如果失败，先记录完整输出和退出码；不要删除目录后反复重试，先确认是模板、网络还是磁盘问题。

生成物有自己的 `scaffold.lock`、Compose 和 AI 入口。后续命令都在生成物目录内执行，不要依赖原仓库的绝对路径。

## 第 3 步：预览生成器会改什么

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- generate crud --spec scaffold/specs/material-brand.yaml --check
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- generate crud --spec scaffold/specs/material-brand.yaml --dry-run
```

`--check` 只校验输入，`--dry-run` 列出计划改动。此时先看三件事：

- 是否只改预期模块，没有碰其他业务；
- 是否包含权限码、菜单、迁移和前端入口；
- 是否出现覆盖已有文件的警告。

预览不是形式。若计划里出现共享文件被重写，应先停下核对模板，而不是用 `--apply` 强行覆盖。

## 第 4 步：执行并验证

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- generate crud --spec scaffold/specs/material-brand.yaml --apply
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install -pl omni-procurement -am
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run lint
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run build
```

预期：生成器完成并列出改动；Maven reactor 成功；前端 lint 与 build 通过。失败时记录：

- 命令、工作目录、退出码；
- 第一条真正的错误，而不是最后一行汇总；
- 是否由生成模板、依赖下载还是本机环境导致；
- 已经产生了哪些文件，是否需要回退。

不要用“再跑一次”代替根因判断。写操作结果不确定时，先检查文件是否已经生成，再决定是否重试。

## 第 5 步：验证权限与入口

构建通过只证明代码能编译。还要验证：

1. 后端写操作是否声明了 `resource:action` 权限码；
2. 权限种子和前端 `v-permission` 是否一致；
3. 菜单/路由是否只在具备权限时出现；
4. 租户和数据范围是否仍按模块规则生效；
5. 真实登录后，目标租户能看到正确入口，越权请求被拒绝。

这一步需要真实运行环境和身份，不能只看源码。没有条件时，明确记录“未验证”，不要把构建成功写成功能通过。

## 第 6 步：留下反馈与断点

完成后至少记录：

- 改了哪些对象，分别属于哪个模块；
- 哪些检查通过，证据在哪里；
- 哪些没有验证，原因是什么；
- 下一步是谁、做什么。

如果是长任务，还应保留当前步骤、失败日志位置和恢复入口。断点不是给“未来有空再看”的备注，而是让下一次会话不用重新摸索的最小信息。

## 用 AI 做一次真实改动：需求、事实、边界与反馈

上面的六步是“跟着生成器走”。真实二开更多是**让 AI 在现有工程里做一次受控改动**。本节沿用同一个主例——为工作台接入模块入口——说明怎样把需求、事实、边界和验证交给 AI，而不是只丢一句“帮我开发”。

### 需求表达：先写清“谁在什么条件下看到什么”

一份可执行的需求至少包含四段：

```text
角色：登录后的管理员/员工
入口：工作台应用目录、全局应用抽屉、顶栏菜单搜索
行为：点击模块在新标签打开；模块内部导航留在当前标签
边界：入口只能来自当前权限可进入的页面；链接不含令牌；四语与窄屏可用
验收：卡片/抽屉/搜索指向同一目标；无权限深链落 403；构建、单测、结构门通过
```

不要写“优化一下工作台”这类无法验收的句子。**验收条件先于实现**，否则 AI 只能猜。

### 影响分析：先判断会不会碰到权限、租户与数据

在动手前让 AI 回答四个问题：

| 问题 | 主例答案 | 若答案为“会” |
| --- | --- | --- |
| 是否新增权限码 | 否，入口沿用既有菜单权限 | 需改 `sys_permission` 种子与 `v-permission` |
| 是否新增租户表或字段 | 否 | 需前向 Liquibase changeset 与种子校验 |
| 是否改后端接口 | 否，只消费现有菜单与统计接口 | 需同步 API 契约与测试 |
| 是否改生成器闭包 | 否，只登记新页面归属 | 需改 catalog 与裁剪逻辑 |

主例中**没有**权限、租户或迁移变更；如果读者照抄模板却需要这些变更，应把它们作为独立任务先做，而不是夹在 UI 改动里。

### 事实选择：给 AI 可核对的文件，而不是印象

按“越接近改动点越优先”的顺序提供：

1. 目标页面与组件源码（`ConsoleHeader.vue`、`ApplicationDrawer.vue`、`WorkspaceEntryList.vue`、`layout/index.vue`）；
2. 派生逻辑（`useApplicationNavigation`、路由 meta）；
3. 约束文档（`docs/frontend-patterns.md`、`docs/architecture.md` 的权限章节）；
4. 现有测试与检查命令（`tests/navigation/*`、`package.json` scripts）。

要求 AI 在回答中引用 `file:line`，并区分“已核实事实”和“推测”。推测必须标注，不能写进实现。

### 文件边界：先说不能改什么

主例的边界：

- **允许**：工作台、顶栏、抽屉、入口列表、模块布局、对应词条与测试、`scaffold/catalog/modules.yaml` 的归属；
- **禁止**：后端业务代码、数据库迁移、正式种子、权限模型、生成器闭包；
- **需要授权才做**：质量脚本、扫描器、README 等跨 WP 文件。

AI 一旦提出越界改动，先停下来确认，而不是“顺手修好”。

### 实施反馈：每一步都给可复核的结果

主例的真实过程是一个闭环：

1. 先构建并截图，发现 CRM 主区被挤到 x=1038；
2. 用 DOM 尺寸定位到 `ConsoleHeader` 与 `el-main` 同排，修 `direction="vertical"`；
3. 复测 1280–1920 五个宽度，主区均从 x=220 开始；
4. 四语截图又发现英文译名与逐字母断行，再做两处最小修复；
5. 最后跑 G1/G2/G6，并把 G3 的既有失败单独登记。

每一步都保留“输入 → 命令 → 输出 → 判断”。**没有输出的步骤不算完成。**

### 验证约定：把门禁写进任务，而不是事后补

| 类型 | 主例使用 | 说明 |
| --- | --- | --- |
| 构建/静态 | `npm run build`、`npm run lint` | 失败即停，不跳过 |
| 单元/类型 | `npm run test:unit`、`npm run typecheck:e2e` | 覆盖导航派生与页面行为 |
| 结构门 | 五预设 structure | 证明裁剪后无死链 |
| 运行态 | 真实登录 + 容器截图 | 证明用户可见结果 |
| 既有缺陷 | G3 README 负例 | 单独登记，不混入本步 PASS |

失败后的下一步也要写：构建失败看首个错误与最近 diff；运行失败先查镜像/端口/登录；结构门失败看是否残留未登记页面。**不能靠重跑碰运气。**

### 何时需要权限或迁移变更

- 新增可访问页面或按钮：先加权限码与种子，再加前端 `v-permission`；
- 新增租户数据表/列：先写前向 changeset，再改实体与查询，最后刷新种子清单；
- 只改展示与导航：**不产生**权限或迁移变更，主例属于这一类。

把“本次未发生”写清楚，和“已经做了”同样重要。

## 常见失败与处理

| 现象 | 先检查 | 不要做 |
|---|---|---|
| 生成器提示路径不存在 | 当前目录、预设输出位置、模板是否完整 | 在仓库外临时目录重生成来绕开 |
| Maven 找不到模块 | 生成物是否包含该模块、父 POM 是否更新 | 手改父 POM 掩盖模板问题 |
| 前端类型错误 | 生成模板与当前组件 API 是否一致 | 关闭类型检查 |
| 权限入口不出现 | 权限码、角色、菜单种子、租户 | 直接改数据库加菜单 |
| 真实登录失败 | 验证码、租户、账号、网关和服务健康 | 用测试后门绕过登录 |

## 走完之后

你应该能回答三个问题：这个任务需要哪些事实、哪些工具和哪些检查；失败时证据在哪里；下一次如何继续。三个问题都能回答，才算真正用上了 Harness，而不只是运行了一个生成器。

回到[Harness 是什么](harness-what-and-why.md)或[事实维护](harness-fact-maintenance.md)补齐概念。
