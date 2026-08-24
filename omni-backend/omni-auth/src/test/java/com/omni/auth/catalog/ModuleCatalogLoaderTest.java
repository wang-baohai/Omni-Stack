package com.omni.auth.catalog;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Auth 模块目录加载器测试。
 */
class ModuleCatalogLoaderTest {

    /**
     * 仓库目录必须能按当前预设解析出稳定的本地与事件模块顺序。
     */
    @Test
    void should_load_repository_catalog() {
        ModuleCatalog catalog = new ModuleCatalogLoader().catalog();
        List<String> expectedLocal = catalog.modules().stream()
                .filter(module -> module.tenantProvisioning() == ModuleCatalog.TenantProvisioningMode.LOCAL)
                .map(ModuleCatalog.ModuleDefinition::id)
                .toList();
        List<String> expectedEvent = catalog.modules().stream()
                .filter(module -> module.tenantProvisioning() == ModuleCatalog.TenantProvisioningMode.EVENT)
                .map(ModuleCatalog.ModuleDefinition::id)
                .toList();

        assertThat(catalog.localProvisioningModuleIds()).containsExactlyElementsOf(expectedLocal);
        assertThat(catalog.eventProvisioningModuleIds()).containsExactlyElementsOf(expectedEvent).startsWith("base");
        assertThat(catalog.modules()).isUnmodifiable();
    }

    /**
     * 未知字段与前向依赖必须失败关闭。
     */
    @Test
    void should_reject_unknown_fields_and_forward_dependencies() {
        String unknownField = """
                version: "1.0.0"
                unexpected: true
                modules: []
                """;
        String forwardDependency = """
                version: "1.0.0"
                modules:
                  - id: auth
                    kind: foundation
                    dependencies: [base]
                    tenantProvisioning: local
                    provisioningSeedIds: [auth-root]
                """;

        assertThatThrownBy(() -> parse(unknownField))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未知字段");
        assertThatThrownBy(() -> parse(forwardDependency))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("依赖尚未声明");
    }

    /**
     * 解析内存 YAML。
     */
    private static ModuleCatalog parse(String yaml) {
        return ModuleCatalogLoader.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }
}
