# 09a29fe 数据库结构快照

> 采集日期：2026-08-20
> 来源：当前 `omni-mysql` 容器的 `information_schema`，只读查询，不包含业务行数据。
> 范围：七个业务数据库 + Nacos、XXL-JOB 两个基础设施数据库。Gateway 无独立数据库。
> 用途：S0-03 基线与 S0-06 `adopt-current` 参考输入；不是数据库备份。

## 1. 指纹算法

每个数据库分别按稳定顺序拼接三类记录并计算 UTF-8 SHA-256：

1. 列：表名、序号、列名、完整类型、可空、默认值、extra、collation；
2. 索引：表名、索引名、列序号、列名、是否非唯一、索引类型；
3. 约束：表名、约束名、约束类型。

当前指纹没有纳入表数据、AUTO_INCREMENT 当前值、视图定义、触发器和存储过程正文。S0-06 的正式指纹必须额外覆盖表引擎/字符集、外键引用与规则、CHECK 表达式、存储过程摘要和 vendor 版本，不能直接复制本摘要算法后宣称可 adopt。

| 数据库 | 元数据记录 | SHA-256 |
|---|---:|---|
| `omni_auth` | 329 | `35b7dbae857dd9b45c196f0f17959c274c63077f304d38de6798e05f28e82715` |
| `omni_base` | 159 | `55d2e63c3ec708b6ef514541a65866e583f3fda9d9fafaca94127fa1eca38990` |
| `omni_workflow` | 1174 | `b7086d489a16a5944d533f8617697ff180e465b8c76567888c023e0aa96f9d84` |
| `omni_crm` | 372 | `c405e50f813a07ed53ac208ca7075dccfcc04b6bc867b6f6e56c1fb982c7d449` |
| `omni_srm` | 562 | `3a69e74b64be7ef19bc7de0ed6839da98a82fcdd45807ffebf47b057da438235` |
| `omni_procurement` | 568 | `92f0645946ae87c3fbcfc8808f5e4905f9d8ea5b10131d5d3a354e041565d4fc` |
| `omni_asset` | 242 | `471965eb58e2d6e8caf64b1bd3f3e45a98fa64e3fafcacf686b2c3f615b921e5` |
| `nacos_config` | 142 | `4556bc14d096cfa2e87b4b47f5b4682f117a2be0854d3802b052e142a0c2af4f` |
| `xxl_job` | 99 | `27a8198842758a19faef688949403976e97a17c1a22f0bf99283ecae16b02588` |

## 2. 七个业务数据库表清单

### `omni_auth`：20 表，210 列

`oauth2_authorization`、`oauth2_authorization_consent`、`oauth2_registered_client`、
`sys_audit_log`、`sys_mq_message`、`sys_org_unit`、`sys_permission`、
`sys_portal_role_request`、`sys_role`、`sys_role_dept`、`sys_role_permission`、
`sys_tenant`、`sys_token_blacklist`、`sys_user`、`sys_user_oauth_provider`、
`sys_user_role`、`sys_user_role_scope`、`sys_user_unit`、`sys_xss_blacklist_rule`、
`sys_xss_config`。

关键计数：20 个主键约束、15 个唯一约束、1 个 CHECK；10 张表含 `tenant_id`，1 张表含 `version`。另有存储过程 `sp_init_tenant`。

### `omni_base`：8 表，110 列

`sys_dict_data`、`sys_dict_type`、`sys_mq_message`、`sys_oper_log`、
`sys_oper_log_archive`、`sys_user_job`、`sys_user_job_log`、`sys_user_job_type`。

关键计数：8 个主键约束、5 个唯一约束；7 张表含 `tenant_id`。

### `omni_workflow`：54 表，754 列

Flowable/FLW vendor 表 45 张：

`ACT_EVT_LOG`、`ACT_GE_BYTEARRAY`、`ACT_GE_PROPERTY`、`ACT_HI_ACTINST`、
`ACT_HI_ATTACHMENT`、`ACT_HI_COMMENT`、`ACT_HI_DETAIL`、`ACT_HI_ENTITYLINK`、
`ACT_HI_IDENTITYLINK`、`ACT_HI_PROCINST`、`ACT_HI_TASKINST`、`ACT_HI_TSK_LOG`、
`ACT_HI_VARINST`、`ACT_ID_BYTEARRAY`、`ACT_ID_GROUP`、`ACT_ID_INFO`、
`ACT_ID_MEMBERSHIP`、`ACT_ID_PRIV`、`ACT_ID_PRIV_MAPPING`、`ACT_ID_PROPERTY`、
`ACT_ID_TOKEN`、`ACT_ID_USER`、`ACT_PROCDEF_INFO`、`ACT_RE_DEPLOYMENT`、
`ACT_RE_MODEL`、`ACT_RE_PROCDEF`、`ACT_RU_ACTINST`、`ACT_RU_DEADLETTER_JOB`、
`ACT_RU_ENTITYLINK`、`ACT_RU_EVENT_SUBSCR`、`ACT_RU_EXECUTION`、`ACT_RU_EXTERNAL_JOB`、
`ACT_RU_HISTORY_JOB`、`ACT_RU_IDENTITYLINK`、`ACT_RU_JOB`、`ACT_RU_SUSPENDED_JOB`、
`ACT_RU_TASK`、`ACT_RU_TIMER_JOB`、`ACT_RU_VARIABLE`、`FLW_CHANNEL_DEFINITION`、
`FLW_EVENT_DEFINITION`、`FLW_EVENT_DEPLOYMENT`、`FLW_EVENT_RESOURCE`、
`FLW_RU_BATCH`、`FLW_RU_BATCH_PART`。

业务扩展/公共表 9 张：`sys_mq_message`、`wf_cc_record`、`wf_delegation_rule`、
`wf_form_schema`、`wf_process_instance_ext`、`wf_process_model`、
`wf_process_model_version`、`wf_process_start_request`、`wf_todo_task`。

关键计数：54 张表、55 个主键列（含复合主键）、47 个外键、14 个唯一约束、1 个 CHECK；9 张扩展表含 `tenant_id`，2 张含 `version`。

### `omni_crm`：12 表，219 列

`crm_activity`、`crm_contact`、`crm_customer`、`crm_lead`、`crm_lead_conversion`、
`crm_opportunity`、`crm_opportunity_stage_history`、`crm_owner_change_log`、
`crm_pipeline`、`crm_pipeline_stage`、`crm_tenant_config`、`sys_mq_message`。

关键计数：12 个主键、9 个唯一约束；12 张表含 `tenant_id`，6 张含 `version`，7 张含 `deleted`。

### `omni_srm`：21 表，327 列

`srm_evaluation`、`srm_evaluation_dimension`、`srm_evaluation_item`、
`srm_evaluation_template`、`srm_event_inbox`、`srm_quotation`、
`srm_quotation_line`、`srm_quotation_request`、`srm_risk_assessment`、
`srm_risk_criterion`、`srm_risk_indicator`、`srm_risk_indicator_type`、
`srm_risk_score_threshold`、`srm_supplier`、`srm_supplier_bank_account`、
`srm_supplier_contact`、`srm_supplier_enrollment`、`srm_supplier_invite`、
`srm_supplier_portal_user`、`srm_supplier_qualification`、`sys_mq_message`。

关键计数：21 个主键、21 个唯一约束、12 个 CHECK；21 张表含 `tenant_id`，14 张含 `version`，18 张含 `deleted`。

### `omni_procurement`：15 表，284 列

`proc_approval_route`、`proc_event_inbox`、`proc_goods_receipt`、
`proc_goods_receipt_line`、`proc_material`、`proc_material_category`、
`proc_purchase_order`、`proc_purchase_order_line`、`proc_requisition`、
`proc_requisition_line`、`proc_rfq`、`proc_rfq_line`、`proc_rfq_supplier`、
`proc_tenant_config`、`sys_mq_message`。

关键计数：15 个主键、24 个唯一约束、54 个 CHECK；15 张表含 `tenant_id`，13 张含 `version`，13 张含 `deleted`。

### `omni_asset`：6 表，137 列

`ast_asset`、`ast_asset_history`、`ast_disposal`、`ast_inbox_event`、
`ast_transfer`、`sys_mq_message`。

关键计数：6 个主键、12 个唯一约束、3 个外键、14 个 CHECK；6 张表含 `tenant_id`，3 张含 `version`，3 张含 `deleted`。

## 3. 基础设施数据库

| 数据库 | 表数 | 范围 |
|---|---:|---|
| `nacos_config` | 10 | Nacos 3.1.1 配置、历史、标签、租户、用户和角色表 |
| `xxl_job` | 8 | XXL-JOB 3.3.1 任务、日志、锁、注册、用户和分组表 |

两者必须作为锁定版本的 vendor changelog 管理。升级 vendor 版本时新增完整兼容性验证，禁止业务 changeSet 直接修改 vendor 表。

## 4. 重要偏差与约束

- 上位计划原称“八个业务数据库”，实际是七个：Auth、Base、Workflow、CRM、SRM、Procurement、Asset；第八个后端服务 Gateway 是无状态入口，不拥有数据库。计划已按事实修正。
- 七个业务库都包含各自的 `sys_mq_message`，因此公共 MQ changeSet 必须参数化应用到每个所有者数据库，不能把某一库的表当作全局共享表。
- `omni_workflow` 的 45 张 vendor 表与 9 张业务扩展/公共表必须分开管理；vendor 指纹不匹配时 `adopt-current` 应失败关闭。
- 当前运行态没有 `DATABASECHANGELOG` / `DATABASECHANGELOGLOCK` 事实，S0-06 只能在备份与指纹确认后执行接管。
- 本文件只记录结构，不证明种子正确、数据无跨租户污染或数据库可恢复；这些是独立质量门。
