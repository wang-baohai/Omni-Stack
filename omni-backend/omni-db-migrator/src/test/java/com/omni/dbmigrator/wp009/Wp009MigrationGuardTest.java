package com.omni.dbmigrator.wp009;

import liquibase.changelog.ChangeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WP-009 受控迁移守卫离线单测（D04 / RF08）。
 * <p>证明固定集合、错目标、未知 pending 的拒绝语义，以及批准集合的正向放行。</p>
 *
 * @author Omni-Stack Team
 */
class Wp009MigrationGuardTest {

    private static final String F_UUID = "4a955545-b585-11f1-b9fc-42f7a703eb82";

    private static ChangeSet cs(String id, String author, String path) {
        ChangeSet c = mock(ChangeSet.class);
        when(c.getId()).thenReturn(id);
        when(c.getAuthor()).thenReturn(author);
        when(c.getFilePath()).thenReturn(path);
        return c;
    }

    private static final String P = "database/changelog/auth/0006-wp009-auth-management.yaml";

    @Test
    @DisplayName("目标绑定：UUID+schema 匹配放行")
    void targetOk() {
        assertTrue(Wp009MigrationGuard.verifyTarget(F_UUID,
                new Wp009MigrationGuard.TargetFingerprint(F_UUID, "omni_auth")).ok());
    }

    @Test
    @DisplayName("目标绑定：错 UUID 立即拒绝（不进入写查询）")
    void wrongUuidRejected() {
        assertFalse(Wp009MigrationGuard.verifyTarget(F_UUID,
                new Wp009MigrationGuard.TargetFingerprint("deadbeef-0000-0000-0000-000000000000", "omni_auth")).ok());
    }

    @Test
    @DisplayName("目标绑定：错 schema 拒绝")
    void wrongSchemaRejected() {
        assertFalse(Wp009MigrationGuard.verifyTarget(F_UUID,
                new Wp009MigrationGuard.TargetFingerprint(F_UUID, "omni_base")).ok());
    }

    @Test
    @DisplayName("pending 恰等于批准集合：放行")
    void approvedPendingOk() {
        List<ChangeSet> pending = List.of(
                cs("auth-0006-wp009-login-policy-table", "omni-stack", P),
                cs("auth-0006-wp009-permissions", "omni-stack", P),
                cs("auth-0006-wp009-oauth2-metadata-widen", "omni-stack", P));
        assertTrue(Wp009MigrationGuard.verifyPendingMatchesApproved(pending).ok());
    }

    @Test
    @DisplayName("未知/额外 pending：拒绝，绝不顺手跑其他模块增量")
    void unknownPendingRejected() {
        List<ChangeSet> pending = List.of(
                cs("auth-0006-wp009-login-policy-table", "omni-stack", P),
                cs("auth-0006-wp009-permissions", "omni-stack", P),
                cs("auth-0006-wp009-oauth2-metadata-widen", "omni-stack", P),
                cs("some-other-module-9999", "someone", "database/changelog/other/x.yaml"));
        assertFalse(Wp009MigrationGuard.verifyPendingMatchesApproved(pending).ok());
    }

    @Test
    @DisplayName("缺失批准项：拒绝（不静默少跑本包增量）")
    void missingApprovedRejected() {
        List<ChangeSet> pending = List.of(
                cs("auth-0006-wp009-login-policy-table", "omni-stack", P));
        assertFalse(Wp009MigrationGuard.verifyPendingMatchesApproved(pending).ok());
    }

    @Test
    @DisplayName("批准集合不可被外部放宽：大小写/别名不匹配即视为未知")
    void caseSensitiveIdentity() {
        List<ChangeSet> pending = List.of(
                cs("AUTH-0006-WP009-LOGIN-POLICY-TABLE", "omni-stack", P),
                cs("auth-0006-wp009-permissions", "omni-stack", P),
                cs("auth-0006-wp009-oauth2-metadata-widen", "omni-stack", P));
        Wp009MigrationGuard.GuardResult r = Wp009MigrationGuard.verifyPendingMatchesApproved(pending);
        assertFalse(r.ok());
    }
}
