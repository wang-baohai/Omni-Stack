# Omni-Stack

基于 Spring Boot 4 + Vue 3 的微服务脚手架，用于有租户、权限、审批和业务域需求的企业后台。源码、工程约束、操作教程和可复用 AI 工作流程随项目交付。

[English](README.en.md) · [日本語](README.jp.md) · [한국어](README.kr.md)

## 重点能力

- 租户隔离、功能权限与数据范围；验证码、社交登录和 OAuth2 授权流程。
<!-- omni:modules:workflow -->
- 独立 Workflow 服务提供 BPMN 建模、发布、审批和会签。
<!-- omni:modules:end -->
<!-- omni:modules:xxl-job -->
- XXL-JOB 支持系统与个人任务。
<!-- omni:modules:end -->
<!-- omni:modules:crm -->
- CRM 销售链路。
<!-- omni:modules:end -->
<!-- omni:modules:srm,procurement,asset -->
- SRM → Procurement → Asset 的供应商、采购和资产协作。
<!-- omni:modules:end -->
- 事务 Outbox、幂等消费、死信处理，以及可选的指标、追踪和日志栈。
- `tools/omni-cli` 生成服务与 CRUD，并按预设生成独立裁剪工程。
- [AGENTS.md](AGENTS.md) 与工程事实约束执行，[AI 资产](ai/README.md)提供可复用工具入口。

主要技术：Spring Boot 4.0.6、JDK 25、Spring Cloud、Vue 3、TypeScript、Vite、Element Plus。准确依赖版本以 POM 和 npm 锁文件为准。

## 最短体验

先按[首次启动教程](docs/guides/quick-start.md)获取代码，准备本机 Docker，并在 `.env` 填好全部必填秘密。JDK 和 Node 由构建容器提供。

```bash
docker compose --profile full up -d --build
docker compose ps
```

前端：`http://localhost:3000`。按教程选择开发租户与种子账号，人工输入本次验证码完成登录。只体验部分业务时先选择预设；结构迁移与基础种子由 migrator 管理，详细数据入口见 [Wiki](docs/wiki/data-and-fixtures.md)。

<!-- omni:preset-table:start -->
`core` · `crm` · `full` · `supply-chain` · `workflow`

[选择指南](docs/preset-quick-selection.md) · [预设依赖矩阵](docs/preset-dependency-matrix.md)
<!-- omni:preset-table:end -->

## 文档与贡献

- [项目 Wiki](docs/wiki/index.md)：从获取代码到业务操作、源码调试、扩展和运维。
- [架构与工程事实](docs/architecture.md)、[API 契约](docs/api-contract.md)：维护者的当前行为基线。
- [独立文档站](docs/wiki/site-maintenance.md)：Docker 运行、离线搜索和静态发布说明。
- [仓库维护规则](docs/repository-governance.md)、[变更记录](CHANGELOG.md)、[架构决策](docs/adr/0001-scaffold-upgrade-foundations.md)。

贡献前阅读对应规范并执行受影响的 Docker 验证。问题和 PR 可提交到 [GitHub](https://github.com/wang-baohai/Omni-Stack) 或 [Gitee](https://gitee.com/wang-baohai/Omni-Stack)。

[Apache License 2.0](LICENSE) · © Wang Baohai
