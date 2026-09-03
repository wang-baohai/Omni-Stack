# 工作流引擎

> 本文档覆盖 Omni-Stack 工作流引擎的架构、核心流程、约束和扩展指南。  
> 架构概览详见 [architecture.md](architecture.md)。Docker 部署配置详见 [docker-deployment.md](docker-deployment.md)。

Omni-Stack 提供基于 **Flowable 7.x** 的可视化 BPMN 工作流引擎，支持模型设计、双版本管理、多实例会签审批和端到端流程跟踪。

## 1. 架构概览

工作流系统由一个独立微服务和一个共享 Starter 库组成：

```
┌──────────────────────────────────────────────────────────────────────────┐
│                          Workflow Engine                                  │
├──────────────────────────────────────────────────────────────────────────┤
│                      omni-workflow (port 8103)                            │
│  ─────────────────────────────────────────────────────────────────────   │
│  Controllers (8): WorkflowModel · ProcessDefinition · ProcessInstance    │
│                   Approval · Task · WorkflowStats · WorkflowIdentity     │
│                   InternalWorkflow                                      │
│  Services (12): WorkflowModel · ProcessDefinition · ProcessInstance      │
│                 WorkflowApproval · WorkflowTask · WorkflowStats           │
│                 WorkflowIdentity · WorkflowTodoSync · InternalWorkflow   │
│                 ProcessStartRequest · CompletionEvent                    │
│                 CandidateResolution                                     │
│  Delegates:  ScopedRoleAssignmentListener · CandidateResolverDelegate    │
│              CandidateResolverBean · CcNotifyDelegate                    │
│  Engine:     BpmnXmlBuilder · BpmnXmlValidator                           │
├──────────────────────────────────────────────────────────────────────────┤
│                  omni-common-workflow (shared starter)                    │
│  FlowableAutoConfiguration · ApprovalService(Impl) · UserGroupLookup     │
│  WorkflowNotificationService · TenantInfoFilter · TenantInfoHolder       │
├──────────────────────────────────────────────────────────────────────────┤
│                        Flowable BPMN Engine 7.x                           │
│  repositoryService · runtimeService · taskService · historyService       │
├──────────────────────────────────────────────────────────────────────────┤
│                       omni_workflow (MySQL)                               │
│  wf_process_model · wf_process_model_version · wf_process_instance_ext   │
│  wf_process_start_request · wf_todo_task · wf_cc_record · sys_mq_message │
│  wf_form_schema · wf_delegation_rule                                    │
└──────────────────────────────────────────────────────────────────────────┘
```

**模块依赖**：

- `omni-common-core` — POJO（`R<T>`、`PageResult`）、XSS SPI 接口
- `omni-common-mybatis` — MyBatis-Plus + MySQL 驱动 + 租户拦截器
- `omni-common-redis` — Redis 缓存，用于 XSS 配置和会话数据
- `omni-common-workflow` — Flowable 自动配置、审批 SPI、租户过滤器、通知 SPI
- `omni-common-mqlog` — Transactional Outbox、内部 API 共享令牌过滤器和可靠消息中继
- `omni-workflow` — 业务层：控制器、服务、委托器、BPMN 引擎工具

**关键设计决策**：

- 选择 **Flowable** 作为 BPMN 引擎：开源、成熟的 Spring Boot 集成、原生多实例（MI）支持
- **双版本管理**：业务版本在 `wf_process_model_version` 中跟踪（DRAFT → PUBLISHED → ARCHIVED），引擎版本由 Flowable 部署管理
- **可视化设计器**：前端 BPMN 建模器生成设计器 JSON，`BpmnXmlBuilder` 转换为 BPMN 2.0 XML
- **动态候选人解析**：`omni:assignment` JSON 扩展元素在任务启动时由 `ScopedRoleAssignmentListener` 解析，无需硬编码审批人
- **跨服务启动双幂等**：分别以租户内 `requestId` 和 `(businessType, businessKey)` 约束流程启动
- **可靠完成通知**：完成元数据和 Outbox 记录在同一本地事务内提交，由中继任务异步投递

### 数据模型

```mermaid
erDiagram
    wf_process_model ||--o{ wf_process_model_version : "1:N 版本"
    wf_process_model_version ||--o{ wf_process_instance_ext : "1:N 实例"
    wf_process_instance_ext ||--o{ wf_todo_task : "1:N 待办"
    wf_process_instance_ext ||--o{ wf_cc_record : "1:N 抄送"
```

### 数据库表（omni_workflow）

| 表名 | 用途 |
|------|------|
| `wf_process_model` | 流程模型注册表，`model_key` 在租户内唯一 |
| `wf_process_model_version` | 版本历史：BPMN XML、设计器 JSON、部署信息 |
| `wf_process_instance_ext` | 实例扩展：关联模型版本，并记录 `requestId`、业务键和完成事件门闩 |
| `wf_process_start_request` | 跨服务启动预留记录；请求 ID 与业务键在租户内分别唯一 |
| `wf_todo_task` | 待办任务缓存，支持按审批人快速查询 |
| `wf_cc_record` | 抄送通知记录，含已读状态 |
| `wf_form_schema` | JSON Schema 表单定义 |
| `wf_delegation_rule` | 审批委托规则（用户到用户，可选流程范围） |
| `sys_mq_message` | Transactional Outbox 消息记录，由可靠消息中继异步投递 |

---

## 2. 核心流程详解

### 2.1 模型创建

```
POST /api/workflow/model  (workflow:model:create)
```

1. `WorkflowModelController.createModel(CreateModelRequest)` → `WorkflowModelService.createModel()`
2. 创建 `wf_process_model` 记录，包含 `model_key`（租户内唯一）
3. 创建初始 `wf_process_model_version` 记录，`status = DRAFT`
4. 将 `wf_process_model.current_draft_version_id` 指向新版本

### 2.2 草稿保存（可视化设计器）

```
PUT /api/workflow/model/{id}/draft  (workflow:model:update)
```

1. `WorkflowModelController.saveDraft(id, SaveDraftRequest)` → `WorkflowModelService.saveDraft()`
2. 更新草稿版本的 `designer_json`，通过 `BpmnXmlBuilder.build()` 重新生成 `bpmn_xml`
3. 计算 `xml_sha256` 用于变更检测
4. 从请求中同步模型名称和分类

**BpmnXmlBuilder** 将设计器 JSON 节点转换为 BPMN 2.0 XML 元素：

| 设计器节点类型 | BPMN 元素 | 扩展 |
|---|---|---|
| `StartEvent` | `<startEvent>` | — |
| `EndEvent` | `<endEvent>` | — |
| `UserTask` | `<userTask>` | `<omni:assignment>` + `flowable:executionListener` |
| `ServiceTask`（抄送） | `<serviceTask>` | `<omni:cc>` + `flowable:delegateExpression` |
| `ExclusiveGateway` | `<exclusiveGateway>` | `default` 属性 |
| `ParallelGateway` | `<parallelGateway>` | — |

### 2.3 模型校验

```
POST /api/workflow/model/{id}/validate  (workflow:model:validate)
```

`BpmnXmlValidator.validate()` 检查项：
1. XML 格式正确性（含 XXE 防护）
2. 恰好一个可执行的 `<process>`，其 id 与 `model_key` 匹配
3. 至少一个 `StartEvent` 和一个 `EndEvent`
4. 每个 `UserTask` 都有 `<omni:assignment>` 扩展
5. 抄送 `ServiceTask` 有 `<omni:cc>` 扩展
6. `ExclusiveGateway` 有一个 `default` 流（不带 `conditionExpression`）
7. 所有 `SequenceFlow` 引用有效的源/目标

### 2.4 模型发布

```
POST /api/workflow/model/{id}/publish  (workflow:model:publish)
```

1. `SELECT FOR UPDATE` 对模型记录加悲观锁
2. 通过 `BpmnXmlValidator` 校验 BPMN XML
3. 将 `targetNamespace` 替换为模型分类
4. 部署到 Flowable：`repositoryService.createDeployment().addString(bpmnXml).deploy()`
5. 计算业务版本号（`max(现有版本) + 1`）
6. 更新版本记录：`status = PUBLISHED`、`deploymentId`、`processDefinitionId`、`engineVersion`
7. 归档之前的已发布版本（`status = ARCHIVED`）
8. 更新模型的 `current_published_version_id`

### 2.5 流程实例启动

```
POST /api/workflow/process-instance/start  (workflow:instance:start)
```

1. `ProcessInstanceController.start(StartProcessRequest)` → `ProcessInstanceService.start()`
2. 解析最新的已发布版本以获取 `processDefinitionId`
3. 启动 Flowable 实例：`runtimeService.startProcessInstanceById(processDefinitionId, businessKey, variables)`
4. 创建 `wf_process_instance_ext` 记录，关联模型、版本和 Flowable 实例
5. `ScopedRoleAssignmentListener` 在每个 UserTask 启动事件触发时解析候选人

### 2.6 审批完成

```
POST /api/workflow/approval/{taskId}/complete  (workflow:approval:complete)
```

1. `ApprovalController.complete(taskId, ApprovalRequest)` → `WorkflowApprovalService.complete()`
2. 设置流程变量：`approved = true/false`、`comment = "..."`
3. 调用 `taskService.complete(taskId, variables)`
4. `ApprovalServiceImpl` 更新多实例计数器（`approvedCount` / `rejectedCount`）
5. 多实例 `completionCondition` 求值：`${rejectedCount > 0 || approvedCount >= requiredApprovals}`
6. 若条件满足 → 剩余的多实例被跳过（deleteReason = `MI_END`）

### 2.7 进度与记录

**进度**（`GET /{id}/progress`）：
- 查询 `HistoricActivityInstance` 获取所有活动
- 按 `activityId` 聚合（去重多实例子实例）
- 对于待办的 UserTask，通过 `CandidateResolverBean` 预解析候选人
- 返回 `ProcessProgressResponse`，包含 `List<ActivityInfo>` 及每个审批人的状态

**审批记录**（`GET /{id}/approval-records`）：
- 查询 `HistoricTaskInstance`（按创建时间升序）
- 判定每个任务的结果：`approved`（通过）/ `rejected`（驳回）/ `auto-approved`（自动通过，MI_END）/ `cancelled`（已取消）/ `pending`（待办）
- 获取 `Comment` 作为审批意见，获取 `approved` 变量用于区分通过/驳回

### 2.8 跨服务内部契约

所有服务间接口统一使用 `/api/internal/**` 路径，不经过 Gateway 用户预认证。调用方必须同时携带：

```http
X-Internal-Token: <共享内部令牌>
X-Tenant-Id: 1
Content-Type: application/json
```

容器级 `InternalApiAuthFilter` 在 Spring Security 链之前校验共享令牌并对全部 `/api/internal/**`
失败关闭；这些路径不会再使用 Gateway 用户预认证。令牌缺失或不匹配返回 HTTP 401；服务端未配置共享令牌时返回 HTTP 503。
内部请求中的 `X-Tenant-Id` 必须与请求体或查询参数中的 `tenantId` 完全一致，不一致返回业务码 403。

#### 2.8.1 幂等启动流程

```http
POST /api/internal/workflow/process-instance/start
```

请求体：

```json
{
  "requestId": "6d2f4d1a-41d7-4f68-a60a-8a2e9425a703",
  "tenantId": 1,
  "modelVersionId": 42,
  "businessType": "PROCUREMENT_REQUISITION",
  "businessKey": "10001",
  "startUserId": 7,
  "startUserName": "buyer",
  "title": "采购申请 PR-202607-0001",
  "variables": {
    "amount": 120000
  }
}
```

| 字段 | 必填 | 约束 | 说明 |
|---|---|---|---|
| `requestId` | 是 | 非空，最长 64 | 调用方生成的幂等请求 ID |
| `tenantId` | 是 | 正整数 | 必须等于 `X-Tenant-Id` |
| `modelVersionId` | 是 | 正整数 | 必须属于当前租户且已关联 Flowable `processDefinitionId` |
| `businessType` | 是 | 非空，最长 100 | 稳定的跨服务业务类型 |
| `businessKey` | 是 | 非空，最长 255 | 调用方业务主键 |
| `startUserId` | 是 | 正整数 | 流程发起人 |
| `startUserName` | 否 | 最长 100 | 发起人显示名 |
| `title` | 否 | 最长 500 | 为空时生成 `{businessType}:{businessKey}` |
| `variables` | 否 | JSON 对象 | 业务流程变量；服务会覆盖写入三个关联变量 |

服务始终按 `modelVersionId` 解析 `processDefinitionId`，再调用
`startProcessInstanceById`。`requestId`、`businessType`、`businessKey` 同时写入流程变量和实例扩展记录。

成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "requestId": "6d2f4d1a-41d7-4f68-a60a-8a2e9425a703",
    "businessType": "PROCUREMENT_REQUISITION",
    "businessKey": "10001",
    "processInstanceId": "22501",
    "replayed": false
  }
}
```

幂等规则：

- `wf_process_start_request` 分别建立 `(tenant_id, request_id)` 和
  `(tenant_id, business_type, business_key)` 唯一约束，任一维度都不能重复创建流程。
- 同一请求意图（相同业务键、`modelVersionId`、`startUserId`）已经成功时，重试返回原
  `processInstanceId`，并设置 `replayed = true`。
- 已有预留仍在处理中返回业务码 409；调用方应使用同一 `requestId` 退避重试。
- 同一 `requestId` 被不同业务使用，或同一业务键改换流程模型/发起人，返回业务码 409，禁止静默复用。

#### 2.8.2 校验任务处理资格

```http
POST /api/internal/workflow/task/assignment/validate
```

请求体：

```json
{
  "tenantId": 1,
  "taskId": "25017",
  "userId": 7,
  "businessType": "PROCUREMENT_REQUISITION",
  "businessKey": "10001"
}
```

校验同时覆盖四层边界：Flowable 任务租户、实例扩展记录租户、`businessType + businessKey`
业务归属，以及用户是否为当前 `ASSIGNEE` 或未签收任务的 `CANDIDATE`。任何一层不匹配都不会授予处理资格。

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "valid": true,
    "processInstanceId": "22501",
    "assignmentType": "ASSIGNEE",
    "message": "校验通过"
  }
}
```

`assignmentType` 仅取 `ASSIGNEE`、`CANDIDATE`、`NONE`。任务不存在或边界不匹配时正常返回
`valid = false`；请求头与请求体租户不一致属于调用方安全错误，返回业务码 403。

#### 2.8.3 流程完成事件

跨服务流程在最终审批结束或被终止时产生 `workflow.process.completed.v1`。事件通过
`workflow-domain-out-0` binding 写入 `workflow-domain-event`，载荷如下：

```json
{
  "eventId": "3f206832-9dc1-4422-870a-a286a979404d",
  "eventType": "workflow.process.completed.v1",
  "occurredAt": "2026-07-21 10:30:00",
  "tenantId": 1,
  "producer": "omni-workflow",
  "businessType": "PROCUREMENT_REQUISITION",
  "businessKey": "10001",
  "processInstanceId": "22501",
  "result": "APPROVED",
  "completedTime": "2026-07-21 10:30:00"
}
```

| 字段 | 说明 |
|---|---|
| `eventId` | UUID；同时作为 Outbox `msgKey` 和消费端幂等键 |
| `eventType` | 固定为 `workflow.process.completed.v1` |
| `occurredAt` | 事件记录产生时间 |
| `tenantId` | 业务租户 ID |
| `producer` | 固定为 `omni-workflow` |
| `businessType` / `businessKey` | 回查调用方聚合的稳定业务标识 |
| `processInstanceId` | Flowable 流程实例 ID |
| `result` | `APPROVED`、`REJECTED`、`CANCELLED` |
| `completedTime` | 流程实际完成或终止时间 |

实例状态/完成元数据更新与 `sys_mq_message` 的 PENDING Outbox 记录在同一本地事务中提交。
`completion_event_id IS NULL` 条件更新是数据库发布门闩，保证同一流程实例只生成一条逻辑完成事件；事务失败时两者一起回滚。
中继任务在提交后异步投递并重试，因此消息传输语义是**至少一次**，消费方仍必须按 `eventId` 幂等消费。

#### 2.8.4 查询已发布模型版本

```http
GET /api/internal/workflow/model-version/{modelVersionId}
X-Internal-Token: <共享内部令牌>
X-Tenant-Id: 1
```

响应包含 `id/modelId/modelKey/category/version/processDefinitionId/status`。其中：

- `modelKey` 是租户内唯一且必须与 BPMN process id 一致的模型标识。
- `category` 是供业务服务绑定审批用途的稳定分类，不等同于可自由显示的模型名称。
- 模型主记录已归档、版本不是 `PUBLISHED`、版本不属于请求租户或缺少
  `processDefinitionId` 时统一返回 404。

业务服务可以在启动前把自身稳定 `businessType` 与 `category` 做精确匹配，防止误用其他业务的
已发布模型。Workflow 内部启动端点会在实际创建实例前再次校验
`ASSET_TRANSFER/ASSET_DISPOSAL` 与模型分类，错配以 404 明确拒绝且不创建实例；既有
Procurement 审批路由不受该 Asset 专用绑定影响。该查询只提供模型元数据，不授予流程启动或审批权限。

---

## 3. 约束与注意事项

### 3.1 多实例 DeleteReason

当多实例 `completionCondition` 触发时，剩余任务会被 Flowable 以 `deleteReason = "MI_END"` 删除——**而非** `"deleted"`。`"deleted"` 原因在整个流程实例被终止或驳回时使用。

**规则**：始终使用 `HistoricTaskInstance.getDeleteReason()` 来区分跳过与取消：

| `deleteReason` | 含义 | 结果 |
|---|---|---|
| `null` | 任务正常完成 | 检查 `approved` 变量 → 通过 / 驳回 |
| `MI_END` | 被多实例 completionCondition 跳过 | 自动通过 |
| `deleted` | 流程终止 / 已驳回 | 已取消 |

**注意**：不要依赖 `HistoricActivityInstance` 的父级查找。多行可能共享相同的 `ACT_ID_`（一行 deleteReason 为 `NULL`，另一行为 `MI_END`）。`putIfAbsent` 可能存入错误的行。应直接使用任务级别的 `deleteReason`。

### 3.2 omni:assignment 扩展元素

`omni:assignment` JSON 是候选人解析的**唯一配置入口**：

```xml
<userTask id="dept-leader-approve" flowable:assignee="${userId}">
  <extensionElements>
    <flowable:executionListener event="start"
        delegateExpression="${scopedRoleAssignmentListener}" />
    <omni:assignment>{
      "roleCode": "DEPT_LEADER",
      "anchorType": "PARENT",
      "anchorParams": {},
      "scopeMode": "SAME_UNIT",
      "fallbackStrategy": "ERROR",
      "approvalMode": "ANY"
    }</omni:assignment>
  </extensionElements>
  <multiInstanceLoopCharacteristics isSequential="false"
      flowable:collection="candidateUserIds"
      flowable:elementVariable="userId">
    <completionCondition>${rejectedCount > 0 || approvedCount >= requiredApprovals}</completionCondition>
  </multiInstanceLoopCharacteristics>
</userTask>
```

**字段说明**：

| 字段 | 取值 | 描述 |
|---|---|---|
| `roleCode` | 任意角色编码（如 `TEAM_LEADER`、`DEPT_LEADER`） | 要解析的目标角色 |
| `anchorType` | `START_USER_PRIMARY_UNIT`、`PARENT`、`ABSOLUTE_UNIT`、`PARENT_BY_TYPE`、`CHILD_BY_CODE`、`SIBLING_BY_CODE`、`PARENT_CHILDREN`、`DEPT_BY_CODE`、`CHILD_UNIT`、`SIBLING_UNIT` | 如何定位锚点组织单元 |
| `anchorParams` | JSON 对象（如 `{"unitIds": [200]}`) | 锚点解析参数 |
| `scopeMode` | `SAME_UNIT`、`UNIT_AND_BELOW`、`CHILDREN_ONLY` | 候选人搜索范围 |
| `fallbackStrategy` | `ERROR`、`ASSIGN_ADMIN`、`ESCALATE_PARENT` | 未找到候选人时的行为 |
| `approvalMode` | `ALL`（默认）、`ANY` | 多实例会签模式 |

### 3.3 审批模式

- **ALL**：所有候选人都必须审批。`requiredApprovals = candidateUserIds.size()`。当 `approvedCount >= requiredApprovals` 时流程推进。
- **ANY**：任意一人审批即可。`requiredApprovals = 1`。首个审批通过后流程推进；剩余任务自动完成，`deleteReason = MI_END`。

两种模式共享相同的 `completionCondition` 表达式：`${rejectedCount > 0 || approvedCount >= requiredApprovals}`。区别在于 `ScopedRoleAssignmentListener` 设置的 `requiredApprovals` 值不同。

**驳回快捷方式**：在两种模式下，任意一个驳回（`rejectedCount > 0`）都会立即触发驳回分支，跳过剩余审批人。

### 3.4 租户隔离

`omni-workflow` 中的 `MybatisPlusConfig` 注册了 `TenantLineInnerInterceptor`，其行为如下：
- 从 `TenantInfoHolder` 读取租户 ID（由 `TenantInfoFilter` 从 `X-Tenant-Id` 请求头设置）
- **排除** Flowable 内部表（`ACT_*` / `act_*` 前缀）不参与租户过滤

Flowable 表通过 Flowable 内置的 `tenantId` 机制实现租户隔离，而非 MyBatis-Plus 拦截。

### 3.5 XSS 集成

`omni-workflow` 通过 `XssConfigProviderImpl` 实现 `XssConfigProvider` SPI：
- 从 Redis 缓存读取 XSS 配置（`xss:enabled:{tenantId}`、`xss:rules:{tenantId}`）
- 缓存由 `omni-auth` 服务写入；工作流服务是**只读消费者**
- 缓存未命中时，返回 `enabled = false`（失败开放策略）

### 3.6 候选人解析组件

| 组件 | Bean 名称 | 触发时机 |
|---|---|---|
| `ScopedRoleAssignmentListener` | `scopedRoleAssignmentListener` | UserTask `start` 事件上的 ExecutionListener |
| `CandidateResolverDelegate` | `candidateResolverDelegate` | UserTask 之前 ServiceTask 中的 JavaDelegate |
| `CandidateResolverBean` | `candidateResolver` | UEL 表达式或离线预解析 |

`CandidateResolverBean` 暴露 `resolveCandidates(processDefinitionId, activityId, startUserId, tenantId)` 供离线使用（例如 `getProgress()` 需要显示谁*将会*审批一个待办任务）。

### 3.7 发布锁定

`publishModel()` 使用 `SELECT FOR UPDATE` 对 `wf_process_model` 加悲观锁，以防止同一模型的并发部署。这很关键，因为 Flowable 部署不是原子操作——涉及多个引擎 API 调用。

---

## 4. 扩展指南

### 4.1 添加新的审批流程类型

1. 设计 BPMN XML，在每个 UserTask 上配置 `<omni:assignment>`
2. 使用 `BpmnXmlValidator` 校验（强制检查必需的扩展）
3. 通过 API 创建模型：`POST /api/workflow/model`
4. 保存 BPMN XML：`PUT /api/workflow/model/{id}/draft`
5. 发布：`POST /api/workflow/model/{id}/publish`

无需修改代码——框架通过 BPMN XML + `omni:assignment` 配置实现数据驱动。

### 4.2 添加新的锚点类型

1. 在 `ScopedRoleAssignmentListener` 的解析逻辑中添加新的锚点类型字符串
2. 实现组织单元查询（例如按特定条件查询 `sys_org_unit`）
3. 如需校验，将锚点类型添加到 `BpmnXmlValidator` 的已知值中
4. 更新前端 `UserTaskPanel.vue`，在属性面板中展示新的锚点类型

### 4.3 添加新的降级策略

1. 在 `ScopedRoleAssignmentListener` 中添加策略常量
2. 实现降级行为（例如 `ASSIGN_ADMIN` → 查询管理员用户，`ESCALATE_PARENT` → 查找上级单元候选人）
3. 更新 `omni:assignment` JSON Schema 校验

### 4.4 自定义通知服务

实现 `omni-common-workflow` 中的 `WorkflowNotificationService` 接口：

```java
@Service
public class MyNotificationService implements WorkflowNotificationService {
    @Override
    public void notifyPendingTask(String assigneeId, String taskId, String title) { ... }

    @Override
    public void clearPendingTask(String taskId) { ... }
}
```

默认的 `NoOpNotificationService`（由 `FlowableAutoConfiguration` 注册）会通过 `@ConditionalOnMissingBean` 被你的实现替换。

### 4.5 抄送（CC）通知

在 BPMN 设计器中添加一个 `ServiceTask` 节点，使用 `ccNotifyDelegate` 委托表达式。配置 `<omni:cc>` 扩展元素指定目标用户 ID 或基于角色的解析。`CcNotifyDelegate` 在运行时创建 `wf_cc_record` 记录。

---

## 5. 技术选型思考：为什么选择 Flowable 7.x

| 考量 | Flowable | Camunda | Activiti |
|------|---------|---------|----------|
| **开源许可** | Apache 2.0（商业友好） | 商业版需许可（社区版 MIT） | Apache 2.0 |
| **Spring Boot 集成** | 原生 Spring Boot Starter，自动配置 | 需额外配置 Spring Boot Starter | 已停止维护（Flowable 是其分叉） |
| **多实例支持** | 原生 MI（Multi-Instance）支持，completionCondition 灵活 | 类似功能 | 基础 MI 支持 |
| **CMMN/DMN** | 支持 BPMN + CMMN + DMN | 支持 BPMN + DMN（CMMN 商业版） | 仅 BPMN |
| **社区活跃度** | 活跃（GitHub 8k+ stars） | 活跃（商业支持） | 已停止维护 |
| **版本 7.x** | Jakarta EE 兼容，Spring Boot 3/4 支持 | 版本 8 架构变动大 | 无新版 |

**结论**：Flowable 7.x 在开源许可、Spring Boot 原生集成、多实例支持方面优势明显，是 Omni-Stack 工作流引擎的最佳选择。

## 6. BPMN 建模最佳实践

### 命名约定

| 元素 | 命名规则 | 示例 |
|------|---------|------|
| Process ID | 与 `model_key` 一致 | `leave-request`, `expense-approval` |
| UserTask ID | kebab-case，描述角色+动作 | `dept-leader-approve`, `hr-review` |
| SequenceFlow ID | `flow-{source}-{target}` | `flow-start-submit` |
| Gateway ID | `{type}-gw-{purpose}` | `exclusive-gw-amount`, `parallel-gw-notify` |

### 建模原则

1. **每个 UserTask 必须配置 `<omni:assignment>`**：动态候选人解析，禁止硬编码 `assignee`
2. **ExclusiveGateway 必须设置 default flow**：无条件分支作为兑底，避免流程死锁
3. **多实例会签使用统一的 completionCondition**：`${rejectedCount > 0 \|\| approvedCount >= requiredApprovals}`
4. **CC 通知使用 ServiceTask + `ccNotifyDelegate`**：非阻塞，不影响主流程
5. **模型发布前必须通过 `BpmnXmlValidator`**：确保 XML 合法性和扩展元素完整性

### 流程设计器前端架构

```
bpmn-js Modeler (开源 BPMN 2.0 建模工具)
    │
    ├── useBpmnModeler.ts      — Modeler 创建/销毁生命周期
    ├── useBpmnExtension.ts    — omni:assignment 等扩展元素读写
    ├── bpmnContextPadI18n.ts  — 上下文菜单国际化
    └── bpmnContextPadProvider.ts — 自定义上下文菜单项

属性面板 (panels/)
    ├── UserTaskPanel.vue      — 角色解析配置（roleCode, anchorType, scopeMode）
    ├── ServiceTaskPanel.vue   — CC 通知配置
    └── GatewayPanel.vue       — 网关条件配置
```

## 7. 故障排查指南

| 问题 | 可能原因 | 排查方法 |
|------|---------|----------|
| **模型发布失败** | BPMN XML 校验未通过 | 调用 `POST /api/workflow/model/{id}/validate` 获取具体错误信息 |
| **候选人解析失败** | `omni:assignment` 配置错误 | 检查 `roleCode`、`anchorType`、`scopeMode` 值是否合法；查看服务日志中的异常信息 |
| **流程实例未启动** | 模型未发布或版本已归档 | 检查 `wf_process_model_version` 表中是否有 `status=PUBLISHED` 的版本 |
| **多实例任务未跳过** | completionCondition 未触发 | 检查 `approvedCount`、`rejectedCount`、`requiredApprovals` 变量值 |
| **deleteReason 显示错误** | MI_END vs deleted 混淆 | 参考 §3.1 MI DeleteReason 表格，`MI_END` = 自动完成，`deleted` = 流程终止 |
| **租户隔离失效** | TenantInfoHolder 未设置 | 确认 Gateway 已注入 `X-Tenant-Id` 请求头；检查 `TenantInfoFilter` 是否正常执行 |
| **BPMN 设计器无法加载** | bpmn-js 版本不兼容 | 确认 `bpmn-js` 版本为 18.x；检查浏览器控制台错误 |

## 8. 管理端界面截图（四语言）

正式图片由文档专用 Playwright 用例 `omni-frontend/e2e-docs/flows/management.flows.spec.ts` 在真实运行栈上生成，按语言分目录存放，不复用其他语言图片、不使用占位图或模拟响应。

- 前置条件：本地 Compose 全栈运行，前端 `127.0.0.1:3000`；`omni-workflow` 健康；库中已有真实流程模型与实例（采集时为 8 个模型/版本、23 条实例）。
- 操作者：`admin`（`SUPER_ADMIN`，具备工作流菜单权限）。
- 操作：登录后依次进入「流程定义」「流程实例」「统计看板」页面。
- 预期状态：页面标题与列标签按当前语言渲染；流程实例列表展示流程标题、流程 Key、业务主键、发起人、状态与发起时间，并提供「流转进度」与「审批记录」入口。
- 令牌：由 `E2eTokenFixture` 在测试进程内签发短期 JWT（TTL 1200 秒），收尾即销毁，不写入文档、日志或版本库。
- 本组全部为**只读采集**：不创建、不修改、不删除任何流程数据，因此不需写入开关，也无数据收尾。

内容说明：当前环境中的流程实例均由历次端到端验证产生，标题带有测试标识（如 `E2ESQ`）。`wf_process_instance_ext` 与 Flowable `ACT_HI_*` 属引擎管理的审计历史且无软删列，不得用 SQL 硬删，因此图片如实保留真实标题，不以造数据或裁剪方式美化。

| 页面 | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| 流程定义（publish） | ![流程定义（简体中文）](images/zh-CN/workflow-definitions.png) | ![流程定义（英文）](images/en-US/workflow-definitions.png) | ![流程定义（日文）](images/ja-JP/workflow-definitions.png) | ![流程定义（韩文）](images/ko-KR/workflow-definitions.png) |
| 流程实例跟踪（instance-tracking） | ![流程实例（简体中文）](images/zh-CN/workflow-instances.png) | ![流程实例（英文）](images/en-US/workflow-instances.png) | ![流程实例（日文）](images/ja-JP/workflow-instances.png) | ![流程实例（韩文）](images/ko-KR/workflow-instances.png) |
| 统计看板（汇总视图） | ![统计看板（简体中文）](images/zh-CN/workflow-stats.png) | ![统计看板（英文）](images/en-US/workflow-stats.png) | ![统计看板（日文）](images/ja-JP/workflow-stats.png) | ![统计看板（韩文）](images/ko-KR/workflow-stats.png) |
