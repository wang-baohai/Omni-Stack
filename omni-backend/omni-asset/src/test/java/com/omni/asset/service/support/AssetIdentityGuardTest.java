package com.omni.asset.service.support;

import com.omni.asset.client.AuthInternalClient;
import com.omni.common.core.internal.InternalOrgDTO;
import com.omni.common.core.internal.InternalUserDTO;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** 资产用户与组织引用完整性守卫测试。 */
@ExtendWith(MockitoExtension.class)
class AssetIdentityGuardTest {

    @Mock private AuthInternalClient authInternalClient;

    /** 启用用户必须属于指定的同租户组织。 */
    @Test
    void shouldAcceptActiveUserInMatchingUnit() {
        when(authInternalClient.getUser(7L, 31L)).thenReturn(R.ok(user(7L, 31L, 12L, 1)));
        when(authInternalClient.getOrg(12L, 31L)).thenReturn(R.ok(org(12L, 31L, 1)));

        assertThatCode(() -> new AssetIdentityGuard(authInternalClient)
                .requireActiveUserInUnit(31L, 7L, 12L)).doesNotThrowAnyException();
    }

    /** 用户主组织与请求组织不一致时必须拒绝。 */
    @Test
    void shouldRejectUserOutsideRequestedUnit() {
        when(authInternalClient.getUser(7L, 31L)).thenReturn(R.ok(user(7L, 31L, 13L, 1)));
        when(authInternalClient.getOrg(12L, 31L)).thenReturn(R.ok(org(12L, 31L, 1)));

        assertThatThrownBy(() -> new AssetIdentityGuard(authInternalClient)
                .requireActiveUserInUnit(31L, 7L, 12L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
    }

    /** Auth 无有效响应时必须以 503 失败关闭。 */
    @Test
    void shouldFailClosedWhenAuthResponseMissing() {
        when(authInternalClient.getUser(7L, 31L)).thenReturn(null);

        assertThatThrownBy(() -> new AssetIdentityGuard(authInternalClient)
                .requireActiveUserInUnit(31L, 7L, 12L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(503);
    }

    private InternalUserDTO user(Long id, Long tenantId, Long unitId, Integer status) {
        InternalUserDTO user = new InternalUserDTO();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setPrimaryUnitId(unitId);
        user.setStatus(status);
        return user;
    }

    private InternalOrgDTO org(Long id, Long tenantId, Integer status) {
        InternalOrgDTO org = new InternalOrgDTO();
        org.setId(id);
        org.setTenantId(tenantId);
        org.setStatus(status);
        return org;
    }
}
