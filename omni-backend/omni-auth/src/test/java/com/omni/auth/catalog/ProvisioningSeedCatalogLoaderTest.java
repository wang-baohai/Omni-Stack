package com.omni.auth.catalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 租户初始化种子目录加载器测试。
 */
class ProvisioningSeedCatalogLoaderTest {

    /**
     * 当前预设必须从 seed ID 解析出全部初始化断言和基础角色，且不依赖 Java 硬编码清单。
     */
    @Test
    void should_resolve_role_natural_keys_from_seed_manifest() {
        ModuleCatalogLoader moduleLoader = new ModuleCatalogLoader();
        ProvisioningSeedCatalog catalog = new ProvisioningSeedCatalogLoader(moduleLoader).catalog();
        long expectedSeedCount = moduleLoader.catalog().modules().stream()
                .mapToLong(module -> module.provisioningSeedIds().size())
                .sum();

        assertThat(catalog.seedsById()).hasSize((int) expectedSeedCount);
        assertThat(catalog.roleCodes()).contains("SUPER_ADMIN", "USER").doesNotHaveDuplicates();
    }
}
