package com.omni.dbmigrator.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 数据库迁移器配置。
 *
 * @param command           执行命令
 * @param adminUrl          MySQL 管理连接，必须连接服务器根路径而非具体数据库
 * @param adminUsername     管理用户名
 * @param adminPassword     管理密码
 * @param appUsername       业务数据库应用用户名
 * @param appPassword       业务数据库应用密码
 * @param changelogRoot     根 changelog classpath 路径
 * @param seedManifest      种子清单 classpath 路径
 * @param adoptionBaseline  既有数据库接管基线 classpath 路径
 * @param backupEvidence    接管时必须提供的外部备份证据文件
 * @param backupMaxAgeHours 备份证据允许的最大时效（小时）
 * @param adoptConfirmation 接管现有库时的人工确认串
 */
@Validated
@ConfigurationProperties(prefix = "omni.db-migrator")
public record DbMigratorProperties(
        @NotBlank String command,
        String adminUrl,
        String adminUsername,
        String adminPassword,
        String appUsername,
        String appPassword,
        @NotBlank String changelogRoot,
        @NotBlank String seedManifest,
        @NotBlank String adoptionBaseline,
        String backupEvidence,
        @Min(1) long backupMaxAgeHours,
        String adoptConfirmation) {
}
