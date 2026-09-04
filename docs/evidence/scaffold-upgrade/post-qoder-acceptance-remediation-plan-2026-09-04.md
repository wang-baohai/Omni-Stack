# Qoder 执行后验收整改交接方案

日期：2026-09-04

仓库：`C:\WorkSpace\QODER\Omni-Stack`

分支：`codex/scaffold-upgrade`

验收基线：`52b2c5cd693479a3cab994ddac4427478a24a554`

## 1. 结论与本方案目标

上一轮 Qoder 产生了大量有效成果，但把多阶段 Goal 提前标记为完成。准确结论是：

```text
OVERALL_ACCEPTANCE=NOT_PASSED
STAGE_A_SRM_QUOTATION=ACCEPTED
STAGE_B_SCREENSHOT_GAPS=PARTIAL
STAGE_C_TRANSLATION_STRUCTURE=COMPLETED
STAGE_C_INDEPENDENT_REVIEW=PENDING
STAGE_D_FINAL_VALIDATION=PARTIAL
ORIGINAL_SCAFFOLD_UPGRADE=OPEN
```

本方案的目标不是推翻现有成果，而是：

1. 先把交接状态改成唯一、无矛盾、可恢复的事实源。
2. 修复四语言文档中的客观缺漏，建立独立复核包，不让结构分数代替语义验收。
3. 经严格前置检查后恢复采购种子数据，解除采购/资产截图链路的数据阻塞。
4. 继续关闭可真实构造的截图 gaps；不可构造项形成明确的产品/授权决策包。
5. 对实际改动执行常规全量构建和门禁；外部 Gate 未满足时如实停在 Gate 前。
6. G1、G7 真正关闭后，才执行 WP-10 和一次性 G8。

## 2. 已复核的当前事实

### 2.1 Git、远端和运行环境

- 本地 HEAD：`52b2c5c`。
- Gitee 与 GitHub 的 `codex/scaffold-upgrade` 已分别实时核对，均为 `52b2c5c`。
- tracked 工作区与暂存区为空；现有 untracked 文件均属于旧交接明确保留的排除项。
- `ace8bf7..52b2c5c` 有 34 个提交、224 个不同文件，约 `+20347/-1738`。
- Docker 当前 15 个容器 running，其中 14 个 healthy；frontend 没有 healthcheck。
- 不需要重新执行 Stage A 的 Maven、Playwright 或写入型数据库验证，除非后续改动触及其代码/数据契约。

### 2.2 当前门禁

2026-09-04 独立轻量复验：

| 检查 | 当前结果 | 正确解释 |
| --- | --- | --- |
| `npm run docs:screenshots:check` | exit 1，8 项失败 | 6 个 partial + 2 个 missing；不是回归到未知状态 |
| `npm run docs:i18n:check` | exit 1，204 项失败 | 102 份均 `present-unverified`，且 102 份缺 `reviewed_at` |
| Qoder parity | 102 个被分类为 `ALIGNED` | 宽松结构分类；74/102 仍有 findings，只有 28 组 findings=0 |
| 截图 coverage | 4 covered / 6 partial / 2 missing | 当前仍有 36 个 gaps |

已确认的 P0 文档客观缺漏：中文 `docs/api-contract.md` 中的 `GET /api/base/mq-message/runtime` 与 `base:mqmessage:list` 没有进入 en/ja/ko；当前 `api-contract` 三语结构指标仍为 83/85 标题、70/72 围栏、295/305 表格行。`guide-authentication` 也存在会话过期章节/图片不同步。

### 2.3 已验收、不得重做的成果

- `653afe3` 的 SRM supplier quotation：4 passed / 0 skipped、12 张真实截图、forward-only auth changeSet、TTL 1200、mutation guard、精确 model/task 关联、JSON 语义比较、manifest/coverage/指南联动均有对应代码和提交证据。
- 后续 Stage B 已提交的只读管理页、响应式、字典三态和详情弹层资产。
- Stage C 已补齐的大量翻译草稿和结构，不整体回滚；只处理经证据确认的缺漏/错误。
- 全部提交已经双远端 fast-forward 推送，不改写历史。

### 2.4 当前交接文档的已知矛盾

`docs/evidence/scaffold-upgrade/qoder-continuous-progress-2026-09-03.md` 是有价值的过程日志，但不是可靠的最终状态页：

- §5.2/§5.3 仍保留早期“Stage B 0、Stage C 111 未开始”表述，后文又称 Stage C 已完成。
- §6 仍引用已经关闭的 `stable-mobile-flow`。
- §8.1 仍写旧 HEAD `6c03b32`。
- §8.3 仍把已经完成的 P1/设计文档列为下一入口。
- 最终叙述称“目标全程纯文档、没有 E2E/截图”，与同一 Goal 的 Stage A/B 提交不符。

## 3. 总体依赖关系

```text
WP-0 统一事实源
  ├─> WP-1 客观 i18n 缺漏整改 ─> EX-2 独立语义复核 ─┐
  └─> WP-2 采购种子数据恢复 ─> WP-3 截图 gaps ────┤
                                                      ├─> G7
EX-1 G1 adoption 运维决定 ────────────────────────────┘
G1 + G7 CLOSED ─> WP-5 清理 ─> G8 once-only ─> 原目标完成

WP-4 常规构建/质量验证覆盖 WP-1～WP-3 的实际代码与文档改动。
```

EX-1、EX-2 是独立输入，Qoder 不能代签。其未完成不会阻止独立的 WP-0～WP-4，但会阻止 G7/WP-5/G8。

## 4. 分阶段实施方案

### WP-0：统一交接事实源

目标：消除“过程记录正确、最终状态互相矛盾”的问题。

实施：

1. 只执行 `git status --short`、`git branch --show-current`、`git log -1 --oneline`、两个远端 SHA 和 Docker 简表。
2. 在旧 checkpoint 顶部增加“当前有效状态”摘要；历史段落保留，但给已过时段落加 `SUPERSEDED_BY_CURRENT_STATUS`，不删除取证过程。
3. 把当前门禁更新为：截图 8 red/36 gaps；i18n strict 204；102 个结构候选、74 个有 findings；Stage D 未完成。
4. 把恢复入口改为 WP-1 和 WP-2；移除已完成翻译组的陈旧待办。
5. 将本方案和执行提示词加入首次精确文档提交。

涉及文件：

- `docs/evidence/scaffold-upgrade/qoder-continuous-progress-2026-09-03.md`
- `docs/evidence/scaffold-upgrade/post-qoder-acceptance-remediation-plan-2026-09-04.md`
- `docs/evidence/scaffold-upgrade/qoder-remediation-goal-prompt-2026-09-04.md`

验收：文档中只存在一份 current state；搜索旧 SHA、旧 gap、旧待办时能看见明确的历史/已取代标识；不得把 G1/G7/WP-10/G8 改为完成。

Token 估算：4K～8K。建议软上限 6K，硬上限 10K。

### WP-1：客观 i18n 缺漏整改与独立复核包

目标：把“结构看起来接近”提升为“所有可机械验证的关键内容都不缺”，但仍不冒充独立语义复核。

实施：

1. 复用当前 parity JSON，一次性生成 74 个有 findings 的队列；不要每修一个文件就重跑全库。
2. 对每个 finding 按下列类型分类，并写入 `docs/evidence/scaffold-upgrade/i18n-findings-resolution-2026-09-04.md`：
   - `EXPECTED_LOCALIZATION`：语言专属图片路径、翻译后的普通展示文字等合理差异；必须写清为何合理。
   - `FIX_REQUIRED`：API 路径、权限码、字段、枚举、命令、代码块、数字、表格行、流程步骤等应 verbatim 或应等价但缺失/错误。
   - `INDEPENDENT_REVIEW_REQUIRED`：只有母语/领域复核者能判断的语义自然度与忠实度。
3. 先修 P0：`api-contract` 缺少 MQ runtime API/权限码；`guide-authentication` 会话过期章节和图片；再按 P1/P2 队列推进。
4. 修译文时只对照对应中文事实源及直接相关代码；禁止顺带重写中文源，除非发现有代码证据的源文档事实错误，并单独记录。
5. 更新真实 `source_sha256`、翻译队列和链接；不批量设置 `status=synchronized`，不填写 `reviewed_at`。
6. 每 8～12 个源文档作为一批，批末统一运行 parity、links、sensitive、i18n `--allow-draft`；只对失败项回查。
7. 完成后生成独立复核包：逐源列出修改摘要、剩余语义疑问、不可翻译 token、复核人签核位置。

验收：

- 所有 API 路径、权限码、字段/枚举和代码块的真实遗漏为 0。
- 每个原始 finding 都有 `EXPECTED_LOCALIZATION`、`FIXED` 或 `INDEPENDENT_REVIEW_REQUIRED` 结论，不能通过调阈值隐藏。
- 结构工具可保留阈值，但报告名称不得再写“100% 内容完整”。
- `links`、`sensitive`、`i18n --allow-draft` 通过。
- strict i18n 在独立复核前仍可预期失败，不吞掉退出码。

Token 估算：35K～70K；独立语义复核不计入本 WP。每批建议 8K～15K，单批硬上限 20K，总软上限 55K、总硬上限 75K。

### WP-2：采购 bootstrap 种子数据恢复

目标：只恢复被历史误删的 13 个 bootstrap 品类，重新满足 `procurement-default-config`，不清理其他历史数据、不代替 G1 运维决定。

实施：

1. 只读核对 `scripts/sql/seed/procurement.sql`、manifest 断言、相关 changelog 与当前库。
2. 在事务中 `SELECT ... FOR UPDATE`，确认 tenant=1、ids=1..13、category_code 与 seed 集合完全一致、全部 `deleted=1`、`update_by IS NULL`，同时不存在冲突的 active code。
3. 任一前置不符：ROLLBACK，输出 `DATA_RESTORE_PRECONDITION_MISMATCH`，不得“尽量恢复”部分行。
4. 前置完全一致时，仅撤销这 13 行的 soft-delete；不改名称、层级、业务键及其他数据。记录前后快照的非敏感字段与摘要。
5. 在同一受控流程中验证影响行数恰好 13、active 集合恰好恢复，再 COMMIT；异常时 ROLLBACK。
6. 运行 `procurement-default-config` 对应 seed verifier/定向测试，预期实际 14=期望 14。
7. 不修改 adoption baseline、不执行 adopt-current、不把 G1 标记为关闭。

临时 SQL/Python 只允许放 `scripts/.work/`，不提交；最终提交只包含确有必要的永久迁移/断言修正。若仓库代码已正确且只是本地运行库恢复，可以没有代码提交，但必须把结果写入 checkpoint。

验收：13 行精确恢复；seed 断言通过；无额外行变化；事务/影响行数证据齐全；G1 仍 PENDING。

Token 估算：6K～12K。软上限 10K，硬上限 15K。

### WP-3：剩余截图 gaps 分批闭环

当前基线：8 个未通过模块、36 个 gaps。所有批次均遵循“四语言同 fixture/同步骤/同视口、真实功能、真实状态、图片质检、数据自建自清理、manifest/coverage/指南/摘要联动”。已存在且有效的图片不得重拍。

#### WP-3A：不依赖高风险外部决定的业务链

优先顺序：

1. SRM `admission-lifecycle`、`detail-and-action-states`。
2. procurement `requisition-approval`、`rfq`、`quotation-receipt`；复用 Stage A 已验证链，不重跑 supplier-quotation 已完成截图。
3. procurement `comparison`、`purchase-order`、`goods-receipt`、success/failure/detail states；在 WP-2 后使用恢复的 seed 或每批唯一 fixture。
4. asset receipt-card/ledger，再推进 allocation/acceptance/return/transfer/disposal 及状态图；依赖采购收货事件确实产生资产卡片。

每个业务批次先写“资源登记表”（tenant、runStamp、业务键、创建 ID、允许的清理契约），再开 mutations；失败也执行同一清理。Flowable 审计历史、Inbox/outbox 等没有安全删除契约的记录必须在开跑前定义保留策略，不得运行后才声称“有意保留”。

验收：测试 0 skipped；图片与 manifest 一一对应；正式图不混入其他语言 fixture 或无关历史测试记录；当前批测试资源为 0，允许保留的审计记录提前获得规则依据；只在模块全部满足时转 covered。

Token 估算：

- SRM 两项：20K～35K。
- procurement 剩余链：40K～75K。
- asset 全链：50K～90K。
- 合计：110K～200K，每个模块软上限 55K、硬上限 90K。

#### WP-3B：需要隔离或额外身份的场景

- workflow model lifecycle/failure：优先使用独立临时流程 key 和明确删除/隔离策略；不得污染现有生产式模型。没有安全回滚路径则形成 PRODUCT_DECISION 包并停止该项。
- workflow countersign：可以让 fixture 为**已有且角色/数据范围正确的测试用户**签发短期 Token；不能临时给普通用户越权、不能硬编码 Token。身份不足则输出所需身份矩阵，等待授权。
- messaging retry/dead-letter：优先在独立 Compose project/临时 volume 中构造失败消息；不得向当前共享 relay 无界注入故障。隔离环境不可行时形成授权包，不在共享环境硬做。
- trace-diagnosis：只使用本批可追踪请求和真实 Trace ID，不伪造可观测数据。

验收：隔离边界、身份来源、清理和失败注入都可复现；共享环境无新增故障残留。

Token 估算：45K～85K。按 workflow 与 messaging 两批，各批软上限 35K、硬上限 50K。

#### WP-3C：缺失产品页面与非页面证据

- system-management 的 config/login-record：先定向确认是否由现有页面承载；若确实没有产品实现，登记 PRODUCT_GAP，不能靠截图任务新增整套业务页面，也不能自行删 required_flow/exempt。
- scaffold-development：若 CLI 功能真实存在，采用四语言/语言无关的真实终端运行记录、输出文件和失败状态证据；若 `implementation-not-yet-delivered` 属实，登记 PRODUCT_GAP。
- operations：优先真实 Compose health、Trace、Grafana/dashboard、alert 页面/终端证据；没有可观察实现就登记 PRODUCT_GAP。
- 对 terminal/dashboard 证据形态先形成 `EVIDENCE_FORMAT_DECISION`，由用户确认后才能调整 coverage schema 或标记 exempt。

验收：不以不存在的 UI 为目标造假；每项明确为 CLOSED、PRODUCT_GAP 或 WAITING_DECISION。

Token 估算：15K～30K（只含核实和决策包）；若用户另行批准开发缺失产品功能，应另立任务，预计额外 60K～150K，不计入本方案。

### WP-4：常规构建与质量验证

触发：WP-1～WP-3 的代码/文档修改稳定后执行一次，不在每个小提交后重复全量运行。

实施：

1. JDK 25，仓库 Maven Wrapper，backend `clean install`。
2. frontend `npm run build`、`npm run lint`。
3. docs links、sensitive、readme、screenshots、i18n strict；允许预期红的项目必须逐项说明，不能汇总成 PASS。
4. 只对实际修改的 E2E 套件做最终组合回归；不得把所有截图重新生成一遍。
5. 保存摘要：命令、时间、通过/失败数、失败归属、日志路径；日志正文不进入 checkpoint。

本 WP 不是 G8。G8 仍等全部 Gate 后一次执行。

验收：build/lint 成功；无未知回归；已知 Gate 失败集合与看板一致。

Token 估算：8K～18K。软上限 15K，硬上限 22K。

### WP-5：外部 Gate 包与停止点

Qoder 可完成：

- 为独立 i18n 复核者输出逐源复核包，不自行填写 `reviewed_at`。
- 为 G1 运维方输出当前 seed/adoption 证据，不自行 APPROVE。
- 为 PRODUCT_GAP、身份、故障注入、证据形态输出最小决策问题。
- 将所有实际完成/未完成项更新到唯一 current state。

Qoder 必须停止：只剩上述外部输入时，状态设为 `WAITING_EXTERNAL_INPUT`，不能标 Goal complete，不能循环轮询。

Token 估算：5K～10K。

### EX-1 / EX-2：独立输入

- EX-1：运维对 adoption baseline 作出 APPROVE/REJECT/NEED_MORE_EVIDENCE。
- EX-2：独立复核者按 102 个译文条目逐项核对语义。只有实际复核通过的条目才改 `synchronized` 并填真实 `reviewed_at`。

独立翻译复核 Token 估算：160K～300K，建议按 P0/P1/P2 分 8～12 个源一批；它不是当前 Qoder 自审的继续执行步骤。

### WP-6：G7、WP-10 与 G8（未来解锁后）

1. 只有截图技术门禁满足既定决策、i18n strict=0，才评估 G7=CLOSED。
2. G1+G7 CLOSED 后才能执行 WP-10：按库存逐类清理临时脚本、patch、debug 图片、旧交接；删除前做引用检查，保留最终文件和 seed SQL。
3. WP-10 完成后只执行一次 G8 全矩阵，生成最终交付报告。

Token 估算：45K～85K；当前 LOCKED，不纳入本次 Goal 的自动执行范围。

## 5. 总 Token 估算

| 范围 | 估算 |
| --- | ---: |
| WP-0～WP-2 | 45K～90K |
| WP-3 截图技术线 | 170K～315K |
| WP-4～WP-5 | 13K～28K |
| 本次 Qoder 可执行部分合计 | **228K～433K** |
| EX-2 独立翻译复核 | 160K～300K |
| 解锁后的 WP-6 | 45K～85K |
| 从当前到原始目标完成的总量级 | **约 433K～818K** |

这是工程量级估算，误差可能达到 -30%～+50%，不能换算成精确的 Qoder 免费调用次数。模型调用、上下文压缩、失败重试和工具返回都会改变实际消耗。

## 6. 节省 Token 与防跑偏约束

1. **唯一事实入口**：先读本方案和 Goal 提示词；旧 checkpoint 只读取指定小节。禁止每轮重读三份大交接和整段历史。
2. **一次检查、批末复验**：同一输入没有变化时不重复全量 gate；文件级修正用定向检查，8～12 个源或一个模块后再统一复验。
3. **不重做通过项**：Stage A、已有图片和已有通过测试只在相关代码发生变化时重跑。
4. **按 ID/文件定位**：先 `rg`/manifest/coverage 精确定位，再读取必要行；禁止无目的全仓 scan/diff/log。
5. **日志摘要**：默认只返回失败附近 50 行；完整日志写排除目录，只在错误分类需要时追加读取，禁止打印 Token/环境变量/数据库整行。
6. **失败防空转**：同一失败连续 3 次没有新证据即 BLOCKED；记录最后命令、错误、进程/session 和下一恢复入口，转做独立任务。
7. **长命令复用进程**：正常 Maven/Playwright 继续运行就复用 session，不终止、不重复启动；状态轮询采用退避。
8. **原子批次**：每批只含一个模块或 8～12 个源文档；批次验收后小提交。不要积累一个跨领域巨型 diff。
9. **上下文收口**：达到各 WP 软上限的约 75% 时停止扩展，先更新 checkpoint、清理本批凭证/数据、提交已验收成果；未完成项下轮从具体入口恢复。
10. **不可伪造 Token 统计**：平台无计数时写 `TOKEN_USAGE=UNKNOWN`，不能为满足预算编数字，也不能因为额度充足扩大需求。
11. **不自动多 Agent**：共享 Docker/DB 的写入任务保持串行；只有用户明确批准且工作树隔离、任务完全独立时才并行。
12. **停止边界**：不得自动修产品缺口、代签独立复核、批准 G1、执行 WP-10/G8 或清理历史残留。

## 7. Git 与数据安全约束

- 禁止 `reset/restore/checkout/stash/clean/rebase/merge/amend/force push`，禁止 `git add .`。
- 不删除、不提交既有排除项；首次状态以 `git status --short` 为准。
- 每批显式 stage；提交前执行 cached name-only、cached check、仅暂存内容 secret scan。
- 每次 push 前做一次 fast-forward 证明；任一远端变化无法证明安全时 `REMOTE_DIVERGENCE_STOP`。
- 数据写入必须提前登记 tenant/runStamp/ID，事务和清理边界与创建步骤一起设计。
- 禁 CAPTCHA bypass、Redis 登录 Token 查询、打印 Token、读取 `.env.before-rebuild-*`。
- 数据库历史、Flowable history、MQ/Inbox 等没有安全删除契约时，先取得明确策略，不能事后自行豁免。

## 8. 本轮 Goal 的完成定义

本次 Qoder Goal **只能**在以下状态结束：

1. `WAITING_EXTERNAL_INPUT`：WP-0～WP-5 的所有当前可执行项完成，剩余项逐一确认为外部决定；这是正常收口，但不是原始目标完成。
2. `PARTIAL_CHECKPOINT`：达到批次/上下文/平台限制，已安全清理并提交所有通过成果，留下精确恢复入口。
3. `BLOCKED`：关键依赖失败，且不存在可继续的独立任务。

禁止在 G1/G7/WP-10/G8 未满足时输出 `OVERALL_GOAL=COMPLETE`。原始升级目标只有在 G8 once-only 全部通过并形成最终报告后才算真正完成。
