package com.omni.dbmigrator.migration;

/**
 * 一个独立 Liquibase 目标数据库。
 *
 * @param id        稳定目标 ID
 * @param database  MySQL 数据库名
 * @param changelog changelog classpath 路径
 * @param vendor    是否为第三方 vendor schema
 */
public record MigrationTarget(String id, String database, String changelog, boolean vendor) {
}
