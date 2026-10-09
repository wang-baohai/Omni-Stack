package com.omni.dbmigrator.adoption;

import java.time.Instant;

/**
 * 接管前备份与隔离恢复证据。
 *
 * @param version 证据格式版本
 * @param baselineCommit 数据源对应基线提交
 * @param sourceServerUuid 源 MySQL server_uuid
 * @param adoptionServerUuid 实际待接管 MySQL server_uuid
 * @param createdAt 备份完成时间
 * @param backupFile 备份文件绝对路径
 * @param backupSha256 备份文件摘要
 * @param restoreVerifiedAt 隔离恢复验证完成时间
 * @param restoreServerUuid 恢复目标 MySQL server_uuid，必须与源不同
 * @param restoreBaselineCommit 恢复后结构验证使用的基线提交
 */
public record BackupEvidence(
        String version,
        String baselineCommit,
        String sourceServerUuid,
        String adoptionServerUuid,
        Instant createdAt,
        String backupFile,
        String backupSha256,
        Instant restoreVerifiedAt,
        String restoreServerUuid,
        String restoreBaselineCommit) {
}
