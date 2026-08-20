# S0-03 数据库与脚本迁移台账

> 基线：`09a29fe10af9c7ddffe5001238d048947868dc98`
> 盘点日期：2026-08-20
> 状态：S0-03 Complete；具体 changeSet 的逐列转换和 seed manifest 归属继续由 S0-05 维护。
> 原则：当前阶段只盘点，不删除、不改写旧 SQL。

## 1. 运行态数据库

| 数据库 | 所有者 | 基线表数 | Liquibase 目标 |
|---|---|---:|---|
| `omni_auth` | Auth | 20 | 独立 changelog；接管租户、RBAC、OAuth2、XSS 与登录审计 |
| `omni_base` | Base | 8 | 独立 changelog；接管字典、任务、操作日志 |
| `omni_workflow` | Workflow + Flowable | 54 | 业务扩展与固定 Flowable vendor schema 分离 |
| `omni_crm` | CRM | 12 | 独立 changelog |
| `omni_srm` | SRM | 21 | 独立 changelog |
| `omni_procurement` | Procurement | 15 | 独立 changelog |
| `omni_asset` | Asset | 6 | 独立 changelog |
| `nacos_config` | Nacos vendor | 10 | 固定 Nacos 3.1.1 vendor changelog |
| `xxl_job` | XXL-JOB vendor | 8 | 固定 XXL-JOB 3.3.1 vendor changelog |

`omni_auth.sp_init_tenant` 是唯一发现的业务存储过程。它同时存在于 `init-all.sql` 和独立 `sp_init_tenant.sql` 中，并由 `TenantProvisionMapper` 直接调用；S0-07 完成 Java/Outbox 模块化初始化前不能删除。

## 2. 25 个 SQL 文件逐项处置

### 2.1 `scripts/sql`：16 个

| 文件 | 当前职责 | 当前调用或引用 | 目标替代物 | 最早删除门 |
|---|---|---|---|---|
| `_check_wf.sql` | Workflow 临时诊断查询 | 无正式运行入口 | 自动化 Workflow 一致性测试/诊断命令 | WP-10，替代测试通过后 |
| `crm-sample-data.sql` | CRM 演示数据 | 人工执行、设计文档 | `database/seed` CRM demo profile | WP-10 |
| `init-all.sql` | 7 个业务库的聚合 DDL、权限/菜单/字典/业务种子、租户过程 | Compose 首次空卷、README、架构与部署文档 | platform + 7 业务 changelog、MQ changeSet、seed manifest | G1 fresh/upgrade/adopt 通过后，WP-10 删除 |
| `init-nacos.sql` | Nacos 3.1.1 vendor schema/seed | Compose 首次空卷 | 固定版本 `nacos_config` vendor changelog | G1 |
| `init-tenant-a.sql` | 演示租户数据 | 人工执行、CRM 设计文档 | preset-aware tenant provisioning fixture | S0-07 与 demo seed 通过后 |
| `init-xxl-job.sql` | XXL-JOB 3.3.1 vendor schema/seed | Compose 首次空卷、调度文档 | 固定版本 `xxl_job` vendor changelog | G1 |
| `migrate-asset-mvp.sql` | 既有环境 Asset 幂等迁移 | README、部署/Asset 设计文档 | `omni_asset` 顺序化 changeSet | adopt/upgrade 验证后 |
| `migrate-crm-mvp.sql` | 既有环境 CRM 幂等迁移 | README、部署/CRM 设计文档 | `omni_crm` 顺序化 changeSet | adopt/upgrade 验证后 |
| `migrate-procurement-mvp.sql` | 既有环境 Procurement 幂等迁移 | README、部署/Procurement 设计文档 | `omni_procurement` 顺序化 changeSet | adopt/upgrade 验证后 |
| `migrate-srm-mvp.sql` | 既有环境 SRM/Auth/MQ 幂等迁移 | README、部署/SRM 设计文档 | `omni_srm`、Auth 兼容、MQ changeSet | adopt/upgrade 验证后 |
| `migrate-supplier-workflow.sql` | 供应商 Workflow 兼容迁移 | 历史人工升级 | Workflow 业务扩展 changeSet + seed | adopt/upgrade 验证后 |
| `migrate-workflow-process-start-idempotency.sql` | 流程启动幂等结构升级 | README、部署文档 | Workflow 业务扩展 changeSet | adopt/upgrade 验证后 |
| `migration-mqmessage.sql` | 多服务 `sys_mq_message` 结构与兼容升级 | 历史人工升级 | 公共 MQ changeSet，按目标库应用 | 所有服务 upgrade 验证后 |
| `procurement-sample-data.sql` | 采购全流程演示数据 | 人工执行 | `database/seed` procurement demo profile | WP-10 |
| `seed-test-data.sql` | 测试用户/角色/数据 | E2E/人工测试准备 | 隔离 test fixture；严禁进入生产 seed | G0/G1 夹具通过后 |
| `sp_init_tenant.sql` | 可重入租户初始化存储过程 | README、设计文档、迁移后人工重导 | Java provisioning + manifest + Outbox | S0-07 稳定且 Phase 5 删除门通过后 |

### 2.2 `scripts` 根目录：4 个

| 文件 | 当前职责 | 风险 | 目标替代物 |
|---|---|---|---|
| `cleanup-inst4.sql` | 特定流程实例数据清理 | 一次性、可能破坏数据 | 测试 fixture 隔离与可回收测试数据 API；无生产替代 |
| `fix-approval-comments.sql` | 审批意见历史修复 | 一次性修复不可追踪 | 带 precondition 的兼容 changeSet 或数据修复任务 |
| `fix-comment-charset.sql` | 字符集修复 | 直接 ALTER，重复执行语义不清 | 带字符集指纹 precondition 的 changeSet |
| `fix-fullmsg.sql` | 历史消息内容修复 | 一次性且缺少正式入口 | 必要时转为审计明确的数据修复 changeSet，否则留证后删除 |

### 2.3 后端资源：5 个

| 文件 | 当前职责 | 事实/风险 | 目标替代物 |
|---|---|---|---|
| `omni-auth/.../db/init-data.sql` | 旧 Auth 聚合初始化参考 | 内容仍含 DDL/seed，但注释称权威源已迁移 | Auth changelog + seed manifest；WP-10 删除 |
| `omni-auth/.../db/migration/V1__init.sql` | 旧 Flyway 风格 Auth 结构 | 仓库未统一启用 Flyway，可能与 `init-all` 漂移 | Auth Liquibase baseline |
| `omni-auth/.../db/migration/V2__add_xss_tables.sql` | XSS 表增量 | 同上 | Auth XSS changeSet |
| `omni-base/.../db/migration/V1__init.sql` | Base 旧 Flyway 风格结构 | 同上 | Base Liquibase baseline |
| `omni-common-mqlog/.../schema.sql` | 服务启动自动创建 Outbox 表 | 多服务自行建表，绕过统一版本门 | 公共 MQ changeSet；starter 改为校验而非建表 |

## 3. 结构、种子与过程分类

| 类型 | 当前事实 | 目标事实源 |
|---|---|---|
| 数据库、表、列、索引、约束、字符集 | 分散在 `init-all`、迁移、资源 schema 和一次性修复中 | `database/changelog/**` Liquibase YAML/XML/vendor SQL 引用 |
| 菜单、权限、角色、字典、默认配置 | 与 DDL 混在 `init-all`、租户过程和样例脚本中 | `database/seed/manifest.yaml` + 模块声明；生成到 `scripts/sql/seed` |
| 测试/演示数据 | 3 个大型 SQL 和临时脚本 | 隔离的 test/demo profile，不进入生产默认 seed |
| 租户初始化 | `sp_init_tenant` 单体过程 | Auth 编排 + Outbox + 各模块幂等 handler |
| vendor schema | Nacos、XXL-JOB、Flowable 来源分散 | 锁定版本、校验摘要的 vendor changelog |
| 一次性数据修复 | 4 个根 SQL 及若干迁移片段 | 带 precondition、审计 ID 和明确不可重复语义的 changeSet/运维任务 |

### 3.1 当前种子写入面

`init-all.sql` 同时向 Auth 的租户/组织/RBAC/XSS、Base 的字典和用户任务、Workflow 模型、CRM、SRM、Procurement 等多域表写种子；`sp_init_tenant.sql` 又复制 Auth/Base/SRM/Procurement 的部分写入逻辑。Nacos、XXL-JOB、演示租户、CRM 示例、采购示例和 E2E 测试数据各有独立脚本。这证明当前没有可机器验证的单一 seed manifest。

运行态关键自然键重复检查结果均为 0：

- Auth：`tenant_id + permission_code`、`tenant_id + role_code`、`tenant_id + username`；
- Base：`tenant_id + type_code`；
- CRM：`tenant_id` 配置；
- SRM：活动模板的 `tenant_id + name`（当前表没有稳定 `template_code`，是待改造缺口）；
- Procurement：活动品类 `tenant_id + category_code`、活动审批规则 `tenant_id + route_code`、租户配置 `tenant_id`。

当前只有一个 `sys_tenant.status=1` 租户。S0-07 引入 `PROVISIONING/ACTIVE/FAILED` 前需要兼容映射，不能直接把整数状态改成字符串导致旧应用失效。

## 4. scripts 根目录 31 文件引用与处置

当前 31 个文件中，仓库正式文档/Compose 没有直接引用根目录下的 PowerShell、Python、JSON、BIN、TXT 和修复 SQL；这意味着它们目前主要是人工调试遗留物，而不是受维护的产品入口。`scripts/sql`、`scripts/bpmn` 等子目录另行盘点。

| 分组 | 文件 | 初步结论 |
|---|---|---|
| HTTP/实例人工操作 | `approve-all.ps1`、`curl-api.ps1`、`curl-test.ps1`、`deploy-and-start.ps1`、`start-inst3.ps1`、`start-instances.ps1`、`start-only.ps1` | 逐个转为正式 npm/CLI/测试入口；不得保留硬编码凭据或 Token |
| 特定实例修复/核验 | `cleanup-inst4.ps1`、`fix-bpmn.ps1`、`fix-inst4.ps1`、`verify-instances.ps1`、`verify-retry-fix.ps1`、`verify-v2.ps1`、`verify-v3.ps1` | 属于一次性问题脚本；保存缺陷证据后由回归测试替代 |
| Python 临时检查 | `check-all.py`、`check-hex.py`、`check-inst3.py`、`check-raw.py`、`test-api.py`、`verify-fullmsg.py` | 提取仍有效断言到测试套件，其余删除 |
| SQL 临时修复 | `cleanup-inst4.sql`、`fix-approval-comments.sql`、`fix-comment-charset.sql`、`fix-fullmsg.sql` | 按 2.2 迁移，不允许作为最终 SQL 保留 |
| 临时响应/二进制/文本 | `api-output.json`、`api-raw-bytes.bin`、`api-raw.json`、`api-result.json`、`temp_procurement_bpmn.txt` | 中间产物；确认无独占证据后 WP-10 删除 |
| 其他临时工具 | `raw-json.ps1`、`verify-comments.ps1` | 将编码/注释断言迁入自动化测试后删除 |

安全发现：`start-instances.ps1` 含一个硬编码的历史过期 JWT。台账不复制其内容。该脚本不得再用于正式验证，WP-10 前必须移除硬编码并增加 Secret 扫描。

## 5. S0-04 输入与未决验证

S0-04 可以开始搭建 migrator 骨架，但以下事项在 baseline changeSet 定稿前必须闭合：

1. 已对 136 张业务表导出不含数据的列、索引、唯一约束、外键、字符集和默认值指纹；结果见 `schema-snapshot.md`。S0-06 前需把同一算法实现到 migrator。
2. 将 `init-all.sql` 的 91 个 `CREATE TABLE` 与运行态 136 张业务表逐库对齐；Flowable vendor 表单独核对。
3. 已确认仓库代码、应用 YAML、容器环境变量名称和当前 Nacos `config_info` 均没有显式 SQL/Flyway/Liquibase 初始化配置。4 个 `db/**` 文件没有发现运行消费者；`omni-common-mqlog/schema.sql` 虽会进入 starter classpath，但 MySQL 环境没有 `spring.sql.init.mode=always` 证据，不能把“自动建表”视为可靠事实。S0-04 统一接管后同步修正文档。
4. 识别所有稳定 seed 自然键，并记录重复权限码、跨租户数据和 ID 假设。
5. 为旧数据卷建立只读备份/恢复演练，不在原卷上直接执行 adopt。
6. E2E test fixture 替代 `seed-test-data.sql` 和历史硬编码令牌后，再冻结测试种子边界。

## 6. S0-03 结论

- 25 个 SQL 已逐文件分类并指定替代路径；31 个 scripts 根文件已分组，未发现正式运行入口引用。
- 七个业务库和两个基础设施库的列/索引/约束摘要与 SHA-256 指纹已冻结。
- 关键 seed 自然键在当前运行态无重复；SRM 模板缺少稳定 code 是 S0-05 必须解决的结构缺口。
- 九库同版本流式备份与隔离恢复成功，表数和存储过程数量一致。
- 不存在无法识别职责或调用方的 SQL；可以进入 S0-04/S0-05，但旧文件在替代物通过前仍不得删除。
