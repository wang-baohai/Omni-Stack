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
    DECLARE v_permission_created TINYINT DEFAULT 0;
    DECLARE v_srm_template_id BIGINT;
    DECLARE v_root_unit_id BIGINT;
    DECLARE v_admin_user_id BIGINT;
    DECLARE v_proc_config_created INT DEFAULT 0;
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
        SET v_permission_created = 0;

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
            SET v_permission_created = 1;
        END IF;

        -- 仅补齐本次新建节点；重跑不得覆盖租户自定义名称、层级、排序或启用状态。
        IF v_permission_created = 1 THEN
            IF v_new_parent_id = 0 THEN
                UPDATE sys_permission SET path = CONCAT('/', v_new_id, '/') WHERE id = v_new_id;
            ELSE
                UPDATE sys_permission child
                JOIN sys_permission parent ON parent.id = v_new_parent_id
                SET child.path = CONCAT(parent.path, v_new_id, '/')
                WHERE child.id = v_new_id;
            END IF;
        END IF;
        INSERT INTO tmp_perm_map (old_id, new_id) VALUES (v_old_id, v_new_id)
        ON DUPLICATE KEY UPDATE new_id = VALUES(new_id);
    END LOOP;
    CLOSE cur;

    -- Step 2: 创建默认角色
    INSERT IGNORE INTO sys_role (tenant_id, role_code, role_name, data_scope, sort, status, create_by) VALUES
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
        (p_tenant_id, 'SUPPLIER',            '供应商',          'SELF',           33, 1, 'system'),
        (p_tenant_id, 'ASSET_ADMIN',         '资产管理员',      'TENANT',         40, 1, 'system'),
        (p_tenant_id, 'ASSET_MANAGER',       '资产经理',        'DEPT_AND_BELOW', 41, 1, 'system'),
        (p_tenant_id, 'ASSET_USER',          '资产使用人',      'SELF',           42, 1, 'system');

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

    -- Step 5.4: SRM 管理员和采购经理获得 SRM 管理权限，显式排除供应商自助门户
    INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
    SELECT r.id, m.new_id
    FROM sys_role r
    CROSS JOIN tmp_perm_map m
    JOIN sys_permission p ON m.old_id = p.id AND p.tenant_id = 1
    WHERE r.tenant_id = p_tenant_id
      AND r.role_code IN ('SRM_ADMIN', 'PROCUREMENT_MANAGER')
      AND (p.permission_code = 'srm' OR p.permission_code LIKE 'srm:%')
      AND p.permission_code NOT IN (
          'srm:portal:enroll', 'srm:portal:profile',
          'srm:portal:evaluation', 'srm:portal:quotation'
      );

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
      AND p.permission_code IN (
          'srm', 'srm:portal', 'srm:portal:profile',
          'srm:portal:evaluation', 'srm:portal:quotation'
      );

    -- Step 5.7: 采购经理获得 MVP 完整采购权限和 Workflow 审批权限
    INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
    SELECT r.id, m.new_id
    FROM sys_role r
    CROSS JOIN tmp_perm_map m
    JOIN sys_permission p ON m.old_id = p.id AND p.tenant_id = 1
    WHERE r.tenant_id = p_tenant_id
      AND r.role_code = 'PROCUREMENT_MANAGER'
      AND (
          p.permission_code = 'procurement'
          OR p.permission_code LIKE 'procurement:%'
          OR p.permission_code IN (
              'workflow', 'workflow:instance', 'workflow:task:todo',
              'workflow:approval:complete', 'workflow:model:list'
          )
      );

    -- Step 5.8: 采购员获得 SELF 概览、共享物料维护及采购执行权限，不授予审批路由和审批视图
    INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
    SELECT r.id, m.new_id
    FROM sys_role r
    CROSS JOIN tmp_perm_map m
    JOIN sys_permission p ON m.old_id = p.id AND p.tenant_id = 1
    WHERE r.tenant_id = p_tenant_id
      AND r.role_code = 'PROCUREMENT_STAFF'
      AND p.permission_code IN (
          'procurement', 'procurement:overview', 'procurement:overview:list',
          'procurement:material', 'procurement:requisition',
          'procurement:material:list', 'procurement:material:create',
          'procurement:material:update', 'procurement:material:delete',
          'procurement:requisition:list', 'procurement:requisition:create',
          'procurement:requisition:update', 'procurement:requisition:delete',
          'procurement:requisition:submit', 'procurement:requisition:cancel',
          'procurement:rfq', 'procurement:purchase-order', 'procurement:goods-receipt',
          'procurement:rfq:list', 'procurement:rfq:create', 'procurement:rfq:update',
          'procurement:rfq:delete', 'procurement:rfq:send',
          'procurement:rfq:award', 'procurement:rfq:cancel',
          'procurement:purchase-order:list',
          'procurement:purchase-order:update', 'procurement:purchase-order:delete',
          'procurement:purchase-order:send', 'procurement:purchase-order:confirm',
          'procurement:purchase-order:cancel',
          'procurement:goods-receipt:list', 'procurement:goods-receipt:create',
          'procurement:goods-receipt:confirm'
      );

    -- Step 5.9: 普通员工可查看共享物料并在 SELF 范围内发起请购
    INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
    SELECT r.id, m.new_id
    FROM sys_role r
    CROSS JOIN tmp_perm_map m
    JOIN sys_permission p ON m.old_id = p.id AND p.tenant_id = 1
    WHERE r.tenant_id = p_tenant_id
      AND r.role_code = 'EMPLOYEE'
      AND p.permission_code IN (
          'procurement', 'procurement:material', 'procurement:material:list',
          'procurement:requisition', 'procurement:requisition:list',
          'procurement:requisition:create', 'procurement:requisition:update',
          'procurement:requisition:delete', 'procurement:requisition:submit',
          'procurement:requisition:cancel'
      );

    -- Step 5.10: 组长和部门领导兼具请购人能力与审批视图权限
    INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
    SELECT r.id, m.new_id
    FROM sys_role r
    CROSS JOIN tmp_perm_map m
    JOIN sys_permission p ON m.old_id = p.id AND p.tenant_id = 1
    WHERE r.tenant_id = p_tenant_id
      AND r.role_code IN ('TEAM_LEADER', 'DEPT_LEADER')
      AND p.permission_code IN (
          'procurement', 'procurement:material', 'procurement:material:list',
          'procurement:requisition', 'procurement:requisition:list',
          'procurement:requisition:create', 'procurement:requisition:update',
          'procurement:requisition:delete', 'procurement:requisition:submit',
          'procurement:requisition:approve', 'procurement:requisition:cancel'
      );

    -- Step 5.11: 资产管理员和资产经理获得范围内完整 Asset 权限
    INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
    SELECT r.id, m.new_id
    FROM sys_role r
    CROSS JOIN tmp_perm_map m
    JOIN sys_permission p ON m.old_id = p.id AND p.tenant_id = 1
    WHERE r.tenant_id = p_tenant_id
      AND r.role_code IN ('ASSET_ADMIN', 'ASSET_MANAGER')
      AND (
          p.permission_code = 'asset'
          OR p.permission_code LIKE 'asset:%'
          OR p.permission_code IN (
              'workflow', 'workflow:instance', 'workflow:task:todo',
              'workflow:approval:complete', 'workflow:model:list'
          )
      );

    -- Step 5.12: 资产使用人只查看、领用和退还本人当前名下资产
    INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
    SELECT r.id, m.new_id
    FROM sys_role r
    CROSS JOIN tmp_perm_map m
    JOIN sys_permission p ON m.old_id = p.id AND p.tenant_id = 1
    WHERE r.tenant_id = p_tenant_id
      AND r.role_code = 'ASSET_USER'
      AND p.permission_code IN (
          'asset', 'asset:asset', 'asset:asset:self',
          'asset:asset:accept', 'asset:asset:return'
      );

    -- 重跑初始化时也严格收敛 ASSET_USER，避免保留历史越权授权。
    DELETE role_permission
    FROM sys_role_permission role_permission
    JOIN sys_role role ON role.id = role_permission.role_id
    JOIN sys_permission permission ON permission.id = role_permission.permission_id
    WHERE role.tenant_id = p_tenant_id
      AND role.role_code = 'ASSET_USER'
      AND permission.tenant_id = role.tenant_id
      AND (permission.permission_code = 'asset' OR permission.permission_code LIKE 'asset:%')
      AND permission.permission_code NOT IN (
          'asset', 'asset:asset', 'asset:asset:self',
          'asset:asset:accept', 'asset:asset:return'
      );

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

    -- Step 10.5: 幂等初始化采购字典类型和字典数据
INSERT INTO omni_base.sys_dict_type
        (tenant_id, type_code, type_name, remark, sort, status, create_by)
    VALUES
        (p_tenant_id, 'proc_requisition_status',  '请购状态',       '采购请购单的状态枚举',         20, 1, 'system'),
        (p_tenant_id, 'proc_rfq_status',          '询价状态',       '采购询价单的状态枚举',         21, 1, 'system'),
        (p_tenant_id, 'proc_rfq_supplier_status', '供应商报价状态', '询价中受邀供应商的状态枚举',   22, 1, 'system'),
        (p_tenant_id, 'proc_po_status',           '采购订单状态',   '采购订单的状态枚举',           23, 1, 'system'),
        (p_tenant_id, 'proc_gr_status',           '收货状态',       '采购收货单的状态枚举',         24, 1, 'system'),
        (p_tenant_id, 'proc_quality_status',      '质检状态',       '收货行的质检结果状态枚举',     25, 1, 'system'),
        (p_tenant_id, 'proc_material_unit',       '物料计量单位',   '采购物料的计量单位枚举',       26, 1, 'system')
    ON DUPLICATE KEY UPDATE
        type_name = VALUES(type_name),
        remark = VALUES(remark),
        sort = VALUES(sort),
        status = 1,
        update_by = 'system';

INSERT INTO omni_base.sys_dict_data
        (tenant_id, type_code, dict_value, dict_label, tag_type, sort, status, create_by)
    SELECT p_tenant_id, item.type_code, item.dict_value, item.dict_label, item.tag_type, item.sort, 1, 'system'
    FROM (
        SELECT 'proc_requisition_status' type_code, 'DRAFT'     dict_value, '草稿'     dict_label, 'info'    tag_type, 1 sort
        UNION ALL SELECT 'proc_requisition_status',   'SUBMITTED',  '已提交',  'primary', 2
        UNION ALL SELECT 'proc_requisition_status',   'APPROVING',  '审批中',  'warning', 3
        UNION ALL SELECT 'proc_requisition_status',   'APPROVED',   '已通过',  'success', 4
        UNION ALL SELECT 'proc_requisition_status',   'REJECTED',   '已驳回',  'danger',  5
        UNION ALL SELECT 'proc_requisition_status',   'CANCELLED',  '已取消',  'info',    6
        UNION ALL SELECT 'proc_rfq_status',           'DRAFT',      '草稿',    'info',    1
        UNION ALL SELECT 'proc_rfq_status',           'SENT',       '报价中',  'primary', 2
        UNION ALL SELECT 'proc_rfq_status',           'CLOSED',     '已截止',  'warning', 3
        UNION ALL SELECT 'proc_rfq_status',           'AWARDED',    '已定标',  'success', 4
        UNION ALL SELECT 'proc_rfq_status',           'CANCELLED',  '已取消',  'danger',  5
        UNION ALL SELECT 'proc_rfq_supplier_status',  'INVITED',    '已邀请',  'info',    1
        UNION ALL SELECT 'proc_rfq_supplier_status',  'QUOTED',     '已报价',  'primary', 2
        UNION ALL SELECT 'proc_rfq_supplier_status',  'EXPIRED',    '已过期',  'warning', 3
        UNION ALL SELECT 'proc_rfq_supplier_status',  'AWARDED',    '已中标',  'success', 4
        UNION ALL SELECT 'proc_rfq_supplier_status',  'REJECTED',   '未中标',  'danger',  5
        UNION ALL SELECT 'proc_po_status',            'DRAFT',      '草稿',    'info',    1
        UNION ALL SELECT 'proc_po_status',            'SENT',       '已发送',  'primary', 2
        UNION ALL SELECT 'proc_po_status',            'CONFIRMED',  '已确认',  'success', 3
        UNION ALL SELECT 'proc_po_status',            'PARTIAL_RECEIVED', '部分收货', 'warning', 4
        UNION ALL SELECT 'proc_po_status',            'RECEIVED',   '已收货',  'success', 5
        UNION ALL SELECT 'proc_po_status',            'CLOSED',     '已关闭',  'info',    6
        UNION ALL SELECT 'proc_po_status',            'CANCELLED',  '已取消',  'danger',  7
        UNION ALL SELECT 'proc_gr_status',            'DRAFT',      '草稿',    'info',    1
        UNION ALL SELECT 'proc_gr_status',            'CONFIRMED',  '已确认',  'success', 2
        UNION ALL SELECT 'proc_quality_status',       'PASS',       '合格',    'success', 1
        UNION ALL SELECT 'proc_quality_status',       'FAIL',       '不合格',  'danger',  2
        UNION ALL SELECT 'proc_quality_status',       'PENDING',    '待定',    'warning', 3
        UNION ALL SELECT 'proc_material_unit',        'EA',         '个',      'primary', 1
        UNION ALL SELECT 'proc_material_unit',        'PCS',        '件',      'primary', 2
        UNION ALL SELECT 'proc_material_unit',        'UNIT',       '台',      'primary', 3
        UNION ALL SELECT 'proc_material_unit',        'SET',        '套',      'primary', 4
        UNION ALL SELECT 'proc_material_unit',        'KG',         '千克',    'info',    5
        UNION ALL SELECT 'proc_material_unit',        'BOX',        '箱',      'info',    6
        UNION ALL SELECT 'proc_material_unit',        'PACK',       '包',      'info',    7
        UNION ALL SELECT 'proc_material_unit',        'M',          '米',      'info',    8
    ) item
    WHERE NOT EXISTS (
        SELECT 1 FROM omni_base.sys_dict_data dict_data
        WHERE dict_data.tenant_id = p_tenant_id
          AND dict_data.type_code = item.type_code
          AND dict_data.dict_value = item.dict_value
    );

    -- Step 10.6: 幂等初始化 Asset 品类和位置字典
    INSERT IGNORE INTO omni_base.sys_dict_type
        (tenant_id, type_code, type_name, remark, sort, status, create_by)
    VALUES
        (p_tenant_id, 'asset_category', '资产品类', '资产台账品类编码', 30, 1, 'system'),
        (p_tenant_id, 'asset_location', '资产位置', '资产存放位置编码', 31, 1, 'system');

    INSERT INTO omni_base.sys_dict_data
        (tenant_id, type_code, dict_value, dict_label, tag_type, sort, status, create_by)
    SELECT p_tenant_id, definitions.type_code, definitions.dict_value,
           definitions.dict_label, definitions.tag_type, definitions.sort, 1, 'system'
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
        FROM omni_base.sys_dict_data dict_data
        WHERE dict_data.tenant_id = p_tenant_id
          AND dict_data.type_code = definitions.type_code
          AND dict_data.dict_value = definitions.dict_value
    );

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

    -- Step 12: 仅首次创建 Procurement 配置时播种品类，重跑不得覆盖租户设置
    INSERT IGNORE INTO omni_procurement.proc_tenant_config
        (tenant_id, currency_code, initialized_time, version, deleted, create_by)
    VALUES
        (p_tenant_id, 'CNY', NOW(), 0, 0, 'system');
    SET v_proc_config_created = ROW_COUNT();

    IF v_proc_config_created = 1 THEN
        INSERT IGNORE INTO omni_procurement.proc_material_category
            (tenant_id, parent_id, category_code, category_name, sort, status, version, deleted, create_by)
        VALUES
            (p_tenant_id, 0, 'IT_DEVICE',     'IT 设备', 10, 1, 0, 0, 'system'),
            (p_tenant_id, 0, 'OFFICE_SUPPLY', '办公用品', 20, 1, 0, 0, 'system'),
            (p_tenant_id, 0, 'RAW_MATERIAL',  '原材料', 30, 1, 0, 0, 'system'),
            (p_tenant_id, 0, 'OTHER',         '其他', 40, 1, 0, 0, 'system');

        -- 子品类（二级），通过 code 反查父级 ID
        INSERT IGNORE INTO omni_procurement.proc_material_category
            (tenant_id, parent_id, category_code, category_name, sort, status, version, deleted, create_by)
        SELECT p_tenant_id, c.id, v.code, v.name, v.sort, 1, 0, 0, 'system'
        FROM omni_procurement.proc_material_category c
        CROSS JOIN (
            SELECT 'IT_DEVICE' AS parent_code, 'LAPTOP' AS code, '笔记本电脑' AS name, 10 AS sort
            UNION ALL SELECT 'IT_DEVICE', 'MONITOR', '显示器', 20
            UNION ALL SELECT 'IT_DEVICE', 'PERIPHERAL', '外设配件', 30
            UNION ALL SELECT 'OFFICE_SUPPLY', 'STATIONERY', '文具', 10
            UNION ALL SELECT 'OFFICE_SUPPLY', 'PAPER', '纸张耗材', 20
            UNION ALL SELECT 'RAW_MATERIAL', 'METAL', '金属材料', 10
            UNION ALL SELECT 'RAW_MATERIAL', 'ELECTRONIC', '电子元器件', 20
            UNION ALL SELECT 'RAW_MATERIAL', 'PLASTIC', '塑料材料', 30
            UNION ALL SELECT 'OTHER', 'SERVICE', '服务', 10
        ) v
        WHERE c.tenant_id = p_tenant_id AND c.parent_id = 0 AND c.category_code = v.parent_code AND c.deleted = 0;
    END IF;

    -- 清理临时表
    DROP TEMPORARY TABLE IF EXISTS tmp_perm_map;
    COMMIT;
END//

DELIMITER ;
