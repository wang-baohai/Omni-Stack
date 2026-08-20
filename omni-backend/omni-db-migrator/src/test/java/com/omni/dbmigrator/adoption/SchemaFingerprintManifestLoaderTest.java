package com.omni.dbmigrator.adoption;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 接管结构指纹清单加载测试。
 */
class SchemaFingerprintManifestLoaderTest {

    /**
     * 应加载冻结提交和全部九个目标。
     */
    @Test
    void should_load_all_targets_when_baseline_is_valid() {
        SchemaFingerprintManifest manifest = new SchemaFingerprintManifestLoader()
                .load("database/adoption/baseline-09a29fe.yaml");

        assertThat(manifest.baselineCommit())
                .isEqualTo("09a29fe10af9c7ddffe5001238d048947868dc98");
        assertThat(manifest.algorithm()).isEqualTo("mysql-show-create-v1");
        assertThat(manifest.targets())
                .extracting(SchemaFingerprintTarget::id)
                .containsExactly(
                        "auth", "base", "workflow", "crm", "srm",
                        "procurement", "asset", "nacos", "xxl-job");
    }
}
