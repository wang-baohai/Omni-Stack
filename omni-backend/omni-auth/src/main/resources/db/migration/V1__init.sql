-- ============================================================
-- Omni-Auth Database Schema (MySQL 8.4)
-- ============================================================

-- --------------------------------------------------------------
-- 1. OAuth2 Standard Tables (Spring Authorization Server)
-- --------------------------------------------------------------
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

CREATE TABLE IF NOT EXISTS oauth2_authorization_consent (
    registered_client_id VARCHAR(100) NOT NULL,
    principal_name       VARCHAR(200) NOT NULL,
    authorities          VARCHAR(1000) DEFAULT NULL,
    scopes               VARCHAR(1000) DEFAULT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------------
-- 2. Business Tables (Multi-tenant RBAC)
-- --------------------------------------------------------------
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

CREATE TABLE IF NOT EXISTS sys_user_role (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_user (user_id),
    INDEX idx_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id     BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    INDEX idx_role (role_id),
    INDEX idx_permission (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

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

CREATE TABLE IF NOT EXISTS sys_user_unit (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    unit_id BIGINT NOT NULL COMMENT '组织单元ID',
    is_primary TINYINT DEFAULT 0 COMMENT '是否主组织: 0-否, 1-是',
    UNIQUE KEY uk_user_unit (user_id, unit_id),
    INDEX idx_user (user_id),
    INDEX idx_unit (unit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户组织关联表';

CREATE TABLE IF NOT EXISTS sys_role_dept (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL COMMENT '角色ID',
    dept_id BIGINT NOT NULL COMMENT '组织单元ID',
    UNIQUE KEY uk_role_dept (role_id, dept_id),
    INDEX idx_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色数据范围关联表';

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
