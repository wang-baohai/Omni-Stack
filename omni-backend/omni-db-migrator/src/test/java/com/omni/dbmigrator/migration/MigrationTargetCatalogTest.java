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
     * 应覆盖基础库、保持 ID 和数据库名唯一，并正确标记 vendor 库。
     */
    @Test
    void should_contain_core_unique_targets_when_catalog_is_loaded() {
        Set<String> ids = MigrationTargetCatalog.targets().stream()
                .map(MigrationTarget::id)
                .collect(Collectors.toSet());
        assertThat(ids).hasSize(MigrationTargetCatalog.targets().size()).contains("auth", "base");
        assertThat(MigrationTargetCatalog.targets())
                .extracting(MigrationTarget::database)
                .doesNotHaveDuplicates();
        assertThat(MigrationTargetCatalog.targets().stream()
                .filter(MigrationTarget::vendor)
                .map(MigrationTarget::id))
                .allMatch(id -> id.equals("nacos") || id.equals("xxl-job"));
    }
}
