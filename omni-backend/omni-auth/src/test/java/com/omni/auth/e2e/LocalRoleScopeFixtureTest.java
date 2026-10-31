package com.omni.auth.e2e;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 候选配置夹具不得接管既有账号、其他本次身份或无创建证据的用户。 */
class LocalRoleScopeFixtureTest {

    @Test
    void acceptsOnlyOwnedNamedCandidateIdentities() {
        assertDoesNotThrow(() -> LocalRoleScopeFixture.checkedIdentity("srmManager", "goal_20261022_srmmanager", true));
        assertThrows(IllegalStateException.class,
                () -> LocalRoleScopeFixture.checkedIdentity("srmManager", "admin", true));
        assertThrows(IllegalStateException.class,
                () -> LocalRoleScopeFixture.checkedIdentity("employee", "goal_20261022_employee", true));
        assertThrows(IllegalStateException.class,
                () -> LocalRoleScopeFixture.checkedIdentity("srmManager", "goal_20261022_srmmanager", false));
    }
}
