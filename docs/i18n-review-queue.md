# 文档翻译人工复核队列

> 本文件由 `node tools/omni-cli/scripts/docs-review-queue.mjs --generate` 自动生成，
> 请勿手工编辑条目；复核结论由复核人在 docs/docs-manifest.yaml 更新 status/reviewed_at 后重新生成本文件。

- 生成时间：2026-09-04
- 中文事实源：38 篇；译文待复核：114 篇（en-US/ja-JP/ko-KR 各 38）
- 已完成人工复核：0 篇

## 复核流程

1. 复核人对照中文事实源逐节核对译文：术语、代码块、命令、路径、链接、数字与表格必须与源一致；
2. 代码块、命令、API 路径、权限码不翻译；叙述性文案保持目标语言自然表达；
3. 复核通过后，由复核人（非翻译初稿生成者）在 docs/docs-manifest.yaml 将对应译文 status 改为 synchronized 并填写 reviewed_at（ISO 日期），随后重新生成本队列；
4. 全部 synchronized 后 `npm run docs:i18n:check` 才能转绿；严禁未经复核直接改状态或日期。

## P0 system-truth 系统真相（3 篇 × 3 语言）

| 文档 | 中文事实源 | 源摘要(前12) | en-US | ja-JP | ko-KR |
| --- | --- | --- | --- | --- | --- |
| architecture | docs/architecture.md | 5fc881579377 | present-unverified | present-unverified | present-unverified |
| api-contract | docs/api-contract.md | 96cebb47233a | present-unverified | present-unverified | present-unverified |
| core-flows | docs/core-flows.md | 2d2afd48d592 | present-unverified | present-unverified | present-unverified |

## P1 开发/模块指南（8 篇 × 3 语言）

| 文档 | 中文事实源 | 源摘要(前12) | en-US | ja-JP | ko-KR |
| --- | --- | --- | --- | --- | --- |
| backend-patterns | docs/backend-patterns.md | 9d60b1049ac8 | present-unverified | present-unverified | present-unverified |
| frontend-patterns | docs/frontend-patterns.md | 982ebd308674 | present-unverified | present-unverified | present-unverified |
| scheduling | docs/scheduling.md | 677b2be31716 | present-unverified | present-unverified | present-unverified |
| workflow | docs/workflow.md | f6dc03e604a0 | present-unverified | present-unverified | present-unverified |
| crm | docs/crm.md | 8ddf46014cbb | present-unverified | present-unverified | present-unverified |
| srm | docs/srm.md | 62c98f8e7ec8 | present-unverified | present-unverified | present-unverified |
| mq-reliability | docs/mq-reliability.md | 93cbfdcba530 | present-unverified | present-unverified | present-unverified |
| guide-scaffold-development | docs/guides/scaffold-development.md | bb5c758f989a | present-unverified | present-unverified | present-unverified |

## P2 其他（27 篇 × 3 语言）

| 文档 | 中文事实源 | 源摘要(前12) | en-US | ja-JP | ko-KR |
| --- | --- | --- | --- | --- | --- |
| docker-deployment | docs/docker-deployment.md | 935f4f110938 | present-unverified | present-unverified | present-unverified |
| observability | docs/observability.md | 16e9664eb19e | present-unverified | present-unverified | present-unverified |
| crm-design | docs/design/crm-design.md | 4ec0c10fc3be | present-unverified | present-unverified | present-unverified |
| srm-design | docs/design/srm-design.md | ceeb22198758 | present-unverified | present-unverified | present-unverified |
| procurement-design | docs/design/procurement-design.md | 46b8f10e8e39 | present-unverified | present-unverified | present-unverified |
| asset-design | docs/design/asset-design.md | bba9461126d8 | present-unverified | present-unverified | present-unverified |
| full-functional-audit | docs/full-functional-audit-2026-08-14.md | d2c3242f9cf8 | <missing> | <missing> | <missing> |
| full-functional-audit-remediation | docs/full-functional-audit-remediation-2026-08-17.md | cdc31c14329c | <missing> | <missing> | <missing> |
| scaffold-upgrade-plan | docs/scaffold-upgrade-plan.md | 281cf5c5e6e9 | <missing> | <missing> | <missing> |
| scaffold-upgrade-implementation-plan | docs/scaffold-upgrade-implementation-plan.md | 1bbdc39b6c2b | <missing> | <missing> | <missing> |
| preset-quick-selection | docs/preset-quick-selection.md | 1a2e8b84b19b | present-unverified | present-unverified | present-unverified |
| preset-maintenance | docs/preset-maintenance.md | c168b62c7bb1 | present-unverified | present-unverified | present-unverified |
| preset-dependency-matrix | docs/preset-dependency-matrix.md | 2b235bedcb4d | present-unverified | present-unverified | present-unverified |
| custom-preset-tutorial | docs/custom-preset-tutorial.md | e4d58bf268d1 | present-unverified | present-unverified | present-unverified |
| preset-upgrade-guide | docs/preset-upgrade-guide.md | 3a91cf33fb23 | present-unverified | present-unverified | present-unverified |
| guide-quick-start | docs/guides/quick-start.md | 0da1a481ebf5 | present-unverified | present-unverified | present-unverified |
| guide-authentication | docs/guides/authentication.md | be86a53a1ba7 | present-unverified | present-unverified | present-unverified |
| guide-permissions | docs/guides/permissions.md | 34abe1aa1881 | present-unverified | present-unverified | present-unverified |
| guide-system-security-audit | docs/guides/system-security-audit.md | 8307115fbd85 | present-unverified | present-unverified | present-unverified |
| guide-workflow-approval | docs/guides/workflow-approval.md | ff4df819c13c | present-unverified | present-unverified | present-unverified |
| guide-scheduling | docs/guides/scheduling.md | 9720091cafc6 | present-unverified | present-unverified | present-unverified |
| guide-crm-flow | docs/guides/crm-flow.md | b61be136ee60 | present-unverified | present-unverified | present-unverified |
| guide-srm-flow | docs/guides/srm-flow.md | 432bd91c02ae | present-unverified | present-unverified | present-unverified |
| guide-procurement-flow | docs/guides/procurement-flow.md | d889d867c0ea | present-unverified | present-unverified | present-unverified |
| guide-asset-flow | docs/guides/asset-flow.md | 99290d743a55 | present-unverified | present-unverified | present-unverified |
| guide-operations-upgrade | docs/guides/operations-upgrade.md | 7b9d5042add3 | present-unverified | present-unverified | present-unverified |
| guide-troubleshooting | docs/guides/troubleshooting.md | 93fa88199018 | present-unverified | present-unverified | present-unverified |

## 复核记录（由复核人追加）

| 日期 | 复核人 | 文档 | 语言 | 结论 |
| --- | --- | --- | --- | --- |
|  |  |  |  |  |

## 章节结构差异（复核优先级提示）

未发现 `##` 节数不一致的译文；结构漂移风险低。
