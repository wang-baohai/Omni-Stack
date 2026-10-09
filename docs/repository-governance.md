# 仓库维护与验证规则

保留产品代码、正式测试和 fixture、可复用工具、构建部署配置、CI、不可变迁移与正式种子，以及当前事实、设计和使用维护说明。执行权限和工作范围以当前用户授权及 [AGENTS.md](../AGENTS.md) 为准。已结束的阶段计划、交接和 Agent 报告由 Git 历史保存。

## 事实源

| 领域 | 正式来源 |
|---|---|
| 系统边界与流程 | [架构](architecture.md)、[核心流程](core-flows.md) |
| API、错误码、日期与金额表示 | [API 契约](api-contract.md)、当前 Controller/DTO |
| 开发约束 | [AGENTS.md](../AGENTS.md)、[后端模式](backend-patterns.md)、[前端模式](frontend-patterns.md) |
| 架构决策 | [ADR](adr/0001-scaffold-upgrade-foundations.md) |
| 业务规则 | Workflow、Scheduling、CRM、SRM、Procurement、Asset 的正式设计与操作指南 |
| 模块与预设 | `scaffold/catalog/modules.yaml`、`scaffold/presets/`；预设文档由 CLI 派生 |
| 数据结构与接管 | `database/changelog/`、`database/adoption/` |
| 种子与断言 | `scripts/sql/seed/`、`database/seed/manifest.yaml` |
| 构建与质量门 | `.github/workflows/quality.yml`、Maven Wrapper/POM、前端和 CLI 的 package scripts |
| 文档语言与复核状态 | `docs/docs-manifest.yaml`；`i18n-review-queue.md` 是派生队列 |
| 截图场景与覆盖 | `omni-frontend/e2e-docs/screenshot-manifest.yaml`、`screenshot-coverage.yaml` |
| 本地运行 | 正式 Compose、Dockerfile、[部署说明](docker-deployment.md)、[快速开始](guides/quick-start.md) |

源码和配置定义当前行为；文档解释边界。历史报告只提供线索，不能代替当前运行证据。中文是文档事实源，其他语言不得改变公共契约或安全规则。

## 验证与证据

- 所有应用和业务验证在本机 Docker 内执行。标准项目为 `omni-stack`，端口使用 AGENTS 和正式 Compose 默认值；不通过临时覆盖绕过冲突。
- 后端修改使用 JDK 25 与 Maven Wrapper 执行 `clean install`；前端修改执行 build、lint，涉及 E2E 源码时单独检查其 TypeScript。
- 缺陷按触发证据、根因、最小修复、针对性验证、必要回归闭环。已通过且未发生相关变化的检查不反复重开。
- 证据注明源 HEAD、相关文件摘要、工具版本、命令、时间、运行镜像身份、预期与实际、退出码及通过/失败/跳过数。未知事实注明原因，不填推测值。
- Build、单元测试、真实 DB/MQ 集成、E2E 和截图覆盖分别说明。未运行、失败、跳过和草稿不能写为通过；生成图片不代表完成视觉复核。
- 真实登录必须通过一次性验证码。受控测试身份只由测试 classpath 的 fixture 生成，不新增生产认证后门。令牌和 Cookie 不进入报告、截图或日志。
- 写入测试先明确租户、业务键和目标环境。故障注入必须核实隔离边界；不确定提交时停止写入并核对副作用。
- 数据库 changeSet 保持 forward-only。不得修改已执行迁移、删除失败测试、放宽断言或伪造翻译人工签核来取得绿色结果。

## 资产保护与清理

生产代码、正式测试、fixtures、setup/teardown、认证和截图 helper、质量检查工具、迁移、种子、CI、正式图片与必要视觉基线均需保留。临时起源不影响长期价值。

清理前保全未提交和未跟踪的有效改动，登记目标路径、用途、摘要和引用。检查 CI、package scripts、构建、生产代码、测试、文档及其他脚本的引用，包括动态拼接与目录发现。唯一有效内容先迁入正式位置，再更新调用者和链接，最后删除原件并验证替代路径。

禁止 `git clean`、`reset --hard`、对目录盲删或只凭文件名判断。代码、配置或契约改变时重跑受影响检查；只删除已归并过程记录时检查引用与最终差异。

真实 `.env`、Trace、HAR、数据库导出和运行日志属于本地敏感资产。最终事实报告只保留脱敏结论，不提交凭据、原始业务数据或临时认证材料。阶段计划、状态页、日志、探针、临时 Compose 与生成输出不作为最终运行依赖。
