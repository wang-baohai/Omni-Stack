-- ============================================================
-- Omni-Stack 采购执行样例数据（完整版）
-- 包含：物料目录、请购单、询价单、采购订单、收货单
-- 前置：init-all.sql 已执行（品类树 + 字典 + 审批路由已就绪）
-- ============================================================

SET NAMES utf8mb4;
USE omni_procurement;

-- ============================================================
-- 0. 清理旧数据（硬删除以避免唯一键冲突）
-- ============================================================
DELETE FROM proc_goods_receipt_line WHERE tenant_id = 1 AND goods_receipt_id IN (1,2);
DELETE FROM proc_goods_receipt WHERE tenant_id = 1 AND id IN (1,2);
DELETE FROM proc_purchase_order_line WHERE tenant_id = 1 AND po_id IN (1,2);
DELETE FROM proc_purchase_order WHERE tenant_id = 1 AND id IN (1,2);
DELETE FROM proc_rfq_supplier WHERE tenant_id = 1 AND rfq_id IN (1,2,3,4);
DELETE FROM proc_rfq_line WHERE tenant_id = 1 AND rfq_id IN (1,2,3,4);
DELETE FROM proc_rfq WHERE tenant_id = 1 AND id IN (1,2,3,4);
DELETE FROM proc_requisition_line WHERE tenant_id = 1 AND requisition_id IN (1,2,3,4,5,6,7,8,9,10,11,12);
DELETE FROM proc_requisition WHERE tenant_id = 1 AND id IN (1,2,3,4,5,6,7,8,9,10,11,12);

-- ============================================================
-- 1. 物料目录（proc_material）- 补充 20 条
-- 已有: id=1 TP-X1C ThinkPad X1 Carbon (category_id=5 LAPTOP)
-- 品类树：
--   1 IT 设备 → 5 笔记本, 6 显示器, 7 外设
--   2 办公用品 → 8 文具, 9 纸张
--   3 原材料 → 10 金属, 11 电子, 12 塑料
--   4 其他 → 13 服务
-- ============================================================
INSERT INTO proc_material
    (tenant_id, category_id, material_code, material_name, specification, unit, asset_managed,
     status, version, deleted, create_by)
VALUES
    -- 笔记本电脑 (category_id=5)
    (1, 5, 'IT-NB-002', 'MacBook Pro 14', 'M3 Pro/18G/512G', 'UNIT', 1, 'ACTIVE', 0, 0, 'sample'),
    (1, 5, 'IT-NB-003', 'Dell XPS 15', '15.6英寸 i9/32G/1T', 'UNIT', 1, 'ACTIVE', 0, 0, 'sample'),
    -- 显示器 (category_id=6)
    (1, 6, 'IT-MON-001', 'Dell U2723QE 显示器', '27英寸 4K IPS Type-C', 'UNIT', 1, 'ACTIVE', 0, 0, 'sample'),
    (1, 6, 'IT-MON-002', 'LG 27UK850-W', '27英寸 4K HDR', 'UNIT', 1, 'ACTIVE', 0, 0, 'sample'),
    (1, 6, 'IT-MON-003', 'BenQ PD2700U', '27英寸 4K 设计师显示器', 'UNIT', 1, 'ACTIVE', 0, 0, 'sample'),
    -- 外设配件 (category_id=7)
    (1, 7, 'IT-KB-001', 'Logitech MX Keys', '无线蓝牙键盘', 'PCS', 0, 'ACTIVE', 0, 0, 'sample'),
    (1, 7, 'IT-MS-001', 'Logitech MX Master 3S', '无线蓝牙鼠标', 'PCS', 0, 'ACTIVE', 0, 0, 'sample'),
    (1, 7, 'IT-DOCK-001', 'CalDigit TS4 扩展坞', 'Thunderbolt 4 18口', 'UNIT', 1, 'ACTIVE', 0, 0, 'sample'),
    (1, 7, 'IT-HD-001', 'WD 2T 移动硬盘', 'USB-C 3.2 2TB', 'PCS', 0, 'ACTIVE', 0, 0, 'sample'),
    -- 文具 (category_id=8)
    (1, 8, 'OF-PEN-001', '晨光中性笔', '0.5mm 黑色 12支/盒', 'BOX', 0, 'ACTIVE', 0, 0, 'sample'),
    (1, 8, 'OF-NB-001', '得力笔记本', 'A5 100页 黑色 6本/包', 'PACK', 0, 'ACTIVE', 0, 0, 'sample'),
    (1, 8, 'OF-FDR-001', '得力文件夹', 'A4 双夹 蓝色 10个/包', 'PACK', 0, 'ACTIVE', 0, 0, 'sample'),
    -- 纸张耗材 (category_id=9)
    (1, 9, 'OF-PAP-001', 'A4 复印纸', '70g 500张/包', 'PACK', 0, 'ACTIVE', 0, 0, 'sample'),
    (1, 9, 'OF-TNR-001', 'HP 硒鼓', 'CF218A 黑色', 'PCS', 0, 'ACTIVE', 0, 0, 'sample'),
    -- 金属材料 (category_id=10)
    (1, 10, 'RM-AL-001', '铝合金型材 6063', 'T5 40x40mm', 'KG', 0, 'ACTIVE', 0, 0, 'sample'),
    (1, 10, 'RM-SS-001', '304不锈钢板', '2B面 1.0mm 1220x2440', 'KG', 0, 'ACTIVE', 0, 0, 'sample'),
    -- 电子元器件 (category_id=11)
    (1, 11, 'RM-PCB-001', 'PCB 电路板', 'FR-4 双面 1.6mm', 'PCS', 0, 'ACTIVE', 0, 0, 'sample'),
    (1, 11, 'RM-RES-001', '贴片电阻 0603', '10KΩ ±1% 编带', 'PCS', 0, 'ACTIVE', 0, 0, 'sample'),
    (1, 11, 'RM-CAP-001', '贴片电容 0805', '100nF ±10% X7R', 'PCS', 0, 'ACTIVE', 0, 0, 'sample'),
    -- 塑料材料 (category_id=12)
    (1, 12, 'RM-ABS-001', 'ABS 塑料板材', '本色 2mm 1000x2000', 'KG', 0, 'ACTIVE', 0, 0, 'sample'),
    (1, 12, 'RM-NYL-001', '尼龙扎带', '4x200mm 白色 1000条/包', 'PACK', 0, 'ACTIVE', 0, 0, 'sample'),
    -- 服务 (category_id=13)
    (1, 13, 'SR-CLEAN-001', '办公室保洁服务', '月度保洁合同', 'SET', 0, 'ACTIVE', 0, 0, 'sample'),
    (1, 13, 'SR-MAINT-001', 'IT 运维外包服务', '季度驻场运维', 'SET', 0, 'ACTIVE', 0, 0, 'sample'),
    (1, 13, 'IT-CLD-001', '云服务器 ECS', '4核8G 包年', 'EA', 0, 'ACTIVE', 0, 0, 'sample')
AS new
ON DUPLICATE KEY UPDATE
    material_name = new.material_name,
    specification = new.specification;

-- ============================================================
-- 2. 请购单（proc_requisition）- 8 张 + 明细行
-- 用户映射:
--   100 张三(unit 101/402), 105 赵六(unit 100), 107 钱七(unit 200), 300 采购经理(unit 101)
-- ============================================================

-- REQ5: 技术部笔记本采购 - DRAFT
INSERT INTO proc_requisition
    (id, tenant_id, requisition_no, title, requester_user_id, requester_unit_id, reason,
     primary_category_code, total_amount, currency_code, status,
     approval_attempt, workflow_start_status,
     owner_user_id, owner_unit_id, version, deleted, create_by, create_time)
VALUES
    (5, 1, 'REQ20260701001', '技术部新员工笔记本采购', 100, 402, '后端组3名新员工入职配备开发笔记本',
     'LAPTOP', 35400.0000, 'CNY', 'DRAFT',
     0, 'NOT_STARTED',
     100, 402, 0, 0, 'sample', '2026-07-01 09:30:00');

INSERT INTO proc_requisition_line
    (id, tenant_id, requisition_id, line_no, material_id, material_code, material_name,
     category_code, unit, quantity, estimated_unit_price, estimated_total_price, remark,
     version, deleted, create_by, create_time)
VALUES
    (5, 1, 5, 1, 1, 'TP-X1C', 'ThinkPad X1 Carbon',
     'LAPTOP', 'UNIT', 3, 11800.0000, 35400.0000, 'i7/16G/512G 配置',
     0, 0, 'sample', '2026-07-01 09:30:00');

-- REQ6: 行政部办公耗材季度补充 - APPROVED
INSERT INTO proc_requisition
    (id, tenant_id, requisition_no, title, requester_user_id, requester_unit_id, reason,
     primary_category_code, total_amount, currency_code, status,
     approval_attempt, workflow_start_status, approved_time,
     owner_user_id, owner_unit_id, version, deleted, create_by, create_time)
VALUES
    (6, 1, 'REQ20260702001', '行政部Q3办公耗材补充', 105, 100, '季度办公耗材集中采购',
     'STATIONERY', 4200.0000, 'CNY', 'APPROVED',
     1, 'STARTED', '2026-07-03 14:00:00',
     105, 100, 0, 0, 'sample', '2026-07-02 10:00:00');

INSERT INTO proc_requisition_line
    (id, tenant_id, requisition_id, line_no, material_id, material_code, material_name,
     category_code, unit, quantity, estimated_unit_price, estimated_total_price, remark,
     version, deleted, create_by, create_time)
VALUES
    (6, 1, 6, 1, 10, 'OF-PEN-001', '晨光中性笔',
     'STATIONERY', 'BOX', 30, 25.0000, 750.0000, '全公司季度用量',
     0, 0, 'sample', '2026-07-02 10:00:00'),
    (7, 1, 6, 2, 13, 'OF-PAP-001', 'A4 复印纸',
     'PAPER', 'PACK', 80, 28.0000, 2240.0000, '各部门季度用量',
     0, 0, 'sample', '2026-07-02 10:00:00'),
    (8, 1, 6, 3, 14, 'OF-TNR-001', 'HP 硒鼓',
     'PAPER', 'PCS', 10, 121.0000, 1210.0000, '打印机备用',
     0, 0, 'sample', '2026-07-02 10:00:00');

-- REQ7: 技术部显示器采购 - APPROVED
INSERT INTO proc_requisition
    (id, tenant_id, requisition_no, title, requester_user_id, requester_unit_id, reason,
     primary_category_code, total_amount, currency_code, status,
     approval_attempt, workflow_start_status, approved_time,
     owner_user_id, owner_unit_id, version, deleted, create_by, create_time)
VALUES
    (7, 1, 'REQ20260703001', '技术部双屏显示器采购', 100, 402, '开发人员双屏配置升级',
     'MONITOR', 24000.0000, 'CNY', 'APPROVED',
     1, 'STARTED', '2026-07-05 16:00:00',
     100, 402, 0, 0, 'sample', '2026-07-03 11:00:00');

INSERT INTO proc_requisition_line
    (id, tenant_id, requisition_id, line_no, material_id, material_code, material_name,
     category_code, unit, quantity, estimated_unit_price, estimated_total_price, remark,
     version, deleted, create_by, create_time)
VALUES
    (9, 1, 7, 1, 4, 'IT-MON-001', 'Dell U2723QE 显示器',
     'MONITOR', 'UNIT', 8, 3000.0000, 24000.0000, '27英寸4K Type-C',
     0, 0, 'sample', '2026-07-03 11:00:00');

-- REQ8: 市场部原材料备料 - APPROVED
INSERT INTO proc_requisition
    (id, tenant_id, requisition_no, title, requester_user_id, requester_unit_id, reason,
     primary_category_code, total_amount, currency_code, status,
     approval_attempt, workflow_start_status, approved_time,
     owner_user_id, owner_unit_id, version, deleted, create_by, create_time)
VALUES
    (8, 1, 'REQ20260705001', 'Q3生产原材料备料', 107, 200, '第三季度生产计划原材料储备',
     'METAL', 185000.0000, 'CNY', 'APPROVED',
     1, 'STARTED', '2026-07-08 10:00:00',
     107, 200, 0, 0, 'sample', '2026-07-05 14:00:00');

INSERT INTO proc_requisition_line
    (id, tenant_id, requisition_id, line_no, material_id, material_code, material_name,
     category_code, unit, quantity, estimated_unit_price, estimated_total_price, remark,
     version, deleted, create_by, create_time)
VALUES
    (10, 1, 8, 1, 16, 'RM-AL-001', '铝合金型材 6063',
     'METAL', 'KG', 500, 210.0000, 105000.0000, 'T5规格 40x40mm',
     0, 0, 'sample', '2026-07-05 14:00:00'),
    (11, 1, 8, 2, 18, 'RM-PCB-001', 'PCB 电路板',
     'ELECTRONIC', 'PCS', 1000, 55.0000, 55000.0000, 'FR-4 双面 1.6mm',
     0, 0, 'sample', '2026-07-05 14:00:00'),
    (12, 1, 8, 3, 21, 'RM-ABS-001', 'ABS 塑料板材',
     'PLASTIC', 'KG', 200, 125.0000, 25000.0000, '本色 2mm',
     0, 0, 'sample', '2026-07-05 14:00:00');

-- REQ9: 外设配件采购 - APPROVING (审批中，概览有数据)
INSERT INTO proc_requisition
    (id, tenant_id, requisition_no, title, requester_user_id, requester_unit_id, reason,
     primary_category_code, total_amount, currency_code, status,
     approval_attempt, workflow_start_status,
     workflow_request_id, workflow_business_key, workflow_model_version_id,
     owner_user_id, owner_unit_id, version, deleted, create_by, create_time)
VALUES
    (9, 1, 'REQ20260710001', '技术部外设配件采购', 100, 402, '开发组键盘鼠标扩展坞配备',
     'PERIPHERAL', 18600.0000, 'CNY', 'APPROVING',
     1, 'STARTED',
     'req-9-wf-001', '9:1', 2,
     100, 402, 0, 0, 'sample', '2026-07-10 09:00:00');

INSERT INTO proc_requisition_line
    (id, tenant_id, requisition_id, line_no, material_id, material_code, material_name,
     category_code, unit, quantity, estimated_unit_price, estimated_total_price, remark,
     version, deleted, create_by, create_time)
VALUES
    (13, 1, 9, 1, 7, 'IT-KB-001', 'Logitech MX Keys',
     'PERIPHERAL', 'PCS', 10, 600.0000, 6000.0000, '无线蓝牙键盘',
     0, 0, 'sample', '2026-07-10 09:00:00'),
    (14, 1, 9, 2, 8, 'IT-MS-001', 'Logitech MX Master 3S',
     'PERIPHERAL', 'PCS', 10, 500.0000, 5000.0000, '无线蓝牙鼠标',
     0, 0, 'sample', '2026-07-10 09:00:00'),
    (15, 1, 9, 3, 9, 'IT-DOCK-001', 'CalDigit TS4 扩展坞',
     'PERIPHERAL', 'UNIT', 4, 1900.0000, 7600.0000, 'Thunderbolt 4',
     0, 0, 'sample', '2026-07-10 09:00:00');

-- REQ10: 服务采购 - DRAFT
INSERT INTO proc_requisition
    (id, tenant_id, requisition_no, title, requester_user_id, requester_unit_id, reason,
     primary_category_code, total_amount, currency_code, status,
     approval_attempt, workflow_start_status,
     owner_user_id, owner_unit_id, version, deleted, create_by, create_time)
VALUES
    (10, 1, 'REQ20260712001', '下半年保洁服务续约', 105, 100, '行政部保洁服务合同到期续约',
     'SERVICE', 36000.0000, 'CNY', 'DRAFT',
     0, 'NOT_STARTED',
     105, 100, 0, 0, 'sample', '2026-07-12 15:00:00');

INSERT INTO proc_requisition_line
    (id, tenant_id, requisition_id, line_no, material_id, material_code, material_name,
     category_code, unit, quantity, estimated_unit_price, estimated_total_price, remark,
     version, deleted, create_by, create_time)
VALUES
    (16, 1, 10, 1, 23, 'SR-CLEAN-001', '办公室保洁服务',
     'SERVICE', 'SET', 6, 6000.0000, 36000.0000, '月度合同 7-12月',
     0, 0, 'sample', '2026-07-12 15:00:00');

-- REQ11: 大额IT设备 - REJECTED
INSERT INTO proc_requisition
    (id, tenant_id, requisition_no, title, requester_user_id, requester_unit_id, reason,
     primary_category_code, total_amount, currency_code, status,
     approval_attempt, workflow_start_status,
     workflow_request_id, workflow_business_key, workflow_model_version_id,
     owner_user_id, owner_unit_id, version, deleted, create_by, create_time)
VALUES
    (11, 1, 'REQ20260708001', 'MacBook Pro 批量采购', 300, 101, '设计组配置升级',
     'LAPTOP', 88500.0000, 'CNY', 'REJECTED',
     1, 'STARTED',
     'req-11-wf-001', '11:1', 2,
     300, 101, 0, 0, 'sample', '2026-07-08 10:00:00');

INSERT INTO proc_requisition_line
    (id, tenant_id, requisition_id, line_no, material_id, material_code, material_name,
     category_code, unit, quantity, estimated_unit_price, estimated_total_price, remark,
     version, deleted, create_by, create_time)
VALUES
    (17, 1, 11, 1, 2, 'IT-NB-002', 'MacBook Pro 14',
     'LAPTOP', 'UNIT', 3, 18500.0000, 55500.0000, 'M3 Pro/18G/512G',
     0, 0, 'sample', '2026-07-08 10:00:00'),
    (18, 1, 11, 2, 6, 'IT-MON-003', 'BenQ PD2700U',
     'MONITOR', 'UNIT', 3, 11000.0000, 33000.0000, '设计师专用4K',
     0, 0, 'sample', '2026-07-08 10:00:00');

-- REQ12: 小额文具紧急采购 - APPROVED
INSERT INTO proc_requisition
    (id, tenant_id, requisition_no, title, requester_user_id, requester_unit_id, reason,
     primary_category_code, total_amount, currency_code, status,
     approval_attempt, workflow_start_status, approved_time,
     owner_user_id, owner_unit_id, version, deleted, create_by, create_time)
VALUES
    (12, 1, 'REQ20260715001', '会议室文具紧急补充', 105, 100, '会议室文具库存告急',
     'STATIONERY', 850.0000, 'CNY', 'APPROVED',
     1, 'STARTED', '2026-07-15 17:00:00',
     105, 100, 0, 0, 'sample', '2026-07-15 14:00:00');

INSERT INTO proc_requisition_line
    (id, tenant_id, requisition_id, line_no, material_id, material_code, material_name,
     category_code, unit, quantity, estimated_unit_price, estimated_total_price, remark,
     version, deleted, create_by, create_time)
VALUES
    (19, 1, 12, 1, 11, 'OF-NB-001', '得力笔记本',
     'STATIONERY', 'PACK', 10, 45.0000, 450.0000, '会议室备用',
     0, 0, 'sample', '2026-07-15 14:00:00'),
    (20, 1, 12, 2, 12, 'OF-FDR-001', '得力文件夹',
     'STATIONERY', 'PACK', 5, 80.0000, 400.0000, '会议室归档用',
     0, 0, 'sample', '2026-07-15 14:00:00');

-- ============================================================
-- 3. 询价单（proc_rfq）- 4 张 + 明细行 + 供应商邀请
-- ============================================================

-- RFQ1: 办公耗材询价（关联REQ6），SENT - 等待报价
INSERT INTO proc_rfq
    (id, tenant_id, rfq_no, requisition_id, title, quotation_deadline, currency_code,
     status, sent_time, owner_user_id, owner_unit_id, version, deleted, create_by, create_time)
VALUES
    (1, 1, 'RFQ20260704001', 6, 'Q3办公耗材询价', '2026-08-20 18:00:00', 'CNY',
     'SENT', '2026-07-04 10:00:00', 105, 100, 0, 0, 'sample', '2026-07-04 09:00:00');

INSERT INTO proc_rfq_line
    (id, tenant_id, rfq_id, line_no, material_id, material_code, material_name,
     category_code, unit, quantity, remark, version, deleted, create_by, create_time)
VALUES
    (1, 1, 1, 1, 10, 'OF-PEN-001', '晨光中性笔',
     'STATIONERY', 'BOX', 30, '要求品牌：晨光/得力',
     0, 0, 'sample', '2026-07-04 09:00:00'),
    (2, 1, 1, 2, 13, 'OF-PAP-001', 'A4 复印纸',
     'PAPER', 'PACK', 80, '要求70g以上',
     0, 0, 'sample', '2026-07-04 09:00:00'),
    (3, 1, 1, 3, 14, 'OF-TNR-001', 'HP 硒鼓',
     'PAPER', 'PCS', 10, 'CF218A 原装',
     0, 0, 'sample', '2026-07-04 09:00:00');

INSERT INTO proc_rfq_supplier
    (id, tenant_id, rfq_id, supplier_id, supplier_name_snapshot, invited_time,
     status, version, deleted, create_by, create_time)
VALUES
    (1, 1, 1, 1, '文达办公用品有限公司', '2026-07-04 10:30:00',
     'INVITED', 0, 0, 'sample', '2026-07-04 10:00:00'),
    (2, 1, 1, 2, '汇通文具批发商行', '2026-07-04 10:30:00',
     'QUOTED', 0, 0, 'sample', '2026-07-04 10:00:00');

-- RFQ2: 显示器询价（关联REQ7），AWARDED - 已定标
INSERT INTO proc_rfq
    (id, tenant_id, rfq_no, requisition_id, title, quotation_deadline, currency_code,
     status, sent_time, awarded_supplier_id, awarded_quotation_id,
     awarded_quotation_version, awarded_time,
     owner_user_id, owner_unit_id, version, deleted, create_by, create_time)
VALUES
    (2, 1, 'RFQ20260706001', 7, '技术部显示器询价', '2026-07-12 18:00:00', 'CNY',
     'AWARDED', '2026-07-06 10:00:00', 3, 2001, 1, '2026-07-13 15:00:00',
     100, 402, 0, 0, 'sample', '2026-07-06 09:00:00');

INSERT INTO proc_rfq_line
    (id, tenant_id, rfq_id, line_no, material_id, material_code, material_name,
     category_code, unit, quantity, remark, version, deleted, create_by, create_time)
VALUES
    (4, 1, 2, 1, 4, 'IT-MON-001', 'Dell U2723QE 显示器',
     'MONITOR', 'UNIT', 8, '27英寸4K Type-C 含支架',
     0, 0, 'sample', '2026-07-06 09:00:00');

INSERT INTO proc_rfq_supplier
    (id, tenant_id, rfq_id, supplier_id, supplier_name_snapshot, invited_time,
     quotation_id, quotation_version, quotation_time,
     status, version, deleted, create_by, create_time)
VALUES
    (3, 1, 2, 3, '戴尔中国官方商城', '2026-07-06 10:30:00',
     2001, 1, '2026-07-10 14:00:00',
     'AWARDED', 0, 0, 'sample', '2026-07-06 10:00:00'),
    (4, 1, 2, 5, '京东企业采购平台', '2026-07-06 10:30:00',
     2002, 1, '2026-07-11 09:00:00',
     'REJECTED', 0, 0, 'sample', '2026-07-06 10:00:00');

-- RFQ3: 原材料询价（关联REQ8），AWARDED - 已定标
INSERT INTO proc_rfq
    (id, tenant_id, rfq_no, requisition_id, title, quotation_deadline, currency_code,
     status, sent_time, awarded_supplier_id, awarded_quotation_id,
     awarded_quotation_version, awarded_time,
     owner_user_id, owner_unit_id, version, deleted, create_by, create_time)
VALUES
    (3, 1, 'RFQ20260709001', 8, 'Q3原材料询价', '2026-07-18 18:00:00', 'CNY',
     'AWARDED', '2026-07-09 10:00:00', 6, 3001, 1, '2026-07-16 14:00:00',
     107, 200, 0, 0, 'sample', '2026-07-09 09:00:00');

INSERT INTO proc_rfq_line
    (id, tenant_id, rfq_id, line_no, material_id, material_code, material_name,
     category_code, unit, quantity, remark, version, deleted, create_by, create_time)
VALUES
    (5, 1, 3, 1, 16, 'RM-AL-001', '铝合金型材 6063',
     'METAL', 'KG', 500, 'T5规格',
     0, 0, 'sample', '2026-07-09 09:00:00'),
    (6, 1, 3, 2, 18, 'RM-PCB-001', 'PCB 电路板',
     'ELECTRONIC', 'PCS', 1000, 'FR-4双面',
     0, 0, 'sample', '2026-07-09 09:00:00'),
    (7, 1, 3, 3, 21, 'RM-ABS-001', 'ABS 塑料板材',
     'PLASTIC', 'KG', 200, '本色2mm',
     0, 0, 'sample', '2026-07-09 09:00:00');

INSERT INTO proc_rfq_supplier
    (id, tenant_id, rfq_id, supplier_id, supplier_name_snapshot, invited_time,
     quotation_id, quotation_version, quotation_time,
     status, version, deleted, create_by, create_time)
VALUES
    (5, 1, 3, 6, '华南铝业有限公司', '2026-07-09 10:30:00',
     3001, 1, '2026-07-14 11:00:00',
     'AWARDED', 0, 0, 'sample', '2026-07-09 10:00:00'),
    (6, 1, 3, 7, '深圳电子材料科技', '2026-07-09 10:30:00',
     3002, 1, '2026-07-15 16:00:00',
     'REJECTED', 0, 0, 'sample', '2026-07-09 10:00:00');

-- RFQ4: 外设配件询价（关联REQ9），DRAFT
INSERT INTO proc_rfq
    (id, tenant_id, rfq_no, requisition_id, title, quotation_deadline, currency_code,
     status, owner_user_id, owner_unit_id, version, deleted, create_by, create_time)
VALUES
    (4, 1, 'RFQ20260711001', 9, '技术部外设询价', '2026-07-25 18:00:00', 'CNY',
     'DRAFT', 100, 402, 0, 0, 'sample', '2026-07-11 09:00:00');

INSERT INTO proc_rfq_line
    (id, tenant_id, rfq_id, line_no, material_id, material_code, material_name,
     category_code, unit, quantity, remark, version, deleted, create_by, create_time)
VALUES
    (8, 1, 4, 1, 7, 'IT-KB-001', 'Logitech MX Keys',
     'PERIPHERAL', 'PCS', 10, '无线蓝牙',
     0, 0, 'sample', '2026-07-11 09:00:00'),
    (9, 1, 4, 2, 8, 'IT-MS-001', 'Logitech MX Master 3S',
     'PERIPHERAL', 'PCS', 10, '无线蓝牙',
     0, 0, 'sample', '2026-07-11 09:00:00'),
    (10, 1, 4, 3, 9, 'IT-DOCK-001', 'CalDigit TS4 扩展坞',
     'PERIPHERAL', 'UNIT', 4, 'Thunderbolt 4',
     0, 0, 'sample', '2026-07-11 09:00:00');

-- ============================================================
-- 4. 采购订单（proc_purchase_order）- 2 张 + 明细行
-- ============================================================

-- PO1: 显示器采购订单（关联RFQ2），CONFIRMED
INSERT INTO proc_purchase_order
    (id, tenant_id, po_no, rfq_id, supplier_id, supplier_name_snapshot, quotation_id, quotation_version,
     title, total_amount, currency_code, status,
     order_time, expected_delivery_date, delivery_address, contact_name, contact_phone,
     owner_user_id, owner_unit_id, version, deleted, create_by, create_time)
VALUES
    (1, 1, 'PO20260714001', 2, 3, '戴尔中国官方商城', 2001, 1,
     '技术部Dell显示器采购订单', 22400.0000, 'CNY', 'CONFIRMED',
     '2026-07-14 09:00:00', '2026-07-24', '北京市朝阳区建国路88号 技术部', '采购助理张三', '13800138100',
     100, 402, 0, 0, 'sample', '2026-07-14 09:00:00');

INSERT INTO proc_purchase_order_line
    (id, tenant_id, po_id, line_no, rfq_line_id, material_id, material_code, material_name,
     category_code, unit, quantity, unit_price, total_price, delivery_days,
     expected_delivery_date, remark, version, deleted, create_by, create_time)
VALUES
    (1, 1, 1, 1, 4, 4, 'IT-MON-001', 'Dell U2723QE 显示器',
     'MONITOR', 'UNIT', 8, 2800.0000, 22400.0000, 7,
     '2026-07-21', '中标价 2800/台 含支架',
     0, 0, 'sample', '2026-07-14 09:00:00');

-- PO2: 原材料采购订单（关联RFQ3），CONFIRMED
INSERT INTO proc_purchase_order
    (id, tenant_id, po_no, rfq_id, supplier_id, supplier_name_snapshot, quotation_id, quotation_version,
     title, total_amount, currency_code, status,
     order_time, expected_delivery_date, delivery_address, contact_name, contact_phone,
     owner_user_id, owner_unit_id, version, deleted, create_by, create_time)
VALUES
    (2, 1, 'PO20260717001', 3, 6, '华南铝业有限公司', 3001, 1,
     'Q3原材料集中采购订单', 178000.0000, 'CNY', 'CONFIRMED',
     '2026-07-17 10:00:00', '2026-08-01', '上海市浦东新区工业园区B栋 仓储部', '采购专员钱七', '13900139200',
     107, 200, 0, 0, 'sample', '2026-07-17 10:00:00');

INSERT INTO proc_purchase_order_line
    (id, tenant_id, po_id, line_no, rfq_line_id, material_id, material_code, material_name,
     category_code, unit, quantity, unit_price, total_price, delivery_days,
     expected_delivery_date, remark, version, deleted, create_by, create_time)
VALUES
    (2, 1, 2, 1, 5, 16, 'RM-AL-001', '铝合金型材 6063',
     'METAL', 'KG', 500, 198.0000, 99000.0000, 10,
     '2026-07-27', '中标价 198/KG',
     0, 0, 'sample', '2026-07-17 10:00:00'),
    (3, 1, 2, 2, 6, 18, 'RM-PCB-001', 'PCB 电路板',
     'ELECTRONIC', 'PCS', 1000, 52.0000, 52000.0000, 12,
     '2026-07-29', '中标价 52/PCS',
     0, 0, 'sample', '2026-07-17 10:00:00'),
    (4, 1, 2, 3, 7, 21, 'RM-ABS-001', 'ABS 塑料板材',
     'PLASTIC', 'KG', 200, 135.0000, 27000.0000, 8,
     '2026-07-25', '中标价 135/KG',
     0, 0, 'sample', '2026-07-17 10:00:00');

-- ============================================================
-- 5. 收货单（proc_goods_receipt）- 1 CONFIRMED + 1 DRAFT
-- ============================================================

-- GR1: 显示器收货（关联PO1），CONFIRMED，质检全部PASS
INSERT INTO proc_goods_receipt
    (id, tenant_id, gr_no, po_id, receiver_user_id, receive_time, remark, status,
     confirmed_time, confirmed_event_id,
     owner_user_id, owner_unit_id, version, deleted, create_by, create_time)
VALUES
    (1, 1, 'GR20260722001', 1, 100, '2026-07-22 14:00:00', 'Dell显示器到货验收', 'CONFIRMED',
     '2026-07-22 15:00:00', 'gr-1-confirmed-001',
     100, 402, 0, 0, 'sample', '2026-07-22 14:00:00');

INSERT INTO proc_goods_receipt_line
    (id, tenant_id, goods_receipt_id, line_no, po_line_id, material_id, material_code, material_name,
     category_code, unit, asset_managed, ordered_quantity, received_quantity, quality_status,
     quality_result_time, confirmed_event_id, remark,
     version, deleted, create_by, create_time)
VALUES
    (1, 1, 1, 1, 1, 4, 'IT-MON-001', 'Dell U2723QE 显示器',
     'MONITOR', 'UNIT', 1, 8, 8, 'PASS',
     '2026-07-22 14:30:00', 'gr-1-line-1-confirmed', '外观完好，配件齐全，Type-C线材正常',
     0, 0, 'sample', '2026-07-22 14:00:00');

-- GR2: 原材料收货（关联PO2），DRAFT，部分质检
INSERT INTO proc_goods_receipt
    (id, tenant_id, gr_no, po_id, receiver_user_id, receive_time, remark, status,
     owner_user_id, owner_unit_id, version, deleted, create_by, create_time)
VALUES
    (2, 1, 'GR20260726001', 2, 107, '2026-07-26 10:00:00', 'Q3原材料首批到货', 'DRAFT',
     107, 200, 0, 0, 'sample', '2026-07-26 10:00:00');

INSERT INTO proc_goods_receipt_line
    (id, tenant_id, goods_receipt_id, line_no, po_line_id, material_id, material_code, material_name,
     category_code, unit, asset_managed, ordered_quantity, received_quantity, quality_status,
     quality_result_time, remark,
     version, deleted, create_by, create_time)
VALUES
    (2, 1, 2, 1, 2, 16, 'RM-AL-001', '铝合金型材 6063',
     'METAL', 'KG', 0, 500, 500, 'PASS',
     '2026-07-26 11:00:00', '材质报告齐全，规格符合',
     0, 0, 'sample', '2026-07-26 10:00:00'),
    (3, 1, 2, 2, 3, 18, 'RM-PCB-001', 'PCB 电路板',
     'ELECTRONIC', 'PCS', 0, 1000, 1000, 'PENDING',
     NULL, '待品质部抽检',
     0, 0, 'sample', '2026-07-26 10:00:00'),
    (4, 1, 2, 3, 4, 21, 'RM-ABS-001', 'ABS 塑料板材',
     'PLASTIC', 'KG', 0, 200, 200, 'PASS',
     '2026-07-26 11:30:00', '厚度均匀，无色差',
     0, 0, 'sample', '2026-07-26 10:00:00');
-- ============================================================
-- Omni-Stack 采购执行样例数据
-- 包含：物料、请购、询价、采购订单、收货
-- 执行：docker cp 后 docker exec bash -c "mysql ... < file"
-- 前置：需先执行 init-all.sql 创建表结构和品类种子数据
-- 品类树：
--   IT 设备(1) → 笔记本电脑(5)、显示器(6)、外设配件(7)
--   办公用品(2) → 文具(8)、纸张耗材(9)
--   原材料(3) → 金属材料(10)、电子元器件(11)、塑料材料(12)
--   其他(4) → 服务(13)
-- ============================================================

USE omni_procurement;

SET NAMES utf8mb4;

-- ============================================================
-- 1. 物料（proc_material）- 20 条
-- 物料只能挂在叶子品类上，category_id 对应上方品类树
-- ============================================================
INSERT INTO proc_material
    (tenant_id, category_id, material_code, material_name, specification, unit, asset_managed,
     status, version, deleted, create_by)
VALUES
    -- 笔记本电脑
    (1, 5, 'IT-NB-001', 'ThinkPad X1 Carbon', '14英寸 i7/16G/512G', 'UNIT', 1,
     'ACTIVE', 0, 0, 'admin'),
    -- 显示器
    (1, 6, 'IT-MON-002', 'Dell U2723QE 显示器', '27英寸 4K IPS', 'UNIT', 1,
     'ACTIVE', 0, 0, 'admin'),
    (1, 6, 'IT-MON-007', 'LG 27UK850-W', '27英寸 4K HDR Type-C', 'UNIT', 1,
     'ACTIVE', 0, 0, 'admin'),
    -- 外设配件
    (1, 7, 'IT-KB-003', 'Logitech MX Keys', '无线蓝牙键盘', 'PCS', 0,
     'ACTIVE', 0, 0, 'admin'),
    (1, 7, 'IT-MS-005', '罗技 MX Master 3S', '无线蓝牙鼠标', 'PCS', 0,
     'ACTIVE', 0, 0, 'admin'),
    (1, 7, 'IT-DOCK-006', 'CalDigit TS4 扩展坞', 'Thunderbolt 4 18口', 'UNIT', 1,
     'ACTIVE', 0, 0, 'admin'),
    -- 文具
    (1, 8, 'OF-PEN-001', '中性签字笔', '0.5mm 黑色 12支/盒', 'BOX', 0,
     'ACTIVE', 0, 0, 'admin'),
    (1, 8, 'OF-FDR-003', '得力文件夹', 'A4 双夹 蓝色 10个/包', 'PACK', 0,
     'ACTIVE', 0, 0, 'admin'),
    -- 纸张耗材
    (1, 9, 'OF-PAP-002', 'A4 复印纸', '70g 500张/包', 'PACK', 0,
     'ACTIVE', 0, 0, 'admin'),
    (1, 9, 'OF-LBL-004', '斑马标签纸', '100x150mm 热敏 800张/卷', 'ROLL', 0,
     'ACTIVE', 0, 0, 'admin'),
    -- 金属材料
    (1, 10, 'RM-AL-001', '铝合金型材', '6063-T5 40x40mm', 'KG', 0,
     'ACTIVE', 0, 0, 'admin'),
    (1, 10, 'RM-SS-003', '304不锈钢板', '2B面 1.0mm 1220x2440', 'KG', 0,
     'ACTIVE', 0, 0, 'admin'),
    -- 电子元器件
    (1, 11, 'RM-PCB-002', 'PCB 电路板', 'FR-4 双面 1.6mm', 'PCS', 0,
     'ACTIVE', 0, 0, 'admin'),
    (1, 11, 'RM-RES-004', '贴片电阻 0603', '10KΩ ±1% 编带', 'PCS', 0,
     'ACTIVE', 0, 0, 'admin'),
    (1, 11, 'RM-CAP-005', '贴片电容 0805', '100nF ±10% X7R', 'PCS', 0,
     'ACTIVE', 0, 0, 'admin'),
    -- 塑料材料
    (1, 12, 'RM-ABS-006', 'ABS 塑料板材', '本色 2mm 1000x2000', 'KG', 0,
     'ACTIVE', 0, 0, 'admin'),
    (1, 12, 'RM-NYL-007', '尼龙扎带', '4x200mm 白色 1000条/包', 'PACK', 0,
     'ACTIVE', 0, 0, 'admin'),
    -- 服务
    (1, 13, 'SR-CLEAN-001', '办公室保洁服务', '月度保洁合同', 'SET', 0,
     'ACTIVE', 0, 0, 'admin'),
    (1, 13, 'SR-MAINT-002', 'IT 运维外包服务', '季度驻场运维', 'SET', 0,
     'ACTIVE', 0, 0, 'admin'),
    (1, 13, 'IT-SRV-004', '云服务器 ECS', '4核8G 包年', 'EA', 0,
     'ACTIVE', 0, 0, 'admin')
AS new
ON DUPLICATE KEY UPDATE
    material_name = new.material_name,
    specification = new.specification;

-- ============================================================
-- 2. 请购单（proc_requisition）- 3 张 + 明细行
-- ============================================================

-- 请购1：IT设备请购，DRAFT 状态
INSERT INTO proc_requisition
    (tenant_id, requisition_no, title, requester_user_id, requester_unit_id, reason,
     primary_category_code, total_amount, currency_code, status,
     approval_attempt, workflow_start_status,
     owner_user_id, owner_unit_id, version, deleted, create_by)
VALUES
    (1, 'REQ20260701001', '研发部笔记本电脑采购', 100, 101, '新员工入职需配备开发用笔记本',
     'LAPTOP', 36000.0000, 'CNY', 'DRAFT',
     0, 'NOT_STARTED',
     100, 101, 0, 0, 'admin');

SET @req1_id = LAST_INSERT_ID();

INSERT INTO proc_requisition_line
    (tenant_id, requisition_id, line_no, material_id, material_code, material_name,
     category_code, unit, quantity, estimated_unit_price, estimated_total_price, remark,
     version, deleted, create_by)
VALUES
    (1, @req1_id, 1, 1, 'IT-NB-001', 'ThinkPad X1 Carbon',
     'LAPTOP', 'UNIT', 3, 12000.0000, 36000.0000, '研发组3名新员工',
     0, 0, 'admin');

-- 请购2：办公用品请购，APPROVED 状态（已完成审批）
INSERT INTO proc_requisition
    (tenant_id, requisition_no, title, requester_user_id, requester_unit_id, reason,
     primary_category_code, total_amount, currency_code, status,
     approval_attempt, workflow_start_status, approved_time,
     owner_user_id, owner_unit_id, version, deleted, create_by)
VALUES
    (1, 'REQ20260702001', '行政部办公耗材补充', 105, 100, '季度办公耗材补充采购',
     'STATIONERY', 2400.0000, 'CNY', 'APPROVED',
     1, 'STARTED', '2026-07-03 14:00:00',
     105, 100, 0, 0, 'admin');

SET @req2_id = LAST_INSERT_ID();

INSERT INTO proc_requisition_line
    (tenant_id, requisition_id, line_no, material_id, material_code, material_name,
     category_code, unit, quantity, estimated_unit_price, estimated_total_price, remark,
     version, deleted, create_by)
VALUES
    (1, @req2_id, 1, 7, 'OF-PEN-001', '中性签字笔',
     'STATIONERY', 'BOX', 20, 30.0000, 600.0000, '行政+研发共用',
     0, 0, 'admin'),
    (1, @req2_id, 2, 9, 'OF-PAP-002', 'A4 复印纸',
     'PAPER', 'PACK', 60, 30.0000, 1800.0000, '全公司季度用量',
     0, 0, 'admin');

-- 请购3：原材料请购，DRAFT 状态（大额）
INSERT INTO proc_requisition
    (tenant_id, requisition_no, title, requester_user_id, requester_unit_id, reason,
     primary_category_code, total_amount, currency_code, status,
     approval_attempt, workflow_start_status,
     owner_user_id, owner_unit_id, version, deleted, create_by)
VALUES
    (1, 'REQ20260710001', '车间原材料备料', 107, 200, 'Q3 生产计划原材料储备',
     'METAL', 150000.0000, 'CNY', 'DRAFT',
     0, 'NOT_STARTED',
     107, 200, 0, 0, 'admin');

SET @req3_id = LAST_INSERT_ID();

INSERT INTO proc_requisition_line
    (tenant_id, requisition_id, line_no, material_id, material_code, material_name,
     category_code, unit, quantity, estimated_unit_price, estimated_total_price, remark,
     version, deleted, create_by)
VALUES
    (1, @req3_id, 1, 11, 'RM-AL-001', '铝合金型材',
     'METAL', 'KG', 500, 200.0000, 100000.0000, '6063-T5 规格',
     0, 0, 'admin'),
    (1, @req3_id, 2, 13, 'RM-PCB-002', 'PCB 电路板',
     'ELECTRONIC', 'PCS', 1000, 50.0000, 50000.0000, 'FR-4 双面',
     0, 0, 'admin');

-- ============================================================
-- 3. 询价单（proc_rfq）- 2 张 + 明细行 + 供应商邀请
-- ============================================================

-- 询价1：办公用品询价，SENT 状态（等待报价）
INSERT INTO proc_rfq
    (tenant_id, rfq_no, requisition_id, title, quotation_deadline, currency_code,
     status, sent_time, owner_user_id, owner_unit_id, version, deleted, create_by)
VALUES
    (1, 'RFQ20260705001', @req2_id, '办公耗材询价', '2026-07-20 18:00:00', 'CNY',
     'SENT', '2026-07-05 10:00:00', 105, 100, 0, 0, 'admin');

SET @rfq1_id = LAST_INSERT_ID();

INSERT INTO proc_rfq_line
    (tenant_id, rfq_id, line_no, material_id, material_code, material_name,
     category_code, unit, quantity, remark, version, deleted, create_by)
VALUES
    (1, @rfq1_id, 1, 7, 'OF-PEN-001', '中性签字笔',
     'STATIONERY', 'BOX', 20, '要求品牌：晨光/得力',
     0, 0, 'admin'),
    (1, @rfq1_id, 2, 9, 'OF-PAP-002', 'A4 复印纸',
     'PAPER', 'PACK', 60, '要求70g以上',
     0, 0, 'admin');

INSERT INTO proc_rfq_supplier
    (tenant_id, rfq_id, supplier_id, supplier_name_snapshot, invited_time,
     status, version, deleted, create_by)
VALUES
    (1, @rfq1_id, 1, '文达办公用品', '2026-07-05 10:30:00',
     'INVITED', 0, 0, 'admin'),
    (1, @rfq1_id, 2, '汇通文具批发', '2026-07-05 10:30:00',
     'QUOTED', 0, 0, 'admin');

-- 询价2：IT设备询价，AWARDED 状态（已定点）
INSERT INTO proc_rfq
    (tenant_id, rfq_no, requisition_id, title, quotation_deadline, currency_code,
     status, sent_time, awarded_supplier_id, awarded_quotation_id,
     awarded_quotation_version, awarded_time,
     owner_user_id, owner_unit_id, version, deleted, create_by)
VALUES
    (1, 'RFQ20260701001', @req1_id, '研发笔记本询价', '2026-07-10 18:00:00', 'CNY',
     'AWARDED', '2026-07-01 14:00:00', 3, 1001, 1, '2026-07-12 16:00:00',
     100, 101, 0, 0, 'admin');

SET @rfq2_id = LAST_INSERT_ID();

INSERT INTO proc_rfq_line
    (tenant_id, rfq_id, line_no, material_id, material_code, material_name,
     category_code, unit, quantity, remark, version, deleted, create_by)
VALUES
    (1, @rfq2_id, 1, 1, 'IT-NB-001', 'ThinkPad X1 Carbon',
     'LAPTOP', 'UNIT', 3, 'i7/16G/512G 配置',
     0, 0, 'admin');

INSERT INTO proc_rfq_supplier
    (tenant_id, rfq_id, supplier_id, supplier_name_snapshot, invited_time,
     quotation_id, quotation_version, quotation_time,
     status, version, deleted, create_by)
VALUES
    (1, @rfq2_id, 3, '联想官方商城', '2026-07-01 14:30:00',
     1001, 1, '2026-07-08 09:00:00',
     'AWARDED', 0, 0, 'admin'),
    (1, @rfq2_id, 4, '京东企业购', '2026-07-01 14:30:00',
     1002, 1, '2026-07-09 11:00:00',
     'REJECTED', 0, 0, 'admin');

-- ============================================================
-- 4. 采购订单（proc_purchase_order）- 1 张 + 明细行
-- ============================================================
INSERT INTO proc_purchase_order
    (tenant_id, po_no, rfq_id, supplier_id, supplier_name_snapshot, quotation_id, quotation_version,
     title, total_amount, currency_code, status,
     order_time, expected_delivery_date, delivery_address, contact_name, contact_phone,
     owner_user_id, owner_unit_id, version, deleted, create_by)
VALUES
    (1, 'PO20260712001', @rfq2_id, 3, '联想官方商城', 1001, 1,
     'ThinkPad X1 Carbon 采购订单', 35400.0000, 'CNY', 'CONFIRMED',
     '2026-07-12 16:30:00', '2026-07-22', '北京市朝阳区建国路88号 研发部', '王助理', '13800138099',
     100, 101, 0, 0, 'admin');

SET @po1_id = LAST_INSERT_ID();

INSERT INTO proc_purchase_order_line
    (tenant_id, po_id, line_no, rfq_line_id, material_id, material_code, material_name,
     category_code, unit, quantity, unit_price, total_price, remark, version, deleted, create_by)
VALUES
    (1, @po1_id, 1, 1, 1, 'IT-NB-001', 'ThinkPad X1 Carbon',
     'LAPTOP', 'UNIT', 3, 11800.0000, 35400.0000, '中标价 11800/台',
     0, 0, 'admin');

-- ============================================================
-- 5. 收货单（proc_goods_receipt）- 1 张草稿 + 明细行
-- ============================================================
INSERT INTO proc_goods_receipt
    (tenant_id, gr_no, po_id, receiver_user_id, receive_time, remark, status,
     owner_user_id, owner_unit_id, version, deleted, create_by)
VALUES
    (1, 'GR20260720001', @po1_id, 100, '2026-07-20 14:00:00', '首批到货验收', 'DRAFT',
     100, 101, 0, 0, 'admin');

SET @gr1_id = LAST_INSERT_ID();

INSERT INTO proc_goods_receipt_line
    (tenant_id, goods_receipt_id, line_no, po_line_id, material_id, material_code, material_name,
     category_code, unit, asset_managed, ordered_quantity, received_quantity, quality_status, remark,
     version, deleted, create_by)
VALUES
    (1, @gr1_id, 1, 1, 1, 'IT-NB-001', 'ThinkPad X1 Carbon',
     'LAPTOP', 'UNIT', 1, 3, 3, 'PASS', '外观完好，配件齐全',
     0, 0, 'admin');
