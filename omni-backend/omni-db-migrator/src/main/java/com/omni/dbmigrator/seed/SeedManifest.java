package com.omni.dbmigrator.seed;

import java.util.List;

/**
 * 正式种子数据清单。
 *
 * @param version         清单版本
 * @param digestAlgorithm 摘要算法，当前只允许 SHA-256
 * @param sources         不可变种子资源及摘要
 * @param modules         模块声明
 * @param assertions      数据库断言
 */
public record SeedManifest(
        String version,
        String digestAlgorithm,
        List<SeedSource> sources,
        List<SeedModule> modules,
        List<SeedAssertion> assertions) {
}
