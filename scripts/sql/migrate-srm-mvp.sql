-- Omni-Stack SRM MVP 既有环境迁移（MySQL 8.4）
-- Compose 宿主机执行顺序：mysql --default-character-set=utf8mb4 -h127.0.0.1 -P13306 -uroot -p < scripts/sql/migrate-srm-mvp.sql
-- 然后执行：mysql --default-character-set=utf8mb4 -h127.0.0.1 -P13306 -uroot -p < scripts/sql/sp_init_tenant.sql
-- 本脚本同时支持“无任何 SRM 表”的旧数据卷与早期 SRM 预览版数据库。

SET NAMES utf8mb4;

-- Workflow 启动初始化器会为该分类幂等部署当前草稿；迁移先修正既有模型元数据。
UPDATE omni_workflow.wf_process_model
SET category = 'SRM_SUPPLIER_ONBOARDING',
    update_by = 'system'
WHERE model_key = 'supplier-onboarding'
  AND category <> 'SRM_SUPPLIER_ONBOARDING';

CREATE DATABASE IF NOT EXISTS omni_srm
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 旧数据卷不会重新执行 init-all.sql，因此在升级脚本中幂等补齐应用账号及授权。
-- CREATE USER 的默认口令仅用于首次创建；不会重置既有环境凭据，生产环境应由部署密钥覆盖。
CREATE USER IF NOT EXISTS 'omni_app'@'%' IDENTIFIED BY 'omni_app_pass';
GRANT ALL PRIVILEGES ON omni_auth.* TO 'omni_app'@'%';
GRANT ALL PRIVILEGES ON omni_base.* TO 'omni_app'@'%';
GRANT ALL PRIVILEGES ON omni_workflow.* TO 'omni_app'@'%';
GRANT ALL PRIVILEGES ON omni_crm.* TO 'omni_app'@'%';
GRANT ALL PRIVILEGES ON omni_srm.* TO 'omni_app'@'%';

-- ============================================================
-- 1. Auth Inbox、权限树和默认角色
-- ============================================================
USE omni_auth;

CREATE TABLE IF NOT EXISTS sys_portal_role_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    supplier_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    error_code VARCHAR(100) DEFAULT NULL,
    version INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_portal_role_request (tenant_id, request_id),
    INDEX idx_portal_role_user (tenant_id, user_id, status),
    CONSTRAINT chk_portal_role_request_status
        CHECK (status IN ('PROCESSING','COMPLETED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='门户角色分配请求幂等 Inbox';

-- Auth 现在也通过 Transactional Outbox 可靠发布门户角色分配结果；旧数据卷必须显式补表。
CREATE TABLE IF NOT EXISTS sys_mq_message (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    msg_id          VARCHAR(36)  NOT NULL COMMENT '业务消息ID（UUID），防重投递',
    topic           VARCHAR(128) NOT NULL COMMENT 'MQ Topic',
    binding_name    VARCHAR(128) NOT NULL COMMENT 'Spring Cloud Stream binding name',
    tag             VARCHAR(64)  DEFAULT NULL COMMENT 'MQ Tag（可选）',
    msg_key         VARCHAR(128) DEFAULT NULL COMMENT '业务键（可选，如 order:12345）',
    payload         TEXT         NOT NULL COMMENT '消息体 JSON',
    broker_type     VARCHAR(32)  NOT NULL DEFAULT 'rocketmq' COMMENT '消息中间件类型（预留多MQ扩展）',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0=PENDING, 1=SENT, 2=FAILED, 3=DEAD_LETTER, 4=SKIPPED',
    retry_count     INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    max_retry       INT          NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    next_retry_time DATETIME     DEFAULT NULL COMMENT '下次重试时间（指数退避）',
    error_msg       VARCHAR(512) DEFAULT NULL COMMENT '最后一次失败错误信息',
    service_name    VARCHAR(64)  NOT NULL COMMENT '来源服务名（spring.application.name）',
    tenant_id       BIGINT       DEFAULT NULL COMMENT '租户ID',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX uk_msg_id (msg_id),
    INDEX idx_relay (status, next_retry_time),
    INDEX idx_tenant_time (tenant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MQ消息发送记录表';

-- 使用脚本内模板建立完整权限树，不依赖旧数据卷已经存在 tenant=1 的 SRM 节点。
DROP TEMPORARY TABLE IF EXISTS tmp_srm_permission_template;
CREATE TEMPORARY TABLE tmp_srm_permission_template (
    permission_code VARCHAR(100) PRIMARY KEY,
    parent_code     VARCHAR(100) DEFAULT NULL,
    permission_name VARCHAR(100) NOT NULL,
    type            VARCHAR(20)  NOT NULL,
    depth           INT          NOT NULL,
    sort            INT          NOT NULL
);

INSERT INTO tmp_srm_permission_template
    (permission_code, parent_code, permission_name, type, depth, sort)
VALUES
    ('srm', NULL, '供应商关系管理', 'DIRECTORY', 1, 8),
    ('srm:overview', 'srm', 'SRM概览', 'MENU', 2, 1),
    ('srm:overview:list', 'srm:overview', '查看SRM概览', 'API', 3, 1),
    ('srm:supplier', 'srm', '供应商管理', 'MENU', 2, 2),
    ('srm:supplier:list', 'srm:supplier', '查看供应商', 'API', 3, 1),
    ('srm:supplier:create', 'srm:supplier', '创建供应商', 'API', 3, 2),
    ('srm:supplier:update', 'srm:supplier', '更新供应商', 'API', 3, 3),
    ('srm:supplier:delete', 'srm:supplier', '删除供应商', 'API', 3, 4),
    ('srm:supplier:approve', 'srm:supplier', '审核通过供应商', 'API', 3, 5),
    ('srm:supplier:reject', 'srm:supplier', '驳回供应商', 'API', 3, 6),
    ('srm:supplier:suspend', 'srm:supplier', '冻结供应商', 'API', 3, 7),
    ('srm:supplier:resume', 'srm:supplier', '解冻供应商', 'API', 3, 8),
    ('srm:supplier:blacklist', 'srm:supplier', '供应商黑名单', 'API', 3, 9),
    ('srm:supplier:restore', 'srm:supplier', '恢复供应商', 'API', 3, 10),
    ('srm:supplier:eliminate', 'srm:supplier', '淘汰供应商', 'API', 3, 11),
    ('srm:supplier:transfer', 'srm:supplier', '转移供应商负责人', 'API', 3, 12),
    ('srm:evaluation', 'srm', '绩效评估', 'MENU', 2, 3),
    ('srm:evaluation:list', 'srm:evaluation', '查看评估', 'API', 3, 1),
    ('srm:evaluation:create', 'srm:evaluation', '创建评估', 'API', 3, 2),
    ('srm:evaluation:view', 'srm:evaluation', '查看评估详情', 'API', 3, 3),
    ('srm:risk', 'srm', '风险管理', 'MENU', 2, 4),
    ('srm:risk:list', 'srm:risk', '查看风险', 'API', 3, 1),
    ('srm:risk:update', 'srm:risk', '更新风险指标', 'API', 3, 2),
    ('srm:risk:assess', 'srm:risk', '创建风险评估', 'API', 3, 3),
    ('srm:portal', 'srm', '供应商门户', 'DIRECTORY', 2, 5),
    ('srm:portal:enroll', 'srm:portal', '门户入驻', 'API', 3, 1),
    ('srm:portal:profile', 'srm:portal', '企业信息', 'MENU', 3, 2),
    ('srm:portal:evaluation', 'srm:portal', '绩效评估', 'MENU', 3, 3),
    ('srm:portal:quotation', 'srm:portal', '询价报价', 'MENU', 3, 4),
    ('srm:invite', 'srm', '邀请管理', 'MENU', 2, 6),
    ('srm:invite:create', 'srm:invite', '创建邀请', 'API', 3, 1),
    ('srm:invite:list', 'srm:invite', '查看邀请', 'API', 3, 2),
    ('srm:invite:revoke', 'srm:invite', '撤销邀请', 'API', 3, 3),
    ('srm:portal:invite', 'srm:invite', '管理门户邀请', 'API', 3, 4),
    ('srm:owner:list', 'srm', '查看负责人选项', 'API', 2, 7),
    ('srm:pii:view', 'srm', '查看完整银行信息', 'API', 2, 8),
    ('srm:contact:list', 'srm:supplier', '查看联系人', 'API', 3, 12),
    ('srm:contact:create', 'srm:supplier', '创建联系人', 'API', 3, 13),
    ('srm:contact:update', 'srm:supplier', '更新联系人', 'API', 3, 14),
    ('srm:contact:delete', 'srm:supplier', '删除联系人', 'API', 3, 15),
    ('srm:qualification:list', 'srm:supplier', '查看资质', 'API', 3, 16),
    ('srm:qualification:create', 'srm:supplier', '创建资质', 'API', 3, 17),
    ('srm:qualification:update', 'srm:supplier', '更新资质', 'API', 3, 18),
    ('srm:qualification:delete', 'srm:supplier', '删除资质', 'API', 3, 19),
    ('srm:bank-account:list', 'srm:supplier', '查看银行账户', 'API', 3, 20),
    ('srm:bank-account:create', 'srm:supplier', '创建银行账户', 'API', 3, 21),
    ('srm:bank-account:update', 'srm:supplier', '更新银行账户', 'API', 3, 22),
    ('srm:bank-account:delete', 'srm:supplier', '删除银行账户', 'API', 3, 23);

-- sys_permission 历史表没有 tenant + code 唯一键；重复节点会放大父子 JOIN 和角色授权，必须先失败关闭。
DELIMITER //
DROP PROCEDURE IF EXISTS sp_srm_assert_permission_integrity//
CREATE PROCEDURE sp_srm_assert_permission_integrity()
BEGIN
    IF EXISTS (
        SELECT 1 FROM sys_permission
        WHERE permission_code = 'srm' OR permission_code LIKE 'srm:%'
        GROUP BY tenant_id, permission_code
        HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'SRM migration aborted: duplicate SRM permission code in tenant';
    END IF;
END//
DELIMITER ;

CALL sp_srm_assert_permission_integrity();
DROP PROCEDURE IF EXISTS sp_srm_assert_permission_integrity;

INSERT INTO sys_permission
    (tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
SELECT tenant.id, 0, template.permission_code, template.permission_name,
       template.type, '', template.depth, template.sort, 1, 'system'
FROM sys_tenant tenant
JOIN tmp_srm_permission_template template ON template.depth = 1
WHERE tenant.status = 1
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission current_permission
      WHERE current_permission.tenant_id = tenant.id
        AND current_permission.permission_code = template.permission_code
  );

INSERT INTO sys_permission
    (tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
SELECT tenant.id, parent.id, template.permission_code, template.permission_name,
       template.type, '', template.depth, template.sort, 1, 'system'
FROM sys_tenant tenant
JOIN tmp_srm_permission_template template ON template.depth = 2
JOIN sys_permission parent
  ON parent.tenant_id = tenant.id AND parent.permission_code = template.parent_code
WHERE tenant.status = 1
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission current_permission
      WHERE current_permission.tenant_id = tenant.id
        AND current_permission.permission_code = template.permission_code
  );

INSERT INTO sys_permission
    (tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
SELECT tenant.id, parent.id, template.permission_code, template.permission_name,
       template.type, '', template.depth, template.sort, 1, 'system'
FROM sys_tenant tenant
JOIN tmp_srm_permission_template template ON template.depth = 3
JOIN sys_permission parent
  ON parent.tenant_id = tenant.id AND parent.permission_code = template.parent_code
WHERE tenant.status = 1
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission current_permission
      WHERE current_permission.tenant_id = tenant.id
        AND current_permission.permission_code = template.permission_code
  );

-- 对早期预览版留下的错误 parent/path/名称进行收敛。
UPDATE sys_permission current_permission
JOIN tmp_srm_permission_template template
  ON template.permission_code = current_permission.permission_code
LEFT JOIN sys_permission parent
  ON parent.tenant_id = current_permission.tenant_id
 AND parent.permission_code = template.parent_code
SET current_permission.parent_id = IF(template.parent_code IS NULL, 0, parent.id),
    current_permission.permission_name = template.permission_name,
    current_permission.type = template.type,
    current_permission.depth = template.depth,
    current_permission.sort = template.sort,
    current_permission.status = 1
WHERE current_permission.permission_code = 'srm'
   OR current_permission.permission_code LIKE 'srm:%';

UPDATE sys_permission
SET path = CONCAT('/', id, '/')
WHERE permission_code = 'srm';

UPDATE sys_permission child
JOIN sys_permission parent
  ON parent.id = child.parent_id AND parent.tenant_id = child.tenant_id
SET child.path = CONCAT(parent.path, child.id, '/')
WHERE child.permission_code LIKE 'srm:%' AND child.depth = 2;

UPDATE sys_permission child
JOIN sys_permission parent
  ON parent.id = child.parent_id AND parent.tenant_id = child.tenant_id
SET child.path = CONCAT(parent.path, child.id, '/')
WHERE child.permission_code LIKE 'srm:%' AND child.depth = 3;

DROP TEMPORARY TABLE IF EXISTS tmp_srm_permission_template;

-- 既有租户补齐 SRM 默认角色。
INSERT INTO sys_role
    (tenant_id, role_code, role_name, data_scope, sort, status, create_by)
SELECT tenant.id, role_template.role_code, role_template.role_name,
       role_template.data_scope, role_template.sort, 1, 'system'
FROM sys_tenant tenant
CROSS JOIN (
    SELECT 'SRM_ADMIN' role_code, 'SRM管理员' role_name, 'TENANT' data_scope, 30 sort
    UNION ALL SELECT 'PROCUREMENT_MANAGER', '采购经理', 'DEPT_AND_BELOW', 31
    UNION ALL SELECT 'PROCUREMENT_STAFF', '采购员', 'SELF', 32
    UNION ALL SELECT 'SUPPLIER', '供应商', 'SELF', 33
) role_template
WHERE tenant.status = 1
  AND NOT EXISTS (
      SELECT 1 FROM sys_role role_current
      WHERE role_current.tenant_id = tenant.id
        AND role_current.role_code = role_template.role_code
  );

-- 将现有角色收敛到 MVP 的最小权限集合，避免旧授权在升级后继续越权。
DELETE role_permission
FROM sys_role_permission role_permission
JOIN sys_role role_current ON role_current.id = role_permission.role_id
JOIN sys_permission permission_current ON permission_current.id = role_permission.permission_id
WHERE (role_current.role_code = 'USER'
       AND (permission_current.permission_code = 'srm'
            OR permission_current.permission_code LIKE 'srm:%')
       AND permission_current.permission_code NOT IN ('srm','srm:portal','srm:portal:enroll'))
   OR (role_current.role_code IN ('SRM_ADMIN','PROCUREMENT_MANAGER')
       AND permission_current.permission_code IN (
           'srm:portal:enroll','srm:portal:profile',
           'srm:portal:evaluation','srm:portal:quotation'
       ))
   OR (role_current.role_code = 'PROCUREMENT_STAFF'
       AND (permission_current.permission_code = 'srm'
            OR permission_current.permission_code LIKE 'srm:%')
       AND permission_current.permission_code NOT IN (
           'srm','srm:overview','srm:supplier','srm:evaluation','srm:risk',
           'srm:overview:list','srm:supplier:list','srm:supplier:create','srm:supplier:update',
           'srm:evaluation:list','srm:evaluation:create','srm:evaluation:view',
           'srm:risk:list','srm:risk:update','srm:risk:assess',
           'srm:contact:list','srm:contact:create','srm:contact:update','srm:contact:delete',
           'srm:qualification:list','srm:qualification:create','srm:qualification:update','srm:qualification:delete',
           'srm:bank-account:list','srm:bank-account:create','srm:bank-account:update','srm:bank-account:delete',
           'srm:owner:list'
       ))
   OR (role_current.role_code = 'SUPPLIER'
       AND (permission_current.permission_code = 'srm'
             OR permission_current.permission_code LIKE 'srm:%')
       AND permission_current.permission_code NOT IN
           ('srm','srm:portal','srm:portal:profile','srm:portal:evaluation',
            'srm:portal:quotation'))
   OR (permission_current.permission_code = 'srm:portal:quotation'
       AND role_current.role_code NOT IN ('SUPER_ADMIN','SUPPLIER'));

-- SUPER_ADMIN 获得全部 SRM 权限。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT role_current.id, permission_current.id
FROM sys_role role_current
JOIN sys_permission permission_current ON permission_current.tenant_id = role_current.tenant_id
WHERE role_current.role_code = 'SUPER_ADMIN'
  AND (permission_current.permission_code = 'srm'
       OR permission_current.permission_code LIKE 'srm:%');

-- 内部 SRM 管理角色不获得供应商自助门户能力。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT role_current.id, permission_current.id
FROM sys_role role_current
JOIN sys_permission permission_current ON permission_current.tenant_id = role_current.tenant_id
WHERE role_current.role_code IN ('SRM_ADMIN','PROCUREMENT_MANAGER')
  AND (permission_current.permission_code = 'srm'
       OR permission_current.permission_code LIKE 'srm:%')
  AND permission_current.permission_code NOT IN
      ('srm:portal:enroll','srm:portal:profile','srm:portal:evaluation',
       'srm:portal:quotation');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT role_current.id, permission_current.id
FROM sys_role role_current
JOIN sys_permission permission_current ON permission_current.tenant_id = role_current.tenant_id
WHERE role_current.role_code = 'PROCUREMENT_STAFF'
  AND permission_current.permission_code IN (
      'srm','srm:overview','srm:supplier','srm:evaluation','srm:risk',
      'srm:overview:list','srm:supplier:list','srm:supplier:create','srm:supplier:update',
      'srm:evaluation:list','srm:evaluation:create','srm:evaluation:view',
      'srm:risk:list','srm:risk:update','srm:risk:assess',
      'srm:contact:list','srm:contact:create','srm:contact:update','srm:contact:delete',
      'srm:qualification:list','srm:qualification:create','srm:qualification:update','srm:qualification:delete',
      'srm:bank-account:list','srm:bank-account:create','srm:bank-account:update','srm:bank-account:delete',
      'srm:owner:list'
  );

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT role_current.id, permission_current.id
FROM sys_role role_current
JOIN sys_permission permission_current ON permission_current.tenant_id = role_current.tenant_id
WHERE (role_current.role_code = 'USER'
       AND permission_current.permission_code IN ('srm','srm:portal','srm:portal:enroll'))
    OR (role_current.role_code = 'SUPPLIER'
       AND permission_current.permission_code IN
           ('srm','srm:portal','srm:portal:profile','srm:portal:evaluation',
            'srm:portal:quotation'));

-- 供应商品类使用 Base 字典 code；为所有既有有效租户幂等补齐 MVP 基线。
USE omni_base;

INSERT INTO sys_dict_type
    (tenant_id, type_code, type_name, remark, sort, status, create_by)
SELECT tenant.id, 'srm_supplier_category', '供应商品类', 'SRM供应商品类编码', 20, 1, 'system'
FROM omni_auth.sys_tenant tenant
WHERE tenant.status = 1
ON DUPLICATE KEY UPDATE
    type_name = VALUES(type_name),
    remark = VALUES(remark),
    sort = VALUES(sort),
    status = 1,
    update_by = 'system';

INSERT INTO sys_dict_data
    (tenant_id, type_code, dict_value, dict_label, tag_type, sort, status, create_by)
SELECT tenant.id, 'srm_supplier_category', category.dict_value, category.dict_label,
       category.tag_type, category.sort, 1, 'system'
FROM omni_auth.sys_tenant tenant
CROSS JOIN (
    SELECT 'ELECTRONICS' dict_value, '电子元器件' dict_label, 'primary' tag_type, 1 sort
    UNION ALL SELECT 'IT', '信息技术', 'success', 2
    UNION ALL SELECT 'RAW_MATERIAL', '原材料', 'warning', 3
    UNION ALL SELECT 'ADMIN', '行政物资', 'info', 4
    UNION ALL SELECT 'SERVICE', '服务', 'primary', 5
) category
WHERE tenant.status = 1
  AND NOT EXISTS (
      SELECT 1 FROM sys_dict_data dict_data
      WHERE dict_data.tenant_id = tenant.id
        AND dict_data.type_code = 'srm_supplier_category'
        AND dict_data.dict_value = category.dict_value
  );

UPDATE sys_dict_data dict_data
JOIN omni_auth.sys_tenant tenant
  ON tenant.id = dict_data.tenant_id AND tenant.status = 1
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
WHERE dict_data.type_code = 'srm_supplier_category';

-- ============================================================
-- 2. SRM 业务表增量迁移
-- ============================================================
-- 先建立与全新安装一致的完整基线；对已有表，后续纠偏步骤继续补齐列、索引和约束。
CREATE DATABASE IF NOT EXISTS omni_srm
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE omni_srm;

-- 12.1 供应商主表
CREATE TABLE IF NOT EXISTS srm_supplier (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id            BIGINT       NOT NULL COMMENT '租户ID',
    supplier_no          VARCHAR(50)  NOT NULL COMMENT '供应商编号',
    name                 VARCHAR(200) NOT NULL COMMENT '供应商名称',
    normalized_name      VARCHAR(200) NOT NULL COMMENT '规范化供应商名称',
    supplier_type        VARCHAR(50)  NOT NULL COMMENT '供应商类型',
    industry_code        VARCHAR(50)  DEFAULT NULL COMMENT '行业编码',
    credit_code          VARCHAR(50)  DEFAULT NULL COMMENT '统一社会信用代码',
    website              VARCHAR(300) DEFAULT NULL,
    phone                VARCHAR(32)  DEFAULT NULL,
    email                VARCHAR(200) DEFAULT NULL,
    region               VARCHAR(100) DEFAULT NULL,
    address              VARCHAR(500) DEFAULT NULL,
    category_code        VARCHAR(50)  DEFAULT NULL COMMENT '品类编码',
    level_code           VARCHAR(50)  DEFAULT NULL COMMENT '等级编码 STRATEGIC/PREFERRED/QUALIFIED/ELIMINATED',
    status               VARCHAR(30)  NOT NULL DEFAULT 'REGISTERING' COMMENT '状态',
    assigned_time        DATETIME     DEFAULT NULL COMMENT '分配时间',
    last_evaluation_time DATETIME     DEFAULT NULL COMMENT '最近评估时间',
    owner_user_id        BIGINT       DEFAULT NULL COMMENT '内部负责人用户ID；门户注册阶段允许为空',
    owner_unit_id        BIGINT       DEFAULT NULL COMMENT '内部负责人组织ID；门户注册阶段允许为空',
    version              INT          NOT NULL DEFAULT 0,
    deleted              TINYINT      NOT NULL DEFAULT 0,
    create_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by            VARCHAR(64)  DEFAULT NULL,
    update_by            VARCHAR(64)  DEFAULT NULL,
    UNIQUE KEY uk_srm_supplier_no (tenant_id, supplier_no),
    UNIQUE KEY uk_srm_supplier_credit (tenant_id, credit_code),
    INDEX idx_srm_supplier_owner_status (tenant_id, owner_user_id, status, deleted),
    INDEX idx_srm_supplier_unit_status (tenant_id, owner_unit_id, status, deleted),
    INDEX idx_srm_supplier_name (tenant_id, normalized_name, deleted),
    INDEX idx_srm_supplier_category_status (tenant_id, category_code, status, deleted),
    INDEX idx_srm_supplier_level (tenant_id, level_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SRM供应商主表';

-- 12.2 供应商联系人
CREATE TABLE IF NOT EXISTS srm_supplier_contact (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    supplier_id     BIGINT       NOT NULL COMMENT '供应商ID',
    name            VARCHAR(100) NOT NULL,
    department      VARCHAR(100) DEFAULT NULL,
    job_title       VARCHAR(100) DEFAULT NULL,
    mobile          VARCHAR(32)  DEFAULT NULL,
    phone           VARCHAR(32)  DEFAULT NULL,
    email           VARCHAR(200) DEFAULT NULL,
    decision_role   VARCHAR(50)  DEFAULT NULL,
    primary_flag    TINYINT      NOT NULL DEFAULT 0,
    status          TINYINT      NOT NULL DEFAULT 1,
    version         INT          NOT NULL DEFAULT 0,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    primary_supplier_guard BIGINT GENERATED ALWAYS AS (
        CASE WHEN primary_flag = 1 AND status = 1 AND deleted = 0 THEN supplier_id ELSE NULL END
    ) STORED,
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by       VARCHAR(64)  DEFAULT NULL,
    update_by       VARCHAR(64)  DEFAULT NULL,
    UNIQUE KEY uk_srm_contact_primary (tenant_id, primary_supplier_guard),
    INDEX idx_srm_contact_supplier (tenant_id, supplier_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SRM供应商联系人';

-- 12.3 供应商资质
CREATE TABLE IF NOT EXISTS srm_supplier_qualification (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL,
    supplier_id         BIGINT       NOT NULL,
    qualification_name  VARCHAR(200) NOT NULL,
    certificate_no      VARCHAR(100) DEFAULT NULL,
    issuing_authority   VARCHAR(200) DEFAULT NULL,
    issue_date          DATE         DEFAULT NULL,
    expiry_date         DATE         DEFAULT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    version             INT          NOT NULL DEFAULT 0,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by           VARCHAR(64)  DEFAULT NULL,
    update_by           VARCHAR(64)  DEFAULT NULL,
    INDEX idx_srm_qual_supplier (tenant_id, supplier_id, deleted),
    INDEX idx_srm_qual_expiry (tenant_id, expiry_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SRM供应商资质';

-- 12.4 供应商银行账户
CREATE TABLE IF NOT EXISTS srm_supplier_bank_account (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL,
    supplier_id   BIGINT       NOT NULL,
    account_name  VARCHAR(200) NOT NULL,
    account_no    VARCHAR(100) NOT NULL,
    bank_name     VARCHAR(200) NOT NULL,
    bank_branch   VARCHAR(200) DEFAULT NULL,
    bank_code     VARCHAR(50)  DEFAULT NULL,
    primary_flag  TINYINT      NOT NULL DEFAULT 0,
    status        TINYINT      NOT NULL DEFAULT 1,
    version       INT          NOT NULL DEFAULT 0,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    primary_supplier_guard BIGINT GENERATED ALWAYS AS (
        CASE WHEN primary_flag = 1 AND status = 1 AND deleted = 0 THEN supplier_id ELSE NULL END
    ) STORED,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by     VARCHAR(64)  DEFAULT NULL,
    update_by     VARCHAR(64)  DEFAULT NULL,
    UNIQUE KEY uk_srm_bank_primary (tenant_id, primary_supplier_guard),
    INDEX idx_srm_bank_supplier (tenant_id, supplier_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SRM供应商银行账户';

-- 12.5 门户用户
CREATE TABLE IF NOT EXISTS srm_supplier_portal_user (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id    BIGINT   NOT NULL,
    supplier_id  BIGINT   NOT NULL,
    user_id      BIGINT   NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_login_time DATETIME DEFAULT NULL,
    version      INT NOT NULL DEFAULT 0,
    deleted      TINYINT NOT NULL DEFAULT 0,
    create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by    VARCHAR(64) DEFAULT NULL,
    update_by    VARCHAR(64) DEFAULT NULL,
    UNIQUE KEY uk_srm_portal_user (tenant_id, user_id),
    INDEX idx_srm_portal_supplier (tenant_id, supplier_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SRM门户用户';

-- 12.6 门户入驻记录
CREATE TABLE IF NOT EXISTS srm_supplier_enrollment (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL,
    supplier_id  BIGINT       NOT NULL,
    user_id      BIGINT       NOT NULL,
    request_id   VARCHAR(64)  NOT NULL COMMENT '幂等请求ID',
    invite_id    BIGINT       DEFAULT NULL,
    status       VARCHAR(30)  NOT NULL DEFAULT 'PENDING_ROLE_ASSIGN',
    retry_count  INT          NOT NULL DEFAULT 0,
    last_error_code VARCHAR(100) DEFAULT NULL,
    next_retry_time DATETIME  DEFAULT NULL,
    version      INT          NOT NULL DEFAULT 0,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    active_user_guard BIGINT GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 AND status IN ('PENDING_ROLE_ASSIGN','ROLE_ASSIGN_FAILED')
             THEN user_id ELSE NULL END
    ) STORED,
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by    VARCHAR(64)  DEFAULT NULL,
    update_by    VARCHAR(64)  DEFAULT NULL,
    UNIQUE KEY uk_srm_enrollment_req (tenant_id, request_id),
    UNIQUE KEY uk_srm_enrollment_active_user (tenant_id, active_user_guard),
    INDEX idx_srm_enrollment_user_status (tenant_id, user_id, status, deleted),
    INDEX idx_srm_enrollment_supplier (tenant_id, supplier_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SRM门户入驻记录';

-- 12.7 邀请
CREATE TABLE IF NOT EXISTS srm_supplier_invite (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id         BIGINT       NOT NULL,
    invite_code_hash  VARCHAR(128) NOT NULL COMMENT '邀请码 SHA-256 哈希',
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    expires_time      DATETIME     NOT NULL,
    max_uses          INT          NOT NULL DEFAULT 1,
    used_count        INT          NOT NULL DEFAULT 0,
    version           INT          NOT NULL DEFAULT 0,
    deleted           TINYINT      NOT NULL DEFAULT 0,
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by         VARCHAR(64)  DEFAULT NULL,
    update_by         VARCHAR(64)  DEFAULT NULL,
    UNIQUE KEY uk_srm_invite_hash (tenant_id, invite_code_hash),
    INDEX idx_srm_invite_status (tenant_id, status, expires_time, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SRM供应商邀请';

-- 12.8 评估模板
CREATE TABLE IF NOT EXISTS srm_evaluation_template (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL,
    name         VARCHAR(200) NOT NULL,
    status       TINYINT      NOT NULL DEFAULT 1,
    default_flag TINYINT      NOT NULL DEFAULT 0,
    version      INT          NOT NULL DEFAULT 0,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by    VARCHAR(64)  DEFAULT NULL,
    update_by    VARCHAR(64)  DEFAULT NULL,
    UNIQUE KEY uk_srm_eval_tpl_name (tenant_id, name, deleted),
    INDEX idx_srm_eval_tpl_status (tenant_id, default_flag, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SRM评估模板';

-- 12.9 评估维度
CREATE TABLE IF NOT EXISTS srm_evaluation_dimension (
    id              BIGINT        AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT        NOT NULL,
    template_id     BIGINT        NOT NULL,
    indicator_name  VARCHAR(200)  NOT NULL COMMENT '指标名称',
    weight          DECIMAL(5,2)  NOT NULL COMMENT '权重百分比',
    sort            INT           NOT NULL DEFAULT 0,
    status          TINYINT       NOT NULL DEFAULT 1,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by       VARCHAR(64)   DEFAULT NULL,
    update_by       VARCHAR(64)   DEFAULT NULL,
    UNIQUE KEY uk_srm_eval_dim_name (tenant_id, template_id, indicator_name, deleted),
    INDEX idx_srm_eval_dim_tpl (tenant_id, template_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SRM评估维度';

-- 12.10 绩效评估
CREATE TABLE IF NOT EXISTS srm_evaluation (
    id                 BIGINT        AUTO_INCREMENT PRIMARY KEY,
    tenant_id          BIGINT        NOT NULL,
    supplier_id        BIGINT        NOT NULL,
    template_id        BIGINT        NOT NULL,
    evaluation_period  VARCHAR(50)   NOT NULL COMMENT '评估周期',
    total_score        DECIMAL(5,2)  NOT NULL DEFAULT 0 COMMENT '百分制总分',
    evaluator_user_id  BIGINT        NOT NULL,
    evaluation_time    DATETIME      NOT NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'COMPLETED',
    owner_user_id      BIGINT        NOT NULL COMMENT '负责人快照',
    owner_unit_id      BIGINT        NOT NULL COMMENT '组织快照',
    version            INT           NOT NULL DEFAULT 0,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    create_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by          VARCHAR(64)   DEFAULT NULL,
    update_by          VARCHAR(64)   DEFAULT NULL,
    INDEX idx_srm_eval_supplier (tenant_id, supplier_id, deleted),
    INDEX idx_srm_eval_owner (tenant_id, owner_user_id, deleted),
    INDEX idx_srm_eval_period (tenant_id, evaluation_period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SRM绩效评估';

-- 12.11 评估评分明细
CREATE TABLE IF NOT EXISTS srm_evaluation_item (
    id              BIGINT        AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT        NOT NULL,
    evaluation_id   BIGINT        NOT NULL,
    dimension_id    BIGINT        NOT NULL,
    indicator_name  VARCHAR(200)  NOT NULL,
    score           DECIMAL(3,1)  NOT NULL COMMENT '评分 1-5',
    weight          DECIMAL(5,2)  NOT NULL COMMENT '权重快照',
    remark          VARCHAR(500)  DEFAULT NULL,
    version         INT           NOT NULL DEFAULT 0,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by       VARCHAR(64)   DEFAULT NULL,
    update_by       VARCHAR(64)   DEFAULT NULL,
    UNIQUE KEY uk_srm_eval_item_dim (tenant_id, evaluation_id, dimension_id),
    INDEX idx_srm_eval_item_eval (tenant_id, evaluation_id, deleted),
    CONSTRAINT chk_srm_eval_item_score CHECK (score >= 1 AND score <= 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SRM评估评分明细';

-- 12.12 风险指标
CREATE TABLE IF NOT EXISTS srm_risk_indicator (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    supplier_id     BIGINT       NOT NULL,
    indicator_type  VARCHAR(50)  NOT NULL COMMENT '指标类型',
    indicator_value VARCHAR(200) DEFAULT NULL,
    risk_level      VARCHAR(20)  NOT NULL DEFAULT 'GREEN' COMMENT 'RED/YELLOW/GREEN',
    assessment_time DATETIME     NOT NULL,
    remark          VARCHAR(500) DEFAULT NULL,
    version         INT          NOT NULL DEFAULT 0,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by       VARCHAR(64)  DEFAULT NULL,
    update_by       VARCHAR(64)  DEFAULT NULL,
    UNIQUE KEY uk_srm_risk_ind_type (tenant_id, supplier_id, indicator_type),
    INDEX idx_srm_risk_ind_supplier (tenant_id, supplier_id, indicator_type, deleted),
    INDEX idx_srm_risk_ind_level (tenant_id, risk_level, deleted),
    CONSTRAINT chk_srm_risk_ind_type CHECK (indicator_type IN ('FINANCIAL','COMPLIANCE','SUPPLY','COOPERATION','QUALITY','CERTIFICATE')),
    CONSTRAINT chk_srm_risk_ind_level CHECK (risk_level IN ('GREEN','YELLOW','RED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SRM风险指标';

-- 12.13 综合风险评估
CREATE TABLE IF NOT EXISTS srm_risk_assessment (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id        BIGINT       NOT NULL,
    supplier_id      BIGINT       NOT NULL,
    overall_level    VARCHAR(20)  NOT NULL COMMENT 'RED/YELLOW/GREEN',
    assessment_time  DATETIME     NOT NULL,
    assessor_user_id BIGINT       NOT NULL,
    remark           VARCHAR(500) DEFAULT NULL,
    version          INT          NOT NULL DEFAULT 0,
    deleted          TINYINT      NOT NULL DEFAULT 0,
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by        VARCHAR(64)  DEFAULT NULL,
    update_by        VARCHAR(64)  DEFAULT NULL,
    INDEX idx_srm_risk_assess_supplier (tenant_id, supplier_id, assessment_time, deleted),
    CONSTRAINT chk_srm_risk_assess_level CHECK (overall_level IN ('GREEN','YELLOW','RED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SRM综合风险评估';

-- 12.14 供应商报价
CREATE TABLE IF NOT EXISTS srm_quotation (
    id                    BIGINT        AUTO_INCREMENT PRIMARY KEY,
    tenant_id             BIGINT        NOT NULL,
    supplier_id           BIGINT        NOT NULL,
    rfq_id                BIGINT        NOT NULL COMMENT 'Procurement RFQ ID；不建跨库外键',
    rfq_no                VARCHAR(64)   NOT NULL COMMENT '询价单号快照',
    supplier_name_snapshot VARCHAR(200) NOT NULL COMMENT '供应商名称快照',
    request_id            VARCHAR(64)   NOT NULL COMMENT '客户端幂等请求ID',
    quotation_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid_until           DATETIME      NOT NULL,
    total_amount          DECIMAL(19,4) NOT NULL DEFAULT 0 COMMENT '服务端汇总金额',
    currency_code         CHAR(3)       NOT NULL DEFAULT 'CNY',
    status                VARCHAR(20)   NOT NULL DEFAULT 'SUBMITTED',
    version               INT           NOT NULL DEFAULT 1,
    deleted               TINYINT       NOT NULL DEFAULT 0,
    active_supplier_guard BIGINT GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 THEN supplier_id ELSE NULL END
    ) STORED,
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by             VARCHAR(64)   DEFAULT NULL,
    update_by             VARCHAR(64)   DEFAULT NULL,
    UNIQUE KEY uk_srm_quote_request (tenant_id, request_id),
    UNIQUE KEY uk_srm_quote_active_supplier (tenant_id, rfq_id, active_supplier_guard),
    INDEX idx_srm_quote_rfq_status (tenant_id, rfq_id, status, deleted),
    INDEX idx_srm_quote_supplier_time (tenant_id, supplier_id, quotation_time, deleted),
    INDEX idx_srm_quote_valid_until (tenant_id, status, valid_until, deleted),
    CONSTRAINT chk_srm_quote_status
        CHECK (status IN ('DRAFT','SUBMITTED','WITHDRAWN','EXPIRED')),
    CONSTRAINT chk_srm_quote_total CHECK (total_amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SRM供应商报价';

-- 12.15 供应商报价明细
CREATE TABLE IF NOT EXISTS srm_quotation_line (
    id                    BIGINT        AUTO_INCREMENT PRIMARY KEY,
    tenant_id             BIGINT        NOT NULL,
    quotation_id          BIGINT        NOT NULL,
    rfq_line_id           BIGINT        NOT NULL COMMENT 'Procurement RFQ行ID；不建跨库外键',
    material_code         VARCHAR(64)   NOT NULL COMMENT '物料编码快照',
    material_name         VARCHAR(200)  NOT NULL COMMENT '物料名称快照',
    unit                  VARCHAR(32)   NOT NULL COMMENT '计量单位快照',
    unit_price            DECIMAL(19,6) NOT NULL,
    quantity              DECIMAL(19,6) NOT NULL COMMENT '询价数量快照',
    line_amount           DECIMAL(19,4) NOT NULL COMMENT '服务端计算行金额',
    delivery_days         INT           NOT NULL DEFAULT 0,
    remark                VARCHAR(500)  DEFAULT NULL,
    version               INT           NOT NULL DEFAULT 0,
    deleted               TINYINT       NOT NULL DEFAULT 0,
    active_rfq_line_guard BIGINT GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 THEN rfq_line_id ELSE NULL END
    ) STORED,
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by             VARCHAR(64)   DEFAULT NULL,
    update_by             VARCHAR(64)   DEFAULT NULL,
    UNIQUE KEY uk_srm_quote_line_active (tenant_id, quotation_id, active_rfq_line_guard),
    INDEX idx_srm_quote_line_quote (tenant_id, quotation_id, deleted),
    INDEX idx_srm_quote_line_rfq_line (tenant_id, rfq_line_id, deleted),
    CONSTRAINT chk_srm_quote_line_price CHECK (unit_price > 0),
    CONSTRAINT chk_srm_quote_line_quantity CHECK (quantity > 0),
    CONSTRAINT chk_srm_quote_line_amount CHECK (line_amount > 0),
    CONSTRAINT chk_srm_quote_line_delivery CHECK (delivery_days BETWEEN 0 AND 3650)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SRM供应商报价明细';

-- 12.16 报价请求幂等历史
CREATE TABLE IF NOT EXISTS srm_quotation_request (
    id             BIGINT      AUTO_INCREMENT PRIMARY KEY,
    tenant_id      BIGINT      NOT NULL,
    request_id     VARCHAR(64) NOT NULL COMMENT '客户端幂等请求ID',
    quotation_id   BIGINT      DEFAULT NULL COMMENT 'RESERVED阶段允许为空',
    rfq_id         BIGINT      NOT NULL,
    supplier_id    BIGINT      NOT NULL,
    request_hash   CHAR(64)    NOT NULL COMMENT '规范化请求体SHA-256',
    target_version INT         DEFAULT NULL COMMENT '本请求成功产生的报价版本',
    status         VARCHAR(20) NOT NULL DEFAULT 'RESERVED',
    create_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by      VARCHAR(64) DEFAULT NULL,
    update_by      VARCHAR(64) DEFAULT NULL,
    UNIQUE KEY uk_srm_quote_req_id (tenant_id, request_id),
    UNIQUE KEY uk_srm_quote_req_version (tenant_id, quotation_id, target_version),
    INDEX idx_srm_quote_req_business (tenant_id, rfq_id, supplier_id, status),
    INDEX idx_srm_quote_req_quote (tenant_id, quotation_id, create_time),
    CONSTRAINT chk_srm_quote_req_status CHECK (status IN ('RESERVED','COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SRM报价请求幂等历史';

-- 12.17 SRM 本地 Transactional Outbox
CREATE TABLE IF NOT EXISTS sys_mq_message (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    msg_id          VARCHAR(36)  NOT NULL COMMENT '业务消息ID',
    topic           VARCHAR(128) NOT NULL COMMENT 'MQ Topic',
    binding_name    VARCHAR(128) NOT NULL COMMENT 'Stream binding',
    tag             VARCHAR(64)  DEFAULT NULL,
    msg_key         VARCHAR(128) DEFAULT NULL COMMENT '事件ID或业务键',
    payload         TEXT         NOT NULL COMMENT '不含PII的消息体',
    broker_type     VARCHAR(32)  NOT NULL DEFAULT 'rocketmq',
    status          TINYINT      NOT NULL DEFAULT 0,
    retry_count     INT          NOT NULL DEFAULT 0,
    max_retry       INT          NOT NULL DEFAULT 3,
    next_retry_time DATETIME     DEFAULT NULL,
    error_msg       VARCHAR(512) DEFAULT NULL,
    service_name    VARCHAR(64)  NOT NULL,
    tenant_id       BIGINT       NOT NULL,
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_srm_mq_msg_id (msg_id),
    INDEX idx_srm_mq_relay (status, next_retry_time),
    INDEX idx_srm_mq_tenant_time (tenant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SRM可靠消息发件箱';

DELIMITER //

DROP PROCEDURE IF EXISTS sp_srm_add_column//
CREATE PROCEDURE sp_srm_add_column(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND COLUMN_NAME = p_column_name
    ) THEN
        SET @srm_ddl = CONCAT('ALTER TABLE `', p_table_name,
                              '` ADD COLUMN `', p_column_name, '` ', p_definition);
        PREPARE srm_stmt FROM @srm_ddl;
        EXECUTE srm_stmt;
        DEALLOCATE PREPARE srm_stmt;
    END IF;
END//

DROP PROCEDURE IF EXISTS sp_srm_rename_column//
CREATE PROCEDURE sp_srm_rename_column(
    IN p_table_name VARCHAR(64),
    IN p_old_column_name VARCHAR(64),
    IN p_new_column_name VARCHAR(64),
    IN p_definition TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND COLUMN_NAME = p_old_column_name
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND COLUMN_NAME = p_new_column_name
    ) THEN
        SET @srm_ddl = CONCAT('ALTER TABLE `', p_table_name,
                              '` CHANGE COLUMN `', p_old_column_name, '` `',
                              p_new_column_name, '` ', p_definition);
        PREPARE srm_stmt FROM @srm_ddl;
        EXECUTE srm_stmt;
        DEALLOCATE PREPARE srm_stmt;
    END IF;
END//

DROP PROCEDURE IF EXISTS sp_srm_add_index//
CREATE PROCEDURE sp_srm_add_index(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND INDEX_NAME = p_index_name
    ) THEN
        SET @srm_ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD ', p_definition);
        PREPARE srm_stmt FROM @srm_ddl;
        EXECUTE srm_stmt;
        DEALLOCATE PREPARE srm_stmt;
    END IF;
END//

DROP PROCEDURE IF EXISTS sp_srm_add_constraint//
CREATE PROCEDURE sp_srm_add_constraint(
    IN p_table_name VARCHAR(64),
    IN p_constraint_name VARCHAR(64),
    IN p_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND CONSTRAINT_NAME = p_constraint_name
    ) THEN
        SET @srm_ddl = CONCAT('ALTER TABLE `', p_table_name,
                              '` ADD CONSTRAINT `', p_constraint_name, '` ', p_definition);
        PREPARE srm_stmt FROM @srm_ddl;
        EXECUTE srm_stmt;
        DEALLOCATE PREPARE srm_stmt;
    END IF;
END//

DROP PROCEDURE IF EXISTS sp_srm_assert_portal_backfill//
CREATE PROCEDURE sp_srm_assert_portal_backfill()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM srm_supplier_enrollment enrollment
        LEFT JOIN (
            SELECT tenant_id, supplier_id, COUNT(*) portal_user_count
            FROM srm_supplier_portal_user
            WHERE status = 'ACTIVE' AND deleted = 0
            GROUP BY tenant_id, supplier_id
        ) portal_user
          ON portal_user.tenant_id = enrollment.tenant_id
         AND portal_user.supplier_id = enrollment.supplier_id
        WHERE enrollment.user_id IS NULL
          AND COALESCE(portal_user.portal_user_count, 0) = 0
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'SRM migration aborted: enrollment has no active portal user mapping';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM srm_supplier_enrollment enrollment
        JOIN (
            SELECT tenant_id, supplier_id, COUNT(*) portal_user_count
            FROM srm_supplier_portal_user
            WHERE status = 'ACTIVE' AND deleted = 0
            GROUP BY tenant_id, supplier_id
        ) portal_user
          ON portal_user.tenant_id = enrollment.tenant_id
         AND portal_user.supplier_id = enrollment.supplier_id
        WHERE enrollment.user_id IS NULL
          AND portal_user.portal_user_count > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'SRM migration aborted: enrollment portal user mapping is ambiguous';
    END IF;
END//

DROP PROCEDURE IF EXISTS sp_srm_assert_integrity//
CREATE PROCEDURE sp_srm_assert_integrity()
BEGIN
    IF EXISTS (
        SELECT 1 FROM srm_supplier
        GROUP BY tenant_id, supplier_no HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'SRM migration aborted: duplicate supplier_no in tenant';
    END IF;

    IF EXISTS (
        SELECT 1 FROM srm_supplier
        WHERE credit_code IS NOT NULL AND TRIM(credit_code) <> ''
        GROUP BY tenant_id, credit_code HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'SRM migration aborted: duplicate credit_code in tenant';
    END IF;

    IF EXISTS (
        SELECT 1 FROM srm_supplier_portal_user
        GROUP BY tenant_id, user_id HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'SRM migration aborted: one portal user maps to multiple suppliers';
    END IF;

    IF EXISTS (
        SELECT 1 FROM srm_supplier_enrollment
        GROUP BY tenant_id, request_id HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'SRM migration aborted: duplicate enrollment request_id in tenant';
    END IF;

    IF EXISTS (
        SELECT 1 FROM srm_supplier_invite
        GROUP BY tenant_id, invite_code_hash HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'SRM migration aborted: duplicate invite hash in tenant';
    END IF;

    IF EXISTS (
        SELECT 1 FROM srm_supplier_enrollment WHERE user_id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'SRM migration aborted: enrollment cannot resolve portal user_id';
    END IF;

    IF EXISTS (
        SELECT 1 FROM srm_supplier_enrollment
        WHERE deleted = 0 AND status IN ('PENDING_ROLE_ASSIGN','ROLE_ASSIGN_FAILED')
        GROUP BY tenant_id, user_id HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'SRM migration aborted: duplicate active enrollment for user';
    END IF;

    IF EXISTS (
        SELECT 1 FROM srm_evaluation_item
        GROUP BY tenant_id, evaluation_id, dimension_id HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'SRM migration aborted: duplicate evaluation dimension item';
    END IF;

    IF EXISTS (
        SELECT 1 FROM srm_evaluation_item WHERE score < 1 OR score > 5
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'SRM migration aborted: evaluation score cannot be converted to 1-5';
    END IF;

    IF EXISTS (
        SELECT 1 FROM srm_risk_indicator
        WHERE indicator_type NOT IN ('FINANCIAL','COMPLIANCE','SUPPLY','COOPERATION','QUALITY','CERTIFICATE')
           OR risk_level NOT IN ('GREEN','YELLOW','RED')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'SRM migration aborted: unsupported risk indicator enum';
    END IF;

    IF EXISTS (
        SELECT 1 FROM srm_risk_assessment
        WHERE overall_level NOT IN ('GREEN','YELLOW','RED')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'SRM migration aborted: unsupported risk assessment level';
    END IF;
END//

DELIMITER ;

CALL sp_srm_rename_column('srm_evaluation_template', 'template_name', 'name',
    'VARCHAR(200) NOT NULL');
CALL sp_srm_rename_column('srm_evaluation_dimension', 'dimension_name', 'indicator_name',
    'VARCHAR(200) NOT NULL COMMENT ''指标名称''');

CALL sp_srm_add_column('srm_supplier', 'normalized_name',
    'VARCHAR(200) NULL AFTER `name`');
UPDATE srm_supplier
SET normalized_name = LOWER(REGEXP_REPLACE(TRIM(name), '[[:space:]]+', ' '))
WHERE normalized_name IS NULL OR normalized_name = ''
   OR normalized_name = LOWER(TRIM(name));
ALTER TABLE srm_supplier
    MODIFY COLUMN normalized_name VARCHAR(200) NOT NULL,
    MODIFY COLUMN owner_user_id BIGINT NULL,
    MODIFY COLUMN owner_unit_id BIGINT NULL;
UPDATE srm_supplier
SET credit_code = CASE
    WHEN TRIM(IFNULL(credit_code, '')) = '' THEN NULL
    ELSE UPPER(TRIM(credit_code))
END;

-- 多个主联系人/默认账户时保留最早一条，其余降为普通项。
UPDATE srm_supplier_contact contact_current
JOIN (
    SELECT tenant_id, supplier_id, MIN(id) keep_id
    FROM srm_supplier_contact
    WHERE primary_flag = 1 AND status = 1 AND deleted = 0
    GROUP BY tenant_id, supplier_id HAVING COUNT(*) > 1
) duplicate_primary
  ON duplicate_primary.tenant_id = contact_current.tenant_id
 AND duplicate_primary.supplier_id = contact_current.supplier_id
SET contact_current.primary_flag = 0,
    contact_current.version = contact_current.version + 1
WHERE contact_current.id <> duplicate_primary.keep_id
  AND contact_current.primary_flag = 1
  AND contact_current.status = 1
  AND contact_current.deleted = 0;

UPDATE srm_supplier_bank_account account_current
JOIN (
    SELECT tenant_id, supplier_id, MIN(id) keep_id
    FROM srm_supplier_bank_account
    WHERE primary_flag = 1 AND status = 1 AND deleted = 0
    GROUP BY tenant_id, supplier_id HAVING COUNT(*) > 1
) duplicate_primary
  ON duplicate_primary.tenant_id = account_current.tenant_id
 AND duplicate_primary.supplier_id = account_current.supplier_id
SET account_current.primary_flag = 0,
    account_current.version = account_current.version + 1
WHERE account_current.id <> duplicate_primary.keep_id
  AND account_current.primary_flag = 1
  AND account_current.status = 1
  AND account_current.deleted = 0;

CALL sp_srm_add_column('srm_supplier_contact', 'primary_supplier_guard',
    'BIGINT GENERATED ALWAYS AS (CASE WHEN `primary_flag` = 1 AND `status` = 1 AND `deleted` = 0 THEN `supplier_id` ELSE NULL END) STORED AFTER `deleted`');
CALL sp_srm_add_column('srm_supplier_bank_account', 'primary_supplier_guard',
    'BIGINT GENERATED ALWAYS AS (CASE WHEN `primary_flag` = 1 AND `status` = 1 AND `deleted` = 0 THEN `supplier_id` ELSE NULL END) STORED AFTER `deleted`');

CALL sp_srm_add_column('srm_supplier_portal_user', 'status',
    'VARCHAR(20) NOT NULL DEFAULT ''ACTIVE'' AFTER `user_id`');
CALL sp_srm_add_column('srm_supplier_portal_user', 'last_login_time',
    'DATETIME NULL AFTER `status`');
CALL sp_srm_add_column('srm_supplier_portal_user', 'version',
    'INT NOT NULL DEFAULT 0 AFTER `last_login_time`');
CALL sp_srm_add_column('srm_supplier_portal_user', 'deleted',
    'TINYINT NOT NULL DEFAULT 0 AFTER `version`');

CALL sp_srm_add_column('srm_supplier_enrollment', 'user_id',
    'BIGINT NULL AFTER `supplier_id`');
CALL sp_srm_assert_portal_backfill();
UPDATE srm_supplier_enrollment enrollment
JOIN (
    SELECT tenant_id, supplier_id, user_id
    FROM srm_supplier_portal_user
    WHERE status = 'ACTIVE' AND deleted = 0
) portal_user
  ON portal_user.tenant_id = enrollment.tenant_id
 AND portal_user.supplier_id = enrollment.supplier_id
SET enrollment.user_id = portal_user.user_id
WHERE enrollment.user_id IS NULL;
CALL sp_srm_add_column('srm_supplier_enrollment', 'retry_count',
    'INT NOT NULL DEFAULT 0 AFTER `status`');
CALL sp_srm_add_column('srm_supplier_enrollment', 'last_error_code',
    'VARCHAR(100) NULL AFTER `retry_count`');
CALL sp_srm_add_column('srm_supplier_enrollment', 'next_retry_time',
    'DATETIME NULL AFTER `last_error_code`');
CALL sp_srm_add_column('srm_supplier_enrollment', 'version',
    'INT NOT NULL DEFAULT 0 AFTER `next_retry_time`');
CALL sp_srm_add_column('srm_supplier_enrollment', 'deleted',
    'TINYINT NOT NULL DEFAULT 0 AFTER `version`');
UPDATE srm_supplier_enrollment
SET status = CASE status
    WHEN 'PENDING' THEN 'PENDING_ROLE_ASSIGN'
    WHEN 'APPROVED' THEN 'COMPLETED'
    WHEN 'REJECTED' THEN 'ROLE_ASSIGN_FAILED'
    ELSE status END;
CALL sp_srm_add_column('srm_supplier_enrollment', 'active_user_guard',
    'BIGINT GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 AND `status` IN (''PENDING_ROLE_ASSIGN'',''ROLE_ASSIGN_FAILED'') THEN `user_id` ELSE NULL END) STORED AFTER `deleted`');

CALL sp_srm_add_column('srm_supplier_invite', 'version',
    'INT NOT NULL DEFAULT 0 AFTER `used_count`');
CALL sp_srm_add_column('srm_supplier_invite', 'deleted',
    'TINYINT NOT NULL DEFAULT 0 AFTER `version`');

CALL sp_srm_add_column('srm_evaluation_template', 'default_flag',
    'TINYINT NOT NULL DEFAULT 0 AFTER `status`');
CALL sp_srm_add_column('srm_evaluation_template', 'version',
    'INT NOT NULL DEFAULT 0 AFTER `default_flag`');
CALL sp_srm_add_column('srm_evaluation_template', 'deleted',
    'TINYINT NOT NULL DEFAULT 0 AFTER `version`');
UPDATE srm_evaluation_template template_current
JOIN (
    SELECT tenant_id,
           COALESCE(MIN(CASE WHEN status = 1 THEN id END), MIN(id)) default_id
    FROM srm_evaluation_template WHERE deleted = 0
    GROUP BY tenant_id
) selected_default
  ON selected_default.tenant_id = template_current.tenant_id
SET template_current.default_flag = IF(template_current.id = selected_default.default_id, 1, 0),
    template_current.status = IF(template_current.id = selected_default.default_id, 1, template_current.status);

-- 旧预览版可能存在同名模板/维度；保留最早配置，给历史重复项改名并停用，避免破坏评估快照引用。
UPDATE srm_evaluation_template template_current
JOIN (
    SELECT tenant_id, name, deleted,
           COALESCE(MIN(CASE WHEN status = 1 THEN id END), MIN(id)) keep_id
    FROM srm_evaluation_template
    GROUP BY tenant_id, name, deleted
    HAVING COUNT(*) > 1
) duplicate_template
  ON duplicate_template.tenant_id = template_current.tenant_id
 AND duplicate_template.name = template_current.name
 AND duplicate_template.deleted = template_current.deleted
SET template_current.name = CONCAT(LEFT(template_current.name, 170), '#legacy-', template_current.id),
    template_current.status = 0,
    template_current.default_flag = 0,
    template_current.version = template_current.version + 1
WHERE template_current.id <> duplicate_template.keep_id;

CALL sp_srm_add_column('srm_evaluation_dimension', 'status',
    'TINYINT NOT NULL DEFAULT 1 AFTER `sort`');
CALL sp_srm_add_column('srm_evaluation_dimension', 'deleted',
    'TINYINT NOT NULL DEFAULT 0 AFTER `status`');

UPDATE srm_evaluation_dimension dimension_current
JOIN (
    SELECT tenant_id, template_id, indicator_name, deleted, MIN(id) keep_id
    FROM srm_evaluation_dimension
    GROUP BY tenant_id, template_id, indicator_name, deleted
    HAVING COUNT(*) > 1
) duplicate_dimension
  ON duplicate_dimension.tenant_id = dimension_current.tenant_id
 AND duplicate_dimension.template_id = dimension_current.template_id
 AND duplicate_dimension.indicator_name = dimension_current.indicator_name
 AND duplicate_dimension.deleted = dimension_current.deleted
SET dimension_current.indicator_name = CONCAT(LEFT(dimension_current.indicator_name, 170), '#legacy-', dimension_current.id),
    dimension_current.status = 0
WHERE dimension_current.id <> duplicate_dimension.keep_id;

CALL sp_srm_add_column('srm_evaluation_item', 'version',
    'INT NOT NULL DEFAULT 0 AFTER `remark`');
CALL sp_srm_add_column('srm_evaluation_item', 'deleted',
    'TINYINT NOT NULL DEFAULT 0 AFTER `version`');
-- 早期预览版曾以百分制保存 item.score；确定性换算到 MVP 的 1-5 分制。
UPDATE srm_evaluation_item
SET score = ROUND(score / 20, 1)
WHERE score > 5 AND score <= 100;
CALL sp_srm_add_column('srm_risk_indicator', 'version',
    'INT NOT NULL DEFAULT 0 AFTER `remark`');
CALL sp_srm_add_column('srm_risk_indicator', 'deleted',
    'TINYINT NOT NULL DEFAULT 0 AFTER `version`');
CALL sp_srm_add_column('srm_risk_assessment', 'version',
    'INT NOT NULL DEFAULT 0 AFTER `remark`');
CALL sp_srm_add_column('srm_risk_assessment', 'deleted',
    'TINYINT NOT NULL DEFAULT 0 AFTER `version`');

-- 先归一风险等级，再合并会与规范类型碰撞的旧别名；合并时保留最高风险，禁止静默降级。
UPDATE srm_risk_indicator
SET indicator_type = UPPER(TRIM(indicator_type));

UPDATE srm_risk_indicator
SET risk_level = CASE UPPER(TRIM(risk_level))
    WHEN 'HIGH' THEN 'RED'
    WHEN 'MEDIUM' THEN 'YELLOW'
    WHEN 'LOW' THEN 'GREEN'
    ELSE UPPER(TRIM(risk_level))
END;
UPDATE srm_risk_assessment
SET overall_level = CASE UPPER(TRIM(overall_level))
    WHEN 'HIGH' THEN 'RED'
    WHEN 'MEDIUM' THEN 'YELLOW'
    WHEN 'LOW' THEN 'GREEN'
    ELSE UPPER(TRIM(overall_level))
END;

UPDATE srm_risk_indicator supply_current
JOIN srm_risk_indicator delivery_legacy
  ON delivery_legacy.tenant_id = supply_current.tenant_id
 AND delivery_legacy.supplier_id = supply_current.supplier_id
 AND delivery_legacy.indicator_type = 'DELIVERY'
SET supply_current.risk_level = CASE
        WHEN FIELD(delivery_legacy.risk_level, 'GREEN','YELLOW','RED')
           > FIELD(supply_current.risk_level, 'GREEN','YELLOW','RED')
        THEN delivery_legacy.risk_level ELSE supply_current.risk_level END,
    supply_current.indicator_value = COALESCE(supply_current.indicator_value,
                                               delivery_legacy.indicator_value),
    supply_current.assessment_time = GREATEST(supply_current.assessment_time,
                                              delivery_legacy.assessment_time),
    supply_current.remark = LEFT(CONCAT_WS('; ', NULLIF(supply_current.remark, ''),
                                           NULLIF(delivery_legacy.remark, '')), 500),
    supply_current.deleted = LEAST(supply_current.deleted, delivery_legacy.deleted),
    supply_current.version = supply_current.version + 1,
    supply_current.update_by = 'srm-migration'
WHERE supply_current.indicator_type = 'SUPPLY';

DELETE delivery_legacy
FROM srm_risk_indicator delivery_legacy
JOIN srm_risk_indicator supply_current
  ON supply_current.tenant_id = delivery_legacy.tenant_id
 AND supply_current.supplier_id = delivery_legacy.supplier_id
 AND supply_current.indicator_type = 'SUPPLY'
WHERE delivery_legacy.indicator_type = 'DELIVERY';

UPDATE srm_risk_indicator
SET indicator_type = 'SUPPLY', version = version + 1
WHERE indicator_type = 'DELIVERY';

UPDATE srm_risk_indicator certificate_current
JOIN (
    SELECT tenant_id, supplier_id,
           CASE MAX(CASE risk_level WHEN 'RED' THEN 3 WHEN 'YELLOW' THEN 2 ELSE 1 END)
               WHEN 3 THEN 'RED' WHEN 2 THEN 'YELLOW' ELSE 'GREEN' END AS risk_level,
           MIN(NULLIF(indicator_value, '')) AS indicator_value,
           MAX(assessment_time) AS assessment_time,
           MAX(NULLIF(remark, '')) AS remark,
           MIN(deleted) AS deleted
    FROM srm_risk_indicator
    WHERE indicator_type LIKE 'QUALIFICATION\_%' ESCAPE '\\'
    GROUP BY tenant_id, supplier_id
) qualification_legacy
  ON qualification_legacy.tenant_id = certificate_current.tenant_id
 AND qualification_legacy.supplier_id = certificate_current.supplier_id
SET certificate_current.risk_level = CASE
        WHEN FIELD(qualification_legacy.risk_level, 'GREEN','YELLOW','RED')
           > FIELD(certificate_current.risk_level, 'GREEN','YELLOW','RED')
        THEN qualification_legacy.risk_level ELSE certificate_current.risk_level END,
    certificate_current.indicator_value = COALESCE(certificate_current.indicator_value,
                                                    qualification_legacy.indicator_value),
    certificate_current.assessment_time = GREATEST(certificate_current.assessment_time,
                                                   qualification_legacy.assessment_time),
    certificate_current.remark = LEFT(CONCAT_WS('; ', NULLIF(certificate_current.remark, ''),
                                                qualification_legacy.remark), 500),
    certificate_current.deleted = LEAST(certificate_current.deleted, qualification_legacy.deleted),
    certificate_current.version = certificate_current.version + 1,
    certificate_current.update_by = 'srm-migration'
WHERE certificate_current.indicator_type = 'CERTIFICATE';

DELETE qualification_legacy
FROM srm_risk_indicator qualification_legacy
JOIN srm_risk_indicator certificate_current
  ON certificate_current.tenant_id = qualification_legacy.tenant_id
 AND certificate_current.supplier_id = qualification_legacy.supplier_id
 AND certificate_current.indicator_type = 'CERTIFICATE'
WHERE qualification_legacy.indicator_type LIKE 'QUALIFICATION\_%' ESCAPE '\\';

UPDATE srm_risk_indicator qualification_keeper
JOIN (
    SELECT MIN(id) AS keep_id,
           CASE MAX(CASE risk_level WHEN 'RED' THEN 3 WHEN 'YELLOW' THEN 2 ELSE 1 END)
               WHEN 3 THEN 'RED' WHEN 2 THEN 'YELLOW' ELSE 'GREEN' END AS risk_level,
           MIN(NULLIF(indicator_value, '')) AS indicator_value,
           MAX(assessment_time) AS assessment_time,
           MAX(NULLIF(remark, '')) AS remark,
           MIN(deleted) AS deleted
    FROM srm_risk_indicator
    WHERE indicator_type LIKE 'QUALIFICATION\_%' ESCAPE '\\'
    GROUP BY tenant_id, supplier_id
) qualification_group ON qualification_group.keep_id = qualification_keeper.id
SET qualification_keeper.risk_level = qualification_group.risk_level,
    qualification_keeper.indicator_value = COALESCE(qualification_keeper.indicator_value,
                                                     qualification_group.indicator_value),
    qualification_keeper.assessment_time = qualification_group.assessment_time,
    qualification_keeper.remark = LEFT(CONCAT_WS('; ', NULLIF(qualification_keeper.remark, ''),
                                                 qualification_group.remark), 500),
    qualification_keeper.deleted = qualification_group.deleted,
    qualification_keeper.version = qualification_keeper.version + 1,
    qualification_keeper.update_by = 'srm-migration';

DELETE qualification_duplicate
FROM srm_risk_indicator qualification_duplicate
JOIN (
    SELECT tenant_id, supplier_id, MIN(id) keep_id
    FROM srm_risk_indicator
    WHERE indicator_type LIKE 'QUALIFICATION\_%' ESCAPE '\\'
    GROUP BY tenant_id, supplier_id
) certificate_group
  ON certificate_group.tenant_id = qualification_duplicate.tenant_id
 AND certificate_group.supplier_id = qualification_duplicate.supplier_id
WHERE qualification_duplicate.indicator_type LIKE 'QUALIFICATION\_%' ESCAPE '\\'
  AND qualification_duplicate.id <> certificate_group.keep_id;

UPDATE srm_risk_indicator
SET indicator_type = 'CERTIFICATE', version = version + 1
WHERE indicator_type LIKE 'QUALIFICATION\_%' ESCAPE '\\';

-- 收敛历史枚举别名；未知值保留到完整性检查中显式失败，避免静默篡改业务含义。
-- 唯一键覆盖逻辑删除行，因此规范类型的历史重复项必须只保留一条。
DELETE duplicate_indicator
FROM srm_risk_indicator duplicate_indicator
JOIN srm_risk_indicator keeper
  ON keeper.tenant_id = duplicate_indicator.tenant_id
 AND keeper.supplier_id = duplicate_indicator.supplier_id
 AND keeper.indicator_type = duplicate_indicator.indicator_type
 AND keeper.id < duplicate_indicator.id
WHERE duplicate_indicator.indicator_type IN
      ('FINANCIAL','COMPLIANCE','SUPPLY','COOPERATION','QUALITY','CERTIFICATE');

UPDATE srm_risk_indicator
SET deleted = 0, version = version + 1
WHERE indicator_type IN ('FINANCIAL','COMPLIANCE','SUPPLY','COOPERATION','QUALITY','CERTIFICATE')
  AND deleted <> 0;

INSERT INTO srm_risk_indicator
    (tenant_id, supplier_id, indicator_type, indicator_value, risk_level,
     assessment_time, remark, version, deleted, create_by)
SELECT supplier.tenant_id, supplier.id, risk_seed.indicator_type,
       NULL, 'GREEN', CURRENT_TIMESTAMP, '系统初始化', 0, 0, 'system'
FROM srm_supplier supplier
CROSS JOIN (
    SELECT 'FINANCIAL' indicator_type
    UNION ALL SELECT 'COMPLIANCE'
    UNION ALL SELECT 'SUPPLY'
    UNION ALL SELECT 'COOPERATION'
    UNION ALL SELECT 'QUALITY'
    UNION ALL SELECT 'CERTIFICATE'
) risk_seed
WHERE supplier.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM srm_risk_indicator indicator_current
      WHERE indicator_current.tenant_id = supplier.tenant_id
        AND indicator_current.supplier_id = supplier.id
        AND indicator_current.indicator_type = risk_seed.indicator_type
        AND indicator_current.deleted = 0
  );

CALL sp_srm_assert_integrity();
ALTER TABLE srm_supplier_enrollment MODIFY COLUMN user_id BIGINT NOT NULL;
ALTER TABLE srm_evaluation_item MODIFY COLUMN score DECIMAL(3,1) NOT NULL COMMENT '评分 1-5';

CALL sp_srm_add_index('srm_supplier', 'uk_srm_supplier_no',
    'UNIQUE KEY `uk_srm_supplier_no` (`tenant_id`,`supplier_no`)');
CALL sp_srm_add_index('srm_supplier', 'uk_srm_supplier_credit',
    'UNIQUE KEY `uk_srm_supplier_credit` (`tenant_id`,`credit_code`)');
CALL sp_srm_add_index('srm_supplier', 'idx_srm_supplier_owner_status',
    'INDEX `idx_srm_supplier_owner_status` (`tenant_id`,`owner_user_id`,`status`,`deleted`)');
CALL sp_srm_add_index('srm_supplier', 'idx_srm_supplier_unit_status',
    'INDEX `idx_srm_supplier_unit_status` (`tenant_id`,`owner_unit_id`,`status`,`deleted`)');
CALL sp_srm_add_index('srm_supplier', 'idx_srm_supplier_name',
    'INDEX `idx_srm_supplier_name` (`tenant_id`,`normalized_name`,`deleted`)');
CALL sp_srm_add_index('srm_supplier', 'idx_srm_supplier_category_status',
    'INDEX `idx_srm_supplier_category_status` (`tenant_id`,`category_code`,`status`,`deleted`)');
CALL sp_srm_add_index('srm_supplier', 'idx_srm_supplier_level',
    'INDEX `idx_srm_supplier_level` (`tenant_id`,`level_code`,`deleted`)');
CALL sp_srm_add_index('srm_supplier_contact', 'uk_srm_contact_primary',
    'UNIQUE KEY `uk_srm_contact_primary` (`tenant_id`,`primary_supplier_guard`)');
CALL sp_srm_add_index('srm_supplier_contact', 'idx_srm_contact_supplier',
    'INDEX `idx_srm_contact_supplier` (`tenant_id`,`supplier_id`,`status`,`deleted`)');
CALL sp_srm_add_index('srm_supplier_qualification', 'idx_srm_qual_supplier',
    'INDEX `idx_srm_qual_supplier` (`tenant_id`,`supplier_id`,`deleted`)');
CALL sp_srm_add_index('srm_supplier_qualification', 'idx_srm_qual_expiry',
    'INDEX `idx_srm_qual_expiry` (`tenant_id`,`expiry_date`)');
CALL sp_srm_add_index('srm_supplier_bank_account', 'uk_srm_bank_primary',
    'UNIQUE KEY `uk_srm_bank_primary` (`tenant_id`,`primary_supplier_guard`)');
CALL sp_srm_add_index('srm_supplier_bank_account', 'idx_srm_bank_supplier',
    'INDEX `idx_srm_bank_supplier` (`tenant_id`,`supplier_id`,`deleted`)');
CALL sp_srm_add_index('srm_supplier_portal_user', 'uk_srm_portal_user',
    'UNIQUE KEY `uk_srm_portal_user` (`tenant_id`,`user_id`)');
CALL sp_srm_add_index('srm_supplier_portal_user', 'idx_srm_portal_supplier',
    'INDEX `idx_srm_portal_supplier` (`tenant_id`,`supplier_id`,`status`,`deleted`)');
CALL sp_srm_add_index('srm_supplier_invite', 'uk_srm_invite_hash',
    'UNIQUE KEY `uk_srm_invite_hash` (`tenant_id`,`invite_code_hash`)');
CALL sp_srm_add_index('srm_supplier_invite', 'idx_srm_invite_status',
    'INDEX `idx_srm_invite_status` (`tenant_id`,`status`,`expires_time`,`deleted`)');
CALL sp_srm_add_index('srm_supplier_enrollment', 'uk_srm_enrollment_req',
    'UNIQUE KEY `uk_srm_enrollment_req` (`tenant_id`,`request_id`)');
CALL sp_srm_add_index('srm_supplier_enrollment', 'idx_srm_enrollment_user_status',
    'INDEX `idx_srm_enrollment_user_status` (`tenant_id`,`user_id`,`status`,`deleted`)');
CALL sp_srm_add_index('srm_supplier_enrollment', 'uk_srm_enrollment_active_user',
    'UNIQUE KEY `uk_srm_enrollment_active_user` (`tenant_id`,`active_user_guard`)');
CALL sp_srm_add_index('srm_supplier_enrollment', 'idx_srm_enrollment_supplier',
    'INDEX `idx_srm_enrollment_supplier` (`tenant_id`,`supplier_id`,`deleted`)');
CALL sp_srm_add_index('srm_evaluation_template', 'uk_srm_eval_tpl_name',
    'UNIQUE KEY `uk_srm_eval_tpl_name` (`tenant_id`,`name`,`deleted`)');
CALL sp_srm_add_index('srm_evaluation_template', 'idx_srm_eval_tpl_status',
    'INDEX `idx_srm_eval_tpl_status` (`tenant_id`,`default_flag`,`status`,`deleted`)');
CALL sp_srm_add_index('srm_evaluation_dimension', 'uk_srm_eval_dim_name',
    'UNIQUE KEY `uk_srm_eval_dim_name` (`tenant_id`,`template_id`,`indicator_name`,`deleted`)');
CALL sp_srm_add_index('srm_evaluation_dimension', 'idx_srm_eval_dim_tpl',
    'INDEX `idx_srm_eval_dim_tpl` (`tenant_id`,`template_id`,`status`,`deleted`)');
CALL sp_srm_add_index('srm_evaluation', 'idx_srm_eval_supplier',
    'INDEX `idx_srm_eval_supplier` (`tenant_id`,`supplier_id`,`deleted`)');
CALL sp_srm_add_index('srm_evaluation', 'idx_srm_eval_owner',
    'INDEX `idx_srm_eval_owner` (`tenant_id`,`owner_user_id`,`deleted`)');
CALL sp_srm_add_index('srm_evaluation', 'idx_srm_eval_period',
    'INDEX `idx_srm_eval_period` (`tenant_id`,`evaluation_period`)');
CALL sp_srm_add_index('srm_evaluation_item', 'uk_srm_eval_item_dim',
    'UNIQUE KEY `uk_srm_eval_item_dim` (`tenant_id`,`evaluation_id`,`dimension_id`)');
CALL sp_srm_add_index('srm_evaluation_item', 'idx_srm_eval_item_eval',
    'INDEX `idx_srm_eval_item_eval` (`tenant_id`,`evaluation_id`,`deleted`)');
CALL sp_srm_add_index('srm_risk_indicator', 'uk_srm_risk_ind_type',
    'UNIQUE KEY `uk_srm_risk_ind_type` (`tenant_id`,`supplier_id`,`indicator_type`)');
CALL sp_srm_add_index('srm_risk_indicator', 'idx_srm_risk_ind_supplier',
    'INDEX `idx_srm_risk_ind_supplier` (`tenant_id`,`supplier_id`,`indicator_type`,`deleted`)');
CALL sp_srm_add_index('srm_risk_indicator', 'idx_srm_risk_ind_level',
    'INDEX `idx_srm_risk_ind_level` (`tenant_id`,`risk_level`,`deleted`)');
CALL sp_srm_add_index('srm_risk_assessment', 'idx_srm_risk_assess_supplier',
    'INDEX `idx_srm_risk_assess_supplier` (`tenant_id`,`supplier_id`,`assessment_time`,`deleted`)');
CALL sp_srm_add_index('srm_quotation', 'uk_srm_quote_request',
    'UNIQUE KEY `uk_srm_quote_request` (`tenant_id`,`request_id`)');
CALL sp_srm_add_index('srm_quotation', 'uk_srm_quote_active_supplier',
    'UNIQUE KEY `uk_srm_quote_active_supplier` (`tenant_id`,`rfq_id`,`active_supplier_guard`)');
CALL sp_srm_add_index('srm_quotation', 'idx_srm_quote_rfq_status',
    'INDEX `idx_srm_quote_rfq_status` (`tenant_id`,`rfq_id`,`status`,`deleted`)');
CALL sp_srm_add_index('srm_quotation', 'idx_srm_quote_supplier_time',
    'INDEX `idx_srm_quote_supplier_time` (`tenant_id`,`supplier_id`,`quotation_time`,`deleted`)');
CALL sp_srm_add_index('srm_quotation', 'idx_srm_quote_valid_until',
    'INDEX `idx_srm_quote_valid_until` (`tenant_id`,`status`,`valid_until`,`deleted`)');
CALL sp_srm_add_index('srm_quotation_line', 'uk_srm_quote_line_active',
    'UNIQUE KEY `uk_srm_quote_line_active` (`tenant_id`,`quotation_id`,`active_rfq_line_guard`)');
CALL sp_srm_add_index('srm_quotation_line', 'idx_srm_quote_line_quote',
    'INDEX `idx_srm_quote_line_quote` (`tenant_id`,`quotation_id`,`deleted`)');
CALL sp_srm_add_index('srm_quotation_line', 'idx_srm_quote_line_rfq_line',
    'INDEX `idx_srm_quote_line_rfq_line` (`tenant_id`,`rfq_line_id`,`deleted`)');
CALL sp_srm_add_index('srm_quotation_request', 'uk_srm_quote_req_id',
    'UNIQUE KEY `uk_srm_quote_req_id` (`tenant_id`,`request_id`)');
CALL sp_srm_add_index('srm_quotation_request', 'uk_srm_quote_req_version',
    'UNIQUE KEY `uk_srm_quote_req_version` (`tenant_id`,`quotation_id`,`target_version`)');
CALL sp_srm_add_index('srm_quotation_request', 'idx_srm_quote_req_business',
    'INDEX `idx_srm_quote_req_business` (`tenant_id`,`rfq_id`,`supplier_id`,`status`)');
CALL sp_srm_add_index('srm_quotation_request', 'idx_srm_quote_req_quote',
    'INDEX `idx_srm_quote_req_quote` (`tenant_id`,`quotation_id`,`create_time`)');
CALL sp_srm_add_index('sys_mq_message', 'uk_srm_mq_msg_id',
    'UNIQUE KEY `uk_srm_mq_msg_id` (`msg_id`)');
CALL sp_srm_add_index('sys_mq_message', 'idx_srm_mq_relay',
    'INDEX `idx_srm_mq_relay` (`status`,`next_retry_time`)');
CALL sp_srm_add_index('sys_mq_message', 'idx_srm_mq_tenant_time',
    'INDEX `idx_srm_mq_tenant_time` (`tenant_id`,`create_time`)');

CALL sp_srm_add_constraint('srm_evaluation_item', 'chk_srm_eval_item_score',
    'CHECK (`score` >= 1 AND `score` <= 5)');
CALL sp_srm_add_constraint('srm_risk_indicator', 'chk_srm_risk_ind_type',
    'CHECK (`indicator_type` IN (''FINANCIAL'',''COMPLIANCE'',''SUPPLY'',''COOPERATION'',''QUALITY'',''CERTIFICATE''))');
CALL sp_srm_add_constraint('srm_risk_indicator', 'chk_srm_risk_ind_level',
    'CHECK (`risk_level` IN (''GREEN'',''YELLOW'',''RED''))');
CALL sp_srm_add_constraint('srm_risk_assessment', 'chk_srm_risk_assess_level',
    'CHECK (`overall_level` IN (''GREEN'',''YELLOW'',''RED''))');
CALL sp_srm_add_constraint('srm_quotation', 'chk_srm_quote_status',
    'CHECK (`status` IN (''DRAFT'',''SUBMITTED'',''WITHDRAWN'',''EXPIRED''))');
CALL sp_srm_add_constraint('srm_quotation', 'chk_srm_quote_total',
    'CHECK (`total_amount` > 0)');
CALL sp_srm_add_constraint('srm_quotation_line', 'chk_srm_quote_line_price',
    'CHECK (`unit_price` > 0)');
CALL sp_srm_add_constraint('srm_quotation_line', 'chk_srm_quote_line_quantity',
    'CHECK (`quantity` > 0)');
CALL sp_srm_add_constraint('srm_quotation_line', 'chk_srm_quote_line_amount',
    'CHECK (`line_amount` > 0)');
CALL sp_srm_add_constraint('srm_quotation_line', 'chk_srm_quote_line_delivery',
    'CHECK (`delivery_days` BETWEEN 0 AND 3650)');
CALL sp_srm_add_constraint('srm_quotation_request', 'chk_srm_quote_req_status',
    'CHECK (`status` IN (''RESERVED'',''COMPLETED''))');

-- 既有租户幂等补齐默认评估模板和四个维度。
INSERT INTO srm_evaluation_template
    (tenant_id, name, status, default_flag, version, deleted, create_by)
SELECT tenant.id, '默认供应商评估模板', 1, 1, 0, 0, 'system'
FROM omni_auth.sys_tenant tenant
WHERE tenant.status = 1
  AND NOT EXISTS (
      SELECT 1 FROM srm_evaluation_template template_current
      WHERE template_current.tenant_id = tenant.id
        AND template_current.default_flag = 1
        AND template_current.status = 1
        AND template_current.deleted = 0
  );

INSERT INTO srm_evaluation_dimension
    (tenant_id, template_id, indicator_name, weight, sort, status, deleted, create_by)
SELECT template_current.tenant_id, template_current.id,
       dimension_seed.indicator_name, dimension_seed.weight,
       dimension_seed.sort, 1, 0, 'system'
FROM srm_evaluation_template template_current
CROSS JOIN (
    SELECT '质量' indicator_name, 30.00 weight, 1 sort
    UNION ALL SELECT '交期', 30.00, 2
    UNION ALL SELECT '价格', 20.00, 3
    UNION ALL SELECT '服务', 20.00, 4
) dimension_seed
WHERE template_current.default_flag = 1
  AND template_current.status = 1
  AND template_current.deleted = 0
ON DUPLICATE KEY UPDATE
    weight = VALUES(weight),
    sort = VALUES(sort),
    status = 1,
    update_by = 'system';

-- 默认模板在 MVP 仅启用四个固定维度；历史自定义项保留但停用，评估快照不受影响。
UPDATE srm_evaluation_dimension dimension_current
JOIN srm_evaluation_template template_current
  ON template_current.id = dimension_current.template_id
 AND template_current.tenant_id = dimension_current.tenant_id
SET dimension_current.status = 0,
    dimension_current.update_by = 'system'
WHERE template_current.default_flag = 1
  AND template_current.status = 1
  AND template_current.deleted = 0
  AND dimension_current.deleted = 0
  AND dimension_current.indicator_name NOT IN ('质量','交期','价格','服务');

DROP PROCEDURE IF EXISTS sp_srm_assert_integrity;
DROP PROCEDURE IF EXISTS sp_srm_assert_portal_backfill;
DROP PROCEDURE IF EXISTS sp_srm_add_constraint;
DROP PROCEDURE IF EXISTS sp_srm_add_index;
DROP PROCEDURE IF EXISTS sp_srm_rename_column;
DROP PROCEDURE IF EXISTS sp_srm_add_column;
FLUSH PRIVILEGES;
