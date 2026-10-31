# Wiki 与界面改造 WP 执行手册

配合 `wiki-workspace-ux-goal-prompt.md` 使用。本版为用户 2026-10-19 授权增加模型能力兜底与未完成交接要求的 r5，保留 r4 的全部细化 S，以及单环境、计划只读与额度豁免要求。原有 30 个 WP、每个 WP 五个 S，共 150 个固定步骤的编号、顺序和正文不变。实施端必须逐字保留本版步骤内容，不能添加执行子编号。

本手册是交付给实施端的提示词材料，当前没有执行任何产品改造或验证。需求依据为 [需求草案](wiki-workspace-ux-requirements.md)。

## A. 执行公共协议

### A1. 路径和步骤

- 已写全的 `omni-frontend/...`、`docs/...`、`tools/...`、`scaffold/...` 均相对仓库根目录。前端缩写 `src/...`、`tests/...`、`e2e/...`、`e2e-docs/...` 相对 `omni-frontend/`；`views/...`、`components/...`、`stores/...`、`router/...`、`api/...`、`utils/...`、`constants/...`、`composables/...`、`layout/...` 相对 `omni-frontend/src/`。前端词条为 `omni-frontend/src/locales/{zh-CN,en-US,ja-JP,ko-KR}.ts`，站点词条为 `docs/.vitepress/locales/*.json`；不得混用。每个 S 实际写入清单一律展开为完整仓库相对路径，未知路径先查找，不猜建平行目录。
- 所有代码 WP 开始前读前端规范；API 设计、业务边界不在此次改造授权中。后端只读核对；确实需要扩大范围按主提示词处理。
- 每个 WP 的允许路径是上界；WP-01 固定范围与已有依据，每个 S 开始前才落实该范围内的实际文件、证据和命令。修改前必须列实际文件，不可将目录白名单解释成可以批量改全部文件。发现同一目标还需修改原白名单内的文件时，先追加本 S 清单和原因再编辑，不改原计划；白名单之外才是范围变更。
- 按原编号顺序执行，不重排计划。依赖已满足即可继续，不为常规实施重复请示；Docker 阻塞时仅允许推进不依赖运行证据的 WP-08—11、17—22 的核对/研究/正文，保留原阻塞项。WP-12—16 可先写已核对草稿，实际启动仍不得计为通过；WP-23 的主例须等 WP-07 的真实改动与检查就绪，不能凭设想提前完成。
- 文档前置达到 READY_FOR_REVIEW 时，后续独立写作可继续；正式审校与严格门未过不能判定整个 Goal 完成。UI 的编译/身份边界回归未过不得继续宣称 UI 冻结或制作正式成功教程。
- 相同“验证项＋根因”最多初次尝试加两轮有依据修复；不同根因不混计为整个 S 的次数上限。记录原始退出码，有真实条件变化后才在原 S 恢复并保留历史；禁止靠更名清零、无限重跑、换无关方案、删除用例或放宽断言。
- 本手册、主提示词、需求和计划锁文件均是只读输入。执行前、每个 S 写入前后和上下文恢复时校验锁文件与受保护文件摘要；只允许更新进度，不允许改计划。校验不符立即记 BLOCKED_PLAN_INTEGRITY，不重算锁来接受变更。
- 只允许锁文件中的 150 个 S。禁止加 S、删 S、改名、重排、拆分、合并，或更改原文、白名单、依赖和完成判据；a/b、D1、文章后缀等都不能成为新增执行步骤。对象库存不是步骤清单。
- 修复当前范围回归或指定审校意见时，引用原 S，记录其真实 attempt 和状态，不复制为新 S、不改原步骤正文；这不授权重新排列原计划或加入无关改造。
- 额度充足，不设置模型用量预算，不因省额度缩减工作或提前结束。资源数量、单环境、授权、质量门和实际平台硬限制仍然有效。

锁的固定摘要从用户启动消息逐字读取，不能从磁盘重新计算后假定它可信。每次校验使用以下只读元数据逻辑，失败立即停止产品写入；这不是新增 S，也不运行应用：

```powershell
# 仅把下行替换为用户启动消息已经给出的摘要，禁止取当前磁盘值自证。
$expectedPlanLockSha256 = '<用户启动消息中的64位SHA-256>'
if ($expectedPlanLockSha256 -notmatch '^[0-9a-fA-F]{64}$') { throw 'BLOCKED_PLAN_INTEGRITY: 缺少用户提供的锁摘要' }
$repoRoot = 'C:/WorkSpace/QODER/Omni-Stack'
$lockPath = Join-Path $repoRoot 'docs/design/wiki-workspace-ux-plan-lock.json'
if ((Get-FileHash -LiteralPath $lockPath -Algorithm SHA256).Hash -ne $expectedPlanLockSha256) { throw 'BLOCKED_PLAN_INTEGRITY: 锁文件已变更' }
$planLock = Get-Content -LiteralPath $lockPath -Raw -Encoding utf8 | ConvertFrom-Json
foreach ($entry in $planLock.protected_files) {
    $entryPath = Join-Path $repoRoot $entry.path
    if ((Get-FileHash -LiteralPath $entryPath -Algorithm SHA256).Hash -ne $entry.sha256) { throw "BLOCKED_PLAN_INTEGRITY: $($entry.path)" }
}
$manualText = Get-Content -LiteralPath (Join-Path $repoRoot 'docs/design/wiki-workspace-ux-work-packages.md') -Raw -Encoding utf8
$rows = [regex]::Matches($manualText, '(?m)^\| (WP-\d{2}\.S\d{2}) \|[^\r\n]*')
$wpHeadings = [regex]::Matches($manualText, '(?m)^### (WP-\d{2}) ')
if ($rows.Count -ne 150 -or $wpHeadings.Count -ne 30 -or $planLock.steps.Count -ne 150 -or $planLock.wp_order.Count -ne 30) { throw 'BLOCKED_PLAN_INTEGRITY: 步骤数量异常' }
for ($i = 0; $i -lt 30; $i++) {
    $expectedWp = 'WP-{0:D2}' -f $i
    if ($wpHeadings[$i].Groups[1].Value -ne $expectedWp -or $planLock.wp_order[$i] -ne $expectedWp) { throw 'BLOCKED_PLAN_INTEGRITY: WP顺序已变' }
}
$digestProvider = [System.Security.Cryptography.SHA256]::Create()
try {
    for ($i = 0; $i -lt 150; $i++) {
        $expectedId = 'WP-{0:D2}.S{1:D2}' -f [int][math]::Floor($i / 5), ($i % 5 + 1)
        $cells = $rows[$i].Value.Substring(2, $rows[$i].Value.Length - 4) -split ' \| '
        if ($cells.Count -ne 5 -or @($cells | Where-Object { [string]::IsNullOrWhiteSpace($_) }).Count -ne 0) { throw "BLOCKED_PLAN_INTEGRITY: $expectedId 细化字段缺失" }
        $digest = [BitConverter]::ToString($digestProvider.ComputeHash([Text.Encoding]::UTF8.GetBytes($rows[$i].Value))).Replace('-', '').ToLowerInvariant()
        if ($rows[$i].Groups[1].Value -ne $expectedId -or $planLock.steps[$i].id -ne $expectedId -or $digest -ne $planLock.steps[$i].content_sha256) { throw "BLOCKED_PLAN_INTEGRITY: $expectedId 内容或顺序已变" }
    }
} finally { $digestProvider.Dispose() }
Write-Output 'PLAN_INTEGRITY_PASS: 30 WP / 150 S / protected files unchanged'
```

摘要校验用于发现改动，不等于操作系统禁止写入。宿主若支持受保护文件规则，将这四份输入配置为禁止代理写入；实施模型不得自行改写保护规则、清除只读属性或重置可信摘要。不要把提示词约束宣传成无法绕过的文件权限。

### A2. 固定 S 内的文章与功能记录

多篇文章或多个功能属于同一个固定 S 的处理对象。对象按文章路径或 inventory 中的功能 ID 记录在该 S 的 items，不附加步骤后缀、不另建任务。一个 S 可以分批处理全部对象；完成其全部要求后才进入下一个 S，不能以“循环”为由回头改写步骤或新增工作包。

| 对象核对内容（不是执行步骤） | 应完成的内容 | 完成依据 |
| --- | --- | --- |
| 依据 | 本页问题、目标读者、现有正文、已核对事实及当前源码 | 私有 page-review 有真实输入与范围 |
| 中文 | 中文主线、符合章节类型的截图或必要真实源码 | 本文可独立理解；usage 无代码，原理篇有解释 |
| 复读 | 每个断言、技术字面量、代码片段、图片/语言及失败恢复 | 问题已修正，不以脚本通过代替阅读 |
| 译文 | en-US/ja-JP/ko-KR 的语义与自然表达；不改源码字面量 | 三译文真实存在且逐项读过，待指定审校状态准确 |
| 登记 | source/translation 摘要、章节/模块归属与必要引用，失效的原签核 | 不全表重排、不伪造 approved；历史按规则保留 |
| 验证 | 适用内容、链接、片段、文档检查和预览的输入摘要及证据 | 自动检查与语义发布状态分别记录 |

自然语言语义自检至少回答：读者的问题是否先出现、首次术语是否当场解释、是否需要强制跳出本文、例子是否贯穿、代码是否解释、操作结果与恢复是否明确。不能仅靠篇幅、链接率或关键词数量打分。

各文章和功能先有完整库存，再在其所属 S 中逐项登记产物与状态。例如步骤仍是 WP-21.S02，items 的对象 ID 可以是 redis-cache；对象 ID 不是新增 S。以 WP-20—22 为例，S02 核对全部对象、S03 完成全部中文、S04 完成全部三译文与适用检查、S05 做全集收口；其它 WP 仍以自己的原步骤正文为准。不得用第一个对象的 PASS 覆盖其余对象。

现有站点对未完成审校生成真实待审提示；可用该正常预览能力检查布局。不得新增 skip/allow-draft 开关或删提示来冒充发布通过。

### A3. 证据契约

- 每条证据记录 WP/S、时间、实际输入文件摘要、命令或浏览器动作、环境/镜像身份、预期/实际、退出码、PASS/FAIL/未运行与脱敏结果路径。
- HEAD 相同但文件有变时不能说证据仍适用；至少比对相关文件、配置和运行镜像摘要。只对适用且未变化范围复用既有有效检查。
- 原始日志、令牌、Cookie、真实私有配置与数据库导出不入 Git、不贴报告。Docker inspect/Compose config 可能包含密钥，只抽取所需的挂载、镜像、网络、端口和服务等非敏感字段，不裸存完整输出。正式图片脱敏后才进入 docs/images。
- 旧任务的范围豁免、截图或审校不自动授权本次例外；引用旧图时只能作为旧版本说明，不能充当新界面的本次业务验证。
- 自动化交付、真实业务、截图、语义审校和发布就绪分别记录。`automated-delivery-pass` 且 `release_ready=false` 仍不是完整发布通过。
- 每条检查标明本 S 针对性检查、最终全量门或外部依赖；未到实现阶段的检查记 planned，不冒充失败，也不能拿它阻止负责实现它的 WP。原有失败须有基线依据，不自动扩修无关功能，最终必做门仍不得以“旧问题”为由报 PASS。
- 文档 WP 可同步本次文章对应的 `docs/docs-manifest.yaml` 记录、真实失效状态、由正式脚本派生的 `docs/i18n-review-queue.md` 与必要摘要；这不授权修改审校策略、批量批准或重排整个清单。只有实际指定审校者可产生相应签核。

### A4. 台账目录和字段

固定使用仓库根目录的 `wiki-workspace-ux.records.local/` 存放以下 JSON、脱敏日志和导出的证据；进度使用 `wiki-workspace-ux.progress.local`，先用 git check-ignore 核对两者确被忽略。它们只保存记录，不存项目副本、依赖、构建产物、私有运行配置或业务数据。禁止另找宿主临时目录、仓库外工程或临时工作树。

构建和验证材料仅存固定 Docker 卷 `omni-stack-validation`：`/validation/source` 保存当前源码的构建输入，`/validation/current-preset` 只保存当前一个生成物，`/validation/cache` 保存可复用依赖，`/validation/evidence` 保存原始取证，`/validation/test-fixtures` 承载工具自身必要的测试 fixture。全部位于 Docker 管理的卷，不 bind 到宿主临时目录；不按 run_id/WP/预设新建卷或工程根。工具内部的测试 fixture 不能启动另一个应用环境。切换产物前先保存结果，只替换已核实归属的 current-preset 内容。

| 文件 | 至少包含的内容 |
| --- | --- |
| inventory.json | 每项的稳定 ID、真实路径、类型、模块归属、源码/现有图片依据；预设和 common 全集 |
| environment.json | Docker Desktop endpoint/context、版本、唯一项目 omni-stack、原网络/数据卷身份、标准端口、原有启用服务及 Wiki 状态、正式服务单实例清单、唯一 verifier 名额、验证卷、当前镜像、替换/恢复方式、容器统计和资源原属状态 |
| commands.json | 验证门 ID、具体命令/参数/工作目录/必要环境变量名、输入摘要、能否执行；不存真实密钥值 |
| coverage.json | 需求 ID、功能/文章/场景 ID、适用预设与语言、负责 WP/S、产物、状态、证据路径 |
| route-contract.json | 每个身份和入口的目标 URL、当前/新标签、认证前后行为、拒绝/失败/恢复预期 |
| page-map.json | 原页面、目标章节/路径、模块依赖、四语言路径、保留/重写/拆分/兼容处理、引用迁移 |
| contract.json | WP-01 时的清单快照引用与摘要、锁文件摘要、150 个原步骤引用、需求/允许范围/验收与默认选择、环境身份、已有明确授权；未确定实现细节只引用负责的原 S，不编造命令或重写原步骤 |
| baseline.json | 原 HEAD/差异、验证的真实基线结果、已有失败及其证据；不得补填尚未执行项 |

每条步骤记录复用主提示词的字段。contract.json 在 WP-01.S05 首次形成后只读，引用的初始快照同样保留；当前 inventory/commands/coverage/page-map 等台账可以在原需求和白名单内补全、纠错，每次保存前后差异、依据及负责 S。每个 S 的实际文件清单和结果写进度，不回写初始契约；目录内新增必要文章或组件不等于新增 WP/S。补全映射不得改变数量既定的预设/common、模块闭包、业务功能、验收标准或删除覆盖义务。仅真正扩大范围时请求用户修订原计划，不能用动态台账自行扩权。

### A5. 细化步骤的读取和执行方式

D 节每个 S 固定使用五列：步骤、读取与前置、具体操作与产物、验证与完成判据、边界/失败处理。后四列共同定义同一个 S；“实施要点”“具体核对”是原 S 的操作说明，不是新步骤或新的可选需求。只执行摘要、不看前置和失败列属于未按计划执行。

- 首次理解 A 节与 WP 总表；后续只定位当前 WP 的依赖、输入、允许/禁止范围和当前 S 整行，需要命令时按 G 编号读取 B 节对应段。不得用上下文太长为由缩写、覆盖或重生成本手册。
- 表格中的前端文件缩写按 A1 展开；“本章/全集/全部场景”均指 inventory/page-map/coverage 中归属于当前原 S 的完整适用对象，四语言路径以 manifest 为准，不从语言代码猜文件后缀。引用文件或 API 先用 rg 定位并核实签名，不存在时记录缺口，不捏造调用或新建同名替代模块。
- 写前在进度中保存原 S、读取文件/符号、实际白名单文件、对象列表、预期产物与实际可用命令。新增的必要文件只能落在当前 WP 原白名单中；范围有矛盾就保留现场请求最小修订，不能由实施代理改计划解决。
- 每个对象记录输入摘要、产物位置、已验证的正常/拒绝/失败行为、真实命令或浏览器动作和结果；不适用的场景写明功能/预设依据，不能把未实现或未执行改为不适用。原 S 的全部适用对象处理完才满足全集条件。
- 五预设、十 common、全功能和四语言不得抽样替代；相同代码输入的已通过检查按 A3 复用，变化后仅重验受影响部分。截图按实际任务选择能解释操作的关键状态，可覆盖多个关联动作，但每个功能仍有对应证据，不为每个按钮机械制造重复图片。
- 写操作返回不确定、超时或连接中断，先通过正式读取接口及本次业务键核对是否已生效，再决定是否重试；不得盲目重复创建、审批、收货、触发任务等有副作用的动作。恢复/清理只操作本次登记且归属明确的对象。
- 每个对象和 S 结束保存真实状态；代码写完或表格勾选不等于检查通过。文档自检与正式指定审校分开，待审状态按主提示词记录；只有相应要求真实满足才标 PASS，不得新造“默认通过”“已跳过即完成”等状态。

### A6. 模型能力不足时的未完成交接

当前模型完成合理排查后仍无法提出有依据的修复或验证结果时，使用原 S 的 BLOCKED 状态并如实记录，不伪造根因、修复或 PASS；只有指定审校尚缺时使用 READY_FOR_REVIEW。任务大、未读必要源码或想节省额度不构成能力不足。相同失败的尝试上限和安全/范围停止条件仍按 A1 与主提示词执行。

固定清单为 `wiki-workspace-ux.records.local/unfinished-tasks.md`。每次新增或解除阻塞更新，交付前逐项对照原 150 个 S 的非 PASS 状态及其 items，确保失败、待审、等待外部条件和依赖尚未执行项都被列入。这是原计划的进度交接，不新增工作包或步骤。确无剩余时写明 0 项及最终证据，不省略清单。

| 清单字段 | 必须记录的实际内容 |
| --- | --- |
| 原计划定位 | 原 WP/S、需求 ID、文章/功能/场景对象；当前 TODO/DOING/BLOCKED/READY_FOR_REVIEW 状态，不重新编号为新任务 |
| 完成边界 | 已完成产物及证据；尚未完成或未验证的具体部分；依赖尚未执行须标未尝试 |
| 问题与复现 | 预期/实际、前置条件、身份类别/租户依据、实际命令与 cwd、退出码、脱敏报错或浏览器步骤 |
| 排查与尝试 | 读过的源码/符号、已执行检查、每次修复及结果；未执行的想法单列为建议，不伪装成尝试 |
| 无法继续的原因 | 模型能力/环境/外部输入/授权/依赖的实际阻塞；已确认结论、未知点和证据缺口分别写，不编造根因 |
| 现场与影响 | 文件/差异/输入摘要、运行镜像/环境、脱敏证据位置、当前应用与未完成修改状态、被阻塞的原 S；不附密钥或原始认证材料 |
| 接手入口 | 最小待研究问题、建议先验证的假设、所需外部动作、恢复原 S 的具体入口与验证方法；建议不改变计划或授权 |

需要该问题产物的步骤不得越过依赖；继续手册允许的独立工作。完成所有可独立工作后交付已完成结果与清单，清楚说明整个 Goal 尚未完成。不得为了便于交接擅自提交/推送、发消息、创建会话或启动代理，由用户决定人工或 Codex 接手。清单写完不能令未完成步骤自动变为 PASS。

## B. 验证门与命令

宿主机只发起 Docker 和只读元数据命令，实际构建、测试及浏览器在 Docker 内执行。下面命令中的 npm 任务已对照当前 package.json；`wiki-workspace-ux-verify.mjs` 是 WP-07.S01 必须按本节边界实现的验证入口，当前尚不存在，不能提前声称已经可运行。所有命令仍须经 WP-00.S04 核对并登记。首次按实际需要安装依赖，成功后无依赖变化不重复 npm ci；同一时间仅运行一个验证任务。

### G0 环境与差异

宿主机只读执行 Git 分支/状态/差异、文件摘要、Docker context/info、Compose 版本和资源检查。唯一应用项目 `omni-stack`；端口取标准映射，MySQL 固定 3306，Wiki 4173。原业务网络和数据卷的真实 ID 必须登记，不另建一套；不自动换 Docker context、端口或清卷。

单一辅助容器固定为 `omni-stack-verifier`，固定验证卷为 `omni-stack-validation`。只在确认同名资源不存在或归本任务所有后创建/复用，连接已经核实的 omni-stack 网络，不发布端口，不再启动 compose.tools 的独立工具项目。工具镜像优先沿用 `docker/tools/Dockerfile`（Node22/JDK25），镜像可由宿主调用 `docker build -t omni-stack-tools:local -f docker/tools/Dockerfile .` 构建；构建过程仍由 Docker Desktop 执行。

构建角色只读挂载正式源码到 `/source`，按库存将必要输入同步到卷内 `/validation/source`；排除 .git、*.local 文件与目录、.env 真值文件、node_modules、target、dist 和旧取证，保留公开配置模板。同步基于文件清单与摘要，同时处理已确认的删除和改名，不能让陈旧输入掩盖缺文件或裁剪错误；仅删除已登记且位于固定同步根内的旧输入，不用宽泛清理。依赖和构建输出留在验证卷，不能写回 /source。正式私有配置仍只有原环境的一份，不自动给每个生成物随机初始化另一份配置。

生成物自足检查时先让构建角色退出，再以相同容器名切到交付角色：只挂验证卷的 current-preset 子路径为 `/workspace`，cache/evidence/test-fixtures 分别挂为 `/cache`、`/evidence`、`/test-fixtures`，不暴露 source 子路径、宿主仓库或 Docker socket。核对 Docker 实际支持的 volume 子路径挂载和 inspect 结果；无法满足则阻塞，不使用新卷绕过。浏览器角色也必须复用同一个辅助容器名额，版本按 lockfile，不假定现有工具镜像已包含浏览器。角色切换不得与尚在运行的命令重叠。

Wiki 服务需要时使用正式 compose.docs.yaml 的 wiki 服务，但项目强制为 `omni-stack`、连接同一原网络、4173 固定，仅一个 wiki 容器；不启动其 browser profile，不创建 omni-stack-docs 项目。应用新版本只对正式服务做原位替换，不启动第二套应用；只允许本任务明确受影响的服务，替换前后比对网络、数据卷、端口、镜像摘要及 `docker stats --no-stream`。保留原数据和私有配置，禁止整栈 down/清卷/prune。

构建角色的 TMPDIR/TMP/TEMP 统一指向卷内 `/validation/test-fixtures`，npm/Maven 缓存指向 `/validation/cache`；交付角色使用实际挂载的 `/test-fixtures`、`/cache`，浏览器角色同样只使用该卷的对应已挂载路径。工具需要 Java 临时路径时同步设置 java.io.tmpdir，不能给角色配置不存在的路径后退回系统临时目录。既有单元测试可在固定 fixture 根下生成必要文件；不得启动第二套服务、Testcontainers 基础设施或宿主临时工程。Playwright 固定 workers=1，不加 Maven -T 或同时启动多个构建；记录并遵守 Docker Desktop 已有资源配额，不自行增加其 CPU/内存配置。

应用镜像必须使用本轮 source/current-preset 的真实构建上下文。现有 docker/tools 镜像已包含 Docker CLI：仅构建角色可按需挂载本机 Docker socket，并核对其 daemon ID 与宿主当前 Docker Desktop 完全一致，在卷内真实目录发起构建；私有配置如需读取，只读挂载原文件，不复制成另一份环境。禁止 Docker-in-Docker、远程 daemon 或启动额外构建器。Docker 卷路径不是 Windows 宿主路径，不能把 `/validation/current-preset` 当宿主 docker build 目录或在宿主解包副本。交付与浏览器角色不挂 Docker socket。复用固定镜像标签并登记输入摘要、当前及回退镜像 ID，不按 WP/重试无限保留新标签。

### G1 前端编译与 lint

```powershell
docker exec -w /validation/source/omni-frontend omni-stack-verifier npm ci
docker exec -w /validation/source/omni-frontend omni-stack-verifier npm run build
docker exec -w /validation/source/omni-frontend omni-stack-verifier npm run lint
```

### G2 前端针对性行为、E2E 类型与语言键

```powershell
docker exec -w /validation/source/omni-frontend omni-stack-verifier npm run test:unit
docker exec -w /validation/source/omni-frontend omni-stack-verifier npm run typecheck:e2e
docker exec -w /validation/source/omni-frontend omni-stack-verifier npm run ui:i18n:parity
```

新增 navigation 测试必须接入实际 test:unit 入口，保留已有适用测试集合。生成工程中不能引用已裁掉的 workflow 测试。

### G3 CLI 构建与回归

```powershell
docker exec -w /validation/source/tools/omni-cli omni-stack-verifier npm ci
docker exec -w /validation/source/tools/omni-cli omni-stack-verifier npm test
```

### G4 文档内容与正式质量门

```powershell
docker exec -w /validation/source/tools/omni-cli omni-stack-verifier npm run docs:links:check
docker exec -w /validation/source/tools/omni-cli omni-stack-verifier npm run docs:i18n:content:check
docker exec -w /validation/source/tools/omni-cli omni-stack-verifier npm run docs:screenshots:check
docker exec -w /validation/source/tools/omni-cli omni-stack-verifier npm run docs:readme:check
docker exec -w /validation/source/tools/omni-cli omni-stack-verifier npm run docs:sensitive:check
```

`G4-release` 是指定审校完成后的严格门：

```powershell
docker exec -w /validation/source/tools/omni-cli omni-stack-verifier npm run docs:i18n:check
docker exec -w /validation/source/tools/omni-cli omni-stack-verifier npm run test:quality-gates
```

内容检查不替代严格门。正常返回待审失败要记录真实原因，不将其包装成发布成功。

### G5 文档测试与站点构建

```powershell
docker exec -w /validation/source/docs omni-stack-verifier npm ci
docker exec -w /validation/source/docs omni-stack-verifier npm test
docker exec -w /validation/source/docs omni-stack-verifier npm run build
```

生成站点允许显示真实待审提示，构建成功不证明翻译审校完成。新增文档脚本测试接入现有 docs 的 test 发现规则。

### G6 五预设结构与生成物自身构建（固定产物位）

在 WP-07.S01 实现仅本次验证所需的 `tools/omni-cli/scripts/wiki-workspace-ux-verify.mjs`，不增加产品功能或修改公开 CLI。仅支持 `--mode=structure|build` 和一个真实 `--preset`，只能在 Docker 内操作固定 `/validation/source` 与 `/validation/current-preset`；输出逐项断言、输入摘要、真实退出码和产物摘要。当前参数尚未实现，使用前必须核对实际帮助及测试结果。

该入口调用现有 planPresetGeneration/applyPresetGeneration/validateGeneratedPreset；复用或最小提取 preset-golden.mjs 的模块残留、Maven、Compose、前端、文档、网关及 locale 断言，不删改断言含义。模式 structure 只检查生成结构；模式 build 另从生成物执行 JDK25 Wrapper clean install、frontend npm ci/build/lint，并检查生成物 Compose 解析与标准端口。先核对实际 Docker 运行环境及固定根的真实路径，拒绝符号链接逃逸、宿主路径、非正式预设和越界清理。配置只引用同一正式环境的受控输入，不能随机初始化另一份配置，也不得执行 migrator、种子导入或数据库重置。必要测试覆盖这些拒绝条件及原断言，接入 G3。

四份本次任务输入（本手册、Goal、需求和计划锁）属于开发执行材料，不属于生成项目的用户文档。WP-07 只将这四个精确路径加入生成排除清单，并验证生成物不含它们；不能删除源仓输入、放宽 docs 校验，或让生成器改写拷贝后的计划再留下失效锁。生成内容、导航及引用仍须满足正式交付断言。

例如 core 的结构检查（同一条原 S 内对五个预设依次执行，不增加 S）：

```powershell
docker exec -w /validation/source/tools/omni-cli omni-stack-verifier node scripts/wiki-workspace-ux-verify.mjs --mode=structure --preset=core
```

最终对 core、workflow、crm、supply-chain、full 逐个运行自身构建，保存每轮结果后才复用 current-preset。对应 core 命令为：

```powershell
docker exec -w /validation/source/tools/omni-cli omni-stack-verifier node scripts/wiki-workspace-ux-verify.mjs --mode=build --preset=core
```

旧 test:preset-structure/test:preset-golden 入口会 mkdtemp，旧 preset-delivery-golden 会建额外卷和容器，旧 preset-runtime-golden 会建随机项目和端口；本任务禁止原样运行这些入口。替代的是资源编排方式，原断言与覆盖要求必须保留，不能谎报旧命令已通过。尚未实现兼容入口时记录 BLOCKED_ENV_POLICY，不擅自改计划或用临时工程绕过。

### G7 生成物文档与 AI 资产自足检查（唯一交付角色）

每个预设完成 G6 后，在切换到下一个预设前，按 G0 切换同一个 verifier 为交付角色，只暴露当前生成物及缓存/证据。只使用生成物自带脚本，以下 core 参数逐个替换为五种真实预设名：

```powershell
docker exec -w /workspace/tools/omni-cli omni-stack-verifier npm ci
docker exec -w /workspace omni-stack-verifier node tools/omni-cli/scripts/verify-preset-delivery.mjs core
```

保存 Docker inspect 的真实挂载与来源隔离证据，不能仅相信脚本打印的 source_checkout_mounted 字段。每份分别读取 translation_review_errors、release_ready 和各子命令；自动化通过仍不代表语义发布就绪。不新增 Docker 卷、缓存卷、网络、临时项目或同名之外的验证容器。

严格沿用 verify-preset-delivery 的现有契约：裁剪后译文摘要改变，应有的待审错误须被精确识别，意外错误仍失败；不把非零退出码一概视为“正常待审”。源仓的指定审校与 G4-release 必须通过，生成物的自身交付门也必须通过；本次不要求另外发布五个裁剪项目。生成物 release_ready=false 时保留实际待审原因，未来发布前另做针对其最终文字的真实审校，不能复用失效签核、手改 scaffold.lock 或关闭检查强行变绿。每轮保存该生成输入、输出摘要、各检查结果及待审清单后再覆盖 current-preset，无须并存五份可运行工程。

### G8 浏览器交互与真实截图

依据 `docs/wiki/site-maintenance.md`、`playwright.docs.config.ts`、现有 fixture/helper 和实际 Docker 网络，在 WP-00 登记可用命令与已发现的适配项；真正执行前复核当前 S 的参数。当前 tools/frontend 镜像不是预装浏览器的镜像，不能假定 npx 安装成功等于浏览器已可用。

- Playwright runner 的版本匹配当前 lockfile；它复用 omni-stack-verifier 名额，构建角色退出后才切换，连接同一原应用网络且不发布端口。仅可使用现有锁定工具，不用浮动 latest 升级依赖。
- 在 runner 中设置 `E2E_RUNTIME_MODE=external`、真实标准服务 URL、`E2E_TENANT_ID` 和证据 ID；`E2E_ARTIFACT_DIR`、`E2E_REGISTRY_PATH`、`E2E_DOCS_IMAGE_DIR` 都指向验证卷的 evidence 固定根，不能导向宿主临时目录。浏览器从容器网络访问现有服务；不换宿主端口。
- 先核对浏览器可达地址与应用的公开 origin、issuer、已注册 redirect_uri 和回调地址。容器服务名可达不代表 OAuth 登录可用，容器内 localhost 也不等于宿主。使用同一环境中已验证的寻址方式，并验证浏览器实际 URL 及回调；禁止改 OAuth 注册地址、关闭校验或凭空宣布地址兼容。确实不可达则记录具体阻塞，不能用受控身份截图冒充该登录路径通过。
- 指定当前场景 spec 执行，不一次盲跑所有有真实业务写入的场景。需要设置数据时先确认 fixture 身份、租户、业务键和清理策略。
- 既有 helper 中的旧隔离项目名、旧日期业务前缀和固定租户不得直接套用本次环境，也不能伪装旧项目名来绕过保护。WP-24 仅允许将这些边界适配为已核实的 omni-stack 环境身份、台账中的租户、业务前缀和本次对象注册表，保留错误环境/租户/对象即拒绝的校验；业务数据写入只走正式接口或原有受控 fixture，清理只针对本次登记的对象，不做全表删除或全局重置。
- `display-fixtures` 仅能作为模拟展示，不能满足真实登录/业务链路要求。普通浏览器检查不得触发宿主机 webServer。
- 业务场景可复用已核验的真实会话，或既有测试 classpath fixture 产生的受控测试身份访问真实后端；逐条记录身份来源。真实登录、注册验证码和第三方授权本身仍须真实完成，不能以令牌 fixture 代替。fixture 初始化、浏览器和构建顺序复用唯一辅助容器；短期凭据留在验证卷私有证据区，不复制进正式图片或报告。
- 桌面与窄屏、四语言、真实响应的 loading/empty/error/retry、无权、深链接、返回和多标签会话分别有适用用例。

### G9 源码片段与可读性

使用 G5 内实际 source-excerpts/风格测试验证代码原文与 usage 无代码边界，另逐页做真实语义复读。上游摘录记录实际版本与来源；只截必要材料，并附出处及适用许可说明。

### G10 AI adapter 与预设派生文档

```powershell
docker exec -w /validation/source/tools/omni-cli omni-stack-verifier node ../../ai/adapters/bootstrap.mjs --tool=codex --check
docker exec -w /validation/source/tools/omni-cli omni-stack-verifier node ../../ai/adapters/bootstrap.mjs --tool=qoder --check
docker exec -w /validation/source/tools/omni-cli omni-stack-verifier npm run docs:preset:check
```

规范源确有变化时先只读预检，再按正式 --apply 路径派生，保护用户修改；不能直接编辑派生 SKILL.md。构建副本中的正式派生结果须核对差异后按已批准文件清单写回仓库，不能整树覆盖或只改副本就宣称源仓已同步。docs:preset 派生源须与本次页面映射及实际 catalog 同步。

### G11 每种预设的两条真实启动路径（同环境顺序替换）

对 core、workflow、crm、supply-chain、full 各自建立 source/generated 两行记录：

1. source：使用正式 CLI 解析当前预设服务清单，核对标准端口、原网络/数据卷和同一私有配置；只在 omni-stack 项目内按该子集原位重建/替换应用。不得直接执行会 down/清数据/重建基础设施的 CLI 操作。先以 config/plan 核对实际行为，确认镜像由本轮 source 输入构建，再执行已登记命令。
2. generated：在验证卷的 current-preset 固定位置按 G6 实际生成，用其自身文件由 G0 构建角色连接同一本机 daemon 构建应用镜像，完成 G7 自足检查；由唯一环境的原正式编排引用这些镜像，不从生成目录另起项目。保存原位替换前的服务镜像 ID 和本轮构建 ID，核对配置后使用明确服务清单及 up 的 --no-build --no-deps（或核实具有同等限制的正式入口），防止偷偷从源仓重建、启动 migrator 或附带依赖。容器实际 Image 必须匹配本轮记录；仅 docker ps 显示 healthy 不足以证明测到了新版本。
3. 每条记录配置依据、实际输入/镜像摘要、构建、迁移状态、健康、首次访问/登录、停止和恢复。原数据库的迁移记录仅只读核对；不执行任何生成物的 migrator、初始化或裁剪种子导入，不触发应用自动迁移写入新 changeSet。禁止新建库、重放迁移或清空数据。需未具备的库结构时报告阻塞，不自改 schema。共享数据上的替换通过不证明“空库首次安装”，教程区分首次安装的配置说明与本次实际验证范围。
4. 五个预设逐个执行；每个 source/generated 案例共用同一环境，保存结果后才切到下一案例。停用本轮不包含的、已登记归属的业务应用时保留数据与基础设施；需要的正式服务最多一个实例，无关服务不触碰。后续截图需要业务模块时，按同样规则恢复原位服务，不增加环境。当前预设名、实际在线服务和菜单暴露范围必须对应，不用 full 在线服务冒充 core 子集通过。
5. 切换前保存容器/镜像/网络/数据/端口快照和原位恢复命令；替换失败时以记录的旧镜像在同一服务位置恢复，不起旁路服务。全部验证后使用本次新版本恢复 WP-00 登记的启用服务集合及原有 Wiki 状态，停止本次临时启用而基线未启用的应用，核对健康。保留当前及必要回退镜像，不积累每次构建标签；只清理确认归本任务且已不使用的工具材料，不全局 prune。资源不满足时记录 BLOCKED_ENV_POLICY，不换端口或另起堆栈。

### G12 按需后端验证

本任务不改后端业务代码。若经用户后续明确授权确需改后端，先读后端规范，写明新增范围，再执行：

```powershell
docker exec -w /validation/source/omni-backend omni-stack-verifier ./mvnw clean install
```

未发生该类改动不反复运行源仓 Maven；G6 中生成物自己的后端构建仍照常验证。

## C. WP 总表

| WP | 工作包 | 需求 |
| --- | --- | --- |
| WP-00 | 建立基线、盘点与验证环境 | 全部需求的执行前置 |
| WP-01 | 冻结需求、路由、文档迁移与覆盖契约 | UI-01—10；WIKI-01—09；事实审计；九项补充建议 |
| WP-02 | 统一应用导航数据与路由解析 | UI-05、06、08、10 |
| WP-03 | 拆分官网、工作台与现有个人业务页面 | UI-01、02、09 |
| WP-04 | 统一顶栏、全局应用抽屉和模块侧栏 | UI-03、04、05 |
| WP-05 | 工作台概览、收藏与最近访问 | UI-02、06、09 |
| WP-06 | 登录回跳、跨标签同步与菜单请求失效保护 | UI-07、08 |
| WP-07 | 界面链路、裁剪适配与界面冻结 | UI-01—10 |
| WP-08 | 全量事实审计与可独立完成的精简 | WIKI-02；事实文档专项审计 |
| WP-09 | 九章站点导航、文章模板与源码片段检查 | 九章展示；文内说明；四语言与生成物一致 |
| WP-10 | 重写项目介绍与阅读入口 | WIKI-01 |
| WP-11 | Harness 思想、最新实践与事实维护教学 | WIKI-02 |
| WP-12 | core 预设独立构建启动教程 | WIKI-04：core |
| WP-13 | workflow 预设独立构建启动教程 | WIKI-04：workflow |
| WP-14 | crm 预设独立构建启动教程 | WIKI-04：crm |
| WP-15 | supply-chain 预设独立构建启动教程 | WIKI-04：supply-chain |
| WP-16 | full 预设独立构建启动教程 | WIKI-04：full |
| WP-17 | 认证授权原理与全部实际登录流程 | WIKI-05 |
| WP-18 | 多租户原理与实现教学 | WIKI-06 |
| WP-19 | 工作流原理与实现教学 | WIKI-07 |
| WP-20 | common-core 与 common 的支撑能力教学 | WIKI-08：omni-common-core、omni-common |
| WP-21 | 数据库、两种 Redis 与服务支撑教学 | WIKI-08：omni-common-mybatis、redis、redis-reactive、service |
| WP-22 | 日志、调度、可靠消息与工作流支撑教学 | WIKI-08：omni-common-operlog、job、mqlog、workflow |
| WP-23 | Harness 二次开发完整示范 | WIKI-09 |
| WP-24 | 官网、工作台、系统、任务与审批的图文使用教程 | WIKI-03：公共及平台功能 |
| WP-25 | CRM 全功能图文使用教程 | WIKI-03：CRM |
| WP-26 | SRM 与供应商门户全功能图文教程 | WIKI-03：SRM 与门户 |
| WP-27 | 采购全功能图文使用教程 | WIKI-03：采购 |
| WP-28 | 资产全功能图文使用教程 | WIKI-03：资产 |
| WP-29 | 全量集成、独立预设、指定审校与最终交付 | 全部需求 |

## D. 每个 WP 的 S 步骤

### WP-00 建立基线、盘点与验证环境

- 需求：全部需求的执行前置。
- 依赖：无；本 WP 不修改产品代码。
- 输入：AGENTS、仓库治理、需求草案、三个 package.json、Compose、截图配置和既有验证脚本。
- 允许改动：只写私有进度、契约和证据；其他文件只读。
- 禁止：不得清空工作区、恢复 stash、初始化数据库、运行全量预设或重写首页。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-00.S01 | 四份计划输入、用户消息锁摘要；Git 当前分支与状态。 | 先核对用户启动消息中的锁文件 SHA-256、受保护文件和 150 个 S，再检查路径、分支、HEAD、tracked/untracked 差异，登记已有文件并保存基线摘要。 实施要点：先按 A1 验锁；再读取 branch/HEAD/status 和 tracked、untracked 文件清单，记录每个已有改动的路径、摘要与归属；只写基线记录。 | 计划完整性通过且分支为 codex/wiki-workspace-ux；每项原有差异有归属，不把未提交文档当成待清理垃圾；校验不符即停止写入。 具体核对：分支匹配；锁通过；四份计划输入按实际 tracked/untracked 状态登记并保留；任何已有源码改动均有登记。 | 锁不符立即 BLOCKED_PLAN_INTEGRITY；分支不符保留现场，不自动切换、stash 或恢复文件。 |
| WP-00.S02 | scaffold/catalog/modules.yaml、五预设、docs-manifest、router/MenuNode、截图两份清单及十个 common 目录。 | 从菜单、路由、页面、模块目录、文档清单、common 源码和截图清单建立库存。记录五种预设、十个 common、所有现有人类读者页面和事实文档。 实施要点：按实际记录建立功能、文章、事实、预设、common 五类库存；每项写稳定 ID、路径、所属模块、现有入口、已有测试/图片和缺口，不凭目录名推断已实现功能。 | inventory.json 含真实路径、类型、模块、现有截图及缺口；不填写臆测数量。 具体核对：对比清单条目与文件发现结果，列出有文件无登记、有登记无文件及无菜单入口；五预设与十 common 名称齐全。 | 缺失来源记 unknown 和待查路径；不通过虚构条目或删除旧覆盖项补齐库存。 |
| WP-00.S03 | 正式 Compose/端口表；当前 Docker context、info、项目标签、非敏感 inspect 与 stats。 | 只读确认本机 Docker Desktop endpoint/context、唯一 omni-stack 项目、原网络/数据卷、标准端口、已有容器和工具版本；登记服务单实例和唯一辅助容器名额。 实施要点：记录 daemon ID、endpoint、原网络/卷 ID、实际启用服务、镜像 ID、端口及 Wiki 开启状态；标注辅助资源已存在与归属，此步不启动或替换服务。 | environment.json 有真实资源 ID、归属和可用依据；没有新增环境、临时工程、网络或端口，环境故障不写成应用缺陷。 具体核对：Compose 标准端口与实际占用逐项对应；每服务实例数明确；所有运行服务能关联唯一项目及原数据卷。 | Docker 不可达或端口被占时登记具体对象；不换 context/端口、不停止无关容器。 |
| WP-00.S04 | G0—G12、docker/tools/Dockerfile、三份 package.json、Playwright 配置；S03 环境记录。 | 核对 G0—G12 和现有脚本，按 G0 配置唯一验证容器与固定验证卷；登记浏览器角色、公开 origin/回调、实际可达 URL、租户、取证及镜像构建/替换/恢复命令。标出负责兼容入口的原 S。 实施要点：逐门登记 cwd、参数、角色、输入和输出路径；先 inspect 归属再按 G0 创建/复用唯一 verifier/验证卷；为原位替换登记明确服务名、镜像及回退命令，新入口仅记 planned。 | 已可执行命令无未解析占位符；WP-07.S01 的新入口记 planned 及实现归属，不因尚未实现而阻止进入 WP-07；只有当前必需依赖不可用才阻塞。禁止运行旧多环境脚本或退回宿主运行应用。 具体核对：验证卷与只读源码挂载正确；工具版本可读；交付角色无源仓/socket；浏览器公开 origin 与回调可达性有单独结论。 | 角色/子路径挂载不可用则阻塞对应验证；未实现入口由 WP-07 负责，不把 planned 当本步失败或已通过。 |
| WP-00.S05 | S01—04 结果；本次可能受影响的已有单元、文档和构建入口。 | 在唯一环境只运行当前受影响范围的针对性基线检查，登记原有失败；创建进度文件并指向 WP-01.S01。 实施要点：只运行已可用且针对本次范围的基线检查；每条保存退出码、输入摘要、是否原有失败；初始化 150 个步骤状态与当前断点，不填未来 PASS。 | baseline.json 保存真实退出码、输入摘要和资源快照；Docker 不可用可继续只读核对，但依赖它的验收仍待执行。 具体核对：基线失败可定位命令和日志；未运行清楚标记；基线与真实 Git/Docker 状态一致。 | 环境检查无法执行时保留该项待验；不得凭旧任务结果补基线，也不启动五预设全量循环。 |

### WP-01 冻结需求、路由、文档迁移与覆盖契约

- 需求：UI-01—10；WIKI-01—09；事实审计；九项补充建议。
- 依赖：WP-00 的库存已形成。
- 输入：需求草案、WP-00 库存、当前 router/access/menu、文档与模块目录。
- 允许改动：只写私有 contract.json、覆盖清单和进度。
- 禁止：不得修改需求或本手册自授权；不得引入新系统、新业务或其他审校工具的授权。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-01.S01 | 需求 UI-01—10、WIKI-01—09、事实审计与补充项；WP-00 库存。 | 将每个 UI/WIKI 编号、事实审计项与补充建议映射到 WP、产物和验收项；建议按需求草案的默认方案执行。 实施要点：为每条需求列目标产物、负责的原 WP/S、必需检查与覆盖对象；同一需求映射多个产物时保留父需求 ID。 | coverage.json 无漏项；一个需求拆成多项时保留其父编号，不能以“后续优化”删除必做项。 具体核对：反向检查每条需求都有产物，每项产物都能追溯需求；没有以“后续”替代的必做事项。 | 无归属项只登记缺口，不临时增加 WP/S；确超原范围才请求用户修订。 |
| WP-01.S02 | router/index.ts、utils/access.ts、LoginForm、callback/device 与现有首页入口。 | 固定官网、工作台、个人任务/审批/已发起/已完成、旧 dashboard、模块与门户的路由和角色落点；列登录回跳与新标签行为表。 实施要点：逐行建立身份×入口行为表：游客、普通内部用户、管理员、供应商、资产本人；写目标 URL、认证前后、新/当前标签、query/hash、刷新、返回、拒绝与失效预期。 | route-contract.json 列每种身份的允许/拒绝/回跳结果，包含查询参数、刷新、返回和已登录访问登录页。 具体核对：官网发起登录回官网；受保护深链回原目标；OAuth 协议请求继续其授权流程；门户和资产本人落点有独立行。 | 未知行为先对照源码标注现状/目标；不通过修改后端角色或协议来统一落点。 |
| WP-01.S03 | docs/docs-manifest.yaml、现有 Wiki/guides/参考页、四语言路径及站点导航。 | 逐页决定原位重写、拆分归入哪章或保留为工程参考；固定九章分组与页面路径。优先原位重写，确需拆页才新增 wiki 子目录。 实施要点：逐页填旧路径→目标章节/文章、四语对应、模块依赖、保留/重写/拆分及旧锚点处理；先确认接收位置再计划迁移，未写正文不登记为已交付。 | page-map.json 覆盖现有面向人的全部页面、旧链接和四语言对应关系；没有无归属页面或占位章节。 具体核对：所有面向人的现有页面都有归属；九章覆盖；旧链接/锚点有目标；没有同一正文多处长期维护。 | 尚未确定文件名保留规划状态，写作前在原白名单落实；不批量移动或删除旧页面。 |
| WP-01.S04 | 功能库存、各预设依赖闭包、common 公共 API/SPI、现有截图覆盖。 | 按功能建立界面截图、五预设双路径启动、原理调用链、common 功能族四份对象覆盖矩阵。所有运行案例均绑定同一环境，确认适用模块和权限。 实施要点：矩阵按实际对象列适用身份、语言、模块、预设、成功与失败场景、负责 S；预设每种分别列 source/generated，common 细到功能族而非只列模块名。 | common 有具体功能；未实现功能不会被加入；矩阵只列处理对象和证据，不能变成新增 S 或第二套运行环境。 具体核对：五预设十个启动格、十 common 及全部功能有归属；不适用项写依赖依据，不用 N/A 逃避缺实现。 | 前置数据/人工条件缺失记 blocker；不为凑矩阵加入未实现业务或第二环境。 |
| WP-01.S05 | S01—04 的实际台账、原计划锁和 WP 白名单；环境基线。 | 固定需求、原 150 个 S 引用、允许范围、验收规则、默认选择和环境身份，保留已核实清单的初始快照并生成只读 contract.json；精确实现路径和命令由负责 S 开始前落实。 实施要点：保存上述清单的初始快照，contract 绑定锁摘要、需求/范围/默认选择及环境身份；计算摘要后保持该基线只读；未来精确文件由原 S 开始前列出。 | 不重写 WP/S，不猜定尚未存在的实现；后续台账修订有原 S 和证据且不扩权。目录归属按真实菜单祖先识别，包含 monitor 下的 base:* 菜单。 具体核对：契约能追溯全部 150 个 S，但不复制改写其正文；当前台账修订有历史，不覆盖初始快照。 | 遗漏实现细节不靠猜测补命令；发现范围矛盾登记到原 S，不修改受保护文件。 |

### WP-02 统一应用导航数据与路由解析

- 需求：UI-05、06、08、10。
- 依赖：WP-01 契约完成。
- 输入：api/menu.ts、stores/permission.ts、constants/menu.ts、SidebarMenu、router、utils/access.ts、catalog。
- 允许改动：omni-frontend/src/utils/application-navigation.ts、src/composables/useApplicationNavigation.ts、src/constants/menu.ts、src/router/index.ts、tests/navigation/application-navigation.test.ts；必要的现有测试入口。
- 禁止：不得改菜单 API/权限种子，不用权限码首段或 MenuNode.path 冒充模块归属/前端 URL。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-02.S01 | api/menu.ts 的 MenuNode；router 的 resolveViewComponent/registerDynamicRoutes；目录祖先及 access 现行规则。 | 写纯函数，将已授权菜单按目录祖先映射为应用、模块菜单和可解析页面；排除无组件、空目录及内部控制台不可见的门户项。 实施要点：在 application-navigation.ts 先建立无请求、无存储副作用的纯转换；递归跟随 DIRECTORY 祖先归属，用实际可解析 MENU 构建叶子，保留稳定 ID 与排序。 | 用固定输入验证嵌套目录、monitor/base 混合权限码、空菜单、未知组件和供应商项。 具体核对：输入含 monitor 目录下 base:operlog、未知组件、空目录、供应商项；输出模块归属正确，输入树未被修改。 | MenuNode.path 是物化路径，不得当 URL；不按权限码首段分模块，不改菜单 API/种子。 |
| WP-02.S02 | menuI18nMap、router 视图覆盖映射、已登记 Wiki 路径和 S01 转换结果。 | 集中解析名称、图标、路由、默认可进入页面和 Wiki 入口；目录无概览时选择真实可访问叶子。 实施要点：为派生对象统一提供稳定应用/模块 ID、标题键、图标、真实 route 目标与 Wiki 入口；目录入口选首个真实可用叶子，无叶子不显示可点空卡片。 | 不得生成不存在的 overview、假菜单路径或不可进入的默认卡片。 具体核对：含特殊视图 system:oauth2、base:operlog；router.resolve 得到有效目标；无虚构 overview 和重复身份对象。 | 找不到视图或权限不足就明确不可进入；不通过添加假页面、硬编码授权卡片兜底。 |
| WP-02.S03 | stores/permission.ts 的 menuTree/menuLoadState；S01/S02 纯转换。 | 提供共享的响应式派生入口，消费现有权限状态与实际存在的视图，避免各组件复制菜单过滤。 实施要点：用 useApplicationNavigation 统一派生模块、可搜索叶子和当前模块菜单；由 computed 消费当前已授权菜单及已包含视图，组件只读取结果，不各自再 fetch/filter。 | 同一账号、同一树在卡片、抽屉、搜索和侧栏得到一致集合。 具体核对：替换菜单、清空权限或加载失败后派生集合立即变化；相同输入在卡片、抽屉、搜索、侧栏一致。 | 无菜单不复用旧账号缓存；本步不新增第二份权限 store 或服务器状态。 |
| WP-02.S04 | router 动态注册、清理逻辑、RouteMeta 类型；S02 的稳定模块 ID。 | 为现有业务路由补充模块标识与面包屑依据，保留 URL、名称及退出清理能力。 实施要点：为动态业务路由附模块/面包屑定位信息，集中复用已核对的 route 解析；旧 path/name 和清理入口保持兼容。 | 直达 /admin/base/operlog 时定位运维监控；既有 route name 和动态注册仍可工作。 具体核对：直接刷新 /admin/base/operlog 能归属 monitor；路由注销后不残留模块 metadata；未知地址仍按原 403/404 规则。 | 不为模块侧栏改所有业务 URL；路由注册失败先查映射，不用宽泛 catch 吞错。 |
| WP-02.S05 | 本 WP 实际差异、tests/navigation 与 package.json test:unit 现有入口。 | 补齐针对行为的导航测试，运行 G1/G2；只修本 WP 引入问题。 实施要点：补行为断言并把新测试接入现有 test:unit；保留旧测试；运行适用 G1/G2，记录当前 source 输入摘要和仍需 WP-07 做的裁剪适配。 | 测试包括缺模块、失去权限、同名不同域及实际默认入口；生成物适配事项登记给 WP-07。 具体核对：至少覆盖混合目录、无组件、空树、失权、稳定排序、同名不同模块及默认叶子；已有测试继续被发现。 | 禁止仅测试实现函数被调用；失败只修导航范围，不关闭旧 workflow/base 测试。 |

### WP-03 拆分官网、工作台与现有个人业务页面

- 需求：UI-01、02、09。
- 依赖：WP-02 的导航模型可用。
- 输入：views/home/index.vue 全部脚本/模板/生命周期、个人任务与工作流 API、router。
- 允许改动：views/home/index.vue、views/workspace/**、现有工作台审批组件、router/index.ts、相关四语词条；catalog 的对应资源归属。
- 禁止：不得丢失现有审批/任务/日志功能，不改后端 API，不趁拆分重构业务规则或去掉失败分支。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-03.S01 | views/home/index.vue 的脚本、模板、生命周期及所有 import；api/myJob/workflow/业务审批视图。 | 列出现有首页每个业务函数、弹窗、请求和定时器的迁移目标，先保全所有功能。 实施要点：逐项登记函数/状态/弹窗/定时器→迁入文件与调用者；标注任务、审批、公开介绍和账户操作；先记完整映射，本步不删除原能力。 | 迁移清单覆盖任务 CRUD/启停/触发/日志、待办/发起/已完成、业务审批表单和通知。 具体核对：saveForm/handleTrigger/loadLogs/pollForNewLog/startGlobalPolling、三类审批详情及提交都有目标；每个 timer 有清理责任。 | 任何函数无接收位置先停迁移；不把复杂部分标 obsolete 后丢弃。 |
| WP-03.S02 | S01 任务映射；api/myJob.ts、CronGenerator、DynamicFormRenderer 和现有字典用法。 | 将个人定时任务整段迁入 /workspace/jobs 对应页面，保持既有接口和归属校验语义。 实施要点：迁移任务列表、分页搜索、CRUD、启停触发、动态表单、日志弹窗和通知到 jobs 页面；请求/错误语义沿用，onUnmounted 清除局部与全局轮询。 | 创建、编辑、删除、触发、日志与轮询有对应代码；离页清理定时器，不重复启动。 具体核对：逐项对照原功能；进入/离开两次不叠加 timer；创建校验、失败恢复、日志分页仍有调用链。 | 不改 MyJobController/归属检查；仅移模板而漏脚本或仅保留任务列表均未完成。 |
| WP-03.S03 | S01 审批映射；workflow、采购请购和资产调拨/处置的实际前端 API 与表单。 | 将审批、已发起、已完成及审批弹窗迁到相应工作台子页；按实际模块归属隔离可选 API/表单。 实施要点：迁入待办、已发起、已完成页面及审批详情/提交；将可选模块表单/API 放回其模块归属，核心页不静态导入已裁能力。 | core 缺 workflow/procurement/asset 时不存在静态导入缺失模块的入口。 具体核对：三类详情与通用任务表单都有落点；分页、任务标识校验和提交失败状态保留；core 能移除可选导入。 | 不重写审批状态机或把缺模块分支简单 catch 掉；未支持行为不新增。 |
| WP-03.S04 | 官网公开正文、S02/S03 新页面、route-contract；现有 dashboard 和 portal/asset 守卫。 | 官网保留公开介绍正文与身份相关顶栏；工作台成为独立受保护页面；添加旧 dashboard 兼容落点。 实施要点：首页仅保留公开介绍及身份相关头部，删除已迁出的私有请求/轮询；注册 /workspace 及子页为受保护路由，旧 dashboard 使用兼容入口。 | 登录状态只改变官网账户区；访问工作台仍经过认证；原业务页地址不变。 具体核对：同一正文登录前后不换页；游客进工作台要求认证；旧 dashboard/query 与既有业务深链可达。 | 无权限用户按现行身份规则落点；不得让新布局绕过守卫或在首页偷偷请求私有统计。 |
| WP-03.S05 | S01 功能迁移表、四语言词条、scaffold/catalog/modules.yaml 的 resources。 | 核对迁移清单、更新四语新增词条和新文件归属，运行 G1/G2。 实施要点：逐行确认原能力已接收，补本次新增文案四语及新文件所属模块；运行 G1/G2 并保留尚未浏览器执行的清单。 | 原首页的每个功能有新落点；暂未浏览器验证的项目单列，不写为通过。 具体核对：所有迁移对象有目标与检查；四语键结构一致；没有首页遗留失效 import、重复请求或模块归属空白。 | 编译通过不能替代业务回归；少一个原功能也不能把迁移表标全完成。 |

### WP-04 统一顶栏、全局应用抽屉和模块侧栏

- 需求：UI-03、04、05。
- 依赖：WP-02、WP-03 的编译与单元门通过。
- 输入：layout/index.vue、SidebarMenu、导航派生结果、现有主题和语言组件。
- 允许改动：src/layout/**、components/ConsoleHeader.vue、components/ApplicationDrawer.vue、components/SidebarMenu.vue、相应四语词条和针对性测试。
- 禁止：不得在每个业务页复制布局，不把全局菜单按钮继续绑定为侧栏折叠，不加入未要求的计费/工单组件。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-04.S01 | layout/index.vue、现有语言/主题/退出组件、统一导航派生。 | 建立共享顶栏，放置全局应用按钮、工作台、菜单搜索、Wiki、语言/主题和账户操作。 实施要点：只建一份 ConsoleHeader，由工作台和模块布局共同引用；接入应用按钮、工作台、搜索、Wiki、语言/主题和账户；全局应用按钮只控制抽屉。 | 工作台与任意模块使用相同入口；官网与控制台的导航职责明确。 具体核对：切到任意模块仍有相同全局入口；账户/主题操作沿用现有状态；官网头部职责没有混入模块侧栏。 | 不复制一份独立用户/主题逻辑；不加计费、工单或未实现搜索服务。 |
| WP-04.S02 | S01 顶栏、统一应用集合、Element Plus Drawer 的已有用法。 | 实现应用抽屉的分类、分组和基于同一导航模型的搜索；点击可用应用能打开真实目标。 实施要点：ApplicationDrawer 展示真实分类/分组及搜索结果，搜索命中应用或可访问菜单；显示 loading/empty/error/retry；状态与关闭行为集中管理。 | 空/失败/加载状态有恢复入口；关闭按钮、遮罩、Esc 和焦点返回可用。 具体核对：鼠标关闭、遮罩、Esc、窄屏与焦点返回均可用；无结果与菜单请求失败有不同文案和恢复入口。 | 不为演示写假应用；搜索不得扩大权限；收藏/最近由 WP-05 接入同一数据。 |
| WP-04.S03 | SidebarMenu、router 模块 metadata、共享派生及现有侧栏折叠状态。 | 侧栏只接收当前模块的菜单，提供独立收起按钮和当前选中项；面包屑随真实路由更新。 实施要点：侧栏只接收当前模块子树；独立按钮控制折叠；当前选中项与面包屑从真实路由计算，工作台采用自己的局部导航。 | CRM 不出现采购菜单，深链接刷新也能选中；工作台不保留整棵管理侧栏。 具体核对：CRM 内不出现采购项；直接刷新深链能定位；模块切换、无权限/未知路由不会显示上个模块菜单。 | 不得靠字符串前缀猜目录；不能把应用抽屉按钮复用为折叠侧栏。 |
| WP-04.S04 | route-contract 的模块入口/内部跳转约定及 S02/S03 可解析目标。 | 按默认方案使模块入口使用可解析的同源新标签链接，模块内部使用当前页路由。 实施要点：全局模块入口渲染真实同源 href 的新标签链接并加合适 rel；模块内部 router 跳转留当前标签；返回工作台入口明确。 | 没有把令牌写到 URL；浏览器原生打开链接行为和返回工作台可用。 具体核对：原生 Ctrl/中键/复制链接行为正常；新标签能加载真实路由；地址不携带 token/Cookie 或认证暂存值。 | 不要依赖点击后异步 window.open 导致弹窗拦截；不把所有内部菜单都改新标签。 |
| WP-04.S05 | 本 WP 差异、四语言词条、现有桌面/窄屏断点和 G1/G2。 | 完成桌面/窄屏布局和四语文案，运行 G1/G2。 实施要点：检查长标题、中文/英日韩文案、抽屉滚动与遮罩；补四语和必要布局修复，执行针对性检查，真实交互纳入 WP-07。 | 没有整页横向溢出、遮罩无法关闭、短标签截断关键操作等本次回归；真实浏览器结果交 WP-07。 具体核对：桌面/窄屏无整页横向溢出；关键按钮可见；G1/G2 有实际退出码，不把未运行视觉项标 PASS。 | 只修本次布局问题，不引入新设计系统或批量重写业务页样式。 |

### WP-05 工作台概览、收藏与最近访问

- 需求：UI-02、06、09。
- 依赖：WP-04 组件可用。
- 输入：现有个人/流程统计 API、导航模型、JWT 身份工具和用户状态。
- 允许改动：omni-frontend/src/views/workspace/index.vue、该工作台引用的卡片组件、omni-frontend/src/stores/workspace.ts、omni-frontend/src/components/ApplicationDrawer.vue、omni-frontend/src/router/index.ts 中的访问成功钩子、相关四语词条和 omni-frontend/tests/navigation/**。
- 禁止：不新增统计后端或资源数据库，不生成模拟指标，不把收藏保存成权限授权。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-05.S01 | 工作台新布局、统一导航、现有用户身份与任务/流程统计 API。 | 实现最近访问、收藏、应用目录、待办摘要、账户信息布局；卡片与抽屉使用同一入口集合。 实施要点：排布账户、最近/收藏、应用目录和待办卡片；每个可点项绑定同一个真实目标，统计只显示后端已有数据并声明适用模块。 | 账户信息来自真实状态；不存在的会员/云资源指标不出现。 具体核对：无数据/无权限账号仍有合理空态；账户来自当前身份；所有入口能解析，不显示虚构云资源或会员数据。 | 不要为凑卡片增接口或伪造数字；缺能力时隐藏对应统计并保留说明。 |
| WP-05.S02 | api/myJob.ts 的 getMyJobStats、workflow 的 getWorkspaceStats、当前权限/模块清单。 | 按实际包含模块和当前权限请求已有统计，各卡片独立处理加载、空值与失败。 实施要点：每张卡先判定模块与权限再请求，使用独立 loading/error/empty 状态；失败时只重试该卡，不连带重载所有菜单或工作台。 | 无权限或无对应模块时不发送该业务请求；一个卡片失败不阻塞整个工作台。 具体核对：无 workflow 的 core 不请求工作流；403、网络失败和零值区分；一张失败不清空其他成功卡片。 | 不要把请求异常转成成功的 0；不通过后台新增聚合接口简化前端。 |
| WP-05.S03 | stores/workspace.ts 规划、utils/jwt.ts 的稳定 tenant/user 身份、应用可见集合。 | 以租户和稳定用户标识隔离收藏与最近访问，支持收藏/取消及稳定排序；工作台和应用抽屉读取同一份响应式记录，身份缺失时不落入公共命名空间。 实施要点：只持久化应用/菜单稳定 ID 与必要顺序；键按 tenant+稳定用户隔离；工作台与抽屉共用同一 store；加载后与当前可见集合求交。 | 两处收藏/最近访问一致；账号切换、权限变化后重新过滤可见项，其他用户的偏好不显示；收藏不赋予访问权限。 具体核对：A/B 用户、同名跨租户、失权与损坏 JSON 均不泄漏记录；收藏取消两处同步；身份无效不写公共键。 | 不能用 username 作唯一用户键、保存 token 或让收藏授予权限；不改现有认证存储方案。 |
| WP-05.S04 | router 成功导航钩子、已解析叶子、S03 身份与偏好键。 | 仅在成功访问可用业务路由后登记最近访问，去重并限制为最近十二项；不保存认证回调、令牌、敏感查询或不存在路径。 实施要点：只在成功抵达可访问业务路由后更新最近列表；稳定 ID 去重并截取十二项；认证回调、403/404、敏感 query/hash 不写入持久化。 | 新标签首次打开、刷新和无权访问行为有用例；存储损坏时恢复为空状态。 具体核对：刷新与新标签首访行为正确；失败导航不新增；权限变化后陈旧条目被过滤；原生链接仍可用。 | 不在 beforeEach 发起时抢先记访问；不得记录完整带授权参数的 URL。 |
| WP-05.S05 | 本 WP 偏好/统计差异、导航测试、四语及 G1/G2。 | 完成四语与交互回归，运行 G1/G2。 实施要点：补偏好恢复/取消/排序/身份切换测试和真实统计状态断言；记录读写键、过滤依据及实际检查结果，不在日志输出身份凭据。 | 收藏持久化、最近访问顺序、搜索命中和隔离断言通过。 具体核对：十二项边界、重复项、异常存储、失权和卡片独立失败均有结果；四语新增键齐全。 | 检查失败修当前范围；不删除隔离用例，也不把模拟统计说成真实业务验证。 |

### WP-06 登录回跳、跨标签同步与菜单请求失效保护

- 需求：UI-07、08。
- 依赖：WP-03—05 目标路由已存在。
- 输入：LoginForm、login/callback/device、navigation.ts、stores/user/permission、router 的会话清理和请求错误处理。
- 允许改动：omni-frontend/src/components/LoginForm.vue、omni-frontend/src/views/login/index.vue、omni-frontend/src/views/portal-login/index.vue、omni-frontend/src/views/callback/index.vue、omni-frontend/src/views/device/index.vue、omni-frontend/src/views/device/verify.vue、omni-frontend/src/utils/navigation.ts、omni-frontend/src/utils/session-sync.ts、omni-frontend/src/stores/user.ts、omni-frontend/src/stores/permission.ts、omni-frontend/src/router/index.ts、omni-frontend/src/api/request.ts、omni-frontend/src/main.ts、omni-frontend/tests/navigation/** 与现有身份测试；只改本 WP 所需会话/回跳行为和词条。
- 禁止：不改后端 OAuth state/签名/令牌/权限机制，不删除真实验证码，不把 OAuth2 授权请求重定向成普通工作台跳转。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-06.S01 | utils/navigation.ts safeAppRedirect、route-contract、router 守卫和 access 当前判定。 | 集中安全解析回跳目标，按冻结行为表实现站内目标、默认工作台和门户落点；拒绝外站、协议相对、控制字符和登录循环。 实施要点：集中校验 route query 的原始类型和标准化站内目标，明确默认落点；保留合法 path/query/hash，阻止外域、协议相对、反斜线、控制字符及认证循环。 | 官网发起登录回官网；深链接登录回原业务地址；已有供应商/资产本人规则仍有效。 具体核对：合法 /admin/...?...#... 可回；数组/空值/恶意编码目标不产生跨域跳转；官网、portal、asset 本人落点符合行为表。 | 不改 access 后端含义；不将所有目标统一丢到 workspace；不在多处分叉实现校验。 |
| WP-06.S02 | LoginForm、login/portal-login、callback、device 两页与现有 PKCE/state 暂存。 | 接入密码、社交回调、设备登录的应用回跳；跨外部跳转需要暂存时仅保存经验证的站内目标并一次性消费。 实施要点：按普通应用回跳与 OAuth 协议返回区分分支；只暂存已校验站内目标并成功后一次消费，失败/取消清理；设备验证保留 user_code 和原授权提交流程。 | 既有会话登录→授权端点→回调流程继续完成；拒绝/失败后不留下错误的旧目标。 具体核对：密码成功、社交 callback 成功/拒绝、设备成功/失效及会话→授权端点的链各有对应分支和测试。 | 不得改 state/PKCE/nonce 协议值或把授权端点替换成 workspace；真实外部授权缺条件单列待验。 |
| WP-06.S03 | user store 的 token/username 键、router clearAuthenticatedSession、main.ts 初始化及 request 401 逻辑。 | 在统一初始化处监听跨标签会话变化，复用完整会话清理，重建身份和菜单；避免事件回写循环。 实施要点：在单一初始化处监听跨标签会话事件；先回收旧权限/动态路由/内存偏好，再按新身份恢复菜单与合法落点；事件处理避免再广播同一事件。 | 一个标签退出后其他受保护标签回到合法认证状态，动态路由/权限/偏好同时失效。 具体核对：双标签退出、A→B 换号与重复事件不循环；403 不触发全局登出；清理复用现有统一入口且初始化仅一次。 | 不把 sessionStorage 当可跨标签总线；不另存一份 token 或将 token 放进消息/URL。 |
| WP-06.S04 | permission store 的 loadMenus/performMenuLoad/reset、menuLoadPromise 与身份变化入口。 | 为菜单加载增加会话代次保护，阻止旧租户或旧账号请求在退出/换号后回填；旧 Promise 结束不覆盖新的加载引用。 实施要点：引入会话代次或同等身份戳；请求启动时捕获、成功/失败/finally 写状态前核验；reset 使旧代次失效，旧 finally 只能清理自己拥有的 Promise。 | 模拟 A 请求未完成→退出→B 登录→A 返回，最终状态只能属于 B。 具体核对：用可控延迟复现 A 请求→退出→B 登录→A 成功/失败/finally；B 的菜单、加载状态及 Promise 均不被旧结果覆盖。 | 仅 abort 不足以证明无旧回填；不得把所有异常吞成空菜单或让 reset 遗留动态路由。 |
| WP-06.S05 | S01—04 的行为表、身份/请求竞态测试及 G1/G2/G8 前置。 | 运行 G1/G2 和针对性认证/边界用例；列出真实外部授权仍待执行的项目。 实施要点：逐项运行重定向、事件和延迟请求用例；对密码/协议回调/设备区分单元模拟与真实外部结果，汇总仍缺的人工动作。 | 不能以模拟回调或 URL 测试代替真实第三方正向授权；真实失败和待验状态有独立记录。 具体核对：恶意回跳、旧 Promise finally、双标签失效与不同身份落点都有可复核结果；测试挂入实际入口。 | 无真实验证码/外部账号就保留待验，不伪造 callback 或放宽认证机制完成本步。 |

### WP-07 界面链路、裁剪适配与界面冻结

- 需求：UI-01—10。
- 依赖：WP-02—06 的针对性检查通过；浏览器步骤依赖 Docker。
- 输入：UI 覆盖矩阵、已核实命令、catalog、preset-generator 和现有 E2E。
- 允许改动：scaffold/catalog/modules.yaml 的前端资源归属、tools/omni-cli/src/preset-generator.ts 中对应裁剪与四份计划材料精确排除、相关现有测试、前端导航 E2E/单元入口；tools/omni-cli/scripts/wiki-workspace-ux-verify.mjs、tools/omni-cli/scripts/preset-golden.mjs 必要的纯断言提取及相应测试；前述 WP 的最小回归修复。验证适配仅限 G6 固定路径和单环境，不扩展为通用执行平台。
- 禁止：不改预设模块闭包或增加服务，不关闭测试、不用全选模块掩盖 core 缺模块问题。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-07.S01 | WP-02—06 新文件清单、catalog 资源、preset-generator、preset-golden 断言与 G6。 | 更新新增页面/组件/词条/测试的模块归属和裁剪逻辑，移除旧首页失效假设并从生成物精确排除四份计划输入；按 G6 完成固定 Docker 产物位的最小验证入口及必要测试。 实施要点：逐文件登记所属模块与依赖，适配可选页面/API/词条/测试裁剪；精确排除四份计划输入；实现固定 source/current-preset 的验证入口及非法路径测试，先编译 CLI 再运行结构门。 | G6/core 与 supply-chain 结构先通过；core 无缺模块静态导入；不复制受保护计划及失效锁；入口拒绝越界路径/非法预设，不建容器/网络/卷、不执行迁移或种子，不放宽原断言。 具体核对：core/supply-chain 无残余 import、无伪模块菜单；新导航测试真实被执行；生成物不带任务锁；G3 与针对性 G6 有结果。 | 只提取必要纯断言，不改闭包；验证入口不得启动服务/新卷或跑迁移/种子，不能吞失败。 |
| WP-07.S02 | UI-01—06 对象矩阵；已更新前端镜像/容器 ID；G8 可达性与真实会话前置。 | 执行官网→登录→工作台→抽屉→CRM→内部菜单→返回工作台完整链路；再检查其他目录的侧栏与默认入口。 实施要点：先确认浏览器访问本轮镜像，再按官网→登录→工作台→抽屉→真实模块→模块内部→返回顺序执行；每个可见模块都核对默认叶子、侧栏和搜索入口，保存逐对象证据。 | 所有实际可见应用都有可进入页面；不是只验一张 CRM 截图就宣布全部模块通过。 具体核对：首页登录前后正文一致；模块新标签、内部当前标签；卡片/抽屉/搜索目的地一致；所有实际可见应用有结果。 | 一个模块成功不能代表其他模块；登录或地址阻塞保留真实原因，不换数据/网络/端口。 |
| WP-07.S03 | route-contract 的五类身份、现有接口拒绝语义、会话代次用例和 G8。 | 执行普通用户、管理员、供应商、资产本人、未登录、菜单失败、空菜单、刷新和多标签退出矩阵。 实施要点：在同一环境依次核验各身份可见/不可见/接口拒绝；包含空菜单、请求失败后重试、刷新、无权限深链、双标签退出及换号，不混用不同身份的 storage state。 | 结果逐项符合 route-contract；接口拒绝与前端隐藏分开验证。 具体核对：供应商不越界管理端；资产本人不变成资产管理员；403 与隐藏分别有证据；旧菜单不回填、失效标签不继续操作。 | 不得为通过矩阵改角色/种子或直接给权限；必须的真实登录条件缺失记 BLOCKED。 |
| WP-07.S04 | 四语言 UI 键、现有桌面/窄屏尺寸、G1/G2/G3/G6 已登记命令。 | 执行四语言及桌面/窄屏导航检查，运行 G1/G2/G3 和 G6 的结构门；完整预设构建留在最终集成。 实施要点：四语言分别看顶栏/抽屉/工作台/模块侧栏的长文案和空/错误状态，登记实际 viewport；串行运行适用门，未改变的已通过输入凭摘要复用。 | 文案齐全；构建、lint、导航单元、E2E 类型、生成结构均有真实结果。 具体核对：所有新增键齐全；关键按钮可见；五预设结构按实际闭包通过；当前改动与测试输入匹配。 | 不新增第三套响应式规格或 UI 框架；严禁并发 Playwright/构建来加速。 |
| WP-07.S05 | UI 全矩阵结果、最终源文件摘要、前端镜像 ID、截图候选版本和 environment 记录。 | 记录 UI 文件摘要、镜像身份和截图版本，冻结后续教程所用界面；复核单套应用、服务单实例、唯一辅助名额及原数据/网络/端口。 实施要点：逐需求确认 UI 已验证范围，保存用于后续教程的固定界面摘要与镜像；检查唯一项目、每服务实例数、原网络/卷/端口及 verifier 资源。 | 环境阻塞时记 BLOCKED 并继续手册允许的独立文档工作；截图不冒充成功，无遗留并行验证环境。 具体核对：只有运行及身份边界检查齐全才写 UI 冻结；缺项列精确 WP/S/场景和待处理动作，不生成正式成功教程。 | UI 后续修复要使受影响截图/证据失效并重验，不能继续沿用旧冻结标记。 |

### WP-08 全量事实审计与可独立完成的精简

- 需求：WIKI-02；事实文档专项审计。
- 依赖：WP-00/01 库存；可独立于浏览器环境推进。
- 输入：全部正式事实文档、AGENTS、AI 规范源、当前源码/配置/测试；设计和历史用于辨别类别。
- 允许改动：库存确认的事实文档及三译文、相关清单/摘要/AI 规范源；只通过正式 adapter 派生入口；私有审计记录。
- 禁止：不改业务实现，不把教程式大白话套给事实文档，不改历史 ADR 来追认当前决定，不删关键约束换取短篇幅。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-08.S01 | 文档库存、docs/docs-manifest、repository-governance、AI 规范源及事实/设计/历史入口。 | 逐份识别现行事实、编码约束、教程、设计和历史；每份列当前事实依据与是否需要修改。 实施要点：逐份确定文档类型、维护责任、读者、当前依据和重复位置；列 audit 条目而非先批量改正文，设计快照注明基线不作为当前承诺。 | 审计台账覆盖库存全部项目，保留已有版本证据，不把历史设计快照当当前保证。 具体核对：所有正式事实与 AI 入口均被覆盖；无修改项也有理由；不把原理教程错当不可变事实。 | 找不到源码依据标待核实；不得把历史 ADR 重写成当前事实或漏审未在导航中的事实源。 |
| WP-08.S02 | 当前单份事实正文及其对应源码、配置、测试；本 WP 审计对象全集。 | 每次处理一份文档，对照代码核对边界、契约、权限、租户、事务、幂等、状态机、失败和扩展约束。 实施要点：逐文件按边界/权限/租户/事务/状态机/失败/扩展核对断言，记录 原文主张→实际符号/配置→结论；只核对需要的模块，不全仓漫读。 | 每项修改有路径/符号或测试依据；未知项明确待核实，不凭旧报告确认。 具体核对：每个待改断言有具体依据；可证实/错误/不确定分开；字段类型、关键版本与异常语义有核对。 | 源码与文档冲突先纠正文档；发现后端缺陷只登记，不借审计修改业务代码。 |
| WP-08.S03 | S02 证据、重复条目的唯一维护位置及 page-map 接收文章。 | 精简重复表述和已存在合适教程的教学内容，修正格式与失效路径；尚无接收文章的独有教学段落登记迁出待办，暂保留。 实施要点：逐份删除真正重复表达，修标题/表格/代码围栏/路径；教程段落已有完整接收页才迁出，独有教学未接收时保留原文并登记原位置和目标。 | 规则的唯一维护位置清楚；没有内容先删除、以后再凭记忆补写的情况。 具体核对：关键不变量未减少；转移内容可在目标找到；引用与锚点仍成立；每份修改能说明为何更精简。 | 不按字数目标机械删减，不为了大白话删除 AI 所需的精确术语和约束。 |
| WP-08.S04 | 受影响事实四语言、manifest 对应记录、ai/ 规范源与 adapter 正式入口。 | 按公共文档协议同步实际改动的四语言、源摘要和待审状态；AI 入口只改规范源后预检派生。 实施要点：只同步实际改动条目；更新源/译文摘要并使旧签核正确失效；规范源改变后预检、派生、核对回写实际差异，再运行适用 G4/G5/G10。 | G4/G5/G10 的内容与结构检查有结果，AI 身份和授权策略保持真实。 具体核对：四语言事实一致；派生入口与规范源相符；Qwen 自检仍记待指定审校；无手改派生技能。 | 发现用户自定义 adapter 差异先保护现场；不覆盖全树或伪造 Codex 复核记录。 |
| WP-08.S05 | 全集审计台账、事实差异、迁出待办和各文档检查结果。 | 复核全量台账，给出已精简、无需改动、等待指定 Wiki 接收的逐项结果。 实施要点：逐条标无需改动/已精简/待 Wiki 接收；每个待迁项关联后续原 S 和真实内容位置，交给 WP-29 收口，不在此步虚称全部迁移完成。 | 本 WP 验收的是完整审计和已可完成精简；迁出待办须在 WP-29 清零，不能提前宣称全量收口。 具体核对：库存每份有结论和证据；规则唯一维护源明确；四语言/AI 同步的实际完成范围可复查。 | 不删除未接收段落来清零；仍未知的核心事实保留 blocker 与核对入口。 |

### WP-09 九章站点导航、文章模板与源码片段检查

- 需求：九章展示；文内说明；四语言与生成物一致。
- 依赖：WP-01 页面映射；WP-08 已有事实分类。
- 输入：VitePress 配置、prepare-site、docs-manifest、locale JSON、源码片段和站点既有测试。
- 允许改动：docs/.vitepress/config.mjs、docs/.vitepress/locales/*.json、现有 docs/.vitepress/theme 中必要的阅读布局样式、docs/scripts/prepare-site.mjs 及对应测试、docs/scripts/source-excerpts.test.mjs、必要的轻量风格测试、docs/docs-manifest.yaml；tools/omni-cli/src/documentation-delivery.ts、tools/omni-cli/scripts/preset-docs.mjs 与对应测试仅改章节元数据/页面映射和模块裁剪适配，不改审校授权或断言标准。
- 禁止：不手改 .vitepress/source 或 navigation.json，不换文档引擎，不把未写正文的占位页面放进正式导航，不削弱质量门。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-09.S01 | docs/docs-manifest 的记录结构、prepare-site 及测试；九章 page-map。 | 为人工阅读页面增加可选 wiki_section/wiki_order 元数据，按固定九章生成分组；事实与设计保留工程参考入口。 实施要点：在单一源清单增加向后兼容的章节/顺序元数据；导航生成按九章与工程参考分组，缺新字段的旧记录仍可解析，站点仍只有一份正文源。 | 旧记录未写新字段时仍可解析；章节由源清单生成，三种译文使用同一章节顺序。 具体核对：旧记录兼容、新记录分组正确、四语顺序一致、无重复/空章节的针对性测试通过。 | 不手改生成 navigation/source，不创建第二份导航清单或更换站点引擎。 |
| WP-09.S02 | VitePress 配置/theme/locales、manifest 已存在正文与 page-map。 | 保留当前 VitePress 搜索、本页目录、前后篇导航，更新四语标签与必要阅读样式；只展示四语正文已存在且登记完整的文章，未审校时保留真实提示。 实施要点：保留搜索、前后篇、当前位置与本页目录；仅为实际四语文章加入导航；必要样式控制正文宽度、长代码和截图在窄屏的阅读，保留待审提示。 | G5 的导航/语言/链接测试通过，窄屏正文与截图可阅读；没有手写第二套目录或占位章节。 具体核对：当前文章目录不混进全站章节；四语言标签匹配；root/subpath 入口和窄屏阅读可查；G5 通过。 | 未写正文只留私有规划；不得新增占位页、隐藏待审标记或批量改全站视觉体系。 |
| WP-09.S03 | source-excerpts.test.mjs、原有两篇四语摘录、page-map 中原理页及生成模块投影。 | 将源码片段检查从原有两篇扩展到页面映射标记的全部原理文章及其子目录，保留已有检查；生成物只核对实际保留模块与文章中的源码。 实施要点：递归发现适用原理页，按既有 omni:source 标记比对交付内原文；保留四语与原有测试，上游片段独立记录版本来源而不伪装成本地文件。 | 篡改片段、缺失源文件和误删适用语言的负向用例失败；上游片段有独立版本依据，不要求生成物访问源仓补缺。 具体核对：改错一行原文、删除引用源、漏一份适用语言均应失败；裁剪后不检测已移除模块，但不能访问源仓补缺。 | 不得只查第一篇或跳过难匹配片段；不要把整个源码文件粘进文档充当讲解。 |
| WP-09.S04 | page-map 的 usage 标记、现有 docs 测试发现规则及使用教程禁代码要求。 | 增加针对 usage 文章的轻量检查，禁止代码围栏、源码片段标记和必需源码跳转；对原理篇保留代码能力。 实施要点：用文章类型精确限定轻量检查，拒绝 usage 中的源码/命令/SQL/配置围栏和必需源码跳转；正常按钮名称、业务截图与解释不误伤。 | 同一份 Java 代码放 usage 应失败、放合法原理篇应通过；不能以链接数量或关键词统计冒充可读性审阅。 具体核对：同一 Java 片段放 usage 失败、放合法原理篇可通过；普通站内阅读链接及 UI 按钮文字可保留。 | 自动规则仅检查边界，不能拿关键词计数代替通俗程度或让所有技术章节禁代码。 |
| WP-09.S05 | G4/G5、documentation-delivery.ts、preset-docs.mjs 和文档裁剪现有测试。 | 运行 G4/G5 与受影响 CLI 测试，确认新字段随生成物裁剪正确携带；记录真实待审提示。 实施要点：将章节/页面依赖贯通到生成投影，串行跑适用测试；抽查 core 和带业务预设的四语导航、图片、源码引用与实际保留模块。 | 正式源和生成投影一致；构建预览成功与翻译发布就绪分开记录。 具体核对：源仓与生成物各自能解释导航来源；已裁模块无残余引用；真实待审预览与正式发布状态分开记录。 | 不弱化 manifest/裁剪断言换通过；源与生成不一致时只修本 WP 适配范围。 |

### WP-10 重写项目介绍与阅读入口

- 需求：WIKI-01。
- 依赖：WP-09 元数据可用；其他文档 READY_FOR_REVIEW 不阻止本 WP 写作。
- 输入：实际仓库、README、模块目录、项目结构和现有介绍页。
- 允许改动：page-map 中 introduction 文章及三译文、Wiki 入口、相应清单；四语言 README 仅必要入口同步。确受 README 入口变化影响时，允许最小更新 tools/omni-cli/scripts/docs-quality.mjs、tools/omni-cli/scripts/preset-docs.mjs、tools/omni-cli/src/preset-generator.ts 中对应生成模板及现有测试，保留 README 职责和原质量要求。
- 禁止：不虚构仓库、能力或性能，不增加社群，不把完整教程塞回 README。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-10.S01 | README 四语、实际 Git remote/仓库说明、模块目录和 introduction 页面映射。 | 逐篇列读者问题、当前依据与文章顺序，覆盖定位、仓库用途、目录和阅读路径。 实施要点：为每篇 introduction 列一个读者问题与证据：项目用途、真实仓库关系、主要目录职责、何时进入及四条阅读路径；没有实际相关仓库就不编补仓库列表。 | 介绍清单无虚构相关仓库，读者可辨别先看功能/启动/原理/开发。 具体核对：定位、仓库、结构与阅读入口均有文章/小节接收；描述不依赖宣传或未经测量性能数字。 | 不新增交流圈，不将未实现模块、开源仓库示例或参考截图能力写成本项目现状。 |
| WP-10.S02 | 本章全部文章对象、S01 依据、真实目录与已核对功能。 | 按 A2 的文章核对要求逐篇重写全部中文，目录说明使用职责和使用时机，不仅堆技术缩写；对象记录归本 S。 实施要点：逐篇写中文：先说解决什么问题，再用普通任务解释目录与能力；必要目录表只写职责和使用时机，正文链接只作补充。 | 不打开源码即可理解项目用途及主要目录；全集完成才通过，不新增文章执行步骤。 具体核对：每篇回答预定读者问题；不打开仓库能理解介绍；没有类名/缩写串列替代解释。 | 全章对象未写完保持 DOING；不因一篇首页完成就将 introduction 全集通过。 |
| WP-10.S03 | 本章稳定中文及其三译文路径、统一术语与 manifest 当前签核。 | 翻译三语并逐项自检，更新真实摘要与待审状态。 实施要点：按同一中文逐篇完成三语，自检标题/能力/目录/导航事实与自然表达，技术名称和地址不翻错；更新对应摘要和真实待审状态。 | 译文保留技术事实而使用自然表达，不生成指定工具的假签核。 具体核对：四语文章集合一致；没有残留中文段落或虚构署名；旧证据随输入变化失效。 | 不批量设 synchronized；文案仍有问题先修正文再登记，不用摘要更新代替审校。 |
| WP-10.S04 | 介绍正文、旧入口/README 链接；对应质量脚本与生成模板。 | 登记当前正文到章节导航，核对旧入口及相关 README 链接。 实施要点：仅把正式正文接入九章导航，核对 README 最短入口和旧链接；若入口变化影响质量规则/生成模板，同步最小相关断言，不扩写 README 教程。 | G4/G5 的适用检查通过；README 仍限定位、亮点、最短入口和导航。 具体核对：G4/G5 的适用项通过；生成 README 指向实际保留文档；旧入口与四语链接可达。 | 不能只改 README 留生成器旧模板，也不能削弱 README 职责检查。 |
| WP-10.S05 | 本章四语全集、可读性检查清单、页面/链接证据与待审材料。 | 按可读性清单读完四语内容，登记实际证据与审校交接。 实施要点：按目标读者从头读每篇，逐条记录术语解释、例子、强制跳转、未证实能力及修正；输出待指定审校的文章和当前摘要。 | 本章全部文章可阅读；未完成指定复核时状态为 READY_FOR_REVIEW。 具体核对：全集均有阅读记录和检查结果；文章状态与真实审校一致，READY_FOR_REVIEW 不冒充发布通过。 | 发现事实错误回归本章原 S 修正并失效相关证据，不扩大为业务功能修复。 |

### WP-11 Harness 思想、最新实践与事实维护教学

- 需求：WIKI-02。
- 依赖：WP-08 审计结果、WP-09 站点元数据；研究可独立于 UI 环境推进。
- 输入：当前日期的一手公开资料、项目 ai/AGENTS/治理/验证与实际审计证据。
- 允许改动：page-map 中 harness 文章及三译文、必要的合规外部说明图、相关源清单；私有资料研究表。
- 禁止：不把厂商宣传当实测，不将行业概念写成本项目已实现，不新增研究型运行平台或自动联网任务。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-11.S01 | 事实审计、ai/AGENTS/治理、真实工具及当前日期可访问一手资料。 | 确定 Harness 的入门、组成、行业实践、本项目实践、事实维护和改良方向文章；建立主张—来源表。 实施要点：按入门→组成→行业实践→本项目→事实维护→改良建立文章对象；为每个重要主张列来源、日期、版本、适用条件及当前项目对应证据。 | 每条外部技术主张有实际打开的一手资料及日期；近期实践与较早基础材料分别标明。 具体核对：五个用户问题均有落点；本项目事实和行业材料分栏；无来源主张列待核实。 | 不把论文/厂商宣传直接当项目已验证能力，也不为了来源数加入无关概念。 |
| WP-11.S02 | S01 未核实主张与一手公开材料；现有有限检索约定。 | 有限检索近期资料：优先至少三份、来自至少两个独立组织的一手材料；同一未证实主张三轮查询后停查并降为待验证。 实施要点：有限检索并实际打开优先三份、两个独立组织的材料；比较发布时间与实际事件/版本，记录能支持什么及不能支持什么。 | 不伪造“最新”“最佳”或行业共识；没有证据的主张不得出现在事实断言中。 具体核对：近期实践和基础资料清楚区分；原文不跳出也能被本文解释；每条结论可追溯。 | 同一主张三轮仍无证据就降为待验证/不采用；不伪造最新日期、引用或行业共识。 |
| WP-11.S03 | 一篇 Harness 文章的主张表及本项目真实小任务上下文。 | 每次写一篇中文，用一个小任务贯穿上下文、约束、工具、验证、反馈；正文解释完后再给可选参考资料。 实施要点：先以普通开发问题解释为何需要 Harness，再让同一小任务贯穿上下文、事实、约束、工具、检查和反馈；外部图/短摘录附近说明其证明对象。 | 不依赖外链完成核心理解；外部截图与本项目截图的来源明确。 具体核对：本文能完整解释概念与实践；术语首次出现即解释；外链只作可选参考且来源/日期清楚。 | 不复制大段外文替代讲解，不创建研究运行平台或新增工程功能。 |
| WP-11.S04 | 事实审计逐项结论、当前事实维护流程及本章中文全集。 | 据实际审计讲事实文档怎么写和维护；改进建议单列；完成三语、清单与内容检查。 实施要点：用本项目例子讲事实记录内容、唯一来源、代码变更后维护和失效检查；将建议单列并说明收益/代价/验证方式；同步三语和对应清单。 | 已实现/未验证/建议不混写；不以截图或示意图替代工程证据。 具体核对：已实现/推导/未验证/建议可区分；四语维护规则与现行治理一致；G4/G5 适用项有结果。 | 不能为了讲 Harness 自创强制治理流程或删 Docker、租户、权限等现行约束。 |
| WP-11.S05 | 本章四语全文、主张—来源表、事实维护例子和引用材料登记。 | 阅读全文和来源表，运行适用 G4/G5，提交指定审校材料。 实施要点：逐篇复读并复核引用是否真的支持相邻结论，检查过期“最新”及建议误写现状；完成适用检查与审校交接清单。 | 本章内容覆盖需求的五个问题，保留实际研究日期与引用，状态不冒充发布就绪。 具体核对：Harness 是什么/行业实践/当前工程/事实维护/改良方向均可在文内理解；来源和审校状态真实。 | 内容有证据缺口保留待核实，不以图多或构建通过宣布语义完成。 |

### WP-12 core 预设独立构建启动教程

- 需求：WIKI-04：core。
- 依赖：WP-01 覆盖矩阵；真实启动依赖可用 Docker。
- 输入：scaffold/presets/core.yaml、实际依赖闭包、CLI 帮助/正式指南、现有环境初始化与 Compose。
- 允许改动：page-map 中 startup 总介绍/core 文章及三译文、当前预设派生说明及相关清单；证据仅私有目录。
- 禁止：不修改预设闭包、不添加业务数据、不用别的预设启动记录替代 core。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-12.S01 | scaffold/presets/core.yaml、catalog 依赖、CLI 实际 plan 与当前初始环境记录。 | 从 core 文件计算真实组件和用途，先写预设是什么、收益、资源与复杂度代价；区分源码子集与裁剪项目。 实施要点：核对 base/gateway/mysql/redis/nacos 声明及传递依赖，解释认证、RBAC、组织、字典、日志与基础任务；明确没有被选入的流程/业务模块不应残留。 另写预设总介绍与 source/generated 的区别，解释裁剪收益以及选择、依赖、配置和资源的复杂度成本。 | 正文描述与当前解析结果一致，不把减少组件写成零配置或零成本。 具体核对：列声明模块、解析闭包、实际服务、端口、功能/身份前置及配置来源，全部能对应当前文件；不硬编码未经解析的组件数量。 | 不得改 YAML 闭包、增加服务或把上一预设组件结论照搬到本页。 |
| WP-12.S02 | core 的服务计划、G0/G11、同一原私有配置、当前/回退镜像 ID 和原端口/网络/数据卷。 | 按 G11/core/source 在唯一 omni-stack 环境验证源码子集配置、构建、原位替换、首次访问和停止恢复；保留同一网络、数据及标准端口。 实施要点：只构建并原位替换 core 的明确应用子集，阻止隐式依赖/迁移/种子启动；顺序核对配置→镜像→健康→首次访问/登录→停止恢复，并保存每阶段结果。 | 每阶段有命令、退出码、输入/镜像摘要和成功判据；没有第二套实例或新数据环境。 具体核对：认证、工作台、系统/基础入口和个人任务可见性；确认无流程/CRM/供应链残留入口；容器实际 Image 与本次构建记录一致；无额外项目、卷、网络和端口。 | 原迁移只读核对，不初始化共享数据库；缺首次安装条件如实待验，不清数据模拟空库。 |
| WP-12.S03 | core 正式生成 dry-run、G6/G7/G11 与固定 current-preset；相同环境的编排与恢复命令。 | dry-run 后在验证卷 current-preset 固定位置生成 core，按 G6/G7 核对产物自足，再用其镜像原位替换同一环境对应服务；按 G11/core/generated 验证。 实施要点：保存上一产物结果后在固定位置生成 core，检查其自身 Maven/frontend/文档/AI；切交付角色验自足，再用其真实镜像替换同一服务，核对健康/入口并保留证据。 | 不依赖源仓代码或隐式挂载；无宿主临时工程，无新项目/网络/数据/端口；不把共享库验证说成空库安装。 具体核对：source/generated 有不同输入和镜像证明；交付角色未挂源仓/socket；预设自身的结构/构建/交付结果、待审状态及运行结果各自记录。 | 不从生成目录另起 Compose 项目，不跑生成物 migrator/种子，不让原编排偷偷从源仓重建。 |
| WP-12.S04 | core 双路径实际命令/输出、page-map 本页四语路径及当前部署指南。 | 写成完整独立页面：前提、配置解释、必要命令、预期界面、失败恢复；同步三语和来源摘要。 实施要点：本页按“适合谁→实际组件/代价→前提→配置解释→构建/启动→首次使用→成功判据→停止/恢复”写完整中文和三语；每条必要命令解释作用与预期。 | 读者无需串看多个排错文章才知道下一步；命令来自实际执行结果。 具体核对：重点说明端口占用、Nacos/认证配置、菜单加载失败和任务组件前置，不能说 core 零配置；配置推导、真实共享数据实测及未验证空库安装清楚标注，不抄私有值。 | 不把全教程写成一个链接或一条换 preset 参数的命令；命令、密钥示例与实际验证范围必须真实。 |
| WP-12.S05 | core 本页四语、source/generated 两行证据、适用 G4/G5、最终环境快照。 | 逐条回放教程检查清单，运行适用 G4/G5；准确登记真实启动是否完成。 实施要点：按教程顺序逐条核对前提与结果，补断裂说明和失效链接，完成适用检查与待审交接；恢复该轮约定的原位服务集合，不能留新旧副本。 | 两种路径未验证的部分保持 BLOCKED/待验，不以仅完成 build 写启动通过。 具体核对：两条路径均有可定位结果；本页单独可理解；实际服务/网络/数据/端口与记录一致；未执行项不计 PASS。 | 启动/人工条件缺失保留对应 S 的 BLOCKED；译文待指定审校记 READY_FOR_REVIEW，不能用 build 成功替代运行。 |

### WP-13 workflow 预设独立构建启动教程

- 需求：WIKI-04：workflow。
- 依赖：WP-12 的通用预设解释已就绪；真实启动依赖 Docker。
- 输入：workflow.yaml 及闭包、工作流初始化/模型/任务实际前置、已核实命令。
- 允许改动：page-map 中 workflow 启动文章及三译文、相关清单。
- 禁止：不照抄 core 的组件结论、不为教程修改审批模型或正式种子。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-13.S01 | scaffold/presets/workflow.yaml、catalog 依赖、CLI 实际 plan 与当前初始环境记录。 | 核对 workflow 组件、依赖、资源和首次使用前置，逐项与 core 区别。 实施要点：以 workflow 声明及闭包解释增加的 BPMN、模型发布、审批、待办和实例；逐项对比 core，明确引擎与运行服务前置。 说明与 core 的真实区别，不能只让读者把 preset 参数替换成 workflow。 | 所有新增前置有当前配置依据，不假定存在任意历史流程实例。 具体核对：列声明模块、解析闭包、实际服务、端口、功能/身份前置及配置来源，全部能对应当前文件；不硬编码未经解析的组件数量。 | 不得改 YAML 闭包、增加服务或把上一预设组件结论照搬到本页。 |
| WP-13.S02 | workflow 的服务计划、G0/G11、同一原私有配置、当前/回退镜像 ID 和原端口/网络/数据卷。 | 按 G11/workflow/source 在同一环境原位替换 workflow 需要的应用，检查健康、登录和工作流入口；保留同一网络、数据与标准端口。 实施要点：只构建并原位替换 workflow 的明确应用子集，阻止隐式依赖/迁移/种子启动；顺序核对配置→镜像→健康→首次访问/登录→停止恢复，并保存每阶段结果。 | 启动/首次操作单独记录，候选人/模型前置清楚；没有新增运行环境。 具体核对：工作流服务、模型/定义/实例入口、工作台待办；已有模型与无模型的空态分开记录；容器实际 Image 与本次构建记录一致；无额外项目、卷、网络和端口。 | 原迁移只读核对，不初始化共享数据库；缺首次安装条件如实待验，不清数据模拟空库。 |
| WP-13.S03 | workflow 正式生成 dry-run、G6/G7/G11 与固定 current-preset；相同环境的编排与恢复命令。 | 在验证卷固定产物位生成 workflow，按 G6/G7 验证其自身文件，再按 G11/workflow/generated 顺序替换同一环境；迁移只核对原数据上的状态。 实施要点：保存上一产物结果后在固定位置生成 workflow，检查其自身 Maven/frontend/文档/AI；切交付角色验自足，再用其真实镜像替换同一服务，核对健康/入口并保留证据。 | 无被裁模块导入、缺文档或源仓依赖；无新项目/卷/网络/端口，不清数据、不重复执行旧迁移。 具体核对：source/generated 有不同输入和镜像证明；交付角色未挂源仓/socket；预设自身的结构/构建/交付结果、待审状态及运行结果各自记录。 | 不从生成目录另起 Compose 项目，不跑生成物 migrator/种子，不让原编排偷偷从源仓重建。 |
| WP-13.S04 | workflow 双路径实际命令/输出、page-map 本页四语路径及当前部署指南。 | 写独立自然语言教程，保留必要命令及每步说明、失败恢复，完成三语。 实施要点：本页按“适合谁→实际组件/代价→前提→配置解释→构建/启动→首次使用→成功判据→停止/恢复”写完整中文和三语；每条必要命令解释作用与预期。 | 不是只给一个替换 preset 参数的命令；完整配置和成功标准在本页。 具体核对：重点说明模型未发布、候选人/角色不足、没有任务与服务失败的不同表现，不为教程改模型；配置推导、真实共享数据实测及未验证空库安装清楚标注，不抄私有值。 | 不把全教程写成一个链接或一条换 preset 参数的命令；命令、密钥示例与实际验证范围必须真实。 |
| WP-13.S05 | workflow 本页四语、source/generated 两行证据、适用 G4/G5、最终环境快照。 | 核对实际步骤与四语言、执行适用 G4/G5并交接审校。 实施要点：按教程顺序逐条核对前提与结果，补断裂说明和失效链接，完成适用检查与待审交接；恢复该轮约定的原位服务集合，不能留新旧副本。 | 两种启动路径、检查结果和未验证项各有记录。 具体核对：两条路径均有可定位结果；本页单独可理解；实际服务/网络/数据/端口与记录一致；未执行项不计 PASS。 | 启动/人工条件缺失保留对应 S 的 BLOCKED；译文待指定审校记 READY_FOR_REVIEW，不能用 build 成功替代运行。 |

### WP-14 crm 预设独立构建启动教程

- 需求：WIKI-04：crm。
- 依赖：WP-12 通用预设解释已就绪；真实启动依赖 Docker。
- 输入：crm.yaml、真实依赖、权限/初始数据和启动 CLI。
- 允许改动：page-map 中 crm 启动文章及三译文、相关清单。
- 禁止：不假定 CRM 包含未声明模块、不虚构演示商机或客户。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-14.S01 | scaffold/presets/crm.yaml、catalog 依赖、CLI 实际 plan 与当前初始环境记录。 | 核对 crm 组成、适用角色、资源和首次页面预期，区别基础种子与业务示例。 实施要点：以 crm 声明及闭包解释销售管道与基础平台关系，核对线索/客户/联系人/商机/活动；不把供应链或未包含模块算入。 解释认证/角色种子与客户、线索等业务数据的区别，正文必须可独立完成准备。 | 文案和清单与当前闭包一致。 具体核对：列声明模块、解析闭包、实际服务、端口、功能/身份前置及配置来源，全部能对应当前文件；不硬编码未经解析的组件数量。 | 不得改 YAML 闭包、增加服务或把上一预设组件结论照搬到本页。 |
| WP-14.S02 | crm 的服务计划、G0/G11、同一原私有配置、当前/回退镜像 ID 和原端口/网络/数据卷。 | 按 G11/crm/source 原位替换同一环境的 CRM 子集，检查健康、登录及真实入口；共用原数据、网络和标准端口。 实施要点：只构建并原位替换 crm 的明确应用子集，阻止隐式依赖/迁移/种子启动；顺序核对配置→镜像→健康→首次访问/登录→停止恢复，并保存每阶段结果。 | 能区分健康、菜单可见与业务数据为空；没有第二套 CRM 或基础设施。 具体核对：CRM 默认入口、销售页面及真实权限；空业务数据不视为启动失败，健康也不等于业务闭环通过；容器实际 Image 与本次构建记录一致；无额外项目、卷、网络和端口。 | 原迁移只读核对，不初始化共享数据库；缺首次安装条件如实待验，不清数据模拟空库。 |
| WP-14.S03 | crm 正式生成 dry-run、G6/G7/G11 与固定 current-preset；相同环境的编排与恢复命令。 | 在验证卷固定产物位生成 crm，按 G6/G7 验证后执行 G11/crm/generated，用自身构建产物顺序替换同一服务。 实施要点：保存上一产物结果后在固定位置生成 crm，检查其自身 Maven/frontend/文档/AI；切交付角色验自足，再用其真实镜像替换同一服务，核对健康/入口并保留证据。 | 自身 frontend/文档/配置可用，无被裁入口；不建临时工程或另一套数据环境。 具体核对：source/generated 有不同输入和镜像证明；交付角色未挂源仓/socket；预设自身的结构/构建/交付结果、待审状态及运行结果各自记录。 | 不从生成目录另起 Compose 项目，不跑生成物 migrator/种子，不让原编排偷偷从源仓重建。 |
| WP-14.S04 | crm 双路径实际命令/输出、page-map 本页四语路径及当前部署指南。 | 完成独立中文教程、必要命令解释、错误恢复和三译文。 实施要点：本页按“适合谁→实际组件/代价→前提→配置解释→构建/启动→首次使用→成功判据→停止/恢复”写完整中文和三语；每条必要命令解释作用与预期。 | 首次使用需要的数据与权限说清楚，不让读者手动改数据库状态。 具体核对：重点说明角色/菜单缺失、空业务数据、网关转发和服务注册问题，不能虚构演示客户；配置推导、真实共享数据实测及未验证空库安装清楚标注，不抄私有值。 | 不把全教程写成一个链接或一条换 preset 参数的命令；命令、密钥示例与实际验证范围必须真实。 |
| WP-14.S05 | crm 本页四语、source/generated 两行证据、适用 G4/G5、最终环境快照。 | 按实际结果核对页面并运行适用 G4/G5。 实施要点：按教程顺序逐条核对前提与结果，补断裂说明和失效链接，完成适用检查与待审交接；恢复该轮约定的原位服务集合，不能留新旧副本。 | 两种路径分别有证据；缺业务正向操作时不改写成完成。 具体核对：两条路径均有可定位结果；本页单独可理解；实际服务/网络/数据/端口与记录一致；未执行项不计 PASS。 | 启动/人工条件缺失保留对应 S 的 BLOCKED；译文待指定审校记 READY_FOR_REVIEW，不能用 build 成功替代运行。 |

### WP-15 supply-chain 预设独立构建启动教程

- 需求：WIKI-04：supply-chain。
- 依赖：WP-12 通用预设解释已就绪；真实启动依赖 Docker。
- 输入：supply-chain.yaml、SRM/采购/资产/工作流的真实闭包及初始化规则。
- 允许改动：page-map 中 supply-chain 启动文章及三译文、相关清单。
- 禁止：不绕过供应商入驻、审批或收货来伪造初始成功，不改数据库状态捷径。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-15.S01 | scaffold/presets/supply-chain.yaml、catalog 依赖、CLI 实际 plan 与当前初始环境记录。 | 解释供应链预设适用场景、实际服务依赖、资源消耗和配置复杂度；列首次业务前置。 实施要点：从 asset 声明与闭包核对采购、供应商、资产、工作流和消息/调度依赖；按真实依赖解释为何组件多及数据前置复杂。 完整说明供应链多角色协作和资源成本，不把所有业务准备工作等同于容器启动。 | 说明邀请/角色/审批模型/业务数据的真实要求，不把启动等同全业务已准备。 具体核对：列声明模块、解析闭包、实际服务、端口、功能/身份前置及配置来源，全部能对应当前文件；不硬编码未经解析的组件数量。 | 不得改 YAML 闭包、增加服务或把上一预设组件结论照搬到本页。 |
| WP-15.S02 | supply-chain 的服务计划、G0/G11、同一原私有配置、当前/回退镜像 ID 和原端口/网络/数据卷。 | 按 G11/supply-chain/source 在同一环境原位替换供应链应用，核对迁移状态和入口；基础设施、数据、网络、标准端口保持同一套。 实施要点：只构建并原位替换 supply-chain 的明确应用子集，阻止隐式依赖/迁移/种子启动；顺序核对配置→镜像→健康→首次访问/登录→停止恢复，并保存每阶段结果。 | 服务健康与门户/内部身份分开验证；不因供应链验证复制一套应用。 具体核对：内部与供应商门户身份、供应商/采购/资产/审批入口；邀请和供应商关联前置独立记录；容器实际 Image 与本次构建记录一致；无额外项目、卷、网络和端口。 | 原迁移只读核对，不初始化共享数据库；缺首次安装条件如实待验，不清数据模拟空库。 |
| WP-15.S03 | supply-chain 正式生成 dry-run、G6/G7/G11 与固定 current-preset；相同环境的编排与恢复命令。 | 在验证卷固定产物位生成 supply-chain，按 G6/G7 自身构建与交付检查后执行 G11/generated，顺序替换同一环境。 实施要点：保存上一产物结果后在固定位置生成 supply-chain，检查其自身 Maven/frontend/文档/AI；切交付角色验自足，再用其真实镜像替换同一服务，核对健康/入口并保留证据。 | 业务、文档、AI 不依赖源仓；没有临时项目、新网络、克隆数据或端口偏移。 具体核对：source/generated 有不同输入和镜像证明；交付角色未挂源仓/socket；预设自身的结构/构建/交付结果、待审状态及运行结果各自记录。 | 不从生成目录另起 Compose 项目，不跑生成物 migrator/种子，不让原编排偷偷从源仓重建。 |
| WP-15.S04 | supply-chain 双路径实际命令/输出、page-map 本页四语路径及当前部署指南。 | 写完整独立教程、必要命令解释、常见失败和恢复，完成三语。 实施要点：本页按“适合谁→实际组件/代价→前提→配置解释→构建/启动→首次使用→成功判据→停止/恢复”写完整中文和三语；每条必要命令解释作用与预期。 | 文字说明有先后顺序，不能用一串服务名代替启动教学。 具体核对：重点说明邀请、门户 Saga、审批模型、消息/调度及收货建卡前置；不以直接改库伪造准备完成；配置推导、真实共享数据实测及未验证空库安装清楚标注，不抄私有值。 | 不把全教程写成一个链接或一条换 preset 参数的命令；命令、密钥示例与实际验证范围必须真实。 |
| WP-15.S05 | supply-chain 本页四语、source/generated 两行证据、适用 G4/G5、最终环境快照。 | 核对每个步骤证据，执行适用 G4/G5并交接。 实施要点：按教程顺序逐条核对前提与结果，补断裂说明和失效链接，完成适用检查与待审交接；恢复该轮约定的原位服务集合，不能留新旧副本。 | 身份、环境、数据准备与启动结果均明确；外部人工条件如实待验。 具体核对：两条路径均有可定位结果；本页单独可理解；实际服务/网络/数据/端口与记录一致；未执行项不计 PASS。 | 启动/人工条件缺失保留对应 S 的 BLOCKED；译文待指定审校记 READY_FOR_REVIEW，不能用 build 成功替代运行。 |

### WP-16 full 预设独立构建启动教程

- 需求：WIKI-04：full。
- 依赖：WP-12 通用预设解释已就绪；真实启动依赖 Docker。
- 输入：full.yaml、完整闭包、各组件开关、资源与默认端口。
- 允许改动：page-map 中 full 启动文章及三译文、预设对比总结和相关清单。
- 禁止：不以 full 构建成功替代另外四个预设，不自动换端口或清空持久卷。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-16.S01 | scaffold/presets/full.yaml、catalog 依赖、CLI 实际 plan 与当前初始环境记录。 | 解释完整预设的能力和成本，列真实必选/可选组件及适用对象。 实施要点：从 crm+asset 声明计算完整闭包，列实际必选组件和原有可选组件，说明全部能力的资源/配置成本与适用人群。 写五种预设的选择对照，结论基于实际闭包和代价，不推荐所有读者无条件用 full。 | 对比结论来自当前闭包，不夸大推荐所有人直接使用 full。 具体核对：列声明模块、解析闭包、实际服务、端口、功能/身份前置及配置来源，全部能对应当前文件；不硬编码未经解析的组件数量。 | 不得改 YAML 闭包、增加服务或把上一预设组件结论照搬到本页。 |
| WP-16.S02 | full 的服务计划、G0/G11、同一原私有配置、当前/回退镜像 ID 和原端口/网络/数据卷。 | 按 G11/full/source 在同一 omni-stack 环境原位替换 full 应用，检查服务顺序、健康和首次使用，不增加基础设施副本。 实施要点：只构建并原位替换 full 的明确应用子集，阻止隐式依赖/迁移/种子启动；顺序核对配置→镜像→健康→首次访问/登录→停止恢复，并保存每阶段结果。 | 可选服务未启与必要服务失败区分；无隐藏配置、额外端口或应用副本。 具体核对：完整服务健康及各实际模块入口；可选组件未启与必需服务异常分别记录；容器实际 Image 与本次构建记录一致；无额外项目、卷、网络和端口。 | 原迁移只读核对，不初始化共享数据库；缺首次安装条件如实待验，不清数据模拟空库。 |
| WP-16.S03 | full 正式生成 dry-run、G6/G7/G11 与固定 current-preset；相同环境的编排与恢复命令。 | 在验证卷固定产物位生成 full，按 G6/G7 检查后执行 G11/full/generated，原位替换并验证；本轮结束恢复 WP-00 登记的启用服务集合，后续取证按需原位启用相应服务。 实施要点：保存上一产物结果后在固定位置生成 full，检查其自身 Maven/frontend/文档/AI；切交付角色验自足，再用其真实镜像替换同一服务，核对健康/入口并保留证据。 | 不沿用错误预设记录，不强制常驻 full；原网络/数据/标准端口及每服务单实例均复核通过。 具体核对：source/generated 有不同输入和镜像证明；交付角色未挂源仓/socket；预设自身的结构/构建/交付结果、待审状态及运行结果各自记录。 | 不从生成目录另起 Compose 项目，不跑生成物 migrator/种子，不让原编排偷偷从源仓重建。 |
| WP-16.S04 | full 双路径实际命令/输出、page-map 本页四语路径及当前部署指南。 | 完成独立中文、三译文和五预设选择说明，保留必要命令解释。 实施要点：本页按“适合谁→实际组件/代价→前提→配置解释→构建/启动→首次使用→成功判据→停止/恢复”写完整中文和三语；每条必要命令解释作用与预期。 | 优势和复杂度缺点同时清楚，普通读者能按本页完成准备。 具体核对：重点说明资源不足、同套服务依赖、端口冲突与可选组件状态；不启动未经需要的 Sentinel 等服务；配置推导、真实共享数据实测及未验证空库安装清楚标注，不抄私有值。 | 不把全教程写成一个链接或一条换 preset 参数的命令；命令、密钥示例与实际验证范围必须真实。 |
| WP-16.S05 | full 本页四语、source/generated 两行证据、适用 G4/G5、最终环境快照。 | 汇总五个预设各两条路径的真实矩阵，运行适用 G4/G5。 实施要点：按教程顺序逐条核对前提与结果，补断裂说明和失效链接，完成适用检查与待审交接；恢复该轮约定的原位服务集合，不能留新旧副本。 | 十个格子均有结果；未执行不是成功，不把所有预设写成同一篇链接。 具体核对：两条路径均有可定位结果；本页单独可理解；实际服务/网络/数据/端口与记录一致；未执行项不计 PASS。 | 启动/人工条件缺失保留对应 S 的 BLOCKED；译文待指定审校记 READY_FOR_REVIEW，不能用 build 成功替代运行。 |

### WP-17 认证授权原理与全部实际登录流程

- 需求：WIKI-05。
- 依赖：WP-08 事实核对与 WP-09 片段检查；可独立于正向外部授权研究写作。
- 输入：当前依赖锁定的 Spring Security/Authorization Server 源码、RFC/一手资料、Auth/Gateway/前端与 core-flows。
- 允许改动：page-map 中 auth 原理文章及三译文、版本与摘录说明、相关清单/源码片段测试配置。
- 禁止：不实现新的授权模式或修认证后端，不混用框架版本，不把自有密码登录写成已实现 password grant。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-17.S01 | Auth/Gateway/前端实际入口、core-flows、当前授权配置、依赖解析与 auth 页面映射。 | 固定文章顺序：登录/授权问题→协议历史与区别→上游机制→本项目各路径→失效/退出/失败。盘点全部实际入口。 实施要点：按普通登录→认证/授权区别→历史/协议→框架→项目链路组织文章；每个实际入口列发起者、身份/凭据、关键请求、返回、令牌/会话、失败与恢复。 | 覆盖密码验证码、会话与 PKCE、设备、三种社交、注册、令牌及权限；不存在的机制注明未实现。 具体核对：覆盖密码验证码、会话+PKCE、设备、GitHub/Google/Gitee、注册、令牌/退出/权限；能力不存在时明确未实现而非补实现。 | 不把项目私有密码登录叫 OAuth password grant；不以框架支持列表冒充项目实现清单。 |
| WP-17.S02 | 当前 POM/锁定依赖实际版本、对应上游源码、官方协议历史与安全资料。 | 定位当前依赖的真实上游版本和关键处理器；逐条建立上游机制到本项目配置与入口的映射。 实施要点：从实际解析版本定位授权端点、认证处理器、授权码/设备/社交的关键上游代码；逐链关联本项目配置、过滤链、网关与前端；记录来源、版本/提交、日期和许可。 | 摘录有版本/提交和出处，不用最新网页示例替代项目所用版本。 具体核对：每段上游代码与当前依赖相符，历史机制与当前建议分开；所有项目映射都能指到真实配置/符号。 | 不要拿最新网页的 API 套旧依赖，也不要因为用户说 SAS 就固定使用不匹配的旧版本教程。 |
| WP-17.S03 | 当前一条登录链的上游/项目原文、请求次序、前端回跳和失败分支证据。 | 每次完成一条登录链的自然语言、时序、关键原文和解释，再处理该链失败分支。 实施要点：对全部链逐一写“场景→完整自然语言流程/时序→必要连续源码片段→逐段解释→失败恢复”；先讲身份、会话与授权关系，再解释具体调用。 | 读者不打开源码即可跟完整条路径；代码确为实际原文且技术字面量未被译写。 具体核对：片段来自真实原文且正文能解释每段责任；片段内不插伪造省略代码，分段引用放在独立连续原文中；不跳仓库也能跟完链路。 | 不复制整类堆砌替代解释；不修改底层认证实现来让教程更顺畅。 |
| WP-17.S04 | auth 四语全集、源码标记、上游来源表、manifest 及 G4/G5/G9。 | 按 A2 对全部文章逐篇完成三译文、摘录核对、清单与预览；区分源码推导、受控测试、真实外部实测。 实施要点：三译文逐篇对照中文完整链与失败语义，保留 URL/协议字段/代码字面量；按源代码、受控测试、真实外部授权三种证据标记实际支持程度。 | G4/G5/G9 的适用检查有结果；没有真实外部授权不写实测成功，文章对象不另编号为步骤。 具体核对：所有适用文章/语言及本地摘录均有检查；上游版本有记录；图片/时序与源码一致；真实外部未验不写实测通过。 | 不得把单元模拟、token fixture 或授权 URL 可生成当第三方正向登录成功。 |
| WP-17.S05 | 全部登录链矩阵、四语正文及各证据类型/待审记录。 | 按登录路径矩阵逐项复读并移交指定审校。 实施要点：逐链从发起到退出复读，检查 session 与 token、state/PKCE、验证码一次性、设备轮询/过期、错误/取消/回跳是否讲清；整理审校交接。 | 全部实际登录类型在文章内得到解释，外部验证状态准确，关键失败/恢复未遗漏。 具体核对：每条实际路径都有完整正文、边界与恢复；不存在的续期/授权模式清楚标注；源码引用与交付摘要一致。 | 缺一条链不以总览代替；外部实测阻塞只影响相应证据，不能伪造或删除该类型。 |

### WP-18 多租户原理与实现教学

- 需求：WIKI-06。
- 依赖：WP-08、WP-09；写作不依赖浏览器。
- 输入：当前登录/网关断言、租户上下文、数据库/Redis、消息与调度，以及权限数据范围的真实代码。
- 允许改动：page-map 中 tenancy 文章及三译文、相关清单与源码片段定位。
- 禁止：不改租户插件或声称客户端身份头可信，不将 relay 全租户扫描误判为漏加租户条件而修改。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-18.S01 | 当前租户、组织、用户、角色模型与权限事实；tenancy 文章对象。 | 用两个租户和不同角色的具体例子，列租户/用户/组织/角色及隔离/共享关系。 实施要点：以两个租户、各自用户和不同角色为同一例子，先解释隔离什么、共享什么、租户不等于组织/角色，再画身份与数据关系。 | 例子解释身份关系，不以类名列表开头。 具体核对：读者可区分租户边界、功能权限、行级数据范围；例子各身份能对应实际规则。 | 示例仅用于解释，不新建租户/用户或改正式种子来凑截图。 |
| WP-18.S02 | 实际登录/网关身份校验、TenantLine/上下文、Redis、Outbox 与调度调用。 | 逐段跟踪登录→网关→服务→数据库/缓存→异步消息/任务的真实租户传递，列有意共享的边界。 实施要点：沿同一请求逐段记录 tenant 的可信来源、传播、校验、落库、缓存键与异步传递；列系统共享表、relay 全租户扫描等有意例外。 | 每个结论有代码依据，区分租户隔离、功能权限与行级范围。 具体核对：每段有真实类/方法或配置依据；客户端自报头与网关可信断言分开；异步与 ThreadLocal 生命周期有解释。 | 不把 MQ relay 例外当缺陷修复；不声称所有表、缓存或服务都用同一种隔离机制。 |
| WP-18.S03 | S01/S02 的同一例子、真实连续代码片段与异常处理。 | 写中文流程和必要代码原文，解释清理上下文、显式 tenantId 与异常恢复。 实施要点：按登录→网关→服务→DB/缓存→异步顺序写中文和必要原文，解释 finally 清上下文、显式 tenantId 与失败时不串租户的要求。 | 代码与交付源码匹配，未制造不存在的统一实现。 具体核对：本文可回答身份从哪来/谁校验/数据为何看不到；代码是交付内原文，例子不依赖源码跳转。 | 不改租户插件、线程模型或数据库字段；建议明确未实现，不插伪造统一 API。 |
| WP-18.S04 | tenancy 四语、片段标记、相邻 auth/common 章节及 G4/G5/G9。 | 完成三语、源码片段检查和适用 G4/G5。 实施要点：逐页翻译并核对租户 ID 类型、可信/不可信边界、异常清理和异步语义；更新真实摘要与待审状态，运行适用检查。 | 技术字面量、租户边界与失败语义跨语言保持一致。 具体核对：四语言例子身份关系与共享例外一致；全部适用片段可匹配；没有把建议译成现状。 | 本章不以安全免责声明代替项目事实；不把检查通过写成跨租户实测通过。 |
| WP-18.S05 | 全部 tenancy 文章、传播链与隔离/共享清单。 | 以“甲能否看乙的数据、后台任务为什么例外、二开怎么接入”检查完整性。 实施要点：从甲能否看乙数据、后台任务为何例外、新代码怎样接入三个问题逐项回读，修正断裂解释并交审；未实测项保留证据类型。 | 答案在本文中成立，留真实待审状态和证据。 具体核对：答案可在本文找到并连成完整流程；关键限制、常见误用和恢复明确。 | 不得省略共享表/异步例外以制造更简单但错误的统一结论。 |

### WP-19 工作流原理与实现教学

- 需求：WIKI-07。
- 依赖：WP-08、WP-09；实际流程证据按当前可用环境获取。
- 输入：docs/workflow、Flowable 版本、实际模型/任务/候选人/审批/事件代码和测试。
- 允许改动：page-map 中 workflow 原理文章及三译文、相关清单与片段定位。
- 禁止：不修改模型发布、候选人、会签或业务状态机以方便讲解，不用历史截图证明本次链路。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-19.S01 | 当前 Flowable 版本、workflow 事实、模型/定义/实例/任务页面与文章映射。 | 从普通审批例子解释模型、版本、实例、任务、候选人与结果，固定渐进文章顺序。 实施要点：选一个实际普通审批场景，先讲模型像图纸、发布版本、一次申请和待办的区别，再列各篇从基础到复杂的阅读顺序。 | 读者能区分图纸、一次申请和一次待办，不被术语直接淹没。 具体核对：核心对象、参与人及状态可用场景解释；读者不会把改草稿等同修改已运行实例。 | 不虚构可执行演示模型或把上游概念直接当项目已有菜单。 |
| WP-19.S02 | 实际模型发布、候选解析、任务动作、业务审批 API/事件及现有测试。 | 追踪实际引擎与业务单据的协作，盘点真实支持的会签、委派、加减签、撤回和拒绝。 实施要点：逐项盘点普通/会签/委派/加减签/撤回/拒绝的真实实现和限制，跟踪引擎与业务单据、权限/租户、事务和事件的协作。 | 未实现或有边界的行为明确列出；不把上游全功能等同本项目能力。 具体核对：每项写已支持/有限制/未实现与依据；候选人配置来自 omni:assignment；业务归属不混进 common。 | 不能为文章实现缺少的流程动作，也不改变审批运行服务边界。 |
| WP-19.S03 | 全部 workflow 原理对象、真实发布/校验/任务结果代码及异常路径。 | 逐篇写中文说明、流程图、关键原文和解释，覆盖版本锁、校验、MI_END 与事务/事件失败。 实施要点：逐篇写场景、流程图、连续真实原文和解释；覆盖 model_key、发布锁/校验、候选监听、会签 MI_END、事务/事件失败及恢复。 | 用代码证明关键机制，不靠源码跳转拼接答案。 具体核对：引擎结果与业务状态分别讲清；关键机制有原文支撑；示意图能与代码流程互证。 | 不改流程模型或后台状态配合叙述；不得省略校验/锁使示例看起来更简单。 |
| WP-19.S04 | workflow 四语、当前片段与可用的真实运行/测试证据；G4/G5/G9。 | 完成三语、片段核对和适用 G4/G5；验证运行证据与文章结论的范围。 实施要点：同步各篇三语并检查对象名、任务结果、顺序和失败语义；原理推导、单元、真实审批证据分别标记，更新模块归属/摘要。 | 源码说明、测试与实测分别标识，没有把图画出来当引擎已验证。 具体核对：全部适用篇目与语言有内容；生成 core 时可裁掉 workflow 篇而不留坏链接；代码片段与交付一致。 | 画出流程不等于执行通过；没有真实运行条件就记录未验，不制造成功实例。 |
| WP-19.S05 | workflow 功能矩阵、四语全文、任务动作和恢复说明。 | 按功能矩阵复读并移交指定审校。 实施要点：按基础到复杂完整读一遍，逐项检查候选人、实例版本、多人结果和失败后状态是否能在文内理解；整理待审材料与未验边界。 | 基础到复杂流程逐级递进，边界和恢复可在当前文章理解。 具体核对：真实支持动作全部有归属；复杂篇仍解释前置概念；未支持行为不混进操作教程。 | 缺陷回到对应原 S 修正，不能加新工作流业务或改状态机补文章。 |

### WP-20 common-core 与 common 的支撑能力教学

- 需求：WIKI-08：omni-common-core、omni-common。
- 依赖：WP-08、WP-09。
- 输入：两个模块的公共 API/SPI、自动配置、实际调用与测试，WP-01 功能族库存。
- 允许改动：page-map 中这两个模块的 common 文章及三译文、对应清单和源码定位。
- 禁止：不把每个 getter 当一个新功能，不照名字猜实现，不改 common 源码或设计新抽象。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-20.S01 | omni-common-core、omni-common 的公共类/SPI/自动配置、调用者与测试。 | 逐项确认两个模块提供的实际功能族，给每项分配文章/小节、调用例和验证入口。 实施要点：按解决的问题归类公共响应、异常/校验、工具和自动装配等真实能力；每个功能族列源码入口、调用例、适用服务、文章/小节与检查依据。 | 公共响应、异常/校验、接口或自动配置等实际能力均有归属，不能只列两个模块名。 具体核对：两个模块所有实际功能族都有归属；不是只列模块名，也不把每个 getter 列成新能力。 | 不能凭类名猜功能；没有实际接入例的能力如实说明状态，不新造调用者。 |
| WP-20.S02 | 本 WP 全部功能族及真实调用例、自动配置导入和条件。 | 按库存逐个核对全部功能的问题、真实接入点、底层代码、适用服务与配置；每次只分析一个对象，结果存本 S 的 items。 实施要点：逐个核对怎么接入、依赖什么、配置条件、输入/返回、失败行为与扩展点；将原文位置和核对结论写入本 S 的 items，处理完全集再进入中文步骤。 | 全部对象均有依据，不虚构所有服务都自动获得未启用能力；未完成全集前本 S 不 PASS。 具体核对：每个功能有可复制的真实依据及适用边界；默认装配与条件未满足的情况区分。 | 不得为了凑教程给所有服务加依赖，或把仅存在的类说成运行时必然启用。 |
| WP-20.S03 | 两个模块的全部已核实功能及文内原文/示例；common 页面映射。 | 对全部库存对象逐个按“问题→价值→怎么用→原理→实现→扩展→失败恢复”写中文单元并内嵌原文，不新增步骤。 实施要点：按每功能的“问题/价值→接入用法→完整流程→原理和实现原文→失败恢复→可扩展方向”逐项写中文，先解释再给代码。 | 每个对象有中文正文，普通读者能在文内学到用法；源码篇幅服务于解释。 具体核对：每个对象都有使用与实现解释，不只有 Maven 依赖或源码链接；建议与现有代码分开。 | 不写通用 Java/Spring 百科填篇幅；不实现建议的工具或新抽象。 |
| WP-20.S04 | 本章两个模块全部中文、三语路径、代码标记及 G4/G5/G9。 | 对全部中文单元逐个完成三译文和适用 G4/G5/G9，逐项记录真实结果；保持原 S 编号。 实施要点：逐对象完成三译文和片段/内容/链接/预览检查；记录当前源、译文摘要和真实待审状态，已过检查按输入摘要复用。 | 四语言按同一原文核对；全集处理完才通过，待指定审校不冒充 PASS。 具体核对：四语功能集合一致，技术字面量不翻译；所有对象均有检查而非只抽首项。 | 指定审校未完成不能署名 Codex 或同步签核；不因省额度压缩掉异常/扩展内容。 |
| WP-20.S05 | 两个模块覆盖表、四语全文与真实检查/待审记录。 | 只核对两个模块功能族的全量覆盖并移交指定审校，不新增或重排 S。 实施要点：逐功能复读为何存在、怎么用、底层流程、边界和扩展是否均有答案，合并不必要重复概念但保持各功能可理解；交接审校。 | 无未处理功能；建议与现有实现明确区分，未达标对象仍记在其原 S。 具体核对：全集无遗漏；关键规则与 auth/tenancy 事实一致；每项产物和证据可追溯。 | 不要在收口时新加能力；漏项引用本 WP 原 S 修复，不增加执行子编号。 |

### WP-21 数据库、两种 Redis 与服务支撑教学

- 需求：WIKI-08：omni-common-mybatis、redis、redis-reactive、service。
- 依赖：WP-20 的文章模板已验证。
- 输入：四模块真实源码、自动配置、调用例、测试及已核对事实。
- 允许改动：page-map 中这四模块的 common 文章及三译文、对应清单和片段定位。
- 禁止：不合并阻塞与响应式能力，不给缺失服务自动添加依赖，不修改拦截器顺序或内部身份信任规则。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-21.S01 | mybatis、redis、redis-reactive、service 四模块及公共 API/自动配置/调用者。 | 按 mybatis→redis→redis-reactive→service 的顺序盘点功能族与实际调用，区分共同概念和不同边界。 实施要点：分别列功能族与服务适用范围，区分数据库拦截器、阻塞 Redis、响应式 Redis 和内部服务支撑；每模块列实际调用例与文章位置。 | 每模块均有独立使用入口、原理及适用服务说明。 具体核对：四模块都有用法、原理、失败和扩展内容归属；相同概念复用解释但不同边界不混写。 | 不能合并两种 Redis 接口，或把 service 当作业务微服务。 |
| WP-21.S02 | 四模块全部功能族；MyBatisPlusConfig/DataScope、Redis 条件装配和内部调用配置。 | 对四模块全部库存对象逐个核对真实装配、配置、调用链和错误分支，存本 S 的 items，不另编号。 实施要点：逐对象追踪装配顺序、配置覆盖、租户/数据权限、阻塞/响应式线程边界与异常处理，记录确切符号和调用流程。 | 插件顺序、阻塞/响应式边界、service 职责有源码依据；全部对象核对后才进入下一个 S。 具体核对：拦截器顺序、ThreadLocal finally 清理、Gateway 响应式限制及内部身份信任都能找到依据。 | 不改拦截器顺序/插件或内部信任规则，也不为教程自动给服务加依赖。 |
| WP-21.S03 | 已核对的全部四模块对象和连续原文片段；对应 common 文章。 | 对全部对象逐个写中文单元，回答为什么、怎么用、实现与扩展并附必要原文，分批完成同一 S。 实施要点：按每项实际问题写接入示例、完整中文流程与实现片段，解释配置为什么生效/何时不生效，再单列扩展、常见错误和恢复。 | 每个对象均有项目实际封装的说明，不能用通用 Spring 教程替代。 具体核对：四模块使用者能凭本文理解接入；阻塞与响应式场景有具体对照，不只一句“不要混用”。 | 示例不伪造 API、字段或自动配置；不写与本项目封装无关的大段框架教程。 |
| WP-21.S04 | 四模块中文全集、三译文、原文片段、manifest 与适用 G4/G5/G9。 | 对全部单元逐个完成三语、片段及适用 G4/G5/G9；只更新原 S 下的对象记录。 实施要点：逐对象翻译并核对类型、命名、插件顺序、阻塞边界和异常语义；更新当前摘要、待审状态并运行检查。 | 依赖、名称和失败语义一致，全集有结果且审校状态真实。 具体核对：每个模块的所有对象有四语内容与结果；无源码字面量被译写或已裁模块的残余引用。 | 不能只完成 mybatis 就标四模块完成；不把脚本通过当实际性能/安全实测。 |
| WP-21.S05 | 四模块全覆盖表，与 auth/tenancy/common 基础篇的交叉事实。 | 核对四模块的完整覆盖及基础/认证/租户章节一致性，不新增或改写步骤。 实施要点：逐篇回读接入条件、用法、底层与限制，消除事实矛盾和重复维护；汇总每模块实际完成/待审及证据。 | 四模块及其功能族分别有内容与依据，缺项保留原归属，不靠术语总览敷衍。 具体核对：四模块均可在文内解释；共用概念不迫使读者跳仓库拼答案；边界与事实源一致。 | 修正只在本章及对应原 S；不趁交叉核对重构基础设施实现。 |

### WP-22 日志、调度、可靠消息与工作流支撑教学

- 需求：WIKI-08：omni-common-operlog、job、mqlog、workflow。
- 依赖：WP-20 模板、WP-19 工作流概念已就绪。
- 输入：四模块代码、注解/SPI/自动配置、Outbox relay、调度注册与实际调用。
- 允许改动：page-map 中这四模块的 common 文章及三译文、对应清单和片段定位。
- 禁止：不改变 handler/Bean 名、重试策略、消息租户或审批运行归属，不将 common 能力写成新的业务服务。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-22.S01 | operlog/job/mqlog/workflow 四模块的注解/SPI/配置、实际业务调用和测试。 | 按 operlog→job→mqlog→workflow 列功能族与使用场景，区分安全审计和操作日志。 实施要点：分别建日志、调度、可靠消息、审批支撑功能族清单，注明调用服务和完整接入路径；先解释各工具解决什么问题。 | 包含为何存在、如何接入及调用模块；Auth 不被错误要求引入操作日志。 具体核对：安全审计与操作日志分开，Auth 不被写成使用 OperLog；common-workflow 与 workflow 服务职责分开。 | 不以模块名推断功能全支持；不把公共库写成新增独立服务。 |
| WP-22.S02 | 四模块全部功能；SystemJobRegistry/UserJobHandlerRegistry、Outbox relay、候选/审批支撑实际代码。 | 逐功能跟踪当前装配和失败恢复：双注解、路由名、注册回滚、显式 tenantId、重试/死信和审批支撑等。 实施要点：逐对象跟踪双注解、Bean 名路由、调度注册回滚、显式 tenantId、消息事务/重试/死信和审批支撑；把失败后的真实状态写清。 | 关键约束与当前代码一致，无为讲解方便而省略事务或归属边界。 具体核对：处理器名、双注解、relay 全租户、消息重试与调用事务均有源码依据；外部注册失败恢复不是空泛重试。 | 不修改 handler/Bean 名或重试策略；不错误给 relay 加租户过滤或隐式 ThreadLocal tenant。 |
| WP-22.S03 | 四模块全部已核实对象、实际连续代码与场景/失败材料。 | 对全部库存功能逐个完成中文解释与关键真实代码，给出合理扩展示例而不实现新功能；对象记录仍归本 S。 实施要点：逐项写问题、使用者、接入、完整底层流程、原文解释、失败恢复及可扩展方向；指出何时该用哪个模块而不是全依赖。 | 全集有中文内容，已实现与建议清楚，读者不跳仓库即可跟上。 具体核对：读者能理解 Outbox 为什么与业务同事务、调度两轨如何分工、公共审批如何接入；技术细节均先解释概念。 | 不实现新 broker/调度平台/审批能力；扩展仅作为明确建议，不写成已交付。 |
| WP-22.S04 | 四模块中文全集、三语路径/模块归属及 G4/G5/G9。 | 对全部单元逐个同步三语并运行适用 G4/G5/G9，workflow 文章标记模块归属；保持原步骤集合。 实施要点：对全部对象逐项翻译、核对原文与失败语义；更新清单及真实待审状态，检查 core 裁剪 workflow 等不适用篇后的链接/摘录。 | 全集有真实结果；core 裁掉 workflow 时相关内容/源码引用适当裁剪。 具体核对：四语对象集合一致；共用模块不被误裁；workflow 专属正文和源码引用按实际依赖保留。 | 不得删除难写的功能或把核心页面留空来取得裁剪通过。 |
| WP-22.S05 | 本 WP 四模块与 WP-20/21 的十 common 全集、文章及证据映射。 | 核对四模块并汇总十个 common 的全覆盖矩阵，不把对象循环变成新的执行步骤。 实施要点：按模块→功能族→文章/小节→四语→真实代码/检查逐格核对，逐项说明已完成与待审，回收重复解释但不减少覆盖。 | 十模块及其功能族无漏项，不把文档完成当实际新增功能。 具体核对：十个模块各自及其实际公共能力都有内容；没有用一篇术语总表替代实现与用法。 | 尚未处理对象保留原 S 未完成；不新造功能来填表或把文档完成称功能新增。 |

### WP-23 Harness 二次开发完整示范

- 需求：WIKI-09。
- 依赖：WP-11 Harness 概念、WP-20—22 支撑解释已就绪；主例必须有 WP-07 的真实 UI 改动和对应检查依据。
- 输入：本次已实施的工作台模块入口改造差异、当前 CLI 支持范围、生成教程、事实/权限/迁移规则和唯一环境的验证记录。
- 允许改动：page-map 中 development 文章及三译文和相关清单；复用现有正式工具及本次验证入口，不新增通用教学脚本。生成演示只使用验证卷固定产物位，不增加源仓业务功能。
- 禁止：不向源仓增加示例业务或新服务，不宣称 CRUD 支持任意模块，不另建示例运行环境，不创建实际并行代理或外部任务。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-23.S01 | WP-07 已验证的模块入口真实差异、WP-11/20—22 内容及当前 CLI 支持范围。 | 以本次“为工作台接入模块入口”的真实改动为完整例子，写明需求、范围、事实输入、产物与检查；生成器扩展另按当前支持范围解释。 实施要点：以本次工作台接模块入口为主例列清需求、范围、事实输入、预期文件、权限/模块限制和检查；生成器额外路径只解释当前支持的职责。 | 主例来自实际差异而非新增业务；生成包、validate、integrate 的职责和未实测边界清楚，不混写参数。 具体核对：主例能追溯实际 diff 和证据；generator 的 generate/validate/integrate 用法来自当前帮助，不声称 CRUD 支持任意业务。 | WP-07 证据未就绪只写规划，不假造已完成主例或加示例后端业务。 |
| WP-23.S02 | 真实主例的修改、失败/修复记录和唯一环境检查结果；现有生成工具。 | 从原改动与证据还原分析、实施、检查和反馈，必要的生成器演示只在验证卷 current-preset 固定位置 dry-run/生成/静态核对；不部署新业务。 实施要点：还原需求分析→提供事实→实施→检查→反馈→修正；必要生成演示只在固定 current-preset dry-run/生成/静态核对，结束保留记录而非另一套可运行工程。 | 主例能按唯一环境记录理解和复现；无宿主临时工程，无新服务/新环境，示例和回退对象边界清楚。 具体核对：读者可按同一环境理解/复现；每个声称执行的命令有实际结果；示例生成输出与源仓业务分开。 | 不部署演示服务、不创建临时工作树/第二环境；缺主例证据不得用想象补齐。 |
| WP-23.S03 | 主例真实上下文、事实约束、权限/租户/迁移规则和检查门。 | 用自然语言解释如何给 AI 上下文/约束，逐步处理权限、租户、数据、前后端、验证和反馈。 实施要点：给出读者可改用的需求表达、影响分析、事实选择、文件边界、实施反馈和验证约定；说明什么情况下需要权限/迁移变更，主例未改则明确未发生。 | 不是只有一个“帮我开发”的大提示词，失败后的下一步也写明。 具体核对：模板有具体输入/产物/成功与失败处理，能解释一步为何要做；不是一句泛化“大改项目”。 | 不把教学模板授权成当前追加任务，不实际启动并行代理、外部消息或生产部署。 |
| WP-23.S04 | 主例当前真实代码、事实/截图/派生资产变更与稳定中文；适用门。 | 展示主例的必要真实代码和文档更新过程，完成三语及适用 G4/G5/G9；复用输入摘要仍一致的原前端验证，生成演示只做固定卷内适用检查。 实施要点：文内展示主例必要原文，说明为何改、怎样验以及哪些事实/教程/图片/生成资产需同步；完成三语；凭相关输入摘要复用有效检查。 | 事实、教程、截图和生成资产的维护职责完整；不虚构新后端改动、空库验证或另开示例环境。 具体核对：从需求到代码到验证到文档有闭环；生成演示只称实际静态验证，未改后端/迁移不虚构。 | 主例后续有修复就更新原文和证据，不能继续引用过时 UI 或凭已变更输入复用 PASS。 |
| WP-23.S05 | 二开文章四语全文、主例 diff/证据和读者恢复路径。 | 按读者角色从头检查例子可执行性并移交审校。 实施要点：模拟读者从头阅读，检查需要提供什么、在哪里操作、怎样知道成功、失败后回到哪里及何时更新事实；整理当前审校交接。 | 能看懂一次任务从需求到验证和文档维护的闭环，未执行部分如实待验。 具体核对：主例不依赖外链拼出关键步骤；权限/租户/模块边界真实；读者知道哪些是已实测和仅解释。 | 缺少必要示例材料仍留待办，不另外新建功能或改变原计划补演示。 |

### WP-24 官网、工作台、系统、任务与审批的图文使用教程

- 需求：WIKI-03：公共及平台功能。
- 依赖：WP-07 UI 已冻结；WP-09 风格检查；新截图依赖 Docker。
- 输入：功能矩阵、实际角色/租户、现有 E2E/fixture、当前界面与四语言标签。
- 允许改动：page-map 中 usage 的公共/平台文章及三译文、docs/images 四语言目录、截图 manifest/coverage；omni-frontend/e2e-docs 中相关 spec/fixture/helper、Playwright 文档配置及现有受控脚本。仅按 G8 适配旧项目/租户/业务前缀与当前 UI，并增加必要的错误目标拒绝用例，不删除环境和对象归属保护。
- 禁止：文章无代码/命令/源码跳转；不制作假的登录或数据成功截图，不以 display-fixtures 取代正式认证取证。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-24.S01 | 真实菜单/无菜单入口、UI 冻结记录、system-platform/scheduling/workflow 等现有 spec 与 docs-page/xxl-audit fixture。 | 按实际菜单和无菜单入口列公共/系统/监控/任务/审批场景，逐项核对真实身份；按 G8 最小适配旧隔离项目名、租户与业务前缀，登记本次对象及恢复方式。 实施要点：逐功能建平台场景并选真实身份；旧项目/日期/租户前缀改为经核验环境和本次对象登记，保留环境/租户/对象拒绝保护；列数据来源及恢复方法。 | 空状态、校验、拒绝、恢复与专用登录均覆盖；错误环境/租户/对象必须拒绝，不能把旧项目名改成万能放行条件。 具体核对：官网、全部实际登录入口、工作台、系统/权限/安全、字典、运维、个人/系统任务、审批各有归属；错误目标的必要拒绝用例通过。 | 不删除旧保护或使用万能项目名；不得让场景创建真实外部任务、未登记数据或新的运行栈。 |
| WP-24.S02 | 本 S 全部场景、真实 UI 镜像、G8 公开地址/身份/数据前置与固定证据路径。 | 在 external 模式真实接口与受控数据上逐场景操作，分语言保存私有候选截图；按 G8 区分真实登录与既有受控测试身份。 实施要点：一次一个场景依次操作并按四语捕获候选图；等待真实接口/异步状态稳定，记录身份来源、租户、预设、业务对象与结果；缺真实验证码时只暂停相关登录场景。 | 每张图有场景、身份来源、预设、语言、界面摘要和结果；登录本身须真实验证码，业务截图可复用合法会话，不能伪造成功状态。 具体核对：全场景有结果；成功/校验/空态/拒绝/恢复按功能适用覆盖；真实登录和受控身份业务截图分开；原图无敏感令牌。 | 写操作超时先按本次业务键查询核实，不盲重发；不能用 mock/HAR 回放制造成功或整页改 DOM 造图。 |
| WP-24.S03 | 全部平台场景的真实动作、候选图与读者使用身份/前置。 | 在本 S 内逐项完成全部公共/平台场景的无代码中文步骤，图片就近放置并解释结果、失败与恢复。 实施要点：逐场景写“想完成什么→谁能做/前提→从哪里进→点什么/填什么→截图→看到什么→失败怎么恢复”；图片前交代场景，后解释关注点。 | 每个场景均有正文；使用自然按钮名称，不夹 HTTP、权限码、Redis 或调试细节。 具体核对：全集均有中文且无源码/命令/SQL/配置；技术身份、HTTP/权限码只留维护台账，正文用真实按钮和业务词。 | 不在操作篇插调试原理或源码链接让读者自己拼流程；缺图场景如实保留待办。 |
| WP-24.S04 | 全部平台中文/三译文、真实四语图、截图 manifest/coverage 与 G4/G5。 | 核对图片脱敏后纳入正式目录，同步三语、图片登记和适用 G4/G5；明确模拟与未验证项。 实施要点：按场景/语言对应复核脱敏原图后才纳入 docs/images；同步三语动作/提示、图片引用和登记，更新真实摘要与待审状态，运行适用检查。 | 修改截图登记不能减少原有必需覆盖来取得通过；图像语言与文章一致。 具体核对：图片语言对应文章；尺寸/字清晰，点击位置和前后状态看得懂；覆盖义务未减少；译文未冒签。 | 不能把中文图一贴四语或用重新命名旧图充当新版本；脱敏不能改变业务结果。 |
| WP-24.S05 | 平台功能矩阵、全部图文、实际检查与待审/阻塞对象。 | 只做全部平台场景的覆盖、阅读与图片复查，交接指定审校；遗漏修复引用原 S，不增加步骤或重复编号。 实施要点：逐项核对能否只凭本文完成操作、每个功能是否有文章/图片归属、失败恢复是否可执行；必要缺项在原 S 修复，输出审校清单。 | 不依赖源码或必点外链即可完成操作；缺人工/外部验证的场景留 BLOCKED。 具体核对：全平台有覆盖记录且无未处理对象被遗漏；缺人工/外部验证的具体场景保留 BLOCKED。 | 不能用一张工作台总览代替系统/任务/审批全过程，也不在收口新加 S。 |

### WP-25 CRM 全功能图文使用教程

- 需求：WIKI-03：CRM。
- 依赖：WP-24 图文模板已验证；当前 CRM 环境和角色可用。
- 输入：CRM 功能矩阵、线索/客户/联系人/商机/活动/概览实际页面及正式流程。
- 允许改动：page-map 的 CRM usage 文章及三译文、相应四语图片/截图登记/E2E。
- 禁止：不改 CRM 状态机、权限或后端，不用直接 SQL 改状态完成故事。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-25.S01 | CRM 当前路由/权限/页面与 crm-teaching 等既有 spec；CRM 事实和本次共享数据。 | 将 CRM 每项实际界面功能映射为可理解的业务任务和角色，确认测试数据来源。 实施要点：按概览、线索、客户、联系人、商机、活动逐菜单/按钮列操作任务，核对筛选、增改、转换/阶段、关联/跟进等实际动作；登记角色与数据前置。 | 覆盖概览、查找/筛选、创建/编辑、转换/阶段变化、跟进等真实存在的动作。 具体核对：实际页面可操作功能均映射场景；“全部”以源码/页面盘点为准，不仅线索转换一条故事。 | 没有实现的动作不加入；不拿现有真实客户数据改状态当教学测试。 |
| WP-25.S02 | CRM 全部场景、本次专用业务键/对象登记、真实角色与 UI 镜像。 | 在本 S 内按业务先后逐项完成全部 CRM 场景，捕获成功、校验、空态和无权的必要截图，逐项保存断点。 实施要点：按业务依赖逐项用页面/正式接口准备受控对象并操作，捕获关键前后状态和适用异常图；所有关联使用本次返回 ID，按场景逐项落盘。 | 全部场景有结果及本次实际业务标识；未完成全集不能把本 S 标 PASS。 具体核对：线索到客户/联系人等实际转换及商机/活动动作都有结果；列表过滤/空态/拒绝同样有记录，镜像和图版本相符。 | 不直接 SQL 改阶段或模拟成功；请求超时先核实已否落库，避免重复转换/创建。 |
| WP-25.S03 | 本次 CRM 全部动作/数据关系/图片及读者身份。 | 在本 S 内逐项写完全部场景的无代码中文教程，解释每一步的业务目的和结果。 实施要点：逐任务写无代码中文，解释业务目的、进入位置、输入要求、按钮、成功后对象变化和错误恢复；关联数据在同一故事说明来龙去脉。 | 每个场景均有正文；销售人员不需知道接口字段或类名即可理解。 具体核对：销售人员能照做且区分线索、客户、联系人、商机和跟进；每个实际功能有正文归属。 | 不以状态常量、接口字段或表名解释操作，不用前几篇总链接代替剩余功能。 |
| WP-25.S04 | CRM 中文全集、三语与对应真实截图；图片登记、G4/G5。 | 对全部文章逐项完成三语、图片脱敏与登记，并运行适用 G4/G5。 实施要点：逐篇同步三语及图中语言，检查业务对象/按钮/阶段语义，脱敏后更新图片、清单、源/译文摘要与真实待审状态。 | 全集四语言步骤和截图对应，不将同一中文图贴到全部语言当完成。 具体核对：全集四语都有匹配步骤和图；相同业务链前后对象关系正确，检查有实际结果。 | 不因某语图片难取而借用中文图，也不能删覆盖项或标同步伪签核。 |
| WP-25.S05 | CRM 全部功能矩阵、文章、图、身份及检查证据。 | 核对 CRM 全集覆盖、阅读质量和图片并交接审校；缺项引用原 S 修复，不生成执行子步骤。 实施要点：从场景头到尾复读，再反向从每个真实菜单/按钮查归属；修漏在对应原 S，交接审校与具体外部待验项。 | 全部操作功能有归属，截图与当前冻结 UI 一致。 具体核对：全 CRM 可操作功能有归属，图与冻结 UI 一致；没有只完成第一场景就宣布整个模块完成。 | 不要改业务规则填证据；未完成项留 DOING/BLOCKED 或待指定审校的真实状态。 |

### WP-26 SRM 与供应商门户全功能图文教程

- 需求：WIKI-03：SRM 与门户。
- 依赖：WP-24 模板；本次 SRM 数据、邀请和角色前置明确。
- 输入：当前供应商生命周期、入驻 Saga、邀请、评价、风险、门户与报价实际界面。
- 允许改动：page-map 的 SRM/portal usage 文章及三译文、相应图片/截图登记/E2E。
- 禁止：不假造注册验证码，不将门户用户当内部负责人，不直接改 Saga/角色/供应商状态。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-26.S01 | SRM 事实/门户规则、实际入驻/邀请/供应商/评价/风险页面与既有 spec。 | 分别按内部管理员、业务负责人、供应商列完整操作故事，明确邀请和身份切换。 实施要点：按内部管理员、负责人、供应商三种视角建完整故事；列邀请、注册、Saga、供应商关联、角色刷新、资料、评价/风险及报价等真实入口与前置。 | 覆盖实际支持的准入/生命周期/评价风险/门户资料等菜单与无菜单入口。 具体核对：内部管理和门户功能不混；同一人何时需要切身份、谁能做什么及数据来自哪里均清楚。 | 不把默认 USER 当已关联 SUPPLIER；不伪造邀请码/验证码或把门户 ID 写成内部负责人。 |
| WP-26.S02 | SRM/门户全场景、合法邀请/注册前置、实际审批/消息状态与本次对象登记。 | 逐故事用真实流程产生本次业务数据，捕获关键状态和可恢复的失败；真实注册验证码由人完成。 实施要点：逐故事走真实页面/正式接口，等待受理、Saga 完成、角色刷新、门户可进入各状态；按真实身份切换并捕获关键四语候选图。 | 请求受理、Saga 完成、角色刷新和实际可进入门户分别记录。 具体核对：每个声称成功阶段都有实际结果，错误/恢复可解释；已受理不被写成最终成功，业务 ID 可关联。 | 真实注册验证码由人完成；不能直改 Saga/角色/关联表绕过前置或重试不确定写入。 |
| WP-26.S03 | SRM/门户全部真实故事、身份与关键图。 | 在本 S 内逐篇完成全部 SRM/门户场景的无代码中文，图片前后解释身份、动作与下一步。 实施要点：逐篇写无代码中文，明确每段当前身份、目标、前提、具体动作与下一步；遇到等待/失败时说清怎么判断和恢复。 | 全集有正文，读者不需要了解 requestId、内部表或消息机制才能照着做。 具体核对：读者无需理解 requestId、Outbox、内部表或权限常量即可照做；所有实际入口有归属。 | 不把实现机制塞进操作步骤，也不把未完成注册流程写成已可进入企业资料。 |
| WP-26.S04 | SRM/门户四语图文全集、图片登记/覆盖及 G4/G5。 | 对全部文章同步三语、正式图片和截图登记，运行适用 G4/G5。 实施要点：逐篇同步三语、真实对应语言的图片和身份说明，脱敏后登记当前版本/场景/预设/证据，更新文章真实待审信息。 | 全集处理后才通过；没有用占位图、展示 fixture 或旧豁免事项冒充本次实测。 具体核对：门户与内部操作的语言/图/身份一致；图片清晰且关键状态未被遮掉；没有沿用旧豁免。 | 不以展示 fixture 或占位/历史图冒充本次业务；不为四语取图扩大账号授权。 |
| WP-26.S05 | 全 SRM/门户矩阵、文内完整故事及真实外部依赖。 | 对照 SRM/门户全场景矩阵复查，移交审校并明确需本人继续的步骤。 实施要点：逐入口反查文章和图片，再按身份贯穿复读；记录仍需本人操作的确切位置、已完成结果、恢复身份/对象与原 S。 | 未实际完成的注册/外部步骤保留待办；权限和身份边界没有被放宽。 具体核对：内部/门户全部功能有处理记录；注册/授权缺项明确未完成；身份边界未改变。 | 不隐藏人工步骤来给 Goal 绿色，也不能新增当前未实现的供应商业务补故事。 |

### WP-27 采购全功能图文使用教程

- 需求：WIKI-03：采购。
- 依赖：WP-24 模板；供应商及审批等前置满足。
- 输入：当前材料/目录、请购、审批路由、RFQ、供应商报价、订单、收货实际功能。
- 允许改动：page-map 的采购 usage 文章及三译文、相应图片/截图登记/E2E。
- 禁止：不绕过报价/审批/收货，不为了截图改变采购或工作流后端。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-27.S01 | 采购事实/设计当前实现、材料/审批路由/请购/RFQ/报价/订单/收货页面与既有 spec。 | 从材料准备到请购、询价、报价、定标、订单、收货列全功能矩阵；区分申请人、审批人、采购员、供应商。 实施要点：按申请人、审批人、采购员、供应商列功能与动作，确认材料、供应商、审批模型及数据权限前置；含查询、配置和实际支持的状态操作。 | 包括实际管理配置、筛选和各状态可操作范围，不能只讲一次顺利采购。 具体核对：从材料准备到收货的真实链路及管理配置均有归属；每一角色只操作其可见范围。 | 未实现动作不加入矩阵；不为演示修改采购/工作流规则或放开门户权限。 |
| WP-27.S02 | 采购全场景、SRM 已登记前置、真实审批/报价规则和本次业务对象。 | 按实际流程逐场景操作并截图，复用本次已知可追溯前置数据。 实施要点：按实际依赖顺序操作并保存真实单号/行 ID/审批结果，复用同一链前置；等待异步状态稳定后截图，按适用情况覆盖拒绝、校验、空态和恢复。 | 关键业务号可连成完整链；无权与输入错误的预期结果明确。 具体核对：业务号可连接请购→询价/报价→订单→收货的实际路径；受理与最终状态不同有说明，身份与图一致。 | 不直接改库跳过报价/审批/收货；超时先核对单据和动作状态再决定是否重试。 |
| WP-27.S03 | 全部采购场景动作、实际界面字段和图；每段当前业务身份。 | 在本 S 内逐项写完全部采购场景的无代码中文步骤，让读者理解操作目的与结果。 实施要点：逐任务写无代码中文，解释业务目的、谁来做、从哪进入、如何填写/提交、何时等待及成功后变化；当前文章把必要前置说明清楚。 | 全集有正文，不以 SQL、HTTP、权限码或状态机常量解释界面动作。 具体核对：普通业务人员可按图操作；不靠 SQL/HTTP/权限码/状态机常量理解流程；全部实际功能有正文。 | 不能只展示顺利采购链遗漏配置/查询/异常恢复，也不增加新采购能力。 |
| WP-27.S04 | 采购中文全集、三译文及四语真实图片；G4/G5/截图登记。 | 对全部文章完成三语、图片与登记，执行适用 G4/G5。 实施要点：逐篇翻译并核对供应商门户和内部操作称谓、金额/数量/状态语义；脱敏图片后同步引用、登记、摘要与真实待审状态。 | 全集处理后才通过，供应商门户与内部采购身份标注清楚，原图语言正确。 具体核对：四语故事对象一致、角色清晰；所有适用图与正文同语言；自动检查真实完成。 | 不把一次性受理图当后续完成，不借旧任务截图或放宽覆盖得到通过。 |
| WP-27.S05 | 采购全功能矩阵、完整图文与单据链/证据。 | 核对采购全集覆盖、阅读与图片并交接审校；遗漏仍归原 S，不新增循环步骤。 实施要点：反向检查每个当前功能都能找到可理解说明，再从申请/审批/采购/供应商视角读完；遗漏回原 S 补齐并交审。 | 没有隐藏缺失的实际功能、图片或异常恢复。 具体核对：所有功能、必要图片、失败/恢复均有归属；相关业务状态/身份能连贯解释。 | 缺真实前置或人工操作保持阻塞，不改业务后端/数据库补证据。 |

### WP-28 资产全功能图文使用教程

- 需求：WIKI-03：资产。
- 依赖：WP-24 模板；收货与审批前置满足。
- 输入：当前资产建卡、台账、领用/接受/归还、调拨、处置、本人视图与概览。
- 允许改动：page-map 的资产 usage 文章及三译文、相应图片/截图登记/E2E。
- 禁止：不直接改资产状态/占用字段，不用不合格收货伪造入库，不改变金额字符串或权限规则。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-28.S01 | 资产当前事实/实现、台账/领用/接受/归还/调拨/处置/本人视图与既有 spec。 | 按资产管理员、使用人、审批人列从建卡到处置的实际功能、输入和前置。 实施要点：按管理员、使用人、审批人列所有实际操作及前置，明确合格收货建卡、固定本人范围、审批模型和互斥占用等对操作的影响。 | 覆盖普通管理视图与固定本人视图，以及当前支持的取消/拒绝/恢复路径。 具体核对：管理端与本人视图分开；实际支持的取消/拒绝/恢复均有场景，金额/数量要求来自当前约束。 | 不以不合格收货或错误数量强造资产，不给本人角色赋管理权限。 |
| WP-28.S02 | 资产全场景、本次收货来源/资产卡/审批对象、身份与受控数据登记。 | 逐场景用真实业务流程操作并捕获必要截图，追踪本次来源和业务标识。 实施要点：沿真实业务生成/复用本次受控对象，逐场景操作、等待最终状态并截图；追踪来源行、卡片、领用/调拨/处置业务号和审批结果。 | 不将接口受理当资产已变更；关键页面显示真实结果。 具体核对：受理、审批完成和卡片状态分开确认；成功/拒绝/取消/恢复按实际功能有证据，来源可追溯。 | 不直接写资产状态/占用字段，不手改金额字符串或重复执行不确定的状态动作。 |
| WP-28.S03 | 全部资产场景、状态前后图、真实角色与操作前提。 | 在本 S 内逐项写完全部资产场景的无代码中文，图片解释当前身份、状态和下一步。 实施要点：逐任务写无代码中文，解释领用、接受、归还、调拨、处置和审批的区别；每图说明当前人、状态、按钮与下一步/失败恢复。 | 全集有正文，读者不依赖源码知道领用、接受、归还与审批的差别。 具体核对：普通读者不懂类名/数据库也能完成任务；全部实际管理和本人功能有文章归属。 | 不把内部枚举/事务细节塞到操作步骤，不用前文总链接代替本文必要解释。 |
| WP-28.S04 | 资产中文全集、三语/四语图及其它使用篇的覆盖记录；G4/G5。 | 对全部文章完成三语、图片脱敏/登记与适用 G4/G5，核对其它使用篇的完整性。 实施要点：逐篇同步三语、脱敏图片、登记与真实待审摘要；比对本章 UI 版本及跨篇引用，列出漏图/漏步骤/语言不符项并在原 S 处理。 | 全集图文与冻结 UI 匹配，遗漏功能有明确待办而非删除覆盖。 具体核对：四语图文完整一致；本人/管理员身份无混用；全站 usage 不含代码，覆盖义务未被删减。 | 不得用同一中文截图充四语或用未实测状态图替代实际结果。 |
| WP-28.S05 | 资产全矩阵和 WP-24—27 的全功能图文覆盖表、实际检查/审校记录。 | 核对资产全集覆盖，汇总全功能使用教程并交接审校；缺项修复引用原 S，不新增循环步骤。 实施要点：逐项核对资产全集，再汇总公共/平台/CRM/SRM/采购/资产全部使用功能→文章→语言→图片→结果；不适用对象须有闭包/能力依据。 | 所有已实现的可操作功能有处理结果；普通使用教程均通过无代码检查与实际阅读。 具体核对：全功能覆盖不是模块名列表；所有实际操作有处理记录，可读性与无代码检查均有结果。 | 剩余真实验证/人工条件保留阻塞；不通过删对象、补空图或加新 S 清零。 |

### WP-29 全量集成、独立预设、指定审校与最终交付

- 需求：全部需求。
- 依赖：所有前置产物已就绪；正式 PASS 依赖必做验证和指定审校。
- 输入：全部覆盖矩阵、事实迁出待办、当前差异/摘要、四语审校结果、生成器和验证命令。
- 允许改动：仅前述 WP 白名单内的最小集成修复、对应文档/清单/生成适配；最终脱敏证据摘要。
- 禁止：不重写验收标准、不改审校授权、不自动提交/发布，不把 automated-delivery-pass 等同 release_ready。

| 步骤 | 读取与前置 | 具体操作与产物 | 验证与完成判据 | 边界/失败处理 |
| --- | --- | --- | --- | --- |
| WP-29.S01 | 事实待迁原文、全部已完成接收页、page-map、旧路径/锚点与生成模板。 | 逐项合并事实迁出待办到已完成 Wiki，清零独有内容迁移；复查所有人类读者页面和旧链接映射，更新必要生成文档规则/模板。 实施要点：逐项把保留的独有教学内容确认接入 Wiki，再最小删除事实源重复教学；同步引用、四语、摘要及必要派生规则，反查所有旧入口。 | 事实精简与人类教程各自成立；旧 URL 有合法兼容处理，正式导航无占位与遗漏。 具体核对：每个待迁项有原位置/接收位置/内容核对；事实约束无丢失；正式导航无空页/孤页，旧链接可达。 | 无完整接收正文先不删原文；本步不发起额外全仓重构或改历史 ADR。 |
| WP-29.S02 | 最终当前差异、各门输入摘要与既有有效证据、G1—G10 适用项。 | 运行最终 G1—G10 的适用检查；对新失败做最小回归修复，重新验证受影响部分。 实施要点：先核对证据是否适用于当前输入，变化范围串行重验；记录命令/退出码/日志，不受影响且摘要一致的检查复用；缺陷仅在前述白名单内修复。 | 根项目真实 build/lint/unit/type/语言键、CLI、文档、片段、链接/图片/敏感及 adapter 检查有结果。 具体核对：前端/CLI/文档/片段/语言键/链接/图片/敏感/AI 派生各门有真实结论；未运行/失败/待审独立列出。 | 原有失败不自动扩修或伪报通过；不能删测试、放宽质量门、盲重跑全量消耗本地资源。 |
| WP-29.S03 | 最终五预设闭包、G6/G7/G11、固定产物位与 source/generated 已有证据/镜像摘要。 | 在固定验证卷逐个生成五预设，按 G6/G7 核对产物自身 UI/文档/AI/模块资源，必要 G11 均顺序替换同一环境；每轮留存摘要和结果后再覆盖产物位，结束恢复基线启用服务。 实施要点：逐预设生成并检自身产物；本轮保留完整输入/输出摘要、自动化结果、真实 release_ready/待审清单，运行缺少或失效的必要启动证据，再复用产物位并恢复基线服务。 | 五行自动化交付及发布就绪字段均真实；交付角色不暴露源仓，不沿用失效签核；同一网络/数据/标准端口、每服务单实例和唯一辅助名额成立。 具体核对：五行均可追溯到实际输入/产物；交付角色无源仓/socket；源/generated 运行证据与实际 Image 匹配；单套资源始终成立。 | 不同时保留五套工程/容器，不把待审误作可发布，也不跑裁剪迁移/种子覆盖共享数据。 |
| WP-29.S04 | 源仓逐篇当前中文/三译文/图片、旧签核失效状态与真实 Codex 授权；最终产物摘要。 | 整理源仓逐篇待审材料与当前摘要，交用户指定的 Codex 实际复核，不自动发消息。收到有效证据后执行 G4-release 和最终四语/根路径/子路径/图片/新 UI 核对；审校后输入有变化则顺序重生受影响预设并复验 G6 结构/G7，应用构建或运行仅在相关输入改变时重验。 实施要点：按篇整理待审材料及明确问题交用户指定 Codex；收到真实结果后核对摘要再登记，运行源仓 G4-release 与最终浏览器检查；审校变更后仅复验受影响生成/构建/运行。 | 源仓未获真实指定审校不能完成；意见在原 S 内修正并复核。生成物按 G7 真实记录裁剪后待审状态，不冒充可发布，不要求本次独立发布五个预设，不手改 scaffold.lock。 具体核对：源仓实际指定审校/严格门通过；五种生成物按 G7 自身契约记录裁剪后的真实发布状态；任何修正后的证据均匹配最终输入。 | 没有真实审校则 READY_FOR_REVIEW，不能给整 Goal 完成；不自署 Codex、不手改锁/关闭待审门、不自动发消息。 |
| WP-29.S05 | 全部 150 个 S、需求/对象覆盖、最终 Git 差异/摘要、证据索引与初始环境记录。 | 审阅全部差异和覆盖矩阵，复核计划锁及完整 150 步；确认最终证据匹配交付输入，输出结果、待发布状态和恢复入口；结束本次辅助任务，复核基线启用服务/Wiki 状态和资源。 实施要点：逐需求与原 S 清点必做证据，确认无过期 PASS/未处理对象；结束已确认归属的辅助任务，恢复新版本的原启用服务/Wiki 状态，按 E 输出最终结果和恢复入口。 | 必做项真实通过才完成；必做外部验证未完成须明确阻塞。计划原文无变更，无遗留额外应用/网络/业务卷/端口；不强制常驻 full，无擅自提交、合并、推送或发布。 具体核对：计划锁通过；范围无扩散；最终源码/镜像/图/审校/生成物证据一致；唯一项目、网络/卷/端口与单实例核对完成。 | 有必做外部验证或源仓审校未完成就明确整体未完成；不擅自提交/推送/合并/发布，不全局清理资源。 |

## E. 最终报告格式

最终报告保留下列字段，内容来自真实证据：

1. 当前分支、实际 HEAD 与相关未提交文件摘要、改动范围。
2. UI-01—10、WIKI-01—09、事实审计及五预设/十 common/全功能覆盖矩阵。
3. WP-00—29 及所属 S 的实际状态与证据索引，完整保留 TODO、DOING、PASS、READY_FOR_REVIEW、BLOCKED；不隐藏尚未执行或部分完成的事项。
4. 构建、单元、类型、浏览器、真实业务、截图、翻译审校、生成物及发布就绪的独立结果。
5. 指定审校者的真实记录；本次没有执行的项目及用户明确接受的范围例外。
6. 未解决问题、需要的最小外部动作、继续执行的 WP/S、私有进度与证据位置。
7. 没有必做项待验才声明 Goal 完成；源仓待指定审校或必做环境验证阻塞时写清整体未完成。生成物按 G7 保留的独立发布待审状态单独列出，不隐藏，也不混成源仓审校已经通过。
8. 单环境最终状态：唯一项目、网络/数据卷 ID、标准端口、应用单实例、辅助容器名额、基线启用服务/Wiki 恢复结果和资源快照。
9. 计划完整性：原锁文件摘要、保护文件摘要与 30 WP/150 S 的比对结果；任何用户明确修订及其独立授权依据。
10. 未完成任务清单：固定文件路径、全部非 PASS 原 S 及未完成对象、实际阻塞原因、已尝试结果、现场证据和接手入口；未尝试与无法解决分别说明。清单为空需明确 0 项及依据，清单不为空不能宣称整个 Goal 完成。

