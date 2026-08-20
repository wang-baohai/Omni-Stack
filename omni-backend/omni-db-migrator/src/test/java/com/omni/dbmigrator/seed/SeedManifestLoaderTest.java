package com.omni.dbmigrator.seed;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 种子清单安全加载测试。
 */
class SeedManifestLoaderTest {

    /** 清单加载器。 */
    private final SeedManifestLoader loader = new SeedManifestLoader();

    /**
     * 应加载正式清单并保留全部模块和断言。
     */
    @Test
    void should_load_manifest_when_contract_is_valid() {
        SeedManifest manifest = loader.load("database/seed/manifest.yaml");

        assertThat(manifest.version()).isEqualTo("1.0.0-bootstrap");
        assertThat(manifest.modules()).hasSize(10);
        assertThat(manifest.assertions()).hasSize(24);
    }

    /**
     * 应拒绝包含写语句的断言。
     */
    @Test
    void should_reject_manifest_when_query_is_not_read_only() {
        assertThatThrownBy(() -> loader.load("invalid-seed-manifest.yaml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("查询不合法");
    }

    /**
     * 应拒绝 classpath 目录穿越。
     */
    @Test
    void should_reject_manifest_when_path_traverses_parent() {
        assertThatThrownBy(() -> loader.load("../manifest.yaml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("安全");
    }
}
