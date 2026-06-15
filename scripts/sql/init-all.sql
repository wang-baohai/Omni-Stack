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
--   1 个超级管理员角色、28 个权限节点、28 条角色权限映射
--
-- 注意：此脚本使用 CREATE TABLE IF NOT EXISTS，可重复执行。
-- ============================================================

-- ============================================================
-- Section 1: 创建数据库
-- ============================================================
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
    INDEX idx_org_unit_tenant_parent (tenant_id, parent_id)
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

-- ============================================================
-- Section 4: 种子数据
-- ============================================================

-- 4.1 默认租户
INSERT INTO sys_tenant (id, tenant_code, tenant_name, domain, contact_name, contact_phone, status, create_by)
VALUES (1, 'default', 'Default Tenant', NULL, 'admin', NULL, 1, 'system');

-- 4.2 根组织单元
INSERT INTO sys_org_unit (id, tenant_id, parent_id, name, type, path, depth, sort, status, create_by)
VALUES (1, 1, 0, 'Default Tenant', 'ORG', '/1/', 1, 0, 1, 'system');

-- 4.3 管理员用户（密码: admin123，BCrypt 编码）
INSERT INTO sys_user (id, tenant_id, username, password, nickname, email, phone, gender, primary_unit_id, status, create_by)
VALUES (1, 1, 'admin', '$2b$10$QjkPz8OnRoNOXTrsj./ov.nDZxK.KvsAZdjzgb1YgWSKKprOVxfIW', 'Administrator', NULL, NULL, 0, 1, 1, 'system');

-- 4.4 超级管理员角色
INSERT INTO sys_role (id, tenant_id, role_code, role_name, data_scope, sort, status, create_by)
VALUES (1, 1, 'SUPER_ADMIN', 'Super Administrator', 'ALL', 0, 1, 'system');

-- 4.4.1 默认用户角色（新用户自动关联，无管理权限）
INSERT INTO sys_role (id, tenant_id, role_code, role_name, data_scope, sort, status, create_by)
VALUES (2, 1, 'USER', 'Default User', 'SELF', 99, 1, 'system');

-- 4.5 用户角色映射：admin → SUPER_ADMIN
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- 4.6 用户组织映射：admin → 根组织（主组织）
INSERT INTO sys_user_unit (user_id, unit_id, is_primary) VALUES (1, 1, 1);

-- 4.7 权限树（1 个目录 + 9 个菜单 + 28 个 API 权限 = 38 条）
INSERT INTO sys_permission (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
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

-- 4.8 角色权限映射：SUPER_ADMIN 拥有全部 38 个权限
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
    (1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6),
    (1, 7), (1, 8), (1, 9), (1, 10),
    (1, 11), (1, 12), (1, 13), (1, 14),
    (1, 15), (1, 16), (1, 17), (1, 18),
    (1, 19), (1, 20), (1, 21), (1, 22),
    (1, 23), (1, 24), (1, 25), (1, 26),
    (1, 27), (1, 28), (1, 29), (1, 30),
    (1, 31), (1, 32), (1, 33), (1, 34),
    (1, 35), (1, 36), (1, 37), (1, 38);
