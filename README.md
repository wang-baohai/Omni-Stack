# Omni-Stack

基于 Spring Boot 4 + Vue 3 的微服务脚手架，帮助团队搭建具有租户隔离、权限控制和业务扩展能力的企业后台。源码、工程事实、图文教程与 AI 协作规范在同一仓库维护。

[English](README.en.md) · [日本語](README.jp.md) · [한국어](README.kr.md)

## 重点亮点

- 官网直接阅读 Wiki；个人工作台集中展示已授权应用，业务模块在新标签页打开。
- 多租户、功能权限与数据范围；验证码登录、社交登录和 OAuth2 授权。
<!-- omni:modules:workflow -->
- 独立 Workflow 服务支持 BPMN 建模、发布、审批和会签。
<!-- omni:modules:end -->
<!-- omni:modules:xxl-job -->
- XXL-JOB 统一管理系统任务与个人任务。
<!-- omni:modules:end -->
<!-- omni:modules:crm -->
- CRM 覆盖线索、客户、商机与跟进。
<!-- omni:modules:end -->
<!-- omni:modules:srm,procurement,asset -->
- SRM → Procurement → Asset 串联供应商、采购和资产协作。
<!-- omni:modules:end -->
- 事务 Outbox、幂等消费与死信管理；可选的指标、链路和统一日志采集。
- `tools/omni-cli` 按预设裁剪独立工程，并生成服务与 CRUD。
- Harness 以精简工程事实、共享 AI 工作流程和验证证据支持持续开发。

技术基线：Spring Boot 4.0.6、JDK 25、Spring Cloud、Vue 3、TypeScript、Vite、Element Plus。

## 项目结构

| 路径 | 职责与入口 |
|---|---|
| `omni-backend/` | Maven 多模块后端。`omni-backend/pom.xml` 汇总服务与共享模块。 |
| `omni-frontend/` | Vue 3 应用，包含公开首页、个人工作台和业务界面。 |
| `database/` · `scripts/sql/seed/` | Liquibase 迁移位于 `database/changelog/`，正式种子及校验规则由 `database/seed/manifest.yaml` 登记。 |
| `scripts/fixtures/` | 受控测试夹具和截图采集工具。 |
| `scaffold/` · `tools/omni-cli/` | `scaffold/catalog/modules.yaml` 定义模块，`scaffold/presets/` 定义预设，CLI 负责生成工程、服务和 CRUD。 |
| `docker/` · `compose.yaml` | Dockerfile 位于 `docker/`。`compose.yaml` 编排本地服务，`compose.tools.yaml` 提供构建与验证工具。 |
| `observability/` | 可选的指标、链路、日志、仪表盘和告警配置。 |
| `docs/` | 工程事实、四语 Wiki 与 [源码入口索引](docs/reference/source-map.md)。 |
| `AGENTS.md` · `ai/` | 项目执行约定与共享 AI 资产入口。 |
| `.agents/skills/` | Codex 的项目技能入口，包含 `omni-change`（项目变更与审阅）和 `grill-me`（通过追问细化方案）。入口文件由 `ai/skills/` 生成。 |
| `.qoder/skills/` | Qoder 的项目技能入口，提供同一套 `omni-change` 和 `grill-me`，由适配脚本同步。 |
| `.github/workflows/` | 源仓库的 GitHub Actions 配置。`quality.yml` 可由推送、Pull Request、定时或手动触发，检查后端、前端、文档、AI 技能入口、数据库迁移、脚手架和安全。 |

### 后端模块

- `omni-gateway` 负责请求入口和路由，`omni-auth` 负责认证与授权，`omni-base` 提供基础数据和平台管理。
<!-- omni:modules:workflow -->
- `omni-workflow` 负责 BPMN 流程建模、审批和会签。
<!-- omni:modules:end -->
<!-- omni:modules:crm -->
- `omni-crm` 管理线索、客户与商机。
<!-- omni:modules:end -->
<!-- omni:modules:srm,procurement,asset -->
- `omni-srm`、`omni-procurement`、`omni-asset` 分别负责供应商、采购与资产。
<!-- omni:modules:end -->
- `omni-common` 和 `omni-common-*` 提供共享基础能力，`omni-db-migrator` 执行数据库迁移。

### 前端源码入口

- `omni-frontend/src/views/` 放页面，`omni-frontend/src/components/` 放复用组件，`omni-frontend/src/api/` 放后端 API 调用。
- `omni-frontend/src/router/` 管理路由，`omni-frontend/src/stores/` 管理 Pinia 状态，`omni-frontend/src/locales/` 存放界面语言资源。
- `omni-frontend/tests/` 放单元测试，`omni-frontend/e2e/` 和 `omni-frontend/e2e-docs/` 放端到端测试与文档截图场景。

### AI 工具目录

`ai/` 集中维护项目级 AI 资产，`ai/manifest.yaml` 登记来源、版本、许可与适用工具。

- `ai/skills/` 保存 Skill 源文件，包括项目变更流程 `omni-change` 和方案追问 `grill-me`。
- `ai/prompts/` 保存变更审阅与文档同步模板。
- `ai/knowledge/` 记录 Docker 验证等项目经验。
- `ai/adapters/bootstrap.mjs` 从共享源生成并同步 Codex 和 Qoder 的项目技能入口。

修改 Skill 时先更新 `ai/skills/`，再用适配脚本同步工具入口。安装与校验步骤见 [AI 工具说明](ai/README.md)。

<!-- omni:preset-table:start -->
`core` · `crm` · `full` · `supply-chain` · `workflow`

[完整 Wiki：项目介绍、图文使用、启动与开发](docs/wiki/index.md)
<!-- omni:preset-table:end -->

[Apache License 2.0](LICENSE) · © Wang Baohai
