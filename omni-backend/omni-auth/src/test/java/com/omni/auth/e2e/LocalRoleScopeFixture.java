package com.omni.auth.e2e;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;
import lombok.extern.slf4j.Slf4j;

/**
 * 本地 Goal 五名专用审批候选人的最小配置夹具，仅进入测试 classpath。
 *
 * <p>正式接口没有角色作用域写入口；本夹具只写 sys_user_role_scope，严格核对原库、单角色身份和组织。
 * 不写业务状态、审批记录、共享角色或其他用户；事务前后证据及新行精确回退保存在固定验证卷。</p>
 */
@Slf4j
public final class LocalRoleScopeFixture {

    private static final Path PRIVATE_ROOT = Path.of("/validation/test-fixtures/goal-20261022");
    private static final Path EVIDENCE_ROOT = Path.of("/validation/evidence/codex-2026-10-22/wp07-s03");
    private static final List<ScopeSpec> SPECS = List.of(
            new ScopeSpec("procManager", "PROCUREMENT_MANAGER", "work"),
            new ScopeSpec("srmManager", "SRM_MANAGER", "a"),
            new ScopeSpec("srmCompliance", "SRM_COMPLIANCE", "a"),
            new ScopeSpec("srmDirector", "SRM_DIRECTOR", "root"),
            new ScopeSpec("assetManager", "ASSET_MANAGER", "work"));

    private LocalRoleScopeFixture() {
    }

    /**
     * 核对并幂等补齐本次五行候选配置，不接受任意 SQL 或用户列表。
     *
     * @param args 只接受 apply 或 audit
     * @throws Exception 任一守卫或事务失败
     */
    public static void main(String[] args) throws Exception {
        if (!"2026-10-22".equals(System.getenv("OMNI_GOAL_ALLOW_LOCAL_FIXTURE"))
                || args.length != 1 || !List.of("apply", "audit").contains(args[0])) {
            throw new IllegalStateException("缺少本次本地配置夹具授权或操作非法");
        }
        if (!PRIVATE_ROOT.toRealPath().equals(PRIVATE_ROOT)) {
            throw new IllegalStateException("固定秘密根不得逃逸");
        }
        ObjectMapper mapper = new ObjectMapper();
        JsonNode config = mapper.readTree(Files.readString(PRIVATE_ROOT.resolve("runtime-secrets.json")));
        JsonNode state = mapper.readTree(Files.readString(PRIVATE_ROOT.resolve("identities.json")));
        LocalChallengeFixture.checkedEnvironment(config.path("environment").asText(),
                config.path("redisHost").asText(), config.path("dbUrl").asText());
        if (!"goal_20261022".equals(state.path("namespace").asText()) || state.path("tenant_id").asInt() != 1) {
            throw new IllegalStateException("测试身份归属不符");
        }
        Files.createDirectories(EVIDENCE_ROOT);
        List<Map<String, Object>> records = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(config.path("dbUrl").asText(),
                config.path("dbUsername").asText(), config.path("dbPassword").asText())) {
            try (var query = connection.prepareStatement("SELECT @@server_uuid, DATABASE()");
                 var row = query.executeQuery()) {
                if (!row.next() || !config.path("expectedDbUuid").asText().equals(row.getString(1))
                        || !"omni_auth".equals(row.getString(2))) {
                    throw new IllegalStateException("本地数据库身份守卫失败");
                }
            }
            connection.setAutoCommit(false);
            try {
                for (ScopeSpec spec : SPECS) {
                    JsonNode account = state.path("accounts").path(spec.alias());
                    checkedIdentity(spec.alias(), account.path("username").asText(), account.path("create_attempted").asBoolean());
                    long userId = account.path("id").asLong();
                    long primaryUnitId = state.path("organizations").path("work").path("id").asLong();
                    long unitId = "root".equals(spec.unit()) ? 1
                            : state.path("organizations").path(spec.unit()).path("id").asLong();
                    long roleId = requireRole(connection, userId, account.path("username").asText(), spec.role(), primaryUnitId);
                    requireUnit(connection, unitId, spec.unit());
                    Map<String, Object> before = scopeRow(connection, userId, roleId);
                    if (before != null && (!Long.valueOf(unitId).equals(before.get("unit_id"))
                            || !"SAME_UNIT".equals(before.get("scope_mode")) || !Integer.valueOf(1).equals(before.get("status")))) {
                        throw new IllegalStateException("本次专用身份存在意外候选范围，拒绝盲改");
                    }
                    Map<String, Object> record = new LinkedHashMap<>();
                    record.put("alias", spec.alias()); record.put("tenant_id", 1L); record.put("user_id", userId);
                    record.put("role_id", roleId); record.put("role_code", spec.role()); record.put("unit_id", unitId);
                    record.put("before", before); record.put("before_sha256", digest(mapper.writeValueAsBytes(before)));
                    records.add(record);
                }
                // 写入前保全本次事实；后续重跑不覆盖最初的事务前状态。
                Path original = EVIDENCE_ROOT.resolve("candidate-scope-original-before.json");
                if (!Files.exists(original)) {
                    Files.writeString(original, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(records), StandardCharsets.UTF_8);
                }
                for (Map<String, Object> record : records) {
                    boolean created = record.get("before") == null && "apply".equals(args[0]);
                    if (created) {
                        try (var insert = connection.prepareStatement(
                                "INSERT INTO sys_user_role_scope (tenant_id,user_id,role_id,unit_id,scope_mode,status) "
                                        + "VALUES (1,?,?,?,'SAME_UNIT',1)", Statement.RETURN_GENERATED_KEYS)) {
                            insert.setLong(1, (Long) record.get("user_id"));
                            insert.setLong(2, (Long) record.get("role_id"));
                            insert.setLong(3, (Long) record.get("unit_id"));
                            if (insert.executeUpdate() != 1) throw new IllegalStateException("候选配置写入数异常");
                        }
                    }
                    Map<String, Object> after = scopeRow(connection, (Long) record.get("user_id"), (Long) record.get("role_id"));
                    record.put("after", after); record.put("created_by_this_run", created);
                    record.put("after_sha256", digest(mapper.writeValueAsBytes(after)));
                    record.put("rollback", created ? Map.of("table", "sys_user_role_scope", "id", after.get("id"),
                            "tenant_id", 1L, "user_id", record.get("user_id"), "operation", "DELETE_OWNED_ROW_ONLY") : "NO_CHANGE");
                }
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        } finally {
            AbandonedConnectionCleanupThread.checkedShutdown();
        }
        Map<String, Object> result = Map.of("at", Instant.now().toString(), "operation", args[0],
                "runtime", "local Docker", "table", "sys_user_role_scope", "objects", records);
        String content = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result) + "\n";
        Files.writeString(EVIDENCE_ROOT.resolve("candidate-scope-run-" + Instant.now().toEpochMilli() + ".json"), content);
        Files.writeString(EVIDENCE_ROOT.resolve("candidate-scope-config.json"), content);
        log.info("本次候选配置核对完成，身份数={}，新增行数={}，操作={}", records.size(),
                records.stream().filter(row -> Boolean.TRUE.equals(row.get("created_by_this_run"))).count(), args[0]);
    }

    /** 只允许正式接口已经尝试创建的本次固定身份，不接管其他账号。 */
    static void checkedIdentity(String alias, String username, boolean createdByGoal) {
        if (SPECS.stream().noneMatch(spec -> spec.alias().equals(alias)) || !createdByGoal
                || !("goal_20261022_" + alias.toLowerCase(java.util.Locale.ROOT)).equals(username)) {
            throw new IllegalStateException("不是本次允许的专用候选身份");
        }
    }

    private static long requireRole(Connection connection, long userId, String username, String role, long primaryUnitId) throws Exception {
        try (var query = connection.prepareStatement(
                "SELECT r.id,r.role_code,u.username,u.primary_unit_id FROM sys_user u "
                        + "JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id "
                        + "WHERE u.id=? AND u.tenant_id=1 AND u.status=1 AND r.tenant_id=1 AND r.status=1 FOR UPDATE")) {
            query.setLong(1, userId);
            try (var rows = query.executeQuery()) {
                if (!rows.next() || !role.equals(rows.getString(2)) || !username.equals(rows.getString(3))
                        || primaryUnitId <= 0 || primaryUnitId != rows.getLong(4)) {
                    throw new IllegalStateException("专用身份的实际角色、租户或主组织不符");
                }
                long roleId = rows.getLong(1);
                if (rows.next()) throw new IllegalStateException("候选人必须是已核实的最小单角色身份");
                return roleId;
            }
        }
    }

    private static void requireUnit(Connection connection, long unitId, String key) throws Exception {
        try (var query = connection.prepareStatement("SELECT name,parent_id FROM sys_org_unit WHERE id=? AND tenant_id=1 AND status=1")) {
            query.setLong(1, unitId);
            try (var rows = query.executeQuery()) {
                if (!rows.next() || ("root".equals(key) ? unitId != 1 || rows.getLong(2) != 0
                        : !("goal_20261022_" + key).equals(rows.getString(1)))) {
                    throw new IllegalStateException("候选作用域不属于允许的组织");
                }
            }
        }
    }

    private static Map<String, Object> scopeRow(Connection connection, long userId, long roleId) throws Exception {
        try (var query = connection.prepareStatement(
                "SELECT id,unit_id,scope_mode,status FROM sys_user_role_scope WHERE tenant_id=1 AND user_id=? AND role_id=? FOR UPDATE")) {
            query.setLong(1, userId); query.setLong(2, roleId);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) return null;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rows.getLong(1)); row.put("unit_id", rows.getLong(2));
                row.put("scope_mode", rows.getString(3)); row.put("status", rows.getInt(4));
                if (rows.next()) throw new IllegalStateException("候选范围超过本次最小一行配置");
                return row;
            }
        }
    }

    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /** 固定允许的候选角色及其组织锚点，不接受任意角色输入。 */
    private record ScopeSpec(String alias, String role, String unit) {
    }
}
