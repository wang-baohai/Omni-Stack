package com.omni.dbmigrator.seed;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.test.util.ReflectionTestUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * 本次本地已复现 SRM 默认种子缺陷的受控增量验证入口，仅存在于测试 classpath。
 *
 * <p>仅执行新 auth-0008 的单个 changeSet，不运行整个 migrator 或种子。
 * 读取真实 JDBC 投影并复用生产规范摘要算法，不更新任何业务结果或迁移历史旧行。</p>
 */
@Slf4j
public final class SrmApproverSeedFixture {

    private static final String CHANGELOG = "database/changelog/auth/0008-srm-workflow-approver-permissions.yaml";
    private static final String CHANGE_ID = "auth-0008-srm-workflow-approver-permissions";
    private static final Path PRIVATE_ROOT = Path.of("/validation/test-fixtures/goal-20261022");
    private static final Path EVIDENCE_ROOT = Path.of("/validation/evidence/codex-2026-10-22/wp07-s03");

    private SrmApproverSeedFixture() {
    }

    /**
     * 对原本地数据库执行或只读验证这一个新增权限增量。
     *
     * @param args 只接受 apply 或 verify
     * @throws Exception 任一来源、数据库或断言不匹配
     */
    public static void main(String[] args) throws Exception {
        if (!"2026-10-22".equals(System.getenv("OMNI_GOAL_ALLOW_LOCAL_FIXTURE"))
                || args.length != 1 || !List.of("apply", "verify").contains(args[0])
                || !PRIVATE_ROOT.toRealPath().equals(PRIVATE_ROOT)) {
            throw new IllegalStateException("本地受控增量授权或路径守卫失败");
        }
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        Object loaded = yaml.load(Files.readString(PRIVATE_ROOT.resolve("runtime-secrets.json")));
        Map<?, ?> config = (Map<?, ?>) loaded;
        String url = text(config, "dbUrl");
        if (!"local".equals(text(config, "environment")) || !url.matches("jdbc:mysql://mysql:3306/omni_auth(?:\\?.*)?")) {
            throw new IllegalStateException("只允许原本地认证库");
        }
        Files.createDirectories(EVIDENCE_ROOT);
        List<String> before;
        List<String> userBefore;
        try (Connection connection = connection(config)) {
            guard(connection, text(config, "expectedDbUuid"));
            before = pairs(connection, false);
            userBefore = pairs(connection, true);
        }
        Path original = EVIDENCE_ROOT.resolve("srm-approver-original-before.yaml");
        if (!Files.exists(original)) Files.writeString(original, yaml.dump(Map.of("pairs", before, "user_permissions", userBefore)));

        if ("apply".equals(args[0])) {
            try (Connection connection = connection(config);
                 ClassLoaderResourceAccessor accessor = new ClassLoaderResourceAccessor(SrmApproverSeedFixture.class.getClassLoader())) {
                guard(connection, text(config, "expectedDbUuid"));
                var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
                try (Liquibase liquibase = new Liquibase(CHANGELOG, accessor, database)) {
                    var changes = liquibase.listUnrunChangeSets(new Contexts("auth"), new LabelExpression("srm-approver-fix"));
                    if (changes.size() > 1 || changes.stream().anyMatch(change -> !CHANGE_ID.equals(change.getId()))) {
                        throw new IllegalStateException("增量范围不是唯一指定的新 changeSet");
                    }
                    liquibase.validate();
                    liquibase.update(new Contexts("auth"), new LabelExpression("srm-approver-fix"));
                }
            }
        }
        SeedAssertion assertion = new SeedManifestLoader().load("database/seed/manifest.yaml").assertions().stream()
                .filter(item -> item.id().equals("srm-role-catalog")).findFirst().orElseThrow();
        try (Connection connection = connection(config)) {
            guard(connection, text(config, "expectedDbUuid"));
            connection.setReadOnly(true);
            List<String> after = pairs(connection, false);
            List<String> userAfter = pairs(connection, true);
            if (after.size() != 12 || !userAfter.equals(userBefore)) {
                throw new IllegalStateException("三角色授权对数不是12，或共享 USER 权限发生变化");
            }
            String digest;
            List<String> canonical;
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(assertion.query())) {
                List<?> projected = ReflectionTestUtils.invokeMethod(SeedVerificationService.class, "canonicalRows", rows);
                canonical = Objects.requireNonNull(projected).stream().map(String.class::cast).sorted().toList();
                digest = ReflectionTestUtils.invokeMethod(SeedVerificationService.class, "sha256", String.join("\n", canonical));
            }
            if (canonical.size() != assertion.expectedRows() || !Objects.equals(digest, assertion.expectedSha256())) {
                Files.writeString(EVIDENCE_ROOT.resolve("srm-approver-projection-mismatch.yaml"), yaml.dump(Map.of(
                        "actual_sha256", Objects.requireNonNull(digest), "expected_sha256", assertion.expectedSha256(), "canonical_rows", canonical)));
                throw new IllegalStateException("真实 JDBC 投影与正式断言不符；保留原值，禁止改授权集合修绿");
            }
            String executionType;
            try (var statement = connection.prepareStatement("SELECT EXECTYPE FROM DATABASECHANGELOG WHERE ID=? AND FILENAME=?")) {
                statement.setString(1, CHANGE_ID);
                statement.setString(2, CHANGELOG);
                try (var rows = statement.executeQuery()) {
                    if (!rows.next() || !"EXECUTED".equals(rows.getString(1))) throw new IllegalStateException("新增 changeSet 无真实执行历史");
                    executionType = rows.getString(1);
                    if (rows.next()) throw new IllegalStateException("新增 changeSet 历史身份不唯一");
                }
            }
            List<String> added = after.stream().filter(pair -> !before.contains(pair)).toList();
            Files.writeString(EVIDENCE_ROOT.resolve("srm-approver-seed-" + args[0] + ".yaml"), yaml.dump(Map.of(
                    "operation", args[0], "changelog", CHANGELOG, "before", before, "after", after,
                    "added_pairs", added, "shared_user_unchanged", true, "canonical_rows", canonical,
                    "projection_sha256", Objects.requireNonNull(digest), "execution_type", executionType, "result", "PASS")));
            log.info("SRM 最小审批授权增量已验证，授权对数={}，本次新增={}，投影={}，USER未变", after.size(), added.size(), digest);
        } finally {
            AbandonedConnectionCleanupThread.checkedShutdown();
        }
    }

    private static Connection connection(Map<?, ?> config) throws Exception {
        return DriverManager.getConnection(text(config, "dbUrl"), text(config, "dbUsername"), text(config, "dbPassword"));
    }

    private static void guard(Connection connection, String uuid) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT @@server_uuid,DATABASE()")) {
            if (!rows.next() || !uuid.equals(rows.getString(1)) || !"omni_auth".equals(rows.getString(2))) {
                throw new IllegalStateException("原库身份不匹配");
            }
        }
        // 原历史表必须存在，拒绝由 Liquibase 隐式创建另一套迁移历史。
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM DATABASECHANGELOG")) {
            if (!rows.next() || rows.getInt(1) == 0) throw new IllegalStateException("原迁移历史不存在");
        }
    }

    private static List<String> pairs(Connection connection, boolean userOnly) throws Exception {
        String filter = userOnly ? "r.role_code='USER'"
                : "r.role_code IN ('SRM_MANAGER','SRM_COMPLIANCE','SRM_DIRECTOR') AND p.permission_code IN "
                    + "('workflow','workflow:instance','workflow:task:todo','workflow:approval:complete')";
        List<String> result = new ArrayList<>();
        try (var query = connection.createStatement(); var rows = query.executeQuery(
                "SELECT r.id,p.id,r.role_code,p.permission_code FROM sys_role_permission rp "
                        + "JOIN sys_role r ON r.id=rp.role_id JOIN sys_permission p ON p.id=rp.permission_id "
                        + "WHERE r.tenant_id=(SELECT id FROM sys_tenant WHERE tenant_code='default') "
                        + "AND p.tenant_id=r.tenant_id AND " + filter + " ORDER BY r.role_code,p.permission_code")) {
            while (rows.next()) result.add(rows.getLong(1) + ":" + rows.getLong(2) + ":" + rows.getString(3) + ":" + rows.getString(4));
        }
        return result;
    }

    private static String text(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalStateException("缺少受限配置字段: " + key);
        return text;
    }
}
