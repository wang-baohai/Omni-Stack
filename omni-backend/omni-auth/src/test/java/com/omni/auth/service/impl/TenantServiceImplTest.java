package com.omni.auth.service.impl;

import com.omni.auth.dto.CreateTenantRequest;
import com.omni.auth.entity.SysTenant;
import com.omni.auth.mapper.SysTenantMapper;
import com.omni.auth.mapper.TenantProvisionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 租户初始化管理员凭据测试。 */
class TenantServiceImplTest {

    /** 创建租户必须使用请求中的密码生成哈希，禁止隐式公共默认密码。 */
    @Test
    void shouldProvisionTenantWithExplicitAdminPassword() {
        SysTenantMapper tenantMapper = mock(SysTenantMapper.class);
        TenantProvisionMapper provisionMapper = mock(TenantProvisionMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        doAnswer(invocation -> {
            SysTenant tenant = invocation.getArgument(0);
            tenant.setId(9L);
            return 1;
        }).when(tenantMapper).insert(any(SysTenant.class));
        when(passwordEncoder.encode("A-secure-admin-password"))
                .thenReturn("$2a$10$encoded");
        CreateTenantRequest request = new CreateTenantRequest();
        request.setTenantCode("tenant-nine");
        request.setTenantName("第九租户");
        request.setAdminPassword("A-secure-admin-password");

        SysTenant tenant = new TenantServiceImpl(
                tenantMapper, provisionMapper, passwordEncoder).createTenant(request);

        assertThat(tenant.getId()).isEqualTo(9L);
        verify(passwordEncoder).encode("A-secure-admin-password");
        verify(provisionMapper).initTenant(9L, "第九租户", "$2a$10$encoded");
    }
}
