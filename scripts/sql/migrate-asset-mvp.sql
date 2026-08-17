-- Asset MVP 数据库、权限与角色迁移（幂等）

-- Workflow 启动初始化器会按修正后的分类幂等部署当前草稿。
UPDATE omni_workflow.wf_process_model
SET category = CASE model_key
        WHEN 'asset-transfer' THEN 'ASSET_TRANSFER'
        WHEN 'asset-disposal' THEN 'ASSET_DISPOSAL'
        ELSE category
    END,
    update_by = 'system'
WHERE model_key IN ('asset-transfer', 'asset-disposal')
  AND category <> CASE model_key
        WHEN 'asset-transfer' THEN 'ASSET_TRANSFER'
        WHEN 'asset-disposal' THEN 'ASSET_DISPOSAL'
        ELSE category
    END;

CREATE DATABASE IF NOT EXISTS omni_asset
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE omni_asset;

CREATE TABLE IF NOT EXISTS ast_asset (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    asset_no VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    category_code VARCHAR(64) NOT NULL,
    specification VARCHAR(500) DEFAULT NULL,
    brand VARCHAR(100) DEFAULT NULL,
    model VARCHAR(100) DEFAULT NULL,
    supplier_id BIGINT DEFAULT NULL,
    supplier_name_snapshot VARCHAR(200) DEFAULT NULL,
    source_po_id BIGINT DEFAULT NULL,
    source_gr_id BIGINT DEFAULT NULL,
    source_gr_line_id BIGINT DEFAULT NULL,
    source_unit_sequence INT DEFAULT NULL,
    source_po_no VARCHAR(64) DEFAULT NULL,
    source_gr_no VARCHAR(64) DEFAULT NULL,
    purchase_date DATE DEFAULT NULL,
    purchase_amount DECIMAL(18,2) DEFAULT NULL,
    currency_code VARCHAR(10) NOT NULL DEFAULT 'CNY',
    location_code VARCHAR(100) DEFAULT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'IN_STOCK',
    current_user_id BIGINT DEFAULT NULL,
    current_unit_id BIGINT DEFAULT NULL,
    allocated_time DATETIME DEFAULT NULL,
    active_operation_type VARCHAR(20) DEFAULT NULL,
    active_operation_id BIGINT DEFAULT NULL,
    warranty_expiry_date DATE DEFAULT NULL,
    expected_life_years INT DEFAULT NULL,
    remark VARCHAR(1000) DEFAULT NULL,
    owner_user_id BIGINT NOT NULL,
    owner_unit_id BIGINT NOT NULL,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    UNIQUE KEY uk_ast_asset_no (tenant_id, asset_no),
    UNIQUE KEY uk_ast_asset_tenant_id (tenant_id, id),
    UNIQUE KEY uk_ast_asset_source_unit (tenant_id, source_gr_line_id, source_unit_sequence),
    UNIQUE KEY uk_ast_asset_active_operation (tenant_id, active_operation_type, active_operation_id),
    INDEX idx_ast_asset_owner_status (tenant_id, owner_unit_id, owner_user_id, status),
    INDEX idx_ast_asset_current_user (tenant_id, current_user_id, status),
    INDEX idx_ast_asset_current_unit (tenant_id, current_unit_id, status),
    INDEX idx_ast_asset_category_status (tenant_id, category_code, status),
    INDEX idx_ast_asset_active (tenant_id, active_operation_type, active_operation_id),
    CONSTRAINT chk_ast_asset_status CHECK (status IN (
        'IN_STOCK','ALLOCATED','IN_USE','MAINTENANCE','TRANSFER',
        'DISPOSAL_PENDING','DISPOSED','SCRAPPED'
    )),
    CONSTRAINT chk_ast_asset_source_sequence CHECK (
        (source_gr_line_id IS NULL AND source_unit_sequence IS NULL)
        OR (source_gr_line_id IS NOT NULL AND source_unit_sequence > 0)
    ),
    CONSTRAINT chk_ast_asset_amount CHECK (purchase_amount IS NULL OR purchase_amount >= 0),
    CONSTRAINT chk_ast_asset_life CHECK (expected_life_years IS NULL OR expected_life_years > 0),
    CONSTRAINT chk_ast_asset_active_pair CHECK (
        (active_operation_type IS NULL AND active_operation_id IS NULL)
        OR (active_operation_type IN ('TRANSFER','DISPOSAL') AND active_operation_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资产台账';

CREATE TABLE IF NOT EXISTS ast_asset_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    from_status VARCHAR(32) DEFAULT NULL,
    to_status VARCHAR(32) NOT NULL,
    changed_by_user_id BIGINT NOT NULL,
    changed_time DATETIME NOT NULL,
    remark VARCHAR(1000) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    INDEX idx_ast_history_asset_time (tenant_id, asset_id, changed_time, id),
    CONSTRAINT fk_ast_history_asset FOREIGN KEY (tenant_id, asset_id)
        REFERENCES ast_asset(tenant_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资产不可变变更历史';

CREATE TABLE IF NOT EXISTS ast_transfer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    transfer_no VARCHAR(64) NOT NULL,
    asset_id BIGINT NOT NULL,
    from_user_id BIGINT DEFAULT NULL,
    from_unit_id BIGINT DEFAULT NULL,
    to_user_id BIGINT NOT NULL,
    to_unit_id BIGINT NOT NULL,
    from_location VARCHAR(100) DEFAULT NULL,
    to_location VARCHAR(100) DEFAULT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_APPROVAL',
    previous_asset_status VARCHAR(32) NOT NULL,
    active_flag TINYINT NOT NULL DEFAULT 1,
    model_version_id BIGINT NOT NULL,
    workflow_start_user_id BIGINT NOT NULL COMMENT 'Workflow 原始发起人用户 ID',
    workflow_start_user_name VARCHAR(100) DEFAULT NULL COMMENT 'Workflow 原始发起人用户名快照',
    workflow_request_id VARCHAR(64) NOT NULL,
    workflow_business_key VARCHAR(255) NOT NULL,
    workflow_start_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    process_instance_id VARCHAR(64) DEFAULT NULL,
    approved_time DATETIME DEFAULT NULL,
    completed_time DATETIME DEFAULT NULL,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    UNIQUE KEY uk_ast_transfer_no (tenant_id, transfer_no),
    UNIQUE KEY uk_ast_transfer_workflow_request (tenant_id, workflow_request_id),
    UNIQUE KEY uk_ast_transfer_business_key (tenant_id, workflow_business_key),
    INDEX idx_ast_transfer_asset_active (tenant_id, asset_id, active_flag, status),
    INDEX idx_ast_transfer_process (tenant_id, process_instance_id),
    CONSTRAINT fk_ast_transfer_asset FOREIGN KEY (tenant_id, asset_id)
        REFERENCES ast_asset(tenant_id, id),
    CONSTRAINT chk_ast_transfer_status CHECK (status IN (
        'PENDING_APPROVAL','START_FAILED','APPROVED','REJECTED','COMPLETED','CANCELLED'
    )),
    CONSTRAINT chk_ast_transfer_start CHECK (workflow_start_status IN ('PENDING','STARTED','FAILED')),
    CONSTRAINT chk_ast_transfer_active CHECK (active_flag IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资产调拨申请';

CREATE TABLE IF NOT EXISTS ast_disposal (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    disposal_no VARCHAR(64) NOT NULL,
    asset_id BIGINT NOT NULL,
    disposal_type VARCHAR(20) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    previous_asset_status VARCHAR(32) NOT NULL,
    residual_value DECIMAL(18,2) DEFAULT NULL,
    disposal_method VARCHAR(500) DEFAULT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_APPROVAL',
    active_flag TINYINT NOT NULL DEFAULT 1,
    model_version_id BIGINT NOT NULL,
    workflow_start_user_id BIGINT NOT NULL COMMENT 'Workflow 原始发起人用户 ID',
    workflow_start_user_name VARCHAR(100) DEFAULT NULL COMMENT 'Workflow 原始发起人用户名快照',
    workflow_request_id VARCHAR(64) NOT NULL,
    workflow_business_key VARCHAR(255) NOT NULL,
    workflow_start_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    process_instance_id VARCHAR(64) DEFAULT NULL,
    approved_time DATETIME DEFAULT NULL,
    completed_time DATETIME DEFAULT NULL,
    final_approver_user_id BIGINT DEFAULT NULL,
    final_approver_remark VARCHAR(1000) DEFAULT NULL,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    UNIQUE KEY uk_ast_disposal_no (tenant_id, disposal_no),
    UNIQUE KEY uk_ast_disposal_workflow_request (tenant_id, workflow_request_id),
    UNIQUE KEY uk_ast_disposal_business_key (tenant_id, workflow_business_key),
    INDEX idx_ast_disposal_asset_active (tenant_id, asset_id, active_flag, status),
    INDEX idx_ast_disposal_process (tenant_id, process_instance_id),
    CONSTRAINT fk_ast_disposal_asset FOREIGN KEY (tenant_id, asset_id)
        REFERENCES ast_asset(tenant_id, id),
    CONSTRAINT chk_ast_disposal_type CHECK (disposal_type IN ('DISCARD','SCRAP')),
    CONSTRAINT chk_ast_disposal_status CHECK (status IN (
        'PENDING_APPROVAL','START_FAILED','APPROVED','REJECTED','COMPLETED','CANCELLED'
    )),
    CONSTRAINT chk_ast_disposal_start CHECK (workflow_start_status IN ('PENDING','STARTED','FAILED')),
    CONSTRAINT chk_ast_disposal_active CHECK (active_flag IN (0,1)),
    CONSTRAINT chk_ast_disposal_residual CHECK (residual_value IS NULL OR residual_value >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资产处置申请';

CREATE TABLE IF NOT EXISTS ast_inbox_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    consumer_name VARCHAR(100) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    source_service VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(64) DEFAULT NULL,
    aggregate_id VARCHAR(128) DEFAULT NULL,
    payload JSON NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    processed_time DATETIME DEFAULT NULL,
    error_message VARCHAR(500) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ast_inbox_consumer_event (consumer_name, event_id),
    INDEX idx_ast_inbox_status (tenant_id, status, create_time),
    CONSTRAINT chk_ast_inbox_status CHECK (status IN ('RECEIVED','PROCESSED','IGNORED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资产跨服务领域事件收件箱';

CREATE TABLE IF NOT EXISTS sys_mq_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    msg_id VARCHAR(36) NOT NULL COMMENT '业务消息ID',
    topic VARCHAR(128) NOT NULL COMMENT 'MQ Topic',
    binding_name VARCHAR(128) NOT NULL COMMENT 'Stream binding',
    tag VARCHAR(64) DEFAULT NULL,
    msg_key VARCHAR(128) DEFAULT NULL COMMENT '事件ID或业务键',
    payload TEXT NOT NULL COMMENT '不含PII的消息体',
    broker_type VARCHAR(32) NOT NULL DEFAULT 'rocketmq',
    status TINYINT NOT NULL DEFAULT 0,
    retry_count INT NOT NULL DEFAULT 0,
    max_retry INT NOT NULL DEFAULT 3,
    next_retry_time DATETIME DEFAULT NULL,
    error_msg VARCHAR(512) DEFAULT NULL,
    service_name VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ast_mq_msg_id (msg_id),
    INDEX idx_ast_mq_relay (status, next_retry_time),
    INDEX idx_ast_mq_tenant_time (tenant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Asset可靠消息发件箱';

-- 兼容早期已创建的 Asset 操作表：补齐 Workflow 原始发起人快照后再收紧非空约束。
DELIMITER //

DROP PROCEDURE IF EXISTS sp_asset_add_column//
CREATE PROCEDURE sp_asset_add_column(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND COLUMN_NAME = p_column_name
    ) THEN
        SET @asset_ddl = CONCAT(
            'ALTER TABLE `', p_table_name, '` ADD COLUMN `',
            p_column_name, '` ', p_definition
        );
        PREPARE asset_stmt FROM @asset_ddl;
        EXECUTE asset_stmt;
        DEALLOCATE PREPARE asset_stmt;
    END IF;
END//

DELIMITER ;

CALL sp_asset_add_column(
    'ast_transfer',
    'workflow_start_user_id',
    'BIGINT DEFAULT NULL COMMENT ''Workflow 原始发起人用户 ID'' AFTER `model_version_id`'
);
CALL sp_asset_add_column(
    'ast_transfer',
    'workflow_start_user_name',
    'VARCHAR(100) DEFAULT NULL COMMENT ''Workflow 原始发起人用户名快照'' AFTER `workflow_start_user_id`'
);
CALL sp_asset_add_column(
    'ast_disposal',
    'workflow_start_user_id',
    'BIGINT DEFAULT NULL COMMENT ''Workflow 原始发起人用户 ID'' AFTER `model_version_id`'
);
CALL sp_asset_add_column(
    'ast_disposal',
    'workflow_start_user_name',
    'VARCHAR(100) DEFAULT NULL COMMENT ''Workflow 原始发起人用户名快照'' AFTER `workflow_start_user_id`'
);

DELIMITER //

DROP PROCEDURE IF EXISTS sp_asset_assert_workflow_initiator//
CREATE PROCEDURE sp_asset_assert_workflow_initiator()
BEGIN
    IF EXISTS (
        SELECT 1 FROM ast_transfer
        WHERE workflow_start_user_id IS NULL OR workflow_start_user_id <= 0
    ) OR EXISTS (
        SELECT 1 FROM ast_disposal
        WHERE workflow_start_user_id IS NULL OR workflow_start_user_id <= 0
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Asset 历史操作缺少可解析的 Workflow 原始发起人，请先完成数据修复';
    END IF;
END//

DELIMITER ;

UPDATE ast_transfer operation_record
JOIN omni_auth.sys_user initiator
  ON initiator.tenant_id = operation_record.tenant_id
 AND initiator.username = operation_record.create_by
SET operation_record.workflow_start_user_id = initiator.id,
    operation_record.workflow_start_user_name = COALESCE(
        NULLIF(operation_record.workflow_start_user_name, ''),
        initiator.username
    )
WHERE operation_record.workflow_start_user_id IS NULL
   OR operation_record.workflow_start_user_id <= 0;

UPDATE ast_disposal operation_record
JOIN omni_auth.sys_user initiator
  ON initiator.tenant_id = operation_record.tenant_id
 AND initiator.username = operation_record.create_by
SET operation_record.workflow_start_user_id = initiator.id,
    operation_record.workflow_start_user_name = COALESCE(
        NULLIF(operation_record.workflow_start_user_name, ''),
        initiator.username
    )
WHERE operation_record.workflow_start_user_id IS NULL
   OR operation_record.workflow_start_user_id <= 0;

CALL sp_asset_assert_workflow_initiator();

ALTER TABLE ast_transfer
    MODIFY COLUMN workflow_start_user_id BIGINT NOT NULL
        COMMENT 'Workflow 原始发起人用户 ID' AFTER model_version_id,
    MODIFY COLUMN workflow_start_user_name VARCHAR(100) DEFAULT NULL
        COMMENT 'Workflow 原始发起人用户名快照' AFTER workflow_start_user_id;

ALTER TABLE ast_disposal
    MODIFY COLUMN workflow_start_user_id BIGINT NOT NULL
        COMMENT 'Workflow 原始发起人用户 ID' AFTER model_version_id,
    MODIFY COLUMN workflow_start_user_name VARCHAR(100) DEFAULT NULL
        COMMENT 'Workflow 原始发起人用户名快照' AFTER workflow_start_user_id;

DROP PROCEDURE IF EXISTS sp_asset_assert_workflow_initiator;
DROP PROCEDURE IF EXISTS sp_asset_add_column;

USE omni_auth;

-- MySQL DDL 会隐式提交；从此处开始将角色、权限和字典种子作为跨库 InnoDB 事务统一提交。
START TRANSACTION;

INSERT IGNORE INTO sys_role (tenant_id, role_code, role_name, data_scope, sort, status, create_by)
SELECT tenant.id, definitions.role_code, definitions.role_name,
       definitions.data_scope, definitions.sort, 1, 'system'
FROM sys_tenant tenant
CROSS JOIN (
    SELECT 'ASSET_ADMIN' role_code, '资产管理员' role_name, 'TENANT' data_scope, 40 sort
    UNION ALL SELECT 'ASSET_MANAGER', '资产经理', 'DEPT_AND_BELOW', 41
    UNION ALL SELECT 'ASSET_USER', '资产使用人', 'SELF', 42
) definitions;

DROP TEMPORARY TABLE IF EXISTS tmp_asset_permission;
CREATE TEMPORARY TABLE tmp_asset_permission (
    permission_id BIGINT PRIMARY KEY
);

INSERT INTO sys_permission
    (tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
SELECT tenant.id, 0, 'asset', '资产管理', 'DIRECTORY', '', 1, 8, 1, 'asset_mvp_migration'
FROM sys_tenant tenant
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission permission
    WHERE permission.tenant_id = tenant.id AND permission.permission_code = 'asset'
);

INSERT INTO sys_permission
    (tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
SELECT root.tenant_id, root.id, definitions.permission_code, definitions.permission_name,
       'MENU', '', 2, definitions.sort, 1, 'asset_mvp_migration'
FROM sys_permission root
CROSS JOIN (
    SELECT 'asset:overview' permission_code, '资产概览' permission_name, 1 sort
    UNION ALL SELECT 'asset:asset', '资产台账', 2
    UNION ALL SELECT 'asset:transfer', '资产调拨', 3
    UNION ALL SELECT 'asset:disposal', '资产处置', 4
) definitions
WHERE root.permission_code = 'asset'
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission existing
      WHERE existing.tenant_id = root.tenant_id
        AND existing.permission_code = definitions.permission_code
  );

INSERT INTO sys_permission
    (tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
SELECT menu.tenant_id, menu.id, definitions.permission_code, definitions.permission_name,
       'API', '', 3, definitions.sort, 1, 'asset_mvp_migration'
FROM sys_permission menu
JOIN (
    SELECT 'asset:overview' menu_code, 'asset:overview:list' permission_code, '查看资产概览' permission_name, 1 sort
    UNION ALL SELECT 'asset:asset', 'asset:asset:list', '查看资产台账', 1
    UNION ALL SELECT 'asset:asset', 'asset:asset:self', '查看我的资产', 2
    UNION ALL SELECT 'asset:asset', 'asset:asset:create', '创建资产', 3
    UNION ALL SELECT 'asset:asset', 'asset:asset:update', '更新资产', 4
    UNION ALL SELECT 'asset:asset', 'asset:asset:delete', '删除资产', 5
    UNION ALL SELECT 'asset:asset', 'asset:asset:allocate', '分配资产', 6
    UNION ALL SELECT 'asset:asset', 'asset:asset:accept', '确认领用资产', 7
    UNION ALL SELECT 'asset:asset', 'asset:asset:return', '退还资产', 8
    UNION ALL SELECT 'asset:asset', 'asset:asset:maintenance', '维护资产状态', 9
    UNION ALL SELECT 'asset:transfer', 'asset:transfer:list', '查看资产调拨', 1
    UNION ALL SELECT 'asset:transfer', 'asset:transfer:create', '创建资产调拨', 2
    UNION ALL SELECT 'asset:transfer', 'asset:transfer:approve', '审批资产调拨视图', 3
    UNION ALL SELECT 'asset:transfer', 'asset:transfer:complete', '完成资产调拨', 4
    UNION ALL SELECT 'asset:transfer', 'asset:transfer:cancel', '取消资产调拨', 5
    UNION ALL SELECT 'asset:transfer', 'asset:transfer:retry', '重试调拨流程', 6
    UNION ALL SELECT 'asset:disposal', 'asset:disposal:list', '查看资产处置', 1
    UNION ALL SELECT 'asset:disposal', 'asset:disposal:create', '创建资产处置', 2
    UNION ALL SELECT 'asset:disposal', 'asset:disposal:approve', '审批资产处置视图', 3
    UNION ALL SELECT 'asset:disposal', 'asset:disposal:complete', '完成资产处置', 4
    UNION ALL SELECT 'asset:disposal', 'asset:disposal:cancel', '取消资产处置', 5
    UNION ALL SELECT 'asset:disposal', 'asset:disposal:retry', '重试处置流程', 6
) definitions ON definitions.menu_code = menu.permission_code
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission existing
    WHERE existing.tenant_id = menu.tenant_id
      AND existing.permission_code = definitions.permission_code
);

INSERT IGNORE INTO tmp_asset_permission (permission_id)
SELECT id
FROM sys_permission
WHERE create_by = 'asset_mvp_migration'
  AND (permission_code = 'asset' OR permission_code LIKE 'asset:%');

UPDATE sys_permission
SET path = CONCAT('/', id, '/')
WHERE id IN (SELECT permission_id FROM tmp_asset_permission)
  AND permission_code = 'asset';

UPDATE sys_permission child
JOIN sys_permission parent ON parent.id = child.parent_id AND parent.tenant_id = child.tenant_id
JOIN tmp_asset_permission created ON created.permission_id = child.id
SET child.path = CONCAT(parent.path, child.id, '/')
WHERE child.permission_code LIKE 'asset:%' AND child.depth = 2;

UPDATE sys_permission child
JOIN sys_permission parent ON parent.id = child.parent_id AND parent.tenant_id = child.tenant_id
JOIN tmp_asset_permission created ON created.permission_id = child.id
SET child.path = CONCAT(parent.path, child.id, '/')
WHERE child.permission_code LIKE 'asset:%' AND child.depth = 3;

UPDATE sys_permission
SET create_by = 'system'
WHERE id IN (SELECT permission_id FROM tmp_asset_permission);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.tenant_id = role.tenant_id
WHERE role.role_code IN ('SUPER_ADMIN', 'ASSET_ADMIN', 'ASSET_MANAGER')
  AND (permission.permission_code = 'asset' OR permission.permission_code LIKE 'asset:%');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.tenant_id = role.tenant_id
WHERE role.role_code IN ('ASSET_ADMIN', 'ASSET_MANAGER')
  AND permission.permission_code IN (
      'workflow', 'workflow:instance', 'workflow:task:todo',
      'workflow:approval:complete', 'workflow:model:list'
  );

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.tenant_id = role.tenant_id
WHERE role.role_code = 'ASSET_USER'
  AND permission.permission_code IN (
      'asset', 'asset:asset', 'asset:asset:self',
      'asset:asset:accept', 'asset:asset:return'
  );

-- ASSET_USER 仅保留“我的资产”、确认领用和退还能力，清理早期或重复迁移留下的其他 Asset 授权。
DELETE role_permission
FROM sys_role_permission role_permission
JOIN sys_role role ON role.id = role_permission.role_id
JOIN sys_permission permission ON permission.id = role_permission.permission_id
WHERE role.role_code = 'ASSET_USER'
  AND permission.tenant_id = role.tenant_id
  AND (permission.permission_code = 'asset' OR permission.permission_code LIKE 'asset:%')
  AND permission.permission_code NOT IN (
      'asset', 'asset:asset', 'asset:asset:self',
      'asset:asset:accept', 'asset:asset:return'
  );

-- 为全部已有租户补齐 Asset 品类和位置字典；只插入缺失值，不覆盖租户自定义内容。
INSERT IGNORE INTO omni_base.sys_dict_type
    (tenant_id, type_code, type_name, remark, sort, status, create_by)
SELECT tenant.id, definitions.type_code, definitions.type_name,
       definitions.remark, definitions.sort, 1, 'system'
FROM sys_tenant tenant
CROSS JOIN (
    SELECT 'asset_category' type_code, '资产品类' type_name,
           '资产台账品类编码' remark, 30 sort
    UNION ALL SELECT 'asset_location', '资产位置', '资产存放位置编码', 31
) definitions;

INSERT INTO omni_base.sys_dict_data
    (tenant_id, type_code, dict_value, dict_label, tag_type, sort, status, create_by)
SELECT tenant.id, definitions.type_code, definitions.dict_value,
       definitions.dict_label, definitions.tag_type, definitions.sort, 1, 'system'
FROM sys_tenant tenant
CROSS JOIN (
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
    WHERE dict_data.tenant_id = tenant.id
      AND dict_data.type_code = definitions.type_code
      AND dict_data.dict_value = definitions.dict_value
);

DROP TEMPORARY TABLE IF EXISTS tmp_asset_permission;

COMMIT;

GRANT ALL PRIVILEGES ON omni_asset.* TO 'omni_app'@'%';
FLUSH PRIVILEGES;
