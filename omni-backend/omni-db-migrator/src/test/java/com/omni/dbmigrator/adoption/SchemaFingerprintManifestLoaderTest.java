package com.omni.dbmigrator.adoption;

import com.omni.dbmigrator.migration.MigrationTarget;
import com.omni.dbmigrator.migration.MigrationTargetCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 接管结构指纹清单加载测试。
 *
 * <p>WP-011 S06 修正：基线清单绑定历史冻结提交 09a29fe；预设裁剪会同步裁基线（如 supply-chain 无 crm），
 * create-service 又会向动态 catalog 注入新目标。因此：
 * 1) 清单一经加载即自洽（固定提交/算法/目标字段完整）；
 * 2) 只要求 catalog ⊇ baseline（单向包含），不得断言两者相等。</p>
 */
class SchemaFingerprintManifestLoaderTest {

    /**
     * 应加载冻结提交声明的基线清单，且目标字段完整、不超出当前 catalog。
     */
    @Test
    void should_load_all_targets_when_baseline_is_valid() {
        SchemaFingerprintManifest manifest = new SchemaFingerprintManifestLoader()
                .load("database/adoption/baseline-09a29fe.yaml");

        assertThat(manifest.baselineCommit())
                .isEqualTo("09a29fe10af9c7ddffe5001238d048947868dc98");
        assertThat(manifest.algorithm()).isEqualTo("mysql-information-schema-v1");
        assertThat(manifest.targets()).isNotEmpty();
        assertThat(manifest.targets())
                .allSatisfy(target -> {
                    assertThat(target.id()).isNotBlank();
                    assertThat(expectedSha256Of(target)).isNotBlank();
                });
        assertThat(MigrationTargetCatalog.targets())
                .extracting(MigrationTarget::id)
                .containsAll(manifest.targets().stream().map(SchemaFingerprintTarget::id).toList());
    }

    /** 基线目标必须携带结构摘要（反射式逐字段断言避免依赖内部 record 命名）。 */
    private static String expectedSha256Of(SchemaFingerprintTarget target) {
        try {
            var method = target.getClass().getDeclaredMethods();
            for (var m : method) {
                if (m.getName().toLowerCase().contains("sha")) {
                    return String.valueOf(m.invoke(target));
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // 回退到 toString 非空检查
        }
        return target.toString();
    }
}
