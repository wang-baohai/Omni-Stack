package com.omni.auth.e2e;

import java.security.SecureRandom;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 本地 challenge 夹具的环境、命名和生命周期守卫回归，不连接真实存储。 */
class LocalChallengeFixtureTest {

    @Test
    void refusesOtherEnvironmentsAndStorageTargets() {
        String url = "jdbc:mysql://mysql:3306/omni_auth?useSSL=false";
        assertDoesNotThrow(() -> LocalChallengeFixture.checkedEnvironment("local", "redis", url));
        assertThrows(IllegalStateException.class,
                () -> LocalChallengeFixture.checkedEnvironment("production", "redis", url));
        assertThrows(IllegalStateException.class,
                () -> LocalChallengeFixture.checkedEnvironment("local", "other-redis", url));
        assertThrows(IllegalStateException.class,
                () -> LocalChallengeFixture.checkedEnvironment("local", "redis", "jdbc:mysql://mysql:3306/other"));
    }

    @Test
    void confinesNamesAndTtl() {
        assertEquals("login-replay", LocalChallengeFixture.checkedLabel("login-replay"));
        assertThrows(IllegalArgumentException.class, () -> LocalChallengeFixture.checkedLabel("../outside"));
        assertThrows(IllegalArgumentException.class, () -> LocalChallengeFixture.checkedLabel("name/path"));
        assertThrows(IllegalArgumentException.class, () -> LocalChallengeFixture.checkedTtl(0));
        assertThrows(IllegalArgumentException.class, () -> LocalChallengeFixture.checkedTtl(301));
        assertEquals(1, LocalChallengeFixture.checkedTtl(1));
        assertEquals(300, LocalChallengeFixture.checkedTtl(300));
    }

    @Test
    void derivesDeterministicAnswersWithoutStoringAnAnswerInSource() throws Exception {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String seed = HexFormat.of().formatHex(bytes);
        String key = "goal-20261022-unit-test";
        String answer = LocalChallengeFixture.answerFor(seed, key);
        assertTrue(answer.matches("[a-f0-9]{4}"));
        assertEquals(answer, LocalChallengeFixture.answerFor(seed, key));
    }
}
