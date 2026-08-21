-- Omni-Stack 正式幂等种子；由 09a29fe 基线数据机械提取并改为 INSERT IGNORE。
-- 结构由 Liquibase YAML 管理；本文件禁止包含 DDL、账号、授权或存储过程。

INSERT IGNORE INTO srm_evaluation_template (id, tenant_id, name, status, default_flag, version, deleted, create_by)
VALUES (1, 1, '默认供应商评估模板', 1, 1, 0, 0, 'system');

INSERT IGNORE INTO srm_evaluation_dimension (id, tenant_id, template_id, indicator_name, weight, sort, status, deleted, create_by)
VALUES
    (1, 1, 1, '质量', 30.00, 1, 1, 0, 'system'),
    (2, 1, 1, '交期', 30.00, 2, 1, 0, 'system'),
    (3, 1, 1, '价格', 20.00, 3, 1, 0, 'system'),
    (4, 1, 1, '服务', 20.00, 4, 1, 0, 'system');

INSERT IGNORE INTO srm_supplier (id, tenant_id, supplier_no, name, normalized_name, supplier_type, industry_code, credit_code, website, phone, email, region, address, category_code, level_code, status, assigned_time, last_evaluation_time, owner_user_id, owner_unit_id, version, deleted, create_by)
VALUES (1, 1, 'SUP-2026-001', '华信精密制造有限公司', '华信精密制造有限公司', 'MANUFACTURER', 'C39', '91320500MA1EXAMPLE', 'https://www.huaxin-precision.com', '0512-66881234', 'contact@huaxin-precision.com', '江苏省苏州市', '苏州工业园区星湖街328号创新产业园A栋', 'ELECTRONICS', 'STRATEGIC', 'APPROVED', '2026-01-01 09:00:00', '2026-01-15 10:00:00', 1, 1, 0, 0, 'system');

INSERT IGNORE INTO srm_supplier_contact (id, tenant_id, supplier_id, name, department, job_title, mobile, phone, email, decision_role, primary_flag, status, version, deleted, create_by)
VALUES
    (1, 1, 1, '王建国', '销售部', '销售总监', '13912345678', '0512-66881234', 'wangjianguo@huaxin-precision.com', 'DECISION_MAKER', 1, 1, 0, 0, 'system'),
    (2, 1, 1, '李芳', '质量部', '质量经理', '13856781234', '0512-66885678', 'lifang@huaxin-precision.com', 'INFLUENCER', 0, 1, 0, 0, 'system'),
    (3, 1, 1, '赵明', '物流部', '物流主管', '13765432100', '0512-66889012', 'zhaoming@huaxin-precision.com', 'USER', 0, 1, 0, 0, 'system');

INSERT IGNORE INTO srm_supplier_qualification (id, tenant_id, supplier_id, qualification_name, certificate_no, issuing_authority, issue_date, expiry_date, status, version, deleted, create_by)
VALUES
    (1, 1, 1, 'ISO 9001:2015 质量管理体系', 'CN24/30001', '中国质量认证中心(CQC)', '2024-03-15', '2027-03-14', 'ACTIVE', 0, 0, 'system'),
    (2, 1, 1, 'ISO 14001:2015 环境管理体系', 'CN24/E0128', '中国质量认证中心(CQC)', '2024-06-01', '2027-05-31', 'ACTIVE', 0, 0, 'system'),
    (3, 1, 1, 'IATF 16949:2016 汽车行业质量管理体系', 'IATF-2025-0456', 'SGS', '2025-01-10', '2028-01-09', 'ACTIVE', 0, 0, 'system');

INSERT IGNORE INTO srm_supplier_bank_account (id, tenant_id, supplier_id, account_name, account_no, bank_name, bank_branch, bank_code, primary_flag, status, version, deleted, create_by)
VALUES
    (1, 1, 1, '华信精密制造有限公司', '6228480322123456789', '中国工商银行', '苏州工业园区支行', 'ICBC', 1, 1, 0, 0, 'system'),
    (2, 1, 1, '华信精密制造有限公司', '5129021234567890', '招商银行', '苏州分行', 'CMB', 0, 1, 0, 0, 'system');

INSERT IGNORE INTO srm_supplier_portal_user (tenant_id, supplier_id, user_id, create_by)
VALUES (1, 1, 200, 'system');

INSERT IGNORE INTO srm_supplier_enrollment (id, tenant_id, supplier_id, user_id, request_id, status, create_by)
VALUES (1, 1, 1, 200, 'enroll-huaxin-20260101', 'COMPLETED', 'system');

INSERT IGNORE INTO srm_evaluation (id, tenant_id, supplier_id, template_id, evaluation_period, total_score, evaluator_user_id, evaluation_time, status, owner_user_id, owner_unit_id, version, deleted, create_by)
VALUES (1, 1, 1, 1, '2025-Q4', 85.00, 1, '2026-01-15 10:00:00', 'COMPLETED', 1, 1, 0, 0, 'system');

INSERT IGNORE INTO srm_evaluation_item (tenant_id, evaluation_id, dimension_id, indicator_name, score, weight, create_by)
VALUES
    (1, 1, 1, '质量', 4.5, 30.00, 'system'),
    (1, 1, 2, '交期', 4.0, 30.00, 'system'),
    (1, 1, 3, '价格', 4.0, 20.00, 'system'),
    (1, 1, 4, '服务', 4.5, 20.00, 'system');

INSERT IGNORE INTO srm_supplier_invite (id, tenant_id, invite_code_hash, status, expires_time, max_uses, used_count, version, deleted, create_by)
VALUES (1, 1, 'a697e214bda1f51dae035be089555d92ddd9780862096747de5f21890a896abb', 'ACTIVE', DATE_ADD(NOW(), INTERVAL 7 DAY), 1, 0, 0, 0, 'system');

INSERT IGNORE INTO srm_risk_assessment (tenant_id, supplier_id, overall_level, assessment_time, assessor_user_id, remark, create_by)
VALUES (1, 1, 'YELLOW', '2026-01-20 14:00:00', 1, '整体风险可控，需关注财务指标变化趋势', 'system');

INSERT IGNORE INTO srm_risk_indicator (tenant_id, supplier_id, indicator_type, indicator_value, criterion_id, score, risk_level, assessment_time, remark, create_by)
VALUES
    (1, 1, 'FINANCIAL', '流动比率 1.2（行业均值 1.8）', NULL, NULL, 'YELLOW', '2026-01-20 14:00:00', '流动比率偏低，需持续关注', 'system'),
    (1, 1, 'QUALITY', '来料合格率 98.5%', NULL, NULL, 'GREEN', '2026-01-20 14:00:00', '质量表现优秀', 'system'),
    (1, 1, 'SUPPLY', '准时交付率 95.2%', NULL, NULL, 'GREEN', '2026-01-20 14:00:00', '交付表现良好', 'system'),
    (1, 1, 'COMPLIANCE', '应付账款同比增长 35%', NULL, NULL, 'YELLOW', '2026-01-20 14:00:00', '应付账款增幅较大，需关注资金链', 'system'),
    (1, 1, 'COOPERATION', '合作响应正常', NULL, NULL, 'GREEN', '2026-01-20 14:00:00', '合作过程稳定', 'system'),
    (1, 1, 'CERTIFICATE', '全部资质在有效期内', NULL, NULL, 'GREEN', '2026-01-20 14:00:00', '暂无资质到期风险', 'system');

INSERT IGNORE INTO srm_risk_indicator_type (tenant_id, type_code, type_name, description, sort, auto_calc, status, deleted, create_by)
VALUES
    (1, 'FINANCIAL',   '财务风险',   '评估供应商的财务健康状况、偿债能力和资金链稳定性', 1, 0, 1, 0, 'system'),
    (1, 'COMPLIANCE',  '合规风险',   '评估供应商的法规遵从、合同履约和信用记录',         2, 0, 1, 0, 'system'),
    (1, 'SUPPLY',      '供应风险',   '评估供应商的交付能力、产能和供应链稳定性',         3, 0, 1, 0, 'system'),
    (1, 'COOPERATION', '合作风险',   '评估供应商的沟通响应、配合度和服务态度',           4, 0, 1, 0, 'system'),
    (1, 'QUALITY',     '质量风险',   '评估供应商的产品质量、来料合格率和质量体系',       5, 0, 1, 0, 'system'),
    (1, 'CERTIFICATE', '资质证书风险', '根据资质到期日自动计算，无需手动评估',             6, 1, 1, 0, 'system');

INSERT IGNORE INTO srm_risk_criterion (tenant_id, indicator_type_id, criterion_label, score, risk_level, sort, status, deleted, create_by)
SELECT 1, t.id, c.cl, c.s, c.rl, c.so, 1, 0, 'system'
FROM srm_risk_indicator_type t
JOIN (
    SELECT 'FINANCIAL' tc, '流动比率>2，资金充裕' cl, 1 s, 'GREEN' rl, 1 so
    UNION ALL SELECT 'FINANCIAL', '流动比率1~2，资金偏紧', 2, 'YELLOW', 2
    UNION ALL SELECT 'FINANCIAL', '流动比率<1，资金紧张', 3, 'RED', 3
    UNION ALL SELECT 'COMPLIANCE', '无重大合规问题', 1, 'GREEN', 4
    UNION ALL SELECT 'COMPLIANCE', '存在轻微违规，已整改', 2, 'YELLOW', 5
    UNION ALL SELECT 'COMPLIANCE', '存在重大违规或诉讼', 3, 'RED', 6
    UNION ALL SELECT 'SUPPLY', '交货准时率>95%，供应稳定', 1, 'GREEN', 7
    UNION ALL SELECT 'SUPPLY', '交货准时率80~95%，偶尔延迟', 2, 'YELLOW', 8
    UNION ALL SELECT 'SUPPLY', '交货准时率<80%，供应不稳定', 3, 'RED', 9
    UNION ALL SELECT 'COOPERATION', '合作满意度高，沟通顺畅', 1, 'GREEN', 10
    UNION ALL SELECT 'COOPERATION', '合作满意度一般，偶有摩擦', 2, 'YELLOW', 11
    UNION ALL SELECT 'COOPERATION', '合作满意度差，沟通困难', 3, 'RED', 12
    UNION ALL SELECT 'QUALITY', '产品合格率>98%，质量优秀', 1, 'GREEN', 13
    UNION ALL SELECT 'QUALITY', '产品合格率90~98%，质量良好', 2, 'YELLOW', 14
    UNION ALL SELECT 'QUALITY', '产品合格率<90%，质量堪忧', 3, 'RED', 15
) c ON t.type_code = c.tc
WHERE t.tenant_id = 1 AND t.deleted = 0;

INSERT IGNORE INTO srm_risk_score_threshold (tenant_id, risk_level, min_score, max_score, deleted)
VALUES
    (1, 'GREEN',  5,  8,  0),
    (1, 'YELLOW', 9,  12, 0),
    (1, 'RED',    13, 15, 0);
