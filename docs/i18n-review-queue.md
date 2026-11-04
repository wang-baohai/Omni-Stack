# 文档翻译复核队列

> 本文件由 `node tools/omni-cli/scripts/docs-review-queue.mjs --generate` 自动生成，
> 请勿手工编辑条目；复核人必须在 manifest 中更新 status/reviewed_at 后重新生成本文件。

- 生成时间：2026-11-04
- 中文事实源：122 篇（Chinese-only 3 篇；需翻译 119 篇）
- 译文待复核：0 篇（en-US/ja-JP/ko-KR）
- 已完成复核：357 篇（HUMAN_REVIEW 0；AI_REVIEW 357）
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
| core-flows | docs/core-flows.md | 6d2086c21f81 | synchronized@2026-10-30 06:50:01 | synchronized@2026-10-30 06:50:01 | synchronized@2026-10-30 06:50:01 |

## P1 开发/模块指南（8 篇 × 3 语言）

| 文档 | 中文事实源 | 源摘要(前12) | en-US | ja-JP | ko-KR |
| --- | --- | --- | --- | --- | --- |
| backend-patterns | docs/backend-patterns.md | 809c62fe362c | synchronized@2026-10-18 02:26:17 | synchronized@2026-10-18 02:26:17 | synchronized@2026-10-18 02:26:17 |
| frontend-patterns | docs/frontend-patterns.md | e3cac58e39fe | synchronized@2026-10-31 08:24:03 | synchronized@2026-10-31 08:24:03 | synchronized@2026-10-31 08:24:03 |
| scheduling | docs/scheduling.md | 6f9d96f8c3b9 | synchronized@2026-10-25 20:15:03 | synchronized@2026-10-25 20:15:03 | synchronized@2026-10-25 20:15:03 |
| workflow | docs/workflow.md | 4cc1e13aff69 | synchronized@2026-10-16 15:16:52 | synchronized@2026-10-16 15:16:52 | synchronized@2026-10-16 15:16:52 |
| crm | docs/crm.md | dbb748468d43 | synchronized@2026-10-15 11:38:58 | synchronized@2026-10-15 11:38:58 | synchronized@2026-10-15 11:38:58 |
| srm | docs/srm.md | 533ce7c0afe3 | synchronized@2026-11-04 15:20:11 | synchronized@2026-11-04 15:20:11 | synchronized@2026-11-04 15:20:11 |
| mq-reliability | docs/mq-reliability.md | 98aae48046ef | synchronized@2026-10-13 12:26:49 | synchronized@2026-10-13 12:26:49 | synchronized@2026-10-13 12:26:49 |
| guide-scaffold-development | docs/guides/scaffold-development.md | 6777bfff1f5d | synchronized@2026-10-17 09:58:40 | synchronized@2026-10-17 09:58:40 | synchronized@2026-10-17 09:58:40 |

## P2 其他（108 篇 × 3 语言）

| 文档 | 中文事实源 | 源摘要(前12) | en-US | ja-JP | ko-KR |
| --- | --- | --- | --- | --- | --- |
| docker-deployment | docs/docker-deployment.md | eb4864921621 | synchronized@2026-10-17 13:52:35 | synchronized@2026-10-17 13:52:35 | synchronized@2026-10-17 13:52:35 |
| observability | docs/observability.md | a75d50b24e5a | synchronized@2026-10-25 22:44:58 | synchronized@2026-10-25 22:44:58 | synchronized@2026-10-25 22:44:58 |
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
| wiki-index | docs/wiki/index.md | 1ada7248cd1e | synchronized@2026-10-25 20:15:03 | synchronized@2026-10-25 20:15:03 | synchronized@2026-10-25 20:15:03 |
| wiki-feature-tour | docs/wiki/feature-tour.md | 58b46cb78fd0 | synchronized@2026-10-13 12:17:37 | synchronized@2026-10-13 12:17:37 | synchronized@2026-10-13 12:17:37 |
| wiki-site-maintenance | docs/wiki/site-maintenance.md | c1e408112efd | synchronized@2026-10-18 02:57:41 | synchronized@2026-10-18 02:57:41 | synchronized@2026-10-18 02:57:41 |
| wiki-data-and-fixtures | docs/wiki/data-and-fixtures.md | 818b691315df | synchronized@2026-10-13 11:58:03 | synchronized@2026-10-13 11:58:03 | synchronized@2026-10-13 11:58:03 |
| wiki-source-debugging | docs/wiki/source-debugging.md | 82f7572b9aae | synchronized@2026-10-17 21:07:11 | synchronized@2026-10-17 21:07:11 | synchronized@2026-10-17 21:07:11 |
| source-map | docs/reference/source-map.md | a1a85b478058 | synchronized@2026-10-14 11:18:34 | synchronized@2026-10-14 11:18:34 | synchronized@2026-10-14 11:18:34 |
| shared-ai-assets | ai/README.md | 4c82c82114f6 | synchronized@2026-10-18 13:47:01 | synchronized@2026-10-18 13:47:01 | synchronized@2026-10-18 13:47:01 |
| project-readme | README.md | f54a6cb37a6c | synchronized@2026-11-04 18:00:18 | synchronized@2026-11-04 18:00:18 | synchronized@2026-11-04 18:00:18 |
| wiki-interface-reference | docs/wiki/interface-reference.md | f6a38d89750e | synchronized@2026-10-17 21:24:24 | synchronized@2026-10-17 21:24:24 | synchronized@2026-10-17 21:24:24 |
| wiki-workflow-development | docs/wiki/workflow-development.md | 0705d000ab8f | synchronized@2026-10-14 10:25:08 | synchronized@2026-10-14 10:25:08 | synchronized@2026-10-14 10:25:08 |
| wiki-outbox-development | docs/wiki/outbox-development.md | d260241659ee | synchronized@2026-10-18 01:20:55 | synchronized@2026-10-18 01:20:55 | synchronized@2026-10-18 01:20:55 |
| wiki-site-interface-copy | docs/.vitepress/locales/zh-CN.json | 943e21983b41 | synchronized@2026-10-25 20:15:03 | synchronized@2026-10-25 20:15:03 | synchronized@2026-10-25 20:15:03 |
| harness-what-and-why | docs/wiki/harness-what-and-why.md | fb1a9131e502 | synchronized@2026-10-29 20:25:03 | synchronized@2026-10-29 20:25:03 | synchronized@2026-10-29 20:25:03 |
| harness-anatomy | docs/wiki/harness-anatomy.md | 3aaab4088fb7 | synchronized@2026-10-29 20:25:03 | synchronized@2026-10-29 20:25:03 | synchronized@2026-10-29 20:25:03 |
| harness-industry-practice | docs/wiki/harness-industry-practice.md | 24d85d073fe5 | synchronized@2026-10-29 20:25:03 | synchronized@2026-10-29 20:25:03 | synchronized@2026-10-29 20:25:03 |
| harness-omni-stack-practice | docs/wiki/harness-omni-stack-practice.md | 0647bc2a15cf | synchronized@2026-10-29 20:25:03 | synchronized@2026-10-29 20:25:03 | synchronized@2026-10-29 20:25:03 |
| harness-fact-maintenance | docs/wiki/harness-fact-maintenance.md | 2deeaa1f6dce | synchronized@2026-10-29 20:25:03 | synchronized@2026-10-29 20:25:03 | synchronized@2026-10-29 20:25:03 |
| harness-improvement | docs/wiki/harness-improvement.md | bd725db14f15 | synchronized@2026-10-29 20:25:03 | synchronized@2026-10-29 20:25:03 | synchronized@2026-10-29 20:25:03 |
| harness-secondary-development | docs/wiki/harness-secondary-development.md | a73438106383 | synchronized@2026-10-29 20:25:03 | synchronized@2026-10-29 20:25:03 | synchronized@2026-10-29 20:25:03 |
| usage-platform | docs/wiki/usage-platform.md | 75bb1ece3a6d | synchronized@2026-10-26 10:09:04 | synchronized@2026-10-26 10:09:04 | synchronized@2026-10-26 10:09:04 |
| usage-crm | docs/wiki/usage-crm.md | 2a592c953215 | synchronized@2026-10-23 14:49:47 | synchronized@2026-10-23 14:49:47 | synchronized@2026-10-23 14:49:47 |
| usage-srm | docs/wiki/usage-srm.md | f0bf8cdc03aa | synchronized@2026-10-26 17:18:30 | synchronized@2026-10-26 17:18:30 | synchronized@2026-10-26 17:18:30 |
| presets-overview | docs/wiki/presets-overview.md | 8a7615ca264d | synchronized@2026-10-29 21:11:14 | synchronized@2026-10-29 21:11:14 | synchronized@2026-10-29 21:11:14 |
| preset-core | docs/wiki/preset-core.md | 31ec8cce08f5 | synchronized@2026-10-29 21:11:14 | synchronized@2026-10-29 21:11:14 | synchronized@2026-10-29 21:11:14 |
| preset-workflow | docs/wiki/preset-workflow.md | d1a3440d2827 | synchronized@2026-10-29 21:27:22 | synchronized@2026-10-29 21:27:22 | synchronized@2026-10-29 21:27:22 |
| preset-crm | docs/wiki/preset-crm.md | 318d0bd8b97e | synchronized@2026-10-29 21:18:21 | synchronized@2026-10-29 21:18:21 | synchronized@2026-10-29 21:18:21 |
| preset-supply-chain | docs/wiki/preset-supply-chain.md | b27e9d7d7a46 | synchronized@2026-10-29 21:22:06 | synchronized@2026-10-29 21:22:06 | synchronized@2026-10-29 21:22:06 |
| preset-full | docs/wiki/preset-full.md | 0212a2b223c7 | synchronized@2026-10-29 21:25:23 | synchronized@2026-10-29 21:25:23 | synchronized@2026-10-29 21:25:23 |
| authentication-principles | docs/wiki/authentication-principles.md | b9c15a56d4ea | synchronized@2026-10-24 12:36:37 | synchronized@2026-10-24 12:36:37 | synchronized@2026-10-24 12:36:37 |
| authentication-design | docs/wiki/authentication-design.md | 90102bdf5ada | synchronized@2026-10-24 12:36:37 | synchronized@2026-10-24 12:36:37 | synchronized@2026-10-24 12:36:37 |
| authentication-framework | docs/wiki/authentication-framework.md | 0570e5526e66 | synchronized@2026-10-24 17:12:40 | synchronized@2026-10-24 17:12:40 | synchronized@2026-10-24 17:12:40 |
| authentication-password | docs/wiki/authentication-password.md | 6a72fb28bfee | synchronized@2026-10-24 12:36:37 | synchronized@2026-10-24 12:36:37 | synchronized@2026-10-24 12:36:37 |
| authentication-oauth2 | docs/wiki/authentication-oauth2.md | f3845d29829a | synchronized@2026-10-24 12:40:16 | synchronized@2026-10-24 12:40:16 | synchronized@2026-10-24 12:40:16 |
| authentication-device-social | docs/wiki/authentication-device-social.md | 56c414170ab9 | synchronized@2026-10-24 18:05:27 | synchronized@2026-10-24 12:40:16 | synchronized@2026-10-24 12:40:16 |
| authentication-security | docs/wiki/authentication-security.md | 8a34e268a2e2 | synchronized@2026-10-24 12:36:37 | synchronized@2026-10-24 12:36:37 | synchronized@2026-10-24 12:36:37 |
| tenancy-principles | docs/wiki/tenancy-principles.md | ed8ad4f4172a | synchronized@2026-10-24 13:29:40 | synchronized@2026-10-24 13:29:40 | synchronized@2026-10-24 13:29:40 |
| tenancy-design | docs/wiki/tenancy-design.md | cd7f3ac3f1e2 | synchronized@2026-10-24 13:29:40 | synchronized@2026-10-24 13:29:40 | synchronized@2026-10-24 13:29:40 |
| tenancy-framework | docs/wiki/tenancy-framework.md | 25d35bcced4b | synchronized@2026-10-24 17:12:40 | synchronized@2026-10-24 17:12:40 | synchronized@2026-10-24 17:12:40 |
| tenancy-request | docs/wiki/tenancy-request.md | 029ccbb8aec6 | synchronized@2026-10-24 13:29:40 | synchronized@2026-10-24 13:29:40 | synchronized@2026-10-24 13:29:40 |
| tenancy-permissions | docs/wiki/tenancy-permissions.md | 2efff5c2448f | synchronized@2026-10-24 13:29:40 | synchronized@2026-10-24 13:29:40 | synchronized@2026-10-24 13:29:40 |
| tenancy-background | docs/wiki/tenancy-background.md | c4c1a2ceed37 | synchronized@2026-10-24 13:29:40 | synchronized@2026-10-24 13:29:40 | synchronized@2026-10-24 13:29:40 |
| workflow-principles | docs/wiki/workflow-principles.md | f9fff9b99b56 | synchronized@2026-10-24 14:08:55 | synchronized@2026-10-24 14:08:55 | synchronized@2026-10-24 14:08:55 |
| workflow-design | docs/wiki/workflow-design.md | cfc3d74c9aaa | synchronized@2026-10-24 14:08:55 | synchronized@2026-10-24 14:08:55 | synchronized@2026-10-24 14:08:55 |
| workflow-framework | docs/wiki/workflow-framework.md | 86d0413ebdcc | synchronized@2026-10-24 14:08:55 | synchronized@2026-10-24 14:08:55 | synchronized@2026-10-24 14:08:55 |
| workflow-models | docs/wiki/workflow-models.md | 9db64d09d78c | synchronized@2026-10-24 14:08:55 | synchronized@2026-10-24 14:08:55 | synchronized@2026-10-24 14:08:55 |
| workflow-assignment | docs/wiki/workflow-assignment.md | 307ee80c8dde | synchronized@2026-10-24 14:08:55 | synchronized@2026-10-24 14:08:55 | synchronized@2026-10-24 14:08:55 |
| workflow-tasks | docs/wiki/workflow-tasks.md | 895230430871 | synchronized@2026-10-24 14:08:55 | synchronized@2026-10-24 14:08:55 | synchronized@2026-10-24 14:08:55 |
| workflow-events | docs/wiki/workflow-events.md | a7171f7913b1 | synchronized@2026-10-24 14:08:55 | synchronized@2026-10-24 14:08:55 | synchronized@2026-10-24 14:08:55 |
| common-core | docs/wiki/common-core.md | 7a44762cb675 | synchronized@2026-10-29 20:41:16 | synchronized@2026-10-29 20:41:16 | synchronized@2026-10-29 20:41:16 |
| common | docs/wiki/common.md | 71dc91abc3e3 | synchronized@2026-10-29 20:41:16 | synchronized@2026-10-29 20:41:16 | synchronized@2026-10-29 20:41:16 |
| common-mybatis | docs/wiki/common-mybatis.md | fefa0f001f28 | synchronized@2026-10-30 07:09:17 | synchronized@2026-10-30 07:09:17 | synchronized@2026-10-30 07:09:17 |
| common-redis | docs/wiki/common-redis.md | 772ee04043c6 | synchronized@2026-10-29 20:52:31 | synchronized@2026-10-29 20:52:31 | synchronized@2026-10-29 20:52:31 |
| common-redis-reactive | docs/wiki/common-redis-reactive.md | 53a6419c47e2 | synchronized@2026-10-29 20:52:31 | synchronized@2026-10-29 20:52:31 | synchronized@2026-10-29 20:52:31 |
| common-service | docs/wiki/common-service.md | 739e8704e4c8 | synchronized@2026-10-29 20:52:31 | synchronized@2026-10-29 20:52:31 | synchronized@2026-10-29 20:52:31 |
| common-operlog | docs/wiki/common-operlog.md | 9a49b984e2e4 | synchronized@2026-10-26 10:23:57 | synchronized@2026-10-26 10:23:57 | synchronized@2026-10-26 10:23:57 |
| common-job | docs/wiki/common-job.md | d0b55ac1932d | synchronized@2026-10-25 20:15:03 | synchronized@2026-10-25 20:15:03 | synchronized@2026-10-25 20:15:03 |
| common-mqlog | docs/wiki/common-mqlog.md | 98e1a11b3e70 | synchronized@2026-10-26 10:23:57 | synchronized@2026-10-26 10:23:57 | synchronized@2026-10-26 10:23:57 |
| common-workflow | docs/wiki/common-workflow.md | c58da64bc102 | synchronized@2026-10-26 10:23:57 | synchronized@2026-10-26 10:23:57 | synchronized@2026-10-26 10:23:57 |
| usage-procurement | docs/wiki/usage-procurement.md | 1e7c483b75e9 | synchronized@2026-10-28 09:59:21 | synchronized@2026-10-28 09:59:21 | synchronized@2026-10-28 09:59:21 |
| usage-asset | docs/wiki/usage-asset.md | 515db0667366 | synchronized@2026-10-29 19:30:51 | synchronized@2026-10-29 19:30:51 | synchronized@2026-10-29 19:30:51 |
| observability-principles | docs/wiki/observability-principles.md | f32c77df68d3 | synchronized@2026-10-25 19:58:24 | synchronized@2026-10-25 19:58:24 | synchronized@2026-10-25 19:58:24 |
| observability-design | docs/wiki/observability-design.md | 8c54f3a7f90e | synchronized@2026-10-24 17:41:35 | synchronized@2026-10-24 17:41:35 | synchronized@2026-10-24 17:41:35 |
| observability-metrics | docs/wiki/observability-metrics.md | fda90a829dd5 | synchronized@2026-10-25 19:58:24 | synchronized@2026-10-25 19:58:24 | synchronized@2026-10-25 19:58:24 |
| observability-tracing | docs/wiki/observability-tracing.md | 6828791dc048 | synchronized@2026-10-25 19:58:24 | synchronized@2026-10-25 19:58:24 | synchronized@2026-10-25 19:58:24 |
| observability-alerts | docs/wiki/observability-alerts.md | 79e7469d124d | synchronized@2026-10-25 19:58:24 | synchronized@2026-10-25 19:58:24 | synchronized@2026-10-25 19:58:24 |
| observability-practice | docs/wiki/observability-practice.md | a465432a89dd | synchronized@2026-10-25 19:58:24 | synchronized@2026-10-25 19:58:24 | synchronized@2026-10-25 19:58:24 |
| logging-principles | docs/wiki/logging-principles.md | 91f194305b60 | synchronized@2026-10-24 23:29:12 | synchronized@2026-10-24 23:29:12 | synchronized@2026-10-24 23:29:12 |
| logging-design | docs/wiki/logging-design.md | 871b5de0b1c5 | synchronized@2026-10-24 23:29:12 | synchronized@2026-10-24 23:29:12 | synchronized@2026-10-24 23:29:12 |
| logging-pipeline | docs/wiki/logging-pipeline.md | 1ca43ab1ea44 | synchronized@2026-10-24 23:29:12 | synchronized@2026-10-24 23:29:12 | synchronized@2026-10-24 23:29:12 |
| logging-audit | docs/wiki/logging-audit.md | b7a95cc55818 | synchronized@2026-10-24 23:29:12 | synchronized@2026-10-24 23:29:12 | synchronized@2026-10-24 23:29:12 |
| logging-practice | docs/wiki/logging-practice.md | 45b92097818e | synchronized@2026-10-24 23:29:12 | synchronized@2026-10-24 23:29:12 | synchronized@2026-10-24 23:29:12 |
| logging-extension | docs/wiki/logging-extension.md | 4944308acba2 | synchronized@2026-10-24 23:29:12 | synchronized@2026-10-24 23:29:12 | synchronized@2026-10-24 23:29:12 |
| mq-principles | docs/wiki/mq-principles.md | e97a3669e4e4 | synchronized@2026-10-24 23:29:13 | synchronized@2026-10-24 23:29:13 | synchronized@2026-10-24 23:29:13 |
| mq-design | docs/wiki/mq-design.md | 38f1f9717133 | synchronized@2026-10-24 23:29:13 | synchronized@2026-10-24 23:29:13 | synchronized@2026-10-24 23:29:13 |
| mq-outbox | docs/wiki/mq-outbox.md | af57a88d53ec | synchronized@2026-10-24 23:29:13 | synchronized@2026-10-24 23:29:13 | synchronized@2026-10-24 23:29:13 |
| mq-relay | docs/wiki/mq-relay.md | 7e2e3707ba56 | synchronized@2026-10-24 23:29:13 | synchronized@2026-10-24 23:29:13 | synchronized@2026-10-24 23:29:13 |
| mq-practice | docs/wiki/mq-practice.md | d4cb680a99ad | synchronized@2026-10-24 23:29:13 | synchronized@2026-10-24 23:29:13 | synchronized@2026-10-24 23:29:13 |
| mq-extension | docs/wiki/mq-extension.md | a87a2447e1bc | synchronized@2026-10-24 23:29:13 | synchronized@2026-10-24 23:29:13 | synchronized@2026-10-24 23:29:13 |
| distributed-jobs-principles | docs/wiki/distributed-jobs-principles.md | 2cf3ac578916 | synchronized@2026-10-25 20:02:37 | synchronized@2026-10-25 20:02:37 | synchronized@2026-10-25 20:02:37 |
| distributed-jobs-design | docs/wiki/distributed-jobs-design.md | 1e62b54b714e | synchronized@2026-10-25 20:02:37 | synchronized@2026-10-25 20:02:37 | synchronized@2026-10-25 20:02:37 |
| distributed-jobs-framework | docs/wiki/distributed-jobs-framework.md | 9c2d110a9555 | synchronized@2026-10-25 20:02:37 | synchronized@2026-10-25 20:02:37 | synchronized@2026-10-25 20:02:37 |
| distributed-jobs-project | docs/wiki/distributed-jobs-project.md | e74ce5d764aa | synchronized@2026-10-25 20:02:37 | synchronized@2026-10-25 20:02:37 | synchronized@2026-10-25 20:02:37 |
| distributed-jobs-admin | docs/wiki/distributed-jobs-admin.md | d6203409d7a1 | synchronized@2026-10-25 20:02:37 | synchronized@2026-10-25 20:02:37 | synchronized@2026-10-25 20:02:37 |
| distributed-jobs-usage | docs/wiki/distributed-jobs-usage.md | adf4ac7a54c7 | synchronized@2026-10-25 20:02:37 | synchronized@2026-10-25 20:02:37 | synchronized@2026-10-25 20:02:37 |
| distributed-jobs-recovery | docs/wiki/distributed-jobs-recovery.md | 0dfdf8cc71b9 | synchronized@2026-10-25 20:02:37 | synchronized@2026-10-25 20:02:37 | synchronized@2026-10-25 20:02:37 |

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
