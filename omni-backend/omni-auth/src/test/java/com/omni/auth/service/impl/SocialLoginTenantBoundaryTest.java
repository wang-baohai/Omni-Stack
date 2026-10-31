package com.omni.auth.service.impl;

import com.omni.auth.config.OAuth2Properties;
import com.omni.auth.dto.ProviderUser;
import com.omni.auth.entity.SysRole;
import com.omni.auth.entity.SysTenant;
import com.omni.auth.entity.SysUser;
import com.omni.auth.entity.SysUserOauthProvider;
import com.omni.auth.entity.TenantProvisionStatusEnum;
import com.omni.auth.mapper.SysRoleMapper;
import com.omni.auth.mapper.SysTenantMapper;
import com.omni.auth.mapper.SysUserMapper;
import com.omni.auth.mapper.SysUserOauthProviderMapper;
import com.omni.auth.mapper.SysUserRoleMapper;
import com.omni.auth.oauth.OAuth2ProviderHandler;
import com.omni.auth.service.JwtTokenService;
import com.omni.auth.service.OnlineUserService;
import com.omni.auth.service.UserService;
import com.omni.auth.util.OAuth2StateUtils;
import com.omni.common.core.result.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 受控提供商/Mapper fixture验证真实服务编排，不代表外部账号实联。 */
class SocialLoginTenantBoundaryTest {

    private final OAuth2ProviderHandler handler = mock(OAuth2ProviderHandler.class);
    private final SysUserOauthProviderMapper bindings = mock(SysUserOauthProviderMapper.class);
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final SysTenantMapper tenants = mock(SysTenantMapper.class);
    private final SysRoleMapper roles = mock(SysRoleMapper.class);
    private final SysUserRoleMapper userRoles = mock(SysUserRoleMapper.class);
    private final UserService userService = mock(UserService.class);
    private final JwtTokenService tokens = mock(JwtTokenService.class);
    private final OAuth2StateUtils states = states();
    private final SocialLoginServiceImpl service = new SocialLoginServiceImpl(
            Map.of("github", handler, "google", handler, "gitee", handler), states,
            bindings, users, tenants, roles, userRoles, userService, tokens,
            mock(OnlineUserService.class), mock(ApplicationEventPublisher.class));

    /** 篡改state或未知提供商必须在外部运输前拒绝。 */
    @Test
    void rejectsUntrustedStateAndUnsupportedProviderBeforeTransport() {
        assertThatThrownBy(() -> service.handleCallback("github", "fixture-code", "forged"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.handleCallback("unsupported", "fixture-code", states.createState(1L)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(handler, bindings, tokens);
    }

    /** 停用和初始化中的租户不能换第三方令牌。 */
    @Test
    void rejectsDisabledOrUnreadyTenantBeforeTransport() {
        SysTenant tenant = activeTenant(2L);
        tenant.setStatus(0);
        when(tenants.selectById(2L)).thenReturn(tenant);
        assertThatThrownBy(() -> service.handleCallback("github", "fixture-code", states.createState(2L)))
                .isInstanceOf(BusinessException.class);
        tenant.setStatus(1);
        tenant.setProvisioningStatus(TenantProvisionStatusEnum.PROVISIONING);
        assertThatThrownBy(() -> service.handleCallback("github", "fixture-code", states.createState(2L)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(handler, bindings, tokens);
    }

    /** 目标租户2不能借同一外部身份进入已绑定租户1，也不得覆盖绑定令牌。 */
    @Test
    void rejectsExistingBindingOwnedByAnotherTenantBeforeAnyWriteOrJwt() {
        prepare(2L);
        SysUserOauthProvider binding = new SysUserOauthProvider();
        binding.setUserId(991L);
        when(bindings.selectByProviderAndUserId("github", "external-71")).thenReturn(binding);
        SysUser foreign = user(991L, 1L);
        when(users.selectById(991L)).thenReturn(foreign);

        assertThatThrownBy(() -> service.handleCallback("github", "fixture-code", states.createState(2L)))
                .isInstanceOf(BusinessException.class);

        verify(bindings, never()).updateById(any(SysUserOauthProvider.class));
        verifyNoInteractions(tokens);
    }

    /** 已绑定同租户用户可继续登录，权限只来源真实用户服务结果。 */
    @Test
    void acceptsExistingSameTenantIdentityWithExactRoles() {
        prepare(2L);
        SysUserOauthProvider binding = new SysUserOauthProvider();
        binding.setUserId(991L);
        when(bindings.selectByProviderAndUserId("github", "external-71")).thenReturn(binding);
        SysUser user = user(991L, 2L);
        when(users.selectById(991L)).thenReturn(user);
        when(userService.getUserRoles(991L)).thenReturn(List.of("USER"));
        when(userService.getUserPermissions(991L)).thenReturn(List.of("srm:portal:enroll"));
        when(tokens.generateToken(user, List.of("USER"), List.of("srm:portal:enroll"))).thenReturn("controlled-fixture-jwt");

        assertThat(service.handleCallback("github", "fixture-code", states.createState(2L)).getAccessToken())
                .isEqualTo("controlled-fixture-jwt");
        verify(tokens).generateToken(user, List.of("USER"), List.of("srm:portal:enroll"));
    }

    /** 并发绑定回读也不能给其他租户的用户写入目标租户角色或签令牌。 */
    @Test
    void concurrentBindingFromAnotherTenantIsRejectedBeforeRoleAssignment() {
        prepare(2L);
        doAnswer(invocation -> { ((SysUser) invocation.getArgument(0)).setId(992L); return 1; })
                .when(users).insert(any(SysUser.class));
        SysUserOauthProvider winner = new SysUserOauthProvider();
        winner.setUserId(991L);
        when(bindings.selectByProviderAndUserId("github", "external-71")).thenReturn(null, winner);
        when(bindings.insert(any(SysUserOauthProvider.class))).thenThrow(new DuplicateKeyException("controlled race"));
        when(users.selectById(991L)).thenReturn(user(991L, 1L));

        assertThatThrownBy(() -> service.handleCallback("github", "fixture-code", states.createState(2L)))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(roles, userRoles, tokens);
    }

    /** 非法资料主体在查询/写入本地绑定前失败关闭。 */
    @Test
    void rejectsMissingExternalSubjectBeforeLocalBinding() {
        prepare(2L);
        when(handler.fetchUserProfile("fixture-provider-token")).thenReturn(ProviderUser.builder().username("fixture").build());

        assertThatThrownBy(() -> service.handleCallback("github", "fixture-code", states.createState(2L)))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(bindings, users, tokens);
    }

    /** 首次创建仅分配目标租户默认USER，不能取得管理员或另一个租户的角色。 */
    @ParameterizedTest
    @CsvSource({"github,gh_", "google,go_", "gitee,ge_"})
    void newIdentityUsesProviderPrefixAndTargetTenantDefaultRole(String provider, String prefix) {
        prepare(2L);
        doAnswer(invocation -> { ((SysUser) invocation.getArgument(0)).setId(992L); return 1; })
                .when(users).insert(any(SysUser.class));
        SysRole role = new SysRole();
        role.setId(202L);
        role.setTenantId(2L);
        role.setRoleCode("USER");
        when(roles.selectByTenantIdAndRoleCode(2L, "USER")).thenReturn(role);
        when(userService.getUserRoles(992L)).thenReturn(List.of("USER"));
        when(userService.getUserPermissions(992L)).thenReturn(List.of());
        when(tokens.generateToken(any(SysUser.class), any(), any())).thenReturn("controlled-fixture-jwt");

        service.handleCallback(provider, "fixture-code", states.createState(2L));

        verify(userRoles).insert(992L, 202L);
        verify(users).insert(org.mockito.ArgumentMatchers.argThat((SysUser user) -> user.getTenantId().equals(2L)
                && user.getUsername().equals(prefix + "fixture")));
    }

    private void prepare(Long tenantId) {
        when(tenants.selectById(tenantId)).thenReturn(activeTenant(tenantId));
        when(handler.exchangeCodeForAccessToken("fixture-code")).thenReturn("fixture-provider-token");
        when(handler.fetchUserProfile("fixture-provider-token")).thenReturn(ProviderUser.builder()
                .providerUserId("external-71").username("fixture").displayName("Fixture").build());
    }

    private SysTenant activeTenant(Long tenantId) {
        SysTenant tenant = new SysTenant();
        tenant.setId(tenantId);
        tenant.setStatus(1);
        tenant.setProvisioningStatus(TenantProvisionStatusEnum.ACTIVE);
        return tenant;
    }

    private SysUser user(Long id, Long tenantId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setStatus(1);
        user.setUsername("fixture-local-user");
        return user;
    }

    private OAuth2StateUtils states() {
        OAuth2Properties properties = new OAuth2Properties();
        properties.setStateSecret("controlled-social-tenant-fixture-secret");
        return new OAuth2StateUtils(properties);
    }
}
