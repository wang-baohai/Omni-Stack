package com.omni.dbmigrator.adoption;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.omni.dbmigrator.config.DbMigratorProperties;
import com.omni.dbmigrator.migration.LiquibaseMigrationService;
import com.omni.dbmigrator.seed.SeedVerificationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 既有数据库双阶段接管编排测试。
 */
class AdoptionServiceTest {

    /** 固定测试基线。 */
    private static final String BASELINE = "09a29fe10af9c7ddffe5001238d048947868dc98";

    /**
     * 错误确认必须停在写入前，精确复用报告摘要才允许同步基线。
     */
    @Test
    void should_require_exact_report_confirmation_before_adoption_write() {
        SchemaFingerprintService fingerprintService = mock(SchemaFingerprintService.class);
        SchemaFingerprintManifestLoader manifestLoader = mock(SchemaFingerprintManifestLoader.class);
        SeedVerificationService seedService = mock(SeedVerificationService.class);
        BackupEvidenceService backupService = mock(BackupEvidenceService.class);
        LiquibaseMigrationService migrationService = mock(LiquibaseMigrationService.class);
        SchemaFingerprintManifest manifest = new SchemaFingerprintManifest(
                BASELINE, SchemaFingerprintManifestLoader.ALGORITHM, List.of());
        BackupVerification backup = new BackupVerification(
                "a".repeat(64),
                "b".repeat(64),
                "11111111-1111-1111-1111-111111111111",
                "22222222-2222-2222-2222-222222222222",
                Instant.parse("2026-08-20T09:00:00Z"),
                Instant.parse("2026-08-20T09:10:00Z"));
        when(manifestLoader.load("database/adoption/baseline-09a29fe.yaml")).thenReturn(manifest);
        when(backupService.verify(BASELINE)).thenReturn(backup);

        AdoptionService firstPhase = service(
                "ADOPT:wrong", fingerprintService, manifestLoader, seedService, backupService, migrationService);
        Throwable rejected = catchThrowable(firstPhase::adoptCurrent);

        assertThat(rejected)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reportSha256=");
        verify(migrationService, never()).adoptBaseline();
        String reportSha256 = rejected.getMessage().substring(rejected.getMessage().indexOf('=') + 1);

        AdoptionService secondPhase = service(
                "ADOPT:" + reportSha256,
                fingerprintService,
                manifestLoader,
                seedService,
                backupService,
                migrationService);
        assertThatCode(secondPhase::adoptCurrent).doesNotThrowAnyException();
        verify(migrationService).adoptBaseline();
    }

    /**
     * 创建使用实际 classpath 基线资源的测试服务。
     */
    private static AdoptionService service(
            String confirmation,
            SchemaFingerprintService fingerprintService,
            SchemaFingerprintManifestLoader manifestLoader,
            SeedVerificationService seedService,
            BackupEvidenceService backupService,
            LiquibaseMigrationService migrationService) {
        DbMigratorProperties properties = new DbMigratorProperties(
                "adopt-current",
                "jdbc:mysql://invalid/",
                "root",
                "secret",
                "database/changelog/db.changelog-root.yaml",
                "database/seed/manifest.yaml",
                "database/adoption/baseline-09a29fe.yaml",
                "C:/evidence.yaml",
                24,
                confirmation);
        return new AdoptionService(
                properties,
                fingerprintService,
                manifestLoader,
                seedService,
                backupService,
                migrationService);
    }
}
