package com.omni.dbmigrator.adoption;

import java.time.Instant;

/**
 * 已核验的备份证据摘要。
 *
 * @param evidenceSha256 证据文件摘要
 * @param backupSha256 备份文件摘要
 * @param sourceServerUuid 源数据库 UUID
 * @param adoptionServerUuid 待接管数据库 UUID
 * @param createdAt 备份完成时间
 * @param restoreVerifiedAt 恢复验证完成时间
 */
public record BackupVerification(
        String evidenceSha256,
        String backupSha256,
        String sourceServerUuid,
        String adoptionServerUuid,
        Instant createdAt,
        Instant restoreVerifiedAt) {
}
