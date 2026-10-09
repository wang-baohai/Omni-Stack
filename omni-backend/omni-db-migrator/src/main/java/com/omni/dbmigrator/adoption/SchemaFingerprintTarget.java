package com.omni.dbmigrator.adoption;

/**
 * 一个既有数据库的接管指纹基线。
 *
 * @param id 目标 ID
 * @param database 数据库名
 * @param expectedTables 预期业务表数，不含 Liquibase 历史表
 * @param expectedViews 预期视图数
 * @param expectedTriggers 预期触发器数
 * @param expectedRoutines 预期存储过程和函数数量
 * @param expectedSha256 预期强结构摘要
 */
public record SchemaFingerprintTarget(
        String id,
        String database,
        int expectedTables,
        int expectedViews,
        int expectedTriggers,
        int expectedRoutines,
        String expectedSha256) {
}
