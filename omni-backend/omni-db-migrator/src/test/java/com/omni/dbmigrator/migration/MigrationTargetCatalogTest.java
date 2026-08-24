package com.omni.dbmigrator.migration;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据库迁移目标目录测试。
 */
class MigrationTargetCatalogTest {

    /**
     * 应覆盖全部固定核心库、保持 ID 唯一，并允许脚手架追加业务库。
     */
    @Test
    void should_contain_core_unique_targets_when_catalog_is_loaded() {
        Set<String> ids = MigrationTargetCatalog.targets().stream()
                .map(MigrationTarget::id)
                .collect(Collectors.toSet());
        assertThat(ids)
                .hasSize(MigrationTargetCatalog.targets().size())
                .contains(
                        "auth",
                        "base",
                        "workflow",
                        "crm",
                        "srm",
                        "procurement",
                        "asset",
                        "nacos",
                        "xxl-job");
        assertThat(MigrationTargetCatalog.targets().stream().filter(MigrationTarget::vendor)).hasSize(2);
    }
}
