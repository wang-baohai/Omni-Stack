# Qoder 验收整改 Goal 执行提示词

将下面代码块完整发送给 Qoder。IDE 使用 Quest Agent；CLI 可把首行改成 `/goal` 目标。不要编辑并重交之前已经产生代码的旧消息。

```text
继续 Omni-Stack 脚手架升级的验收整改任务。

仓库：C:\WorkSpace\QODER\Omni-Stack
预期分支：codex/scaffold-upgrade
已验收基线：52b2c5cd693479a3cab994ddac4427478a24a554

唯一实施方案：
docs/evidence/scaffold-upgrade/post-qoder-acceptance-remediation-plan-2026-09-04.md

过程证据（只按方案指定小节读取，不作为最终状态直接照抄）：
docs/evidence/scaffold-upgrade/qoder-continuous-progress-2026-09-03.md

先完整阅读唯一实施方案，然后执行 WP-0→WP-5。不要重新做全仓审计、重新设计计划或重复运行已通过且输入未变化的 Stage A 检查。

目标不是“必须宣布全部完成”，而是：
1. 统一当前交接事实；
2. 修复可客观验证的 i18n 缺漏并形成独立复核包；
3. 在精确事务前置全部满足时恢复 tenant=1、ids=1..13 的 bootstrap 采购品类；
4. 分模块关闭所有当前可安全构造的截图 gaps；
5. 对剩余产品、身份、隔离和证据形态问题形成决策包；
6. 对实际变更执行常规构建与质量验证；
7. 最终停在 WAITING_EXTERNAL_INPUT、PARTIAL_CHECKPOINT 或 BLOCKED 的真实状态。

已冻结事实：
- Stage A supplier quotation 已由提交 653afe3 完成，不重做；只有相关代码变化才重跑。
- 当前 HEAD 应为 52b2c5c；Gitee/GitHub 当时一致。接管时只做一次最小实时核验。
- tracked 工作区应干净；untracked 排除项不提交、不删除。
- Docker 已有 15 running/14 healthy，frontend 无 healthcheck；不要无故重建全栈。
- screenshot strict 当前应为 8 red、36 gaps。
- i18n strict 当前应为 204 red；102 个 present-unverified，独立复核前不得标 synchronized/reviewed_at。
- parity 的 102 ALIGNED 是宽松结构分类：74/102 仍有 findings，不能宣称 100% 内容完整。
- P0 已知缺漏：api-contract 三语缺 GET /api/base/mq-message/runtime 与 base:mqmessage:list；guide-authentication 会话过期章节/图片不同步。
- G1/G7 仍 PENDING，WP-10/G8 仍 LOCKED。

执行细则：

WP-0：最小核验 Git/双远端/Docker，把 checkpoint 改为唯一 current state。旧段落保留但标 SUPERSEDED；修正旧 SHA、旧 Stage B/C 待办、旧 stable-mobile-flow、错误的“全程纯文档”结论。将两份 2026-09-04 新文档纳入首次精确提交。

WP-1：复用现有 parity JSON，将 74 个 findings 按 EXPECTED_LOCALIZATION / FIX_REQUIRED / INDEPENDENT_REVIEW_REQUIRED 逐项分诊，写 i18n-findings-resolution-2026-09-04.md。先修 P0，再处理关键 API、权限、字段、枚举、命令、代码块、数字和表格遗漏。8～12 个源为一批；批末统一跑 parity、links、sensitive、i18n --allow-draft。不改阈值隐藏问题，不批量填写 synchronized/reviewed_at，不将自己视为独立复核者。

WP-2：只读核对 seed/changelog/manifest/运行库；仅当 tenant=1、ids=1..13、category_code 集合、deleted=1、update_by IS NULL、无 active 冲突全部精确匹配时，在单事务中锁定并撤销这 13 行的 soft-delete。影响行数或前后集合不是恰好 13就回滚并报告 DATA_RESTORE_PRECONDITION_MISMATCH。恢复后运行 procurement-default-config 定向 verifier，预期 14=14。不执行 adopt-current，不关闭 G1，不碰其他历史数据。本提示明确授权这一项符合全部前置时的精确恢复；不授权范围扩大或部分恢复。

WP-3：按方案 3A→3C 分批。已有有效图片不重拍。每批先建立 tenant/runStamp/businessKey/ID/清理契约，再启动 mutations；四语言必须同 fixture/步骤/视口、0 skipped、真实功能/状态、图片质检、数据自清理、manifest/coverage/指南/摘要联动。正式图片不能混入其他语言 fixture 或无关历史数据。共享 Flowable/MQ 无安全清理路径时先停该项并形成决策包。

优先可执行顺序：SRM admission/detail → procurement requisition/RFQ/quotation → comparison/PO/GR → asset receipt/ledger/lifecycle。workflow 多身份只允许为已有正确角色用户签发短期 Token，不临时越权。MQ retry/dead-letter 优先独立 Compose/临时 volume，禁止污染共享 relay。不存在的 system/CLI/operations 产品能力登记 PRODUCT_GAP，不能造 UI、删 required_flow 或自行 exempt。

WP-4：实际改动稳定后，按 AGENTS 用 JDK25+Maven Wrapper 跑一次 backend clean install，跑 frontend build/lint，并统一跑 docs gates。只对被修改的 E2E 做最终组合回归，不重新生成所有截图。预期红项必须按真实退出码列出，不得汇总成 PASS。本步骤不是 G8。

WP-5：生成独立 i18n 复核包、G1 运维包和各类最小决策问题；把 checkpoint 更新为当前唯一事实。只剩外部输入时标 WAITING_EXTERNAL_INPUT 后停止，不轮询、不自动进入 WP-10/G8。

Token 与防跑偏：
- 按实施方案每个 WP 的估算和软/硬上限执行；平台无法统计时写 TOKEN_USAGE=UNKNOWN，不编造。
- 先 rg/manifest/coverage 精确定位，只读取必要行；不重复整读旧交接、全仓 diff 和历史。
- 文件级定向检查，8～12 个源或一个模块后再跑一次批量 gate；输入未变不重复。
- 日志默认只读错误附近 50 行，完整日志放排除目录；禁止输出 Token、环境变量或数据库敏感行。
- 同一失败连续三次无新证据即标 BLOCKED，记录最后命令/session/恢复入口，转做独立工作。
- 长命令正常运行就复用原进程并退避查询，不终止、不重复启动、不无限空轮询。
- 每个模块或文档批次验收后更新一次 checkpoint 并做一个可审查的小提交，不制造跨领域巨型 diff。
- 达到 WP 软上限约 75% 时停止扩展，先完成凭证/数据清理、checkpoint 和已验收提交；下轮按恢复入口继续。
- 不自动启用多 Agent；共享 Docker/数据库写入串行。

Git/安全：
- 禁 reset/restore/checkout/stash/clean/rebase/merge/amend/force push，禁 git add .。
- 排除项不提交不删除，现有成果不回滚。
- 每批显式 stage；提交前 cached name-only/check/secret scan。
- push 前一次 fast-forward 证明；任一远端不一致且无法证明安全时 REMOTE_DIVERGENCE_STOP。
- 禁 CAPTCHA bypass、Redis 登录 Token 查询、打印 Token、读取 .env.before-rebuild-*。
- 数据写入前必须登记归属，异常也执行同批清理；未知历史数据不处理。

外部边界：
- 不代替独立复核者填写 reviewed_at/status；不代替运维批准 G1。
- 不自动开发 PRODUCT_GAP，不自动批准多身份或共享故障注入。
- 不清理历史临时文件/旧凭证/历史 E2ESQ，不执行 WP-10，不执行 G8。
- G1/G7/WP-10/G8 未满足时禁止输出 OVERALL_GOAL=COMPLETE。

每个 WP 输出并写入 checkpoint：DONE/PARTIAL/BLOCKED、涉及文件、验证命令与真实结果、数据/凭证清理、commit/remote SHA、估算或 UNKNOWN 的 Token 用量、下一条具体恢复操作。

最终只允许：
- WAITING_EXTERNAL_INPUT：全部当前可执行项完成，只剩逐条列明的外部决定；
- PARTIAL_CHECKPOINT：平台/上下文限制前安全收口；
- BLOCKED：关键依赖阻塞且没有独立工作可继续。

现在从 WP-0 开始执行，不再停留在重复验收或重新规划。
```

## CLI Goal 简短入口

在 Qoder CLI 已选择正确工作目录、模型和安全权限后，可发送：

```text
/goal 按 docs/evidence/scaffold-upgrade/qoder-remediation-goal-prompt-2026-09-04.md 的完整提示词执行 WP-0 到 WP-5；分批验收并维护 checkpoint，直到 WAITING_EXTERNAL_INPUT、PARTIAL_CHECKPOINT 或真实 BLOCKED，禁止提前标记原始升级目标完成
```
