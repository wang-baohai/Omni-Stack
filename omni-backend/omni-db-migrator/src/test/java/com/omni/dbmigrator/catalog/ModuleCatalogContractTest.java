package com.omni.dbmigrator.catalog;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.omni.dbmigrator.seed.SeedAssertion;
import com.omni.dbmigrator.seed.SeedManifest;
import com.omni.dbmigrator.seed.SeedManifestLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 脚手架模块目录与种子清单的一致性契约。
 */
class ModuleCatalogContractTest {

    /**
     * 模块依赖必须按拓扑顺序声明，且 provisioning seed 必须与种子清单一一对应。
     *
     * @throws Exception 资源读取失败
     */
    @Test
    void should_match_seed_manifest_and_use_topological_dependencies() throws Exception {
        Map<?, ?> root = loadCatalog();
        assertThat(root.get("version")).isEqualTo("1.0.0");
        List<?> moduleValues = requiredList(root, "modules");
        SeedManifest seedManifest = new SeedManifestLoader().load("database/seed/manifest.yaml");
        List<String> seedModuleIds = seedManifest.modules().stream().map(module -> module.id()).toList();
        Set<String> assertionIds = seedManifest.assertions().stream().map(SeedAssertion::id)
                .collect(java.util.stream.Collectors.toSet());

        List<String> seededCatalogIds = new ArrayList<>();
        Set<String> knownIds = new HashSet<>();
        Set<String> catalogSeedIds = new HashSet<>();
        for (Object value : moduleValues) {
            Map<?, ?> module = requiredMap(value, "module");
            String id = requiredString(module, "id");
            assertThat(knownIds.add(id)).as("模块 ID 唯一: " + id).isTrue();
            for (Object dependency : requiredList(module, "dependencies")) {
                assertThat(knownIds).as(id + " 依赖必须先声明").contains(dependency.toString());
            }
            String provisioning = requiredString(module, "tenantProvisioning");
            assertThat(provisioning).isIn("local", "event", "none");
            List<?> provisioningSeedIds = requiredList(module, "provisioningSeedIds");
            if (provisioningSeedIds.isEmpty()) {
                assertThat(provisioning).as(id + " 无种子模块不得触发租户初始化").isEqualTo("none");
            } else {
                seededCatalogIds.add(id);
            }
            for (Object seedId : provisioningSeedIds) {
                assertThat(catalogSeedIds.add(seedId.toString()))
                        .as("provisioning seed ID 唯一: " + seedId)
                        .isTrue();
            }
        }

        assertThat(seededCatalogIds).containsExactlyElementsOf(seedModuleIds);
        assertThat(catalogSeedIds).containsExactlyInAnyOrderElementsOf(assertionIds);
    }

    /**
     * 安全加载模块目录。
     */
    private static Map<?, ?> loadCatalog() throws Exception {
        try (InputStream inputStream = ModuleCatalogContractTest.class.getClassLoader()
                .getResourceAsStream("scaffold/catalog/modules.yaml")) {
            assertThat(inputStream).isNotNull();
            Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(inputStream);
            return requiredMap(loaded, "root");
        }
    }

    private static Map<?, ?> requiredMap(Object value, String field) {
        assertThat(value).as(field).isInstanceOf(Map.class);
        return (Map<?, ?>) value;
    }

    private static List<?> requiredList(Map<?, ?> map, String field) {
        Object value = map.get(field);
        assertThat(value).as(field).isInstanceOf(List.class);
        return (List<?>) value;
    }

    private static String requiredString(Map<?, ?> map, String field) {
        Object value = map.get(field);
        assertThat(value).as(field).isInstanceOf(String.class);
        return (String) value;
    }
}
