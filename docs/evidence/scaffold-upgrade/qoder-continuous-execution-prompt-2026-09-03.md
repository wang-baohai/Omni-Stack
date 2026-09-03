# Qoder 多阶段连续执行提示词

日期：2026-09-03。用途：在 Qoder 中用一个持续任务推进多个已批准的技术收口阶段；本文件不是代码实施完成报告。

## 使用前必读

- 截图中的 800 次是 Qwen3.8-Max 的免费模型调用额度，不是 800 个 Quest。官方按平均值估算 800 次约可执行 80 个任务；单个长任务会有多次模型调用。免费次数优先消耗，用尽后转入套餐 Credits 计费，已领取次数于 2026-09-30 23:59（UTC+8）到期。不要把“一个任务”理解为“一次扣费”。[官方活动规则](https://docs.qoder.com/zh/events/qwen-max)
- IDE 可使用 Quest 的 Agent 模式；CLI 支持 `/goal <目标>` 持续执行。两者都是工作方式，不是计费减免机制。[Quest Agent](https://docs.qoder.com/zh/user-guide/quest/agent-mode)、[CLI Goal](https://docs.qoder.com/zh/cli/goal)
- 手动选择 Qwen3.8-Max，避免 Auto/其他模型不适用这份专属免费额度。不同版本可用配置以实际界面为准。[模型选择器](https://docs.qoder.com/zh/qoder/model-selector)
- 当前仓库有重要未提交成果，本任务应接续原本地目录和分支；不要为开启 Quest 自动切换分支或创建一个遗漏未提交成果的工作区。已有其他工具正在写同一仓库时，先暂停其中一方。
- 建议用单 Agent 连续推进；当前流程共享 Docker、数据库和测试身份，默认不开 Experts 并发修改/写入。取消 Token 上限不等于需要最大上下文、最多子 Agent 或输出全部日志。
- 用量提示词不是账单硬限制。需在客户端用量面板核对实际扣减，尤其是在开始和阶段交界时；无法自动读取用量时，Agent 不得编造剩余次数。CLI `--turns` 是执行轮数保护，不等于免费调用次数，不能拿 `--turns 800` 保证不超出 800 次额度。
- 不要通过编辑旧消息或 Revert 来“更新提示词”：官方说明编辑重交会丢弃该轮之后的文件变更。请追加发送下面的新指令，或在原目录创建接管任务。[Agent 编辑消息说明](https://docs.qoder.com/zh/user-guide/quest/agent-mode)

## 可直接发送给 Qoder 的完整提示词

```text
继续 Omni-Stack 脚手架升级任务，使用多阶段连续执行方式，完成以下明确授权且当前可执行的技术收口工作。

仓库：C:\WorkSpace\QODER\Omni-Stack
预期分支：codex/scaffold-upgrade
已知交接基线：ace8bf7；必须最小核验实际 HEAD，不回滚其他工具后续成果。

一、事实入口与指令优先级

先读：
docs/evidence/scaffold-upgrade/srm-supplier-quotation-qoder-handoff-2026-09-03.md

随后仅按阶段需要读：
docs/evidence/scaffold-upgrade/handover-status-2026-09-03.md
docs/evidence/scaffold-upgrade/final-gate-unlock-preflight-2026-09-02.md
docs/evidence/scaffold-upgrade/srm-quotation-recovery-2026-09-03.md

本提示明确替代旧交接中的：
- 40K/50K/70K Token 上限；
- 仅做 SRM 单一 gap、每批完成后必须等待下一条消息；
- 整个任务最多三轮迭代；
- 因 Token 预算而省略必需构建或验收的安排。

其余事实更正、权限与数据安全、真实证据、Git 保护、冻结结论、外部审批和 Gate 依赖全部保留。旧文档的 strict 8→7、8 张已有截图等错误结论不得重新启用。

本次授权范围仅为下面 A-D，不等于自动批准原计划全部任务。先形成简短阶段清单后开始，不再重复制作完整开发计划或全仓审计。

二、阶段 A：完成 SRM supplier-quotation

按主交接 §5 Step 0→6 修正前置、定向验证、签发 Token、真实 E2E、数据收尾、文档登记、提交交付。

必须解决：
- 精确选择 procurement-approval，移除 workflow-options 和审批任务的第一条兜底。
- 实际 E2E_MUTATIONS 防护、资源创建即时登记和可验证的清理。
- auth seed canonical SHA、角色/作用域断言及 forward-only 升级兼容。
- JDK 25 + Maven Wrapper 编译 fixture，确认有效 TTL=1200，再签发。
- QuotationSubmittedServiceImpl 当前源码回归验证。

验收：四语言 4 passed、0 skipped、12 张真实 PNG；本阶段资源按归属清理，凭证安全销毁；manifest/coverage/指南/摘要一致。
此时 SRM 仍 partial，strict 仍应是 8 个已知模块覆盖失败，不得为降数修改检查器或造假 covered。

阶段通过并记录 checkpoint 后，自动进入 B，不必等待用户再次发送“继续”。若 A 局部阻塞，可完成与其不共享失败前置的 B/C 项；阻塞 A 的链路不得冒充通过。

三、阶段 B：连续关闭现有计划的其余截图技术 gaps

本提示明确授权原交接中已列出的截图深度收口队列：
1. SRM 剩余 gaps；
2. system-management；
3. messaging-monitoring；
4. workflow；
5. procurement；
6. asset；
7. scaffold-development；
8. operations。

以实际 screenshot-coverage.yaml 的 required_flows/gaps 为范围，以既有成本/可构造性说明为背景；可根据依赖调整顺序，不重查冻结根因。

每个模块按“定位既有 gap→复用正式功能和测试工具→真实验证/截图→清理本阶段数据→图片质检→manifest→指南/源摘要→覆盖检查→checkpoint”闭环。

只补已有产品功能的测试、截图、操作说明和必要测试支撑，不为消除 gap 新造业务能力、绕过权限、制造生产故障或购买/调用新的外部付费服务。非页面流程不得伪造 UI 图片，按既有文档标准提供可复核的真实操作证据。

截图必须是真实系统状态，不得用 mock 成功响应、占位图、其他语言图片或 AI 生成图替代。完整模块实际满足门禁才改 covered；只关闭部分 gap 就保持 partial。

无法构造的场景、新发现的 PRODUCT_DEFECT、缺失的必要身份/权限/环境，逐项登记 BLOCKED 与所需输入。不得擅自 exempt、标记产品不存在就删 required_flow、修改业务规则或拓展修复。继续做确实独立的其他项目。

该阶段没有“必须把 strict 归零”的造假压力。目标是全部可执行项真实完成、不可执行项有明确证据和恢复条件。

四、阶段 C：四语言文档预审与修订

完成与截图有关的源文档更新后，按实际 docs review queue，P0→P1→P2，逐中文事实源及其 en-US/ja-JP/ko-KR 译文推进。历史队列为 38 个源、114 项译文，以实际增量为准。

检查语义、主要流程、前置条件、命令、权限码、API、截图引用、术语、数字、链接和代码块；有冲突时仅定向查看相关实现。允许修正文档/译文并刷新真实源摘要，禁止全仓重新审计或顺手改无关代码。

为每个完成的预审项记录：源与译文路径/摘要、检查范围、发现的问题、修改及剩余疑问，形成可供下一验收者使用的记录；按同一源成组完成，避免无组织地改遍所有文档。

本阶段是 Qoder 的实质性预审/修订，不自动替代 preflight 中指定的独立 Codex final review 或人工验收。不得将自审或自动 parity 检查冒充这些验收，不批量填 synchronized/reviewed_at。修订后需重新独立复核的条目保持真实待审核状态。

五、阶段 D：汇总验证与可接续交付

对实际变更按 AGENTS 执行必要构建/测试；不再以节省 Token 为由跳过。模块内先定向验证，改动稳定后整合运行需要的全量编译、frontend build/lint等常规验收；同一未变化状态下已通过的检查不重复跑。新修改使旧结果失效时必须重验。

常规构建验证不等于正式 G8。不得提前执行 G8 once-only 综合矩阵或宣称全工程终验完成。

汇总已关闭/剩余 gaps、strict 实际结果、文档预审进展、代码及数据清理证据、当前 Git 提交与远端状态。明确区分 DONE、IMPLEMENTED_NOT_EXECUTED、BLOCKED、NOT_STARTED。

六、仍不授权的事项

- 代替运维批准 adoption baseline，或将 G1 自动置 CLOSED。
- 把 Qoder 自审冒充独立 Codex final review / 人工复核，或据此将 G7 自动置 CLOSED。
- 执行 WP-10 历史临时文件清理、清除历史测试数据、读取敏感备份、提前运行 G8。
- 自动进入远端隔离 CI / workflow 重构等非阻塞后续任务。
- 为多完成任务而扩展业务需求、改权限架构、降低检查标准或绕过平台安全确认。

只要这些外部事项未满足，保持对应 Gate 真实状态；不因它们阻塞独立的 A-D 工作，也不无限等待外部输入。

七、资源与持续执行策略

不设置人为 Token 总上限。以有效成果为目标，不以输出长度、工具调用数、子 Agent 数或消耗完额度为目标。

使用用户在客户端选定的 Qwen3.8-Max，不擅自切换 Auto 或其他计费模型、不购买资源包、不自动启用多 Agent。共享 Docker/数据库的写入 E2E 串行，避免互相污染；可并行的普通只读命令自行合并。

免费额度按模型调用扣减，一个 Goal/Quest 不等于一次调用。每个阶段如果能读到可信用量就记录；不能读取就明确 UNKNOWN，提醒用户看客户端，不编造计费数字。观察到免费次数接近耗尽或模型/计费来源改变时先收口提示，不擅自同意消耗额外付费资源。提示词不能代替平台级账单限制。

取消整批三轮限制，但保留防空转条件：同一问题连续三次尝试没有新证据或实质进展，标记该项 BLOCKED，停止盲试，继续不依赖它的工作。有新证据的合理迭代不被旧整批上限截断。

上下文只保留当前阶段必需材料；读过且未变化的内容不重复整读。日志先摘要，确需诊断时允许读取足够上下文，但不得泄露敏感信息或无目的转储全仓内容。

长命令返回运行标识后复用原进程，正常构建超过 30 秒不视为失败、不强杀或重启。保留有界等待和状态查询，等待期间可以推进独立工作；工具失联记 TOOL_TIMEOUT 并保存恢复入口，不无限空轮询。

八、安全与 Git

保留主交接认证、凭证清理、精确测试数据清理全部约束。只用 E2eTokenFixture 中具备适当权限的既有测试身份；不能为了其他模块测试临时给 admin/supplier1 越权或硬编码真实 Token。新增身份需求登记待确认。

只清理本次任务各阶段确认归属的 tenant+runStamp+资源 ID；历史残留独立列出。禁止 CAPTCHA bypass、Redis 登录 Token 查询、打印 Secret、读取 .env.before-rebuild-*。

保留当前未提交和已提交成果。禁 reset/restore/checkout/stash/clean/rebase/merge/amend/force push，禁 git add .。排除项不提交不删除，WP-10 不执行。

阶段 A 的 stage 范围按主交接执行。阶段 B/C 扩展至该阶段实际相关且经过审阅的测试、真实图片、文档、manifest/queue 和结果记录；不得扩大到整个目录或无关文件。

每个可独立验收阶段用清晰的小提交保存成果，不攒成一个不可审查的大提交。提交前核对 cached name-only/check/secret scan，只输出检查结论，不打印凭证。已有不归属本任务的暂存内容不得擅自取消暂存或混入提交。

允许对已验收阶段正常 commit/push；每次 push 前仅做一次有界 fast-forward 核验，无法证明或发生分叉即 REMOTE_DIVERGENCE_STOP，不自行合并处理。不要反复推送未变化内容。Git 交付不安全时停止提交推送；保留本地现场并报告。

九、断点与完成条件

维护一个简洁的持续执行 checkpoint：已完成阶段、变更文件、验证命令和时间、资源归属、未完成项、阻塞原因、运行进程标识、下一条具体操作。可写到：
docs/evidence/scaffold-upgrade/qoder-continuous-progress-2026-09-03.md

每完成一个模块或文档批次更新一次，不每读一个文件就写长日志。上下文压缩、网络中断、进程重启后，从 checkpoint 和最小 Git/运行状态增量恢复，禁止重开全仓审计，不丢弃已有成果。

不要因为完成一个 gap 就结束整项任务。持续执行，直到 A-D 的全部可执行项完成，或剩余项全部需要外部批准/新增权限/不可用环境，或用户停止/平台额度及运行限制要求停止。

如使用 Goal，以上就是目标终点。全是外部阻塞时真实暂停，不能把原始升级目标标完成，也不能让 Goal 为“必须完成全部”反复空转。

最终交付：
- 各阶段已完成内容和明确证据；
- 实际测试、图片、strict、文档预审统计；
- 未执行验证与外部 Gate 状态；
- 数据及凭证收尾结果；
- 本地/远端提交状态；
- 如实记录的用量信息或 UNKNOWN；
- 剩余任务、阻塞条件及下一恢复入口。
```

## 可选：CLI Goal 启动方式

先在客户端选择正确模型、确认工作目录和当前权限模式，再启动目标。不要为无人值守而关闭安全保护。官方 Goal 指南和命令参考对自动权限切换的表述存在差异，以当前客户端实际模式/提示为准。[指南](https://docs.qoder.com/zh/cli/goal)、[命令参考](https://docs.qoder.com/zh/cli/goal-reference)

```text
/goal 按 docs/evidence/scaffold-upgrade/qoder-continuous-execution-prompt-2026-09-03.md 的完整提示词持续执行 Omni-Stack A-D 技术收口阶段，直到全部可执行项完成或仅剩需外部输入的阻塞项；逐阶段验收并保存 checkpoint，保留所有安全和 Gate 边界
```

IDE 用户直接在 Quest Agent 中发送上面的完整提示词即可，不必将 CLI 的斜杠命令当成 IDE 通用命令。
