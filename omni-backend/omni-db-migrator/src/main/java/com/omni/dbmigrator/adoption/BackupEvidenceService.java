package com.omni.dbmigrator.adoption;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.omni.dbmigrator.config.DbMigratorProperties;

/**
 * 校验接管所需的备份文件及隔离恢复证据。
 */
@Service
public class BackupEvidenceService {

    /** SHA-256 格式。 */
    private static final String SHA256_PATTERN = "[a-f0-9]{64}";
    /** MySQL server_uuid 格式。 */
    private static final String UUID_PATTERN = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-"
            + "[a-fA-F0-9]{4}-[a-fA-F0-9]{12}";
    /** 接受少量机器时钟偏差。 */
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

    /** 迁移配置。 */
    private final DbMigratorProperties properties;

    /**
     * 创建证据服务。
     *
     * @param properties 配置
     */
    public BackupEvidenceService(DbMigratorProperties properties) {
        this.properties = properties;
    }

    /**
     * 核验外部证据、备份文件摘要、时效和源数据库身份。
     *
     * @param baselineCommit 当前接管基线提交
     * @return 已核验摘要
     */
    public BackupVerification verify(String baselineCommit) {
        Path evidencePath = requiredAbsoluteFile(properties.backupEvidence(), "备份证据");
        BackupEvidence evidence = load(evidencePath);
        validateEvidence(evidence, baselineCommit);

        Path backupPath = requiredAbsoluteFile(evidence.backupFile(), "数据库备份");
        String actualBackupSha256 = sha256(backupPath);
        if (!evidence.backupSha256().equals(actualBackupSha256)) {
            throw new IllegalStateException("数据库备份 SHA-256 与证据不一致");
        }
        String currentServerUuid = currentServerUuid();
        if (!evidence.adoptionServerUuid().equalsIgnoreCase(currentServerUuid)) {
            throw new IllegalStateException("备份证据 adoption server_uuid 与当前数据库不一致");
        }
        return new BackupVerification(
                sha256(evidencePath), actualBackupSha256, evidence.sourceServerUuid(), currentServerUuid,
                evidence.createdAt(), evidence.restoreVerifiedAt());
    }

    /**
     * 安全加载 YAML 证据。
     */
    private static BackupEvidence load(Path path) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(inputStream);
            if (!(loaded instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("备份证据根节点必须是对象");
            }
            return new BackupEvidence(
                    string(map, "version"),
                    string(map, "baselineCommit"),
                    string(map, "sourceServerUuid"),
                    string(map, "adoptionServerUuid"),
                    instant(map, "createdAt"),
                    string(map, "backupFile"),
                    string(map, "backupSha256"),
                    instant(map, "restoreVerifiedAt"),
                    string(map, "restoreServerUuid"),
                    string(map, "restoreBaselineCommit"));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("读取备份证据失败", exception);
        }
    }

    /**
     * 校验证据字段与时间关系。
     */
    private void validateEvidence(BackupEvidence evidence, String baselineCommit) {
        validateEvidence(evidence, baselineCommit, Instant.now(), properties.backupMaxAgeHours());
    }

    /**
     * 校验证据字段与时间关系，独立参数便于对拒绝路径做确定性测试。
     *
     * @param evidence          备份证据
     * @param baselineCommit    当前基线提交
     * @param now               当前时间
     * @param backupMaxAgeHours 最大备份时效
     */
    static void validateEvidence(
            BackupEvidence evidence,
            String baselineCommit,
            Instant now,
            long backupMaxAgeHours) {
        if (!"1".equals(evidence.version())
                || !baselineCommit.equals(evidence.baselineCommit())
                || !baselineCommit.equals(evidence.restoreBaselineCommit())) {
            throw new IllegalArgumentException("备份证据版本或基线提交不匹配");
        }
        if (!evidence.backupSha256().matches(SHA256_PATTERN)
                || !evidence.sourceServerUuid().matches(UUID_PATTERN)
                || !evidence.adoptionServerUuid().matches(UUID_PATTERN)
                || !evidence.restoreServerUuid().matches(UUID_PATTERN)
                || evidence.sourceServerUuid().equalsIgnoreCase(evidence.restoreServerUuid())
                || (!evidence.adoptionServerUuid().equalsIgnoreCase(evidence.sourceServerUuid())
                && !evidence.adoptionServerUuid().equalsIgnoreCase(evidence.restoreServerUuid()))) {
            throw new IllegalArgumentException("备份摘要或源/恢复 server_uuid 不合法");
        }
        if (evidence.createdAt().isAfter(now.plus(CLOCK_SKEW))
                || evidence.restoreVerifiedAt().isAfter(now.plus(CLOCK_SKEW))
                || evidence.restoreVerifiedAt().isBefore(evidence.createdAt())
                || evidence.createdAt().isBefore(now.minus(Duration.ofHours(backupMaxAgeHours)))) {
            throw new IllegalArgumentException("备份证据时间关系不合法或已超过允许时效");
        }
    }

    /**
     * 查询当前 MySQL server_uuid。
     */
    private String currentServerUuid() {
        try (Connection connection = DriverManager.getConnection(
                properties.adminUrl(), properties.adminUsername(), properties.adminPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT @@server_uuid")) {
            if (!resultSet.next()) {
                throw new IllegalStateException("无法读取 MySQL server_uuid");
            }
            return resultSet.getString(1);
        } catch (Exception exception) {
            throw new IllegalStateException("读取 MySQL server_uuid 失败", exception);
        }
    }

    /**
     * 要求配置为存在的绝对普通文件。
     */
    private static Path requiredAbsoluteFile(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "文件路径不能为空");
        }
        Path path = Path.of(value).normalize();
        if (!path.isAbsolute() || !Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException(label + "必须是可读的绝对普通文件");
        }
        final long size;
        try {
            size = Files.size(path);
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取" + label + "文件大小", exception);
        }
        if (size <= 0) {
            throw new IllegalArgumentException(label + "文件不能为空");
        }
        return path;
    }

    /**
     * 流式计算文件 SHA-256，避免将大型备份读入内存。
     */
    private static String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = Files.newInputStream(path);
                 DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
                digestInputStream.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("计算文件 SHA-256 失败", exception);
        }
    }

    private static String string(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof String string && !string.isBlank()) {
            return string;
        }
        throw new IllegalArgumentException("备份证据缺少字符串字段: " + key);
    }

    private static Instant instant(Map<?, ?> map, String key) {
        try {
            return Instant.parse(string(map, key));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("备份证据时间必须是 ISO-8601 UTC: " + key, exception);
        }
    }
}
