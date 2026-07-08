-- MQ消息补偿管理模块 - 运行中数据库迁移脚本
-- 执行时间：迁移时执行一次

USE omni_auth;

-- 1. 新增"运维监控"目录
INSERT INTO sys_permission (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES (63, 1, 0, 'monitor', '运维监控', 'DIRECTORY', '/63/', 1, 5, 1, 'system');

-- 2. 迁移操作日志：从基础数据(50)移到运维监控(63)下，更新 path
UPDATE sys_permission SET parent_id = 63, path = '/63/61/' WHERE id = 61;
UPDATE sys_permission SET path = '/63/61/62/' WHERE id = 62;

-- 3. 新增消息记录权限节点
INSERT INTO sys_permission (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (64, 1, 63, 'base:mqmessage',        '消息记录',     'MENU',      '/63/64/',    2, 2, 1, 'system'),
    (65, 1, 64, 'base:mqmessage:list',   '查看消息记录', 'API',       '/63/64/65/', 3, 1, 1, 'system'),
    (66, 1, 64, 'base:mqmessage:resend', '重发消息',     'API',       '/63/64/66/', 3, 2, 1, 'system'),
    (67, 1, 64, 'base:mqmessage:skip',   '忽略消息',     'API',       '/63/64/67/', 3, 3, 1, 'system');

-- 4. SUPER_ADMIN 角色追加新权限
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
    (1, 63), (1, 64), (1, 65), (1, 66), (1, 67);

-- 5. 各服务库建表 sys_mq_message
USE omni_auth;
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

USE omni_base;
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

USE omni_workflow;
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
