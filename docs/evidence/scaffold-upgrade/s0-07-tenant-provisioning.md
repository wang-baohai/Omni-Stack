# S0-07 租户模块化初始化实施记录

> 开始日期：2026-08-20
> 当前状态：Foundation complete；运行时切换尚未完成

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

## 当前仍保留的兼容路径

`TenantServiceImpl` 目前仍调用 `TenantProvisionMapper -> sp_init_tenant`。这是刻意保留的切换门，不代表 S0-07 已完成。以下运行时能力闭合前不得删除存储过程，也不得把新租户创建路径标记为模块化初始化完成：

1. Auth 本地 Java 初始化器接管权限树、默认角色、根组织、管理员和 XSS。
2. Auth 在同一本地事务写 `tenant.provision-requested.v1` Outbox，事件不含管理员凭据。
3. Base、Workflow、CRM、SRM、Procurement、Asset 按 `tenantId + moduleId` 幂等消费并写结果 Outbox。
4. Auth 按 `requestId + moduleId` 汇总结果；只有所有目标模块成功才将租户切为 `ACTIVE`。
5. 失败模块可重试且不重复创建管理员、权限、字典、模板、管道或品类。
6. 登录租户列表和实际认证都拒绝 `PROVISIONING/FAILED` 租户。
7. 管理 UI 展示总体状态、失败模块、脱敏错误和重试入口。

## 安全与兼容约束

- 管理员明文密码只进入 Auth 的 `PasswordEncoder`，BCrypt 哈希也不得进入跨服务事件。
- 结果错误信息必须脱敏并限制长度，不能复制堆栈、SQL、消息正文或连接信息。
- `requestId` 用于一次初始化尝试的跨服务关联；模块事实唯一键仍为 `tenantId + moduleId`，相同请求重放必须幂等。
- 现有 `status=1` 租户继续视为已完成初始化；新增字段先兼容旧应用，再切换新创建路径。
- `sp_init_tenant` 只在 Phase 5 删除门通过后清理，不能在事件消费者和失败恢复尚未验证时提前删除。
