# ZCode GLM-5.3-Flash Goal 执行提示词

在 ZCode 中选择 **Goal Mode** 和 **GLM-5.3-Flash**，打开仓库 `C:\WorkSpace\QODER\Omni-Stack`，然后完整发送下面的提示词。不要使用 Qoder 的 Quest Agent 或 `/goal` 语法。

建议同时通过 `@` 引用：

- `@docs/evidence/scaffold-upgrade/zcode-glm53-flash-execution-plan-2026-09-04.md`
- `@docs/evidence/scaffold-upgrade/qoder-continuous-progress-2026-09-03.md`

```text
继续 Omni-Stack 脚手架升级的验收整改，但本轮只执行适合 GLM-5.3-Flash 的低风险批次。

仓库：C:\WorkSpace\QODER\Omni-Stack
预期分支：codex/scaffold-upgrade
已知接管基线：52b2c5cd693479a3cab994ddac4427478a24a554

本轮唯一实施入口：
docs/evidence/scaffold-upgrade/zcode-glm53-flash-execution-plan-2026-09-04.md

过程证据：
docs/evidence/scaffold-upgrade/qoder-continuous-progress-2026-09-03.md

完整阅读唯一实施入口。旧 post-qoder 方案和 progress 只按新方案指定的小节读取；不要重新做全仓审计、重新制定计划、重复调查已冻结结论或重跑输入未变化的检查。

本轮依次执行 ZF-0 → ZF-1 → ZF-2 → ZF-3 → ZF-4：

ZF-0：只做一次最小 Git、双远端和 Docker 简表核验；统一 progress 顶部 current state，把矛盾旧段落标记 SUPERSEDED_BY_CURRENT_STATUS。首个精确文档提交纳入 post-qoder 方案、Qoder 历史提示词、本 ZCode 方案和本提示词。不得提交或删除其他 excluded untracked 文件。

ZF-1：复用已有 parity 输出，对 findings 一次建队列，按 EXPECTED_LOCALIZATION / FIX_REQUIRED / INDEPENDENT_REVIEW_REQUIRED 分类。先修 api-contract 的 MQ runtime API/权限码和 guide-authentication 的会话过期章节/图片，再处理 API、权限码、字段、枚举、命令、代码块、数字、表格和步骤等客观遗漏。8～12 个中文源为一批，批末统一跑 parity、links、sensitive、i18n --allow-draft。禁止改阈值隐藏问题，禁止批量填写 synchronized/reviewed_at，禁止宣称已经独立语义复核。

ZF-2：只闭环 SRM admission-lifecycle、detail-and-action-states 两组低风险截图；已有有效图片不重拍。先用 manifest/coverage/已有测试定向定位。运行 mutations 前记录 tenant、runStamp、业务键、创建 ID 和清理契约；四语言同 fixture、同步骤、同视口、0 skipped；逐图检查语言、关键状态、遮挡、空白和测试残留；同步更新图片、manifest、coverage、对应指南和摘要；只清理本轮登记数据。

同时只读核实 system-management 的 config/login-record 页面承载情况，以及 scaffold-development、operations 的已有产品能力和证据入口。已有能力记录真实入口；不存在则登记 PRODUCT_GAP；证据类型不明确则登记 EVIDENCE_FORMAT_DECISION。不得为了截图造 UI、删 required flow、改 coverage 门槛或自行 exempt。

ZF-3：只验证实际改动。i18n 每批运行规定的文档门禁；SRM 稳定后运行修改过的 Playwright 套件和 screenshot strict；最终统一运行一次 frontend build、lint 和适用 docs gates。只有实际修改后端代码才跑 JDK25 + Maven Wrapper backend clean install。不得重跑所有截图或未受影响的全矩阵。记录真实命令、退出码、通过/失败数和最多 50 行错误摘要。

ZF-4：每个稳定批次精确 stage 和小提交，禁止 git add .。提交前检查 staged 文件清单、git diff --cached --check 和敏感信息。push 前只做一次 fast-forward/remote SHA 核验；分叉立即 REMOTE_DIVERGENCE_STOP，禁止 rebase/merge/force push。更新 current state 和 Flash 验收摘要，输出提交 SHA、实际验证、遗留事项和下一恢复入口，然后停止。

明确禁止执行并写入 STRONG_MODEL_QUEUE：
- tenant=1、ids=1..13 的采购 bootstrap 数据恢复或其他历史数据修复；
- Liquibase、schema、seed adoption baseline 修改；
- procurement comparison/PO/GR、asset 全链跨服务 E2E；
- workflow lifecycle/failure/countersign、多身份或候选人作用域；
- MQ retry/dead-letter、共享中间件故障注入；
- G1/G7/WP-10/G8、最终全矩阵和最终 Gate 定级；
- 独立翻译语义签核和 reviewed_at；
- PRODUCT_GAP 开发、coverage 放宽、required flow 删除；
- 历史 patch、脚本、debug 图片、.artifacts 或其他 excluded untracked 清理。

遇到上述高风险项，不猜测、不实现；记录目标、证据、风险、涉及文件和推荐恢复入口。其他不依赖它的 ZF 工作继续。只有排除项成为唯一剩余工作时，输出 FLASH_BATCH=ACTION_REQUIRED_MODEL 并停止。

Token 与防跑偏约束必须执行：
- 总估算 70K～134K；总软上限 105K、硬上限 145K；无可信计数时写 TOKEN_USAGE=UNKNOWN，禁止编造。
- 只完整读取新方案和本提示词；使用 rg、manifest、coverage 和精确行范围，不扫描全仓、不重读完整历史。
- 同一 Git/Docker/门禁状态在输入未变化时只检查一次；复用已有输出。
- 8～12 个 i18n 源为一批，批末才运行全局工具；只回查失败文件。
- 日志默认最多读失败附近 50 行；完整日志写排除目录，不进入 checkpoint。
- 同一失败最多 3 轮有新证据的 targeted 修复；无新证据则 BLOCKED，不得反复换命令碰运气。
- 长命令正常运行时复用原 session；每次等待不超过 60 秒，不重启、不并行重复执行。
- 禁止自动多 Agent；共享 Docker、数据库和 E2E mutations 串行。
- 接近批次硬上限时先完成数据/凭证清理、已通过成果提交和 checkpoint；不得省略验证来宣称完成。
- Token 仍有余额不构成扩展本轮范围的授权。

安全约束：
- 禁 reset/restore/checkout/stash/clean/rebase/merge/amend/force push；禁 git add .。
- excluded untracked 不提交不删除。
- 认证只走现有 E2E fixture 和已授权身份；禁 CAPTCHA bypass、Redis Token 查询、Token 输出、读取 .env.before-rebuild-*。
- Token 只通过进程环境变量传递；交接中不得出现 Token 值或敏感数据库整行。
- 只清理本轮有 runStamp/业务键登记且有明确契约的数据。
- 文档与实际冲突时以代码、Git 和真实验证为准，并记录冲突。

每个 ZF 写 checkpoint：DONE/PARTIAL/BLOCKED、变更文件、真实验证与退出码、数据/凭证清理、commit/remote SHA、Token 用量或 UNKNOWN、下一恢复入口。

只有 ZF-0～ZF-4 全部满足新方案验收条件，才能输出 FLASH_BATCH=CLOSED。否则只能输出 FLASH_BATCH=PARTIAL_CHECKPOINT、FLASH_BATCH=BLOCKED、FLASH_BATCH=ACTION_REQUIRED_MODEL 或 FLASH_BATCH=REMOTE_DIVERGENCE_STOP。

无论本批结果如何，禁止输出 OVERALL_GOAL=COMPLETE。完成本批后停止，不自动执行强模型队列，不循环等待；后续由 Codex 独立验收。
```

## 极简续跑入口

若 ZCode 因网络或会话中断，但已有 checkpoint，可在新的 Goal Mode 会话中发送：

```text
按 @docs/evidence/scaffold-upgrade/zcode-glm53-flash-execution-plan-2026-09-04.md 和最新 current state，从第一项未完成的 ZF 小步继续。先复用已有提交、测试和日志证据，不重跑已通过且输入未变化的检查；严格遵守 Flash 排除范围、Token 约束和最终状态定义。
```
