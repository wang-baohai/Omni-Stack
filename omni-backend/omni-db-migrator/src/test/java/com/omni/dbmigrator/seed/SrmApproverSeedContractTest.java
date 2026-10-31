package com.omni.dbmigrator.seed;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import static org.assertj.core.api.Assertions.assertThat;

/** 正常 SRM 审批角色的最小权限及新装/升级一致性回归。 */
class SrmApproverSeedContractTest {

    @Test
    void freshAndForwardOnlyUpgradeGrantTheSameMinimalPermissionSet() throws Exception {
        String seed = resource("scripts/sql/seed/auth.sql");
        Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(resource(
                "database/changelog/auth/0008-srm-workflow-approver-permissions.yaml"));
        Map<?, ?> root = (Map<?, ?>) loaded;
        Map<?, ?> changeSet = (Map<?, ?>) ((Map<?, ?>) ((List<?>) root.get("databaseChangeLog")).getFirst()).get("changeSet");
        List<?> changes = (List<?>) changeSet.get("changes");
        assertThat(changes).hasSize(1);
        String upgrade = ((Map<?, ?>) ((Map<?, ?>) changes.getFirst()).get("sql")).get("sql").toString();
        assertThat(tokens(upgrade, "r\\.role_code")).containsExactlyInAnyOrder("SRM_MANAGER", "SRM_COMPLIANCE", "SRM_DIRECTOR");
        assertThat(tokens(upgrade, "p\\.permission_code")).containsExactlyInAnyOrder(
                "workflow", "workflow:instance", "workflow:task:todo", "workflow:approval:complete");
        assertThat(upgrade).contains("tenant_code = 'default'", "r.status = 1 AND p.status = 1")
                .doesNotContain("SUPER_ADMIN", "'USER'", "workflow:model", "UPDATE ", "DELETE ", "TRUNCATE ");
        // 该迁移归属认证模块，子集预设仍保留；没有 SRM 角色时不能补回其初始种子。
        if (!includesSrm()) {
            assertThat(seed).doesNotContain("SRM_APPROVER_WORKFLOW_PERMISSIONS_BEGIN", "SRM_MANAGER",
                    "SRM_COMPLIANCE", "SRM_DIRECTOR");
            return;
        }
        String block = seed.substring(seed.indexOf("-- SRM_APPROVER_WORKFLOW_PERMISSIONS_BEGIN"),
                seed.indexOf("-- SRM_APPROVER_WORKFLOW_PERMISSIONS_END"));
        assertThat(normalize(block)).isEqualTo(normalize(upgrade));
    }

    @Test
    void seedAssertionDetectsMissingWorkflowGrantsRatherThanOnlyRoleNames() throws Exception {
        SeedManifest manifest = new SeedManifestLoader().load("database/seed/manifest.yaml");
        assertThat(manifest.modules().stream().anyMatch(module -> module.id().equals("srm")))
                .isEqualTo(includesSrm());
        if (!includesSrm()) {
            assertThat(manifest.assertions()).noneMatch(assertion -> assertion.id().equals("srm-role-catalog"));
            return;
        }
        SeedAssertion assertion = manifest.assertions().stream()
                .filter(item -> item.id().equals("srm-role-catalog")).findFirst().orElseThrow();
        assertThat(assertion.expectedRows()).isEqualTo(5);
        assertThat(assertion.query()).contains("sys_role_permission", "COUNT(DISTINCT p.permission_code)",
                "workflow:approval:complete", "SRM_MANAGER", "SRM_COMPLIANCE", "SRM_DIRECTOR");
    }

    /** 按正式模块目录判断预设边界；缺种子或断言不能被误当成已裁剪。 */
    private static boolean includesSrm() throws Exception {
        Map<?, ?> catalog = new Yaml(new SafeConstructor(new LoaderOptions()))
                .load(resource("scaffold/catalog/modules.yaml"));
        return ((List<?>) catalog.get("modules")).stream()
                .anyMatch(module -> "srm".equals(((Map<?, ?>) module).get("id")));
    }

    private static String resource(String path) throws Exception {
        try (var stream = SrmApproverSeedContractTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("缺少正式资源: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String normalize(String sql) {
        return sql.replaceAll("(?m)--[^\\r\\n]*", "").replaceAll("\\s+", " ").trim();
    }

    private static Set<String> tokens(String sql, String column) {
        var clause = Pattern.compile(column + "\\s+IN\\s*\\(([^)]+)\\)").matcher(sql);
        if (!clause.find()) throw new IllegalStateException("缺少受限授权集合");
        return Pattern.compile("'([^']*)'").matcher(clause.group(1)).results()
                .map(match -> match.group(1)).collect(Collectors.toSet());
    }
}
