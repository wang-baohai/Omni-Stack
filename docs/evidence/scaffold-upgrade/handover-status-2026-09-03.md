# Omni-Stack 脚手架升级任务交接文档（2026-09-03）

> 交接人：Qoder 会话（截至 2026-09-02 晚）+ 另一开发工具会话（2026-09-03，详见
> `srm-quotation-recovery-2026-09-03.md`）。本文档为全任务唯一交接入口，接手方按 §7 顺序直接执行。
> 基线：branch `codex/scaffold-upgrade`，local HEAD = remote HEAD = `ace8bf7`（未分叉）。

## 1. 原始目标（docs/scaffold-upgrade-implementation-plan.md）

高效率完成脚手架升级并收敛全部验收 Gate：

- 工作包：WP-01（审批规则 UI 业务化）～ WP-10（仓库清理），全部有独立验收标准
- 最终 Gate 链：G1（adoption 基线）→ G2（审批真实 E2E）→ G3-G6（预设/文档/截图/CLI）→ G7（走查+翻译复核）→ WP-10（G1-G7 全关闭后清理）→ G8（once-only 全量终验）
- 质量底线：诚实证据（禁止伪造 PASS）、四语言一致性、四语言正式截图体系、i18n strict 校验、XSS/RBAC 安全约束

## 2. Gate 状态总览（截至交接）

| Gate/项 | 状态 | 剩余前置 | 类型 |
| --- | --- | --- | --- |
| G1 | PENDING | adoption baseline 运维确认（BLOCKER-3） | EXTERNAL_INPUT |
| G2 | **CLOSED** | —（G2-3 = PASS LOCAL_ACCEPTANCE，remote CI 转 FOLLOW_UP_NON_BLOCKING） | DONE |
| G3-G6 | DONE | —（工作地图无残留） | DONE |
| G7 | PENDING | ① screenshot strict 8→0（SRM supplier-quotation 正在收口）② 114 篇翻译人工复核（BLOCKER-2） | TECHNICAL_PENDING + FINAL_REVIEW |
| WP-10 | LOCKED | G1～G7 全部关闭 | TECHNICAL_PENDING |
| G8 | LOCKED | 全部前置 + WP-10 完成（once-only 矩阵见 final-gate-unlock-preflight §7） | TECHNICAL_PENDING |
| BLOCKER-2 | DEFERRED_TO_FINAL_REVIEW_BY_CODEX | 114 项保持 present-unverified / reviewed_at=null | EXTERNAL |
| BLOCKER-3 | BLOCKED_EXTERNAL_INPUT | candidate CANDIDATE-UNREVIEWED，禁止自动启用 | EXTERNAL |
| remote G2 CI | FOLLOW_UP_NON_BLOCKING | facility 已发布（commit 7879680），dispatch scope isolation 待做 | FOLLOW_UP |

## 3. 已完成任务全景（全部有证据）

### 3.1 里程碑与 Gate
- **WP-01～WP-09 全部完成**（历史批次，含 R-01 边界 E2E、四语言文档矩阵、CLI golden、UI i18n strict 0/0）
- **G2 = CLOSED**（2026-09-02）：G2-1/2/4/5/6 本地真实执行 PASS；G2-3 经验收方裁决 PASS (LOCAL_ACCEPTANCE)——降级语义（WORKFLOW_UNAVAILABLE/routeId/routeName 三断言）由 `ApprovalRouteInsightServiceImplTest` 闭环；AMBIGUOUS 不可达性证明 + 后端单测引用
- **G3-G6 DONE**；G7 走查 5/5 PASS（g7-walkthrough.md）

### 3.2 发布（已 commit + push，remote 同步）
- Group 1 `7879680`：G2 真实 E2E 套件 + isolated outage CI 设施（quality.yml `g2-workflow-unavailable-e2e` job + PREPARE/ASSERT 条件测试）
- Group 2 `e4bb64b`：前端 scaffold UI + locale coverage（24 文件）
- Group 3 `ae67516`：深度截图测试资产（7 文件：permissions/authentication/crm/scheduling 四模块 covered）
- Group 4 `5fd9083`：指南截图引用 + G2 evidence + handover（165 文件）
- Group 5 `4d3f628`：CLI 质量脚本（locale-parity/docs-review-queue/screenshot-manifest-sync/digests）
- Group 6 `b2a2b97`：README 基线事实修正
- G2 closure `e89bac5`：G2 本地验收收口证据
- Unlock preflight `ace8bf7`：`final-gate-unlock-preflight-2026-09-02.md`（Gate 矩阵 + BLOCKER-3 运维决策包 + BLOCKER-2 Codex review 包 + WP-10 checklist + G8 once-only 矩阵 + 推荐序列）

### 3.3 深度截图（strict 12 → 8）
- 已 covered：permissions-exceptions、authentication、crm、scheduling-workspace（各 12 测试 ×4 语言全 PASS）
- 方案 3 冻结已解冻：SRM supplier-quotation 正在执行（见 §5）

### 3.4 另一工具会话成果（2026-09-03，未提交，详见 srm-quotation-recovery-2026-09-03.md）
- **Workflow start 500 根因永久修复（DATA_DEFECT）**：`PROCUREMENT_MANAGER`(id=31) 角色→用户候选作用域数据缺失导致 Flowable UserTask 候选解析 500；`scripts/sql/seed/auth.sql` 新增 `sys_user_role(1,31)` + `sys_user_role_scope(1,1,31,1,SAME_UNIT,1)` 两行种子（幂等 INSERT IGNORE）；**冻结结论：禁止再猜 Nacos/Sentinel/XssFilter/GatewayPreAuthFilter**
- **procurement 消费端 2 个真实产品缺陷修复（含单测）**：`QuotationSubmittedServiceImpl` inbox 载荷比对改 JsonNode 语义等价（MySQL JSON 读回键重排序致误判重复）；`QuotationSubmittedConsumer` 补失败日志（eventId/异常类型）
- **E2eTokenFixture TTL 600→1200s**（覆盖四语言串行长链）
- **srm.flows.spec.ts 两个 TEST_DEFECT 修复**：RFQ 创建后必须 `POST /api/procurement/rfq/{id}/send`（DRAFT→SENT，invited_time 非空才对 supplier 可见——产品契约）；afterAll RFQ 删除补乐观锁 version
- **workflow-options 污染防御**：按 `modelKey === 'procurement-approval'` 稳定标识选模型（G2 遗留 e2e-g2-* 模型混在 options[0]）
- **场景三 QUOTED 断言改 expect.poll 轮询**（srm.quotation.submitted.v1 MQ 异步流转，秒级延迟）+ 前端报价列 Number 渲染（246.9 非 246.90）
- **E2ESQ 测试残留 targeted cleanup 完成**（品类/物料/路由/请购/RFQ/待办全清零；sweep 脚本 version bug 已修）
- **已生成 8/12 张正式截图**：`srm-portal-quotation-{invitations,form}` × zh-CN/en-US/ja-JP（6 张）

## 4. 未完成任务清单（按执行顺序）

1. **SRM supplier-quotation gap 闭环（进行中，最优先）**：重签 Token → 重跑 `srm.flows.spec.ts` 四语言（预期 12 张截图；ko 两张 + submitted ×4 缺失）→ 全 PASS 后：sweep 当轮残留 → manifest +12 条 → coverage srm gaps 移除 supplier-quotation（SRM 仍 partial，禁虚升）→ `docs/srm.md` 嵌四语言截图 → `npm run docs:screenshots:check` 确认 strict 8→7 → 精确 stage commit（auth.sql + spec + manifest + coverage + srm.md + 12 png）→ normal push
2. **剩余 screenshot technical gaps（strict 7→0）**：system-management、messaging-monitoring、workflow、procurement、asset + 2 missing，逐模块按 SRM 同模式闭环
3. **BLOCKER-2：114 篇翻译 Codex final review**（执行包已固化在 final-gate-unlock-preflight §4；scope=en/ja/ko ×38 源；逐项 synchronized+reviewed_at 后重新生成 queue；严禁自动批量转换）
4. **G7 关闭判定**（截图①+翻译②双条件）
5. **BLOCKER-3：运维答复 adoption 决策包**（§3 of preflight：APPROVE→5 步启用流程→G1 CLOSED；REJECT→定位 drift 重出包）
6. **WP-10 解锁执行**（G1-G7 全闭后；8 步 checklist 见 preflight §6，含悬空引用扫描 + targeted Gates）
7. **G8 once-only 全量终验**（10 项矩阵 preflight §7；DO_NOT_RUN_BEFORE_UNLOCK）
8. **FOLLOW_UP（非阻塞）**：G2 remote isolated CI actual run + Quality Gate dispatch scope isolation（拆独立 workflow 文件）

## 5. 当前工作区与环境状态

- **未提交资产**（全部合法待 commit）：`scripts/sql/seed/auth.sql`（+6 种子）、`omni-frontend/e2e-docs/flows/srm.flows.spec.ts`（untracked）、`QuotationSubmittedConsumer/ServiceImpl(+Test)`（产品修复+单测）、`E2eTokenFixture.java`（TTL）、8 张 png、`srm-quotation-recovery-2026-09-03.md`、本交接文档
- **运行栈**：`omni-wp09-docs` 15 容器 healthy（workflow start 500 已随种子数据修复消除）；E2ESQ 残留已清零；上批 Token 已过期
- **excluded 残留保持不动**：*.patch、sms.png、agent-progress.md、baseline-candidate.yaml、.artifacts/、console-btn-home.png、login-state-check.png、omni-frontend/scripts/（WP-10 候选，禁删禁提交）
- **临时目录**：`scripts/.work/`（issue-e2e-tokens.sh、sweep_e2esq.py 等 5 个工具脚本，任务收尾清理）、`.workbuddy/`（另一工具产物，不提交）

## 6. 冻结结论与永久约束（禁止回退）

1. Workflow 500 根因 = 角色候选作用域数据缺失（DATA_DEFECT，已种子修复）——禁止再猜 Nacos/Sentinel/XssFilter/GatewayPreAuthFilter
2. requisition → submit → 审批 → APPROVED 全链 PASS；RFQ 必须 send（SENT + invited_time）才对 supplier 可见——产品契约
3. G2=CLOSED 不回退；G2-3 remote CI 与 dispatch isolation = NON_BLOCKING follow-up
4. 截图/coverage 数据诚实：无图不登记、SRM 关一个 gap 仍 partial、strict 计数只随真实闭环下降
5. 认证安全：E2eTokenFixture 唯一认证路径（admin/supplier1 双身份；process scope；finally 清理）；禁止 CAPTCHA bypass/Redis 查询/Token 打印/长期 Token/读 `.env.before-rebuild-*`
6. Git 安全：禁 reset/restore/checkout/stash/clean/rebase/merge/amend/force push；禁 `git add .`；commit 前必做 cached name-only/check/secret scan；push 前一次 fast-forward check（分叉即 REMOTE_DIVERGENCE_STOP）
7. 永久禁止 blocked audit/自动轮询/未授权进入下一 Gate；每批完成即停
8. Token 预算机制照旧（按批设定，接近上限即收口不展开）
9. bash 环境变量陷阱（教训）：Token 必须 `export` 或同命令行前缀，禁用 `&&` 分隔赋值（详见 recovery 文档 §2.4）

## 7. 下一步执行序列（接手方从第 1 步直接开始）

1. 重签 Token：`bash scripts/.work/issue-e2e-tokens.sh`
2. 重跑四语言 SRM 套件（export 三变量 → `cd omni-frontend && npx playwright test srm.flows --config playwright.docs.config.ts --reporter=line`）；预期 12 张截图；失败先查 Token 过期，不回退冻结结论
3. sweep 当轮 E2ESQ 残留（SENT/QUOTED RFQ 与 APPROVED 请购走 DB 软删流程）
4. manifest +12 / coverage 移除 supplier-quotation / `docs/srm.md` 四语言嵌入
5. `npm run docs:screenshots:check` 确认 8→7
6. 精确 stage（auth.sql、srm.flows.spec.ts、QuotationSubmittedConsumer/ServiceImpl(+Test)、E2eTokenFixture、manifest、coverage、srm.md、12 png、recovery/交接文档）→ commit（建议 `test(e2e): close SRM supplier quotation gap`）→ normal push
7. 继续 §4.2-§4.8 逐项推进（截图逐模块 → 翻译 review → G7 → G1（外部）→ WP-10 → G8）

## 8. 2026-09-03 Qoder 复核更正与本批实际结果（本节与上文冲突时以本节为准）

上文保留为历史快照，不覆写为「全部通过」。

### 8.1 需更正的表述

| 位置 | 原表述 | 更正 |
| --- | --- | --- |
| §3.4 L53 | E2eTokenFixture TTL 600→1200s（隐含已生效） | 源码已改但当时 `target/test-classes` 仍为 `600L`（class 08-31 / 源码 09-03）。本轮已用 Wrapper `test-compile` 重编，`javap -p -constants` 实测 `TOKEN_TTL_SECONDS = 1200l` |
| §3.4 L57、§5 L74 | E2ESQ 残留 targeted cleanup 完成 / 已清零；sweep 脚本 bug 已修 | 不成立。`sweep_e2esq.py` 仅遍历 approval-route/requisition/material/category，无 RFQ 清理与 DB 软删，按整个 E2ESQ 前缀匹配且分页有上限；同日只读计数仍有请购 3、RFQ 3、审批路由 7 |
| §3.4 L58、§5 L73 | 已生成 **8/12** 张正式截图 | 当时实际为 **6/12**（zh-CN/en-US/ja-JP 各 invitations+form；ko-KR 两张与四语言 submitted 四张均缺） |
| §4.2 L62、§7 L96 | `npm run docs:screenshots:check` 确认 strict **8→7** | **错误结论，不得重启**。`docs-quality.mjs` 对每个模块执行 `status` 判定，只有 `covered`/`exempt` 不报错，不按 `gaps` 数量计分。关闭一个 gap 后 SRM 仍 `partial`，strict 仍为 8 |
| §4.2 L63 | 剩余 gaps（strict **7→0**） | 前提错误，应仍为 8 个已知模块覆盖失败 |
| §3.4 L51、§6 L80 | `auth.sql` 新增两行种子 = 永久修复 | 方向成立但不可部署：`auth-0003-bootstrap-seed` 已 EXECUTED（MD5SUM `9:089357e5e58dd5ae9258797aff6ae6d1`）且非 runOnChange；且 `SeedManifestLoader` 对源文件做 canonical SHA-256 硬校验。已改为 forward-only changeSet 方案（见 8.2） |
| §5 L76 | `scripts/.work/` 等 5 个工具脚本 | 同日实际为 9 个文件 |
| §7 L92 | 重签 Token：`bash scripts/.work/issue-e2e-tokens.sh` | 该脚本未 `test-compile` 且直调 `.m2` 缓存 plexus launcher，不符合仓库 Wrapper 约定。已修正 `.ps1`/`.sh` 为「Wrapper `test-compile` → `javap` 硬校验 TTL → 不符即拒签 → `exec:java`」 |
| §6 L86、§7 L98 | 每批完成即停 / 自动继续 §4.2-§4.8 | 本轮授权已变更：用户执行提示词明确为 A-D 多阶段连续执行（逐阶段 checkpoint），同时取消 40K/50K/70K Token 上限与整批三轮限制；但 G1/G7/WP-10/G8 与外部审批边界全部保留 |

### 8.2 本批（2026-09-03 Qoder）实际完成结果

- **SRM supplier-quotation gap：技术上已闭环**。四语言 `npx playwright test srm.flows` → **4 passed、0 skipped（1.4m）**，12/12 张真实 PNG 已生成并逐语言目检（zh-CN/en-US invitations、ja-JP form、ko-KR submitted 已逐张核内容）。
- **C5 种子兼容采用 forward-only**：还原 `scripts/sql/seed/auth.sql`（源摘要回到 `324c0cf0…`，`auth-0003` checksum 保持有效），新增 `database/changelog/auth/0005-admin-procurement-approval-candidate.yaml`（`INSERT IGNORE … SELECT` 幂等，`onFail: MARK_RAN` 兼容裁剪 preset）+ manifest 断言 `procurement-admin-approval-candidate`（rows=2，sha256 `97efdc60…`）+ modules.yaml 登记。
- **验证证据**：`mvnw -pl omni-auth,omni-procurement,omni-db-migrator test-compile` BUILD SUCCESS；`QuotationSubmittedServiceImplTest` **9/0/0/0**；`omni-db-migrator test` **22/0/0/0**（含离线 Liquibase changelog 校验、adoption label 契约、manifest 源摘要硬校验、模块目录 1:1）；srm.flows.spec.ts 定向 eslint + tsc strict 均 exit 0。
- **本批数据清理有据**：API 删除 12 项（品类/物料/审批路由）；对 8 项 409 残留（rfq 18-21、requisition 48-51）做事务软删，涵盖 `proc_rfq_line`/`proc_rfq_supplier`/`proc_requisition_line` 及跳库 `srm_quotation`(14-17)/`srm_quotation_line`，**7 条 UPDATE 的 ROW_COUNT 全为 4（合计 28）**，POST-CHECK 本批 0 残留；历史残留（E2ESQ RFQ 3 / srm_quotation 3）未被触碰，单列 `HISTORICAL_RESIDUAL_OUT_OF_SCOPE`。
- **未软删且已记录理由**：`proc_event_inbox`（ids 32-39）与 `srm_quotation_request`（ids 14-17）均**无 `deleted` 列**，属幂等/请求台账而非业务数据，硬删会削弱重放保护。
- **凭证已销毁**：仅删本批 `tokens-20260903-160646.json` 与指向它的 `latest.txt`；同目录 12 个更早会话凭证文件未触碰（已过期但仍在 TEMP，待单独授权清理）。
- **strict 实跑结果**：`npm run docs:screenshots:check` → **exit 1，8 个模块覆盖失败**（system-management/messaging-monitoring/workflow/srm/procurement/asset = partial，scaffold-development/operations = missing），**无新增登记/图片/用例文件错误**。`docs:links:check` 与敏感内容扫描均 exit 0。
- **Gate 不变**：G1=PENDING（外部运维）、G7=PENDING（仍 8 模块覆盖失败 + 114 篇翻译复核，均为 `present-unverified`/`reviewed_at: null`）、WP-10=LOCKED、G8=LOCKED。本批未执行 G8，未代办 adoption 批准，未将自审冒充独立 Codex final review。

持续执行断点与逐阶段证据见 `qoder-continuous-progress-2026-09-03.md`。
