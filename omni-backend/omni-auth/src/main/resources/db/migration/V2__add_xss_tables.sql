-- ============================================================
-- V2: XSS 防护配置表与黑名单规则表
-- ============================================================

-- 2.1 XSS 防护全局配置表（每租户一条）
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

-- 2.2 XSS 黑名单规则表
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

-- 2.3 种子数据：默认租户 XSS 配置（关闭状态）
INSERT IGNORE INTO sys_xss_config (tenant_id, enabled, create_by) VALUES (1, 0, 'system');

-- 2.4 种子数据：XSS 黑名单预置规则
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
