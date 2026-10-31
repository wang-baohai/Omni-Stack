package com.omni.auth.e2e;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;

/**
 * 原WP-28.S02专用账号230的一行审批候选配置；仅由容器测试classpath以源码方式运行。
 * 不更新共享角色、业务状态、资产占用或审批结果。
 */
public final class LocalAssetCandidateScopeFixture {
    private static final Path PRIVATE = Path.of("/validation/test-fixtures/goal-20261022");
    private static final Path EVIDENCE = Path.of("/validation/evidence/codex-2026-10-22/wp28-s02");

    private LocalAssetCandidateScopeFixture() {
    }

    /**
     * 仅接受apply/audit；核对固定本地库、既有专用身份、单角色及组织后事务化补齐一行。
     *
     * @param args 固定操作
     * @throws Exception 任一身份、数据库或事务守卫失败
     */
    public static void main(String[] args) throws Exception {
        if (!"2026-10-22".equals(System.getenv("OMNI_GOAL_ALLOW_LOCAL_FIXTURE"))
                || !Files.exists(Path.of("/.dockerenv")) || Files.exists(Path.of("/var/run/docker.sock"))
                || args.length != 1 || !List.of("apply", "audit").contains(args[0])
                || !PRIVATE.toRealPath().equals(PRIVATE)) {
            throw new IllegalStateException("仅允许无Docker socket的本地固定测试环境");
        }
        ObjectMapper mapper = new ObjectMapper();
        JsonNode config = mapper.readTree(Files.readString(PRIVATE.resolve("runtime-secrets.json")));
        JsonNode identities = mapper.readTree(Files.readString(PRIVATE.resolve("identities.json")));
        if (!"local".equals(config.path("environment").asText())
                || !"redis".equals(config.path("redisHost").asText())
                || !config.path("dbUrl").asText().matches("jdbc:mysql://mysql:3306/omni_auth(?:\\?.*)?")) {
            throw new IllegalStateException("夹具仅允许原本地环境和认证库");
        }
        JsonNode actor = identities.path("accounts").path("assetApprover");
        if (!"goal_20261022".equals(identities.path("namespace").asText())
                || identities.path("tenant_id").asInt() != 1 || actor.path("id").asLong() != 230
                || !actor.path("create_attempted").asBoolean()
                || !"goal_20261028_assetapprover".equals(actor.path("username").asText())
                || identities.path("organizations").path("work").path("id").asLong() != 606
                || identities.path("organizations").path("b").path("id").asLong() != 605) {
            throw new IllegalStateException("拒绝接管其他身份或组织");
        }
        Files.createDirectories(EVIDENCE);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("at", Instant.now().toString()); result.put("operation", args[0]);
        result.put("original_step", "WP-28.S02"); result.put("table", "sys_user_role_scope");
        result.put("tenant_id", 1); result.put("user_id", 230); result.put("role_id", 41);
        result.put("unit_id", 606); result.put("scope_mode", "SAME_UNIT");
        try (Connection connection = DriverManager.getConnection(config.path("dbUrl").asText(),
                config.path("dbUsername").asText(), config.path("dbPassword").asText())) {
            try (var query = connection.prepareStatement("SELECT @@server_uuid,DATABASE()"); var row = query.executeQuery()) {
                if (!row.next() || !config.path("expectedDbUuid").asText().equals(row.getString(1))
                        || !"omni_auth".equals(row.getString(2))) {
                    throw new IllegalStateException("本地数据库身份守卫失败");
                }
            }
            // 固定本次兄弟单位的只读候选清单，用于后续隔离配置恢复负例，不更改任何授权。
            try (var query = connection.prepareStatement("SELECT DISTINCT urs.user_id FROM sys_user_role_scope urs "
                    + "JOIN sys_role r ON urs.role_id=r.id JOIN sys_user u ON urs.user_id=u.id "
                    + "WHERE r.role_code='ASSET_MANAGER' AND r.tenant_id=1 AND r.status=1 "
                    + "AND urs.unit_id=605 AND urs.status=1 AND u.status=1"); var row = query.executeQuery()) {
                List<Long> candidateIds = new ArrayList<>();
                while (row.next()) candidateIds.add(row.getLong(1));
                result.put("owned_peer_unit_605_active_candidate_ids", candidateIds);
                result.put("owned_peer_candidate_query_read_only", true);
            }
            connection.setAutoCommit(false);
            try {
                try (var query = connection.prepareStatement("SELECT u.username,u.primary_unit_id,u.status,r.id,r.role_code,r.status "
                        + "FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id "
                        + "JOIN sys_role r ON r.id=ur.role_id WHERE u.id=230 AND u.tenant_id=1 AND r.tenant_id=1 FOR UPDATE");
                        var row = query.executeQuery()) {
                    if (!row.next() || !"goal_20261028_assetapprover".equals(row.getString(1))
                            || row.getLong(2) != 606 || row.getInt(3) != 1 || row.getLong(4) != 41
                            || !"ASSET_MANAGER".equals(row.getString(5)) || row.getInt(6) != 1 || row.next()) {
                        throw new IllegalStateException("实际账号必须仍为启用的固定单角色与主组织");
                    }
                }
                try (var query = connection.prepareStatement("SELECT name,status FROM sys_org_unit WHERE id=606 AND tenant_id=1 FOR UPDATE");
                        var row = query.executeQuery()) {
                    if (!row.next() || !"goal_20261022_work".equals(row.getString(1)) || row.getInt(2) != 1) {
                        throw new IllegalStateException("实际组织归属不符");
                    }
                }
                Map<String, Object> before = scope(connection);
                result.put("before", before);
                Path original = EVIDENCE.resolve("asset-approver-scope-original-before.json");
                if (!Files.exists(original)) Files.writeString(original, mapper.writeValueAsString(result));
                boolean created = before == null && "apply".equals(args[0]);
                if (created) {
                    try (var insert = connection.prepareStatement("INSERT INTO sys_user_role_scope "
                            + "(tenant_id,user_id,role_id,unit_id,scope_mode,status) VALUES (1,230,41,606,'SAME_UNIT',1)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        if (insert.executeUpdate() != 1) throw new IllegalStateException("候选配置写入行数不符");
                    }
                }
                Map<String, Object> after = scope(connection);
                result.put("after", after); result.put("created_by_this_run", created);
                result.put("rollback", created ? Map.of("operation", "DELETE_OWNED_ROW_ONLY", "table", "sys_user_role_scope",
                        "id", after.get("id"), "tenant_id", 1, "user_id", 230, "role_id", 41, "unit_id", 606) : "NO_CHANGE");
                result.put("business_state_modified", false); result.put("shared_role_modified", false);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback(); throw exception;
            }
        } finally {
            AbandonedConnectionCleanupThread.checkedShutdown();
        }
        result.put("fixture_source_sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(Path.of("scripts/fixtures/com/omni/auth/e2e/LocalAssetCandidateScopeFixture.java")))));
        Files.writeString(EVIDENCE.resolve("asset-approver-scope-" + Instant.now().toEpochMilli() + ".json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }

    private static Map<String, Object> scope(Connection connection) throws Exception {
        try (var query = connection.prepareStatement("SELECT id,unit_id,scope_mode,status FROM sys_user_role_scope "
                + "WHERE tenant_id=1 AND user_id=230 AND role_id=41 FOR UPDATE"); var row = query.executeQuery()) {
            if (!row.next()) return null;
            Map<String, Object> found = Map.of("id", row.getLong(1), "unit_id", row.getLong(2),
                    "scope_mode", row.getString(3), "status", row.getInt(4));
            if (row.getLong(2) != 606 || !"SAME_UNIT".equals(row.getString(3)) || row.getInt(4) != 1 || row.next()) {
                throw new IllegalStateException("本次账号已有意外范围，拒绝盲改");
            }
            return found;
        }
    }
}
