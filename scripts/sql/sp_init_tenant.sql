USE omni_auth;

DROP PROCEDURE IF EXISTS sp_init_tenant;

DELIMITER //

CREATE PROCEDURE sp_init_tenant(
    IN p_tenant_id   BIGINT,
    IN p_tenant_name VARCHAR(200),
    IN p_admin_pwd   VARCHAR(255)
)
BEGIN
    DECLARE v_old_id     BIGINT;
    DECLARE v_parent_id  BIGINT;
    DECLARE v_new_parent_id BIGINT;
    DECLARE v_new_id     BIGINT;
    DECLARE v_done       INT DEFAULT 0;

    -- 按 depth 排序保证父节点先插入
    DECLARE cur CURSOR FOR
        SELECT id, parent_id FROM sys_permission
        WHERE tenant_id = 1 ORDER BY depth, id;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    -- Step 1: 克隆权限树（tenant 1 作为模板）
    CREATE TEMPORARY TABLE tmp_perm_map (old_id BIGINT PRIMARY KEY, new_id BIGINT);

    OPEN cur;
    perm_loop: LOOP
        FETCH cur INTO v_old_id, v_parent_id;
        IF v_done THEN LEAVE perm_loop; END IF;

        SET v_new_parent_id = IF(v_parent_id = 0, 0,
            IFNULL((SELECT new_id FROM tmp_perm_map WHERE old_id = v_parent_id), 0));

        INSERT INTO sys_permission (tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
        SELECT p_tenant_id,
               v_new_parent_id,
               permission_code, permission_name, type, '', depth, sort, 1, 'system'
        FROM sys_permission t WHERE t.id = v_old_id;

        SET v_new_id = LAST_INSERT_ID();
        IF v_new_parent_id = 0 THEN
            UPDATE sys_permission SET path = CONCAT('/', v_new_id, '/') WHERE id = v_new_id;
        ELSE
            UPDATE sys_permission child
            JOIN sys_permission parent ON parent.id = v_new_parent_id
            SET child.path = CONCAT(parent.path, v_new_id, '/')
            WHERE child.id = v_new_id;
        END IF;
        INSERT INTO tmp_perm_map (old_id, new_id) VALUES (v_old_id, v_new_id);
    END LOOP;
    CLOSE cur;

    -- Step 2: 创建默认角色
    INSERT INTO sys_role (tenant_id, role_code, role_name, data_scope, sort, status, create_by) VALUES
        (p_tenant_id, 'SUPER_ADMIN', 'Super Administrator', 'ALL',  0, 1, 'system'),
        (p_tenant_id, 'USER',        'Default User',        'SELF', 99, 1, 'system'),
        (p_tenant_id, 'EMPLOYEE',    '普通员工',             'SELF', 10, 1, 'system'),
        (p_tenant_id, 'TEAM_LEADER', '工作组组长',         'DEPT', 11, 1, 'system'),
        (p_tenant_id, 'DEPT_LEADER', '部门领导', 'DEPT_AND_BELOW', 12, 1, 'system'),
        (p_tenant_id, 'CRM_ADMIN',     'CRM管理员', 'TENANT',         20, 1, 'system'),
        (p_tenant_id, 'SALES_MANAGER', '销售经理',  'DEPT_AND_BELOW', 21, 1, 'system'),
        (p_tenant_id, 'SALES_REP',     '销售代表',  'SELF',           22, 1, 'system'),
        (p_tenant_id, 'CRM_VIEWER',    'CRM只读员', 'TENANT',         23, 1, 'system');

    -- Step 3: SUPER_ADMIN 获得全部权限
    INSERT INTO sys_role_permission (role_id, permission_id)
    SELECT (SELECT id FROM sys_role WHERE tenant_id = p_tenant_id AND role_code = 'SUPER_ADMIN' LIMIT 1),
           new_id FROM tmp_perm_map;

    -- Step 4: USER 角色只读菜单权限
    INSERT INTO sys_role_permission (role_id, permission_id)
    SELECT (SELECT id FROM sys_role WHERE tenant_id = p_tenant_id AND role_code = 'USER' LIMIT 1),
           m.new_id
    FROM tmp_perm_map m
    JOIN sys_permission p ON m.old_id = p.id AND p.tenant_id = 1
    WHERE p.permission_code IN (
        'system:user','system:role','system:permission','system:org','system:tenant',
        'system:user:list','system:role:list','system:permission:list','system:org:list','system:tenant:list',
        'system:oauth2','system:oauth2:list',
        'base:dict','dict:type:list','dict:data:list',
        'base:operlog','base:operlog:list',
        'job:user-job-type','job:user-job-type:list',
        'workflow:definition','workflow:definition:list',
        'workflow:instance','workflow:instance:list','workflow:task:todo'
    );

    -- Step 5: EMPLOYEE / TEAM_LEADER / DEPT_LEADER 工作流操作权限
    INSERT INTO sys_role_permission (role_id, permission_id)
    SELECT r.id, m.new_id
    FROM sys_role r
    CROSS JOIN tmp_perm_map m
    JOIN sys_permission p ON m.old_id = p.id AND p.tenant_id = 1
    WHERE r.tenant_id = p_tenant_id
      AND r.role_code IN ('EMPLOYEE', 'TEAM_LEADER', 'DEPT_LEADER')
      AND p.permission_code IN (
          'workflow:instance:list','workflow:instance:start','workflow:instance:terminate',
          'workflow:task:todo','workflow:approval:complete',
          'workflow:approval:add-signer','workflow:approval:remove-signer','workflow:approval:delegate'
      );

    -- Step 5.1: CRM 管理员和销售经理获得完整 CRM 权限
    INSERT INTO sys_role_permission (role_id, permission_id)
    SELECT r.id, m.new_id
    FROM sys_role r
    CROSS JOIN tmp_perm_map m
    JOIN sys_permission p ON m.old_id = p.id AND p.tenant_id = 1
    WHERE r.tenant_id = p_tenant_id
      AND r.role_code IN ('CRM_ADMIN', 'SALES_MANAGER')
      AND (p.permission_code = 'crm' OR p.permission_code LIKE 'crm:%');

    -- Step 5.2: 销售代表日常销售闭环权限
    INSERT INTO sys_role_permission (role_id, permission_id)
    SELECT r.id, m.new_id
    FROM sys_role r
    CROSS JOIN tmp_perm_map m
    JOIN sys_permission p ON m.old_id = p.id AND p.tenant_id = 1
    WHERE r.tenant_id = p_tenant_id AND r.role_code = 'SALES_REP'
      AND p.permission_code IN (
          'crm','crm:overview','crm:lead','crm:customer','crm:contact','crm:opportunity','crm:activity',
          'crm:overview:list','crm:lead:list','crm:lead:create','crm:lead:update',
          'crm:lead:disqualify','crm:lead:convert',
          'crm:customer:list','crm:customer:create','crm:customer:update','crm:customer:status',
          'crm:contact:list','crm:contact:create','crm:contact:update',
          'crm:opportunity:list','crm:opportunity:create','crm:opportunity:update',
          'crm:opportunity:stage','crm:opportunity:reopen',
          'crm:activity:list','crm:activity:create','crm:activity:update',
          'crm:activity:complete','crm:activity:cancel','crm:owner:list','crm:pii:view'
      );

    -- Step 5.3: CRM 只读角色默认返回脱敏数据
    INSERT INTO sys_role_permission (role_id, permission_id)
    SELECT r.id, m.new_id
    FROM sys_role r
    CROSS JOIN tmp_perm_map m
    JOIN sys_permission p ON m.old_id = p.id AND p.tenant_id = 1
    WHERE r.tenant_id = p_tenant_id AND r.role_code = 'CRM_VIEWER'
      AND p.permission_code IN (
          'crm','crm:overview','crm:lead','crm:customer','crm:contact','crm:opportunity','crm:activity',
          'crm:overview:list','crm:lead:list','crm:customer:list','crm:contact:list',
          'crm:opportunity:list','crm:activity:list'
      );

    -- Step 6: 创建根组织
    INSERT INTO sys_org_unit (tenant_id, parent_id, name, type, path, depth, sort, status, create_by)
    VALUES (p_tenant_id, 0, p_tenant_name, 'ORG', CONCAT('/', p_tenant_id, '/'), 1, 0, 1, 'system');

    -- Step 7: 创建管理员账号（默认密码由调用方传入，BCrypt 编码）
    INSERT INTO sys_user (tenant_id, username, password, nickname, gender, primary_unit_id, status, create_by)
    VALUES (p_tenant_id, 'admin', p_admin_pwd, CONCAT(p_tenant_name, ' Admin'), 0,
            (SELECT id FROM sys_org_unit WHERE tenant_id = p_tenant_id AND parent_id = 0 LIMIT 1),
            1, 'system');

    -- Step 8: 关联管理员角色和组织
    INSERT INTO sys_user_role (user_id, role_id) VALUES (
        LAST_INSERT_ID(),
        (SELECT id FROM sys_role WHERE tenant_id = p_tenant_id AND role_code = 'SUPER_ADMIN' LIMIT 1)
    );
    INSERT INTO sys_user_unit (user_id, unit_id, is_primary) VALUES (
        (SELECT id FROM sys_user WHERE tenant_id = p_tenant_id AND username = 'admin' LIMIT 1),
        (SELECT id FROM sys_org_unit WHERE tenant_id = p_tenant_id AND parent_id = 0 LIMIT 1),
        1
    );

    -- Step 9: XSS 防护默认配置 + 预置规则
    INSERT INTO sys_xss_config (tenant_id, enabled, create_by) VALUES (p_tenant_id, 0, 'system');
    INSERT INTO sys_xss_blacklist_rule (tenant_id, rule_name, rule_type, pattern, enabled, description, sort_order, create_by)
    SELECT p_tenant_id, rule_name, rule_type, pattern, enabled, description, sort_order, 'system'
    FROM sys_xss_blacklist_rule WHERE tenant_id = 1;

    -- 清理临时表
    DROP TEMPORARY TABLE IF EXISTS tmp_perm_map;
END//

DELIMITER ;
