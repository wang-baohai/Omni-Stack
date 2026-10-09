package com.omni.dbmigrator.adoption;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.omni.dbmigrator.config.DbMigratorProperties;
import com.omni.dbmigrator.migration.LiquibaseMigrationService;
import com.omni.dbmigrator.seed.SeedVerificationService;

/**
 * 编排既有数据库双阶段安全接管。
 */
@Slf4j
@Service
public class AdoptionService {

    /** 配置。 */
    private final DbMigratorProperties properties;
    /** 结构指纹。 */
    private final SchemaFingerprintService fingerprintService;
    /** 指纹基线加载器。 */
    private final SchemaFingerprintManifestLoader fingerprintManifestLoader;
    /** 种子校验。 */
    private final SeedVerificationService seedVerificationService;
    /** 备份证据。 */
    private final BackupEvidenceService backupEvidenceService;
    /** Liquibase 迁移服务。 */
    private final LiquibaseMigrationService migrationService;

    /**
     * 创建接管编排器。
     */
    public AdoptionService(
            DbMigratorProperties properties,
            SchemaFingerprintService fingerprintService,
            SchemaFingerprintManifestLoader fingerprintManifestLoader,
            SeedVerificationService seedVerificationService,
            BackupEvidenceService backupEvidenceService,
            LiquibaseMigrationService migrationService) {
        this.properties = properties;
        this.fingerprintService = fingerprintService;
        this.fingerprintManifestLoader = fingerprintManifestLoader;
        this.seedVerificationService = seedVerificationService;
        this.backupEvidenceService = backupEvidenceService;
        this.migrationService = migrationService;
    }

    /**
     * 执行只读预检；确认摘要匹配时才同步 baseline 历史。
     */
    public void adoptCurrent() {
        SchemaFingerprintManifest baseline = fingerprintManifestLoader.load(properties.adoptionBaseline());
        fingerprintService.verifyAll();
        seedVerificationService.verifyAll();
        BackupVerification backup = backupEvidenceService.verify(baseline.baselineCommit());
        String reportSha256 = reportSha256(baseline, backup);
        String requiredConfirmation = "ADOPT:" + reportSha256;
        if (!requiredConfirmation.equals(properties.adoptConfirmation())) {
            log.warn("接管预检通过但未写入；请人工复核后设置 DB_MIGRATOR_ADOPT_CONFIRMATION={}",
                    requiredConfirmation);
            throw new IllegalStateException("接管需要与本次报告完全匹配的二阶段人工确认: reportSha256="
                    + reportSha256);
        }
        migrationService.adoptBaseline();
        log.info("既有数据库 baseline 接管成功: baseline={}, reportSha256={}",
                baseline.baselineCommit(), reportSha256);
    }

    /**
     * 生成不含凭据且对全部关键输入敏感的确认摘要。
     */
    private String reportSha256(SchemaFingerprintManifest baseline, BackupVerification backup) {
        String canonical = String.join("|",
                "adoption-report-v1",
                baseline.baselineCommit(),
                baseline.algorithm(),
                resourceSha256(properties.adoptionBaseline()),
                resourceSha256(properties.seedManifest()),
                backup.evidenceSha256(),
                backup.backupSha256(),
                backup.sourceServerUuid(),
                backup.adoptionServerUuid(),
                backup.createdAt().toString(),
                backup.restoreVerifiedAt().toString());
        return sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算 classpath 资源摘要。
     */
    private static String resourceSha256(String resourcePath) {
        try (InputStream inputStream = AdoptionService.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("找不到接管报告资源: " + resourcePath);
            }
            return sha256(inputStream.readAllBytes());
        } catch (Exception exception) {
            throw new IllegalStateException("计算接管报告资源摘要失败", exception);
        }
    }

    /**
     * 计算 SHA-256。
     */
    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException("计算接管报告摘要失败", exception);
        }
    }
}
