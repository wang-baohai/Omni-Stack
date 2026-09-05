# QODER EXECUTION HANDOFF

TEMPORARY GOVERNANCE ARTIFACT；2026-09-05。适用于没有历史聊天的Qoder。只以本文恢复执行，不从旧Flash/ZF/Stage进度继续推算。本文不拥有另一份状态：事实归current-state，活动进度归work-packages。

## PROJECT / MASTER GOAL

Omni-Stack：Spring Boot4/JDK25 + Vue3/TypeScript多租户微服务脚手架。完整交付包含现有八服务业务、安全/工作流/可靠MQ、数据库版本管理、service/CRUD生成器、五预设、轻量开发、可观测性、四语言文档、完整E2E/步骤截图、清理、可追溯发布。范围不能缩成最近的SRM或翻译批次。

按顺序读取：

1. 根 `AGENTS.md` 和 [MASTER DELIVERY GOAL](../../scaffold-upgrade-plan.md)。
2. [长期治理 / Evidence / Escalation规则](../../repository-governance.md)。
3. [CURRENT FACTUAL STATE / PROTECTED BASELINE](current-state.md) 与 [verification.json](verification.json)。
4. [ACTIVE WORK PACKAGES](work-packages.md)；精确新ID为WP-001～016。
5. [REPOSITORY CLEANUP MANIFEST](cleanup-manifest.csv)，仅分类设计，未满足删除前置。
6. 当前WP对应architecture/API/patterns/领域设计/正式指南；从governance的canonical表定位。

## CURRENT BASELINE / HEAD / STATUS

基准HEAD `204bf874efe6a43e8f849c5d4321f84cef2b6764`，分支 `codex/scaffold-upgrade`；调查时Gitee/GitHub该分支同SHA。本治理包是该HEAD上的未提交文档修改，不能声称已推送或只用remote checkout交接。开始前核对HEAD、文件清单与digest，保存用户既有改动；任何变化先做增量对账，禁止reset。

当前总体IN_PROGRESS；G0/G2存在REWORK，G1有接管/备份阻断，G3～G6有实现但缺当前候选运行验证，G7未完成，G8明确后置。初始strict i18n204、screenshots8、queue12失败，标准lint38 errors/26 warnings；治理修改后的实际检查以verification的final_checks为准，不能拿初始数代替最终工作树。未跑本轮Maven/build/E2E；不存在本轮FINAL PASS。

## PROTECTED WORK

保留19模块源码、migration/seed/catalog、189 Java测试文件、10 CLI test文件、断言式E2E、10 tracked文档flow及shared fixture、所有golden/checker/CI/部署工具。保留未提交supplier-detail spec/local配置及其修复能力。230个manifest官方图候选须先审核才可替换/退役；保护不等于视觉已PASS。

保护09a29fe旧adoption基线与现有安全/兼容契约，不直接使用baseline-candidate。原工作区全部改动、四份含认证头Trace与失败/registry证据受限保全；不上传原件、不盲删。历史32/32、ALIGNED、DONE或旧“4 passed”不能独立升级当前状态。

## ACTIVE WP / EXECUTION ORDER

第一批推荐：

1. **WP-001** 建立候选树、原件保全与隔离运行身份；只纳入经过审查的当前改动。
2. **WP-002** 修复队列语言范围、故障PREPARE阶段协议、CI真实执行/skip与门禁证据能力，保留strict红灯的真实原因。
3. **WP-004** 仅在隔离条件就绪后，沿当前SRM spec定位最新失败，单语通过后四语，证明副作用/清理；不要重修已纠正的旧keyword等问题。
4. 同批准备 **WP-003 / ESCALATION-001** 的旧库接管决定包和 **WP-005** 数据只读前置；未解除阻断时停止相应写入。

其后WP-006采购→WP-007资产；WP-008 Workflow/任务/CRM、WP-009认证/管理、WP-010 MQ/观测、WP-011 CLI/预设按各自依赖分小批推进。WP-012 UI语言与WP-013文档可按稳定领域分批收敛，WP-014逐场景审图。G1～G7真正完成后WP-015清理，WP-016提交候选给Codex独立审查；通过后仅做治理封存diff与必要验证。

不要求多Agent执行；可用Qoder Efficient处理已明确的机械任务，但责任、范围与验收不得改变。

## BLOCKED ITEMS / DEFERRED ITEMS

具体升级记录均在work-packages末尾，包含全部决策字段：

- ESCALATION-001：旧库adoption支持边界、标签及真实备份/恢复证据。不是“fresh指纹不同就替换baseline”。
- ESCALATION-002：隔离target、同环境短期身份/多审批人、Workflow/MQ故障注入与副作用边界。已有授权覆盖的安全准备直接做；缺真实身份/权限则BLOCKED。
- ESCALATION-003：配置/登录记录的业务/API/权限与脱敏契约，不能把OAuth授权记录当登录日志，不能自行豁免缺流程。
- ESCALATION-004：共享采购种子恢复。历史13行状态本轮未核实；优先fresh隔离fixture，避免依赖修共享栈。

明确DEFERRED仅有G8/WP-015最终清理及最终治理封存。生产特定容量/灾备/第三方OAuth实网/独立渗透专项遵循MASTER原边界，不能冒充已验收。独立语义签核未取得时保留对应译文未审核，不能阻止其他独立WP推进，也不能代签。

## TEST ORDER / E2E REQUIREMENTS

证据→契约→最小修复→单用例→相关回归→完整候选门。两轮不稳定即升级，targeted上限三轮且每轮有新Evidence。JDK25先设JAVA_HOME，再用Maven wrapper；前端改动必须build/标准lint；fixture/E2E需独立TS检查。CLI单位/黄金/真实runtime、五预设、数据库fresh/adopt/upgrade与恢复、全部业务E2E按WP和MASTER门表执行。

用例数量/文件数量不计算完成率。所有必需测试有expected/executed/pass/fail/skip；skip不算PASS。真实业务必须真实API/服务/DB；public展示mock、浏览器401故障、真实依赖outage分级登记。按tenant/run/准确ID登记创建与不确定POST，检查Workflow/XXL-JOB/inbox/outbox/audit副作用；不能以有效主表零行代替清理完成。

## SCREENSHOT / EVIDENCE / SECURITY REQUIREMENTS

按required_flow×state×role×locale×viewport验收；默认1440×900，关键移动390×844及平板。先审已有官方候选图，缺口随稳定用例生成；逐图检查当前版本、无敏感/截断/加载态、业务步骤正确。真实四语内容与独立审核必须具备，图片生成后不自动covered。保留E2E/screenshots helper/fixtures作为长期能力。

所有Evidence注明类型、候选SHA/文件digest、环境镜像、命令/cwd/版本/时间、断言与计数、脱敏/复核/失效条件。Static/Test/Runtime/Visual/DB/Git/Remote不能互相替代。Trace原件至少已有100条Bearer头记录，token有效期未核实；只提交脱敏摘要。不得记录真实Secret/Cookie/JWT、绕CAPTCHA或添加生产测试后门。

## CLEANUP REQUIREMENTS

先保全→归并规范→重构可重复能力→验证替代物→刷新七域引用→精确小批删除→新checkout最终回归。CSV逐路径执行，出现新文件/新引用/hash变化即重新评估。保护测试、migration、bootstrap、seed framework、CI、checker、auth/browser/screenshot fixtures。旧SQL必须先证明正式迁移/旧库升级替代，不能因文件名临时直接删。仓库外TEMP/机器文件不在本清单授权内。

最终本handoff、current-state、WP、CSV和verification也是清理对象。长期规则已在MASTER/governance；尚有唯一长期事实须归并正式docs/ADR/发布Evidence再删。产品候选Review通过后纯治理封存，不继续制造新的LATEST/FINAL/CURRENT平行状态页。

## FINAL HANDOFF CONDITION

每一重要批次按WP提交可审查diff与证据，输出 `READY_FOR_CODEX_REVIEW=true` 或 `READY_FOR_CODEX_REVIEW=false`。不得自己宣布FINAL PASS。Codex独立核查目标、源码、测试、运行、截图、文档、清理和所有门后只能给PASS或REWORK_REQUIRED；后者形成REWORK，不由Codex代实施。

本轮治理就绪检查：

```text
MASTER_GOAL_READY=true
CURRENT_STATE_RECONCILED=true
CANONICAL_SOURCES_DEFINED=true
PROTECTED_BASELINE_READY=true
CLEANUP_MANIFEST_READY=true
E2E_ASSETS_PROTECTED=true
QODER_WORK_PACKAGES_READY=true
TEST_STRATEGY_READY=true
QUALITY_GATES_READY=true
ESCALATION_RULES_READY=true
QODER_HANDOFF_READY=true
HANDOFF_READY=true
```

这些值只证明治理包已可执行、未知/阻断已有归属和停止条件；不代表产品交付通过，也不授权执行仍缺前置的WP。治理包验证以verification.final_checks为准。Codex本轮到此移交，停止Implementation。
