package com.omni.dbmigrator.adoption;

import com.omni.dbmigrator.migration.MigrationTarget;
import com.omni.dbmigrator.migration.MigrationTargetCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 接管结构指纹清单加载测试。
 */
class SchemaFingerprintManifestLoaderTest {

    /**
     * 应加载冻结提交和当前模块清单声明的全部目标。
     */
    @Test
    void should_load_all_targets_when_baseline_is_valid() {
        SchemaFingerprintManifest manifest = new SchemaFingerprintManifestLoader()
                .load("database/adoption/baseline-09a29fe.yaml");

        assertThat(manifest.baselineCommit())
                .isEqualTo("09a29fe10af9c7ddffe5001238d048947868dc98");
        assertThat(manifest.algorithm()).isEqualTo("mysql-information-schema-v1");
        assertThat(manifest.targets())
                .extracting(SchemaFingerprintTarget::id)
                .containsExactlyElementsOf(MigrationTargetCatalog.targets().stream()
                        .map(MigrationTarget::id)
                        .toList());
    }
}
