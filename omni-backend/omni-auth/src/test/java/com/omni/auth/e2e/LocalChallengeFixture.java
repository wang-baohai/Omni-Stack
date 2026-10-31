package com.omni.auth.e2e;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 本地 Goal 专用 challenge 夹具，仅存在于测试 classpath，不启动服务或替换生产校验器。
 *
 * <p>只写本次命名空间内的短期 Redis 键；不读取真实验证码。答案由固定验证卷内的随机种子派生，
 * 仅写权限受限文件。实际 HTTP 登录仍使用生产验证码校验器、密码验证、租户与锁定规则。</p>
 */
@Slf4j
public final class LocalChallengeFixture {

    private static final Path PRIVATE_ROOT = Path.of("/validation/test-fixtures/goal-20261022");
    private static final String AUTHORIZATION = "2026-10-22";

    private LocalChallengeFixture() {
    }

    /**
     * 建立一个受控短期 challenge，不输出答案、Redis 口令或用户会话。
     *
     * @param args 场景标签及有效秒数，均不含秘密
     * @throws Exception 守卫、连接或文件操作失败
     */
    public static void main(String[] args) throws Exception {
        if (!AUTHORIZATION.equals(System.getenv("OMNI_GOAL_ALLOW_LOCAL_FIXTURE"))) {
            throw new IllegalStateException("仅允许本次显式授权的本地测试夹具");
        }
        if (args.length != 2) {
            throw new IllegalArgumentException("参数必须为场景标签和 TTL 秒数");
        }
        String label = checkedLabel(args[0]);
        int ttl = checkedTtl(Integer.parseInt(args[1]));
        Path root = PRIVATE_ROOT.toRealPath();
        if (!root.equals(PRIVATE_ROOT) || Files.isSymbolicLink(PRIVATE_ROOT)) {
            throw new IllegalStateException("测试秘密根路径不得逃逸或使用符号链接");
        }
        Path configPath = checkedFile(root, "runtime-secrets.json");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode config = mapper.readTree(Files.readString(configPath, StandardCharsets.UTF_8));
        checkedEnvironment(config.path("environment").asText(), config.path("redisHost").asText(),
                config.path("dbUrl").asText());
        String seed = config.path("challengeSeed").asText();
        if (!seed.matches("[a-f0-9]{64}")) {
            throw new IllegalStateException("缺少卷内专用随机种子");
        }

        // 在任何 Redis 写入之前确认原库身份；此连接全程只读。
        try (var connection = DriverManager.getConnection(config.path("dbUrl").asText(),
                config.path("dbUsername").asText(), config.path("dbPassword").asText())) {
            connection.setReadOnly(true);
            try (var statement = connection.prepareStatement("SELECT @@server_uuid, DATABASE()");
                 var rows = statement.executeQuery()) {
                if (!rows.next() || !config.path("expectedDbUuid").asText().equals(rows.getString(1))
                        || !"omni_auth".equals(rows.getString(2))) {
                    throw new IllegalStateException("原本地数据库身份守卫失败");
                }
            }
        } finally {
            AbandonedConnectionCleanupThread.checkedShutdown();
        }

        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration("redis", 6379);
        redisConfig.setPassword(RedisPassword.of(config.path("redisPassword").asText()));
        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig);
        factory.afterPropertiesSet();
        factory.start();
        try {
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            redis.afterPropertiesSet();
            if (!Boolean.TRUE.equals(redis.hasKey("sys:jwk:keystore"))) {
                throw new IllegalStateException("原环境密钥库缺失，拒绝创建或轮换生产密钥");
            }
            String key = "goal-20261022-" + UUID.randomUUID();
            String answer = answerFor(seed, key);
            Path output = checkedFile(root, "challenge-" + label + ".json");
            redis.opsForValue().set("captcha:" + key, answer, Duration.ofSeconds(ttl));
            Files.writeString(output, mapper.writeValueAsString(Map.of(
                    "captchaKey", key, "captchaCode", answer, "ttl_seconds", ttl,
                    "created_at", Instant.now().toString(), "identity_source", "test-fixture")), StandardCharsets.UTF_8);
            Files.setPosixFilePermissions(output, PosixFilePermissions.fromString("rw-------"));
            log.info("受控 challenge 已建立，标签={}，TTL={} 秒；未输出答案", label, ttl);
        } finally {
            factory.destroy();
        }
    }

    /** 检查标签，禁止将用户输入作为目录或命令片段。 */
    static String checkedLabel(String label) {
        if (label == null || !label.matches("[a-z][a-z0-9-]{0,47}")) {
            throw new IllegalArgumentException("场景标签非法");
        }
        return label;
    }

    /** TTL 不得无限延长或关闭到期语义。 */
    static int checkedTtl(int ttl) {
        if (ttl < 1 || ttl > 300) {
            throw new IllegalArgumentException("测试 challenge TTL 必须为 1 至 300 秒");
        }
        return ttl;
    }

    /** 仅接受本次已登记的本地服务地址及原认证库。 */
    static void checkedEnvironment(String environment, String redisHost, String databaseUrl) {
        if (!"local".equals(environment) || !"redis".equals(redisHost)
                || !databaseUrl.matches("jdbc:mysql://mysql:3306/omni_auth(?:\\?.*)?")) {
            throw new IllegalStateException("夹具仅允许原本地环境、Redis 和认证库");
        }
    }

    /** 用卷内随机种子为已知测试键确定性派生答案，不硬编码答案。 */
    static String answerFor(String seed, String key) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((seed + ":" + key).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest).substring(0, 4);
    }

    /** 固定根内的目标文件也不得是符号链接。 */
    private static Path checkedFile(Path root, String name) {
        Path path = root.resolve(name).normalize();
        if (!path.getParent().equals(root) || Files.isSymbolicLink(path)
                || (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(path))) {
            throw new IllegalStateException("测试秘密文件路径不安全");
        }
        return path;
    }
}
