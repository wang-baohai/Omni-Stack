# 文档翻译人工复核队列

> 本文件由 `node tools/omni-cli/scripts/docs-review-queue.mjs --generate` 自动生成，
> 请勿手工编辑条目；复核人必须在 manifest 中更新 status/reviewed_at 后重新生成本文件。

- 生成时间：2026-10-08
- 中文事实源：34 篇（Chinese-only 0 篇；需翻译 34 篇）
- 译文待复核：71 篇（en-US/ja-JP/ko-KR）
- 已完成人工复核：31 篇

## 复核流程

1. 复核人对照中文事实源逐节核对译文：术语、代码块、命令、路径、链接、数字与表格必须与源一致；
2. 代码块、命令、API 路径、权限码不翻译；叙述性文案保持目标语言自然表达；
3. 复核通过后，由复核人（非翻译初稿生成者）在 docs/docs-manifest.yaml 将对应译文 status 改为 synchronized 并填写 reviewed_at（ISO 日期），随后重新生成本队列；
4. 全部 synchronized 后 `npm run docs:i18n:check` 才能转绿；严禁未经复核直接改状态或日期。

## P0 system-truth 系统真相（3 篇 × 3 语言）

| 文档 | 中文事实源 | 源摘要(前12) | en-US | ja-JP | ko-KR |
| --- | --- | --- | --- | --- | --- |
| architecture | docs/architecture.md | a642b292be14 | present-unverified | present-unverified | present-unverified |
| api-contract | docs/api-contract.md | 60c18671b12e | present-unverified | present-unverified | present-unverified |
| core-flows | docs/core-flows.md | 19d8001ba528 | present-unverified | present-unverified | present-unverified |

## P1 开发/模块指南（8 篇 × 3 语言）

| 文档 | 中文事实源 | 源摘要(前12) | en-US | ja-JP | ko-KR |
| --- | --- | --- | --- | --- | --- |
| backend-patterns | docs/backend-patterns.md | dbd21f7d6048 | present-unverified | present-unverified | present-unverified |
| frontend-patterns | docs/frontend-patterns.md | 9c86ddb75751 | present-unverified | present-unverified | present-unverified |
| scheduling | docs/scheduling.md | 3ca14937792c | present-unverified | present-unverified | present-unverified |
| workflow | docs/workflow.md | 08de8ac09fa9 | present-unverified | present-unverified | present-unverified |
| crm | docs/crm.md | 8ddf46014cbb | present-unverified | present-unverified | present-unverified |
| srm | docs/srm.md | fc10c16cba9f | present-unverified | present-unverified | present-unverified |
| mq-reliability | docs/mq-reliability.md | cdc95ef7f2a5 | present-unverified | present-unverified | present-unverified |
| guide-scaffold-development | docs/guides/scaffold-development.md | df46e278232a | synchronized@2026-10-05T15:09:12.619Z | synchronized@2026-10-05T15:09:12.619Z | present-unverified |

## P2 其他（23 篇 × 3 语言）

| 文档 | 中文事实源 | 源摘要(前12) | en-US | ja-JP | ko-KR |
| --- | --- | --- | --- | --- | --- |
| docker-deployment | docs/docker-deployment.md | a14b3f1bc816 | present-unverified | present-unverified | present-unverified |
| observability | docs/observability.md | 67fd84b7ebd7 | present-unverified | present-unverified | present-unverified |
| crm-design | docs/design/crm-design.md | 2b85ffc5a003 | present-unverified | present-unverified | present-unverified |
| srm-design | docs/design/srm-design.md | ceeb22198758 | present-unverified | present-unverified | present-unverified |
| procurement-design | docs/design/procurement-design.md | 46b8f10e8e39 | present-unverified | present-unverified | present-unverified |
| asset-design | docs/design/asset-design.md | bba9461126d8 | present-unverified | present-unverified | present-unverified |
| preset-quick-selection | docs/preset-quick-selection.md | 592114d6a6d3 | synchronized@2026-09-30T17:59:36.838Z | synchronized@2026-09-30T17:59:37.756Z | synchronized@2026-09-30T17:59:38.744Z |
| preset-maintenance | docs/preset-maintenance.md | c168b62c7bb1 | synchronized@2026-09-30T17:59:27.970Z | synchronized@2026-09-30T08:10:23.650Z | synchronized@2026-09-30T08:10:23.663Z |
| preset-dependency-matrix | docs/preset-dependency-matrix.md | 6b63121f029a | synchronized@2026-09-30T08:10:23.674Z | synchronized@2026-09-30T08:10:23.685Z | synchronized@2026-09-30T08:10:23.696Z |
| custom-preset-tutorial | docs/custom-preset-tutorial.md | d435a5d976a5 | synchronized@2026-09-30T17:59:30.989Z | synchronized@2026-09-30T17:59:31.986Z | synchronized@2026-09-30T17:59:32.945Z |
| preset-upgrade-guide | docs/preset-upgrade-guide.md | 2991c5aaebf3 | synchronized@2026-09-30T17:59:33.854Z | synchronized@2026-09-30T17:59:34.850Z | synchronized@2026-09-30T17:59:35.814Z |
| guide-quick-start | docs/guides/quick-start.md | 15dfceb25ba2 | present-unverified | present-unverified | present-unverified |
| guide-authentication | docs/guides/authentication.md | 6f654b5f7d5b | present-unverified | present-unverified | present-unverified |
| guide-permissions | docs/guides/permissions.md | 15857fcc5dd5 | present-unverified | present-unverified | present-unverified |
| guide-system-security-audit | docs/guides/system-security-audit.md | 11b09f799014 | present-unverified | present-unverified | present-unverified |
| guide-workflow-approval | docs/guides/workflow-approval.md | ff4df819c13c | synchronized@2026-09-30T17:59:42.473Z | present-unverified | present-unverified |
| guide-scheduling | docs/guides/scheduling.md | 9720091cafc6 | synchronized@2026-09-30T17:59:43.388Z | synchronized@2026-09-30T17:59:44.405Z | synchronized@2026-09-30T17:59:45.365Z |
| guide-crm-flow | docs/guides/crm-flow.md | b61be136ee60 | synchronized@2026-10-05T15:09:12.619Z | synchronized@2026-10-05T15:09:12.619Z | synchronized@2026-10-05T15:09:12.619Z |
| guide-srm-flow | docs/guides/srm-flow.md | 432bd91c02ae | present-unverified | present-unverified | present-unverified |
| guide-procurement-flow | docs/guides/procurement-flow.md | a0dbc9a5d0d5 | present-unverified | present-unverified | present-unverified |
| guide-asset-flow | docs/guides/asset-flow.md | 604cc241701a | synchronized@2026-10-05T15:09:12.619Z | synchronized@2026-10-05T15:09:12.619Z | synchronized@2026-10-05T15:09:12.619Z |
| guide-operations-upgrade | docs/guides/operations-upgrade.md | b579f54839ec | present-unverified | synchronized@2026-10-05T15:14:09.347Z | synchronized@2026-10-05T15:14:09.347Z |
| guide-troubleshooting | docs/guides/troubleshooting.md | 12df8ee1a321 | present-unverified | synchronized@2026-10-05T15:09:12.619Z | synchronized@2026-10-05T15:09:12.619Z |

## 复核记录（由复核人追加）

| 日期 | 复核人 | 文档 | 语言 | 结论 |
| --- | --- | --- | --- | --- |
|  |  |  |  |  |

## 章节结构差异（复核优先级提示）

未发现 `##` 节数不一致的译文；结构漂移风险低。
