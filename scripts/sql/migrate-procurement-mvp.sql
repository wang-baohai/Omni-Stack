-- ============================================================
-- Procurement MVP 采购闭环迁移
-- 适用：已有 Omni-Stack 数据库升级
-- 内容：15 张采购表、权限树、角色授权、租户配置与品类种子
-- 执行本迁移后重新导入 scripts/sql/sp_init_tenant.sql，更新未来租户初始化过程。
-- ============================================================

CREATE DATABASE IF NOT EXISTS omni_procurement
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE omni_procurement;

CREATE TABLE IF NOT EXISTS proc_tenant_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL DEFAULT 'CNY',
    initialized_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    active_tenant_guard TINYINT GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 THEN 1 ELSE NULL END
    ) STORED,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    UNIQUE KEY uk_proc_config_active_tenant (tenant_id, active_tenant_guard),
    CONSTRAINT chk_proc_config_currency CHECK (currency_code REGEXP '^[A-Z]{3}$'),
    CONSTRAINT chk_proc_config_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购租户配置';

CREATE TABLE IF NOT EXISTS proc_material_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    parent_id BIGINT NOT NULL DEFAULT 0,
    category_code VARCHAR(50) NOT NULL,
    category_name VARCHAR(100) NOT NULL,
    sort INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    active_category_code_guard VARCHAR(50) GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 THEN category_code ELSE NULL END
    ) STORED,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    UNIQUE KEY uk_proc_category_active_code (tenant_id, active_category_code_guard),
    INDEX idx_proc_category_parent (tenant_id, parent_id, status, deleted),
    CONSTRAINT chk_proc_category_parent CHECK (parent_id >= 0),
    CONSTRAINT chk_proc_category_sort CHECK (sort >= 0),
    CONSTRAINT chk_proc_category_status CHECK (status IN (0, 1)),
    CONSTRAINT chk_proc_category_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购物料品类';

CREATE TABLE IF NOT EXISTS proc_material (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    material_code VARCHAR(64) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    specification VARCHAR(500) DEFAULT NULL,
    unit VARCHAR(32) NOT NULL,
    asset_managed TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    active_material_code_guard VARCHAR(64) GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 THEN material_code ELSE NULL END
    ) STORED,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    UNIQUE KEY uk_proc_material_active_code (tenant_id, active_material_code_guard),
    INDEX idx_proc_material_category (tenant_id, category_id, status, deleted),
    INDEX idx_proc_material_name (tenant_id, material_name, deleted),
    CONSTRAINT chk_proc_material_asset CHECK (asset_managed IN (0, 1)),
    CONSTRAINT chk_proc_material_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_proc_material_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购物料目录';

CREATE TABLE IF NOT EXISTS proc_approval_route (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    route_code VARCHAR(64) NOT NULL,
    category_code VARCHAR(50) NOT NULL,
    min_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    max_amount DECIMAL(19,4) DEFAULT NULL,
    model_version_id BIGINT NOT NULL,
    priority INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    active_route_code_guard VARCHAR(64) GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 THEN route_code ELSE NULL END
    ) STORED,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    UNIQUE KEY uk_proc_route_active_code (tenant_id, active_route_code_guard),
    INDEX idx_proc_route_match (tenant_id, category_code, status, min_amount, max_amount, deleted),
    CONSTRAINT chk_proc_route_amount CHECK (
        min_amount >= 0 AND (max_amount IS NULL OR max_amount > min_amount)
    ),
    CONSTRAINT chk_proc_route_priority CHECK (priority >= 0),
    CONSTRAINT chk_proc_route_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_proc_route_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='请购审批路由';

CREATE TABLE IF NOT EXISTS proc_requisition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    requisition_no VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    requester_user_id BIGINT NOT NULL,
    requester_unit_id BIGINT NOT NULL,
    reason VARCHAR(1000) DEFAULT NULL,
    primary_category_code VARCHAR(50) NOT NULL,
    total_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    currency_code CHAR(3) NOT NULL DEFAULT 'CNY',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    approval_attempt INT NOT NULL DEFAULT 0,
    workflow_request_id VARCHAR(64) DEFAULT NULL,
    workflow_business_key VARCHAR(128) DEFAULT NULL,
    workflow_model_version_id BIGINT DEFAULT NULL,
    process_instance_id VARCHAR(64) DEFAULT NULL,
    workflow_start_status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    approved_time DATETIME DEFAULT NULL,
    workflow_completed_time DATETIME DEFAULT NULL,
    owner_user_id BIGINT NOT NULL,
    owner_unit_id BIGINT NOT NULL,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
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

CREATE TABLE IF NOT EXISTS proc_requisition_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    requisition_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    material_code VARCHAR(64) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    category_code VARCHAR(50) NOT NULL,
    unit VARCHAR(32) NOT NULL,
    quantity DECIMAL(19,6) NOT NULL,
    estimated_unit_price DECIMAL(19,6) NOT NULL,
    estimated_total_price DECIMAL(19,4) NOT NULL,
    remark VARCHAR(500) DEFAULT NULL,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    active_line_no_guard INT GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 THEN line_no ELSE NULL END
    ) STORED,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    UNIQUE KEY uk_proc_req_line_active (tenant_id, requisition_id, active_line_no_guard),
    INDEX idx_proc_req_line_req (tenant_id, requisition_id, deleted),
    INDEX idx_proc_req_line_material (tenant_id, material_id, deleted),
    CONSTRAINT chk_proc_req_line_no CHECK (line_no > 0),
    CONSTRAINT chk_proc_req_line_quantity CHECK (quantity > 0),
    CONSTRAINT chk_proc_req_line_price CHECK (estimated_unit_price >= 0),
    CONSTRAINT chk_proc_req_line_total CHECK (estimated_total_price >= 0),
    CONSTRAINT chk_proc_req_line_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购请购明细';

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

CREATE TABLE IF NOT EXISTS proc_event_inbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
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
    UNIQUE KEY uk_proc_inbox_event (tenant_id, event_id),
    INDEX idx_proc_inbox_status (tenant_id, status, create_time),
    CONSTRAINT chk_proc_inbox_status CHECK (status IN ('RECEIVED','PROCESSED','IGNORED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购领域事件收件箱';

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
    UNIQUE KEY uk_proc_mq_msg_id (msg_id),
    INDEX idx_proc_mq_relay (status, next_retry_time),
    INDEX idx_proc_mq_tenant_time (tenant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Procurement可靠消息发件箱';

-- 为所有已有租户补首批 Procurement 权限树
USE omni_auth;

INSERT INTO sys_permission
    (tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
SELECT tenants.tenant_id, 0, 'procurement', '采购管理', 'DIRECTORY', '', 1, 9, 1, 'system'
FROM (SELECT DISTINCT tenant_id FROM sys_role) tenants
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission permission
    WHERE permission.tenant_id = tenants.tenant_id
      AND permission.permission_code = 'procurement'
);

INSERT INTO sys_permission
    (tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
SELECT root.tenant_id, root.id, definitions.permission_code, definitions.permission_name,
       'MENU', '', 2, definitions.sort, 1, 'system'
FROM sys_permission root
CROSS JOIN (
    SELECT 'procurement:overview' permission_code, '采购概览' permission_name, 1 sort
    UNION ALL SELECT 'procurement:material', '物料目录', 2
    UNION ALL SELECT 'procurement:requisition', '请购管理', 3
    UNION ALL SELECT 'procurement:approval-route', '审批路由', 4
    UNION ALL SELECT 'procurement:rfq', '询价管理', 5
    UNION ALL SELECT 'procurement:purchase-order', '采购订单', 6
    UNION ALL SELECT 'procurement:goods-receipt', '采购收货', 7
) definitions
WHERE root.permission_code = 'procurement'
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission existing
      WHERE existing.tenant_id = root.tenant_id
        AND existing.permission_code = definitions.permission_code
  );

INSERT INTO sys_permission
    (tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
SELECT menu.tenant_id, menu.id, definitions.permission_code, definitions.permission_name,
       'API', '', 3, definitions.sort, 1, 'system'
FROM sys_permission menu
JOIN (
    SELECT 'procurement:overview' menu_code, 'procurement:overview:list' permission_code, '查看采购概览' permission_name, 1 sort
    UNION ALL SELECT 'procurement:material', 'procurement:material:list', '查看物料', 1
    UNION ALL SELECT 'procurement:material', 'procurement:material:create', '创建物料', 2
    UNION ALL SELECT 'procurement:material', 'procurement:material:update', '更新物料', 3
    UNION ALL SELECT 'procurement:material', 'procurement:material:delete', '删除物料', 4
    UNION ALL SELECT 'procurement:requisition', 'procurement:requisition:list', '查看请购', 1
    UNION ALL SELECT 'procurement:requisition', 'procurement:requisition:create', '创建请购', 2
    UNION ALL SELECT 'procurement:requisition', 'procurement:requisition:update', '更新请购', 3
    UNION ALL SELECT 'procurement:requisition', 'procurement:requisition:delete', '删除请购', 4
    UNION ALL SELECT 'procurement:requisition', 'procurement:requisition:submit', '提交请购', 5
    UNION ALL SELECT 'procurement:requisition', 'procurement:requisition:approve', '审批请购视图', 6
    UNION ALL SELECT 'procurement:requisition', 'procurement:requisition:cancel', '取消请购', 7
    UNION ALL SELECT 'procurement:approval-route', 'procurement:approval-route:list', '查看审批路由', 1
    UNION ALL SELECT 'procurement:approval-route', 'procurement:approval-route:create', '创建审批路由', 2
    UNION ALL SELECT 'procurement:approval-route', 'procurement:approval-route:update', '更新审批路由', 3
    UNION ALL SELECT 'procurement:approval-route', 'procurement:approval-route:delete', '删除审批路由', 4
    UNION ALL SELECT 'procurement:rfq', 'procurement:rfq:list', '查看询价', 1
    UNION ALL SELECT 'procurement:rfq', 'procurement:rfq:create', '创建询价', 2
    UNION ALL SELECT 'procurement:rfq', 'procurement:rfq:update', '更新询价', 3
    UNION ALL SELECT 'procurement:rfq', 'procurement:rfq:delete', '删除询价', 4
    UNION ALL SELECT 'procurement:rfq', 'procurement:rfq:send', '发送询价', 5
    UNION ALL SELECT 'procurement:rfq', 'procurement:rfq:award', '询价定点', 6
    UNION ALL SELECT 'procurement:rfq', 'procurement:rfq:cancel', '取消询价', 7
    UNION ALL SELECT 'procurement:purchase-order', 'procurement:purchase-order:list', '查看采购订单', 1
    UNION ALL SELECT 'procurement:purchase-order', 'procurement:purchase-order:update', '更新采购订单', 3
    UNION ALL SELECT 'procurement:purchase-order', 'procurement:purchase-order:delete', '删除采购订单', 4
    UNION ALL SELECT 'procurement:purchase-order', 'procurement:purchase-order:send', '发送采购订单', 5
    UNION ALL SELECT 'procurement:purchase-order', 'procurement:purchase-order:confirm', '确认采购订单', 6
    UNION ALL SELECT 'procurement:purchase-order', 'procurement:purchase-order:cancel', '取消采购订单', 7
    UNION ALL SELECT 'procurement:goods-receipt', 'procurement:goods-receipt:list', '查看采购收货', 1
    UNION ALL SELECT 'procurement:goods-receipt', 'procurement:goods-receipt:create', '创建采购收货', 2
    UNION ALL SELECT 'procurement:goods-receipt', 'procurement:goods-receipt:confirm', '确认采购收货', 3
) definitions ON definitions.menu_code = menu.permission_code
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission existing
    WHERE existing.tenant_id = menu.tenant_id
      AND existing.permission_code = definitions.permission_code
);

UPDATE sys_permission root
SET root.path = CONCAT('/', root.id, '/'), root.status = 1, root.update_by = 'system'
WHERE root.permission_code = 'procurement';

UPDATE sys_permission child
JOIN sys_permission parent ON parent.id = child.parent_id AND parent.tenant_id = child.tenant_id
SET child.path = CONCAT(parent.path, child.id, '/'), child.status = 1, child.update_by = 'system'
WHERE child.permission_code LIKE 'procurement:%'
  AND child.depth = 2;

UPDATE sys_permission child
JOIN sys_permission parent ON parent.id = child.parent_id AND parent.tenant_id = child.tenant_id
SET child.path = CONCAT(parent.path, child.id, '/'), child.status = 1, child.update_by = 'system'
WHERE child.permission_code LIKE 'procurement:%'
  AND child.depth = 3;

-- 既有租户重跑迁移时也统一概览优先的菜单顺序，避免新增菜单与物料目录同为 sort=1。
UPDATE sys_permission
SET sort = CASE permission_code
    WHEN 'procurement:overview' THEN 1
    WHEN 'procurement:material' THEN 2
    WHEN 'procurement:requisition' THEN 3
    WHEN 'procurement:approval-route' THEN 4
    WHEN 'procurement:rfq' THEN 5
    WHEN 'procurement:purchase-order' THEN 6
    WHEN 'procurement:goods-receipt' THEN 7
    ELSE sort
END,
update_by = 'system'
WHERE permission_code IN (
    'procurement:overview', 'procurement:material', 'procurement:requisition',
    'procurement:approval-route', 'procurement:rfq',
    'procurement:purchase-order', 'procurement:goods-receipt'
);

-- 采购经理：全部 MVP 采购权限，并补齐 Workflow 待办与完成权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.role_code = 'PROCUREMENT_MANAGER'
  AND (
      p.permission_code = 'procurement'
      OR p.permission_code LIKE 'procurement:%'
      OR p.permission_code IN (
          'workflow', 'workflow:instance', 'workflow:task:todo',
          'workflow:approval:complete', 'workflow:model:list'
      )
  );

-- SUPER_ADMIN：新增权限全部补齐
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.role_code = 'SUPER_ADMIN'
  AND (p.permission_code = 'procurement' OR p.permission_code LIKE 'procurement:%');

-- 采购员：SELF 概览、共享物料维护及请购/询价/订单/收货执行
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.role_code = 'PROCUREMENT_STAFF'
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

-- 采购订单只能由 RFQ 定点事务生成，清理旧版预留但从未实现的外部创建权限
DELETE role_permission
FROM sys_role_permission role_permission
JOIN sys_permission permission ON permission.id = role_permission.permission_id
WHERE permission.permission_code = 'procurement:purchase-order:create';

DELETE FROM sys_permission
WHERE permission_code = 'procurement:purchase-order:create';

-- 普通员工：共享物料只读与 SELF 请购
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.role_code = 'EMPLOYEE'
  AND p.permission_code IN (
      'procurement', 'procurement:material', 'procurement:material:list',
      'procurement:requisition', 'procurement:requisition:list',
      'procurement:requisition:create', 'procurement:requisition:update',
      'procurement:requisition:delete', 'procurement:requisition:submit',
      'procurement:requisition:cancel'
  );

-- 组长和部门领导：请购人能力与审批视图，审批提交仍由 Workflow 校验任务归属
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.role_code IN ('TEAM_LEADER', 'DEPT_LEADER')
  AND p.permission_code IN (
      'procurement', 'procurement:material', 'procurement:material:list',
      'procurement:requisition', 'procurement:requisition:list',
      'procurement:requisition:create', 'procurement:requisition:update',
      'procurement:requisition:delete', 'procurement:requisition:submit',
      'procurement:requisition:approve', 'procurement:requisition:cancel'
  );

-- USER 明确不授予任何 Procurement 权限。

-- 只为尚无有效配置的既有租户执行一次默认初始化；重跑不得覆盖币种或复活品类
USE omni_procurement;

DROP TEMPORARY TABLE IF EXISTS tmp_proc_tenants_to_initialize;
CREATE TEMPORARY TABLE tmp_proc_tenants_to_initialize (
    tenant_id BIGINT PRIMARY KEY
);

INSERT INTO tmp_proc_tenants_to_initialize (tenant_id)
SELECT tenants.tenant_id
FROM (SELECT DISTINCT tenant_id FROM omni_auth.sys_role) tenants
WHERE NOT EXISTS (
    SELECT 1
    FROM proc_tenant_config config
    WHERE config.tenant_id = tenants.tenant_id
      AND config.deleted = 0
);

START TRANSACTION;

INSERT IGNORE INTO proc_tenant_config
    (tenant_id, currency_code, initialized_time, version, deleted, create_by)
SELECT tenant_id, 'CNY', NOW(), 0, 0, 'system'
FROM tmp_proc_tenants_to_initialize;

INSERT IGNORE INTO proc_material_category
    (tenant_id, parent_id, category_code, category_name, sort, status, version, deleted, create_by)
SELECT tenants.tenant_id, 0, categories.category_code, categories.category_name,
       categories.sort, 1, 0, 0, 'system'
FROM tmp_proc_tenants_to_initialize tenants
CROSS JOIN (
    SELECT 'IT_DEVICE' category_code, 'IT 设备' category_name, 10 sort
    UNION ALL SELECT 'OFFICE_SUPPLY', '办公用品', 20
    UNION ALL SELECT 'RAW_MATERIAL', '原材料', 30
    UNION ALL SELECT 'OTHER', '其他', 40
) categories;

COMMIT;

DROP TEMPORARY TABLE IF EXISTS tmp_proc_tenants_to_initialize;

GRANT ALL PRIVILEGES ON omni_procurement.* TO 'omni_app'@'%';
FLUSH PRIVILEGES;
