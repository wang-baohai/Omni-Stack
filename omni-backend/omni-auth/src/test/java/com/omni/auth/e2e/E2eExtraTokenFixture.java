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
import java.util.List;

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
 * WP007 附加令牌签发：为 zhaoliu 等额外身份签发测试令牌。
 */
public final class E2eExtraTokenFixture {

    private static final long TOKEN_TTL_SECONDS = 1200L;

    private E2eExtraTokenFixture() {
    }

    /** 仓库根目录标记，用于防止令牌文件写入仓库内。 */
    private static final String REPO_MARKER = "omni-backend";

    public static void main(String[] args) throws Exception {
        Path output = Path.of(System.getenv("E2E_FIXTURE_OUTPUT")).toAbsolutePath().normalize();
        validateOutputPath(output);
        Long tenantId = Long.valueOf(System.getenv().getOrDefault("E2E_FIXTURE_TENANT_ID", "1"));

        LettuceConnectionFactory cf = new LettuceConnectionFactory(buildRedisConfig());
        cf.afterPropertiesSet();
        cf.start();
        try {
            StringRedisTemplate redis = new StringRedisTemplate(cf);
            redis.afterPropertiesSet();
            RedisKeyStoreLoader loader = new RedisKeyStoreLoader(redis);
            ReflectionTestUtils.setField(loader, "encryptKey",
                    System.getenv("E2E_FIXTURE_JWK_ENCRYPT_KEY"));
            JWKSource<SecurityContext> jwk = loader.loadJwkSource();
            JwtTokenServiceImpl tokenService = new JwtTokenServiceImpl(jwk, TOKEN_TTL_SECONDS);

            try (Connection conn = openDb()) {
                String zhaoliuToken = issueSimple(conn, tokenService, tenantId, "zhaoliu");
                String json = "{\n"
                        + "  \"zhaoliuToken\": \"" + zhaoliuToken + "\"\n"
                        + "}\n";
                Files.writeString(output, json, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                System.out.println("WP007 extra token written to " + output);
            } finally {
                AbandonedConnectionCleanupThread.checkedShutdown();
            }
        } finally {
            cf.destroy();
        }
    }

    private static String issueSimple(Connection conn, JwtTokenServiceImpl tokenService,
                                       Long tenantId, String username) throws Exception {
        SysUser user = findUser(conn, tenantId, username);
        List<String> roles = queryStrings(conn,
                "SELECT r.role_code FROM sys_role r "
                        + "INNER JOIN sys_user_role ur ON r.id = ur.role_id "
                        + "WHERE ur.user_id = ? AND r.status = 1 ORDER BY r.role_code",
                user.getId());
        List<String> permissions = queryStrings(conn,
                "SELECT DISTINCT p.permission_code FROM sys_permission p "
                        + "INNER JOIN sys_role_permission rp ON p.id = rp.permission_id "
                        + "INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id "
                        + "WHERE ur.user_id = ? AND p.status = 1 ORDER BY p.permission_code",
                user.getId());
        return tokenService.generateToken(user, roles, permissions);
    }

    private static SysUser findUser(Connection conn, Long tenantId, String username) throws Exception {
        String sql = "SELECT id, tenant_id, username, status FROM sys_user WHERE tenant_id = ? AND username = ? AND status = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, tenantId);
            ps.setString(2, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("User not found: " + username);
                SysUser u = new SysUser();
                u.setId(rs.getLong("id"));
                u.setTenantId(rs.getLong("tenant_id"));
                u.setUsername(rs.getString("username"));
                u.setStatus(rs.getInt("status"));
                return u;
            }
        }
    }

    private static List<String> queryStrings(Connection conn, String sql, Long userId) throws Exception {
        List<String> vals = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) vals.add(rs.getString(1));
            }
        }
        return List.copyOf(vals);
    }

    /**
     * 校验输出路径：必须在仓库外，不得包含仓库内标识符；父目录必须存在。
     * CREATE_NEW 不能单独作为目录保护。
     */
    private static void validateOutputPath(Path output) {
        String pathStr = output.toString();
        if (pathStr.contains(REPO_MARKER)) {
            throw new IllegalStateException(
                    "Output path must be outside repository: " + output);
        }
        if (output.getFileName() == null || output.getParent() == null) {
            throw new IllegalStateException(
                    "Output path must have parent directory: " + output);
        }
        if (!Files.isDirectory(output.getParent())) {
            throw new IllegalStateException(
                    "Output parent directory does not exist: " + output.getParent());
        }
    }

    private static RedisStandaloneConfiguration buildRedisConfig() {
        String host = System.getenv("E2E_FIXTURE_REDIS_HOST");
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("E2E_FIXTURE_REDIS_HOST must be set explicitly");
        }
        int port = Integer.parseInt(System.getenv().getOrDefault("E2E_FIXTURE_REDIS_PORT", "6379"));
        int db = Integer.parseInt(System.getenv().getOrDefault("E2E_FIXTURE_REDIS_DATABASE", "0"));
        String pwd = System.getenv("E2E_FIXTURE_REDIS_PASSWORD");
        RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration(host, port);
        cfg.setDatabase(db);
        if (pwd != null && !pwd.isBlank()) cfg.setPassword(RedisPassword.of(pwd));
        return cfg;
    }

    private static Connection openDb() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        return DriverManager.getConnection(
                System.getenv("E2E_FIXTURE_DB_URL"),
                System.getenv("E2E_FIXTURE_DB_USERNAME"),
                System.getenv("E2E_FIXTURE_DB_PASSWORD"));
    }
}
