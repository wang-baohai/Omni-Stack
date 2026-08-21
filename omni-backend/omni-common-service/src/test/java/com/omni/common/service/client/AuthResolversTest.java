package com.omni.common.service.client;

import com.omni.common.core.internal.InternalDataScopeDTO;
import com.omni.common.core.result.R;
import com.omni.common.core.security.XssSettings;
import com.omni.common.service.identity.ServiceRequestIdentity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthResolversTest {

    private final AuthSecuritySettingsClient client = mock(AuthSecuritySettingsClient.class);

    @Test
    void shouldReturnAuthoritativeDataScope() {
        InternalDataScopeDTO dto = new InternalDataScopeDTO();
        dto.setUserId(7L);
        dto.setTenantId(3L);
        dto.setPermissionCode("crm:lead:list");
        when(client.resolveDataScope(7L, 3L, "crm:lead:list")).thenReturn(R.ok(dto));

        InternalDataScopeDTO result = new AuthDataScopeResolver(client).resolve(
                new ServiceRequestIdentity(7L, 3L, "alice"), "crm:lead:list");

        assertThat(result).isSameAs(dto);
    }

    @Test
    void shouldFailClosedForInvalidDataScopeResponse() {
        when(client.resolveDataScope(7L, 3L, "crm:lead:list")).thenReturn(R.fail(500, "error"));

        assertThatThrownBy(() -> new AuthDataScopeResolver(client).resolve(
                new ServiceRequestIdentity(7L, 3L, "alice"), "crm:lead:list"))
                .hasMessageContaining("无法解析");
    }

    @Test
    void shouldReturnAuthoritativeXssSettings() {
        XssSettings settings = XssSettings.builder().enabled(true).build();
        when(client.getXssSettings(3L)).thenReturn(R.ok(settings));

        assertThat(new AuthXssSettingsResolver(client).resolve(3L)).isSameAs(settings);
    }
}
