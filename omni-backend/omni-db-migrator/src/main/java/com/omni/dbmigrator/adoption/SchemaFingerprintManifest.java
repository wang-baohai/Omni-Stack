package com.omni.dbmigrator.adoption;

import java.util.List;

/**
 * 既有数据库接管指纹清单。
 *
 * @param baselineCommit 参考 Git 提交
 * @param algorithm 指纹算法版本
 * @param targets 冻结基线覆盖的目标数据库
 */
public record SchemaFingerprintManifest(
        String baselineCommit,
        String algorithm,
        List<SchemaFingerprintTarget> targets) {
}
