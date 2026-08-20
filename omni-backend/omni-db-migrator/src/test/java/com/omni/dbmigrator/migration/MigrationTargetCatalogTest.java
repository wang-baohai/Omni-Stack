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
     * 应覆盖七个业务库和两个基础设施库且 ID 唯一。
     */
    @Test
    void should_contain_nine_unique_targets_when_catalog_is_loaded() {
        assertThat(MigrationTargetCatalog.targets()).hasSize(9);
        Set<String> ids = MigrationTargetCatalog.targets().stream()
                .map(MigrationTarget::id)
                .collect(Collectors.toSet());
        assertThat(ids).hasSize(9);
        assertThat(MigrationTargetCatalog.targets().stream().filter(MigrationTarget::vendor)).hasSize(2);
    }
}
