package com.omni.dbmigrator.seed;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.omni.dbmigrator.config.DbMigratorProperties;
import com.omni.dbmigrator.migration.LiquibaseMigrationService;

/**
 * 按 manifest 校验正式种子数据，不修改数据库。
 */
@Slf4j
@Service
public class SeedVerificationService {

    /** 空结果的 SHA-256。 */
    private static final String EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    /** 单项断言最大允许返回行数，避免错误查询耗尽内存。 */
    private static final int MAX_ASSERTION_ROWS = 20_000;

    /** 迁移器配置。 */
    private final DbMigratorProperties properties;
    /** manifest 加载器。 */
    private final SeedManifestLoader manifestLoader;
    /** 提供经过校验的目标 JDBC URL。 */
    private final LiquibaseMigrationService migrationService;

    /**
     * 创建种子校验服务。
     *
     * @param properties 配置
     * @param manifestLoader manifest 加载器
     * @param migrationService 数据库迁移服务
     */
    public SeedVerificationService(
            DbMigratorProperties properties,
            SeedManifestLoader manifestLoader,
            LiquibaseMigrationService migrationService) {
        this.properties = properties;
        this.manifestLoader = manifestLoader;
        this.migrationService = migrationService;
    }

    /**
     * 校验 manifest 中全部种子断言。
     *
     * <p>启用模块按清单期望校验；未启用模块必须查询不到任何受管记录，借此发现
     * 项目裁剪后残留的权限、字典、模型或配置。</p>
     */
    public void verifyAll() {
        SeedManifest manifest = manifestLoader.load(properties.seedManifest());
        Map<String, SeedModule> modules = moduleIndex(manifest.modules());
        List<SeedVerificationResult> results = new ArrayList<>();
        int executionFailures = 0;
        for (SeedAssertion assertion : manifest.assertions()) {
            SeedModule module = modules.get(assertion.module());
            int expectedRows = module.enabledByDefault() ? assertion.expectedRows() : 0;
            String expectedSha256 = module.enabledByDefault() ? assertion.expectedSha256() : EMPTY_SHA256;
            SeedSnapshot snapshot;
            try {
                snapshot = execute(assertion);
            } catch (RuntimeException exception) {
                executionFailures++;
                log.error("种子断言执行失败: id={}, reason={}", assertion.id(), exception.getMessage());
                continue;
            }
            boolean passed = expectedRows == snapshot.rows()
                    && expectedSha256.equals(snapshot.sha256());
            results.add(new SeedVerificationResult(
                    assertion.id(),
                    expectedRows,
                    snapshot.rows(),
                    expectedSha256,
                    snapshot.sha256(),
                    passed));
            if (passed) {
                log.info("种子断言通过: id={}, rows={}, sha256={}",
                        assertion.id(), snapshot.rows(), snapshot.sha256());
            } else {
                log.error("种子断言失败: id={}, expectedRows={}, actualRows={}, expectedSha256={}, actualSha256={}",
                        assertion.id(), expectedRows, snapshot.rows(), expectedSha256, snapshot.sha256());
            }
        }
        long failed = results.stream().filter(result -> !result.passed()).count() + executionFailures;
        if (failed > 0) {
            throw new IllegalStateException("种子数据校验失败: manifestVersion=" + manifest.version()
                    + ", failed=" + failed + ", total=" + results.size());
        }
        log.info("种子数据校验完成: manifestVersion={}, assertions={}",
                manifest.version(), results.size());
    }

    /**
     * 执行一个只读断言并生成规范摘要。
     */
    private SeedSnapshot execute(SeedAssertion assertion) {
        try (Connection connection = DriverManager.getConnection(
                migrationService.targetUrl(assertion.database()),
                properties.adminUsername(),
                properties.adminPassword())) {
            connection.setReadOnly(true);
            try (Statement statement = connection.createStatement()) {
                statement.setMaxRows(MAX_ASSERTION_ROWS + 1);
                statement.setQueryTimeout(30);
                try (ResultSet resultSet = statement.executeQuery(assertion.query())) {
                    List<String> rows = canonicalRows(resultSet);
                    if (rows.size() > MAX_ASSERTION_ROWS) {
                        throw new IllegalStateException("种子断言结果超过安全上限: " + assertion.id());
                    }
                    rows.sort(Comparator.naturalOrder());
                    return new SeedSnapshot(rows.size(), sha256(String.join("\n", rows)));
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("执行种子断言失败: id=" + assertion.id(), exception);
        }
    }

    /**
     * 将结果集转成与行顺序无关、与列顺序有关的稳定文本。
     */
    private static List<String> canonicalRows(ResultSet resultSet) throws Exception {
        List<String> rows = new ArrayList<>();
        ResultSetMetaData metadata = resultSet.getMetaData();
        int columns = metadata.getColumnCount();
        while (resultSet.next()) {
            StringBuilder row = new StringBuilder();
            for (int index = 1; index <= columns; index++) {
                if (index > 1) {
                    row.append('|');
                }
                String value = resultSet.getString(index);
                row.append(escape(metadata.getColumnLabel(index).toLowerCase()))
                        .append(':')
                        .append(metadata.getColumnType(index))
                        .append('=')
                        .append(value == null ? "<NULL>" : escape(value));
            }
            rows.add(row.toString());
        }
        return rows;
    }

    /**
     * 转义规范串中的结构字符。
     */
    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("|", "\\|")
                .replace("=", "\\=")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    /**
     * 计算小写十六进制 SHA-256。
     */
    private static String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 构建模块索引。
     */
    private static Map<String, SeedModule> moduleIndex(List<SeedModule> modules) {
        Map<String, SeedModule> result = new HashMap<>();
        for (SeedModule module : modules) {
            result.put(module.id(), module);
        }
        return result;
    }

    /**
     * 一项查询的行数和摘要。
     *
     * @param rows 行数
     * @param sha256 摘要
     */
    private record SeedSnapshot(int rows, String sha256) {
    }
}
