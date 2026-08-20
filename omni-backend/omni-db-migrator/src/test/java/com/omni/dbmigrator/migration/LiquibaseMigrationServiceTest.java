package com.omni.dbmigrator.migration;

import org.junit.jupiter.api.Test;

import com.omni.dbmigrator.config.DbMigratorProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Liquibase 多数据库迁移服务测试。
 */
class LiquibaseMigrationServiceTest {

    /**
     * 应从服务器根连接构建保留参数的目标库 URL。
     */
    @Test
    void should_build_target_url_when_admin_url_targets_server_root() {
        LiquibaseMigrationService service = new LiquibaseMigrationService(properties(
                "jdbc:mysql://127.0.0.1:3306/?useSSL=false"));

        assertThat(service.targetUrl("omni_auth"))
                .isEqualTo("jdbc:mysql://127.0.0.1:3306/omni_auth?useSSL=false");
    }

    /**
     * 应拒绝连接到具体数据库的管理 URL。
     */
    @Test
    void should_reject_admin_url_when_database_is_already_selected() {
        LiquibaseMigrationService service = new LiquibaseMigrationService(properties(
                "jdbc:mysql://127.0.0.1:3306/omni_auth?useSSL=false"));

        assertThatThrownBy(() -> service.targetUrl("omni_base"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("根路径");
    }

    /**
     * 应在无数据库连接时离线校验全部 YAML。
     */
    @Test
    void should_validate_all_changelogs_when_database_is_offline() {
        LiquibaseMigrationService service = new LiquibaseMigrationService(new DbMigratorProperties(
                "validate",
                "",
                "",
                "",
                "database/changelog/db.changelog-root.yaml",
                "database/seed/manifest.yaml",
                ""));

        service.validateAll();
    }

    /**
     * 创建测试配置。
     *
     * @param adminUrl 管理 URL
     * @return 迁移配置
     */
    private static DbMigratorProperties properties(String adminUrl) {
        return new DbMigratorProperties(
                "validate",
                adminUrl,
                "test-user",
                "test-password",
                "database/changelog/db.changelog-root.yaml",
                "database/seed/manifest.yaml",
                "");
    }
}
