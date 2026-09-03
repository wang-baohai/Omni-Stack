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

### Step 6 精确提交与推送 — IN_PROGRESS

已完成：有界远端 fast-forward 核验 —— `git ls-remote origin refs/heads/codex/scaffold-upgrade` = `ace8bf737695e7a63e3c576882f29cbf200782ff`，
与本地 HEAD 完全相同，`git merge-base --is-ancestor <remote> HEAD` exit 0 → **无分叉，可 fast-forward**。

待执行：用 `git add --pathspec-from-file=scripts/.work/qoder-stage-pathspec.txt`（**30 条显式路径，未用 `git add .`**）stage，
再做 `git diff --cached --name-only` / `--check` / 仅暂存内容 secret scan，确认未混入 §2.3 排除项后 commit 并 push。

提交后远端 SHA 写入执行响应，**不 amend 回填、不循环创建文档提交**。


## 2. 本轮变更文件（阶段 A）

已修改/新增，均属本批范围：

- `omni-frontend/e2e-docs/flows/srm.flows.spec.ts`（untracked，重写）
- `database/changelog/auth/0005-admin-procurement-approval-candidate.yaml`（新增）
- `database/changelog/auth/db.changelog-auth.yaml`（+2 行 include）
- `database/seed/manifest.yaml`（+25 行断言）
- `scaffold/catalog/modules.yaml`（procurement provisioningSeedIds +1）
- `scripts/sql/seed/auth.sql`（**还原到 HEAD**，净变更 0）
- `omni-backend/omni-auth/src/test/java/com/omni/auth/e2e/E2eTokenFixture.java`（TTL 600→1200，沿用既有未提交修改）
- `omni-backend/omni-procurement/.../QuotationSubmittedConsumer.java`、`QuotationSubmittedServiceImpl.java`、`QuotationSubmittedServiceImplTest.java`（沿用既有未提交修改，本轮完成回归验证）

不提交的临时文件（§2.3 排除项 + 本轮新增辅助）：

- `scripts/.work/issue-e2e-tokens.ps1`、`issue-e2e-tokens.sh`（本轮修正）
- `scripts/.work/qoder-check-changelog.sql`、`qoder-check-assertion.sql`（本轮只读核查）
- `scripts/.work/qoder-mvn-*.log`（本轮构建日志）
- 其余既有排除项原样保留：根目录 `*.patch`、`sms.png`、`agent-progress.md`、`baseline-candidate.yaml`、`.workbuddy/`、
  `login-state-check.png`、`omni-frontend/console-btn-home.png`、`omni-frontend/.artifacts/`、`omni-frontend/scripts/`、
  `scripts/.work/` 既有脚本、`docs/scaffold-upgrade-task-handoff-2026-08-27.md`

## 3. 阶段 B/C/D 状态

| 阶段 | 状态 | 说明 |
| --- | --- | --- |
| B 截图 gaps 队列 | NOT_STARTED | 待 A 收口后以实际 `screenshot-coverage.yaml` 的 required_flows/gaps 为范围推进 |
| C 四语言文档预审 | NOT_STARTED | 待与截图相关的源文档更新后，按实际 review queue P0→P1→P2 |
| D 汇总验证与交付 | NOT_STARTED | 常规构建验证，不等于 G8 |

## 4. Gate 与外部阻塞（保持真实状态，不因 A-D 推进而改动）

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

## 5. 用量与运行标识

- 模型：客户端选定的 Qwen3.8-Max，未切换 Auto/其他计费模型，未购买资源包，未启用多 Agent 并发写入。
- 免费额度剩余：**UNKNOWN** —— Agent 无法读取客户端用量面板，不编造计费数字；请在客户端用量面板核对。
- 后台运行进程：terminal 1（`mvnw test-compile`，已结束，BUILD SUCCESS）。当前无遗留后台进程。
- 共享 Docker/数据库的写入型 E2E 串行执行，未并发。

## 6. 恢复入口

上下文压缩或进程重启后：读本文件 → `git status --short` + `git log -1 --oneline` + `docker compose ls` 做最小增量核对 →
按「下一条具体操作」继续。**禁止重开全仓审计**，禁止丢弃已有成果。

防空转：同一问题连续三次尝试无新证据或实质进展即标 BLOCKED，停止盲试，转向不依赖它的独立工作。
