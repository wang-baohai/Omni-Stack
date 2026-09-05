# CURRENT FACTUAL STATE / PROTECTED BASELINE

2026-09-05；本文件为 TEMPORARY GOVERNANCE ARTIFACT，是本轮唯一状态快照。目标见 [MASTER](../../scaffold-upgrade-plan.md)，后续状态只在 [WP](work-packages.md) 更新。历史报告不再充当执行入口。机器记录见 [verification.json](verification.json)，全路径资产处置见 [cleanup-manifest.csv](cleanup-manifest.csv)。

## 1. 总体裁决及调查边界

总体实施状态：**IN_PROGRESS**。存在丰富的业务实现、迁移、CLI、预设、可观测性、四语言和测试资产；当前不能宣布最终系统验收通过。构建历史PASS、230个manifest图记录、四组coverage covered、远端一致均不能代替完整G0～G8。

本轮实际执行Git/文件/引用/契约分析、只读Docker容器状态、双remote查询、九项轻量文档/语言检查、一次标准ESLint等价检查，以及Trace认证头计数；没有运行Maven、frontend build、E2E、截图、数据库查询/修改或环境操作。Docker/API业务/浏览器/DB事务/CI最新run没有形成与候选源码绑定的新验收证据。未取得的原始用户聊天、远端CI结果、历史完整原始测试日志明确不用于VERIFIED。

`verification.json`为本轮命令证据；其中initial_status在写治理文件前采集。命令输出的编码以JSON内UTF-8为准。Trace仅统计头名/数量，不输出值。后续新增治理文件及源文档摘要变化是本轮授权文档修改，不属于初始130个未跟踪文件。

## 2. Git 与 Working Tree

| 项目 | 当前可验证事实 |
|---|---|
| branch | `codex/scaffold-upgrade` |
| HEAD | `204bf874efe6a43e8f849c5d4321f84cef2b6764` |
| staged | 调查开始时无暂存diff |
| unstaged | 10个tracked文件，27行增加/12行删除：3份docker-deployment译文、2份authentication译文、5份历史状态/计划文件；详见initial_status |
| untracked | 开始时130个文件（git status简表的目录已展开）；含1份SRM spec、local Playwright配置、.artifacts、.work、6个patch、根截图、候选baseline、历史handoff |
| 资产总数 | 1,971 tracked + 130 untracked，不包含node_modules/target/dist/.git的内部文件 |
| Gitee branch | 本轮`git ls-remote`确认为HEAD |
| GitHub branch | 本轮`git ls-remote`确认为HEAD |
| Git工作规则 | 原工作区不reset、不clean、不代提交、不push；保存并审查既有改动，Qoder第一批从明确候选树开始 |

初次Docker管道/Gitee连接受sandbox限制，随后只读授权查询成功；不是当前BLOCKER。Remote查询只证明该分支，不证明merge、tag、发布或CI通过。新治理文档当前未提交，所以新handoff必须连同当前文件集交付，远端HEAD单独clone没有本轮治理内容。

## 3. Project Archaeology

| 阶段 / 可重定位证据 | 恢复的目标或变化 | 当前对账 |
|---|---|---|
| `762df18`（2026-05-28初始化）及当时README | 微服务脚手架、Spring Boot4/Vue3、Harness文档 | 当前19个Maven子模块、Vue应用和正式docs存在；初始化目标持续有效 |
| `f018fe6`、`929ac52`、`7c31675`、`3817ea8`、`6e766ab`、`d992cd1`、`5d8ca32` | CAPTCHA/多租户JWT、OAuth2/PKCE、设备码、多provider、自注册与审计 | 当前auth源码、core-flows/API和权限实现可定位；第三方实网没有本轮验证 |
| `570ee57`、`62d5527`、`a28144e`、`5ba8254` | XXL-JOB、独立Workflow、可靠MQ、内部安全/DataScope | 对应服务/公共模块/测试仍在，不缩减为仅页面建设 |
| `995bfa0`、`525a2c5`、`09a29fe` | CRM→SRM→采购收货资产闭环 | 当前领域/迁移/E2E存在；早期32/32、90/100等历史数字不延用 |
| `a8ab673`及ADR-0001 | R-01～R-10、D-01～D-09、数据库地基和G0～G8 | 是脚手架交付扩展，不能以当前剩余i18n替代整个目标 |
| `d24c827`～`595b057` | Liquibase、旧库接管、Java租户provision、migrator-gated启动 | migration/baseline/seed源仍在；旧接管报告方法须重新裁决 |
| `e5c0f05`、`80a7e27`、`0bf2253`至`26f65e9` | 规则UI、零warning门、公共Starter四试点 | 当前实现应保护；本轮标准lint真实为红灯且来源是临时脚本 |
| `e0191b6`、`89c30d2`、`95dd0e6`、`99dcc93`、`eeb4099`、`b660e2e` | service/CRUD/预设、golden/runtime矩阵、轻量模式 | CLI、catalog、tests和Compose长期保留；旧coverage称未交付与代码存在冲突 |
| `0381c6a` | 指标/同步异步Trace/观测栈 | 配置/源码和旧运行报告存在；当前未启动观测栈且没有新运行绑定证据 |
| `4aba85c`、`da85ae6`、`7879680`、`4d3f628` | 四语言/截图/CI/真实E2E与门禁扩展 | 严格门存在，但队列、故障编排及覆盖验收能力有确定性缺口 |
| `653afe3`、`31ee4e8`、`6c03b32`、`1f01f55` | SRM报价、门户响应式、字典三态、详情弹层 | 代码/manifest/图保留；旧4passed、16passed等仅历史报告，本轮未复跑 |
| `52b2c5c`、`8b43263`～`204bf87`及当前dirty tree | 翻译客观缺漏修复、Flash文档及SRM targeted修复 | 102译文仍未签核；旧SRM finding部分已被本地修复，不能重复按旧代码施工 |

本轮附件是2026-09-05角色/治理新要求的直接来源。以上Git记录仅恢复历史目标及变更顺序，提交标题并不证明当时或当前运行成功。

## 4. 当前轻量验证与运行证据

| ID | Evidence级别 / 主张 | 当前结果与限制 |
|---|---|---|
| V-01 | Static，docs links与README固定事实检查 | VERIFIED：两项exit0；只覆盖manifest登记文档及四README；不是全repo/语义检查 |
| V-02 | Static，严格i18n | REWORK_REQUIRED：exit1，204项；38源组中34组三语共102译文全部present-unverified且reviewed_at空；不能代签 |
| V-03 | Static，截图manifest/coverage | IN_PROGRESS：strict exit1，8模块；230单图记录/230路径；12模块=4 covered/6 partial/2 missing，共36显式gap；这些是声明值，不是独立视觉PASS |
| V-04 | Static，文档sensitive扫描 | VERIFIED：exit0，范围仅markdownFiles()集合；未检查zip/patch/env/运行日志，不能证明仓库无秘密 |
| V-05 | Static，locale parity与UI硬编码增量门 | VERIFIED：四语各2319 key、无key/placeholder差异、UI 0/0；不能证明日/韩翻译自然或后端错误本地化 |
| V-06 | Static，复核队列 | REWORK_REQUIRED：exit1，12项；脚本对4个translations={}的中文条目仍生成3语<missing>，与manifest语言范围冲突 |
| V-07 | Static，admin manifest-sync | VERIFIED：exit0，预期42/登记42；只证明该脚本静态清单同步，不代表230图审核 |
| V-08 | Test/static lint | REWORK_REQUIRED：标准eslint . --max-warnings0（只改JSON reporter）exit1，38 errors/26 warnings；均位于8个untracked `omni-frontend/scripts/.work/*.mjs`；没有降低门槛 |
| V-09 | Runtime，Docker inventory | VERIFIED（仅健康快照）：15 running、14 healthy，frontend无healthcheck；项目名omni-wp09-docs；各业务latest镜像尚未绑定HEAD，不声称业务通过 |
| V-10 | Remote/Git | VERIFIED（仅分支身份）：本地/Gitee/GitHub为204bf87；没有核对CI run、tag或发布 |
| V-11 | Test artifact，历史SRM结果 | `.artifacts/docs-playwright/.last-run.json`为failed/4 IDs，mtime 01:30:08+08；docs-flash-repair为failed/1 ID，mtime07:17:00+08；无源码digest，不能精确证明当前spec执行结果，当前修复未验收 |
| V-12 | Static artifact，Trace敏感性 | VERIFIED：4个trace.zip各25条Bearer头（合计100）；仅计数，未校验token有效期；保留受限、不得原件提交 |

Docker观察：默认MySQL宿主13306、RocketMQ namesrv19876、XXL-JOB18080，与AGENTS服务端口简表不同；前端3000/Gateway8102发布在全部接口。Compose实际配置优先于泛化的端口表。未读真实.env。浏览器业务、DB当前残留、采购13品类是否soft-deleted、Flowable/Outbox副作用本轮未查；全部仅保留为需要重新读库验证的历史线索，不能照历史SQL恢复。

## 5. MASTER目标当前状态

| MG | 当前状态 | 证据边界 / 下一验收 |
|---|---|---|
| 01/02/03/04/05/06/07/08 | IMPLEMENTED_NOT_VERIFIED | 八服务+19模块/前端路由/DTO/测试存在；主流程未本轮运行；特定缺口由WP-004、006～009处理 |
| 09 | BLOCKED | 当前migrator指向baseline-09a29fe；旧库支持范围/真实备份/adoption算法假设未完成裁决（ESCALATION-001） |
| 10/11/12/13/14/15 | IMPLEMENTED_NOT_VERIFIED | 公共Starter/CLI/CRUD/五预设/轻量/观测资产及历史报告在；缺当前候选运行绑定与完整矩阵 |
| 16 | REWORK_REQUIRED | lint当前38/26；parity静态通过但语义/UI错误文案仍需验证 |
| 17 | IN_PROGRESS | 102译文存在但未签核；队列12项失败，内容客观缺漏需按当前版本裁决 |
| 18 | IN_PROGRESS | 正式测试/截图基础已实现；36声明gap且covered值未经逐状态独立审核 |
| 19 | NOT_STARTED | 本轮只设计清理清单，未实际清理；部分资产必须先重构/保全 |
| 20 | IN_PROGRESS | 双远端分支一致；未发布/未最终Review，G0～G8无完整当前证据 |

G0=REWORK_REQUIRED；G1=BLOCKED；G2=REWORK_REQUIRED（故障PREPARE确定性问题）；G3/G4/G5/G6=IMPLEMENTED_NOT_VERIFIED；G7=IN_PROGRESS；G8=DEFERRED（明确依赖G1～G7）。这些门状态不意味着重新实现已存在的模块。

## 6. 独立裁决的冲突与重工登记

| Finding | 状态 | 当前证据 → 裁决 → WP |
|---|---|---|
| REWORK-001 | REWORK_REQUIRED | quality.yml全局设置G2_OUTAGE_STATE_FILE；functional.spec.ts:629在该变量存在时skip PREPARE，同时642行写该路径。现有编排必跳过PREPARE，不能形成准备状态文件；修阶段协议与可执行CI，WP-002 |
| REWORK-002 | REWORK_REQUIRED | docs-review-queue.mjs对LOCALES无条件建行，4个合法Chinese-only source变成12个<missing>。修语言范围解释并保留复核要求，WP-002 |
| REWORK-003 | REWORK_REQUIRED | docs-quality screenshots只验路径/部分元数据和module.status，未逐required_flow/state检查，也未验图像/当前SHA；覆盖comments用字典三态代表全部管理动作、旧social图代表登录，不能直接验收，WP-002/014 |
| REWORK-004 | IMPLEMENTED_NOT_VERIFIED | 旧Review列出的keyword/records/version/无保存点击/编辑标题错误已在当前untracked SRM spec中修正：name分页、data.records、version删除、单次POST等待、edit标题、允许APPROVING及撤回路径。最新留存run仍failed，不能复述旧错误或称修好，WP-004 |
| REWORK-005 | REWORK_REQUIRED | 8个untracked临时脚本触发标准lint红灯；原文件保全后决定哪些能力进入正式工具/fixture，再清理，WP-001/002/015 |
| REWORK-006 | IN_PROGRESS | 截图coverage中CLI/observability“not-yet-delivered”与当前源/入口相矛盾，只能改成实现存在、缺场景运行证据；禁止将缺口直接covered，WP-011/014 |
| REWORK-007 | REWORK_REQUIRED | CI secret扫描排除patch/env且不扫描Trace，npm audit只critical；nightly缺token可skip、health只计5个服务、Compose必填Secret未完整配置，E2E默认localhost3000与HTTP服务生命周期未显式绑定。缺口需完整编排评审，不能宣告CI当前PASS，WP-002 |
| REWORK-008 | IN_PROGRESS | 旧adoption报告把fresh当前结构与旧baseline比较失败称为baseline错误，建议换candidate；当前AdoptionService先旧指纹/seed/backup/二阶段确认再adoptBaseline，AdoptionLabelContractTest要求baseline与upgrade隔离。fresh后adopt并不能证明旧库升级路径错误；先ESCALATION-001，WP-003 |
| REWORK-009 | IN_PROGRESS | AGENTS/MQ说明仍称schema.sql自动建表；当前MqLogAutoConfiguration未见该初始化，migrator已有common变更；不能按说明保留第二结构源，也不能未查运行/classpath引用先删除，WP-003/013/015 |
| REWORK-010 | IN_PROGRESS | ProcurementIntegrationTest/GoodsReceiptConcurrencyTest使用MockitoExtension；CRM真实MySQL测试由环境变量启用。名字/测试数量不能代替真实DB并发/事务证据，WP-006/007/016 |

## 7. PROTECTED BASELINE

保护指“禁止无依据删除/重写”，验证范围另记。没有足够证据认定八服务业务整体VERIFIED；不虚构生产运行冻结范围。

| PB-ID | 资产 / 身份 | 本轮可VERIFIED范围 | 保护及重新打开条件 |
|---|---|---|---|
| PB-01 | HEAD 204bf87、关键历史09a29fe/ADR决策 | Git身份、历史可追溯、双remote分支一致 | 不改写历史、不reset原工作区；新候选追加提交 |
| PB-02 | formal source/config/migration/catalog/seed；逐文件SHA在CSV | 当前存在/摘要/引用与19模块组合 | 已存在生产资产默认保留；业务运行为IMPLEMENTED_NOT_VERIFIED，只因确定性缺陷进入最小WP |
| PB-03 | 189 Java测试文件（含E2eTokenFixture）、10 CLI test文件、functional.spec、10个tracked文档flow、shared docs-page、两正式Playwright配置 | 可执行工程入口及调用关系存在 | 全部KEEP_ENGINEERING_ASSET；测试失败也不能删除；Mockito与真实DB分级 |
| PB-04 | untracked srm-supplier-detail spec、local配置及其registry能力 | 契约修正和失败登记存在 | 保留原工作与能力；修复通过后归入正式suite，再删除重复配置，不丢E2E |
| PB-05 | docs-quality/locale/UI/manifest-sync/CI/helpers | V01/V04/V05/V07限定静态检查PASS；其它checker有明确问题 | 保留能力，WP-002修缺陷；不得降低strict或刷baseline消红 |
| PB-06 | 230manifest图记录及docs/images、旧正式用例/稳定fixture | 路径/用例关联存在；没有本轮视觉复核 | 候选官方图受保护，逐图审核前不批删；与当前不一致只在替代图验收后退役 |
| PB-07 | 架构/API/领域/开发/部署正式docs、ADR、四语有效翻译 | 文档存在/链接/部分固定事实可验证 | 保留有效约束及译文，定点改事实错误；不整体重译/另建平行事实源 |
| PB-08 | 4个trace及所有原始失败产物/未知.env备份 | 认证头统计/路径存在 | 保留受限直至脱敏摘要/保全裁决；不得共享原件或以过期为由声称已销毁 |

## 8. 扫描完整性与后续决策

CSV覆盖本轮列出的所有tracked/untracked文件，另以精确目录行覆盖ignored target/dist/test-results/日志等运行/构建产物，并以单文件行记录存在的本地env配置。依赖安装目录/node_modules、Git对象库、外部TEMP/用户机器其他目录不作为仓库cleanup对象。输出自身用SELF记录，避免自引用hash；新增/变更文件必须再对账。

所有候选有七域显式引用扫描；没有静态命中会明确写NOT_PROVEN_NO_DYNAMIC_REFERENCES，Qoder仍需验证glob/classpath/隐式发现。这份清单完成分类设计，不声称删除前置均已完成。本轮没有删除任何生产、测试、脚本、Trace或运行产物。历史规模、逐类计数、所有候选和风险以CSV及verification.inventory_summary为准。

最终inventory覆盖2,132项：包括全部仓库文件与ignored目录汇总。初始历史过程文档49份、历史走查图5张；一次性/待重构脚本77份；tracked SQL 33份，其中8份正式seed、25份非seed待迁移替代核实。原位重建后的MASTER作为canonical保留，不计入待退役旧过程文档。详细分类及引用命中由CSV保存，源文件/图片命中可能只是同名classpath或注释，已特别标注，不当成实际运行加载。

本轮新governance按docs-manifest.source_only登记为内部中文规则，既有checker不扫描该集合；本轮对治理包单独做全部链接/路径/manifest完整性检查。原38文档组/102待审译文范围未减少。
