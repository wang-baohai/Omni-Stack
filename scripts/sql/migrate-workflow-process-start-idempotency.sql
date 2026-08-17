-- Omni-Stack Workflow 跨服务流程启动幂等与完成事件升级（MySQL 8.4）
-- 可重复执行；用于既有数据卷，fresh 环境由 scripts/sql/init-all.sql 创建。

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS omni_workflow
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE omni_workflow;

CREATE TABLE IF NOT EXISTS wf_process_start_request (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id           BIGINT        NOT NULL COMMENT '租户 ID',
    request_id          VARCHAR(64)   NOT NULL COMMENT '调用方请求 ID',
    business_type       VARCHAR(100)  NOT NULL COMMENT '业务类型',
    business_key        VARCHAR(255)  NOT NULL COMMENT '业务主键',
    model_version_id    BIGINT        NOT NULL COMMENT '流程模型版本 ID',
    start_user_id       BIGINT        NOT NULL COMMENT '发起人用户 ID',
    status              VARCHAR(20)   NOT NULL DEFAULT 'RESERVED' COMMENT '状态: RESERVED/STARTED/FAILED',
    process_instance_id VARCHAR(64)   DEFAULT NULL COMMENT 'Flowable 流程实例 ID',
    retry_count         INT           NOT NULL DEFAULT 0 COMMENT '重试次数',
    last_error          VARCHAR(1000) DEFAULT NULL COMMENT '最近一次失败原因',
    started_time        DATETIME      DEFAULT NULL COMMENT '启动成功时间',
    failed_time         DATETIME      DEFAULT NULL COMMENT '最近一次失败时间',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wf_start_request (tenant_id, request_id),
    UNIQUE KEY uk_wf_start_business (tenant_id, business_type, business_key),
    UNIQUE KEY uk_wf_start_process_instance (tenant_id, process_instance_id),
    INDEX idx_wf_start_status (tenant_id, status, update_time),
    CONSTRAINT chk_wf_start_status CHECK (status IN ('RESERVED', 'STARTED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='跨服务流程启动幂等请求表';

DROP PROCEDURE IF EXISTS sp_migrate_workflow_process_start_idempotency;

DELIMITER $$

CREATE PROCEDURE sp_migrate_workflow_process_start_idempotency()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'wf_process_instance_ext'
    ) THEN
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'wf_process_instance_ext'
              AND column_name = 'request_id'
        ) THEN
            ALTER TABLE wf_process_instance_ext
                ADD COLUMN request_id VARCHAR(64) DEFAULT NULL
                    COMMENT '跨服务调用请求 ID' AFTER engine_version;
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'wf_process_instance_ext'
              AND column_name = 'business_type'
        ) THEN
            ALTER TABLE wf_process_instance_ext
                ADD COLUMN business_type VARCHAR(100) DEFAULT NULL
                    COMMENT '跨服务业务类型' AFTER request_id;
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'wf_process_instance_ext'
              AND column_name = 'completion_result'
        ) THEN
            ALTER TABLE wf_process_instance_ext
                ADD COLUMN completion_result VARCHAR(20) DEFAULT NULL
                    COMMENT '完成结果: APPROVED/REJECTED/CANCELLED' AFTER status;
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'wf_process_instance_ext'
              AND column_name = 'completed_time'
        ) THEN
            ALTER TABLE wf_process_instance_ext
                ADD COLUMN completed_time DATETIME DEFAULT NULL
                    COMMENT '业务完成时间' AFTER completion_result;
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'wf_process_instance_ext'
              AND column_name = 'completion_event_id'
        ) THEN
            ALTER TABLE wf_process_instance_ext
                ADD COLUMN completion_event_id VARCHAR(36) DEFAULT NULL
                    COMMENT '完成事件 ID；同实例仅发布一次' AFTER completed_time;
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'wf_process_instance_ext'
              AND index_name = 'idx_wf_ext_request'
        ) THEN
            ALTER TABLE wf_process_instance_ext
                ADD INDEX idx_wf_ext_request (tenant_id, request_id);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'wf_process_instance_ext'
              AND index_name = 'idx_wf_ext_business'
        ) THEN
            ALTER TABLE wf_process_instance_ext
                ADD INDEX idx_wf_ext_business (tenant_id, business_type, business_key);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'wf_process_instance_ext'
              AND index_name = 'uk_wf_completion_event'
        ) THEN
            ALTER TABLE wf_process_instance_ext
                ADD UNIQUE INDEX uk_wf_completion_event (completion_event_id);
        END IF;
    END IF;
END$$

DELIMITER ;

CALL sp_migrate_workflow_process_start_idempotency();
DROP PROCEDURE IF EXISTS sp_migrate_workflow_process_start_idempotency;
