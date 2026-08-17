-- ============================================================
-- Omni-Stack CRM 样例数据
-- 包含：线索、客户、联系人、商机、阶段历史、跟进活动、Lead 转换
-- 执行：docker exec -i omni-mysql mysql -uroot -proot omni_crm < scripts/sql/crm-sample-data.sql
-- ============================================================

USE omni_crm;

SET NAMES utf8mb4;

-- ============================================================
-- 1. 线索（crm_lead）- 8 条
--    业务流程：Lead 1/2/3/5/8 已转换为客户，Lead 4 无效，Lead 6/7 跟进中
-- ============================================================
INSERT INTO crm_lead
    (id, tenant_id, lead_no, full_name, company_name, job_title, mobile, phone, email, region, address,
     source_code, industry_code, rating, status, disqualify_reason,
     owner_user_id, owner_unit_id, assigned_time, last_activity_time, next_followup_time, converted_time,
     version, deleted, create_by)
VALUES
(1, 1, 'L20260001', '张三', '北京星辰科技', 'CTO', '13800138001', NULL, 'zhangsan@xingchen.com', '北京', '北京市朝阳区建国路88号',
 'WEB', 'TECH', 'A', 'CONVERTED', NULL,
 100, 101, NOW(), '2026-07-12 15:30:00', NULL, '2026-07-13 09:00:00',
 0, 0, 'admin'),

(2, 1, 'L20260002', '李四', '上海云帆网络', 'COO', '13800138002', NULL, 'lisi@yunfan.com', '上海', '上海市浦东新区陆家嘴环路100号',
 'REFERRAL', 'TECH', 'A', 'FOLLOWING', NULL,
 101, 101, NOW(), '2026-07-09 12:00:00', '2026-07-15 09:00:00', NULL,
 0, 0, 'admin'),

(3, 1, 'L20260003', '王五', '深圳蓝海智能', 'CPO', '13800138003', NULL, 'wangwu@lanhai.com', '深圳', '深圳市南山区科技园南区',
 'WEINAR', 'AI', 'B', 'NEW', NULL,
 102, 101, NOW(), '2026-07-11 16:20:00', '2026-07-14 14:00:00', NULL,
 0, 0, 'admin'),

(4, 1, 'L20260004', '赵六', '杭州绿竹教育', '校长', '13800138004', NULL, 'zhaoliu@lvzhu.edu', '杭州', '杭州市西湖区文三路200号',
 'CONFERENCE', 'EDU', 'C', 'DISQUALIFIED', '预算不足，暂缓项目',
 103, 102, NOW(), '2026-07-08 10:00:00', NULL, NULL,
 0, 0, 'admin'),

(5, 1, 'L20260005', '钱七', '广州红日电商', '运营总监', '13800138005', NULL, 'qianqi@hongri.com', '广州', '广州市天河区体育西路58号',
 'WEB', 'ECOMMERCE', 'A', 'CONVERTED', NULL,
 105, 100, NOW(), '2026-07-10 11:00:00', NULL, '2026-07-10 09:00:00',
 0, 0, 'admin'),

(6, 1, 'L20260006', '郑十二', '西安碧水设计', '设计总监', '13800138012', NULL, 'zhengse@bishui.com', '西安', '西安市雁塔区高新路66号',
 'WEINAR', 'DESIGN', 'B', 'NEW', NULL,
 107, 200, NOW(), NULL, '2026-07-14 16:00:00', NULL,
 0, 0, 'admin'),

(7, 1, 'L20260007', '周九', '武汉青松医药', '采购经理', '13800138007', NULL, 'zhoujiu@qingsong.com', '武汉', '武汉市江夏区光谷大道88号',
 'WEB', 'PHARMA', 'A', 'FOLLOWING', NULL,
 100, 101, NOW(), '2026-07-08 15:05:00', '2026-07-17 09:00:00', NULL,
 0, 0, 'admin'),

(8, 1, 'L20260008', '孙八', '成都紫气文化', '市场部总监', '13800138006', NULL, 'sunba@ziqi.com', '成都', '成都市锦江区春熙路55号',
 'REFERRAL', 'CULTURE', 'B', 'CONVERTED', NULL,
 107, 200, NOW(), '2026-07-05 11:00:00', NULL, '2026-07-06 09:00:00',
 0, 0, 'admin');

-- ============================================================
-- 2. 客户（crm_customer）- 5 条
--    全部由线索转换而来（source_code = LEAD_CONVERT）
-- ============================================================
INSERT INTO crm_customer
    (id, tenant_id, customer_no, name, normalized_name, customer_type, industry_code, level_code, source_code,
     credit_code, website, phone, email, region, address, status,
     owner_user_id, owner_unit_id, last_activity_time, next_followup_time,
     version, deleted, create_by)
VALUES
(1, 1, 'C20260001', '北京星辰科技有限公司', '北京星辰科技', 'ENTERPRISE', 'TECH', 'A', 'LEAD_CONVERT',
 '91110000MA01ABCX', 'https://www.xingchen.com', '010-88888888', 'contact@xingchen.com', '北京', '北京市朝阳区建国路88号', 'ACTIVE',
 100, 101, '2026-07-13 11:30:00', '2026-07-20 14:00:00',
 0, 0, 'admin'),

(2, 1, 'C20260002', '上海云帆网络有限公司', '上海云帆网络', 'ENTERPRISE', 'TECH', 'A', 'LEAD_CONVERT',
 '91310000MA02DEFY', 'https://www.yunfan.com', '021-66666666', 'contact@yunfan.com', '上海', '上海市浦东新区陆家嘴环路100号', 'ACTIVE',
 101, 101, '2026-07-09 12:00:00', '2026-07-15 09:00:00',
 0, 0, 'admin'),

(3, 1, 'C20260003', '深圳蓝海智能科技有限公司', '深圳蓝海智能', 'STARTUP', 'AI', 'B', 'LEAD_CONVERT',
 '91440300MA03IGHZ', 'https://www.lanhai.ai', '0755-88889999', 'contact@lanhai.ai', '深圳', '深圳市南山区科技园南区', 'DORMANT',
 102, 101, '2026-06-15 10:00:00', '2026-08-01 14:00:00',
 0, 0, 'admin'),

(4, 1, 'C20260004', '广州红日电子商务有限公司', '广州红日电商', 'SME', 'ECOMMERCE', 'A', 'LEAD_CONVERT',
 '91440100MA04JKLW', 'https://www.hongri.com', '020-33334444', 'contact@hongri.com', '广州', '广州市天河区体育西路58号', 'ACTIVE',
 105, 100, '2026-07-10 11:00:00', NULL,
 0, 0, 'admin'),

(5, 1, 'C20260005', '成都紫气文化传媒有限公司', '成都紫气文化', 'SME', 'CULTURE', 'B', 'LEAD_CONVERT',
 '91510100MA05MNVT', 'https://www.ziqi.com', '028-86667777', 'contact@ziqi.com', '成都', '成都市锦江区春熙路55号', 'ACTIVE',
 107, 200, '2026-07-05 11:30:00', '2026-07-18 10:00:00',
 0, 0, 'admin');

-- ============================================================
-- 3. 联系人（crm_contact）- 7 条
--    每个客户至少一个主要联系人，星辰和紫气有2个联系人
-- ============================================================
INSERT INTO crm_contact
    (id, tenant_id, customer_id, name, department, job_title, mobile, phone, email, decision_role,
     primary_flag, status, owner_user_id, owner_unit_id,
     version, deleted, create_by)
VALUES
(1, 1, 1, '张三', '技术部', 'CTO', '13800138001', NULL, 'zhangsan@xingchen.com', 'DECISION_MAKER',
 1, 1, 100, 101,
 0, 0, 'admin'),

(2, 1, 1, '张三丰', '研发部', '架构师', '13800138011', NULL, 'zhangsf@xingchen.com', 'INFLUENCER',
 0, 1, 100, 101,
 0, 0, 'admin'),

(3, 1, 2, '李四', '运营部', 'COO', '13800138002', NULL, 'lisi@yunfan.com', 'DECISION_MAKER',
 1, 1, 101, 101,
 0, 0, 'admin'),

(4, 1, 3, '王五', '产品部', 'CPO', '13800138003', NULL, 'wangwu@lanhai.ai', 'DECISION_MAKER',
 1, 1, 102, 101,
 0, 0, 'admin'),

(5, 1, 4, '钱七', '商务部', '总监', '13800138005', NULL, 'qianqi@hongri.com', 'DECISION_MAKER',
 1, 1, 105, 100,
 0, 0, 'admin'),

(6, 1, 5, '孙八', '市场部', '总监', '13800138006', NULL, 'sunba@ziqi.com', 'DECISION_MAKER',
 1, 1, 107, 200,
 0, 0, 'admin'),

(7, 1, 5, '孙小明', '市场部', '经理', '13800138016', NULL, 'sunxm@ziqi.com', 'INFLUENCER',
 0, 1, 107, 200,
 0, 0, 'admin');

-- ============================================================
-- 4. 商机（crm_opportunity）- 6 条
--    客户1有2个商机（ERP + 数据分析），其他已转换客户各1个
-- ============================================================
INSERT INTO crm_opportunity
    (id, tenant_id, opportunity_no, name, customer_id, primary_contact_id, source_lead_id,
     pipeline_id, stage_id, status, amount, currency_code, probability,
     expected_close_date, actual_close_time, loss_reason,
     stage_change_time, next_followup_time,
     owner_user_id, owner_unit_id,
     version, deleted, create_by)
VALUES
(1, 1, 'O20260001', '星辰-ERP 系统项目', 1, 1, 1,
 1, 2, 'QUALIFICATION', 150000.00, 'CNY', 30,
 '2026-09-30', NULL, NULL,
 '2026-07-10 10:00:00', '2026-07-15 10:00:00',
 100, 101,
 0, 0, 'admin'),

(2, 1, 'O20260002', '云帆-SaaS 平台定制', 2, 3, 2,
 1, 4, 'NEGOTIATION', 280000.00, 'CNY', 80,
 '2026-08-15', NULL, NULL,
 '2026-07-10 10:00:00', '2026-07-15 14:00:00',
 101, 101,
 0, 0, 'admin'),

(3, 1, 'O20260003', '蓝海-AI 客服系统', 3, 4, 3,
 1, 1, 'DISCOVERY', 95000.00, 'CNY', 10,
 '2026-10-20', NULL, NULL,
 '2026-07-11 16:00:00', '2026-07-14 14:00:00',
 102, 101,
 0, 0, 'admin'),

(4, 1, 'O20260004', '红日-订单中台', 4, 5, 5,
 1, 6, 'LOST', 200000.00, 'CNY', 0,
 '2026-07-10', '2026-07-10 16:00:00', '客户选择竞品，价格因素',
 '2026-07-10 16:00:00', NULL,
 105, 100,
 0, 0, 'admin'),

(5, 1, 'O20260005', '紫气-会员营销平台', 5, 6, 8,
 1, 5, 'WON', 120000.00, 'CNY', 100,
 '2026-07-10', '2026-07-10 10:00:00', NULL,
 '2026-07-10 10:00:00', '2026-07-18 10:00:00',
 107, 200,
 0, 0, 'admin'),

(6, 1, 'O20260006', '星辰-数据分析平台', 1, 1, 1,
 1, 3, 'PROPOSAL', 180000.00, 'CNY', 50,
 '2026-11-30', NULL, NULL,
 '2026-07-12 14:00:00', '2026-07-20 09:00:00',
 100, 101,
 0, 0, 'admin');

-- ============================================================
-- 5. 商机阶段历史（crm_opportunity_stage_history）- 10 条
--    记录每个商机的阶段变迁，日期严格递增
-- ============================================================
INSERT INTO crm_opportunity_stage_history
    (id, tenant_id, opportunity_id, from_stage_id, to_stage_id, from_status, to_status,
     change_reason, changed_by_user_id, changed_time, create_by)
VALUES
(1, 1, 1, NULL, 2, NULL, 'QUALIFICATION', '新线索合格判定', 100, '2026-07-10 10:00:00', 'admin'),

(2, 1, 2, NULL, 2, NULL, 'QUALIFICATION', '初步接触后判定合格', 101, '2026-07-05 11:00:00', 'admin'),
(3, 1, 2, 2, 3, 'QUALIFICATION', 'PROPOSAL', '通过资格确认，进入方案阶段', 101, '2026-07-08 15:00:00', 'admin'),
(4, 1, 2, 3, 4, 'PROPOSAL', 'NEGOTIATION', '方案通过，进入商务谈判', 101, '2026-07-10 10:00:00', 'admin'),

(5, 1, 5, NULL, 1, NULL, 'DISCOVERY', 'Lead 转换自动创建', 107, '2026-07-06 09:00:00', 'admin'),
(6, 1, 5, 1, 2, 'DISCOVERY', 'QUALIFICATION', '需求明确', 107, '2026-07-06 14:00:00', 'admin'),
(7, 1, 5, 2, 3, 'QUALIFICATION', 'PROPOSAL', '提交方案', 107, '2026-07-07 10:00:00', 'admin'),
(8, 1, 5, 3, 4, 'PROPOSAL', 'NEGOTIATION', '方案通过', 107, '2026-07-08 11:00:00', 'admin'),
(9, 1, 5, 4, 5, 'NEGOTIATION', 'WON', '签约成功', 107, '2026-07-10 10:00:00', 'admin'),

(10, 1, 6, NULL, 3, NULL, 'PROPOSAL', '直接创建商机', 100, '2026-07-12 14:00:00', 'admin');

-- ============================================================
-- 6. 跟进活动（crm_activity）- 13 条
--    活动 contact_id 必须与 root 实体所属客户的联系人匹配
-- ============================================================
INSERT INTO crm_activity
    (id, tenant_id, root_type, root_id, contact_id, activity_type, subject, content,
     status, planned_start_time, planned_end_time, completed_time, next_action_time,
     performed_by_user_id,
     owner_user_id, owner_unit_id,
     version, deleted, create_by)
VALUES
-- 星辰科技线索跟进（Lead 1 → 转换为客户后继续跟进）
(1, 1, 'LEAD', 1, 1, 'CALL', '首次电话沟通', '与客户CTO张三进行首次电话沟通，了解其ERP系统升级需求，预算约15万。',
 'COMPLETED', '2026-07-10 10:00:00', '2026-07-10 10:30:00', '2026-07-10 10:15:00', NULL,
 100, 100, 101, 0, 0, 'admin'),

(2, 1, 'LEAD', 1, 1, 'MEETING', '需求讨论会', '与星辰科技研发团队进行需求讨论，确认需要ERP系统升级和数据分析两个模块。',
 'COMPLETED', '2026-07-12 14:00:00', '2026-07-12 16:00:00', '2026-07-12 15:30:00', NULL,
 100, 100, 101, 0, 0, 'admin'),

-- 云帆网络线索跟进
(3, 1, 'LEAD', 2, 3, 'VISIT', '拜访客户', '拜访上海云帆网络，了解其SaaS平台定制需求，客户希望8月中旬上线。',
 'COMPLETED', '2026-07-09 11:00:00', '2026-07-09 12:30:00', '2026-07-09 12:00:00', NULL,
 101, 101, 101, 0, 0, 'admin'),

(4, 1, 'LEAD', 2, 3, 'CALL', '报价沟通', '电话沟通SaaS平台定制报价，客户反馈8月中旬需要上线。',
 'PLANNED', '2026-07-15 09:00:00', '2026-07-15 09:30:00', NULL, NULL,
 NULL, 101, 101, 0, 0, 'admin'),

-- 蓝海智能线索跟进
(5, 1, 'LEAD', 3, 4, 'CALL', '初步接触', '与蓝海智能CPO王五初步接触，了解其AI客服系统需求。',
 'COMPLETED', '2026-07-11 16:00:00', '2026-07-11 16:30:00', '2026-07-11 16:20:00', NULL,
 102, 102, 101, 0, 0, 'admin'),

-- 武汉青松医药线索跟进（无联系人，尚未转换）
(6, 1, 'LEAD', 7, NULL, 'EMAIL', '发送产品手册', '向武汉青松医药发送产品手册和案例集。',
 'COMPLETED', '2026-07-08 15:00:00', '2026-07-08 15:10:00', '2026-07-08 15:05:00', NULL,
 100, 100, 101, 0, 0, 'admin'),

-- 星辰科技客户跟进（转换后）
(7, 1, 'CUSTOMER', 1, 1, 'MEETING', '项目启动会', '星辰科技ERP系统项目启动会，确认项目范围和里程碑。',
 'COMPLETED', '2026-07-13 10:00:00', '2026-07-13 12:00:00', '2026-07-13 11:30:00', NULL,
 100, 100, 101, 0, 0, 'admin'),

-- 云帆网络客户跟进
(8, 1, 'CUSTOMER', 2, 3, 'VISIT', '现场调研', '到云帆网络现场调研SaaS平台技术架构。',
 'PLANNED', '2026-07-18 14:00:00', '2026-07-18 16:00:00', NULL, NULL,
 NULL, 101, 101, 0, 0, 'admin'),

-- 紫气文化客户跟进（赢单后售后回访）
(9, 1, 'CUSTOMER', 5, 6, 'CALL', '售后回访', '紫气文化会员营销平台上线后回访，客户使用反馈良好。',
 'COMPLETED', '2026-07-12 11:00:00', '2026-07-12 11:30:00', '2026-07-12 11:30:00', NULL,
 107, 107, 200, 0, 0, 'admin'),

-- 云帆商机跟进
(10, 1, 'OPPORTUNITY', 2, 3, 'MEETING', '方案评审', '云帆SaaS平台定制方案评审，客户认可技术方案。',
 'COMPLETED', '2026-07-10 10:00:00', '2026-07-10 12:00:00', '2026-07-10 12:00:00', NULL,
 101, 101, 101, 0, 0, 'admin'),

-- 星辰数据分析商机跟进
(11, 1, 'OPPORTUNITY', 6, 1, 'PROPOSAL', '提交方案', '向星辰科技提交数据分析平台方案。',
 'PLANNED', '2026-07-20 09:00:00', '2026-07-20 10:00:00', NULL, NULL,
 NULL, 100, 101, 0, 0, 'admin'),

-- 杭州绿竹线索（无效）
(12, 1, 'LEAD', 4, NULL, 'CALL', '无效跟进', '与杭州绿竹教育沟通，客户表示预算不足，暂缓项目。',
 'CANCELLED', '2026-07-08 10:00:00', '2026-07-08 10:30:00', NULL, NULL,
 103, 103, 102, 0, 0, 'admin'),

-- 红日电商客户跟进（赢单后回访）
(13, 1, 'CUSTOMER', 4, 5, 'CALL', '赢单回访', '红日电商订单中台项目赢单后回访，讨论实施计划和时间安排。',
 'COMPLETED', '2026-07-12 14:00:00', '2026-07-12 14:30:00', '2026-07-12 14:30:00', NULL,
 105, 105, 100, 0, 0, 'admin');

-- ============================================================
-- 7. Lead 转换记录（crm_lead_conversion）- 5 条
--    每个已转换的 Lead 都有对应的转换记录，关联客户、联系人和商机
-- ============================================================
INSERT INTO crm_lead_conversion
    (id, tenant_id, lead_id, customer_id, contact_id, opportunity_id,
     converted_by_user_id, converted_time, create_by)
VALUES
(1, 1, 8, 5, 6, 5,
 107, '2026-07-06 09:00:00', 'admin'),

(2, 1, 1, 1, 1, 1,
 100, '2026-07-13 09:00:00', 'admin'),

(3, 1, 2, 2, 3, 2,
 101, '2026-07-05 09:00:00', 'admin'),

(4, 1, 3, 3, 4, 3,
 102, '2026-06-20 09:00:00', 'admin'),

(5, 1, 5, 4, 5, 4,
 105, '2026-07-10 09:00:00', 'admin');

-- ============================================================
-- 8. Owner 变更记录（crm_owner_change_log）- 示例 2 条
-- ============================================================
INSERT INTO crm_owner_change_log
    (tenant_id, entity_type, entity_id, old_owner_user_id, old_owner_unit_id,
     new_owner_user_id, new_owner_unit_id, operation_type, reason,
     operator_user_id, operated_time, create_by)
VALUES
(1, 'CUSTOMER', 3, 100, 101, 102, 101, 'TRANSFER', '客户归属调整到销售1组',
 100, '2026-06-15 10:00:00', 'admin'),

(1, 'CUSTOMER', 4, 105, 100, 105, 100, 'TRANSFER', '客户重新分配',
 1, '2026-07-10 16:00:00', 'admin');

-- ============================================================
-- 验证
-- ============================================================
SELECT '=== 线索统计 ===' AS '';
SELECT status, COUNT(*) AS count FROM crm_lead WHERE deleted = 0 GROUP BY status;

SELECT '=== 客户统计 ===' AS '';
SELECT status, COUNT(*) AS count FROM crm_customer WHERE deleted = 0 GROUP BY status;

SELECT '=== 商机统计 ===' AS '';
SELECT stage_id, status, COUNT(*) AS count, SUM(amount) AS total_amount FROM crm_opportunity WHERE deleted = 0 GROUP BY stage_id, status;

SELECT '=== 活动统计 ===' AS '';
SELECT status, COUNT(*) AS count FROM crm_activity WHERE deleted = 0 GROUP BY status;

SELECT '=== 联系人统计 ===' AS '';
SELECT COUNT(*) AS total_contacts FROM crm_contact WHERE deleted = 0;

SELECT '=== 转换记录 ===' AS '';
SELECT COUNT(*) AS total_conversions FROM crm_lead_conversion;
-- ============================================================
-- Omni-Stack CRM 样例数据
-- 包含：线索、客户、联系人、商机、阶段历史、跟进活动、Lead 转换
-- 执行：docker exec -i omni-mysql mysql -uroot -proot omni_crm < scripts/sql/crm-sample-data.sql
-- ============================================================

USE omni_crm;

SET NAMES utf8mb4;

-- ============================================================
-- 1. 线索（crm_lead）- 8 条
-- ============================================================
INSERT INTO crm_lead
    (tenant_id, lead_no, full_name, company_name, job_title, mobile, phone, email, region, address,
     source_code, industry_code, rating, status, disqualify_reason,
     owner_user_id, owner_unit_id, assigned_time, last_activity_time, next_followup_time, converted_time,
     version, deleted, create_by)
VALUES
(1, 'L20260001', '张三', '北京星辰科技', 'CTO', '13800138001', NULL, 'zhangsan@xingchen.com', '北京', '北京市朝阳区建国路88号',
 'WEB', 'TECH', 'A', 'QUALIFIED', NULL,
 100, 101, NOW(), '2026-07-12 15:30:00', '2026-07-15 10:00:00', NULL,
 0, 0, 'admin'),

(1, 'L20260002', '李四', '上海云帆网络', 'COO', '13800138002', NULL, 'lisi@yunfan.com', '上海', '上海市浦东新区陆家嘴环路100号',
 'REFERRAL', 'TECH', 'A', 'FOLLOWING', NULL,
 101, 101, NOW(), '2026-07-09 12:00:00', '2026-07-15 09:00:00', NULL,
 0, 0, 'admin'),

(1, 'L20260003', '王五', '深圳蓝海智能', 'CPO', '13800138003', NULL, 'wangwu@lanhai.com', '深圳', '深圳市南山区科技园南区',
 'WEINAR', 'AI', 'B', 'NEW', NULL,
 102, 101, NOW(), '2026-07-11 16:20:00', '2026-07-14 14:00:00', NULL,
 0, 0, 'admin'),

(1, 'L20260004', '赵六', '杭州绿竹教育', '校长', '13800138004', NULL, 'zhaoliu@lvzhu.edu', '杭州', '杭州市西湖区文三路200号',
 'CONFERENCE', 'EDU', 'C', 'DISQUALIFIED', '预算不足，暂缓项目',
 103, 102, NOW(), '2026-07-08 10:00:00', NULL, NULL,
 0, 0, 'admin'),

(1, 'L20260005', '钱七', '广州红日电商', '运营总监', '13800138005', NULL, 'qianqi@hongri.com', '广州', '广州市天河区体育西路58号',
 'WEB', 'ECOMMERCE', 'A', 'QUALIFIED', NULL,
 105, 100, NOW(), '2026-07-10 11:00:00', '2026-07-16 10:00:00', NULL,
 0, 0, 'admin'),

(1, 'L20260006', '孙八', '成都紫气文化', '市场部总监', '13800138006', NULL, 'sunba@ziqi.com', '成都', '成都市锦江区春熙路55号',
 'REFERRAL', 'CULTURE', 'B', 'NEW', NULL,
 107, 200, NOW(), NULL, '2026-07-14 16:00:00', NULL,
 0, 0, 'admin'),

(1, 'L20260007', '周九', '武汉青松医药', '采购经理', '13800138007', NULL, 'zhoujiu@qingsong.com', '武汉', '武汉市江夏区光谷大道88号',
 'WEB', 'PHARMA', 'A', 'FOLLOWING', NULL,
 100, 101, NOW(), '2026-07-08 15:05:00', '2026-07-17 09:00:00', NULL,
 0, 0, 'admin'),

(1, 'L20260008', '吴十', '南京黄花物流', '总经理', '13800138008', NULL, 'wushi@huanghua.com', '南京', '南京市栖霞区仙林大学城99号',
 'CONFERENCE', 'LOGISTICS', 'B', 'CONVERTED', NULL,
 103, 102, NOW(), '2026-07-05 11:00:00', NULL, '2026-07-06 09:00:00',
 0, 0, 'admin');

-- ============================================================
-- 2. 客户（crm_customer）- 5 条
-- ============================================================
INSERT INTO crm_customer
    (tenant_id, customer_no, name, normalized_name, customer_type, industry_code, level_code, source_code,
     credit_code, website, phone, email, region, address, status,
     owner_user_id, owner_unit_id, last_activity_time, next_followup_time,
     version, deleted, create_by)
VALUES
(1, 'C20260001', '北京星辰科技有限公司', '北京星辰科技', 'ENTERPRISE', 'TECH', 'A', 'LEAD_CONVERT',
 '91110000MA01ABCX', 'https://www.xingchen.com', '010-88888888', 'contact@xingchen.com', '北京', '北京市朝阳区建国路88号', 'ACTIVE',
 100, 101, '2026-07-13 11:30:00', '2026-07-20 14:00:00',
 0, 0, 'admin'),

(1, 'C20260002', '上海云帆网络有限公司', '上海云帆网络', 'ENTERPRISE', 'TECH', 'A', 'LEAD_CONVERT',
 '91310000MA02DEFY', 'https://www.yunfan.com', '021-66666666', 'contact@yunfan.com', '上海', '上海市浦东新区陆家嘴环路100号', 'POTENTIAL',
 101, 101, '2026-07-09 12:00:00', '2026-07-15 09:00:00',
 0, 0, 'admin'),

(1, 'C20260003', '深圳蓝海智能科技有限公司', '深圳蓝海智能', 'STARTUP', 'AI', 'B', 'LEAD_CONVERT',
 '91440300MA03IGHZ', 'https://www.lanhai.ai', '0755-88889999', 'contact@lanhai.ai', '深圳', '深圳市南山区科技园南区', 'DORMANT',
 102, 101, '2026-06-15 10:00:00', '2026-08-01 14:00:00',
 0, 0, 'admin'),

(1, 'C20260004', '广州红日电子商务有限公司', '广州红日电商', 'SME', 'ECOMMERCE', 'A', 'LEAD_CONVERT',
 '91440100MA04JKLW', 'https://www.hongri.com', '020-33334444', 'contact@hongri.com', '广州', '广州市天河区体育西路58号', 'LOST',
 105, 100, '2026-07-10 11:00:00', NULL,
 0, 0, 'admin'),

(1, 'C20260005', '成都紫气文化传媒有限公司', '成都紫气文化', 'SME', 'CULTURE', 'B', 'LEAD_CONVERT',
 '91510100MA05MNVT', 'https://www.ziqi.com', '028-86667777', 'contact@ziqi.com', '成都', '成都市锦江区春熙路55号', 'ACTIVE',
 107, 200, '2026-07-05 11:30:00', '2026-07-18 10:00:00',
 0, 0, 'admin');

-- ============================================================
-- 3. 联系人（crm_contact）- 7 条
-- ============================================================
INSERT INTO crm_contact
    (tenant_id, customer_id, name, department, job_title, mobile, phone, email, decision_role,
     primary_flag, status, owner_user_id, owner_unit_id,
     version, deleted, create_by)
VALUES
(1, 1, '张三', '技术部', 'CTO', '13800138001', NULL, 'zhangsan@xingchen.com', 'DECISION_MAKER',
 1, 1, 100, 101,
 0, 0, 'admin'),

(1, 1, '张三丰', '研发部', '架构师', '13800138011', NULL, 'zhangsf@xingchen.com', 'INFLUENCER',
 0, 1, 100, 101,
 0, 0, 'admin'),

(1, 2, '李四', '运营部', 'COO', '13800138002', NULL, 'lisi@yunfan.com', 'DECISION_MAKER',
 1, 1, 101, 101,
 0, 0, 'admin'),

(1, 3, '王五', '产品部', 'CPO', '13800138003', NULL, 'wangwu@lanhai.ai', 'DECISION_MAKER',
 1, 1, 102, 101,
 0, 0, 'admin'),

(1, 4, '钱七', '商务部', '总监', '13800138005', NULL, 'qianqi@hongri.com', 'DECISION_MAKER',
 1, 1, 105, 100,
 0, 0, 'admin'),

(1, 5, '孙八', '市场部', '总监', '13800138006', NULL, 'sunba@ziqi.com', 'DECISION_MAKER',
 1, 1, 107, 200,
 0, 0, 'admin'),

(1, 5, '孙小明', '市场部', '经理', '13800138016', NULL, 'sunxm@ziqi.com', 'INFLUENCER',
 0, 1, 107, 200,
 0, 0, 'admin');

-- ============================================================
-- 4. 商机（crm_opportunity）- 6 条
-- ============================================================
INSERT INTO crm_opportunity
    (tenant_id, opportunity_no, name, customer_id, primary_contact_id, source_lead_id,
     pipeline_id, stage_id, status, amount, currency_code, probability,
     expected_close_date, actual_close_time, loss_reason,
     stage_change_time, next_followup_time,
     owner_user_id, owner_unit_id,
     version, deleted, create_by)
VALUES
(1, 'O20260001', '星辰-ERP 系统项目', 1, 1, 1,
 1, 2, 'QUALIFICATION', 150000.00, 'CNY', 30,
 '2026-09-30', NULL, NULL,
 '2026-07-10 10:00:00', '2026-07-15 10:00:00',
 100, 101,
 0, 0, 'admin'),

(1, 'O20260002', '云帆-SaaS 平台定制', 2, 3, 2,
 1, 4, 'NEGOTIATION', 280000.00, 'CNY', 80,
 '2026-08-15', NULL, NULL,
 '2026-07-10 10:00:00', '2026-07-15 14:00:00',
 101, 101,
 0, 0, 'admin'),

(1, 'O20260003', '蓝海-AI 客服系统', 3, 4, 3,
 1, 1, 'DISCOVERY', 95000.00, 'CNY', 10,
 '2026-10-20', NULL, NULL,
 '2026-07-11 16:00:00', '2026-07-14 14:00:00',
 102, 101,
 0, 0, 'admin'),

(1, 'O20260004', '红日-订单中台', 4, 5, 5,
 1, 6, 'LOST', 200000.00, 'CNY', 0,
 '2026-07-10', '2026-07-10 16:00:00', '客户选择竞品，价格因素',
 '2026-07-10 16:00:00', NULL,
 105, 100,
 0, 0, 'admin'),

(1, 'O20260005', '紫气-会员营销平台', 5, 6, 8,
 1, 5, 'WON', 120000.00, 'CNY', 100,
 '2026-07-01', '2026-07-01 10:00:00', NULL,
 '2026-07-01 10:00:00', '2026-07-18 10:00:00',
 107, 200,
 0, 0, 'admin'),

(1, 'O20260006', '星辰-数据分析平台', 1, 1, 1,
 1, 3, 'PROPOSAL', 180000.00, 'CNY', 50,
 '2026-11-30', NULL, NULL,
 '2026-07-12 14:00:00', '2026-07-20 09:00:00',
 100, 101,
 0, 0, 'admin');

-- ============================================================
-- 5. 商机阶段历史（crm_opportunity_stage_history）- 6 条
-- ============================================================
INSERT INTO crm_opportunity_stage_history
    (tenant_id, opportunity_id, from_stage_id, to_stage_id, from_status, to_status,
     change_reason, changed_by_user_id, changed_time, create_by)
VALUES
(1, 1, NULL, 2, NULL, 'QUALIFICATION', '新线索合格判定', 100, '2026-07-10 10:00:00', 'admin'),

(1, 2, NULL, 2, NULL, 'QUALIFICATION', '初步接触后判定合格', 101, '2026-07-05 11:00:00', 'admin'),
(1, 2, 2, 3, 'QUALIFICATION', 'PROPOSAL', '通过资格确认，进入方案阶段', 101, '2026-07-08 15:00:00', 'admin'),
(1, 2, 3, 4, 'PROPOSAL', 'NEGOTIATION', '方案通过，进入商务谈判', 101, '2026-07-10 10:00:00', 'admin'),

(1, 5, NULL, 1, NULL, 'DISCOVERY', 'Lead 转换自动创建', 103, '2026-07-06 09:00:00', 'admin'),
(1, 5, 1, 2, 'DISCOVERY', 'QUALIFICATION', '需求明确', 107, '2026-07-06 14:00:00', 'admin'),
(1, 5, 2, 3, 'QUALIFICATION', 'PROPOSAL', '提交方案', 107, '2026-07-07 10:00:00', 'admin'),
(1, 5, 3, 4, 'PROPOSAL', 'NEGOTIATION', '方案通过', 107, '2026-07-08 11:00:00', 'admin'),
(1, 5, 4, 5, 'NEGOTIATION', 'WON', '签约成功', 107, '2026-07-01 10:00:00', 'admin'),

(1, 6, NULL, 3, NULL, 'PROPOSAL', '直接创建商机', 100, '2026-07-12 14:00:00', 'admin');

-- ============================================================
-- 6. 跟进活动（crm_activity）- 12 条
-- ============================================================
INSERT INTO crm_activity
    (tenant_id, root_type, root_id, contact_id, activity_type, subject, content,
     status, planned_start_time, planned_end_time, completed_time, next_action_time,
     performed_by_user_id,
     owner_user_id, owner_unit_id,
     version, deleted, create_by)
VALUES
(1, 'LEAD', 1, 1, 'CALL', '首次电话沟通', '与客户CTO张三进行首次电话沟通，了解其ERP系统升级需求，预算约15万。',
 'COMPLETED', '2026-07-10 10:00:00', '2026-07-10 10:30:00', '2026-07-10 10:15:00', NULL,
 100,
 100, 101,
 0, 0, 'admin'),

(1, 'LEAD', 1, 1, 'MEETING', '需求讨论会', '与星辰科技研发团队进行需求讨论，确认需要ERP系统升级和数据分析两个模块。',
 'COMPLETED', '2026-07-12 14:00:00', '2026-07-12 16:00:00', '2026-07-12 15:30:00', NULL,
 100,
 100, 101,
 0, 0, 'admin'),

(1, 'LEAD', 2, 3, 'VISIT', '拜访客户', '拜访上海云帆网络，了解其SaaS平台定制需求，客户希望8月中旬上线。',
 'COMPLETED', '2026-07-09 11:00:00', '2026-07-09 12:30:00', '2026-07-09 12:00:00', NULL,
 101,
 101, 101,
 0, 0, 'admin'),

(1, 'LEAD', 2, 3, 'CALL', '报价沟通', '电话沟通SaaS平台定制报价，客户反馈8月中旬需要上线。',
 'PLANNED', '2026-07-15 09:00:00', '2026-07-15 09:30:00', NULL, NULL,
 NULL,
 101, 101,
 0, 0, 'admin'),

(1, 'LEAD', 3, 4, 'CALL', '初步接触', '与蓝海智能CPO王五初步接触，了解其AI客服系统需求。',
 'COMPLETED', '2026-07-11 16:00:00', '2026-07-11 16:30:00', '2026-07-11 16:20:00', NULL,
 102,
 102, 101,
 0, 0, 'admin'),

(1, 'LEAD', 7, NULL, 'EMAIL', '发送产品手册', '向武汉青松医药发送产品手册和案例集。',
 'COMPLETED', '2026-07-08 15:00:00', '2026-07-08 15:10:00', '2026-07-08 15:05:00', NULL,
 100,
 100, 101,
 0, 0, 'admin'),

(1, 'CUSTOMER', 1, 1, 'MEETING', '项目启动会', '星辰科技ERP系统项目启动会，确认项目范围和里程碑。',
 'COMPLETED', '2026-07-13 10:00:00', '2026-07-13 12:00:00', '2026-07-13 11:30:00', NULL,
 100,
 100, 101,
 0, 0, 'admin'),

(1, 'CUSTOMER', 2, 3, 'VISIT', '现场调研', '到云帆网络现场调研SaaS平台技术架构。',
 'PLANNED', '2026-07-18 14:00:00', '2026-07-18 16:00:00', NULL, NULL,
 NULL,
 101, 101,
 0, 0, 'admin'),

(1, 'CUSTOMER', 5, 6, 'CALL', '需求确认', '与紫气文化确认会员营销平台需求细节。',
 'COMPLETED', '2026-07-05 11:00:00', '2026-07-05 11:30:00', '2026-07-05 11:30:00', NULL,
 107,
 107, 200,
 0, 0, 'admin'),

(1, 'OPPORTUNITY', 2, 3, 'MEETING', '方案评审', '云帆SaaS平台定制方案评审，客户认可技术方案。',
 'COMPLETED', '2026-07-10 10:00:00', '2026-07-10 12:00:00', '2026-07-10 12:00:00', NULL,
 101,
 101, 101,
 0, 0, 'admin'),

(1, 'OPPORTUNITY', 6, 1, 'PROPOSAL', '提交方案', '向星辰科技提交数据分析平台方案。',
 'PLANNED', '2026-07-20 09:00:00', '2026-07-20 10:00:00', NULL, NULL,
 NULL,
 100, 101,
 0, 0, 'admin'),

(1, 'LEAD', 4, NULL, 'CALL', '无效跟进', '与杭州绿竹教育沟通，客户表示预算不足，暂缓项目。',
 'CANCELLED', '2026-07-08 10:00:00', '2026-07-08 10:30:00', NULL, NULL,
 103,
 103, 102,
 0, 0, 'admin');

-- ============================================================
-- 7. Lead 转换记录（crm_lead_conversion）- 1 条
-- ============================================================
INSERT INTO crm_lead_conversion
    (tenant_id, lead_id, customer_id, contact_id, opportunity_id,
     converted_by_user_id, converted_time, create_by)
VALUES
(1, 8, 5, 6, 5,
 103, '2026-07-06 09:00:00', 'admin');

-- ============================================================
-- 8. Owner 变更记录（crm_owner_change_log）- 示例 2 条
-- ============================================================
INSERT INTO crm_owner_change_log
    (tenant_id, entity_type, entity_id, old_owner_user_id, old_owner_unit_id,
     new_owner_user_id, new_owner_unit_id, operation_type, reason,
     operator_user_id, operated_time, create_by)
VALUES
(1, 'CUSTOMER', 3, 100, 101, 102, 101, 'TRANSFER', '客户归属调整到销售1组',
 100, '2026-06-15 10:00:00', 'admin'),

(1, 'CUSTOMER', 4, 105, 100, 105, 100, 'TRANSFER', '客户重新分配',
 1, '2026-07-10 16:00:00', 'admin');

-- ============================================================
-- 验证
-- ============================================================
SELECT '=== 线索统计 ===' AS '';
SELECT status, COUNT(*) AS count FROM crm_lead WHERE deleted = 0 GROUP BY status;

SELECT '=== 客户统计 ===' AS '';
SELECT status, COUNT(*) AS count FROM crm_customer WHERE deleted = 0 GROUP BY status;

SELECT '=== 商机统计 ===' AS '';
SELECT stage_id, status, COUNT(*) AS count, SUM(amount) AS total_amount FROM crm_opportunity WHERE deleted = 0 GROUP BY stage_id, status;

SELECT '=== 活动统计 ===' AS '';
SELECT status, COUNT(*) AS count FROM crm_activity WHERE deleted = 0 GROUP BY status;

SELECT '=== 联系人统计 ===' AS '';
SELECT COUNT(*) AS total_contacts FROM crm_contact WHERE deleted = 0;
