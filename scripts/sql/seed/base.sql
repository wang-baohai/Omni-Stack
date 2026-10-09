-- Omni-Stack 正式幂等种子；由 09a29fe 基线数据机械提取并改为 INSERT IGNORE。
-- 结构由 Liquibase YAML 管理；本文件禁止包含 DDL、账号、授权或存储过程。

INSERT IGNORE INTO sys_dict_type (tenant_id, type_code, type_name, sort, status, create_by) VALUES
    (1, 'sys_user_gender',   '用户性别',   1, 1, 'system'),
    (1, 'sys_common_status', '通用状态',   2, 1, 'system'),
    (1, 'sys_notice_type',   '通知类型',   3, 1, 'system'),
    (1, 'srm_supplier_category', '供应商品类', 20, 1, 'system');

INSERT IGNORE INTO sys_dict_data (tenant_id, type_code, dict_value, dict_label, tag_type, sort, status, create_by) VALUES
    (1, 'sys_user_gender', '1', '男',     'primary', 1, 1, 'system'),
    (1, 'sys_user_gender', '2', '女',     'danger',  2, 1, 'system'),
    (1, 'sys_user_gender', '0', '未知',   'info',    3, 1, 'system'),
    (1, 'sys_common_status', '1', '启用', 'success', 1, 1, 'system'),
    (1, 'sys_common_status', '0', '禁用', 'danger',  2, 1, 'system'),
    (1, 'sys_notice_type', '1', '系统通知', 'primary', 1, 1, 'system'),
    (1, 'sys_notice_type', '2', '业务通知', 'warning', 2, 1, 'system');

INSERT IGNORE INTO sys_dict_data
    (tenant_id, type_code, dict_value, dict_label, tag_type, sort, status, create_by)
SELECT 1, 'srm_supplier_category', category.dict_value, category.dict_label,
       category.tag_type, category.sort, 1, 'system'
FROM (
    SELECT 'ELECTRONICS' dict_value, '电子元器件' dict_label, 'primary' tag_type, 1 sort
    UNION ALL SELECT 'IT', '信息技术', 'success', 2
    UNION ALL SELECT 'RAW_MATERIAL', '原材料', 'warning', 3
    UNION ALL SELECT 'ADMIN', '行政物资', 'info', 4
    UNION ALL SELECT 'SERVICE', '服务', 'primary', 5
) category
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data dict_data
    WHERE dict_data.tenant_id = 1
      AND dict_data.type_code = 'srm_supplier_category'
      AND dict_data.dict_value = category.dict_value
);
INSERT IGNORE INTO sys_user_job_type (type_code, type_name, description, param_template, status) VALUES
('Task-00001', '喝水提醒', '定时提醒喝水，根据杯型参数生成个性化提醒消息',
 '{"type":"object","properties":{"cupShape":{"type":"string","title":"杯型","enum":["大杯","中杯","小杯","玻璃杯"],"default":"中杯"}},"required":["cupShape"]}',
 1);

INSERT IGNORE INTO sys_user_job (tenant_id, job_name, job_type, cron_expression, job_params, status, create_by) VALUES
(1, '我要喝水', 'Task-00001', '0 * * * * ?', '{"cupShape":"玻璃杯"}', 1, 'admin');

INSERT IGNORE INTO sys_dict_type (id, tenant_id, type_code, type_name, remark, sort, status, create_by)
VALUES
    (10, 1, 'workflow_category', '流程分类', '工作流审批流程的分类标签', 10, 1, 'system');

INSERT IGNORE INTO sys_dict_data (tenant_id, type_code, dict_value, dict_label, sort, status, create_by)
VALUES
    (1, 'workflow_category', 'leave',          '请假审批', 1, 1, 'system'),
    (1, 'workflow_category', 'expense',        '报销审批', 2, 1, 'system'),
    (1, 'workflow_category', 'purchase',       '采购审批', 3, 1, 'system'),
    (1, 'workflow_category', 'contract',       '合同审批', 4, 1, 'system'),
    (1, 'workflow_category', 'general',        '通用审批', 5, 1, 'system'),
    (1, 'workflow_category', 'asset_transfer', '资产调拨', 6, 1, 'system'),
    (1, 'workflow_category', 'asset_disposal', '资产处置', 7, 1, 'system'),
    (1, 'workflow_category', 'supplier',       '供应商审批', 8, 1, 'system');

INSERT IGNORE INTO sys_dict_type (tenant_id, type_code, type_name, remark, sort, status, create_by)
VALUES
    (1, 'crm_activity_type',   '跟进活动类型', 'CRM跟进活动的类型枚举',   30, 1, 'system'),
    (1, 'crm_activity_status', '活动状态',     'CRM跟进活动的状态枚举',   31, 1, 'system'),
    (1, 'crm_root_type',       '关联对象类型', 'CRM活动关联的业务对象类型', 32, 1, 'system');

INSERT IGNORE INTO sys_dict_data (tenant_id, type_code, dict_value, dict_label, tag_type, sort, status, create_by)
VALUES
    (1, 'crm_activity_type', 'CALL',     '电话',     'primary', 1, 1, 'system'),
    (1, 'crm_activity_type', 'VISIT',    '拜访',     'success', 2, 1, 'system'),
    (1, 'crm_activity_type', 'EMAIL',    '邮件',     'info',    3, 1, 'system'),
    (1, 'crm_activity_type', 'MEETING',  '会议',     'warning', 4, 1, 'system'),
    (1, 'crm_activity_type', 'PROPOSAL', '提交方案', 'primary', 5, 1, 'system'),
    (1, 'crm_activity_type', 'OTHER',    '其他',     'info',    6, 1, 'system'),
    (1, 'crm_activity_status', 'PLANNED',   '计划中', 'primary', 1, 1, 'system'),
    (1, 'crm_activity_status', 'COMPLETED', '已完成', 'success', 2, 1, 'system'),
    (1, 'crm_activity_status', 'CANCELLED', '已取消', 'info',    3, 1, 'system'),
    (1, 'crm_root_type', 'LEAD',        '线索',   'primary', 1, 1, 'system'),
    (1, 'crm_root_type', 'CUSTOMER',    '客户',   'success', 2, 1, 'system'),
    (1, 'crm_root_type', 'OPPORTUNITY', '商机',   'warning', 3, 1, 'system');

INSERT IGNORE INTO sys_dict_type (tenant_id, type_code, type_name, remark, sort, status, create_by)
VALUES
    (1, 'proc_requisition_status',   '请购状态',       '采购请购单的状态枚举',         20, 1, 'system'),
    (1, 'proc_rfq_status',           '询价状态',       '采购询价单的状态枚举',         21, 1, 'system'),
    (1, 'proc_rfq_supplier_status',  '供应商报价状态', '询价中受邀供应商的状态枚举',   22, 1, 'system'),
    (1, 'proc_po_status',            '采购订单状态',   '采购订单的状态枚举',           23, 1, 'system'),
    (1, 'proc_gr_status',            '收货状态',       '采购收货单的状态枚举',         24, 1, 'system'),
    (1, 'proc_quality_status',       '质检状态',       '收货行的质检结果状态枚举',     25, 1, 'system'),
    (1, 'proc_material_unit',        '物料计量单位',   '采购物料的计量单位枚举',       26, 1, 'system');

INSERT IGNORE INTO sys_dict_data (tenant_id, type_code, dict_value,         dict_label,     tag_type,  sort, status, create_by)
VALUES
    -- 请购状态
    (1, 'proc_requisition_status',   'DRAFT',           '草稿',     'info',     1, 1, 'system'),
    (1, 'proc_requisition_status',   'SUBMITTED',       '已提交',   'primary',  2, 1, 'system'),
    (1, 'proc_requisition_status',   'APPROVING',       '审批中',   'warning',  3, 1, 'system'),
    (1, 'proc_requisition_status',   'APPROVED',        '已通过',   'success',  4, 1, 'system'),
    (1, 'proc_requisition_status',   'REJECTED',        '已驳回',   'danger',   5, 1, 'system'),
    (1, 'proc_requisition_status',   'CANCELLED',       '已取消',   'info',     6, 1, 'system'),
    -- 询价状态
    (1, 'proc_rfq_status',           'DRAFT',           '草稿',     'info',     1, 1, 'system'),
    (1, 'proc_rfq_status',           'SENT',            '报价中',   'primary',  2, 1, 'system'),
    (1, 'proc_rfq_status',           'CLOSED',          '已截止',   'warning',  3, 1, 'system'),
    (1, 'proc_rfq_status',           'AWARDED',         '已定标',   'success',  4, 1, 'system'),
    (1, 'proc_rfq_status',           'CANCELLED',       '已取消',   'danger',   5, 1, 'system'),
    -- 供应商报价状态
    (1, 'proc_rfq_supplier_status',  'INVITED',         '已邀请',   'info',     1, 1, 'system'),
    (1, 'proc_rfq_supplier_status',  'QUOTED',          '已报价',   'primary',  2, 1, 'system'),
    (1, 'proc_rfq_supplier_status',  'EXPIRED',         '已过期',   'warning',  3, 1, 'system'),
    (1, 'proc_rfq_supplier_status',  'AWARDED',         '已中标',   'success',  4, 1, 'system'),
    (1, 'proc_rfq_supplier_status',  'REJECTED',        '未中标',   'danger',   5, 1, 'system'),
    -- 采购订单状态
    (1, 'proc_po_status',            'DRAFT',           '草稿',     'info',     1, 1, 'system'),
    (1, 'proc_po_status',            'SENT',            '已发送',   'primary',  2, 1, 'system'),
    (1, 'proc_po_status',            'CONFIRMED',       '已确认',   'success',  3, 1, 'system'),
    (1, 'proc_po_status',            'PARTIAL_RECEIVED','部分收货', 'warning',  4, 1, 'system'),
    (1, 'proc_po_status',            'RECEIVED',        '已收货',   'success',  5, 1, 'system'),
    (1, 'proc_po_status',            'CLOSED',          '已关闭',   'info',     6, 1, 'system'),
    (1, 'proc_po_status',            'CANCELLED',       '已取消',   'danger',   7, 1, 'system'),
    -- 收货状态
    (1, 'proc_gr_status',            'DRAFT',           '草稿',     'info',     1, 1, 'system'),
    (1, 'proc_gr_status',            'CONFIRMED',       '已确认',   'success',  2, 1, 'system'),
    -- 质检状态
    (1, 'proc_quality_status',       'PASS',            '合格',     'success',  1, 1, 'system'),
    (1, 'proc_quality_status',       'FAIL',            '不合格',   'danger',   2, 1, 'system'),
    (1, 'proc_quality_status',       'PENDING',         '待定',     'warning',  3, 1, 'system'),
    -- 物料计量单位
    (1, 'proc_material_unit',        'EA',              '个',       'primary',  1, 1, 'system'),
    (1, 'proc_material_unit',        'PCS',             '件',       'primary',  2, 1, 'system'),
    (1, 'proc_material_unit',        'UNIT',            '台',       'primary',  3, 1, 'system'),
    (1, 'proc_material_unit',        'SET',             '套',       'primary',  4, 1, 'system'),
    (1, 'proc_material_unit',        'KG',              '千克',     'info',     5, 1, 'system'),
    (1, 'proc_material_unit',        'BOX',             '箱',       'info',     6, 1, 'system'),
    (1, 'proc_material_unit',        'PACK',            '包',       'info',     7, 1, 'system'),
    (1, 'proc_material_unit',        'M',               '米',       'info',     8, 1, 'system');

INSERT IGNORE INTO sys_dict_type
    (tenant_id, type_code, type_name, remark, sort, status, create_by)
VALUES
    (1, 'asset_category', '资产品类', '资产台账品类编码', 30, 1, 'system'),
    (1, 'asset_location', '资产位置', '资产存放位置编码', 31, 1, 'system');

INSERT IGNORE INTO sys_dict_data
    (tenant_id, type_code, dict_value, dict_label, tag_type, sort, status, create_by)
SELECT 1, definitions.type_code, definitions.dict_value, definitions.dict_label,
       definitions.tag_type, definitions.sort, 1, 'system'
FROM (
    SELECT 'asset_category' type_code, 'IT_DEVICE' dict_value, 'IT设备' dict_label,
           'primary' tag_type, 1 sort
    UNION ALL SELECT 'asset_category', 'LAPTOP', '笔记本电脑', 'primary', 2
    UNION ALL SELECT 'asset_category', 'DESKTOP', '台式电脑', 'primary', 3
    UNION ALL SELECT 'asset_category', 'MONITOR', '显示器', 'success', 4
    UNION ALL SELECT 'asset_category', 'PERIPHERAL', '外设配件', 'info', 5
    UNION ALL SELECT 'asset_category', 'MOBILE_DEVICE', '移动设备', 'success', 6
    UNION ALL SELECT 'asset_category', 'NETWORK_DEVICE', '网络设备', 'warning', 7
    UNION ALL SELECT 'asset_category', 'OFFICE_EQUIPMENT', '办公设备', 'info', 8
    UNION ALL SELECT 'asset_category', 'FURNITURE', '办公家具', 'info', 9
    UNION ALL SELECT 'asset_category', 'OTHER', '其他', 'info', 10
    UNION ALL SELECT 'asset_location', 'ASSET_WAREHOUSE', '资产仓库', 'primary', 1
    UNION ALL SELECT 'asset_location', 'IT_WAREHOUSE', 'IT仓库', 'primary', 2
    UNION ALL SELECT 'asset_location', 'OFFICE_AREA', '办公区', 'success', 3
    UNION ALL SELECT 'asset_location', 'MEETING_ROOM', '会议室', 'warning', 4
    UNION ALL SELECT 'asset_location', 'SERVER_ROOM', '机房', 'danger', 5
    UNION ALL SELECT 'asset_location', 'OTHER', '其他', 'info', 6
) definitions
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_dict_data dict_data
    WHERE dict_data.tenant_id = 1
      AND dict_data.type_code = definitions.type_code
      AND dict_data.dict_value = definitions.dict_value
);
