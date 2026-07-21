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
    DECLARE v_srm_template_id BIGINT;
    DECLARE v_root_unit_id BIGINT;
    DECLARE v_admin_user_id BIGINT;
    DECLARE v_done       INT DEFAULT 0;

    -- 按 depth 排序保证父节点先插入
    DECLARE cur CURSOR FOR
        SELECT id, parent_id FROM sys_permission
        WHERE tenant_id = 1 ORDER BY depth, id;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        DROP TEMPORARY TABLE IF EXISTS tmp_perm_map;
        RESIGNAL;
    END;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    -- Step 1: 克隆权限树（tenant 1 作为模板）
    START TRANSACTION;
    DROP TEMPORARY TABLE IF EXISTS tmp_perm_map;
    CREATE TEMPORARY TABLE tmp_perm_map (old_id BIGINT PRIMARY KEY, new_id BIGINT);

    OPEN cur;
    perm_loop: LOOP
        FETCH cur INTO v_old_id, v_parent_id;
        IF v_done THEN LEAVE perm_loop; END IF;

        SET v_new_parent_id = IF(v_parent_id = 0, 0,
            IFNULL((SELECT new_id FROM tmp_perm_map WHERE old_id = v_parent_id), 0));

        SET v_new_id = (
            SELECT existing_permission.id
            FROM sys_permission existing_permission
            JOIN sys_permission template_permission ON template_permission.id = v_old_id
            WHERE existing_permission.tenant_id = p_tenant_id
              AND existing_permission.permission_code = template_permission.permission_code
            ORDER BY existing_permission.id
            LIMIT 1
        );

        IF v_new_id IS NULL THEN
            INSERT INTO sys_permission
                (tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
            SELECT p_tenant_id,
                   v_new_parent_id,
                   permission_code, permission_name, type, '', depth, sort, 1, 'system'
            FROM sys_permission template_permission
            WHERE template_permission.id = v_old_id;
            SET v_new_id = LAST_INSERT_ID();
        END IF;

        UPDATE sys_permission target_permission
        JOIN sys_permission template_permission ON template_permission.id = v_old_id
        SET target_permission.parent_id = v_new_parent_id,
            target_permission.permission_name = template_permission.permission_name,
            target_permission.type = template_permission.type,
            target_permission.depth = template_permission.depth,
            target_permission.sort = template_permission.sort,
            target_permission.status = 1,
            target_permission.update_by = 'system'
        WHERE target_permission.id = v_new_id;

        IF v_new_parent_id = 0 THEN
            UPDATE sys_permission SET path = CONCAT('/', v_new_id, '/') WHERE id = v_new_id;
        ELSE
            UPDATE sys_permission child
            JOIN sys_permission parent ON parent.id = v_new_parent_id
            SET child.path = CONCAT(parent.path, v_new_id, '/')
            WHERE child.id = v_new_id;
        END IF;
        INSERT INTO tmp_perm_map (old_id, new_id) VALUES (v_old_id, v_new_id)
        ON DUPLICATE KEY UPDATE new_id = VALUES(new_id);
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
        (p_tenant_id, 'CRM_VIEWER',    'CRM只读员', 'TENANT',         23, 1, 'system'),
        (p_tenant_id, 'SRM_ADMIN',           'SRM管理员',       'TENANT',         30, 1, 'system'),
        (p_tenant_id, 'PROCUREMENT_MANAGER', '采购经理',        'DEPT_AND_BELOW', 31, 1, 'system'),
        (p_tenant_id, 'PROCUREMENT_STAFF',   '采购员',          'SELF',           32, 1, 'system'),
        (p_tenant_id, 'SUPPLIER',            '供应商',          'SELF',           33, 1, 'system')
    ON DUPLICATE KEY UPDATE
        role_name = VALUES(role_name),
        data_scope = VALUES(data_scope),
        sort = VALUES(sort),
        status = 1,
        update_by = 'system';

    -- Step 3: SUPER_ADMIN 获得全部权限
    INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
    SELECT (SELECT id FROM sys_role WHERE tenant_id = p_tenant_id AND role_code = 'SUPER_ADMIN' LIMIT 1),
           new_id FROM tmp_perm_map;

    -- Step 4: USER 角色只读菜单权限
    INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
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
        'workflow:instance','workflow:instance:list','workflow:task:todo',
        'srm','srm:portal','srm:portal:enroll'
    );

    -- Step 5: EMPLOYEE / TEAM_LEADER / DEPT_LEADER 工作流操作权限
    INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
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
    INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
    SELECT r.id, m.new_id
    FROM sys_role r
    CROSS JOIN tmp_perm_map m
    JOIN sys_permission p ON m.old_id = p.id AND p.tenant_id = 1
    WHERE r.tenant_id = p_tenant_id
      AND r.role_code IN ('CRM_ADMIN', 'SALES_MANAGER')
      AND (p.permission_code = 'crm' OR p.permission_code LIKE 'crm:%');

    -- Step 5.2: 销售代表日常销售闭环权限
    INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
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
    INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
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

    -- Step 5.4: SRM 管理员和采购经理获得全部 SRM 权限
    INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
    SELECT r.id, m.new_id
    FROM sys_role r
    CROSS JOIN tmp_perm_map m
    JOIN sys_permission p ON m.old_id = p.id AND p.tenant_id = 1
    WHERE r.tenant_id = p_tenant_id
      AND r.role_code IN ('SRM_ADMIN', 'PROCUREMENT_MANAGER')
      AND (p.permission_code = 'srm' OR p.permission_code LIKE 'srm:%')
      AND p.permission_code NOT IN ('srm:portal:enroll', 'srm:portal:profile', 'srm:portal:evaluation');

    -- Step 5.5: 采购员日常供应商管理权限
    INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
    SELECT r.id, m.new_id
    FROM sys_role r
    CROSS JOIN tmp_perm_map m
    JOIN sys_permission p ON m.old_id = p.id AND p.tenant_id = 1
    WHERE r.tenant_id = p_tenant_id AND r.role_code = 'PROCUREMENT_STAFF'
      AND p.permission_code IN (
          'srm','srm:overview','srm:supplier','srm:evaluation','srm:risk',
          'srm:overview:list','srm:supplier:list','srm:supplier:create','srm:supplier:update',
          'srm:evaluation:list','srm:evaluation:create','srm:evaluation:view',
          'srm:risk:list','srm:risk:update','srm:risk:assess',
          'srm:contact:list','srm:contact:create','srm:contact:update','srm:contact:delete',
          'srm:qualification:list','srm:qualification:create','srm:qualification:update','srm:qualification:delete',
          'srm:bank-account:list','srm:bank-account:create','srm:bank-account:update','srm:bank-account:delete',
          'srm:owner:list'
      );

    -- Step 5.6: SUPPLIER 角色门户权限（企业信息 + 绩效）
    INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
    SELECT r.id, m.new_id
    FROM sys_role r
    CROSS JOIN tmp_perm_map m
    JOIN sys_permission p ON m.old_id = p.id AND p.tenant_id = 1
    WHERE r.tenant_id = p_tenant_id AND r.role_code = 'SUPPLIER'
      AND p.permission_code IN ('srm', 'srm:portal', 'srm:portal:profile', 'srm:portal:evaluation');

    -- Step 6: 创建根组织
    INSERT INTO sys_org_unit (tenant_id, parent_id, name, type, path, depth, sort, status, create_by)
    SELECT p_tenant_id, 0, p_tenant_name, 'ORG', CONCAT('/', p_tenant_id, '/'), 1, 0, 1, 'system'
    WHERE NOT EXISTS (
        SELECT 1 FROM sys_org_unit
        WHERE tenant_id = p_tenant_id AND parent_id = 0
    );

    SET v_root_unit_id = (
        SELECT id FROM sys_org_unit
        WHERE tenant_id = p_tenant_id AND parent_id = 0
        ORDER BY id LIMIT 1
    );

    -- Step 7: 创建管理员账号（默认密码由调用方传入，BCrypt 编码）
    INSERT INTO sys_user (tenant_id, username, password, nickname, gender, primary_unit_id, status, create_by)
    SELECT p_tenant_id, 'admin', p_admin_pwd, CONCAT(p_tenant_name, ' Admin'), 0,
           v_root_unit_id, 1, 'system'
    WHERE NOT EXISTS (
        SELECT 1 FROM sys_user
        WHERE tenant_id = p_tenant_id AND username = 'admin'
    );

    SET v_admin_user_id = (
        SELECT id FROM sys_user
        WHERE tenant_id = p_tenant_id AND username = 'admin'
        ORDER BY id LIMIT 1
    );
    UPDATE sys_user
    SET primary_unit_id = COALESCE(primary_unit_id, v_root_unit_id),
        update_by = 'system'
    WHERE id = v_admin_user_id;

    -- Step 8: 关联管理员角色和组织
    INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (
        v_admin_user_id,
        (SELECT id FROM sys_role WHERE tenant_id = p_tenant_id AND role_code = 'SUPER_ADMIN' LIMIT 1)
    );
    INSERT IGNORE INTO sys_user_unit (user_id, unit_id, is_primary) VALUES (
        v_admin_user_id,
        v_root_unit_id,
        1
    );

    -- Step 9: XSS 防护默认配置 + 预置规则
    INSERT INTO sys_xss_config (tenant_id, enabled, create_by)
    SELECT p_tenant_id, 0, 'system'
    WHERE NOT EXISTS (
        SELECT 1 FROM sys_xss_config WHERE tenant_id = p_tenant_id
    );
    INSERT INTO sys_xss_blacklist_rule (tenant_id, rule_name, rule_type, pattern, enabled, description, sort_order, create_by)
    SELECT p_tenant_id, rule_name, rule_type, pattern, enabled, description, sort_order, 'system'
    FROM sys_xss_blacklist_rule source_rule
    WHERE source_rule.tenant_id = 1
      AND NOT EXISTS (
          SELECT 1 FROM sys_xss_blacklist_rule target_rule
          WHERE target_rule.tenant_id = p_tenant_id
            AND target_rule.rule_type = source_rule.rule_type
            AND target_rule.pattern = source_rule.pattern
      );

    -- Step 10: 幂等初始化 SRM 供应商品类字典
    INSERT INTO omni_base.sys_dict_type
        (tenant_id, type_code, type_name, remark, sort, status, create_by)
    VALUES
        (p_tenant_id, 'srm_supplier_category', '供应商品类', 'SRM供应商品类编码', 20, 1, 'system')
    ON DUPLICATE KEY UPDATE
        type_name = VALUES(type_name),
        remark = VALUES(remark),
        sort = VALUES(sort),
        status = 1,
        update_by = 'system';

    INSERT INTO omni_base.sys_dict_data
        (tenant_id, type_code, dict_value, dict_label, tag_type, sort, status, create_by)
    SELECT p_tenant_id, 'srm_supplier_category', category.dict_value, category.dict_label,
           category.tag_type, category.sort, 1, 'system'
    FROM (
        SELECT 'ELECTRONICS' dict_value, '电子元器件' dict_label, 'primary' tag_type, 1 sort
        UNION ALL SELECT 'IT', '信息技术', 'success', 2
        UNION ALL SELECT 'RAW_MATERIAL', '原材料', 'warning', 3
        UNION ALL SELECT 'ADMIN', '行政物资', 'info', 4
        UNION ALL SELECT 'SERVICE', '服务', 'primary', 5
    ) category
    WHERE NOT EXISTS (
        SELECT 1 FROM omni_base.sys_dict_data dict_data
        WHERE dict_data.tenant_id = p_tenant_id
          AND dict_data.type_code = 'srm_supplier_category'
          AND dict_data.dict_value = category.dict_value
    );

    UPDATE omni_base.sys_dict_data dict_data
    JOIN (
        SELECT 'ELECTRONICS' dict_value, '电子元器件' dict_label, 'primary' tag_type, 1 sort
        UNION ALL SELECT 'IT', '信息技术', 'success', 2
        UNION ALL SELECT 'RAW_MATERIAL', '原材料', 'warning', 3
        UNION ALL SELECT 'ADMIN', '行政物资', 'info', 4
        UNION ALL SELECT 'SERVICE', '服务', 'primary', 5
    ) category ON category.dict_value = dict_data.dict_value
    SET dict_data.dict_label = category.dict_label,
        dict_data.tag_type = category.tag_type,
        dict_data.sort = category.sort,
        dict_data.status = 1,
        dict_data.update_by = 'system'
    WHERE dict_data.tenant_id = p_tenant_id
      AND dict_data.type_code = 'srm_supplier_category';

    -- Step 11: 幂等归一 SRM 默认评估模板与四个启用维度
    SET v_srm_template_id = (
        SELECT id FROM omni_srm.srm_evaluation_template
        WHERE tenant_id = p_tenant_id AND deleted = 0
        ORDER BY status DESC, default_flag DESC, id
        LIMIT 1
    );

    IF v_srm_template_id IS NULL THEN
        INSERT INTO omni_srm.srm_evaluation_template
            (tenant_id, name, status, default_flag, version, deleted, create_by)
        VALUES
            (p_tenant_id, '默认供应商评估模板', 1, 1, 0, 0, 'system');
        SET v_srm_template_id = LAST_INSERT_ID();
    END IF;

    UPDATE omni_srm.srm_evaluation_template
    SET default_flag = IF(id = v_srm_template_id, 1, 0),
        status = IF(id = v_srm_template_id, 1, status),
        update_by = 'system'
    WHERE tenant_id = p_tenant_id AND deleted = 0;

    INSERT INTO omni_srm.srm_evaluation_dimension
        (tenant_id, template_id, indicator_name, weight, sort, status, deleted, create_by)
    VALUES
        (p_tenant_id, v_srm_template_id, '质量', 30.00, 1, 1, 0, 'system'),
        (p_tenant_id, v_srm_template_id, '交期', 30.00, 2, 1, 0, 'system'),
        (p_tenant_id, v_srm_template_id, '价格', 20.00, 3, 1, 0, 'system'),
        (p_tenant_id, v_srm_template_id, '服务', 20.00, 4, 1, 0, 'system')
    ON DUPLICATE KEY UPDATE
        weight = VALUES(weight),
        sort = VALUES(sort),
        status = 1,
        update_by = 'system';

    UPDATE omni_srm.srm_evaluation_dimension
    SET status = 0, update_by = 'system'
    WHERE tenant_id = p_tenant_id
      AND template_id = v_srm_template_id
      AND deleted = 0
      AND indicator_name NOT IN ('质量','交期','价格','服务');

    -- 清理临时表
    DROP TEMPORARY TABLE IF EXISTS tmp_perm_map;
    COMMIT;
END//

DELIMITER ;
