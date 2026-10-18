# 正式种子、教学数据与测试夹具

适用基线：主干 `fc165e3`。先完成[首次启动](../guides/quick-start.md)，确认目标 Compose project、租户和业务记录。本页不授权重置已有数据。

## 1. 先辨认数据来源

| 来源 | 职责 | 何时使用 |
|---|---|---|
| `database/changelog/` | 不可变结构和必要数据迁移 | fresh 与升级，由正式 migrator 执行 |
| `scripts/sql/seed/` | 幂等基础种子 | migrator 引用，不手工再执行一次 |
| `database/seed/manifest.yaml` | 种子摘要、自然键和验收断言 | 核对正式基础数据，不要求所有用户数据为空 |
| `database/seed/history/` | 冻结的历史种子版本 | 只服务已存在 changeSet 的重放，不能随便编辑 |
| `scripts/fixtures/`、测试 classpath | 有界身份与测试数据 | 已确认的本地验证环境；不是生产初始化 |
| 操作教程建立的记录 | 真实业务演示 | 通过界面或正式 API 创建，用本次返回的 ID |

例如管理员、开发租户、菜单和审批模型属于基础能力；已批准请购、报价、采购订单和资产卡片属于业务记录。初始种子不保证存在历史图中的 `PR-1-1` 或 `RFQ-1-1`。

## 2. fresh、重放和接管

fresh 指独立的空数据库环境。Compose 的 migrator 成功退出后，才启动依赖它的应用。再次启动是升级重放，不能删除 volume 来使问题消失。

接管旧数据要使用 `database/adoption/` 的冻结基线与真实备份证明。它与 fresh 不同，不能未经核对把已有数据库标成已迁移。失败时保留日志和版本，采用前向修复；已执行 changeSet 不得改写。

基础断言按正式种子的自然键取样，不把用户新增数据纳入固定行数。新增权限或基础数据时更新正式源、SHA-256 与对应断言，不能修改预期值掩盖缺项。

## 3. 教学记录怎么建立

1. 选择所需预设：采购和资产完整链路需要 supply-chain；CRM 需要 crm。
2. 记录当前租户、管理员、申请人、审批人和供应商。供应商必须有 SUPPLIER 角色和有效门户关联，不能只凭用户名假定有权限。
3. 使用 `WIKI-<本次唯一标识>` 作为演示名称前缀。不要使用真实手机、邮箱、银行或企业信息。
4. 按 [SRM](../guides/srm-flow.md)、[采购](../guides/procurement-flow.md)、[资产](../guides/asset-flow.md) 的顺序，通过正式操作建立前置数据，记录每一步返回的 ID、状态和版本。
5. 同一请求的重试复用 requestId；新的一次业务操作使用新 requestId。不要重新创建整条链来掩盖未返回的操作结果。
6. 先检查不可见、拒绝、取消和重复请求，再确认主链成功。清理只针对本次业务键，按状态允许的撤回、关闭或删除操作执行。

已有业务链不能物理删除时，保留取消/终态记录并记清范围。演示脚本不是万能 reset；不覆盖共享租户数据，不批量删除数据库或 volume。

<!-- omni:modules:workflow -->
### 教学审批人的组织作用域

给用户分配角色和主组织，还不足以让该用户成为审批候选人。当前用户角色 API 维护 `sys_user_role`，候选人解析另外查询 `sys_user_role_scope`；现有管理页面没有该作用域绑定的维护入口。先用候选人预览核对，不能把“角色已选中”当成审批可用。

仅在独立、无宿主 MySQL 端口映射的教学项目中，可以使用[组织作用域夹具](../../scripts/fixtures/run-workflow-scope-fixture.ps1)为明确的 `wiki{run}_name` 账号补齐已有角色与主组织之间的 `SAME_UNIT` 绑定。它不创建用户、不授予角色、不覆盖冲突，也不支持标准 `omni-stack`。先准备已启用的用户、角色和主组织，再运行宿主 PowerShell 包装器；数据库客户端和 SQL 均在目标 Docker 容器执行：

```powershell
$env:E2E_MUTATIONS = 'true'
$evidence = Join-Path ([IO.Path]::GetTempPath()) ('omni-scope-' + [guid]::NewGuid())
./scripts/fixtures/run-workflow-scope-fixture.ps1 -ProjectName 'omni-wiki-acceptance-yourrun' -Usernames 'wikiyourrun_manager','wikiyourrun_compliance' -OutputDirectory $evidence
```

把项目名与账号替换为本次实际值。回执记录 SQL 摘要、写入前后绑定及容器身份；再次核查用新的输出目录和 `-VerifyOnly`。出现冲突时保留回执并修正教学配置，不能重置数据库。

<!-- omni:modules:end -->

<!-- omni:modules:srm -->
候选资格与完成审批的接口权限是两项条件。当前 SRM 三种审批角色的基础种子不授予 `workflow:approval:complete`。为教学审批人通过角色管理新增最小审批角色，包含 `workflow`、`workflow:instance`、`workflow:instance:list`、`workflow:task:todo`、`workflow:approval:complete`，同时保留其原业务角色。更新后重新登录或重新签发受控短时令牌，再用该身份完成自己的候选任务。不要用超级管理员代签来掩盖权限缺项。
<!-- omni:modules:end -->

## 4. 受控身份和验证码

真实登录使用服务产生的一次性验证码，人阅读后输入。成功或失败的校验都会原子消费 captchaKey，重试必须刷新图片。

登录后 E2E 可使用 `omni-auth/src/test/java/.../E2eTokenFixture.java` 的短期测试身份。它从已确认的数据库读取角色并使用当前 Redis 密钥，不启动生产登录后门，不读取用户密码，也不破解验证码。包装入口是 `scripts/fixtures/run-token-fixture.ps1`，输出必须放在仓库外，使用后清理。

历史隔离 fixture 的租户、UUID 和资源保护条件必须保留。缺少授权输入时应失败关闭；不能替换成标准栈并声称隔离测试成功。具体规则见 [夹具说明](../../scripts/fixtures/README.md)。

## 5. 如何判断结果

| 现象 | 判断与下一步 |
|---|---|
| 目录、权限或默认模型缺失 | 检查 migrator 与种子断言，不直接补业务表状态 |
| 概览显示空数据 | 空业务环境的正常结果；按教程建立记录 |
| 收货后无卡片 | 检查 PASS、assetManaged、精确正整数数量及 MQ/Inbox |
| 当前用户看不到演示记录 | 检查租户、角色、owner/current user 数据范围 |
| 重跑出现重复对象 | 核对 requestId、事件 ID、来源单位序号和上次副作用 |
| 截图数据与本次不同 | 使用本次 ID 和状态；旧图不是当前证据 |

备份与恢复见 [运维教程](../guides/operations-upgrade.md)。提交文档只保留脱敏结论和必要图片，原始令牌、认证状态、数据库导出和日志不进仓库。
