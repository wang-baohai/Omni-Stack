-- ============================================================
-- Base 服务 Flyway 迁移脚本（V1）
-- 创建 omni_base 数据库的字典管理表
-- ============================================================

-- 字典类型表
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

-- 字典数据表
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
