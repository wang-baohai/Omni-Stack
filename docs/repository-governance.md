# 仓库治理、证据与独立验收规则

本文是长期 CONSTRAINTS / QUALITY GATES，定义如何维护与证明事实，不保存滚动进度。完整交付契约见 [MASTER DELIVERY GOAL](scaffold-upgrade-plan.md)。2026-09-05 治理请求确立 Codex = THINK + DESIGN + REVIEW，Qoder = EXECUTE + TEST + FIX + CLEANUP + COLLECT EVIDENCE。

## 1. 决策权与事实裁决

原始用户要求决定 TARGET STATE；当前代码、配置、migration、测试契约与可验证证据决定 CURRENT STATE。按生产代码/配置/migration → 自动化测试与真实契约 → 可复现 CI/build/runtime → DB/API/browser 证据 → 正式文档 → Git 历史 → 历史 Agent 报告裁决。第七类只提供线索；文件名、Agent 自述、提交标题不构成通过证据。事实优先级不能授权降低目标；测试与业务契约冲突必须先审契约。

Codex 设计目标、架构、测试及门禁，独立审查并发出 PASS 或 REWORK_REQUIRED；发现实现问题登记 WP、REWORK 或 ESCALATION，不直接修复。Qoder 可在已明确的 WP 内实现、测试、修复、浏览器验证、截图、证据整理及经清单约束的清理。Qoder 不修改 MASTER GOAL、不减少范围或放宽验收、不删除失败测试、不改测试迎合错误业务、不代签语义复核、不自行宣布 FINAL PASS。输出的准备状态只能为 `READY_FOR_CODEX_REVIEW=true` 或 `READY_FOR_CODEX_REVIEW=false`，另附事实与证据。

## 2. 唯一事实源设计

| 事实领域 | Canonical Source | 维护责任 / 派生规则 |
|---|---|---|
| 完整交付目标、最终 DoD | `docs/scaffold-upgrade-plan.md` | Codex；本次原位收敛，历史版本由 Git 保留 |
| 治理、Evidence、保护、清理与验收权 | 本文 | Codex；Qoder 可提出修订，不自行降门 |
| 系统边界、数据流 | `docs/architecture.md`、`docs/core-flows.md` | 前者拥有组件边界，后者拥有端到端时序，互链不复制状态 |
| API、错误码、金额与日期表示 | `docs/api-contract.md` 与当前 DTO/Controller | 前者为公共契约说明，源码为实现事实；冲突登记处理 |
| 开发规则 | `AGENTS.md`、`docs/backend-patterns.md`、`docs/frontend-patterns.md` | AGENTS 只拥有执行约束；pattern 只拥有实现惯例 |
| 决策及兼容边界 | `docs/adr/0001-scaffold-upgrade-foundations.md`；后续 ADR | Decision / Reason / Impact / Constraint，不保存 Agent 对话 |
| 业务规则 | `docs/workflow.md`、`docs/scheduling.md`、`docs/crm.md`、`docs/design/*-design.md` | 模块责任人；用户操作步骤归 `docs/guides/`，不得另造状态机规范 |
| 模块和预设组合 | `scaffold/catalog/modules.yaml`、`scaffold/presets/` | CLI 维护；预设依赖矩阵由此派生，不手改生成结果 |
| 数据结构、接管与种子 | `database/changelog/`、`database/adoption/`、`database/seed/manifest.yaml`、`scripts/sql/seed/` | migration 拥有结构；adoption 拥有指定旧基线指纹；seed 文件拥有数据，manifest 拥有 digest/断言 |
| 可复现质量资产 | `omni-backend/**/src/test/`、`omni-frontend/e2e/`、`omni-frontend/e2e-docs/`、`tools/omni-cli/test/` | 测试为 executable fact；修复能力保留，可因确证契约缺陷修改 |
| 实际门禁入口 | `.github/workflows/quality.yml`、两个 `package.json`、Maven wrapper/POM、`tools/omni-cli/scripts/` | CI 是执行实现，MASTER 的验收标准不可被较弱脚本替代 |
| 文档语言范围、源摘要与复核签核 | `docs/docs-manifest.yaml` | `docs/i18n-review-queue.md` 只作派生队列；不可成为第二套状态源 |
| 截图场景与覆盖 | `omni-frontend/e2e-docs/screenshot-manifest.yaml`、`screenshot-coverage.yaml` | manifest 拥有单图身份/操作/来源；coverage 拥有必需流程/状态映射，两者不能凭数量互证 |
| 运行/部署/运维 | `docs/docker-deployment.md`、`docs/guides/quick-start.md`、`docs/observability.md`；Compose/docker/observability 配置 | 文档解释，配置定义行为；运行证据独立记录 |
| 当前交付候选状态 | `docs/evidence/project-consolidation/current-state.md` | 临时唯一对账入口，只链接上述事实源与证据，不复制其实现规范 |
| 活动 WP / Qoder 恢复入口 | 同目录 `work-packages.md` / `qoder-handoff.md` | 前者拥有 WP 状态，后者只拥有执行顺序，不另写一份滚动进度 |

历史 implementation-plan、audit、handoff、checkpoint、execution report 不能继续作为当前执行入口。清理前将唯一有效约束归入以上文件，保留引用与 Git SHA。中文为文档源；英/日/韩为经审核的派生说明，不能自行改变 API/安全/状态机。既有四个 Chinese-only 规划/审查条目不要求伪造三语译文；长期对外指南仍须四语。

## 3. 状态与证据记录

MASTER 子目标、WP、Finding 只用：`VERIFIED`（实现且有当前可信证据）、`IMPLEMENTED_NOT_VERIFIED`（有实现无充分验证）、`IN_PROGRESS`、`NOT_STARTED`、`BLOCKED`（具体客观阻断）、`DEFERRED`（有明确后置决定）、`REWORK_REQUIRED`、`REJECTED`。验收结果另用 PASS / FAIL / BLOCKED，不把它混成实施状态。未运行写为证据字段 `execution=not_run`，不能写 VERIFIED。没有预定义权重，不报告完成百分比。

每条证据必须有：ID、主张、类型、范围、源 HEAD、候选 working-tree digest/文件 SHA、工具版本、命令与 cwd、执行时间及时区、环境/服务镜像 ID 或摘要、预期与实际、exit code、通过/失败/跳过数及原因、关联测试/路径、脱敏方法、复核人、失效条件。无真实记录的字段写明 unavailable/原因，不填推测值。旧证据复用须说明相关代码/配置/fixture/依赖/运行镜像没有变化；否则降为历史线索。

| 证据类型 | 可以证明 | 不能替代 |
|---|---|---|
| Static Evidence | 文件/配置/契约、引用、摘要、确定性分支 | 启动、数据库事务、E2E |
| Test Evidence | 指定树、指定环境下实际执行的断言 | 未执行/skip 场景、真实外部依赖 |
| Runtime Evidence | 指定镜像的健康、API、浏览器行为 | HEAD 一致性、全业务正确 |
| Visual Evidence | 指定图的可见状态与人工检查 | 后端副作用、完整流程覆盖、真实登录 |
| Database Evidence | 指定租户/资源的结构、数据、事务与副作用 | 未查询表、其他时间点/环境 |
| Git Evidence | HEAD、diff、文件身份、提交关系 | CI 或运行验收 |
| Remote Evidence | 指定时间的目标远端 SHA/发布记录 | 产品 PASS |

Build PASS ≠ E2E PASS；E2E PASS ≠ screenshot coverage PASS；allow-draft ≠ strict；文件删除 ≠ Trace 脱敏；有效业务行零 ≠ Workflow/Outbox/audit 无副作用；Remote SHA 相同 ≠ 发布验收。代码/测试/环境/证据缺一不补造结论。

## 4. 测试、浏览器与截图规则

顺序：证据 → 契约审查 → 最小修正 → targeted 测试 → 受影响回归 → 最终候选统一门禁。两轮不能稳定收敛即升级；任何情况下 targeted debug 最多三轮，每轮必须有新证据。禁止无证据重跑、加 timeout 或循环试错。修复缺陷先写失败触发和应有契约，不能只列文件改动。

测试分 unit、带真实 DB/MQ 的 integration、DTO/SQL/Feign/迁移标签等 contract、断言式 E2E、独立文档视觉套件。名字含 Integration/Concurrency 不证明用了真实数据库；Mockito 交互断言保留但仅登记它真正验证的层次。运行必须记录 required tests 的 expected/executed/pass/fail/skip；必需场景 skip 不能验收。构建 tsconfig 未包含 E2E 时需要单独 TS 检查。

真实 E2E 使用隔离环境短期身份，不绕过 CAPTCHA、不新增生产后门。公开展示 mock、接口故障注入可以验证 UI 行为，但必须逐场景登记 `DISPLAY_FIXTURE` / `CLIENT_FAULT_INJECTION`，不能标为真实服务/社交登录/设备授权完成。断言闭环需真实 API、服务与 DB。身份必须属于该环境的 issuer/key/tenant/RBAC；静态 Secret 注入本身不证明身份可用。

所有写入型 fixture 先记录 tenant、runId、准确业务键与计划副作用，取得 ID 后立即登记。并发/超时产生不确定提交时停止后续写入。清理通过正式 API、乐观锁 version、业务终态处理，追踪 workflow instance/task/model、inbox/outbox、角色 Saga、XXL-JOB、audit。不得 SQL 批量硬删审计历史或修改种子掩盖残留。故障注入只在已核实的隔离 Compose project/端口/卷进行；没有确定边界时对应 WP BLOCKED。

OFFICIAL 图要求：稳定场景/步骤/角色/路由/语言/视口、fixture、实际操作和预期、源码/运行身份、用例、图文件 digest、生成时间、遮罩和人工视觉审核、对应 coverage 状态。默认桌面 1440×900、移动 390×844，关键业务表单补平板；所有主要流程覆盖入口、填写、提交、处理中、成功与典型失败，不适用状态必须写业务理由并由 Codex 批准。四语言均需内容自然、无截断、无未加载画布/遮罩、无凭证/真实 PII。生成图片不自动升级 OFFICIAL；失败/调试图片不能凑 coverage。`docs/images/` 位置和 `covered` 标签都不证明当前版本视觉验收。

## 5. 资产保护与清理

分类只能为 KEEP_CANONICAL、KEEP_ENGINEERING_ASSET、KEEP_FINAL_EVIDENCE、MERGE_THEN_DELETE、REFACTOR_THEN_DELETE、DELETE_AFTER_VALIDATION、DEFER_CLEANUP、PROTECTED。推荐操作只能为 KEEP、MERGE_THEN_DELETE、REFACTOR_THEN_DELETE、DELETE、DEFER。PROTECTED 不等于实现已经正确：分别记录保护理由和验证范围。

生产代码、不可变 migration、正式 seed、构建/部署/CI、E2E、fixtures、setup/teardown、认证/截图/browser helper、测试工具、quality gate、文档/i18n/security checker、visual baseline 都受保护。临时起源不影响可复现测试的长期资产身份。Verified 实现不得因风格重写；重新打开须有新的确定性证据、受影响契约及最小 WP。

每项 cleanup 必须登记 Path、Artifact Type、Current Purpose、Referenced By、Classification、Information To Preserve、Canonical Destination、Recommended Action、Delete Preconditions、Risk，另保留 git 状态、SHA256 与引用扫描范围。本轮 [cleanup-manifest.csv](evidence/project-consolidation/cleanup-manifest.csv) 是快照，不是批量删除命令。

删除前重扫 CI、package scripts、build、production、test、docs、其他脚本七类引用；覆盖显式路径/相对路径/文件名、glob/import、classpath、反射/目录发现、shell 动态拼接和未跟踪本地入口。静态零命中不能证明无动态依赖。新文件/改动 digest/未解释引用使旧清理结论失效。先验证替代物，再更新调用者，再逐项删除及 diff/链接检查，最后从新 checkout 跑 fresh/upgrade/预设/E2E/安全回归。禁止 `git clean`、`reset --hard`、对目录盲删、仅按临时文件名判断。

Trace/HAR/browser profile/log/DB dump 按敏感运行证据管理。原件当前保留受限，禁止直接提交/上传；先形成不含 Authorization/Cookie/token/session/PII 的正式摘要，复核保全需要及保留期限，再由 Qoder 执行获准处置。令牌过期不是内容脱敏。真实 `.env`/备份/local 配置只登记路径与存在性，不读取或复制内容进治理包。

最终结构：代码/测试/工具保留长期能力；docs 保留事实、约束、决策和指南；图片保留正式图与必要 visual baseline；历史过程交由 Git。交付通过后，当前状态、WP、Handoff、执行日志、临时 Review/索引/CSV 也必须收敛删除。终审将“运行产品候选”与“仅移除已归并临时治理文件的封存候选”分开：先审前者，再对封存 diff 做范围/链接/摘要审查，源代码/测试/配置/正式文档实质变化则重新验证受影响门，避免删除自己的验收依据或循环重跑全量测试。

## 6. Escalation 协议

架构冲突、业务契约冲突、重大数据模型决定、安全边界不明、环境与设计冲突、需要改目标/降验收、两轮无法收敛，停止对应 WP 并登记 `ESCALATION-xxx`。独立无依赖 WP 可继续。

每条升级必须含 Problem、Observed Evidence、Relevant Contract、Current Implementation、Why Current Plan Cannot Continue、Options、Risk、Decision Needed；记录阻塞 WP、责任角色和解除条件。Codex 决定后回写 canonical 决策，只在该决定覆盖的范围恢复。不把 UNKNOWN 当环境故障，不把环境缺失当产品缺陷。真实备份/外部身份/授权不能由时间流逝、Agent 判断或虚构签名代替。
