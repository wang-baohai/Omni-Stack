package com.omni.auth.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.omni.auth.catalog.ModuleCatalog.ModuleDefinition;
import com.omni.auth.catalog.ModuleCatalog.TenantProvisioningMode;

/**
 * 安全加载并校验 {@code scaffold/catalog/modules.yaml}。
 */
@Component
public class ModuleCatalogLoader {

    /** 模块目录类路径。 */
    static final String CATALOG_PATH = "scaffold/catalog/modules.yaml";
    /** 当前支持的目录协议版本。 */
    private static final String SUPPORTED_VERSION = "1.0.0";
    /** 根节点允许字段。 */
    private static final Set<String> ROOT_FIELDS = Set.of("version", "modules");
    /** 模块节点允许字段。 */
    private static final Set<String> MODULE_FIELDS = Set.of(
            "id", "artifactId", "kind", "version", "dependencies", "optionalModules", "conflicts",
            "backendModules", "frontend", "gatewayRoutes", "composeServices", "database",
            "tenantProvisioning", "permissionRoots", "provisioningSeedIds", "nacosConfigs", "ports",
            "mq", "xxl", "docs", "resourceHints", "deprecation", "compatibility");

    /** 已校验的不可变模块目录。 */
    private final ModuleCatalog catalog;

    /**
     * 从类路径加载模块目录，配置无效时阻止服务启动。
     */
    public ModuleCatalogLoader() {
        ClassPathResource resource = new ClassPathResource(CATALOG_PATH);
        try (InputStream inputStream = resource.getInputStream()) {
            this.catalog = parse(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取脚手架模块目录: " + CATALOG_PATH, exception);
        }
    }

    /**
     * 获取启动时已校验的模块目录。
     *
     * @return 不可变模块目录
     */
    public ModuleCatalog catalog() {
        return catalog;
    }

    /**
     * 安全解析模块目录，供契约测试复用。
     *
     * @param inputStream YAML 输入流
     * @return 已校验目录
     */
    static ModuleCatalog parse(InputStream inputStream) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setCodePointLimit(1_000_000);
        Object loaded = new Yaml(new SafeConstructor(options)).load(inputStream);
        Map<?, ?> root = requiredMap(loaded, "根节点");
        rejectUnknownFields(root, ROOT_FIELDS, "根节点");
        String version = requiredString(root, "version");
        if (!SUPPORTED_VERSION.equals(version)) {
            throw new IllegalStateException("不支持的模块目录版本: " + version);
        }

        List<?> moduleValues = requiredList(root, "modules");
        List<ModuleDefinition> modules = new ArrayList<>(moduleValues.size());
        Set<String> knownIds = new HashSet<>(moduleValues.size());
        for (Object value : moduleValues) {
            Map<?, ?> module = requiredMap(value, "模块节点");
            rejectUnknownFields(module, MODULE_FIELDS, "模块节点");
            ModuleDefinition definition = parseModule(module, knownIds);
            if (!knownIds.add(definition.id())) {
                throw new IllegalStateException("模块 ID 重复: " + definition.id());
            }
            modules.add(definition);
        }
        if (modules.isEmpty()) {
            throw new IllegalStateException("模块目录不能为空");
        }
        return new ModuleCatalog(version, modules);
    }

    /**
     * 解析并校验单个模块。
     */
    private static ModuleDefinition parseModule(Map<?, ?> module, Set<String> knownIds) {
        String id = requiredString(module, "id");
        String kind = requiredString(module, "kind");
        List<String> dependencies = stringList(module, "dependencies");
        for (String dependency : dependencies) {
            if (!knownIds.contains(dependency)) {
                throw new IllegalStateException("模块 " + id + " 的依赖尚未声明: " + dependency);
            }
        }
        TenantProvisioningMode mode;
        try {
            mode = TenantProvisioningMode.valueOf(requiredString(module, "tenantProvisioning").toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("模块 " + id + " 的租户初始化模式无效", exception);
        }
        return new ModuleDefinition(
                id,
                kind,
                dependencies,
                mode,
                optionalStringList(module, "permissionRoots"),
                stringList(module, "provisioningSeedIds"));
    }

    /**
     * 拒绝未声明字段，避免拼写错误被静默忽略。
     */
    private static void rejectUnknownFields(Map<?, ?> value, Set<String> allowedFields, String location) {
        for (Object key : value.keySet()) {
            if (!(key instanceof String field) || !allowedFields.contains(field)) {
                throw new IllegalStateException(location + "包含未知字段: " + key);
            }
        }
    }

    /**
     * 读取必填字符串。
     */
    private static String requiredString(Map<?, ?> value, String field) {
        Object result = value.get(field);
        if (!(result instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("字段必须是非空字符串: " + field);
        }
        return text;
    }

    /**
     * 读取必填列表。
     */
    private static List<?> requiredList(Map<?, ?> value, String field) {
        Object result = value.get(field);
        if (!(result instanceof List<?> list)) {
            throw new IllegalStateException("字段必须是列表: " + field);
        }
        return list;
    }

    /**
     * 读取必填字符串列表。
     */
    private static List<String> stringList(Map<?, ?> value, String field) {
        return convertStringList(requiredList(value, field), field);
    }

    /**
     * 读取可选字符串列表。
     */
    private static List<String> optionalStringList(Map<?, ?> value, String field) {
        if (!value.containsKey(field)) {
            return List.of();
        }
        return convertStringList(requiredList(value, field), field);
    }

    /**
     * 校验并转换字符串列表。
     */
    private static List<String> convertStringList(List<?> values, String field) {
        List<String> result = new ArrayList<>(values.size());
        for (Object value : values) {
            if (!(value instanceof String text) || text.isBlank()) {
                throw new IllegalStateException("字段包含无效字符串: " + field);
            }
            result.add(text);
        }
        return List.copyOf(result);
    }

    /**
     * 校验并转换 Map。
     */
    private static Map<?, ?> requiredMap(Object value, String field) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalStateException(field + "必须是对象");
        }
        return map;
    }
}
