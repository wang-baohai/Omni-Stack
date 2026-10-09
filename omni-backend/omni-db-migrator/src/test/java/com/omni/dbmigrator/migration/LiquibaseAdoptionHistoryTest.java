package com.omni.dbmigrator.migration;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 既有 Liquibase 历史接管白名单测试。
 */
class LiquibaseAdoptionHistoryTest {

    /**
     * 冻结身份和基线标签同时匹配时应通过。
     */
    @Test
    void should_accept_exact_frozen_baseline_identity() {
        LiquibaseMigrationService.ChangeSetIdentity expected = identity("auth-0001-baseline-schema");

        assertThatCode(() -> LiquibaseMigrationService.validateAdoptionHistoryRow(
                "auth", expected, "database:auth,adoption-baseline", Set.of(expected)))
                .doesNotThrowAnyException();
    }

    /**
     * 未知 changeSet 即使伪装为基线标签也应拒绝。
     */
    @Test
    void should_reject_unknown_identity_with_baseline_label() {
        LiquibaseMigrationService.ChangeSetIdentity expected = identity("auth-0001-baseline-schema");
        LiquibaseMigrationService.ChangeSetIdentity unknown = identity("forged-change-set");

        assertThatThrownBy(() -> LiquibaseMigrationService.validateAdoptionHistoryRow(
                "auth", unknown, "adoption-baseline", Set.of(expected)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未知或非 baseline");
    }

    /**
     * 身份匹配但没有基线标签时也应拒绝。
     */
    @Test
    void should_reject_matching_identity_without_baseline_label() {
        LiquibaseMigrationService.ChangeSetIdentity expected = identity("auth-0001-baseline-schema");

        assertThatThrownBy(() -> LiquibaseMigrationService.validateAdoptionHistoryRow(
                "auth", expected, "database:auth,not-adoption-baseline", Set.of(expected)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未知或非 baseline");
    }

    /**
     * 创建测试身份。
     */
    private static LiquibaseMigrationService.ChangeSetIdentity identity(String id) {
        return new LiquibaseMigrationService.ChangeSetIdentity(
                id, "omni", "database/changelog/auth/0001-auth-schema.yaml");
    }
}
