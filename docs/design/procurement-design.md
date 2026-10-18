# 采购执行模块架构与实现基线

> 本页保留 2026-07-27 的原设计日期、分期和重要决策，同时记录当前源码约束；中文为事实源。原验收结论只适用于当时范围，不能替代当前运行证据或发布验收。四语复核按授权 AI_REVIEW/HUMAN_REVIEW 分别记录，见 [ADR 0003](../adr/0003-authorized-ai-translation-review.md)。

> 原记录状态（2026-07-27）：MVP 已实现并完成验证
> 项目：Omni-Stack
> 日期：2026-07-27
> 目标：说明 omni-procurement MVP 的架构、跨服务契约和实施边界；实现入口为 `omni-backend/omni-procurement` 与 `omni-frontend/src/views/procurement`。

设计依据：`README.md`，以及 `docs/` 中 architecture、api-contract、backend-patterns、frontend-patterns、core-flows、scheduling、workflow、mq-reliability、docker-deployment 全部主题文档；同时参照 `docs/design/srm-design.md` 的 SRM 供应商模型。

操作、账号和截图见[采购 Wiki](../guides/procurement-flow.md)；扩展和 Docker 工具入口见[开发 Wiki](../guides/scaffold-development.md)。当前源码按[源码索引](../reference/source-map.md)导航。

[中文](procurement-design.md) · [English](procurement-design.en.md) · [日本語](procurement-design.jp.md) · [한국어](procurement-design.kr.md)

## 1. 设计结论

采购执行应建设为独立 Servlet 微服务，与供应商管理（`omni-srm`）和资产管理（`omni-asset`）分离。SRM 是地基，Procurement 依赖 SRM 的供应商数据，Asset 依赖 Procurement 的采购来源数据。

| 项目 | 决策 |
|---|---|
| Maven 模块 / 服务名 | `omni-procurement` |
| 本地端口 / 管理端口 | `8106` / `19906` |
| XXL-JOB 执行器 | `omni-procurement` / `9906`（启用定时提醒时） |
| 数据库 | `omni_procurement` |
| Gateway | `/api/procurement/**` → `lb://omni-procurement`，不使用 `StripPrefix` |
| Redis | DB 0，共享 Auth XSS 缓存键；未证实独立 proc: 业务缓存前缀 |
| 前端 | 继续使用 `omni-frontend`，新增 `views/procurement/**` |

Procurement MVP 覆盖采购执行闭环：

> 物料目录 → 请购申请 → 审批 → 询价/比价 → 定点 → 采购订单 → 收货确认。

三单匹配（PO + 收货单 + 发票）和付款不进入 MVP，留给 ERP 或财务系统。合同管理在 Phase 2 引入。

## 2. 产品范围

### 2.1 用户与目标

| 用户 | 核心诉求 |
|---|---|
| 需求部门员工 | 提交采购申请，跟踪请购进度 |
| 采购员 | 管理询价、比价、下单、跟踪订单进度 |
| 采购经理 | 审批请购、管理采购流程、查看采购统计 |
| 部门经理/高管 | 审批请购（按金额阈值），查看采购支出 |
| 供应商 | 通过门户查看询价单并提交报价（复用 SRM 门户） |

MVP 应能回答：有多少待审批请购单；某个请购单审批到哪一步了；哪些询价单等待供应商报价；某个采购订单的收货状态；按品类/供应商/部门的采购支出统计。

### 2.2 分期

| 阶段 | 能力 |
|---|---|
| MVP | 物料目录、请购申请、审批流（品类+金额多维分支）、询价/比价、采购订单、收货确认、采购概览 |
| Phase 2 | 合同管理、反向拍卖、采购模板、框架协议、三单匹配、供应商绩效联动 |
| Phase 3 | 采购分析（价格趋势、供应商集中度）、预算管控、自动补货建议 |

## 3. 系统边界

| 组件 | 权威职责 | Procurement 的使用方式 |
|---|---|---|
| `omni-auth` | 租户、用户、组织、角色、权限、数据范围、XSS 配置 | 内部 OpenFeign；只存用户/组织 ID |
| `omni-srm` | 供应商数据（准入、分级、风险） | 内部 OpenFeign 查询供应商；供应商门户报价通过 SRM 服务 |
| `omni-base` | 字典、操作日志 | 操作日志汇聚 |
| `omni-workflow` | BPMN、实例、审批及唯一 Flowable 运行时 | 模型查询/预览、幂等启动、任务资格校验；消费完成事件，不提供 Procurement 直接终止流程能力 |
| `omni-procurement` | 物料、请购、询价、采购订单、收货 | 唯一业务写入方 |
| `omni-asset` | 资产管理 | 收货验收通过后，通过 Outbox 事件和受控历史补偿创建资产卡片 |
| XXL-JOB | 触发批量扫描 | 订单超期提醒（Phase 2） |
| RocketMQ | 异步运输 | 至少一次；消费者必须幂等 |

```mermaid
flowchart LR
    UI["omni-frontend"] --> GW["omni-gateway<br/>JWT 验证/身份头"]
    GW --> PROC["omni-procurement :8106"]
    GW --> AUTH["omni-auth :8100"]
    PROC -->|"OpenFeign + X-Internal-Token"| AUTH
    PROC -->|"OpenFeign + X-Internal-Token"| SRM["omni-srm :8105"]
    PROC -->|"Feign"| WF["omni-workflow :8103"]
    PROC --> DB[("omni_procurement")]
    PROC --> R[("Redis DB 0")]
    PROC --> O["sys_mq_message"]
    O -->|"mqRelayHandler"| MQ["RocketMQ"]
    WF -->|"workflow.process.completed.v1 / Outbox"| MQ
    MQ -->|"workflow.process.completed.v1"| PROC
    MQ -->|"合格收货事件"| ASSET["omni-asset :8107"]
    ASSET -->|"历史候选回扫"| PROC
```

当前基础依赖：`omni-common-core`、`omni-common`、`omni-common-mybatis`、`omni-common-redis`、`omni-common-operlog`、`omni-common-job`、`omni-common-mqlog`、`omni-common-service`；另有 Web、Validation、Security、AspectJ、OpenFeign、LoadBalancer、Nacos、RocketMQ Stream、Actuator、Lombok 等。

**Procurement 不依赖 `omni-common-workflow`，也不在本服务嵌入 Flowable。** `omni-workflow` 是独立微服务和 Flowable 唯一运行时；Procurement 通过内部 Feign 契约发起流程，通过可靠领域事件接收审批结果。跨服务 DTO 应位于纯契约模块或由 Feign 客户端本地定义，不能因此引入 Flowable Starter。

## 4. 领域与数据设计

### 4.1 聚合

| 聚合 | 表 | 职责 |
|---|---|---|
| ProcurementConfig | `proc_tenant_config`、`proc_approval_route` | 租户币种与品类/金额审批模型路由 |
| Material | `proc_material_category`、`proc_material` | 物料品类树、物料目录 |
| Requisition | `proc_requisition`、`proc_requisition_line` | 请购申请、明细行；审批任务和记录由 omni-workflow 权威管理 |
| RFQ | `proc_rfq`、`proc_rfq_line`、`proc_rfq_supplier` | 询价单、明细行、受邀供应商 |
| PurchaseOrder | `proc_purchase_order`、`proc_purchase_order_line` | 采购订单、明细行 |
| GoodsReceipt | `proc_goods_receipt`、`proc_goods_receipt_line` | 收货单、明细行 |
| Inbox | `proc_event_inbox` | Workflow 完成/报价事件的租户内幂等与原始意图记录 |

```mermaid
erDiagram
    PROC_MATERIAL_CATEGORY ||--o{ PROC_MATERIAL : 包含
    PROC_REQUISITION ||--o{ PROC_REQUISITION_LINE : 明细
    PROC_REQUISITION ||--o{ PROC_RFQ : 触发
    PROC_RFQ ||--o{ PROC_RFQ_LINE : 明细
    PROC_RFQ ||--o{ PROC_RFQ_SUPPLIER : 邀请
    PROC_RFQ ||--o| PROC_PURCHASE_ORDER : 定点
    PROC_PURCHASE_ORDER ||--o{ PROC_PURCHASE_ORDER_LINE : 明细
    PROC_PURCHASE_ORDER ||--o{ PROC_GOODS_RECEIPT : 收货
    PROC_GOODS_RECEIPT ||--o{ PROC_GOODS_RECEIPT_LINE : 明细
    PROC_RFQ_SUPPLIER }o--|| SRM_SUPPLIER : 引用
```

### 4.2 通用字段与规则

所有 `proc_*` 表保留 tenant_id。直接业务根有 version/deleted 和审计字段；范围映射按聚合决定：Requisition 使用 requester_user_id/requester_unit_id，RFQ、PurchaseOrder、GoodsReceipt 使用 owner_user_id/owner_unit_id。子行按父关系继承；Material/Category、审批路由、租户配置是租户共享，不能给所有表机械追加 owner 条件。

- 请购创建从当前有效 scope 取申请人/主组织，并保存相同的初始 owner；实际数据权限仍按 requester 列。
- RFQ 从当前 scope 取 owner，PO 继承 RFQ owner，GR 以当前收货人及主组织为 owner；不能以供应商门户账号或消息线程身份猜测归属。
- 编号来自数据库 ID，物料编码及业务编号在租户内唯一；SRM 只通过 supplier_id 逻辑引用，不建跨库外键。
- 数量/单价为 DECIMAL(19,6)，行/总金额为 DECIMAL(19,4)，HTTP JSON 使用十进制字符串，禁止 JavaScript number 金额运算；请购估价允许 0，报价和订单单价为正。
- 日期时间使用 yyyy-MM-dd HH:mm:ss；预计/实际交付日期为 LocalDate 日期字段，按实际 DTO 处理。
- 普通 PUT 不改生命周期、审批状态或 owner；既有业务变更先授权再按状态/版本/影响行数执行，租户共享配置、创建编号补全和账本不泛化为同一种命令。

### 4.3 主要表

`proc_tenant_config`

- `tenant_id/currency_code/initialized_time/version` 与审计字段，tenant 内唯一。

`proc_approval_route`

- `route_code/category_code/min_amount/max_amount/model_version_id/priority/status/version/deleted` 与审计字段。
- 精确品类优先于 `category_code='*'` 默认路由；区间语义为 `min_amount <= total_amount < max_amount`，max 为空表示无上限。
- 同品类活动金额区间不得重叠；提交请购时匹配零条或多条都返回 409，客户端不得传 modelVersionId。

`proc_material_category`

- `tenant_id/parent_id/category_code/category_name/sort/status/version/deleted` 与审计字段。
- 支持任意层级品类树（parent_id 为 0 表示顶级品类），物料只能关联叶子品类。
- MVP 在物料目录页提供品类树管理；`category_code` 创建后不可修改，更新和删除必须携带 `version` 并执行条件更新。

`proc_material`

- `tenant_id/category_id/material_code/material_name/specification/unit/asset_managed/status/version/deleted`。
- `specification` 为文本描述（MVP 不做结构化规格参数）。
- `unit` 为规范化的大写计量单位（如 EA、PCS、UNIT、SET、KG）。
- `asset_managed` 表示合格收货后是否按“每个单位一张资产卡片”进入 Asset；仅 `EA/PCS/UNIT/SET` 可启用，耗材、服务和 KG 等连续计量物料必须为 false。
- 索引：tenant + category_id/status、tenant + material_code（唯一）。

`proc_requisition`

- `requisition_no/title/requester_user_id/requester_unit_id/reason/primary_category_code/total_amount/currency_code`。
- `status`：DRAFT/SUBMITTED/APPROVING/APPROVED/REJECTED/CANCELLED。
- `approval_attempt/workflow_request_id/workflow_business_key/workflow_model_version_id/process_instance_id`：当前审批轮次及 Workflow 幂等快照；businessKey 固定为 `{requisitionId}:{approvalAttempt}`。
- `workflow_start_status`：NOT_STARTED/PENDING/FAILED/STARTED；与业务 status 分离，失败时业务状态仍为 SUBMITTED。
- `approved_time/workflow_completed_time`：审批完成时间快照；审批意见仍以 Workflow 为权威，Procurement 不复制或伪造最终意见。
- `owner_user_id/owner_unit_id/version/deleted` 和审计字段。

`proc_requisition_line`

- `line_no/requisition_id/material_id/material_code/material_name/category_code/unit/quantity/estimated_unit_price/estimated_total_price/remark`；物料编码、名称、品类和单位均为提交时快照。
- 请购单总金额 = SUM(line.estimated_total_price)。
- MVP 强制一张请购的所有行属于同一品类；跨品类需求拆成多张请购，避免单值审批路由语义不确定。

`proc_rfq`

- `rfq_no/requisition_id/title/quotation_deadline/currency_code/status/sent_time/owner_user_id/owner_unit_id/version/deleted`。
- `status`：DRAFT/SENT/CLOSED/AWARDED/CANCELLED。
- `awarded_supplier_id/awarded_quotation_id/awarded_quotation_version/awarded_time`：定点与报价版本快照。
- 关联可见且 APPROVED 的请购，复制其全部明细；同一请购可被多个 RFQ 引用，当前创建不实现按行/品类拆分。

`proc_rfq_line`

- `rfq_id/line_no/material_id/material_code/material_name/category_code/unit/quantity/remark/version/deleted`，全部为请购行快照。

`proc_rfq_supplier`

- `rfq_id/supplier_id/supplier_name_snapshot/invited_time/quotation_id/quotation_version/quotation_request_id/quotation_time/status/version/deleted`。
- `status`：INVITED/QUOTED/EXPIRED/AWARDED/REJECTED。`AWARDED/REJECTED` 为定点后的只读历史终态，不能继续报价。
- 新的报价写入要求 RFQ status=SENT、邀请 status IN (INVITED, QUOTED) 且 deadline 严格晚于当前时间；成功历史重放的不同校验顺序见 SRM 报价契约。
- `quotation_id` 逻辑关联 SRM 的 `srm_quotation`（不建跨库外键）；`supplier_name_snapshot` 仅用于历史展示，不作为当前供应商状态或权限依据。

`proc_purchase_order`

- `po_no/rfq_id/supplier_id/quotation_id/quotation_version/title/total_amount/currency_code`。
- 中标报价的供应商名称、逐行单价、交期等直接复制到 PO/PO Line 形成不可变业务快照；quotation_id/version 仅用于追溯。
- `status`：DRAFT/SENT/CONFIRMED/PARTIAL_RECEIVED/RECEIVED/CLOSED/CANCELLED。
- `order_time/expected_delivery_date/actual_delivery_date`。
- `delivery_address/contact_name/contact_phone`。
- `owner_user_id/owner_unit_id/version/deleted` 和审计字段。

`proc_purchase_order_line`

- `po_id/material_id/material_name/unit/quantity/unit_price/total_price/remark`。

`proc_goods_receipt`

- `gr_no/po_id/receiver_user_id/receive_time/remark/status/owner_user_id/owner_unit_id/version/deleted`。
- `status`：DRAFT/CONFIRMED。
- 确认后触发 Outbox 事件通知 Asset 创建资产卡片。

`proc_goods_receipt_line`

- `goods_receipt_id/po_line_id/material_id/material_name/unit/ordered_quantity/received_quantity/quality_status/remark`。
- `quality_status`：PASS/FAIL/PENDING。
- 收货数量可小于等于订单数量（分批收货）。

## 5. 状态机与核心流程

### 5.1 请购申请（Requisition）

```mermaid
stateDiagram-v2
    [*] --> DRAFT: 创建请购
    DRAFT --> SUBMITTED: 提交
    SUBMITTED --> APPROVING: 确认 STARTED
    APPROVING --> APPROVED: 完成：通过
    APPROVING --> REJECTED: 完成：驳回
    APPROVING --> CANCELLED: 完成：取消
    DRAFT --> CANCELLED: 取消
    SUBMITTED --> CANCELLED: 仅 FAILED 取消
    REJECTED --> DRAFT: 编辑回草稿
    APPROVED --> [*]
    CANCELLED --> [*]
```

提交先在独立本地事务内重算物料、品类和金额，解析审批路由并提交 SUBMITTED/PENDING 快照及 Outbox，再调用 Workflow。只有确认启动结果才推进 APPROVING；SUBMITTED 的取消仅允许 workflow_start_status=FAILED，APPROVING 的 CANCELLED 来自匹配的 Workflow 完成结果，不能由采购 cancel 命令直接终止。

### 5.2 审批流设计（与 omni-workflow 集成）

下图保留原设计的品类/金额分支示例，不代表当前每个租户实际部署了这些 BPMN、岗位或金额阈值。当前提交先由服务端审批规则选择已发布模型，再按该模型运行。

```mermaid
flowchart TD
    A["请购提交"] --> B{"按品类分支"}
    B -->|"IT设备"| C{"IT金额"}
    B -->|"办公用品"| D{"行政金额"}
    B -->|"原材料"| E{"采购金额"}
    B -->|"其他"| F{"通用金额"}
    C -->|"<50000"| C1["部门经理"]
    C -->|">=50000"| C2["部门经理 → CTO"]
    D -->|"<10000"| D1["行政主管"]
    D -->|">=10000"| D2["行政主管 → 行政总监"]
    E -->|"<100000"| E1["采购经理"]
    E -->|">=100000"| E2["采购经理 → 供应链VP"]
    F -->|"<30000"| F1["需求部门负责人"]
    F -->|">=30000"| F2["需求部门负责人 → CFO"]
```

实现方式：
- 在 `omni-workflow` 中为每个品类创建一个 BPMN 流程模型（如 `procurement_approval_it`、`procurement_approval_office` 等）。
- 或者使用一个通用 BPMN，通过 Exclusive Gateway 的两级嵌套（品类 → 金额）实现路由。
- Procurement 服务在请购提交时，根据请购的唯一品类和服务端重算总金额，从 `proc_approval_route` 选择已发布的 modelVersionId，通过 Workflow 内部 API 启动流程；客户端不能指定模型版本。
- 审批人通过 Flowable 的 `ScopedRoleAssignmentListener` 动态解析（利用已有的组织架构+角色体系）。

跨服务启动必须可重试：每次从 DRAFT 提交时先将 `approvalAttempt + 1`，生成并持久化 requestId 与 `businessKey={requisitionId}:{approvalAttempt}`；Procurement 以 `tenantId + businessType(PROCUREMENT_REQUISITION) + businessKey` 作为当前轮次幂等键。先以本地事务更新为 `status=SUBMITTED, workflow_start_status=PENDING`，事务提交后调用 Workflow；成功后写入 processInstanceId，设置 workflow_start_status=STARTED 并推进到 APPROVING。响应丢失或调用失败时尝试标记 FAILED，实际状态须重读；retry 必须复用已保存的 requestId/businessKey/modelVersionId 且不得增加 attempt。REJECTED 修改成功后回到 DRAFT，再提交才开启新 attempt，避免 Workflow 永久业务键唯一约束重放旧流程。

**MVP 约束**：单张请购仅允许一个品类；审批路由支持精确品类与 `*` 默认路由，BPMN 可按金额分支。后续需要跨品类请购时再定义明确的主品类或多流程策略，禁止当前版本隐式取第一行。

模型/路由或物料校验在 prepareSubmit 的本地提交之前失败时会回滚，不能统一说成 SUBMITTED/FAILED。提交后启动调用/响应不确定时，Coordinator 尝试以 REQUIRES_NEW 标记 FAILED；标记可能失败或不命中，必须重读真实状态/版本。FAILED 不证明远程流程未创建；retry-start 只对 SUBMITTED/FAILED 复用原请求、模型、金额、申请人和轮次，不重选路由。

### 5.2.1 请购审批规则管理

管理界面以业务名称、适用品类、金额范围和流程名称为主，不要求业务人员输入技术编码、模型版本 ID
或 priority。`route_code` 由服务端生成 `APR-{ULID}`，仅在高级信息中只读展示；`route_name` 是业务必填名。
可绑定流程固定为 Workflow `category=purchase` 的当前已发布版本，而运行实例继续使用
`businessType=PROCUREMENT_REQUISITION`，两类标识不能混用。

列表按当前页去重 `modelVersionId` 后使用不超过 200 条的批量内部接口补充流程元数据，禁止逐行调用。
Workflow 不可用时只读列表保留本地规则并标记 `UNAVAILABLE`；创建、更新和请购提交仍失败关闭。
匹配测试调用与请购提交相同的 `ApprovalRouteResolver.evaluate`，明确返回唯一命中、无匹配和历史脏数据
多匹配。覆盖分析对每个有效品类从 0 到无穷切分半开区间，先应用精确规则，再以默认规则补缺；
停用和删除前在内存排除目标规则复用同一算法生成影响提示。

### 5.3 询价/比价（RFQ）

```mermaid
sequenceDiagram
    participant BUYER as 采购员
    participant S as RfqService
    participant SRM as omni-srm
    participant DB as omni_procurement
    participant SUP as 供应商门户
    BUYER->>S: POST /api/procurement/rfq (CreateRequest)
    S->>SRM: 验证 APPROVED 供应商
    S->>DB: 保存 RFQ/明细/邀请
    BUYER->>S: POST /api/procurement/rfq/{id}/send
    S->>SRM: 重新验证供应商
    S->>DB: Outbox procurement.rfq.sent.v1
    SUP->>SRM: GET /api/srm/portal/quotation/invitations
    SRM->>S: GET /api/internal/procurement/rfq/invitations
    SUP->>SRM: GET /api/srm/portal/quotation/invitations/{rfqId}
    SRM->>S: GET /api/internal/procurement/rfq/{rfqId}/invitation
    SUP->>SRM: POST /api/srm/portal/quotation (requestId/rfqId/version/validUntil/lines)
    SRM->>SRM: 保存报价/账本/Outbox
    SRM-->>S: MQ srm.quotation.submitted.v1
    S->>DB: 按事件意图/版本更新邀请
    BUYER->>S: POST /api/procurement/rfq/{id}/award (AwardRequest)
    S->>SRM: 读取当前有效报价
    S->>DB: 生成或核对 PO 快照
    S->>DB: 更新 RFQ/邀请及 Outbox
    S-->>BUYER: R<AwardResult> (rfq/purchaseOrder)
```

比价方式：MVP 提供简单的比价视图——通过 `GET /api/internal/quotation/batch` 列出受邀供应商的有效报价（单价、总价、交期），采购员手动选择定点供应商。不做自动评标算法。定点事务必须保存 quotationId、报价版本以及金额/交期不可变快照，后续 SRM 报价变更不得改变既有定点和采购订单。

定点使用 RfqRequests.AwardRequest：rfqVersion、quotationId、quotationVersion、title、可选 expectedDeliveryDate，以及 deliveryAddress/contactName/contactPhone；supplierId 从所选有效报价取得，不能只按供应商 ID 定点。返回 `R<AwardResult>`，包含 rfq 与 purchaseOrder。

### 5.4 采购订单（Purchase Order）

```mermaid
stateDiagram-v2
    [*] --> DRAFT: 定点生成
    DRAFT --> SENT: 发送
    SENT --> CONFIRMED: 管理端确认
    CONFIRMED --> PARTIAL_RECEIVED: 部分收货
    CONFIRMED --> RECEIVED: 全部收货
    PARTIAL_RECEIVED --> RECEIVED: 全部收货
    DRAFT --> CANCELLED: 取消
    SENT --> CANCELLED: 取消
    CONFIRMED --> CANCELLED: 取消
```

订单只由 RFQ 定点生成。确认是受采购管理权限和行范围保护的命令，当前没有独立供应商门户确认 API。DRAFT/SENT/CONFIRMED 可按 cancel 权限、状态和版本取消，未接入额外取消审批。CLOSED 为已有状态值，当前 Controller 无 close 入口；首次完整收货可直接从 CONFIRMED 到 RECEIVED。

### 5.5 收货确认（Goods Receipt）

```mermaid
sequenceDiagram
    participant R as 收货人
    participant S as GoodsReceiptService
    participant DB as omni_procurement
    participant O as Outbox
    R->>S: POST /api/procurement/goods-receipt (poId/lines)
    S->>DB: 保存收货草稿 (DRAFT)
    R->>S: POST /api/procurement/goods-receipt/{id}/confirm (version)
    S->>DB: GR/PO FOR UPDATE + tenant/scope
    S->>DB: 累计数量校验
    S->>DB: 同事务确认/版本更新
    S->>O: procurement.goods-receipt.confirmed.v1 (同事务写 Outbox)
    S-->>R: R<GoodsReceiptViews.Detail>
```

创建 DRAFT 不占用订单已收数量，也不发送事件。确认时必须锁定 PO，重新基于所有 CONFIRMED 收货行累计校验，防止多个草稿并发确认导致超收。确认成功后写 Outbox 事件 `procurement.goods-receipt.confirmed.v1`；Asset 尚未建设时不阻塞 Procurement，但不能假设历史事件会一直滞留在 Outbox/Broker，Asset 上线必须执行下文定义的历史补偿回扫。

只有 `quality_status=PASS`、`asset_managed=true` 且 receivedQuantity 为正整数的收货行可以资产化。PENDING/FAIL 行、耗材、服务和连续计量物料不会创建资产。PENDING 后续通过 `POST /goods-receipt/{id}/quality-result` 变为 PASS 时，Procurement 发布 `procurement.goods-receipt.quality-passed.v1`，仅携带本次新通过的行；禁止修改或复用已经发送的 confirmed 事件。

质检补录仅允许已确认收货单的 PENDING 行变为 PASS/FAIL。只对本次新通过且符合资产化条件的行发送 quality-passed；重复修改终态行返回冲突，不冒称成功重放。confirmed 事件只含当时符合条件的资产候选，候选可以为空。累计校验按同租户全部已确认收货统计，专用汇总忽略收货人 owner 范围以免漏计；收货单/PO 用 FOR UPDATE 与版本更新，明细查询本身没有 FOR UPDATE。

## 6. 租户、RBAC 与数据权限

### 6.1 信任链

与 SRM 一致：Gateway JWT → `GatewayPreAuthenticationFilter` → `ServiceIdentityFilter`（Tenant/用户身份校验）→ `@PreAuthorize` → `@ServiceDataScope` → MyBatis DataPermission → `ProcRecordAccessGuard`。

### 6.2 权限树与角色

菜单：`procurement`（DIRECTORY）以及 `procurement:overview`、`procurement:material`、`procurement:approval-route`、`procurement:requisition`、`procurement:rfq`、`procurement:purchase-order`、`procurement:goods-receipt`（MENU）。只为已经交付的页面种 MENU，避免动态侧栏出现死链接。

API 权限：

- `procurement:overview:list`
- `procurement:material:list/create/update/delete`
- `procurement:approval-route:list/create/update/delete`
- `procurement:requisition:list/create/update/delete/submit/approve/cancel`
- `procurement:rfq:list/create/update/delete/send/award/cancel`
- `procurement:purchase-order:list/update/delete/send/confirm/cancel`（采购订单仅由 RFQ 定点生成）
- `procurement:goods-receipt:list/create/confirm`

| 角色 | dataScope | 能力 |
|---|---|---|
| `PROCUREMENT_MANAGER` | DEPT_AND_BELOW | 部门及下级、审批、统计 |
| `PROCUREMENT_STAFF` | SELF | 自己负责的请购、询价、订单及 SELF 范围概览 |
| `EMPLOYEE` | SELF | 提交和查看自己的请购 |
| `TEAM_LEADER` | DEPT | Workflow 分配给本人的审批及部门审批业务视图 |
| `DEPT_LEADER` | DEPT_AND_BELOW | Workflow 分配给本人的审批及部门树审批业务视图 |
| `SUPER_ADMIN` | ALL | 所有功能，采购数据仍限当前租户 |

默认 USER 不授予采购权限。

### 6.3 Procurement 上下文与 SQL 拦截

通用请求身份、DataScope 上下文与切面由 `omni-common-service` 提供：`ServiceIdentityContext`、`ServiceDataScopeContext`、`@ServiceDataScope` 和 `ServicePersistenceAutoConfiguration`。采购模块只保留领域差异：`ProcTenantTablePolicy`、`ProcDataPermissionHandler` 与 `ProcRecordAccessGuard`。

拦截器顺序固定：`TenantLineInnerInterceptor → DataPermissionInterceptor → OptimisticLockerInnerInterceptor → PaginationInnerInterceptor`。`ProcTenantTablePolicy` 只对 `proc_*` 表启用 TenantLine；`sys_mq_message` 必须排除，保证跨租户 Outbox Relay 可以扫描待发送消息。领域表的数据权限映射仍由 `ProcDataPermissionHandler` 按下表定义。

| dataScope | 条件 |
|---|---|
| SELF | `requester_user_id = currentUserId` 或 `owner_user_id = currentUserId` |
| DEPT | `requester_unit_id = primaryUnitId` |
| DEPT_AND_BELOW / CUSTOM | `requester_unit_id IN accessibleUnitIds` |
| TENANT / ALL | 不加 owner 条件，TenantLine 始终保留 |

上表仅描述作用域语义，实际 SQL 必须按表映射，禁止把 `requester_user_id OR owner_user_id` 机械追加到所有 `proc_*` 表：

| 资源/表 | SELF 列 | DEPT/CUSTOM 列 | 子资源约束 |
|---|---|---|---|
| Requisition | `requester_user_id` | `requester_unit_id` | Line 通过同 tenant 的 requisition_id 继承 |
| RFQ | `owner_user_id` | `owner_unit_id` | Line/Supplier 通过同 tenant 的 rfq_id 继承 |
| PurchaseOrder | `owner_user_id` | `owner_unit_id` | Line 通过同 tenant 的 po_id 继承 |
| GoodsReceipt | `owner_user_id`（收货负责人） | `owner_unit_id` | Line 通过同 tenant 的 goods_receipt_id 继承 |
| Material/Category | 无 SELF 私有语义 | 由功能权限控制，租户内共享 | 始终保留 TenantLine |
| Overview | 按当前 permissionCode 映射到对应业务表 owner/requester 列 | 同左 | 聚合 SQL 必须应用与列表相同范围 |

### 6.4 审批流权限

请购任务通过 Workflow 完成，读取专用 approval-view 要有 procurement:requisition:approve。服务先读取同租户流程身份，要求 APPROVING/STARTED、完整 businessKey（requisitionId:attempt）与 processInstanceId，再向 Workflow 校验当前用户、taskId、businessType、完整业务键及返回的实例 ID。校验成功后临时使用 TENANT 范围读只读业务 VO，并在 finally 恢复原 scope；普通详情继续使用 requester 范围，不能复用该例外。完成任务还需 workflow:approval:complete 和真实任务资格。

## 7. API 设计

### 7.1 通用契约

与 SRM 一致：`R<T>`、`R<PageResult<T>>`、`page=1`、`size=10`、`size <= 100`。

### 7.2 端点

以下按真实 Controller 列出 50 条外部方法/路径，均相对 /api/procurement，每项完整列出；另有 3 条内部只读路径。审批视图需 taskId 查询参数，删除等 version 参数按实际 DTO/query 契约。

| Controller | 方法与路径 |
|---|---|
| 审批规则 (ApprovalRouteController) | `GET /approval-route/list` · `GET /approval-route/workflow-options` · `POST /approval-route/match-preview` · `GET /approval-route/coverage` · `GET /approval-route/impact` · `POST /approval-route` · `PUT /approval-route/{id}` · `DELETE /approval-route/{id}` |
| 收货 (GoodsReceiptController) | `GET /goods-receipt/list` · `GET /goods-receipt/{id}` · `POST /goods-receipt` · `POST /goods-receipt/{id}/confirm` · `POST /goods-receipt/{id}/quality-result` |
| 物料与品类 (MaterialController) | `GET /material/category/list` · `POST /material/category` · `PUT /material/category/{id}` · `DELETE /material/category/{id}` · `GET /material/list` · `GET /material/{id}` · `POST /material` · `PUT /material/{id}` · `DELETE /material/{id}` |
| 概览 (OverviewController) | `GET /overview/summary` · `GET /overview/spend-analysis` |
| 采购订单 (PurchaseOrderController) | `GET /purchase-order/list` · `GET /purchase-order/{id}` · `PUT /purchase-order/{id}` · `DELETE /purchase-order/{id}` · `POST /purchase-order/{id}/send` · `POST /purchase-order/{id}/confirm` · `POST /purchase-order/{id}/cancel` |
| 请购 (RequisitionController) | `GET /requisition/list` · `GET /requisition/{id}` · `GET /requisition/{id}/approval-view` · `POST /requisition` · `PUT /requisition/{id}` · `DELETE /requisition/{id}` · `POST /requisition/{id}/submit` · `POST /requisition/{id}/retry-start` · `POST /requisition/{id}/cancel` |
| 询价 (RfqController) | `GET /rfq/supplier-options` · `GET /rfq/list` · `GET /rfq/{id}` · `GET /rfq/{id}/comparison` · `POST /rfq` · `PUT /rfq/{id}` · `DELETE /rfq/{id}` · `POST /rfq/{id}/send` · `POST /rfq/{id}/award` · `POST /rfq/{id}/cancel` |

Overview 摘要固定返回审批中的请购数量、截止时间内仍有 `INVITED` 供应商的 `SENT` RFQ 数量、
采购订单各状态数量、收货草稿数量，以及按 `currencyCode` 分组的已确认采购承诺金额。
采购承诺与支出仅统计 `CONFIRMED/PARTIAL_RECEIVED/RECEIVED/CLOSED` 订单，不包含
`DRAFT/SENT/CANCELLED`。`spend-analysis` 的 `dimension` 支持
`CATEGORY/SUPPLIER/DEPARTMENT`，其中 DEPARTMENT 表示采购订单 `owner_unit_id`；
结果先按币种、再按金额降序排列，`limit` 范围为 1–100。任何接口都不得跨币种直接求和。

| Controller | 方法与路径 |
|---|---|
| 内部资产候选 (InternalAssetCandidateController) | `GET /api/internal/procurement/goods-receipt/asset-candidates` |
| 内部邀请 (InternalRfqInvitationController) | `GET /api/internal/procurement/rfq/invitations` · `GET /api/internal/procurement/rfq/{rfqId}/invitation` |

### 7.3 端点与 DataScope permission 映射

| 操作 | permissionCode |
|---|---|
| Overview | `procurement:overview:list` |
| Material list/detail | `procurement:material:list` |
| Material create/update/delete | `procurement:material:create/update/delete` |
| Approval route list/create/update/delete | `procurement:approval-route:list/create/update/delete` |
| Requisition list/detail | `procurement:requisition:list` |
| Requisition create/update/delete | `procurement:requisition:create/update/delete` |
| Requisition submit | `procurement:requisition:submit` |
| Requisition retry-start | `procurement:requisition:submit` |
| Requisition approve | `procurement:requisition:approve` |
| Requisition cancel | `procurement:requisition:cancel` |
| RFQ list/detail | `procurement:rfq:list` |
| RFQ create/update/delete/send/award/cancel | `procurement:rfq:create/update/delete/send/award/cancel` |
| PO list/detail | `procurement:purchase-order:list` |
| PO update/delete/send/confirm/cancel | `procurement:purchase-order:update/delete/send/confirm/cancel`（无外部 create，订单仅由 RFQ 定点生成） |
| GR list/detail | `procurement:goods-receipt:list` |
| GR create/confirm/quality-result | `procurement:goods-receipt:create/confirm`（quality-result 复用 confirm 权限） |

## 8. 跨服务一致性

### 8.1 Auth Feign

Auth 是用户/组织/权限/数据范围的权威。当前业务创建从有效 ServiceDataScopeContext 取身份与主组织，PO 继承 RFQ 归属；不把 SRM 的负责人转移/候选解析能力视为采购已具备。模块保留 AuthInternalClient 契约，但接口声明不证明每条创建/列表路径都调用它。共享身份和范围解析失败关闭，XSS 缓存回源有独立安全基线。

### 8.2 SRM Feign

当前 SrmInternalClient 使用 `GET /api/internal/supplier/search`、`POST /api/internal/supplier/batch` 和 `GET /api/internal/quotation/batch`。创建/发送 RFQ 验证当前租户 APPROVED 供应商；比价和定点重新读取有效报价并校验版本、邀请与完整行快照，不能依赖历史名称快照作授权。

Procurement 向 SRM 提供 `GET /api/internal/procurement/rfq/invitations` 和 `GET /api/internal/procurement/rfq/{rfqId}/invitation`。supplierId 必须来自当前 ACTIVE PortalUser，内部调用使用 X-Internal-Token/X-Tenant-Id，重复 tenant 参数须一致；Gateway 不公开该前缀。

新的报价写入校验 SENT、INVITED/QUOTED、未来 deadline、validUntil 和完整行；成功历史重放先于新状态/RFQ 校验，但仍需门户关联、DTO 与意图匹配，返回当前报价且不重发事件。SRM 不跨库写 RFQ，Procurement 用 Inbox 消费报价事件，旧报价版本不得覆盖新版本。

SRM 不可用时，依赖当前供应商/报价的动作失败关闭；已生成 PO 的后续本地命令使用既有快照，不因此一概失效。邀请与订单展示使用保存的名称快照，不声称所有列表都做当前供应商 batch enrich。

### 8.3 Workflow 集成

Procurement 不嵌入 Flowable，通过 `X-Internal-Token` 保护的 Workflow 内部 API 集成。请购审批流程：

1. Procurement 将请购条件更新为 SUBMITTED，事务提交后调用 Workflow `POST /api/internal/workflow/process-instance/start`。
2. 请求包含已持久化的 `requestId`、`tenantId`、`modelVersionId`、`businessType=PROCUREMENT_REQUISITION`、`businessKey={requisitionId}:{approvalAttempt}`、`startUserId` 和 variables。
3. `variables` 包含 `requisitionId`、`approvalAttempt`、`materialCategory`（唯一品类 code）、`totalAmount`（总金额十进制字符串）、`requesterUnitId`（申请部门）；Workflow 按 modelVersionId 对应的已发布 BPMN 启动实例。
4. Workflow 对 `tenantId + businessType + businessKey` 建唯一幂等约束；重复请求返回已有 processInstanceId。
5. Procurement 保存 processInstanceId，将状态推进到 APPROVING。超时或响应丢失时以同一 requestId/businessKey 重试。
6. 审批结束后，Workflow 在本地事务中发布 `workflow.process.completed.v1`，携带 eventId、tenantId、businessType、businessKey、processInstanceId、result 和 completedTime。
7. Procurement 使用 Inbox 唯一键幂等消费，仅当 tenant/businessKey（含当前 attempt）/processInstanceId 均匹配且当前状态为 APPROVING 时更新为 APPROVED、REJECTED 或 CANCELLED，并发送采购领域事件。若完成事件早于本地 `markStarted` 到达，必须抛出可重试异常且不能把 Inbox 标成已处理；旧 attempt 事件只做幂等忽略。

工作流集成遵循 `docs/workflow.md` 的规范：
- `model_key` 在租户内唯一。
- 通过 `processDefinitionId` 启动流程，不使用 `processKey`。
- 流程实例追溯字段记录在 `wf_process_instance_ext`。
- Flowable 表和运行时只存在于 `omni-workflow` 数据库，Procurement 不依赖 `omni-common-workflow`。
- 不使用未定义且不可靠的同步 `WorkflowCallbackService`；审批结果通过 Workflow Outbox 事件交付。

### 8.4 Asset 联动

收货确认后写 Outbox 事件 `procurement.goods-receipt.confirmed.v1`。事件 payload 包含：

```json
{
  "eventId": "018f...uuid",
  "eventType": "procurement.goods-receipt.confirmed.v1",
  "occurredAt": "2026-07-13 10:30:00",
  "tenantId": 1,
  "payload": {
    "goodsReceiptId": 301,
    "grNo": "GR202607130001",
    "purchaseOrderId": 201,
    "poNo": "PO202607100001",
    "supplierId": 101,
    "supplierNameSnapshot": "某某科技",
    "purchaseDate": "2026-07-13 10:30:00",
    "currencyCode": "CNY",
    "ownerUserId": 1001,
    "ownerUnitId": 2001,
    "lines": [
      {
        "goodsReceiptLineId": 401,
        "purchaseOrderLineId": 501,
        "materialId": 601,
        "materialCode": "IT-NB-001",
        "materialNameSnapshot": "ThinkPad X1 Carbon",
        "categoryCode": "LAPTOP",
        "unit": "PCS",
        "receivedQuantity": "5.000000",
        "qualityStatus": "PASS",
        "assetManaged": true,
        "assetQuantity": 5,
        "unitPrice": "12000.000000",
        "totalPrice": "60000.0000"
      }
    ]
  }
}
```

`ownerUserId/ownerUnitId` 是收货单管理归属的不可缺失快照，Asset 直接继承为新资产的管理人和管理部门；不得用供应商门户用户或消息消费线程身份猜测。数量、单价和金额沿用 Procurement 十进制字符串契约，只有整型计数 `assetQuantity` 使用 JSON number。

`omni-asset` 只对满足资产化条件的行按 `assetQuantity` 创建资产卡片。实时事件使用
`consumerName + eventId` 的 Inbox 唯一键幂等，实时消费和历史回扫共同使用
`tenantId + goodsReceiptLineId + unitSequence` 的资产来源唯一键兜底；同一幂等键绑定不同完整业务意图时返回冲突，不得静默覆盖。

本地 Outbox 与 Relay 提供可重试发送路径；插入不证明 Broker/消费者已成功，也不保证未来消费者永久保留消息。历史候选按收货行 ID 升序游标，afterId>=0、size=1–100；仅取 CONFIRMED/PASS/assetManaged/正整数且有来源事件门闩的行，服务再验证唯一来源事件、owner 和关联快照。实时与回扫需共同使用 Inbox 的 consumerName+eventId 及租户/收货行/单位序号来源唯一约束，不能仅靠 Inbox 解释不重复；意图冲突不能覆盖。

### 8.5 Outbox 事件

- `procurement.requisition.submitted.v1`
- `procurement.requisition.approved.v1`
- `procurement.requisition.rejected.v1`
- `procurement.requisition.cancelled.v1`
- `procurement.rfq.sent.v1`
- `procurement.rfq.awarded.v1`
- `procurement.purchase-order.created.v1`
- `procurement.purchase-order.confirmed.v1`
- `procurement.goods-receipt.confirmed.v1`
- `procurement.goods-receipt.quality-passed.v1`

## 9. 隐私、操作日志与 XSS

### 9.1 OperLog 脱敏

OperLogSanitizer 规范化字段名并按包含规则处理 address/contactname/phone 等，故当前地址、联系人和电话有对应规则；非 JSON 退回文本模式处理部分手机号、邮箱、凭据。规则不保证任意 PII 识别，也不替代业务权限。

### 9.2 PII

PO Summary 返回地址、姓名、电话的掩码字段；Detail 在 procurement:purchase-order:list 和行可见性校验后返回完整值，当前没有额外独立 PII 权限检查。Phase 2 的打印/导出权限仍是计划。

### 9.3 XSS

共享 CachedServiceXssConfigProvider 使用 Redis DB 0 的 xss:enabled:{tenantId}/xss:rules:{tenantId}；缓存异常/未命中回源 Auth，回源失败使用安全基线，同时尊重权威 enabled。配置不是 proc: 业务缓存。备注按纯文本处理。

## 10. 前端设计

```text
omni-frontend/src/
├── api/
│   ├── procurement-overview.ts
│   ├── procurement-material.ts
│   ├── procurement-requisition.ts
│   ├── procurement-rfq.ts
│   ├── procurement-purchase-order.ts
│   └── procurement-goods-receipt.ts
├── views/procurement/
│   ├── overview/index.vue           # 采购概览与支出
│   ├── material/index.vue           # 物料目录
│   ├── requisition/index.vue        # 请购
│   ├── rfq/index.vue                # 询价
│   ├── purchase-order/index.vue     # 采购订单
│   └── goods-receipt/index.vue      # 收货
└── components/procurement/
    ├── RequisitionForm.vue          # 请购表单
    ├── RfqCompareView.vue           # 比价
    ├── PurchaseOrderTracker.vue     # 订单进度
    └── GoodsReceiptForm.vue         # 收货表单
```

- `ApiResponse/PageResult` 只从 `src/types/api.ts` 导入。
- 请购表单支持明细行动态增删（添加/移除物料行）。
- 比价视图使用 Element Plus 表格横向对比各供应商报价。
- Workflow 待办列表本身不携带 Procurement businessKey；打开任务时先调用 `/api/workflow/task/{taskId}/form`，从 variables 读取 `businessType/requisitionId`，再加载 Procurement `approval-view`。业务表单加载或任务分配校验失败时必须禁止提交审批。
- `router/index.ts` 与 `layout/index.vue` iconMap 补 Procurement。

另有 procurement-approval-route.ts 和 views/procurement/approval-route（含规则向导、覆盖、试算、图预览）。菜单、权限按钮与 zh-CN/en-US/ja-JP/ko-KR 四语一起维护。

## 11. 工程落点

实现入口是当前已有文件，新增服务教学见开发 Wiki；下面不是要求再次创建模块的操作清单。

| 入口 | 当前文件 |
|---|---|
| 应用与扫描 | [omni-backend/omni-procurement/src/main/java/com/omni/procurement/ProcurementApplication.java](../../omni-backend/omni-procurement/src/main/java/com/omni/procurement/ProcurementApplication.java) |
| 配置 | [omni-backend/omni-procurement/src/main/resources/application.yml](../../omni-backend/omni-procurement/src/main/resources/application.yml) · [omni-backend/omni-procurement/src/main/resources/application-lite.yml](../../omni-backend/omni-procurement/src/main/resources/application-lite.yml) |
| 服务/接口 | [omni-backend/omni-procurement/src/main/java/com/omni/procurement/service/impl/](../../omni-backend/omni-procurement/src/main/java/com/omni/procurement/service/impl/) · [omni-backend/omni-procurement/src/main/java/com/omni/procurement/controller/](../../omni-backend/omni-procurement/src/main/java/com/omni/procurement/controller/) |
| 权限与范围 | [omni-backend/omni-procurement/src/main/java/com/omni/procurement/security/ProcDataPermissionHandler.java](../../omni-backend/omni-procurement/src/main/java/com/omni/procurement/security/ProcDataPermissionHandler.java) |
| 迁移与种子 | [database/changelog/procurement/](../../database/changelog/procurement/) · [scripts/sql/seed/procurement.sql](../../scripts/sql/seed/procurement.sql) · [scripts/sql/seed/auth.sql](../../scripts/sql/seed/auth.sql) · [database/seed/manifest.yaml](../../database/seed/manifest.yaml) |
| 容器 | [compose.yaml](../../compose.yaml) · [compose.tools.yaml](../../compose.tools.yaml) · [docker/backend/Dockerfile](../../docker/backend/Dockerfile) · [start.bat](../../start.bat) · [start.sh](../../start.sh) |
| 前端 | [omni-frontend/src/views/procurement/](../../omni-frontend/src/views/procurement/) · [omni-frontend/src/api/procurement-approval-route.ts](../../omni-frontend/src/api/procurement-approval-route.ts) |
| 租户初始化 | [omni-backend/omni-procurement/src/main/java/com/omni/procurement/service/impl/ProcTenantInitializerImpl.java](../../omni-backend/omni-procurement/src/main/java/com/omni/procurement/service/impl/ProcTenantInitializerImpl.java) · [omni-backend/omni-procurement/src/main/java/com/omni/procurement/service/impl/ProcTenantModuleProvisioner.java](../../omni-backend/omni-procurement/src/main/java/com/omni/procurement/service/impl/ProcTenantModuleProvisioner.java) |

ProcTenantInitializerImpl 首次创建配置时写 CNY 和 13 个默认品类，存在配置时提前返回，不保证以后每次补齐被移除的所有品类。ProcTenantModuleProvisioner 绑定租户身份并在 finally 清理。

## 12. 非功能设计

### 性能

分页端点遵守最大100；品类树、选项和内部游标按各自契约返回。供应商有效性批量校验与历史名称快照展示不同，不保证每个列表都实时补全。

- 概览统计使用 Mapper 层聚合 SQL（按品类/供应商/负责部门及币种的采购支出 `GROUP BY`），
  每个聚合根继续应用与其列表相同的 requester/owner DataScope，禁止跨币种求和和逐记录查询。

### 并发与幂等

收货确认以 receipt/PO 的实际 FOR UPDATE、同租户全部已确认累计数量及版本/影响行数共同保证并发边界；明细查询不能凭方法名推定锁行。定点锁 RFQ/邀请并校验当前报价版本，先生成/核对 PO 快照，再同事务更新 RFQ 与 Outbox。

- 请购提交 → 审批流启动：本地状态条件更新 + Workflow 的 `tenantId + businessType + businessKey` 唯一幂等；超时使用相同 requestId 重试。
- 门户报价提交：SRM 用永久请求历史表以 `(tenantId, requestId)` 幂等、requestHash 防止同键异意图，并以 `(tenantId, rfqId, supplierId)` 约束唯一有效报价；相同意图重放返回当前快照，不重复发布事件。
- 报价事件和审批结果事件：各自使用 Inbox eventId 唯一键幂等消费；同一 quotationId 的旧 quotationVersion 不得覆盖新版本。

proc_event_inbox 以 (tenant_id,event_id) 唯一键保存完整事件 JSON。报价/审批消费先验证事件类型、来源、聚合与 JSON 语义意图；同键异意图返回 409，不能以 JSON 键顺序或空白差异误判。更新业务结果与 Inbox 状态在同一本地事务中。

### 降级

依赖故障按具体阶段处理：提交前模型/路由失败回滚，提交后启动不确定尝试 FAILED 并要求重读；不得假定远程未启动。RFQ 的供应商/报价操作依赖 SRM，已生成 PO 的本地命令使用快照。权限范围不能因 Auth 故障放宽；XSS 回源有独立基线。Broker 不可用时本地 Outbox 可保留待发送，最终投递仍需证据。

## 13. 测试与验收

本节是设计要求与验收场景清单，不是全部已执行记录。当前摘要逐份匹配的 Docker Reactor 产物为采购 49 份报告、213 例通过、0 失败/错误/跳过；只证明各测试实际断言，不证明真实 Workflow/MQ、MySQL 并发或新教程步骤。操作与截图按采购 Wiki 的独立证据范围判断。

最低测试集：

- 请购状态机合法/非法迁移。
- 请购审批流启动和结果消费（Mock Workflow Feign/MQ，不在 Procurement 测试中启动 Flowable）。
- 审批结果事件重复、乱序、tenant/businessKey/processInstanceId 不匹配时不会错误更新请购。
- 审批人只能通过分配给自己的 taskId 读取 approval-view，不能利用该接口读取任意请购。
- 审批流按金额分支路由正确性。
- 询价定点生成采购订单。
- SRM 报价事件幂等消费；SRM 不能直接更新 Procurement 表。
- 定点后修改 SRM 报价不影响已保存的中标快照和采购订单。
- 收货数量不超过订单数量。
- 分批收货累计正确。
- 创建 DRAFT 不更新 PO 已收数量且不发事件；确认时才锁 PO、累计校验并投递。
- 两个可分别成立但合计超收的草稿并发确认时只有一个成功。
- PENDING 质检转 PASS 只发布新通过行的 quality-passed 事件，重复提交不重复资产化。
- 非资产物料、质检失败/待定、非整数连续计量收货不会生成资产候选。
- Asset 实时消费与历史补偿回扫并行时不重复创建资产。
- 跨租户隔离。
- 缺 tenant/scope 失败关闭。
- Outbox 事件写入完整性。

端到端验收：创建物料 → 提交请购 → 审批通过 → 创建询价 → 邀请供应商 → 供应商报价 → 比价定点 → 生成采购订单 → 收货确认 → Outbox 事件发出。

## 14. 实施顺序

以下是 2026-07-27 原实施计划，保留用于追溯；阶段编号和原表述不代表尚需再次创建已存在模块，也不作为当前完成证据。当前事实以本页的源码契约和实际验证记录为准。

### Milestone 0：前置确认

- 确认 SRM 已建设完成，供应商内部 API 可用。
- 确认 Workflow 服务可用，Flowable 引擎正常。
- 确认 Workflow 内部启动/任务校验 API 与 `workflow.process.completed.v1` 事件契约已就绪；Procurement 不引入 `omni-common-workflow`。

### Milestone 1：服务搭建 + 安全底座

- 创建模块、配置、Gateway、Docker、DB。
- TenantLine + DataPermission + Pagination。
- 权限树、Procurement 角色、已有租户迁移。
- 前端 root 菜单。

### Milestone 2：物料目录

- 品类树（任意层级）和物料 CRUD。
- 物料编号 tenant 内唯一。

### Milestone 3：请购 + 审批流

- 请购 CRUD（含明细行动态增删）。
- 请购提交 → Flowable 审批流启动。
- 审批结果事件幂等消费 → 请购状态更新。
- BPMN 流程模型创建（按品类+金额路由）。

### Milestone 4：询价/比价 + 采购订单

- 询价单 CRUD + 邀请供应商。
- 比价视图（手动定点）。
- 采购订单生成 + 状态跟踪。

### Milestone 5：收货 + 生产加固

- 收货确认（含质检状态）。
- 分批收货。
- Outbox 事件（收货确认 → Asset 联动）。
- Asset 历史补偿回扫内部 API。
- 概览统计。
- 测试、索引、安全验收。
- 更新 docs/、AGENTS.md。

## 15. ADR 摘要

| 决策 | 选择 | 原因 |
|---|---|---|
| 服务 | 独立 `omni-procurement` | 与 SRM/Asset 分离，职责清晰 |
| Workflow 集成 | 独立 `omni-workflow` 内部 API + 审批结果事件 | 保持 Flowable 唯一运行时和数据库边界 |
| 审批路由 | Exclusive Gateway 按品类+金额 | 多维审批决策，可扩展 |
| 比价方式 | 手动定点 | MVP 不做自动评标 |
| 收货 → Asset | Outbox 实时事件 + 历史补偿回扫 | 解耦且不依赖 Broker 为未来消费者永久保存消息 |
| 供应商数据 | Feign 调用 SRM | 不跨库读取 SRM 数据 |
| 三单匹配 | 不做 | 留给 ERP/财务系统 |
| MVP 品类管理 | 已开放前端管理 UI | 支持任意层级品类树自主维护 |

## 16. 主要风险

以下保留原设计风险与拟定处置；其中“先金额、后品类”等是当时规划，当前已实现服务端品类/金额规则，不能据此降低当前审批或验收要求。

| 优先级 | 风险 | 处理 |
|---|---|---|
| P0 | Workflow 不可用或响应丢失造成审批半启动/重复启动 | 503 + SUBMITTED/FAILED 可重试启动状态 + 跨服务业务键幂等 |
| P0 | SRM 不可用导致无法询价/下单 | 503 拒绝操作，不绕过供应商校验 |
| P0 | 写操作绕过查询数据权限 | AccessGuard + 条件更新 |
| P1 | 审批流 BPMN 过于复杂 | MVP 先按金额单维度，后续加品类 |
| P1 | 收货数量超收 | 乐观锁 + 数量校验 |
| P0 | SRM 门户跨库写 RFQ 状态 | SRM 报价事件 + Procurement Inbox 消费，禁止跨库写 |
| P1 | Asset 未就绪导致历史收货错过消费 | 实时 Outbox + Asset 上线后的分页补偿回扫 |
| P2 | 品类审批分支数量膨胀 | 预留通用审批模板 + 配置化 |
