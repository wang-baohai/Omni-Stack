package com.omni.dbmigrator.adoption;

import java.util.Map;
import java.util.Set;

import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

import com.omni.dbmigrator.migration.MigrationTarget;
import com.omni.dbmigrator.migration.MigrationTargetCatalog;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 接管 baseline 与真实 upgrade 标签契约测试。
 */
class AdoptionLabelContractTest {

    /**
     * 应确保接管只同步基线，公共 MQ 规范化保持待执行。
     */
    @Test
    void should_separate_baseline_from_upgrade_for_every_target() throws Exception {
        Map<String, Integer> expectedBaseline = Map.of(
                "auth", 2,
                "base", 2,
                "workflow", 3,
                "crm", 2,
                "srm", 2,
                "procurement", 2,
                "asset", 2,
                "nacos", 1,
                "xxl-job", 1);
        Map<String, Integer> expectedUpgrade = Map.of(
                "auth", 3,
                "base", 1,
                "workflow", 2,
                "crm", 1,
                "srm", 1,
                "procurement", 2,
                "asset", 1,
                "nacos", 0,
                "xxl-job", 0);

        try (ClassLoaderResourceAccessor accessor = new ClassLoaderResourceAccessor(
                AdoptionLabelContractTest.class.getClassLoader())) {
            DatabaseChangeLog platform = parse("database/changelog/db.changelog-root.yaml", accessor);
            assertThat(count(platform, "adoption-baseline")).isEqualTo(1);
            assertLabelsDisjoint("platform", platform);

            for (MigrationTarget target : MigrationTargetCatalog.targets()) {
                DatabaseChangeLog changelog = parse(target.changelog(), accessor);
                if (expectedBaseline.containsKey(target.id())) {
                    assertThat(count(changelog, "adoption-baseline"))
                            .as(target.id() + " baseline")
                            .isEqualTo(expectedBaseline.get(target.id()).longValue());
                    assertThat(count(changelog, "adoption-upgrade"))
                            .as(target.id() + " upgrade")
                            .isGreaterThanOrEqualTo(expectedUpgrade.get(target.id()).longValue());
                }
                assertLabelsDisjoint(target.id(), changelog);
            }
        }
    }

    /**
     * 解析一个聚合 changelog。
     */
    private static DatabaseChangeLog parse(String path, ClassLoaderResourceAccessor accessor) throws Exception {
        return ChangeLogParserFactory.getInstance()
                .getParser(path, accessor)
                .parse(path, new ChangeLogParameters(), accessor);
    }

    /**
     * 统计包含指定标签的 changeSet。
     */
    private static long count(DatabaseChangeLog changelog, String label) {
        return changelog.getChangeSets().stream()
                .filter(changeSet -> {
                    Set<String> labels = changeSet.getLabels().getLabels();
                    return labels.contains(label);
                })
                .count();
    }

    /**
     * 同一个 changeSet 不得同时属于接管基线和后续升级。
     */
    private static void assertLabelsDisjoint(String targetId, DatabaseChangeLog changelog) {
        assertThat(changelog.getChangeSets())
                .allSatisfy(changeSet -> {
                    Set<String> labels = changeSet.getLabels().getLabels();
                    assertThat(labels.contains("adoption-baseline") && labels.contains("adoption-upgrade"))
                            .as(targetId + " changeSet " + changeSet.getId())
                            .isFalse();
                });
    }
}
