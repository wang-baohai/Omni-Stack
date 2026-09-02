# Final Gate Unlock Preflight（2026-09-02）

> 目的：把所有「等待外部决策/最终复核」事项准备到收到确认即可执行。
> 本文件只固化事实与清单，不解除任何 blocker、不改变任何 Gate 状态。
> 事实源：`defect-adoption-baseline-drift.md`、`i18n-review-queue.md`、`handover-status-2026-09-01.md`（工作地图 L93-101、收尾序列 L42-46）、`wp10-inventory.md`。

## 1. Current Accepted Baseline

- branch `codex/scaffold-upgrade`，local/remote HEAD = `e89bac5`（`docs: close G2 on local acceptance evidence`）
- `G2=CLOSED`；remote isolated CI = FOLLOW_UP_NON_BLOCKING
- `efd651e` 仅为历史起始基线，不再作为执行基线

## 2. Gate Dependency Matrix

| Gate | 当前状态 | 剩余前置 | 类型 |
| --- | --- | --- | --- |
| G1 | PENDING | adoption baseline 运维确认（BLOCKER-3） | EXTERNAL_INPUT |
| G2 | **CLOSED** | — | DONE |
| G3-G6 | DONE | —（工作地图无残留项） | DONE |
| G7 | PENDING | ① 深度截图闭环（6 partial + 2 missing，strict 8 red）② 114 篇翻译人工复核（BLOCKER-2） | FROZEN_DECISION + FINAL_REVIEW |
| WP-10 | LOCKED | G1～G7 全部关闭（handover L30/L100） | TECHNICAL_PENDING |
| G8 | LOCKED | 全部前置 + WP-10 cleanup 完成 | TECHNICAL_PENDING |

## 3. BLOCKER-3 运维决策包（目标库 drift）

### 3.1 Drift 摘要（本地实证，算法已逐条复现验证）

| 库 | 冻结基线表数 | 实测 | 差异 |
| --- | --- | --- | --- |
| omni_auth | 20 | 22 | + sys_mq_message（WP-03 多服务迁移）+ sys_tenant_module_provision（S0-07） |
| omni_base/workflow/crm/srm/procurement/asset | 8/54/12/21/15/6 | +1 各 | + sys_mq_message |
| nacos_config / xxl_job | 10 / 8 | ✅ exact | 一致（算法可信性对照证明） |

根因：冻结基线 `baseline-09a29fe.yaml`（2026-08-20 快照）早于 sys_mq_message 与租户开通表——**基线整体过时，非 migrate 缺陷**。

### 3.2 Candidate 状态

九库候选指纹已生成（数值全量见 `defect-adoption-baseline-drift.md` L51-65），标注 `baselineCommit: efd651e...-CANDIDATE-UNREVIEWED`——**未经运维确认、未绑定备份证据、禁止直接用于 adopt-current**。当前仓库根另有未跟踪 `baseline-candidate.yaml` 残留（WP-10 候选，保持不动）。

### 3.3 运维需明确回答（APPROVE / REJECT / NEED_MORE_EVIDENCE）

1. 7 库 drift（+sys_mq_message、auth 额外 +sys_tenant_module_provision）是否为预期部署结果？
2. 是否允许以 candidate 指纹为新 adoption baseline（重命名 `baseline-efd651e.yaml` 入 `database/adoption/`）？
3. 是否存在环境特有差异不应固化进基线？
4. 是否需要 DB owner 逐库审核后再批？

### 3.4 APPROVE 后执行路径（届时执行，本批不执行）

重命名入 `database/adoption/` → 绑定外部备份证据 → `DbMigratorProperties.adoptionBaseline` 指向新基线 → 临时空库 migrate→adopt-current 回归 → 更新 schema-snapshot/migration-inventory 引用 → **G1=CLOSED**。
REJECT：不更新基线，定位 drift 来源后重新出包。

## 4. BLOCKER-2 Codex Final Review 执行包

### 4.1 Scope（以 queue 当前数据为准）

- 38 中文事实源 × 3 locale（en-US/ja-JP/ko-KR）= **114 项**，P0 3 篇 / P1 8 篇 / P2 27 篇
- 当前：全部 `present-unverified`，已完成人工复核 **0** 篇，`reviewed_at` 全空
- 只 review 译文；**zh 源不 review**。状态管理在 `docs/docs-manifest.yaml`（queue 由 `docs-review-queue.mjs` 生成，勿手编）

### 4.2 Review Criteria

1. 语义准确（与中文事实源逐节一致）；2. 术语/代码块/命令/API 路径/权限码/数字/表格/链接不翻译且无漂移；3. 业务术语统一；4. 无 placeholder 丢失；5. 无错误机器直译；6. 目标语言叙述自然；7. 产品名/字段名不误译。

### 4.3 Output Contract

- 逐项真正完成 review 后才在 docs-manifest.yaml 置 `status=synchronized` + `reviewed_at=<实际日期>`（复核人非初稿生成者）→ 重新生成 queue
- 不通过：登记具体问题并修正译文后重审；**严禁批量转换或自动化 parity 充当人工 review**

### 4.4 与 G7 关系

114 项全部 synchronized 后 G7 关闭条件②满足；G7 同时要求条件①（见 §5）。

## 5. Screenshot 8 red 最终影响判定

- **结论：属于 G7 硬门组成部分（A 类），不是可忽略的 docs 增强项。**
- 依据：`handover-status-2026-09-01.md` L42「Token 环境跑 G2 与深度截图 → G2/**G7** 关闭」——深度截图被列为 G7 关闭路径。
- 当前 strict `12 → 8`（6 partial + 2 missing），经验收方「方案 3」冻结，等批量授权（成本矩阵已出：全部 >65K 或含不可构造 gap）。
- 本批不改状态、不补图；恢复执行需单独批量授权。

## 6. WP-10 Unlock Checklist（解锁后按序，依据 `wp10-inventory.md` 批次 A/B/C）

1. 敏感备份类（`.env.before-rebuild-*` 等，验证 .gitignore 防御后处理）
2. patches（en/ja/ko + portal 六个）
3. `.artifacts` / debug 截图（`login-state-check.png` 等）
4. one-off scripts（`scripts/*.ps1|*.py` 临时验证类）
5. one-off SQL（`scripts/sql` 修复脚本）
6. progress/handoff 残留（`agent-progress.md`、历史 handoff）
7. 悬空引用全仓扫描（删除物不得被 docs/代码/配置引用）
8. targeted Gates 回归（仅受影响面）
`DO_NOT_RUN_BEFORE_UNLOCK`：以上全部。当前保持 `WP-10=LOCKED`。

## 7. G8 Once-Only Checklist（全部前置满足后仅执行一次）

1. backend Maven 全 reactor（JDK 25）
2. frontend build + lint
3. five presets golden 验证
4. Compose full E2E
5. DB migration + adoption 全链路（新 baseline）
6. security 面复核
7. docs 一致性（i18n/docs check 全绿）
8. screenshot 体系复核
9. reference integrity 全仓
10. 生成 G8 证据与正式交付报告
`DO_NOT_RUN_BEFORE_UNLOCK`。禁止分批或重复执行。

## 8. Recommended Final Sequence（以依赖矩阵为准）

1. BLOCKER-3 运维答复 → APPROVE 则 5 步启用流程 → **G1=CLOSED**
2. screenshot 深度线批量授权 → 6 partial + 2 missing 执行 → G7 条件①
3. BLOCKER-2 Codex final review 114 项 → G7 条件② → **G7=CLOSED**
4. G1+G7 全闭 → **WP-10 解锁**：批次 A/B/C + 悬空扫描 + cleanup report → G1～G7 全 CLOSED
5. Gate re-check → **G8 once-only** → 交付报告
