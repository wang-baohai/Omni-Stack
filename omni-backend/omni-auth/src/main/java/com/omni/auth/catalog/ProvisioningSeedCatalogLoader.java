package com.omni.auth.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.omni.auth.catalog.ModuleCatalog.ModuleDefinition;
import com.omni.auth.catalog.ProvisioningSeedCatalog.SeedDefinition;

/**
 * 将模块目录的 provisioning seed ID 解析到数据库种子清单。
 */
@Component
public class ProvisioningSeedCatalogLoader {

    /** 种子清单类路径。 */
    static final String MANIFEST_PATH = "database/seed/manifest.yaml";
    /** provisioning 节点允许字段。 */
    private static final Set<String> PROVISIONING_FIELDS = Set.of("roleCodes");

    /** 已解析且按模块拓扑顺序排列的 seed 目录。 */
    private final ProvisioningSeedCatalog catalog;

    /**
     * 加载种子清单并与模块目录交叉校验。
     *
     * @param moduleCatalogLoader 模块目录加载器
     */
    public ProvisioningSeedCatalogLoader(ModuleCatalogLoader moduleCatalogLoader) {
        ClassPathResource resource = new ClassPathResource(MANIFEST_PATH);
        try (InputStream inputStream = resource.getInputStream()) {
            this.catalog = parse(inputStream, moduleCatalogLoader.catalog());
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取数据库种子清单: " + MANIFEST_PATH, exception);
        }
    }

    /**
     * 获取已验证的 provisioning seed 目录。
     *
     * @return 不可变 seed 目录
     */
    public ProvisioningSeedCatalog catalog() {
        return catalog;
    }

    /**
     * 解析清单并严格匹配模块声明的 seed ID。
     */
    static ProvisioningSeedCatalog parse(InputStream inputStream, ModuleCatalog moduleCatalog) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setCodePointLimit(2_000_000);
        Object loaded = new Yaml(new SafeConstructor(options)).load(inputStream);
        Map<?, ?> root = requiredMap(loaded, "种子清单根节点");
        List<?> assertions = requiredList(root, "assertions");
        Map<String, Map<?, ?>> assertionsById = new LinkedHashMap<>(assertions.size());
        for (Object value : assertions) {
            Map<?, ?> assertion = requiredMap(value, "种子断言");
            String id = requiredString(assertion, "id");
            if (assertionsById.putIfAbsent(id, assertion) != null) {
                throw new IllegalStateException("种子断言 ID 重复: " + id);
            }
        }

        Map<String, SeedDefinition> selected = new LinkedHashMap<>();
        for (ModuleDefinition module : moduleCatalog.modules()) {
            for (String seedId : module.provisioningSeedIds()) {
                Map<?, ?> assertion = assertionsById.get(seedId);
                if (assertion == null) {
                    throw new IllegalStateException("模块引用了不存在的 provisioning seed: " + seedId);
                }
                String assertionModule = requiredString(assertion, "module");
                if (!module.id().equals(assertionModule)) {
                    throw new IllegalStateException("provisioning seed 所属模块不匹配: " + seedId);
                }
                selected.put(seedId, new SeedDefinition(
                        seedId,
                        module.id(),
                        parseRoleCodes(assertion, seedId)));
            }
        }
        return new ProvisioningSeedCatalog(selected);
    }

    /**
     * 读取可选的角色自然键声明。
     */
    private static List<String> parseRoleCodes(Map<?, ?> assertion, String seedId) {
        Object value = assertion.get("provisioning");
        if (value == null) {
            return List.of();
        }
        Map<?, ?> provisioning = requiredMap(value, "provisioning");
        for (Object key : provisioning.keySet()) {
            if (!(key instanceof String field) || !PROVISIONING_FIELDS.contains(field)) {
                throw new IllegalStateException("seed " + seedId + " 包含未知 provisioning 字段: " + key);
            }
        }
        if (!provisioning.containsKey("roleCodes")) {
            return List.of();
        }
        List<?> values = requiredList(provisioning, "roleCodes");
        List<String> roleCodes = new ArrayList<>(values.size());
        for (Object roleCode : values) {
            if (!(roleCode instanceof String text) || text.isBlank()) {
                throw new IllegalStateException("seed " + seedId + " 包含无效角色编码");
            }
            roleCodes.add(text);
        }
        if (roleCodes.stream().distinct().count() != roleCodes.size()) {
            throw new IllegalStateException("seed " + seedId + " 包含重复角色编码");
        }
        return List.copyOf(roleCodes);
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
     * 校验并转换 Map。
     */
    private static Map<?, ?> requiredMap(Object value, String field) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalStateException(field + "必须是对象");
        }
        return map;
    }
}
