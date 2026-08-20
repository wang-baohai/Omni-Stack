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
 * 为本地和 CI 的登录后 E2E 场景签发短期测试令牌。
 *
 * <p>该类只位于 test classpath，不启动 HTTP 服务，不校验或绕过 CAPTCHA，也不读取用户密码。
 * 它从隔离测试数据库读取已启用用户及授权，并复用当前 Auth Redis 密钥库和生产令牌服务签名。
 * 输出文件必须位于仓库外，由调用方在 Playwright 结束后立即删除。</p>
 */
public final class E2eTokenFixture {

    /** 短期测试令牌有效期，单位秒。 */
    private static final long TOKEN_TTL_SECONDS = 600L;
    /** 管理员角色。 */
    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";
    /** 员工角色。 */
    private static final String EMPLOYEE_ROLE = "EMPLOYEE";
    /** 供应商角色。 */
    private static final String SUPPLIER_ROLE = "SUPPLIER";

    /** 工具类禁止实例化。 */
    private E2eTokenFixture() {
    }

    /**
     * 生成三种 E2E 身份的短期令牌文件。
     *
     * @param args 不接受命令行参数，所有敏感配置必须通过环境变量注入
     * @throws Exception 数据库、Redis、密钥或文件操作失败
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            throw new IllegalArgumentException("E2E Token 夹具不接受命令行参数");
        }

        Path output = validateOutputPath(requiredEnv("E2E_FIXTURE_OUTPUT"));
        Long tenantId = Long.valueOf(optionalEnv("E2E_FIXTURE_TENANT_ID", "1"));

        LettuceConnectionFactory connectionFactory = createRedisConnectionFactory();
        try {
            StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
            redisTemplate.afterPropertiesSet();

            RedisKeyStoreLoader keyStoreLoader = new RedisKeyStoreLoader(redisTemplate);
            ReflectionTestUtils.setField(
                    keyStoreLoader,
                    "encryptKey",
                    requiredEnv("E2E_FIXTURE_JWK_ENCRYPT_KEY"));
            JWKSource<SecurityContext> jwkSource = keyStoreLoader.loadJwkSource();
            JwtTokenServiceImpl tokenService = new JwtTokenServiceImpl(jwkSource, TOKEN_TTL_SECONDS);

            try (Connection connection = openDatabaseConnection()) {
                String adminToken = issueToken(
                        connection,
                        tokenService,
                        tenantId,
                        optionalEnv("E2E_FIXTURE_ADMIN_USERNAME", "admin"),
                        SUPER_ADMIN_ROLE,
                        List.of());
                String employeeToken = issueToken(
                        connection,
                        tokenService,
                        tenantId,
                        optionalEnv("E2E_FIXTURE_EMPLOYEE_USERNAME", "zhangsan"),
                        EMPLOYEE_ROLE,
                        List.of(SUPER_ADMIN_ROLE, SUPPLIER_ROLE));
                String supplierToken = issueToken(
                        connection,
                        tokenService,
                        tenantId,
                        optionalEnv("E2E_FIXTURE_SUPPLIER_USERNAME", "supplier1"),
                        SUPPLIER_ROLE,
                        List.of(SUPER_ADMIN_ROLE));

                writeTokenFile(output, adminToken, employeeToken, supplierToken);
            } finally {
                AbandonedConnectionCleanupThread.checkedShutdown();
            }
        } finally {
            connectionFactory.destroy();
        }
    }

    /**
     * 创建 Redis 连接工厂。
     *
     * @return 已启动的连接工厂
     */
    private static LettuceConnectionFactory createRedisConnectionFactory() {
        String host = optionalEnv("E2E_FIXTURE_REDIS_HOST", "127.0.0.1");
        int port = Integer.parseInt(optionalEnv("E2E_FIXTURE_REDIS_PORT", "6379"));
        int database = Integer.parseInt(optionalEnv("E2E_FIXTURE_REDIS_DATABASE", "0"));
        String password = System.getenv("E2E_FIXTURE_REDIS_PASSWORD");

        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(host, port);
        configuration.setDatabase(database);
        if (password != null && !password.isBlank()) {
            configuration.setPassword(RedisPassword.of(password));
        }

        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        return connectionFactory;
    }

    /**
     * 打开只读身份查询所需的数据库连接。
     *
     * @return JDBC 连接
     * @throws Exception JDBC 驱动或连接失败
     */
    private static Connection openDatabaseConnection() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        Connection connection = DriverManager.getConnection(
                requiredEnv("E2E_FIXTURE_DB_URL"),
                requiredEnv("E2E_FIXTURE_DB_USERNAME"),
                requiredEnv("E2E_FIXTURE_DB_PASSWORD"));
        connection.setReadOnly(true);
        return connection;
    }

    /**
     * 校验用户、角色和禁止角色后签发令牌。
     *
     * @param connection     数据库连接
     * @param tokenService   生产令牌服务
     * @param tenantId       租户 ID
     * @param username       用户名
     * @param requiredRole   必须拥有的角色
     * @param forbiddenRoles 禁止拥有的角色
     * @return 短期 JWT
     * @throws Exception 查询或签发失败
     */
    private static String issueToken(Connection connection,
                                     JwtTokenServiceImpl tokenService,
                                     Long tenantId,
                                     String username,
                                     String requiredRole,
                                     List<String> forbiddenRoles) throws Exception {
        SysUser user = findActiveUser(connection, tenantId, username);
        List<String> roles = queryStrings(connection,
                "SELECT r.role_code FROM sys_role r "
                        + "INNER JOIN sys_user_role ur ON r.id = ur.role_id "
                        + "WHERE ur.user_id = ? AND r.status = 1 ORDER BY r.role_code",
                user.getId());
        if (!roles.contains(requiredRole)) {
            throw new IllegalStateException("E2E 身份缺少要求角色: " + username + "/" + requiredRole);
        }
        for (String forbiddenRole : forbiddenRoles) {
            if (roles.contains(forbiddenRole)) {
                throw new IllegalStateException("E2E 身份包含禁止角色: " + username + "/" + forbiddenRole);
            }
        }

        List<String> permissions = queryStrings(connection,
                "SELECT DISTINCT p.permission_code FROM sys_permission p "
                        + "INNER JOIN sys_role_permission rp ON p.id = rp.permission_id "
                        + "INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id "
                        + "WHERE ur.user_id = ? AND p.status = 1 ORDER BY p.permission_code",
                user.getId());
        return tokenService.generateToken(user, roles, permissions);
    }

    /**
     * 查询指定租户的启用用户。
     *
     * @param connection 数据库连接
     * @param tenantId   租户 ID
     * @param username   用户名
     * @return 用户实体
     * @throws Exception 查询失败或身份不唯一
     */
    private static SysUser findActiveUser(Connection connection, Long tenantId, String username) throws Exception {
        String sql = "SELECT id, tenant_id, username, status FROM sys_user "
                + "WHERE tenant_id = ? AND username = ? AND status = 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, tenantId);
            statement.setString(2, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("未找到启用的 E2E 用户: " + username);
                }
                SysUser user = new SysUser();
                user.setId(resultSet.getLong("id"));
                user.setTenantId(resultSet.getLong("tenant_id"));
                user.setUsername(resultSet.getString("username"));
                user.setStatus(resultSet.getInt("status"));
                if (resultSet.next()) {
                    throw new IllegalStateException("E2E 用户不唯一: " + username);
                }
                return user;
            }
        }
    }

    /**
     * 执行单参数字符串列表查询。
     *
     * @param connection 数据库连接
     * @param sql        参数化 SQL
     * @param userId     用户 ID
     * @return 查询结果
     * @throws Exception 查询失败
     */
    private static List<String> queryStrings(Connection connection, String sql, Long userId) throws Exception {
        List<String> values = new ArrayList<>(32);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    values.add(resultSet.getString(1));
                }
            }
        }
        return List.copyOf(values);
    }

    /**
     * 校验输出文件必须位于仓库外且尚不存在。
     *
     * @param rawPath 原始路径
     * @return 规范化绝对路径
     */
    private static Path validateOutputPath(String rawPath) {
        Path output = Path.of(rawPath).toAbsolutePath().normalize();
        Path workspace = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (output.startsWith(workspace)) {
            throw new IllegalArgumentException("E2E Token 文件禁止写入仓库");
        }
        if (Files.exists(output)) {
            throw new IllegalArgumentException("E2E Token 输出文件已存在");
        }
        Path parent = output.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IllegalArgumentException("E2E Token 输出目录不存在");
        }
        return output;
    }

    /**
     * 原子创建令牌 JSON 文件，不在日志中输出内容。
     *
     * @param output        输出路径
     * @param adminToken    管理员令牌
     * @param employeeToken 员工令牌
     * @param supplierToken 供应商令牌
     * @throws Exception 文件写入失败
     */
    private static void writeTokenFile(Path output,
                                       String adminToken,
                                       String employeeToken,
                                       String supplierToken) throws Exception {
        String json = "{\n"
                + "  \"adminToken\": \"" + adminToken + "\",\n"
                + "  \"employeeToken\": \"" + employeeToken + "\",\n"
                + "  \"supplierToken\": \"" + supplierToken + "\"\n"
                + "}\n";
        Files.writeString(
                output,
                json,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
    }

    /**
     * 读取必填环境变量。
     *
     * @param name 变量名
     * @return 非空变量值
     */
    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少必填环境变量: " + name);
        }
        return value;
    }

    /**
     * 读取可选环境变量。
     *
     * @param name         变量名
     * @param defaultValue 默认值
     * @return 变量值或默认值
     */
    private static String optionalEnv(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
