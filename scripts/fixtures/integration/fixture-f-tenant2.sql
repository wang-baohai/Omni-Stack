-- WP-008 F 环境专用 fixture（仅允许在 F 隔离 MySQL 执行，bootstrap-f.ps1 带 UUID 守卫调用）
-- 目的：提供第二个真实 tenant(2002) 的 组织/用户/角色/范围 与 CRM 管道，以及 tenant(1) 的 WP008 专属审批身份，
--       支撑 FW04/FW07/FJ04/FC07 等双租户真实测试。绝不针对 M（守卫拒绝 UUID 814a6039-ac2d-11f1-aa42-a6e45e360df1）。
SET NAMES utf8mb4;

-- ===== 租户 2002 =====
INSERT IGNORE INTO omni_auth.sys_tenant (id, tenant_code, tenant_name, domain, contact_name, contact_phone, status, provisioning_status, create_by)
VALUES (2002, 'wp008f-t2', 'WP008F Tenant2', NULL, 'WP008F', '00000000000', 1, 'ACTIVE', 'wp008f');

-- 组织：2101 DEPT 根 / 2102 TEAM
INSERT IGNORE INTO omni_auth.sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES
(2101, 2002, 0, 'WP008F二部', 'DEPT', 'wp008f-dept2', '/2101/', 1, 10, 1, 'wp008f'),
(2102, 2002, 2101, 'WP008F二组', 'TEAM', 'wp008f-team2', '/2101/2102/', 2, 10, 1, 'wp008f');

-- 角色：复刻 tenant1 的 WP008_APPROVER/EMPLOYEE/SALES_REP/SALES_MANAGER/CRM_VIEWER 权限集到 tenant 2002（role_code 同名）
INSERT IGNORE INTO omni_auth.sys_role (id, tenant_id, role_code, role_name, data_scope, sort, status, create_by)
VALUES
(2111, 2002, 'WP008_APPROVER', 'WP008F审批员', 'TENANT', 10, 1, 'wp008f'),
(2112, 2002, 'EMPLOYEE', 'WP008F员工', 'TENANT', 11, 1, 'wp008f'),
(2113, 2002, 'SALES_REP', 'WP008F销售代表', 'SELF', 12, 1, 'wp008f'),
(2114, 2002, 'SALES_MANAGER', 'WP008F销售经理', 'DEPT_AND_BELOW', 13, 1, 'wp008f'),
(2115, 2002, 'CRM_VIEWER', 'WP008F只读', 'SELF', 14, 1, 'wp008f');

-- role_permission：按 role_code 从 tenant 1 复制（WP008_APPROVER 在 tenant1 由 bootstrap 预置同法生成）
INSERT IGNORE INTO omni_auth.sys_role_permission (role_id, permission_id)
SELECT nr.id, rp.permission_id
FROM omni_auth.sys_role sr
JOIN omni_auth.sys_role_permission rp ON rp.role_id = sr.id
JOIN omni_auth.sys_role nr ON nr.tenant_id = 2002 AND nr.role_code = sr.role_code
WHERE sr.tenant_id = 1;

-- 用户（密码哈希复用 zhangsan 的既有种子哈希，仅供数据完备，不做登录试密）
SET @pwd = (SELECT password FROM omni_auth.sys_user WHERE tenant_id = 1 AND username = 'zhangsan');
INSERT IGNORE INTO omni_auth.sys_user (id, tenant_id, username, password, nickname, email, phone, primary_unit_id, status, create_by)
VALUES
(2201, 2002, 'f2_req',    @pwd, 'WP008F2发起', 'f2.req@example.com', '13900000001', 2102, 1, 'wp008f'),
(2202, 2002, 'f2_ap1',    @pwd, 'WP008F2审批一', 'f2.ap1@example.com', '13900000002', 2102, 1, 'wp008f'),
(2203, 2002, 'f2_ap2',    @pwd, 'WP008F2审批二', 'f2.ap2@example.com', '13900000003', 2102, 1, 'wp008f'),
(2204, 2002, 'f2_sales',  @pwd, 'WP008F2销售', 'f2.sales@example.com', '13900000004', 2102, 1, 'wp008f'),
(2205, 2002, 'f2_mgr',    @pwd, 'WP008F2经理', 'f2.mgr@example.com', '13900000005', 2101, 1, 'wp008f'),
(2206, 2002, 'f2_viewer', @pwd, 'WP008F2只读', 'f2.viewer@example.com', '13900000006', 2102, 1, 'wp008f');

INSERT IGNORE INTO omni_auth.sys_user_role (user_id, role_id) VALUES
(2201, 2112), (2202, 2111), (2203, 2111), (2204, 2113), (2205, 2114), (2206, 2115);

INSERT IGNORE INTO omni_auth.sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status)
SELECT 2002, ur.user_id, ur.role_id, u.primary_unit_id,
       CASE r.role_code WHEN 'SALES_MANAGER' THEN 'DEPT_AND_BELOW' ELSE 'SAME_UNIT' END, 1
FROM omni_auth.sys_user_role ur
JOIN omni_auth.sys_role r ON r.id = ur.role_id
JOIN omni_auth.sys_user u ON u.id = ur.user_id
WHERE r.tenant_id = 2002;

-- tenant 2002 CRM 初始化：config + pipeline + stages（复刻 tenant1 结构）
INSERT IGNORE INTO omni_crm.crm_pipeline (id, tenant_id, code, name, status, default_flag, sort, version, deleted, create_by)
VALUES (2102, 2002, 'DEFAULT', 'WP008F默认销售管道', 1, 1, 0, 0, 0, 'wp008f');
INSERT IGNORE INTO omni_crm.crm_pipeline_stage
    (id, tenant_id, pipeline_id, stage_code, stage_name, stage_type, probability, sort, status, deleted, create_by)
VALUES
(2110, 2002, 2102, 'DISCOVERY',     '需求发现', 'OPEN', 10,  10, 1, 0, 'wp008f'),
(2111, 2002, 2102, 'QUALIFICATION', '资格确认', 'OPEN', 30,  20, 1, 0, 'wp008f'),
(2112, 2002, 2102, 'PROPOSAL',      '方案报价', 'OPEN', 50,  30, 1, 0, 'wp008f'),
(2113, 2002, 2102, 'NEGOTIATION',   '商务谈判', 'OPEN', 80,  40, 1, 0, 'wp008f'),
(2114, 2002, 2102, 'WON',           '赢单',     'WON',  100, 50, 1, 0, 'wp008f'),
(2115, 2002, 2102, 'LOST',          '输单',     'LOST', 0,   60, 1, 0, 'wp008f');
INSERT IGNORE INTO omni_crm.crm_tenant_config (id, tenant_id, default_pipeline_id, currency_code, lead_duplicate_policy, initialized_time, create_by)
VALUES (2101, 2002, 2102, 'CNY', 'WARN', NOW(), 'wp008f');

-- ===== 租户 1 的 WP008 专属身份（不触碰既有种子用户/角色）=====
-- tenant1 角色 WP008_APPROVER（权限复制 EMPLOYEE 的 workflow 子集 + todo）
INSERT IGNORE INTO omni_auth.sys_role (id, tenant_id, role_code, role_name, data_scope, sort, status, create_by)
VALUES (2121, 1, 'WP008_APPROVER', 'WP008审批员(F)', 'TENANT', 40, 1, 'wp008f');
INSERT IGNORE INTO omni_auth.sys_role_permission (role_id, permission_id)
SELECT 2121, p.id FROM omni_auth.sys_permission p
WHERE p.tenant_id = 1 AND p.permission_code IN ('workflow:task:todo','workflow:approval:complete','workflow:instance:list');

-- tenant1 新组织 2103（挂在销售部100下）
INSERT IGNORE INTO omni_auth.sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (2103, 1, 100, 'WP008F审批组', 'TEAM', 'wp008f-team1', '/1/100/2103/', 3, 20, 1, 'wp008f');

INSERT IGNORE INTO omni_auth.sys_user (id, tenant_id, username, password, nickname, email, phone, primary_unit_id, status, create_by)
VALUES
(2210, 1, 'f1_req',    @pwd, 'WP008F1发起', 'f1.req@example.com', '13910000001', 2103, 1, 'wp008f'),
(2211, 1, 'f1_ap1',    @pwd, 'WP008F1审批一', 'f1.ap1@example.com', '13910000002', 2103, 1, 'wp008f'),
(2212, 1, 'f1_ap2',    @pwd, 'WP008F1审批二', 'f1.ap2@example.com', '13910000003', 2103, 1, 'wp008f'),
(2213, 1, 'f1_nocand', @pwd, 'WP008F1非候选', 'f1.nocand@example.com', '13910000004', 2103, 1, 'wp008f');

-- f1_req/f1_nocand=EMPLOYEE（有功能权限），f1_ap1/f1_ap2=WP008_APPROVER
INSERT IGNORE INTO omni_auth.sys_user_role (user_id, role_id) VALUES
(2210, 10), (2211, 2121), (2212, 2121), (2213, 10), (2213, 2121);
INSERT IGNORE INTO omni_auth.sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status) VALUES
(1, 2210, 10, 2103, 'SAME_UNIT', 1),
(1, 2211, 2121, 2103, 'SAME_UNIT', 1),
(1, 2212, 2121, 2103, 'SAME_UNIT', 1),
(1, 2213, 10, 2103, 'SAME_UNIT', 1);
-- f1_nocand 额外挂 WP008_APPROVER 但 scope 指向另一单位（CHILD 单元 101），用于 UNIT 失败/fallback 边界
INSERT IGNORE INTO omni_auth.sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status)
VALUES (1, 2213, 2121, 101, 'SAME_UNIT', 1);

-- tenant1 的 WP008_APPROVER 同构复制到 tenant2 角色 2111 已完成（上方 role_code join 在 2121 存在后需重跑一次）
INSERT IGNORE INTO omni_auth.sys_role_permission (role_id, permission_id)
SELECT 2111, rp.permission_id FROM omni_auth.sys_role_permission rp WHERE rp.role_id = 2121;
