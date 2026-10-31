# 受控验证资产

这些文件保留可复用的测试能力，不参与正式数据库初始化。正式结构与数据分别由 `database/changelog/`、`scripts/sql/seed/` 管理。

- `captcha/CaptchaFixture.java`：受控验证码图片 fixture；真实登录仍须使用服务生成的一次性验证码。
- `integration/*.sql`：显式隔离的双租户与 CRM 测试数据。只能在已核实目标的测试数据库执行，不能导入标准环境或生产库。历史绑定环境的测试保留其 UUID、租户和资源保护条件，不能取消保护条件以冒充通过。
- `observability/alert-receiver.py`：通知送达与短时失败 fixture。只记录白名单字段；标准 Alertmanager 默认不依赖此接收器。
- `observability/perf-compare.py`：对有效业务 GET 的性能比较工具。认证头只从受限文件读取，不打印其值。
- `capture-observability.mjs`：Docker 内采集 Grafana 四语与 Alertmanager 原生英文界面。`alert-controls` 模式只验证 390 像素视口的原生横滚、Muted 标签两次点击及恢复，限制 GET/HEAD，不创建静默或重做故障；当前告警为 0 时不能冒称 firing 证据。与 `dashboard`、`alert` 模式一样必须使用新的仓库外输出目录，边界见[运维教程](../../docs/guides/operations-upgrade.md)。
- `run-token-fixture.ps1`：宿主仅作为 Docker CLI 启动包装器；JDK 25、Maven Wrapper 和测试 classpath 身份生成均在容器中执行。标准应用镜像不包含该 fixture，令牌输出放在仓库外且禁止覆盖已有文件。
- `capture-procurement-readonly.mjs`：Docker 内复查现存教学分类的两种物料、三视口规则试算与当前范围概览。校验固定隔离项目、24 个无宿主端口容器、源码/镜像身份和前后数据摘要；只允许 GET 与只读 match-preview，拦截持久化写入。四语言手机侧栏须经真实点击开合后再操作表单。短期身份和原图输出放在仓库外，不重放业务链，范围与截图限制见[采购教程第 10 节](../../docs/guides/procurement-flow.md#10-复查物料规则与概览)。
- `capture-procurement-quotation.mjs`：相同隔离保护下只读核对已定标询价的报价版本、两家供应商各自记录及定标后比价业务码 409。真实打开/关闭详情，使用产品主题按钮，禁止提交报价或重新定标。可用 `E2E_PROCUREMENT_CAPTURE_FILTER=语言:actor,...` 只补拍指定 `manager/supplierC/supplierD` 场景；每次使用全新的私有回执和图目录。配图只证明可见列表金额/终态，行价格由接口与控件断言验证，见[采购教程第 11 节](../../docs/guides/procurement-flow.md#11-定标后核对两家供应商的记录)。
- `capture-srm-teaching.mjs`：Docker 内 SRM 入驻、三节点准入、生命周期详情、未受邀读取边界与样例淘汰入口。必须显式选择阶段，核对固定隔离项目、24 个无宿主端口容器的运行时快照，使用仓库外短期身份和原子操作登记；成功阶段禁止重放，未知提交先核对。注册图不证明验证码自注册，API 审批不冒充全程 UI 点击；正式历史不可强删。步骤、图片与边界见 [SRM 教程](../../docs/guides/srm-flow.md)。
- `capture-srm-native-actions.mjs`：相同隔离保护下创建新的内部供应商，通过原生按钮依次冻结、解冻、加入及移出黑名单、淘汰。显式阶段为 `prepare / suspend / resume / blacklist / restore / eliminate`，三次准入决策使用普通候选人 API；中文实际提交一次，四语预览和结果图不重复写入。原子登记保留状态与版本，成功阶段拒绝重放，字节一致结果图可复用。仅证明桌面原生创建和生命周期操作，不证明 Portal 验证码自注册，见[教程第 9 节](../../docs/guides/srm-flow.md#9-通过原生按钮创建并操作独立供应商)。
- `capture-asset-usage.mjs`：Docker 内资产手工练习卡的准备、真实界面分配/接受/归还、只读补拍与历史保护验证。显式阶段、固定隔离项目、运行时白名单、仓库外短期身份及原子操作登记均不可省略；成功提交不重放。采购来源终态卡只读，练习卡不得冒充收货建卡；历史删除冲突时保留记录，见[资产教程](../../docs/guides/asset-flow.md)。
- `omni-frontend/e2e-docs/flows/procurement-scope.flows.spec.ts`：四语只读采购经理范围用例，要求可见部门的真实单据与另一部门创建人可读的专用草稿。业务码 404 与 HTTP 403 分别验证，禁止使用已删除资源或 SUPER_ADMIN 冒充范围隔离。草稿准备和清理由正式接口在隔离项目完成，截图必须先于删除，见[权限教程](../../docs/guides/permissions.md)。
- `compose.teaching.yaml`：与正式 `compose.yaml` 和 `--profile full` 合用的教学覆盖层，清空宿主端口并禁用自动重启；使用唯一 `omni-e2e-*` 项目、仓库外私密 env 和独立数据卷。不是标准开发启动入口。
- `run-mq-teaching-fixture.ps1`：仅接受本机 Docker 中独立、健康且无宿主端口的教学 MySQL。Prepare/Release 准备路由失败，Repair 只修复路由，Replay 保留原 eventId，Cleanup 仅清理回执内终态合成行；重发/忽略使用正式页面。SQL、资源摘要和状态回执放在仓库外，写入需 `E2E_MUTATIONS=true`。步骤见 [可靠消息教程](../../docs/wiki/outbox-development.md)。
- `run-mq-teaching-browser.mjs`：Docker 内的真实页面阶段入口，读取仓库外短期 token JSON；要求新阶段登记，不覆盖已有结果。只有 `--list` 可选，不能用参数绕过断言；调用前准备当前运行时白名单快照和浏览器依赖卷。
- `run-backup-rehearsal.ps1`：PowerShell 7.4+ 的 Docker 包装器，只读备份已核实的独立教学 MySQL，在新内网、新卷恢复九库并重放正式迁移器。比较八库稳定数据摘要、九库表清单与迁移历史；动态 XXL 记录仍备份，但不纳入跨时间摘要。可用 `-MigratorImage` 指定已构建的本机镜像。成功只清理本次恢复资源；失败保留副作用供核对，禁止直接重跑。SQL、私密 env 和日志保存在仓库外。应用、会话与 MQ 恢复另行验收，见[运维指南](../../docs/guides/operations-upgrade.md)。

- `capture-wiki-home.mjs`：只读采集同一 `omni-stack` full 环境前端的四语公开 Wiki 首页，无账号、令牌或验证码前置。仅接受无 Docker socket 的容器页面验证角色、标准前端地址和固定 `/validation/evidence/` 输出；校验十二章正文、语言与匿名状态，原图及回执不覆盖。回执只证明实际采集，逐张 AI 图审另留证据。
- `capture-unified-support.mjs`：在同一环境使用已登记的专用测试身份，只读采集操作日志和 MQ 记录的四语页面；保留原图、真实接口结果与身份来源。已有发送记录不证明本次重发、消费或集中日志系统运行，图片另行实际 AI 审读。

所有验证工具、服务和应用在本机 Docker 内运行，输出写到仓库外的受限目录。使用 fixture 前先核实目标容器、数据库、租户和业务键；不得仅根据旧环境名称复用运行数据。

测试身份入口要求全部九个 `E2E_FIXTURE_*` 连接/输出变量与显式 `E2E_FIXTURE_DOCKER_NETWORK`。DB URL 和 Redis host 使用该网络内的容器服务名；不依赖宿主 JDK 路径。调用方先建立受限输出目录并注入环境，再运行 `./scripts/fixtures/run-token-fixture.ps1`。脚本只显示缺失变量名和结果路径，不显示秘密。构建 fixture 时跳过测试不是正式后端验收通过；完整后端修改仍执行 `clean install`。

短期令牌仅用于经过授权的测试环境，验证结束删除输出文件。真实首次登录仍由人输入一次性验证码；测试令牌不能证明密码登录或 CAPTCHA 验证已经通过。
<!-- omni:modules:workflow -->
`run-workflow-scope-fixture.ps1` 解决独立教学环境的角色组织绑定前提：用户角色 API 不维护 `sys_user_role_scope`，不能仅凭已分配角色断言候选人可用。入口只接受 `omni-e2e-*` / `omni-wiki-acceptance-*`、无宿主端口且使用本项目网络和数据卷的 MySQL，以及明确的 `wiki{run}_name` 账号；写入还需要 `E2E_MUTATIONS=true`。它仅为同租户已有启用角色与主组织插入缺失 `SAME_UNIT` 绑定，不创建用户或授予角色，不覆盖已有冲突。使用新的仓库外输出目录记录 SQL 摘要和前后回执；`-VerifyOnly` 只读核对。完整准备与调用见 [教学数据](../../docs/wiki/data-and-fixtures.md)。候选资格与 `workflow:approval:complete` 功能权限须分别验证。
<!-- omni:modules:end -->
