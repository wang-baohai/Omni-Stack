package com.omni.dbmigrator.adoption;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.omni.dbmigrator.config.DbMigratorProperties;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 备份与隔离恢复证据拒绝门测试。
 */
class BackupEvidenceServiceTest {

    /** 测试临时目录。 */
    @TempDir
    Path temporaryDirectory;

    /** 固定测试基线。 */
    private static final String BASELINE = "09a29fe10af9c7ddffe5001238d048947868dc98";
    /** 固定当前时间。 */
    private static final Instant NOW = Instant.parse("2026-08-20T10:00:00Z");
    /** 源实例 UUID。 */
    private static final String SOURCE_UUID = "11111111-1111-1111-1111-111111111111";
    /** 恢复实例 UUID。 */
    private static final String RESTORE_UUID = "22222222-2222-2222-2222-222222222222";

    /**
     * 独立恢复克隆作为接管目标时应通过。
     */
    @Test
    void should_accept_verified_restore_clone_as_adoption_target() {
        BackupEvidence evidence = evidence(RESTORE_UUID, SOURCE_UUID, RESTORE_UUID, NOW.minusSeconds(600));

        assertThatCode(() -> BackupEvidenceService.validateEvidence(evidence, BASELINE, NOW, 24))
                .doesNotThrowAnyException();
    }

    /**
     * 接管目标既不是源实例也不是恢复实例时应拒绝。
     */
    @Test
    void should_reject_unrelated_adoption_server() {
        BackupEvidence evidence = evidence(
                "33333333-3333-3333-3333-333333333333", SOURCE_UUID, RESTORE_UUID, NOW.minusSeconds(600));

        assertThatThrownBy(() -> BackupEvidenceService.validateEvidence(evidence, BASELINE, NOW, 24))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("server_uuid");
    }

    /**
     * 所谓恢复仍指向源实例时应拒绝。
     */
    @Test
    void should_reject_restore_on_source_server() {
        BackupEvidence evidence = evidence(SOURCE_UUID, SOURCE_UUID, SOURCE_UUID, NOW.minusSeconds(600));

        assertThatThrownBy(() -> BackupEvidenceService.validateEvidence(evidence, BASELINE, NOW, 24))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("server_uuid");
    }

    /**
     * 超过允许时效的备份应拒绝。
     */
    @Test
    void should_reject_stale_backup() {
        BackupEvidence evidence = evidence(RESTORE_UUID, SOURCE_UUID, RESTORE_UUID, NOW.minusSeconds(25 * 3600));

        assertThatThrownBy(() -> BackupEvidenceService.validateEvidence(evidence, BASELINE, NOW, 24))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("时效");
    }

    /**
     * 备份文件内容被篡改时应在连接数据库前拒绝。
     *
     * @throws Exception 文件写入失败
     */
    @Test
    void should_reject_tampered_backup_before_database_access() throws Exception {
        Path backup = temporaryDirectory.resolve("backup.sql").toAbsolutePath();
        Path evidencePath = temporaryDirectory.resolve("evidence.yaml").toAbsolutePath();
        Files.writeString(backup, "tampered backup", StandardCharsets.UTF_8);
        Instant createdAt = Instant.now().minusSeconds(60);
        String yaml = """
                version: "1"
                baselineCommit: "%s"
                sourceServerUuid: "%s"
                adoptionServerUuid: "%s"
                createdAt: "%s"
                backupFile: "%s"
                backupSha256: "%s"
                restoreVerifiedAt: "%s"
                restoreServerUuid: "%s"
                restoreBaselineCommit: "%s"
                """.formatted(
                BASELINE,
                SOURCE_UUID,
                RESTORE_UUID,
                createdAt,
                backup.toString().replace('\\', '/'),
                "0".repeat(64),
                createdAt.plusSeconds(30),
                RESTORE_UUID,
                BASELINE);
        Files.writeString(evidencePath, yaml, StandardCharsets.UTF_8);
        DbMigratorProperties properties = new DbMigratorProperties(
                "adopt-current", "jdbc:mysql://invalid/", "root", "secret",
                "omni_app", "test-app-password",
                "root.yaml", "seed.yaml", "baseline.yaml", evidencePath.toString(), 24, "");

        assertThatThrownBy(() -> new BackupEvidenceService(properties).verify(BASELINE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SHA-256");
    }

    /**
     * 创建一份字段完整的固定证据。
     */
    private static BackupEvidence evidence(
            String adoptionUuid,
            String sourceUuid,
            String restoreUuid,
            Instant createdAt) {
        return new BackupEvidence(
                "1",
                BASELINE,
                sourceUuid,
                adoptionUuid,
                createdAt,
                "C:/backup.sql",
                "a".repeat(64),
                createdAt.plusSeconds(300),
                restoreUuid,
                BASELINE);
    }
}
