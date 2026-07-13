-- Omni-Stack CRM MVP 既有环境迁移（MySQL 8.4）
-- 执行顺序：mysql -uroot -p < scripts/sql/migrate-crm-mvp.sql
-- 然后执行：mysql -uroot -p < scripts/sql/sp_init_tenant.sql

-- ============================================================
-- 1. CRM 业务库
-- ============================================================
CREATE DATABASE IF NOT EXISTS omni_crm
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE omni_crm;

CREATE TABLE IF NOT EXISTS crm_tenant_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    default_pipeline_id BIGINT DEFAULT NULL,
    currency_code VARCHAR(10) NOT NULL DEFAULT 'CNY',
    lead_duplicate_policy VARCHAR(20) NOT NULL DEFAULT 'WARN',
    initialized_time DATETIME NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    UNIQUE KEY uk_crm_config_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS crm_pipeline (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    default_flag TINYINT NOT NULL DEFAULT 0,
    sort INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    UNIQUE KEY uk_crm_pipeline_code (tenant_id, code),
    INDEX idx_crm_pipeline_status (tenant_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS crm_pipeline_stage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    pipeline_id BIGINT NOT NULL,
    stage_code VARCHAR(50) NOT NULL,
    stage_name VARCHAR(100) NOT NULL,
    stage_type VARCHAR(20) NOT NULL,
    probability DECIMAL(5,2) NOT NULL DEFAULT 0,
    sort INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    UNIQUE KEY uk_crm_stage_code (tenant_id, pipeline_id, stage_code),
    INDEX idx_crm_stage_sort (tenant_id, pipeline_id, status, deleted, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS crm_lead (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    lead_no VARCHAR(50) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    company_name VARCHAR(200) DEFAULT NULL,
    job_title VARCHAR(100) DEFAULT NULL,
    mobile VARCHAR(32) DEFAULT NULL,
    phone VARCHAR(32) DEFAULT NULL,
    email VARCHAR(200) DEFAULT NULL,
    region VARCHAR(100) DEFAULT NULL,
    address VARCHAR(500) DEFAULT NULL,
    source_code VARCHAR(50) DEFAULT NULL,
    industry_code VARCHAR(50) DEFAULT NULL,
    rating VARCHAR(20) DEFAULT NULL,
    status VARCHAR(20) NOT NULL,
    disqualify_reason VARCHAR(500) DEFAULT NULL,
    owner_user_id BIGINT NOT NULL,
    owner_unit_id BIGINT NOT NULL,
    assigned_time DATETIME DEFAULT NULL,
    last_activity_time DATETIME DEFAULT NULL,
    next_followup_time DATETIME DEFAULT NULL,
    converted_time DATETIME DEFAULT NULL,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    UNIQUE KEY uk_crm_lead_no (tenant_id, lead_no),
    INDEX idx_crm_lead_owner_status (tenant_id, owner_user_id, status, deleted),
    INDEX idx_crm_lead_unit_status (tenant_id, owner_unit_id, status, deleted),
    INDEX idx_crm_lead_followup (tenant_id, next_followup_time, status, deleted),
    INDEX idx_crm_lead_company (tenant_id, company_name),
    INDEX idx_crm_lead_mobile (tenant_id, mobile),
    INDEX idx_crm_lead_email (tenant_id, email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS crm_lead_conversion (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    lead_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    contact_id BIGINT DEFAULT NULL,
    opportunity_id BIGINT DEFAULT NULL,
    converted_by_user_id BIGINT NOT NULL,
    converted_time DATETIME NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    UNIQUE KEY uk_crm_conversion_lead (tenant_id, lead_id),
    INDEX idx_crm_conversion_customer (tenant_id, customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS crm_customer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    customer_no VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    normalized_name VARCHAR(200) NOT NULL,
    customer_type VARCHAR(50) DEFAULT NULL,
    industry_code VARCHAR(50) DEFAULT NULL,
    level_code VARCHAR(50) DEFAULT NULL,
    source_code VARCHAR(50) DEFAULT NULL,
    credit_code VARCHAR(50) DEFAULT NULL,
    website VARCHAR(300) DEFAULT NULL,
    phone VARCHAR(32) DEFAULT NULL,
    email VARCHAR(200) DEFAULT NULL,
    region VARCHAR(100) DEFAULT NULL,
    address VARCHAR(500) DEFAULT NULL,
    status VARCHAR(20) NOT NULL,
    owner_user_id BIGINT NOT NULL,
    owner_unit_id BIGINT NOT NULL,
    last_activity_time DATETIME DEFAULT NULL,
    next_followup_time DATETIME DEFAULT NULL,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    UNIQUE KEY uk_crm_customer_no (tenant_id, customer_no),
    INDEX idx_crm_customer_owner_status (tenant_id, owner_user_id, status, deleted),
    INDEX idx_crm_customer_unit_status (tenant_id, owner_unit_id, status, deleted),
    INDEX idx_crm_customer_name (tenant_id, normalized_name, deleted),
    INDEX idx_crm_customer_credit (tenant_id, credit_code),
    INDEX idx_crm_customer_phone (tenant_id, phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS crm_contact (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    department VARCHAR(100) DEFAULT NULL,
    job_title VARCHAR(100) DEFAULT NULL,
    mobile VARCHAR(32) DEFAULT NULL,
    phone VARCHAR(32) DEFAULT NULL,
    email VARCHAR(200) DEFAULT NULL,
    decision_role VARCHAR(50) DEFAULT NULL,
    primary_flag TINYINT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    owner_user_id BIGINT NOT NULL,
    owner_unit_id BIGINT NOT NULL,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    primary_customer_guard BIGINT GENERATED ALWAYS AS (
        CASE WHEN primary_flag = 1 AND status = 1 AND deleted = 0 THEN customer_id ELSE NULL END
    ) STORED,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    UNIQUE KEY uk_crm_contact_primary (tenant_id, primary_customer_guard),
    INDEX idx_crm_contact_customer (tenant_id, customer_id, status, deleted),
    INDEX idx_crm_contact_owner (tenant_id, owner_user_id, deleted),
    INDEX idx_crm_contact_mobile (tenant_id, mobile),
    INDEX idx_crm_contact_email (tenant_id, email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS crm_opportunity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    opportunity_no VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    customer_id BIGINT NOT NULL,
    primary_contact_id BIGINT DEFAULT NULL,
    source_lead_id BIGINT DEFAULT NULL,
    pipeline_id BIGINT NOT NULL,
    stage_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    currency_code VARCHAR(10) NOT NULL,
    probability DECIMAL(5,2) NOT NULL DEFAULT 0,
    expected_close_date DATE DEFAULT NULL,
    actual_close_time DATETIME DEFAULT NULL,
    loss_reason VARCHAR(500) DEFAULT NULL,
    stage_change_time DATETIME NOT NULL,
    next_followup_time DATETIME DEFAULT NULL,
    owner_user_id BIGINT NOT NULL,
    owner_unit_id BIGINT NOT NULL,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    UNIQUE KEY uk_crm_opportunity_no (tenant_id, opportunity_no),
    INDEX idx_crm_opp_owner_status (tenant_id, owner_user_id, status, deleted),
    INDEX idx_crm_opp_unit_status (tenant_id, owner_unit_id, status, deleted),
    INDEX idx_crm_opp_stage (tenant_id, pipeline_id, stage_id, status, deleted),
    INDEX idx_crm_opp_customer (tenant_id, customer_id, status, deleted),
    INDEX idx_crm_opp_close_date (tenant_id, expected_close_date, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS crm_opportunity_stage_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    opportunity_id BIGINT NOT NULL,
    from_stage_id BIGINT DEFAULT NULL,
    to_stage_id BIGINT NOT NULL,
    from_status VARCHAR(20) DEFAULT NULL,
    to_status VARCHAR(20) NOT NULL,
    change_reason VARCHAR(500) DEFAULT NULL,
    changed_by_user_id BIGINT NOT NULL,
    changed_time DATETIME NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    INDEX idx_crm_opp_history (tenant_id, opportunity_id, changed_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS crm_activity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    root_type VARCHAR(20) NOT NULL,
    root_id BIGINT NOT NULL,
    contact_id BIGINT DEFAULT NULL,
    activity_type VARCHAR(50) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    content TEXT DEFAULT NULL,
    status VARCHAR(20) NOT NULL,
    planned_start_time DATETIME DEFAULT NULL,
    planned_end_time DATETIME DEFAULT NULL,
    completed_time DATETIME DEFAULT NULL,
    next_action_time DATETIME DEFAULT NULL,
    performed_by_user_id BIGINT DEFAULT NULL,
    owner_user_id BIGINT NOT NULL,
    owner_unit_id BIGINT NOT NULL,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    INDEX idx_crm_activity_root (tenant_id, root_type, root_id, status, deleted),
    INDEX idx_crm_activity_owner (tenant_id, owner_user_id, status, deleted),
    INDEX idx_crm_activity_plan (tenant_id, planned_start_time, status, deleted),
    INDEX idx_crm_activity_next (tenant_id, next_action_time, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS crm_owner_change_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    entity_type VARCHAR(30) NOT NULL,
    entity_id BIGINT NOT NULL,
    old_owner_user_id BIGINT DEFAULT NULL,
    old_owner_unit_id BIGINT DEFAULT NULL,
    new_owner_user_id BIGINT NOT NULL,
    new_owner_unit_id BIGINT NOT NULL,
    operation_type VARCHAR(30) NOT NULL,
    reason VARCHAR(500) DEFAULT NULL,
    operator_user_id BIGINT NOT NULL,
    operated_time DATETIME NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    INDEX idx_crm_owner_change_entity (tenant_id, entity_type, entity_id, operated_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_mq_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    msg_id VARCHAR(36) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    binding_name VARCHAR(128) NOT NULL,
    tag VARCHAR(64) DEFAULT NULL,
    msg_key VARCHAR(128) DEFAULT NULL,
    payload TEXT NOT NULL,
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
    UNIQUE KEY uk_crm_mq_msg_id (msg_id),
    INDEX idx_crm_mq_relay (status, next_retry_time),
    INDEX idx_crm_mq_tenant_time (tenant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 默认租户种子按自然键幂等创建，不能假设既有库的自增主键仍从 1 开始。
INSERT INTO crm_pipeline
    (tenant_id, code, name, status, default_flag, sort, version, deleted, create_by)
SELECT 1, 'DEFAULT', '默认销售管道', 1, 1, 0, 0, 0, 'system'
WHERE NOT EXISTS (
    SELECT 1 FROM crm_pipeline WHERE tenant_id = 1 AND code = 'DEFAULT'
);

UPDATE crm_pipeline
SET name = '默认销售管道',
    status = 1,
    default_flag = 1,
    deleted = 0,
    update_time = NOW(),
    update_by = 'system'
WHERE tenant_id = 1 AND code = 'DEFAULT';

SET @crm_default_pipeline_id = (
    SELECT id FROM crm_pipeline
    WHERE tenant_id = 1 AND code = 'DEFAULT' AND deleted = 0
    ORDER BY id LIMIT 1
);

INSERT INTO crm_pipeline_stage
    (tenant_id, pipeline_id, stage_code, stage_name, stage_type, probability, sort, status, deleted, create_by)
SELECT 1, @crm_default_pipeline_id, seed.stage_code, seed.stage_name, seed.stage_type,
       seed.probability, seed.sort_no, 1, 0, 'system'
FROM (
    SELECT 'DISCOVERY' AS stage_code, '需求发现' AS stage_name, 'OPEN' AS stage_type, 10 AS probability, 10 AS sort_no
    UNION ALL SELECT 'QUALIFICATION', '资格确认', 'OPEN', 30, 20
    UNION ALL SELECT 'PROPOSAL', '方案报价', 'OPEN', 50, 30
    UNION ALL SELECT 'NEGOTIATION', '商务谈判', 'OPEN', 80, 40
    UNION ALL SELECT 'WON', '赢单', 'WON', 100, 50
    UNION ALL SELECT 'LOST', '输单', 'LOST', 0, 60
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM crm_pipeline_stage stage
    WHERE stage.tenant_id = 1
      AND stage.pipeline_id = @crm_default_pipeline_id
      AND stage.stage_code = seed.stage_code
);

UPDATE crm_pipeline_stage stage
JOIN (
    SELECT 'DISCOVERY' AS stage_code, '需求发现' AS stage_name, 'OPEN' AS stage_type, 10 AS probability, 10 AS sort_no
    UNION ALL SELECT 'QUALIFICATION', '资格确认', 'OPEN', 30, 20
    UNION ALL SELECT 'PROPOSAL', '方案报价', 'OPEN', 50, 30
    UNION ALL SELECT 'NEGOTIATION', '商务谈判', 'OPEN', 80, 40
    UNION ALL SELECT 'WON', '赢单', 'WON', 100, 50
    UNION ALL SELECT 'LOST', '输单', 'LOST', 0, 60
) seed ON seed.stage_code = stage.stage_code
SET stage.stage_name = seed.stage_name,
    stage.stage_type = seed.stage_type,
    stage.probability = seed.probability,
    stage.sort = seed.sort_no,
    stage.status = 1,
    stage.deleted = 0,
    stage.update_time = NOW(),
    stage.update_by = 'system'
WHERE stage.tenant_id = 1
  AND stage.pipeline_id = @crm_default_pipeline_id;

INSERT INTO crm_tenant_config
    (tenant_id, default_pipeline_id, currency_code, lead_duplicate_policy, initialized_time, create_by)
SELECT 1, @crm_default_pipeline_id, 'CNY', 'WARN', NOW(), 'system'
WHERE NOT EXISTS (
    SELECT 1 FROM crm_tenant_config WHERE tenant_id = 1
);

UPDATE crm_tenant_config
SET default_pipeline_id = @crm_default_pipeline_id,
    update_time = NOW(),
    update_by = 'system'
WHERE tenant_id = 1
  AND default_pipeline_id <> @crm_default_pipeline_id;

-- ============================================================
-- 2. 操作日志事件幂等列
-- ============================================================
USE omni_base;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = 'omni_base' AND TABLE_NAME = 'sys_oper_log' AND COLUMN_NAME = 'event_id') = 0,
    'ALTER TABLE sys_oper_log ADD COLUMN event_id VARCHAR(64) NULL AFTER id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
UPDATE sys_oper_log SET event_id = CONCAT('legacy-', id) WHERE event_id IS NULL OR event_id = '';
ALTER TABLE sys_oper_log MODIFY COLUMN event_id VARCHAR(64) NOT NULL;
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = 'omni_base' AND TABLE_NAME = 'sys_oper_log' AND INDEX_NAME = 'uk_operlog_event') = 0,
    'ALTER TABLE sys_oper_log ADD UNIQUE KEY uk_operlog_event (event_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = 'omni_base' AND TABLE_NAME = 'sys_oper_log_archive' AND COLUMN_NAME = 'event_id') = 0,
    'ALTER TABLE sys_oper_log_archive ADD COLUMN event_id VARCHAR(64) NULL AFTER id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
UPDATE sys_oper_log_archive SET event_id = CONCAT('legacy-archive-', id)
WHERE event_id IS NULL OR event_id = '';
ALTER TABLE sys_oper_log_archive MODIFY COLUMN event_id VARCHAR(64) NOT NULL;
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = 'omni_base' AND TABLE_NAME = 'sys_oper_log_archive'
       AND INDEX_NAME = 'uk_archive_event') = 0,
    'ALTER TABLE sys_oper_log_archive ADD UNIQUE KEY uk_archive_event (event_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 3. 为全部既有租户补齐 CRM 权限和角色
-- ============================================================
USE omni_auth;

DROP TEMPORARY TABLE IF EXISTS tmp_crm_perm_def;
CREATE TEMPORARY TABLE tmp_crm_perm_def (
    permission_code VARCHAR(200) PRIMARY KEY,
    parent_code VARCHAR(200) DEFAULT NULL,
    permission_name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    depth INT NOT NULL,
    sort INT NOT NULL
);

INSERT INTO tmp_crm_perm_def VALUES
('crm',NULL,'客户关系管理','DIRECTORY',1,7),
('crm:overview','crm','CRM概览','MENU',2,1),
('crm:overview:list','crm:overview','查看CRM概览','API',3,1),
('crm:lead','crm','线索管理','MENU',2,2),
('crm:lead:list','crm:lead','查看线索','API',3,1),
('crm:lead:create','crm:lead','创建线索','API',3,2),
('crm:lead:update','crm:lead','更新线索','API',3,3),
('crm:lead:delete','crm:lead','删除线索','API',3,4),
('crm:lead:assign','crm:lead','分配线索','API',3,5),
('crm:lead:disqualify','crm:lead','判定无效线索','API',3,6),
('crm:lead:convert','crm:lead','转换线索','API',3,7),
('crm:customer','crm','客户管理','MENU',2,3),
('crm:customer:list','crm:customer','查看客户','API',3,1),
('crm:customer:create','crm:customer','创建客户','API',3,2),
('crm:customer:update','crm:customer','更新客户','API',3,3),
('crm:customer:delete','crm:customer','删除客户','API',3,4),
('crm:customer:status','crm:customer','变更客户状态','API',3,5),
('crm:customer:transfer','crm:customer','转移客户','API',3,6),
('crm:customer:blacklist','crm:customer','客户黑名单','API',3,7),
('crm:contact','crm','联系人管理','MENU',2,4),
('crm:contact:list','crm:contact','查看联系人','API',3,1),
('crm:contact:create','crm:contact','创建联系人','API',3,2),
('crm:contact:update','crm:contact','更新联系人','API',3,3),
('crm:contact:delete','crm:contact','删除联系人','API',3,4),
('crm:opportunity','crm','商机管理','MENU',2,5),
('crm:opportunity:list','crm:opportunity','查看商机','API',3,1),
('crm:opportunity:create','crm:opportunity','创建商机','API',3,2),
('crm:opportunity:update','crm:opportunity','更新商机','API',3,3),
('crm:opportunity:delete','crm:opportunity','删除商机','API',3,4),
('crm:opportunity:assign','crm:opportunity','分配商机','API',3,5),
('crm:opportunity:stage','crm:opportunity','推进商机阶段','API',3,6),
('crm:opportunity:reopen','crm:opportunity','重开商机','API',3,7),
('crm:activity','crm','跟进管理','MENU',2,6),
('crm:activity:list','crm:activity','查看跟进','API',3,1),
('crm:activity:create','crm:activity','创建跟进','API',3,2),
('crm:activity:update','crm:activity','更新跟进','API',3,3),
('crm:activity:delete','crm:activity','删除跟进','API',3,4),
('crm:activity:complete','crm:activity','完成跟进','API',3,5),
('crm:activity:cancel','crm:activity','取消跟进','API',3,6),
('crm:owner:list','crm','查看负责人选项','API',2,7),
('crm:pii:view','crm','查看完整联系信息','API',2,8);

DROP PROCEDURE IF EXISTS sp_migrate_crm_permissions;
DELIMITER //
CREATE PROCEDURE sp_migrate_crm_permissions()
BEGIN
    DECLARE v_tenant_id BIGINT;
    DECLARE v_done INT DEFAULT 0;
    DECLARE tenant_cursor CURSOR FOR SELECT id FROM sys_tenant WHERE status = 1 ORDER BY id;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    OPEN tenant_cursor;
    tenant_loop: LOOP
        FETCH tenant_cursor INTO v_tenant_id;
        IF v_done THEN LEAVE tenant_loop; END IF;

        INSERT INTO sys_permission
            (tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
        SELECT v_tenant_id, 0, d.permission_code, d.permission_name, d.type, '', d.depth, d.sort, 1, 'system'
        FROM tmp_crm_perm_def d
        WHERE d.depth = 1 AND NOT EXISTS (
            SELECT 1 FROM sys_permission p
            WHERE p.tenant_id = v_tenant_id AND p.permission_code = d.permission_code);

        INSERT INTO sys_permission
            (tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
        SELECT v_tenant_id, parent.id, d.permission_code, d.permission_name, d.type, '', d.depth, d.sort, 1, 'system'
        FROM tmp_crm_perm_def d
        JOIN sys_permission parent
          ON parent.tenant_id = v_tenant_id AND parent.permission_code = d.parent_code
        WHERE d.depth = 2 AND NOT EXISTS (
            SELECT 1 FROM sys_permission p
            WHERE p.tenant_id = v_tenant_id AND p.permission_code = d.permission_code);

        INSERT INTO sys_permission
            (tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
        SELECT v_tenant_id, parent.id, d.permission_code, d.permission_name, d.type, '', d.depth, d.sort, 1, 'system'
        FROM tmp_crm_perm_def d
        JOIN sys_permission parent
          ON parent.tenant_id = v_tenant_id AND parent.permission_code = d.parent_code
        WHERE d.depth = 3 AND NOT EXISTS (
            SELECT 1 FROM sys_permission p
            WHERE p.tenant_id = v_tenant_id AND p.permission_code = d.permission_code);

        UPDATE sys_permission p
        JOIN tmp_crm_perm_def d ON d.permission_code = p.permission_code
        SET p.permission_name = d.permission_name,
            p.type = d.type,
            p.depth = d.depth,
            p.sort = d.sort,
            p.status = 1
        WHERE p.tenant_id = v_tenant_id;

        UPDATE sys_permission p
        JOIN tmp_crm_perm_def d ON d.permission_code = p.permission_code
        SET p.path = CONCAT('/', p.id, '/'), p.parent_id = 0
        WHERE p.tenant_id = v_tenant_id AND d.depth = 1;

        UPDATE sys_permission child
        JOIN tmp_crm_perm_def d ON d.permission_code = child.permission_code
        JOIN sys_permission parent
          ON parent.tenant_id = child.tenant_id AND parent.permission_code = d.parent_code
        SET child.parent_id = parent.id, child.path = CONCAT(parent.path, child.id, '/')
        WHERE child.tenant_id = v_tenant_id AND d.depth = 2;

        UPDATE sys_permission child
        JOIN tmp_crm_perm_def d ON d.permission_code = child.permission_code
        JOIN sys_permission parent
          ON parent.tenant_id = child.tenant_id AND parent.permission_code = d.parent_code
        SET child.parent_id = parent.id, child.path = CONCAT(parent.path, child.id, '/')
        WHERE child.tenant_id = v_tenant_id AND d.depth = 3;

        INSERT INTO sys_role
            (tenant_id, role_code, role_name, data_scope, sort, status, create_by)
        SELECT v_tenant_id, 'CRM_ADMIN', 'CRM管理员', 'TENANT', 20, 1, 'system'
        WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = v_tenant_id AND role_code = 'CRM_ADMIN');
        INSERT INTO sys_role
            (tenant_id, role_code, role_name, data_scope, sort, status, create_by)
        SELECT v_tenant_id, 'SALES_MANAGER', '销售经理', 'DEPT_AND_BELOW', 21, 1, 'system'
        WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = v_tenant_id AND role_code = 'SALES_MANAGER');
        INSERT INTO sys_role
            (tenant_id, role_code, role_name, data_scope, sort, status, create_by)
        SELECT v_tenant_id, 'SALES_REP', '销售代表', 'SELF', 22, 1, 'system'
        WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = v_tenant_id AND role_code = 'SALES_REP');
        INSERT INTO sys_role
            (tenant_id, role_code, role_name, data_scope, sort, status, create_by)
        SELECT v_tenant_id, 'CRM_VIEWER', 'CRM只读员', 'TENANT', 23, 1, 'system'
        WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = v_tenant_id AND role_code = 'CRM_VIEWER');

        INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
        SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.tenant_id = r.tenant_id
        WHERE r.tenant_id = v_tenant_id
          AND r.role_code IN ('SUPER_ADMIN','CRM_ADMIN','SALES_MANAGER')
          AND (p.permission_code = 'crm' OR p.permission_code LIKE 'crm:%');

        INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
        SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.tenant_id = r.tenant_id
        WHERE r.tenant_id = v_tenant_id AND r.role_code = 'SALES_REP'
          AND p.permission_code IN (
              'crm','crm:overview','crm:lead','crm:customer','crm:contact','crm:opportunity','crm:activity',
              'crm:overview:list','crm:lead:list','crm:lead:create','crm:lead:update',
              'crm:lead:disqualify','crm:lead:convert',
              'crm:customer:list','crm:customer:create','crm:customer:update','crm:customer:status',
              'crm:contact:list','crm:contact:create','crm:contact:update',
              'crm:opportunity:list','crm:opportunity:create','crm:opportunity:update',
              'crm:opportunity:stage','crm:opportunity:reopen',
              'crm:activity:list','crm:activity:create','crm:activity:update',
              'crm:activity:complete','crm:activity:cancel','crm:owner:list','crm:pii:view');

        INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
        SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.tenant_id = r.tenant_id
        WHERE r.tenant_id = v_tenant_id AND r.role_code = 'CRM_VIEWER'
          AND p.permission_code IN (
              'crm','crm:overview','crm:lead','crm:customer','crm:contact','crm:opportunity','crm:activity',
              'crm:overview:list','crm:lead:list','crm:customer:list','crm:contact:list',
              'crm:opportunity:list','crm:activity:list');
    END LOOP;
    CLOSE tenant_cursor;
END//
DELIMITER ;

CALL sp_migrate_crm_permissions();
DROP PROCEDURE IF EXISTS sp_migrate_crm_permissions;
DROP TEMPORARY TABLE IF EXISTS tmp_crm_perm_def;
