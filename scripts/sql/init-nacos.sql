-- ============================================================
-- Nacos v3.1.1 数据库初始化脚本（MySQL 外部存储）
-- ============================================================
-- 用途：创建 nacos_config 数据库及全部表结构，使 Nacos 从内嵌 Derby
--       切换到 MySQL 持久化，重启后配置数据不丢失。
-- 适用场景：
--   1. Docker MySQL 容器首次启动时自动执行（挂载到 /docker-entrypoint-initdb.d/）
--   2. 手动执行：mysql -uroot -proot < scripts/sql/init-nacos.sql
--
-- 表结构概览（共 10 表）：
--   配置管理（7 表）：
--     config_info            — 主配置存储
--     config_info_gray       — 灰度配置发布
--     config_tags_relation   — 配置标签关联
--     group_capacity         — Group 容量配额
--     his_config_info        — 配置变更历史
--     tenant_capacity        — 租户容量配额
--     tenant_info            — 租户/命名空间注册
--   权限管理（3 表）：
--     users                  — Nacos 应用用户
--     roles                  — 用户角色关联
--     permissions            — 角色资源权限
--
-- 种子数据：
--   1 个管理员用户（nacos/nacos）、1 条 ROLE_ADMIN 角色
--
-- 注意：此脚本使用 CREATE TABLE IF NOT EXISTS，可重复执行。
-- 来源：Nacos 官方 mysql-schema.sql（v3.1.1）
-- ============================================================

-- ============================================================
-- Section 1: 创建数据库
-- ============================================================
CREATE DATABASE IF NOT EXISTS nacos_config
    DEFAULT CHARACTER SET utf8
    DEFAULT COLLATE utf8_bin;

USE nacos_config;

-- ============================================================
-- Section 2: 配置管理表
-- ============================================================

-- 2.1 主配置存储表
CREATE TABLE IF NOT EXISTS `config_info` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
    `data_id` varchar(255) NOT NULL COMMENT 'data_id',
    `group_id` varchar(128) DEFAULT NULL COMMENT 'group_id',
    `content` longtext NOT NULL COMMENT 'content',
    `md5` varchar(32) DEFAULT NULL COMMENT 'md5',
    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    `src_user` text COMMENT 'source user',
    `src_ip` varchar(50) DEFAULT NULL COMMENT 'source ip',
    `app_name` varchar(128) DEFAULT NULL COMMENT 'app_name',
    `tenant_id` varchar(128) DEFAULT '' COMMENT '租户字段',
    `c_desc` varchar(256) DEFAULT NULL COMMENT 'configuration description',
    `c_use` varchar(64) DEFAULT NULL COMMENT 'configuration usage',
    `effect` varchar(64) DEFAULT NULL COMMENT '配置生效的描述',
    `type` varchar(64) DEFAULT NULL COMMENT '配置的类型',
    `c_schema` text COMMENT '配置的模式',
    `encrypted_data_key` varchar(1024) NOT NULL DEFAULT '' COMMENT '密钥',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_configinfo_datagrouptenant` (`data_id`,`group_id`,`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='config_info';

-- 2.2 灰度配置发布表
CREATE TABLE IF NOT EXISTS `config_info_gray` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',
    `data_id` varchar(255) NOT NULL COMMENT 'data_id',
    `group_id` varchar(128) NOT NULL COMMENT 'group_id',
    `content` longtext NOT NULL COMMENT 'content',
    `md5` varchar(32) DEFAULT NULL COMMENT 'md5',
    `src_user` text COMMENT 'src_user',
    `src_ip` varchar(100) DEFAULT NULL COMMENT 'src_ip',
    `gmt_create` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'gmt_create',
    `gmt_modified` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'gmt_modified',
    `app_name` varchar(128) DEFAULT NULL COMMENT 'app_name',
    `tenant_id` varchar(128) DEFAULT '' COMMENT 'tenant_id',
    `gray_name` varchar(128) NOT NULL COMMENT 'gray_name',
    `gray_rule` text NOT NULL COMMENT 'gray_rule',
    `encrypted_data_key` varchar(256) NOT NULL DEFAULT '' COMMENT 'encrypted_data_key',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_configinfogray_datagrouptenantgray` (`data_id`,`group_id`,`tenant_id`,`gray_name`),
    KEY `idx_dataid_gmt_modified` (`data_id`,`gmt_modified`),
    KEY `idx_gmt_modified` (`gmt_modified`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 COMMENT='config_info_gray';

-- 2.3 配置标签关联表
CREATE TABLE IF NOT EXISTS `config_tags_relation` (
    `id` bigint(20) NOT NULL COMMENT 'id',
    `tag_name` varchar(128) NOT NULL COMMENT 'tag_name',
    `tag_type` varchar(64) DEFAULT NULL COMMENT 'tag_type',
    `data_id` varchar(255) NOT NULL COMMENT 'data_id',
    `group_id` varchar(128) NOT NULL COMMENT 'group_id',
    `tenant_id` varchar(128) DEFAULT '' COMMENT 'tenant_id',
    `nid` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'nid, 自增长标识',
    PRIMARY KEY (`nid`),
    UNIQUE KEY `uk_configtagrelation_configidtag` (`id`,`tag_name`,`tag_type`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='config_tag_relation';

-- 2.4 Group 容量配额表
CREATE TABLE IF NOT EXISTS `group_capacity` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `group_id` varchar(128) NOT NULL DEFAULT '' COMMENT 'Group ID，空字符表示整个集群',
    `quota` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '配额，0表示使用默认值',
    `usage` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '使用量',
    `max_size` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '单个配置大小上限，单位为字节，0表示使用默认值',
    `max_aggr_count` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '聚合子配置最大个数，0表示使用默认值',
    `max_aggr_size` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '单个聚合数据的子配置大小上限，单位为字节，0表示使用默认值',
    `max_history_count` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '最大变更历史数量',
    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_id` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='集群、各Group容量信息表';

-- 2.5 配置变更历史表
CREATE TABLE IF NOT EXISTS `his_config_info` (
    `id` bigint(20) unsigned NOT NULL COMMENT 'id',
    `nid` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'nid, 自增标识',
    `data_id` varchar(255) NOT NULL COMMENT 'data_id',
    `group_id` varchar(128) NOT NULL COMMENT 'group_id',
    `app_name` varchar(128) DEFAULT NULL COMMENT 'app_name',
    `content` longtext NOT NULL COMMENT 'content',
    `md5` varchar(32) DEFAULT NULL COMMENT 'md5',
    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    `src_user` text COMMENT 'source user',
    `src_ip` varchar(50) DEFAULT NULL COMMENT 'source ip',
    `op_type` char(10) DEFAULT NULL COMMENT 'operation type',
    `tenant_id` varchar(128) DEFAULT '' COMMENT '租户字段',
    `encrypted_data_key` varchar(1024) NOT NULL DEFAULT '' COMMENT '密钥',
    `publish_type` varchar(50) DEFAULT 'formal' COMMENT 'publish type gray or formal',
    `gray_name` varchar(50) DEFAULT NULL COMMENT 'gray name',
    `ext_info` longtext DEFAULT NULL COMMENT 'ext info',
    PRIMARY KEY (`nid`),
    KEY `idx_gmt_create` (`gmt_create`),
    KEY `idx_gmt_modified` (`gmt_modified`),
    KEY `idx_did` (`data_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='多租户改造';

-- 2.6 租户容量配额表
CREATE TABLE IF NOT EXISTS `tenant_capacity` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id` varchar(128) NOT NULL DEFAULT '' COMMENT 'Tenant ID',
    `quota` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '配额，0表示使用默认值',
    `usage` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '使用量',
    `max_size` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '单个配置大小上限，单位为字节，0表示使用默认值',
    `max_aggr_count` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '聚合子配置最大个数',
    `max_aggr_size` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '单个聚合数据的子配置大小上限，单位为字节，0表示使用默认值',
    `max_history_count` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '最大变更历史数量',
    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='租户容量信息表';

-- 2.7 租户/命名空间注册表
CREATE TABLE IF NOT EXISTS `tenant_info` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
    `kp` varchar(128) NOT NULL COMMENT 'kp',
    `tenant_id` varchar(128) default '' COMMENT 'tenant_id',
    `tenant_name` varchar(128) default '' COMMENT 'tenant_name',
    `tenant_desc` varchar(256) DEFAULT NULL COMMENT 'tenant_desc',
    `create_source` varchar(32) DEFAULT NULL COMMENT 'create_source',
    `gmt_create` bigint(20) NOT NULL COMMENT '创建时间',
    `gmt_modified` bigint(20) NOT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_info_kptenantid` (`kp`,`tenant_id`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='tenant_info';

-- ============================================================
-- Section 3: 权限管理表
-- ============================================================

-- 3.1 Nacos 应用用户表
CREATE TABLE IF NOT EXISTS `users` (
    `username` varchar(50) NOT NULL PRIMARY KEY COMMENT 'username',
    `password` varchar(500) NOT NULL COMMENT 'password',
    `enabled` boolean NOT NULL COMMENT 'enabled'
);

-- 3.2 用户角色关联表
CREATE TABLE IF NOT EXISTS `roles` (
    `username` varchar(50) NOT NULL COMMENT 'username',
    `role` varchar(50) NOT NULL COMMENT 'role',
    UNIQUE INDEX `idx_user_role` (`username` ASC, `role` ASC) USING BTREE
);

-- 3.3 角色资源权限表
CREATE TABLE IF NOT EXISTS `permissions` (
    `role` varchar(50) NOT NULL COMMENT 'role',
    `resource` varchar(128) NOT NULL COMMENT 'resource',
    `action` varchar(8) NOT NULL COMMENT 'action',
    UNIQUE INDEX `uk_role_permission` (`role`,`resource`,`action`) USING BTREE
);

-- ============================================================
-- Section 4: 种子数据
-- ============================================================

-- 插入默认管理员用户（nacos/nacos）
INSERT IGNORE INTO users (username, password, enabled)
VALUES ('nacos', '$2a$10$EuWPZHzz32dJN7jexM34MOeYirDdFAZm2kuWj7VEOJhhZkDrxfvUu', TRUE);

-- 插入管理员角色
INSERT IGNORE INTO roles (username, role)
VALUES ('nacos', 'ROLE_ADMIN');
