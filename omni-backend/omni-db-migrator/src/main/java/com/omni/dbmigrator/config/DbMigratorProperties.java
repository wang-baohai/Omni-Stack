package com.omni.dbmigrator.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 数据库迁移器配置。
 *
 * @param command           执行命令
 * @param adminUrl          MySQL 管理连接，必须连接服务器根路径而非具体数据库
 * @param adminUsername     管理用户名
 * @param adminPassword     管理密码
 * @param changelogRoot     根 changelog classpath 路径
 * @param seedManifest      种子清单 classpath 路径
 * @param adoptConfirmation 接管现有库时的人工确认串
 */
@Validated
@ConfigurationProperties(prefix = "omni.db-migrator")
public record DbMigratorProperties(
        @NotBlank String command,
        String adminUrl,
        String adminUsername,
        String adminPassword,
        @NotBlank String changelogRoot,
        @NotBlank String seedManifest,
        String adoptConfirmation) {
}
