# 脚手架升级任务状态汇总（2026-09-01，HEAD efd651e，未提交工作区）

## 一、本会话已完成并通过验证

| Step | 内容 | 验证证据 |
| --- | --- | --- |
| 1 | 工作区保护与修改分类 | 每批 porcelain 盘点，暂存区始终为空，敏感文件零触碰 |
| 2 | 公开 E2E 日文断言修复 | `npm run test:e2e` **8 passed / 0 failed / 19 skipped**（每批复跑保持） |
| 3/4 | locale 语义收口 | 四语言各 **2319 键**全一致；`ui:i18n:parity --strict` 通过；supplierPortal 69 条中文残留、procurementApprovalRules ja/ko 各 81 条、portal.quotation/contextPad/cron.preview 全部翻译；operLog 33 键补齐 en/ja/ko |
| 5 | G2 确定性改造 | AMBIGUOUS fixture 重叠区间断言、失效模型故障注入（删除已发布模型）、金额六点矩阵、三视口+键盘、自建自清理；无 Token 时正确 skip |
| 6 | 文档事实收口 | README×4 目录树、crm×4 扩展指南、quick-start×4 预设列表；init-all/migrate-*/sp_init_tenant 指导性引用清零；`docs:links/readme:check` 通过 |
| 7 | 人工复核队列 | `docs/i18n-review-queue.md`（114 篇，P0/P1/P2 分组）+ `docs-review-queue.mjs`（generate/check）；status/reviewed_at 零伪造 |
| 8 | 截图收口 | manifest 16→**58 条**（42 admin 同步脚本登记）；42 测试加四语言核心文案断言；coverage procurement/asset missing→partial；9 个 zh 指南嵌入 14 张图（四要素） |
| 9 | CI 严格门 | quality.yml 7 jobs：移除两处 `--allow-draft`、parity strict、queue/sync 校验、database-migrations 五步链、cli-golden、security、夜间 Compose E2E+全量截图 |
| 10 | G7 走查 | 新手走查 **5/5 PASS**（浏览器实证含 demo seed 登录），quick-start 落地页描述修正；证据 g7-walkthrough.md |
| 11 | WP-10 inventory | 六类清点完成，`wp10-inventory.md`；敏感备份已入 .gitignore（防御） |
| 12 | 本地终验 | migrator 链路实证（fresh/replay 0 changeSet/validate/verify-seed 24 断言，g1-migration-local-evidence.md）；production build **BUILD_EXIT=0**；lint 通过 |
| 12b | security 门本地预演 | secret 扫描 **SECRET_SCAN_CLEAN**；前端 audit 0 漏洞；CLI `fast-xml-parser` 5.3.6→**5.11.1**（清零 4 个 high advisory）后 40/40 测试通过，audit 0 漏洞 |
| 12c | 四语言指南截图嵌入 | en/jp/kr × 8+1 指南全部嵌入对应 locale 图（共 50+ 引用）；响应式图仅 zh-CN 提供，其余语言引用 zh-CN 布局图并显式标注；`docs:links/readme:check` 通过 |

新增正式工具：`locale-parity-check.mjs`、`docs-review-queue.mjs`、`screenshot-manifest-sync.mjs`（均带校验模式，npm scripts 已注册）。

## 二、阻塞项（外部输入，均已在证据文档登记恢复条件）

| # | 阻塞 | 依赖输入 | 解锁后动作 |
| --- | --- | --- | --- |
| 1 | G2 确定性用例真实执行 | `E2E_ADMIN_TOKEN`、`E2E_EMPLOYEE_TOKEN`、`E2E_MUTATIONS=true` | Token 环境直接 `npm run test:e2e` |
| 2 | 深度状态截图 + ja/ko 审批规则图重拍 | 同上（admin Token） | 扩展 docs 截图场景 → coverage 逐模块 covered → `docs:screenshots:check` 转绿 |
| 3 | 114 篇文档人工复核 | 实际复核人 | 按 `docs/i18n-review-queue.md` 复核 → manifest 更新 → `docs:i18n:check` 转绿 |
| 4 | WP-10 分批清理 + cleanup report | G1～G7 全部关闭 | 按 `wp10-inventory.md` 批次 A/B/C 执行 |
| 5 | adoption 基线刷新 | 运维/接管流程决策（备份证据链） | 按 `defect-adoption-baseline-drift.md` 方案刷新冻结基线后，adopt-current 方可在真实接管场景使用 |
| 6 | 登录后 Compose E2E / 夜间矩阵 | CI secrets + 首跑 | quality.yml `compose-e2e-nightly` |

## 三、严格检查当前红灯（预期且诚实）

- `docs:i18n:check`：228 项 = 114 篇 × (status + reviewed_at)，与复核队列一一对应；
- `docs:screenshots:check`：12 项 = coverage 未达 covered 的模块数；
- 两者在阻塞 #2/#3 解除前保持红灯，不得用 allow-draft 或改状态制造绿色。

## 四、剩余步骤（全部解锁后）

1. Token 环境跑 G2 与深度截图 → G2/G7 关闭；
2. 人工复核完成 → G7 关闭；
3. WP-10 批次 A/B/C 清理 + 悬空引用扫描 + cleanup-report；
4. 最终全量验收：后端一次 clean install、前端 build、五预设矩阵、全量 E2E；
5. 生成 G8 证据与正式交付报告。

## 五、纪律遵守记录

- 未提交任何内容、未 push、未 reset/restore/checkout/stash；
- `.env.before-rebuild-20260828` 内容零读取，本会话内已入 .gitignore 防误提交；
- 无临时文件残留（探针/容器均已清理）；未删除任何文件；
- 全部绿灯来自真实执行，全部红灯保留并登记原因。

## 六、2026-09-02 恢复会话增量（最新事实，覆盖上文过时表述）

本节为最新增量，与上文冲突时以本节为准。详细证据见 `g2-real-e2e-evidence.md`（含附一/二/三）。

### 状态变化

| 项 | BEFORE | AFTER |
| --- | --- | --- |
| BLOCKER-1 受控认证 | 未解决 | **DONE**——E2eTokenFixture APPROVED（安全边界 10/10），admin/zhangsan/supplier1 按需签发、process-scope 注入、多轮零泄露实证 |
| G2 | 确定性 fixture 未真实执行 | **G2=PARTIAL**：真实执行 5/6 PASS（G2-1/2/4/5/6），G2-3 DEFERRED_TO_ISOLATED_CI（断言语义保留，`E2E_WORKFLOW_FAULT_INJECTION` 条件挂起） |
| BLOCKER-2 | BLOCKED | **DEFERRED_TO_FINAL_REVIEW_BY_CODEX**（114 篇 present-unverified/null 保持，禁本阶段处理） |
| screenshot strict | 12 red | **8 red**（冻结；四模块 covered 后不再自动推进） |
| 前端容器 | 旧快照 | 已重建（含最新四语言翻译） |

### 新增 covered 模块（四语言正式图 + manifest + 指南全链路）

1. permissions-exceptions（28 图，7/7 flows）
2. authentication（含 session-expired 四语言，产品 handle401 降级实证）
3. crm（lead create 表单/校验失败/成功 + 越权 403，E2E_MUTATIONS 自建自清理）
4. scheduling-workspace（job-type + personal lifecycle/validation，喝水提醒 demo handler）

### 重要结论更正

- 菜单接口 500 **不白屏**：产品降级页 `/menu-load-error` 完好（首批失败为测试断言错误）
- scheduling ja/ko 失败根因为 **TEST_DEFECT**（locator 误用 `common.userJobTypes`，真实 key `userJob.jobType`；
  行编辑按钮漏 ko「편집」）——产品无缺陷
- adoption 前批「白屏疑云」同批更正，勿再引用

### 新增/更新的事实源文件

- `g2-real-e2e-evidence.md`（G2 执行 + 认证 + 更正记录，含附一/二/三）
- `screenshot-manifest.yaml`（+70 条，累计约 136 条）
- `screenshot-coverage.yaml`（4 模块 covered + 闭环依据注释）
- `tools/omni-cli/scripts/`：locale-parity-check / docs-review-queue / screenshot-manifest-sync / refresh-source-digests
- 用户指南：9 模块指南嵌入四语言截图引用

### 剩余工作地图（决策点）

| 工作 | 依赖 | 状态 |
| --- | --- | --- |
| 深度截图 ×6 partial + 2 missing | 批量授权（成本矩阵已出：全 >65K 或含不可构造 gap） | **方案 3 冻结** |
| 114 篇翻译复核 | Codex 终审 | DEFERRED |
| adoption 基线确认 | 运维 | BLOCKED |
| **G2（Gate 2）** | — | **CLOSED**（2026-09-02：G2-1/2/4/5/6 PASS + G2-3 PASS LOCAL_ACCEPTANCE；remote isolated CI = FOLLOW_UP_NON_BLOCKING） |
| G2-3 isolated CI 设施 | CI 环境 + 授权 | **已发布；转 FOLLOW_UP_NON_BLOCKING**（2026-09-02 验收方裁决：G2-3 以本地验收 PASS（LOCAL_ACCEPTANCE）关闭，G2=CLOSED；remote isolated outage CI 未执行，登记为 regression hardening 非阻塞 follow-up；dispatch scope isolation（Quality Gate 6 无门槛 job）同属 follow-up） |
| WP-10 分批清理 | G1-G7 关闭 | 禁止提前 |
| G8 最终验收 | 全部前置 | 禁止提前 |

### 纪律补充（2026-09-02 会话）

- 恢复会话零 Token 泄露、零持久化违规、零未授权删除；
- 临时 runner 全部 `scripts/.work` 批末清理（TEMP_FILES=0 多次验证）；
- 用户已禁止自动 blocked audit 与等待轮询（永久规则，见任务指令十五/二十四节）。

### Final Gate Unlock Preflight（2026-09-02）

统一解锁包已固化：`final-gate-unlock-preflight-2026-09-02.md`。新依赖结论：
- **G1 唯一残留 = adoption 运维确认**（EXTERNAL_INPUT），无其他未完成项；
- **screenshot 8 red = G7 硬门组成部分**（依据 L42：深度截图是 G7 关闭路径），非可忽略项，等批量授权；
- **G7 = 深度截图闭环 + 114 翻译人工复核** 双条件；
- 推荐序列：BLOCKER-3 答复 → G1 CLOSED → 截图批量授权 → G7 条件① → Codex review → G7 条件② → G7 CLOSED → WP-10（A/B/C+悬空扫描）→ Gate re-check → G8 once-only。
