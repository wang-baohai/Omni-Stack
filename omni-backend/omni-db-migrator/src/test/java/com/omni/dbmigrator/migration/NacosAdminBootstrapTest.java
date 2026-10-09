package com.omni.dbmigrator.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 校验厂商密码格式和迁移重放的账号保护。 */
class NacosAdminBootstrapTest {

    @Test
    void shouldUseCompatibleHashAndOnlyUpdatePublicSeed() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(contains("WHERE username = 'nacos' AND password = ?")))
                .thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        assertThat(NacosAdminBootstrap.rotateSeedPassword(connection, "local-nacos-password")).isEqualTo(1);
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(statement).setString(eq(1), hash.capture());
        assertThat(new BCryptPasswordEncoder().matches("local-nacos-password", hash.getValue())).isTrue();
        verify(statement).setString(2, NacosAdminBootstrap.SEED_PASSWORD_HASH);
    }

    @Test
    void shouldProtectPreviouslyConfiguredPassword() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(contains("AND password = ?"))).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(0);
        assertThat(NacosAdminBootstrap.rotateSeedPassword(connection, "another-password")).isZero();
    }
}
