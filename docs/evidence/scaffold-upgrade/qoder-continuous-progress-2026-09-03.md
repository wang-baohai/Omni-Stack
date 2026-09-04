# Qoder 多阶段连续执行 checkpoint

日期：2026-09-03。执行指令：`docs/evidence/scaffold-upgrade/qoder-continuous-execution-prompt-2026-09-03.md`（A-D 多阶段连续执行）。

本文件是持续执行的断点记录，按阶段/模块批次更新，不逐文件记流水。主交接事实入口仍是
`srm-supplier-quotation-qoder-handoff-2026-09-03.md`；本文件与其冲突时以本文件的**实测证据**为准。

## 0. 接管基线（Step 0 实测，非沿用旧快照）

| 项目 | 实测值 | 备注 |
| --- | --- | --- |
| 本地分支 | `codex/scaffold-upgrade` | 与预期一致 |
| 本地 HEAD | `ace8bf7` `docs: prepare final gate unlock decisions` | 与交接基线一致 |
| 未提交计数 | 32 条（5 tracked modified + 27 untracked） | 与主交接 §2.2/§2.3 吻合 |
| Compose 项目 | `omni-wp09-docs`，`running(15)` | 14 healthy；frontend 无 healthcheck |
| procurement | `Up 3 hours (healthy)` | 未重建；镜像与源码逐字节一致性仍未证明 |
| JDK | OpenJDK `25.0.2` | `JAVA_HOME=C:\APP\JDK25\jdk-25.0.2` |
| Maven | 仓库 Wrapper `mvnw.cmd` | 未使用系统 mvn，未使用 .m2 launcher 直调 |

## 1. 阶段 A 进度

### Step 1 前置修正 — DONE

**C4 测试防护**（`omni-frontend/e2e-docs/flows/srm.flows.spec.ts`，重写）：

- 移除 `workflow-options` 的 `?? options[0]` 兜底：未命中 `procurement-approval` 时断言失败并回显实际候选 modelKey 列表。
- 移除审批任务的 `?? records[0]` 兜底：改为 `records.filter(title 含本轮 runStamp 标题)` 并断言 `toHaveLength(1)`，唯一命中才批准。
- `E2E_MUTATIONS` 落地为运行时防护：`expectOk()` 是所有写入调用的唯一出口，未开启即抛错；skip 条件同时要求 admin/supplier Token 与 `E2E_MUTATIONS=true`。
- 创建即时登记：`registerCreated()` 在每次成功创建后立即写 `createdResources` 并落盘到仓库外
  `%TEMP%\omni-e2esq-registry\run-<runStamp>.json`（含 tenantId/runStamp/kind/id/version/locale/label），链路中途失败也保有归属。
- 缺 ID 即失败：`registerCreated()` 对无 `id` 的响应抛错，避免产生无法清理的无归属数据。
- afterAll 逐条核对 DELETE 响应（httpStatus/code/message/deleted），按 `rfq→requisition→approvalRoute→material→category` 反向依赖顺序清理；删除前对 rfq/requisition 取最新版本号（乐观锁版本会被 submit/approve/send 消耗）。
- 日志改用 `process.stdout.write`（满足仓库 `no-console` 门禁），仅输出计数与资源标识，不输出 Token。
- 顺带修正原有潜在类型缺陷：`Awaited<ReturnType<ReturnType<typeof apiContext>['post']>>` 改为 `APIResponse`；全文件零 `any`。

**C5 种子兼容 — 方案已按实测证据变更（重要更正）**：

实测证据（本轮取得，非沿用旧结论）：

- `omni_auth.DATABASECHANGELOG` 中 `auth-0003-bootstrap-seed` 已 `EXECUTED`（2026-08-28 13:15:18），记录 `MD5SUM=9:089357e5e58dd5ae9258797aff6ae6d1`。
- `SeedManifestLoader.parseSources()` 在**加载清单时**即对每个源做 canonical SHA-256 硬校验，不匹配抛「种子资源摘要不匹配」。
- HEAD 版 `auth.sql` canonical = `324c0cf0f9fa53cf7892b38952ebc033d83f39268dce1bcb3da17aa0a2168971` = 清单记录值（清单在 HEAD 是正确的）。
- 修改后 `auth.sql` canonical = `fa40f7012d90635a5d0f8b2cd88064f1fce4ab37d1ec41eadd832d451275b3fb`（与主交接 C5 数字一致，可复现）。
- `sys_role` id 31 = `PROCUREMENT_MANAGER`（采购经理，tenant 1，status 1）；运行库已存在 admin→31 与 `sys_user_role_scope(1,1,31,1,SAME_UNIT,1)`。
- 唯一键实测存在：`uk_user_role(user_id,role_id)`、`uk_role_scope(tenant_id,user_id,role_id,unit_id)` → `INSERT IGNORE` 幂等安全。

结论：**同时修改 `auth.sql` 会触发两条独立硬失败**——清单源摘要不匹配（migrator 加载即失败）与已执行 changeSet 的 Liquibase checksum 校验失败。因此不采用「改 auth.sql + 刷新 manifest + 兼容旧 checksum」路线。

采用 forward-only 方案（与仓库既有 `0004` 同类做法一致）：

- **还原** `scripts/sql/seed/auth.sql` 到 HEAD 内容（-6 行）→ 源摘要回到 `324c0cf0…`，`auth-0003` 已记录 checksum 保持有效。未使用 `git restore/checkout`，为定向文件编辑。
- 新增 `database/changelog/auth/0005-admin-procurement-approval-candidate.yaml`：`INSERT IGNORE … SELECT` 幂等补齐 admin 的 `PROCUREMENT_MANAGER` 角色与主部门 `SAME_UNIT` 作用域；`preConditions onFail: MARK_RAN`，裁剪掉 procurement 的 preset 因角色不存在而标记已执行，不失败。
- `database/changelog/auth/db.changelog-auth.yaml` 增加 0005 include。
- `database/seed/manifest.yaml` 新增断言 `procurement-admin-approval-candidate`（module=procurement, database=omni_auth, expectedRows=2, expectedSha256=`97efdc606efa50789e6e3fea36c4ba77f770b74a2ce0378f3a268e6eb7597ada`）。
- `scaffold/catalog/modules.yaml` procurement `provisioningSeedIds` 增加该 ID（`ModuleCatalogContractTest` 要求断言与目录 1:1 闭合）。
- **未改** manifest `version`（`SeedManifestLoaderTest` 固定为 `1.0.0-bootstrap`），因为本批 `sources` 摘要未变，只有新增断言；「变更源必须同步版本和摘要」的契约未被触发。
- **未加** `provisioning.roleCodes`，避免改变新租户角色克隆行为。
- **未动** `database/adoption/baseline-09a29fe.yaml`，G1 adoption 决策保持外部阻塞。

摘要算法已用 3 个既有断言反验模型正确（`auth-role-catalog`/`procurement-role-catalog`/`asset-role-catalog` 全部 MODEL_OK）：
行格式 `<列名小写>:<JDBC类型>=<转义值>`，值内 `|`→`\|`、`=`→`\=`、`\`→`\\`，行自然排序后 `\n` 连接取 SHA-256；
新断言实测查询恰好返回 `role|PROCUREMENT_MANAGER` 与 `scope|PROCUREMENT_MANAGER|SAME_UNIT` 两行。

**C3 签发流程**：`scripts/.work/issue-e2e-tokens.ps1` 与 `.sh` 均改为「仓库 Wrapper `test-compile` → `javap -p -constants` 硬校验 `TOKEN_TTL_SECONDS=1200L` → 不符即拒绝签发 → `exec:java`」。移除旧 `.sh` 对 `.m2` 缓存 plexus launcher 的直调。两脚本均为未提交临时文件。

### Step 2 定向编译与验证 — DONE

| 验证 | 命令 | 结果 | 时间 |
| --- | --- | --- | --- |
| auth/procurement/db-migrator 测试编译 | `mvnw.cmd -pl omni-auth,omni-procurement,omni-db-migrator test-compile` | BUILD SUCCESS，13.9s；omni-auth 重编 18 个测试源 | 2026-09-03 15:58:32 +08:00 |
| fixture 有效 TTL | `javap -p -constants -classpath omni-auth/target/test-classes com.omni.auth.e2e.E2eTokenFixture` | `private static final long TOKEN_TTL_SECONDS = 1200l`；class 15:58:28 > 源码 11:03:24 | 同上 |
| Quotation 回归 | `mvnw.cmd -pl omni-procurement test -Dtest=QuotationSubmittedServiceImplTest` | **Tests run: 9, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS，1.265s；主类 class 12:18:08 > 源码 12:17:21，测试源 12:15:20 < 测试类 12:16:08 | 2026-09-03 ≈16:01 |
| 种子/changelog 契约 | `mvnw.cmd -pl omni-db-migrator test` | **Tests run: 22, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS | 2026-09-03 ≈16:04 |
| 前端定向静态检查 | `npx eslint e2e-docs/flows/srm.flows.spec.ts --max-warnings 0` | exit 0（0 error 0 warning） | 2026-09-03 ≈15:56 |
| 前端定向类型检查 | `npx tsc --noEmit --strict --noUnusedLocals … e2e-docs/flows/srm.flows.spec.ts` | exit 0 | 同上 |
| Playwright 用例发现 | `npx playwright test srm.flows --config playwright.docs.config.ts --list` | **Total: 4 tests**（zh-CN/en-US/ja-JP/ko-KR 各 1） | 同上 |

db-migrator 22 项中与本批直接相关：`SeedManifestLoaderTest`(4) 加载清单时实校验 8 个源摘要（含还原后的 auth.sql）；
`ModuleCatalogContractTest`(1) 校验断言 ID 与模块目录 1:1；`LiquibaseMigrationServiceTest`(3, 4.19s) 离线校验全部 changelog（含新增 0005）；
`AdoptionLabelContractTest`(1) 校验 adoption label 约定。

已确认 `omni-db-migrator/pom.xml` 将 `../database`、`../scaffold`、`../scripts/sql/seed` 声明为主资源，
每次构建从仓库实时复制 → 上述测试校验的是当前工作区文件，不是陈旧副本。

`NOT_RUN_THIS_BATCH`（用户执行提示明确授权定向验证例外）：完整 backend `clean install`、完整 frontend `npm run build`/`npm run lint` 全仓、G8。
注意：`tsconfig.json` 的 include 只覆盖 `src/**`，e2e-docs 不在 `vue-tsc -b` 范围内，故已用独立 tsc + eslint 定向覆盖。

### Step 3 签发 Token 并执行四语言 E2E — DONE

执行路径：`scripts/.work/run-srm-e2e.ps1`（内部先调 `issue-e2e-tokens.ps1`，再在同一进程注入三个环境变量并运行 Playwright）。

| 环节 | 实测结果 |
| --- | --- |
| TTL 硬门禁 | `COMPILED_CONSTANT=private static final long TOKEN_TTL_SECONDS = 1200l;` → `TTL_VERIFIED=1200L` |
| 签发 | `ISSUE_OK`，凭证文件 `%TEMP%\omni-e2e-tokens\tokens-20260903-160646.json`（路径已记录，内容从不输出） |
| 注入 | `TOKEN_LOADED adminPresent=True supplierPresent=True mutations=true` |
| 套件结果 | **4 passed (1.4m)**，0 failed，**0 skipped**，`PLAYWRIGHT_EXIT=0` |
| 图片 | **12/12** 生成，时间 16:07:01 → 16:08:17，体积 50,641–165,928 bytes |
| 资源登记 | `registered=20`，`%TEMP%\omni-e2esq-registry\run-1788422814826.json` |
| afterAll 清理 | `deleted=12`（品类/物料/审批路由），`residual=8`（rfq 18-21、requisition 48-51，均 409「仅草稿可删除」） |

图片质检（真实系统状态，非 mock/占位/AI 生成），四语言×三状态已逐张目检关键项：

- `zh-CN/invitations`：中文 UI、supplier1 已登录、本批 `RFQ-1-18 / E2ESQ RFQ zh-CN 1788422814826` 在列。
- `en-US/invitations`：英文 UI（RFQ Quotations / Not Quoted / Submit Quotation），本批 `RFQ-1-19` 为 **INVITED + Not Quoted**。
- `ja-JP/form`：日文 UI（サプライヤー見積 / 見積有効期 / 品目コード），单价 `123.45`、数量 `2`、有效期=fixture 截止时间、RFQ 状态 `SENT`。
- `ko-KR/submitted`：韩文 UI（견적 응답 / 초대 상태 / 견적 수정），本批 `RFQ-1-21` 为 **QUOTED + CNY 246.9**，与断言 `EXPECTED_TOTAL` 一致。
- 敏感信息：无 Token/密码/密钥可见；仅出现测试身份 `supplier1`（预期内，非敏感）。
- **质量注意点（已如实记录，不隐瞞）**：共享本地环境的列表同时显示 3 行历史 E2ESQ 残留（RFQ-1-15/16/17）。本批无权删除历史数据，已在 `docs/srm.md` 新章节与主交接 §8.2 显式说明。

### Step 4 本批数据与凭证收尾 — DONE

数据清理（事务软删，`scripts/.work/qoder-cleanup-batch.sql`，结果存 `scripts/.work/qoder-cleanup-result.txt`）：

- 归属守卫：每条子表 UPDATE 都 JOIN 父表并要求 `tenant_id=1` + 显式 ID + `title LIKE '%1788422814826%'`；**未使用 E2ESQ 前缀批量 UPDATE**，未跳租户。
- PRE-CHECK：7 项目标各 4 行 → 7 条 UPDATE 的 `ROW_COUNT()` **全为 4**（合计 **28** 行）→ `COMMIT`。
- 覆盖表：`proc_rfq_line`、`proc_rfq_supplier`、`proc_rfq`、`proc_requisition_line`、`proc_requisition`（omni_procurement）+ 跳库 `srm_quotation_line`、`srm_quotation`（omni_srm）。
- POST-CHECK：本批 7 项均 **0** 条 `deleted=0` 残留；12 行根记录带可识别标记 `update_by='e2e-cleanup-1788422814826'`（可逆：`deleted=0` 即还原）。
- 历史残留未被触碰：`historical_proc_rfq_E2ESQ=3`、`historical_srm_quotation=3` → `HISTORICAL_RESIDUAL_OUT_OF_SCOPE`。
- **未软删且已记录理由**：`proc_event_inbox`（ids 32-39）与 `srm_quotation_request`（ids 14-17）均 **无 `deleted` 列**，属幂等/客户端请求台账而非业务数据，硬删会削弱重放保护；`sys_mq_message` 按 AGENTS 约束不纳入租户清理。

凭证销毁（`scripts/.work/destroy-e2e-credentials.ps1`）：

- `ENV_CLEARED=process-scope`；`POINTER_REMOVED=yes`（latest.txt 确实指向本批）；`BATCH_TOKEN_FILE_REMOVED=yes`。
- 目录文件数 14 → 12；**12 个更早会话（09:44–12:22）的 token JSON 未触碰**（JWT 已过期但文件仍在，清理需单独授权）。
- 本批登记文件 `omni-e2esq-registry\run-1788422814826.json` 保留作证据（不含任何凭证）。

### Step 5 图片登记、指南与看板 — DONE

| 项 | 实测结果 |
| --- | --- |
| `screenshot-manifest.yaml` | **+12 条**（无重复 ID，原先无任何 `srm-portal-quotation` 条目）；`last_success_at` 用每张图真实拍摄时刻；action/expected 按四语言分别撰写 |
| `screenshot-coverage.yaml` | SRM `gaps` 仅移除 `supplier-quotation` → `[admission-lifecycle, stable-mobile-flow, detail-and-action-states]`；`existing_assets` +3 行 brace-glob（代表 12 文件）；**status 仍 `partial`** |
| `docs/srm.md` | 新增「### 供应商报价流程截图（四语言）」，+59 行；含公共前置条件表（环境/数据前置/操作者/令牌/写入开关/视口）与三个步骤的四要素（操作者/操作/预期状态），图片路径为 `images/<locale>/…` |
| `docs/docs-manifest.yaml` | srm `source_sha256` 刷新为 `aa1a1e2a96a5f37686f12c03b226224e8ff5d3eea69877b7bbbf14b0b12f1d8a`（旧 `897f5a1b…`）；**114 项译文状态保持 `present-unverified` / `reviewed_at: null`，未伪造同步** |
| 旧交接更正 | 两份 2026-09-03 旧交接各追加一个更正节（表格形式逐条列出原表述/更正/证据），**保留历史原文与时点**；主交接 §8 结果记录已改为实测值并新增 §8.1/§8.2 |
| strict 实跑 | `npm run docs:screenshots:check` → **exit 1，文档质量检查失败：8 项**（system-management / messaging-monitoring / workflow / srm / procurement / asset = partial；scaffold-development / operations = missing）。**无新增登记/图片不存在/用例不存在错误** → 12 条新条目全部通过校验。未改检查器，未造假 covered |
| 链接检查 | `npm run docs:links:check` → **exit 0**（新增 12 个图片引用均解析成功） |
| 敏感扫描 | `docs-quality.mjs --scope=sensitive --allow-draft` → **exit 0**（无 JWT/Bearer/私钥模式，含新增 checkpoint 与 srm.md 章节） |

### Step 6 精确提交与推送 — DONE

| 项 | 实测结果 |
| --- | --- |
| 提交前 fast-forward 核验 | `git ls-remote` = `ace8bf737695e7a63e3c576882f29cbf200782ff` == 本地 HEAD，`merge-base --is-ancestor` exit 0 → 无分叉 |
| stage 方式 | `git add --pathspec-from-file=scripts/.work/qoder-stage-pathspec.txt`，**30 条显式路径**；未用 `git add .` |
| `git diff --cached --name-only` | 恰好 30 个文件；未暂存余量 **正好等于 §2.3 排除项清单**（无 TEMP 凭证、无 `*.patch`、无 debug PNG、无 `scripts/.work/`） |
| `git diff --cached --check` | exit 0（无空白错误） |
| secret scan（仅暂存内容） | 18 个文本文件 / 1813 新增行 / 7 种模式（JWT、Bearer、私钥、URL 内凭证、secret 赋值、AWS、GH token）→ **CLEAN**；仅输出结论，未回显内容 |
| commit | `653afe3c9ca148775e9c027f8d05cac25e8223e4` `test(e2e): close SRM supplier quotation gap`，30 files changed, +1813/-6 |
| push | `FAST_FORWARD_PROVEN=yes` → `PUSH_EXIT=0` → `REMOTE_AFTER_PUSH=653afe3c9ca148775e9c027f8d05cac25e8223e4` |
| 交付状态 | **DELIVERY=PUSHED**（remote HEAD == local HEAD）；`origin` 配了双 push URL，gitee 与 github **均** `ace8bf7..653afe3` fast-forward |
| 未做的危险操作 | 未 amend 回填远端 SHA、未 reset/restore/checkout/stash/clean/rebase/merge/force push、未 unstage 任何不属于本批的内容 |

阶段 A 完成定义（主交接 §7）五项**全部满足** → `SRM_SUPPLIER_QUOTATION_GAP=CLOSED; DELIVERY=PUSHED`。

备注：`.workbuddy/` 在 Step 0 时存在于 `git status`，本轮结束时已不在列表中——属其他工具活动，本批未读写也未提交该目录。


## 2. 本轮变更文件

### 2.1 阶段 A（已提交于 `653afe3`）

- `omni-frontend/e2e-docs/flows/srm.flows.spec.ts`（重写，原为 untracked）
- `database/changelog/auth/0005-admin-procurement-approval-candidate.yaml`（新增）
- `database/changelog/auth/db.changelog-auth.yaml`（+2 行 include）
- `database/seed/manifest.yaml`（+25 行断言）
- `scaffold/catalog/modules.yaml`（procurement provisioningSeedIds +1）
- `scripts/sql/seed/auth.sql`（**还原到 HEAD**，净变更 0，未进入提交）
- `docs/images/{zh-CN,en-US,ja-JP,ko-KR}/srm-portal-quotation-{invitations,form,submitted}.png`（12 张）
- `omni-frontend/e2e-docs/screenshot-manifest.yaml`（+12 条）、`screenshot-coverage.yaml`（SRM gaps/assets）
- `docs/srm.md`（+59 行截图章节）、`docs/docs-manifest.yaml`（srm 源摘要）
- `omni-backend/omni-auth/.../E2eTokenFixture.java`（TTL 600→1200）
- `omni-backend/omni-procurement/.../QuotationSubmittedConsumer.java`、`QuotationSubmittedServiceImpl.java`、`QuotationSubmittedServiceImplTest.java`
- 5 份证据文档：主交接、两份旧交接（含新增更正节）、本 checkpoint、用户执行提示词

### 2.2 阶段 B/C（待提交）

阶段 B 第一批（只读采集）：

- `omni-frontend/e2e-docs/flows/management.flows.spec.ts`（新增，18 scene × 4 语言）
- `docs/images/{zh-CN,en-US,ja-JP,ko-KR}/<18 个页面 ID>.png`（**72 张**）
- `omni-frontend/e2e-docs/screenshot-manifest.yaml`（+72 条 → 194）
- `omni-frontend/e2e-docs/screenshot-coverage.yaml`（4 模块 assets；workflow 移除 `tracking`；system-management gap 精确化）
- `docs/workflow.md`（+§8 四语言截图）、`docs/guides/system-security-audit.md`（+§8 十页四语言截图）、`docs/srm.md`（+SRM 管理端页面小节）

阶段 C：

- `docs/srm.en.md`、`docs/srm.jp.md`、`docs/srm.kr.md`（修正与源矛盾的「Phase 2 报价预留」陈旧章节）
- `docs/i18n-review-queue.md`（`npm run docs:i18n:queue` 重生成）
- `docs/docs-manifest.yaml`（本批 3 份源摘要 + 修正 4 项既有陈旧摘要）
- `docs/evidence/scaffold-upgrade/qoder-continuous-progress-2026-09-03.md`（本文件）

临时探测用例 `e2e-docs/flows/zz-probe.spec.ts` **用后已删除**，从未进入提交；其产物仅存于未跟踪的 `.artifacts/probe/`。

### 2.3 不提交的临时文件

- `scripts/.work/issue-e2e-tokens.ps1`、`issue-e2e-tokens.sh`（本轮修正）
- `scripts/.work/qoder-check-changelog.sql`、`qoder-check-assertion.sql`（本轮只读核查）
- `scripts/.work/qoder-mvn-*.log`（本轮构建日志）
- 其余既有排除项原样保留：根目录 `*.patch`、`sms.png`、`agent-progress.md`、`baseline-candidate.yaml`、`.workbuddy/`、
  `login-state-check.png`、`omni-frontend/console-btn-home.png`、`omni-frontend/.artifacts/`、`omni-frontend/scripts/`、
  `scripts/.work/` 既有脚本、`docs/scaffold-upgrade-task-handoff-2026-08-27.md`

## 3. 阶段 B：截图技术 gaps

### 3.1 实际范围（以 `screenshot-coverage.yaml` 为准，非沿用旧队列描述）

12 个模块，**39 个 gaps**，其中 8 个模块未达 `covered`/`exempt`（即 strict 的 8 个失败）：

| 模块 | status | required_flows | 已有 assets | manifest 条目 | gaps |
| --- | --- | --- | --- | --- | --- |
| authentication | covered | 5 | 12 | 16 | 0 |
| scheduling-workspace | covered | 8 | 6 | 16 | 0 |
| crm | covered | 7 | 13 | 16 | 0 |
| permissions-exceptions | covered | 7 | 7 | 24 | 0 |
| system-management | partial | 14 | 6 | 8 | 3（most-management-flows, detail-and-action-states, failure-states） |
| messaging-monitoring | partial | 5 | 3 | 4 | 4（retry, dead-letter, trace-diagnosis, detail-and-action-states） |
| workflow | partial | 10 | 3 | 4 | 5（model-lifecycle, countersign, tracking, detail-and-action-states, failure-states） |
| srm | partial | 8 | 15 | 20 | 3（admission-lifecycle, stable-mobile-flow, detail-and-action-states） |
| procurement | partial | 9 | 4 | 10 | 10（material, requisition-approval, rfq, quotation-receipt, comparison, purchase-order, goods-receipt + 3 状态） |
| asset | partial | 8 | 1 | 4 | 10（receipt-card, ledger, allocation, acceptance, return, transfer-approval, disposal-approval + 3 状态） |
| scaffold-development | missing | 4 | 0 | 0 | 2（implementation-not-yet-delivered, all-scenes） |
| operations | missing | 5 | 0 | 0 | 2（observability-not-yet-delivered, all-scenes） |

跨模块高频 gap：`detail-and-action-states` 出现于 6 个模块；`failure-states` 2、`success-result` 2、`failure-or-forbidden` 2、`all-scenes` 2。

### 3.2 可构造性实测证据（决定哪些 gap 可真实关闭）

只读数据库计数（`deleted=0`，2026-09-03 阶段 B 分诊时实测）：

| 对象 | 活行 | 含义 |
| --- | --- | --- |
| `proc_material` | **0**（总 51，全部 `deleted=1`，`update_by=admin`） | 物料目录页为空；每次截图需自建 fixture 物料 |
| `proc_material_category` | **0**（总 74） | **含 13 行 bootstrap 种子品类被误删 —— 见 3.3** |
| `proc_tenant_config` | 1 | 正常 |
| `proc_approval_route` | 9 | 含历史 E2ESQ 残留 |
| `proc_requisition` / `proc_rfq` | 3 / 3 | 均为历史 E2ESQ 残留（RFQ-1-15/16/17） |
| `proc_purchase_order` / `proc_goods_receipt` | **0 / 0** | 订单与收货链从未跑通 |
| `ast_asset` / `ast_transfer` / `ast_disposal` / `ast_asset_history` / `ast_inbox_event` | **全部 0** | 资产模块无任何业务数据 |
| `srm_supplier` / `_enrollment` / `_invite` / `_portal_user` / `_evaluation` / `_risk_assessment` | 各 **1** | SRM 管理页可出列表/详情截图 |
| `srm_quotation` | 3 | 历史残留 |
| `wf_process_model` / `_version` | 8 / 8 | 模型页有真实数据 |
| `wf_process_instance_ext` / `wf_process_start_request` | 23 / 23 | 实例跟踪页有真实数据 |
| `wf_todo_task` / `wf_cc_record` / `wf_delegation_rule` / `wf_form_schema` | 全部 0 | 待办/抄送/委派/表单无数据 |
| `sys_mq_message`（按库） | procurement 562、base 35、srm 34、workflow 23、crm 21、asset/auth 0 | MQ 页读 omni_base（35 行） |

### 3.3 BLOCKED：新发现 DATA_DEFECT（种子品类被误删，影响 G1 与 procurement/asset 全部 gaps）

**现象**：manifest 断言 `procurement-default-config` 期望 `expectedRows: 14`、`expectedSha256: d1aebd181f…`；
按断言原文在运行库实跑只返回 **1 行**（仅 `config`，品类贡献 0 行）。
→ `SeedVerificationService.verifyAll()` 在此库会失败，进而阻断 `adopt-current` 预检（G1 相关）。

**根因归属（硬证据）**：

- `proc_material_category` ids **1-13** 为 bootstrap 种子品类（`IT_DEVICE / OFFICE_SUPPLY / RAW_MATERIAL / OTHER / LAPTOP / MONITOR / PERIPHERAL / STATIONERY / PAPER / METAL / ELECTRONIC / PLASTIC / SERVICE`），`create_time` 均为 **2026-08-28 13:15:25**，与 `omni_procurement.DATABASECHANGELOG` 中 `procurement-0002-bootstrap-seed`（EXECUTED，同一时刻）吻合。
- 这 13 行 `deleted=1`、`update_by=NULL`、`update_time` **全部为同一时刻 2026-09-03 12:20:12**。
- 单一相同时间戳 + `update_by` 为空 = **直接批量 SQL UPDATE**，而非正式 API 删除（API 路径会写 `update_by='admin'`，对比：另 61 行 `update_by='admin'` 均为历次 E2E 自建自删的品类）。
- 该时刻 **早于本批**（本批 E2E 为 16:06-16:08）；且本批清理标记为 `update_by='e2e-cleanup-1788422814826'`，**从未触碰品类表**（本批 4 个品类由 afterAll 经正式 API 删除，属 `admin` 那 61 行）。
- 因此：**本批无责**。推断为更早会话的「精确 DB 软删」未严格限定 E2ESQ 前缀，连带抹除了 bootstrap 种子目录。

**建议修复（未执行）**：将 ids 1-13 且 `tenant_id=1` 且 `update_by IS NULL` 且 `deleted=1` 的 13 行还原为 `deleted=0`（事务 + `ROW_COUNT()=13` 核对），即可使运行库重新满足 `procurement-default-config` 断言。

**为何不自行修复**：用户执行指令明确「只清理本次任务各阶段确认归属的 tenant+runStamp+资源 ID；历史残留独立列出」、「无法构造的场景、新发现的 PRODUCT_DEFECT…逐项登记 BLOCKED 与所需输入」、「不擅自…拓展修复」。这 13 行不属本批归属，恢复它们是对共享环境历史数据的写入，需单独授权。
→ 分类：**BLOCKED / DATA_DEFECT**，所需输入：授权执行上述 13 行还原（或由运维在正式迁移中重放种子）。

**连带影响**：`procurement` 的 `material` gap 与 `asset` 全部 10 个 gap 在目录为空时无法产出有意义的正式图；
asset 建卡只能由采购收货（`qualityStatus=PASS && assetManaged=true`）摄入，而 PO/GR 均为 0 行，
因此 asset 任一 gap 都需先跑通「物料(assetManaged) → 请购 → 审批 → RFQ → 报价 → 定点 → PO → 收货 → 建卡」全链。

### 3.4 阶段 B 逐项分类（未开始实际闭环）

| 项 | 分类 | 依据 / 所需输入 |
| --- | --- | --- |
| workflow `tracking` | EXECUTABLE（低成本、只读） | `/admin/workflow/instance` 有 23 个真实实例；可复用 `admin.flows.spec.ts` 的 scene 模式，无写入、无清理 |
| workflow `model-lifecycle`（只读部分） | EXECUTABLE（中） | 8 个模型/版本已在；发布/校验等写操作需额外 fixture |
| srm `detail-and-action-states` | EXECUTABLE（中） | supplier/evaluation/risk 各 1 行真实数据可出详情页 |
| srm `admission-lifecycle` | EXECUTABLE（高） | 需跑完整 Portal 注册 Saga（inviteToken + requestId + 准入审批 + 激活） |
| srm `stable-mobile-flow` | EXECUTABLE（中） | 参照 procurement 已有 390×844 / 1024×768 special_viewports 模式 |
| system-management 3 gaps | EXECUTABLE（高体量） | 14 个 required_flows × 4 语言；`most-management-flows` 本身即大批量 |
| messaging-monitoring `retry`/`dead-letter` | EXECUTABLE（中，需谨慎） | 需构造 FAILED/DEAD_LETTER 消息；退避为 `2^retryCount × 10s`，构造死信耗时且会向共享 relay 注入失败消息 |
| messaging-monitoring `trace-diagnosis` | EXECUTABLE（中） | 需 Trace ID 排障页面真实数据 |
| workflow `countersign` | EXECUTABLE（高） | 需会签多实例模型与多审批人身份；现有测试身份仅 admin/supplier1，**可能需新增身份→待确认** |
| procurement `material` | BLOCKED（依赖 3.3） | 目录活行为 0；修复种子后可低成本闭环 |
| procurement `requisition-approval`/`rfq`/`quotation-receipt` | EXECUTABLE（高） | 阶段 A 已验证可构造该链；但截图内容将是自建测试数据 |
| procurement `comparison`/`purchase-order`/`goods-receipt` | EXECUTABLE（很高） | 需定点→PO→收货全链；当前 PO/GR 为 0 |
| asset 10 gaps | BLOCKED（依赖 3.3 + 全链） | `ast_*` 全 0；建卡依赖采购收货摄入，需先跑通 procurement 全链 |
| scaffold-development 2 gaps | BLOCKED / 需单独设计 | gap 名即 `implementation-not-yet-delivered`；4 个流程为 CLI 非页面流程，**不得伪造 UI 图**，需按既有文档标准提供可复核的真实命令输出证据，并需决定登记形态（`exempt` 需授权，不得自行标记） |
| operations 2 gaps | BLOCKED / 需单独设计 | gap 名即 `observability-not-yet-delivered`；可观测栈为 Compose/Grafana 基础设施，非应用页面，同上 |

**阶段 B 分诊阶段实际闭环数：0**（上表为分诊结论）。实际执行结果见 §3.5。
未擅自 `exempt`、未删除任何 `required_flow`、未修改检查器或业务规则。

### 3.5 阶段 B 第一批实际执行结果（只读采集，已闭环）

**方法**：先用一次性临时探测用例（用后已删除，未提交）以 admin 只读遍历 27 个候选管理页，
将四语言下**实际渲染**的 `document.title`、表头、行数写入 `.artifacts/probe/routes-*.json`；
再据实测值编写正式用例。不依赖控制台输出（代码页会造成乱码误判），也不从语言包推测。

探测结论：**27 页全部在四语言下正常渲染且标题已本地化，0 个 403/404**（`TITLE_NOT_LOCALIZED_or_DENIED = 0`）；
9 页无表格数据（organizations、online-users、workflow-stats、procurement 物料/订单/收货、asset 台账/调拨/处置）。

**新增用例**：`omni-frontend/e2e-docs/flows/management.flows.spec.ts`（18 个 scene × 4 语言 = 72 用例）。
双重断言：`toHaveTitle(expectTitle[locale])`（证明语言包与动态路由/菜单 i18n 解析正确）
+ `.el-main` 包含实测本地化列标签（证明业务内容真实渲染，而非只等容器出现就截图）。

| 项 | 实测结果 |
| --- | --- |
| 静态检查 | `eslint --max-warnings 0` exit 0；`tsc --noEmit --strict` exit 0；`playwright --list` = **Total: 72 tests** |
| 真实运行 | **72 passed（1.2m）/ 0 failed / 0 skipped**，`PLAYWRIGHT_EXIT=0`，TTL 门禁 `1200L` 先行通过 |
| 图片 | **72/72** 存在，体积 118,521–303,145 bytes（无异常小图），mtime 17:32:49–17:34:00 |
| 写入 | 无。`E2E_MUTATIONS=false`，全部只读，因此**无数据需清理** |
| 凭证 | 本轮两份（探测 `tokens-20260903-172150.json`、采集 `tokens-20260903-173240.json`）**均已销毁**；目录回到 12 个更早会话文件，未触碰 |
| manifest | **+72 条 → 共 194 条**，零重复 ID、零缺字段、图片与用例文件全部存在 |
| coverage | system-management +9 assets、messaging-monitoring +1、workflow +3、srm +5 |
| strict | exit 1、**恰好 8 个模块覆盖失败**、**非覆盖类错误 0** |
| links / sensitive | 均 exit 0 |

**gap 变动（如实，不夸大）**：

- workflow：移除 `tracking`（`/admin/workflow/instance` 四语言正式图，页面真实渲染 23 条实例与流转进度/审批记录入口）→ gaps 5→4。
- system-management：移除 `most-management-flows`（14 个 required_flows 中 12 个已有四语言图；menu 由权限管理承载、data-scope 由角色管理承载），
  新增**精确**的 `config-page-absent` 与 `login-record-page-absent` → gaps 数量仍 3→4，但语义从模糊变为可行动。
  实测依据：`sys_permission` 中无任何 `menu`/`data-scope`/`config`/`login`/`param` 相关权限码（仅 `xssconfig` 与 `srm:risk:config`），且前端无对应 view 目录。
  按约束**未删除这两个 required_flow、未自行 exempt**。
- srm / messaging-monitoring：资产显著增加，但 gaps **未减少**（列表视图不等于关闭 admission-lifecycle / stable-mobile-flow / detail-and-action-states / retry / dead-letter / trace-diagnosis）。
- 因此 strict 仍为 **8 个模块覆盖失败**，未降数；本批实际关闭 **1 个 gap**（workflow `tracking`）+ 1 个模糊 gap 精确化。

**有意排除并说明理由**：procurement 物料/订单/收货与 asset 台账/调拨/处置（活行为 0，空表无文档价值，待 §3.3 DATA_DEFECT 修复后补采）；
procurement 请购/询价（当前仅存历史 E2ESQ 测试残留行，不宜作正式文档图）；job-types（scheduling-workspace 已 covered，无 gap 收益）。

**i18n 观察（非缺陷，已核）**：多个页面的表格列标签在 ja-JP/ko-KR 下渲染为英文（如 `Permission Name`/`Type Code`/`Tenant Code`）。
两项权威检查均通过：`npm run ui:i18n:parity`（四语言各 **2319 键、0 缺失、0 placeholder 不一致**）与 `npm run ui:i18n:check`（**0/0 项**）。
→ 判定为语言包既有取值（译文质量待优化），**不是**硬编码缺陷；用例按**实际渲染值**断言，不美化。

### 3.6 对阶段 A 报告的诚实更正：workflow 历史实例残留

阶段 A §4 声称「本批 0 残留」**不完整**。实测发现本批还在 **omni-workflow** 留下了跨服务记录：

- `wf_process_instance_ext` ids **20-23**，`business_key` = `48:1`/`49:1`/`50:1`/`51:1`（即本批四份请购），`process_key=procurement-approval`，status=2，`create_time` 16:06:56 / 16:07:19 / 16:07:39 / 16:07:59 —— 与四语言运行时刻逐一对应。
- Flowable `ACT_HI_PROCINST` 共 23 条（含本批 4 条）。

为何未清理（与 `proc_event_inbox`/`srm_quotation_request` 同一类结论，但阶段 A **漏登记**）：
`wf_process_instance_ext` **无 `deleted` 列**，且与引擎管理的 `ACT_HI_*` 历史表强关联；用 SQL 硬删会造成扩展表与 Flowable 历史不一致，
正规删除需走引擎 `HistoryService`，不属本批授权的数据清理范围。→ 分类：**审计/历史台账，有意保留，现已显式登记**。

连带影响（已如实写入指南与 coverage 注释）：workflow 实例跟踪页的正式截图因此会显示带 `E2ESQ` 标识的真实标题。
本批**未为美化图片而造数据、裁剪或隐藏真实标题**。

### 3.7 阶段 B 第二批实际执行结果（srm `stable-mobile-flow` 已关闭）

**新增用例**：`omni-frontend/e2e-docs/flows/srm-portal-responsive.flows.spec.ts`（2 视口 × 4 语言 = 8 用例）。
视口沿用仓库既有响应式约定（390×844、1024×768，与 procurement approval-rules 一致）；
页签/按钮文案**沿用阶段 A 已实测通过的 selector 值**，不重新推测。

断言不只截图：页签 `toBeVisible` + 可点击，「刷新询价」按钮 `toBeVisible` + `toBeEnabled`，
以此证明窄屏下操作入口未被挤出或遮挡（即「稳定可用」的实质判据）。

| 项 | 实测结果 |
| --- | --- |
| 静态检查 | `eslint --max-warnings 0` exit 0；`tsc --noEmit --strict` exit 0；`playwright --list` = **Total: 8 tests** |
| 真实运行 | **8 passed（6.4s）/ 0 failed / 0 skipped**，`PLAYWRIGHT_EXIT=0`，TTL 门禁 `1200L` 通过 |
| 图片 | **8/8** 存在，26,032–46,304 bytes（窄屏故体积小于桌面图），mtime 18:00:42–18:00:48 |
| 图片质检 | `zh-CN/srm-portal-quotation-mobile` 已目检：中文 UI、三页签渲染、表格响应式收敛为「询价单号/询价主题/操作」三列、`刷新询价` 与逐行「修改报价」均可见可点、无破损布局 |
| 写入 | 无。仅开页/切页签/点刷新，`E2E_MUTATIONS=false`，**无数据需清理** |
| 凭证 | `tokens-20260903-180033.json` **已销毁**（POINTER_REMOVED=yes、BATCH_TOKEN_FILE_REMOVED=yes），目录回到 12 个更早会话文件 |
| manifest | **+8 条 → 共 202 条**（`step: responsive`，viewport 分别为 390×844 / 1024×768），零重复、零缺字段、图片/用例文件全在 |
| coverage | srm 新增 2 行 assets + `special_viewports`（两个视口绑定 `supplier-quotation`）；**gaps 3→2**，移除 `stable-mobile-flow` |
| 指南 | `docs/srm.md` 新增「供应商门户响应式稳定性截图（四语言）」小节；同时修正上一小节中已过时的表述（避免文档内部矛盾） |
| 摘要 | `docs/srm.md` → `62c98f8e7ec8…`；全量复核 **38 份源文档、0 摘要不匹配** |
| strict | exit 1、**恰好 8 个模块覆盖失败**、**非覆盖类错误 0** |
| links / sensitive | 均 exit 0 |

**附带佐证阶段 A 清理生效**：移动/平板图中列表仅剩 RFQ-1-15/16/17（历史残留），
本批阶段 A 的 RFQ-1-18…21 **已不再出现** → 与 §1 Step 4 的 POST-CHECK（本批 0 残留）相互印证。
图片如实保留真实列表内容，**未为填充画面而造数据**。

**累计阶段 B 已关闭 gap：2 个**（workflow `tracking`、srm `stable-mobile-flow`）+ 1 个模糊 gap 精确化（system-management）。
strict 模块失败数仍为 8（因为每个模块需**全部** required_flows 达标才能转 covered，关闭单个 gap 不会降数）——与主交接 C1 一致。

### 3.8 阶段 B 第三批实际执行结果（system-management 两个状态 gap 已关闭）

**先探测后编写**：用一次性临时探测用例（用后已删除，未提交）实测字典页对话框结构，
并以**列表总数前后对比自证未写入**（四语言均 `noDataCreated=true`，before=17 / after=17，因为只提交空表单）。

**新增用例**：`omni-frontend/e2e-docs/flows/system-dictionary.flows.spec.ts`（3 态 × 4 语言 = 12 图，每语言 1 用例）。
参照 crm/scheduling 已 covered 模块的既有范式：新建对话框 → 必填校验失败 → 创建成功。

| 项 | 实测结果 |
| --- | --- |
| 静态检查 | `eslint --max-warnings 0` exit 0；`tsc --noEmit --strict` exit 0；`playwright --list` = **Total: 4 tests** |
| 真实运行 | **4 passed（7.3s）/ 0 failed / 0 skipped**，`PLAYWRIGHT_EXIT=0`，`E2E_MUTATIONS=true`，TTL 门禁 `1200L` 通过 |
| 图片 | **12/12** 存在，133,349–167,230 bytes，mtime 18:22:38–18:22:43 |
| 图片质检 | `ja-JP/system-dictionary-create-validation` 已目检：日文 UI（並び順/キャンセル/確認）+ 红色错误提示 + 对话框保持打开 + 无敏感信息 |
| 写入防护 | `E2E_MUTATIONS` 未开则整组 skip 且写入抛错；创建意图产生即登记（typeCode 含 runStamp） |
| 清理 | afterAll 走正式 `DELETE /api/base/dict/type/{id}`：`registered=4 deleted=4 residual=0`；且用例内对残留做 `expect(...).toHaveLength(0)` 硬断言（字典为租户共享参考数据，不得静默留残） |
| 独立 DB 复核 | `sys_dict_type` = **17**（回到基线）；`E2EDICT-%` 残留 = **0**（type 与 data 两表）；`base-dictionary-catalog` 断言复现 = **101** = expectedRows（未被本批污染） |
| 凭证 | `tokens-20260903-182228.json` **已销毁**；目录回到 12 个更早会话文件 |
| manifest | **+12 条 → 共 214 条**，零重复、零缺字段、图片/用例文件全在 |
| coverage | system-management +3 assets；**gaps 4→2**，移除 `detail-and-action-states` 与 `failure-states` |
| 指南 | `docs/guides/system-security-audit.md` +§9（三态四语言表 + PRODUCT_DEFECT 登记）；摘要 → `8307115fbd85…` |
| strict | exit 1、**恰好 8 个模块覆盖失败**、**非覆盖类错误 0** |
| links / sensitive | 均 exit 0；全量复核 **38 份源文档、0 摘要不匹配** |

**gap 关闭依据**：

- `detail-and-action-states`：三态齐备（create-or-edit / failure-or-forbidden / key-action-success）× 四语言，与 CRM 已 covered 模块的定义一致。
- `failure-states`：业务必填校验失败四语言图（本批）+ 403 权限拒绝由 `permissions-exceptions` 模块的 `employee-forbidden-403` 四语言图闭环（与 authentication 模块同一分工约定，已在 coverage 注释中写明依据）。
- system-management 仅剩 `config-page-absent` 与 `login-record-page-absent` 两个 **产品未提供页面**的 gap（实测无权限码、无 view 目录），因此仍为 `partial`，未擅自 exempt、未删 required_flow。

**新登记 PRODUCT_DEFECT（i18n，本批不修产品）**：

1. `views/base/dict/index.vue` **未定义任何 form rules** → 前端不做必填校验（`.el-form-item__error` 实测为空），错误完全依赖后端 400。
2. `CreateDictTypeRequest` 的 `@NotBlank(message = "字典类型编码不能为空")` 为**中文硬编码** → 四语言 UI 下错误提示均为中文（已在 ja-JP 图中目检确认）。
3. ja-JP/ko-KR 的 `dict.createType`/`typeCode`/`typeName`/`remark` 语言包**取值本身即英文**（仅「排序/並び順/정렬」已译）；
   因 `ui:i18n:parity`（2319 键、0 缺失）与 `ui:i18n:check`（0/0）均通过，属**译文完整度**问题，现有工具无法检出「值语言错误」。

处置：截图**如实保留真实文案**（未 mock、未美化、未隐藏），已在 coverage 注释、指南 §9 与 manifest `expected` 三处同步登记；
修复需产品侧决策（后端消息国际化方案 + 补齐 ja/ko 译文），**所需输入：授权修改后端校验消息机制与语言包**。

**累计阶段 B 已关闭 gap：4 个** —— workflow `tracking`、srm `stable-mobile-flow`、system-management `detail-and-action-states`、system-management `failure-states`；
另将 system-management `most-management-flows` 精确化为两个产品缺失页面 gap。
strict 模块失败数仍为 8（需全部 required_flows 达标才能转 covered）——与主交接 C1 一致，未降数、未改检查器。

### 3.9 提交与推送台账（含一次瞬时推送失败的完整处置）

| 提交 | 内容 | 文件数 | 交付 |
| --- | --- | --- | --- |
| `653afe3` | 阶段 A：SRM supplier-quotation gap 闭环 | 30 | PUSHED（gitee+github） |
| `99ad567` | 阶段 C：srm 三语译文事实矛盾修正 + 队列重生成 | 5 | PUSHED |
| `c40c7cc` | 阶段 B 第一批：72 张只读管理页截图，关闭 workflow `tracking` | 80 | PUSHED |
| `31ee4e8` | 阶段 B 第二批：8 张响应式图，关闭 srm `stable-mobile-flow` | 14 | PUSHED |
| `6c03b32` | 阶段 B 第三批：12 张字典三态图，关闭 system-management 两个状态 gap | 18 | PUSHED（见下方事件） |
| `cc5cdfd` | 阶段 B：记录进度与暂停 checkpoint（`docs(evidence)`） | 1 | PUSHED |
| `4047e5b` | 阶段 C：srm 三个截图章节补译 en/ja/ko（结构对齐） | 3 | PUSHED |
| `1f01f55` | 阶段 B 第四批：16 张只读详情弹层图（含加载态质检拦截修正，§3.10） | 23 | PUSHED |
| `e299d1e` | 阶段 C：P0 architecture 首轮（四库章节 + 模块计数 + Compose + Flowable 8.x，§4.5.1-4.5.2） | 15 | PUSHED |
| 本批 | 阶段 C：P0 architecture 收尾（§5 +4 行 + 缺陷 A/B/C，三语 ALIGNED 138/138，§4.5.3-4.5.8） | 6 | 提交推送后 remote==local（见 §4.5.8） |

**`6c03b32` 推送事件（如实记录）**：

1. 首次 push 守卫返回 `FAST_FORWARD_PROVEN=yes` 但 `PUSH_EXIT=128`，守卫按设计输出 `PUSH_FAILED: local commit retained, remote unchanged` 并**停止**，未拉取/合并/强推/改配置。
2. 定向诊断：`git remote -v` 显示 `origin` 配了**两个 push URL**（gitee 与 github）。直接重试并捕获 stderr 得到真实原因：
   gitee 返回 `Everything up-to-date`（已成功），github 报 `unable to access … Failed to connect to 127.0.0.1 port 7897`——本地代理端口不可达。
3. 取证：`Test-NetConnection 127.0.0.1:7897` = **True**（已恢复）；`git ls-remote https://github.com/…` exit 0；
   当时 gitee=`6c03b32`、github=`31ee4e8`（落后一个提交），`git config --get http.proxy/https.proxy` 均为空（代理非仓库级配置）。
4. 带新证据重试（非盲试）：先 `git merge-base --is-ancestor 31ee4e8 HEAD` exit 0 证明可 fast-forward，再普通 push → **`PUSH_EXIT=0`**，github `31ee4e8..6c03b32`。
5. 最终核验：**LOCAL = GITEE = GITHUB = `6c03b3254f1ce760fc779ce8b3623530eeef96a1`**；暂存区空；无未提交的跟踪变更。

处置约束遵守情况：**未**修改 git config（含未删除 github push URL、未改代理）、**未** force push、**未** merge/rebase/reset 消除差异；
失败时先保留本地现场并报告，仅在取得「代理已恢复 + github 可达 + 可 fast-forward」三项新证据后重试一次。
分类：瞬时环境阻塞（已自消），**不构成 REMOTE_DIVERGENCE_STOP**（无分叉，两端均为本地 HEAD 祖先）。

### 3.10 阶段 B 第四批实际执行结果（只读详情弹层；含一次质检拦截）

**环境变化**：本批开始前发现 **Docker 守护进程已完全停止**（`dockerDesktopLinuxEngine` 管道不存在，无 docker 进程）。
已启动 Docker Desktop（非破坏性：**未重建、未清卷、未改配置**），15 个容器自动恢复为 running / 14 healthy，前端 HTTP 200，与停机前一致。

**探测**：用一次性临时用例（用后已删除）按 §8.2 已固化的四语言按钮文案逐个打开 4 个弹层，实测标题/字段标签/按钮（4 passed / 1.3m）。

**新增用例**：`omni-frontend/e2e-docs/flows/detail-overlays.flows.spec.ts`（4 弹层 × 4 语言 = 16 图，只读）：
流程实例「流转进度」「审批记录」、流程模型「版本历史」、MQ 消息「查看详情」。

**图片质检拦截到一个真实缺陷（重要过程记录）**：

- 首轮 16 passed 后目检 `zh-CN/workflow-instance-progress.png`，发现弹层内是 **BPMN 异步加载中的转圈态**（画布空白 + spinner + BPMN.IO 水印），**不是有效文档图**。
- 根因：该 scene 是四个场景中**唯一 `expectLabel: null`** 的，缺少内容就绪断言就截图；其余三个因断言了本地化字段标签而内容已确认渲染。
- 定位：`components/workflow/ProcessProgressDialog.vue` 用 `v-loading="loading"` 包裹 `.bpmn-viewer-wrap`，`NavigatedViewer.importXML` 完成后才有 `.djs-container svg` 与 `.djs-element`，最后 `canvas.zoom('fit-viewport')`。
- 修正：新增 `readySelector` 字段（该 scene 为 `.bpmn-viewer-wrap .djs-element`），并对**所有** scene 统一增加 `.el-loading-mask` 必须已消失的兜底断言（超时 30s）。
- 重跑：**16 passed（25.9s）/ 0 skipped**，图片 mtime 由 01:20:29–01:20:48Z 更新为 01:23:37–01:24:00Z；
  重拍后目检确认为**完整渲染的真实流程图**：提交 → 采购经理审批 → 审批结果网关 → 审批通过/审批驳回，
  已执行节点绿色（`completed-node`）、未走过分支灰色，无加载态。**加载态图片未被登记为正式资产**。

| 项 | 实测结果 |
| --- | --- |
| 静态检查 | `eslint --max-warnings 0` exit 0；`tsc --noEmit --strict` exit 0；`playwright --list` = **16 tests** |
| 真实运行 | 修正后 **16 passed（25.9s）/ 0 failed / 0 skipped**，`PLAYWRIGHT_EXIT=0`，TTL 门禁 `1200L` |
| 图片 | **16/16**，96,047–198,674 bytes，无异常小图 |
| 写入 | 无。仅点击查看类动作，明确避开设计/校验/发布/删除/终止/重发/跳过；`E2E_MUTATIONS=false`，**无数据需清理** |
| 凭证 | 本轮两份（`tokens-20260904-091315.json` 探测、`tokens-20260904-092328.json` 采集）**均已销毁**；目录实数回到 12 |
| manifest | **+16 条 → 共 230 条**（`step: detail`），零重复、零缺字段、图片/用例文件全在 |
| coverage | workflow +3 assets、messaging-monitoring +1 asset；**gaps 未减少**（理由已写入注释，见下） |
| 指南 | `docs/workflow.md` §8 新增「只读详情弹层」小节；`docs/mq-reliability.md` 新增 §10；两份摘要已刷新（`238d45b19b05…` / `93cbfdcba530…`） |
| strict | exit 1、**恰好 8 个模块覆盖失败**、**非覆盖类错误 0**；links / sensitive 均 exit 0；38 份源文档 0 摘要不匹配 |

**本批未关闭任何 gap，已如实登记两项 BLOCKED（含硬证据）**：

1. **messaging-monitoring `retry` / `dead-letter` / `detail-and-action-states`（操作态）**：实测 5 个库 `sys_mq_message` 共 **809 行**
   （base 87、procurement 644、srm 34、workflow 23、crm 21），**status 全为 1，无任何 FAILED / DEAD_LETTER**。
   要产出这两个状态必须向跨租户共享的 outbox 注入必然失败的消息并让 relay 按 `2^retryCount × 10s` 反复重试报错，
   属「在共享基础设施上制造故障」，与指令「不制造生产故障」相冲 → **需单独授权**。
2. **workflow `model-lifecycle` / `detail-and-action-states` / `failure-states` / `countersign`**：前三项需「建模→BPMN 设计→校验→发布」写入链，
   而发布会向**共享 Flowable 引擎**部署流程定义（`ACT_RE_*`）且未经验证存在干净的删除/回滚路径；
   `countersign` 另需**多审批人身份**（现有受信任身份仅 admin/supplier1，禁止临时越权）→ **需单独授权/新增身份确认**。

**累计阶段 B 已关闭 gap：仍为 4 个**（workflow `tracking`、srm `stable-mobile-flow`、system-management `detail-and-action-states` 与 `failure-states`）；
本批新增 **16 张真实只读详情图**作为 workflow/messaging-monitoring 的必需资产，但未谎报 gap 关闭。strict 仍 8。

## 4. 阶段 C：四语言文档预审与修订

### 4.1 队列实测

`npm run docs:i18n:queue` 重新生成成功（exit 0）：**中文事实源 38 篇，译文待复核 114 篇（en/ja/ko 各 38），已完成人工复核 0 篇**。
优先级：**P0 system-truth 3 篇**（architecture `bb2700158866`、api-contract `96cebb47233a`、core-flows `2d2afd48d592`）；
**P1 开发/模块指南 8 篇**（backend-patterns、frontend-patterns、scheduling、workflow、crm、**srm `aa1a1e2a96a5`**、mq-reliability、guide-scaffold-development）；**P2 其他 27 篇**。
`docs/i18n-review-queue.md` 因 srm 源摘要变化而重生成，已纳入本轮提交。

### 4.2 srm 组预审记录（P1，本轮实质完成 1 组）

- **源**：`docs/srm.md`，摘要 `aa1a1e2a96a5f37686f12c03b226224e8ff5d3eea69877b7bbbf14b0b12f1d8a`（本批阶段 A 新增截图章节后）。
- **译文**：`docs/srm.en.md`、`docs/srm.jp.md`、`docs/srm.kr.md`（均 445-448 行，章节结构与源一致，`## 10.` 分别在 424/426/426 行）。
- **检查范围**：章节结构对齐、主要流程与前置条件、API 路径、权限码、术语、数字与精度、代码块与反引号标识、图片引用、正字法。
- **发现的问题（严重，事实性矛盾）**：三份译文在 §9 各保留一个中文源**已不存在**的子章节「Phase 2 报价预留 / Phase 2 Quotation Reservation / Phase 2 見積り予約 / Phase 2 견적 예약」，声称「MVP 不创建报价表、不注册报价端点、不发行 `srm:portal:quotation` 权限」。
  该陈述与**本批已验证的运行事实直接矛盾**：`srm_quotation`/`srm_quotation_line`/`srm_quotation_request` 三表均存在且有真实数据；`srm:portal:quotation` 权限存在（MENU，path `/400/440/444/`，status=1）；四语言 E2E 已通过该端点真实提交报价并流转 QUOTED。
  结构计数也印证偏差：中文源 27 个 `###`，en 译文 26 个。
- **已做修改**：将三份译文的该子章节替换为与中文源一致的「Procurement 报价集成」完整译文（门户三端点、提交请求字段白名单、服务端派生字段、`srm_quotation`/`srm_quotation_request` 幂等与唯一性约束、金额精度 `DECIMAL(19,6)`/`DECIMAL(19,4)`、同事务与 409 语义、`version=0` 创建哨兵、事件 payload 字段与 Inbox 幂等消费）。代码块、API 路径、权限码、表名与字段名保持不翻译。另修正日文译文混入的中文字形（身份→身分、幂等→冪等）。
- **剩余疑问 / 未完成**：中文源本批新增的「### 供应商报价流程截图（四语言）」（含公共前置条件表与三个步骤四要素、12 个图片引用）**尚未译入 en/ja/ko**，三份译文仍缺该节；插入锚点为各文件 `## 10.` 之前。这是明确、有界、可续做的下一项。
- **状态处理**：三份译文在 `docs/docs-manifest.yaml` 中**仍保持 `present-unverified` / `reviewed_at: null`**。本轮属 Qoder 实质性预审与修订，**不替代 preflight 指定的独立 Codex final review 或人工验收**，未批量填 `synchronized`，未把自审冒充外部验收。
- **校验**：修改后 `npm run docs:links:check` exit 0；`docs-quality.mjs --scope=sensitive --allow-draft` exit 0。

### 4.3 阶段 C 其余项

P0 3 篇 ×3、P1 其余 7 篇 ×3、P2 27 篇 ×3 = **111 项译文 NOT_STARTED**。按同一源成组推进，下一组建议 P0 `architecture`。

### 4.4 新发现并修正：4 项既有源摘要陈旧（HEAD 即存在）

刷新本批三份源文档摘要后做全量复核，发现 `docs/docs-manifest.yaml` 中 **4 份指南的 `source_sha256` 与真实源文件不匹配**：
`guide-authentication`、`guide-permissions`、`guide-scheduling`、`guide-crm-flow`。

归因证据：这 4 份 guide 在本轮工作区**未被修改**（`git status` 无条目），且 `sha256(git show HEAD:<file>)` 与当前工作区摘要完全相同、两者均与已提交 manifest 值不符
→ **不匹配在 HEAD 即已存在**，属之前有人修改 guide 却未刷新 manifest，非本轮造成。

影响：`docs-quality.mjs --scope=translations` 会对这 4 项报「中文事实源摘要已变化」，独立于 114 篇 `present-unverified` 问题。

处置：按阶段 C 授权「允许修正文档/译文并刷新真实源摘要」，将 4 项刷新为真实当前摘要（行级精确替换，不重排 YAML）。
刷新后全量复核：**38 份源文档、0 摘要不匹配**。
该修正**不掩盖**译文陈旧：114 项译文状态仍全部为 `present-unverified` / `reviewed_at: null`（已程序化复核分布确认）。

本批同时刷新的三份源摘要：`docs/srm.md` → `c8462a0cbbba…`、`docs/workflow.md` → `36f7622d14b9…`、`docs/guides/system-security-audit.md` → `cd346252b455…`。

### 4.5 P0 architecture 组实质预审与修订（本轮完成）

#### 4.5.1 先建立客观度量工具（对剩余 102 项均有杠杆）

依据仓库自有约定（`docs/i18n-review-queue.md` 复核流程第 2 条：「代码块、命令、API 路径、权限码不翻译」），
编写平价检查器（`scripts/.work/qoder-doc-parity.mjs`，不提交），对 **114 组源/译文** 客观比对：
围栏代码块数、表格行数、标题数与层级序列、Markdown 链接集合、反引号内联代码集合、`/api/` 路径集合、权限码集合。
链接比对已做归一化（译文指向 `xxx.en.md` 等本地化交叉链接是正确行为，不计作差异；剔除随语言变化的 `#锚点`）。

**全量完整度分级结果**（按标题/代码块/表格行最差比值判定）：

| 分级 | 组数 | 含义 |
| --- | --- | --- |
| ALIGNED | 48 | 结构齐备，仅需语义复核 |
| PARTIAL | 21 | 局部缺失 |
| MAJOR_GAP | 20 | 大块缺失 |
| **STUB** | **25** | 译文几乎为空壳 |

**关键结论：G7 的翻译阻塞不是「复核 114 篇」，而是约 45 个文档-语言对存在内容缺失，其中 25 组几乎为空壳。**

STUB 清单（译文/源 标题数，代码块，表格行）：
`asset-design` 8/52、0/18、0/124；`crm-design` 9/55、0/32、0/124；`srm-design` 8/53、0/22、0/138；
`procurement-design` 8/54、0/20、0/128；`full-functional-audit` 11/74、0/2、0/97；
`full-functional-audit-remediation` 8/10、0/0、0/80；`scaffold-upgrade-implementation-plan` 23/133、0/28、11/265；
`custom-preset-tutorial` ja/ko 1/6；`guide-troubleshooting` ja/ko 表格 0/7。

MAJOR_GAP 清单：**`api-contract` 三语均 53/85 标题、30/72 代码块、139/305 表格行，缺 101 个 API 路径与 56 个权限码**（P0 系统真相文档）；
`workflow` 三语 32/39、22/40、58/92；`guide-system-security-audit` 8/11、5/22（部分因本目标阶段 B 新增图片表而拉大，已登记为新增待补译项）；
`observability`、4/8 代码块、6〜14/29 表格行；`scaffold-upgrade-plan` 11/36、12/54；`preset-quick-selection`、`preset-maintenance` ko、`custom-preset-tutorial` en。

已验证的正向结果：**`srm` 三语言均 ALIGNED**（43/43 标题、18/18 代码块、94/94 表格行），证明 §4.2 的补译是结构完整的。

#### 4.5.2 architecture 组已修正的四类缺陷（均有硬证据）

1. **§9.2 缺 4 个数据库章节**：源有 7 个 `####` 逐库子章节，en/ja/ko 均只有 3 个 —— `omni_crm`、`omni_srm`、`omni_procurement`、`omni_asset` 在三语言中完全缺失。已补全（包含表清单、租户/数据范围继承语义、幂等约束与指向各 design 译文的链接）。jp/ko 另补回了缺失的 workflow「详见」链接行。
2. **§4 模块清单数量错误（最严重的事实缺陷）**：源为「Common 生态（**10 个**）」与「微服务模块（**8 个**）」，而三份译文写成「**8** Auto-Configuration Modules」与「Microservice Modules (**4**)」，且表中缺 `omni-common-workflow`、`omni-common-service` 与 `omni-crm`/`omni-srm`/`omni-procurement`/`omni-asset`。
   → 照译文阅读会以为平台只有 4 个微服务。已补齐至 10 + 8，标题数量同步更正。现四份文档均含全部 18 个模块名（程序化核验 `missingModules=NONE`）。
3. **Compose 命令陈旧（会导致实操失败）**：三份译文写 `docker compose up -d` + `docker-compose.yml` + 「12 容器」，而实际仓库为根目录 `compose.yaml` 通过 include 合并 `compose.infra.yaml`/`compose.apps.yaml`、`--profile full` 共 **16 容器**，日常开发用 `omni dev up --preset <id>`，可选 `--observability`。已按源改写 3 处/语言，并补全启动顺序中缺失的 CRM/SRM/Procurement/Asset。核验：四份文档 `stale_refs=0`。
4. **全仓 Flowable 版本陈述陈旧**：`omni-backend/pom.xml` 为 `<flowable.version>8.0.0</flowable.version>`，且 `architecture.md` 模块表自身已写 `Flowable 8.0.0`，但全仓 **9 个文件共 20 处**仍写 `Flowable 7.x`（含中文源、三语译文、`workflow.md` 组与 4 份 README）。
   已先确认**无任何 `flowable-7` 锚点入站引用**（改标题不断链），再统一改为 `Flowable 8.x`。
   核验：`Flowable 7.x` **20 → 0**，独立 `git grep 'Flowable 7'` 无匹配，`Flowable 8.x` 分布 20 处与原位置一致。
   同时修正 `README.jp.md` 混入的中文 `会籤` → 日文 `会署`，以及 `architecture.jp.md` 的 `定时` → `定期`。

源文档修改后已刷新摘要：`docs/architecture.md` → `dc60013a2cba…`、`docs/workflow.md` → `f6dc03e604a0…`；全量复核 **38 份源文档、0 摘要不匹配**。
门禁：`docs:links:check` exit 0（含锚点校验）、`docs:readme:check` exit 0、`--scope=sensitive` exit 0、`docs:i18n:queue` 重生成 exit 0。

修复后平价：architecture **en-US → ALIGNED**（57/57 标题、36/36 代码块、129/133 表格行）；ja-JP 56/57、32/36、119/133；ko-KR 57/57、34/36、109/133（仍 PARTIAL）。

#### 4.5.3 architecture 组已完全对齐（本轮收尾）

续修后逐节比对结果：**en / ja / ko 三份译文的「有内容缺口的章节数 = 0」**，且平价指标均为 **57/57 标题、36/36 代码块、138/138 表格行**（三份均 ALIGNED）。全库完整度分布由 ALIGNED 48 / PARTIAL 21 变为 **ALIGNED 50 / PARTIAL 19**（MAJOR_GAP 20、STUB 25 不变）。

本轮续修的三项：

1. **ja §11 RBAC 内容补齐**：§11.2 权限树表（`DIRECTORY`/`MENU`/`BUTTON`/`API`）、§11.3 请求级数据流图与两种过滤模式表、§11.4 整节（含 `core-flows` 链接）。
2. **ja/ko §13 被缩写的内容补齐**：§13.1 的 `getProviderId()`/`buildAuthorizationUrl()`/`exchangeCodeForAccessToken()`/`fetchUserProfile()` 与 `ProviderUser` DTO、`Map<String, OAuth2ProviderHandler>` 自动发现句；§13.2 的 `xss:enabled:{tenantId}` + `xss:rules:{tenantId}`；§13.3 的 `type_code` 唯一性与 `scheduling` 链接。
3. **三语 §5「局部与整体关系」表补 4 行**：`omni-crm`/`omni-srm`/`omni-procurement`/`omni-asset` 四个业务域（此前译文只到 `omni-workflow`，读者无法从架构文档得知四个业务域存在）。

#### 4.5.4 本轮新发现的三个事实缺陷（已修，均有权威证据）

**缺陷 A：`omni_auth` 表清单与计数错（源文档自身缺陷，影响四份）**

- 权威证据：`database/changelog/auth/0001-auth-schema.yaml`（`adoption-baseline` 结构基线）共 **19 张表** = 3 个 `oauth2_*` + 16 个 `sys_*`。
- 文档原状：标题写「omni_auth 数据库（**14 表**）」「多租户 RBAC（**11 表**）」，但实际列出 3 + 13 = 16 行；且**完全遗漏 3 张表**：`sys_user_role_scope`、`sys_audit_log`、`sys_portal_role_request`。
- 严重性：`sys_user_role_scope` 正是阶段 A workflow-500 修复与数据权限/候选人解析的核心表，在 P0 架构文档中缺席影响很大。
- 修正：标题改为 19 表，RBAC 组改为 14 表并补 `sys_user_role_scope` 行，新增「安全审计与门户角色（2 表）」分组；用途描述均取自 DDL 真实 COMMENT 与列定义（`scope_mode`: SAME_UNIT / UNIT_AND_BELOW、`event_type` 枚举、`status`: PROCESSING/COMPLETED/FAILED），**未臆测语义**。en/ja 同步；ko 原本把两张表写成**逗号散文列表**（丢失用途列、0/20 表格行），已重建为与源一致的三张表。

**缺陷 B：§14.5 Gateway 路由示例在三份译文中均错（高危、可操作）**

- 译文 yaml 均多出 `filters:` + `- StripPrefix=2`，且均丢失源文档那句「下游 Controller 保留并声明完整 `/api/order/**` 路径；当前网关不做 `StripPrefix`」。
- 权威证据：`omni-backend/omni-gateway/src/main/resources/application.yml` 中 `StripPrefix` 出现 **0 次**，所有业务路由仅 `predicates: - Path=/api/<service>/**` 无 filters；Controller 声明完整路径（如 `@RequestMapping("/api/crm/lead")`）。
- 危害：照译文配置会剥掉 `/api/<service>` 前缀，**直接打断新服务路由**。
- 修正：三份译文删除错误 `filters`，补回源文档该句（与源逐字一致，未自行添加额外解释以免与源分叉）。

**缺陷 C：§14.6 权限种子 SQL 在 en 中也是错的（附带发现）**

- en 代码块用 `name, code` 列名、`'order:list:page'`、类型 `'BUTTON'`、path `'/order'`/`'list'`/`NULL`；而真实 schema 与源文档为 `permission_name, permission_code`、`'order:list'`、类型 `'API'`、物化路径 `'/<目录ID>/'`。已用阶段 A 核实的真实 seed 语句交叉印证（`INSERT IGNORE INTO sys_permission (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)`）。
- ja/ko 则**完全缺失该 SQL 代码块**。
- 修正：按仓库「代码块不翻译」约定，三份译文统一采用源的 verbatim SQL。

#### 4.5.5 architecture 组剩余细小差异（不阻碍 ALIGNED）

- ja 缺 4 个内联代码提及：`(username, tenant_id)`、`XxlJobAdminClient`、`XxlJobSpringExecutor`、`resource:action`。
- ko 缺 4 个：`DataPermissionHandlerImpl`、`XxlJobAdminClient`、`XxlJobSpringExecutor`、`spring.cloud.gateway`。
- 三语均缺 `/api/crm/` 路径提及（1 处）；en 有 1 个额外内联代码。
- 均为已对齐章节内的散文级提及，**结构平价已 100%**，待后续逐句语义复核时一并处理。

#### 4.5.6 附带发现（待验证，本轮未改 coverage）

`sys_audit_log.event_type` 的 DDL 枚举包含 **LOGIN_SUCCESS / LOGIN_FAILED / LOGOUT / ACCOUNT_LOCKED / PASSWORD_CHANGED** 等，说明登录记录可能实际由「审计日志」页（`system:auditlog`，本目标已采四语言图 `system-audit-log`）承载。因此阶段 B 将 system-management 的 `login-record` 归为「产品未提供页面」可能过于保守。**但本轮 Docker 守护进程已停，无法查运行库确认该页确实展示登录类事件**，故**未改 coverage**，仅登记为待验证项（恢复运行栈后用一条只读查询可定：按 `event_type` 统计 `sys_audit_log`）。另：AGENTS.md 称登录日志表为 `sys_login_log`，而实际 DDL 为 `sys_audit_log`（AGENTS.md 不在 docs-manifest 范围内，本轮未改）。

#### 4.5.7 以下为中间过程的定位记录（已被 4.5.3–4.5.6 取代，保留不删）

##### 旧：architecture 组剩余待办（当时定位，现已完成）

- ja-JP 缺 4 个代码块、ko-KR 缺 2 个代码块；三语各缺 4〜24 行表格（en 129/133、ja 119/133、ko 109/133）。
- ja-JP 标题 56/57（尚缺 1 个）。
- 缺失的内联代码提示位置：`/api/auth/menus`、`/api/order/**`、`DataPermissionHandlerImpl`、`SocialLoginServiceImpl`、`Map<String, OAuth2ProviderHandler>`、`buildAuthorizationUrl()`、`exchangeCodeForAccessToken()`、`fetchUserProfile()`、`getProviderId()`、`XxlJobAdminClient`、`XxlJobSpringExecutor`、`spring.cloud.gateway`、`xss:enabled:{tenantId}`、`DIRECTORY`/`MENU`/`BUTTON`/`API` 等（集中于 §6 局部架构、§11 RBAC、§13 扩展点）。
- §5 依赖关系图的 ASCII 图在译文中仅含 auth/base/gateway，缺 crm/srm/procurement/asset（属代码块内内容，需整块重绘）。

**状态处理**：`architecture` 三项译文在 `docs/docs-manifest.yaml` 中**仍保持 `present-unverified` / `reviewed_at: null`**。
本轮为 Qoder 实质预审与修订，**不替代独立 Codex final review 或人工验收**，未批量填 `synchronized`。

#### 4.5.8 接管复验（2026-09-04 新会话，对已 stage 字节独立重跑，非沿用上一会话结论）

新会话按用户执行提示「先最小核对当前 Git 与运行现场」接管，对**已 stage 但未提交的 architecture 批次字节**独立重跑客观门禁，全部通过：

| 检查 | 命令 | 结果（2026-09-04 实测） |
| --- | --- | --- |
| 平价（architecture 三语） | `node scripts/.work/qoder-doc-parity.mjs` | en/ja/ko 均 **ALIGNED**，57/57 标题、36/36 代码块、**138/138** 表格行 |
| 全库完整度分布 | 同上 | **ALIGNED 50 / PARTIAL 19 / MAJOR_GAP 20 / STUB 25**（=114），与 §4.5.3 一致 |
| 源摘要新鲜度 | `docs-quality.mjs --scope=i18n --allow-draft` | exit 0；`architecture.md` 实测 SHA256=`5fc881579377…` == manifest 值（缺陷 A 改源后已刷新） |
| 链接 | `docs-quality.mjs --scope=links` | exit 0 |
| 敏感内容 | `docs-quality.mjs --scope=sensitive --allow-draft` | exit 0 |
| Flowable 版本 | `git grep "Flowable 7" -- docs/*.md` | 0 处真实陈旧引用（仅本 checkpoint 的修复叙述行命中，非产品文档） |
| Compose 陈旧引用 | `git grep "docker-compose.yml" / "12 容器"` | `docs/architecture*.md` 0 处 |

注：`docs-quality.mjs` 无 `--scope=translations`（§4.4 旧称），源摘要新鲜度实际由 `--scope=i18n` 承载，本轮以其为准。

**运行现场**：`docker compose ls` = `omni-wp09-docs running(15)`；14 healthy + frontend（无 healthcheck），与 §8.1 一致，**未重建、未清卷、未改配置**。

**Git 现场**：本地 HEAD = 远端 = `e299d1e`（`git ls-remote origin` 单值，无分叉）；本批 6 个文件（`architecture.md`/`.en`/`.jp`/`.kr` + `docs-manifest.yaml` + 本 checkpoint）已 stage，工作区无未暂存跟踪变更（用户手动编辑的 `architecture.kr.md` 已与 index 一致，parity 未因该编辑破对齐）。提交后 local 领先 `e299d1e` 一格，fast-forward 推送。

**下一可执行项（阶段 C P0 续）**：architecture 组已收尾；P0 剩 `api-contract`（parity=MAJOR_GAP，三语 53/85 标题、30/72 代码块、139/305 表格行，缺 101 API 路径 + 56 权限码）与 `core-flows`。`api-contract` 为大体量补译，需评估工量后分组推进；不属外部阻塞。

#### 4.5.9 完整度矩阵：STUB / MAJOR_GAP 穷举清单（G7 真实工量，2026-09-04 parity 实跑）

本清单由 `node scripts/.work/qoder-doc-parity.mjs` 客观生成（写 `scripts/.work/qoder-doc-parity.txt`，不提交），按文档分组穷举 **STUB 25 对 + MAJOR_GAP 20 对**（与 §4.5.8 分布一致）。指标格式：标题/代码块/表格行（译文/源）。这是 G7 翻译阻塞的**真实工量**——非「复核 114 篇」，而是下列 45 对存在实质内容缺失（另 19 对 PARTIAL 为局部缺失，50 对 ALIGNED 仅需语义复核）。

**STUB（25 对，译文几乎为空壳，工量最重）**：

| 文档 | 语言 | 标题 | 代码块 | 表格行 |
| --- | --- | --- | --- | --- |
| `asset-design` | en/ja/ko | 7〜8/52 | 0/18 | 0/124 |
| `crm-design` | en/ja/ko | 8〜9/55 | 0/32 | 0/124 |
| `procurement-design` | en/ja/ko | 7〜8/54 | 0/20 | 0/128 |
| `srm-design` | en/ja/ko | 7〜8/53 | 0/22 | 0/138 |
| `full-functional-audit` | en/ja/ko | 7〜11/74 | 0/2 | 0/97 |
| `full-functional-audit-remediation` | en/ja/ko | 7〜8/10 | 0/0 | 0/80 |
| `scaffold-upgrade-implementation-plan` | en/ja/ko | 23/133 | 0/28 | 11/265 |
| `custom-preset-tutorial` | ja/ko | 1/6 | 4/8 | 0/0 |
| `guide-troubleshooting` | ja/ko | 10/10 | 0/0 | 0/7 |

**MAJOR_GAP（20 对，大块缺失）**：

| 文档 | 语言 | 标题 | 代码块 | 表格行 |
| --- | --- | --- | --- | --- |
| `api-contract`（P0 系统真相） | en/ja/ko | 53/85 | 30/72 | 139/305 |
| `workflow`（P1） | en/ja/ko | 32/39 | 22/40 | 58/92 |
| `guide-system-security-audit` | en/ja/ko | 8/11 | 0/0 | 5/22 |
| `observability` | en/ja/ko | 8/8 | 4/8 | 6〜14/29 |
| `scaffold-upgrade-plan` | en/ja/ko | 11/36 | 0/0 | 12/54 |
| `preset-quick-selection` | en/ja/ko | 2〜5/5 | 2/4 | 7/7 |
| `custom-preset-tutorial` | en | 4/6 | 4/8 | 0/0 |
| `preset-maintenance` | ko | 3/6 | 2/2 | 0/0 |

观察：`*-design`（asset/crm/procurement/srm）四份设计文档三语均为 STUB（代码块/表格几乎全缺），是单一最大工量簇；`api-contract`（P0）三语均 MAJOR_GAP，缺 101 个 API 路径 + 56 个权限码（§4.5.1）。`guide-system-security-audit`/`observability` 的部分缺口由本目标阶段 B 新增图片表拉大（§4.5.1 已登记为新增待补译项）。本清单仅为 G7 提供客观工量底数，**不替代独立 Codex final review 或人工验收**，未据此修改任何译文 `status`/`reviewed_at`。

### 4.6 P0 core-flows 组实质预审与修订（2026-09-04 完成）

- **源**：`docs/core-flows.md`（1871 行，本轮**未动源**，摘要不变；i18n 门禁 exit 0 佐证）。
- **译文**：`docs/core-flows.en.md`、`.jp.md`、`.kr.md`（修订前各 1619 行，三份结构与行号完全一致）。
- **检查范围**：标题层级序列、围栏代码块、表格行、Markdown 链接、`/api/` 路径、权限码、术语正字法、mermaid 约定。

**发现的问题（结构性缺失，P0 系统真相文档）**：三份译文均缺 **Flow 13-18 共 6 个流程**（源 20 个 `##`，译文仅 14 个）：Flow 13/14（CRM 线索幂等转换、商机推进与权限隔离）、Flow 15/15.1（SRM 供应商准入审批、请购审批规则配置与匹配试算）、Flow 16（Procurement 请购审批与异步回写）、Flow 17（Procurement 收货到 Asset 建卡/调拨/处置）、Flow 18（SRM 门户邀请入驻与角色分配 Saga）。合计缺 14 个标题（6 `##` + 8 `###`）+ 7 个 mermaid 代码块。归因：这 6 个流程是源文档在译文完成之后新增（CRM/SRM/Procurement/Asset 四个业务域的端到端流程），译文从未回填；en/ja/ko 三份缺口完全一致。严重性：P0 文档中四个业务域的核心流程在三语言译文里**完全缺席**。

**已做修改**：

1. 三份译文补入 Flow 13-18 全文（各 **+243 行**），按源顺序插入（Flow 13/14 置于 Docker 章节前，Flow 15-18 置于故障排查章节后，与源一致：Flow 15-18 之间无 `---` 分隔）。遵循仓库既有约定：叙述文案/标题译为目标语言，`/api/` 路径、权限码、字段名、SQL、内联代码保持 verbatim。**mermaid 沿用现有 jp/ko 约定（英文）**——现有 Flow 1-12 的 jp/ko mermaid 即为英文，故新增 6 个流程的 jp/ko mermaid 同样用英文以保内部一致；en mermaid 按 en Flow 1 约定译为英文。
2. 术语正字法修正（本轮预审新发现，均为 Flow 1-12 既有泄漏的中文字形）：
   - `core-flows.jp.md`：`身份` → `身分` **11 处**（日语正确字形为 身分）。
   - `core-flows.kr.md`：**13 处**汉字泄漏 → 谚文：`身份`→`신원`、`排查 방법`→`문제 해결 방법`（5 处表头，并补齐列分隔空格）、`入口`→`진입점`、`本部门`→`본 부서`（4 处）、`装配`→`구성`、`自助`→`셀프서비스`、`生效`→`적용`。
   - 复核：`grep '\p{Han}' core-flows.kr.md` = **0**；`grep 身份 core-flows.jp.md` = **0**。

**验证（2026-09-04 实测）**：平价 core-flows 三语均 **ALIGNED**（111/111 标题、84/84 代码块、250/250 表格行）；parity **0 findings**（api 路径/权限码/链接/内联代码集合与源一致）；全库分布 ALIGNED 50→**53**、PARTIAL 19→**16**（MAJOR_GAP 20、STUB 25 不变）；links / sensitive 均 exit 0。

**剩余疑问 / 未完成**：jp/ko 现有 Flow 1-12 的 mermaid 为英文（未译为日/韩），是既有的译文完整度问题；本轮为保内部一致，新增流程 mermaid 沿用英文，**未**回溯翻译既有 mermaid（属更大范围的译文质量决策，需单独评估）。三份译文在 `docs-manifest.yaml` 仍保持 `present-unverified` / `reviewed_at: null`；本轮为 Qoder 实质预审与修订，不替代独立 Codex final review 或人工验收。

**状态**：core-flows 组（1 源 + 3 译文）结构平价 100%、术语泄漏清零。P0 三篇中 architecture、core-flows 已收尾，剩 `api-contract`（MAJOR_GAP，大体量）。

### 4.7 P0 api-contract 组实质预审与修订（2026-09-04 完成）

- **源**：`docs/api-contract.md`（1381 行，本轮**未动源**，摘要不变；i18n 门禁 exit 0 佐证）。
- **译文**：`docs/api-contract.en.md`、`.jp.md`、`.kr.md`（修订前各 586/587 行，均止于 §14）。
- **检查范围**：章节结构、TOC 锚点、API 路径、权限码、字段/枚举、JSON/HTTP 代码块、表格、术语正字法、跨文档链接。

**发现的问题（结构性缺失，P0 系统真相文档）**：三份译文均止于 §14，完全缺失源文档后增的 **§15-18 共 4 个业务域契约**：§15 SRM MVP、§16 Workflow 跨服务、§17 Procurement MVP、§18 Asset MVP。合计缺 30 个标题（4 `##` + 26 `###`）、20 个代码块（40 围栏行）、156 个表格行，以及 101 个 API 路径 + 56 个权限码（与 §4.5.1 parity 数据吻合）。归因：§15-18 是源在译文完成后新增的四业务域契约，译文从未回填；三份缺口一致。

**已做修改**：

1. 三份译文补入 §15-18 全文 + TOC 四条锚点（en +757 行、ja +755、ko +756）。遵循约定：API 路径、权限码、字段名、枚举、金额、日期、HTTP 方法 verbatim；叙述/表格说明列/标题译为目标语言；JSON 示例的自然语言值（名称/标题/事由/备注/地址/联系人/响应消息）按各语言既有 §12 约定本地化（en 英文、ja 日文、ko 韩文），字段名与枚举不动。
2. 结构性核验（§15-end 分段计量，文件级脚本 `qoder-section-metrics.mjs`）：source §15-end = en §15-end = **{fences 40, headings 30, tables 156}** 完全一致；ja/ko parity 与 en 逐项相同（83/85、70/72、295/305）。
3. 跨文档链接：源 §16 指向 `workflow.md#28-跨服务内部契约`，但 `workflow.{en,jp,kr}.md` 均无 §2.8（workflow 组为 MAJOR_GAP），故三语 §16 链接降为**文件级** `workflow.{en,jp,kr}.md`（去锚点，避免断链致 links 门禁失败）；待 workflow 译文补全 §2.8 后应恢复锚点。已登记。
4. 术语正字法修正：`api-contract.jp.md` §8 既有 `身份` → `身分`（1 处，与 core-flows.jp 同类）；`api-contract.kr.md` 补译过程中自检出并修正 **7 处** CJK 泄漏（`基础`→기초、`時は`→시에는、`ヒット`→적중、`失效`→실효、`来源`×2→소스、`のみ`→만）+ 1 处谚文分写空格，复核 `grep '\p{Han}|\p{Hiragana}|\p{Katakana}'` ko = **0**、ja 简化字/谚文泄漏 = **0**。

**验证（2026-09-04 实测）**：parity api-contract 三语均 **ALIGNED**（83/85 标题、70/72 代码块、295/305 表格行）；全库分布 ALIGNED 53→**56**、MAJOR_GAP 20→**17**（PARTIAL 16、STUB 25 不变）；links / sensitive / i18n 均 exit 0。

**剩余疑问 / 未完成**：三语 §1-14 仍各有约 2 标题 / 1 代码块 / 10 表格行的既有小缺口（source §1-14 = {32,55,149} vs 译文 §1-14 = {30,53,139}），系译文早于源更新的历史缺口，**非本轮 §15-18 引入**（§15-18 分段计量已证完全一致）；不阻碍 ALIGNED，留待逐句语义复核。§16 workflow 锚点待 workflow 译文补全后恢复。三份译文 `docs-manifest.yaml` 仍 `present-unverified` / `reviewed_at: null`；本轮为 Qoder 实质预审与修订，不替代独立 Codex final review 或人工验收。

**状态**：api-contract 组（1 源 + 3 译文）§15-18 补齐、结构平价三语 ALIGNED、CJK 泄漏清零。**至此 P0 三篇（architecture、core-flows、api-contract）全部收尾**。下一优先级为 P1（backend-patterns、frontend-patterns、scheduling、workflow、crm、mq-reliability、guide-scaffold-development 等）与 4 份 `*-design` STUB（asset/crm/srm/procurement-design，各三语）。

### 4.8 P1 workflow 组实质预审与修订（2026-09-04 完成）

- **源**：`docs/workflow.md`（602 行，本轮**未动源**，摘要不变；i18n exit 0 佐证）。
- **译文**：`docs/workflow.en.md`、`.jp.md`、`.kr.md`（修订前各 384 行、32 个真实标题，三份结构一致）。
- **检查范围**：章节结构、跨服务内部契约、API 路径、权限码、字段/枚举、JSON/HTTP 代码块、表格、截图图片引用、术语正字法、跨文档链接锚点。

**发现的问题（结构性缺失）**：三份译文均缺 **§2.8 跨服务内部契约（含 2.8.1-2.8.4）** 与 **§8 管理端界面截图（四语言，含只读详情弹层）**，共 7 个真实标题（39-32）。§2.8 是 workflow 侧的幂等启动/任务资格校验/完成事件/模型版本查询内部契约（与 api-contract §16 相关但独立，businessKey 格式、表列、幂等表名均不同，**不可复用**）；§8 是阶段 B 采集的管理端截图小节（含 6 张 workflow 图片引用）。

**已做修改**：

1. 三份译文补入 §2.8（2.8.1-2.8.4）+ §8（含只读详情弹层）全文（en/ja/ko 各 +206〜207 行），插入源对应位置（§2.8 在 §2.7 与 §3 之间、§8 在 §7 之后 EOF）。API 路径/权限码/字段名/枚举/金额 verbatim；`X-Internal-Token` 占位符与 JSON 自然语言值（title/message）按各语言本地化；§8 图片路径 verbatim、alt 文本与页面名译为目标语言。
2. **恢复 api-contract §16 跨文档锚点**：workflow.{en,jp,kr}.md 补入 §2.8 后，将上一轮降为文件级的 api-contract §16 链接恢复为精确锚点（`#28-cross-service-internal-contract` / `#28-クロスサービス内部契約` / `#28-크로스-서비스-내부-계약`）；links 门禁 exit 0 证明三锚点均可解析。
3. 术语正字法：ko §8 自检出并修正 1 处 CJK 泄漏（`任何`→`어떤`）+ 1 处助词（`집계을`→`집계를`）；复核 ko `\p{Han}|\p{Hiragana}|\p{Katakana}` = **0**、ja 简化字/谚文 = **0**。

**验证（2026-09-04 实测）**：parity workflow 三语均 **ALIGNED**（39/39 标题、40/40 代码块、90/92 表格行）；links / sensitive / i18n 均 exit 0；全库分布 ALIGNED 56→**59**、MAJOR_GAP 17→**14**（PARTIAL 16、STUB 25 不变）。

**剩余疑问 / 未完成**：workflow 三语 §1-7 仍有约 2 表格行的既有小缺口（parity 90/92），非本轮引入，不阻碍 ALIGNED。**既有译文质量项**：workflow.jp/ko 的 §3、§3.1 等少数标题仍为英文（如 `## 3. Constraints & Pitfalls`、`### 3.1 MI DeleteReason`）而正文已译，属既有标题未本地化，本轮聚焦结构补齐未逐一改标题，登记为待办（不影响 parity 计数）。三份译文 `docs-manifest.yaml` 仍 `present-unverified` / `reviewed_at: null`；不替代独立 Codex final review 或人工验收。

**状态**：workflow 组（1 源 + 3 译文）§2.8+§8 补齐、三语 ALIGNED、api-contract §16 锚点已恢复。P1 已启动，剩 backend-patterns、frontend-patterns、scheduling、crm、mq-reliability、guide-scaffold-development。

### 4.9 guide-system-security-audit 组补译（2026-09-04，闭合阶段 B 自造缺口）

阶段 B 曾向源 `docs/guides/system-security-audit.md` 新增 §8（管理端 10 页四语言截图）与 §9（字典三态截图 + i18n PRODUCT_DEFECT 登记），但三份译文未回填 → parity MAJOR_GAP（8/11 标题、5/22 表格）。本轮补入 §8+§9+PRODUCT_DEFECT 子节+未覆盖 flow 说明（en/ja/ko 各 +55 行），插入 §7 与「参见」之间。图片路径 `../images/{locale}/system-*.png` verbatim（stage B 已提交，links exit 0 佐证存在）、alt 文本与页面名译为目标语言；PRODUCT_DEFECT 中的中文错误串（`字典类型编码不能为空`、`排序/並び順/정렬`）按「代码/字面量 verbatim」**保留中文**（其本身即被记录的硬编码缺陷，翻译会掩盖事实）。

验证：parity 三语均 **ALIGNED**（11/11 标题、22/22 表格）；ko CJK 扫描仅剩 3 处**有意的**中文缺陷引用（非泄漏）；links/sensitive/i18n exit 0；全库分布 ALIGNED 59→**62**、MAJOR_GAP 14→**11**。

### 4.10 mq-reliability 组补译（2026-09-04，闭合阶段 B 自造缺口）

阶段 B 第四批曾向源 `docs/mq-reliability.md` 新增 §10（消息详情弹层四语言截图 + retry/dead-letter/trace-diagnosis 未覆盖说明），三份译文未回填 → parity 缺 1 标题 / 3 表格行 / 5 链接（4 张 monitor-mq-message-detail 图 + observability.md）。本轮补入 §10 全文（en/ja/ko 各 +22 行），图片路径 verbatim、observability 链接沿用既有 en/jp/ko 约定指向 `observability.md`（该组译文尚不完整，与 guide See-also 一致）。另修正 ko §2 既有 1 处日文片假名泄漏 `クリア`→`초기화`。

验证：parity mq-reliability 三语 **ALIGNED**（38/38 标题、22/22 代码块、48/48 表格行）、**0 findings**（5 缺失链接已补齐）；links/sensitive/i18n exit 0；ko CJK 扫描 0。至此**阶段 B 自造的三处译文缺口（workflow §8、guide §8/§9、mq §10）全部闭合**。

### 4.11 observability 组忠实重译（2026-09-04）

parity 显示 observability 三语为 MAJOR_GAP（8/8 标题齐、但 4/8 代码块、en 14/29 表、ja/ko 6/29 表）。核查发现根因不同于前几组：**译文是源的缩写/概述版**，而非缺整节——en 把 §2 十行本地入口表压成 6 行、丢了 §2 两个 `dev down` 代码块、§5 SLO 九行表整段改为散文、§3 指标清单与 §4 生产清单也被概括；ja/ko 更简。

处置：按**忠实全量重译**（非最小补差），以源 130 行为准重写 en/ja/ko（各 +88〜89/-21〜31），恢复全部 4 个代码块（含 §7 第 3 条 `check config`）、§2 十行表、§5 九行 SLO 表、§3 两组自定义指标清单与 trace 关联段、§4 五项生产清单。代码块/指标名/命令 verbatim；叙述与表格译为目标语言。自检出并修正 ko 1 处日文假名泄漏（`통제下の`→`통제하의`）、ja 2 处中文词（`開箱`→初期状態、`公衆網`→インターネット）。

验证：parity observability 三语 **ALIGNED 且逐项满分**（8/8 标题、8/8 代码块、29/29 表格行）；ko CJK 扫描 0；links/sensitive/i18n exit 0；全库分布 ALIGNED 62→**65**、MAJOR_GAP 11→**8**。三份译文仍 `present-unverified`/`reviewed_at: null`（不替代独立复核）。

### 4.12 preset 三组补译（2026-09-04：preset-quick-selection / preset-maintenance / custom-preset-tutorial）

继续清理 MAJOR_GAP/STUB 中的 scaffold CLI 预设文档三组（均小体量、纯 CLI/散文，无运行栈依赖）：

- **preset-quick-selection**（已提交 `7f04a40`）：en 把 §生成后必须验证的 `test:preset` 代码块并进散文（缺 1 块）；ja 缺 2 个 `##`、ko 缺 3 个 `##`。en 补回代码块，ja/ko 忠实重译补齐 5 个 `##` + 代码块 → 三语 ALIGNED 5/5、4/4、7/7。
- **preset-maintenance**：en 本已 ALIGNED（6/6）；ja 缺 2 个 `##`（§修改正式预设+§黄金样例 并为单一标题、§失败回滚 无标题）、ko 缺 3 个 `##`。忠实重译 ja/ko 补齐 5 个 `##` + 受管理文件 7 项明细 → 三语 ALIGNED 6/6、2/2。
- **custom-preset-tutorial**：en MAJOR_GAP（4/6，§2/§3 合并、§4 代码块降为散文）、ja/ko **STUB**（仅 1 个标题、无 `##` 分节）。三语忠实重译补齐 §1-4 + 常见错误共 6 标题、4 代码块（yaml/powershell）→ 三语 ALIGNED 6/6、8/8。yaml 示例的 displayName/description 按各语言本地化（en 英、ja 日、ko 韩），命令/路径/模块 ID verbatim。

验证：parity 三组九对全部 **ALIGNED**；ko CJK 扫描（preset-maintenance/custom-preset-tutorial）= 0；links/sensitive/i18n exit 0。全库分布 ALIGNED 68→**73**、PARTIAL 16→**15**、MAJOR_GAP 5→**3**、STUB 25→**23**（本批 +5 ALIGNED，含 2 个 STUB 转 ALIGNED）。三份译文仍 `present-unverified`/`reviewed_at: null`。

### 4.13 guide-troubleshooting ja/ko 忠实重译（2026-09-04）

parity 显示 guide-troubleshooting ja/ko 为 STUB（10/10 标题齐、但 0/7 表格）。核查：ja/ko 把源每个 `##` 小节压成单句散文，丢了 §1 启动失败的 7 行「现象/检查/处理」表，标题也被缩写（如「起動」应为「起動失敗」）。忠实重译 ja/ko：恢复 §1 七行表 + §2-9 全部 bullets/编号列表 + 完整标题（en 本已 ALIGNED，未改）。验证：parity 三语 ALIGNED 10/10、7/7；ko CJK 0；links/i18n exit 0；全库分布 ALIGNED 73→**75**、STUB 23→**21**（MAJOR_GAP 3、PARTIAL 15）。

### 4.14 preset-upgrade-guide + guide-crm-flow 组补译（2026-09-04）

继续清理 PARTIAL：

- **preset-upgrade-guide**（提交 `1758683`）：三语把 §版本判断 并入 §推荐流程、§回滚+§升级完成标准 合为一节（4/6 标题），bullet 列表压成散文。忠实重译三语，恢复 5 个 `##` + 全部 bullet → ALIGNED 6/6。
- **guide-crm-flow**：三语均缺 §6 的图 2/3/4（新建线索必填校验、创建成功、无权限 403）三个 `####` 子节（10/13 标题）；ja/ko 另把 §2-7 压成单段（charRatio 0.57/0.63）。en 补入图 2/3/4（实测 9 张 en/ja/ko `crm-lead-*` 图均存在）；ja/ko 忠实重译（恢复 §2 五步编号、§3-7 明细、§6 四图）→ 三语 ALIGNED 13/13、2/2。图片路径 `../images/{locale}/crm-*.png` verbatim。

验证：parity 两组六对全 ALIGNED；ko CJK 0；links/i18n exit 0；全库分布 ALIGNED 75→**81**、PARTIAL 15→**9**、MAJOR_GAP 3、STUB 21。

### 4.15 guide-scheduling + guide-permissions 组补译（2026-09-04）

清理最后两个 PARTIAL 指南组（均为 stage B 截图小节 + 浓缩散文）：

- **guide-scheduling**（提交 `382d5f0`）：三语均缺 §1 操作截图的图 2/3/4（任务类型、创建接口失败、个人任务生命周期），ja/ko 另把 §2/§4/§6 列表压成单句。en 补图 2-4；ja/ko 忠实重译（恢复 §2 五步、§4 六步、§6 bullets + 全部 4 图）→ ALIGNED 12/12、2/2。12 张 scheduling 图实测存在。
- **guide-permissions**：三语均缺 §5 操作截图的图 2-7（员工越权 403、员工可见范围、供应商门户范围、404、列表接口失败、菜单加载失败降级页，共 6 图），ja/ko 另把 §1/§2/§4/§5/§6 列表压成散文（charRatio 0.48/0.50）。en 补图 2-7；ja/ko 忠实重译（恢复 §1 四层 bullet、§2 五步、§4 范围+映射 bullets、§5 六步、§6 七步 + 全部 7 图）→ ALIGNED 15/15、2/2。21 张 permissions 图实测存在。自检出并修正 ko 1 处 CJK 泄漏（`실패下の`→`실패 상황의`）。

验证：parity 两组六对全 ALIGNED；ko CJK 0；links/i18n exit 0；全库分布 ALIGNED 81→**87**、PARTIAL 9→**3**（仅剩 docker-deployment×3）、MAJOR_GAP 3、STUB 21。

### 4.16 docker-deployment 组：en 陈旧译文 reconcile（2026-09-04；ja/ko 待续）

parity 曾显示 docker-deployment 三语 PARTIAL（99/100 标题、78/78 代码块、108/132 表格）。深入核查发现**根因是译文整体陈旧**（早于四项重大源变更），非简单浓缩：

1. §2 容器网络拓扑缺 9 行（crm/srm/procurement/asset 的跨服务通信）+ 缺「生产网络边界」注；
2. §3.1 启动链写「5 层」且层级图缺 crm/srm/procurement/asset（源为 8 层）；
3. §3.2 健康检查缺 4 个业务服务行；
4. §5.6 标题「4 instances」（源「8 个」）+ 环境变量表缺 `OMNI_INTERNAL_API_TOKEN`/`MYSQL_URL` + 缺 `.env` 首启说明；
5. §16.1 端口表陈旧（`8100` 而非 `127.0.0.1:8100` 回环绑定，缺安全说明；Nacos 拆成 3 行 vs 源 1 行）；
6. §16.2 是**完全不同的旧节**（散文「Credentials and Exposure」）vs 源「本地初始化账号与密钥来源」5 行表；
7. §16.3 关键文件表写 `docker-compose.yml`（源已拆为 `compose.yaml`+`compose.infra.yaml`+`compose.apps.yaml`）+ 缺 Migrator 镜像行；
8. 附录构建产物缺 omni-crm 行。

本轮**忠实 reconcile en**（8 处修正，表格 108→132 与源逐项一致）→ en ALIGNED（99/100、78/78、132/132）；全库分布 ALIGNED 87→**88**、PARTIAL 3→**2**。

**ja/ko 已于 §4.17 完成**（同样 8 处陈旧，按 en diff 为模板 reconcile）。

### 4.17 docker-deployment 组：ja/ko 陈旧译文 reconcile（2026-09-04）

按 §4.16 en 的 8 处 diff 为模板，对 ja/ko 施加相同 reconcile（描述列译为目标语言，服务名/端口/路径/命令/环境变量名 verbatim）：

1. §2 拓扑补 9 行（crm→auth/namesrv/xxl-job、srm→mysql/auth、procurement→srm/workflow、asset→procurement/workflow）+ 补「生产网络边界」注；
2. §3.1「5 層/5계층」→「8 レイヤー/8개 계층」+ 层级图补 crm/srm/procurement/asset（Layer 3-7）；
3. §3.2 健康检查补 4 业务服务行（语言中性，复用 en）；
4. §5.6「4 インスタンス/4개 인스턴스」→「8 サービス/8개 서비스」+ 环境变量表补 `OMNI_INTERNAL_API_TOKEN`/`MYSQL_URL` + 补 `.env` 首启说明；
5. §16.1 端口表改回环绑定（`8100`→`127.0.0.1:8100` 等）+ 补安全说明；Nacos 合并为 `Console/API/gRPC` 1 行；
6. §16.2 旧散文节「認証情報と公開範囲/인증 정보와 공개 범위」→「本地初始化账号与密钥来源」5 行表；
7. §16.3 `docker-compose.yml`→`compose.yaml`+`compose.infra.yaml`+`compose.apps.yaml` 3 行 + 补 Migrator 镜像行 + start.bat 说明更新；
8. 附录构建产物补 omni-crm 行（语言中性）。

ko §16.2 首次替换因原文用「프런트엔드」（含 트）失配，其余 10 处已成功应用；读取实际内容后以修正锚点单独重试 §16.2 成功。

**验证**：parity → docker-deployment 三语全 **ALIGNED（en/ja/ko 各 99/100 标题、78/78 代码块、132/132 表格）**；ko CJK 泄漏扫描（Han/Hiragana/Katakana）**0 匹配**；门禁 `links` PASS、`sensitive` PASS、`i18n --allow-draft` PASS（源校验和新鲜——本轮仅改译文内容未动源）。i18n 非 draft 模式下 docker-deployment 三语各 2 条既有失败（`status != synchronized` + `reviewed_at: null`）属**全仓 114 文档统一的人工签核待办**（manifest 仅 1 处 synchronized），系外部审批边界，本 Agent 不伪造人工复核日期。

**全库分布**：ALIGNED 88→**90**、PARTIAL 2→**0**、MAJOR_GAP 3、STUB 21（共 114 组）。**PARTIAL 清零**。

### 4.18 计划/审查类文档改为仅中文：移除 4 组 en/ja/ko 译文（2026-09-04 用户决策）

**用户决策**：阶段性计划文档与交接文档只需中文，不做 en/ja/ko 翻译，并将清理已有译文。经确认范围为 4 组（asset/crm/srm/procurement-design 等设计真相文档仍保留多语言）：`scaffold-upgrade-plan`（计划，原 MAJOR_GAP）、`scaffold-upgrade-implementation-plan`（实施计划，原 STUB）、`full-functional-audit-2026-08-14`（阶段审查快照，原 STUB）、`full-functional-audit-remediation-2026-08-17`（阶段审查修复，原 STUB）。

**前置诚实说明**：本 Agent 在收到该决策前，已于本轮先行把 `scaffold-upgrade-plan` 忠实重译为 en/ja/ko（三语一度 ALIGNED 36/36、54/54）。收到「计划文档仅中文」决策后，立即 `git restore` 撤销这批**未提交**改动（含 checkpoint 旧稿），未进入任何提交，无副作用。

**执行动作**：（1）删除 12 个译文文件（4 组 ×3 语言），Chinese 源文档全部保留；（2）`docs-manifest.yaml` 将 4 组 `translations:` 三行块改为 `translations: {}`（附中文决策注释），保留 doc 条目与 source/source_sha256；（3）用官方生成器 `docs-review-queue.mjs --generate` 重生成 `docs/i18n-review-queue.md`（4 组译文列变 `<missing>`，并同步此前已对齐文档从差异表移除 + architecture 源摘要刷新）。

**验证**：links/sensitive PASS；i18n --allow-draft PASS；i18n strict 失败 **228→204**（-24 = 4 文档×3 语×2 消息）；队列对已删文件 **0 引用**；`translations: {}` 未导致 `checkTranslations` 崩溃（`Object.entries({})` 为空）。

**parity 分布**：114 组 → **102 组**；ALIGNED 90 不变、MAJOR_GAP 3→**0**、STUB 21→**12**。剩余 STUB 12 = asset/crm/srm/procurement-design ×3（设计真相文档，按用户决策保留多语言，为后续唯一可执行翻译缺口）。

### 4.19 asset-design 组：三语 STUB 空壳忠实全量重译（2026-09-04）

按用户决策，*-design 设计真相文档保留多语言。asset-design（源 683 行/16 节/52 标题/18 表/62 代码块，含 mermaid flowchart+erDiagram+stateDiagram+3×sequenceDiagram）三语原为 STUB 空壳（en 7-8/52 标题、0 表、0 代码）。

本轮从源忠实全量重译 en/ja/ko：
- 结构严格对齐源：52 标题、18 表、124 代码围栏行（62 块）；
- 约定：代码围栏（mermaid + text 目录树/API 流）在 en/ja/ko 一律保持英文（mermaid 标签英文符合既有约定）；散文/标题/表说明/列表译目标语言；字段名/枚举(IN_STOCK 等)/端点/权限码(asset:*)/端口/DECIMAL/BigDecimal verbatim；
- 泄漏修正：ja 3 处（每次→毎回、销毁→破棄、非法→不正）；ko 5 处（本部门→본 부서、jenis×2→종류、自用→자체 사용、회원 误译→폴백）。

验证：parity 三语全 ALIGNED（52/52、18/18、124/124；en 1.68/ja 1.13/ko 1.16）；ja 广义简中字形扫描 0、ko CJK+误词扫描 0；links/sensitive/i18n-draft PASS。

**全库分布**：ALIGNED 90→**93**、STUB 12→**9**（共 102 组）。剩余 STUB 9 = crm/srm/procurement-design ×3。

### 4.20 crm-design 组：三语 STUB 空壳忠实全量重译（2026-09-04）

crm-design（源 820 行/16 节/55 标题/约 13 表/62 代码块，含 mermaid flowchart+erDiagram+stateDiagram+sequenceDiagram、JSON 事件信封、PowerShell 验证命令）三语原为 STUB 空壳。本轮从源忠实全量重译 en/ja/ko，结构严格对齐（55 标题、32 表行、124 代码围栏）。

约定同 §4.19：代码围栏（mermaid/text/json/powershell）在 en/ja/ko 一律保持英文/verbatim；散文/标题/表说明/列表译目标语言；字段名/枚举/端点/权限码(crm:*)/端口 verbatim。

泄漏修正：ja 5 处（待办→未処理タスク、档案→プロファイル、完善→整備、挂载→マウント、来源→ソース）；ko 7 处（进入→진입、本部门×2→본 부서、同名→같은 이름의、完善→완비、kinds→종류、种类→종류）+ 2 处韩语分写。

验证：parity 三语全 ALIGNED（55/55、32/32、124/124；en 1.74/ja 1.17/ko 1.20）；ja 中文词扫描 0、ko CJK+误词扫描 0；links/sensitive/i18n-draft PASS。

**全库分布**：ALIGNED 93→**96**、STUB 9→**6**（共 102 组）。剩余 STUB 6 = srm-design ×3 + procurement-design ×3。

## 5. 阶段 D：汇总验证与分类

### 5.1 本会话已执行的验证（均为实跑，非沿用历史报告）

| 验证 | 命令 | 结果 |
| --- | --- | --- |
| 后端定向编译 | `mvnw.cmd -pl omni-auth,omni-procurement,omni-db-migrator test-compile` | BUILD SUCCESS 13.9s |
| fixture TTL | `javap -p -constants …E2eTokenFixture` | `TOKEN_TTL_SECONDS = 1200l` |
| Quotation 回归 | `mvnw.cmd -pl omni-procurement test -Dtest=QuotationSubmittedServiceImplTest` | 9/0/0/0，BUILD SUCCESS |
| 种子与迁移契约 | `mvnw.cmd -pl omni-db-migrator test` | 22/0/0/0，BUILD SUCCESS |
| 前端定向静态检查 | `npx eslint …srm.flows.spec.ts --max-warnings 0`；`npx tsc --noEmit --strict …` | 均 exit 0 |
| 用例发现 | `playwright test srm.flows --list` | Total: 4 tests |
| 真实 E2E | `playwright test srm.flows --config playwright.docs.config.ts` | **4 passed / 0 skipped（1.4m）** |
| strict 截图门禁 | `npm run docs:screenshots:check` | exit 1，**8 个模块覆盖失败**（预期值，无新增错误） |
| 链接门禁 | `npm run docs:links:check` | exit 0 |
| 敏感内容扫描 | `docs-quality.mjs --scope=sensitive --allow-draft` | exit 0 |
| 暂存区 secret scan | `scripts/.work/qoder-secret-scan.mjs`（7 模式 / 1813 新增行） | CLEAN |
| Git 交付 | `qoder-push-guard.ps1` | FAST_FORWARD_PROVEN=yes；PUSH_EXIT=0；DELIVERY=PUSHED |

常规构建验证**不等于** G8；本会话**未**执行 G8 once-only 综合矩阵，未宣称全工程终验完成。

### 5.2 状态分类

**DONE（有本会话实测证据）**

- 阶段 A 全部 Step 0-6：C3/C4/C5 前置修正、定向编译与验证、四语言真实 E2E（4 passed/0 skipped）、12/12 图片生成与质检、28 行本批数据事务软删与 0 残留、凭证销毁、manifest/coverage/`docs/srm.md`/docs-manifest 登记、两份旧交接与主交接结果记录更正、精确提交 `653afe3` 与推送（双远端 fast-forward）。
- 阶段 B 的范围核定、可构造性实测取证、逐项分类，以及 1 项新 DATA_DEFECT 的完整归因。
- 阶段 C 的队列重生成与 srm 组（1 源 + 3 译文）实质预审与修订。

**IMPLEMENTED_NOT_EXECUTED**

- 无。本批所有代码/数据/文档变更均已执行并通过对应定向验证。

**BLOCKED（含所需输入）**

| 项 | 阻塞原因 | 所需输入 |
| --- | --- | --- |
| `procurement-default-config` 种子断言在运行库失败（实际 1 行 / 期望 14） | 13 行 bootstrap 种子品类于 2026-09-03 12:20:12 被直接批量 SQL 软删（`update_by=NULL`），非本批所为 | 授权还原 ids 1-13（事务 + `ROW_COUNT()=13`），或由运维在正式迁移重放种子 |
| procurement `material`；asset 全部 10 gaps | 依赖上一项；且 PO/GR/`ast_*` 全为 0 行 | 先修复种子目录，再授权跑通 采购→收货→建卡 全链 |
| scaffold-development 2 gaps；operations 2 gaps | 非页面流程（CLI / 可观测基础设施），gap 名即 `*-not-yet-delivered`；禁止伪造 UI 图，且不得自行 `exempt` | 决定登记形态与证据标准（真实命令输出/仪表盘证据 vs 授权 `exempt`） |
| workflow `countersign` | 需多审批人身份；现有受信任测试身份仅 admin/supplier1，禁止临时越权 | 确认新增测试身份（登记待确认，不自行创建） |
| G1 / G7 / WP-10 / G8 | 外部运维 adoption 决策；独立 Codex final review 与 114 篇人工复核；前置未满足 | 外部输入，本会话不代办、不轮询、不提前执行 |
| 历史残留（E2ESQ RFQ-1-15/16/17 及对应 `srm_quotation` 3 行）；TEMP 中 12 个更早会话凭证文件 | 不属本批归属 | 单独授权后清理 |

**NOT_STARTED**

- 阶段 B 的实际 gap 闭环：39 项中 0 项关闭（已完成分诊与取证）。
- 阶段 C 其余 111 项译文预审（P0 3 篇、P1 其余 7 篇、P2 27 篇，各 ×3 语言），以及 srm 组截图章节的三语补译。
- backend 全量 `clean install`、frontend 全仓 `npm run build` / `npm run lint`、G8。

### 5.3 收口原因（如实说明，不谎称全部完成或全部外部阻塞）

阶段 A 已按完成定义五项全部满足并推送。阶段 B/C 的剩余项**大多技术上可执行、并非外部阻塞**，
但每一项闭环都需一轮与阶段 A 同量级的工作（四语言文案提取 → 重签 Token → 真实 E2E → 图片质检 → 清理 → manifest/coverage/指南/摘要 → strict → 提交推送）。
本会话在**上下文/运行预算**这一平台限制下收口，而非因为剩余项全部被外部阻塞。
下一接手方可从 5.2 的 EXECUTABLE 低成本项（workflow `tracking`）直接续做，无需重开全仓审计。

### 5.4 本会话（2026-09-04 接管）交付小结

用户以 `qoder-continuous-execution-prompt-2026-09-03.md` 再次接管，最小核对 Git/运行现场后连续推进阶段 C，共 23 个小提交（均 fast-forward 推送、三端一致）：

| 提交 | 内容 |
| --- | --- |
| `dd8ce78` | P0 architecture 收尾（三语 ALIGNED 138/138） |
| `e8eb034` | checkpoint：architecture parity 复验 + G7 完整度矩阵 |
| `22cf8ee` | P0 core-flows 补译 Flow 13-18（三语 ALIGNED 111/111）+ CJK 修正 |
| `afadfe2` | P0 api-contract §15-18 补译 en |
| `f372084` | P0 api-contract §15-18 补译 ja/ko + checkpoint §4.7 |
| `afa591e` | checkpoint：§5.4 会话小结 + §8.3 恢复指针 |
| `33aaf6e` | P1 workflow §2.8+§8 补译三语 ALIGNED + 恢复 api-contract §16 锚点 |
| `ea0904a` | guide-system-security-audit §8/§9 截图章节补译三语 ALIGNED |
| `b219c7d` | mq-reliability §10 补译三语 ALIGNED（闭合阶段 B 自造缺口） |
| `903bb07` | checkpoint：§5.4 刷新（workflow/guide/mq 提交登记） |
| `6be975a` | observability 忠实重译 en/ja/ko（ALIGNED） |
| `7f04a40` | preset-quick-selection en/ja/ko 对齐 |
| `cafa81d` | preset-maintenance + custom-preset-tutorial en/ja/ko 对齐 |
| `1db646b` | guide-troubleshooting ja/ko 忠实重译 |
| `1758683` | preset-upgrade-guide en/ja/ko 对齐 |
| `b1aec40` | guide-crm-flow en/ja/ko 对齐（恢复图 2-4） |
| `382d5f0` | guide-scheduling en/ja/ko 对齐 |
| `c3d50a3` | guide-permissions en/ja/ko 对齐（恢复图 2-7） |
| `6238ec7` | docker-deployment en 陈旧译文 reconcile（§4.16，ALIGNED 132/132） |
| `61744b6` | docker-deployment ja/ko reconcile（§4.17，三语 ALIGNED，PARTIAL 清零） |
| `3ffdda7` | 计划/审查类 4 组文档改为仅中文：移除 12 译文 + manifest `translations:{}` + 队列重生成（§4.18，MAJOR_GAP 清零、STUB 21→12） |
| `381ec40` | asset-design 三语 STUB 空壳忠实全量重译（§4.19，ALIGNED 52/52、18/18、124/124） |
| 本轮 | crm-design 三语 STUB 空壳忠实全量重译（§4.20，ALIGNED 55/55、32/32、124/124） |

**里程碑：P0 三篇系统真相文档（architecture、core-flows、api-contract）四语言全部 ALIGNED；P1 workflow 组补齐；阶段 B 自造的三处译文缺口全部闭合；observability/preset-*/guide-*/docker-deployment 等浓缩或陈旧译文已忠实重译或 reconcile**。全库完整度分布由接管时 ALIGNED 48 / PARTIAL 21 / MAJOR_GAP 20 / STUB 25 变为 **ALIGNED 96 / PARTIAL 0 / MAJOR_GAP 0 / STUB 6（共 102 组）**（本会话 +48 ALIGNED；PARTIAL 与 MAJOR_GAP 清零；9 项 STUB 因计划/审查类文档改为仅中文移出翻译范围；asset-design、crm-design 三语已忠实重译为 ALIGNED）。23 个提交均 fast-forward 推送、三端一致。

运行现场：`omni-wp09-docs running(15)` 全程未重建；本会话均为**文档只读 + 译文修订**，无 E2E、无 Token 签发、无数据写入、无凭证产生。免费额度剩余：**UNKNOWN**（Agent 无法读取客户端用量面板，请在客户端核对）。

**下一可执行项（阶段 C，非外部阻塞）**：结构性缺口仅剩 **STUB 6**（srm/procurement-design ×3 设计真相文档，译文近乎空壳，各需独立整轮甚至多轮；asset-design、crm-design 已完成）。计划/审查/交接类文档已按用户决策定为仅中文，不再列入翻译范围。manifest 人工签核（`status: synchronized` + `reviewed_at`）系全仓统一待办，属外部审批边界，本 Agent 不代办、不伪造复核日期。

**外部阻塞项不变**（见 §5.2 BLOCKED 表 + §6）：DATA_DEFECT 种子还原（需授权）、workflow countersign（需新增身份）、scaffold-development/operations（非页面流程，需决定登记形态）、G1/G7/G8/WP-10。

## 6. Gate 与外部阻塞（保持真实状态，不因 A-D 推进而改动）

| 项 | 状态 |
| --- | --- |
| G1 adoption baseline | BLOCKED / PENDING（外部运维确认，本批不代办） |
| G2 | CLOSED（继承，不重复验证） |
| G3-G6 | DONE（继承） |
| G7 | BLOCKED / PENDING（8 模块覆盖失败 + 翻译复核未收口；不得用 Qoder 自审冒充独立 Codex final review） |
| WP-10 历史临时文件清理 | LOCKED（本批不执行） |
| G8 once-only 综合矩阵 | LOCKED（本批不提前执行） |

strict 预期：关闭 `supplier-quotation` 一个 gap 后 SRM 仍为 `partial`（尚有 `admission-lifecycle`、`stable-mobile-flow`、
`detail-and-action-states`），`npm run docs:screenshots:check` 仍应是 **8 个已知模块覆盖失败、exit 1**。
不得为降数修改检查器、造假 `covered` 或吞掉失败输出（主交接 C1，本轮继续遵守）。

## 7. 用量与运行标识

- 模型：客户端选定的 Qwen3.8-Max，未切换 Auto/其他计费模型，未购买资源包，未启用多 Agent 并发写入。
- 免费额度剩余：**UNKNOWN** —— Agent 无法读取客户端用量面板，不编造计费数字；请在客户端用量面板核对。
- 后台运行进程：无遗留。曾使用 terminal 1 运行 `mvnw test-compile`（已结束，BUILD SUCCESS）与 `run-srm-e2e.ps1`（已结束，PLAYWRIGHT_EXIT=0）。
- 共享 Docker/数据库的写入型 E2E 串行执行（`workers: 1`），未并发；未重建全栈、未清空卷、未关闭健康依赖服务。
- 本轮临时辅助文件（均不提交）：`scripts/.work/` 下 `issue-e2e-tokens.ps1`/`.sh`（已修）、`run-srm-e2e.ps1`、`destroy-e2e-credentials.ps1`、`qoder-push-guard.ps1`、`qoder-secret-scan.mjs`、`qoder-cleanup-batch.sql`、`qoder-check-*.sql`、`qoder-mvn-*.log`、`qoder-srm-e2e.log`、`qoder-cleanup-result.txt`、`qoder-stage-pathspec.txt`；`omni-frontend/scripts/.work/qoder-gap-survey.mjs`。
- 工具异常记录：两次 PowerShell 命令返回 ExitCode=1 但实际成功（Maven 测试与 git push），原因是 PowerShell 将原生命令的 stderr 进度输出当作 NativeCommandError；已以命令自身的结构化输出（`BUILD SUCCESS`、`PUSH_EXIT=0`、`DELIVERY=PUSHED`）为准，未误判为失败。一次 `docker exec` 在 `has_risk` 沙箱下被拒（`docker.exe 拒绝访问`），改用与其余只读查询相同的执行路径后成功，未绕过任何用户级安全确认。无 TOOL_TIMEOUT。

## 8. 暂停点与恢复入口

### 8.1 本次暂停状态（用户主动要求下班暂停，非阻塞、非失败）

暂停时现场**干净且已全部交付**：

- LOCAL = GITEE = GITHUB = `6c03b3254f1ce760fc779ce8b3623530eeef96a1`（本文件所在的收尾提交推送后会前进一格）。
- 暂存区空；工作区只剩 §2.3 排除项（无本任务未提交的跟踪变更）。
- 本会话共签发 5 份凭证（160646 / 172150 / 173240 / 180943 / 182228 / 184357），**全部已销毁**；
  `%TEMP%\omni-e2e-tokens\` 仅剩 12 个更早会话文件（不属本任务，未触碰）。
- 两个临时探测用例（`zz-probe.spec.ts`、`zz-probe-dict.spec.ts`、`zz-probe-details.spec.ts`）**均已删除**，从未进入提交。
- Docker `omni-wp09-docs` 仍 15 容器 running，**未重建、未停服、未清卷**；无遗留后台进程。
- 本批测试数据零残留：阶段 A 的 28 行软删 + 字典批 `E2EDICT-%` 硬删后实测 0，`sys_dict_type` 回到基线 17。

### 8.2 已固化的探测成果（下次不必重跑）

最后一轮只读探测（4 passed / 27.1s）已取得三个页面的**行操作按钮四语言真实文案**，可直接用于编写正式用例：

| 页面 | 行数 | zh-CN | en-US | ja-JP | ko-KR |
| --- | --- | --- | --- | --- | --- |
| `/admin/workflow/instance` | 10 | 流转进度 / 审批记录 | Process Progress / Approval Records | プロセス進捗 / 承認履歴 | 프로세스 진행 상태 / 승인 기록 |
| `/admin/workflow/model` | 8 | 设计 / 校验 / 发布 / 版本 / 删除 | Design / Validate / Publish / Version / Delete | 設計 / 検証 / 公開 / バージョン / 削除 | 설계 / 검증 / 게시 / 버전 / 삭제 |
| `/admin/base/mqmessage` | 10 | 查看详情 | Detail | Detail | Detail |

已验证的弹层行为：`/admin/base/mqmessage` 在 en/ja/ko 下点 `Detail` 可打开弹层，标题 `Detail`、按钮 `[Close]`（**只读**）。

两个需注意的探测经验：

1. 中文按钮为复合词（如「查看详情」「流转进度」），用 `^…$` 精确匹配会漏；下次应用 **includes + 排除写操作关键词**（删除/编辑/发布/终止/重发/跳过）的双重过滤。
2. 上述探测**未对中文 mqmessage 打开弹层**（正则未命中复合词），因此缺 zh 的弹层标题/字段清单；已知按钮文案为「查看详情」，可直接使用。
3. 新发现一个译文完整度实例：mqmessage 行操作在 **ja-JP/ko-KR 下也渲染为英文 `Detail`**（与 §3.5 的 i18n 观察同类）。

### 8.3 下一条具体操作（按优先序）

1. **最低成本、只读、无需清理**：workflow 与 messaging-monitoring 的 `detail-and-action-states`。
   直接用 §8.2 的真实文案新建一个只读弹层用例：
   - `/admin/workflow/instance` 首行点「流转进度/Process Progress/プロセス進捗/프로세스 진행 상태」与「审批记录/Approval Records/承認履歴/승인 기록」（各 4 语言）；
   - `/admin/workflow/model` 首行点「版本/Version/バージョン/버전」（只读历史；**避开** 设计/校验/发布/删除 中的写操作，尤其发布与删除）；
   - `/admin/base/mqmessage` 首行点「查看详情/Detail」，弹层标题与 `Close` 按钮已实测。
   随后：manifest 登记（`step: detail`）、coverage 移除对应 `detail-and-action-states`、指南补节 + 刷新摘要、strict 重跑、提交推送。
2. **阶段 C**：P0 三篇（architecture、core-flows、api-contract）**已全部收尾**（见 §4.5-4.7、§5.4）。下一组为 P1（`workflow` MAJOR_GAP 优先，其 §2.8 补全后可恢复 api-contract §16 锚点；及 backend-patterns/frontend-patterns/scheduling/crm/mq-reliability/guide-scaffold-development）与 4 份 `*-design` STUB，按同一源成组推进。
3. **需授权才能推进**（不自行执行）：修复 §3.3 DATA_DEFECT（还原 `proc_material_category` ids 1-13，事务 + `ROW_COUNT()=13`）→ 解锁 procurement `material` 与 asset 全部 10 gaps；修复 §3.8 登记的 i18n PRODUCT_DEFECT（后端校验消息国际化 + 补齐 ja/ko 译文）。
4. **需先确认身份**：workflow `countersign` 需多审批人；现有受信任测试身份仅 admin/supplier1，**登记待确认，不自行创建、不临时越权**。
5. **scaffold-development / operations**（各 2 gaps）：非页面流程（CLI / 可观测基础设施），禁止伪造 UI 图；需先决定登记形态与证据标准（真实命令输出/仪表盘证据 vs 授权 `exempt`）。

恢复步骤：读本文件 → `git status --short` + `git log -1 --oneline` + `git ls-remote origin refs/heads/codex/scaffold-upgrade` + `docker compose ls` 做最小增量核对 →
按 8.3 第 1 项继续。**禁止重开全仓审计**，禁止丢弃已有成果。

推送注意：`origin` 配了 gitee + github **两个 push URL**，github 走本地代理 `127.0.0.1:7897`；
若再现 `PUSH_EXIT=128`，先分别 `git ls-remote` 两个 URL 定位哪端落后、`Test-NetConnection 127.0.0.1 -Port 7897` 确认代理，
再在证明可 fast-forward 后重试；**不改 git config、不删 push URL、不 force push**（参见 §3.9 实例）。

防空转：同一问题连续三次尝试无新证据或实质进展即标 BLOCKED，停止盲试，转向不依赖它的独立工作。
