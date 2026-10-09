# 内置浏览器步骤映射（WP-014 新增）

本目录是 **additive** 层，不替换、不删除 `omni-frontend/e2e-docs/flows/` 下的历史 Playwright 断言源。

## 为什么需要
- 用户合同要求：所有界面操作、语言切换、视口验证与截图只使用 Qoder 内置浏览器。
- `npm run test:e2e` / `docs:screenshots` / `playwright test` 会启动外部浏览器，本轮 **不得执行**，
  其状态如实记 `NOT_RUN_PER_USER_BROWSER_CONSTRAINT`，不能伪造 exit 0。
- 历史 flows 里的稳定 case/step ID 与断言仍有效，因此把它们转写为声明式步骤，
  交由内置浏览器逐步骤真实执行并产生收据（`steps[].step_id` + 观察值）。

## 约定
- `legacy_case_ref` 指向断言源：`omni-frontend/e2e-docs/flows/<file>.spec.ts#<test title>`。
- `value_ref` 只写引用名（如 `cred.admin`、`fixture.inviteToken`），**禁止**写入口令、JWT、Cookie、
  Authorization、验证码答案或任何真实秘密。
- `runner.css_viewport_gate` 为本轮实测门；无精确视口能力时为 `BLOCKED`，
  相应格子不得申请正式发布（详见执行证据 `builtin-browser-capabilities.json`）。
- 步骤实例（含真实 URL、端口、构件与运行时绑定）属执行期证据，写在
  `.qoder/wp014-evidence/<executionId>/s04/builtin-step-map.json`，不入库。

