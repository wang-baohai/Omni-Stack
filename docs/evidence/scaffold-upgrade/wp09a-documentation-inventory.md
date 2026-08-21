# WP-09A 文档与截图事实盘点

日期：2026-08-21  
分支：`codex/scaffold-upgrade`

## 结论

已建立可机器读取的文档与截图覆盖基线。该基线只陈述当前事实：已有翻译统一标记为
`present-unverified`，缺失翻译标记为 `missing`，没有把“文件存在”误判为“已按当前中文事实源复核”。

## 文档清单

- `docs/docs-manifest.yaml` 收录 19 份正式中文事实源。
- 11 份事实源具有英、日、韩文件，共 33 份现存翻译，当前均待人工复核。
- 8 份事实源缺少三种翻译，共 24 个明确缺口，包括四份领域设计、两份审查记录和两份升级计划。
- 每份事实源记录 SHA-256、文档类型、责任模块、维护责任人和代码/API/权限/截图依赖。
- ADR 与实施证据暂列为 source-only，并记录不纳入最终用户文档发布的原因。

## 截图清单

- `omni-frontend/e2e-docs/screenshot-coverage.yaml` 收录实施计划规定的 12 个覆盖模块。
- 仓库现有 36 张文档图片全部登记；它们只作为历史覆盖资产，不代表存在可重放 fixture 或四语言版本。
- Authentication、System、Scheduling、Messaging、Workflow、CRM、SRM 为 partial。
- Procurement、Asset、Permissions/Exceptions、Scaffold Development、Operations 为 missing。
- Procurement 审批规则明确登记 1440×900、390×844、1024×768 三视口要求。

## 校验结果

- 两份 YAML 均通过 PyYAML 解析。
- 所有中文源文件、标记为已存在的翻译文件和 36 个历史截图路径均真实存在。
- `missing` 状态与磁盘缺失状态一致。
- 正式翻译发布、截图用例一一对应和四语言人工复核仍属于 WP-09B，不在本基线中伪装为完成。
