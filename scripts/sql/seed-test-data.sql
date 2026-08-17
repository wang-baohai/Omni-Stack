-- 采购与资产 MVP 测试数据：组织架构 + 角色绑定
-- 密码统一为 123456 (BCrypt hash 与现有用户一致)
SET NAMES utf8mb4;
USE omni_auth;

-- ============================================================
-- 1. 创建测试用户
-- ============================================================

-- 采购经理 (id=300) -> sales-team-1 (unit 101)
INSERT INTO sys_user (id, tenant_id, username, password, nickname, primary_unit_id, status, create_by)
VALUES (300, 1, 'proc_manager', '$2b$10$b2rvxsi2LxHSZxzOuf4jOemAbYsmWGIWW/OhjzGBWWeg60lW/oLCa',
        '采购经理', 101, 1, 'system')
ON DUPLICATE KEY UPDATE primary_unit_id = 101, status = 1;

-- 资产经理 (id=301) -> sales-team-1 (unit 101)
INSERT INTO sys_user (id, tenant_id, username, password, nickname, primary_unit_id, status, create_by)
VALUES (301, 1, 'asset_manager', '$2b$10$b2rvxsi2LxHSZxzOuf4jOemAbYsmWGIWW/OhjzGBWWeg60lW/oLCa',
        '资产经理', 101, 1, 'system')
ON DUPLICATE KEY UPDATE primary_unit_id = 101, status = 1;

-- ============================================================
-- 2. 移动 admin 到叶子组织 (sales-team-1, unit 101)
--    这样 PARENT 锚点能找到 sales-dept(100) 的 DEPT_LEADER
-- ============================================================

UPDATE sys_user SET primary_unit_id = 101 WHERE id = 1 AND tenant_id = 1;

-- ============================================================
-- 3. sys_user_role: 用户角色 (全局)
-- ============================================================

-- proc_manager 角色
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (300, 2);   -- USER
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (300, 10);  -- EMPLOYEE
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (300, 31);  -- PROCUREMENT_MANAGER
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (300, 32);  -- PROCUREMENT_STAFF

-- asset_manager 角色
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (301, 2);   -- USER
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (301, 10);  -- EMPLOYEE
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (301, 41);  -- ASSET_MANAGER
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (301, 42);  -- ASSET_USER

-- admin 追加业务角色
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (1, 2);     -- USER
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (1, 10);    -- EMPLOYEE
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (1, 31);    -- PROCUREMENT_MANAGER
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (1, 41);    -- ASSET_MANAGER

-- ============================================================
-- 4. sys_user_role_scope: 角色作用域绑定 (组织单元级)
--    这是 ScopedRoleAssignmentListener 查询候选人的数据源
-- ============================================================

-- proc_manager 在 sales-team-1 (101) 担任 PROCUREMENT_MANAGER
INSERT INTO sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status)
VALUES (1, 300, 31, 101, 'SAME_UNIT', 1)
ON DUPLICATE KEY UPDATE status = 1;

-- proc_manager 在 sales-team-1 (101) 担任 EMPLOYEE
INSERT INTO sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status)
VALUES (1, 300, 10, 101, 'SAME_UNIT', 1)
ON DUPLICATE KEY UPDATE status = 1;

-- asset_manager 在 sales-team-1 (101) 担任 ASSET_MANAGER
INSERT INTO sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status)
VALUES (1, 301, 41, 101, 'SAME_UNIT', 1)
ON DUPLICATE KEY UPDATE status = 1;

-- asset_manager 在 sales-team-1 (101) 担任 EMPLOYEE
INSERT INTO sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status)
VALUES (1, 301, 10, 101, 'SAME_UNIT', 1)
ON DUPLICATE KEY UPDATE status = 1;

-- admin 在 sales-team-1 (101) 担任 PROCUREMENT_MANAGER (自己提单也能自审)
INSERT INTO sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status)
VALUES (1, 1, 31, 101, 'SAME_UNIT', 1)
ON DUPLICATE KEY UPDATE status = 1;

-- admin 在 sales-team-1 (101) 担任 ASSET_MANAGER
INSERT INTO sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status)
VALUES (1, 1, 41, 101, 'SAME_UNIT', 1)
ON DUPLICATE KEY UPDATE status = 1;

-- admin 在 sales-team-1 (101) 担任 TEAM_LEADER (可自审低金额 IT / 办公用品)
INSERT INTO sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status)
VALUES (1, 1, 11, 101, 'SAME_UNIT', 1)
ON DUPLICATE KEY UPDATE status = 1;

-- admin 在 sales-team-1 (101) 担任 DEPT_LEADER (通用品类低金额自审)
INSERT INTO sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status)
VALUES (1, 1, 12, 101, 'SAME_UNIT', 1)
ON DUPLICATE KEY UPDATE status = 1;

-- admin 在 sales-dept (100) 担任 DEPT_LEADER (可自审高金额通用品类)
INSERT INTO sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status)
VALUES (1, 1, 12, 100, 'SAME_UNIT', 1)
ON DUPLICATE KEY UPDATE status = 1;

-- ============================================================
-- 5. 给现有用户补充缺失的基础角色绑定
-- ============================================================

-- lisi/lisi2 缺少 USER 角色
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (101, 2);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (102, 2);
-- zhaoliu/zhaoliu2 缺少 USER 角色
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (105, 2);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (106, 2);
-- qianqi/qianqi2 缺少 USER 角色
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (107, 2);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (108, 2);
-- zhangsan 缺少 USER 角色
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (100, 2);

-- wangwu/wangwu2 (sales-team-2, unit 102) 缺少 USER 角色
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (103, 2);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (104, 2);
