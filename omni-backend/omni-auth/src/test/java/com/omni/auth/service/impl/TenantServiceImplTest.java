package com.omni.auth.service.impl;

import com.omni.auth.dto.CreateTenantRequest;
import com.omni.auth.entity.SysTenant;
import com.omni.auth.entity.TenantProvisionStatusEnum;
import com.omni.auth.mapper.SysTenantMapper;
import com.omni.auth.service.TenantProvisionService;
import com.omni.common.core.result.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        TenantProvisionService provisionService = mock(TenantProvisionService.class);
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
                tenantMapper, provisionService, passwordEncoder).createTenant(request);

        assertThat(tenant.getId()).isEqualTo(9L);
        assertThat(tenant.getProvisioningStatus()).isEqualTo(TenantProvisionStatusEnum.PROVISIONING);
        verify(passwordEncoder).encode("A-secure-admin-password");
        verify(provisionService).startProvisioning(tenant, "$2a$10$encoded");
    }

    /** 初始化未完成的租户必须拒绝登录。 */
    @Test
    void shouldRejectLoginUntilProvisioningIsActive() {
        SysTenantMapper tenantMapper = mock(SysTenantMapper.class);
        TenantProvisionService provisionService = mock(TenantProvisionService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        SysTenant tenant = new SysTenant();
        tenant.setId(9L);
        tenant.setStatus(1);
        tenant.setProvisioningStatus(TenantProvisionStatusEnum.PROVISIONING);
        when(tenantMapper.selectById(9L)).thenReturn(tenant);
        TenantServiceImpl service = new TenantServiceImpl(
                tenantMapper, provisionService, passwordEncoder);

        assertThatThrownBy(() -> service.requireLoginAvailable(9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("正在初始化");

        tenant.setProvisioningStatus(TenantProvisionStatusEnum.ACTIVE);
        service.requireLoginAvailable(9L);
    }
}
