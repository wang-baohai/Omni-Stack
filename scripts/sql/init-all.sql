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
    INDEX idx_operlog_tenant (tenant_id),
    INDEX idx_operlog_time (oper_time),
    INDEX idx_operlog_module (module),
    INDEX idx_operlog_type (oper_type),
    INDEX idx_operlog_username (oper_username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志热表';

-- 5.4 操作日志归档表（冷表）
CREATE TABLE IF NOT EXISTS sys_oper_log_archive (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
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
    INDEX idx_archive_tenant (tenant_id),
    INDEX idx_archive_time (oper_time),
    INDEX idx_archive_module (module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志归档冷表';

-- 5.5 预置字典类型（默认租户 1）
INSERT IGNORE INTO sys_dict_type (tenant_id, type_code, type_name, sort, status, create_by) VALUES
    (1, 'sys_user_gender',   '用户性别',   1, 1, 'system'),
    (1, 'sys_common_status', '通用状态',   2, 1, 'system'),
    (1, 'sys_notice_type',   '通知类型',   3, 1, 'system');

-- 5.6 预置字典数据
INSERT IGNORE INTO sys_dict_data (tenant_id, type_code, dict_value, dict_label, tag_type, sort, status, create_by) VALUES
    (1, 'sys_user_gender', '1', '男',     'primary', 1, 1, 'system'),
    (1, 'sys_user_gender', '2', '女',     'danger',  2, 1, 'system'),
    (1, 'sys_user_gender', '0', '未知',   'info',    3, 1, 'system'),
    (1, 'sys_common_status', '1', '启用', 'success', 1, 1, 'system'),
    (1, 'sys_common_status', '0', '禁用', 'danger',  2, 1, 'system'),
    (1, 'sys_notice_type', '1', '系统通知', 'primary', 1, 1, 'system'),
    (1, 'sys_notice_type', '2', '业务通知', 'warning', 2, 1, 'system');

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
    business_key        VARCHAR(255) DEFAULT NULL COMMENT '业务主键',
    title               VARCHAR(500) DEFAULT NULL COMMENT '流程标题',
    start_user_id       BIGINT       NOT NULL COMMENT '发起人用户 ID',
    start_user_name     VARCHAR(100) DEFAULT NULL COMMENT '发起人用户名',
    category            VARCHAR(100) DEFAULT NULL COMMENT '流程分类',
    status              TINYINT      DEFAULT 1 COMMENT '状态: 0-已终止, 1-进行中, 2-已完成',
    create_time         DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_process_instance_id (process_instance_id),
    INDEX idx_start_user (tenant_id, start_user_id),
    INDEX idx_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='流程实例扩展表';

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
    (1, 'workflow_category', 'leave',    '请假审批', 1, 1, 'system'),
    (1, 'workflow_category', 'expense',  '报销审批', 2, 1, 'system'),
    (1, 'workflow_category', 'purchase', '采购审批', 3, 1, 'system'),
    (1, 'workflow_category', 'contract', '合同审批', 4, 1, 'system'),
    (1, 'workflow_category', 'general',  '通用审批', 5, 1, 'system');

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

-- ============================================================
-- Section 10: 4级会签审批请假流程 - 种子数据
-- ============================================================
USE omni_auth;

-- 10.1 组织架构
INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (100, 1, 1, '技术研发部', 'DEPT', 'tech-dept', '/1/100/', 2, 1, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (101, 1, 100, '后端1组', 'TEAM', 'backend-1', '/1/100/101/', 3, 1, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (102, 1, 100, '架构1组', 'TEAM', 'arch-1', '/1/100/102/', 3, 2, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (200, 1, 1, '人事部', 'DEPT', 'hr-dept', '/1/200/', 2, 2, 1, 'system');

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

        INSERT INTO sys_permission (tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
        SELECT p_tenant_id,
               IF(parent_id = 0, 0, IFNULL((SELECT new_id FROM tmp_perm_map WHERE old_id = t.parent_id), 0)),
               permission_code, permission_name, type, path, depth, sort, 1, 'system'
        FROM sys_permission t WHERE t.id = v_old_id;

        SET v_new_id = LAST_INSERT_ID();
        INSERT INTO tmp_perm_map (old_id, new_id) VALUES (v_old_id, v_new_id);
    END LOOP;
    CLOSE cur;

    -- Step 2: 创建默认角色
    INSERT INTO sys_role (tenant_id, role_code, role_name, data_scope, sort, status, create_by) VALUES
        (p_tenant_id, 'SUPER_ADMIN', 'Super Administrator', 'ALL',  0, 1, 'system'),
        (p_tenant_id, 'USER',        'Default User',        'SELF', 99, 1, 'system'),
        (p_tenant_id, 'EMPLOYEE',    '普通员工',             'SELF', 10, 1, 'system'),
        (p_tenant_id, 'TEAM_LEADER', '工作组组长',         'DEPT', 11, 1, 'system'),
        (p_tenant_id, 'DEPT_LEADER', '部门领导', 'DEPT_AND_BELOW', 12, 1, 'system');

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

