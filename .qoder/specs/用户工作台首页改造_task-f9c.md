# 用户工作台：首页登录后变身定时任务工作台

## Context

当前系统为单层控制台模式，普通 `USER` 角色无法访问定时任务管理。需要将系统划分为两层架构：**用户工作台**（首页 `/`）面向所有登录用户提供个人任务自助管理；**管理控制台**（`/admin`）保留给管理员做任务类型配置和系统管理。

## 设计决策

| 决策点 | 结论 |
|--------|------|
| 用户模式 | 用户自助，按 `create_by` 过滤只看自己的任务 |
| 首页定位 | 登录后变工作台，Hero 仅未登录时显示 |
| 控制台定位 | 管理员专属，保留现有"任务管理"和"执行日志"全局视图 |
| API 策略 | 工作台 `/api/base/my-job`（只需登录）+ 控制台 `/api/base/user-job`（RBAC） |
| 布局 | 仪表盘式：统计卡片 + 创建按钮 + 任务列表 |
| 执行记录 | 任务行内"查看日志"按钮 → 弹窗展示 |
| 登录跳转 | 直接到工作台 `/`，不再跳转 `/admin/dashboard` |
| 任务类型 | 工作台独立 `GET /api/base/my-job/types`（无 RBAC） |
| 统计卡片 | `GET /api/base/my-job/stats` 聚合端点 |

## Task 1: 后端 — 新增 MyJobController

**新建** `omni-base/src/main/java/com/omni/base/controller/MyJobController.java`

路径前缀 `/api/base/my-job`，无 `@PreAuthorize`，只需登录态：
- `GET /list` — 按 `create_by` 过滤当前用户任务（分页）
- `GET /types` — 返回启用状态的任务类型列表
- `GET /stats` — 聚合统计（任务总数/今日执行/今日失败）
- `POST` — 创建任务（`create_by` 自动填当前用户名）
- `PUT /{id}` — 更新任务（校验 `create_by` 归属）
- `DELETE /{id}` — 删除任务（校验 `create_by` 归属）
- `PUT /{id}/status` — 切换状态（校验归属）
- `POST /{id}/trigger` — 立即触发（校验归属）
- `GET /{id}/logs` — 查询该任务执行日志

通过 `SecurityContextHolder` 获取用户名，`@RequestHeader("X-Tenant-Id")` 获取租户 ID。复用 `UserJobService` 和 `UserJobTypeService`。

## Task 2: 后端 — Service 层扩展

**修改文件**：
- `UserJobQuery.java` — 新增 `createBy` 字段
- `UserJobService.java` — 新增 `getStats(Long tenantId, String createBy)` 方法
- `UserJobServiceImpl.java` — `listJobs()` 查询条件增加 `createBy` 过滤 + 实现统计查询

**新建** `MyJobStats.java` DTO：`totalJobs`, `todayExecuted`, `todayFailed`

**统计 SQL**：
- `totalJobs`: `COUNT(*) FROM sys_user_job WHERE tenant_id=? AND create_by=?`
- `todayExecuted`: `COUNT(*) FROM sys_user_job_log l JOIN sys_user_job j ON l.job_id=j.id WHERE j.tenant_id=? AND j.create_by=? AND DATE(l.fire_time)=CURDATE()`
- `todayFailed`: 同上 + `AND l.status=0`

## Task 3: 前端 — 新增工作台 API 层

**新建** `omni-frontend/src/api/myJob.ts`

API 函数：`listMyJobs`, `createMyJob`, `updateMyJob`, `deleteMyJob`, `toggleMyJobStatus`, `triggerMyJob`, `getMyJobTypes`, `getMyJobStats`, `getMyJobLogs`

路径统一 `/base/my-job/...`，从 JWT 获取租户 ID 注入 `X-Tenant-Id` header。

## Task 4: 前端 — 改造首页为工作台

**修改** `omni-frontend/src/views/home/index.vue`

- `v-if="!userStore.isLoggedIn"` → 显示 Hero 落地页（现有内容）
- `v-else` → 显示工作台界面

**工作台布局**：
```
Header: Logo + 语言/主题切换 + 用户菜单 + [控制台]（仅管理员可见）
────────────────────────────────────────────────
统计卡片: [任务总数] [今日执行] [今日失败]
────────────────────────────────────────────────
[+ 创建任务]  搜索栏(名称/类型/状态)
────────────────────────────────────────────────
任务列表 (el-table):
  名称 | 类型 | Cron | 状态 | 下次执行 | 操作
  操作: 编辑 | 删除 | 启用/禁用 | 执行 | 查看日志
────────────────────────────────────────────────
分页组件
```

**弹窗**：
- 创建/编辑任务弹窗：复用 `CronGenerator` + `DynamicFormRenderer`
- 执行日志弹窗：800px 宽，展示该任务的执行记录

**Header "控制台"按钮**：通过 `permissionStore` 检查用户是否拥有 `base:user-job-type:*` 等管理权限，有则显示按钮。

## Task 5: 前端 — 路由守卫修改

**修改** `omni-frontend/src/router/index.ts`：
- `Home` 路由保持 `requiresAuth: false`（已登录和未登录都可访问）
- 路由守卫：已登录用户访问 `/` 直接放行（不再重定向 Dashboard）

**修改** `omni-frontend/src/stores/user.ts`：
- 登录成功后跳转目标改为 `Home`（`/`）

## Task 6: 前端 — i18n 国际化

**修改** `zh-CN.ts` + `en-US.ts`，新增 `workspace` 模块：
- `workspace.title` / `workspace.stats.*` / `workspace.createJob` / `workspace.viewLogs` / `workspace.noJobs` 等

## 关键文件清单

| 操作 | 文件路径 |
|------|----------|
| 新建 | `omni-base/.../controller/MyJobController.java` |
| 新建 | `omni-base/.../dto/MyJobStats.java` |
| 修改 | `omni-base/.../dto/UserJobQuery.java` |
| 修改 | `omni-base/.../service/UserJobService.java` |
| 修改 | `omni-base/.../service/impl/UserJobServiceImpl.java` |
| 新建 | `omni-frontend/src/api/myJob.ts` |
| 修改 | `omni-frontend/src/views/home/index.vue` |
| 修改 | `omni-frontend/src/router/index.ts` |
| 修改 | `omni-frontend/src/stores/user.ts` |
| 修改 | `omni-frontend/src/locales/zh-CN.ts` |
| 修改 | `omni-frontend/src/locales/en-US.ts` |

## Verification

1. 后端编译: `cd omni-backend && ./mvnw clean install`
2. 前端构建: `cd omni-frontend && npm run build && npm run lint`
3. 端到端验证:
   - 未登录 `/` → Hero 落地页
   - 登录后 → 自动跳转工作台
   - 创建/编辑/删除/切换状态/立即触发 → 正常
   - "查看日志"弹窗 → 显示执行记录
   - 统计卡片数字正确
   - 普通用户无"控制台"按钮，管理员有
   - 控制台"任务管理"页面仍正常（管理员全局视图）