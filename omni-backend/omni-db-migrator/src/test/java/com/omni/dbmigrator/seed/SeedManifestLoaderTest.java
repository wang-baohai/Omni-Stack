package com.omni.dbmigrator.seed;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

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
     * 应加载正式清单并保证种子源、模块和断言引用闭合。
     */
    @Test
    void should_load_manifest_when_contract_is_valid() {
        SeedManifest manifest = loader.load("database/seed/manifest.yaml");
        Set<String> moduleIds = manifest.modules().stream()
                .map(SeedModule::id)
                .collect(Collectors.toSet());

        assertThat(manifest.version()).isEqualTo("1.0.0-bootstrap");
        assertThat(moduleIds).hasSize(manifest.modules().size()).contains("platform", "auth", "base");
        assertThat(manifest.sources()).isNotEmpty().allMatch(source -> moduleIds.contains(source.module()));
        assertThat(manifest.assertions()).isNotEmpty().allMatch(assertion -> moduleIds.contains(assertion.module()));
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

    /**
     * Windows 与 Unix 换行必须产生相同摘要。
     */
    @Test
    void should_normalize_line_endings_when_digest_is_calculated() {
        byte[] lf = "INSERT INTO sample VALUES (1);\n".getBytes(StandardCharsets.UTF_8);
        byte[] crlf = "INSERT INTO sample VALUES (1);\r\n".getBytes(StandardCharsets.UTF_8);

        assertThat(SeedManifestLoader.canonicalSha256(crlf))
                .isEqualTo(SeedManifestLoader.canonicalSha256(lf));
    }
}
