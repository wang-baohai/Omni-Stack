# ZCode GLM-5.3-Flash 验收整改执行方案

## 1. 目的与边界

本方案从 `post-qoder-acceptance-remediation-plan-2026-09-04.md` 中抽取适合 GLM-5.3-Flash 独立完成、风险可控且能够客观验收的工作。目标是在一个 ZCode Goal 中尽量关闭这些事项，形成可供下一轮独立验收的提交和证据；本批不承担高风险数据恢复、跨服务故障注入或最终 Gate 定级。

本方案是 **Flash 批次的唯一执行入口**。上游整改方案保留为范围来源，但不得据此自动扩展本批任务。

仓库与接管基线：

- 仓库：`C:\WorkSpace\QODER\Omni-Stack`
- 预期分支：`codex/scaffold-upgrade`
- 已知基线：`52b2c5cd693479a3cab994ddac4427478a24a554`
- 基线会随合法提交变化；开始时只做一次实时核验，以 Git 实际状态为准。

事实优先级：

1. 当前代码、Git、实际测试结果；
2. 本方案；
3. `post-qoder-acceptance-remediation-plan-2026-09-04.md` 的指定相关小节；
4. 历史 progress/checkpoint。历史结论不得覆盖新证据。

## 2. 冻结事实

- Stage A supplier quotation 已完成，不重做；只有相关实现发生变化才运行对应定向回归。
- 已知截图门禁基线为 8 个 red、36 个 gaps；必须实时执行后才能写成当前结果。
- 已知 i18n strict 基线为 204；102 个 `ALIGNED` 仅表示宽松结构分类，其中 74 个仍有 findings。
- 独立语义复核完成前，不得把翻译标记为 `synchronized`，不得填写 `reviewed_at`。
- G1、G7 仍待外部输入；WP-10 和 G8 仍锁定。
- 已排除的 untracked 临时项不提交、不删除；本批不承担历史临时文件清理。
- 本地 Docker 可继续复用，但旧容器数量和健康状态只作为历史信息，开始时只核验一次简表。

## 3. 本批范围

### ZF-0：接管和事实源收口

目标：建立一份无矛盾、可继续执行的 current state。

实施：

1. 只执行一次最小 Git 核验：分支、短状态、HEAD、两个远端分支 SHA。
2. 只执行一次 Docker 服务简表，不读取全量日志、不重建全栈。
3. 在 `qoder-continuous-progress-2026-09-03.md` 顶部维护唯一 current state；旧结论保留并标记 `SUPERSEDED_BY_CURRENT_STATUS`。
4. 修正旧 SHA、旧截图/i18n 数量、已完成翻译待办和“全程纯文档”等冲突表述。
5. 首个精确文档提交纳入以下四份方案/提示词：
   - `post-qoder-acceptance-remediation-plan-2026-09-04.md`
   - `qoder-remediation-goal-prompt-2026-09-04.md`
   - `zcode-glm53-flash-execution-plan-2026-09-04.md`
   - `zcode-glm53-flash-goal-prompt-2026-09-04.md`

验收：current state 只有一份；旧结论有明确历史标识；不得把 G1/G7/WP-10/G8 改成完成。

Token 估算：3K～6K；软上限 5K，硬上限 8K。

### ZF-1：客观 i18n 缺漏整改和独立复核包

目标：关闭能够由代码、中文事实源和结构工具客观验证的遗漏，不冒充母语或领域独立复核者。

实施：

1. 复用现有 parity 结果生成一次 findings 队列，禁止每修一个文件就重扫全库。
2. 创建/维护 `i18n-findings-resolution-2026-09-04.md`，逐项分类为：
   - `EXPECTED_LOCALIZATION`：合理语言差异，并记录理由；
   - `FIX_REQUIRED`：API、权限码、字段、枚举、命令、代码块、数字、表格或流程步骤确有缺失/错误；
   - `INDEPENDENT_REVIEW_REQUIRED`：语义自然度、领域表达或文化本地化只能由独立复核者判定。
3. 先处理 P0：`api-contract` 三语中的 MQ runtime API/权限码，以及 `guide-authentication` 会话过期章节/图片。
4. 再按 P1/P2 处理所有能够客观判定的 `FIX_REQUIRED`；只对照对应中文源和直接相关代码，不顺带重写中文源。
5. 每 8～12 个源文档为一批；批末统一运行 parity、links、sensitive、i18n `--allow-draft`，只回查失败文件。
6. 更新真实 `source_sha256` 和翻译队列；禁止批量设置 `synchronized` 或填写 `reviewed_at`。
7. 输出独立复核包：逐源修改摘要、剩余语义疑问、不可翻译 token、复核签核位置。

验收：

- API 路径、权限码、字段/枚举、命令和代码块的客观遗漏为 0；
- 每个被处理 finding 都有可追踪分类；
- links、sensitive、i18n `--allow-draft` 通过；
- strict i18n 的真实失败不得隐藏或改写为 PASS；
- 不出现“100% 语义正确”“已独立复核”等越权结论。

Token 估算：30K～60K；每批建议 7K～12K，单批硬上限 16K；总软上限 50K，总硬上限 70K。

### ZF-2：低风险截图与证据闭环

目标：利用 GLM-5.3-Flash 的截图理解能力，关闭无需采购种子恢复、无需额外身份授权、无需共享中间件故障注入的场景。

允许执行：

1. SRM `admission-lifecycle`。
2. SRM `detail-and-action-states`。
3. `system-management` 中 config/login-record 是否已有真实承载页面的只读核实。
4. `scaffold-development` 和 `operations` 已有产品能力及可用证据形态的只读核实。

SRM 截图执行要求：

- 先从 manifest、coverage 和现有测试定向定位入口，禁止全仓重新调查。
- 已有且有效的正式图片不重拍。
- 运行写入型 E2E 前记录 tenant、runStamp、业务键、创建 ID 和清理契约。
- 四语言必须使用同一 fixture、相同步骤和视口；测试不得 skipped。
- 正式截图必须显示真实功能和目标状态，不得混入其他语言 fixture、调试页面或无关历史数据。
- 逐图检查可读性、语言、关键状态、遮挡、空白和测试残留。
- 截图、manifest、coverage、对应指南和证据摘要必须同步更新。
- 测试数据仅清理本轮创建且有契约的数据；禁止扩大到历史记录。

只读能力核实要求：

- 先用路由、菜单、页面、命令入口和文档做定向交叉验证。
- 已有真实能力则记录可执行入口和建议证据；不存在则登记 `PRODUCT_GAP`。
- 非页面能力需要调整 coverage 证据类型时，输出 `EVIDENCE_FORMAT_DECISION`，不得自行 exempt、删除 required flow 或临时造 UI。

验收：SRM 目标场景测试 0 skipped、正式图片与 manifest 一一对应、本轮可清理测试资源归零；只读核实项目逐项为 `CLOSED`、`PRODUCT_GAP` 或 `WAITING_DECISION`。

Token 估算：25K～45K；SRM 和只读核实分两批，各批软上限 22K、硬上限 30K。

### ZF-3：针对性验证

目标：对本批实际改动给出一次可复现验证，不重复运行与本批无关的全量矩阵。

实施：

1. 每个 i18n 批次只运行规定的四类文档门禁。
2. SRM 截图稳定后，只运行实际修改的 Playwright 套件及 screenshot strict 检查。
3. 所有代码/文档修改稳定后，统一运行一次 frontend build、lint 和适用的 docs gates。
4. 只有本批实际修改后端代码时才执行 JDK 25 + Maven Wrapper backend `clean install`；纯文档/截图改动不得为形式完整重复跑后端全量构建。
5. 保存命令、退出码、通过/失败数和最多 50 行的失败摘要；完整日志放排除目录，不进入 checkpoint。

验收：所有本批应通过项通过；预期未关闭项按真实退出码和 gap 数量登记；没有把部分门禁汇总成全局 PASS。

Token 估算：8K～15K；软上限 12K，硬上限 18K。

### ZF-4：提交、交接和停止

目标：让下一轮验收可以仅根据提交、差异和证据复核，不需要重做本轮调查。

实施：

1. 每个稳定批次使用精确路径 stage，禁止 `git add .`。
2. 提交前运行 staged 文件清单、`git diff --cached --check` 和敏感信息扫描。
3. 使用语义清楚的小提交；不得混入排除项或半成品。
4. push 前只执行一次 fast-forward/remote SHA 核验；出现分叉立即输出 `REMOTE_DIVERGENCE_STOP`，禁止 rebase、merge 或 force push。
5. 更新 current state 和 Flash 批次验收摘要，记录提交 SHA、验证结果、遗留项与下一恢复入口。
6. 输出最终状态后停止，不自动进入强模型任务，不等待轮询。

Token 估算：4K～8K；软上限 6K，硬上限 10K。

## 4. 明确排除并留给更强模型的任务

以下任务本批禁止执行，即使上下文或调用额度仍充足：

1. tenant=1、ids=1..13 的采购 bootstrap 数据恢复及任何数据库历史修复。
2. 新增或修改 Liquibase changeSet、schema、seed adoption baseline。
3. procurement comparison/PO/GR 和 asset receipt/ledger/lifecycle 的跨服务 E2E。
4. workflow model lifecycle、failure、countersign、多身份和候选人作用域场景。
5. MQ retry/dead-letter 故障注入、共享 relay/Flowable/数据库隔离实验。
6. 对 G1、G7、WP-10、G8 作关闭判断或执行最终全矩阵。
7. 独立翻译语义签核、填写 `reviewed_at`。
8. 开发 `PRODUCT_GAP`、调整 coverage 门槛或删除 required flow。
9. 清理历史 patch、脚本、debug 图片、`.artifacts/` 或其他 excluded untracked 项。

发现上述需求时写入 `STRONG_MODEL_QUEUE`，包含目标、证据、风险、涉及文件和推荐恢复入口；不要继续实现。

## 5. Token 与防跑偏约束

本批估算总量为 **70K～134K Token**；建议总软上限 **105K**、硬上限 **145K**。若平台不提供可信计数，写 `TOKEN_USAGE=UNKNOWN`，不得编造。

1. 只完整阅读本方案和 ZCode 提示词；上游大文档只按本方案指出的小节读取。
2. 用 `rg`、文件清单、manifest 和 coverage 定向定位；禁止全仓扫描、重新审计架构或重读完整历史。
3. 同一个 Git/Docker/门禁状态在输入没有变化时只检查一次，后续引用已有证据。
4. 每 8～12 个 i18n 源为一批；不要逐文件启动全库工具。
5. 日志默认最多读取失败附近 50 行；只有错误分类确有需要时再追加一次定向读取。
6. 同一失败最多进行 3 轮有新证据的 targeted 修复；无新证据则标记 `BLOCKED`，不得换命令反复碰运气。
7. 长命令正常运行时复用原 session；等待不超过 60 秒一轮，不重启、不并行重复执行。
8. 禁止自动多 Agent；共享 Docker、数据库和 E2E mutations 保持串行。
9. 达到某批软上限前先收敛当前小步；接近硬上限时必须完成数据清理、通过成果提交和 checkpoint，不得为宣布完成而省略验证。
10. Token 额度充足不等于扩大范围；只执行 ZF-0～ZF-4。

## 6. Git、认证和数据安全

- 禁止 `reset`、`restore`、`checkout`、`stash`、`clean`、`rebase`、`merge`、`amend`、force push。
- 禁止 `git add .`；不得删除或提交 excluded untracked 文件。
- 登录只走现有 E2E fixture 和授权身份；禁止 CAPTCHA bypass、Redis Token 查询、打印 Token、读取 `.env.before-rebuild-*`。
- Token 只通过进程环境变量传递；日志和交接不得出现 Token 值或敏感数据库整行。
- 仅清理本轮有 runStamp/业务键登记且有明确契约的数据。
- 文档与代码冲突时以代码、Git 和实际验证结果为准，并记录冲突。

## 7. Flash 批次完成定义

只有同时满足以下条件，才能输出 `FLASH_BATCH=CLOSED`：

1. ZF-0 的 current state 已统一并提交；
2. ZF-1 所有客观 findings 已分类，可修复项完成，独立复核包已生成；
3. ZF-2 的两个 SRM 场景已闭环，三个只读能力核实结论齐全；
4. ZF-3 的适用验证已真实执行并记录退出码；
5. ZF-4 所有通过成果已精确提交并安全推送；
6. 数据、凭证和临时运行进程已按本轮契约收口；
7. 强模型队列和外部输入队列有明确恢复入口。

否则只能输出：

- `FLASH_BATCH=PARTIAL_CHECKPOINT`：已有通过成果已提交，剩余任务有精确恢复入口；
- `FLASH_BATCH=BLOCKED`：同一阻塞已完成最多 3 轮有证据的定向尝试；
- `FLASH_BATCH=ACTION_REQUIRED_MODEL`：触及本方案明确排除的高风险任务；
- `FLASH_BATCH=REMOTE_DIVERGENCE_STOP`：远端分叉且不能 fast-forward。

无论 Flash 批次状态如何，都不得输出 `OVERALL_GOAL=COMPLETE`。完成后停止，等待独立验收。
