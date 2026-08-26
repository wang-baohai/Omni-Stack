# 菜单、角色、功能权限与数据权限

Omni-Stack 将“能否执行功能”和“能看到哪些数据”分开处理。只隐藏按钮不能构成安全控制，所有写接口都必须由后端再次鉴权。

## 1. 四层关系

```text
用户 → 用户角色范围 → 角色 → 权限树
                    ↘ 数据范围
```

- 用户：租户内账号。
- 角色：一组稳定权限码和数据范围策略。
- 权限：`DIRECTORY`、`MENU`、`BUTTON/API` 三类节点。
- 用户角色范围：用户在特定组织单元内拥有某角色。

权限码使用 `resource:action` 格式，例如 `procurement:requisition:create`。目录和菜单用于导航，按钮/API 权限用于实际操作。

## 2. 动态菜单

登录后前端调用 `GET /api/auth/menus`。Auth 服务按当前权限返回树形菜单；前端只把 `MENU` 节点转换为动态路由，并通过共享映射翻译菜单名称。

菜单不出现时按顺序检查：

1. 当前预设是否包含目标模块。
2. `sys_permission` 是否存在对应目录、菜单和操作权限。
3. 当前角色是否通过 `sys_role_permission` 获得权限。
4. JWT 中是否包含最新 authorities；权限变更后应重新登录。
5. 页面按钮是否使用同一个 `v-permission` 权限码。

不要把没有权限的页面通过硬编码静态路由暴露出来。

## 3. 功能权限

后端写操作必须声明 `@PreAuthorize`，前端按钮使用同一权限码的 `v-permission`。指令采用 `display:none` 保持 Vue 响应式结构，但它只改善界面体验，不代替后端检查。

`MyJobController` 是例外：个人任务按每行 `createBy` 校验归属，不使用端点级 RBAC。供应商 Portal 同时要求 Portal 权限、`SUPPLIER` 角色和有效关联。

## 4. 数据权限

DataScope 决定查询和写操作能够触达的数据集合。常见范围包括：

- 全部数据。
- 当前租户。
- 当前组织。
- 当前组织及下级。
- 仅本人。
- 自定义组织集合。

Servlet 业务服务通过可信 Gateway 身份建立请求上下文，再由 MyBatis 数据权限拦截器追加条件。拦截器顺序必须为 DataPermission 在 Pagination 之前；请求结束必须在 `finally` 清理 ThreadLocal。

领域映射不能套用同一 owner 列：

- CRM、SRM、Procurement、Asset 各自维护聚合根可见性。
- 子表通过聚合根继承范围，不能给不存在 owner 列的子表追加条件。
- 采购申请使用申请人列，RFQ、订单和收货使用负责人列。
- 资产“我的资产”、接收和归还固定按当前用户；管理视图使用 owner 列。

## 5. 角色维护流程

1. 建立或选择角色。
2. 分配功能权限树。
3. 设置数据范围。
4. 在具体组织范围内把角色授予用户。
5. 使用目标用户重新登录验证菜单、按钮、API 和数据集合。
6. 至少验证一个 403 场景，确认后端拒绝越权请求。

不要只使用超级管理员验收权限功能。

## 6. 新增权限的代码清单

新增写功能时必须同时更新：

1. Controller `@PreAuthorize`。
2. `scripts/sql/seed/auth.sql` 权限节点和默认角色关系。
3. `database/seed/manifest.yaml` SHA-256 与断言。
4. 前端动态路由映射或页面入口。
5. 操作按钮 `v-permission`。
6. 功能权限、数据范围和跨租户自动化测试。
7. 对应模块文档与截图。

详细实现见 [后端规范](../backend-patterns.md)、[前端规范](../frontend-patterns.md) 和 [核心流程](../core-flows.md)。

