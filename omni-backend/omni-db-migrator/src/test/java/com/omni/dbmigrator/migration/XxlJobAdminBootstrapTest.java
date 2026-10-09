package com.omni.dbmigrator.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理员密码初始化必须使用厂商摘要格式并保护已有密码。
 */
class XxlJobAdminBootstrapTest {

    @Test
    void shouldHashPasswordAndOnlyUpdateUnchangedSeedAccount() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(contains("WHERE id = 1 AND username = 'admin' AND password = ?")))
                .thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);

        assertThat(XxlJobAdminBootstrap.rotateSeedPassword(connection, "local-admin-password"))
                .isEqualTo(1);
        verify(statement).setString(1,
                "381d86fa6339e1e82cb58d2b97b17fdd3aefa47b9d18a849e933fbfbbe29a979");
        verify(statement).setString(2, XxlJobAdminBootstrap.SEED_PASSWORD_HASH);
    }

    @Test
    void shouldLeavePreviouslyConfiguredAccountUnchanged() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(contains("AND password = ?"))).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(0);

        assertThat(XxlJobAdminBootstrap.rotateSeedPassword(connection, "another-local-password"))
                .isZero();
        verify(statement).executeUpdate();
    }
}
