package com.omni.dbmigrator.seed;

/**
 * 单项种子校验结果。
 *
 * @param assertionId 断言 ID
 * @param expectedRows 预期行数
 * @param actualRows 实际行数
 * @param expectedSha256 预期摘要
 * @param actualSha256 实际摘要
 * @param passed 是否通过
 */
public record SeedVerificationResult(
        String assertionId,
        int expectedRows,
        int actualRows,
        String expectedSha256,
        String actualSha256,
        boolean passed) {
}
