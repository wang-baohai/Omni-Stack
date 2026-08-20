package com.omni.dbmigrator.adoption;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * 安全加载接管指纹清单。
 */
@Component
public class SchemaFingerprintManifestLoader {

    /** 当前受支持的算法版本。 */
    public static final String ALGORITHM = "mysql-information-schema-v1";
    /** 小写 SHA-256 格式。 */
    private static final String SHA256_PATTERN = "[a-f0-9]{64}";

    /**
     * 从 classpath 加载接管基线。
     *
     * @param resourcePath 资源路径
     * @return 强类型清单
     */
    public SchemaFingerprintManifest load(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank() || resourcePath.startsWith("/")
                || resourcePath.contains("..")) {
            throw new IllegalArgumentException("接管基线路径必须是安全的 classpath 相对路径");
        }
        try (InputStream inputStream = SchemaFingerprintManifestLoader.class.getClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("找不到接管基线: " + resourcePath);
            }
            Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(inputStream);
            if (!(loaded instanceof Map<?, ?> root)) {
                throw new IllegalArgumentException("接管基线根节点必须是对象");
            }
            return parse(root);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("读取接管基线失败: " + resourcePath, exception);
        }
    }

    /**
     * 解析并校验清单字段。
     */
    private static SchemaFingerprintManifest parse(Map<?, ?> root) {
        String baselineCommit = string(root, "baselineCommit");
        String algorithm = string(root, "algorithm");
        if (!ALGORITHM.equals(algorithm)) {
            throw new IllegalArgumentException("不支持的接管指纹算法: " + algorithm);
        }
        Object rawTargets = root.get("targets");
        if (!(rawTargets instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("接管基线 targets 必须是非空数组");
        }
        List<SchemaFingerprintTarget> targets = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> databases = new HashSet<>();
        for (Object value : list) {
            if (!(value instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("接管目标必须是对象");
            }
            String id = string(map, "id");
            String database = string(map, "database");
            int expectedTables = integer(map, "expectedTables");
            int expectedViews = integer(map, "expectedViews");
            int expectedTriggers = integer(map, "expectedTriggers");
            int expectedRoutines = integer(map, "expectedRoutines");
            String expectedSha256 = string(map, "expectedSha256");
            if (!id.matches("[a-z][a-z0-9-]*") || !ids.add(id)
                    || !database.matches("[a-z0-9_]+") || !databases.add(database)
                    || expectedTables < 0 || expectedViews < 0 || expectedTriggers < 0
                    || expectedRoutines < 0
                    || !expectedSha256.matches(SHA256_PATTERN)) {
                throw new IllegalArgumentException("接管目标字段不合法或重复: " + id);
            }
            targets.add(new SchemaFingerprintTarget(
                    id, database, expectedTables, expectedViews, expectedTriggers,
                    expectedRoutines, expectedSha256));
        }
        return new SchemaFingerprintManifest(baselineCommit, algorithm, List.copyOf(targets));
    }

    private static String string(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof String string && !string.isBlank()) {
            return string;
        }
        throw new IllegalArgumentException("接管基线缺少字符串字段: " + key);
    }

    private static int integer(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalArgumentException("接管基线缺少整数数字段: " + key);
    }
}
