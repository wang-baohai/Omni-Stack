# ADR-0001：高效率脚手架升级基础决策

- 状态：Accepted
- 决策日期：2026-08-20
- 适用基线：`09a29fe10af9c7ddffe5001238d048947868dc98`
- 决策来源：用户确认依据 `docs/scaffold-upgrade-implementation-plan.md` 直接开始实施
- 复审门：每项决策对应工作包开始前；如事实冲突，只能新增 ADR 替代，不能静默偏离

## 背景

Omni-Stack 已经具备完整的 Spring Boot 4 + Vue 3 微服务业务基线，但数据库初始化、服务生成、模块裁剪、观测、文档和 UI 业务化仍缺少统一工程能力。详细实施计划将升级拆分为可测试、可回滚的工作包；本 ADR 固化开始实施所需的 D-01～D-09。

## 决策

| ID | 已接受决策 | 关键约束 |
|---|---|---|
| D-01 | CLI 使用 Node.js 22 + TypeScript、Commander、YAML、JSON Schema/Ajv 和 Handlebars | CLI 必须可测试、可 dry-run、可重复执行，禁止静默覆盖 |
| D-02 | 新增 `omni-common-service` 组合 Starter | 只组合 Servlet 业务服务横切能力；Gateway 保持 reactive 专用链，Auth 保留授权服务器适配层 |
| D-03 | `scaffold/catalog/modules.yaml` 是模块组合单一事实来源 | preset 只引用模块 ID，不复制依赖关系 |
| D-04 | 结构版本使用 Liquibase YAML；新增一次性 `omni-db-migrator` | 最终 `.sql` 只允许位于 `scripts/sql/seed` 且只含幂等种子数据；结构、vendor、修复迁入不可变 changeSet |
| D-05 | 文档截图使用独立 Playwright 配置和隔离测试环境 | 登录后状态用短期测试 Token；不破解或绕过 CAPTCHA，不新增生产后门 |
| D-06 | 默认观测栈为 Micrometer + OpenTelemetry/OTLP + Prometheus + Alertmanager + Grafana + Tempo + Loki + Alloy | 本地可选启用，生产 Secret 外置，Trace/Metric/Log 可关联 |
| D-07 | preset 默认生成到新目录 | 不原地裁剪当前仓库；未来若需要，必须独立高风险评审 |
| D-08 | 审批规则技术编码由服务端生成 `APR-{ULID}`，创建后不可编辑 | 业务人员只维护规则名称；稳定键不依赖可变名称或语言 |
| D-09 | 请购审批规则只允许选择 Workflow `category=purchase` 的当前已发布版本 | 运行时 `businessType` 保持 `PROCUREMENT_REQUISITION`；category 与 businessType 不混用 |

## 结果

- S0-02 的决策冻结条件成立，后续实现可以按工作包进入详细设计和编码。
- D-04 是数据库改造和最终脚本清理的前置条件，旧 SQL 在替代物通过 fresh/upgrade 验证前不得删除。
- D-05 要求先交付测试范围的受控认证夹具，所有截图和登录态 E2E 才能成为正式证据。
- D-08、D-09 会改变审批规则的展示和约束，但必须兼容既有路由引用；迁移前先输出兼容报告。

## 被否决的替代方案

- 继续以一个超大 `init-all.sql` 兼任结构和种子事实源：无法可靠升级已有环境，也无法满足最终 SQL 清理约束。
- 在当前仓库原地删除未选模块：回滚面过大，容易破坏用户已有定制。
- 用规则名称或可编辑编码作为审批规则稳定键：改名、多语言和历史引用会产生歧义。
- 自动填写 CAPTCHA 或开放测试登录生产端点：扩大安全边界，不能作为文档自动化基础。
