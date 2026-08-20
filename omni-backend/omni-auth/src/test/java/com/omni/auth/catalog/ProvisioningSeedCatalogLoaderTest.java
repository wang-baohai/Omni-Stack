package com.omni.auth.catalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 租户初始化种子目录加载器测试。
 */
class ProvisioningSeedCatalogLoaderTest {

    /**
     * 全量目录必须从 seed ID 解析出当前 19 个默认角色，且不依赖 Java 硬编码清单。
     */
    @Test
    void should_resolve_role_natural_keys_from_seed_manifest() {
        ModuleCatalogLoader moduleLoader = new ModuleCatalogLoader();
        ProvisioningSeedCatalog catalog = new ProvisioningSeedCatalogLoader(moduleLoader).catalog();

        assertThat(catalog.seedsById()).hasSize(24);
        assertThat(catalog.roleCodes()).containsExactly(
                "SUPER_ADMIN", "USER",
                "EMPLOYEE", "TEAM_LEADER", "DEPT_LEADER",
                "CRM_ADMIN", "CRM_VIEWER", "SALES_MANAGER", "SALES_REP",
                "SRM_ADMIN", "SRM_COMPLIANCE", "SRM_DIRECTOR", "SRM_MANAGER", "SUPPLIER",
                "PROCUREMENT_MANAGER", "PROCUREMENT_STAFF",
                "ASSET_ADMIN", "ASSET_MANAGER", "ASSET_USER");
    }
}
