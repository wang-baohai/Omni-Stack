package com.omni.dbmigrator.seed;

/**
 * 一项只读种子数据断言。
 *
 * <p>查询结果会按列标签、JDBC 类型和值生成稳定规范串，排序后计算 SHA-256。
 * 清单只保存数量和摘要，日志不会输出可能包含敏感信息的具体数据。</p>
 *
 * @param id             稳定断言 ID
 * @param module         所属模块 ID
 * @param database       目标数据库
 * @param query          只读 SELECT 查询
 * @param expectedRows   预期行数
 * @param expectedSha256 预期规范结果 SHA-256
 */
public record SeedAssertion(
        String id,
        String module,
        String database,
        String query,
        int expectedRows,
        String expectedSha256) {
}
