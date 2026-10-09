package com.omni.auth.e2e;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.omni.auth.entity.SysUser;
import com.omni.auth.security.RedisKeyStoreLoader;
import com.omni.auth.service.impl.JwtTokenServiceImpl;

/**
 * WP-008 受控短期身份夹具（test classpath）。
 *
 * <p>复用 E2eTokenFixture 机制：真实 Redis JWK keystore + 生产 JwtTokenServiceImpl 签名，
 * 数据库只读校验启用用户与角色白/黑名单；额外强制目标 DB UUID 断言（防误签 M/F 串环境）。
 * 身份清单经 {@code WP008_FIXTURE_IDENTITIES} 注入，格式
 * {@code alias:tenantId:username[:requiredRole[:forbiddenRole,forbidden2]]} 以分号分隔。
 * 输出 JSON 只允许写到仓库外路径，不打印任何 token。</p>
 */
public final class Wp008TokenFixture {

    /** 与既有受控夹具一致的短期 TTL（秒）。 */
    private static final long TOKEN_TTL_SECONDS = 1200L;

    /** 工具类禁止实例化。 */
    private Wp008TokenFixture() {
    }

    /**
     * 主入口。
     *
     * @param args 不接受参数，配置一律走环境变量
     * @throws Exception 任一守卫或签发失败
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            throw new IllegalArgumentException("Wp008TokenFixture 不接受命令行参数");
        }
        Path output = validateOutputPath(requiredEnv("WP008_FIXTURE_OUTPUT"));
        String expectedUuid = requiredEnv("WP008_FIXTURE_EXPECT_DB_UUID");

        LettuceConnectionFactory factory = createRedisConnectionFactory();
        try {
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            redis.afterPropertiesSet();
            RedisKeyStoreLoader loader = new RedisKeyStoreLoader(redis);
            ReflectionTestUtils.setField(loader, "encryptKey", requiredEnv("WP008_FIXTURE_JWK_ENCRYPT_KEY"));
            JWKSource<SecurityContext> jwkSource = loader.loadJwkSource();
            JwtTokenServiceImpl tokenService = new JwtTokenServiceImpl(jwkSource, TOKEN_TTL_SECONDS);

            Map<String, String> tokens = new LinkedHashMap<>();
            try (Connection connection = openDatabaseConnection(expectedUuid)) {
                for (String spec : requiredEnv("WP008_FIXTURE_IDENTITIES").split(";")) {
                    if (spec.isBlank()) {
                        continue;
                    }
                    String[] parts = spec.trim().split(":");
                    if (parts.length < 3) {
                        throw new IllegalArgumentException("身份规格非法: " + spec);
                    }
                    String alias = parts[0];
                    Long tenantId = Long.valueOf(parts[1]);
                    String username = parts[2];
                    String requiredRole = parts.length > 3 && !parts[3].isBlank() ? parts[3] : null;
                    List<String> forbidden = parts.length > 4 && !parts[4].isBlank()
                            ? List.of(parts[4].split(",")) : List.of();
                    tokens.put(alias, issueToken(connection, tokenService, tenantId, username, requiredRole, forbidden));
                }
            }
            writeJson(output, tokens);
            System.out.println("WP008-FIXTURE-OK identities=" + tokens.keySet());
        } finally {
            factory.destroy();
            AbandonedConnectionCleanupThread.checkedShutdown();
        }
    }

    private static LettuceConnectionFactory createRedisConnectionFactory() {
        RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration(
                optionalEnv("WP008_FIXTURE_REDIS_HOST", "127.0.0.1"),
                Integer.parseInt(optionalEnv("WP008_FIXTURE_REDIS_PORT", "6379")));
        String pw = System.getenv("WP008_FIXTURE_REDIS_PASSWORD");
        if (pw != null && !pw.isBlank()) {
            cfg.setPassword(RedisPassword.of(pw));
        }
        LettuceConnectionFactory f = new LettuceConnectionFactory(cfg);
        f.afterPropertiesSet();
        f.start();
        return f;
    }

    private static Connection openDatabaseConnection(String expectedUuid) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        Connection c = DriverManager.getConnection(requiredEnv("WP008_FIXTURE_DB_URL"),
                requiredEnv("WP008_FIXTURE_DB_USERNAME"), requiredEnv("WP008_FIXTURE_DB_PASSWORD"));
        c.setReadOnly(true);
        try (PreparedStatement ps = c.prepareStatement("SELECT @@server_uuid");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            if (!rs.getString(1).equalsIgnoreCase(expectedUuid)) {
                throw new IllegalStateException("DB UUID 守卫失败，拒绝签发");
            }
        }
        return c;
    }

    private static String issueToken(Connection connection, JwtTokenServiceImpl tokenService, Long tenantId,
                                     String username, String requiredRole, List<String> forbiddenRoles) throws Exception {
        SysUser user = findActiveUser(connection, tenantId, username);
        List<String> roles = queryStrings(connection,
                "SELECT r.role_code FROM omni_auth.sys_role r "
                        + "INNER JOIN omni_auth.sys_user_role ur ON r.id = ur.role_id "
                        + "WHERE ur.user_id = ? AND r.status = 1 ORDER BY r.role_code", user.getId());
        if (requiredRole != null && !roles.contains(requiredRole)) {
            throw new IllegalStateException("身份缺少要求角色: " + username + "/" + requiredRole);
        }
        for (String f : forbiddenRoles) {
            if (roles.contains(f)) {
                throw new IllegalStateException("身份包含禁止角色: " + username + "/" + f);
            }
        }
        List<String> permissions = queryStrings(connection,
                "SELECT DISTINCT p.permission_code FROM omni_auth.sys_permission p "
                        + "INNER JOIN omni_auth.sys_role_permission rp ON p.id = rp.permission_id "
                        + "INNER JOIN omni_auth.sys_user_role ur ON rp.role_id = ur.role_id "
                        + "WHERE ur.user_id = ? AND p.status = 1 ORDER BY p.permission_code", user.getId());
        return tokenService.generateToken(user, roles, permissions);
    }

    private static SysUser findActiveUser(Connection connection, Long tenantId, String username) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, tenant_id, username, status FROM omni_auth.sys_user "
                        + "WHERE tenant_id = ? AND username = ? AND status = 1")) {
            ps.setLong(1, tenantId);
            ps.setString(2, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("未找到启用用户: " + username + "@tenant" + tenantId);
                }
                SysUser u = new SysUser();
                u.setId(rs.getLong("id"));
                u.setTenantId(rs.getLong("tenant_id"));
                u.setUsername(rs.getString("username"));
                u.setStatus(rs.getInt("status"));
                if (rs.next()) {
                    throw new IllegalStateException("用户不唯一: " + username);
                }
                return u;
            }
        }
    }

    private static List<String> queryStrings(Connection connection, String sql, Long userId) throws Exception {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }

    private static Path validateOutputPath(String raw) {
        Path output = Path.of(raw).toAbsolutePath().normalize();
        Path workspace = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (output.startsWith(workspace)) {
            throw new IllegalArgumentException("token 输出禁止写入仓库内: " + output);
        }
        if (Files.exists(output)) {
            throw new IllegalArgumentException("输出文件已存在，拒绝覆盖");
        }
        if (output.getParent() == null || !Files.isDirectory(output.getParent())) {
            throw new IllegalArgumentException("输出目录不存在");
        }
        return output;
    }

    private static void writeJson(Path output, Map<String, String> tokens) throws Exception {
        StringBuilder sb = new StringBuilder("{\n");
        List<String> entries = new ArrayList<>();
        tokens.forEach((k, v) -> entries.add("  \"" + k + "\": \"" + v + "\""));
        sb.append(String.join(",\n", entries)).append("\n}\n");
        Files.writeString(output, sb.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static String requiredEnv(String name) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("缺少环境变量: " + name);
        }
        return v;
    }

    private static String optionalEnv(String name, String def) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? def : v;
    }
}
