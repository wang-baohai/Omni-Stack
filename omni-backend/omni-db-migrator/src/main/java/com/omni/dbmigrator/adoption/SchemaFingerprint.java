package com.omni.dbmigrator.adoption;

/**
 * 一个数据库的实际强结构指纹。
 *
 * @param database 数据库名
 * @param tables 基础表数
 * @param views 视图数
 * @param triggers 触发器数
 * @param routines 存储过程和函数数
 * @param sha256 规范结构摘要
 */
public record SchemaFingerprint(
        String database,
        int tables,
        int views,
        int triggers,
        int routines,
        String sha256) {
}
