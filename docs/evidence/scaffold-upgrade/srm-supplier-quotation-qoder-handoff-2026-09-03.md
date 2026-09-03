# SRM supplier-quotation：复核更正与 Qoder 执行交接

日期：2026-09-03

仓库：`C:\WorkSpace\QODER\Omni-Stack`

分支：`codex/scaffold-upgrade`

复核基线：`ace8bf7`（`docs: prepare final gate unlock decisions`）

## 1. 阅读入口、证据时效与本批边界

本文件是下一批 **SRM supplier-quotation 单一 gap 收口**的主入口，修正旧交接中的错误执行前提。本文中的仓库文件路径均相对上述仓库根目录。

背景文档只按需查阅相关小节，不重新调查已经冻结的结论：

- `docs/evidence/scaffold-upgrade/handover-status-2026-09-03.md`
- `docs/evidence/scaffold-upgrade/srm-quotation-recovery-2026-09-03.md`
- `docs/evidence/scaffold-upgrade/final-gate-unlock-preflight-2026-09-02.md`

本文与上述文档冲突时，以本文的更正和可复现代码/测试证据为准；实际工作区发生变化时，以新的定向核验结果为准，不机械套用旧快照。仓库规则仍生效；下一批由用户发送的执行提示词明确限定任务范围及验证例外。

证据分为三层，禁止混报：

1. **写入本文件前再次确认**：当前分支、最后一条提交、`git status --short`；关键脚本、测试防护条件和 strict 判定代码的定向读取。
2. **同日上一轮复核已实际取得**：远端 SHA、Docker 选定状态、E2ESQ 数据计数、Token 编译常量、种子摘要、strict 检查结果。写本文时未重复执行这些检查，属于同日快照而非持续监控。
3. **历史产物/冻结交接**：单测报告、G2/G3-G6 等历史验收。不能视作本轮对当前源码的重新验证。

本次文档整理没有签发 Token、运行写入型 E2E、修改业务代码/数据库、清理文件、stage、commit 或 push。

下一批唯一目标：修正本链路必需的测试及可部署性前置缺陷，完成真实四语言报价流程、12 张截图、定向数据清理、文档登记及安全提交推送。**不是完成整个脚手架升级，也不解锁 G7/WP-10/G8。**

不在本批范围：其他模块截图、SRM 其他 gaps、114 篇翻译批量复核、adoption baseline 运维决策、远端隔离 CI 补验、历史临时文件清理、重构或新业务功能。

## 2. 当前真实现场

### 2.1 Git 与运行环境

| 项目 | 已取得证据 | 限定说明 |
| --- | --- | --- |
| 本地分支/HEAD | `codex/scaffold-upgrade` / `ace8bf7` | 写本文前重新确认，未变化 |
| 远端分支 | `ace8bf737695e7a63e3c576882f29cbf200782ff` | 上一轮 `git ls-remote` 成功结果；当时 local=remote，推送前须再定向核验 |
| Docker | Compose 项目 `omni-wp09-docs`，15 个容器 running | 14 个 healthy；frontend 没有 healthcheck，不能写成 15 个 healthy |
| 前端/数据库入口 | 前端 `127.0.0.1:3000`；宿主 MySQL 端口 `13306` | 同日运行快照；不能误用文档默认 MySQL 3306 |
| procurement | healthy，启动时间 `2026-09-03 04:18:34 UTC`，无 bind mount | 镜像 ID `sha256:c002da6f12d65fe27da12339c9959b2c584ce2ba4920ebba578f15df5759ea88`；镜像与当前源码逐字节一致性未证明 |
| auth/frontend | 均 running，无 bind mount；auth healthy | 本地源码更新不会自动进入容器 |

不要因为本地 `target` 老旧就认定容器没有修复，也不要因为容器近期启动就认定已运行最新源码。仅在相关运行验证失败且有证据指向制品不一致时，处理受影响的单服务；不重建全栈、不清空卷、不关闭健康依赖服务。

### 2.2 当前任务相关未提交文件

| 路径 | Git 状态 | 修改目的与实际成熟度 |
| --- | --- | --- |
| `omni-backend/omni-auth/src/test/java/com/omni/auth/e2e/E2eTokenFixture.java` | tracked modified | TTL 600→1200 秒；源码已改，本地已编译 class 仍是 600，不能直接称已生效 |
| `omni-backend/omni-procurement/src/main/java/com/omni/procurement/consumer/QuotationSubmittedConsumer.java` | tracked modified | 增加消费及异常诊断日志，保留异常重抛与上下文释放；不是已证明的第二个行为缺陷修复 |
| `omni-backend/omni-procurement/src/main/java/com/omni/procurement/service/impl/QuotationSubmittedServiceImpl.java` | tracked modified | Inbox payload 从字符串比较改为 JSON 树语义比较，处理 MySQL JSON 归一化；需当前源码定向验证 |
| `omni-backend/omni-procurement/src/test/java/com/omni/procurement/service/impl/QuotationSubmittedServiceImplTest.java` | tracked modified | 增加 JSON 归一化回归用例；现有报告 9/0/0/0 是历史产物 |
| `scripts/sql/seed/auth.sql` | tracked modified | 为 admin 增加采购经理角色及候选作用域；数据修复方向成立，摘要与历史迁移兼容尚未收口 |
| `omni-frontend/e2e-docs/flows/srm.flows.spec.ts` | untracked | 已实现请购→审批→RFQ send→供应商报价及截图，但还存在模型/任务兜底、写入开关与清理缺陷 |
| `docs/images/{zh-CN,en-US,ja-JP}/srm-portal-quotation-{invitations,form}.png` | 6 个 untracked 文件 | 文件存在；不代表 submitted 已验证，也不等于四语言全部通过 |
| 上述两份 2026-09-03 旧交接文档 | untracked | 含本文指出的过时结论；下一批需最小更正并保留历史证据语境 |
| 本文件 | 本轮新建，untracked | 下一批执行入口；不是实施完成报告 |

下列必需配套在当前快照尚未修改：`database/seed/manifest.yaml`、对应 forward-only changelog/断言、`omni-frontend/e2e-docs/screenshot-manifest.yaml`、`omni-frontend/e2e-docs/screenshot-coverage.yaml`、`docs/srm.md`、`docs/docs-manifest.yaml`。

### 2.3 必须原样保留的非本批文件

不提交、不删除、不回滚：根目录 `*.patch`、`sms.png`、`agent-progress.md`、`baseline-candidate.yaml`、`.workbuddy/`、`login-state-check.png`、`omni-frontend/console-btn-home.png`、`omni-frontend/.artifacts/`、其他 debug PNG、`omni-frontend/scripts/`、`scripts/.work/`，以及 `docs/scaffold-upgrade-task-handoff-2026-08-27.md`。

`scripts/.work/` 上一轮实际有 9 个文件，并非旧文档所说的 5 个。允许对本批确有需要的现有临时辅助脚本作最小安全修正，但不纳入提交，不删除旧脚本；永久维护入口不得只依赖未提交脚本。WP-10 仍 LOCKED，不能借本批执行历史文件清理。

## 3. 必须更正的七项结论

### C1. strict 不会因关闭一个 gap 从 8 降到 7

`tools/omni-cli/scripts/docs-quality.mjs` 对每个模块执行 `status` 判定：只有 `covered` 或 `exempt` 不报覆盖错误；它不按 `gaps` 数量计分。

已执行 `npm run docs:screenshots:check`，得到 8 个模块覆盖失败：system-management、messaging-monitoring、workflow、srm、procurement、asset 为 partial，scaffold-development、operations 为 missing。

本批完成后 SRM 仍有 `admission-lifecycle`、`stable-mobile-flow`、`detail-and-action-states`，因此必须保持 `partial`。正确预期是 **strict 仍为 8 个已知模块覆盖失败，预期 exit code 1，且不新增资源/登记错误**。不得改检查器、造假 `covered` 或吞掉失败输出来制造 8→7。关闭一个 gap 与关闭整个 G7 是两个不同结论。

### C2. 当前是 6/12 张，而非 8/12 张

已存在：zh-CN、en-US、ja-JP 各 invitations/form 两张，共 6 张。

缺少：ko-KR 的 invitations/form 两张，以及四语言的 submitted 四张，共 6 张。目标是 **4 个语言测试，每个 3 个截图状态，共 12 张 PNG**，不是 12 个独立测试。

最终正式图片必须与实际成功运行、对应语言和真实页面状态匹配；不能仅按文件存在登记。不得复用其他语言图片、生成占位图或模拟报价成功响应。

### C3. Token 源码 1200，不代表实际签发 1200

同日已用 `javap -p -constants` 核对本地 fixture class：`TOKEN_TTL_SECONDS=600l`。源码为 1200；class 时间为 08-31，源码修改为 09-03。

`scripts/.work/issue-e2e-tokens.sh` 只执行 `exec:java`，没有先 test-compile，且直接调用 Maven 缓存目录中的 launcher，不符合仓库 Wrapper 约定。不能照旧“先签发再调试”。

先用 JDK 25 + Maven Wrapper 定向编译 fixture，确认实际常量/签发元数据 TTL=1200，再签发和运行。认证仅用 `E2eTokenFixture` 的 admin 与 supplier1 身份。fixture 当前还会生成 employee 凭证，不能将这个额外身份用于本批业务验证。

允许 fixture 按既有实现读取签名密钥；禁止另行查询 Redis 中的登录 Token、绕过 CAPTCHA 或输出 Token。禁止读取 `.env.before-rebuild-*`。如需加载当前 `.env`，仅限现有受控签发流程，不打印内容或环境变量。

Token 输出目录为系统 TEMP 下 `omni-e2e-tokens`，`latest.txt` 是 JSON 文件路径指针，不是 Token JSON 本体。解析需处理 BOM/CRLF、Windows/Git Bash 路径。使用 `export` 单独逐行设置环境变量；不能把赋值用 `&&` 串接，不能使用 `set -x`。

fixture 不会自动删除所写的凭证文件；调用方应在本批结束/异常时清除环境变量，并仅删除本批新签发且确认归属的 TEMP 凭证文件。只有当 `latest.txt` 仍指向该文件时才移除该指针；不碰其他会话凭证，不递归清空 TEMP。这是敏感凭证生命周期处理，不是 WP-10 历史脚本清理。

### C4. 测试保护仍不完整

`srm.flows.spec.ts` 现有问题：

- workflow-options 使用 `modelKey==='procurement-approval'` 后仍有 `?? options[0]`；必须删除兜底，未找到目标模型应明确失败。
- 审批任务查找后仍有 `?? records[0]`；必须绑定本轮请购/流程的唯一关联，不能批准其他任务。后端支持 title 查询，不重新调查这个已存在的能力。
- `E2E_MUTATIONS` 只出现在注释，运行时没有强制防护；缺少 `E2E_MUTATIONS=true` 时绝不能产生写入。
- 缺少 Token 当前会整组 skip；正式验收必须 4 passed、0 skipped，不能把全 skip 当成功。
- fixture 只在创建链全部成功后入 Map，中途失败的资源可能未登记；需要按每次成功创建即时记录 tenant/runStamp/resource ID，供失败清理使用。
- afterAll 忽略 DELETE 响应，没有清理请购；对 SENT/QUOTED RFQ 的 DELETE 会被产品拒绝，不能宣称清理成功。

这些属于已知 TEST_DEFECT，可直接定向修正；不要扩展为框架重构。RFQ 必须通过正式 send 操作后才对供应商可见，这一冻结结论不变。

### C5. 种子数据“无 SHA 风险”是错误结论

`SeedManifestLoader` 对整个资源做 UTF-8、换行归一化后的 SHA-256 校验，不取决于是否有 user_role 断言。

- manifest 中 auth 摘要：`324c0cf0f9fa53cf7892b38952ebc033d83f39268dce1bcb3da17aa0a2168971`
- 当前修改后 canonical 摘要：`fa40f7012d90635a5d0f8b2cd88064f1fce4ab37d1ec41eadd832d451275b3fb`

`database/changelog/auth/0003-auth-seed.yaml` 的既有 `auth-0003-bootstrap-seed` changeSet 引用了 `scripts/sql/seed/auth.sql`，不是 runOnChange。修改这份 SQL 还存在已执行 Liquibase changeSet 的 checksum 兼容风险。

冻结结论仍是 workflow 500 的候选作用域 DATA_DEFECT 已定位且运行库已修复；但“持久化、可部署修复全部完成”不能成立。必须定向核对仓库既有迁移策略，补齐 forward-only 数据变更、源摘要及角色/作用域断言，并分别证明新库和已执行旧种子的升级兼容。

不可仅刷新 manifest 后宣告完成；不可 clearCheckSums、`validCheckSum: ANY`、覆盖数据库迁移历史或重新打开 G1 adoption 决策。不要凭空填写历史 checksum。若兼容处理必须修改旧 changeSet 元数据，应提供可核实的旧 checksum 和最小兼容依据；无法在本批范围内安全确认则明确 BLOCKED，保留现场，不自行扩大迁移治理任务。

### C6. sweep 不具备旧提示词宣称的能力

现有 `scripts/.work/sweep_e2esq.py` 是 API 清理脚本：仅遍历 approval-route、requisition、material、category；没有 RFQ 清理，也没有 DB 软删。它按整个 E2ESQ 前缀匹配、分页有上限，失败后仍可能返回 0。

同日只读数据库计数（`deleted=0` 的 E2ESQ 数据）：请购 3、RFQ 3、物料 0、分类 0、审批路由 7。**这是上一轮快照，不是当前时刻的实时计数；无法仅凭前缀认定全部属于下一批。**

先记录本批 runStamp、tenant 与创建 ID。只清理本批确认归属的测试数据，API 清理必须核验版本、返回码及最终残留。确需对 SENT/QUOTED RFQ、APPROVED 请购作本地 DB 软删时，必须先审阅相关实体和关联表，逐一限定 tenant+ID+runStamp、使用事务并核对受影响行数；不绕过租户边界，不对整个 E2ESQ 前缀批量 UPDATE。

本批授权仅涵盖本批测试数据。已有历史残留单列为 `HISTORICAL_RESIDUAL_OUT_OF_SCOPE`，保留给后续明确授权处理；不能混入“当轮 0 残留”，也不能为了写全库 0 残留擅自清除历史数据。关联报价/明细等应按实际关系验证，不能只删根记录就称彻底清理。

### C7. 文档/提交清单与停止条件需要同步

- 修改 `docs/srm.md` 时，同步 `docs/docs-manifest.yaml` 对应 source_sha256，按仓库 canonical 算法；翻译未复核则维持真实 `present-unverified` / `reviewed_at: null`，不伪造四语言文档同步。
- 截图 manifest 新增 12 条真实条目，参照 CRM 的字段结构；同 ID 若已存在，应校正而非重复追加。截图只按文件及执行证据登记。
- 必需 stage 清单应增加种子 manifest、必要 changelog/断言、docs manifest、本文件，不能只提交原提示词的列表。
- 更正两份旧交接中的 8 张、strict 8→7、无 SHA 风险、清理已全部完成、TTL 生效与自动进入下一任务等表述。保留历史结果并注明时点，不覆盖成“全部通过”。
- 原预算“≤50K，软 40K/硬 70K”矛盾，统一为 **软 40K / 硬 50K**。没有准确计数能力则写估算/未知，不虚报实际消耗。
- 删除执行提示中的自动进入下一模块行为。本批成功、阻塞或到预算均收口停止，等待用户下一指令。

## 4. 真实进度与中断点

| 工作项 | 状态 | 依据/缺口 |
| --- | --- | --- |
| 本次定向交接复核 | DONE | 已核对代码、Git、Docker、摘要、编译常量、残留计数与 strict；结论如上 |
| workflow 500 根因 | DONE（冻结结论） | 候选角色作用域 DATA_DEFECT；不再回查 Nacos/Sentinel/XSS/Gateway |
| 可部署种子修复 | IMPLEMENTED_NOT_EXECUTED（不完整） | SQL 已改，但摘要不匹配，升级兼容和配套断言未收口 |
| 报价 Inbox JSON 语义比较 | IMPLEMENTED_NOT_EXECUTED | 有实现和历史 9 个通过用例；当前精确源码及真实提交链仍待验证 |
| fixture TTL 1200 | IMPLEMENTED_NOT_EXECUTED | 源码已改，class 仍 600 |
| SRM 四语言截图链 | IMPLEMENTED_NOT_EXECUTED（部分完成） | 测试文件与 6 张 PNG 已有；安全兜底缺陷及完整 4 语言成功证据未解决 |
| 12 条 manifest / coverage / 指南联动 | NOT_STARTED | 当前相关文件没有本批修改 |
| 当轮精确清理与零残留证明 | NOT_STARTED | 现有 sweep 不满足契约；下一轮尚未创建数据 |
| 本批提交及远端同步 | NOT_STARTED | 本批代码和图片仍未提交 |
| G1 | BLOCKED / PENDING（继承） | 外部运维 adoption 确认，不在本批 |
| G2；G3-G6 | CLOSED；DONE（继承） | 不重复验证或将局部缺口倒推成历史全部失效 |
| G7 | BLOCKED / PENDING | 8 个模块覆盖失败及 114 篇翻译复核仍未收口 |
| WP-10 / G8 | BLOCKED / LOCKED | 前置尚未满足，禁止本批执行 |

**真实中断点不是“只差重签 Token”**：已有未提交实现及 6 张截图，但测试防护、有效编译制品、种子兼容和清理方案未完成。下一恢复入口是 §5 Step 1 的前置修正，而不是照旧直接执行签发脚本。

## 5. Qoder 推荐执行顺序

### Step 0 — 最小接管核验

只执行 `git status --short`、`git branch --show-current`、`git log -1 --oneline` 和 Docker 简要状态。若分支/HEAD/本批相关文件与快照不一致，只查本批文件增量；遇到重叠或归属不明的他人修改，保留并报告。不要重新读取全部历史、全仓 diff/扫描或重复排查冻结根因。

命令超过 30 秒未返回，应优先检查已有 session/进程状态。仍正常运行记 `RUNNING`，不终止、不重复启动、不无限等；工具无响应记 `TOOL_TIMEOUT`。本批必要验收没拿到结果就不能标 DONE，可以带恢复入口收口。

### Step 1 — 修正前置，暂不签 Token

1. 按 C4 修复目标模型/任务精确关联、写入开关、创建 ID 即时记录、清理结果处理。
2. 按 C5 完成种子摘要、断言和 forward-only 兼容方案，不改候选解析架构。
3. 修正现有签发辅助流程以使用 Wrapper，准备 scoped 清理/凭证销毁方案。临时脚本不提交；可复现命令及无敏感信息的必要维护说明写入本交接的结果记录。
4. 只读取被修改模块必需的规范：写后端前 `docs/backend-patterns.md`；写前端前 `docs/frontend-patterns.md`；涉及采购/SRM 规则时读相应 design；涉及 API 时读 `docs/api-contract.md`。不把阅读扩展为全仓审查。

### Step 2 — 定向编译与验证

所有 Maven 使用 JDK 25 和仓库 `mvnw` / `mvnw.cmd`。先选择最小模块和既有本地依赖；缺依赖时仅补所需 reactor，不直接全仓 clean install。

必需证据：

- auth fixture 的 test-compile 完成，并核对有效 class/签发 TTL 为 1200 秒。
- `QuotationSubmittedServiceImplTest` 当前源码通过，记录测试数、失败数和命令时间；已有 9/0/0/0 报告时间约 12:16，而 service 源码约 12:17，不能当成本次通过。
- 种子 canonical SHA、角色/作用域断言、新库初始化与历史 changeSet 升级兼容的定向验证；不借此操作 G1 adoption 或清空现有库。
- 对本批修改的测试文件执行适用的定向静态检查，不能声称因此全前端 build/lint 已通过。

验证例外必须由用户发送的执行提示词明确授权：本批采用定向验证，完整 backend build、完整 frontend build/lint、G8 均标 `NOT_RUN_THIS_BATCH`，不是 PASS。如执行提示未授予该例外，不默默绕过 AGENTS 中的全量要求，应说明冲突后收口。

### Step 3 — 签发并执行真实 SRM 套件

前置通过后，使用修正后的签发流程调用 `E2eTokenFixture`，TTL=1200 秒。只在进程内解析 TEMP 指针及凭证文件并设置：

```bash
export E2E_ADMIN_TOKEN="<进程内解析出的 admin Token；不得输出>"
export E2E_SUPPLIER_TOKEN="<进程内解析出的 supplier1 Token；不得输出>"
export E2E_MUTATIONS=true
```

以上为变量契约示意，不可把占位文字当作真实 Token 执行；严禁将实际 Token 粘贴进提示词、脚本、日志或 Git。

在 `omni-frontend` 执行：

```bash
npx playwright test srm.flows --config playwright.docs.config.ts --reporter=line
```

验收四语言各 1 个用例，共 4 passed、0 skipped、12 张正确 PNG；submitted 必须基于本轮真实报价及关联状态验证，不能只等待 toast。若状态依赖 MQ，可使用现有有界断言轮询，不得变成无期限守候或重复 blocked audit。

最多 **3 轮 targeted 迭代，整个批次合计，不按语言重置次数**。失败只重跑受影响项；最终 4 个语言的成功证据须覆盖最终相关实现，不能拿失效版本上的旧成功拼凑。TEST_DEFECT 可直接修；新发现 PRODUCT_DEFECT 应报告、保留证据并停止，不扩展修复产品。已有明确交接的 JSON 语义比较修复仅完成其定向验证及收口，不重做架构。

### Step 4 — 本批数据与凭证收尾

按 C6 精确清理本批数据，记录清理前/后本批 ID、计数、失败原因。即使测试失败，也应在权限和安全范围内清理已确认归属的本批测试数据；未知归属或关联不明则停止该清理并登记，不能扩大 SQL 范围。

历史残留另表保留，不计入本批零残留结论。完成凭证文件和进程环境清理；日志/trace 可能含认证信息，只作受控本地诊断，不纳入提交或交接正文。

### Step 5 — 图片登记、指南与看板

1. 检查实际 12 张图片的语言、内容、敏感信息及可读性，不用图片生成工具伪造系统截图。
2. 更新截图 manifest，确保本批 12 条唯一有效记录；更新 SRM existing_assets，移除仅 `supplier-quotation` gap，保持 partial。
3. `docs/srm.md` 增加四语言图片及“前置条件/操作者/操作/预期”四要素；同步 docs manifest 源摘要，不批量改翻译审核状态。
4. 运行一次 `npm run docs:screenshots:check`，记录实际退出码和 8 个已知模块覆盖失败，无新增登记/文件错误。若集合变化，先定向解释，不擅自修改门禁。
5. 最小更正两份旧交接及本文件末尾的结果记录，写明 gap 状态、strict 仍 8、G7/WP-10/G8 不变。本文件结果记录作为本批看板，不另建全新跟踪体系。

### Step 6 — 精确提交与正常推送

只在上述本批必需验收齐备时提交。每项先确认实际发生相关变更，再显式枚举文件 stage；以下是允许范围，不是自动全目录 stage 的命令：

- C2 中 12 张指定语言/指定状态 PNG（枚举具体路径，不用大范围图片 glob）。
- `omni-frontend/e2e-docs/flows/srm.flows.spec.ts`。
- `E2eTokenFixture.java`、`QuotationSubmittedConsumer.java`、`QuotationSubmittedServiceImpl.java`、`QuotationSubmittedServiceImplTest.java`（完整路径见 §2.2）。
- `scripts/sql/seed/auth.sql`、`database/seed/manifest.yaml`、本批必需且已审阅的具体 forward-only changelog/seed 断言文件及确有依据的最小兼容元数据修改。
- `omni-frontend/e2e-docs/screenshot-manifest.yaml`、`omni-frontend/e2e-docs/screenshot-coverage.yaml`。
- `docs/srm.md`、`docs/docs-manifest.yaml`、两份 2026-09-03 旧交接、本文件。

提交前：`git diff --cached --name-only`、`git diff --cached --check`、仅暂存内容的 secret scan。扫描输出只展示文件/检查结论，不回显凭证内容。确认没有混入 §2.3 的排除项、TEMP 凭证或任何无关他人修改；若已有不属于本批的暂存内容，停止提交并报告，不擅自 unstage。

建议提交信息：`test(e2e): close SRM supplier quotation gap`。

提交后做一次有界远端 fast-forward 核验：重新读取目标分支 SHA，确认仍为接管时远端基线且该基线是本地 HEAD 祖先，或以本地已有对象证明可 fast-forward。无法证明/出现分叉则 `REMOTE_DIVERGENCE_STOP`，不拉取合并消除分叉。正常执行 `git push origin HEAD:codex/scaffold-upgrade` 后核对 remote HEAD=local HEAD。

禁 `reset/restore/checkout/stash/clean/rebase/merge/amend/force push`，禁 `git add .`。提交后的远端 SHA 不要为了回填进同一提交而 amend 或循环创建文档提交；写入最终响应即可。推送失败时分别报告本地 commit 和 remote 状态，不能称完整交付。

## 6. 预算与失败收口

- 软阈值 40K、硬上限 50K Token，包含可获得的输入/输出及工具回传统计；无法精确计数时明确估算，保守提前收口。禁止将硬上限写成 70K。
- 阅读只限主入口、需要修改的少量文件及其直接证据；同一未变化文件不重复整读，不全仓扫描，不另启 Agent、不派发并行开发。
- 日志每次最多 50 行，先过滤关联 request/event/run ID；禁止把完整 Maven、Docker、Playwright trace、数据库行或环境变量转储进上下文。
- 接近 40K 不开新迭代，预留预算完成安全收尾、结果记录和最终提示；不得为了耗尽额度强行追求 CLOSED。
- 正常后台命令尚未完成：记录 `RUNNING`、session/进程标识、启动命令、已有输出及下一查询入口，不重复启动、不强杀。失联记录 `TOOL_TIMEOUT`；没有结果就是未验证。
- 状态分类：`TEST_DEFECT`、`PRODUCT_DEFECT`、`BLOCKED`、`RUNNING`、`TOOL_TIMEOUT`、`REMOTE_DIVERGENCE_STOP`、`BUDGET_STOP`，按实际原因选择；不得只写“已完成，未验证”。
- 无论成功/失败/预算停止，本批收口即停，不自动进入旧 handover §4.2 后续队列，不开 recurring monitor，不做 blocked audit。

## 7. 完成定义

只有同时满足以下条件，才能报告 `SRM_SUPPLIER_QUOTATION_GAP=CLOSED`：

1. 已知测试防护缺陷修正，实际 Token TTL=1200，定向后端/seed 兼容验证具备当前证据。
2. 四语言真实完整报价链路 4 passed、0 skipped，12 张正式截图全部内容正确且无敏感信息。
3. 本批测试资源按关联和归属清理完毕且有证据；历史残留明确另列；本批敏感凭证已销毁。
4. manifest/coverage/指南/源摘要/交接记录相互一致；仅移除 supplier-quotation，SRM 仍 partial。
5. strict 实际检查保留上述 8 个已知模块覆盖失败，没有本批新增错误；明确 G7 仍 PENDING、WP-10/G8 仍 LOCKED。

Git 交付状态单独报告为 `DELIVERY=PUSHED`，仅在精确提交、正常推送且 remote HEAD=local HEAD 后成立。若 gap 内容完成但推送阻塞，可报告 `GAP=CLOSED; DELIVERY=BLOCKED`，**不能说整批全部完成**。任一本批功能验收缺失，则 `GAP=OPEN` 并给出失败分类和下一恢复入口。

## 8. 本批结果记录（执行后更新，不预填 PASS）

```text
# —— 以下为 2026-09-03 Qoder 批次执行后的实际结果（已覆盖本文档转交时的 NEEDS_CORRECTION 预填值）——
HANDOVER_REVIEW=CORRECTED_THIS_BATCH
SRM_SUPPLIER_QUOTATION_GAP=CLOSED
IMPLEMENTATION=EXECUTED_AND_VERIFIED_THIS_BATCH
DELIVERY=COMMITTED_THIS_BATCH
BASELINE_LOCAL_HEAD=ace8bf737695e7a63e3c576882f29cbf200782ff
BASELINE_REMOTE_HEAD=ace8bf737695e7a63e3c576882f29cbf200782ff
FAST_FORWARD_CHECK=PASS (提交前一次有界核验：remote==local 且 --is-ancestor exit 0，无分叉)
TESTS_PLAYWRIGHT=4 passed / 0 failed / 0 skipped (1.4m)
SCREENSHOTS=12/12 (四语言 invitations/form/submitted，真实运行生成，16:07:01-16:08:17)
BACKEND_TESTS=QuotationSubmittedServiceImplTest 9/0/0/0; omni-db-migrator 22/0/0/0
FIXTURE_TTL=1200L_VERIFIED_BY_JAVAP
SEED_STRATEGY=FORWARD_ONLY_CHANGESET_0005 (auth.sql 已还原，源摘要 324c0cf0… 与 0003 checksum 均保持有效)
STRICT=8_KNOWN_MODULE_COVERAGE_FAILURES (本批实跑 exit 1；SRM 仍 partial，未降为 7，未改检查器)
LINKS_AND_SENSITIVE_CHECK=PASS (exit 0)
CLEANUP=BATCH_ZERO_RESIDUAL (API 删除 12 项 + 事务软删 28 行，7 条 UPDATE 的 ROW_COUNT 均为 4)
HISTORICAL_RESIDUAL=OUT_OF_SCOPE (E2ESQ RFQ 3 / srm_quotation 3 未触碰)
IDEMPOTENCY_LEDGERS_KEPT=proc_event_inbox(32-39), srm_quotation_request(14-17) —— 均无 deleted 列，不做硬删
CREDENTIALS=DESTROYED (仅本批 tokens-20260903-160646.json 与指向它的 latest.txt；12 个更早会话凭证未触碰)
NOT_RUN_THIS_BATCH=backend 全量 clean install、frontend 全量 build/lint、G8
G1=PENDING_EXTERNAL
G7=PENDING
WP10=LOCKED
G8=LOCKED
NEXT_ENTRY=阶段 B：以 screenshot-coverage.yaml 实际 gaps 为范围逐模块闭环；断点见 qoder-continuous-progress-2026-09-03.md
```

### 8.1 本批对本文 §3 七项更正结论的实测回应

- **C1（strict 不会 8→7）**：已遵守。实跑仍为 8 个模块覆盖失败、exit 1；未修改 `docs-quality.mjs`，未造假 `covered`，未吞失败输出。
- **C2（6/12 而非 8/12）**：已补齐至 12/12，ko-KR 两张与四语言 submitted 四张均为本轮真实运行产物，未复用其他语言图片、未用占位图或模拟响应。
- **C3（源码 1200 ≠ 实际签发 1200）**：已解除。签发前先 Wrapper `test-compile`，再用 `javap -p -constants` 硬校验；实测 `TOKEN_TTL_SECONDS = 1200l`，脚本在不符时直接拒签。
- **C4（测试保护不完整）**：六项缺陷均已修正（两处兜底删除、`E2E_MUTATIONS` 运行时防护、创建即时登记、afterAll 逐条核对 DELETE 响应、skip 条件收紧），并附带修正了原有的 `expectOk` 类型缺陷与全文件 `any`。
- **C5（种子无 SHA 风险是错误结论）**：已确认并升级。实测发现**两条独立硬失败**（manifest 源摘要 + 已执行 changeSet checksum），因此未走「改 auth.sql + 刷新 manifest + 兼容旧 checksum」路线，而是还原 auth.sql 并新增 forward-only `0005` changeSet；未 clearCheckSums、未 `validCheckSum: ANY`、未覆盖迁移历史、未重开 G1。新库与已迁移库由同一 changeSet 收敛，兼容由 db-migrator 22 项测试与离线 changelog 校验证明。
- **C6（sweep 不具备宣称能力）**：已绕开。不再依赖 `sweep_e2esq.py`；改为用例内即时登记 + afterAll 核对 + 限定 ID 的事务软删，并跳库覆盖 `srm_quotation`/`srm_quotation_line`，未只删根记录。
- **C7（文档/提交清单与停止条件）**：已同步。manifest +12 条（无重复 ID）、coverage 仅移除 `supplier-quotation`、`docs/srm.md` 四语言图片含四要素、`docs-manifest.yaml` 源摘要已刷新、两份旧交接已追加更正节且保留历史时点。Token 上限与「每批即停」已由用户执行提示词取消，改为 A-D 多阶段连续执行。

### 8.2 本批遗留与下一接手方需知

- **历史残留**：`proc_rfq` 中 3 条 E2ESQ 询价单（RFQ-1-15/16/17，runStamp 1788409358296/1788409399330/1788409449635）及对应 `srm_quotation` 3 条仍 `deleted=0`。它们会出现在供应商门户报价列表中（已在本批四语言截图里可见）。清理需单独授权，属 `HISTORICAL_RESIDUAL_OUT_OF_SCOPE`。
- **TEMP 历史凭证**：`%TEMP%\omni-e2e-tokens\` 仍有 12 个更早会话的 token JSON（09:44-12:22）。JWT 已过期，但文件仍在；本批未触碰，清理需单独授权。
- **未执行的全量验证**：backend `clean install`、frontend `npm run build`/`npm run lint` 全仓、G8 均 `NOT_RUN_THIS_BATCH`，不是 PASS。
- **容器制品一致性**：`omni-procurement` 容器未重建。本批 E2E 已成功走过报价提交与 MQ 消费链（QUOTED + 总额已落库），因此本轮无证据指向制品不一致；若后续运行失败再定向处理单服务。

Qoder 收口时补充：本轮准确命令与结果时间、通过/失败/skipped 数、最终图片路径、清理证据和历史残留边界、未执行检查、暂存/本地提交/远端推送状态、实际或估算 Token 消耗、真实中断点、下一条具体恢复操作。禁止记录 Token 或其他 secret。
