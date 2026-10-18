# 项目预设、容器开发与服务扩展

适用范围：2026-10-17 核对的 CLI 0.6.1、服务模板 1.5.1、CRUD 模板 1.1.1、预设交付模板 1.1.0。操作者是本地开发者，无需业务登录。generated 是独立学习输出，不是正式种子。先按[首次启动](quick-start.md)准备 Docker 工具镜像、依赖与工作目录映射；服务、构建、测试和浏览器均在本机 Docker 中运行。不要在有个人未提交改动的正式工作区试验接入。

## 1. 选择与生成预设

预设是模块选择，catalog 计算依赖闭包。`preset list` 应列出 core、workflow、crm、supply-chain、full；`explain crm` 展示 explicit 与 resolved。资源预算和模块差异只以 [依赖矩阵](../preset-dependency-matrix.md) 为准。

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset list
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset explain crm
```

“启动部分服务”仍使用原仓库；“生成裁剪项目”复制所选源码、迁移、种子、四语 Wiki 和适用 AI 资产到新目录。先 dry-run，再移除该参数创建；这里没有 --apply。输出必须不存在或为空，不能覆盖别人的目录。

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create crm --output generated/wiki-crm --dry-run
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create crm --output generated/wiki-crm
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset validate crm --output generated/wiki-crm
```

预期看到 `preset created: crm@...`，校验输出 `generated preset valid`。scaffold.lock 的 sourceDigest、scaffoldDigest 分别绑定源工程和生成工程的 scaffold/ 目录；catalog、预设、文档和 AI 输入也有对应摘要/资源校验。它不对全部应用源码作完整性签名，也不证明编译或业务验收。进入新目录后使用它自己的 compose.tools.yaml、compose.docs.yaml 和 ai/adapters/bootstrap.mjs，设置新项目仓库地址，按 quick-start 生成私有 .env；不依赖原仓库绝对路径。

## 2. 启动子集与轻量模式

`dev` 提供 up、down、status、doctor、logs；没有 dev plan。`dev up` 在 Docker 调用前打印 modules、services、mode 与降级说明。core/workflow/crm 默认 lite，关闭 Nacos 客户端、XXL-JOB、MQ relay 与自动探测，配置仍允许业务事务写 Outbox；这不是实际插入或投递证据。含 SRM/Procurement/Asset 的不安全消费者闭包在未指定模式时选择 full，显式 lite 则被拒绝；full 保持 .env 权威。

以下命令针对标准 omni-stack 项目，使用固定端口，包括 MySQL 3306。已有栈时先核对端口、project、卷与归属。隔离验证使用独立拥有的卷和内部网络，不漂移业务端口；down 默认保留卷。CLI 工具镜像需要 Node.js、Docker CLI 与 JDK 25，Compose 同时提供 Docker socket 和工程目录映射。doctor 退出码 0 仍可能包含 SKIP；Linux 工具容器的宿主端口探测可跳过，应另查 Docker 已发布端口。其 Nacos 探针检查服务端 API/服务器列表，不证明每个业务服务已注册。

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- dev doctor
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- dev up --preset crm --project-name omni-stack
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- dev status --project-name omni-stack
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- dev logs --module crm --tail 200 --project-name omni-stack
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- dev down --project-name omni-stack
```

## 3. 生成服务包并接入

`create-service` 默认只预览。即使 --apply，也只是生成带 omni-service.lock.json 的服务包，不会接入 monorepo。必须再运行 service validate 和 service integrate；后者也默认只读，第二个 --apply 才写入 Maven、catalog、Gateway、Compose、权限、菜单与迁移入口。下面先生成独立 core 学习项目，再在该项目中操作，防止污染原仓库。

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create core --output generated/learning-core
cd generated/learning-core
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- create-service inventory --output generated/inventory-bundle
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- create-service inventory --output generated/inventory-bundle --apply
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- service validate inventory --output generated/inventory-bundle
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- service integrate inventory --source generated/inventory-bundle
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- service integrate inventory --source generated/inventory-bundle --apply
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install -pl omni-inventory,omni-db-migrator -am
```

接入计划应无 CONFLICT，结果显示 integration applied。端口冲突、包摘要变化或有 Git 的目标中存在未提交改动时，先处理具体冲突，不关闭保护或强制覆盖。集成完成后才存在 omni-inventory Maven 模块。新服务使用 Servlet 公共 starter；核对公共配置提供的 XssConfigProvider SPI 在实际 classpath/开关下启用，按需扩展，不复制 reactive Gateway 链。骨架仍需领域校验、DataScope 表映射与负向测试。

当前计划和渲染器均覆盖 zh-CN、en-US、ja-JP、ko-KR 前端 locale；目标存在 .git 时，四个 locale 文件都纳入写前脏文件检查。没有 .git 的学习工程不获得该 Git 保护；需要时先建立并提交清洁基线。注册四语入口不等于业务术语已译好。新文档和译文保持 present-unverified，实际语义复核通过后才可按现行授权记录 AI_REVIEW，无需人工复核，也不能由生成器自动批准。

<!-- omni:modules:procurement -->
## 4. 生成 CRUD

当前 CRUD 规格只支持 procurement。在包含该模块的独立学习项目使用 scaffold/specs/material-brand.yaml。实际命令是 generate crud --spec；--check 只校验规格，--dry-run 渲染计划，--apply 进行跨文件原子写入，dry-run 与 apply 互斥。先审查 module、permission、DataScope 和字段，避免与既有表冲突。

下面的 cd ../.. 接着第 3 节，从 generated/learning-core 返回原仓库根目录。若单独阅读本节，请先进入原仓库根目录，跳过这条 cd；其余命令从那里开始。

```bash
cd ../..
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create supply-chain --output generated/learning-supply-chain
cd generated/learning-supply-chain
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- generate crud --spec scaffold/specs/material-brand.yaml --check
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- generate crud --spec scaffold/specs/material-brand.yaml --dry-run
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- generate crud --spec scaffold/specs/material-brand.yaml --apply
```

输出只证明规格或生成事务，不能代替业务验证。生成后运行后端构建、前端 lint/build、权限负例和数据库 fresh/upgrade，更新四语说明。结构变更只追加 forward-only Liquibase changeSet；正式种子同步摘要和断言，不新增临时 migrate SQL。

CRUD 当前生成中文说明和 ja-JP/ko-KR 术语 YAML，不自动产出完整四语文档。运行时 locale 注册已覆盖四语，但实体/字段名称仍可能是源语言占位值；须实际翻译、检查页面和完成授权语义复核。AI_REVIEW 可满足现行文档审校要求，自动注册或锁文件校验本身不能赋予复核通过状态。

<!-- omni:modules:end -->

## 5. 增减模块与升级

停用：dev up 选择更小闭包，源码与升级路径保留。裁剪：preset create 交付新工程，并校验锁、导航、图片和 AI 规则。永久移除：先盘点反向依赖、Gateway/Nacos/Compose、Maven、菜单/路由、权限/种子、迁移升级、消息消费者与任务；把独有能力迁移后再删除。已执行迁移不能删除或改写，既有数据升级要有独立备份和回退方案。

新增或调整预设时，维护 scaffold/catalog/modules.yaml 与 scaffold/presets/*.yaml 的依赖闭包和资源归属，刷新派生矩阵及五预设验证。步骤见 [自定义预设](../custom-preset-tutorial.md)、[维护说明](../preset-maintenance.md) 与 [升级指南](../preset-upgrade-guide.md)。不要只删一个 Maven module 就宣称完整移除。

## 6. 验证和清理

完成条件：容器 JDK 25 Reactor 构建，前端 lint/build，必要 E2E 类型检查，权限与数据隔离负例，fresh/upgrade，所选预设和生成锁，四语内容及按[现行审校策略](../adr/0003-authorized-ai-translation-review.md)完成的实际语义复核。验证命令从学习项目根目录运行：

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run lint
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run build
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- dev doctor
```

命名卷保留，教学数据按本次唯一业务键清理；先列出 generated 的确切目录、确认没有新增个人内容，再删除本次拥有的输出。未知目录不覆盖，不用 git clean/reset --hard 或 Docker 全局 prune。原始日志和令牌放在仓库外。源码断点与失败恢复见 [源码调试](../wiki/source-debugging.md)。

## 7. 对照实际终端结果

2026-10-12 的 [验收记录](../reference/evidence/scaffold-2026-10-12.json) 保存输入源码摘要、工具与镜像身份、执行时间和验证边界。以下链接是本机 Docker 的真实终端文本，四语共用；私有输出路径使用占位符，不能直接把占位符复制到 shell。

[预设选择](../reference/evidence/scaffold-preset-select-2026-10-12.txt) 实际执行了 list、explain crm、core/supply-chain 生成与校验，并将 core 移动后再次校验。这证明这些步骤可复现，不代表五个预设的全部独立交付门禁已经通过。

[服务接入](../reference/evidence/scaffold-create-service-2026-10-12.txt) 中，基础包 inventory-basic 生成 32 个受锁文件、接入修改 38 个文件；带 MQ/Job/DataScope 的 inventory-sample 黄金构建是另一份工程，生成 33 个、接入 39 个文件。core 缺少 rocketmq 和 xxl-job，接入带这两项能力的包会在写入前被拒绝；应选择包含依赖的预设，保留此保护。

<!-- omni:modules:procurement -->
[CRUD 生成](../reference/evidence/scaffold-crud-generate-2026-10-12.txt) 显示实际 --check、--dry-run、--apply 和重复生成；重复执行得到 unchanged，锁定 18 个文件、首次事务涉及 29 个文件。另一份黄金 fixture 的后端 clean verify、前端 build 和 lint 通过，新增 lint 错误为 0；它不代替所生成 supply-chain 工程的完整业务验收。
<!-- omni:modules:end -->

[轻量运行](../reference/evidence/scaffold-lite-mode-2026-10-12.txt) 使用迁移目录后的 core 自身 CLI、14 模块后端和前端构建产物，启动无宿主端口的独立环境。六个运行容器就绪，一次性迁移器退出码为 0；Gateway → Auth 租户查询成功，匿名访问受保护 Base 接口返回 401。Nacos/XXL-JOB/MQ relay 与消费者关闭，但本次只读探针没有验证 Outbox 写入或消息投递。doctor 的宿主端口扫描和 Nacos 注册检查为 SKIP，不能写成通过。

该历史环境使用已核对产物制作的私有预构建镜像；此前正式多阶段 Docker 构建的网络限制仍是独立未通过项，--no-build 不能作为普通启动捷径。当时 down 保留命名卷，随后只删除本次拥有的卷；最终容器、业务卷和网络为 0，原教学环境 24 个容器身份未变。该日期的记录不表示当前仍有这些容器。

2026-10-17 的[当前 CLI 记录](../reference/evidence/scaffold-cli-2026-10-17.json)与[真实终端文本](../reference/evidence/scaffold-cli-2026-10-17.txt)补充 10 条成功命令：预设 list/explain/预览、合法服务包的预览/创建/校验、CRUD --check，以及 dev/dev up/service integrate 帮助。基础服务包为 32 个受锁文件、模板 1.5.1，仅创建一次并留在私有证据目录。起初数字连字符段派生的 Java 包名不合法，CLI 在写前拒绝；使用合法服务 ID 后只执行剩余步骤，未重跑前三条成功命令。

本轮使用现有已核对工具镜像与依赖缓存；未重新生成完整预设、执行服务接入、启动业务或重跑 Maven。11 项服务相关测试和 13 项 CRUD 测试、生成结果比较另有对应范围证据，完整当前 golden 与五预设结构矩阵仍须验收。旧记录中要求人工签核的条款仅作历史保留；现行[授权策略](../adr/0003-authorized-ai-translation-review.md)允许真实 AI_REVIEW，无需人工复核。该规则不把历史日志或自动生成状态当作新的文档批准。

<!-- omni:modules:crm -->
## 8. CRM 扩展与容器测试

本节面向开发者，无需业务登录；先在无个人改动的独立学习项目使用 crm/full 预设。阅读 [CRM 当前事实](../crm.md) 和第 3 节接入规则，再修改代码。下面是操作方法；本次文档整改没有重新执行这些业务/数据库步骤。

### 8.1 增加聚合、权限与事件

1. 只追加 `database/changelog/crm/` 下的 forward-only changeSet 并接入现有 master，不改已执行迁移。`CrmTenantEntity` 在 BaseEntity 上增加 tenantId；owner、version、deleted 是否存在由具体聚合表和实体决定，不能要求每张新表都有这些列。按实际 owner 映射、乐观锁和删除策略设计 schema/实体，并保持基础审计约定。
2. 增加独立请求/响应 DTO、Mapper、Service 接口与实现、Controller。在实际的 `CrmDataPermissionHandler` 注册 owner 映射；不要把 owner 条件加到不含这些列的表。
3. 正式权限写入 `scripts/sql/seed/auth.sql` 的 API 类型权限，并按角色分配。若需模块种子则同步 `crm.sql`；刷新 `database/seed/manifest.yaml` 的 SHA-256 和对应断言。控制器同时声明完整同码 `@PreAuthorize`、`@ServiceDataScope`，写操作补 `@OperLog`，界面按钮补 `v-permission`。
4. 事务内显式传入 tenantId 调用 `ReliableMessageRelay.send("crm-domain-out-0", envelope, tenantId, eventId)`；payload 不含完整 PII。消费者从信封顶层读取 `eventId`，结合消费者身份、租户和业务意图做原子去重与冲突检查，不能依赖 Broker msgKey 自动保证业务只执行一次。
5. 验证不同租户/负责人越权、缺身份/范围、旧版本、无效状态、回滚、重复消息及 PII 分支。预期外部命令不可见返回 404、版本/状态冲突返回 409；按实际接口记录 HTTP 与 R.code。

现有 Opportunity 管道不可通过管理 UI 编辑。新增阶段管理 API/UI 与既有引用迁移是后续设计事项；不能直接改阶段表后宣称扩展完成。

### 8.2 先跑普通构建与测试

从学习项目根目录运行，下列 backend 工具容器已提供 JDK 25/JAVA_HOME：

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install -pl omni-crm -am
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run lint
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run build
```

先按第 6 节安装依赖。检查每个模块 BUILD SUCCESS、测试 failures/errors/skipped 与前端退出码。缺少 CRM_TEST_MYSQL_URL 时四个 MySQL 用例跳过；成功构建不能替代数据库集成验收。已有源码与报告摘要匹配时复用报告，避免无变化重复构建。

### 8.3 需要 MySQL 拦截器验证时

`CrmMysqlInterceptorIntegrationTest` 会 DROP/CREATE `crm_lead`。只对本次新建、可销毁的空数据库执行；绝不能指向现有业务库。操作者在仓库外准备私有 mysql-it.env（MYSQL_DATABASE=crm_it 与强 MYSQL_ROOT_PASSWORD）和 crm-it.env（CRM_TEST_MYSQL_URL 指向 `jdbc:mysql://crm-it-mysql:3306/crm_it?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai`、CRM_TEST_MYSQL_USERNAME=root、同一 CRM_TEST_MYSQL_PASSWORD）。不把凭据放进 Git、截图或共享终端记录。

以下 PowerShell 示例使用本次唯一名字，工作目录为学习项目根目录。私有目录和卷名由操作者设置；命名冲突时先核对归属，不覆盖已有资源。

```powershell
$private = 'C:\Private\crm-it-yourrun'
$network = 'crm-it-yourrun'
$cache = 'crm-it-maven-yourrun'
$mysql = 'crm-it-mysql-yourrun'
docker network create $network
docker run -d --name $mysql --network $network --network-alias crm-it-mysql --env-file "$private\mysql-it.env" mysql:8.4
docker logs $mysql
```

日志确认 MySQL ready 后执行；失败先核对私有文件、容器网络和数据库名，不取消测试库限制：

```powershell
docker run --rm --network $network --env-file "$private\crm-it.env" --mount "type=bind,source=$PWD,target=/workspace" --mount "type=volume,source=$cache,target=/root/.m2" -w /workspace/omni-backend eclipse-temurin:25-jdk ./mvnw -pl omni-crm -am -Dtest=CrmMysqlInterceptorIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

成功条件是该测试报告 4 用例、0 失败、0 错误、0 跳过；其他模块没有匹配测试不算这四例通过。该测试只覆盖 tenant/owner/分页/缺 scope 的 MyBatis 路径，不覆盖登录、真实 Feign、业务转换并发或 Broker 投递。记录镜像身份、源码摘要、执行时间和完整测试报告，凭据脱敏。当前整改报告的四例仍是跳过，不能拿本教程当新执行结果。

确认容器、网络和缓存确由本次创建且无个人数据后，按确切名字清理。MySQL 镜像的 /var/lib/mysql 使用匿名卷，下例 -v 仅随本次可销毁测试容器删除其匿名卷；命名卷不会因此删除，Maven 缓存默认保留。禁止全局 prune：

```powershell
docker rm -f -v $mysql
docker network rm $network
```

失败时先保留脱敏日志和报告；修正原因后重试测试，不重放已经成功的业务创建。源码入口：[测试](../../omni-backend/omni-crm/src/test/java/com/omni/crm/integration/CrmMysqlInterceptorIntegrationTest.java)、[实体](../../omni-backend/omni-crm/src/main/java/com/omni/crm/entity/CrmTenantEntity.java)、[数据权限](../../omni-backend/omni-crm/src/main/java/com/omni/crm/security/CrmDataPermissionHandler.java)、[事件信封](../../omni-backend/omni-crm/src/main/java/com/omni/crm/dto/DomainEventEnvelope.java)。

<!-- omni:modules:end -->

<!-- omni:modules:srm -->
## 9. SRM 扩展与容器测试

本节面向独立 supply-chain/full 学习项目的开发者，无需业务登录。先读 [SRM 当前事实](../srm.md) 和本文的容器、服务接入步骤。

1. 在 database/changelog/srm/ 追加 forward-only changeSet 并接入 master，不改已执行迁移或遗留 init-all.sql。新直接 owner 聚合按 SrmOwnedEntity 建模并登记 OWNED_TABLES；子资源按 SrmTenantEntity 和父/孙关系登记，不能给没有 owner 列的表拼 owner 条件。
2. 增加 Entity、Mapper、独立请求/响应 DTO、Service 接口/实现、Controller。当前领域事实见 Supplier/Evaluation/Risk/Quotation 服务；新增资源不能绕过租户、行级授权、状态或版本检查。
3. 正式模块种子写 scripts/sql/seed/srm.sql，API 权限/角色分配写 scripts/sql/seed/auth.sql，刷新 database/seed/manifest.yaml 的 SHA-256 和断言。这里的 owner 端点是内部管理用户的业务 API，不是 Internal-Token 服务间协议；使用完整同码 @PreAuthorize/@ServiceDataScope，写操作加 @OperLog、界面加 v-permission。门户 profile/evaluation 需要 SUPPLIER、对应权限与 ACTIVE PortalUser 关联；USER 的 inviteToken/requestId 入驻流程另行处理，不能把管理 owner 当门户账号。
4. 业务事务内显式传 tenantId 调用 ReliableMessageRelay.send；从信封顶层 eventId 设计消费者去重，校验租户与业务意图，不能靠 Broker msgKey 自动只执行一次。payload 不含账户、联系信息或邀请令牌；报价金额使用十进制字符串。
5. 为越权/跨租户、缺身份/范围、旧版本、非法状态、重复事件/异意图、事务回滚和 PII 分支设计检查。按 HTTP 与 R.code 判断真实结果；不能仅看按钮隐藏、HTTP 200 或 Outbox 插入。

先按本文第 6 节安装依赖。从学习项目根目录运行，backend 工具容器提供 JDK 25/JAVA_HOME：

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install -pl omni-srm -am
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run lint
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run build
```

只在对应源码发生变化时重复后端或前端构建；摘要匹配的有效报告可以复用。检查 Reactor 模块结果、测试 failures/errors/skipped 和前端退出码，保留镜像身份、源码摘要、时刻和脱敏报告。

单元/配置/控制器测试报告只证明各自断言范围，不证明真实验证码、Workflow/MQ、数据库并发或教程步骤已运行。真实操作按 [SRM Wiki](srm-flow.md) 的账号、邀请、唯一前缀与取证边界执行；已有成功业务键先查状态，不能重复创建。需要数据库集成时用专门可销毁环境，不能把测试指向共享业务库。

预期异常时先重读详情和版本，核对角色、tenant/owner 或 ACTIVE PortalUser 关联，保留 trace/错误码。角色 Saga 重试与审批启动重试是不同能力，不能凭 COMPLETED 或失败字符串自动重发业务启动。

只清理本次拥有且无个人内容的学习输出，保留命名卷与必要缓存；不用全局 prune、git clean/reset --hard。迁移后旧数据的升级与回退须另有验证。源码入口：[范围策略](../../omni-backend/omni-srm/src/main/java/com/omni/srm/security/SrmDataPermissionHandler.java)、[状态机](../../omni-backend/omni-srm/src/main/java/com/omni/srm/domain/SrmStateMachine.java)、[报价](../../omni-backend/omni-srm/src/main/java/com/omni/srm/service/impl/QuotationServiceImpl.java)、[风险](../../omni-backend/omni-srm/src/main/java/com/omni/srm/service/impl/RiskServiceImpl.java)。
<!-- omni:modules:end -->
