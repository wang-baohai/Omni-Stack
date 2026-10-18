# 文档翻译复核队列

> 本文件由 `node tools/omni-cli/scripts/docs-review-queue.mjs --generate` 自动生成，
> 请勿手工编辑条目；复核人必须在 manifest 中更新 status/reviewed_at 后重新生成本文件。

- 生成时间：2026-10-18
- 中文事实源：49 篇（Chinese-only 3 篇；需翻译 46 篇）
- 译文待复核：0 篇（en-US/ja-JP/ko-KR）
- 已完成复核：138 篇（HUMAN_REVIEW 0；AI_REVIEW 138）
- Chinese-only 文档（不生成译文行）：repository-governance, adr-wiki-ai-assets, curated-changelog

## 复核流程

1. 复核人对照中文事实源逐节核对译文：术语、代码块、命令、路径、链接、数字与表格必须与源一致；
2. 代码块、命令、API 路径、权限码不翻译；叙述性文案保持目标语言自然表达；
3. 按当前授权完成实际复核后记录 reviewer、reviewed_at、review_type、reviewed_source_sha256 和 translation_sha256；HUMAN_REVIEW 要求真人，AI_REVIEW 要求清单中的所有者授权及摘要绑定的逐项审校证据，不能冒称人工；
4. 全部 synchronized 后 `npm run docs:i18n:check` 才能转绿；严禁未经复核直接改状态或日期。

## P0 system-truth 系统真相（3 篇 × 3 语言）

| 文档 | 中文事实源 | 源摘要(前12) | en-US | ja-JP | ko-KR |
| --- | --- | --- | --- | --- | --- |
| architecture | docs/architecture.md | dc06378a9efe | synchronized@2026-10-17 09:58:40 | synchronized@2026-10-17 09:58:40 | synchronized@2026-10-17 09:58:40 |
| api-contract | docs/api-contract.md | 63bb7c8c879a | synchronized@2026-10-16 16:52:27 | synchronized@2026-10-16 16:52:27 | synchronized@2026-10-16 16:52:27 |
| core-flows | docs/core-flows.md | 932748a1e461 | synchronized@2026-10-18 08:04:41 | synchronized@2026-10-18 08:04:41 | synchronized@2026-10-18 08:04:41 |

## P1 开发/模块指南（8 篇 × 3 语言）

| 文档 | 中文事实源 | 源摘要(前12) | en-US | ja-JP | ko-KR |
| --- | --- | --- | --- | --- | --- |
| backend-patterns | docs/backend-patterns.md | 809c62fe362c | synchronized@2026-10-18 02:26:17 | synchronized@2026-10-18 02:26:17 | synchronized@2026-10-18 02:26:17 |
| frontend-patterns | docs/frontend-patterns.md | 69df06b582a4 | synchronized@2026-10-18 02:26:17 | synchronized@2026-10-18 02:26:17 | synchronized@2026-10-18 02:26:17 |
| scheduling | docs/scheduling.md | af1e4a539d55 | synchronized@2026-10-15 08:48:47 | synchronized@2026-10-15 08:48:47 | synchronized@2026-10-15 08:48:47 |
| workflow | docs/workflow.md | 4cc1e13aff69 | synchronized@2026-10-16 15:16:52 | synchronized@2026-10-16 15:16:52 | synchronized@2026-10-16 15:16:52 |
| crm | docs/crm.md | dbb748468d43 | synchronized@2026-10-15 11:38:58 | synchronized@2026-10-15 11:38:58 | synchronized@2026-10-15 11:38:58 |
| srm | docs/srm.md | b5d3f7de4909 | synchronized@2026-10-18 02:13:05 | synchronized@2026-10-18 02:13:05 | synchronized@2026-10-18 02:13:05 |
| mq-reliability | docs/mq-reliability.md | 98aae48046ef | synchronized@2026-10-13 12:26:49 | synchronized@2026-10-13 12:26:49 | synchronized@2026-10-13 12:26:49 |
| guide-scaffold-development | docs/guides/scaffold-development.md | 6777bfff1f5d | synchronized@2026-10-17 09:58:40 | synchronized@2026-10-17 09:58:40 | synchronized@2026-10-17 09:58:40 |

## P2 其他（35 篇 × 3 语言）

| 文档 | 中文事实源 | 源摘要(前12) | en-US | ja-JP | ko-KR |
| --- | --- | --- | --- | --- | --- |
| docker-deployment | docs/docker-deployment.md | eb4864921621 | synchronized@2026-10-17 13:52:35 | synchronized@2026-10-17 13:52:35 | synchronized@2026-10-17 13:52:35 |
| observability | docs/observability.md | 798c160e3208 | synchronized@2026-10-13 14:54:15 | synchronized@2026-10-13 14:54:15 | synchronized@2026-10-13 14:54:15 |
| crm-design | docs/design/crm-design.md | 07a28829eedf | synchronized@2026-10-18 08:04:41 | synchronized@2026-10-17 08:24:10 | synchronized@2026-10-17 08:24:10 |
| srm-design | docs/design/srm-design.md | 84d9d8b32687 | synchronized@2026-10-18 08:04:41 | synchronized@2026-10-18 01:31:44 | synchronized@2026-10-18 01:31:44 |
| procurement-design | docs/design/procurement-design.md | 57521465bcd8 | synchronized@2026-10-18 02:13:05 | synchronized@2026-10-18 02:13:05 | synchronized@2026-10-18 02:13:05 |
| asset-design | docs/design/asset-design.md | 65697478af3c | synchronized@2026-10-18 08:04:41 | synchronized@2026-10-16 09:22:57 | synchronized@2026-10-16 09:22:57 |
| preset-quick-selection | docs/preset-quick-selection.md | 4b3af45e9711 | synchronized@2026-10-13 12:17:37 | synchronized@2026-10-13 12:17:37 | synchronized@2026-10-13 12:17:37 |
| preset-maintenance | docs/preset-maintenance.md | d174bfe252ae | synchronized@2026-10-13 12:17:37 | synchronized@2026-10-13 12:17:37 | synchronized@2026-10-13 12:17:37 |
| preset-dependency-matrix | docs/preset-dependency-matrix.md | 6b63121f029a | synchronized@2026-10-13 12:17:37 | synchronized@2026-10-13 12:17:37 | synchronized@2026-10-13 12:17:37 |
| custom-preset-tutorial | docs/custom-preset-tutorial.md | 52b76d03e398 | synchronized@2026-10-13 12:54:18 | synchronized@2026-10-13 12:54:18 | synchronized@2026-10-13 12:54:18 |
| preset-upgrade-guide | docs/preset-upgrade-guide.md | 2991c5aaebf3 | synchronized@2026-10-13 12:17:37 | synchronized@2026-10-13 12:17:37 | synchronized@2026-10-13 12:17:37 |
| guide-quick-start | docs/guides/quick-start.md | fa594d5972ab | synchronized@2026-10-13 12:54:18 | synchronized@2026-10-13 12:54:18 | synchronized@2026-10-13 12:54:18 |
| guide-authentication | docs/guides/authentication.md | e395fe11bda1 | synchronized@2026-10-18 08:50:36 | synchronized@2026-10-18 08:50:36 | synchronized@2026-10-18 08:50:36 |
| guide-permissions | docs/guides/permissions.md | 53bb104afd5d | synchronized@2026-10-18 13:10:29 | synchronized@2026-10-18 13:10:29 | synchronized@2026-10-18 13:10:29 |
| guide-system-security-audit | docs/guides/system-security-audit.md | ecd3f7191218 | synchronized@2026-10-17 18:58:51 | synchronized@2026-10-17 18:58:51 | synchronized@2026-10-17 18:58:51 |
| guide-workflow-approval | docs/guides/workflow-approval.md | 43e51b029bef | synchronized@2026-10-17 19:33:57 | synchronized@2026-10-17 19:33:57 | synchronized@2026-10-17 19:33:57 |
| guide-scheduling | docs/guides/scheduling.md | 064a93470c55 | synchronized@2026-10-14 09:08:30 | synchronized@2026-10-14 09:08:30 | synchronized@2026-10-14 09:08:30 |
| guide-crm-flow | docs/guides/crm-flow.md | b833d13167c3 | synchronized@2026-10-17 19:45:10 | synchronized@2026-10-17 19:45:10 | synchronized@2026-10-17 19:45:10 |
| guide-srm-flow | docs/guides/srm-flow.md | 10b8ea683570 | synchronized@2026-10-17 20:03:36 | synchronized@2026-10-17 20:03:36 | synchronized@2026-10-17 20:03:36 |
| guide-procurement-flow | docs/guides/procurement-flow.md | 201034f4ee9b | synchronized@2026-10-17 20:21:09 | synchronized@2026-10-17 20:21:09 | synchronized@2026-10-17 20:21:09 |
| guide-asset-flow | docs/guides/asset-flow.md | 0e0a4523741b | synchronized@2026-10-17 20:33:06 | synchronized@2026-10-17 20:33:06 | synchronized@2026-10-17 20:33:06 |
| guide-operations-upgrade | docs/guides/operations-upgrade.md | cc4c3588f07c | synchronized@2026-10-13 14:54:15 | synchronized@2026-10-13 14:54:15 | synchronized@2026-10-13 14:54:15 |
| guide-troubleshooting | docs/guides/troubleshooting.md | 2481ddc1de17 | synchronized@2026-10-17 20:49:46 | synchronized@2026-10-17 20:46:23 | synchronized@2026-10-17 20:46:23 |
| wiki-index | docs/wiki/index.md | 371317d798e6 | synchronized@2026-10-13 11:58:03 | synchronized@2026-10-13 11:58:03 | synchronized@2026-10-13 11:58:03 |
| wiki-feature-tour | docs/wiki/feature-tour.md | 58b46cb78fd0 | synchronized@2026-10-13 12:17:37 | synchronized@2026-10-13 12:17:37 | synchronized@2026-10-13 12:17:37 |
| wiki-site-maintenance | docs/wiki/site-maintenance.md | c1e408112efd | synchronized@2026-10-18 02:57:41 | synchronized@2026-10-18 02:57:41 | synchronized@2026-10-18 02:57:41 |
| wiki-data-and-fixtures | docs/wiki/data-and-fixtures.md | 818b691315df | synchronized@2026-10-13 11:58:03 | synchronized@2026-10-13 11:58:03 | synchronized@2026-10-13 11:58:03 |
| wiki-source-debugging | docs/wiki/source-debugging.md | 82f7572b9aae | synchronized@2026-10-17 21:07:11 | synchronized@2026-10-17 21:07:11 | synchronized@2026-10-17 21:07:11 |
| source-map | docs/reference/source-map.md | a1a85b478058 | synchronized@2026-10-14 11:18:34 | synchronized@2026-10-14 11:18:34 | synchronized@2026-10-14 11:18:34 |
| shared-ai-assets | ai/README.md | 4c82c82114f6 | synchronized@2026-10-18 13:47:01 | synchronized@2026-10-18 13:47:01 | synchronized@2026-10-18 13:47:01 |
| project-readme | README.md | 9884ad04e96f | synchronized@2026-10-18T06:44:23.993Z | synchronized@2026-10-18T06:44:23.993Z | synchronized@2026-10-18T06:44:23.993Z |
| wiki-interface-reference | docs/wiki/interface-reference.md | f6a38d89750e | synchronized@2026-10-17 21:24:24 | synchronized@2026-10-17 21:24:24 | synchronized@2026-10-17 21:24:24 |
| wiki-workflow-development | docs/wiki/workflow-development.md | 0705d000ab8f | synchronized@2026-10-14 10:25:08 | synchronized@2026-10-14 10:25:08 | synchronized@2026-10-14 10:25:08 |
| wiki-outbox-development | docs/wiki/outbox-development.md | d260241659ee | synchronized@2026-10-18 01:20:55 | synchronized@2026-10-18 01:20:55 | synchronized@2026-10-18 01:20:55 |
| wiki-site-interface-copy | docs/.vitepress/locales/zh-CN.json | d6032e90eeda | synchronized@2026-10-13 11:42:35 | synchronized@2026-10-13 11:42:35 | synchronized@2026-10-13 11:42:35 |

## Chinese-only 文档（仅中文事实源）

| 文档 | 类型 | 中文事实源 |
| --- | --- | --- |
| repository-governance | governance | docs/repository-governance.md |
| adr-wiki-ai-assets | adr | docs/adr/0002-repository-wiki-and-ai-assets.md |
| curated-changelog | history | CHANGELOG.md |

## 复核记录（由复核人追加）

| 日期 | 复核人 | 文档 | 语言 | 结论 |
| --- | --- | --- | --- | --- |
|  |  |  |  |  |

## 章节结构差异（复核优先级提示）

未发现 `##` 节数不一致的译文；结构漂移风险低。
