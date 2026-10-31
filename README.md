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

<!-- omni:preset-table:start -->
`core` · `crm` · `full` · `supply-chain` · `workflow`

[完整 Wiki：项目介绍、图文使用、启动与开发](docs/wiki/index.md)
<!-- omni:preset-table:end -->

[Apache License 2.0](LICENSE) · © Wang Baohai
