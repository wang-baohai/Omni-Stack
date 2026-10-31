# Omni-Stack 项目文档

这里介绍 Omni-Stack 是什么、怎样使用和启动，以及怎样在现有工程上继续开发。左侧先安排项目与使用入门，再安排开发方法和关键技术；“工程事实”放在启动教程之后，方便随时核对精确规则。

[English](index.en.md) · [日本語](index.jp.md) · [한국어](index.kr.md) · [项目简介与亮点](../../README.md)

## 开始阅读

| 章节 | 主要内容 |
|---|---|
| [项目介绍](../../README.md) | 项目定位与亮点；目录职责见下方 |
| [全功能图文使用教程](usage-platform.md) | 结合截图认识首页、工作台和各业务模块 |
| [Harness 思想与工程实践](harness-what-and-why.md) | 理解 Harness，以及工程事实如何帮助 AI 开发 |
| [预设选择、构建与启动](presets-overview.md) | 先理解预设，再按所选预设完成启动 |
| [工程事实](../architecture.md) | 核对系统边界、接口、开发模式和各领域当前规则 |
| [使用 Harness 开展二次开发](harness-secondary-development.md) | 从一个具体任务走完开发和验证过程 |
| [common 公共支撑能力](common-core.md) | 了解公共工具为什么存在、怎样使用和扩展 |
| [多租户](tenancy-principles.md) | 从不同公司的数据如何分开，读到框架拦截、权限和后台任务 |
| [工作流](workflow-principles.md) | 从一张申请怎样审批，读到流程引擎、模型、候选人、任务与消息 |
| [统一日志采集](logging-principles.md) | 从运行日志、操作日志和安全审计的区别，读到采集、查询与扩展 |
| [统一MQ管理](mq-principles.md) | 从消息为什么异步处理，读到Outbox、重试、幂等与管理恢复 |
| [分布式定时任务（XXL-JOB）](distributed-jobs-principles.md) | 从时间表与幂等讲起，读到框架、工程双轨和真实调度网页 |
| [认证授权](authentication-principles.md) | 从“我是谁、能做什么”讲起，再读方案选择、框架源码和登录实践 |
| [可观测：指标、日志、链路与告警](observability-principles.md) | 从“系统为什么慢”讲起，逐步读懂工具分工、实际配置和排障方法 |

只想先了解界面，可以从图文使用教程开始；准备在本机运行，先看预设选择。教程中的截图和业务号只是示例，实际结果以本次运行返回的 ID 为准。

## 目录职责

| 目录 | 负责什么 | 什么时候进入 |
|---|---|---|
| `omni-backend/` | Spring Boot 微服务与 common 模块 | 看后端实现、鉴权、租户、工作流和业务域 |
| `omni-frontend/` | Vue 单应用（官网、工作台、控制台、门户） | 看界面、路由、权限与前端测试 |
| `scaffold/` | 模块目录与五种预设的单一事实源 | 选择或裁剪预设、确认模块依赖关系 |
| `database/` | Liquibase 迁移与种子断言清单 | 核对表结构、种子和迁移状态 |
| `docker/` | 应用、工具和文档镜像的构建文件 | 构建或部署镜像 |
| `docs/` | 事实文档、指南、Wiki 与文档质量脚本 | 查契约、教程和发布状态 |
| `ai/` | 共享 AI 资产的规范源与派生 adapter | 使用或维护 AI 工作流 |
| `tools/` | CLI、预设生成、文档与质量脚本 | 生成预设、跑质量门或文档检查 |
| `observability/` | 指标、追踪、日志和告警配置 | 配置本地可观测栈 |
| `scripts/` | 正式 SQL 种子、fixture 与运维脚本 | 初始化数据或运行受控 fixture |

目录表只说明职责和使用时机；具体类、接口和数据流以对应事实文档与源码为准。

## 仓库关系

源码以 [Gitee](https://gitee.com/wang-baohai/Omni-Stack) 为 `origin`（fetch/push），[GitHub](https://github.com/wang-baohai/Omni-Stack) 是推送远端（push）。Wiki 正文来自本仓库 `docs/`，站点由清单生成，不维护第二份正文。中文是内容基准，其他语言的复核状态以 [文档清单](../docs-manifest.yaml) 为准；站点能构建不代表译文已经复核。

## 工程参考

需要精确核对系统边界、请求流转或接口格式时，阅读[系统架构](../architecture.md)、[核心流程](../core-flows.md)和 [API 契约](../api-contract.md)。这些文档维护工程规则，具体操作仍以对应的图文教程为入口；历史验收记录不能代替当前运行验证。
