# S0-07 租户模块化初始化实施记录

> 开始日期：2026-08-20
> 当前状态：Auth runtime foundation complete；跨服务消费者与运行时切换尚未完成

## 本检查点已完成

- 新增 `scaffold/catalog/modules.yaml`，以拓扑顺序声明 10 个模块、依赖、租户初始化模式、权限根和 provisioning seed ID。
- 增加机器契约，强制模块 ID 与 `database/seed/manifest.yaml` 一致，并确保每个 seed ID 只归属一个模块、依赖只能指向已声明模块。
- 在 `omni-common-core` 增加 `tenant.provision-requested.v1` 与 `tenant.provision-result.v1` 强类型事件。
- 请求事件字段经过测试，禁止出现密码、密码哈希、Token、联系人或电话；模块集合发布后不可变。
- Auth 新增 `provisioning_status/request_id/error` 兼容字段和 `sys_tenant_module_provision` 模块状态表。
- 既有租户迁移后默认映射为 `ACTIVE`；旧 `status=0/1` 继续只表达业务启停，不与初始化状态混用。
- 新结构使用独立 `adoption-upgrade` changeSet，不修改已冻结的 Auth baseline。
- 已在一次性 MySQL 8.4 空实例执行九库 fresh 迁移；Auth 新 changeSet 为 `EXECUTED/adoption-upgrade`，三个字段和状态表均符合声明。
- 对同一空实例立即重复执行迁移，平台与九个目标全部 `Run: 0`；测试容器及匿名卷随后已删除。
- Auth 启动时严格加载 `modules.yaml` 和种子清单；未知字段、重复键、前向依赖、seed 缺失或模块归属不一致均失败关闭。
- 默认角色自然键由各模块 provisioning seed 解析，不再由 Java 或存储过程维护第二份角色清单。
- Auth 本地初始化器按模块权限根和角色自然键克隆默认租户模板，幂等创建根组织、管理员和 XSS；重放不覆盖租户自定义名称、排序或管理员密码。
- Auth 初始化协调器已实现模块状态、确定性请求事件、失败脱敏、租户行锁汇总和失败模块重试；请求事件契约和序列化对象均不含管理员凭据。
- Auth 相关模块测试共 38 项通过，其中新增目录、seed、本地初始化首次/重放、Outbox 和失败汇总测试 9 项。

## 当前仍保留的兼容路径

`TenantServiceImpl` 目前仍调用 `TenantProvisionMapper -> sp_init_tenant`。这是刻意保留的切换门，不代表 S0-07 已完成。以下运行时能力闭合前不得删除存储过程，也不得把新租户创建路径标记为模块化初始化完成：

1. Base、Workflow、CRM、SRM、Procurement、Asset 按 `tenantId + moduleId` 幂等消费并写结果 Outbox。
2. Auth 增加结果消费者，把已实现的 `requestId + moduleId` 汇总逻辑接入消息 binding。
3. 跨服务消费者和失败恢复集成测试通过后，`TenantServiceImpl` 才从存储过程切到新协调器。
4. 登录租户列表和实际认证都拒绝 `PROVISIONING/FAILED` 租户。
5. 管理 UI 展示总体状态、失败模块、脱敏错误和重试入口。

## 安全与兼容约束

- 管理员明文密码只进入 Auth 的 `PasswordEncoder`，BCrypt 哈希也不得进入跨服务事件。
- 结果错误信息必须脱敏并限制长度，不能复制堆栈、SQL、消息正文或连接信息。
- `requestId` 用于一次初始化尝试的跨服务关联；模块事实唯一键仍为 `tenantId + moduleId`，相同请求重放必须幂等。
- 现有 `status=1` 租户继续视为已完成初始化；新增字段先兼容旧应用，再切换新创建路径。
- `sp_init_tenant` 只在 Phase 5 删除门通过后清理，不能在事件消费者和失败恢复尚未验证时提前删除。
