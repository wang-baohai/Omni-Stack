# ACTIVE WORK PACKAGES / ESCALATIONS

TEMPORARY GOVERNANCE ARTIFACT；2026-09-05。以下WP-001～016是本轮命名空间，替代旧计划中同名数字，不允许混用旧WP-01或ZF状态。目标归 [MASTER](../../scaffold-upgrade-plan.md)，事实与Finding归 [current-state](current-state.md)。Qoder只能更新工作事实与Evidence，不修改验收。

## 通用执行契约

每个WP按下表各字段独立验收；表内的“通用约束”指：遵守AGENTS及相关canonical docs；保留已验证实现和所有可复用测试；先契约后修复；不降低门、不伪造身份/证据、两轮不稳定或最多三轮新证据调试必须升级。每个WP的Evidence必须使用repository-governance §3全字段，并明确候选SHA/diff摘要、测试失败/skip和作用域；“N/A”须写理由，不能省略验证。依赖未验收时仅推进不依赖部分。

WP验收输出只能 PASS / FAIL / BLOCKED；实施状态单独按八值维护。Qoder提交 `READY_FOR_CODEX_REVIEW=true/false`，Codex决定最终验收。下面当前状态是设计时状态，不能照抄成实施结果。

## WP-001：候选树、隔离运行环境与保全

| 字段 | 要求 |
|---|---|
| Objective | 建立不丢用户改动、可重现和可审查的执行起点 |
| Current State | NOT_STARTED；当前HEAD已核实，原工作区dirty、运行镜像未绑定源码 |
| Scope | 10个tracked改动、130个原untracked、镜像/端口/卷/身份及Evidence目录 |
| Implementation Requirements | 对照initial_status/CSV保存路径摘要；逐diff识别用户与Agent产物，单独审查纳入候选的译文/spec/local配置；用明确新候选checkout和唯一Compose project/端口/卷，记录镜像ID/源码SHA/依赖；无需迁移原环境 |
| Constraints | 通用约束；不reset/clean/搬走原工作；不把.env/Trace/patch盲目add；新checkout排除临时产物要有处置记录，不能声称原tree lint已绿 |
| Dependencies | 无；运行准备依赖受控身份/环境权限 |
| Acceptance Criteria | 初始每个改动有保全与纳入/留存决定；候选清单明确；服务镜像可追溯；E2E身份实际匹配环境 |
| Required Static Verification | Git diff/status、全部路径SHA、Compose config不打印Secret；七域引用初扫 |
| Required Tests | checkout文件集与fixture来源自检；不为单纯文档保全跑全build |
| Required Runtime Verification | 候选各必需服务健康/API可达、tenant/role只读资格校验；原环境不停止 |
| Required Screenshot | N/A，环境身份由结构化证据证明 |
| Required Evidence | 文件保全/候选diff、镜像与配置摘要、隔离边界、身份元数据（无token值） |
| Cleanup Impact | .work/.artifacts原件受限保存；最终处置转WP-015 |
| Failure Conditions | 不明文件丢失、候选与证据不匹配、运行仍用未标识latest |
| Escalation Conditions | 无独立环境/安全身份、未知写入影响；ESCALATION-002 |

## WP-002：质量门实现与CI执行语义修复

| 字段 | 要求 |
|---|---|
| Objective | 让门禁真实验证MASTER，避免skip/元数据造成绿色假象 |
| Current State | REWORK_REQUIRED；REWORK-001/002/003/005/007 |
| Scope | CI、docs-review-queue、截图checker、E2E编排/类型检查与临时脚本处置方案 |
| Implementation Requirements | 修复PREPARE状态文件逻辑；Chinese-only条目不生成假翻译；校验required flow/state与例外审批及证据来源；必需测试skip使验收失败；CI生成完整隔离Secret/同环境短期身份、逐服务健康/baseURL、清理独立栈；覆盖runtime压缩证据敏感检查；补E2E TS入口 |
| Constraints | 通用约束；不重写业务、不降低lint/strict、不将fixture展示图等同真实认证；保留12/204/8原失败记录 |
| Dependencies | WP-001候选清单；真实CI/故障验证需隔离环境 |
| Acceptance Criteria | 队列12假缺失消除且未审核译文仍红；PREPARE/ASSERT真实执行并可追溯同fixture；缺token/缺服务/coverage虚报场景明确FAIL/BLOCKED；最终标准lint0/0 |
| Required Static Verification | CI配置/Secret变量声明、test discovery/tsconfig、失败计数、七域脚本引用 |
| Required Tests | checker负例（缺图/缺状态/未审核/伪covered）；Chinese-only正例；PREPARE阶段条件与缺state负例；标准lint和E2E类型检查 |
| Required Runtime Verification | 一个隔离nightly/手动候选run；真实Workflow outage而非失效模型代替网络依赖中断；required tests零不明skip |
| Required Screenshot | checker不需图；故障UI若属G2状态需按WP-014采样 |
| Required Evidence | 修改前后失败触发/输出、CI run关联SHA与测试摘要；机制性修复不代签102译文 |
| Cleanup Impact | 有价值.work能力转正式checker；原件最终WP-015删除；原tree lint仍须在最终原件处置后复验 |
| Failure Conditions | allow-draft作为strict、CI缺参数仍绿、只计算healthy数量、为消lint放宽ignore或删除测试 |
| Escalation Conditions | 隔离编排冲突/安全策略需改目标；ESCALATION-002 |

## WP-003：数据接管契约、迁移与恢复

| 字段 | 要求 |
|---|---|
| Objective | 证明受支持旧库安全升级，同时保留fresh与seed完整性 |
| Current State | BLOCKED；ESCALATION-001，REWORK-008/009 |
| Scope | adoption基线、备份/恢复、baseline/upgrade标签、migrator、Java租户provision、遗留SQL替代映射 |
| Implementation Requirements | 先裁决旧基线支持范围；保留09a29fe，禁止直接采纳candidate；在可恢复隔离旧库演练fingerprint→seed→backup→二阶段确认→adopt→增量migrate；fresh/replay/verify-seed独立跑；查清MQ schema.sql实际加载与AGENTS陈旧说明 |
| Constraints | 通用约束；forward-only、不可改已应用changeSet、不得伪造backup/confirmation，不执行生产down迁移 |
| Dependencies | WP-001；ESCALATION-001解除 |
| Acceptance Criteria | fresh与真实旧库路径均有可重放证据，失败恢复/二次执行安全；seed assertions/digest匹配；支持范围与备份绑定；无不明DDL双源 |
| Required Static Verification | adoption标签/指纹/资源classpaths、全部残留SQL与调用、seed manifest/hash、Flowable/vendor边界 |
| Required Tests | migrator/adoption/backup/seed/provision契约；fresh/replay/旧库upgrade/失败注入及恢复 |
| Required Runtime Verification | 隔离DB的schema fingerprint、迁移历史、seed、租户跨服务幂等；真实恢复证明 |
| Required Screenshot | N/A，DB结构/恢复用脱敏结构证据 |
| Required Evidence | 源/目标版本、备份存储引用与hash、恢复记录、确认摘要、变更集执行前后 |
| Cleanup Impact | 生成逐旧SQL→changeSet/fixture映射；G1通过后才进入WP-015 |
| Failure Conditions | fresh后adopt当旧库验收、替换指纹绕过漂移、直接删schema/存储过程来源 |
| Escalation Conditions | 接管语义、seed与旧库兼容、重大数据决定冲突；ESCALATION-001 |

## WP-004：SRM供应商详情E2E闭环

| 字段 | 要求 |
|---|---|
| Objective | 在当前修复基础上完成供应商创建/详情/动作及可证明清理 |
| Current State | IMPLEMENTED_NOT_VERIFIED；REWORK-004，最新保留run仍失败 |
| Scope | untracked supplier-detail spec/local配置及正式fixture整合，必要的确证产品缺陷 |
| Implementation Requirements | 先读当前代码；保留name/PageResult/version/单次保存/正确edit标题/APPROVING识别；定位最新失败；POST不确定提交按registry停止；撤回/删除走正式API并核实workflow/outbox/audit副作用 |
| Constraints | 通用约束；不重复修历史已修点、不强关弹层/延时掩盖；不SQL硬删审计 |
| Dependencies | WP-001；G2编排可独立先做本地单语 |
| Acceptance Criteria | 单语targeted先通过再四语，4执行0失败0skip；每次创建精确1条；超时无未知残留；清理和历史副作用可解释 |
| Required Static Verification | SupplierController/SrmRequests/当前view契约，mutation guard、分页全查与resource registry |
| Required Tests | 单场景→四语；正式ESLint与E2E TS；如生产改动执行相关后端测试/全构建 |
| Required Runtime Verification | 每次ID/tenant/run、业务状态、workflow撤回/终态、有效供应商及inbox/outbox审计核对 |
| Required Screenshot | 四语创建校验/成功/详情及关键动作；先候选图，WP-014审核后登记 |
| Required Evidence | 精确命令/当前spec hash、失败定位、单次写请求及清理摘要，不保存认证头 |
| Cleanup Impact | spec受保护成为正式E2E；local配置能力归入正式配置后才删重复件 |
| Failure Conditions | 无实际保存/分页空数组假清理/遗留未知提交/只看到列表即PASS |
| Escalation Conditions | 工作流副作用无安全闭环、两轮不收敛、需新身份；ESCALATION-002 |

## WP-005：采购fixture与现有环境数据裁决

| 字段 | 要求 |
|---|---|
| Objective | 为采购/资产提供可复现数据，不按旧报告直接恢复共享数据 |
| Current State | NOT_STARTED；13个bootstrap品类soft-delete是未本轮核实的历史线索 |
| Scope | procurement seed、manifest断言、隔离fresh fixture、旧运行数据的只读核对 |
| Implementation Requirements | 比对当前seed/13品类ID业务键与当前库；优先在WP-001隔离fresh构造；若必须修共享数据，单独证明tenant/IDs/codes/deleted/version/冲突及业务影响，提交精确事务方案后执行授权范围 |
| Constraints | 通用约束；不得假定deleted=1或盲跑旧cleanup SQL；不重写seed迎合污染数据 |
| Dependencies | WP-001；迁移问题依赖WP-003；共享数据恢复触发ESCALATION-004 |
| Acceptance Criteria | fixture来源明确，seed断言通过，数据可重复构造且无污染；恢复方案原子且前置失配零写入 |
| Required Static Verification | seed与hash/assertions、测试数据引用、领域安全规则 |
| Required Tests | fixture幂等/前置失配拒绝；后续WP006/007使用相同fixture |
| Required Runtime Verification | 指定tenant/业务键只读前后摘要、隔离种子校验；所有额外历史副作用分开登记 |
| Required Screenshot | N/A，数据前置证据由DB摘要证明 |
| Required Evidence | 查询范围/计数/业务键hash及环境身份、被恢复或新建集合，不含PII |
| Cleanup Impact | sample-data/临时SQL能力归正式fixture；保留旧来源到WP-015 |
| Failure Conditions | 使用历史状态作为执行前置、部分恢复/未检查冲突、改生产数据凑截图 |
| Escalation Conditions | 共享库恢复必要、当前数据与seed冲突；ESCALATION-004 |

## WP-006：采购与报价业务验收

| 字段 | 要求 |
|---|---|
| Objective | 验收物料→请购审批→RFQ→供应商报价→定点→PO→收货真实链 |
| Current State | IMPLEMENTED_NOT_VERIFIED；10个采购视觉gap |
| Scope | Procurement/SRM报价/Workflow接口、规则边界、真实DB并发及相关E2E |
| Implementation Requirements | 在现有实现/653afe3报价资产上增量补缺；覆盖规则五金额边界、具体/默认/无规则/重叠/失效工作流；预览与提交一致；报价重复事件、订单收货并发及拒绝 |
| Constraints | 通用约束及procurement设计；无Flowable依赖、aggregate-specific DataScope，金额精度遵守契约 |
| Dependencies | WP-001/005；真实outage依赖WP-002 |
| Acceptance Criteria | 全链真实提交与状态/版本正确；租户和角色负例、并发库存/收货约束有真实DB断言；无未知副作用 |
| Required Static Verification | API DTO、权限seed/hash、owner/requester映射、Outbox显式tenantId |
| Required Tests | 单元/DTO/mapper契约、真实MySQL事务并发、角色E2E、报价回归；不把Mockito并发名当DB证明 |
| Required Runtime Verification | Request→workflow→event→采购终态，RFQ/报价/PO/GR关联与inbox/outbox审计 |
| Required Screenshot | 四语物料/规则/请购/RFQ/报价/比价/PO/收货步骤与失败；规则三视口 |
| Required Evidence | 每一业务ID链、权限负例、事件幂等、真实测试与图映射 |
| Cleanup Impact | 保留既有报价/断言suite，退役临时probe需先沉淀能力 |
| Failure Conditions | mock代替真实链、跨租户泄漏、试算/提交不一致、仅happy path |
| Escalation Conditions | business contract/数据模型冲突、新身份不可得或两轮未收敛 |

## WP-007：资产完整生命周期验收

| 字段 | 要求 |
|---|---|
| Objective | 收货到建卡及领用/归还/调拨/处置可验收 |
| Current State | IMPLEMENTED_NOT_VERIFIED；10个资产视觉gap |
| Scope | Asset收货consumer/backfill/占用及审批、相关测试/E2E |
| Implementation Requirements | 正负assetQuantity/PASS过滤、双幂等、decimal strings；并发transfer/disposal占用；撤回/拒绝原状态与占用原子恢复；固定currentUser与管理DataScope分开 |
| Constraints | 通用约束及asset-design；不嵌Flowable、不为截图硬删审计 |
| Dependencies | WP-001/005/006收货fixture |
| Acceptance Criteria | 收货单位数与卡数正确，重复消息不重复；全部生命周期/并发/权限/拒绝恢复真实DB可验证 |
| Required Static Verification | DTO金额、tenant/owner/currentUser、occupancy transaction与idempotency键 |
| Required Tests | consumer/serialization/state machine/DB并发、端到端接收归还调拨处置 |
| Required Runtime Verification | GR-line→event→asset→approval全链、占用和恢复字段、inbox/outbox与审计 |
| Required Screenshot | 四语收货建卡/台账/领用/接收/归还/调拨/处置关键状态、失败与移动表单 |
| Required Evidence | 单位序号映射、精度、并发结果及角色/租户断言、图映射 |
| Cleanup Impact | backfill/验证能力归正式可重复工具；旧SQL保留至WP-015 |
| Failure Conditions | JSON金额number、重复卡、并发双占用、清理副作用未知 |
| Escalation Conditions | 资产业务/占用语义冲突或缺少审批身份 |

## WP-008：Workflow、任务及CRM完整覆盖裁决

| 字段 | 要求 |
|---|---|
| Objective | 补建模/会签与调度真实链，并核实CRM/调度的covered标签 |
| Current State | IMPLEMENTED_NOT_VERIFIED；Workflow4gap，CRM/任务covered仅声明 |
| Scope | 模型设计发布、候选人、MI审批、任务生命周期、CRM转化/阶段活动 |
| Implementation Requirements | 各子领域分独立小批验收；稳定fixture多审批身份；校验/发布/历史版本/MI_END/幂等完成；任务trigger/pause/resume/log及失败回滚；CRM转化/阶段/权限，而非只编辑对话框 |
| Constraints | 通用约束；工作流审计和部署须有隔离恢复路径；MyJob ownership保持 |
| Dependencies | WP-001；多审批身份与发布隔离由ESCALATION-002解除 |
| Acceptance Criteria | required_flows逐一有真实断言；工作流四gap关闭；CRM/调度无用单一三态代表全部流程 |
| Required Static Verification | 模型唯一键/锁/候选JSON/MI_END、job双注解/typeCode、CRM幂等与PII |
| Required Tests | domain targeted、真实调度/审批/API业务E2E、CRM租户/转化并发 |
| Required Runtime Verification | Flowable任务及部署、XXL-JOB任务/执行记录、CRM/Outbox闭环和清理 |
| Required Screenshot | 四语完整流程步骤、会签/失败/轨迹、触发暂停恢复日志、CRM转化和阶段推进 |
| Required Evidence | 子领域分别PASS/FAIL/BLOCKED，执行ID链及图/用例映射 |
| Cleanup Impact | 可复用BPMN/fixtures保留；旧脚本BPMN需与资源正本对照 |
| Failure Conditions | 用列表/历史图替代写入链、误判MI_END、任务外部记录孤儿 |
| Escalation Conditions | 候选策略/模型版本业务决定、共享引擎无法隔离 |

## WP-009：认证、管理与权限入口补齐

| 字段 | 要求 |
|---|---|
| Objective | 证明认证/系统管理完整范围及权限异常态，明确缺页面契约 |
| Current State | IN_PROGRESS；config/login-record入口未设计裁决 |
| Scope | 认证实际协议、系统配置/登录记录、租户组织用户角色菜单DataScope、会话/XSS/审计 |
| Implementation Requirements | 先Codex裁决缺入口产品契约；OAuth授权记录不能冒称登录日志；按canonical API设计受控读写权限、seed/菜单/四语；真实登录/设备协议与展示mock分级；管理每类关键动作分别覆盖 |
| Constraints | 通用约束；不绕验证码/造生产后门；auth不加OperLog；自注册/默认USER例外遵守AGENTS |
| Dependencies | WP-001/002；缺入口设计依赖ESCALATION-003 |
| Acceptance Criteria | 2个管理缺口有获批业务入口并验收；认证/RBAC/DataScope/XSS负例与审计真实；外部provider未实网明确外部范围 |
| Required Static Verification | 真实Controller/DTO/权限/日志存储、注册例外、前后端权限、三语说明 |
| Required Tests | auth/gateway/starter单元与跨租户/内部头/XSS集成；真实认证及管理E2E |
| Required Runtime Verification | 正确401/403、菜单可见/后端拒绝、日志分类、配置缓存失效，敏感数据脱敏 |
| Required Screenshot | 四语认证/管理每流程关键状态、403/404/会话过期；模拟图显式标记 |
| Required Evidence | 路由/权限/实际接口/日志类型、真实与mock场景清单、人工验证码或受控身份来源 |
| Cleanup Impact | 保留认证fixture/所有负例，不删missing flow规避范围 |
| Failure Conditions | OAuth授权表冒充login log、mock证明真实登录、无后端鉴权 |
| Escalation Conditions | 配置数据模型/安全策略未定；ESCALATION-003 |

## WP-010：可靠MQ与可观测运维验收

| 字段 | 要求 |
|---|---|
| Objective | 证明消息失败处理和同步/异步Trace排障，而非仅配置存在 |
| Current State | IMPLEMENTED_NOT_VERIFIED；MQ4gap，operations missing |
| Scope | outbox/relay/inbox、retry/dead-letter/manual操作、观测栈/看板/告警/SLO |
| Implementation Requirements | 在隔离环境生成受控可恢复失败；验证退避/DEAD_LETTER/重发跳过权限；HTTP/Feign/MQ trace关联；采集指标日志/告警与关闭观测回归；依据旧性能方法重用或验证变化 |
| Constraints | 通用约束；不向共享outbox注入毒消息；relay跨租户扫描与查询tenant隔离不混淆 |
| Dependencies | WP-001/002；隔离故障授权ESCALATION-002 |
| Acceptance Criteria | 状态机/重试次数及时间、权限/租户/关联trace、真实告警和恢复路径有证据；观测可选关闭不影响业务 |
| Required Static Verification | 显式tenantId、retry配置、W3C/producerTrace、指标维度与secret外置 |
| Required Tests | relay/sender/consumer targeted、真实MQ重投递、观测开关与故障流程 |
| Required Runtime Verification | MQ/DB状态、Trace查询、指标/日志/alert恢复；性能采样环境和误差范围 |
| Required Screenshot | 四语消息失败/死信/详情/操作；运维UI无语言切换时由Codex记录实际语言及指南映射，不伪造翻译图 |
| Required Evidence | 事件ID/TraceID映射、脱敏查询输出、告警触发/恢复时间、图审核 |
| Cleanup Impact | 临时log/probe提炼正式排障指南与工具；原件受限 |
| Failure Conditions | 仅看到SENT判全状态完成、mock替代依赖中断、无异步Trace关联 |
| Escalation Conditions | 无隔离故障边界、观测图语言适用范围需裁决 |

## WP-011：脚手架、预设与部署复现

| 字段 | 要求 |
|---|---|
| Objective | 完成service/CRUD/五预设的真实交付矩阵及新手开发能力 |
| Current State | IMPLEMENTED_NOT_VERIFIED；源/tests存在，coverage未交付措辞陈旧 |
| Scope | CLI/catalog/schema/templates、五预设、lite/full、dev/doctor、Docker/deployment |
| Implementation Requirements | 保留生成器安全写入/所有权；复用已有golden/runtime测试，补覆盖缺口；全部五预设fresh与权限/租户/业务smoke；生成物无本仓/本机依赖；新服务内置安全/观测能力 |
| Constraints | 通用约束；新目录输出，不原地裁剪；目录边界/原子回滚与forward-only SQL要求 |
| Dependencies | WP-001/003数据能力、WP-002门禁 |
| Acceptance Criteria | G4/G5全部通过，lite/full同契约且资源/耗时测量；文档不读源码可生成/自定义 |
| Required Static Verification | catalog依赖闭包、锁/模板/生成物diff、Compose/routes/permission/seed一致 |
| Required Tests | CLI npm test及全部service/CRUD/preset goldens，五预设runtime；生成物后端build/前端build/lint |
| Required Runtime Verification | 独立生成物启动/迁移/登录、tenant/permission及至少一条可用业务链；doctor失败诊断 |
| Required Screenshot | 新服务/CRUD/预设/doctor操作证据；Web生成物四语图，CLI命令用脱敏文本/终端证据 |
| Required Evidence | descriptor/catalog hash→生成物commit→运行镜像/测试，五预设逐格结果 |
| Cleanup Impact | golden/fixtures/工具均长期保留；生成输出不是生产源，可验证后退役 |
| Failure Conditions | 用两预设smoke代替五runtime、只build不启动、静默覆盖定制 |
| Escalation Conditions | catalog与领域服务边界冲突、重大生成协议兼容决定 |

## WP-012：UI四语言质量与错误态

| 字段 | 要求 |
|---|---|
| Objective | 解决键齐但内容未译、后端中文错误、响应式与类型质量缺口 |
| Current State | IN_PROGRESS；key/placeholder门PASS，语义未验收 |
| Scope | 四语locale、管理/业务弹层错误、后端错误契约及前端类型 |
| Implementation Requirements | 当前界面/源码证据定位日/韩英文残留、dict必填后端中文等；逐项业务等价修正，不批量刷语言状态；保留错误码/trace与安全文案；与WP009契约联动 |
| Constraints | 通用约束；不得把后端业务异常吞掉或只改截图；不改变API稳定字段 |
| Dependencies | WP-001；相关domain WP可分批提供稳定页面 |
| Acceptance Criteria | 四语关键UI/错误自然、无布局溢出/未译key；标准lint0/0和build通过，现有业务断言不退化 |
| Required Static Verification | locale strict parity、ui check、E2E TS、真实错误路径与权限 |
| Required Tests | targeted组件/页面实际错误→相关E2E；frontend build/lint；后端变更按AGENTS全build |
| Required Runtime Verification | 四语同一操作/异常渲染、三视口、401/403与trace显示 |
| Required Screenshot | 受影响图重拍并人工审核，原图到替代图获批前保留 |
| Required Evidence | 逐token/界面Finding与修正、业务语义复核、实际渲染和测试 |
| Cleanup Impact | 6个patch必须证明已归并/不丢唯一修改后才删除 |
| Failure Conditions | parity绿代替翻译质量、改baseline/隐藏错误消红 |
| Escalation Conditions | 术语/错误契约需领域决策或独立语义复核不可取得 |

## WP-013：正式文档事实归并与语义复核

| 字段 | 要求 |
|---|---|
| Objective | 当前事实有唯一源，四语准确并完成真实独立签核 |
| Current State | IN_PROGRESS；102译文未审核，历史中间文档待归并 |
| Scope | 正式docs/README/docs-manifest/review队列、旧plan/audit/handoff有效内容 |
| Implementation Requirements | 按MASTER20项与旧详细计划要求逐条映射到canonical；纠正SQL初始化/端口/模块/CLI与观测状态；优先当前改动译文并查客观缺漏；规范token/代码块/权限/枚举忠实，逐文逐语种实审记录源与译文hash/人/日期 |
| Constraints | 通用约束；不批量填写synchronized/reviewed_at、不把结构ALIGNED当语义；只更新实际审核记录 |
| Dependencies | WP-002队列；相关实现稳定后签核，静态对账可先行 |
| Acceptance Criteria | 全目标无遗失、每个事实唯一canonical；102及最终应有译文真实审核；links/readme/i18n strict/queue/preset docs通过 |
| Required Static Verification | 当前源码/API/seed/Compose事实、源digest、未译稳定token/缺章/链接，历史要求逐条映射 |
| Required Tests | 文档checker/命令验证；用户/维护者分别按指南走查 |
| Required Runtime Verification | 新手启动登录/权限/主业务、自定义预设与故障恢复；不得依赖旧handoff或本机残留 |
| Required Screenshot | 引用已通过WP-014的正式图，编号/语言/步骤准确 |
| Required Evidence | 语义复核记录、源/译hash、规范→文档映射、走查操作与结果 |
| Cleanup Impact | 有效历史信息先归正式docs/ADR，确认无唯一信息后WP-015删除 |
| Failure Conditions | Agent签名造假、译文落后源、改MASTER缩范围、复制第二事实源 |
| Escalation Conditions | 当前代码与长期契约冲突、外部语义签核未取得（登记BLOCKED部分） |

## WP-014：全流程视觉Evidence验收

| 字段 | 要求 |
|---|---|
| Objective | 按业务流程/状态完成四语图证据，而不是提高图片数 |
| Current State | IN_PROGRESS；230记录，36gap，covered模块也需验证语义 |
| Scope | screenshot-manifest/coverage、docs/images及所有正式文档flow |
| Implementation Requirements | 逐required_flow×关键state×role×locale×viewport建映射；现有230图先审核版本/用例/敏感/布局，仍可复用的不重拍；缺口随领域WP产图；改正CLI/obs陈旧原因；display/mock记录来源类别 |
| Constraints | 通用约束；不删required flow、不自行exempt；生成≠审核；不把失败截图转正式 |
| Dependencies | WP-002、004、006～013按场景稳定后汇总 |
| Acceptance Criteria | 所有必需场景/状态有当前证据或Codex明确不适用决定，strict通过；无失配/敏感/未加载/截断图；4语与关键三视口符合MASTER |
| Required Static Verification | 每图路径/test/fixture/hash/版本/生成与审核时间、无孤图/重复ID；flow/state反查完整 |
| Required Tests | 每受影响文档用例先targeted、批次重放；断言式E2E结果独立关联 |
| Required Runtime Verification | 捕获的是已知镜像真实页面；涉及mock/fault只证明其明确层次 |
| Required Screenshot | 本WP自身即验收全部正式场景；人工逐图审，不批量自动声称PASS |
| Required Evidence | 逐图审核、覆盖映射、替代前后digest、隐私检查和实际执行摘要 |
| Cleanup Impact | 旧official替代验收后退役；临时失败图/Trace保全后按WP-015处置 |
| Failure Conditions | 单个字典动作替全部系统动作、covered仅手写、无效历史图凑数量 |
| Escalation Conditions | 缺业务能力、外部UI语言/三视口不适用需裁决、身份或故障边界不明 |

## WP-015：仓库清理与治理产物收敛

| 字段 | 要求 |
|---|---|
| Objective | 按manifest实现只保留长期资产的HEAD，保护所有可复用测试 |
| Current State | DEFERRED；明确后置于G1～G7；本轮未删除 |
| Scope | CSV所有候选，旧SQL/脚本/BPMN/patch、旧过程docs、临时runtime、新治理包退役 |
| Implementation Requirements | 先保全受限证据→归并事实/决策→重构测试/工具→七域引用及替代验证→精确小批删除→全新checkout验收；逐path保留实际动作/原hash/替代hash/引用处理；先完成产品候选Review，最后删除已归并本轮handoff/WP/状态/临时索引 |
| Constraints | 通用约束；禁止目录盲删/git clean/reset、不可变migration或E2E删除；Trace原件保全决定之前只DEFER；不操作外部TEMP/用户文件 |
| Dependencies | WP-003及G1～G7真正PASS、WP-013归并、WP-014正式图审核；final治理退役依赖Codex候选Review |
| Acceptance Criteria | 每个候选前置可验证、无悬空引用、正式测试全保留；SQL仅seed且无DDL；新checkout完整启动/测试；原工作保全及处置有记录 |
| Required Static Verification | CSV更新/新增文件/七域引用/动态glob/SQL分布/diff/secret/文档链接 |
| Required Tests | 替代能力targeted→清理后fresh/upgrade/五预设/全量E2E/安全；纯治理封存diff只做范围/链接检查 |
| Required Runtime Verification | 删除后新checkout完整启动、迁移和主链，不依赖本机中间文件 |
| Required Screenshot | 保留已审核official/baseline；只有实质UI变化才重拍 |
| Required Evidence | 精确处置表和替代验证、清理commit、全新checkout结果；脱敏最终证据归正式发布记录 |
| Cleanup Impact | 最终handoff/WP/current-state/CSV/verification均退役；长期规则留repository-governance/MASTER/正式docs |
| Failure Conditions | 测试/fixture丢失、未知引用未解决就删、秘密原件上传、重复状态页继续堆积 |
| Escalation Conditions | 删除前置不满足、唯一信息/用户未提交改动无法归属、保全要求冲突 |

## WP-016：最终候选验证、远端与独立Review

| 字段 | 要求 |
|---|---|
| Objective | 提供可独立审查的最终交付候选，完成Codex终审与封存 |
| Current State | NOT_STARTED；仅双remote分支SHA已核对 |
| Scope | MG01～20、G0～G8、安全/发布/恢复、cleanup最终diff及Evidence |
| Implementation Requirements | 汇总同一候选树证据；执行必要最终全门禁，记录所有skip；检查运行镜像/源码/依赖一致；准备版本/升级/回滚/备份/恢复指南与发布材料；远端推送/合并/发布按既有授权范围执行，不擅自force |
| Constraints | 通用约束；Qoder只能READY_FOR_CODEX_REVIEW，不可FINAL PASS；remote绿不代替系统验收 |
| Dependencies | WP001～014验收，WP015产品清理；最后纯治理封存待Codex审查 |
| Acceptance Criteria | MG/Gate全有可重现当前证据，无必需BLOCKED/未决REWORK；Codex独立审查PASS；封存diff只删已归并治理产物且链接有效 |
| Required Static Verification | 全diff、schema/seed/版本、保护清单、Evidence/remote SHA/发布材料、保留文件secret扫描 |
| Required Tests | Maven全reactor clean install；frontend build/lint；CLI全golden/preset矩阵；真实集成/全E2E；docs/i18n/screenshots strict与安全，按变更复用已绑定证据 |
| Required Runtime Verification | 新checkout部署、健康/API/browser业务链、升级/备份恢复演练、实际目标remote和release身份 |
| Required Screenshot | 正式图全已验收且与候选一致；不为最后文档封存重复批量截图 |
| Required Evidence | 最终Gate矩阵、测试计数、candidate SHA/镜像/工具版本、恢复与release记录、Codex独立Review |
| Cleanup Impact | 确认临时治理体系退役；受限原始证据不随公开交付上传 |
| Failure Conditions | 未执行/skip伪绿、未绑定历史日志、缺语义审核、未决必需阻断、自行宣布完成 |
| Escalation Conditions | 任何门失败形成REWORK；需改目标/验收或发布授权不足回交Codex |

## ESCALATION-001：旧库adoption支持范围与备份证据

- Problem：历史报告把fresh后指纹漂移当旧基线错误，并建议替换candidate；缺受支持旧库真实备份/恢复证据。
- Observed Evidence：AdoptionService先verifyAll/seed/backup，再二阶段确认；AdoptionLabelContractTest把baseline与upgrade隔离；默认资源仍baseline-09a29fe；根candidate未启用。
- Relevant Contract：MASTER MG09、ADR D-04、不可变migration和外部BackupVerification。
- Current Implementation：新schema与旧09a29fe指纹可不同，fresh→adopt本身不足以证明产品缺陷。
- Why Current Plan Cannot Continue：直接替换会改变受支持旧库边界并可能错误标记upgrade已执行。
- Options：A（推荐）维持09a29fe旧库接管，恢复该版本副本后验证adopt→upgrade；B若要支持更多已漂移旧库，为每个受支持版本新增不可变基线和独立映射/ADR；不得将当前fresh库冒充旧库。
- Risk：丢升级、重复DDL、错误seed历史或无法恢复。
- Decision Needed：Codex确认支持版本/标签映射方案；运维提供对应真实备份/恢复/环境身份。解除前WP003写入部分BLOCKED，只读分析可继续。

## ESCALATION-002：隔离环境、短期身份与故障注入边界

- Problem：已有omni-wp09-docs栈包含历史状态；新镜像与源码、角色/审批人身份未绑定；共享Workflow/MQ故障与写入不能安全假定。
- Observed Evidence：15运行容器latest镜像，E2E依赖token变量，CI缺必填Secret且outage阶段有逻辑错误。
- Relevant Contract：ADR D-05、MASTER MG02/04/18、repository-governance §4。
- Current Implementation：静态Secret传递不验证issuer/租户，故障阶段需要实际停服务，Flowable/XXL-JOB有持久副作用。
- Why Current Plan Cannot Continue：运行对象和权限不明确时无法界定写入/故障/清理范围。
- Options：A（推荐）新隔离project、独立端口/卷、fresh数据、同环境短期fixture身份，明确admin/employee/supplier/审批人；B受控独占现有测试环境并先验证保全/恢复范围。
- Risk：污染共享任务/MQ、跨环境token无效、旧镜像造成错误归因。
- Decision Needed：明确允许使用的隔离目标和身份来源；已有用户授权可覆盖的独立安全环境准备直接执行，无需重复询问；缺少权限/身份时仅对应WP BLOCKED。

## ESCALATION-003：系统配置与登录记录的业务契约

- Problem：coverage明确要求config和login-record，但源码/路由未提供同名view；OAuth授权记录不能代替登录日志。
- Observed Evidence：system视图清单及coverage的2gap、authrecord对应OAuth；旧说明把缺页面当产品未提供。
- Relevant Contract：MASTER MG02/03/18与原R-09主要管理流程。
- Current Implementation：已有sys_login_log及认证审计能力；配置域对象/接口范围尚未在该缺口定义中确定。
- Why Current Plan Cannot Continue：Qoder不能为凑图随意新增配置数据模型或自行豁免required flow。
- Options：A（推荐）Codex设计登录记录读接口/页面与明确的系统配置对象及resource:action权限；B若已有等价入口，提供真实路由/API及用户操作对照并由Codex裁决等价，仍保留原功能要求。
- Risk：引入第二配置源、泄露认证PII、错误授权或业务范围偷减。
- Decision Needed：数据/API/权限/保留与脱敏契约。其他现存管理流程WP009可先验证，缺口实现待设计裁决。

## ESCALATION-004：共享采购种子恢复

- Problem：历史报告称tenant1的13个bootstrap品类被软删，本轮未读库确认。
- Observed Evidence：当前seed/manifest存在、历史恢复前置记录；没有当前DB前后快照。
- Relevant Contract：MG09、procurement tenant/DataScope、WP005原子前置验证。
- Current Implementation：seed与runtime数据可能随人工操作变化，不能从旧报告推断当前可恢复集合。
- Why Current Plan Cannot Continue：直接恢复会修改既存业务数据且可能触发冲突。
- Options：A（推荐）fresh隔离fixture完成验收，不依赖修共享栈；B确认仍需修复后提供精确tenant/IDs/codes/前后条件/事务/回滚审查包，仅获准范围操作。
- Risk：覆盖合法删除、唯一键冲突、业务层级错误及隐藏历史副作用。
- Decision Needed：是否需要恢复共享数据及具体受控方案；缺少决定不阻止隔离fresh验证。
