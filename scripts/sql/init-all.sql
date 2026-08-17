-- ============================================================
-- Omni-Stack 数据库初始化脚本（权威版本）
-- ============================================================
-- 用途：一键创建 omni_auth 数据库，包含全部表结构和种子数据。
-- 适用场景：
--   1. Docker MySQL 容器首次启动时自动执行（挂载到 /docker-entrypoint-initdb.d/）
--   2. 手动执行：mysql -uroot -proot < scripts/sql/init-all.sql
--
-- 表结构概览（共 15 表）：
--   OAuth2 标准表（3 表）：
--     oauth2_registered_client — 客户端注册
--     oauth2_authorization     — 授权记录
--     oauth2_authorization_consent — 授权同意
--   多租户 RBAC 表（12 表）：
--     sys_tenant, sys_user, sys_role, sys_permission,
--     sys_user_role, sys_role_permission, sys_org_unit,
--     sys_user_unit, sys_role_dept, sys_token_blacklist,
--     sys_user_oauth_provider, sys_audit_log
--
-- 种子数据：
--   1 个默认租户、1 个根组织单元、1 个管理员用户（admin/admin123）、
--   1 个超级管理员角色、43 个权限节点、43 条角色权限映射
--
-- 注意：此脚本使用 CREATE TABLE IF NOT EXISTS，可重复执行。
-- ============================================================

-- ============================================================
-- Section 1: 创建数据库
-- ============================================================

-- 强制客户端使用 UTF-8 字符集，防止 Docker 初始化时中文乱码
SET NAMES utf8mb4;

-- 创建应用专用用户（非 root，以确保 --init-connect=SET NAMES utf8mb4 生效）
CREATE USER IF NOT EXISTS 'omni_app'@'%' IDENTIFIED BY 'omni_app_pass';

CREATE DATABASE IF NOT EXISTS omni_auth
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE omni_auth;

-- ============================================================
-- Section 2: OAuth2 标准表（Spring Authorization Server 7.x）
-- ============================================================

-- 2.1 OAuth2 客户端注册表
CREATE TABLE IF NOT EXISTS oauth2_registered_client (
    id                            VARCHAR(100) NOT NULL,
    client_id                     VARCHAR(100) NOT NULL,
    client_id_issued_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    client_secret                 VARCHAR(400) DEFAULT NULL,
    client_secret_expires_at      TIMESTAMP DEFAULT NULL,
    client_name                   VARCHAR(200) NOT NULL,
    client_authentication_methods VARCHAR(1000) NOT NULL,
    authorization_grant_types     VARCHAR(1000) NOT NULL,
    redirect_uris                 VARCHAR(1000) DEFAULT NULL,
    post_logout_redirect_uris     VARCHAR(1000) DEFAULT NULL,
    scopes                        VARCHAR(1000) NOT NULL,
    client_settings               VARCHAR(2000) NOT NULL,
    token_settings                VARCHAR(2000) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_client_id (client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2.2 OAuth2 授权记录表（含 SAS 7 Device Authorization Grant 列）
CREATE TABLE IF NOT EXISTS oauth2_authorization (
    id                            VARCHAR(100) NOT NULL,
    registered_client_id          VARCHAR(100) NOT NULL,
    principal_name                VARCHAR(200) DEFAULT NULL,
    authorization_grant_type      VARCHAR(100) DEFAULT NULL,
    authorized_scopes             VARCHAR(1000) DEFAULT NULL,
    attributes                    TEXT DEFAULT NULL,
    state                         VARCHAR(500) DEFAULT NULL,
    authorization_code_value      TEXT DEFAULT NULL,
    authorization_code_issued_at    TIMESTAMP DEFAULT NULL,
    authorization_code_expires_at TIMESTAMP DEFAULT NULL,
    authorization_code_metadata   VARCHAR(2000) DEFAULT NULL,
    access_token_value            TEXT DEFAULT NULL,
    access_token_issued_at        TIMESTAMP DEFAULT NULL,
    access_token_expires_at       TIMESTAMP DEFAULT NULL,
    access_token_metadata         VARCHAR(2000) DEFAULT NULL,
    access_token_type             VARCHAR(100) DEFAULT NULL,
    access_token_scopes           VARCHAR(1000) DEFAULT NULL,
    oidc_id_token_value           TEXT DEFAULT NULL,
    oidc_id_token_issued_at       TIMESTAMP DEFAULT NULL,
    oidc_id_token_expires_at      TIMESTAMP DEFAULT NULL,
    oidc_id_token_metadata        VARCHAR(2000) DEFAULT NULL,
    refresh_token_value           TEXT DEFAULT NULL,
    refresh_token_issued_at       TIMESTAMP DEFAULT NULL,
    refresh_token_expires_at      TIMESTAMP DEFAULT NULL,
    refresh_token_metadata        VARCHAR(2000) DEFAULT NULL,
    user_code_value               TEXT DEFAULT NULL,
    user_code_issued_at           TIMESTAMP DEFAULT NULL,
    user_code_expires_at          TIMESTAMP DEFAULT NULL,
    user_code_metadata            VARCHAR(2000) DEFAULT NULL,
    device_code_value             TEXT DEFAULT NULL,
    device_code_issued_at         TIMESTAMP DEFAULT NULL,
    device_code_expires_at        TIMESTAMP DEFAULT NULL,
    device_code_metadata          VARCHAR(2000) DEFAULT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2.3 OAuth2 授权同意记录表
CREATE TABLE IF NOT EXISTS oauth2_authorization_consent (
    registered_client_id VARCHAR(100) NOT NULL,
    principal_name       VARCHAR(200) NOT NULL,
    authorities          VARCHAR(1000) DEFAULT NULL,
    scopes               VARCHAR(1000) DEFAULT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Section 3: 多租户 RBAC 业务表
-- ============================================================

-- 3.1 租户表
CREATE TABLE IF NOT EXISTS sys_tenant (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '租户ID',
    tenant_code VARCHAR(64)  NOT NULL UNIQUE COMMENT '租户编码',
    tenant_name VARCHAR(100) NOT NULL COMMENT '租户名称',
    domain      VARCHAR(200) DEFAULT NULL COMMENT '租户域名',
    contact_name    VARCHAR(100) DEFAULT NULL COMMENT '联系人',
    contact_phone   VARCHAR(20)  DEFAULT NULL COMMENT '联系电话',
    status      TINYINT      DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by   VARCHAR(64)  DEFAULT NULL,
    update_by   VARCHAR(64)  DEFAULT NULL,
    INDEX idx_tenant_code (tenant_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户表';

-- 3.2 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    tenant_id      BIGINT       NOT NULL COMMENT '租户ID',
    username       VARCHAR(64)  NOT NULL COMMENT '用户名',
    password       VARCHAR(256) DEFAULT NULL COMMENT '密码',
    nickname       VARCHAR(100) DEFAULT NULL COMMENT '昵称',
    email          VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    phone          VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    avatar         VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    gender         TINYINT      DEFAULT 0 COMMENT '性别: 0-未知, 1-男, 2-女',
    primary_unit_id BIGINT      DEFAULT NULL COMMENT '主组织单元ID',
    status         TINYINT      DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by      VARCHAR(64)  DEFAULT NULL,
    update_by      VARCHAR(64)  DEFAULT NULL,
    UNIQUE KEY uk_user_tenant_username (tenant_id, username),
    INDEX idx_user_tenant (tenant_id),
    INDEX idx_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 3.3 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '角色ID',
    tenant_id   BIGINT       NOT NULL COMMENT '租户ID',
    role_code   VARCHAR(64)  NOT NULL COMMENT '角色编码',
    role_name   VARCHAR(100) NOT NULL COMMENT '角色名称',
    data_scope  VARCHAR(20)  DEFAULT 'TENANT' COMMENT '数据范围: ALL/TENANT/DEPT_AND_BELOW/DEPT/SELF/CUSTOM',
    sort        INT          DEFAULT 0 COMMENT '排序',
    status      TINYINT      DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by   VARCHAR(64)  DEFAULT NULL,
    update_by   VARCHAR(64)  DEFAULT NULL,
    UNIQUE KEY uk_role_tenant_code (tenant_id, role_code),
    INDEX idx_role_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- 3.4 权限表（物化路径层级结构）
CREATE TABLE IF NOT EXISTS sys_permission (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '权限ID',
    tenant_id   BIGINT       NOT NULL COMMENT '租户ID',
    parent_id   BIGINT       DEFAULT 0 COMMENT '父权限ID',
    permission_code VARCHAR(200) NOT NULL COMMENT '权限编码: resource:action',
    permission_name VARCHAR(100) NOT NULL COMMENT '权限名称',
    type        VARCHAR(20)  NOT NULL COMMENT '类型: DIRECTORY/MENU/BUTTON/API',
    path        VARCHAR(500) NOT NULL DEFAULT '' COMMENT '物化路径',
    depth       INT          DEFAULT 1 COMMENT '深度',
    sort        INT          DEFAULT 0 COMMENT '排序',
    status      TINYINT      DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by   VARCHAR(64)  DEFAULT NULL,
    update_by   VARCHAR(64)  DEFAULT NULL,
    INDEX idx_permission_tenant (tenant_id),
    INDEX idx_permission_path (path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

-- 3.5 用户角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_user (user_id),
    INDEX idx_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- 3.6 角色权限关联表
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id     BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    INDEX idx_role (role_id),
    INDEX idx_permission (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- 3.7 组织单元表（物化路径层级结构）
CREATE TABLE IF NOT EXISTS sys_org_unit (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '组织单元ID',
    tenant_id   BIGINT       NOT NULL COMMENT '租户ID',
    parent_id   BIGINT       DEFAULT 0 COMMENT '父节点ID',
    name        VARCHAR(100) NOT NULL COMMENT '单元名称',
    type        VARCHAR(20)  NOT NULL COMMENT '类型: ORG/DEPT/TEAM/GROUP',
    unit_code   VARCHAR(50)  DEFAULT NULL COMMENT '单元编码（同父节点下唯一）',
    path        VARCHAR(500) NOT NULL COMMENT '物化路径',
    depth       INT          NOT NULL DEFAULT 1 COMMENT '深度',
    sort        INT          DEFAULT 0 COMMENT '排序',
    status      TINYINT      DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by   VARCHAR(64)  DEFAULT NULL,
    update_by   VARCHAR(64)  DEFAULT NULL,
    INDEX idx_org_unit_tenant (tenant_id),
    INDEX idx_org_unit_path (path),
    INDEX idx_org_unit_tenant_parent (tenant_id, parent_id),
    UNIQUE KEY uk_org_unit_parent_code (parent_id, unit_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组织单元表';

-- 3.8 用户组织关联表
CREATE TABLE IF NOT EXISTS sys_user_unit (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    unit_id BIGINT NOT NULL COMMENT '组织单元ID',
    is_primary TINYINT DEFAULT 0 COMMENT '是否主组织: 0-否, 1-是',
    UNIQUE KEY uk_user_unit (user_id, unit_id),
    INDEX idx_user (user_id),
    INDEX idx_unit (unit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户组织关联表';

-- 3.9 角色数据范围关联表（CUSTOM 数据范围时使用）
CREATE TABLE IF NOT EXISTS sys_role_dept (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL COMMENT '角色ID',
    dept_id BIGINT NOT NULL COMMENT '组织单元ID',
    UNIQUE KEY uk_role_dept (role_id, dept_id),
    INDEX idx_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色数据范围关联表';

-- 3.10 Token 黑名单表
CREATE TABLE IF NOT EXISTS sys_token_blacklist (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    token_jti   VARCHAR(100) NOT NULL COMMENT 'Token JTI',
    token_type  VARCHAR(20)  NOT NULL COMMENT '类型: ACCESS/REFRESH',
    expire_time DATETIME     NOT NULL COMMENT '过期时间',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_token_jti (token_jti),
    INDEX idx_expire_time (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Token黑名单表';

-- 3.11 用户第三方身份关联表
CREATE TABLE IF NOT EXISTS sys_user_oauth_provider (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id           BIGINT       NOT NULL COMMENT '本地用户ID',
    provider          VARCHAR(32)  NOT NULL COMMENT '提供商标识（github/google/wechat/gitee）',
    provider_user_id  VARCHAR(100) NOT NULL COMMENT '第三方用户ID',
    provider_username VARCHAR(100) DEFAULT NULL COMMENT '第三方用户名',
    provider_email    VARCHAR(200) DEFAULT NULL COMMENT '第三方邮箱',
    provider_avatar   VARCHAR(500) DEFAULT NULL COMMENT '第三方头像URL',
    access_token      VARCHAR(500) DEFAULT NULL COMMENT '第三方访问令牌',
    create_time       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_provider_user (provider, provider_user_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户第三方身份关联表';

-- 3.12 安全审计日志表（追加写入，不可变记录）
CREATE TABLE IF NOT EXISTS sys_audit_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '审计日志ID',
    tenant_id   BIGINT       NOT NULL COMMENT '租户ID',
    event_type  VARCHAR(32)  NOT NULL COMMENT '事件类型: LOGIN_SUCCESS/LOGIN_FAILED/LOGOUT/ACCOUNT_LOCKED/ACCOUNT_UNLOCKED/PASSWORD_CHANGED/USER_CREATED/USER_DELETED/USER_STATUS_CHANGED/ROLE_ASSIGNED/ROLE_REVOKED',
    username    VARCHAR(64)  DEFAULT NULL COMMENT '操作目标用户名',
    user_id     BIGINT       DEFAULT NULL COMMENT '操作目标用户ID',
    ip_address  VARCHAR(64)  DEFAULT NULL COMMENT '客户端IP地址',
    user_agent  VARCHAR(500) DEFAULT NULL COMMENT '客户端User-Agent',
    description VARCHAR(500) DEFAULT NULL COMMENT '事件描述',
    extra       JSON         DEFAULT NULL COMMENT '事件扩展字段（JSON）',
    create_by   VARCHAR(64)  DEFAULT NULL COMMENT '操作人（用户名或system）',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '事件发生时间',
    INDEX idx_audit_tenant (tenant_id),
    INDEX idx_audit_event_type (event_type),
    INDEX idx_audit_username (username),
    INDEX idx_audit_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='安全审计日志表';

-- 3.13 XSS 防护全局配置表（每租户一条）
CREATE TABLE IF NOT EXISTS sys_xss_config (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id   BIGINT       NOT NULL COMMENT '租户ID',
    enabled     TINYINT      NOT NULL DEFAULT 0 COMMENT '全局开关: 0-关闭, 1-开启',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by   VARCHAR(64)  DEFAULT NULL,
    update_by   VARCHAR(64)  DEFAULT NULL,
    UNIQUE KEY uk_xss_config_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='XSS防护全局配置表';

-- 3.14 XSS 黑名单规则表
CREATE TABLE IF NOT EXISTS sys_xss_blacklist_rule (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id   BIGINT       NOT NULL COMMENT '租户ID',
    rule_name   VARCHAR(64)  NOT NULL COMMENT '规则名称',
    rule_type   VARCHAR(32)  NOT NULL COMMENT '规则类型: HTML_TAG, EVENT_HANDLER, DANGEROUS_PROTOCOL, CUSTOM_PATTERN',
    pattern     VARCHAR(255) NOT NULL COMMENT '匹配模式（标签名/正则表达式）',
    enabled     TINYINT      NOT NULL DEFAULT 1 COMMENT '规则开关: 0-禁用, 1-启用',
    description VARCHAR(255) DEFAULT NULL COMMENT '规则说明',
    sort_order  INT          DEFAULT 0 COMMENT '排序',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by   VARCHAR(64)  DEFAULT NULL,
    update_by   VARCHAR(64)  DEFAULT NULL,
    INDEX idx_xss_rule_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='XSS黑名单规则表';

-- ============================================================
-- Section 4: 种子数据
-- ============================================================

-- 4.1 默认租户
INSERT IGNORE INTO sys_tenant (id, tenant_code, tenant_name, domain, contact_name, contact_phone, status, create_by)
VALUES (1, 'default', 'Default Tenant', NULL, 'admin', NULL, 1, 'system');

-- 4.2 根组织单元
INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, path, depth, sort, status, create_by)
VALUES (1, 1, 0, 'Default Tenant', 'ORG', '/1/', 1, 0, 1, 'system');

-- 4.3 管理员用户（密码: admin123，BCrypt 编码）
INSERT IGNORE INTO sys_user (id, tenant_id, username, password, nickname, email, phone, gender, primary_unit_id, status, create_by)
VALUES (1, 1, 'admin', '$2b$10$QjkPz8OnRoNOXTrsj./ov.nDZxK.KvsAZdjzgb1YgWSKKprOVxfIW', 'Administrator', NULL, NULL, 0, 1, 1, 'system');

-- 4.4 超级管理员角色
INSERT IGNORE INTO sys_role (id, tenant_id, role_code, role_name, data_scope, sort, status, create_by)
VALUES (1, 1, 'SUPER_ADMIN', 'Super Administrator', 'ALL', 0, 1, 'system');

-- 4.4.1 默认用户角色（新用户自动关联，无管理权限）
INSERT IGNORE INTO sys_role (id, tenant_id, role_code, role_name, data_scope, sort, status, create_by)
VALUES (2, 1, 'USER', 'Default User', 'SELF', 99, 1, 'system');

-- 4.5 用户角色映射：admin → SUPER_ADMIN
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- 4.6 用户组织映射：admin → 根组织（主组织）
INSERT IGNORE INTO sys_user_unit (user_id, unit_id, is_primary) VALUES (1, 1, 1);

-- 4.7 权限树（1 个目录 + 9 个菜单 + 28 个 API 权限 = 38 条）
INSERT IGNORE INTO sys_permission (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (1,  1, 0, 'system',                '系统管理',   'DIRECTORY', '/1/',       1, 0, 1, 'system'),
    (2,  1, 1, 'system:user',           '用户管理',     'MENU',      '/1/2/',     2, 1, 1, 'system'),
    (3,  1, 1, 'system:role',           '角色管理',     'MENU',      '/1/3/',     2, 2, 1, 'system'),
    (4,  1, 1, 'system:permission',     '权限管理',     'MENU',      '/1/4/',     2, 3, 1, 'system'),
    (5,  1, 1, 'system:org',            '组织管理',   'MENU',      '/1/5/',     2, 4, 1, 'system'),
    (6,  1, 1, 'system:tenant',         '租户管理',   'MENU',      '/1/6/',     2, 5, 1, 'system'),
    (7,  1, 2, 'system:user:list',      '查看用户',          'API',       '/1/2/7/',   3, 1, 1, 'system'),
    (8,  1, 2, 'system:user:create',    '创建用户',         'API',       '/1/2/8/',   3, 2, 1, 'system'),
    (9,  1, 2, 'system:user:update',    '更新用户',         'API',       '/1/2/9/',   3, 3, 1, 'system'),
    (10, 1, 2, 'system:user:delete',    '删除用户',         'API',       '/1/2/10/',  3, 4, 1, 'system'),
    (11, 1, 3, 'system:role:list',      '查看角色',          'API',       '/1/3/11/',  3, 1, 1, 'system'),
    (12, 1, 3, 'system:role:create',    '创建角色',         'API',       '/1/3/12/',  3, 2, 1, 'system'),
    (13, 1, 3, 'system:role:update',    '更新角色',         'API',       '/1/3/13/',  3, 3, 1, 'system'),
    (14, 1, 3, 'system:role:delete',    '删除角色',         'API',       '/1/3/14/',  3, 4, 1, 'system'),
    (15, 1, 4, 'system:permission:list','查看权限',    'API',       '/1/4/15/',  3, 1, 1, 'system'),
    (16, 1, 4, 'system:permission:create','创建权限', 'API',       '/1/4/16/',  3, 2, 1, 'system'),
    (17, 1, 4, 'system:permission:update','更新权限', 'API',       '/1/4/17/',  3, 3, 1, 'system'),
    (18, 1, 4, 'system:permission:delete','删除权限', 'API',       '/1/4/18/',  3, 4, 1, 'system'),
    (19, 1, 5, 'system:org:list',       '查看组织',  'API',       '/1/5/19/',  3, 1, 1, 'system'),
    (20, 1, 5, 'system:org:create',     '创建组织', 'API',       '/1/5/20/',  3, 2, 1, 'system'),
    (21, 1, 5, 'system:org:update',     '更新组织', 'API',       '/1/5/21/',  3, 3, 1, 'system'),
    (22, 1, 5, 'system:org:delete',     '删除组织', 'API',       '/1/5/22/',  3, 4, 1, 'system'),
    (23, 1, 6, 'system:tenant:list',    '查看租户',        'API',       '/1/6/23/',  3, 1, 1, 'system'),
    (24, 1, 6, 'system:tenant:create',  '创建租户',       'API',       '/1/6/24/',  3, 2, 1, 'system'),
    (25, 1, 6, 'system:tenant:update',  '更新租户',       'API',       '/1/6/25/',  3, 3, 1, 'system'),
    (26, 1, 6, 'system:tenant:delete',  '删除租户',       'API',       '/1/6/26/',  3, 4, 1, 'system'),
    (27, 1, 1, 'system:oauth2',         'OAuth2 客户端',      'MENU',      '/1/27/',    2, 6, 1, 'system'),
    (28, 1, 27,'system:oauth2:list',    '查看客户端',        'API',       '/1/27/28/', 3, 1, 1, 'system'),
    (29, 1, 27,'system:oauth2:create',  '创建客户端',       'API',       '/1/27/29/', 3, 2, 1, 'system'),
    (30, 1, 27,'system:oauth2:update',  '更新客户端',       'API',       '/1/27/30/', 3, 3, 1, 'system'),
    (31, 1, 27,'system:oauth2:delete',  '删除客户端',       'API',       '/1/27/31/', 3, 4, 1, 'system'),
    (32, 1, 1, 'system:online',         '在线用户',        'MENU',      '/1/32/',    2, 7, 1, 'system'),
    (33, 1, 32,'system:online:list',    '查看在线用户',   'API',       '/1/32/33/', 3, 1, 1, 'system'),
    (34, 1, 32,'system:online:kick',    '强制下线',    'API',       '/1/32/34/', 3, 2, 1, 'system'),
    (35, 1, 1, 'system:authrecord',     '授权记录',        'MENU',      '/1/35/',    2, 8, 1, 'system'),
    (36, 1, 35,'system:authrecord:list','查看授权记录',   'API',       '/1/35/36/', 3, 1, 1, 'system'),
    (37, 1, 1, 'system:auditlog',       '审计日志',        'MENU',      '/1/37/',    2, 9, 1, 'system'),
    (38, 1, 37,'system:auditlog:list',  '查看审计日志',   'API',       '/1/37/38/', 3, 1, 1, 'system');

-- 4.8 角色权限映射：SUPER_ADMIN 拥有全部权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES
    (1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6),
    (1, 7), (1, 8), (1, 9), (1, 10),
    (1, 11), (1, 12), (1, 13), (1, 14),
    (1, 15), (1, 16), (1, 17), (1, 18),
    (1, 19), (1, 20), (1, 21), (1, 22),
    (1, 23), (1, 24), (1, 25), (1, 26),
    (1, 27), (1, 28), (1, 29), (1, 30),
    (1, 31), (1, 32), (1, 33), (1, 34),
    (1, 35), (1, 36), (1, 37), (1, 38),
    (1, 39), (1, 40), (1, 41), (1, 42), (1, 43);

-- 4.9 XSS 防护权限节点（1 个菜单 + 4 个 API 权限 = 5 条）
INSERT IGNORE INTO sys_permission (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (39, 1, 1,  'system:xssconfig',         'XSS防护配置',   'MENU', '/1/39/',    2, 10, 1, 'system'),
    (40, 1, 39, 'system:xssconfig:list',    '查看XSS配置',   'API',  '/1/39/40/', 3, 1,  1, 'system'),
    (41, 1, 39, 'system:xssconfig:update',  '更新XSS配置',   'API',  '/1/39/41/', 3, 2,  1, 'system'),
    (42, 1, 39, 'system:xssconfig:create',  '创建XSS规则',   'API',  '/1/39/42/', 3, 3,  1, 'system'),
    (43, 1, 39, 'system:xssconfig:delete',  '删除XSS规则',   'API',  '/1/39/43/', 3, 4,  1, 'system');

-- 4.10 XSS 防护默认配置（默认租户：关闭状态）
INSERT IGNORE INTO sys_xss_config (tenant_id, enabled, create_by) VALUES (1, 0, 'system');

-- 4.11 XSS 黑名单预置规则
INSERT IGNORE INTO sys_xss_blacklist_rule (tenant_id, rule_name, rule_type, pattern, enabled, description, sort_order, create_by) VALUES
    (1, 'Script标签',     'HTML_TAG',           'script',            1, '拦截<script>标签及其内容',   1,  'system'),
    (1, 'IFrame标签',     'HTML_TAG',           'iframe',            1, '拦截<iframe>标签及其内容',   2,  'system'),
    (1, 'Object标签',     'HTML_TAG',           'object',            1, '拦截<object>标签及其内容',   3,  'system'),
    (1, 'Embed标签',      'HTML_TAG',           'embed',             1, '拦截<embed>标签及其内容',    4,  'system'),
    (1, 'Form标签',       'HTML_TAG',           'form',              1, '拦截<form>标签及其内容',     5,  'system'),
    (1, '事件处理器',      'EVENT_HANDLER',      'on\\w+',           1, '拦截on*事件属性(onclick等)', 6,  'system'),
    (1, 'JavaScript协议', 'DANGEROUS_PROTOCOL', 'javascript:',       1, '拦截javascript:伪协议',     7,  'system'),
    (1, 'VBScript协议',   'DANGEROUS_PROTOCOL', 'vbscript:',         1, '拦截vbscript:伪协议',        8,  'system'),
    (1, 'Data协议',       'DANGEROUS_PROTOCOL', 'data:',             1, '拦截data:URI',              9,  'system'),
    (1, 'Expression',     'CUSTOM_PATTERN',     'expression\\s*\\(', 1, '拦截CSS expression表达式',   10, 'system');

-- 4.12 Base 服务权限节点（1 个目录 + 1 个菜单 + 9 个 API 权限 = 11 条）
INSERT IGNORE INTO sys_permission (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (50, 1, 0,  'base',                 '基础数据',       'DIRECTORY', '/50/',       1, 1, 1, 'system'),
    (51, 1, 50, 'base:dict',            '字典管理',       'MENU',      '/50/51/',    2, 1, 1, 'system'),
    (52, 1, 51, 'dict:type:list',       '查看字典类型',   'API',       '/50/51/52/', 3, 1, 1, 'system'),
    (53, 1, 51, 'dict:type:create',     '创建字典类型',   'API',       '/50/51/53/', 3, 2, 1, 'system'),
    (54, 1, 51, 'dict:type:update',     '更新字典类型',   'API',       '/50/51/54/', 3, 3, 1, 'system'),
    (55, 1, 51, 'dict:type:delete',     '删除字典类型',   'API',       '/50/51/55/', 3, 4, 1, 'system'),
    (56, 1, 51, 'dict:data:list',       '查看字典数据',   'API',       '/50/51/56/', 3, 5, 1, 'system'),
    (57, 1, 51, 'dict:data:create',     '创建字典数据',   'API',       '/50/51/57/', 3, 6, 1, 'system'),
    (58, 1, 51, 'dict:data:update',     '更新字典数据',   'API',       '/50/51/58/', 3, 7, 1, 'system'),
    (59, 1, 51, 'dict:data:delete',     '删除字典数据',   'API',       '/50/51/59/', 3, 8, 1, 'system'),
    (60, 1, 51, 'dict:data:refresh',    '刷新字典缓存',   'API',       '/50/51/60/', 3, 9, 1, 'system');

-- 4.13 运维监控权限节点（1 个目录 + 2 个菜单 + 4 个 API 权限 = 7 条）
INSERT IGNORE INTO sys_permission (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (63, 1, 0,  'monitor',                  '运维监控',     'DIRECTORY', '/63/',          1, 5, 1, 'system'),
    (61, 1, 63, 'base:operlog',             '操作日志',     'MENU',      '/63/61/',       2, 1, 1, 'system'),
    (62, 1, 61, 'base:operlog:list',        '查看操作日志', 'API',       '/63/61/62/',    3, 1, 1, 'system'),
    (64, 1, 63, 'base:mqmessage',           '消息记录',     'MENU',      '/63/64/',       2, 2, 1, 'system'),
    (65, 1, 64, 'base:mqmessage:list',      '查看消息记录', 'API',       '/63/64/65/',    3, 1, 1, 'system'),
    (66, 1, 64, 'base:mqmessage:resend',    '重发消息',     'API',       '/63/64/66/',    3, 2, 1, 'system'),
    (67, 1, 64, 'base:mqmessage:skip',      '忽略消息',     'API',       '/63/64/67/',    3, 3, 1, 'system');

-- 4.14 SUPER_ADMIN 角色追加 Base 服务权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES
    (1, 50), (1, 51), (1, 52), (1, 53), (1, 54), (1, 55),
    (1, 56), (1, 57), (1, 58), (1, 59), (1, 60), (1, 61), (1, 62),
    (1, 63), (1, 64), (1, 65), (1, 66), (1, 67);

-- ============================================================
-- Section 5: Base 服务 - 数据字典
-- ============================================================
CREATE DATABASE IF NOT EXISTS omni_base
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE omni_base;

-- 5.1 字典类型表
CREATE TABLE IF NOT EXISTS sys_dict_type (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL COMMENT '租户ID',
    type_code   VARCHAR(100) NOT NULL COMMENT '字典类型编码',
    type_name   VARCHAR(200) NOT NULL COMMENT '字典类型名称',
    remark      VARCHAR(500) DEFAULT NULL COMMENT '备注',
    sort        INT          DEFAULT 0 COMMENT '排序',
    status      TINYINT      DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by   VARCHAR(64)  DEFAULT NULL,
    update_by   VARCHAR(64)  DEFAULT NULL,
    UNIQUE KEY uk_dict_type_tenant_code (tenant_id, type_code),
    INDEX idx_dict_type_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典类型表';

-- 5.2 字典数据表
CREATE TABLE IF NOT EXISTS sys_dict_data (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL COMMENT '租户ID',
    type_code   VARCHAR(100) NOT NULL COMMENT '字典类型编码',
    dict_value  VARCHAR(200) NOT NULL COMMENT '字典值',
    dict_label  VARCHAR(200) NOT NULL COMMENT '字典标签',
    tag_type    VARCHAR(50)  DEFAULT NULL COMMENT '标签样式：success/warning/danger/info/primary',
    remark      VARCHAR(500) DEFAULT NULL COMMENT '备注',
    sort        INT          DEFAULT 0 COMMENT '排序',
    status      TINYINT      DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by   VARCHAR(64)  DEFAULT NULL,
    update_by   VARCHAR(64)  DEFAULT NULL,
    INDEX idx_dict_data_tenant (tenant_id),
    INDEX idx_dict_data_tenant_type (tenant_id, type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典数据表';

-- 5.3 操作日志表（热表，保留最近 180 天）
CREATE TABLE IF NOT EXISTS sys_oper_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    event_id        VARCHAR(64)  NOT NULL COMMENT 'MQ事件唯一标识',
    tenant_id       BIGINT       NOT NULL COMMENT '租户ID',
    oper_username    VARCHAR(64)  DEFAULT NULL COMMENT '操作人用户名',
    oper_time       DATETIME     NOT NULL COMMENT '操作时间',
    module          VARCHAR(100) DEFAULT NULL COMMENT '业务模块名称',
    oper_type       VARCHAR(20)  NOT NULL COMMENT '操作类型: CREATE/UPDATE/DELETE/QUERY/EXPORT/IMPORT',
    request_method  VARCHAR(10)  DEFAULT NULL COMMENT 'HTTP方法',
    request_url     VARCHAR(500) DEFAULT NULL COMMENT '请求URL',
    request_params  TEXT         DEFAULT NULL COMMENT '请求参数JSON',
    response_status INT          DEFAULT 200 COMMENT '响应状态码',
    ip_address      VARCHAR(64)  DEFAULT NULL COMMENT '客户端IP',
    user_agent      VARCHAR(500) DEFAULT NULL COMMENT 'User-Agent',
    execution_time  BIGINT       DEFAULT NULL COMMENT '执行耗时（毫秒）',
    old_value       JSON         DEFAULT NULL COMMENT '变更前值快照',
    new_value       JSON         DEFAULT NULL COMMENT '变更后值快照',
    error_msg       VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    UNIQUE KEY uk_operlog_event (event_id),
    INDEX idx_operlog_tenant (tenant_id),
    INDEX idx_operlog_time (oper_time),
    INDEX idx_operlog_module (module),
    INDEX idx_operlog_type (oper_type),
    INDEX idx_operlog_username (oper_username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志热表';

-- 5.4 操作日志归档表（冷表）
CREATE TABLE IF NOT EXISTS sys_oper_log_archive (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    event_id        VARCHAR(64)  NOT NULL COMMENT '原始MQ事件唯一标识',
    tenant_id       BIGINT       NOT NULL COMMENT '租户ID',
    oper_username    VARCHAR(64)  DEFAULT NULL COMMENT '操作人用户名',
    oper_time       DATETIME     NOT NULL COMMENT '操作时间',
    module          VARCHAR(100) DEFAULT NULL COMMENT '业务模块名称',
    oper_type       VARCHAR(20)  NOT NULL COMMENT '操作类型',
    request_method  VARCHAR(10)  DEFAULT NULL COMMENT 'HTTP方法',
    request_url     VARCHAR(500) DEFAULT NULL COMMENT '请求URL',
    request_params  TEXT         DEFAULT NULL COMMENT '请求参数JSON',
    response_status INT          DEFAULT 200 COMMENT '响应状态码',
    ip_address      VARCHAR(64)  DEFAULT NULL COMMENT '客户端IP',
    user_agent      VARCHAR(500) DEFAULT NULL COMMENT 'User-Agent',
    execution_time  BIGINT       DEFAULT NULL COMMENT '执行耗时（毫秒）',
    old_value       JSON         DEFAULT NULL COMMENT '变更前值快照',
    new_value       JSON         DEFAULT NULL COMMENT '变更后值快照',
    error_msg       VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
    create_time     DATETIME     DEFAULT NULL COMMENT '原始记录创建时间',
    archived_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '归档时间',
    UNIQUE KEY uk_archive_event (event_id),
    INDEX idx_archive_tenant (tenant_id),
    INDEX idx_archive_time (oper_time),
    INDEX idx_archive_module (module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志归档冷表';

-- 5.5 预置字典类型（默认租户 1）
INSERT IGNORE INTO sys_dict_type (tenant_id, type_code, type_name, sort, status, create_by) VALUES
    (1, 'sys_user_gender',   '用户性别',   1, 1, 'system'),
    (1, 'sys_common_status', '通用状态',   2, 1, 'system'),
    (1, 'sys_notice_type',   '通知类型',   3, 1, 'system'),
    (1, 'srm_supplier_category', '供应商品类', 20, 1, 'system');

-- 5.6 预置字典数据
INSERT IGNORE INTO sys_dict_data (tenant_id, type_code, dict_value, dict_label, tag_type, sort, status, create_by) VALUES
    (1, 'sys_user_gender', '1', '男',     'primary', 1, 1, 'system'),
    (1, 'sys_user_gender', '2', '女',     'danger',  2, 1, 'system'),
    (1, 'sys_user_gender', '0', '未知',   'info',    3, 1, 'system'),
    (1, 'sys_common_status', '1', '启用', 'success', 1, 1, 'system'),
    (1, 'sys_common_status', '0', '禁用', 'danger',  2, 1, 'system'),
    (1, 'sys_notice_type', '1', '系统通知', 'primary', 1, 1, 'system'),
    (1, 'sys_notice_type', '2', '业务通知', 'warning', 2, 1, 'system');

INSERT INTO sys_dict_data
    (tenant_id, type_code, dict_value, dict_label, tag_type, sort, status, create_by)
SELECT 1, 'srm_supplier_category', category.dict_value, category.dict_label,
       category.tag_type, category.sort, 1, 'system'
FROM (
    SELECT 'ELECTRONICS' dict_value, '电子元器件' dict_label, 'primary' tag_type, 1 sort
    UNION ALL SELECT 'IT', '信息技术', 'success', 2
    UNION ALL SELECT 'RAW_MATERIAL', '原材料', 'warning', 3
    UNION ALL SELECT 'ADMIN', '行政物资', 'info', 4
    UNION ALL SELECT 'SERVICE', '服务', 'primary', 5
) category
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data dict_data
    WHERE dict_data.tenant_id = 1
      AND dict_data.type_code = 'srm_supplier_category'
      AND dict_data.dict_value = category.dict_value
);

-- ============================================================
-- Section 6: Base 服务 - 用户自定义任务
-- ============================================================

-- 6.1 任务类型注册表
CREATE TABLE IF NOT EXISTS sys_user_job_type (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    type_code      VARCHAR(50)  NOT NULL COMMENT '任务类型编码（唯一，对应 UserJobHandler Bean 名称）',
    type_name      VARCHAR(100) NOT NULL COMMENT '任务类型名称（中文显示名）',
    description    VARCHAR(500) DEFAULT NULL COMMENT '任务类型描述',
    param_template JSON         DEFAULT NULL COMMENT '参数模板（JSON Schema，前端据此渲染表单）',
    status         TINYINT      DEFAULT 1 COMMENT '状态：0-禁用, 1-启用',
    create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_job_type_code (type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务类型注册表';

-- 6.2 用户自定义任务表
CREATE TABLE IF NOT EXISTS sys_user_job (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '任务ID',
    tenant_id       BIGINT       NOT NULL COMMENT '租户ID',
    job_name        VARCHAR(100) NOT NULL COMMENT '任务名称',
    job_type        VARCHAR(50)  NOT NULL COMMENT '任务类型编码（关联 sys_user_job_type.type_code）',
    cron_expression VARCHAR(100) NOT NULL COMMENT 'Cron 表达式',
    job_params      JSON         DEFAULT NULL COMMENT '任务参数（JSON 格式，由任务类型定义）',
    status          TINYINT      DEFAULT 1 COMMENT '状态：0-禁用, 1-启用',
    xxl_job_id      BIGINT       DEFAULT NULL COMMENT 'XXL-JOB 调度中心任务 ID',
    last_fire_time  DATETIME     DEFAULT NULL COMMENT '上次触发时间',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by       VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    update_by       VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
    INDEX idx_user_job_tenant (tenant_id),
    INDEX idx_user_job_xxl (xxl_job_id),
    INDEX idx_user_job_type (job_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户自定义任务表';

-- 6.3 用户任务执行日志表
CREATE TABLE IF NOT EXISTS sys_user_job_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id          BIGINT       NOT NULL COMMENT '关联任务ID',
    tenant_id       BIGINT       DEFAULT NULL COMMENT '租户ID',
    job_name        VARCHAR(100) DEFAULT NULL COMMENT '任务名称',
    job_type        VARCHAR(50)  DEFAULT NULL COMMENT '任务类型编码',
    fire_time       DATETIME     DEFAULT NULL COMMENT '触发时间',
    execute_time_ms BIGINT       DEFAULT NULL COMMENT '执行耗时（毫秒）',
    status          TINYINT      DEFAULT NULL COMMENT '0=失败, 1=成功',
    error_message   TEXT         DEFAULT NULL COMMENT '错误信息',
    result_message  VARCHAR(500) DEFAULT NULL COMMENT '执行结果消息（前端通知弹窗用）',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_job_log_job (job_id),
    INDEX idx_job_log_type (job_type),
    INDEX idx_job_log_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户任务执行日志表';

-- 6.4 MQ 消息发送记录表（各服务共用，omni-common-mqlog starter 自动建表）
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

-- 6.5 喝水提醒任务类型 + 示例任务（种子数据）
SET NAMES utf8mb4;
INSERT IGNORE INTO sys_user_job_type (type_code, type_name, description, param_template, status) VALUES
('Task-00001', '喝水提醒', '定时提醒喝水，根据杯型参数生成个性化提醒消息',
 '{"type":"object","properties":{"cupShape":{"type":"string","title":"杯型","enum":["大杯","中杯","小杯","玻璃杯"],"default":"中杯"}},"required":["cupShape"]}',
 1);

INSERT IGNORE INTO sys_user_job (tenant_id, job_name, job_type, cron_expression, job_params, status, create_by) VALUES
(1, '我要喝水', 'Task-00001', '0 * * * * ?', '{"cupShape":"玻璃杯"}', 1, 'admin');

-- ============================================================
-- Section 7: Phase 2 权限节点 - 任务调度（独立一级菜单）
-- 注意：权限表在 omni_auth 库，需切换上下文
-- ============================================================
USE omni_auth;

-- 7.0 任务调度一级目录
INSERT IGNORE INTO sys_permission (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (110, 1, 0, 'job', '任务调度', 'DIRECTORY', '/110/', 1, 2, 1, 'system');

-- 7.1 任务类型管理权限（1 MENU + 4 API = 5 条）
INSERT IGNORE INTO sys_permission (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (70, 1, 110, 'job:user-job-type',        '任务类型管理',     'MENU', '/110/70/',      2, 1, 1, 'system'),
    (71, 1, 70,  'job:user-job-type:list',    '查看任务类型',     'API',  '/110/70/71/',   3, 1, 1, 'system'),
    (72, 1, 70,  'job:user-job-type:create',  '创建任务类型',     'API',  '/110/70/72/',   3, 2, 1, 'system'),
    (73, 1, 70,  'job:user-job-type:update',  '更新任务类型',     'API',  '/110/70/73/',   3, 3, 1, 'system'),
    (74, 1, 70,  'job:user-job-type:delete',  '删除任务类型',     'API',  '/110/70/74/',   3, 4, 1, 'system');

-- 7.4 SUPER_ADMIN 角色追加 Phase 2 权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES
    (1, 110),
    (1, 70), (1, 71), (1, 72), (1, 73), (1, 74);

-- 7.5 系统任务管理权限（1 MENU + 2 API = 3 条）
INSERT IGNORE INTO sys_permission (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (100, 1, 110, 'job:system-job',           '系统任务',         'MENU', '/110/100/',      2, 4, 1, 'system'),
    (101, 1, 100, 'job:system-job:list',     '查看系统任务',     'API',  '/110/100/101/',   3, 1, 1, 'system'),
    (102, 1, 100, 'job:system-job:manage',   '管理系统任务',     'API',  '/110/100/102/',   3, 2, 1, 'system');

-- 7.6 SUPER_ADMIN 角色追加系统任务权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES
    (1, 100), (1, 101), (1, 102);

-- ============================================================
-- Section 8: omni_workflow 工作流引擎
-- ============================================================

-- 8.1 创建工作流数据库
CREATE DATABASE IF NOT EXISTS omni_workflow
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE omni_workflow;

-- 8.2 流程实例扩展表
CREATE TABLE IF NOT EXISTS wf_process_instance_ext (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL COMMENT '租户 ID',
    process_instance_id VARCHAR(64)  NOT NULL COMMENT 'Flowable 流程实例 ID',
    process_key         VARCHAR(255) NOT NULL COMMENT '流程定义 Key',
    model_id              BIGINT       DEFAULT NULL COMMENT '流程模型 ID',
    model_version_id      BIGINT       DEFAULT NULL COMMENT '流程模型版本 ID',
    process_definition_id VARCHAR(255) DEFAULT NULL COMMENT 'Flowable 流程定义 ID',
    deployment_id         VARCHAR(64)  DEFAULT NULL COMMENT 'Flowable 部署 ID',
    business_version      INT          DEFAULT NULL COMMENT '业务版本号',
    engine_version        INT          DEFAULT NULL COMMENT 'Flowable 引擎版本号',
    request_id          VARCHAR(64)  DEFAULT NULL COMMENT '跨服务调用请求 ID',
    business_type       VARCHAR(100) DEFAULT NULL COMMENT '跨服务业务类型',
    business_key        VARCHAR(255) DEFAULT NULL COMMENT '业务主键',
    title               VARCHAR(500) DEFAULT NULL COMMENT '流程标题',
    start_user_id       BIGINT       NOT NULL COMMENT '发起人用户 ID',
    start_user_name     VARCHAR(100) DEFAULT NULL COMMENT '发起人用户名',
    category            VARCHAR(100) DEFAULT NULL COMMENT '流程分类',
    status              TINYINT      DEFAULT 1 COMMENT '状态: 0-已终止, 1-进行中, 2-已完成',
    completion_result   VARCHAR(20)  DEFAULT NULL COMMENT '完成结果: APPROVED/REJECTED/CANCELLED',
    completed_time      DATETIME     DEFAULT NULL COMMENT '业务完成时间',
    completion_event_id VARCHAR(36)  DEFAULT NULL COMMENT '完成事件 ID；同实例仅发布一次',
    create_time         DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_process_instance_id (process_instance_id),
    INDEX idx_start_user (tenant_id, start_user_id),
    INDEX idx_status (tenant_id, status),
    INDEX idx_wf_ext_request (tenant_id, request_id),
    INDEX idx_wf_ext_business (tenant_id, business_type, business_key),
    UNIQUE KEY uk_wf_completion_event (completion_event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='流程实例扩展表';

-- 8.2b 跨服务流程启动幂等请求表
CREATE TABLE IF NOT EXISTS wf_process_start_request (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL COMMENT '租户 ID',
    request_id          VARCHAR(64)  NOT NULL COMMENT '调用方请求 ID',
    business_type       VARCHAR(100) NOT NULL COMMENT '业务类型',
    business_key        VARCHAR(255) NOT NULL COMMENT '业务主键',
    model_version_id    BIGINT       NOT NULL COMMENT '流程模型版本 ID',
    start_user_id       BIGINT       NOT NULL COMMENT '发起人用户 ID',
    status              VARCHAR(20)  NOT NULL DEFAULT 'RESERVED' COMMENT '状态: RESERVED/STARTED/FAILED',
    process_instance_id VARCHAR(64)  DEFAULT NULL COMMENT 'Flowable 流程实例 ID',
    retry_count         INT          NOT NULL DEFAULT 0 COMMENT '重试次数',
    last_error          VARCHAR(1000) DEFAULT NULL COMMENT '最近一次失败原因',
    started_time        DATETIME     DEFAULT NULL COMMENT '启动成功时间',
    failed_time         DATETIME     DEFAULT NULL COMMENT '最近一次失败时间',
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wf_start_request (tenant_id, request_id),
    UNIQUE KEY uk_wf_start_business (tenant_id, business_type, business_key),
    UNIQUE KEY uk_wf_start_process_instance (tenant_id, process_instance_id),
    INDEX idx_wf_start_status (tenant_id, status, update_time),
    CONSTRAINT chk_wf_start_status CHECK (status IN ('RESERVED', 'STARTED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='跨服务流程启动幂等请求表';

-- 8.3 待办任务缓存表
CREATE TABLE IF NOT EXISTS wf_todo_task (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL COMMENT '租户 ID',
    task_id             VARCHAR(64)  NOT NULL COMMENT 'Flowable 任务 ID',
    process_instance_id VARCHAR(64)  NOT NULL COMMENT '流程实例 ID',
    process_key         VARCHAR(255) DEFAULT NULL COMMENT '流程定义 Key',
    task_name           VARCHAR(255) DEFAULT NULL COMMENT '任务名称',
    assignee_id         BIGINT       NOT NULL COMMENT '处理人用户 ID',
    assignee_name       VARCHAR(100) DEFAULT NULL COMMENT '处理人用户名',
    title               VARCHAR(500) DEFAULT NULL COMMENT '流程标题',
    category            VARCHAR(100) DEFAULT NULL COMMENT '流程分类',
    create_time         DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant_id (tenant_id),
    UNIQUE KEY uk_task_id (task_id),
    INDEX idx_assignee (tenant_id, assignee_id),
    INDEX idx_process_instance (process_instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='待办任务缓存表';

-- 8.4 JSON Schema 表单定义表
CREATE TABLE IF NOT EXISTS wf_form_schema (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL COMMENT '租户 ID',
    form_key        VARCHAR(100) NOT NULL COMMENT '表单标识',
    form_name       VARCHAR(200) NOT NULL COMMENT '表单名称',
    schema_json     TEXT         NOT NULL COMMENT 'JSON Schema 内容',
    version         INT          DEFAULT 1 COMMENT '版本号',
    status          TINYINT      DEFAULT 1 COMMENT '状态: 1=启用 0=禁用',
    create_by       VARCHAR(100) DEFAULT NULL COMMENT '创建人',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant_form (tenant_id, form_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='JSON Schema 表单定义表';

-- 8.5 审批委托规则表
CREATE TABLE IF NOT EXISTS wf_delegation_rule (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL COMMENT '租户 ID',
    from_user_id    BIGINT       NOT NULL COMMENT '委托人用户 ID',
    to_user_id      BIGINT       NOT NULL COMMENT '被委托人用户 ID',
    process_key     VARCHAR(255) DEFAULT NULL COMMENT '限定流程 Key（空表示全部）',
    start_time      DATETIME     DEFAULT NULL COMMENT '生效开始时间',
    end_time        DATETIME     DEFAULT NULL COMMENT '生效结束时间',
    status          TINYINT      DEFAULT 1 COMMENT '状态: 1=启用 0=禁用',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant_from (tenant_id, from_user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='审批委托规则表';

-- 8.5b MQ 消息发送记录表（omni_workflow 库副本，omni-common-mqlog starter 自动建表）
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

-- 8.6 工作流权限种子数据（写入 omni_auth.sys_permission）
USE omni_auth;

-- 工作流管理目录
INSERT IGNORE INTO sys_permission (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (200, 1, 0,   'workflow',                        '工作流管理',     'DIRECTORY', '/200/',          1, 6, 1, 'system'),
    (201, 1, 200, 'workflow:definition',              '流程定义',       'MENU',      '/200/201/',      2, 2, 1, 'system'),
    (202, 1, 201, 'workflow:definition:list',         '查看流程定义',   'API',       '/200/201/202/',  3, 1, 1, 'system'),
    (203, 1, 201, 'workflow:definition:deploy',       '部署流程',       'API',       '/200/201/203/',  3, 2, 1, 'system'),
    (204, 1, 201, 'workflow:definition:update',       '挂起/激活流程',  'API',       '/200/201/204/',  3, 3, 1, 'system'),
    (205, 1, 201, 'workflow:definition:delete',       '删除流程',       'API',       '/200/201/205/',  3, 4, 1, 'system'),
    (210, 1, 200, 'workflow:instance',                '流程实例',       'MENU',      '/200/210/',      2, 3, 1, 'system'),
    (211, 1, 210, 'workflow:instance:list',           '查看流程实例',   'API',       '/200/210/211/',  3, 1, 1, 'system'),
    (212, 1, 210, 'workflow:instance:start',          '发起流程实例',   'API',       '/200/210/212/',  3, 2, 1, 'system'),
    (213, 1, 210, 'workflow:instance:terminate',      '终止流程实例',   'API',       '/200/210/213/',  3, 3, 1, 'system'),
    (214, 1, 210, 'workflow:task:todo',               '查看待办任务',   'API',       '/200/210/214/',  3, 4, 1, 'system'),
    (215, 1, 210, 'workflow:approval:complete',       '完成审批任务',   'API',       '/200/210/215/',  3, 5, 1, 'system'),
    (216, 1, 210, 'workflow:approval:add-signer',     '审批加签',       'API',       '/200/210/216/',  3, 6, 1, 'system'),
    (217, 1, 210, 'workflow:approval:remove-signer',  '审批减签',       'API',       '/200/210/217/',  3, 7, 1, 'system'),
    (218, 1, 210, 'workflow:approval:delegate',       '审批委托',       'API',       '/200/210/218/',  3, 8, 1, 'system'),
    (220, 1, 200, 'workflow:stats',                   '统计看板',       'MENU',      '/200/220/',      2, 4, 1, 'system'),
    (221, 1, 220, 'workflow:stats:admin',             '管理端统计',     'API',       '/200/220/221/',  3, 1, 1, 'system'),
    (222, 1, 200, 'workflow:identity',                '身份管理',       'API',       '/200/222/',      2, 5, 1, 'system'),
    (223, 1, 222, 'workflow:identity:list',           '查询身份数据',   'API',       '/200/222/223/',  3, 1, 1, 'system'),
    (224, 1, 200, 'workflow:model',                   '流程模型',       'MENU',      '/200/224/',      2, 1, 1, 'system'),
    (225, 1, 224, 'workflow:model:list',              '查询模型列表',   'API',       '/200/224/225/',  3, 1, 1, 'system'),
    (226, 1, 224, 'workflow:model:create',            '创建模型',       'API',       '/200/224/226/',  3, 2, 1, 'system'),
    (227, 1, 224, 'workflow:model:update',            '更新模型',       'API',       '/200/224/227/',  3, 3, 1, 'system'),
    (228, 1, 224, 'workflow:model:validate',          '校验模型',       'API',       '/200/224/228/',  3, 4, 1, 'system'),
    (229, 1, 224, 'workflow:model:publish',           '发布模型',       'API',       '/200/224/229/',  3, 5, 1, 'system'),
    (230, 1, 224, 'workflow:model:delete',            '删除模型',       'API',       '/200/224/230/',  3, 6, 1, 'system');

-- SUPER_ADMIN 角色追加工作流权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES
    (1, 200), (1, 201), (1, 202), (1, 203), (1, 204), (1, 205),
    (1, 210), (1, 211), (1, 212), (1, 213), (1, 214), (1, 215),
    (1, 216), (1, 217), (1, 218), (1, 220), (1, 221),
    (1, 222), (1, 223), (1, 224), (1, 225), (1, 226), (1, 227),
    (1, 228), (1, 229), (1, 230);

-- USER 角色追加只读菜单权限（系统各模块浏览 + 工作流查看，无写操作权限）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES
    (2, 2), (2, 3), (2, 4), (2, 5), (2, 6), (2, 7),
    (2, 11), (2, 15), (2, 19), (2, 23),
    (2, 27), (2, 28),
    (2, 51), (2, 52), (2, 56),
    (2, 61), (2, 62),
    (2, 70), (2, 71),
    (2, 201), (2, 202), (2, 210), (2, 211), (2, 214);

-- EMPLOYEE / TEAM_LEADER / DEPT_LEADER 追加用户侧工作流权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES
    (10, 211), (10, 212), (10, 213), (10, 214), (10, 215), (10, 216), (10, 217), (10, 218),
    (11, 211), (11, 212), (11, 213), (11, 214), (11, 215), (11, 216), (11, 217), (11, 218),
    (12, 211), (12, 212), (12, 213), (12, 214), (12, 215), (12, 216), (12, 217), (12, 218);

-- 8.7 流程分类字典数据（写入 omni_base.sys_dict_type + sys_dict_data）
USE omni_base;

INSERT IGNORE INTO sys_dict_type (id, tenant_id, type_code, type_name, remark, sort, status, create_by)
VALUES
    (10, 1, 'workflow_category', '流程分类', '工作流审批流程的分类标签', 10, 1, 'system');

INSERT IGNORE INTO sys_dict_data (tenant_id, type_code, dict_value, dict_label, sort, status, create_by)
VALUES
    (1, 'workflow_category', 'leave',          '请假审批', 1, 1, 'system'),
    (1, 'workflow_category', 'expense',        '报销审批', 2, 1, 'system'),
    (1, 'workflow_category', 'purchase',       '采购审批', 3, 1, 'system'),
    (1, 'workflow_category', 'contract',       '合同审批', 4, 1, 'system'),
    (1, 'workflow_category', 'general',        '通用审批', 5, 1, 'system'),
    (1, 'workflow_category', 'ASSET_TRANSFER',          '资产调拨', 6, 1, 'system'),
    (1, 'workflow_category', 'ASSET_DISPOSAL',          '资产处置', 7, 1, 'system'),
    (1, 'workflow_category', 'SRM_SUPPLIER_ONBOARDING', '供应商审批', 8, 1, 'system');

-- ============================================================
-- Section 9: 工作流可视化设计器升级
-- ============================================================

-- 9.1 流程模型主表
USE omni_workflow;

CREATE TABLE IF NOT EXISTS wf_process_model (
    id                           BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id                    BIGINT       NOT NULL COMMENT '租户 ID',
    model_key                    VARCHAR(100) NOT NULL COMMENT '模型标识（BPMN process id，同租户唯一）',
    model_name                   VARCHAR(200) NOT NULL COMMENT '模型名称',
    category                     VARCHAR(100) DEFAULT NULL COMMENT '流程分类',
    status                       TINYINT      DEFAULT 1 COMMENT '状态: 0-已归档, 1-正常',
    current_draft_version_id     BIGINT       DEFAULT NULL COMMENT '当前草稿版本 ID',
    current_published_version_id BIGINT       DEFAULT NULL COMMENT '当前已发布版本 ID',
    create_by                    VARCHAR(64)  DEFAULT NULL,
    update_by                    VARCHAR(64)  DEFAULT NULL,
    create_time                  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time                  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_model_tenant_key (tenant_id, model_key),
    INDEX idx_model_tenant (tenant_id),
    INDEX idx_model_category (tenant_id, category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='流程模型主表';

-- 9.2 流程模型版本表
CREATE TABLE IF NOT EXISTS wf_process_model_version (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id             BIGINT       NOT NULL COMMENT '租户 ID',
    model_id              BIGINT       NOT NULL COMMENT '关联 wf_process_model.id',
    version               INT          NOT NULL COMMENT '业务版本号（1, 2, 3...）',
    status                VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '版本状态: DRAFT/PUBLISHED/FAILED/ARCHIVED',
    bpmn_xml              LONGTEXT     DEFAULT NULL COMMENT 'BPMN XML 内容',
    designer_json         LONGTEXT     DEFAULT NULL COMMENT '可视化设计器 JSON',
    xml_sha256            VARCHAR(64)  DEFAULT NULL COMMENT 'BPMN XML 的 SHA-256 摘要',
    deployment_id         VARCHAR(64)  DEFAULT NULL COMMENT 'Flowable 部署 ID',
    process_definition_id VARCHAR(255) DEFAULT NULL COMMENT 'Flowable 流程定义 ID',
    engine_process_key    VARCHAR(255) DEFAULT NULL COMMENT 'Flowable 引擎 process key',
    engine_version        INT          DEFAULT NULL COMMENT 'Flowable 引擎版本号',
    publish_time          DATETIME     DEFAULT NULL COMMENT '发布时间',
    publish_by            VARCHAR(64)  DEFAULT NULL COMMENT '发布人',
    create_time           DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time           DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_model_version (model_id, version),
    INDEX idx_version_tenant (tenant_id),
    INDEX idx_version_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='流程模型版本表';

-- 9.3 抄送记录表
CREATE TABLE IF NOT EXISTS wf_cc_record (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL COMMENT '租户 ID',
    process_instance_id VARCHAR(64)  NOT NULL COMMENT '流程实例 ID',
    source_activity_id  VARCHAR(255) DEFAULT NULL COMMENT '来源活动节点 ID',
    user_id             BIGINT       NOT NULL COMMENT '被抄送人用户 ID',
    title               VARCHAR(500) DEFAULT NULL COMMENT '流程标题',
    read_status         TINYINT      DEFAULT 0 COMMENT '已读状态: 0-未读, 1-已读',
    create_time         DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_cc_tenant (tenant_id),
    INDEX idx_cc_user (tenant_id, user_id, read_status),
    INDEX idx_cc_instance (process_instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='抄送记录表';

-- 9.4 预置请假审批流程模型（DRAFT 状态，用户需手动发布）
INSERT INTO wf_process_model (id, tenant_id, model_key, model_name, category, status, current_draft_version_id, create_by)
VALUES (1, 1, 'leave', '请假审批（3级会签）', 'leave', 1, 1, 'system');

INSERT INTO wf_process_model_version (id, tenant_id, model_id, version, status, bpmn_xml, designer_json)
VALUES (1, 1, 1, 1, 'DRAFT',
'<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xmlns:flowable="http://flowable.org/bpmn"
  xmlns:omni="http://omni.com/workflow"
  id="Definitions_leave"
  targetNamespace="http://flowable.org/test"
  xsi:schemaLocation="http://www.omg.org/spec/BPMN20 http://www.omg.org/spec/BPMN20/bpmn20.xsd">

  <process id="leave" name="请假审批（3级会签）" isExecutable="true">
    <documentation>3级会签审批请假流程：直属领导 → 部门领导 → 跨部门领导</documentation>
    <startEvent id="start" name="提交请假申请" flowable:initiator="initiator" />
    <userTask id="direct-leader-approve" name="直属领导审批" flowable:assignee="${userId}">
      <documentation>发起人所在组织的组长（正副职）会签审批</documentation>
      <extensionElements>
        <flowable:executionListener event="start" delegateExpression="${scopedRoleAssignmentListener}" />
        <omni:assignment>{"roleCode":"TEAM_LEADER","anchorType":"START_USER_PRIMARY_UNIT","anchorParams":{},"scopeMode":"SAME_UNIT","fallbackStrategy":"ERROR","approvalMode":"ALL"}</omni:assignment>
      </extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false"
        flowable:collection="candidateUserIds"
        flowable:elementVariable="userId">
        <completionCondition xsi:type="tFormalExpression">${rejectedCount > 0 || approvedCount >= requiredApprovals}</completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>
    <exclusiveGateway id="gw-level1" name="第1级结果" default="flow-l1-reject" />
    <userTask id="dept-leader-approve" name="部门领导审批" flowable:assignee="${userId}">
      <documentation>发起人上级组织的部门领导（正副职）会签审批</documentation>
      <extensionElements>
        <flowable:executionListener event="start" delegateExpression="${scopedRoleAssignmentListener}" />
        <omni:assignment>{"roleCode":"DEPT_LEADER","anchorType":"PARENT","anchorParams":{},"scopeMode":"SAME_UNIT","fallbackStrategy":"ERROR","approvalMode":"ANY"}</omni:assignment>
      </extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false"
        flowable:collection="candidateUserIds"
        flowable:elementVariable="userId">
        <completionCondition xsi:type="tFormalExpression">${rejectedCount > 0 || approvedCount >= requiredApprovals}</completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>
    <exclusiveGateway id="gw-level2" name="第2级结果" default="flow-l2-reject" />
    <userTask id="cross-dept-approve" name="跨部门领导审批" flowable:assignee="${userId}">
      <documentation>指定组织的领导（正副职）会签审批，设计者从全量组织树中选择</documentation>
      <extensionElements>
        <flowable:executionListener event="start" delegateExpression="${scopedRoleAssignmentListener}" />
        <omni:assignment>{"roleCode":"DEPT_LEADER","anchorType":"ABSOLUTE_UNIT","anchorParams":{"unitIds":[200]},"scopeMode":"SAME_UNIT","fallbackStrategy":"ERROR","approvalMode":"ANY"}</omni:assignment>
      </extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false"
        flowable:collection="candidateUserIds"
        flowable:elementVariable="userId">
        <completionCondition xsi:type="tFormalExpression">${rejectedCount > 0 || approvedCount >= requiredApprovals}</completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>
    <exclusiveGateway id="gw-level3" name="第3级结果" default="flow-l3-reject" />
    <endEvent id="end-approved" name="审批通过" />
    <endEvent id="end-rejected" name="审批驳回" />
    <sequenceFlow id="flow-start" sourceRef="start" targetRef="direct-leader-approve" />
    <sequenceFlow id="flow-l1-to-gw" sourceRef="direct-leader-approve" targetRef="gw-level1" />
    <sequenceFlow id="flow-l1-pass" sourceRef="gw-level1" targetRef="dept-leader-approve">
      <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow-l1-reject" sourceRef="gw-level1" targetRef="end-rejected" />
    <sequenceFlow id="flow-l2-to-gw" sourceRef="dept-leader-approve" targetRef="gw-level2" />
    <sequenceFlow id="flow-l2-pass" sourceRef="gw-level2" targetRef="cross-dept-approve">
      <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow-l2-reject" sourceRef="gw-level2" targetRef="end-rejected" />
    <sequenceFlow id="flow-l3-to-gw" sourceRef="cross-dept-approve" targetRef="gw-level3" />
    <sequenceFlow id="flow-l3-pass" sourceRef="gw-level3" targetRef="end-approved">
      <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow-l3-reject" sourceRef="gw-level3" targetRef="end-rejected" />
  </process>

  <bpmndi:BPMNDiagram id="BPMNDiagram_leave">
    <bpmndi:BPMNPlane id="BPMNPlane_leave" bpmnElement="leave">
      <bpmndi:BPMNShape id="start_di" bpmnElement="start">
        <dc:Bounds x="100" y="300" width="36" height="36" />
        <bpmndi:BPMNLabel><dc:Bounds x="80" y="343" width="76" height="14" /></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="l1_di" bpmnElement="direct-leader-approve">
        <dc:Bounds x="200" y="278" width="150" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="gw1_di" bpmnElement="gw-level1" isMarkerVisible="true">
        <dc:Bounds x="420" y="293" width="50" height="50" />
        <bpmndi:BPMNLabel><dc:Bounds x="405" y="350" width="80" height="14" /></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="l2_di" bpmnElement="dept-leader-approve">
        <dc:Bounds x="540" y="278" width="150" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="gw2_di" bpmnElement="gw-level2" isMarkerVisible="true">
        <dc:Bounds x="760" y="293" width="50" height="50" />
        <bpmndi:BPMNLabel><dc:Bounds x="745" y="350" width="80" height="14" /></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="l3_di" bpmnElement="cross-dept-approve">
        <dc:Bounds x="880" y="278" width="150" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="gw3_di" bpmnElement="gw-level3" isMarkerVisible="true">
        <dc:Bounds x="1100" y="293" width="50" height="50" />
        <bpmndi:BPMNLabel><dc:Bounds x="1085" y="350" width="80" height="14" /></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="end_approved_di" bpmnElement="end-approved">
        <dc:Bounds x="1220" y="300" width="36" height="36" />
        <bpmndi:BPMNLabel><dc:Bounds x="1208" y="343" width="60" height="14" /></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="end_rejected_di" bpmnElement="end-rejected">
        <dc:Bounds x="785" y="450" width="36" height="36" />
        <bpmndi:BPMNLabel><dc:Bounds x="773" y="493" width="60" height="14" /></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="flow_start_di" bpmnElement="flow-start">
        <di:waypoint x="136" y="318" /><di:waypoint x="200" y="318" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_l1_gw_di" bpmnElement="flow-l1-to-gw">
        <di:waypoint x="350" y="318" /><di:waypoint x="420" y="318" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_l1_pass_di" bpmnElement="flow-l1-pass">
        <di:waypoint x="470" y="318" /><di:waypoint x="540" y="318" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_l1_reject_di" bpmnElement="flow-l1-reject">
        <di:waypoint x="445" y="343" /><di:waypoint x="445" y="468" /><di:waypoint x="785" y="468" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_l2_gw_di" bpmnElement="flow-l2-to-gw">
        <di:waypoint x="690" y="318" /><di:waypoint x="760" y="318" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_l2_pass_di" bpmnElement="flow-l2-pass">
        <di:waypoint x="810" y="318" /><di:waypoint x="880" y="318" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_l2_reject_di" bpmnElement="flow-l2-reject">
        <di:waypoint x="785" y="343" /><di:waypoint x="785" y="450" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_l3_gw_di" bpmnElement="flow-l3-to-gw">
        <di:waypoint x="1030" y="318" /><di:waypoint x="1100" y="318" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_l3_pass_di" bpmnElement="flow-l3-pass">
        <di:waypoint x="1150" y="318" /><di:waypoint x="1220" y="318" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_l3_reject_di" bpmnElement="flow-l3-reject">
        <di:waypoint x="1125" y="343" /><di:waypoint x="1125" y="468" /><di:waypoint x="821" y="468" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>',
NULL);

-- 9.4b 预置采购申请审批流程模型（DRAFT 状态，用户需手动发布）
INSERT INTO wf_process_model (id, tenant_id, model_key, model_name, category, status, current_draft_version_id, create_by)
VALUES (2, 1, 'procurement-approval', '采购申请审批', 'purchase', 1, 2, 'system');

INSERT INTO wf_process_model_version (id, tenant_id, model_id, version, status, bpmn_xml, designer_json)
VALUES (2, 1, 2, 1, 'DRAFT',
'<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:flowable="http://flowable.org/bpmn" xmlns:omni="http://omni.com/workflow" id="Definitions_procurement_approval" targetNamespace="http://flowable.org/test" xsi:schemaLocation="http://www.omg.org/spec/BPMN20 http://www.omg.org/spec/BPMN20/bpmn20.xsd">
  <process id="procurement-approval" name="采购申请审批" isExecutable="true">
    <documentation>采购经理审批采购申请</documentation>
    <startEvent id="start" name="提交" flowable:initiator="initiator" />
    <userTask id="proc-manager-approve" name="采购经理审批" flowable:assignee="${userId}">
      <extensionElements>
        <flowable:executionListener event="start" delegateExpression="${scopedRoleAssignmentListener}" />
        <omni:assignment>{"roleCode":"PROCUREMENT_MANAGER","anchorType":"START_USER_PRIMARY_UNIT","anchorParams":{},"scopeMode":"SAME_UNIT","fallbackStrategy":"ERROR","approvalMode":"ANY"}</omni:assignment>
      </extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false" flowable:collection="candidateUserIds" flowable:elementVariable="userId">
        <completionCondition xsi:type="tFormalExpression">${rejectedCount > 0 || approvedCount >= requiredApprovals}</completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>
    <exclusiveGateway id="gw-level1" name="审批结果" default="flow-l1-reject" />
    <endEvent id="end-approved" name="审批通过" />
    <endEvent id="end-rejected" name="审批驳回" />
    <sequenceFlow id="flow-start" sourceRef="start" targetRef="proc-manager-approve" />
    <sequenceFlow id="flow-l1-to-gw" sourceRef="proc-manager-approve" targetRef="gw-level1" />
    <sequenceFlow id="flow-l1-pass" sourceRef="gw-level1" targetRef="end-approved">
      <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow-l1-reject" sourceRef="gw-level1" targetRef="end-rejected" />
  </process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_procurement_approval"><bpmndi:BPMNPlane id="BPMNPlane_procurement_approval" bpmnElement="procurement-approval">
    <bpmndi:BPMNShape id="start_di" bpmnElement="start"><dc:Bounds x="100" y="300" width="36" height="36" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="l1_di" bpmnElement="proc-manager-approve"><dc:Bounds x="200" y="278" width="150" height="80" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="gw1_di" bpmnElement="gw-level1" isMarkerVisible="true"><dc:Bounds x="420" y="293" width="50" height="50" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="end_approved_di" bpmnElement="end-approved"><dc:Bounds x="540" y="300" width="36" height="36" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="end_rejected_di" bpmnElement="end-rejected"><dc:Bounds x="445" y="450" width="36" height="36" /></bpmndi:BPMNShape>
    <bpmndi:BPMNEdge id="flow_start_di" bpmnElement="flow-start"><di:waypoint x="136" y="318" /><di:waypoint x="200" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l1_gw_di" bpmnElement="flow-l1-to-gw"><di:waypoint x="350" y="318" /><di:waypoint x="420" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l1_pass_di" bpmnElement="flow-l1-pass"><di:waypoint x="470" y="318" /><di:waypoint x="540" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l1_reject_di" bpmnElement="flow-l1-reject"><di:waypoint x="445" y="343" /><di:waypoint x="445" y="450" /></bpmndi:BPMNEdge>
  </bpmndi:BPMNPlane></bpmndi:BPMNDiagram>
</definitions>',
NULL);

-- 9.4c 预置资产调拨审批流程模型（Workflow 启动时幂等自动发布）
INSERT INTO wf_process_model (id, tenant_id, model_key, model_name, category, status, current_draft_version_id, create_by)
VALUES (3, 1, 'asset-transfer', '资产调拨审批', 'ASSET_TRANSFER', 1, 3, 'system');

INSERT INTO wf_process_model_version (id, tenant_id, model_id, version, status, bpmn_xml, designer_json)
VALUES (3, 1, 3, 1, 'DRAFT',
'<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:flowable="http://flowable.org/bpmn" xmlns:omni="http://omni.com/workflow" id="Definitions_asset_transfer" targetNamespace="http://flowable.org/test" xsi:schemaLocation="http://www.omg.org/spec/BPMN20 http://www.omg.org/spec/BPMN20/bpmn20.xsd">
  <process id="asset-transfer" name="资产调拨审批" isExecutable="true">
    <documentation>资产经理审批资产调拨</documentation>
    <startEvent id="start" name="提交" flowable:initiator="initiator" />
    <userTask id="asset-manager-approve" name="资产经理审批" flowable:assignee="${userId}">
      <extensionElements>
        <flowable:executionListener event="start" delegateExpression="${scopedRoleAssignmentListener}" />
        <omni:assignment>{"roleCode":"ASSET_MANAGER","anchorType":"START_USER_PRIMARY_UNIT","anchorParams":{},"scopeMode":"SAME_UNIT","fallbackStrategy":"ERROR","approvalMode":"ANY"}</omni:assignment>
      </extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false" flowable:collection="candidateUserIds" flowable:elementVariable="userId">
        <completionCondition xsi:type="tFormalExpression">${rejectedCount > 0 || approvedCount >= requiredApprovals}</completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>
    <exclusiveGateway id="gw-level1" name="审批结果" default="flow-l1-reject" />
    <endEvent id="end-approved" name="审批通过" />
    <endEvent id="end-rejected" name="审批驳回" />
    <sequenceFlow id="flow-start" sourceRef="start" targetRef="asset-manager-approve" />
    <sequenceFlow id="flow-l1-to-gw" sourceRef="asset-manager-approve" targetRef="gw-level1" />
    <sequenceFlow id="flow-l1-pass" sourceRef="gw-level1" targetRef="end-approved">
      <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow-l1-reject" sourceRef="gw-level1" targetRef="end-rejected" />
  </process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_asset_transfer"><bpmndi:BPMNPlane id="BPMNPlane_asset_transfer" bpmnElement="asset-transfer">
    <bpmndi:BPMNShape id="start_di" bpmnElement="start"><dc:Bounds x="100" y="300" width="36" height="36" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="l1_di" bpmnElement="asset-manager-approve"><dc:Bounds x="200" y="278" width="150" height="80" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="gw1_di" bpmnElement="gw-level1" isMarkerVisible="true"><dc:Bounds x="420" y="293" width="50" height="50" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="end_approved_di" bpmnElement="end-approved"><dc:Bounds x="540" y="300" width="36" height="36" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="end_rejected_di" bpmnElement="end-rejected"><dc:Bounds x="445" y="450" width="36" height="36" /></bpmndi:BPMNShape>
    <bpmndi:BPMNEdge id="flow_start_di" bpmnElement="flow-start"><di:waypoint x="136" y="318" /><di:waypoint x="200" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l1_gw_di" bpmnElement="flow-l1-to-gw"><di:waypoint x="350" y="318" /><di:waypoint x="420" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l1_pass_di" bpmnElement="flow-l1-pass"><di:waypoint x="470" y="318" /><di:waypoint x="540" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l1_reject_di" bpmnElement="flow-l1-reject"><di:waypoint x="445" y="343" /><di:waypoint x="445" y="450" /></bpmndi:BPMNEdge>
  </bpmndi:BPMNPlane></bpmndi:BPMNDiagram>
</definitions>',
NULL);

-- 9.4d 预置资产处置审批流程模型（Workflow 启动时幂等自动发布）
INSERT INTO wf_process_model (id, tenant_id, model_key, model_name, category, status, current_draft_version_id, create_by)
VALUES (4, 1, 'asset-disposal', '资产处置审批', 'ASSET_DISPOSAL', 1, 4, 'system');

INSERT INTO wf_process_model_version (id, tenant_id, model_id, version, status, bpmn_xml, designer_json)
VALUES (4, 1, 4, 1, 'DRAFT',
'<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:flowable="http://flowable.org/bpmn" xmlns:omni="http://omni.com/workflow" id="Definitions_asset_disposal" targetNamespace="http://flowable.org/test" xsi:schemaLocation="http://www.omg.org/spec/BPMN20 http://www.omg.org/spec/BPMN20/bpmn20.xsd">
  <process id="asset-disposal" name="资产处置审批" isExecutable="true">
    <documentation>资产经理审批资产处置</documentation>
    <startEvent id="start" name="提交" flowable:initiator="initiator" />
    <userTask id="asset-manager-approve" name="资产经理审批" flowable:assignee="${userId}">
      <extensionElements>
        <flowable:executionListener event="start" delegateExpression="${scopedRoleAssignmentListener}" />
        <omni:assignment>{"roleCode":"ASSET_MANAGER","anchorType":"START_USER_PRIMARY_UNIT","anchorParams":{},"scopeMode":"SAME_UNIT","fallbackStrategy":"ERROR","approvalMode":"ANY"}</omni:assignment>
      </extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false" flowable:collection="candidateUserIds" flowable:elementVariable="userId">
        <completionCondition xsi:type="tFormalExpression">${rejectedCount > 0 || approvedCount >= requiredApprovals}</completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>
    <exclusiveGateway id="gw-level1" name="审批结果" default="flow-l1-reject" />
    <endEvent id="end-approved" name="审批通过" />
    <endEvent id="end-rejected" name="审批驳回" />
    <sequenceFlow id="flow-start" sourceRef="start" targetRef="asset-manager-approve" />
    <sequenceFlow id="flow-l1-to-gw" sourceRef="asset-manager-approve" targetRef="gw-level1" />
    <sequenceFlow id="flow-l1-pass" sourceRef="gw-level1" targetRef="end-approved">
      <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow-l1-reject" sourceRef="gw-level1" targetRef="end-rejected" />
  </process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_asset_disposal"><bpmndi:BPMNPlane id="BPMNPlane_asset_disposal" bpmnElement="asset-disposal">
    <bpmndi:BPMNShape id="start_di" bpmnElement="start"><dc:Bounds x="100" y="300" width="36" height="36" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="l1_di" bpmnElement="asset-manager-approve"><dc:Bounds x="200" y="278" width="150" height="80" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="gw1_di" bpmnElement="gw-level1" isMarkerVisible="true"><dc:Bounds x="420" y="293" width="50" height="50" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="end_approved_di" bpmnElement="end-approved"><dc:Bounds x="540" y="300" width="36" height="36" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="end_rejected_di" bpmnElement="end-rejected"><dc:Bounds x="445" y="450" width="36" height="36" /></bpmndi:BPMNShape>
    <bpmndi:BPMNEdge id="flow_start_di" bpmnElement="flow-start"><di:waypoint x="136" y="318" /><di:waypoint x="200" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l1_gw_di" bpmnElement="flow-l1-to-gw"><di:waypoint x="350" y="318" /><di:waypoint x="420" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l1_pass_di" bpmnElement="flow-l1-pass"><di:waypoint x="470" y="318" /><di:waypoint x="540" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l1_reject_di" bpmnElement="flow-l1-reject"><di:waypoint x="445" y="343" /><di:waypoint x="445" y="450" /></bpmndi:BPMNEdge>
  </bpmndi:BPMNPlane></bpmndi:BPMNDiagram>
</definitions>',
NULL);

-- 9.4e 预置供应商准入审批流程模型（Workflow 启动时幂等自动发布）
INSERT INTO wf_process_model (id, tenant_id, model_key, model_name, category, status, current_draft_version_id, create_by)
VALUES (5, 1, 'supplier-onboarding', '供应商准入审批', 'SRM_SUPPLIER_ONBOARDING', 1, 5, 'system');

INSERT INTO wf_process_model_version (id, tenant_id, model_id, version, status, bpmn_xml, designer_json)
VALUES (5, 1, 5, 1, 'DRAFT',
'<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:flowable="http://flowable.org/bpmn" xmlns:omni="http://omni.com/workflow" id="Definitions_supplier_onboarding" targetNamespace="http://flowable.org/test" xsi:schemaLocation="http://www.omg.org/spec/BPMN20 http://www.omg.org/spec/BPMN20/bpmn20.xsd">
  <process id="supplier-onboarding" name="供应商准入审批（3级会签）" isExecutable="true">
    <documentation>3级会签审批供应商准入流程：采购经理 → 合规负责人 → 高管</documentation>
    <startEvent id="start" name="提交供应商准入" flowable:initiator="initiator" />
    <userTask id="manager-approve" name="采购经理审批" flowable:assignee="${userId}">
      <extensionElements>
        <flowable:executionListener event="start" delegateExpression="${scopedRoleAssignmentListener}" />
        <omni:assignment>{"roleCode":"SRM_MANAGER","anchorType":"PARENT","anchorParams":{},"scopeMode":"SAME_UNIT","fallbackStrategy":"ERROR","approvalMode":"ANY"}</omni:assignment>
      </extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false" flowable:collection="candidateUserIds" flowable:elementVariable="userId">
        <completionCondition xsi:type="tFormalExpression">${rejectedCount > 0 || approvedCount >= requiredApprovals}</completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>
    <exclusiveGateway id="gw-level1" name="第1级结果" default="flow-l1-reject" />
    <userTask id="compliance-approve" name="合规负责人审批" flowable:assignee="${userId}">
      <extensionElements>
        <flowable:executionListener event="start" delegateExpression="${scopedRoleAssignmentListener}" />
        <omni:assignment>{"roleCode":"SRM_COMPLIANCE","anchorType":"PARENT","anchorParams":{},"scopeMode":"SAME_UNIT","fallbackStrategy":"ERROR","approvalMode":"ANY"}</omni:assignment>
      </extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false" flowable:collection="candidateUserIds" flowable:elementVariable="userId">
        <completionCondition xsi:type="tFormalExpression">${rejectedCount > 0 || approvedCount >= requiredApprovals}</completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>
    <exclusiveGateway id="gw-level2" name="第2级结果" default="flow-l2-reject" />
    <userTask id="director-approve" name="高管审批" flowable:assignee="${userId}">
      <extensionElements>
        <flowable:executionListener event="start" delegateExpression="${scopedRoleAssignmentListener}" />
        <omni:assignment>{"roleCode":"SRM_DIRECTOR","anchorType":"ABSOLUTE_UNIT","anchorParams":{"unitIds":[1]},"scopeMode":"SAME_UNIT","fallbackStrategy":"ERROR","approvalMode":"ALL"}</omni:assignment>
      </extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false" flowable:collection="candidateUserIds" flowable:elementVariable="userId">
        <completionCondition xsi:type="tFormalExpression">${rejectedCount > 0 || approvedCount >= requiredApprovals}</completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>
    <exclusiveGateway id="gw-level3" name="第3级结果" default="flow-l3-reject" />
    <endEvent id="end-approved" name="审批通过" />
    <endEvent id="end-rejected" name="审批驳回" />
    <sequenceFlow id="flow-start" sourceRef="start" targetRef="manager-approve" />
    <sequenceFlow id="flow-l1-to-gw" sourceRef="manager-approve" targetRef="gw-level1" />
    <sequenceFlow id="flow-l1-pass" sourceRef="gw-level1" targetRef="compliance-approve">
      <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow-l1-reject" sourceRef="gw-level1" targetRef="end-rejected" />
    <sequenceFlow id="flow-l2-to-gw" sourceRef="compliance-approve" targetRef="gw-level2" />
    <sequenceFlow id="flow-l2-pass" sourceRef="gw-level2" targetRef="director-approve">
      <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow-l2-reject" sourceRef="gw-level2" targetRef="end-rejected" />
    <sequenceFlow id="flow-l3-to-gw" sourceRef="director-approve" targetRef="gw-level3" />
    <sequenceFlow id="flow-l3-pass" sourceRef="gw-level3" targetRef="end-approved">
      <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow-l3-reject" sourceRef="gw-level3" targetRef="end-rejected" />
  </process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_supplier_onboarding"><bpmndi:BPMNPlane id="BPMNPlane_supplier_onboarding" bpmnElement="supplier-onboarding">
    <bpmndi:BPMNShape id="start_di" bpmnElement="start"><dc:Bounds x="100" y="300" width="36" height="36" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="l1_di" bpmnElement="manager-approve"><dc:Bounds x="200" y="278" width="150" height="80" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="gw1_di" bpmnElement="gw-level1" isMarkerVisible="true"><dc:Bounds x="420" y="293" width="50" height="50" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="l2_di" bpmnElement="compliance-approve"><dc:Bounds x="540" y="278" width="150" height="80" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="gw2_di" bpmnElement="gw-level2" isMarkerVisible="true"><dc:Bounds x="760" y="293" width="50" height="50" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="l3_di" bpmnElement="director-approve"><dc:Bounds x="880" y="278" width="150" height="80" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="gw3_di" bpmnElement="gw-level3" isMarkerVisible="true"><dc:Bounds x="1100" y="293" width="50" height="50" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="end_approved_di" bpmnElement="end-approved"><dc:Bounds x="1220" y="300" width="36" height="36" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="end_rejected_di" bpmnElement="end-rejected"><dc:Bounds x="785" y="450" width="36" height="36" /></bpmndi:BPMNShape>
    <bpmndi:BPMNEdge id="flow_start_di" bpmnElement="flow-start"><di:waypoint x="136" y="318" /><di:waypoint x="200" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l1_gw_di" bpmnElement="flow-l1-to-gw"><di:waypoint x="350" y="318" /><di:waypoint x="420" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l1_pass_di" bpmnElement="flow-l1-pass"><di:waypoint x="470" y="318" /><di:waypoint x="540" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l1_reject_di" bpmnElement="flow-l1-reject"><di:waypoint x="445" y="343" /><di:waypoint x="445" y="468" /><di:waypoint x="785" y="468" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l2_gw_di" bpmnElement="flow-l2-to-gw"><di:waypoint x="690" y="318" /><di:waypoint x="760" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l2_pass_di" bpmnElement="flow-l2-pass"><di:waypoint x="810" y="318" /><di:waypoint x="880" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l2_reject_di" bpmnElement="flow-l2-reject"><di:waypoint x="785" y="343" /><di:waypoint x="785" y="450" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l3_gw_di" bpmnElement="flow-l3-to-gw"><di:waypoint x="1030" y="318" /><di:waypoint x="1100" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l3_pass_di" bpmnElement="flow-l3-pass"><di:waypoint x="1150" y="318" /><di:waypoint x="1220" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l3_reject_di" bpmnElement="flow-l3-reject"><di:waypoint x="1125" y="343" /><di:waypoint x="1125" y="468" /><di:waypoint x="821" y="468" /></bpmndi:BPMNEdge>
  </bpmndi:BPMNPlane></bpmndi:BPMNDiagram>
</definitions>',
NULL);

-- 9.5 用户角色作用域表（omni_auth 库）
USE omni_auth;

CREATE TABLE IF NOT EXISTS sys_user_role_scope (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL COMMENT '租户 ID',
    user_id     BIGINT       NOT NULL COMMENT '用户 ID',
    role_id     BIGINT       NOT NULL COMMENT '角色 ID',
    unit_id     BIGINT       NOT NULL COMMENT '组织单元 ID',
    scope_mode  VARCHAR(20)  NOT NULL DEFAULT 'SAME_UNIT' COMMENT '作用域模式: SAME_UNIT / UNIT_AND_BELOW',
    status      TINYINT      DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_scope (tenant_id, user_id, role_id, unit_id),
    INDEX idx_scope_tenant (tenant_id),
    INDEX idx_scope_user (user_id),
    INDEX idx_scope_role_unit (role_id, unit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户角色作用域表';

-- 9.6 MQ 消息发送记录表（omni_auth 库副本，omni-common-mqlog starter 自动建表）
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

-- 9.7 CRM 功能权限与默认角色（omni_auth）
INSERT IGNORE INTO sys_permission
    (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (300, 1, 0,   'crm',                      '客户关系管理', 'DIRECTORY', '/300/',         1, 7, 1, 'system'),
    (301, 1, 300, 'crm:overview',             'CRM概览',      'MENU',      '/300/301/',     2, 1, 1, 'system'),
    (302, 1, 301, 'crm:overview:list',        '查看CRM概览',  'API',       '/300/301/302/', 3, 1, 1, 'system'),
    (310, 1, 300, 'crm:lead',                 '线索管理',     'MENU',      '/300/310/',     2, 2, 1, 'system'),
    (311, 1, 310, 'crm:lead:list',            '查看线索',     'API',       '/300/310/311/', 3, 1, 1, 'system'),
    (312, 1, 310, 'crm:lead:create',          '创建线索',     'API',       '/300/310/312/', 3, 2, 1, 'system'),
    (313, 1, 310, 'crm:lead:update',          '更新线索',     'API',       '/300/310/313/', 3, 3, 1, 'system'),
    (314, 1, 310, 'crm:lead:delete',          '删除线索',     'API',       '/300/310/314/', 3, 4, 1, 'system'),
    (315, 1, 310, 'crm:lead:assign',          '分配线索',     'API',       '/300/310/315/', 3, 5, 1, 'system'),
    (316, 1, 310, 'crm:lead:disqualify',      '判定无效线索', 'API',       '/300/310/316/', 3, 6, 1, 'system'),
    (317, 1, 310, 'crm:lead:convert',         '转换线索',     'API',       '/300/310/317/', 3, 7, 1, 'system'),
    (320, 1, 300, 'crm:customer',             '客户管理',     'MENU',      '/300/320/',     2, 3, 1, 'system'),
    (321, 1, 320, 'crm:customer:list',        '查看客户',     'API',       '/300/320/321/', 3, 1, 1, 'system'),
    (322, 1, 320, 'crm:customer:create',      '创建客户',     'API',       '/300/320/322/', 3, 2, 1, 'system'),
    (323, 1, 320, 'crm:customer:update',      '更新客户',     'API',       '/300/320/323/', 3, 3, 1, 'system'),
    (324, 1, 320, 'crm:customer:delete',      '删除客户',     'API',       '/300/320/324/', 3, 4, 1, 'system'),
    (325, 1, 320, 'crm:customer:status',      '变更客户状态', 'API',       '/300/320/325/', 3, 5, 1, 'system'),
    (326, 1, 320, 'crm:customer:transfer',    '转移客户',     'API',       '/300/320/326/', 3, 6, 1, 'system'),
    (327, 1, 320, 'crm:customer:blacklist',   '客户黑名单',   'API',       '/300/320/327/', 3, 7, 1, 'system'),
    (332, 1, 300, 'crm:contact',              '联系人管理',   'MENU',      '/300/332/',     2, 4, 1, 'system'),
    (328, 1, 332, 'crm:contact:list',         '查看联系人',   'API',       '/300/332/328/', 3, 1, 1, 'system'),
    (329, 1, 332, 'crm:contact:create',       '创建联系人',   'API',       '/300/332/329/', 3, 2, 1, 'system'),
    (330, 1, 332, 'crm:contact:update',       '更新联系人',   'API',       '/300/332/330/', 3, 3, 1, 'system'),
    (331, 1, 332, 'crm:contact:delete',       '删除联系人',   'API',       '/300/332/331/', 3, 4, 1, 'system'),
    (340, 1, 300, 'crm:opportunity',          '商机管理',     'MENU',      '/300/340/',     2, 5, 1, 'system'),
    (341, 1, 340, 'crm:opportunity:list',     '查看商机',     'API',       '/300/340/341/', 3, 1, 1, 'system'),
    (342, 1, 340, 'crm:opportunity:create',   '创建商机',     'API',       '/300/340/342/', 3, 2, 1, 'system'),
    (343, 1, 340, 'crm:opportunity:update',   '更新商机',     'API',       '/300/340/343/', 3, 3, 1, 'system'),
    (344, 1, 340, 'crm:opportunity:delete',   '删除商机',     'API',       '/300/340/344/', 3, 4, 1, 'system'),
    (345, 1, 340, 'crm:opportunity:assign',   '分配商机',     'API',       '/300/340/345/', 3, 5, 1, 'system'),
    (346, 1, 340, 'crm:opportunity:stage',    '推进商机阶段', 'API',       '/300/340/346/', 3, 6, 1, 'system'),
    (347, 1, 340, 'crm:opportunity:reopen',   '重开商机',     'API',       '/300/340/347/', 3, 7, 1, 'system'),
    (360, 1, 300, 'crm:activity',             '跟进管理',     'MENU',      '/300/360/',     2, 6, 1, 'system'),
    (361, 1, 360, 'crm:activity:list',        '查看跟进',     'API',       '/300/360/361/', 3, 1, 1, 'system'),
    (362, 1, 360, 'crm:activity:create',      '创建跟进',     'API',       '/300/360/362/', 3, 2, 1, 'system'),
    (363, 1, 360, 'crm:activity:update',      '更新跟进',     'API',       '/300/360/363/', 3, 3, 1, 'system'),
    (364, 1, 360, 'crm:activity:delete',      '删除跟进',     'API',       '/300/360/364/', 3, 4, 1, 'system'),
    (365, 1, 360, 'crm:activity:complete',    '完成跟进',     'API',       '/300/360/365/', 3, 5, 1, 'system'),
    (366, 1, 360, 'crm:activity:cancel',      '取消跟进',     'API',       '/300/360/366/', 3, 6, 1, 'system'),
    (380, 1, 300, 'crm:owner:list',           '查看负责人选项','API',       '/300/380/',     2, 7, 1, 'system'),
    (381, 1, 300, 'crm:pii:view',             '查看完整联系信息','API',     '/300/381/',     2, 8, 1, 'system');

INSERT IGNORE INTO sys_role
    (id, tenant_id, role_code, role_name, data_scope, sort, status, create_by)
VALUES
    (20, 1, 'CRM_ADMIN',     'CRM管理员', 'TENANT',         20, 1, 'system'),
    (21, 1, 'SALES_MANAGER', '销售经理',  'DEPT_AND_BELOW', 21, 1, 'system'),
    (22, 1, 'SALES_REP',     '销售代表',  'SELF',           22, 1, 'system'),
    (23, 1, 'CRM_VIEWER',    'CRM只读员', 'TENANT',         23, 1, 'system');

-- SUPER_ADMIN、CRM_ADMIN、SALES_MANAGER 获得完整 CRM 权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.tenant_id = 1
  AND r.role_code IN ('SUPER_ADMIN', 'CRM_ADMIN', 'SALES_MANAGER')
  AND (p.permission_code = 'crm' OR p.permission_code LIKE 'crm:%');

-- 销售代表可在 SELF 数据范围内完成日常销售闭环，不授予删除、分配、转移和黑名单权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.tenant_id = 1 AND r.role_code = 'SALES_REP'
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

-- 只读角色可查看租户级统计和业务数据，但默认只能看到脱敏联系信息
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.tenant_id = 1 AND r.role_code = 'CRM_VIEWER'
  AND p.permission_code IN (
      'crm','crm:overview','crm:lead','crm:customer','crm:contact','crm:opportunity','crm:activity',
      'crm:overview:list','crm:lead:list','crm:customer:list','crm:contact:list',
      'crm:opportunity:list','crm:activity:list'
  );

-- Portal 角色分配请求 Inbox：按 request_id 幂等消费 SRM Saga 请求
CREATE TABLE IF NOT EXISTS sys_portal_role_request (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL,
    request_id  VARCHAR(64)  NOT NULL,
    supplier_id BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    role_code   VARCHAR(50)  NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PROCESSING',
    error_code  VARCHAR(100) DEFAULT NULL,
    version     INT          NOT NULL DEFAULT 0,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_portal_role_request (tenant_id, request_id),
    INDEX idx_portal_role_user (tenant_id, user_id, status),
    CONSTRAINT chk_portal_role_request_status CHECK (status IN ('PROCESSING','COMPLETED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门户角色分配请求幂等 Inbox';

-- ============================================================
-- Section 9.8: SRM 功能权限与默认角色（omni_auth）
-- ============================================================
INSERT IGNORE INTO sys_permission
    (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (400, 1, 0,   'srm',                          '供应商关系管理',  'DIRECTORY', '/400/',             1, 8, 1, 'system'),
    (401, 1, 400, 'srm:overview',                   'SRM概览',        'MENU',      '/400/401/',         2, 1, 1, 'system'),
    (402, 1, 401, 'srm:overview:list',              '查看SRM概览',    'API',       '/400/401/402/',     3, 1, 1, 'system'),
    (410, 1, 400, 'srm:supplier',                   '供应商管理',      'MENU',      '/400/410/',         2, 2, 1, 'system'),
    (411, 1, 410, 'srm:supplier:list',              '查看供应商',      'API',       '/400/410/411/',     3, 1, 1, 'system'),
    (412, 1, 410, 'srm:supplier:create',            '创建供应商',      'API',       '/400/410/412/',     3, 2, 1, 'system'),
    (413, 1, 410, 'srm:supplier:update',            '更新供应商',      'API',       '/400/410/413/',     3, 3, 1, 'system'),
    (414, 1, 410, 'srm:supplier:delete',            '删除供应商',      'API',       '/400/410/414/',     3, 4, 1, 'system'),
    (415, 1, 410, 'srm:supplier:withdraw',         '撤回审批流程',    'API',       '/400/410/415/',     3, 5, 1, 'system'),
    (416, 1, 410, 'srm:supplier:cancel',           '取消审批流程',    'API',       '/400/410/416/',     3, 6, 1, 'system'),
    (417, 1, 410, 'srm:supplier:suspend',           '冻结供应商',      'API',       '/400/410/417/',     3, 7, 1, 'system'),
    (418, 1, 410, 'srm:supplier:resume',            '解冻供应商',      'API',       '/400/410/418/',     3, 8, 1, 'system'),
    (419, 1, 410, 'srm:supplier:blacklist',         '供应商黑名单',    'API',       '/400/410/419/',     3, 9, 1, 'system'),
    (462, 1, 410, 'srm:supplier:restore',           '恢复供应商',      'API',       '/400/410/462/',     3, 10, 1, 'system'),
    (463, 1, 410, 'srm:supplier:eliminate',         '淘汰供应商',      'API',       '/400/410/463/',     3, 11, 1, 'system'),
    (464, 1, 410, 'srm:supplier:transfer',          '转移供应商负责人', 'API',       '/400/410/464/',     3, 12, 1, 'system'),
    (420, 1, 400, 'srm:evaluation',                 '绩效评估',        'MENU',      '/400/420/',         2, 3, 1, 'system'),
    (421, 1, 420, 'srm:evaluation:list',            '查看评估',        'API',       '/400/420/421/',     3, 1, 1, 'system'),
    (422, 1, 420, 'srm:evaluation:create',          '创建评估',        'API',       '/400/420/422/',     3, 2, 1, 'system'),
    (423, 1, 420, 'srm:evaluation:view',            '查看评估详情',    'API',       '/400/420/423/',     3, 3, 1, 'system'),
    (430, 1, 400, 'srm:risk',                       '风险管理',        'MENU',      '/400/430/',         2, 4, 1, 'system'),
    (431, 1, 430, 'srm:risk:list',                  '查看风险',        'API',       '/400/430/431/',     3, 1, 1, 'system'),
    (432, 1, 430, 'srm:risk:update',                '更新风险指标',    'API',       '/400/430/432/',     3, 2, 1, 'system'),
    (433, 1, 430, 'srm:risk:assess',                '创建风险评估',    'API',       '/400/430/433/',     3, 3, 1, 'system'),
    (434, 1, 400, 'srm:risk:config',                '风险指标配置',    'MENU',      '/400/434/',         2, 5, 1, 'system'),
    (435, 1, 434, 'srm:risk:config:list',           '查看指标配置',    'API',       '/400/434/435/',     3, 1, 1, 'system'),
    (436, 1, 434, 'srm:risk:config:update',         '修改指标配置',    'API',       '/400/434/436/',     3, 2, 1, 'system'),
    (440, 1, 400, 'srm:portal',                     '供应商门户',      'DIRECTORY', '/400/440/',         2, 5, 1, 'system'),
    (441, 1, 440, 'srm:portal:enroll',              '门户入驻',        'API',       '/400/440/441/',     3, 1, 1, 'system'),
    (442, 1, 440, 'srm:portal:profile',             '企业信息',        'MENU',      '/400/440/442/',     3, 2, 1, 'system'),
    (443, 1, 440, 'srm:portal:evaluation',          '绩效评估',        'MENU',      '/400/440/443/',     3, 3, 1, 'system'),
    (444, 1, 440, 'srm:portal:quotation',           '询价报价',        'MENU',      '/400/440/444/',     3, 4, 1, 'system'),
    (450, 1, 400, 'srm:invite',                     '邀请管理',        'MENU',      '/400/450/',         2, 6, 1, 'system'),
    (451, 1, 450, 'srm:invite:create',              '创建邀请',        'API',       '/400/450/451/',     3, 1, 1, 'system'),
    (452, 1, 450, 'srm:invite:list',                '查看邀请',        'API',       '/400/450/452/',     3, 2, 1, 'system'),
    (453, 1, 450, 'srm:invite:revoke',              '撤销邀请',        'API',       '/400/450/453/',     3, 3, 1, 'system'),
    (482, 1, 450, 'srm:portal:invite',              '管理门户邀请',    'API',       '/400/450/482/',     3, 4, 1, 'system'),
    (460, 1, 400, 'srm:owner:list',                 '查看负责人选项',  'API',       '/400/460/',         2, 7, 1, 'system'),
    (461, 1, 400, 'srm:pii:view',                   '查看完整银行信息','API',       '/400/461/',         2, 8, 1, 'system'),
    -- 联系人权限
    (470, 1, 410, 'srm:contact:list',               '查看联系人',      'API',       '/400/410/470/',     3, 12, 1, 'system'),
    (471, 1, 410, 'srm:contact:create',             '创建联系人',      'API',       '/400/410/471/',     3, 13, 1, 'system'),
    (480, 1, 410, 'srm:contact:update',             '更新联系人',      'API',       '/400/410/480/',     3, 14, 1, 'system'),
    (481, 1, 410, 'srm:contact:delete',             '删除联系人',      'API',       '/400/410/481/',     3, 15, 1, 'system'),
    -- 资质权限
    (472, 1, 410, 'srm:qualification:list',          '查看资质',        'API',       '/400/410/472/',     3, 14, 1, 'system'),
    (473, 1, 410, 'srm:qualification:create',        '创建资质',        'API',       '/400/410/473/',     3, 15, 1, 'system'),
    (474, 1, 410, 'srm:qualification:update',        '更新资质',        'API',       '/400/410/474/',     3, 16, 1, 'system'),
    (475, 1, 410, 'srm:qualification:delete',        '删除资质',        'API',       '/400/410/475/',     3, 17, 1, 'system'),
    -- 银行账户权限
    (476, 1, 410, 'srm:bank-account:list',            '查看银行账户',    'API',       '/400/410/476/',     3, 18, 1, 'system'),
    (477, 1, 410, 'srm:bank-account:create',          '创建银行账户',    'API',       '/400/410/477/',     3, 19, 1, 'system'),
    (478, 1, 410, 'srm:bank-account:update',          '更新银行账户',    'API',       '/400/410/478/',     3, 20, 1, 'system'),
    (479, 1, 410, 'srm:bank-account:delete',          '删除银行账户',    'API',       '/400/410/479/',     3, 21, 1, 'system');

INSERT IGNORE INTO sys_role
    (id, tenant_id, role_code, role_name, data_scope, sort, status, create_by)
VALUES
    (30, 1, 'SRM_ADMIN',           'SRM管理员',       'TENANT',         30, 1, 'system'),
    (31, 1, 'PROCUREMENT_MANAGER', '采购经理',        'DEPT_AND_BELOW', 31, 1, 'system'),
    (32, 1, 'PROCUREMENT_STAFF',   '采购员',          'SELF',           32, 1, 'system'),
    (33, 1, 'SUPPLIER',            '供应商',          'SELF',           33, 1, 'system'),
    (34, 1, 'SRM_MANAGER',         '采购经理',        'DEPT_AND_BELOW', 34, 1, 'system'),
    (35, 1, 'SRM_COMPLIANCE',      '合规负责人',      'DEPT_AND_BELOW', 35, 1, 'system'),
    (36, 1, 'SRM_DIRECTOR',        '高管',            'TENANT',         36, 1, 'system');

-- SUPER_ADMIN 获得全部 SRM 权限；SRM_ADMIN、PROCUREMENT_MANAGER 显式排除供应商自助门户
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.tenant_id = 1
  AND r.role_code IN ('SUPER_ADMIN', 'SRM_ADMIN', 'PROCUREMENT_MANAGER')
  AND (p.permission_code = 'srm' OR p.permission_code LIKE 'srm:%')
  AND (r.role_code = 'SUPER_ADMIN'
       OR p.permission_code NOT IN (
           'srm:portal:enroll', 'srm:portal:profile',
           'srm:portal:evaluation', 'srm:portal:quotation'
       ));

-- 采购员日常供应商管理权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.tenant_id = 1 AND r.role_code = 'PROCUREMENT_STAFF'
  AND p.permission_code IN (
      'srm','srm:overview','srm:supplier','srm:evaluation','srm:risk',
      'srm:overview:list','srm:supplier:list','srm:supplier:create','srm:supplier:update',
      'srm:evaluation:list','srm:evaluation:create','srm:evaluation:view',
      'srm:risk:list','srm:risk:update','srm:risk:assess','srm:risk:config','srm:risk:config:list','srm:risk:config:update',
      'srm:contact:list','srm:contact:create','srm:contact:update','srm:contact:delete',
      'srm:qualification:list','srm:qualification:create','srm:qualification:update','srm:qualification:delete',
      'srm:bank-account:list','srm:bank-account:create','srm:bank-account:update','srm:bank-account:delete',
      'srm:owner:list'
  );

-- USER 角色仅授予门户入驻权限；企业信息必须等待 SUPPLIER 角色与 PortalUser 关联建立后访问
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.tenant_id = 1 AND r.role_code = 'USER'
  AND p.permission_code IN ('srm', 'srm:portal', 'srm:portal:enroll');

-- 供应商角色授予门户权限（企业信息 + 绩效）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.tenant_id = 1 AND r.role_code = 'SUPPLIER'
  AND p.permission_code IN (
      'srm', 'srm:portal', 'srm:portal:profile',
      'srm:portal:evaluation', 'srm:portal:quotation'
  );

-- ============================================================
-- Section 9.9: Procurement 首批功能权限（omni_auth）
-- ============================================================
INSERT IGNORE INTO sys_permission
    (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (500, 1, 0,   'procurement',                         '采购管理',     'DIRECTORY', '/500/',         1, 8, 1, 'system'),
    (505, 1, 500, 'procurement:overview',                '采购概览',     'MENU',      '/500/505/',     2, 1, 1, 'system'),
    (506, 1, 505, 'procurement:overview:list',           '查看采购概览', 'API',       '/500/505/506/', 3, 1, 1, 'system'),
    (510, 1, 500, 'procurement:material',                '物料目录',     'MENU',      '/500/510/',     2, 2, 1, 'system'),
    (511, 1, 510, 'procurement:material:list',           '查看物料',     'API',       '/500/510/511/', 3, 1, 1, 'system'),
    (512, 1, 510, 'procurement:material:create',         '创建物料',     'API',       '/500/510/512/', 3, 2, 1, 'system'),
    (513, 1, 510, 'procurement:material:update',         '更新物料',     'API',       '/500/510/513/', 3, 3, 1, 'system'),
    (514, 1, 510, 'procurement:material:delete',         '删除物料',     'API',       '/500/510/514/', 3, 4, 1, 'system'),
    (520, 1, 500, 'procurement:requisition',             '请购管理',     'MENU',      '/500/520/',     2, 3, 1, 'system'),
    (521, 1, 520, 'procurement:requisition:list',        '查看请购',     'API',       '/500/520/521/', 3, 1, 1, 'system'),
    (522, 1, 520, 'procurement:requisition:create',      '创建请购',     'API',       '/500/520/522/', 3, 2, 1, 'system'),
    (523, 1, 520, 'procurement:requisition:update',      '更新请购',     'API',       '/500/520/523/', 3, 3, 1, 'system'),
    (524, 1, 520, 'procurement:requisition:delete',      '删除请购',     'API',       '/500/520/524/', 3, 4, 1, 'system'),
    (525, 1, 520, 'procurement:requisition:submit',      '提交请购',     'API',       '/500/520/525/', 3, 5, 1, 'system'),
    (526, 1, 520, 'procurement:requisition:approve',     '审批请购视图', 'API',       '/500/520/526/', 3, 6, 1, 'system'),
    (527, 1, 520, 'procurement:requisition:cancel',      '取消请购',     'API',       '/500/520/527/', 3, 7, 1, 'system'),
    (530, 1, 500, 'procurement:approval-route',          '审批路由',     'MENU',      '/500/530/',     2, 4, 1, 'system'),
    (531, 1, 530, 'procurement:approval-route:list',     '查看审批路由', 'API',       '/500/530/531/', 3, 1, 1, 'system'),
    (532, 1, 530, 'procurement:approval-route:create',   '创建审批路由', 'API',       '/500/530/532/', 3, 2, 1, 'system'),
    (533, 1, 530, 'procurement:approval-route:update',   '更新审批路由', 'API',       '/500/530/533/', 3, 3, 1, 'system'),
    (534, 1, 530, 'procurement:approval-route:delete',   '删除审批路由', 'API',       '/500/530/534/', 3, 4, 1, 'system'),
    (540, 1, 500, 'procurement:rfq',                     '询价管理',     'MENU',      '/500/540/',     2, 5, 1, 'system'),
    (541, 1, 540, 'procurement:rfq:list',                '查看询价',     'API',       '/500/540/541/', 3, 1, 1, 'system'),
    (542, 1, 540, 'procurement:rfq:create',              '创建询价',     'API',       '/500/540/542/', 3, 2, 1, 'system'),
    (543, 1, 540, 'procurement:rfq:update',              '更新询价',     'API',       '/500/540/543/', 3, 3, 1, 'system'),
    (544, 1, 540, 'procurement:rfq:delete',              '删除询价',     'API',       '/500/540/544/', 3, 4, 1, 'system'),
    (545, 1, 540, 'procurement:rfq:send',                '发送询价',     'API',       '/500/540/545/', 3, 5, 1, 'system'),
    (546, 1, 540, 'procurement:rfq:award',               '询价定点',     'API',       '/500/540/546/', 3, 6, 1, 'system'),
    (547, 1, 540, 'procurement:rfq:cancel',              '取消询价',     'API',       '/500/540/547/', 3, 7, 1, 'system'),
    (550, 1, 500, 'procurement:purchase-order',          '采购订单',     'MENU',      '/500/550/',     2, 6, 1, 'system'),
    (551, 1, 550, 'procurement:purchase-order:list',     '查看采购订单', 'API',       '/500/550/551/', 3, 1, 1, 'system'),
    (553, 1, 550, 'procurement:purchase-order:update',   '更新采购订单', 'API',       '/500/550/553/', 3, 3, 1, 'system'),
    (554, 1, 550, 'procurement:purchase-order:delete',   '删除采购订单', 'API',       '/500/550/554/', 3, 4, 1, 'system'),
    (555, 1, 550, 'procurement:purchase-order:send',     '发送采购订单', 'API',       '/500/550/555/', 3, 5, 1, 'system'),
    (556, 1, 550, 'procurement:purchase-order:confirm',  '确认采购订单', 'API',       '/500/550/556/', 3, 6, 1, 'system'),
    (557, 1, 550, 'procurement:purchase-order:cancel',   '取消采购订单', 'API',       '/500/550/557/', 3, 7, 1, 'system'),
    (560, 1, 500, 'procurement:goods-receipt',           '采购收货',     'MENU',      '/500/560/',     2, 7, 1, 'system'),
    (561, 1, 560, 'procurement:goods-receipt:list',      '查看采购收货', 'API',       '/500/560/561/', 3, 1, 1, 'system'),
    (562, 1, 560, 'procurement:goods-receipt:create',    '创建采购收货', 'API',       '/500/560/562/', 3, 2, 1, 'system'),
    (563, 1, 560, 'procurement:goods-receipt:confirm',   '确认采购收货', 'API',       '/500/560/563/', 3, 3, 1, 'system');

-- 采购经理拥有 MVP 全部采购权限；SUPER_ADMIN 由模板规则获得全部权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.tenant_id = 1
  AND r.role_code IN ('SUPER_ADMIN', 'PROCUREMENT_MANAGER')
  AND (p.permission_code = 'procurement' OR p.permission_code LIKE 'procurement:%');

-- 采购经理承担请购审批时必须同时拥有 Workflow 待办与完成权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.tenant_id = 1
  AND r.role_code = 'PROCUREMENT_MANAGER'
  AND p.permission_code IN (
      'workflow', 'workflow:instance', 'workflow:task:todo',
      'workflow:approval:complete', 'workflow:model:list'
  );

-- 采购员可查看 SELF 概览、维护共享物料并完成采购执行；不授予审批路由和审批视图
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.tenant_id = 1
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

-- 普通员工可查看共享物料并在 SELF 范围内发起请购
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.tenant_id = 1
  AND r.role_code = 'EMPLOYEE'
  AND p.permission_code IN (
      'procurement', 'procurement:material', 'procurement:material:list',
      'procurement:requisition', 'procurement:requisition:list',
      'procurement:requisition:create', 'procurement:requisition:update',
      'procurement:requisition:delete', 'procurement:requisition:submit',
      'procurement:requisition:cancel'
  );

-- 组长和部门领导同时具备请购人能力与只读审批视图；真正完成审批仍校验 Workflow 任务归属
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.tenant_id = 1
  AND r.role_code IN ('TEAM_LEADER', 'DEPT_LEADER')
  AND p.permission_code IN (
      'procurement', 'procurement:material', 'procurement:material:list',
      'procurement:requisition', 'procurement:requisition:list',
      'procurement:requisition:create', 'procurement:requisition:update',
      'procurement:requisition:delete', 'procurement:requisition:submit',
      'procurement:requisition:approve', 'procurement:requisition:cancel'
  );

-- ============================================================
-- Section 10: CRM 服务 - 销售前闭环
-- ============================================================
CREATE DATABASE IF NOT EXISTS omni_crm
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE omni_crm;

-- 10.1 CRM 租户配置
CREATE TABLE IF NOT EXISTS crm_tenant_config (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id             BIGINT       NOT NULL COMMENT '租户ID',
    default_pipeline_id   BIGINT       DEFAULT NULL COMMENT '默认销售管道ID',
    currency_code         VARCHAR(10)  NOT NULL DEFAULT 'CNY' COMMENT '默认币种',
    lead_duplicate_policy VARCHAR(20)  NOT NULL DEFAULT 'WARN' COMMENT '线索重复策略',
    initialized_time      DATETIME     NOT NULL COMMENT '初始化时间',
    create_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by             VARCHAR(64)  DEFAULT NULL,
    update_by             VARCHAR(64)  DEFAULT NULL,
    UNIQUE KEY uk_crm_config_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM租户配置';

-- 10.2 销售管道及阶段
CREATE TABLE IF NOT EXISTS crm_pipeline (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL COMMENT '租户ID',
    code         VARCHAR(50)  NOT NULL COMMENT '稳定编码',
    name         VARCHAR(100) NOT NULL COMMENT '管道名称',
    status       TINYINT      NOT NULL DEFAULT 1,
    default_flag TINYINT      NOT NULL DEFAULT 0,
    sort         INT          NOT NULL DEFAULT 0,
    version      INT          NOT NULL DEFAULT 0,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by    VARCHAR(64)  DEFAULT NULL,
    update_by    VARCHAR(64)  DEFAULT NULL,
    UNIQUE KEY uk_crm_pipeline_code (tenant_id, code),
    INDEX idx_crm_pipeline_status (tenant_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM销售管道';

CREATE TABLE IF NOT EXISTS crm_pipeline_stage (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id    BIGINT        NOT NULL COMMENT '租户ID',
    pipeline_id  BIGINT        NOT NULL COMMENT '销售管道ID',
    stage_code   VARCHAR(50)   NOT NULL COMMENT '阶段编码',
    stage_name   VARCHAR(100)  NOT NULL COMMENT '阶段名称',
    stage_type   VARCHAR(20)   NOT NULL COMMENT 'OPEN/WON/LOST',
    probability  DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '成交概率百分比',
    sort         INT           NOT NULL DEFAULT 0,
    status       TINYINT       NOT NULL DEFAULT 1,
    deleted      TINYINT       NOT NULL DEFAULT 0,
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by    VARCHAR(64)   DEFAULT NULL,
    update_by    VARCHAR(64)   DEFAULT NULL,
    UNIQUE KEY uk_crm_stage_code (tenant_id, pipeline_id, stage_code),
    INDEX idx_crm_stage_sort (tenant_id, pipeline_id, status, deleted, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM销售管道阶段';

-- 10.3 线索与转换事实
CREATE TABLE IF NOT EXISTS crm_lead (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL COMMENT '租户ID',
    lead_no             VARCHAR(50)  NOT NULL COMMENT '线索编号',
    full_name           VARCHAR(100) NOT NULL COMMENT '姓名',
    company_name        VARCHAR(200) DEFAULT NULL COMMENT '公司名称',
    job_title           VARCHAR(100) DEFAULT NULL COMMENT '职位',
    mobile              VARCHAR(32)  DEFAULT NULL COMMENT '手机',
    phone               VARCHAR(32)  DEFAULT NULL COMMENT '电话',
    email               VARCHAR(200) DEFAULT NULL COMMENT '邮箱',
    region              VARCHAR(100) DEFAULT NULL COMMENT '地区',
    address             VARCHAR(500) DEFAULT NULL COMMENT '地址',
    source_code         VARCHAR(50)  DEFAULT NULL COMMENT '来源编码',
    industry_code       VARCHAR(50)  DEFAULT NULL COMMENT '行业编码',
    rating              VARCHAR(20)  DEFAULT NULL COMMENT '评级',
    status              VARCHAR(20)  NOT NULL COMMENT '生命周期状态',
    disqualify_reason   VARCHAR(500) DEFAULT NULL COMMENT '无效原因',
    owner_user_id       BIGINT       NOT NULL COMMENT '负责人用户ID',
    owner_unit_id       BIGINT       NOT NULL COMMENT '负责人组织ID',
    assigned_time       DATETIME     DEFAULT NULL,
    last_activity_time  DATETIME     DEFAULT NULL,
    next_followup_time  DATETIME     DEFAULT NULL,
    converted_time      DATETIME     DEFAULT NULL,
    version             INT          NOT NULL DEFAULT 0,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by           VARCHAR(64)  DEFAULT NULL,
    update_by           VARCHAR(64)  DEFAULT NULL,
    UNIQUE KEY uk_crm_lead_no (tenant_id, lead_no),
    INDEX idx_crm_lead_owner_status (tenant_id, owner_user_id, status, deleted),
    INDEX idx_crm_lead_unit_status (tenant_id, owner_unit_id, status, deleted),
    INDEX idx_crm_lead_followup (tenant_id, next_followup_time, status, deleted),
    INDEX idx_crm_lead_company (tenant_id, company_name),
    INDEX idx_crm_lead_mobile (tenant_id, mobile),
    INDEX idx_crm_lead_email (tenant_id, email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM线索';

CREATE TABLE IF NOT EXISTS crm_lead_conversion (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id             BIGINT      NOT NULL COMMENT '租户ID',
    lead_id               BIGINT      NOT NULL COMMENT '来源线索ID',
    customer_id           BIGINT      NOT NULL COMMENT '客户ID',
    contact_id            BIGINT      DEFAULT NULL COMMENT '联系人ID',
    opportunity_id        BIGINT      DEFAULT NULL COMMENT '商机ID',
    converted_by_user_id  BIGINT      NOT NULL COMMENT '转换用户ID',
    converted_time        DATETIME    NOT NULL COMMENT '转换时间',
    create_time           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by             VARCHAR(64) DEFAULT NULL,
    update_by             VARCHAR(64) DEFAULT NULL,
    UNIQUE KEY uk_crm_conversion_lead (tenant_id, lead_id),
    INDEX idx_crm_conversion_customer (tenant_id, customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM线索转换事实';

-- 10.4 客户与联系人
CREATE TABLE IF NOT EXISTS crm_customer (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id            BIGINT       NOT NULL COMMENT '租户ID',
    customer_no          VARCHAR(50)  NOT NULL COMMENT '客户编号',
    name                 VARCHAR(200) NOT NULL COMMENT '客户名称',
    normalized_name      VARCHAR(200) NOT NULL COMMENT '归一化名称',
    customer_type        VARCHAR(50)  DEFAULT NULL,
    industry_code        VARCHAR(50)  DEFAULT NULL,
    level_code           VARCHAR(50)  DEFAULT NULL,
    source_code          VARCHAR(50)  DEFAULT NULL,
    credit_code          VARCHAR(50)  DEFAULT NULL COMMENT '统一信用代码',
    website              VARCHAR(300) DEFAULT NULL,
    phone                VARCHAR(32)  DEFAULT NULL,
    email                VARCHAR(200) DEFAULT NULL,
    region               VARCHAR(100) DEFAULT NULL,
    address              VARCHAR(500) DEFAULT NULL,
    status               VARCHAR(20)  NOT NULL COMMENT '生命周期状态',
    owner_user_id        BIGINT       NOT NULL COMMENT '负责人用户ID',
    owner_unit_id        BIGINT       NOT NULL COMMENT '负责人组织ID',
    last_activity_time   DATETIME     DEFAULT NULL,
    next_followup_time   DATETIME     DEFAULT NULL,
    version              INT          NOT NULL DEFAULT 0,
    deleted              TINYINT      NOT NULL DEFAULT 0,
    create_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by            VARCHAR(64)  DEFAULT NULL,
    update_by            VARCHAR(64)  DEFAULT NULL,
    UNIQUE KEY uk_crm_customer_no (tenant_id, customer_no),
    INDEX idx_crm_customer_owner_status (tenant_id, owner_user_id, status, deleted),
    INDEX idx_crm_customer_unit_status (tenant_id, owner_unit_id, status, deleted),
    INDEX idx_crm_customer_name (tenant_id, normalized_name, deleted),
    INDEX idx_crm_customer_credit (tenant_id, credit_code),
    INDEX idx_crm_customer_phone (tenant_id, phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM客户';

CREATE TABLE IF NOT EXISTS crm_contact (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id             BIGINT       NOT NULL COMMENT '租户ID',
    customer_id           BIGINT       NOT NULL COMMENT '客户ID',
    name                  VARCHAR(100) NOT NULL COMMENT '姓名',
    department            VARCHAR(100) DEFAULT NULL,
    job_title             VARCHAR(100) DEFAULT NULL,
    mobile                VARCHAR(32)  DEFAULT NULL,
    phone                 VARCHAR(32)  DEFAULT NULL,
    email                 VARCHAR(200) DEFAULT NULL,
    decision_role         VARCHAR(50)  DEFAULT NULL,
    primary_flag          TINYINT      NOT NULL DEFAULT 0,
    status                TINYINT      NOT NULL DEFAULT 1,
    owner_user_id         BIGINT       NOT NULL COMMENT '负责人权限快照',
    owner_unit_id         BIGINT       NOT NULL COMMENT '组织权限快照',
    version               INT          NOT NULL DEFAULT 0,
    deleted               TINYINT      NOT NULL DEFAULT 0,
    primary_customer_guard BIGINT GENERATED ALWAYS AS (
        CASE WHEN primary_flag = 1 AND status = 1 AND deleted = 0 THEN customer_id ELSE NULL END
    ) STORED,
    create_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by             VARCHAR(64)  DEFAULT NULL,
    update_by             VARCHAR(64)  DEFAULT NULL,
    UNIQUE KEY uk_crm_contact_primary (tenant_id, primary_customer_guard),
    INDEX idx_crm_contact_customer (tenant_id, customer_id, status, deleted),
    INDEX idx_crm_contact_owner (tenant_id, owner_user_id, deleted),
    INDEX idx_crm_contact_mobile (tenant_id, mobile),
    INDEX idx_crm_contact_email (tenant_id, email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM客户联系人';

-- 10.5 商机及阶段历史
CREATE TABLE IF NOT EXISTS crm_opportunity (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id             BIGINT        NOT NULL COMMENT '租户ID',
    opportunity_no        VARCHAR(50)   NOT NULL COMMENT '商机编号',
    name                  VARCHAR(200)  NOT NULL COMMENT '商机名称',
    customer_id           BIGINT        NOT NULL COMMENT '客户ID',
    primary_contact_id    BIGINT        DEFAULT NULL,
    source_lead_id        BIGINT        DEFAULT NULL,
    pipeline_id           BIGINT        NOT NULL,
    stage_id              BIGINT        NOT NULL,
    status                VARCHAR(20)   NOT NULL COMMENT 'OPEN/WON/LOST',
    amount                DECIMAL(18,2) NOT NULL DEFAULT 0,
    currency_code         VARCHAR(10)   NOT NULL COMMENT '租户默认币种',
    probability           DECIMAL(5,2)  NOT NULL DEFAULT 0,
    expected_close_date   DATE          DEFAULT NULL,
    actual_close_time     DATETIME      DEFAULT NULL,
    loss_reason           VARCHAR(500)  DEFAULT NULL,
    stage_change_time     DATETIME      NOT NULL,
    next_followup_time    DATETIME      DEFAULT NULL,
    owner_user_id         BIGINT        NOT NULL,
    owner_unit_id         BIGINT        NOT NULL,
    version               INT           NOT NULL DEFAULT 0,
    deleted               TINYINT       NOT NULL DEFAULT 0,
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by             VARCHAR(64)   DEFAULT NULL,
    update_by             VARCHAR(64)   DEFAULT NULL,
    UNIQUE KEY uk_crm_opportunity_no (tenant_id, opportunity_no),
    INDEX idx_crm_opp_owner_status (tenant_id, owner_user_id, status, deleted),
    INDEX idx_crm_opp_unit_status (tenant_id, owner_unit_id, status, deleted),
    INDEX idx_crm_opp_stage (tenant_id, pipeline_id, stage_id, status, deleted),
    INDEX idx_crm_opp_customer (tenant_id, customer_id, status, deleted),
    INDEX idx_crm_opp_close_date (tenant_id, expected_close_date, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM商机';

CREATE TABLE IF NOT EXISTS crm_opportunity_stage_history (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL,
    opportunity_id      BIGINT       NOT NULL,
    from_stage_id       BIGINT       DEFAULT NULL,
    to_stage_id         BIGINT       NOT NULL,
    from_status         VARCHAR(20)  DEFAULT NULL,
    to_status           VARCHAR(20)  NOT NULL,
    change_reason       VARCHAR(500) DEFAULT NULL,
    changed_by_user_id  BIGINT       NOT NULL,
    changed_time        DATETIME     NOT NULL,
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by           VARCHAR(64)  DEFAULT NULL,
    update_by           VARCHAR(64)  DEFAULT NULL,
    INDEX idx_crm_opp_history (tenant_id, opportunity_id, changed_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM商机阶段历史';

-- 10.6 跟进活动与负责人变更历史
CREATE TABLE IF NOT EXISTS crm_activity (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id            BIGINT       NOT NULL,
    root_type            VARCHAR(20)  NOT NULL COMMENT 'LEAD/CUSTOMER/OPPORTUNITY',
    root_id              BIGINT       NOT NULL,
    contact_id           BIGINT       DEFAULT NULL,
    activity_type        VARCHAR(50)  NOT NULL,
    subject              VARCHAR(200) NOT NULL,
    content              TEXT         DEFAULT NULL COMMENT '纯文本跟进内容',
    status               VARCHAR(20)  NOT NULL,
    planned_start_time   DATETIME     DEFAULT NULL,
    planned_end_time     DATETIME     DEFAULT NULL,
    completed_time       DATETIME     DEFAULT NULL,
    next_action_time     DATETIME     DEFAULT NULL,
    performed_by_user_id BIGINT       DEFAULT NULL,
    owner_user_id        BIGINT       NOT NULL,
    owner_unit_id        BIGINT       NOT NULL,
    version              INT          NOT NULL DEFAULT 0,
    deleted              TINYINT      NOT NULL DEFAULT 0,
    create_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by            VARCHAR(64)  DEFAULT NULL,
    update_by            VARCHAR(64)  DEFAULT NULL,
    INDEX idx_crm_activity_root (tenant_id, root_type, root_id, status, deleted),
    INDEX idx_crm_activity_owner (tenant_id, owner_user_id, status, deleted),
    INDEX idx_crm_activity_plan (tenant_id, planned_start_time, status, deleted),
    INDEX idx_crm_activity_next (tenant_id, next_action_time, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM跟进活动';

CREATE TABLE IF NOT EXISTS crm_owner_change_log (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id          BIGINT       NOT NULL,
    entity_type        VARCHAR(30)  NOT NULL,
    entity_id          BIGINT       NOT NULL,
    old_owner_user_id  BIGINT       DEFAULT NULL,
    old_owner_unit_id  BIGINT       DEFAULT NULL,
    new_owner_user_id  BIGINT       NOT NULL,
    new_owner_unit_id  BIGINT       NOT NULL,
    operation_type     VARCHAR(30)  NOT NULL,
    reason             VARCHAR(500) DEFAULT NULL,
    operator_user_id   BIGINT       NOT NULL,
    operated_time      DATETIME     NOT NULL,
    create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by          VARCHAR(64)  DEFAULT NULL,
    update_by          VARCHAR(64)  DEFAULT NULL,
    INDEX idx_crm_owner_change_entity (tenant_id, entity_type, entity_id, operated_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM负责人变更历史';

-- 10.7 CRM 本地 Transactional Outbox
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
    UNIQUE KEY uk_crm_mq_msg_id (msg_id),
    INDEX idx_crm_mq_relay (status, next_retry_time),
    INDEX idx_crm_mq_tenant_time (tenant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM可靠消息发件箱';

-- 默认租户配置；其他租户由 CrmTenantInitializer 首次使用时幂等创建
INSERT IGNORE INTO crm_pipeline
    (id, tenant_id, code, name, status, default_flag, sort, version, deleted, create_by)
VALUES (1, 1, 'DEFAULT', '默认销售管道', 1, 1, 0, 0, 0, 'system');

INSERT IGNORE INTO crm_pipeline_stage
    (id, tenant_id, pipeline_id, stage_code, stage_name, stage_type, probability, sort, status, deleted, create_by)
VALUES
    (1, 1, 1, 'DISCOVERY',     '需求发现', 'OPEN', 10, 10, 1, 0, 'system'),
    (2, 1, 1, 'QUALIFICATION', '资格确认', 'OPEN', 30, 20, 1, 0, 'system'),
    (3, 1, 1, 'PROPOSAL',      '方案报价', 'OPEN', 50, 30, 1, 0, 'system'),
    (4, 1, 1, 'NEGOTIATION',   '商务谈判', 'OPEN', 80, 40, 1, 0, 'system'),
    (5, 1, 1, 'WON',           '赢单',     'WON', 100, 50, 1, 0, 'system'),
    (6, 1, 1, 'LOST',          '输单',     'LOST', 0, 60, 1, 0, 'system');

INSERT IGNORE INTO crm_tenant_config
    (id, tenant_id, default_pipeline_id, currency_code, lead_duplicate_policy, initialized_time, create_by)
VALUES (1, 1, 1, 'CNY', 'WARN', NOW(), 'system');

-- 10.8 CRM 样例数据（线索、客户、联系人、商机、阶段历史、跟进活动、Lead 转换）
-- 一键启动后用户可在前端看到完整的销售管道演示数据

-- 线索（crm_lead）- 8 条
INSERT IGNORE INTO crm_lead
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

-- 客户（crm_customer）- 5 条
INSERT IGNORE INTO crm_customer
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

-- 联系人（crm_contact）- 7 条
INSERT IGNORE INTO crm_contact
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

-- 商机（crm_opportunity）- 6 条
INSERT IGNORE INTO crm_opportunity
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

-- 商机阶段历史（crm_opportunity_stage_history）- 11 条
INSERT IGNORE INTO crm_opportunity_stage_history
    (id, tenant_id, opportunity_id, from_stage_id, to_stage_id, from_status, to_status,
     change_reason, changed_by_user_id, changed_time, create_by)
VALUES
(1, 1, 1, NULL, 2, NULL, 'QUALIFICATION', '新线索合格判定', 100, '2026-07-10 10:00:00', 'admin'),

(2, 1, 2, NULL, 2, NULL, 'QUALIFICATION', '初步接触后判定合格', 101, '2026-07-05 11:00:00', 'admin'),
(3, 1, 2, 2, 3, 'QUALIFICATION', 'PROPOSAL', '通过资格确认，进入方案阶段', 101, '2026-07-08 15:00:00', 'admin'),
(4, 1, 2, 3, 4, 'PROPOSAL', 'NEGOTIATION', '方案通过，进入商务谈判', 101, '2026-07-10 10:00:00', 'admin'),

(5, 1, 5, NULL, 1, NULL, 'DISCOVERY', 'Lead 转换自动创建', 103, '2026-07-06 09:00:00', 'admin'),
(6, 1, 5, 1, 2, 'DISCOVERY', 'QUALIFICATION', '需求明确', 107, '2026-07-06 14:00:00', 'admin'),
(7, 1, 5, 2, 3, 'QUALIFICATION', 'PROPOSAL', '提交方案', 107, '2026-07-07 10:00:00', 'admin'),
(8, 1, 5, 3, 4, 'PROPOSAL', 'NEGOTIATION', '方案通过', 107, '2026-07-08 11:00:00', 'admin'),
(9, 1, 5, 4, 5, 'NEGOTIATION', 'WON', '签约成功', 107, '2026-07-10 10:00:00', 'admin'),

(10, 1, 6, NULL, 3, NULL, 'PROPOSAL', '直接创建商机', 100, '2026-07-12 14:00:00', 'admin');

-- 跟进活动（crm_activity）- 12 条
INSERT IGNORE INTO crm_activity
    (id, tenant_id, root_type, root_id, contact_id, activity_type, subject, content,
     status, planned_start_time, planned_end_time, completed_time, next_action_time,
     performed_by_user_id,
     owner_user_id, owner_unit_id,
     version, deleted, create_by)
VALUES
(1, 1, 'LEAD', 1, 1, 'CALL', '首次电话沟通', '与客户CTO张三进行首次电话沟通，了解其ERP系统升级需求，预算约15万。',
 'COMPLETED', '2026-07-10 10:00:00', '2026-07-10 10:30:00', '2026-07-10 10:15:00', NULL,
 100,
 100, 101,
 0, 0, 'admin'),

(2, 1, 'LEAD', 1, 1, 'MEETING', '需求讨论会', '与星辰科技研发团队进行需求讨论，确认需要ERP系统升级和数据分析两个模块。',
 'COMPLETED', '2026-07-12 14:00:00', '2026-07-12 16:00:00', '2026-07-12 15:30:00', NULL,
 100,
 100, 101,
 0, 0, 'admin'),

(3, 1, 'LEAD', 2, 3, 'VISIT', '拜访客户', '拜访上海云帆网络，了解其SaaS平台定制需求，客户希望8月中旬上线。',
 'COMPLETED', '2026-07-09 11:00:00', '2026-07-09 12:30:00', '2026-07-09 12:00:00', NULL,
 101,
 101, 101,
 0, 0, 'admin'),

(4, 1, 'LEAD', 2, 3, 'CALL', '报价沟通', '电话沟通SaaS平台定制报价，客户反馍8月中旬需要上线。',
 'PLANNED', '2026-07-15 09:00:00', '2026-07-15 09:30:00', NULL, NULL,
 NULL,
 101, 101,
 0, 0, 'admin'),

(5, 1, 'LEAD', 3, 4, 'CALL', '初步接触', '与蓝海智能CPO王五初步接触，了解其AI客服系统需求。',
 'COMPLETED', '2026-07-11 16:00:00', '2026-07-11 16:30:00', '2026-07-11 16:20:00', NULL,
 102,
 102, 101,
 0, 0, 'admin'),

(6, 1, 'LEAD', 7, NULL, 'EMAIL', '发送产品手册', '向武汉青松医药发送产品手册和案例集。',
 'COMPLETED', '2026-07-08 15:00:00', '2026-07-08 15:10:00', '2026-07-08 15:05:00', NULL,
 100,
 100, 101,
 0, 0, 'admin'),

(7, 1, 'CUSTOMER', 1, 1, 'MEETING', '项目启动会', '星辰科技ERP系统项目启动会，确认项目范围和里程碑。',
 'COMPLETED', '2026-07-13 10:00:00', '2026-07-13 12:00:00', '2026-07-13 11:30:00', NULL,
 100,
 100, 101,
 0, 0, 'admin'),

(8, 1, 'CUSTOMER', 2, 3, 'VISIT', '现场调研', '到云帆网络现场调研SaaS平台技术架构。',
 'PLANNED', '2026-07-18 14:00:00', '2026-07-18 16:00:00', NULL, NULL,
 NULL,
 101, 101,
 0, 0, 'admin'),

(9, 1, 'CUSTOMER', 5, 6, 'CALL', '售后回访', '紫气文化会员营销平台上线后回访，客户使用反馈良好。',
 'COMPLETED', '2026-07-12 11:00:00', '2026-07-12 11:30:00', '2026-07-12 11:30:00', NULL,
 107,
 107, 200,
 0, 0, 'admin'),

(10, 1, 'OPPORTUNITY', 2, 3, 'MEETING', '方案评审', '云帆SaaS平台定制方案评审，客户认可技术方案。',
 'COMPLETED', '2026-07-10 10:00:00', '2026-07-10 12:00:00', '2026-07-10 12:00:00', NULL,
 101,
 101, 101,
 0, 0, 'admin'),

(11, 1, 'OPPORTUNITY', 6, 1, 'PROPOSAL', '提交方案', '向星辰科技提交数据分析平台方案。',
 'PLANNED', '2026-07-20 09:00:00', '2026-07-20 10:00:00', NULL, NULL,
 NULL,
 100, 101,
 0, 0, 'admin'),

(12, 1, 'LEAD', 4, NULL, 'CALL', '无效跟进', '与杭州绿竹教育沟通，客户表示预算不足，暂缓项目。',
 'CANCELLED', '2026-07-08 10:00:00', '2026-07-08 10:30:00', NULL, NULL,
 103,
 103, 102,
 0, 0, 'admin'),

(13, 1, 'CUSTOMER', 4, 5, 'CALL', '赢单回访', '红日电商订单中台项目赢单后回访，讨论实施计划和时间安排。',
 'COMPLETED', '2026-07-12 14:00:00', '2026-07-12 14:30:00', '2026-07-12 14:30:00', NULL,
 105,
 105, 100,
 0, 0, 'admin');

-- Lead 转换记录（crm_lead_conversion）- 5 条
INSERT IGNORE INTO crm_lead_conversion
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

-- Owner 变更记录（crm_owner_change_log）- 2 条
INSERT IGNORE INTO crm_owner_change_log
    (id, tenant_id, entity_type, entity_id, old_owner_user_id, old_owner_unit_id,
     new_owner_user_id, new_owner_unit_id, operation_type, reason,
     operator_user_id, operated_time, create_by)
VALUES
(1, 1, 'CUSTOMER', 3, 100, 101, 102, 101, 'TRANSFER', '客户归属调整到销售1组',
 100, '2026-06-15 10:00:00', 'admin'),

(2, 1, 'CUSTOMER', 4, 105, 100, 105, 100, 'TRANSFER', '客户重新分配',
 1, '2026-07-10 16:00:00', 'admin');

-- 10.9 CRM 字典数据（写入 omni_base.sys_dict_type + sys_dict_data）
USE omni_base;

INSERT IGNORE INTO sys_dict_type (tenant_id, type_code, type_name, remark, sort, status, create_by)
VALUES
    (1, 'crm_activity_type',   '跟进活动类型', 'CRM跟进活动的类型枚举',   30, 1, 'system'),
    (1, 'crm_activity_status', '活动状态',     'CRM跟进活动的状态枚举',   31, 1, 'system'),
    (1, 'crm_root_type',       '关联对象类型', 'CRM活动关联的业务对象类型', 32, 1, 'system');

INSERT IGNORE INTO sys_dict_data (tenant_id, type_code, dict_value, dict_label, tag_type, sort, status, create_by)
VALUES
    (1, 'crm_activity_type', 'CALL',     '电话',     'primary', 1, 1, 'system'),
    (1, 'crm_activity_type', 'VISIT',    '拜访',     'success', 2, 1, 'system'),
    (1, 'crm_activity_type', 'EMAIL',    '邮件',     'info',    3, 1, 'system'),
    (1, 'crm_activity_type', 'MEETING',  '会议',     'warning', 4, 1, 'system'),
    (1, 'crm_activity_type', 'PROPOSAL', '提交方案', 'primary', 5, 1, 'system'),
    (1, 'crm_activity_type', 'OTHER',    '其他',     'info',    6, 1, 'system'),
    (1, 'crm_activity_status', 'PLANNED',   '计划中', 'primary', 1, 1, 'system'),
    (1, 'crm_activity_status', 'COMPLETED', '已完成', 'success', 2, 1, 'system'),
    (1, 'crm_activity_status', 'CANCELLED', '已取消', 'info',    3, 1, 'system'),
    (1, 'crm_root_type', 'LEAD',        '线索',   'primary', 1, 1, 'system'),
    (1, 'crm_root_type', 'CUSTOMER',    '客户',   'success', 2, 1, 'system'),
    (1, 'crm_root_type', 'OPPORTUNITY', '商机',   'warning', 3, 1, 'system');

-- ============================================================
-- Section 11: 4级会签审批请假流程 - 种子数据
-- ============================================================
USE omni_auth;

-- 10.1 组织架构
INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (100, 1, 1, '销售部', 'DEPT', 'sales-dept', '/1/100/', 2, 1, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (101, 1, 100, '销售1组', 'TEAM', 'sales-team-1', '/1/100/101/', 3, 1, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (102, 1, 100, '销售2组', 'TEAM', 'sales-team-2', '/1/100/102/', 3, 2, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (200, 1, 1, '市场部', 'DEPT', 'marketing-dept', '/1/200/', 2, 2, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (300, 1, 1, '财务部', 'DEPT', 'finance-dept', '/1/300/', 2, 3, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (301, 1, 300, '会计组', 'TEAM', 'finance-accounting', '/1/300/301/', 3, 1, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (302, 1, 300, '审计组', 'TEAM', 'finance-audit', '/1/300/302/', 3, 2, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (303, 1, 300, '税务组', 'TEAM', 'finance-tax', '/1/300/303/', 3, 3, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (400, 1, 1, '技术部', 'DEPT', 'tech-dept', '/1/400/', 2, 4, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (401, 1, 400, '前端组', 'TEAM', 'tech-frontend', '/1/400/401/', 3, 1, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (402, 1, 400, '后端组', 'TEAM', 'tech-backend', '/1/400/402/', 3, 2, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (403, 1, 400, '测试组', 'TEAM', 'tech-qa', '/1/400/403/', 3, 3, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (404, 1, 400, '运维组', 'TEAM', 'tech-ops', '/1/400/404/', 3, 4, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (500, 1, 1, '法律部', 'DEPT', 'legal-dept', '/1/500/', 2, 5, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (501, 1, 500, '合规组', 'TEAM', 'legal-compliance', '/1/500/501/', 3, 1, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (502, 1, 500, '法务组', 'TEAM', 'legal-affairs', '/1/500/502/', 3, 2, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (600, 1, 1, '人事部', 'DEPT', 'hr-dept', '/1/600/', 2, 6, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (601, 1, 600, '招聘组', 'TEAM', 'hr-recruit', '/1/600/601/', 3, 1, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (602, 1, 600, '培训组', 'TEAM', 'hr-training', '/1/600/602/', 3, 2, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (603, 1, 600, '薪酬组', 'TEAM', 'hr-compensation', '/1/600/603/', 3, 3, 1, 'system');

-- 10.2 角色
INSERT IGNORE INTO sys_role (id, tenant_id, role_code, role_name, data_scope, sort, status, create_by)
VALUES (10, 1, 'EMPLOYEE', '普通员工', 'SELF', 10, 1, 'system');

INSERT IGNORE INTO sys_role (id, tenant_id, role_code, role_name, data_scope, sort, status, create_by)
VALUES (11, 1, 'TEAM_LEADER', '工作组组长', 'DEPT', 11, 1, 'system');

INSERT IGNORE INTO sys_role (id, tenant_id, role_code, role_name, data_scope, sort, status, create_by)
VALUES (12, 1, 'DEPT_LEADER', '部门领导', 'DEPT_AND_BELOW', 12, 1, 'system');

-- 10.3 用户（密码统一为 123456，BCrypt 编码）
INSERT IGNORE INTO sys_user (id, tenant_id, username, password, nickname, gender, primary_unit_id, status, create_by)
VALUES (100, 1, 'zhangsan', '$2b$10$b2rvxsi2LxHSZxzOuf4jOemAbYsmWGIWW/OhjzGBWWeg60lW/oLCa', '张三', 1, 101, 1, 'system');

INSERT IGNORE INTO sys_user (id, tenant_id, username, password, nickname, gender, primary_unit_id, status, create_by)
VALUES (101, 1, 'lisi', '$2b$10$b2rvxsi2LxHSZxzOuf4jOemAbYsmWGIWW/OhjzGBWWeg60lW/oLCa', '李四', 1, 101, 1, 'system');

INSERT IGNORE INTO sys_user (id, tenant_id, username, password, nickname, gender, primary_unit_id, status, create_by)
VALUES (102, 1, 'lisi2', '$2b$10$b2rvxsi2LxHSZxzOuf4jOemAbYsmWGIWW/OhjzGBWWeg60lW/oLCa', '李四2', 1, 101, 1, 'system');

INSERT IGNORE INTO sys_user (id, tenant_id, username, password, nickname, gender, primary_unit_id, status, create_by)
VALUES (103, 1, 'wangwu', '$2b$10$b2rvxsi2LxHSZxzOuf4jOemAbYsmWGIWW/OhjzGBWWeg60lW/oLCa', '王五', 1, 102, 1, 'system');

INSERT IGNORE INTO sys_user (id, tenant_id, username, password, nickname, gender, primary_unit_id, status, create_by)
VALUES (104, 1, 'wangwu2', '$2b$10$b2rvxsi2LxHSZxzOuf4jOemAbYsmWGIWW/OhjzGBWWeg60lW/oLCa', '王五2', 1, 102, 1, 'system');

INSERT IGNORE INTO sys_user (id, tenant_id, username, password, nickname, gender, primary_unit_id, status, create_by)
VALUES (105, 1, 'zhaoliu', '$2b$10$b2rvxsi2LxHSZxzOuf4jOemAbYsmWGIWW/OhjzGBWWeg60lW/oLCa', '赵六', 1, 100, 1, 'system');

INSERT IGNORE INTO sys_user (id, tenant_id, username, password, nickname, gender, primary_unit_id, status, create_by)
VALUES (106, 1, 'zhaoliu2', '$2b$10$b2rvxsi2LxHSZxzOuf4jOemAbYsmWGIWW/OhjzGBWWeg60lW/oLCa', '赵六2', 1, 100, 1, 'system');

INSERT IGNORE INTO sys_user (id, tenant_id, username, password, nickname, gender, primary_unit_id, status, create_by)
VALUES (107, 1, 'qianqi', '$2b$10$b2rvxsi2LxHSZxzOuf4jOemAbYsmWGIWW/OhjzGBWWeg60lW/oLCa', '钱七', 1, 200, 1, 'system');

INSERT IGNORE INTO sys_user (id, tenant_id, username, password, nickname, gender, primary_unit_id, status, create_by)
VALUES (108, 1, 'qianqi2', '$2b$10$b2rvxsi2LxHSZxzOuf4jOemAbYsmWGIWW/OhjzGBWWeg60lW/oLCa', '钱七2', 1, 200, 1, 'system');

-- 10.4 用户角色作用域（sys_user_role_scope）
INSERT IGNORE INTO sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status) VALUES (1, 100, 10, 101, 'SAME_UNIT', 1);
INSERT IGNORE INTO sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status) VALUES (1, 101, 11, 101, 'SAME_UNIT', 1);
INSERT IGNORE INTO sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status) VALUES (1, 102, 11, 101, 'SAME_UNIT', 1);
INSERT IGNORE INTO sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status) VALUES (1, 103, 11, 102, 'SAME_UNIT', 1);
INSERT IGNORE INTO sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status) VALUES (1, 104, 11, 102, 'SAME_UNIT', 1);
INSERT IGNORE INTO sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status) VALUES (1, 105, 12, 100, 'SAME_UNIT', 1);
INSERT IGNORE INTO sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status) VALUES (1, 106, 12, 100, 'SAME_UNIT', 1);
INSERT IGNORE INTO sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status) VALUES (1, 107, 12, 200, 'SAME_UNIT', 1);
INSERT IGNORE INTO sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status) VALUES (1, 108, 12, 200, 'SAME_UNIT', 1);

-- 10.5 用户角色关联（sys_user_role）
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (100, 10);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (101, 11);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (102, 11);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (103, 11);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (104, 11);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (105, 12);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (106, 12);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (107, 12);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (108, 12);

-- 10.6 用户组织关联（sys_user_unit）
INSERT IGNORE INTO sys_user_unit (user_id, unit_id, is_primary) VALUES (100, 101, 1);
INSERT IGNORE INTO sys_user_unit (user_id, unit_id, is_primary) VALUES (101, 101, 1);
INSERT IGNORE INTO sys_user_unit (user_id, unit_id, is_primary) VALUES (102, 101, 1);
INSERT IGNORE INTO sys_user_unit (user_id, unit_id, is_primary) VALUES (103, 102, 1);
INSERT IGNORE INTO sys_user_unit (user_id, unit_id, is_primary) VALUES (104, 102, 1);
INSERT IGNORE INTO sys_user_unit (user_id, unit_id, is_primary) VALUES (105, 100, 1);
INSERT IGNORE INTO sys_user_unit (user_id, unit_id, is_primary) VALUES (106, 100, 1);
INSERT IGNORE INTO sys_user_unit (user_id, unit_id, is_primary) VALUES (107, 200, 1);
INSERT IGNORE INTO sys_user_unit (user_id, unit_id, is_primary) VALUES (108, 200, 1);

-- ============================================================
-- Section 11: 租户初始化存储过程
-- 创建新租户后调用 CALL sp_init_tenant(newTenantId, 'tenantName', 'adminPassword')
-- 自动克隆权限树、创建默认角色、根组织、管理员账号、XSS 配置
-- ============================================================
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
          'srm:risk:list','srm:risk:update','srm:risk:assess','srm:risk:config','srm:risk:config:list','srm:risk:config:update',
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

-- ============================================================
-- Section 12: SRM 服务 — 供应商关系管理
-- ============================================================
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
    workflow_request_id   VARCHAR(64)  DEFAULT NULL COMMENT 'Workflow启动请求幂等键',
    workflow_business_key VARCHAR(128) DEFAULT NULL COMMENT 'Workflow业务键 格式supplierId:attempt',
    workflow_model_version_id BIGINT   DEFAULT NULL COMMENT '本轮选定的已发布流程模型版本ID',
    process_instance_id   VARCHAR(64)  DEFAULT NULL COMMENT 'Workflow流程实例ID',
    workflow_start_status VARCHAR(20)  DEFAULT 'NOT_STARTED' COMMENT 'NOT_STARTED/PENDING/FAILED/STARTED',
    workflow_completed_time DATETIME   DEFAULT NULL COMMENT 'Workflow审批完成时间',
    approval_attempt      INT          NOT NULL DEFAULT 0 COMMENT '当前审批轮次',
    approved_time         DATETIME     DEFAULT NULL COMMENT '审批通过时间',
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

-- 12.12 风险指标类型（动态维护，替代硬编码枚举）
CREATE TABLE IF NOT EXISTS srm_risk_indicator_type (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL,
    type_code   VARCHAR(50)  NOT NULL COMMENT '指标编码，如 FINANCIAL',
    type_name   VARCHAR(100) NOT NULL COMMENT '指标名称，如 财务风险',
    description VARCHAR(500) DEFAULT NULL COMMENT '指标说明',
    sort        INT          DEFAULT 0 COMMENT '排序',
    auto_calc   TINYINT      DEFAULT 0 COMMENT '是否自动计算：0=手动 1=自动(如CERTIFICATE)',
    status      TINYINT      DEFAULT 1 COMMENT '1=启用 0=禁用',
    deleted     TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by   VARCHAR(64)  DEFAULT NULL,
    update_by   VARCHAR(64)  DEFAULT NULL,
    UNIQUE KEY uk_risk_type_tenant_code (tenant_id, type_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SRM风险指标类型';

-- 12.12.1 评分标准（每个指标类型的预设评分选项）
CREATE TABLE IF NOT EXISTS srm_risk_criterion (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id         BIGINT       NOT NULL,
    indicator_type_id BIGINT       NOT NULL COMMENT '关联指标类型',
    criterion_label   VARCHAR(200) NOT NULL COMMENT '评分标准描述',
    score             INT          NOT NULL COMMENT '分值（越高越危险）',
    risk_level        VARCHAR(20)  NOT NULL COMMENT '对应风险等级 GREEN/YELLOW/RED',
    sort              INT          DEFAULT 0,
    status            TINYINT      DEFAULT 1 COMMENT '1=启用 0=禁用',
    deleted           TINYINT      NOT NULL DEFAULT 0,
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by         VARCHAR(64)  DEFAULT NULL,
    update_by         VARCHAR(64)  DEFAULT NULL,
    INDEX idx_risk_criterion_type (indicator_type_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SRM风险评分标准';

-- 12.12.2 得分-风险等级映射
CREATE TABLE IF NOT EXISTS srm_risk_score_threshold (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT      NOT NULL,
    risk_level  VARCHAR(20) NOT NULL COMMENT 'GREEN/YELLOW/RED',
    min_score   INT         NOT NULL COMMENT '最小分（含）',
    max_score   INT         NOT NULL COMMENT '最大分（含）',
    deleted     TINYINT     NOT NULL DEFAULT 0,
    create_time DATETIME    DEFAULT NULL,
    update_time DATETIME    DEFAULT NULL,
    create_by   VARCHAR(50) DEFAULT NULL,
    update_by   VARCHAR(50) DEFAULT NULL,
    UNIQUE KEY uk_risk_threshold_level (tenant_id, risk_level, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SRM风险得分阈值';

-- 12.12.3 风险指标（每个供应商的实际指标值）
CREATE TABLE IF NOT EXISTS srm_risk_indicator (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    supplier_id     BIGINT       NOT NULL,
    indicator_type  VARCHAR(50)  NOT NULL COMMENT '指标类型编码（关联 srm_risk_indicator_type.type_code）',
    indicator_value VARCHAR(200) DEFAULT NULL COMMENT '指标值（评分标准描述文本）',
    criterion_id    BIGINT       DEFAULT NULL COMMENT '选中的评分标准 ID',
    score           INT          DEFAULT NULL COMMENT '该指标的得分',
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

-- 12.18 SRM领域事件收件箱
CREATE TABLE IF NOT EXISTS srm_event_inbox (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    event_id        VARCHAR(64) NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    source_service  VARCHAR(64) NOT NULL,
    aggregate_type  VARCHAR(64) DEFAULT NULL,
    aggregate_id    VARCHAR(128) DEFAULT NULL,
    payload         JSON NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    processed_time  DATETIME DEFAULT NULL,
    error_message   VARCHAR(500) DEFAULT NULL,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_srm_inbox_event (tenant_id, event_id),
    INDEX idx_srm_inbox_status (tenant_id, status, create_time),
    CONSTRAINT chk_srm_inbox_status CHECK (status IN ('RECEIVED','PROCESSED','IGNORED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SRM领域事件收件箱';

-- 默认评估模板（质量30%/交期30%/价格20%/服务20%）
INSERT IGNORE INTO srm_evaluation_template (id, tenant_id, name, status, default_flag, version, deleted, create_by)
VALUES (1, 1, '默认供应商评估模板', 1, 1, 0, 0, 'system');

INSERT IGNORE INTO srm_evaluation_dimension (id, tenant_id, template_id, indicator_name, weight, sort, status, deleted, create_by)
VALUES
    (1, 1, 1, '质量', 30.00, 1, 1, 0, 'system'),
    (2, 1, 1, '交期', 30.00, 2, 1, 0, 'system'),
    (3, 1, 1, '价格', 20.00, 3, 1, 0, 'system'),
    (4, 1, 1, '服务', 20.00, 4, 1, 0, 'system');

-- ============================================================
-- 13. 供应商门户样例数据
-- ============================================================

-- 13.1 供应商门户用户（切回 omni_auth）
USE omni_auth;

INSERT IGNORE INTO sys_user (id, tenant_id, username, password, nickname, email, phone, gender, primary_unit_id, status, create_by)
VALUES (200, 1, 'supplier1', '$2b$10$TWIuwQVfxgsioXe/2O9cgOXtuZwOREr1IBkgTj2.nhA1NSlnad1oa', '王建国', 'wangjianguo@huaxin-precision.com', '13912345678', 1, 1, 1, 'system');

-- 13.2 供应商角色绑定（仅 SUPPLIER，不绑定 USER 避免暴露内部管理菜单）
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (200, 33);

-- 13.3 用户组织映射
INSERT IGNORE INTO sys_user_unit (user_id, unit_id, is_primary) VALUES (200, 1, 1);

-- 13.4 供应商业务数据（切回 omni_srm）
USE omni_srm;

-- 供应商企业
INSERT IGNORE INTO srm_supplier (id, tenant_id, supplier_no, name, normalized_name, supplier_type, industry_code, credit_code, website, phone, email, region, address, category_code, level_code, status, assigned_time, last_evaluation_time, owner_user_id, owner_unit_id, version, deleted, create_by)
VALUES (1, 1, 'SUP-2026-001', '华信精密制造有限公司', '华信精密制造有限公司', 'MANUFACTURER', 'C39', '91320500MA1EXAMPLE', 'https://www.huaxin-precision.com', '0512-66881234', 'contact@huaxin-precision.com', '江苏省苏州市', '苏州工业园区星湖街328号创新产业园A栋', 'ELECTRONICS', 'STRATEGIC', 'APPROVED', '2026-01-01 09:00:00', '2026-01-15 10:00:00', 1, 1, 0, 0, 'system');

-- 供应商联系人
INSERT IGNORE INTO srm_supplier_contact (id, tenant_id, supplier_id, name, department, job_title, mobile, phone, email, decision_role, primary_flag, status, version, deleted, create_by)
VALUES
    (1, 1, 1, '王建国', '销售部', '销售总监', '13912345678', '0512-66881234', 'wangjianguo@huaxin-precision.com', 'DECISION_MAKER', 1, 1, 0, 0, 'system'),
    (2, 1, 1, '李芳', '质量部', '质量经理', '13856781234', '0512-66885678', 'lifang@huaxin-precision.com', 'INFLUENCER', 0, 1, 0, 0, 'system'),
    (3, 1, 1, '赵明', '物流部', '物流主管', '13765432100', '0512-66889012', 'zhaoming@huaxin-precision.com', 'USER', 0, 1, 0, 0, 'system');

-- 供应商资质
INSERT IGNORE INTO srm_supplier_qualification (id, tenant_id, supplier_id, qualification_name, certificate_no, issuing_authority, issue_date, expiry_date, status, version, deleted, create_by)
VALUES
    (1, 1, 1, 'ISO 9001:2015 质量管理体系', 'CN24/30001', '中国质量认证中心(CQC)', '2024-03-15', '2027-03-14', 'ACTIVE', 0, 0, 'system'),
    (2, 1, 1, 'ISO 14001:2015 环境管理体系', 'CN24/E0128', '中国质量认证中心(CQC)', '2024-06-01', '2027-05-31', 'ACTIVE', 0, 0, 'system'),
    (3, 1, 1, 'IATF 16949:2016 汽车行业质量管理体系', 'IATF-2025-0456', 'SGS', '2025-01-10', '2028-01-09', 'ACTIVE', 0, 0, 'system');

-- 供应商银行账户
INSERT IGNORE INTO srm_supplier_bank_account (id, tenant_id, supplier_id, account_name, account_no, bank_name, bank_branch, bank_code, primary_flag, status, version, deleted, create_by)
VALUES
    (1, 1, 1, '华信精密制造有限公司', '6228480322123456789', '中国工商银行', '苏州工业园区支行', 'ICBC', 1, 1, 0, 0, 'system'),
    (2, 1, 1, '华信精密制造有限公司', '5129021234567890', '招商银行', '苏州分行', 'CMB', 0, 1, 0, 0, 'system');

-- 门户用户关联
INSERT IGNORE INTO srm_supplier_portal_user (tenant_id, supplier_id, user_id, create_by)
VALUES (1, 1, 200, 'system');

-- 门户入驻记录（已审批通过）
INSERT IGNORE INTO srm_supplier_enrollment (id, tenant_id, supplier_id, user_id, request_id, status, create_by)
VALUES (1, 1, 1, 200, 'enroll-huaxin-20260101', 'COMPLETED', 'system');

-- 绩效评估（2025 Q4）
INSERT IGNORE INTO srm_evaluation (id, tenant_id, supplier_id, template_id, evaluation_period, total_score, evaluator_user_id, evaluation_time, status, owner_user_id, owner_unit_id, version, deleted, create_by)
VALUES (1, 1, 1, 1, '2025-Q4', 85.00, 1, '2026-01-15 10:00:00', 'COMPLETED', 1, 1, 0, 0, 'system');

INSERT IGNORE INTO srm_evaluation_item (tenant_id, evaluation_id, dimension_id, indicator_name, score, weight, create_by)
VALUES
    (1, 1, 1, '质量', 4.5, 30.00, 'system'),
    (1, 1, 2, '交期', 4.0, 30.00, 'system'),
    (1, 1, 3, '价格', 4.0, 20.00, 'system'),
    (1, 1, 4, '服务', 4.5, 20.00, 'system');

-- 供应商邀请（示例邀请码 hash，有效期 7 天，最大使用 1 次）
INSERT IGNORE INTO srm_supplier_invite (id, tenant_id, invite_code_hash, status, expires_time, max_uses, used_count, version, deleted, create_by)
VALUES (1, 1, 'a697e214bda1f51dae035be089555d92ddd9780862096747de5f21890a896abb', 'ACTIVE', DATE_ADD(NOW(), INTERVAL 7 DAY), 1, 0, 0, 0, 'system');

-- 风险评估
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

-- 风险指标类型种子数据
INSERT IGNORE INTO srm_risk_indicator_type (tenant_id, type_code, type_name, description, sort, auto_calc, status, deleted, create_by)
VALUES
    (1, 'FINANCIAL',   '财务风险',   '评估供应商的财务健康状况、偿债能力和资金链稳定性', 1, 0, 1, 0, 'system'),
    (1, 'COMPLIANCE',  '合规风险',   '评估供应商的法规遵从、合同履约和信用记录',         2, 0, 1, 0, 'system'),
    (1, 'SUPPLY',      '供应风险',   '评估供应商的交付能力、产能和供应链稳定性',         3, 0, 1, 0, 'system'),
    (1, 'COOPERATION', '合作风险',   '评估供应商的沟通响应、配合度和服务态度',           4, 0, 1, 0, 'system'),
    (1, 'QUALITY',     '质量风险',   '评估供应商的产品质量、来料合格率和质量体系',       5, 0, 1, 0, 'system'),
    (1, 'CERTIFICATE', '资质证书风险', '根据资质到期日自动计算，无需手动评估',             6, 1, 1, 0, 'system');

-- 评分标准种子数据（每个手动指标 3 条：GREEN=1分, YELLOW=2分, RED=3分）
INSERT IGNORE INTO srm_risk_criterion (tenant_id, indicator_type_id, criterion_label, score, risk_level, sort, status, deleted, create_by)
SELECT 1, t.id, c.criterion_label, c.score, c.risk_level, c.sort, 1, 0, 'system'
FROM srm_risk_indicator_type t
JOIN (
    SELECT 'FINANCIAL' tc, '流动比率>2，资金充裕' cl, 1 s, 'GREEN' rl, 1 so
    UNION ALL SELECT 'FINANCIAL', '流动比率1~2，资金偏紧', 2, 'YELLOW', 2
    UNION ALL SELECT 'FINANCIAL', '流动比率<1，资金链紧张', 3, 'RED', 3
    UNION ALL SELECT 'COMPLIANCE', '无违规记录，信用良好', 1, 'GREEN', 4
    UNION ALL SELECT 'COMPLIANCE', '存在轻微违规或合同纠纷', 2, 'YELLOW', 5
    UNION ALL SELECT 'COMPLIANCE', '存在重大违规或诉讼', 3, 'RED', 6
    UNION ALL SELECT 'SUPPLY', '准时交付率>95%，产能充足', 1, 'GREEN', 7
    UNION ALL SELECT 'SUPPLY', '准时交付率80~95%，产能偏紧', 2, 'YELLOW', 8
    UNION ALL SELECT 'SUPPLY', '准时交付率<80%，供应不稳定', 3, 'RED', 9
    UNION ALL SELECT 'COOPERATION', '响应及时，沟通顺畅', 1, 'GREEN', 10
    UNION ALL SELECT 'COOPERATION', '响应偶有延迟，需催促', 2, 'YELLOW', 11
    UNION ALL SELECT 'COOPERATION', '响应严重滞后，配合度差', 3, 'RED', 12
    UNION ALL SELECT 'QUALITY', '来料合格率>98%，质量体系完善', 1, 'GREEN', 13
    UNION ALL SELECT 'QUALITY', '来料合格率90~98%，偶有质量问题', 2, 'YELLOW', 14
    UNION ALL SELECT 'QUALITY', '来料合格率<90%，质量风险高', 3, 'RED', 15
) c ON t.type_code = c.tc
WHERE t.tenant_id = 1 AND t.deleted = 0;

-- 得分阈值种子数据（5个手动指标，总分5~15）
INSERT IGNORE INTO srm_risk_score_threshold (tenant_id, risk_level, min_score, max_score, deleted)
VALUES
    (1, 'GREEN',  5,  8,  0),
    (1, 'YELLOW', 9,  12, 0),
    (1, 'RED',    13, 15, 0);

-- ============================================================
-- Section 14: Procurement 服务 — 采购管理首批基础
-- ============================================================
CREATE DATABASE IF NOT EXISTS omni_procurement
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE omni_procurement;

-- 14.1 租户级采购配置
CREATE TABLE IF NOT EXISTS proc_tenant_config (
    id                  BIGINT       AUTO_INCREMENT PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL,
    currency_code       CHAR(3)      NOT NULL DEFAULT 'CNY',
    initialized_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version             INT          NOT NULL DEFAULT 0,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    active_tenant_guard TINYINT GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 THEN 1 ELSE NULL END
    ) STORED,
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by           VARCHAR(64)  DEFAULT NULL,
    update_by           VARCHAR(64)  DEFAULT NULL,
    UNIQUE KEY uk_proc_config_active_tenant (tenant_id, active_tenant_guard),
    CONSTRAINT chk_proc_config_currency CHECK (currency_code REGEXP '^[A-Z]{3}$'),
    CONSTRAINT chk_proc_config_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购租户配置';

-- 14.2 两级物料品类
CREATE TABLE IF NOT EXISTS proc_material_category (
    id                         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    tenant_id                  BIGINT       NOT NULL,
    parent_id                  BIGINT       NOT NULL DEFAULT 0,
    category_code              VARCHAR(50)  NOT NULL,
    category_name              VARCHAR(100) NOT NULL,
    sort                       INT          NOT NULL DEFAULT 0,
    status                     TINYINT      NOT NULL DEFAULT 1,
    version                    INT          NOT NULL DEFAULT 0,
    deleted                    TINYINT      NOT NULL DEFAULT 0,
    active_category_code_guard VARCHAR(50) GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 THEN category_code ELSE NULL END
    ) STORED,
    create_time                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time                DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by                  VARCHAR(64)  DEFAULT NULL,
    update_by                  VARCHAR(64)  DEFAULT NULL,
    UNIQUE KEY uk_proc_category_active_code (tenant_id, active_category_code_guard),
    INDEX idx_proc_category_parent (tenant_id, parent_id, status, deleted),
    CONSTRAINT chk_proc_category_parent CHECK (parent_id >= 0),
    CONSTRAINT chk_proc_category_sort CHECK (sort >= 0),
    CONSTRAINT chk_proc_category_status CHECK (status IN (0, 1)),
    CONSTRAINT chk_proc_category_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购物料品类';

-- 14.3 物料目录
CREATE TABLE IF NOT EXISTS proc_material (
    id                         BIGINT        AUTO_INCREMENT PRIMARY KEY,
    tenant_id                  BIGINT        NOT NULL,
    category_id                BIGINT        NOT NULL,
    material_code              VARCHAR(64)   NOT NULL,
    material_name              VARCHAR(200)  NOT NULL,
    specification              VARCHAR(500)  DEFAULT NULL,
    unit                       VARCHAR(32)    NOT NULL,
    asset_managed              TINYINT       NOT NULL DEFAULT 0,
    status                     VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    version                    INT           NOT NULL DEFAULT 0,
    deleted                    TINYINT       NOT NULL DEFAULT 0,
    active_material_code_guard VARCHAR(64) GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 THEN material_code ELSE NULL END
    ) STORED,
    create_time                DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time                DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by                  VARCHAR(64)   DEFAULT NULL,
    update_by                  VARCHAR(64)   DEFAULT NULL,
    UNIQUE KEY uk_proc_material_active_code (tenant_id, active_material_code_guard),
    INDEX idx_proc_material_category (tenant_id, category_id, status, deleted),
    INDEX idx_proc_material_name (tenant_id, material_name, deleted),
    CONSTRAINT chk_proc_material_asset CHECK (asset_managed IN (0, 1)),
    CONSTRAINT chk_proc_material_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_proc_material_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购物料目录';

-- 14.4 请购审批路由
CREATE TABLE IF NOT EXISTS proc_approval_route (
    id                      BIGINT        AUTO_INCREMENT PRIMARY KEY,
    tenant_id               BIGINT        NOT NULL,
    route_code              VARCHAR(64)   NOT NULL,
    category_code           VARCHAR(50)   NOT NULL,
    min_amount              DECIMAL(19,4) NOT NULL DEFAULT 0,
    max_amount              DECIMAL(19,4) DEFAULT NULL,
    model_version_id        BIGINT        NOT NULL,
    priority                INT           NOT NULL DEFAULT 0,
    status                  VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    version                 INT           NOT NULL DEFAULT 0,
    deleted                 TINYINT       NOT NULL DEFAULT 0,
    active_route_code_guard VARCHAR(64) GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 THEN route_code ELSE NULL END
    ) STORED,
    create_time             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time             DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by               VARCHAR(64)   DEFAULT NULL,
    update_by               VARCHAR(64)   DEFAULT NULL,
    UNIQUE KEY uk_proc_route_active_code (tenant_id, active_route_code_guard),
    INDEX idx_proc_route_match (tenant_id, category_code, status, min_amount, max_amount, deleted),
    CONSTRAINT chk_proc_route_amount CHECK (
        min_amount >= 0 AND (max_amount IS NULL OR max_amount > min_amount)
    ),
    CONSTRAINT chk_proc_route_priority CHECK (priority >= 0),
    CONSTRAINT chk_proc_route_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_proc_route_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='请购审批路由';

-- 14.5 请购单聚合根
CREATE TABLE IF NOT EXISTS proc_requisition (
    id                        BIGINT        AUTO_INCREMENT PRIMARY KEY,
    tenant_id                 BIGINT        NOT NULL,
    requisition_no            VARCHAR(64)   NOT NULL,
    title                     VARCHAR(200)  NOT NULL,
    requester_user_id         BIGINT        NOT NULL,
    requester_unit_id         BIGINT        NOT NULL,
    reason                    VARCHAR(1000) DEFAULT NULL,
    primary_category_code     VARCHAR(50)   NOT NULL,
    total_amount              DECIMAL(19,4) NOT NULL DEFAULT 0,
    currency_code             CHAR(3)       NOT NULL DEFAULT 'CNY',
    status                    VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    approval_attempt          INT           NOT NULL DEFAULT 0,
    workflow_request_id       VARCHAR(64)   DEFAULT NULL,
    workflow_business_key     VARCHAR(128)  DEFAULT NULL,
    workflow_model_version_id BIGINT        DEFAULT NULL,
    process_instance_id       VARCHAR(64)   DEFAULT NULL,
    workflow_start_status     VARCHAR(20)   NOT NULL DEFAULT 'NOT_STARTED',
    approved_time             DATETIME      DEFAULT NULL,
    workflow_completed_time   DATETIME      DEFAULT NULL,
    owner_user_id             BIGINT        NOT NULL,
    owner_unit_id             BIGINT        NOT NULL,
    version                   INT           NOT NULL DEFAULT 0,
    deleted                   TINYINT       NOT NULL DEFAULT 0,
    create_time               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time               DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by                 VARCHAR(64)   DEFAULT NULL,
    update_by                 VARCHAR(64)   DEFAULT NULL,
    UNIQUE KEY uk_proc_req_no (tenant_id, requisition_no),
    UNIQUE KEY uk_proc_req_workflow_request (tenant_id, workflow_request_id),
    UNIQUE KEY uk_proc_req_workflow_business (tenant_id, workflow_business_key),
    INDEX idx_proc_req_requester_status (tenant_id, requester_user_id, status, deleted),
    INDEX idx_proc_req_unit_status (tenant_id, requester_unit_id, status, deleted),
    INDEX idx_proc_req_owner (tenant_id, owner_user_id, owner_unit_id, deleted),
    INDEX idx_proc_req_process_instance (tenant_id, process_instance_id),
    CONSTRAINT chk_proc_req_total CHECK (total_amount >= 0),
    CONSTRAINT chk_proc_req_currency CHECK (currency_code REGEXP '^[A-Z]{3}$'),
    CONSTRAINT chk_proc_req_status CHECK (
        status IN ('DRAFT','SUBMITTED','APPROVING','APPROVED','REJECTED','CANCELLED')
    ),
    CONSTRAINT chk_proc_req_workflow_start CHECK (
        workflow_start_status IN ('NOT_STARTED','PENDING','FAILED','STARTED')
    ),
    CONSTRAINT chk_proc_req_attempt CHECK (approval_attempt >= 0),
    CONSTRAINT chk_proc_req_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购请购单';

-- 14.6 请购明细及物料历史快照
CREATE TABLE IF NOT EXISTS proc_requisition_line (
    id                         BIGINT        AUTO_INCREMENT PRIMARY KEY,
    tenant_id                  BIGINT        NOT NULL,
    requisition_id             BIGINT        NOT NULL,
    line_no                    INT           NOT NULL,
    material_id                BIGINT        NOT NULL,
    material_code              VARCHAR(64)   NOT NULL,
    material_name              VARCHAR(200)  NOT NULL,
    category_code              VARCHAR(50)   NOT NULL,
    unit                       VARCHAR(32)    NOT NULL,
    quantity                   DECIMAL(19,6) NOT NULL,
    estimated_unit_price       DECIMAL(19,6) NOT NULL,
    estimated_total_price      DECIMAL(19,4) NOT NULL,
    remark                     VARCHAR(500)  DEFAULT NULL,
    version                    INT           NOT NULL DEFAULT 0,
    deleted                    TINYINT       NOT NULL DEFAULT 0,
    active_line_no_guard       INT GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 THEN line_no ELSE NULL END
    ) STORED,
    create_time                DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time                DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by                  VARCHAR(64)   DEFAULT NULL,
    update_by                  VARCHAR(64)   DEFAULT NULL,
    UNIQUE KEY uk_proc_req_line_active (tenant_id, requisition_id, active_line_no_guard),
    INDEX idx_proc_req_line_req (tenant_id, requisition_id, deleted),
    INDEX idx_proc_req_line_material (tenant_id, material_id, deleted),
    CONSTRAINT chk_proc_req_line_no CHECK (line_no > 0),
    CONSTRAINT chk_proc_req_line_quantity CHECK (quantity > 0),
    CONSTRAINT chk_proc_req_line_price CHECK (estimated_unit_price >= 0),
    CONSTRAINT chk_proc_req_line_total CHECK (estimated_total_price >= 0),
    CONSTRAINT chk_proc_req_line_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购请购明细';

-- 14.7 询价单聚合根
CREATE TABLE IF NOT EXISTS proc_rfq (
    id                        BIGINT       AUTO_INCREMENT PRIMARY KEY,
    tenant_id                 BIGINT       NOT NULL,
    rfq_no                    VARCHAR(64)  NOT NULL,
    requisition_id            BIGINT       NOT NULL,
    title                     VARCHAR(200) NOT NULL,
    quotation_deadline        DATETIME     NOT NULL,
    currency_code             CHAR(3)      NOT NULL DEFAULT 'CNY',
    status                    VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    sent_time                 DATETIME     DEFAULT NULL,
    awarded_supplier_id       BIGINT       DEFAULT NULL,
    awarded_quotation_id      BIGINT       DEFAULT NULL,
    awarded_quotation_version INT          DEFAULT NULL,
    awarded_time              DATETIME     DEFAULT NULL,
    owner_user_id             BIGINT       NOT NULL,
    owner_unit_id             BIGINT       NOT NULL,
    version                   INT          NOT NULL DEFAULT 0,
    deleted                   TINYINT      NOT NULL DEFAULT 0,
    create_time               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time               DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by                 VARCHAR(64)  DEFAULT NULL,
    update_by                 VARCHAR(64)  DEFAULT NULL,
    UNIQUE KEY uk_proc_rfq_no (tenant_id, rfq_no),
    INDEX idx_proc_rfq_requisition (tenant_id, requisition_id, status, deleted),
    INDEX idx_proc_rfq_status_deadline (tenant_id, status, quotation_deadline, deleted),
    INDEX idx_proc_rfq_owner (tenant_id, owner_user_id, owner_unit_id, deleted),
    INDEX idx_proc_rfq_award (tenant_id, awarded_supplier_id, awarded_quotation_id),
    CONSTRAINT chk_proc_rfq_currency CHECK (currency_code REGEXP '^[A-Z]{3}$'),
    CONSTRAINT chk_proc_rfq_status CHECK (
        status IN ('DRAFT','SENT','CLOSED','AWARDED','CANCELLED')
    ),
    CONSTRAINT chk_proc_rfq_quote_version CHECK (
        awarded_quotation_version IS NULL OR awarded_quotation_version > 0
    ),
    CONSTRAINT chk_proc_rfq_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购询价单';

-- 14.8 询价明细快照
CREATE TABLE IF NOT EXISTS proc_rfq_line (
    id                   BIGINT        AUTO_INCREMENT PRIMARY KEY,
    tenant_id            BIGINT        NOT NULL,
    rfq_id               BIGINT        NOT NULL,
    line_no              INT           NOT NULL,
    material_id          BIGINT        NOT NULL,
    material_code        VARCHAR(64)   NOT NULL,
    material_name        VARCHAR(200)  NOT NULL,
    category_code        VARCHAR(64)   NOT NULL,
    unit                 VARCHAR(32)   NOT NULL,
    quantity             DECIMAL(19,6) NOT NULL,
    remark               VARCHAR(500)  DEFAULT NULL,
    version              INT           NOT NULL DEFAULT 0,
    deleted              TINYINT       NOT NULL DEFAULT 0,
    active_line_no_guard INT GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 THEN line_no ELSE NULL END
    ) STORED,
    create_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by            VARCHAR(64)   DEFAULT NULL,
    update_by            VARCHAR(64)   DEFAULT NULL,
    UNIQUE KEY uk_proc_rfq_line_active (tenant_id, rfq_id, active_line_no_guard),
    INDEX idx_proc_rfq_line_rfq (tenant_id, rfq_id, deleted),
    INDEX idx_proc_rfq_line_material (tenant_id, material_id, deleted),
    CONSTRAINT chk_proc_rfq_line_no CHECK (line_no > 0),
    CONSTRAINT chk_proc_rfq_line_quantity CHECK (quantity > 0),
    CONSTRAINT chk_proc_rfq_line_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购询价明细';

-- 14.9 询价供应商邀请与最新报价快照
CREATE TABLE IF NOT EXISTS proc_rfq_supplier (
    id                       BIGINT       AUTO_INCREMENT PRIMARY KEY,
    tenant_id                BIGINT       NOT NULL,
    rfq_id                   BIGINT       NOT NULL,
    supplier_id              BIGINT       NOT NULL COMMENT 'SRM供应商ID，不建跨库外键',
    supplier_name_snapshot   VARCHAR(200) NOT NULL,
    invited_time             DATETIME     DEFAULT NULL,
    quotation_id             BIGINT       DEFAULT NULL COMMENT 'SRM报价ID，不建跨库外键',
    quotation_version        INT          DEFAULT NULL,
    quotation_request_id     VARCHAR(64)  DEFAULT NULL,
    quotation_time           DATETIME     DEFAULT NULL,
    status                   VARCHAR(20)  NOT NULL DEFAULT 'INVITED',
    version                  INT          NOT NULL DEFAULT 0,
    deleted                  TINYINT      NOT NULL DEFAULT 0,
    active_supplier_guard    BIGINT GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 THEN supplier_id ELSE NULL END
    ) STORED,
    create_time              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time              DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by                VARCHAR(64)  DEFAULT NULL,
    update_by                VARCHAR(64)  DEFAULT NULL,
    UNIQUE KEY uk_proc_rfq_supplier_active (tenant_id, rfq_id, active_supplier_guard),
    UNIQUE KEY uk_proc_rfq_quote (tenant_id, quotation_id),
    UNIQUE KEY uk_proc_rfq_quote_request (tenant_id, quotation_request_id),
    INDEX idx_proc_rfq_supplier_rfq (tenant_id, rfq_id, status, deleted),
    INDEX idx_proc_rfq_supplier_supplier (tenant_id, supplier_id, status, deleted),
    INDEX idx_proc_rfq_supplier_quote_time (tenant_id, quotation_time, deleted),
    CONSTRAINT chk_proc_rfq_supplier_status CHECK (
        status IN ('INVITED','QUOTED','EXPIRED','AWARDED','REJECTED')
    ),
    CONSTRAINT chk_proc_rfq_supplier_quote_version CHECK (
        quotation_version IS NULL OR quotation_version > 0
    ),
    CONSTRAINT chk_proc_rfq_supplier_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购询价供应商邀请';

-- 14.10 采购订单及中标报价不可变快照
CREATE TABLE IF NOT EXISTS proc_purchase_order (
    id                       BIGINT        AUTO_INCREMENT PRIMARY KEY,
    tenant_id                BIGINT        NOT NULL,
    po_no                    VARCHAR(64)   NOT NULL,
    rfq_id                   BIGINT        NOT NULL,
    supplier_id              BIGINT        NOT NULL COMMENT 'SRM供应商ID，不建跨库外键',
    supplier_name_snapshot   VARCHAR(200)  NOT NULL,
    quotation_id             BIGINT        NOT NULL COMMENT 'SRM报价ID，不建跨库外键',
    quotation_version        INT           NOT NULL,
    title                    VARCHAR(200)  NOT NULL,
    total_amount             DECIMAL(19,4) NOT NULL,
    currency_code            CHAR(3)       NOT NULL,
    status                   VARCHAR(24)   NOT NULL DEFAULT 'DRAFT',
    order_time               DATETIME      DEFAULT NULL,
    expected_delivery_date   DATE          DEFAULT NULL,
    actual_delivery_date     DATE          DEFAULT NULL,
    delivery_address         VARCHAR(500)  NOT NULL,
    contact_name             VARCHAR(100)  NOT NULL,
    contact_phone            VARCHAR(50)   NOT NULL,
    owner_user_id            BIGINT        NOT NULL,
    owner_unit_id            BIGINT        NOT NULL,
    version                  INT           NOT NULL DEFAULT 0,
    deleted                  TINYINT       NOT NULL DEFAULT 0,
    create_time              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time              DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by                VARCHAR(64)   DEFAULT NULL,
    update_by                VARCHAR(64)   DEFAULT NULL,
    UNIQUE KEY uk_proc_po_no (tenant_id, po_no),
    UNIQUE KEY uk_proc_po_rfq (tenant_id, rfq_id),
    UNIQUE KEY uk_proc_po_quotation (tenant_id, quotation_id, quotation_version),
    INDEX idx_proc_po_supplier_status (tenant_id, supplier_id, status, deleted),
    INDEX idx_proc_po_owner (tenant_id, owner_user_id, owner_unit_id, deleted),
    INDEX idx_proc_po_delivery (tenant_id, status, expected_delivery_date, deleted),
    CONSTRAINT chk_proc_po_total CHECK (total_amount > 0),
    CONSTRAINT chk_proc_po_currency CHECK (currency_code REGEXP '^[A-Z]{3}$'),
    CONSTRAINT chk_proc_po_quote_version CHECK (quotation_version > 0),
    CONSTRAINT chk_proc_po_status CHECK (
        status IN ('DRAFT','SENT','CONFIRMED','PARTIAL_RECEIVED','RECEIVED','CLOSED','CANCELLED')
    ),
    CONSTRAINT chk_proc_po_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购订单';

-- 14.11 采购订单行及报价价格/交期快照
CREATE TABLE IF NOT EXISTS proc_purchase_order_line (
    id                     BIGINT        AUTO_INCREMENT PRIMARY KEY,
    tenant_id              BIGINT        NOT NULL,
    po_id                  BIGINT        NOT NULL,
    line_no                INT           NOT NULL,
    rfq_line_id            BIGINT        NOT NULL,
    material_id            BIGINT        NOT NULL,
    material_code          VARCHAR(64)   NOT NULL,
    material_name          VARCHAR(200)  NOT NULL,
    category_code          VARCHAR(64)   NOT NULL,
    unit                   VARCHAR(32)   NOT NULL,
    quantity               DECIMAL(19,6) NOT NULL,
    unit_price             DECIMAL(19,6) NOT NULL,
    total_price            DECIMAL(19,4) NOT NULL,
    delivery_days          INT           NOT NULL DEFAULT 0,
    expected_delivery_date DATE          DEFAULT NULL,
    remark                 VARCHAR(500)  DEFAULT NULL,
    version                INT           NOT NULL DEFAULT 0,
    deleted                TINYINT       NOT NULL DEFAULT 0,
    active_line_no_guard   INT GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 THEN line_no ELSE NULL END
    ) STORED,
    create_time            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time            DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by              VARCHAR(64)   DEFAULT NULL,
    update_by              VARCHAR(64)   DEFAULT NULL,
    UNIQUE KEY uk_proc_po_line_active (tenant_id, po_id, active_line_no_guard),
    UNIQUE KEY uk_proc_po_rfq_line (tenant_id, po_id, rfq_line_id),
    INDEX idx_proc_po_line_po (tenant_id, po_id, deleted),
    INDEX idx_proc_po_line_material (tenant_id, material_id, deleted),
    CONSTRAINT chk_proc_po_line_no CHECK (line_no > 0),
    CONSTRAINT chk_proc_po_line_quantity CHECK (quantity > 0),
    CONSTRAINT chk_proc_po_line_price CHECK (unit_price > 0),
    CONSTRAINT chk_proc_po_line_total CHECK (total_price > 0),
    CONSTRAINT chk_proc_po_line_delivery CHECK (delivery_days BETWEEN 0 AND 3650),
    CONSTRAINT chk_proc_po_line_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购订单明细';

-- 14.12 采购收货单
CREATE TABLE IF NOT EXISTS proc_goods_receipt (
    id                   BIGINT       AUTO_INCREMENT PRIMARY KEY,
    tenant_id            BIGINT       NOT NULL,
    gr_no                VARCHAR(64)  NOT NULL,
    po_id                BIGINT       NOT NULL,
    receiver_user_id     BIGINT       NOT NULL,
    receive_time         DATETIME     NOT NULL,
    remark               VARCHAR(500) DEFAULT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    confirmed_time       DATETIME     DEFAULT NULL,
    confirmed_event_id   VARCHAR(64)  DEFAULT NULL,
    owner_user_id        BIGINT       NOT NULL,
    owner_unit_id        BIGINT       NOT NULL,
    version              INT          NOT NULL DEFAULT 0,
    deleted              TINYINT      NOT NULL DEFAULT 0,
    create_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by            VARCHAR(64)  DEFAULT NULL,
    update_by            VARCHAR(64)  DEFAULT NULL,
    UNIQUE KEY uk_proc_gr_no (tenant_id, gr_no),
    UNIQUE KEY uk_proc_gr_confirmed_event (tenant_id, confirmed_event_id),
    INDEX idx_proc_gr_po_status (tenant_id, po_id, status, deleted),
    INDEX idx_proc_gr_receiver_time (tenant_id, receiver_user_id, receive_time, deleted),
    INDEX idx_proc_gr_owner (tenant_id, owner_user_id, owner_unit_id, deleted),
    CONSTRAINT chk_proc_gr_status CHECK (status IN ('DRAFT','CONFIRMED')),
    CONSTRAINT chk_proc_gr_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购收货单';

-- 14.13 采购收货行、质检与资产事件幂等快照
CREATE TABLE IF NOT EXISTS proc_goods_receipt_line (
    id                         BIGINT        AUTO_INCREMENT PRIMARY KEY,
    tenant_id                  BIGINT        NOT NULL,
    goods_receipt_id           BIGINT        NOT NULL,
    line_no                    INT           NOT NULL,
    po_line_id                 BIGINT        NOT NULL,
    material_id                BIGINT        NOT NULL,
    material_code              VARCHAR(64)   NOT NULL,
    material_name              VARCHAR(200)  NOT NULL,
    category_code              VARCHAR(64)   NOT NULL,
    unit                       VARCHAR(32)    NOT NULL,
    asset_managed              TINYINT       NOT NULL DEFAULT 0,
    ordered_quantity           DECIMAL(19,6) NOT NULL,
    received_quantity          DECIMAL(19,6) NOT NULL,
    quality_status             VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    quality_result_time        DATETIME      DEFAULT NULL,
    confirmed_event_id         VARCHAR(64)   DEFAULT NULL,
    quality_passed_event_id    VARCHAR(64)   DEFAULT NULL,
    remark                     VARCHAR(500)  DEFAULT NULL,
    version                    INT           NOT NULL DEFAULT 0,
    deleted                    TINYINT       NOT NULL DEFAULT 0,
    active_line_no_guard       INT GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 THEN line_no ELSE NULL END
    ) STORED,
    active_po_line_guard       BIGINT GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 THEN po_line_id ELSE NULL END
    ) STORED,
    create_time                DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time                DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by                  VARCHAR(64)   DEFAULT NULL,
    update_by                  VARCHAR(64)   DEFAULT NULL,
    UNIQUE KEY uk_proc_gr_line_active (tenant_id, goods_receipt_id, active_line_no_guard),
    UNIQUE KEY uk_proc_gr_po_line_active (tenant_id, goods_receipt_id, active_po_line_guard),
    INDEX idx_proc_gr_quality_event (tenant_id, quality_passed_event_id),
    INDEX idx_proc_gr_line_receipt (tenant_id, goods_receipt_id, deleted),
    INDEX idx_proc_gr_line_po (tenant_id, po_line_id, deleted),
    INDEX idx_proc_gr_line_confirmed_event (tenant_id, confirmed_event_id),
    INDEX idx_proc_gr_asset_candidate (tenant_id, asset_managed, quality_status, deleted, id),
    CONSTRAINT chk_proc_gr_line_no CHECK (line_no > 0),
    CONSTRAINT chk_proc_gr_line_asset CHECK (asset_managed IN (0, 1)),
    CONSTRAINT chk_proc_gr_line_ordered CHECK (ordered_quantity > 0),
    CONSTRAINT chk_proc_gr_line_received CHECK (
        received_quantity > 0 AND received_quantity <= ordered_quantity
    ),
    CONSTRAINT chk_proc_gr_line_quality CHECK (quality_status IN ('PASS','FAIL','PENDING')),
    CONSTRAINT chk_proc_gr_line_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购收货明细';

-- 14.14 采购领域事件 Inbox
CREATE TABLE IF NOT EXISTS proc_event_inbox (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    event_id        VARCHAR(64)  NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    source_service  VARCHAR(64)  NOT NULL,
    aggregate_type  VARCHAR(64)  DEFAULT NULL,
    aggregate_id    VARCHAR(128) DEFAULT NULL,
    payload         JSON         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'RECEIVED',
    processed_time  DATETIME     DEFAULT NULL,
    error_message   VARCHAR(500) DEFAULT NULL,
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_proc_inbox_event (tenant_id, event_id),
    INDEX idx_proc_inbox_status (tenant_id, status, create_time),
    CONSTRAINT chk_proc_inbox_status CHECK (status IN ('RECEIVED','PROCESSED','IGNORED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购领域事件收件箱';

-- 14.15 Procurement 本地 Transactional Outbox
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
    UNIQUE KEY uk_proc_mq_msg_id (msg_id),
    INDEX idx_proc_mq_relay (status, next_retry_time),
    INDEX idx_proc_mq_tenant_time (tenant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Procurement可靠消息发件箱';

-- tenant=1 默认采购配置与顶级物料品类
INSERT INTO proc_tenant_config
    (tenant_id, currency_code, initialized_time, version, deleted, create_by)
VALUES
    (1, 'CNY', NOW(), 0, 0, 'system')
AS new
ON DUPLICATE KEY UPDATE
    currency_code = new.currency_code,
    initialized_time = COALESCE(proc_tenant_config.initialized_time, new.initialized_time),
    deleted = 0,
    update_by = 'system';

INSERT INTO proc_material_category
    (tenant_id, parent_id, category_code, category_name, sort, status, version, deleted, create_by)
VALUES
    (1, 0, 'IT_DEVICE',     'IT 设备', 10, 1, 0, 0, 'system'),
    (1, 0, 'OFFICE_SUPPLY', '办公用品', 20, 1, 0, 0, 'system'),
    (1, 0, 'RAW_MATERIAL',  '原材料', 30, 1, 0, 0, 'system'),
    (1, 0, 'OTHER',         '其他', 40, 1, 0, 0, 'system')
AS new
ON DUPLICATE KEY UPDATE
    category_name = new.category_name,
    sort = new.sort,
    status = 1,
    deleted = 0,
    update_by = 'system';

-- 子品类（二级），通过 code 反查父级 ID
INSERT INTO proc_material_category
    (tenant_id, parent_id, category_code, category_name, sort, status, version, deleted, create_by)
SELECT 1, c.id, v.code, v.name, v.sort, 1, 0, 0, 'system'
FROM proc_material_category c
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
WHERE c.tenant_id = 1 AND c.parent_id = 0 AND c.category_code = v.parent_code AND c.deleted = 0
ON DUPLICATE KEY UPDATE
    category_name = VALUES(category_name),
    sort = VALUES(sort),
    status = 1,
    deleted = 0,
    update_by = 'system';

-- 14.15 采购字典数据（写入 omni_base.sys_dict_type + sys_dict_data）
USE omni_base;

INSERT IGNORE INTO sys_dict_type (tenant_id, type_code, type_name, remark, sort, status, create_by)
VALUES
    (1, 'proc_requisition_status',   '请购状态',       '采购请购单的状态枚举',         20, 1, 'system'),
    (1, 'proc_rfq_status',           '询价状态',       '采购询价单的状态枚举',         21, 1, 'system'),
    (1, 'proc_rfq_supplier_status',  '供应商报价状态', '询价中受邀供应商的状态枚举',   22, 1, 'system'),
    (1, 'proc_po_status',            '采购订单状态',   '采购订单的状态枚举',           23, 1, 'system'),
    (1, 'proc_gr_status',            '收货状态',       '采购收货单的状态枚举',         24, 1, 'system'),
    (1, 'proc_quality_status',       '质检状态',       '收货行的质检结果状态枚举',     25, 1, 'system'),
    (1, 'proc_material_unit',        '物料计量单位',   '采购物料的计量单位枚举',       26, 1, 'system');

INSERT IGNORE INTO sys_dict_data (tenant_id, type_code, dict_value,         dict_label,     tag_type,  sort, status, create_by)
VALUES
    -- 请购状态
    (1, 'proc_requisition_status',   'DRAFT',           '草稿',     'info',     1, 1, 'system'),
    (1, 'proc_requisition_status',   'SUBMITTED',       '已提交',   'primary',  2, 1, 'system'),
    (1, 'proc_requisition_status',   'APPROVING',       '审批中',   'warning',  3, 1, 'system'),
    (1, 'proc_requisition_status',   'APPROVED',        '已通过',   'success',  4, 1, 'system'),
    (1, 'proc_requisition_status',   'REJECTED',        '已驳回',   'danger',   5, 1, 'system'),
    (1, 'proc_requisition_status',   'CANCELLED',       '已取消',   'info',     6, 1, 'system'),
    -- 询价状态
    (1, 'proc_rfq_status',           'DRAFT',           '草稿',     'info',     1, 1, 'system'),
    (1, 'proc_rfq_status',           'SENT',            '报价中',   'primary',  2, 1, 'system'),
    (1, 'proc_rfq_status',           'CLOSED',          '已截止',   'warning',  3, 1, 'system'),
    (1, 'proc_rfq_status',           'AWARDED',         '已定标',   'success',  4, 1, 'system'),
    (1, 'proc_rfq_status',           'CANCELLED',       '已取消',   'danger',   5, 1, 'system'),
    -- 供应商报价状态
    (1, 'proc_rfq_supplier_status',  'INVITED',         '已邀请',   'info',     1, 1, 'system'),
    (1, 'proc_rfq_supplier_status',  'QUOTED',          '已报价',   'primary',  2, 1, 'system'),
    (1, 'proc_rfq_supplier_status',  'EXPIRED',         '已过期',   'warning',  3, 1, 'system'),
    (1, 'proc_rfq_supplier_status',  'AWARDED',         '已中标',   'success',  4, 1, 'system'),
    (1, 'proc_rfq_supplier_status',  'REJECTED',        '未中标',   'danger',   5, 1, 'system'),
    -- 采购订单状态
    (1, 'proc_po_status',            'DRAFT',           '草稿',     'info',     1, 1, 'system'),
    (1, 'proc_po_status',            'SENT',            '已发送',   'primary',  2, 1, 'system'),
    (1, 'proc_po_status',            'CONFIRMED',       '已确认',   'success',  3, 1, 'system'),
    (1, 'proc_po_status',            'PARTIAL_RECEIVED','部分收货', 'warning',  4, 1, 'system'),
    (1, 'proc_po_status',            'RECEIVED',        '已收货',   'success',  5, 1, 'system'),
    (1, 'proc_po_status',            'CLOSED',          '已关闭',   'info',     6, 1, 'system'),
    (1, 'proc_po_status',            'CANCELLED',       '已取消',   'danger',   7, 1, 'system'),
    -- 收货状态
    (1, 'proc_gr_status',            'DRAFT',           '草稿',     'info',     1, 1, 'system'),
    (1, 'proc_gr_status',            'CONFIRMED',       '已确认',   'success',  2, 1, 'system'),
    -- 质检状态
    (1, 'proc_quality_status',       'PASS',            '合格',     'success',  1, 1, 'system'),
    (1, 'proc_quality_status',       'FAIL',            '不合格',   'danger',   2, 1, 'system'),
    (1, 'proc_quality_status',       'PENDING',         '待定',     'warning',  3, 1, 'system'),
    -- 物料计量单位
    (1, 'proc_material_unit',        'EA',              '个',       'primary',  1, 1, 'system'),
    (1, 'proc_material_unit',        'PCS',             '件',       'primary',  2, 1, 'system'),
    (1, 'proc_material_unit',        'UNIT',            '台',       'primary',  3, 1, 'system'),
    (1, 'proc_material_unit',        'SET',             '套',       'primary',  4, 1, 'system'),
    (1, 'proc_material_unit',        'KG',              '千克',     'info',     5, 1, 'system'),
    (1, 'proc_material_unit',        'BOX',             '箱',       'info',     6, 1, 'system'),
    (1, 'proc_material_unit',        'PACK',            '包',       'info',     7, 1, 'system'),
    (1, 'proc_material_unit',        'M',               '米',       'info',     8, 1, 'system');

-- 14.16 Asset 品类和位置字典（默认租户 1）
USE omni_base;

INSERT IGNORE INTO sys_dict_type
    (tenant_id, type_code, type_name, remark, sort, status, create_by)
VALUES
    (1, 'asset_category', '资产品类', '资产台账品类编码', 30, 1, 'system'),
    (1, 'asset_location', '资产位置', '资产存放位置编码', 31, 1, 'system');

INSERT INTO sys_dict_data
    (tenant_id, type_code, dict_value, dict_label, tag_type, sort, status, create_by)
SELECT 1, definitions.type_code, definitions.dict_value, definitions.dict_label,
       definitions.tag_type, definitions.sort, 1, 'system'
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
    FROM sys_dict_data dict_data
    WHERE dict_data.tenant_id = 1
      AND dict_data.type_code = definitions.type_code
      AND dict_data.dict_value = definitions.dict_value
);

-- ============================================================
-- Section 15: Asset 服务 - 资产全生命周期 MVP
-- ============================================================
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

-- tenant=1 Asset 权限模板和默认角色
USE omni_auth;

INSERT IGNORE INTO sys_role
    (id, tenant_id, role_code, role_name, data_scope, sort, status, create_by)
VALUES
    (40, 1, 'ASSET_ADMIN',   '资产管理员', 'TENANT',         40, 1, 'system'),
    (41, 1, 'ASSET_MANAGER', '资产经理',   'DEPT_AND_BELOW', 41, 1, 'system'),
    (42, 1, 'ASSET_USER',    '资产使用人', 'SELF',           42, 1, 'system');

INSERT IGNORE INTO sys_permission
    (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (700, 1,   0, 'asset',                       '资产管理',         'DIRECTORY', '/700/',         1, 9, 1, 'system'),
    (710, 1, 700, 'asset:overview',              '资产概览',         'MENU',      '/700/710/',     2, 1, 1, 'system'),
    (711, 1, 710, 'asset:overview:list',         '查看资产概览',     'API',       '/700/710/711/', 3, 1, 1, 'system'),
    (720, 1, 700, 'asset:asset',                 '资产台账',         'MENU',      '/700/720/',     2, 2, 1, 'system'),
    (721, 1, 720, 'asset:asset:list',            '查看资产台账',     'API',       '/700/720/721/', 3, 1, 1, 'system'),
    (722, 1, 720, 'asset:asset:self',            '查看我的资产',     'API',       '/700/720/722/', 3, 2, 1, 'system'),
    (723, 1, 720, 'asset:asset:create',          '创建资产',         'API',       '/700/720/723/', 3, 3, 1, 'system'),
    (724, 1, 720, 'asset:asset:update',          '更新资产',         'API',       '/700/720/724/', 3, 4, 1, 'system'),
    (725, 1, 720, 'asset:asset:delete',          '删除资产',         'API',       '/700/720/725/', 3, 5, 1, 'system'),
    (726, 1, 720, 'asset:asset:allocate',        '分配资产',         'API',       '/700/720/726/', 3, 6, 1, 'system'),
    (727, 1, 720, 'asset:asset:accept',          '确认领用资产',     'API',       '/700/720/727/', 3, 7, 1, 'system'),
    (728, 1, 720, 'asset:asset:return',          '退还资产',         'API',       '/700/720/728/', 3, 8, 1, 'system'),
    (729, 1, 720, 'asset:asset:maintenance',     '维护资产状态',     'API',       '/700/720/729/', 3, 9, 1, 'system'),
    (740, 1, 700, 'asset:transfer',              '资产调拨',         'MENU',      '/700/740/',     2, 3, 1, 'system'),
    (741, 1, 740, 'asset:transfer:list',         '查看资产调拨',     'API',       '/700/740/741/', 3, 1, 1, 'system'),
    (742, 1, 740, 'asset:transfer:create',       '创建资产调拨',     'API',       '/700/740/742/', 3, 2, 1, 'system'),
    (743, 1, 740, 'asset:transfer:approve',      '审批资产调拨视图', 'API',       '/700/740/743/', 3, 3, 1, 'system'),
    (744, 1, 740, 'asset:transfer:complete',     '完成资产调拨',     'API',       '/700/740/744/', 3, 4, 1, 'system'),
    (745, 1, 740, 'asset:transfer:cancel',       '取消资产调拨',     'API',       '/700/740/745/', 3, 5, 1, 'system'),
    (746, 1, 740, 'asset:transfer:retry',        '重试调拨流程',     'API',       '/700/740/746/', 3, 6, 1, 'system'),
    (750, 1, 700, 'asset:disposal',              '资产处置',         'MENU',      '/700/750/',     2, 4, 1, 'system'),
    (751, 1, 750, 'asset:disposal:list',         '查看资产处置',     'API',       '/700/750/751/', 3, 1, 1, 'system'),
    (752, 1, 750, 'asset:disposal:create',       '创建资产处置',     'API',       '/700/750/752/', 3, 2, 1, 'system'),
    (753, 1, 750, 'asset:disposal:approve',      '审批资产处置视图', 'API',       '/700/750/753/', 3, 3, 1, 'system'),
    (754, 1, 750, 'asset:disposal:complete',     '完成资产处置',     'API',       '/700/750/754/', 3, 4, 1, 'system'),
    (755, 1, 750, 'asset:disposal:cancel',       '取消资产处置',     'API',       '/700/750/755/', 3, 5, 1, 'system'),
    (756, 1, 750, 'asset:disposal:retry',        '重试处置流程',     'API',       '/700/750/756/', 3, 6, 1, 'system');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.tenant_id = role.tenant_id
WHERE role.tenant_id = 1
  AND role.role_code IN ('SUPER_ADMIN', 'ASSET_ADMIN', 'ASSET_MANAGER')
  AND (permission.permission_code = 'asset' OR permission.permission_code LIKE 'asset:%');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.tenant_id = role.tenant_id
WHERE role.tenant_id = 1
  AND role.role_code IN ('ASSET_ADMIN', 'ASSET_MANAGER')
  AND permission.permission_code IN (
      'workflow', 'workflow:instance', 'workflow:task:todo',
      'workflow:approval:complete', 'workflow:model:list'
  );

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.tenant_id = role.tenant_id
WHERE role.tenant_id = 1 AND role.role_code = 'ASSET_USER'
  AND permission.permission_code IN (
      'asset', 'asset:asset', 'asset:asset:self',
      'asset:asset:accept', 'asset:asset:return'
  );

-- init-all 重跑时严格收敛 tenant=1 的 ASSET_USER Asset 授权。
DELETE role_permission
FROM sys_role_permission role_permission
JOIN sys_role role ON role.id = role_permission.role_id
JOIN sys_permission permission ON permission.id = role_permission.permission_id
WHERE role.tenant_id = 1
  AND role.role_code = 'ASSET_USER'
  AND permission.tenant_id = role.tenant_id
  AND (permission.permission_code = 'asset' OR permission.permission_code LIKE 'asset:%')
  AND permission.permission_code NOT IN (
      'asset', 'asset:asset', 'asset:asset:self',
      'asset:asset:accept', 'asset:asset:return'
  );

-- ============================================================
-- 授权应用用户访问所有 omni 数据库
-- ============================================================
GRANT ALL PRIVILEGES ON omni_auth.* TO 'omni_app'@'%';
GRANT ALL PRIVILEGES ON omni_base.* TO 'omni_app'@'%';
GRANT ALL PRIVILEGES ON omni_workflow.* TO 'omni_app'@'%';
GRANT ALL PRIVILEGES ON omni_crm.* TO 'omni_app'@'%';
GRANT ALL PRIVILEGES ON omni_srm.* TO 'omni_app'@'%';
GRANT ALL PRIVILEGES ON omni_procurement.* TO 'omni_app'@'%';
GRANT ALL PRIVILEGES ON omni_asset.* TO 'omni_app'@'%';
FLUSH PRIVILEGES;

