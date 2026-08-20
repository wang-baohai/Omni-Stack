package com.omni.dbmigrator.adoption;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.omni.dbmigrator.config.DbMigratorProperties;
import com.omni.dbmigrator.migration.LiquibaseMigrationService;

/**
 * 计算并校验既有 MySQL 数据库的强结构指纹。
 */
@Slf4j
@Service
public class SchemaFingerprintService {

    /** 迁移器配置。 */
    private final DbMigratorProperties properties;
    /** 指纹清单加载器。 */
    private final SchemaFingerprintManifestLoader manifestLoader;
    /** 提供经过校验的目标 URL。 */
    private final LiquibaseMigrationService migrationService;

    /**
     * 创建指纹服务。
     *
     * @param properties 配置
     * @param manifestLoader 清单加载器
     * @param migrationService 迁移服务
     */
    public SchemaFingerprintService(
            DbMigratorProperties properties,
            SchemaFingerprintManifestLoader manifestLoader,
            LiquibaseMigrationService migrationService) {
        this.properties = properties;
        this.manifestLoader = manifestLoader;
        this.migrationService = migrationService;
    }

    /**
     * 校验全部目标与冻结基线完全一致。
     */
    public void verifyAll() {
        SchemaFingerprintManifest manifest = manifestLoader.load(properties.adoptionBaseline());
        int failures = 0;
        for (SchemaFingerprintTarget target : manifest.targets()) {
            SchemaFingerprint actual = fingerprint(target);
            boolean passed = target.expectedTables() == actual.tables()
                    && target.expectedViews() == actual.views()
                    && target.expectedTriggers() == actual.triggers()
                    && target.expectedRoutines() == actual.routines()
                    && target.expectedSha256().equals(actual.sha256());
            if (passed) {
                log.info("接管结构指纹通过: target={}, tables={}, views={}, triggers={}, routines={}, sha256={}",
                        target.id(), actual.tables(), actual.views(), actual.triggers(), actual.routines(),
                        actual.sha256());
            } else {
                failures++;
                log.error("接管结构指纹失败: target={}, expected={}/{}/{}/{}/{}, actual={}/{}/{}/{}/{}",
                        target.id(), target.expectedTables(), target.expectedViews(), target.expectedTriggers(),
                        target.expectedRoutines(), target.expectedSha256(), actual.tables(), actual.views(),
                        actual.triggers(), actual.routines(), actual.sha256());
            }
        }
        if (failures > 0) {
            throw new IllegalStateException("接管结构指纹校验失败: baseline=" + manifest.baselineCommit()
                    + ", failed=" + failures + ", total=" + manifest.targets().size());
        }
        log.info("接管结构指纹全部通过: baseline={}, targets={}",
                manifest.baselineCommit(), manifest.targets().size());
    }

    /**
     * 计算单库指纹。
     */
    private SchemaFingerprint fingerprint(SchemaFingerprintTarget target) {
        try (Connection connection = DriverManager.getConnection(
                migrationService.targetUrl(target.database()),
                properties.adminUsername(),
                properties.adminPassword())) {
            connection.setReadOnly(true);
            List<String> parts = new ArrayList<>();
            int tables = appendSchema(connection, target.database(), parts);
            int views = appendViews(connection, target.database(), parts);
            int triggers = appendTriggers(connection, target.database(), parts);
            int routines = appendRoutines(connection, target.database(), parts);
            appendVendorFacts(connection, target.id(), parts);
            return new SchemaFingerprint(
                    target.database(), tables, views, triggers, routines, sha256(String.join("\n", parts)));
        } catch (Exception exception) {
            throw new IllegalStateException("计算接管结构指纹失败: target=" + target.id(), exception);
        }
    }

    /**
     * 追加可跨备份恢复稳定比较的表、列、索引和约束语义。
     */
    private static int appendSchema(Connection connection, String database, List<String> parts) throws Exception {
        String excluded = " AND TABLE_NAME NOT IN ('DATABASECHANGELOG', 'DATABASECHANGELOGLOCK') ";
        int tables = appendRows(connection,
                "SELECT TABLE_NAME, ENGINE, ROW_FORMAT, TABLE_COLLATION, CREATE_OPTIONS, TABLE_COMMENT "
                        + "FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? "
                        + "AND TABLE_TYPE = 'BASE TABLE'" + excluded
                        + "ORDER BY BINARY TABLE_NAME",
                database, "TABLE", parts);
        appendRows(connection,
                "SELECT TABLE_NAME, ORDINAL_POSITION, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, "
                        + "COLUMN_DEFAULT, EXTRA, CHARACTER_SET_NAME, COLLATION_NAME, GENERATION_EXPRESSION, SRS_ID "
                        + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ?" + excluded
                        + "ORDER BY BINARY TABLE_NAME, ORDINAL_POSITION",
                database, "COLUMN", parts);
        appendRows(connection,
                "SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME, COLLATION, "
                        + "SUB_PART, PACKED, NULLABLE, INDEX_TYPE, COMMENT, INDEX_COMMENT, IS_VISIBLE, EXPRESSION "
                        + "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = ?" + excluded
                        + "ORDER BY BINARY TABLE_NAME, BINARY INDEX_NAME, SEQ_IN_INDEX",
                database, "INDEX", parts);
        appendRows(connection,
                "SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE, ENFORCED "
                        + "FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = ?" + excluded
                        + "ORDER BY BINARY TABLE_NAME, BINARY CONSTRAINT_NAME",
                database, "CONSTRAINT", parts);
        appendRows(connection,
                "SELECT TABLE_NAME, CONSTRAINT_NAME, COLUMN_NAME, ORDINAL_POSITION, POSITION_IN_UNIQUE_CONSTRAINT, "
                        + "REFERENCED_TABLE_SCHEMA, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME "
                        + "FROM information_schema.KEY_COLUMN_USAGE WHERE CONSTRAINT_SCHEMA = ?" + excluded
                        + "ORDER BY BINARY TABLE_NAME, BINARY CONSTRAINT_NAME, ORDINAL_POSITION",
                database, "KEY", parts);
        appendRows(connection,
                "SELECT TABLE_NAME, CONSTRAINT_NAME, UNIQUE_CONSTRAINT_SCHEMA, UNIQUE_CONSTRAINT_NAME, "
                        + "MATCH_OPTION, UPDATE_RULE, DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS "
                        + "WHERE CONSTRAINT_SCHEMA = ?" + excluded
                        + "ORDER BY BINARY TABLE_NAME, BINARY CONSTRAINT_NAME",
                database, "REFERENCE", parts);
        appendRows(connection,
                "SELECT constraints_current.TABLE_NAME, checks.CONSTRAINT_NAME, checks.CHECK_CLAUSE, "
                        + "constraints_current.ENFORCED FROM information_schema.CHECK_CONSTRAINTS checks "
                        + "JOIN information_schema.TABLE_CONSTRAINTS constraints_current "
                        + "ON constraints_current.CONSTRAINT_SCHEMA = checks.CONSTRAINT_SCHEMA "
                        + "AND constraints_current.CONSTRAINT_NAME = checks.CONSTRAINT_NAME "
                        + "WHERE checks.CONSTRAINT_SCHEMA = ?" + excluded
                        + "ORDER BY BINARY constraints_current.TABLE_NAME, BINARY checks.CONSTRAINT_NAME",
                database, "CHECK", parts);
        return tables;
    }

    /**
     * 追加视图定义。
     */
    private static int appendViews(Connection connection, String database, List<String> parts) throws Exception {
        String sql = "SELECT TABLE_NAME, VIEW_DEFINITION, CHECK_OPTION, IS_UPDATABLE, SECURITY_TYPE, "
                + "CHARACTER_SET_CLIENT, COLLATION_CONNECTION FROM information_schema.VIEWS "
                + "WHERE TABLE_SCHEMA = ? ORDER BY BINARY TABLE_NAME";
        return appendRows(connection, sql, database, "VIEW", parts);
    }

    /**
     * 追加触发器定义。
     */
    private static int appendTriggers(Connection connection, String database, List<String> parts) throws Exception {
        String sql = "SELECT TRIGGER_NAME, EVENT_MANIPULATION, EVENT_OBJECT_TABLE, ACTION_ORDER, "
                + "ACTION_CONDITION, ACTION_STATEMENT, ACTION_ORIENTATION, ACTION_TIMING, SQL_MODE, "
                + "CHARACTER_SET_CLIENT, COLLATION_CONNECTION, DATABASE_COLLATION "
                + "FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = ? ORDER BY BINARY TRIGGER_NAME";
        return appendRows(connection, sql, database, "TRIGGER", parts);
    }

    /**
     * 追加存储过程和函数定义。
     */
    private static int appendRoutines(Connection connection, String database, List<String> parts) throws Exception {
        String sql = "SELECT ROUTINE_NAME, ROUTINE_TYPE, DATA_TYPE, SQL_DATA_ACCESS, SECURITY_TYPE, "
                + "ROUTINE_DEFINITION, SQL_MODE, CHARACTER_SET_CLIENT, COLLATION_CONNECTION, DATABASE_COLLATION "
                + "FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = ? "
                + "ORDER BY BINARY ROUTINE_TYPE, BINARY ROUTINE_NAME";
        return appendRows(connection, sql, database, "ROUTINE", parts);
    }

    /**
     * 将 vendor 数据库内的版本事实加入结构指纹。
     */
    private static void appendVendorFacts(Connection connection, String targetId, List<String> parts)
            throws Exception {
        if (!"workflow".equals(targetId)) {
            return;
        }
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT NAME_, VALUE_ FROM ACT_GE_PROPERTY "
                             + "WHERE NAME_ IN ('schema.version', 'schema.history') ORDER BY BINARY NAME_")) {
            while (resultSet.next()) {
                parts.add("VENDOR_FACT|" + normalize(resultSet.getString(1))
                        + "|" + normalize(resultSet.getString(2)));
            }
        }
    }

    /**
     * 执行信息模式查询并按返回列顺序追加规范行。
     */
    private static int appendRows(
            Connection connection,
            String sql,
            String database,
            String prefix,
            List<String> parts) throws Exception {
        int count = 0;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, database);
            try (ResultSet resultSet = statement.executeQuery()) {
                int columns = resultSet.getMetaData().getColumnCount();
                while (resultSet.next()) {
                    StringBuilder row = new StringBuilder(prefix);
                    for (int index = 1; index <= columns; index++) {
                        row.append('|');
                        String value = resultSet.getString(index);
                        row.append(value == null ? "<NULL>" : normalize(value));
                    }
                    parts.add(row.toString());
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 统一换行和不可打印控制字符。
     */
    private static String normalize(String value) {
        if (value == null) {
            return "<NULL>";
        }
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder result = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(codePoint -> {
            if ((codePoint >= 0 && codePoint < 32 && codePoint != '\n' && codePoint != '\t')
                    || (codePoint >= 127 && codePoint <= 159)) {
                result.append('\uFFFD');
            } else {
                result.appendCodePoint(codePoint);
            }
        });
        return result.toString();
    }

    /**
     * 计算小写十六进制 SHA-256。
     */
    private static String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
