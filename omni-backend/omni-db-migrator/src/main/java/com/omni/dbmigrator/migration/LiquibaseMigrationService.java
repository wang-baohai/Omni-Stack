package com.omni.dbmigrator.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.database.OfflineConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.omni.dbmigrator.config.DbMigratorProperties;

/**
 * 负责校验、查询和执行多数据库 Liquibase changelog。
 */
@Slf4j
@Service
public class LiquibaseMigrationService {

    /** 数据库迁移器配置。 */
    private final DbMigratorProperties properties;

    /**
     * 创建迁移服务并校验管理连接格式。
     *
     * @param properties 迁移器配置
     */
    public LiquibaseMigrationService(DbMigratorProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验平台根 changelog 和所有目标 changelog。
     */
    public void validateAll() {
        withOfflineLiquibase(properties.changelogRoot(), liquibase -> {
            liquibase.validate();
            log.info("平台 changelog 校验通过: {}", properties.changelogRoot());
        });
        for (MigrationTarget target : MigrationTargetCatalog.targets()) {
            withOfflineLiquibase(target.changelog(), liquibase -> {
                liquibase.validate();
                log.info("目标 changelog 校验通过: target={}, path={}", target.id(), target.changelog());
            });
        }
    }

    /**
     * 输出各目标数据库的待执行 changeSet 数量。
     */
    public void status() {
        requireAdminConfig();
        requireHistoryTable("omni_auth", "platform");
        withLiquibase(targetUrl("omni_auth"), properties.changelogRoot(), liquibase -> {
            List<ChangeSet> pending = liquibase.listUnrunChangeSets(new Contexts("platform"), new LabelExpression());
            log.info("平台迁移状态: pending={}", pending.size());
        });
        for (MigrationTarget target : MigrationTargetCatalog.targets()) {
            requireHistoryTable(target.database(), target.id());
            withLiquibase(targetUrl(target.database()), target.changelog(), liquibase -> {
                List<ChangeSet> pending = liquibase.listUnrunChangeSets(new Contexts(), new LabelExpression());
                log.info("数据库迁移状态: target={}, pending={}", target.id(), pending.size());
            });
        }
    }

    /**
     * 先执行平台迁移，再按目录顺序迁移九个目标数据库。
     */
    public void migrate() {
        requireAdminConfig();
        for (MigrationTarget target : MigrationTargetCatalog.targets()) {
            DatabaseState state = inspectState(target.database());
            if (state.exists() && state.userTableCount() > 0 && !state.historyTableExists()) {
                throw new IllegalStateException("目标库包含未接管结构，禁止直接 migrate: target=" + target.id()
                        + "；请先备份并执行 adopt-current");
            }
        }
        ensureBootstrapDatabase();
        withLiquibase(targetUrl("omni_auth"), properties.changelogRoot(),
                liquibase -> liquibase.update(new Contexts("platform"), new LabelExpression()));
        for (MigrationTarget target : MigrationTargetCatalog.targets()) {
            withLiquibase(targetUrl(target.database()), target.changelog(),
                    liquibase -> liquibase.update(new Contexts(target.id()), new LabelExpression()));
            log.info("数据库迁移完成: target={}", target.id());
        }
    }

    /**
     * 根据管理连接构建目标数据库 JDBC URL。
     *
     * @param database 数据库名
     * @return 目标 JDBC URL
     */
    public String targetUrl(String database) {
        requireAdminConfig();
        if (database == null || !database.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("目标数据库名不合法");
        }
        int queryIndex = properties.adminUrl().indexOf('?');
        String base = queryIndex >= 0 ? properties.adminUrl().substring(0, queryIndex) : properties.adminUrl();
        String query = queryIndex >= 0 ? properties.adminUrl().substring(queryIndex) : "";
        return base + database + query;
    }

    /**
     * 使用独立连接执行一个 Liquibase 操作。
     *
     * @param url       JDBC URL
     * @param changelog changelog 路径
     * @param action    Liquibase 操作
     */
    private void withLiquibase(String url, String changelog, LiquibaseAction action) {
        try (Connection connection = DriverManager.getConnection(
                url,
                properties.adminUsername(),
                properties.adminPassword());
             ClassLoaderResourceAccessor accessor = new ClassLoaderResourceAccessor(
                     LiquibaseMigrationService.class.getClassLoader())) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase(changelog, accessor, database)) {
                action.accept(liquibase);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("数据库迁移操作失败: changelog=" + changelog, exception);
        }
    }

    /**
     * 使用离线 MySQL 元数据校验 changelog，保证 validate 命令不会创建历史表或修改目标库。
     *
     * @param changelog changelog 路径
     * @param action    Liquibase 操作
     */
    private void withOfflineLiquibase(String changelog, LiquibaseAction action) {
        try (ClassLoaderResourceAccessor accessor = new ClassLoaderResourceAccessor(
                LiquibaseMigrationService.class.getClassLoader());
             OfflineConnection connection = new OfflineConnection("offline:mysql", accessor)) {
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(connection);
            try (Liquibase liquibase = new Liquibase(changelog, accessor, database)) {
                action.accept(liquibase);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("数据库 changelog 离线校验失败: changelog=" + changelog, exception);
        }
    }

    /**
     * Fresh 环境中先创建承载平台迁移历史的 Auth 数据库。
     *
     * <p>这是 Liquibase 在 MySQL 无默认数据库连接上无法创建 DATABASECHANGELOG 的最小引导步骤；
     * 后续平台 changeSet 会以相同 DDL 幂等确认该数据库及其余目标库。</p>
     */
    private void ensureBootstrapDatabase() {
        try (Connection connection = DriverManager.getConnection(
                properties.adminUrl(),
                properties.adminUsername(),
                properties.adminPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS omni_auth "
                    + "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        } catch (Exception exception) {
            throw new IllegalStateException("无法创建 Liquibase 引导数据库 omni_auth", exception);
        }
    }

    /**
     * 检查目标库是否存在、是否已有业务表以及是否已建立 Liquibase 历史表。
     *
     * @param database 数据库名
     * @return 数据库状态
     */
    private DatabaseState inspectState(String database) {
        String sql = "SELECT "
                + "EXISTS(SELECT 1 FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = ?), "
                + "(SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? "
                + "AND TABLE_NAME NOT IN ('DATABASECHANGELOG', 'DATABASECHANGELOGLOCK')), "
                + "EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? "
                + "AND TABLE_NAME = 'DATABASECHANGELOG')";
        try (Connection connection = DriverManager.getConnection(
                properties.adminUrl(),
                properties.adminUsername(),
                properties.adminPassword());
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, database);
            statement.setString(2, database);
            statement.setString(3, database);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("无法读取目标数据库状态: " + database);
                }
                return new DatabaseState(
                        resultSet.getBoolean(1),
                        resultSet.getInt(2),
                        resultSet.getBoolean(3));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("读取目标数据库状态失败: " + database, exception);
        }
    }

    /**
     * status 只允许读取已经迁移或接管的数据库，避免 Liquibase 隐式创建历史表。
     *
     * @param database 数据库名
     * @param targetId 目标 ID
     */
    private void requireHistoryTable(String database, String targetId) {
        DatabaseState state = inspectState(database);
        if (!state.exists() || !state.historyTableExists()) {
            throw new IllegalStateException("目标库尚未建立 Liquibase 历史，status 已停止且未修改数据库: target="
                    + targetId);
        }
    }

    /**
     * 数据库命令必须显式提供管理连接；离线 validate 不需要。
     */
    private void requireAdminConfig() {
        if (properties.adminUrl() == null || properties.adminUrl().isBlank()
                || properties.adminUsername() == null || properties.adminUsername().isBlank()
                || properties.adminPassword() == null || properties.adminPassword().isBlank()) {
            throw new IllegalArgumentException("status/migrate/adopt-current/verify-seed 必须配置数据库管理连接");
        }
        validateAdminUrl(properties.adminUrl());
    }

    /**
     * 管理 URL 必须以数据库分隔斜杠结尾，避免误把平台 changeSet 写入业务库。
     *
     * @param adminUrl 管理 JDBC URL
     */
    private static void validateAdminUrl(String adminUrl) {
        int queryIndex = adminUrl.indexOf('?');
        String base = queryIndex >= 0 ? adminUrl.substring(0, queryIndex) : adminUrl;
        if (!base.matches("jdbc:mysql://[^/]+/")) {
            throw new IllegalArgumentException("DB_MIGRATOR_ADMIN_URL 必须连接 MySQL 服务器根路径并以 / 结尾");
        }
    }

    /**
     * 允许向统一资源管理方法传入会抛出受检异常的 Liquibase 操作。
     */
    @FunctionalInterface
    private interface LiquibaseAction {

        /**
         * 执行 Liquibase 操作。
         *
         * @param liquibase Liquibase 实例
         * @throws Exception 操作失败
         */
        void accept(Liquibase liquibase) throws Exception;
    }

    /**
     * 目标数据库迁移前状态。
     *
     * @param exists             数据库是否存在
     * @param userTableCount     排除 Liquibase 历史后的表数
     * @param historyTableExists 是否存在 DATABASECHANGELOG
     */
    private record DatabaseState(boolean exists, int userTableCount, boolean historyTableExists) {
    }
}
