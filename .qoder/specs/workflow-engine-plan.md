# 工作流引擎集成实施计划

## Context

Omni-Stack 脚手架当前已具备认证、权限、字典、定时任务等基础能力，但缺少企业审批流能力。本计划集成 Flowable 8.0 工作流引擎，提供开箱即用的流程设计、审批、监控全链路能力，同时改造用户工作台以承载审批待办/已办。

**重要修正**: 原设计指定 Flowable 7.x，但 Spring Boot 4 默认使用 Jackson 3，Flowable 7.x 不兼容。改用 **Flowable 8.0.0**（支持 Spring Boot 4 + Jackson 3）。

## 设计决策汇总

| 决策项 | 方案 |
|--------|------|
| 引擎 | Flowable 8.0.0 嵌入式部署 |
| 架构 | `omni-workflow`(8103) + `omni-common-workflow` Starter |
| 身份 | 禁用 Flowable Identity，桥接 RBAC（UserGroupLookup） |
| 多租户 | Flowable `TENANT_ID_` + TenantInfoHolder |
| 表单 | 混合模式（JSON Schema + businessKey） |
| 会签 | Multi-Instance 封装 ApprovalService |
| 设计器 | bpmn-js 封装 Vue3 `<BpmnDesigner />` |
| 通知 | WorkflowNotificationService SPI，V1 站内待办 |
| 工作台 | el-tabs（待我审批/我发起的/我已办的/我的定时任务） |
| 管理控制台 | V1+V2 全部功能 |
| Demo | 请假审批流程 |

## Task 1: 基础设施 + 核心引擎

### 1.1 父 POM 修改
**文件**: `omni-backend/pom.xml`
- `<properties>` 新增 `<flowable.version>8.0.0</flowable.version>`
- `<modules>` 新增 `omni-common-workflow` 和 `omni-workflow`
- `<dependencyManagement>` 新增 `omni-common-workflow`

### 1.2 omni-common-workflow 骨架
**目录**: `omni-backend/omni-common-workflow/` (新建)

```
src/main/java/com/omni/common/workflow/
  config/
    FlowableAutoConfiguration.java   -- @AutoConfiguration, 禁用IDM, 注册引擎
  tenant/
    TenantInfoHolder.java            -- ThreadLocal<String> 租户ID
    TenantInfoFilter.java            -- 从 X-Tenant-Id 头设置 TenantInfoHolder
  identity/
    UserGroupLookup.java             -- SPI: getGroupsForUser/getAllGroups
  approval/
    ApprovalService.java             -- SPI: approve/reject/addSigner/removeSigner/delegate
    ApprovalServiceImpl.java         -- Multi-Instance 实现
  notification/
    WorkflowNotificationService.java -- SPI: notifyPendingTask
```

**pom.xml 依赖**: `flowable-spring-boot-starter` + `omni-common-core`
**AutoConfiguration.imports**: 注册 `FlowableAutoConfiguration`
**默认 YAML**: `flowable.database-schema-update: true`, `disable-idm-engine: true`, `history-level: audit`

### 1.3 omni-workflow 微服务骨架
**目录**: `omni-backend/omni-workflow/` (新建)

```
src/main/java/com/omni/workflow/
  WorkflowApplication.java           -- @SpringBootApplication + @MapperScan
  config/
    SecurityConfig.java              -- GatewayPreAuthFilter + STATELESS (复制 omni-base 模式)
    MybatisPlusConfig.java           -- TenantLineInnerInterceptor (仅对 wf_* 表, 排除 ACT_*)
    WorkflowSecurityConfig.java      -- UserGroupLookup 实现 (查 sys_role/sys_org_unit)
  controller/
    ProcessDefinitionController.java
    ProcessInstanceController.java
    TaskController.java
    ApprovalController.java
    FormSchemaController.java        -- V2
    WorkflowStatsController.java
  service/ (+ impl/)
    ProcessDefinitionService         -- 包装 RepositoryService
    ProcessInstanceService           -- 包装 RuntimeService + HistoryService
    WorkflowTaskService              -- 包装 TaskService
    FormSchemaService                -- CRUD wf_process_form_schema
    WorkflowStatsService             -- 聚合统计
  mapper/
    WfProcessFormSchemaMapper.java
    WfDelegationRuleMapper.java      -- V2
  entity/
    WfProcessFormSchema.java
    WfDelegationRule.java            -- V2
    WfProcessInstanceExt.java
    WfTodoTask.java
  dto/
    DeployProcessRequest / ProcessDefinitionVO
    StartProcessRequest / ProcessInstanceVO
    TaskVO / ApprovalRequest
    AddSignerRequest / DelegateRequest
    FormSchemaVO / WorkflowStatsVO
src/main/resources/
  application.yml                    -- port 8103, datasource omni_workflow, Nacos
  processes/leave-request.bpmn20.xml -- 请假审批 Demo
```

**application.yml 要点**: port 8103, datasource `omni_workflow`, Nacos discovery/config, RocketMQ operlog

### 1.4 Gateway 路由
**文件**: `omni-gateway/src/main/resources/application.yml`
- routes 新增: `id: omni-workflow`, `uri: lb://omni-workflow`, `predicates: Path=/api/workflow/**`

### 1.5 SQL 初始化脚本
**文件**: `scripts/sql/init-all.sql` (末尾追加 Section 8)
- 创建 `omni_workflow` 数据库
- 4 张业务扩展表: `wf_process_form_schema`, `wf_process_instance_ext`, `wf_todo_task`, `wf_delegation_rule`
- 权限种子数据: `sys_permission` 工作流目录+菜单+API (约 12 条)
- 角色权限关联: SUPER_ADMIN 追加工作流权限
- 字典种子数据: `wf_process_category` (4 个分类)

### 1.6 启动脚本
**文件**: `start.bat`, `start.sh` — 新增 omni-workflow 服务启动命令

## Task 2: 流程定义管理 + BPMN 设计器

### 2.1 后端 API
**ProcessDefinitionController** (`/process-definition`):
| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/list` | 分页查询 (tenantId 过滤) |
| GET | `/{id}/bpmn` | 获取 BPMN XML |
| POST | `/deploy` | 部署 (BPMN XML + name + category) |
| PUT | `/{id}/suspend` | 挂起 |
| PUT | `/{id}/activate` | 激活 |
| DELETE | `/{deploymentId}` | 删除部署 |

### 2.2 前端
- `src/api/workflow-process.ts` — 流程定义 CRUD
- `src/views/workflow/process-definition/index.vue` — 列表 + 部署弹窗
- `src/components/BpmnDesigner.vue` — bpmn-js 封装组件
  - Props: `modelValue`(XML), `readonly`, `highlightedNodes`
  - Emits: `update:modelValue`
  - npm 依赖: `bpmn-js`, `bpmn-js-properties-panel`
- i18n / menu 常量 / router iconMap 更新

## Task 3: 流程发起 + 审批 + 工作台改造

### 3.1 后端 API
**ProcessInstanceController** (`/process-instance`):
| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/start` | 发起流程 |
| GET | `/my-initiated` | 我发起的 |
| GET | `/my-completed` | 我已办的 |
| GET | `/list` | 管理: 全部实例 |
| GET | `/{id}/detail` | 详情 (流程图高亮) |
| DELETE | `/{id}` | 终止 |

**TaskController** (`/task`):
| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/todo` | 待我审批 |
| GET | `/todo/count` | 待办数量 |
| GET | `/{taskId}/form` | 获取任务表单 |

**ApprovalController** (`/approval`):
| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/{taskId}/complete` | 审批通过/驳回 |
| POST | `/{taskId}/add-signer` | 加签 |
| POST | `/{taskId}/remove-signer` | 减签 |
| POST | `/{taskId}/delegate` | 委托 |

### 3.2 前端工作台改造
**文件**: `src/views/home/index.vue`
- `<main class="workspace">` 内部改为 `el-tabs` 4 标签页:
  - 待我审批: 审批任务列表 + 操作按钮
  - 我发起的: 已发起流程列表 + 状态
  - 我已办的: 历史流程列表
  - 我的定时任务: 现有内容迁移至此 tab
- 统计卡片 3→4: 待审批 / 我发起的 / 已办结 / 今日执行任务
- CSS `.ws-stats` grid: `repeat(3,1fr)` → `repeat(4,1fr)`

### 3.3 审批组件
- `src/components/ApprovalDialog.vue` — 审批操作弹窗 (复用 `DynamicFormRenderer`)

### 3.4 前端 API
- `src/api/workflow-instance.ts`
- `src/api/workflow-task.ts`
- `src/api/workflow-approval.ts`

### 3.5 管理页面
- `src/views/workflow/instance/index.vue` — 流程实例监控

## Task 4: Demo 流程 + 统计 + 收尾

### 4.1 请假审批 BPMN
**文件**: `omni-workflow/src/main/resources/processes/leave-request.bpmn20.xml`
- StartEvent → UserTask(直属主管) → ExclusiveGateway(≤3天直接通过/>3天总监) → UserTask(总监) → EndEvent
- 配合 JSON Schema 表单 (请假类型/开始日期/结束日期/天数/事由)

### 4.2 统计
**WorkflowStatsController** (`/stats`):
| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/workspace` | 工作台统计 (todoCount, myInitiatedCount 等) |
| GET | `/admin` | 管理端统计 (totalInstances, runningCount 等) |

### 4.3 前端
- `src/api/workflow-stats.ts`
- 工作台统计卡片接入统计数据
- `src/views/workflow/task/index.vue` — 任务管理页 (管理员)

## Task 5 (V2): 表单设计器 + 委托 + 看板

| # | 任务 |
|---|------|
| 5.1 | FormSchemaController + Service CRUD |
| 5.2 | 可视化 JSON Schema 编辑器组件 |
| 5.3 | WfDelegationRule 管理页面 + 审批时自动查委托规则 |
| 5.4 | 管理端统计仪表板 (ECharts) |

## 风险与注意事项

1. **ACT_* 表租户隔离**: Flowable `TENANT_ID_` (VARCHAR) vs 项目 `tenant_id` (BIGINT)。`MybatisPlusConfig` 的 `TenantLineInnerInterceptor` 必须排除 `ACT_` 前缀表
2. **跨服务用户信息**: `UserGroupLookup` 需查 `omni_auth` 库。V1 方案: 配置双数据源直连; 后续可改 Feign
3. **待办同步**: `wf_todo_task` 通过 Flowable TaskListener 同步写入/删除
4. **bpmn-js 许可**: MIT，可商用
5. **Jackson 3**: Flowable 8 + Spring Boot 4 均默认 Jackson 3，项目自定义 DTO 注解需确认兼容

## 验证方案

1. **后端编译**: `cd omni-backend && ./mvnw clean install` 成功
2. **服务启动**: omni-workflow 启动后 Nacos 可见注册，Flowable 自动建表
3. **流程部署**: 通过 API 部署请假审批 BPMN，`ACT_RE_PROCDEF` 有记录
4. **工作台**: 登录后 4 个 tab 均可加载数据
5. **审批流**: 发起请假 → 主管审批 → 流程完成，各步骤数据正确
6. **前端构建**: `npm run build && npm run lint` 成功
