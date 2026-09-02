# G7 文档门走查证据（2026-09-01）

## 走查环境

- 分支：`codex/scaffold-upgrade`，HEAD `efd651e`
- 本地栈：前端 dev server `:3000`，网关 `:8102` 健康
- 走查依据：`docs/guides/quick-start.md`（zh-CN）

## 新手走查结果（5/5 PASS）

| 步骤 | 结果 | 证据 |
| --- | --- | --- |
| 1 中文登录页渲染（租户/用户名/密码/验证码/登录方式） | PASS | walkthrough-1-login-zh.png |
| 2 English 标题 `Authorization Center` | PASS | walkthrough-2-login-en.png |
| 3 日本語标题 `認証センター` | PASS | walkthrough-3-login-ja.png |
| 4 demo seed 首次登录（admin + 图形验证码 GGB3，一次通过） | PASS | — |
| 5 登录后侧边栏含系统管理/基础数据/任务调度/运维监控/工作流管理；欢迎卡与文档一致 | PASS | walkthrough-4-dashboard.png |

截图位于本目录，属走查证据，不进入 screenshot-manifest。

## 走查发现与处置

1. 登录默认落地为审批工作台（无侧边栏），需点击右上角「控制台」进入管理仪表盘
   → 已同步修正 `docs/guides/quick-start.md` 图 1 的操作描述。
2. 初报的「日文界面语言菜单当前项显示简体中文」经截图复核为误报：菜单中「简体中文」是未选中的 zh 选项（正常展示），右上角按钮正确显示当前语言短标签「🌐日」，当前项 disabled 逻辑正常。
3. 落地页「我要喝水」彩蛋与 Dashboard「未接入统一 Metrics」提示均为预期行为。

## G7 分项状态

| 分项 | 状态 | 说明 |
| --- | --- | --- |
| README 事实（zh/en/jp/kr） | 通过 | Liquibase/种子/五预设表述一致，init-all.sql 指导性引用清零 |
| 新手走查 | 通过 | 5/5 PASS，发现项已回写文档 |
| 四语言文档人工复核 | 阻塞 | 114 篇 present-unverified，复核队列见 `docs/i18n-review-queue.md`，需实际复核人确认 |
| 全流程深度截图 | 阻塞 | 详情/编辑/关键动作/成功/失败状态截图需受控 `E2E_ADMIN_TOKEN` 环境；ja-JP/ko-KR 审批规则图需重拍 |

## 关闭条件

- 人工复核队列 114 篇全部 synchronized 且 `docs:i18n:check` 转绿；
- 深度截图补齐并通过 `docs:screenshots:check`（12 项覆盖状态检查转绿）；
- 二者完成后 G7 可宣布关闭。
