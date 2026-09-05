# Omni-Stack 高效率脚手架升级详细实施计划

> 历史设计明细，2026-09-05起不再作为当前执行入口。完整目标以 [MASTER DELIVERY GOAL](scaffold-upgrade-plan.md) 为准，执行只读 [Qoder Handoff](evidence/project-consolidation/qoder-handoff.md)。本文保留到有效设计/验收细节逐项归并完成后由Qoder按清单删除；原G0～G8及D-01～09追溯不因入口切换丢失。

> 文档状态：可执行基线，待按本文决策门逐阶段实施
> 编制日期：2026-08-20
> 代码基线：main 分支，提交 09a29fe
> 上位计划：[scaffold-upgrade-plan.md](scaffold-upgrade-plan.md)
> 原始审查：[full-functional-audit-2026-08-14.md](full-functional-audit-2026-08-14.md)
> 现状审查：[full-functional-audit-remediation-2026-08-17.md](full-functional-audit-remediation-2026-08-17.md)

## 0. 文档可信度说明

“百分之百正确”不能诚实地理解为对未来工期、第三方依赖行为和所有实现细节作绝对保证。本计划采用以下可验证定义：

1. 当前状态、文件路径、模块数量、质量门和已知债务均以 2026-08-20 的仓库实际内容为依据。
2. 已存在的业务规则以 architecture、api-contract、backend-patterns、frontend-patterns、core-flows、workflow、scheduling、mq-reliability、CRM、SRM、Procurement、Asset 文档和对应代码共同校验。
3. 尚未实现的技术方案明确标为“计划默认决策”，不伪装成当前事实。
4. 每个高风险决策都设置进入条件、验收证据、失败停止条件和回滚策略。
5. 如果实施开始前基线提交不再是 09a29fe，必须先执行第 24 章的基线重检，再允许使用本文。

因此，本文可以作为当前基线上的详细开发计划直接拆票实施；它不是跳过阶段设计评审、数据库备份或用户验收的理由。

## 1. 目标、范围与非目标

### 1.1 总目标

把当前“功能较完整的企业应用工程”升级为“可生成、可裁剪、可维护、可验证、可交付的高效率脚手架”，并将脚手架复用成熟度由约 86/100 提升到 92～95/100。

最终必须同时具备：

- 一条命令创建新微服务。
- 一份声明生成标准全栈 CRUD。
- 按 core、workflow、crm、supply-chain、full 预设生成新项目。
- 业务服务通过公共 Starter 接入租户、安全、DataScope、内部调用、XSS、审计等能力。
- 单模块开发不必启动完整 15 容器栈。
- 指标、结构化日志、分布式追踪、告警、Dashboard 和 SLO 模板。
- 前端 0 error / 0 warning 的强制 lint 门。
- 中、英、日、韩四语言文档和 UI 语言包。
- 覆盖主要功能流的可重放截图体系。
- 正式数据库版本管理和最终脚本清理，全仓 .sql 文件只保留规范化种子数据。

### 1.2 本计划覆盖

| 路线图项 | 本文工作包 |
|---|---|
| R-01 请购审批规则业务化 UI | WP-01 |
| R-02 create-service CLI | WP-03 |
| R-03 公共业务 Starter | WP-02 |
| R-04 全栈 CRUD 生成器 | WP-04 |
| R-05 项目裁剪预设与维护说明 | WP-05 |
| R-06 轻量开发模式 | WP-06 |
| R-07 可观测性模板 | WP-07 |
| R-08 前端类型与 lint 债务清理 | WP-08 |
| R-09 四语言文档、README、全流程截图 | WP-09 |
| R-10 临时脚本和历史残留清理 | WP-10 |
| R-10 的前置数据库结构管理能力 | WP-00 |

### 1.3 非目标

以下事项不在本次脚手架升级中隐式扩展：

- 新增 ERP、财务、合同、开票、回款、资产折旧、盘点或维修等业务域。
- 改写 CRM、SRM、Procurement、Asset 已稳定的领域状态机。
- 把 Flowable 嵌入 Procurement、SRM、Asset 或 CRM。
- 把阻塞 Redis Starter 引入 Gateway。
- 用脚手架生成器自动生成复杂状态机、Saga、审批补偿或跨服务幂等逻辑。
- 代替目标生产环境的容量测试、灾备演练、第三方 OAuth 实网验收和独立渗透测试。
- 在 R-10 前提前删除历史脚本或数据库迁移回退来源。

## 2. 已核准的当前基线

### 2.1 仓库和质量基线

| 项目 | 2026-08-20 实际状态 | 证据 |
|---|---|---|
| Git | main@09a29fe | git rev-parse、git log |
| 工作区 | 原有正式文件无未提交修改；scaffold-upgrade-plan.md 与本文均为本轮新增、尚未提交的文档 | git status |
| Maven | 17 个子模块；加父聚合工程共 18 个 reactor 项目 | omni-backend/pom.xml |
| Java | 777 个主代码文件、132 个测试代码文件 | omni-backend/src 文件清点 |
| 后端最近完整门禁 | 18/18 reactor SUCCESS，498 测试通过 | 全功能修复报告 |
| 前端 | 59 个 Vue 页面、31 个组件、44 个 API TypeScript 文件 | omni-frontend/src |
| 前端构建 | 最近完整复验通过 | 全功能修复报告 |
| ESLint | 本次实测 0 error、197 warning；其中 84 条可自动修复 | npm run lint |
| Playwright | 1 个 spec，经参数化展开为 18 个场景；最近本地 18/18 通过 | functional.spec.ts、修复报告 |
| CI 中的 E2E | 只执行 Playwright 用例发现，不实际启动浏览器回归 | .github/workflows/quality.yml |
| Compose | 15 个 service；当前没有 profiles | docker-compose.yml |
| 依赖安全 | 最近复验 npm audit 为 0 vulnerability | 全功能修复报告 |

说明：

- 修复报告中的“18/18 模块”指 17 个子模块加父聚合工程的 Maven reactor 结果，不应再写成 18 个子模块。
- architecture.md 当前把公共模块概括为 8 个，但父 POM 实际包含 9 个 common 模块，其中包括 omni-common-workflow；R-09 必须修正文档表述。

### 2.2 当前模块清单

公共模块：

1. omni-common-core
2. omni-common
3. omni-common-mybatis
4. omni-common-redis
5. omni-common-redis-reactive
6. omni-common-operlog
7. omni-common-job
8. omni-common-mqlog
9. omni-common-workflow

可运行后端服务：

1. omni-auth
2. omni-base
3. omni-workflow
4. omni-crm
5. omni-srm
6. omni-procurement
7. omni-asset
8. omni-gateway

关键依赖事实：

- auth、base、crm、srm、procurement、asset 都依赖 common-job 和 common-mqlog。
- workflow 额外依赖 common-workflow，是 Flowable 唯一运行时。
- gateway 只依赖 common-redis-reactive，不能使用阻塞 Redis Starter。
- crm、srm、procurement、asset 不依赖 common-workflow。

### 2.3 当前重复实现

代码清点确认：

- GatewayPreAuthFilter 在 auth、base、workflow、crm、srm、procurement、asset 中各有实现。
- CRM、SRM、Procurement、Asset 分别维护高度相似的 TenantContext、TenantContextFilter、DataScopeContext 和 DataScopeAspect。
- 四个业务服务分别维护 MyBatis 拦截器链，顺序均要求 TenantLine → DataPermission → Pagination。
- XssConfigProviderImpl 在 auth、base、workflow、crm、srm、procurement、asset 中分别存在，且缓存未命中策略并不完全一致。
- InternalApiAuthFilter 已存在于 common-mqlog，但这使内部 API 鉴权与 MQ 功能产生不必要耦合；auth 另有 InternalApiFilter。
- 内部 Feign Token、租户头和 Trace ID 传播在多组 Client 配置中重复。
- DataPermission 的表映射、子表继承、AccessGuard 和状态机校验具有领域语义，不能机械抽入通用 Starter。

这决定 R-03 只能抽取协议、上下文、过滤器、拦截器装配和 SPI；领域表映射与写授权必须留在各服务。

### 2.4 当前 R-01 实现事实

当前审批配置入口为：

- 前端：omni-frontend/src/views/procurement/approval-route/index.vue
- 前端 API：omni-frontend/src/api/procurement-approval-route.ts
- 后端：ApprovalRouteController、ApprovalRouteServiceImpl
- 规则解析：ApprovalRouteResolver、ApprovalRoutePolicy
- 数据表：proc_approval_route

现有规则：

- 精确品类优先于通配品类“*”。
- 金额区间是包含下界、不包含上界。
- 同一品类启用规则的金额区间不可重叠。
- 无匹配或多匹配时返回 409。
- 保存时校验 Workflow 模型版本存在、属于当前租户且为 PUBLISHED。
- 请购提交由 ApprovalRouteResolver 选择规则。

现有不足：

- 表中没有业务规则名称字段。
- VO 只有 modelVersionId，没有流程名称、发布时间或审批节点摘要。
- 前端在缺少 workflow:model:list 权限时退化为输入数字模型版本 ID。
- Workflow 内部 API 只能查单个版本或按分类查当前版本，不能提供已发布模型选择清单和安全的节点预览。
- 页面向业务人员直接暴露 routeCode、modelVersionId、优先级和半开区间。
- 没有匹配测试、有效覆盖分析、停用影响预览和失效模型标记。

### 2.5 当前文档、语言和截图

在新增本文后，docs 目录应有 52 个 Markdown 文件，其中：

- 中文源文档 19 个。
- 英文 11 个。
- 日文 11 个。
- 韩文 11 个。
- 8 个中文源文档尚无三种翻译：两份审查记录、两份脚手架计划，以及 CRM、SRM、Procurement、Asset 四份 design 文档。

前端当前只有：

- zh-CN.ts
- en-US.ts

语言切换按钮在登录、注册、设备码、门户、工作台和布局中使用二选一逻辑，不能直接扩展为四语言下拉框。

仓库共有 37 个图片资源，其中 docs/images 下为 36 张业务文档图片。现有截图主要覆盖认证、系统、Workflow、Scheduling、CRM 和 SRM；没有成体系的 Procurement 与 Asset 全流程截图。四份 README 合计包含 124 个 Markdown 图片节点，但各语言复用同一组文件，没有按语言生成独立 UI 截图。

### 2.6 当前数据库和脚本

当前数据库没有统一启用 Flyway 或 Liquibase：

- 全仓共有 25 个 SQL 文件：scripts/sql 下 16 个、scripts 根目录 4 个、后端资源目录 5 个。
- 后端资源中的 5 个文件为 Auth 的 V1/V2/init-data、Base 的 V1，以及 common-mqlog/schema.sql；当前未发现 Flyway 或 Liquibase 依赖，因此 db/migration 命名不代表这些文件会被统一自动执行。
- docker-compose.yml 在空数据卷首次启动时挂载 init-all.sql、init-nacos.sql、init-xxl-job.sql。
- init-all.sql 同时承担数据库创建、DDL、种子、权限、角色、默认模型和 sp_init_tenant 存储过程。
- 既有环境依赖多份 migrate-*.sql。
- common-mqlog 通过 schema.sql 自动创建 sys_mq_message。
- Docker entrypoint SQL 只在空卷首次执行，不能替代已有数据库升级。

scripts 根目录另有 31 个历史文件，包括 16 个 PowerShell、6 个 Python、4 个 SQL、3 个 JSON、1 个 BIN 和 1 个 TXT；其中大量文件名带 check、fix、temp、verify、raw 或实例编号。它们是 R-10 的候选对象，不允许在引用关系和替代能力未验证前直接删除。

### 2.7 当前可观测性

已有能力：

- Gateway 与 Servlet 服务有 X-Trace-Id。
- Servlet TraceIdFilter 写入 MDC，并由公共 Feign 配置传播。
- 所有运行服务均引入 Actuator。
- CRM、SRM、Procurement、Asset 配置中声明暴露 prometheus 和 metrics 端点。

缺失能力：

- 未发现 Prometheus registry 依赖。
- 未发现 Micrometer Tracing、OpenTelemetry 或 OTLP 导出配置。
- 未发现 Prometheus、Alertmanager、Grafana、Tempo、Loki 或日志采集服务。
- 未发现告警规则、Dashboard JSON、SLO 模板和追踪采样策略。
- 不同服务的 Actuator 暴露项不一致。

## 3. 强制实施原则

1. **先建基线，后改功能。** 每个阶段开始前保存构建、测试、数据库和运行态证据。
2. **先扩展，后迁移，最后删除。** 数据库字段、公共 Starter 和配置均采用 expand → migrate → contract。
3. **安全失败关闭。** 缺少 tenant、scope、内部令牌或可信身份时不得降级为全量访问。
4. **领域语义不下沉。** DataScope 表映射、子资源继承、AccessGuard、状态机、Saga 和幂等规则保留在业务模块。
5. **生成区与手写区隔离。** 生成器只覆盖带所有权标记的文件或区块；手写领域代码永不被静默覆盖。
6. **单一事实来源。** 模块、端口、依赖、预设、Compose、Gateway、权限和文档矩阵由机器可读清单驱动。
7. **旧接口兼容一个发布周期。** R-01 和公共 Starter 迁移期间，已有路径、权限码和已启动流程语义保持不变。
8. **数据库不可用 down 脚本冒险回滚。** 结构变更优先前向修复；发布回滚依赖兼容窗口、备份和应用版本切换。
9. **截图不绕过生产安全。** 不提交 CAPTCHA 答案、JWT、内部 Token、私钥、真实密码或生产数据。
10. **删除只在 Phase 5。** R-10 之前允许标记 deprecated，不允许破坏性清理。

## 4. 计划默认技术决策与决策门

下表中的“默认”是本文为了可直接执行而选定的实现方向。若要更改，必须在对应工作包开始前更新 ADR 和本文，不得开发到一半临时换轨。

| ID | 默认决策 | 状态 | 最迟确认点 |
|---|---|---|---|
| D-01 | 脚手架 CLI 使用 Node.js 22 + TypeScript；Commander 负责命令，YAML + JSON Schema/Ajv 负责声明校验，Handlebars 负责模板 | 计划默认 | WP-03 开始前 |
| D-02 | 新增 omni-common-service 作为 Servlet 业务服务组合 Starter；Gateway 保持 reactive 专用链，Auth 保留授权服务器适配层 | 计划默认 | WP-02 开始前 |
| D-03 | scaffold/catalog/modules.yaml 是模块组合单一事实来源；presets 只引用模块 ID，不复制依赖关系 | 计划默认 | WP-03 开始前 |
| D-04 | 使用 Liquibase YAML 管理结构；新增一次性运行的 omni-db-migrator；全仓 .sql 最终只允许存在于 scripts/sql/seed 并承载幂等种子数据 | 计划默认 | WP-00 开始前 |
| D-05 | 文档截图使用独立 Playwright 配置和隔离测试环境；登录后状态通过短期测试 Token 注入，不自动破解或绕过 CAPTCHA | 计划默认 | WP-09 截图开发前 |
| D-06 | 可观测性默认采用 Micrometer + OpenTelemetry/OTLP + Prometheus + Alertmanager + Grafana + Tempo + Loki + Alloy | 计划默认 | WP-07 开始前 |
| D-07 | 预设命令默认生成到新目录，不原地裁剪当前仓库；原地裁剪若未来需要，作为独立高风险功能评审 | 已锁定 | WP-05 |
| D-08 | 审批规则 routeCode 使用服务端生成的 APR-{ULID} 稳定技术标识，创建后不可编辑；业务人员只维护 routeName | 计划默认 | WP-01 开始前 |
| D-09 | 新建请购审批规则只允许选择 Workflow category=purchase 的当前已发布版本；businessType 仍为 PROCUREMENT_REQUISITION，二者不混用 | 计划默认 | WP-01 开始前 |

D-08 是对上位计划“按规则名称自动生成、可在高级信息调整”的收敛：名称会改动且涉及多语言，不能作为稳定键；可编辑技术编码也会增加引用和审计风险。如必须允许自定义编码，应在 WP-01 前明确唯一性、改名、历史引用和权限规则，并同步修改本文。

D-09 使用当前种子和模型已有的 purchase 分类，避免采购人员误选资产、SRM 或通用流程。实施前必须盘点所有现有规则引用；若发现引用其他分类，不自动改写或直接禁用，而是输出兼容报告并由业务确认允许列表或迁移方案。

### 4.1 D-04 的明确解释

R-10 的“SQL 只保留最终种子数据”按以下最终状态实施：

- database/seed/manifest.yaml 和其引用的声明是菜单、权限、角色、字典及模块初始化的事实源；scripts/sql/seed 是受校验的可交付产物，CI 必须检测二者漂移。
- scripts/sql/seed 下只保留可重复执行的正式种子数据。
- 应用表结构、索引、约束、数据库和用户授权由 Liquibase YAML 表达。
- Auth/Base 的 db/migration、Auth init-data、common-mqlog/schema.sql 以及 scripts 根目录 4 个 SQL 全部进入迁移台账；结构转为 changelog、种子归并到 seed、一次性修复验证后删除。
- Nacos 3.1.1、XXL-JOB 3.3.1 和 Flowable 8.0.0 的固定版本数据库结构转换为带 upstreamVersion 和 checksum 元数据的 vendor changelog，不继续保留散落 SQL。
- Flowable vendor changelog 必须与官方 8.0.0 schema 指纹一致；接管完成后关闭运行时 database-schema-update，后续版本升级追加经过官方升级脚本对照的新 changeSet。
- sp_init_tenant 的权限、角色、根组织、管理员、XSS 和模块初始化逻辑迁移为正式 Java 服务与幂等模块初始化协议，不把大型存储过程伪装进 YAML。
- crm-sample-data、procurement-sample-data 和 seed-test-data 不作为生产种子；改为 E2E fixture 数据，由正式测试工具管理。

如果不接受 D-04，则不能满足 R-10 的最终 SQL 约束，必须先修改上位计划，不能在实施中含糊处理。

## 5. 目标目录与组件结构

目标新增或重组结构如下：

~~~text
Omni-Stack/
├── scaffold/
│   ├── catalog/
│   │   └── modules.yaml
│   ├── presets/
│   │   ├── core.yaml
│   │   ├── workflow.yaml
│   │   ├── crm.yaml
│   │   ├── supply-chain.yaml
│   │   └── full.yaml
│   ├── schemas/
│   │   ├── module.schema.json
│   │   ├── preset.schema.json
│   │   └── crud.schema.json
│   └── templates/
│       ├── service/
│       └── crud/
├── tools/
│   └── omni-cli/
│       ├── src/
│       ├── test/
│       ├── package.json
│       └── package-lock.json
├── database/
│   ├── changelog/
│   │   ├── platform/
│   │   ├── auth/
│   │   ├── base/
│   │   ├── workflow/
│   │   ├── crm/
│   │   ├── srm/
│   │   ├── procurement/
│   │   ├── asset/
│   │   └── vendor/
│   └── seed/
│       └── manifest.yaml
├── scripts/
│   └── sql/
│       └── seed/
├── observability/
│   ├── prometheus/
│   ├── alertmanager/
│   ├── grafana/
│   ├── tempo/
│   ├── loki/
│   └── alloy/
├── docs/
│   ├── docs-manifest.yaml
│   ├── guides/
│   └── images/
│       ├── zh-CN/
│       ├── en-US/
│       ├── ja-JP/
│       └── ko-KR/
├── omni-backend/
│   ├── omni-common-service/
│   └── omni-db-migrator/
└── omni-frontend/
    ├── e2e/
    ├── e2e-docs/
    ├── playwright.config.ts
    └── playwright.docs.config.ts
~~~

正式实现时，如果某目录没有长期调用入口、测试或维护责任，不得为了匹配此树而空建目录。

## 6. 总体依赖与实施顺序

~~~mermaid
flowchart TD
    P0[Phase 0 基线与数据库版本管理] --> P1[Phase 1 R-01 + R-08 + R-09 基线]
    P1 --> S[R-03 公共 Starter]
    S --> C[R-02 create-service CLI]
    C --> G[R-04 CRUD 生成器]
    C --> PR[R-05 项目预设]
    PR --> L[R-06 轻量开发模式]
    G --> O[R-07 可观测性模板]
    L --> O
    O --> D[R-09 四语言文档与全流程截图收口]
    D --> X[R-10 最终清理]
~~~

执行阶段：

| Phase | 工作包 | 主要结果 | 允许进入下一阶段的条件 |
|---|---|---|---|
| 0 | WP-00 | 冻结基线；建立正式数据库版本管理；保留旧 SQL 只作回退 | 空库初始化、现有库接管和升级演练全部通过 |
| 1 | WP-01、WP-08、WP-09A | 审批规则 UI 业务化；lint 清零；建立文档/截图清单 | R-01 边界 E2E 通过，lint 0/0，文档基线无断链 |
| 2 | WP-02、WP-03 | 公共 Starter 和 create-service CLI | 四个业务服务迁移无回归；黄金服务首次构建通过 |
| 3 | WP-04、WP-05、WP-06 | CRUD 生成、项目预设、轻量启动 | 五个预设均通过生成、构建、Compose 和冒烟验证 |
| 4 | WP-07、WP-09B | 可观测性和四语言/截图全面收口 | Trace 跨服务可查；四语言结构一致；截图清单 100% |
| 5 | WP-10 | 新旧临时文件、一次性脚本和中间产物清理 | 全量质量门与 fresh/upgrade 数据库双路径通过 |

WP-09A 指 Phase 1 的文档事实盘点、docs-manifest 和截图覆盖清单；WP-09B 指 Phase 4 的四语言正文、UI 本地化、截图生成和 README 收口。二者仍属于同一个 WP-09，不是两个独立范围。

## 7. WP-00：数据库版本管理与实施基线

WP-00 是 R-10 的前置能力，但不在此阶段删除任何旧脚本。

### 7.1 WP-00.1 基线冻结

交付：

- docs/evidence/scaffold-upgrade-baseline.md，记录提交、环境、模块、测试、容器和数据库版本。
- 当前七个业务数据库及 Nacos、XXL-JOB 两个基础设施数据库的结构快照和关键约束清单，不提交真实数据。Gateway 无独立数据库。
- 当前全仓 25 个 SQL 文件的逐文件职责映射，覆盖 scripts/sql 的 16 个、scripts 根目录的 4 个和后端资源目录的 5 个。
- 当前 31 个 scripts 根文件的引用清单。

验证：

- JDK 25 全量 Maven clean install。
- 前端 build、lint 基线和 Playwright 18 场景。
- docker compose config、15 服务健康状态。
- 当前空库初始化一次、持久化卷重启一次。

停止条件：

- 基线构建或既有核心流程与修复报告不一致。
- 当前数据库无法备份和恢复。
- 无法识别某个 SQL 或脚本的实际调用方。

### 7.2 WP-00.2 引入 omni-db-migrator

新增正式 Maven 模块 omni-backend/omni-db-migrator：

- 命令模式运行，不作为长期在线服务。
- 不依赖 Nacos、Redis、RocketMQ、XXL-JOB 或其他业务服务，只使用管理 MySQL 连接和本地配置。
- 使用管理连接创建数据库、应用账号和授权。
- 为 auth、base、workflow、crm、srm、procurement、asset、nacos_config、xxl_job 分别维护 changelog 和 DATABASECHANGELOG。
- 支持 validate、status、migrate、adopt-current、verify-seed 命令。
- 日志严禁输出数据库密码。
- Compose 中作为 one-shot 依赖，成功退出后 Nacos、XXL-JOB 和业务服务才允许启动。
- Liquibase 版本使用 Spring Boot 4.0.6 依赖管理确认的兼容版本并写入锁定证据，不由子模块任意覆盖。

配置来源：

- 开发环境从 .env 注入。
- CI 使用临时 MySQL 服务。
- 生产环境只接受外部 Secret，不提供弱默认值。

### 7.3 WP-00.3 转换当前结构

转换顺序：

1. platform：数据库、字符集、应用账号和授权。
2. auth：租户、用户、组织、角色、权限、OAuth2、登录审计、XSS。
3. base：字典、任务、操作日志。
4. workflow：业务扩展表，以及从 Flowable 8.0.0 官方 schema 转换并按指纹校验的 ACT_* vendor changelog；完成接管后所有正式环境设置 database-schema-update=false。
5. crm。
6. srm。
7. procurement。
8. asset。
9. common-mqlog 的 sys_mq_message 公共变更集。
10. 固定版本 Nacos 和 XXL-JOB vendor 结构。

每个 changeSet 必须：

- 有不可变 ID、author、目标数据库和说明。
- 有可机器检查的 precondition。
- 结构和种子分离。
- 不使用按日期随意命名的一次性文件。
- 禁止修改已经在任何环境执行过的 changeSet；只能追加。

种子转换必须使用 tenant + 稳定业务 code 等自然键实现幂等，不依赖自增 ID 在不同环境相同。生成的 seed SQL 必须带 manifest 版本和摘要，verify-seed 同时检查缺失、重复、意外覆盖及 catalog 中已裁掉模块的残留。

### 7.4 WP-00.4 接管现有数据库

adopt-current 只能在以下条件全部满足时执行：

1. 已完成可恢复备份。
2. schema fingerprint 与 09a29fe 参考快照一致，或差异已在白名单中解释。
3. 唯一约束、索引、字符集、逻辑删除字段和版本字段均通过校验。
4. 关键种子按 tenant + code 校验，不按自增 ID 假定一致。
5. 工具生成接管报告，由用户确认后才写 DATABASECHANGELOG。

任何未知列、缺失约束、重复权限码、跨租户脏数据或无法解释的表都必须停止接管，不能自动“修好后继续”。

### 7.5 WP-00.5 替换 sp_init_tenant

目标：

- Auth 内的 TenantProvisionService 负责租户、权限树、默认角色、根组织、管理员和 XSS。
- 权限和默认角色由 modules.yaml 的 provisioning seed ID 解析 database/seed/manifest，不再把所有模块权限硬编码在一个存储过程或复制进多个清单。
- Auth 在本地事务中写入 tenant.provision-requested Outbox 事件。
- Base、CRM、SRM、Procurement、Asset 以 tenantId + moduleId 为幂等键初始化各自字典或默认配置。
- Auth 汇总成功/失败结果；失败可重试，不能把半初始化租户当成正常可登录租户。
- 管理页展示 PROVISIONING、ACTIVE、FAILED 状态和失败模块。

兼容迁移：

- 现有 status=1 租户映射为 ACTIVE。
- 新状态字段先允许旧应用忽略，再切换新应用，最后移除存储过程调用。
- 旧 sp_init_tenant 保留到 Phase 5；新租户路径稳定后才删除。

测试：

- 同 requestId 重放不重复创建权限、角色、字典或模板。
- 某模块暂时不可用时可恢复，不重复创建管理员。
- 用户密码只在 Auth 内 BCrypt 处理，不进入 MQ。
- 不同预设只初始化包含模块的权限与默认数据。

### 7.6 WP-00 验收

- 全新空卷由 db-migrator 完成全部正式结构和种子，15 服务可健康启动。
- 09a29fe 数据卷副本可 adopt 并升级，核心数据行数和关键业务状态不变。
- db-migrator 重跑无新增副作用。
- 任一 changeSet 失败时业务服务不启动，并保留明确错误和 Trace/Run ID。
- 尚未删除旧 SQL，回退路径经过演练。
- AGENTS.md、architecture、backend-patterns、frontend-patterns、scheduling、mq-reliability、docker-deployment 及四个业务设计文档已改为 Liquibase/seed 新事实；兼容期引用旧 SQL 时必须明确标注 deprecated 和删除门。

## 8. WP-01：请购审批规则业务化 UI

### 8.1 数据与兼容改造

proc_approval_route 新增：

| 字段 | 类型 | 规则 |
|---|---|---|
| route_name | VARCHAR(100) | 新 UI 必填；旧记录用 route_code 回填 |

保留字段：

- route_code 继续作为租户内稳定技术标识，保持唯一且创建后不可修改。
- category_code、min_amount、max_amount、model_version_id、priority、status、version 和 deleted 语义不变。
- 已启动流程仍绑定原 modelVersionId，不受规则编辑影响。

兼容要求：

- 原有四个 CRUD 路径和 procurement:approval-route:* 权限码不变。
- 新版 Create/Update Request 增加 routeName。
- 一个兼容发布周期内，旧 API 调用未传 routeName 时由服务端回退为 routeCode 并记录弃用日志；前端和生成器不得使用回退。
- routeCode 由服务端生成 APR-{ULID}，高级信息只读展示；保留对已有合法 routeCode 的读取兼容，不再要求业务人员输入。
- 金额继续以十进制字符串传输，不改为 JSON number。

涉及文件：

- database/changelog/procurement
- ProcApprovalRoute.java
- ApprovalRouteRequests.java
- ApprovalRouteViews.java
- ProcViewAssembler.java
- ApprovalRouteServiceImpl.java
- api-contract 四语言版本

### 8.2 Workflow 内部只读能力

新增内部接口：

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | /api/internal/workflow/model-versions/published?category=purchase | 返回当前租户指定分类可绑定的当前已发布模型 |
| POST | /api/internal/workflow/model-versions/resolve | 按不超过 200 个版本 ID 批量返回既有规则的模型元数据和可用状态 |
| GET | /api/internal/workflow/model-version/{id}/preview | 返回模型元数据和安全审批图预览 |

已发布模型列表返回：

- id、modelId、modelKey、modelName、category。
- version、publishTime、processDefinitionId、status。
- approvalPreviewVersion，便于未来演进预览契约。
- 只返回当前租户、category 精确匹配、主模型有效、currentPublishedVersionId 指向 PUBLISHED 且有 processDefinitionId 的记录。

审批预览不能返回原始 BPMN XML 或 designerJson，只返回：

- 节点 ID、节点名称、节点类型。
- UserTask 的 roleCode、approvalMode 和业务化说明。
- Gateway 的分支标识和已脱敏条件摘要。
- 有向边、默认分支和是否存在分支。
- linearSummary 只在确定为无环单路径时返回；有分支时 UI 必须展示“实际路径取决于请购数据”，禁止伪装成固定顺序。

实现复用：

- 复用 XmlSecurityUtils 的 XXE 防护。
- 复用 CandidateResolutionService 已有的 UserTask 提取能力，但增加独立的只读 DTO 和完整图解析测试。
- 不在预览时解析真实审批人，避免把当前组织状态误认为未来运行时结果。

### 8.3 Procurement 业务外观接口

前端不直接依赖 workflow:model:list。新增：

| 方法 | 路径 | 权限 | 行为 |
|---|---|---|---|
| GET | /api/procurement/approval-route/workflow-options | procurement:approval-route:list | Procurement 通过内部 API 聚合已发布流程 |
| POST | /api/procurement/approval-route/match-preview | procurement:approval-route:list | 使用真实规则解析器进行无副作用匹配 |
| GET | /api/procurement/approval-route/coverage | procurement:approval-route:list | 分析有效覆盖、断档、默认兜底和失效模型 |
| GET | /api/procurement/approval-route/impact | procurement:approval-route:list | 模拟停用或删除指定规则后的影响 |

match-preview 请求：

- categoryCode。
- totalAmount，十进制字符串。

match-preview 响应：

- outcome：MATCHED、NO_MATCH、AMBIGUOUS、WORKFLOW_UNAVAILABLE。
- routeId、routeName、routeCode。
- categoryCode、effectiveCategoryCode、defaultRule。
- minAmount、maxAmount。
- modelVersionId、modelName、modelVersion、publishTime。
- approvalGraph 和可理解的 actionMessage。

关键实现规则：

- ApprovalRouteResolver 增加 evaluate 方法，返回结构化评估结果。
- 现有 resolve 方法改为调用 evaluate 后将非 MATCHED 转成原有 BusinessException。
- 请购提交和匹配测试因此共享唯一算法。
- Procurement workflow-options 固定请求 category=purchase；创建或更换流程时服务端再次校验该分类，不能相信前端过滤。
- businessType=PROCUREMENT_REQUISITION 继续用于运行实例和完成事件契约，不替换 Workflow 模型 category=purchase。
- 现有规则若引用非 purchase 模型，列表必须标记 LEGACY_CATEGORY；兼容期允许原提交语义继续运行，但在业务确认迁移或允许列表前不能静默切换模型。
- 前端不得计算规则命中。
- 规则分页聚合当前页去重后的 modelVersionId，通过 resolve 批量查询，禁止逐行 N+1 调用；超过批量上限时按固定大小分片。
- Workflow 暂不可用时，规则 CRUD 的安全校验仍失败关闭；只读列表继续返回本地规则并把 workflowAvailability 标为 UNAVAILABLE，流程名称显示“暂时无法验证”，技术 ID 只在高级信息中显示，不得伪装为正常或提供数字输入后门。

### 8.4 覆盖性算法

coverage 必须按当前解析语义计算：

1. 读取当前租户全部有效物料品类和所有启用规则。
2. 分别归并精确品类和默认规则的金额区间。
3. 对具体品类先应用精确规则，再用默认规则填补精确规则未覆盖区间。
4. 输出从 0 到无穷的有效覆盖片段、断档片段和默认兜底片段。
5. 标记重复匹配、无默认规则、规则引用失效模型和全部规则停用。
6. impact 使用相同算法，但在内存视图中排除目标规则，不改数据库。

边界必须覆盖：

- 0。
- 相邻区间共同边界。
- 四位小数。
- 无上限。
- 精确规则断档被默认规则补齐。
- 精确和默认同时命中时精确优先。
- 历史脏数据造成多匹配时明确报告，不按 priority 静默选择。

### 8.5 前端重构

目标文件：

~~~text
omni-frontend/src/views/procurement/approval-route/
├── index.vue
├── components/
│   ├── ApprovalRuleWizard.vue
│   ├── ApprovalFlowPreview.vue
│   ├── RuleCoverageAlert.vue
│   ├── RuleMatchTester.vue
│   └── RuleAdvancedInfo.vue
└── composables/
    └── useApprovalRules.ts
~~~

页面顺序：

1. 一行业务说明。
2. 匹配测试器。
3. 持续可见的覆盖风险。
4. 规则列表。
5. 新建/编辑三步向导。

规则列表默认列：

- 规则名称。
- 适用品类或默认规则。
- 业务化金额范围。
- 审批节点摘要。
- 流程名称和发布版本。
- 状态。
- 查看、编辑、停用、删除。

交互要求：

- 不显示数字 modelVersionId 输入框。
- 不在主表单显示 priority；旧数据仍按 priority 排序，新增默认取当前品类最大值加 10。
- 选择流程失败时显示所需权限和 Trace ID，不提供数字输入后门。
- 停用、删除前显示 impact 结果。
- 移动端把表格转换为卡片，不产生水平页面滚动。
- 390×844、768×1024、1440×900 三种视口验收。
- 表单支持键盘操作、明确焦点、可访问标签和错误摘要。

### 8.6 菜单、权限和文档

- 数据库 MENU 显示名改为“请购审批规则”。
- API 权限显示名改为“查看/创建/更新/删除请购审批规则”。
- permission_code、动态路由路径和前端目录保持不变。
- zh-CN 与 en-US 在 Phase 1 更新；ja-JP 与 ko-KR 在 WP-09B 加入。
- procurement-design、api-contract、core-flows、README 和 Procurement 用户手册同步。
- 操作日志模块名改为“请购审批规则”，保留实体变更前后快照和 Trace ID。

### 8.7 测试与验收

后端测试：

- ApprovalRoutePolicy 边界和异常。
- ApprovalRouteResolver evaluate/resolve 一致性。
- coverage/impact 区间归并。
- routeName 兼容回填与校验。
- Workflow published/resolve/preview 的分类过滤、200 条上限和租户隔离。
- purchase 与 PROCUREMENT_REQUISITION 语义不混用，遗留分类进入兼容报告。
- 内部 Token 缺失、错误和未配置时的 401/503。
- Procurement facade 在 Workflow 404、503、无效响应时的行为。
- 请购提交与 match-preview 对同一输入返回同一路由。

前端测试和 E2E：

- 0、9,999.99、10,000、99,999.99、100,000。
- 精确品类优先默认规则。
- 无规则、断档、重叠脏数据、失效模型、权限不足。
- 停用影响提示。
- 创建、编辑、停用、重新启用和删除。
- 三视口、键盘操作和基本可访问性。

WP-01 完成标准：

1. 采购经理不接触模型版本 ID 也能完成创建和验证。
2. 页面所有命中结果来自服务端真实解析器。
3. 原有已启动流程不变。
4. 权限码和路由兼容。
5. 新旧数据库升级路径均通过。

## 9. WP-08：前端类型与 lint 债务清理

### 9.1 当前债务分组

本次实测 197 条 warning，主要集中于：

- BPMN Modeler、moddle、context pad 和属性面板的 no-explicit-any。
- DynamicFormRenderer、SchemaFieldEditor 和任务表单的动态 Schema 类型。
- home 页面和 Workflow 进度组件的 Vue 格式规则。
- SRM 风险配置页。
- 少量 no-console、attributes-order 和缩进问题。

### 9.2 实施顺序

1. 先运行 ESLint 可自动修复项，单独提交，不混入业务逻辑。
2. 建立 src/types/bpmn.ts，声明最小必要的 Modeler、Element、BusinessObject、Moddle、CommandContext 和 ContextPad 类型。
3. 对第三方库暂缺的类型使用 unknown + type guard，不以 any 或全局禁用规则代替。
4. 建立 src/types/schema.ts，统一动态字段、选项、参数值和校验类型。
5. 拆分 home 页面模板和脚本中的复杂区域，修复格式与隐式类型。
6. 将 console 调用替换为正式 logger/错误状态；生产代码不保留 console。
7. 清理剩余业务页面警告。
8. 将 lint 命令升级为 eslint . --max-warnings 0。

### 9.3 禁止做法

- 不降低 no-explicit-any 或 no-console 规则等级。
- 不添加大范围 eslint-disable。
- 不用 as any 绕过。
- 不因格式自动修复改变页面行为而不做构建/E2E。
- 不把 env.d.ts 的合理声明随意扩成任意模块。

### 9.4 验收

- npm run lint 为 0 error / 0 warning。
- npm run build 通过。
- Workflow 建模、保存、校验、发布和属性编辑 E2E 通过。
- 动态任务表单创建/编辑/触发通过。
- CI lint 使用 --max-warnings 0。

## 10. WP-02：公共业务 Starter

### 10.1 模块边界

新增 omni-common-service，面向 Servlet 业务服务，提供：

- 可信 Gateway 身份头解析和 Spring Security Authentication 建立。
- 请求身份与租户上下文。
- 内部 API 共享令牌过滤器。
- 内部 Feign Token、tenant 和 Trace/traceparent 传播。
- 通用 DataScope 注解、上下文、解析流程和 SPI。
- MyBatis TenantLine/DataPermission/Pagination 固定顺序装配器。
- Redis XSS 配置读取、Auth 回源和本地安全基线。
- 服务安全属性、健康指示和启动时配置校验。

仍为独立可选依赖：

- omni-common-operlog。
- omni-common-job。
- omni-common-mqlog。
- omni-common-workflow。

不进入 omni-common-service：

- Gateway reactive 安全链。
- Spring Authorization Server 专用逻辑。
- 具体表名和 owner 列映射。
- CRM/SRM/Procurement/Asset AccessGuard。
- 状态机、工作流协调、Inbox/Outbox 业务幂等。

### 10.2 公共 SPI

建议接口：

| SPI | 责任 | 默认行为 |
|---|---|---|
| ServiceIdentityProperties | 服务显示名、公开路径、管理路径、内部路径 | 启动时严格校验 |
| TenantTablePolicy | 判定哪些表应用 TenantLine | 无实现则业务 SQL 失败启动 |
| DataScopeTablePolicy | 按表和 permissionCode 生成数据范围表达式 | 由业务模块实现 |
| DataScopeResolver | 向 Auth 解析权威范围 | 默认 Feign 实现，失败关闭 |
| XssSettingsFallback | Auth 和 Redis 都失败时提供基线 | 默认 enabled=true 安全基线 |
| InternalTenantResolver | 内部消息/任务显式建立系统上下文 | 默认不从 ThreadLocal 猜测 |

公共上下文使用不可变快照并在 finally 清理。异步、MQ 和调度线程必须显式传 tenantId，不自动继承请求线程。

### 10.3 自动配置

目标属性示例：

~~~yaml
omni:
  service:
    name: omni-crm
    display-name: CRM
    table-prefixes:
      - crm_
    gateway-preauth:
      enabled: true
    internal-api:
      enabled: true
      token: REQUIRED_ENV_VALUE
    data-scope:
      enabled: true
    xss:
      auth-fallback-enabled: true
~~~

自动配置要求：

- 使用 AutoConfiguration.imports。
- 关键 Bean 使用 ConditionalOnMissingBean，允许有依据的服务适配。
- 安全必需属性为空时启动失败，不使用弱默认值。
- InternalApiAuthFilter 从 common-mqlog 移出后保留一个发布周期的兼容桥，避免两个 Filter 同时注册。
- 所有默认 Bean 有 slice test 和最小示例应用集成测试。

### 10.4 分批迁移

顺序固定：

1. CRM：领域较独立，作为第一迁移样板。
2. SRM：验证 Portal 与内部 API 边界。
3. Procurement：验证复杂表映射和 Workflow/SRM Feign。
4. Asset：验证使用维度与管理维度不能被通用化。
5. Base：只迁移可复用预认证和内部 API 部分。
6. Workflow：保留 TenantInfoHolder/Flowable 租户适配，迁移外围安全能力。
7. Auth：只复用底层工具，不替换 Authorization Server 专用安全链。
8. Gateway：不迁移，继续 reactive 实现。

每迁移一个服务必须：

- 先增加 Starter 并保留旧实现，用测试证明新旧等价。
- 切换 Bean。
- 运行该模块和依赖测试。
- 完成运行态 401/403/503、租户、DataScope、XSS 和内部调用复验。
- 最后删除该服务重复类。

### 10.5 R-03 验收

- CRM、SRM、Procurement、Asset 不再各自复制 TenantContextFilter、GatewayPreAuthFilter、DataScopeAspect 和 XSS 缓存模板。
- 各服务仍保留自己的 DataPermission 表映射和 AccessGuard。
- 拦截器顺序测试在四个服务全部通过。
- 缺 tenant、scope 或内部 Token 的失败关闭行为不变。
- XSS 缓存未命中策略统一为 Auth 回源，失败后启用安全基线。
- Auth 不引入 OperLog。
- Gateway 不引入阻塞 Redis。
- 全量 18 个原 reactor 项目和新增模块全部通过 clean install。

## 11. WP-03：create-service CLI

### 11.1 命令与参数

正式命令：

~~~text
omni create-service <service-id> [options]
omni service validate <service-id>
omni catalog validate
omni doctor
~~~

create-service 必填或可推导参数：

- service-id，例如 inventory。
- Java package，例如 com.example.inventory。
- 显示名称。
- API 前缀。
- 服务端口、管理端口、XXL 执行器端口。
- 数据库名和表前缀。
- 是否启用 OperLog、Job、MQ、DataScope。
- 输出目录。

默认行为：

- service-id 生成 omni-{id}。
- 默认生成 Servlet 服务并依赖 omni-common-service。
- 默认启用租户、Gateway 预认证、内部 API、XSS、Actuator。
- DataScope 只有在提供表策略时才启用，不能生成“空上下文即全量”。
- 默认 dry-run 显示变更计划；显式 --apply 才写入。
- 写入使用临时 staging 目录和原子替换；任一步失败不留下半个模块。

### 11.2 生成内容

后端：

- Maven POM 和父模块登记。
- Application 入口。
- application.yml、application-dev.yml。
- Security、TenantTablePolicy、DataScopeTablePolicy 示例。
- Controller、Service 接口/实现、Mapper 包占位说明，不生成虚假业务。
- XSS 和内部 API 由 Starter 提供，不复制实现。
- 最小 context test、security test、tenant fail-closed test。

基础设施：

- module catalog 条目。
- Gateway 路由条目。
- Dockerfile POM 缓存条目。
- Compose service、端口、环境和 healthcheck。
- Nacos 配置名。
- XXL appname/port，仅在启用 Job 时生成。
- MQ binding，仅在启用 MQ 时生成。

前端和权限：

- 模块根菜单骨架。
- 前端 view 目录和 API 文件骨架。
- menu i18n key。
- 权限种子声明，不直接拼接自增 ID。
- 模块文档骨架和四语言待办状态。

### 11.3 安全写入规则

- CLI 解析 Maven XML 和 YAML 结构，不使用脆弱的全文件字符串替换。
- modules.yaml 先校验后写。
- 端口、service-id、artifactId、数据库名、API 前缀和 permission code 冲突时拒绝。
- 目标文件存在且不属于生成器时拒绝覆盖。
- 工作区目标文件有未提交修改时拒绝，除非用户明确使用 --force；即使 force 也先生成备份 diff，不覆盖未知区块。
- 生成文件包含 generated-by、generator-version 和 template-version 元数据。
- 重复执行同一命令应为无变化或明确提示，不重复插入模块。

### 11.4 黄金样例

固定生成 omni-inventory-sample，但只在测试临时目录中存在：

1. 生成服务。
2. 校验目录和 catalog。
3. 将生成项目加入临时 Maven reactor。
4. 运行 clean install。
5. 运行 docker compose config。
6. 启动最小依赖并验证 health、401、403、内部 API 401/503。
7. 删除临时目录。

黄金样例输出不能提交为重复业务模块；应提交输入 fixture、期望快照和测试。

### 11.5 WP-03 验收

- Windows、Linux、macOS 路径处理测试通过。
- 同一输入两次执行幂等。
- 冲突和中途失败不会污染工作区。
- 新服务首次 Maven 构建通过。
- Compose、Gateway、Dockerfile、权限、前端目录和文档无遗漏。
- 维护者无需复制 CRM/SRM 等现有模块。

## 12. WP-04：全栈 CRUD 生成器

### 12.1 能力边界

生成器只处理“标准主数据/简单聚合”：

- 单表或一个根表加简单明细表。
- 分页、详情、创建、更新、逻辑删除。
- 可选状态启停，但不生成复杂状态机。
- tenant、owner、version、deleted 和审计字段。
- 标准 DataScope 表映射。
- 权限、动态菜单、前端表单和测试。

明确拒绝自动生成：

- 跨服务 Saga。
- Workflow 审批协调。
- Inbox/Outbox 业务事件语义。
- 多聚合事务。
- PII 策略推断。
- 复杂金额、库存、超收或资产幂等规则。

当声明包含上述能力时，CLI 必须报错并引导创建服务骨架后手写领域逻辑，不能生成看似完整但不安全的代码。

### 12.2 输入声明

命令：

~~~text
omni generate crud --spec scaffold/specs/material-brand.yaml
omni generate crud --spec ... --dry-run
omni generate crud --spec ... --apply
omni generate crud --spec ... --check
~~~

crud schema 至少包含：

- moduleId、aggregateName、tableName、tablePrefix。
- API path、permission resource、menu parent。
- 字段名、Java/TypeScript 类型、数据库类型、长度、是否必填。
- 列表/查询/表单/详情可见性。
- unique、index、排序。
- tenant、dataScope、optimisticLock、logicalDelete。
- PII 分类和掩码策略；未声明 PII 时不能自动猜测。
- Decimal 是否必须字符串传输。
- DateTime 是否使用统一格式。
- 前端控件、字典 typeCode 和 i18n key。

### 12.3 生成结果

数据库：

- Liquibase YAML changeSet。
- 索引、唯一约束和注释。
- 权限/菜单正式种子条目。
- 不生成 migrate-task-number.sql 等一次性文件。

后端：

- Entity、Request、Query、VO。
- Mapper。
- Service 接口和 ServiceImpl。
- Controller。
- DataScope policy 注册。
- Request validation。
- tenant + id + version 条件写入。
- OperLog 和 PreAuthorize。
- 单元测试、Controller 安全测试和 Mapper 集成测试骨架。

前端：

- API TypeScript。
- 列表、筛选、分页、详情和编辑对话框。
- v-permission。
- 动态路由约定目录。
- zh-CN、en-US、ja-JP、ko-KR key。
- 响应式基础布局。

文档：

- API 索引片段。
- 权限码表。
- 数据表字段表。
- 新手操作说明骨架。
- 生成元数据和允许手写区域说明。

### 12.4 所有权与再生成

文件分三类：

| 类型 | 行为 |
|---|---|
| generated | 可由同版本生成器整体重建 |
| generated-section | 只更新明确标记区块 |
| handwritten | 永不自动覆盖 |

再生成前：

- 对 generated 文件比较模板版本和内容 hash。
- 检测到人工修改时停止并生成三方 diff。
- 数据库 changeSet 已执行后不得改写；字段变更必须生成新的 changeSet。
- 删除字段默认只生成 deprecated 标记和迁移计划，不立即 DROP。

### 12.5 黄金 CRUD

使用 material-brand 作为无业务状态机的样例：

- tenant 共享主数据。
- brandCode 唯一。
- brandName、status、remark。
- version + deleted。
- 标准列表和 CRUD 权限。

测试流水线：

1. 从声明生成。
2. fresh DB 迁移。
3. 后端编译和测试。
4. 前端 lint/build。
5. 管理员 CRUD E2E。
6. 无权限用户 403。
7. 跨租户读写失败。
8. 第二次生成无差异。

### 12.6 WP-04 验收

- 标准主数据从声明到可运行页面不超过半天人工调整。
- 生成结果满足 AGENTS.md 的分层、注释、权限、日期、Decimal 和 ThreadLocal 约束。
- 生成器不能覆盖手写领域代码。
- 生成的权限、路由、种子和文档无孤儿。
- 生成结果通过全部质量门。

## 13. WP-05：项目裁剪预设和维护说明

### 13.1 单一模块清单

scaffold/catalog/modules.yaml 为唯一事实来源，每个模块声明：

- id、artifactId、type、version。
- requiredModules、optionalModules、conflicts。
- backend Maven 模块。
- frontend view/API/i18n globs。
- Gateway routes。
- Compose services。
- database changelog 和 seed。
- permission roots 和 provisioning seed ID；只引用 database/seed/manifest，不复制种子内容。
- Nacos config。
- ports。
- MQ producers/consumers。
- XXL handler/appname。
- docs。
- resourceHints。
- deprecation 和 compatibility。

所有路径在 catalog validate 时必须真实存在；反向扫描也必须报告仓库中未被 catalog 管理的正式模块。

### 13.2 五个正式预设

| 预设 | 后端服务 | 主要基础设施 | 业务边界 |
|---|---|---|---|
| core | auth、base、gateway | MySQL、Redis、Nacos；Job/MQ 可按功能开关 | 认证、RBAC、组织、字典、日志、基础任务 |
| workflow | core + workflow | core + Workflow DB | BPMN、审批、待办、流程实例 |
| crm | core + crm | core + CRM DB | CRM 销售管道，不带供应链 |
| supply-chain | core + workflow + srm + procurement + asset | core + Workflow、RocketMQ、XXL-JOB | 供应商、采购、资产闭环 |
| full | core + workflow + crm + srm + procurement + asset | 全部 | 当前完整能力 |

依赖硬规则：

- asset 依赖 procurement、workflow 和 srm 契约。
- procurement 依赖 srm 和 workflow。
- srm 的准入审批依赖 workflow。
- CRM 不依赖 workflow。
- 任何 Servlet 业务服务依赖 common-service。
- Gateway 只依赖 common-redis-reactive。
- common-workflow 只随 workflow 服务进入生成工程。

若未来改变这些规则，必须先修改模块系统真相文档和 catalog，再修改预设。

### 13.3 预设命令

~~~text
omni preset list
omni preset explain <preset>
omni preset create <preset> --output <new-directory>
omni preset validate <preset>
omni preset diff <left> <right>
~~~

安全规则：

- 输出目录必须不存在或为空。
- 默认不修改当前源仓库。
- 生成前显示服务、端口、数据库、页面、权限和资源估算。
- 生成后写入 scaffold.lock，记录源版本、presetVersion、模块和模板版本。
- 自定义预设只能组合 catalog 中合法模块。
- 错误组合在写文件前失败。

### 13.4 维护文档

必须新增并提供四语言：

- preset-quick-selection。
- preset-maintenance。
- preset-dependency-matrix。
- custom-preset-tutorial。
- preset-upgrade-guide。

维护手册必须能完成：

1. 新增模块。
2. 把模块加入或移出预设。
3. 声明必选、可选、冲突和传递依赖。
4. 更新后端、前端、权限、数据库、Compose、消息和文档落点。
5. 增加 presetVersion 和兼容说明。
6. 生成并验证黄金样例。
7. 失败回滚和定位。

依赖矩阵和 README 预设表由 modules.yaml 自动生成，不手工维护第二份事实。

### 13.5 预设验收矩阵

每个预设执行：

- catalog/preset schema validation。
- 生成到临时目录。
- Maven clean install。
- 前端 npm ci、lint、build。
- db-migrator fresh。
- docker compose config。
- 启动该预设。
- 登录、菜单、health 和一条核心冒烟流程。
- 扫描被裁掉模块的 Maven、路由、页面、权限、DB、MQ 和文档残留。

full 还必须执行当前 18 条 E2E 及新增关键闭环；较小预设执行按能力裁剪后的 E2E，不把不存在功能判成失败。

## 14. WP-06：轻量开发模式

### 14.1 Compose 目标

当前 15 service 单一 Compose 拆为可组合配置，但保持一条完整启动命令：

- compose.yaml：网络、卷和最小公共定义。
- compose.infra.yaml：MySQL、Redis、Nacos、RocketMQ、XXL-JOB。
- compose.apps.yaml：业务服务和前端。
- compose.observability.yaml：WP-07 观测栈。
- profile 映射由 preset/catalog 生成或校验。

建议 profile：

- core。
- workflow。
- crm。
- supply-chain。
- full。
- observability。
- docs。

同一服务可以属于多个 profile；不得复制 service 定义。

### 14.2 功能开关

轻量模式不能只是不启动依赖，还要显式关闭客户端：

- XXL_JOB_ENABLED=false 时不注册执行器和任务。
- MQ relay disabled 时仍可按约定写 Outbox，页面明确显示未启用投递；若业务要求实时消费者则 preset 校验必须拒绝。
- Nacos 可在 local profile 下关闭 discovery/config，使用本地配置。
- Observability 未启用时 OTLP exporter 不应阻塞启动。

每个开关必须有自动配置测试，禁止用捕获异常后继续运行掩盖错误。

### 14.3 开发命令

~~~text
omni dev up --preset core
omni dev up --module crm
omni dev down
omni dev status
omni dev doctor
omni dev logs --module crm
omni test deps --module crm
~~~

doctor 检查：

- JDK 25、Node >=22.12、Docker Compose。
- 端口冲突。
- .env 必填项但不输出值。
- 磁盘、Docker daemon、数据库连接。
- preset 依赖闭包。
- 服务 health 和 Nacos 注册。

### 14.4 模块开发组合

| 开发对象 | 最小运行组合 |
|---|---|
| 前端公共页 | frontend + mock/指定 Gateway，不默认启动业务服务 |
| Auth/Base | MySQL、Redis、Nacos、auth、base、gateway、frontend |
| Workflow | core + workflow；按任务/MQ场景启用 XXL/RocketMQ |
| CRM | core + crm |
| SRM | core + workflow + srm；Portal 报价联调再加 procurement |
| Procurement | core + workflow + srm + procurement |
| Asset | supply-chain 全依赖 |

此表由真实依赖验证后写入四语言文档，不能只依据 POM 推断运行依赖。

### 14.5 WP-06 验收

- 开发 CRM 时无需启动 Asset、Procurement 和 SRM。
- 开发公共登录页时无需启动完整 15 service。
- full 仍能一条命令启动。
- 每个 profile 的 docker compose config 无错误。
- 停止默认保留命名卷；删除卷必须显式确认。
- Windows 不再要求为未启动服务预留全部端口。

## 15. WP-07：可观测性模板

### 15.1 应用侧

统一引入：

- spring-boot-starter-actuator。
- micrometer-registry-prometheus。
- Micrometer Tracing OTel bridge。
- OTLP exporter。
- Spring Boot 内置结构化 JSON 日志。

统一标签只允许：

- service.name。
- environment。
- instance。
- HTTP method、route template、status。
- exception class。
- MQ destination 和 result。
- 代码中封闭枚举的 operation 和 status；新增前必须证明取值集合固定。
- 数据库迁移指标可额外使用受版本控制的 schema version；不得使用动态 SQL、表名或连接地址。

禁止把 tenantId、userId、username、businessKey、URL 原始路径或消息 payload 作为 Metrics label。

### 15.2 Trace 兼容

- W3C traceparent 作为标准跨服务传播。
- 现有 X-Trace-Id 保留一个兼容周期，并映射到当前 traceId。
- Gateway 必须删除伪造的外部身份头，但可按标准规则接收或重建 trace 上下文。
- Feign、RestClient、WebClient、StreamBridge 和消息消费者接入 Observation。
- Outbox 记录 msgId/eventId 作为日志字段，不作为高基数 Metrics label。
- MQ 消费日志同时记录 producerTraceId 和 consumerTraceId，保持异步因果关系。

### 15.3 观测栈

observability profile 包含：

| 组件 | 责任 |
|---|---|
| Prometheus | 抓取应用和基础设施指标 |
| Pushgateway | 接收迁移器等短生命周期任务的结束指标 |
| Node Exporter | 提供宿主文件系统与节点资源指标 |
| cAdvisor | 提供本地容器资源和重启指标 |
| Alertmanager | 告警路由模板 |
| Grafana | Dashboard 和统一查询入口 |
| Tempo | Trace 存储 |
| Loki | 结构化日志存储 |
| Alloy | 容器日志采集和转发 |
| OpenTelemetry Collector | OTLP 接收、处理和导出 |

管理端口只暴露到 Docker 内网或 127.0.0.1，不经 Gateway 暴露。生产部署文档必须要求认证、TLS、保留策略和外部 Secret。

### 15.4 自定义指标

至少提供：

- HTTP 延迟、错误率、吞吐。
- JVM、连接池、线程、GC。
- Feign 调用延迟与错误。
- Outbox PENDING/FAILED/DEAD_LETTER 数量和最老消息年龄。
- XXL-JOB 注册失败和任务失败。
- Workflow 启动失败、待办积压和完成延迟。
- Procurement Workflow start retry。
- Asset/SRM/Procurement Inbox 重试和死信。
- 数据库迁移成功/失败和版本。

### 15.5 Dashboard 与告警

Dashboard：

1. 平台总览。
2. 单服务 RED。
3. JVM/连接池。
4. Feign 依赖图。
5. MQ/Outbox。
6. Workflow。
7. 数据库迁移。

告警最小集：

- 5xx 错误率。
- P95/P99 延迟。
- 实例不可用。
- 数据库连接池耗尽。
- Outbox 最老消息超阈值。
- DEAD_LETTER 增长。
- Workflow 启动失败持续。
- 磁盘、内存和容器重启。

所有阈值在示例环境提供默认值，并标注“必须按真实容量校准”；不能把示例阈值宣称为生产 SLO。

### 15.6 SLO 模板

提供：

- 可用性 SLI。
- 成功请求率。
- 延迟分位。
- 消息交付新鲜度。
- 审批启动成功率。
- 错误预算和多窗口燃烧率示例。

SLO 文档区分：

- 平台模板。
- 业务服务自定义项。
- 本地开发不启用值班路由。
- 生产告警接收人由部署方配置。

### 15.7 WP-07 验收

- Gateway → 业务服务 → Feign → Workflow 的一次请求可用同一 Trace 查询。
- Procurement Outbox → Broker → Asset Consumer 能建立异步关联。
- Prometheus target 全部健康。
- Dashboard 无缺失数据源或无效查询。
- promtool 校验告警规则。
- observability profile 关闭时业务服务仍正常启动。
- 采样关闭/降低后性能开销在约定预算内；具体预算在基准测试后记录。

## 16. WP-09：四语言文档、README 与全流程截图

### 16.1 目标

建立“代码、配置、权限、文档、截图”同步更新的机制，使第一次接触项目的使用者能在不阅读源码的前提下完成环境启动、功能体验、项目裁剪、二次开发和问题排查。

本工作包不是简单翻译已有文字。必须先修正文档与代码不一致的内容，再以中文为事实源同步英文、日文和韩文版本。

### 16.2 文档信息架构

中文源文档继续位于 docs 根目录及其现有子目录；语言版本保持当前的同目录文件后缀约定：

- 中文源：name.md。
- 英文：name.en.md。
- 日文：name.jp.md。
- 韩文：name.kr.md。

新增 docs/docs-manifest.yaml，至少记录：

- 中文源文件和三份翻译文件的映射。
- 文档类型、责任模块和维护责任人。
- 源文档内容摘要值。
- 最后一次人工复核日期。
- 代码、API、权限或截图依赖。
- 是否允许仅中文存在；正式交付文档默认不允许。

翻译状态不得只依据文件是否存在判断。源文档摘要值变化后，三种翻译必须被标记为待同步，CI 应阻止在未同步状态下发布正式文档站或版本。

### 16.3 必须补齐的文档

第一批按本文写入后的仓库基线补齐：

1. 两份功能审查记录的英文、日文、韩文版本。
2. docs/scaffold-upgrade-plan.md 的英文、日文、韩文版本。
3. 本实施计划的英文、日文、韩文版本。
4. docs/design 下 SRM、Procurement、Asset 及其他当前仅中文设计文档的三种翻译。
5. 以下面向使用者的新文档及其四语言版本：
   - 五分钟快速启动。
   - 登录、验证码、社交登录与租户选择。
   - 菜单、角色、功能权限和数据权限。
   - 系统配置、安全配置和审计日志。
   - 工作流建模、发布、审批和候选人规则。
   - 系统任务、个人任务和任务类型扩展。
   - CRM 完整业务流程。
   - SRM 管理端和供应商门户完整业务流程。
   - 采购申请、询价、报价、订单、收货完整业务流程。
   - 资产入账、领用、归还、调拨和处置完整业务流程。
   - 项目预设、轻量模式、服务创建和 CRUD 生成教程。
   - 运维、可观测性、备份、恢复和升级指南。
   - 常见故障与排查手册。

已有架构、API、后端规范、前端规范、核心流程、调度、工作流、CRM、SRM、Procurement、Asset 和 MQ 可靠性文档也必须在对应功能完成后同步修订，不能以新增教程替代事实文档。

根 AGENTS.md 也是执行规则事实源。数据库、权限种子、模块结构、构建命令或新增 Starter 改变时，必须与代码和上述文档在同一工作包更新；最终不得继续指示开发者修改已经删除的 init-all 或 migrate SQL。

### 16.4 UI 国际化补齐

文档四语言与界面语言必须统一。实施项包括：

- 新增 ja-JP 和 ko-KR 语言包。
- 将当前只在 zh-CN 与 en-US 之间切换的二元逻辑替换为共享 LanguageSelector 组件。
- 清理页面中的硬编码中文、英文和固定 zh-CN 格式化参数。
- 日期、数字、金额、分页、校验提示、空状态和后端错误映射均随语言切换。
- 业务枚举展示文案与后端稳定值分离，禁止翻译后改变接口参数。
- Playwright 为四种语言分别验证登录后菜单、核心标题、关键表单和错误提示。

翻译应由熟悉业务术语的人复核。自动翻译可以生成初稿，但不能作为最终验收证据。

### 16.5 README 更新

根 README.md、README.en.md、README.jp.md、README.kr.md 必须保持相同章节结构，至少覆盖：

- 项目定位、适用场景和非目标。
- 当前技术栈和准确版本。
- 服务、公共模块、端口和依赖关系。
- 标准模式、轻量模式和项目预设。
- 最短启动路径和完整开发路径。
- 默认账号只在开发种子数据中提供，并明确修改要求。
- 登录方式和验证码说明。
- 主要业务模块入口。
- 架构图、核心业务流程图和截图索引。
- 数据库迁移、升级和回滚约束。
- 生成器、CLI 和公共 Starter 使用入口。
- 测试、质量门和 CI 状态。
- 生产部署安全提示。
- 四语言切换链接和文档导航。

README 中的模块数量、端口、命令、文件路径和截图必须由 CI 或文档校验脚本核对；不能保留已经被代码替代的历史描述。

### 16.6 截图自动化设计

新增独立的文档截图测试套件，建议结构：

~~~text
omni-frontend/
├─ e2e-docs/
│  ├─ fixtures/
│  ├─ flows/
│  └─ screenshot-manifest.yaml
├─ playwright.docs.config.ts
└─ scripts/docs-screenshot/
docs/
└─ images/
   ├─ zh-CN/
   ├─ en-US/
   ├─ ja-JP/
   └─ ko-KR/
~~~

每一张正式截图在 screenshot-manifest.yaml 中记录：

- 稳定 ID、模块、流程、步骤和界面语言。
- 使用角色、路由、视口和前置数据。
- 操作前状态、操作动作和期望结果。
- 最终图片路径和对应 Playwright 用例。
- 需要遮罩的账号、令牌、邮箱、手机号或业务敏感字段。
- 适用版本和最后一次成功生成时间。

截图执行规则：

1. 使用隔离的 fresh 数据库和固定、可重复执行的 E2E fixture。
2. 登录后截图使用环境变量注入的短期测试令牌或正式登录流程；令牌不得写入仓库。
3. 不破解、不绕过 CAPTCHA。登录页只验证验证码控件展示；需要真实登录时使用后端受控测试 fixture 或预置短期令牌。
4. 正式图片至少生成桌面视口 1440 × 900；供应商门户、审批规则页和关键表单另验收 390 × 844，审批规则页再验收 1024 × 768。
5. 禁用不稳定动画、固定时区和系统时间、等待网络空闲及目标控件稳定。
6. 截图前统一遮罩敏感数据；测试报告不得回显令牌。
7. 临时图片在任务结束时删除，只保留 manifest 引用的最终优化图片。
8. 同一流程的四语言截图使用相同 fixture、步骤编号和视口。

建议提供以下命令：

~~~text
npm run docs:screenshots
npm run docs:screenshots:check
npm run docs:links:check
npm run docs:i18n:check
~~~

### 16.7 截图覆盖矩阵

每个主要模块至少覆盖“入口或概览、列表、详情、新增或编辑、关键业务动作、成功结果、失败或权限受限状态”。无对应状态的模块必须在 manifest 中写明豁免原因。

| 模块 | 必须覆盖的核心流程 |
|---|---|
| 公共与认证 | 密码/CAPTCHA、注册、社交登录、设备码授权和登录失效恢复 |
| 系统管理 | 租户、组织、用户、角色、权限、菜单、数据权限、参数、字典、OAuth2、在线用户、登录记录、审计和 XSS |
| 调度与个人工作台 | 系统任务、任务类型、个人任务创建、调度、触发、暂停、恢复和执行日志 |
| MQ 与监控 | 消息状态、失败重试、死信处理、操作日志和 Trace ID 排障 |
| 工作流 | 模型创建、BPMN 设计、候选人配置、校验、发布、发起、待办审批、会签、完成和实例跟踪 |
| CRM | 线索、转客户、联系人、商机阶段、活动和概览 |
| SRM | 供应商邀请、Portal 注册、准入审批、激活、生命周期、评估、风险和供应商报价 |
| Procurement | 物料、审批规则配置与匹配、申请审批、询价、报价接收与比价、订单、收货和概览 |
| Asset | 收货建卡、资产台账、分配、接收、归还、调拨审批、处置审批和概览 |
| 权限与异常 | 普通员工、采购管理员、供应商三类角色范围，以及 403、404、菜单失败和接口失败 |
| 开发脚手架 | create-service、CRUD 生成、预设选择、轻量模式 |
| 运维 | Compose 启动、健康检查、追踪、Dashboard 和告警示例 |

文档中的流程截图必须按步骤编号，并在图片前说明前置条件、操作者和预期状态，不能只展示互不关联的页面快照。

### 16.8 文档校验与发布

PR 必须执行：

- Markdown 链接、锚点和本地图片存在性校验。
- 四语言映射和源摘要同步校验。
- README 命令、端口、模块名称和路径校验。
- manifest 中用例和最终图片一一对应校验。
- 敏感信息模式扫描。
- 关键页面的 Playwright 实际执行，不再只执行用例列表。

完整四语言截图再生成放在受信任的夜间或发布流水线中运行。流水线使用隔离测试数据和 Secret，输出差异报告，人工确认有意的视觉变化后才更新正式图片。

### 16.9 WP-09 验收

- docs-manifest 中不存在正式文档缺译或过期翻译。
- 四份 README 章节、命令和事实一致。
- ja-JP、ko-KR 可以在 UI 中直接选择并持久化。
- 所有主要模块均满足截图覆盖矩阵或有明确豁免。
- 任一正式图片都能追溯到稳定 Playwright 用例和 fixture。
- 全新用户仅依赖 README 和用户指南即可完成启动、登录、核心流程体验和新服务创建。
- 文档链接、截图、敏感信息和国际化检查全部通过。

## 17. WP-10：临时脚本、历史迁移文件和中间产物清理

### 17.1 清理原则

清理是最后一个工作包，只能在替代工具、Liquibase、自动化测试和最终文档全部可用后执行。判断依据不是文件名看起来是否临时，而是：

1. 是否被构建、运行、测试、文档、Compose 或其他脚本引用。
2. 是否承载尚未迁移的结构、种子、fixture 或故障恢复知识。
3. 是否有受维护的正式替代物。
4. 删除后能否通过 fresh、upgrade 和全量回归证明没有能力丢失。

所有删除都应在独立提交中完成，并在删除前创建可定位的 Git 标签或里程碑提交。Git 历史是恢复手段，但不能替代删除前的引用审查。

### 17.2 当前清理候选基线

当前 scripts 根目录共 31 个文件，其中包含 16 个 PowerShell、6 个 Python、4 个 SQL、3 个 JSON、1 个二进制文件和 1 个文本文件。名称包含 check、fix、temp、raw、result、output、test、verify、cleanup 的文件全部进入候选清单，但在引用扫描和内容归档完成前不得直接删除。

重点候选类别：

- 一次性接口调用、编码修复、实例修复和状态核验脚本。
- api-output、api-raw、api-result、raw-json 等调试输出。
- 临时 BPMN、临时请求体和二进制响应。
- 已被正式测试替代的 curl、审批、启动和验证脚本。
- 仅服务于某次数据修复的 SQL 和 Python 文件。
- 未被文档或测试引用的历史截图和中间图片。

scripts/sql 当前 16 个文件必须按 WP-00 的迁移台账处理：

| 当前内容 | 最终去向 |
|---|---|
| 表、索引、约束、存储过程和升级 DDL | Liquibase changelog 或正式 Java 迁移逻辑 |
| 固定权限、菜单、字典和示例配置 | scripts/sql/seed 下的幂等种子文件 |
| E2E 样例业务数据 | 测试 fixture，不保留在正式 SQL 目录 |
| 检查、修复和过渡迁移 SQL | 验证已吸收后删除 |
| Nacos、XXL-JOB 等受版本约束的数据库结构 | vendor changelog，并记录上游版本 |

scripts 根目录的 4 个修复/清理 SQL 与后端资源目录的 5 个初始化 SQL 适用同一规则；它们不能因为不在 scripts/sql 中而绕过 inventory、替代物验证和最终清理。

最终全仓 .sql 只允许位于 scripts/sql/seed 并承载种子数据；其他目录出现 .sql，或 seed 文件出现 DDL、ALTER、DROP、CREATE TABLE、存储过程及一次性修复语句时，CI 必须失败。

### 17.3 正式保留白名单

清理后允许保留的脚本或工具必须满足全部条件：

- 有清晰名称、入口命令、参数说明和维护责任。
- 有自动化测试或至少有可重复的验证命令。
- 被 README、运维指南或 package/Maven 脚本引用。
- 不包含固定凭据、访问令牌、个人路径或真实业务数据。
- 临时文件写入操作系统临时目录，并在 finally 或等价机制中清理。

预期正式类别仅包括：

- create-service CLI。
- CRUD generator。
- project preset 工具。
- 文档、截图、链接和国际化校验工具。
- 必需的部署、备份、恢复和诊断工具。
- E2E fixture 和测试启动器。
- scripts/sql/seed 下的最终幂等种子数据。

scripts/bpmn 中仍有运行时或测试价值的模型应分别迁入 Workflow 正式资源或测试 fixture，并由测试和文档引用；没有消费者的模型才可删除。

### 17.4 清理执行步骤

1. 使用 Git 文件清单和全文引用扫描生成 cleanup-inventory.json。
2. 为每个候选标记 keep、move、replace 或 delete，并记录理由和替代物。
3. 将仍有价值的逻辑迁入正式 CLI、测试、Liquibase、fixture 或文档。
4. 运行替代物的定向测试。
5. 按“输出文件、临时脚本、历史 SQL、孤立图片”分批删除，每批独立提交。
6. 每批执行完整质量门，并重新扫描悬空引用。
7. 生成 docs/maintenance/cleanup-report.md，列出删除、迁移、保留和恢复方式。

清理报告不记录 Secret 内容，只记录文件路径、分类、替代物和验证证据。

### 17.5 WP-10 验收

- scripts 根目录无无法说明用途的临时文件或调试输出。
- 全仓 .sql 只位于 scripts/sql/seed，且全部为幂等种子数据。
- 无仓库内引用指向已删除文件。
- 无真实令牌、密码、个人绝对路径或测试产生的业务数据。
- fresh 数据库、upgrade 数据库、全部预设、后端、前端和 E2E 均通过。
- cleanup-report 与实际 Git 变更一致。
- 最终仓库克隆后不依赖任何被删除的本地残留即可完整启动。

## 18. 全局测试矩阵与质量门

### 18.1 本地标准验证命令

后端：

~~~powershell
$env:JAVA_HOME='C:\APP\JDK25\jdk-25.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
Set-Location omni-backend
.\mvnw.cmd clean install
~~~

前端：

~~~powershell
Set-Location omni-frontend
npm ci
npm run lint
npm run build
~~~

Compose 与浏览器测试：

~~~powershell
# 先依据 .env.example 配置全部必填变量；不得把真实 Secret 写入命令或日志
docker compose config --quiet
docker compose up -d
docker compose ps
Set-Location omni-frontend
npm run test:e2e
~~~

实施中新增的 CLI、生成器、数据库、预设、文档和截图命令必须写入根 README 以及 CI，不能只存在于开发者本机历史中。

### 18.2 测试层次

| 层次 | 覆盖内容 | 何时执行 |
|---|---|---|
| 静态检查 | Java 编译、Checkstyle 类约束、TypeScript、ESLint、YAML、Compose、BPMN | 每次 PR |
| 单元测试 | 路由匹配、模板 helper、配置合并、预设依赖闭包、敏感字段处理 | 每次 PR |
| 模块集成测试 | Controller、Service、Mapper、租户、数据权限、Outbox、Liquibase | 每次 PR |
| 契约测试 | Gateway 路由、Feign DTO、R 与 PageResult、权限码、Workflow 内部 API | 每次 PR |
| 生成物测试 | 新服务、CRUD、预设工程生成后编译、lint、测试 | 每次 PR 的代表样例；夜间全矩阵 |
| 数据库测试 | 空库 fresh、当前基线 upgrade、重复执行、失败恢复、租户初始化 | 每次数据库变更 |
| 浏览器 E2E | 真实启动栈上的登录后核心流程、权限和错误状态 | PR 冒烟；夜间全量 |
| 文档测试 | 链接、路径、命令、四语言同步、截图 manifest、敏感信息 | 每次 PR |
| 非功能测试 | 启动时间、内存、接口延迟、追踪开销、镜像体积 | 阶段出口及发布 |
| 安全测试 | 依赖漏洞、Secret、XSS、越权、租户隔离、内部接口认证 | 每次 PR 与发布 |

当前 Playwright 基线在完整环境变量和可变更 fixture 下展开为 18 个场景；现有 CI 只执行 test --list。升级期间必须把 CI 改为在隔离环境中实际执行冒烟场景，并保留完整 18 场景及新增流程的夜间执行。

### 18.3 必须建立的 CI 作业

PR 必需作业：

1. catalog-validate：模块目录、依赖和模板元数据校验。
2. database-validate：Liquibase 语法、顺序、校验和及禁止危险变更检查。
3. backend-build：JDK 25 下完整 Maven reactor。
4. frontend-quality：npm ci、lint、type-check 和 production build。
5. generator-smoke：至少生成一个服务和一个 CRUD 垂直切片并编译。
6. preset-smoke：lite 及一个业务预设完成配置生成、编译和 Compose 配置校验。
7. docs-quality：链接、四语言、截图 manifest 和敏感信息检查。
8. compose-e2e-smoke：真实启动依赖和代表服务，实际执行浏览器冒烟。
9. dependency-security：Maven、npm、镜像和 Secret 扫描。

夜间或发布作业：

- 所有官方预设的 fresh 生成、启动和健康检查。
- 当前正式数据库快照的 upgrade 克隆测试。
- 全量浏览器 E2E 和四语言截图再生成。
- 多租户、数据权限、Workflow、Outbox、Inbox 跨服务回归。
- 可观测性数据连通、Dashboard 查询和告警规则校验。
- 性能、启动时间、内存和镜像体积对比。

### 18.4 阶段质量门

| 门 | 通过条件 | 阻止事项 |
|---|---|---|
| G0 基线门 | 当前构建、测试、lint、Compose、E2E 证据归档 | 未知红灯进入重构 |
| G1 数据门 | fresh、upgrade、重复执行和失败恢复通过 | Starter、CLI 和预设大规模改动 |
| G2 审批规则门 | 创建、编辑、冲突、预览、提交匹配、三视口通过 | R-01 发布 |
| G3 Starter 门 | 四个试点服务迁移完成且安全回归通过 | 全服务迁移 |
| G4 生成器门 | 同一 catalog 驱动 CLI、CRUD、预设且生成物无漂移 | 对外宣布脚手架可用 |
| G5 预设门 | 所有官方预设均能 fresh 启动并通过权限/租户测试 | 轻量模式发布 |
| G6 可观测性门 | 同步、异步 Trace、指标、日志和告警证据完整 | 运维能力发布 |
| G7 文档门 | 四语言、README、全流程截图和新手走查通过 | 正式版本发布 |
| G8 清理门 | 替代物、引用扫描、全量回归和清理报告通过 | 删除历史脚本 |

任一质量门失败时只能修复或回退当前工作包，不能通过修改验收标准、隐藏失败用例或删除测试放行。

### 18.5 安全与隔离专项回归

每个公共 Starter、生成模板和预设都必须验证：

- TenantLine 只拦截本服务业务表。
- DataPermissionInterceptor 位于 PaginationInnerInterceptor 之前。
- 子表权限通过聚合根继承，不假设不存在的 owner 字段。
- 所有写 Controller 权限码与种子、前端 v-permission 一致。
- MyJobController 仍使用逐行 ownership，不增加 PreAuthorize。
- Gateway 保持响应安全头且只依赖 reactive Redis。
- Auth 不引入操作日志依赖，登录行为仍由登录日志保存。
- XSS SPI、内部 API 认证和短期测试令牌不会形成生产旁路。
- Outbox 显式传 tenantId，后台 Relay 仍能跨租户扫描。
- Procurement 和 Asset 不引入 Flowable，审批运行时仍属于 Workflow。

## 19. 版本、发布与回滚策略

### 19.1 版本边界

- 公共 Starter、create-service CLI、CRUD generator 和 preset 工具分别使用语义化版本。
- 生成工程写入 scaffold.lock，记录 catalog、模板、CLI 和预设版本。
- 数据库 changelog 只能追加；已发布 changeSet 不允许修改内容。
- API 的兼容期、废弃日期和替代接口写入 api-contract 及发布说明。
- 文档和截图标记适用的产品版本，避免新版图片解释旧版行为。

### 19.2 发布顺序

1. 先发布数据库 expand 变更和向后兼容代码。
2. 再发布 Workflow 内部查询能力和公共 Starter。
3. 按 CRM → SRM → Procurement → Asset → Base/Auth/Gateway 的适用性逐服务迁移；每个服务独立验收。
4. 发布审批规则新 UI，并保留旧字段读取兼容期。
5. 发布 CLI、CRUD、preset 和 lite 模式的候选版本。
6. 发布可观测性 profile。
7. 完成四语言文档和截图后发布正式脚手架版本。
8. 至少跨一个正式版本确认无旧消费者后执行数据库 contract 和最终清理。

Auth 和 Gateway 不套用 servlet 业务 Starter 的迁移顺序，应按其特殊约束单独实施。

### 19.3 数据库回滚

数据库采用 expand-migrate-contract：

- expand 阶段只增加兼容列、表、索引和双读或回填能力。
- migrate 阶段完成数据回填、校验和消费者切换。
- contract 阶段在下一兼容窗口删除旧结构。

生产回滚默认回滚应用版本，不执行自动向下迁移。发布前必须完成数据库备份、恢复演练和旧应用对 expand 结构的兼容验证。破坏性变更必须有独立审批和维护窗口。

### 19.4 功能回滚

- 审批规则新 UI 通过路由级开关回到兼容页，但所有新旧页面调用同一后端匹配语义。
- Starter 逐服务迁移，每个服务保留一个可回退提交，不同时切换全部服务。
- CLI、CRUD 和 preset 通过版本固定回退；不得覆盖已经存在的用户文件。
- observability profile 默认可关闭，关闭后业务启动和请求链路不受影响。
- 文档图片更新按独立提交审核，可回退图片而不回退业务代码。

### 19.5 变更控制

以下变化必须先形成 ADR，再调整本文：

- 更换 CLI 语言或模板引擎。
- 改变数据库迁移工具或一站式 migrator 责任边界。
- 改变模块 catalog 的唯一事实源。
- 允许预设在原仓库内做破坏性裁剪。
- 将审批运行时移出 Workflow。
- 改变日志、指标或 Trace 后端技术栈。

## 20. 工作量、日历时间与 Token 预算

### 20.1 角色和责任

| 责任 | 必须承担的内容 |
|---|---|
| 技术负责人 | D-01～D-09、模块边界、API 兼容、阶段门和范围控制 |
| 数据库负责人 | inventory、Liquibase、adopt、备份恢复、租户 provisioning 和数据验证 |
| 后端负责人 | 审批规则、Workflow 内部契约、Starter、生成模板和安全隔离 |
| 前端/体验负责人 | 审批规则 UI、国际化、响应式、可访问性和 lint |
| 平台/DevOps 负责人 | CLI、preset、Compose、CI、可观测性和发布回滚 |
| 测试自动化负责人 | 黄金样例、fresh/upgrade、契约、E2E、截图 fixture 和证据 |
| 文档/本地化负责人 | 中文事实源、英日韩复核、README、新手走查和截图审核 |
| 安全评审人 | Secret、租户、DataScope、XSS、内部接口、依赖和生产边界 |

同一人员可以承担多个责任，但数据库 adopt、公共安全 Starter 和最终删除三个 P0 操作必须至少由另一名评审人复核。

### 20.2 分项估算

| 工作包 | 人日估算 | Token 估算 |
|---|---:|---:|
| WP-00 数据库版本管理与基线 | 8～12 | 0.35M～0.60M |
| WP-01 审批规则业务化 UI | 8～12 | 0.40M～0.70M |
| WP-02 公共 Starter | 15～22 | 0.85M～1.35M |
| WP-03 create-service CLI | 12～18 | 0.65M～1.05M |
| WP-04 CRUD generator | 15～22 | 0.80M～1.25M |
| WP-05 项目预设 | 10～15 | 0.50M～0.80M |
| WP-06 轻量模式 | 7～10 | 0.30M～0.55M |
| WP-07 可观测性 | 12～18 | 0.65M～1.05M |
| WP-08 lint 治理 | 4～7 | 0.20M～0.35M |
| WP-09 四语言文档与截图 | 25～40 | 1.50M～2.60M |
| WP-10 最终清理 | 5～8 | 0.15M～0.35M |
| 集成回归、评审和风险储备 | 10～15 | 0.70M～1.00M |
| 合计 | 131～199 人日 | 7.05M～11.65M |

建议执行预算为约 9.5M Token，硬上限预留为约 12M Token，与路线图估算一致。Token 估算包含代码阅读、生成、工具输出、失败重试、测试分析和文档同步，不是模型供应商的计费承诺。

### 20.3 日历时间

- 单人串行：约 26～40 周。
- 具备后端、前端/体验、平台/测试、文档/本地化四类责任并行：约 10～16 周。
- 如果只能投入两名全栈工程师：约 16～24 周。

上述日历时间以需求冻结、开发环境稳定、评审在一个工作日内反馈为前提。生产环境接入、四语言人工校对、截图逐张审核和历史数据库样本质量可能延长周期。

### 20.4 预算控制

- 每个工作包开始前记录实际基线和剩余预算。
- 达到单项估算上界 80% 时进行范围与风险复盘。
- 生成器、预设和截图优先通过确定性脚本复用，避免重复手工生成。
- 不以压缩测试、跳过文档或保留临时脚本的方式节省 Token。
- 超过 12M 前必须重新批准范围；不得自动扩大到业务新功能。

若通过 ChatGPT Plus/Codex 交互执行，实际可用量由当时产品的使用限额、模型和任务复杂度决定，并不等同于固定 ChatGPT credits。若通过 API 执行，费用应按实际输入、缓存输入、输出和工具相关用量乘以执行时的官方单价单独核算。

## 21. 风险登记与处置

| 风险 | 等级 | 触发信号 | 预防与处置 |
|---|---|---|---|
| 既有数据库被错误当成空库迁移 | P0 | changelog 空但业务表已存在 | 指纹核验、adopt 流程、只读预检、备份与克隆升级演练 |
| init-all 内容拆分后遗漏租户初始化逻辑 | P0 | 新租户权限或业务配置缺失 | 建立 DDL/seed/procedure 台账，租户 provisioning 契约测试 |
| 公共 Starter 改变安全或拦截器顺序 | P0 | 越权、跨租户、分页异常 | 先试点、自动配置条件测试、专项隔离回归、逐服务发布 |
| 审批预览和实际提交语义不同 | P0 | 预览命中但提交失败或走不同模型 | 共用 ApprovalRouteResolver evaluate 结果模型和同一集成测试 fixture |
| 生成器覆盖用户文件 | P0 | 非空目录发生静默修改 | 默认拒绝、dry-run、冲突报告、原子写入、无隐式 force |
| 预设裁剪出不完整依赖图 | P0 | 编译通过但运行时缺权限、路由或表 | catalog 依赖闭包、fresh 启动、核心 E2E、维护说明 |
| 清理误删唯一修复知识或数据 | P0 | 删除后升级或故障恢复失败 | inventory、替代物、独立提交、Git 标签、全量回归后删除 |
| 四语言文档与中文事实源漂移 | P1 | 源摘要变化但翻译未改 | docs-manifest、CI 阻断、责任人和人工复核日期 |
| 截图测试不稳定或泄漏 Secret | P1 | 随机差异、报告出现令牌 | 固定 fixture、稳定等待、遮罩、受信任流水线、Secret 扫描 |
| OpenTelemetry 增加延迟或成本 | P1 | P95、CPU、存储显著上升 | 采样策略、基准测试、profile 开关、保留策略 |
| lite 与 full 模式长期分叉 | P1 | 同一模块出现两套代码 | 单一代码路径、配置差异、预设矩阵回归 |
| 外部镜像或插件版本漂移 | P1 | fresh 环境无法复现 | 固定版本、校验摘要、定期受控升级 |
| Token 或时间超预算 | P1 | 工作包达到上界 80% 仍未过门 | 停止扩面、复盘失败原因、拆分交付、用户批准后调整 |

P0 风险未关闭前不得进入对应发布门；P1 风险必须有责任人、截止日期和可观察的缓解证据。

## 22. 路线图到代码的追踪矩阵

| 路线图项 | 主要现有入口 | 计划新增或重点修改 | 核心证据 |
|---|---|---|---|
| R-01 审批规则 UI | ProcApprovalRoute、ApprovalRoutePolicy、ApprovalRouteResolver、procurement/approval-route/index.vue | route_name、Workflow 只读查询、匹配/覆盖/影响预览、业务化组件 | 后端匹配测试、UI 三视口、申请提交一致性 E2E |
| R-02 create-service | 父 pom.xml、Gateway application.yml、docker-compose.yml、前端路由/API/i18n | scaffold-cli、服务模板、scaffold.lock、变更报告 | 黄金样例生成、编译、启动和回滚测试 |
| R-03 公共 Starter | 四个业务服务的 config、security、internal 包；omni-common-mqlog 中内部认证 | omni-common-service、SPI、自动配置、迁移矩阵 | 自动配置测试、四服务安全回归 |
| R-04 CRUD generator | backend/frontend patterns、api-contract、init-all 权限与菜单 | entity descriptor、后端/前端模板、Liquibase changeSet、E2E skeleton | 黄金 CRUD 全栈回归、再生成漂移检查 |
| R-05 项目预设 | Maven modules、Compose services、Gateway routes、菜单/权限/README | modules.yaml、preset.yaml、维护说明、输出工程 | 五预设 fresh 生成、启动和核心 E2E |
| R-06 轻量模式 | docker-compose.yml、各服务 Nacos 配置、前端代理 | lite profile、local 配置、开发组合命令 | lite 启动时间/内存、核心流程、与 full 一致性 |
| R-07 可观测性 | TraceIdFilter、Feign 传播、Actuator | OTel、Prometheus、Grafana、Tempo、Loki、Alloy、告警 | 同步/异步 Trace、指标、日志和告警查询 |
| R-08 lint 治理 | omni-frontend ESLint 和当前 197 warnings | 类型守卫、BPMN adapter、格式化、CI 零 warning | npm run lint 零 error 零 warning、build、E2E |
| R-09 文档和截图 | 四份 README、docs、locales、现有 images、Playwright | docs-manifest、ja/ko UI、用户指南、截图套件 | 四语言同步、链接、截图 manifest、新手走查 |
| R-10 最终清理 | scripts、scripts/sql、scripts/bpmn、历史图片 | inventory、正式替代物、cleanup-report | 引用扫描、fresh/upgrade/预设/全量回归 |
| 跨项数据库基线 | 全仓 25 个 SQL，包括 init-all、各 migrate、Auth/Base db/migration、schema.sql、sp_init_tenant | omni-db-migrator、Liquibase、seed、adopt 协议 | fresh、upgrade、幂等、失败恢复和租户初始化 |

矩阵中的类名是逻辑入口；实施 ticket 必须补充绝对或仓库相对文件路径、方法级影响和测试用例编号。

## 23. 可直接执行的任务序列

### 23.1 执行批次 0：冻结基线并建立数据库地基

| ID | 任务 | 依赖 | 完成产物 |
|---|---|---|---|
| S0-01 | 重跑当前构建、lint、Compose 和 E2E，归档证据 | 无 | 基线报告和失败清单 |
| S0-02 | 评审并冻结 D-01～D-09 | S0-01 | ADR 与签字记录 |
| S0-03 | 建立 SQL/表/种子/存储过程/租户初始化台账 | S0-01 | migration-inventory |
| S0-04 | 创建 omni-db-migrator 和 Liquibase 根 changelog | S0-02、S0-03 | 可启动 migrator |
| S0-05 | 转换平台、业务、vendor 和 MQ schema | S0-04 | 顺序化 changeSet |
| S0-06 | 实现现有库 adopt、指纹校验和备份前置检查 | S0-05 | upgrade runner |
| S0-07 | 替换 sp_init_tenant 并补齐模块 provisioning | S0-05 | Java 编排与契约测试 |
| S0-08 | 通过 fresh、upgrade、重复执行和失败恢复 | S0-06、S0-07 | G1 证据 |

S0-01 只记录基线，不顺手修复问题；任何新发现先进入缺陷清单并确定是否阻断 G1。

### 23.2 执行批次 1：先交付可见体验并清理前端债务

| ID | 任务 | 依赖 | 完成产物 |
|---|---|---|---|
| S1-01 | 为审批规则增加业务名称和兼容迁移 | S0-08 | 数据/API 兼容 |
| S1-02 | 增加 Workflow 已发布模型列表和安全预览 | S0-08 | 内部只读接口 |
| S1-03 | 实现 Procurement 工作流选项、匹配、覆盖和影响预览 | S1-01、S1-02 | 业务外观 API |
| S1-04 | 拆分审批规则页面组件并重写文案和交互 | S1-03 | 新 UI |
| S1-05 | 完成冲突、权限、降级、三视口和申请提交 E2E | S1-04 | G2 证据 |
| S1-06 | 按 unsafe-any → console → reactivity → formatting 清零 lint | S1-05 | G2 前端零 warning |

### 23.3 执行批次 2A：抽取公共 Starter

| ID | 任务 | 依赖 | 完成产物 |
|---|---|---|---|
| S2-01 | 建立重复实现清单、SPI 边界和自动配置条件矩阵 | S0-08 | Starter 设计记录 |
| S2-02 | 创建 omni-common-service 并迁移无业务语义能力 | S2-01 | Starter v0 |
| S2-03 | 迁移 CRM 试点并执行安全专项回归 | S2-02 | CRM 证据 |
| S2-04 | 依次迁移 SRM、Procurement、Asset | S2-03 | 四服务证据 |
| S2-05 | 处理 Base 适用项，确认 Auth/Gateway 特例 | S2-04 | 完整采用矩阵 |
| S2-06 | 删除已经被 Starter 替代的重复实现 | S2-05 | G3 证据 |

### 23.4 执行批次 2B/3A：建立单一元数据驱动的生成工具

| ID | 任务 | 依赖 | 完成产物 |
|---|---|---|---|
| S3-01 | 创建 modules.yaml schema 和 catalog validator | S2-06 | 唯一模块目录 |
| S3-02 | 实现 create-service plan、dry-run 和冲突检查 | S3-01 | CLI 核心 |
| S3-03 | 实现后端、前端、Compose、Gateway、权限和文档模板 | S3-02 | 完整服务生成 |
| S3-04 | 建立 service-golden 黄金样例和编译/启动测试 | S3-03 | R-02 证据 |
| S3-05 | 定义 CRUD descriptor schema 和安全类型映射 | S3-01 | CRUD 输入契约 |
| S3-06 | 实现后端、前端、权限、Liquibase 和 E2E 模板 | S3-05 | CRUD 生成器 |
| S3-07 | 建立 CRUD 黄金样例、再生成和所有权测试 | S3-06 | G4 证据 |

### 23.5 执行批次 3B：项目预设和轻量模式

| ID | 任务 | 依赖 | 完成产物 |
|---|---|---|---|
| S4-01 | 定义五个 preset.yaml 和依赖闭包规则 | S3-07 | 预设声明 |
| S4-02 | 实现目标目录生成、配置裁剪和报告 | S4-01 | preset CLI |
| S4-03 | 编写预设维护说明和新增模块操作流程 | S4-02 | maintenance guide |
| S4-04 | 所有预设执行 fresh 编译、启动和核心 E2E | S4-03 | G5 预设证据 |
| S4-05 | 实现 Compose lite profile 和本地配置 | S3-01 | lite 环境 |
| S4-06 | 实现模块开发组合命令和功能降级提示 | S4-05 | 开发命令 |
| S4-07 | 对比 lite/full 的行为、资源和启动时间 | S4-04、S4-06 | G5 轻量证据 |

### 23.6 执行批次 4A：可观测性

| ID | 任务 | 依赖 | 完成产物 |
|---|---|---|---|
| S5-01 | 统一 Trace/MDC/Feign/MQ 上下文语义 | S2-06 | 应用观测基础 |
| S5-02 | 增加 OTel、Prometheus registry 和自定义指标 | S5-01 | 指标与 Trace |
| S5-03 | 增加 observability profile 和本地观测栈 | S5-02 | Compose 栈 |
| S5-04 | 建立 Dashboard、告警和 SLO 模板 | S5-03 | 运维模板 |
| S5-05 | 完成同步、异步、性能和关闭 profile 回归 | S5-04 | G6 证据 |

### 23.7 执行批次 4B：文档、本地化和截图

| ID | 任务 | 依赖 | 完成产物 |
|---|---|---|---|
| S6-01 | 建立 docs-manifest、链接和翻译同步校验 | S3-01 | 文档 CI |
| S6-02 | 增加 ja-JP、ko-KR UI 并替换二元语言逻辑 | S1-06 | 四语言 UI |
| S6-03 | 修订事实文档并补齐用户、开发、运维指南 | S4-07、S5-05 | 中文事实源 |
| S6-04 | 人工复核三种翻译 | S6-03 | 四语言文档 |
| S6-05 | 建立隔离 fixture、截图 manifest 和 Playwright 套件 | S6-02、S6-03 | 截图流水线 |
| S6-06 | 生成并审核全部流程的四语言截图 | S6-05 | 最终图片 |
| S6-07 | 更新四份 README 并执行新手走查 | S6-04、S6-06 | G7 证据 |

### 23.8 执行批次 5：最终清理与发布

| ID | 任务 | 依赖 | 完成产物 |
|---|---|---|---|
| S7-01 | 生成临时文件、脚本、SQL、BPMN 和图片 inventory | G1～G7 | 清理分类 |
| S7-02 | 迁移仍有价值的逻辑和数据 | S7-01 | 正式替代物 |
| S7-03 | 分批删除并执行悬空引用扫描 | S7-02 | 清理提交 |
| S7-04 | 执行 fresh、upgrade、五预设、全量 E2E 和安全回归 | S7-03 | G8 证据 |
| S7-05 | 发布 cleanup-report、升级说明和最终版本 | S7-04 | 正式交付 |

### 23.9 Ticket 与提交规则

- 一个 ticket 只解决一个可验收风险，必须声明依赖、修改范围、测试和回滚。
- 数据库 changeSet、应用兼容代码、消费者切换和 contract 清理分别提交。
- 自动生成的变更与模板本身分开提交，便于识别漂移。
- 大规模格式化与逻辑修改分开提交。
- 删除临时文件按类别分开提交。
- 每个提交信息使用 Conventional Commits，并明确模块，例如 feat(scaffold)、refactor(common-service)、docs(scaffold)。

## 24. 基线复核与证据留存

### 24.1 每个阶段开始时复核

~~~powershell
git status --short
git rev-parse --abbrev-ref HEAD
git rev-parse --short HEAD
(rg --files omni-backend -g 'pom.xml' | Measure-Object).Count
(rg --files omni-backend | Where-Object { $_ -match 'src[\\/]main[\\/]java[\\/].+\.java$' } | Measure-Object).Count
(rg --files omni-backend | Where-Object { $_ -match 'src[\\/]test[\\/]java[\\/].+\.java$' } | Measure-Object).Count
(rg --files omni-frontend/src/views -g '*.vue' | Measure-Object).Count
(rg --files omni-frontend/src/components -g '*.vue' | Measure-Object).Count
(rg --files omni-frontend/src/api -g '*.ts' | Measure-Object).Count
(rg --files docs -g '*.md' | Measure-Object).Count
(rg --files docs/images | Measure-Object).Count
(Get-ChildItem -LiteralPath scripts -File | Measure-Object).Count
(rg --files -g '*.sql' | Measure-Object).Count
docker compose config --services
~~~

执行 Compose 复核前必须依据 .env.example 提供必填环境变量；若仅做语法检查，可使用本地临时占位值，但不能把占位值用于运行态通过证明。

本文写入后的预期核对值：

- pom.xml 共 18 个：1 个父工程加 17 个子模块。
- 后端主 Java 文件 777 个，测试 Java 文件 132 个。
- 前端 views 59 个、components 31 个、API TypeScript 文件 44 个。
- docs Markdown 文件 52 个。
- docs/images 文件 36 个；加前端 vite.svg 后全仓相关图片资源 37 个。
- scripts 文件 31 个。
- 全仓 SQL 文件 25 个，其中 scripts/sql 为 16 个、scripts 根目录为 4 个、后端资源目录为 5 个。
- Compose 服务 15 个，当前尚无 profile。

这些数字是 main@09a29fe 及本文写入时的基线，不是永久验收常量。后续阶段必须记录变化原因，不能为迎合旧数字删除有效新增文件。

### 24.2 证据目录

每个阶段在 docs/evidence/scaffold-upgrade 下保存一份正式摘要，内容包括：

- Git commit、工具版本、执行时间和环境说明。
- 实际执行命令、退出码和报告路径。
- 通过、失败、跳过数量及跳过理由。
- 数据库 fresh/upgrade 来源摘要，禁止保存真实业务数据。
- Compose 健康状态和 E2E 报告索引。
- 性能基线及与上一阶段差异。
- 已知限制、风险责任人和后续 ticket。

大体积原始构建日志、浏览器 trace、数据库快照和容器镜像放在 CI artifact，不直接提交仓库；仓库只保留可审计摘要和稳定链接。

### 24.3 计划维护

- 本文是执行基线，docs/scaffold-upgrade-plan.md 是目标和范围基线。
- 代码事实变化时先更新对应事实文档，再更新本文的路径和任务。
- 需求新增必须标明属于既有 R 项、缺陷修复还是新范围。
- 估算变化超过单工作包 20% 或总预算 10% 时必须记录原因。
- 每通过一个质量门，更新任务状态、证据链接、实际人日和实际 Token。
- 已完成任务不得仅因文档重排被重新打开；需要新增修复 ticket。

## 25. Definition of Ready 与最终 Definition of Done

### 25.1 开始实施前的 Ready 条件

满足以下条件即可直接从 S0-01 开始，不需要再创建另一份高层开发计划：

- 认可本文的目标、非目标和 D-01～D-09 默认技术决策，或已用 ADR 明确替代决策。
- 指定代码评审人以及数据库、安全、前端体验和文档的责任人。
- 可以使用隔离的 MySQL、Redis、Nacos、RocketMQ、XXL-JOB 和浏览器测试环境。
- 提供一份脱敏的当前数据库克隆用于 upgrade 测试。
- 确认测试短期令牌的受控生成方式；不要求提供 CAPTCHA 破解或生产旁路。
- 当前未提交用户改动已被识别并在实施分支中保护。
- 采用 codex/ 前缀创建实施分支，或由项目明确指定其他分支策略。

缺少生产告警接收人、生产 OAuth Secret 或外部可观测性存储不会阻止本地实现，但会阻止对应生产发布验收。

### 25.2 单个工作包 Done

- 代码、配置、数据库、权限、前端、测试和文档在同一工作包内闭环。
- 定向测试和所有受影响的全局质量门通过。
- 无新增 lint warning、编译 warning、临时脚本或未分类 SQL。
- 兼容、回滚、监控和安全影响有证据。
- 四语言事实源和截图在该能力对用户可见时同步完成；中间阶段可标记未发布，但不能宣称正式交付。
- ticket、提交、变更日志和证据可互相追溯。

### 25.3 整体升级 Done

只有同时满足以下条件，才能声明“高效率脚手架升级完成”：

1. R-01～R-10 和 WP-00 全部通过对应质量门。
2. 后端完整 Maven reactor 在 JDK 25 下通过。
3. 前端 lint 为 0 error、0 warning，production build 通过。
4. CI 实际执行浏览器 E2E，而非仅列举用例；全量场景通过。
5. 五个官方预设均可在全新目录 fresh 生成、编译、启动和完成核心流程。
6. 现有数据库 upgrade、空库 fresh、重复执行、失败恢复和租户初始化通过。
7. create-service 和 CRUD 生成物符合当前架构、API、安全、权限和文档规范。
8. lite 与 full 使用同一业务代码，差异仅来自声明式依赖和配置。
9. 同步调用、异步消息、Workflow 和数据库迁移具备可查询的观测证据。
10. 四种 UI 语言、四份 README、四语言正式文档和全流程截图同步且通过人工复核。
11. 第一次使用者按文档能够独立完成启动、登录、核心业务体验、项目预设和新模块开发。
12. 临时脚本和中间产物已清理，全仓 .sql 只位于 scripts/sql/seed 且只包含最终幂等种子数据。
13. 无已知 P0 风险、无高危依赖漏洞、无 Secret 或跨租户/越权缺陷。
14. 发布、升级、备份、恢复、回滚和维护说明完成演练。
15. 最终审查记录、实际工期、实际 Token 和遗留风险已经归档。

## 26. 实施结论

本计划已经把路线图拆解到可排期、可提交、可测试、可回滚的工作包和 ticket，可以作为直接实施基线。正确的启动点是 S0-01，而不是立即批量改代码；G0 与 G1 会先验证仓库和数据库事实，避免后续在错误基线上扩大改动。

“百分之百正确”在未来实现开始前无法靠文档承诺。本计划采用的替代标准是：当前事实均可从指定 commit 和仓库重新核对，未来选择均标记为决策，所有实现均有质量门、证据、兼容和回滚条件。只要基线或需求变化，就按 24.3 更新本文后继续执行。
