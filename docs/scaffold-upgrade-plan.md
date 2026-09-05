# Omni-Stack MASTER DELIVERY GOAL

本文是整个项目最终交付契约（TARGET STATE + QUALITY GATES），2026-09-05 原位重建。原升级路线图 R-01～R-10 全部保留为目标，撤销旧成熟度/完成百分比表达。本文不存滚动状态；当前事实、WP 与执行入口见 [Qoder Handoff](evidence/project-consolidation/qoder-handoff.md)。治理与证据规则见 [repository-governance](repository-governance.md)。

## 1. Project Objective 与目标来源

交付一个可运行、可生成、可裁剪、可维护、可验证、可部署的多租户微服务脚手架。新使用者能仅依赖 README/指南选择预设、生成项目、初始化、登录、配置权限并走通一条完整业务链；维护者能增加服务、标准 CRUD 和自定义预设而不复制横切安全实现。

目标恢复链：最早可定位的初始化 `762df18` → 认证/OAuth2/RBAC/安全/任务/工作流/可靠 MQ → CRM `995bfa0` → SRM `525a2c5` → 采购资产闭环 `09a29fe` → 2026-08-17 R-01～R-10 原路线图 → 2026-08-20 详细计划与 Accepted ADR-0001 → 本轮用户治理授权。完整历史聊天原文不在仓库，本轮不声称逐字恢复不可取得的原始对话；以可追溯 Git 文档及 ADR 的接受记录恢复承诺，不按当前实现缩小范围。详细考古及可信度见 current-state。

原详细计划保留到约束逐项归并完成，其历史 G0～G8 与 D-01～D-09 继续追溯。若细节冲突，Codex 裁决并登记 ADR；Qoder 不自行选择较低标准。

## 2. Functional Scope 与目标架构

| MG-ID | 最终目标 / 可观察的交付行为 | 长期契约来源 | 原始追溯 |
|---|---|---|---|
| MG-01 | JDK25 / Spring Boot4 + Vue3/TS 的 monorepo；8 个独立服务 Auth/Base/Gateway/Workflow/CRM/SRM/Procurement/Asset，公共 Starter 与一次性 migrator；Gateway WebFlux 与 Servlet/阻塞 Redis 隔离 | architecture、backend/frontend-patterns、AGENTS | 初始化及 D-02 |
| MG-02 | 密码+CAPTCHA、自注册、OAuth2 PKCE、设备授权、GitHub/Google/Gitee、账号锁定/登录审计；租户/RBAC/组织/数据范围/菜单/按钮一致；XSS 三层防护、内部调用鉴权和 Gateway 安全头 | core-flows、api-contract、authentication/permissions guides | 早期认证与安全扩展 |
| MG-03 | Base 字典、操作日志/审计、在线会话、系统任务、任务类型、我的任务调度/触发/暂停/恢复/日志；系统管理范围中的配置与登录记录须有明确业务操作入口或经架构裁决的等价路径 | scheduling、core-flows、system-security-audit guide | 任务/安全功能与 R-09 |
| MG-04 | Workflow 建模/BPMN/候选人/校验/发布、版本追踪、发起/审批/会签/撤回/完成/失败恢复；实例保留原部署版本，领域服务只调用 Workflow | workflow、API §16 | 独立 workflow 与会签 |
| MG-05 | CRM 线索→客户/联系人/商机的幂等转化，客户360、阶段历史、活动；租户、DataScope、PII保护与 Outbox 一致 | crm、crm-design、API/core-flows | CRM 扩展 |
| MG-06 | SRM 邀请/入驻/准入审批/生命周期、门户角色 Saga、资质/联系人、评价/风险、供应商询价邀请与报价；supplier 身份不写内部 owner | srm-design、API §15、srm-flow | SRM 扩展 |
| MG-07 | 物料→请购审批→RFQ→门户报价→比价/定点→PO→收货质检；规则向导、只读匹配/覆盖/影响预览使用真实服务端解析；具体品类优先、金额边界/重叠/无规则/降级/权限均可验收 | procurement-design、API §17 | 采购闭环、R-01、D-08/09 |
| MG-08 | PASS且assetManaged、正整数assetQuantity 的收货建卡；双幂等；台账→领用/接收/归还→调拨/处置审批；原子占用/恢复；金额 decimal strings | asset-design、API §18 | 资产闭环 |
| MG-09 | Liquibase forward-only schema/vendor/upgrade，空库、旧库接管、重放、失败恢复、种子校验；Java 幂等租户 provisioning 替代存储过程；备份/恢复证据与旧基线绑定 | ADR-0001 D-04、database 目录、deployment guide | R-10、旧 WP-00 |
| MG-10 | Servlet 公共 Starter 组合 tenant/Gateway预认证/DataScope/internal/XSS/audit；CRM/SRM/Procurement/Asset 采用，Base适用项明确，Auth/Gateway保持特例 | architecture、backend-patterns、ADR D-02 | R-03 |
| MG-11 | 一条 create-service 命令生成可构建、可启动、安全边界完整的新服务；plan/dry-run/冲突检测/原子写入/失败恢复/所有权，不静默覆盖 | scaffold catalog/schema/templates、CLI、开发指南 | R-02、D-01/03 |
| MG-12 | 声明生成 SQL替代changeSet、DTO/Service/Controller、权限/菜单、前端API/路由/页面及测试；支持安全类型映射、再生成漂移与所有权；不生成复杂Saga/状态机 | CRUD descriptor、CLI、API/patterns | R-04 |
| MG-13 | core/workflow/crm/supply-chain/full 五预设各能 fresh 生成、构建、启动、登录、权限/租户验证；同一catalog驱动依赖闭包；新目录生成；四语自定义预设维护教程 | scaffold/catalog、presets、preset guides | R-05、D-03/07 |
| MG-14 | Compose profiles/CLI dev/doctor 支持按需启动；lite/full核心契约一致，记录启动时间与内存对比；缺失可选服务清楚降级 | Compose、CLI、deployment/quick-start | R-06 |
| MG-15 | 同步HTTP/Feign及异步Outbox/MQ的 Trace关联；指标/结构化日志/看板/告警/SLO；可关闭观测profile，Secret外置；失败能按Trace ID定位 | observability、mq-reliability、observability配置 | R-07、D-06 |
| MG-16 | 前端标准 lint 为0 error/0 warning；强类型与四语 UI 自然一致，key/placeholder一致且无中文/英文残留冒充翻译 | frontend-patterns、locale文件、eslint | R-08、R-09 |
| MG-17 | 中/英/日/韩 README、架构/API/开发部署运维与11组主要业务/二次开发指南语义一致、版本正确、链接有效、复核签核真实；新手可独立走查 | docs-manifest、正式docs/guides与README | R-09 |
| MG-18 | 每条主要流程有断言型真实E2E及步骤视觉证据；四语、关键三视口、角色/异常态；真实业务与display/mock/fault证据分级；截图能追溯当前版本 | e2e、e2e-docs、manifest/coverage | R-09、D-05 |
| MG-19 | 生产源码/正式测试/fixture/CI/工具/迁移保留；一次性脚本、旧交接/重复状态与临时运行产物完成分类/替代/引用验证/清理 | repository-governance、cleanup manifest | R-10、本轮治理 |
| MG-20 | 全门禁可复现、候选提交明确、远端一致、升级/备份/恢复/回滚说明及演练、发布产物可追溯、脱敏证据和Codex最终独立审查 | CI、release记录、正式运维指南 | 原最终DoD |

目标版本以当前 POM、lockfile、Compose 和 AGENTS 核对，不以旧报告或运行中的 latest 标签代替。所有 Controller 使用 R<T>/PageResult；日期 yyyy-MM-dd HH:mm:ss；租户/权限由后端强制执行；Outbox必须显式tenantId，后台跨租户relay不能被业务租户拦截阻断。涉及领域细节必须读取对应 canonical 文档，禁止把此概览当完整业务规格。

## 3. 明确边界与未决事项

本目标不隐式新增财务、合同、开票、回款、折旧、盘点、维修等业务域；不重写稳定状态机；不将 Flowable 嵌入领域服务；不把阻塞Redis引入Gateway；不原地裁剪用户项目；不让生成器自动合成复杂Saga/幂等审批补偿。

目标生产环境的容量/灾备、真实第三方OAuth实网验收和独立渗透测试属于原计划明确外部专项，不能拿本地模拟宣布这些通过。框架内部认证/回调协议、安全与恢复能力仍必须验证；某次发布若宣称具体外部集成已可用，必须提供该集成真实授权验收记录。其余目标不得以缺页面/缺身份/环境困难自动DEFERRED。

D-04最终SQL目标保持：`.sql`只在`scripts/sql/seed/`承载幂等种子；schema/vendor/修复进入不可变Liquibase YAML，sample/test数据转正式fixture。当前残留SQL，包括common-mqlog/schema.sql，删除前必须确认运行加载、迁移标签、fresh/旧库adoption/upgrade和测试引用。旧AGENTS的自动建表描述与现代码/目标的冲突要先对账，不能照旧规则重引入运行DDL，也不能先删文件逼迁移。

旧库adoption须验证“受支持旧版本无Liquibase历史→校验/备份/接管→增量migrate”，不能用“当前fresh库指纹不等于旧09a29fe”直接证明旧基线错误。`baseline-candidate.yaml`未经裁决不得替换已冻结基线。配置/登录记录入口及身份隔离范围见本轮ESCALATION登记，结论未定时对应WP停止，完整功能目标保持。

## 4. TEST / E2E STRATEGY

顺序为静态契约→单元→契约/真实依赖集成→真实业务E2E→视觉/文档→候选部署与最终门禁。当前源码、fixtures与运行镜像绑定后复用无变化证据；必要测试不得以token缺失、skip或宽松选项绕过。每个失败形成WP/REWORK，先定位再运行；完整Maven/frontend build/E2E由Qoder在实施阶段执行，本轮不执行。

| 验证范围 | 必需断言 / 失败边界 |
|---|---|
| 认证/权限 | 有效与无效登录/CAPTCHA、自注册角色、PKCE/设备协议、provider callback错误、过期/锁定/登出、跨租户/角色/DataScope/PII/XSS/internal token；401与403区分 |
| 任务/Workflow | 我的任务所有权与XXL-JOB注册失败回滚；触发/暂停/恢复/日志；模型唯一键、发布锁、校验、候选角色范围、MI_END会签结果、幂等启动及重复完成事件 |
| CRM/SRM | 线索重复转化、无权子资源、租户隔离；邀请requestId、角色Saga补偿、入驻/报价状态机、重复消息、可回收fixture及审计副作用 |
| Procurement/Asset | 规则金额0/9999.99/10000/99999.99/100000、category/default/overlap/no-match；试算与真实提交同一路由；RFQ报价PO收货；正整数建卡、重复事件、并发收货/占用、拒绝/撤回恢复与金额精度 |
| migration/provision | fresh、同库重放、旧基线接管后upgrade、失败恢复/二阶段确认、manifest checksum/断言、跨服务租户幂等provision；真实DB的事务/唯一键/乐观锁，不只Mockito |
| CLI/预设 | 单元、service/CRUD goldens、生成物后前端构建及真实runtime、所有权/冲突/恢复、五预设fresh/权限/租户/业务smoke、lite/full对照 |
| MQ/观测 | PENDING→SENT、重试/退避/死信/手动处理、异步inbox/outbox幂等；Trace跨HTTP和MQ、指标/日志/告警查询、观测关闭后业务不受影响 |
| UI/文档/视觉 | 四语key/placeholder/语义与服务端错误；响应式/权限/失败态；每流程每关键状态映射至当前用例/图片/审核；新手仅读指南走通业务 |

## 5. QUALITY GATES

以下是验收标准；脚本实现不完整时修脚本能力，不改标准。全部要求未满足时不得FINAL PASS。各门状态只在current-state/活动WP登记。

| Gate | PASS条件 | 必需执行入口与证据 |
|---|---|---|
| G0 基线/构建 | 候选树、依赖和环境明确；后端全reactor clean install通过；前端build、标准lint为0/0；无不明skip | JDK25先设JAVA_HOME；`omni-backend/mvnw clean install`（Windows mvnw.cmd）；frontend `npm run build`、`npm run lint`；原始摘要和测试XML |
| G1 数据 | MG-09全部链路与恢复完成，备份/确认/旧库范围有真实证据，seed与真实DB一致 | migrator `migrate`/重放/`validate`/`verify-seed`；受控旧库`adopt-current`；不得本轮运行或伪造备份 |
| G2 审批与业务 | 规则设计/权限/冲突/依赖中断/历史兼容/三视口和真实请购提交一致；现有八服务主链无回归 | targeted Java contract + `npm run test:e2e`及领域E2E；真实故障隔离、身份、数据/副作用证据 |
| G3 Starter/安全 | 四试点采用及Base/Auth/Gateway特例符合设计；tenant/DataScope/internal/XSS及异常清理真实验证 | common-service与四服务安全测试、跨租户/内部调用集成、配置/自动装配契约 |
| G4 生成器 | service/CRUD可独立构建运行，安全/权限/数据与再生成契约成立 | CLI `npm test`、`test:golden`、`test:crud-golden`、`test:crud-db-golden`、`test:crud-runtime-golden` |
| G5 预设/轻量 | 五正式预设全部fresh构建启动/权限租户业务smoke；lite/full对照有测量 | CLI `test:preset-structure`、`test:preset-golden`、`test:preset-runtime`、dev/doctor；两预设smoke不能代替五预设runtime |
| G6 观测/运维 | 同步异步Trace、真实指标/日志/告警、关闭profile回归与排障步骤 | observability配置与运行查询，MQ重试/死信场景；必要运维视觉证据 |
| G7 四语/视觉/新手 | 102现有译文及最终范围全部真实签核；所有必需流程/状态/语言/视口可追溯；无未裁决gap；新手走查与README事实一致 | docs-quality `--scope=links/readme/i18n/screenshots/sensitive`分别strict；docs-review-queue、screenshot-manifest-sync、locale-parity `--strict`、ui-i18n-audit `--check`、`docs:preset:check`；人工语义/图像审核 |
| G8 清理/交付 | G1～G7完成；替代和七域引用检查；测试不丢失、仅种子SQL、正式文档/图片准确；清理后新checkout可部署及全量回归；双remote/发布/恢复证据和独立Review | cleanup逐项处置、fresh/upgrade/五预设/全量E2E/安全/链接；受影响源变化触发对应门，无变化不等价重复全跑 |

安全横贯所有门：扫描保留源码/配置/文档/patch/env模板和压缩运行证据，依赖审计覆盖实际生产依赖；不得靠当前CI排除路径或仅critical阈值掩盖已知可利用问题。已确认风险须修复或由Codex记录可审查处置，Qoder不能自行接受风险。CI所需Secret只描述来源，不把真实值写入仓库/日志。

## 6. 最终 Definition of Done

MG-01～MG-20逐项有当前证据，G0～G8及横向安全要求PASS，无未决必需项/REWORK；外部专项边界明确且没有冒充完成。所有必需测试实际执行，真实E2E与展示mock分清，四语内容经过真实审核，图片覆盖按流程/状态判断。新项目与自定义预设按文档可复现；现有库升级、备份恢复和应用回滚路径经过演练。全新checkout不依赖本机残留；生产代码、可复用E2E/fixtures、quality gates受保护，临时治理体系本身完成归并与清理。

交付候选包含明确commit、版本/依赖锁、schema/seed版本、测试及镜像摘要、脱敏Evidence、远端引用与发布记录。Qoder仅提交READY_FOR_CODEX_REVIEW；Codex依据目标→diff→源码→测试→E2E→runtime→截图→docs→cleanup→Evidence→门禁独立裁决PASS或REWORK_REQUIRED。远端一致与Git clean分别是交付证据，不可作为系统通过的充分条件；不得reset用户工作区取得clean。
