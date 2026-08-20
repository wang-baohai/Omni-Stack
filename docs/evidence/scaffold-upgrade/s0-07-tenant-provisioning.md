# S0-07 租户模块化初始化实施记录

> 开始日期：2026-08-20
> 当前状态：实现完成；等待 S0-08 全栈消息与失败恢复门禁证据

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
- Base、Workflow、CRM、SRM、Procurement、Asset 已实现统一 `TenantModuleProvisioner` SPI；每个服务使用独立消费者组接收同一请求，并在本地领域事务中写入幂等回执和结果 Outbox。
- Base 克隆字典目录，Workflow 克隆并发布默认流程模型，CRM 执行阶段与字典初始化，SRM 初始化评估模板并克隆风险目录，Procurement 初始化完整 13 项物料分类，Asset 显式确认无模块自有默认事实。
- 消费回执表以 `request_id + module_id` 唯一约束阻止同一请求重复执行；失败摘要进行连接串、密码、Token 和换行脱敏，不记录原始异常堆栈。
- `TenantServiceImpl` 已停止调用 `sp_init_tenant`，在同一 Auth 事务中持久化租户、执行本地初始化并写请求 Outbox；旧 Mapper 和存储过程仅作为 Phase 5 前的兼容残留保留。
- 密码登录、会话登录和社交登录均拒绝 `PROVISIONING/FAILED` 租户；公开登录租户列表只返回业务启用且初始化为 `ACTIVE` 的租户。
- 租户管理 API 和 UI 已展示总体状态、脱敏失败摘要、模块明细和失败模块重试入口；重试生成新 requestId，已成功模块保持成功且不会重新执行。
- 新增/更新的契约和适配测试覆盖 Auth 编排、通用消费、Base、Workflow、CRM、SRM、Procurement、Asset；目标 reactor、前端 production build 均通过，本次 UI 没有新增 ESLint 告警。
- 在一次性 MySQL 8.4 隔离实例重新执行九库 fresh 迁移，七个应用库均创建 `sys_tenant_provision_receipt`；紧接着第二次迁移全部报告无待执行 changeSet。

## S0-08 尚需闭合的运行证据

以下不是 S0-07 的代码缺口，而是进入 G1 前必须在隔离全栈完成的运行门禁：

1. 真实 RocketMQ 广播到六个服务，Auth 收齐结果后租户从 `PROVISIONING` 转为 `ACTIVE`。
2. 人为停止一个模块后创建租户，确认失败或超时可观测；恢复模块并重试后转为 `ACTIVE`，且管理员和已成功模块数据不重复。
3. 对 fresh、adopt 后 upgrade、重复执行、迁移中断恢复分别归档数据库计数、changeSet 和回滚/恢复证据。
4. 在 Compose 启动契约中强制 migrator 成功后才启动应用，并关闭 Flowable 运行时自动建表。

## 安全与兼容约束

- 管理员明文密码只进入 Auth 的 `PasswordEncoder`，BCrypt 哈希也不得进入跨服务事件。
- 结果错误信息必须脱敏并限制长度，不能复制堆栈、SQL、消息正文或连接信息。
- `requestId` 用于一次初始化尝试的跨服务关联；模块事实唯一键仍为 `tenantId + moduleId`，相同请求重放必须幂等。
- 现有 `status=1` 租户继续视为已完成初始化；新增字段先兼容旧应用，再切换新创建路径。
- `sp_init_tenant` 只在 Phase 5 删除门通过后清理，不能在事件消费者和失败恢复尚未验证时提前删除。
