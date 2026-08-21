package com.omni.dbmigrator.seed;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * 从 classpath 加载并严格校验种子清单。
 */
@Component
public class SeedManifestLoader {

    /** 稳定 ID 格式。 */
    private static final String ID_PATTERN = "[a-z][a-z0-9-]*";
    /** 数据库标识符格式。 */
    private static final String DATABASE_PATTERN = "[a-z0-9_]+";
    /** SHA-256 十六进制格式。 */
    private static final String SHA256_PATTERN = "[a-f0-9]{64}";

    /**
     * 加载清单。
     *
     * @param resourcePath classpath 资源路径
     * @return 已校验清单
     */
    public SeedManifest load(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank() || resourcePath.startsWith("/")
                || resourcePath.contains("..")) {
            throw new IllegalArgumentException("种子清单路径必须是安全的 classpath 相对路径");
        }
        ClassLoader classLoader = SeedManifestLoader.class.getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("找不到种子清单: " + resourcePath);
            }
            Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(inputStream);
            if (!(loaded instanceof Map<?, ?> root)) {
                throw new IllegalArgumentException("种子清单根节点必须是对象");
            }
            return parse(root);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("读取种子清单失败: " + resourcePath, exception);
        }
    }

    /**
     * 将安全加载后的 Map 转换为强类型清单。
     */
    private static SeedManifest parse(Map<?, ?> root) {
        String version = requiredString(root, "version");
        String digestAlgorithm = requiredString(root, "digestAlgorithm");
        if (!"SHA-256".equals(digestAlgorithm)) {
            throw new IllegalArgumentException("种子清单只允许 SHA-256 摘要算法");
        }

        List<SeedModule> modules = parseModules(requiredList(root, "modules"));
        Set<String> moduleIds = new HashSet<>();
        for (SeedModule module : modules) {
            if (!moduleIds.add(module.id())) {
                throw new IllegalArgumentException("种子模块 ID 重复: " + module.id());
            }
        }

        List<SeedSource> sources = parseSources(requiredList(root, "sources"), moduleIds);
        List<SeedAssertion> assertions = parseAssertions(requiredList(root, "assertions"), moduleIds);
        return new SeedManifest(
                version,
                digestAlgorithm,
                List.copyOf(sources),
                List.copyOf(modules),
                List.copyOf(assertions));
    }

    /**
     * 解析种子资源并立即校验 classpath 内容摘要。
     */
    private static List<SeedSource> parseSources(List<?> values, Set<String> moduleIds) {
        List<SeedSource> sources = new ArrayList<>();
        Set<String> sourceIds = new HashSet<>();
        Set<String> resources = new HashSet<>();
        for (Object value : values) {
            Map<?, ?> map = requiredMap(value, "sources");
            String id = requiredString(map, "id");
            String module = requiredString(map, "module");
            String resource = requiredString(map, "resource");
            String sha256 = requiredString(map, "sha256");
            if (!id.matches(ID_PATTERN) || !sourceIds.add(id)) {
                throw new IllegalArgumentException("种子源 ID 不合法或重复: " + id);
            }
            if (!moduleIds.contains(module)) {
                throw new IllegalArgumentException("种子源引用未知模块: " + id);
            }
            if (!isSafeSeedResource(resource) || !resources.add(resource)) {
                throw new IllegalArgumentException("种子源路径不安全或重复: " + id);
            }
            if (!sha256.matches(SHA256_PATTERN)) {
                throw new IllegalArgumentException("种子源摘要不合法: " + id);
            }
            verifyResourceDigest(resource, sha256);
            sources.add(new SeedSource(id, module, resource, sha256));
        }
        return sources;
    }

    /**
     * 种子资源只能位于最终 SQL 目录，禁止绝对路径和目录穿越。
     */
    private static boolean isSafeSeedResource(String resource) {
        return resource.matches("scripts/sql/seed/[a-z0-9-]+\\.sql")
                && !resource.startsWith("/")
                && !resource.contains("..");
    }

    /**
     * 校验种子文件与清单摘要完全一致，防止迁移输入被静默修改。
     */
    private static void verifyResourceDigest(String resource, String expectedSha256) {
        ClassLoader classLoader = SeedManifestLoader.class.getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(resource)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("找不到种子资源: " + resource);
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String actual = HexFormat.of().formatHex(digest.digest(inputStream.readAllBytes()));
            if (!expectedSha256.equals(actual)) {
                throw new IllegalArgumentException("种子资源摘要不匹配: " + resource);
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("校验种子资源失败: " + resource, exception);
        }
    }

    /**
     * 解析模块声明。
     */
    private static List<SeedModule> parseModules(List<?> values) {
        List<SeedModule> modules = new ArrayList<>();
        for (Object value : values) {
            Map<?, ?> map = requiredMap(value, "modules");
            String id = requiredString(map, "id");
            if (!id.matches(ID_PATTERN)) {
                throw new IllegalArgumentException("种子模块 ID 不合法: " + id);
            }
            modules.add(new SeedModule(id, requiredBoolean(map, "enabledByDefault")));
        }
        return modules;
    }

    /**
     * 解析断言声明。
     */
    private static List<SeedAssertion> parseAssertions(List<?> values, Set<String> moduleIds) {
        List<SeedAssertion> assertions = new ArrayList<>();
        Set<String> assertionIds = new HashSet<>();
        for (Object value : values) {
            Map<?, ?> map = requiredMap(value, "assertions");
            String id = requiredString(map, "id");
            String module = requiredString(map, "module");
            String database = requiredString(map, "database");
            String query = requiredString(map, "query").trim();
            int expectedRows = requiredInteger(map, "expectedRows");
            String expectedSha256 = requiredString(map, "expectedSha256");
            if (!id.matches(ID_PATTERN) || !assertionIds.add(id)) {
                throw new IllegalArgumentException("种子断言 ID 不合法或重复: " + id);
            }
            if (!moduleIds.contains(module)) {
                throw new IllegalArgumentException("种子断言引用未知模块: " + id);
            }
            if (!database.matches(DATABASE_PATTERN) || !isReadOnlySelect(query)) {
                throw new IllegalArgumentException("种子断言数据库名或查询不合法: " + id);
            }
            if (expectedRows < 0 || !expectedSha256.matches(SHA256_PATTERN)) {
                throw new IllegalArgumentException("种子断言数量或摘要不合法: " + id);
            }
            assertions.add(new SeedAssertion(id, module, database, query, expectedRows, expectedSha256));
        }
        return assertions;
    }

    /**
     * 仅允许一个不含注释和语句分隔符的 SELECT。
     */
    private static boolean isReadOnlySelect(String query) {
        String normalized = query.stripLeading().toUpperCase();
        return normalized.startsWith("SELECT ")
                && !query.contains(";")
                && !query.contains("--")
                && !query.contains("/*")
                && !query.contains("#")
                && !normalized.contains(" INTO OUTFILE")
                && !normalized.contains(" INTO DUMPFILE")
                && !normalized.contains(" FOR UPDATE")
                && !normalized.contains(" LOCK IN SHARE MODE")
                && !normalized.contains("SLEEP(")
                && !normalized.contains("BENCHMARK(")
                && !normalized.contains("LOAD_FILE(");
    }

    private static Map<?, ?> requiredMap(Object value, String key) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw new IllegalArgumentException("种子清单字段必须是对象: " + key);
    }

    private static List<?> requiredList(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof List<?> list) {
            return list;
        }
        throw new IllegalArgumentException("种子清单字段必须是数组: " + key);
    }

    private static String requiredString(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof String string && !string.isBlank()) {
            return string;
        }
        throw new IllegalArgumentException("种子清单缺少字符串字段: " + key);
    }

    private static boolean requiredBoolean(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new IllegalArgumentException("种子清单缺少布尔字段: " + key);
    }

    private static int requiredInteger(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalArgumentException("种子清单缺少整数数字段: " + key);
    }
}
