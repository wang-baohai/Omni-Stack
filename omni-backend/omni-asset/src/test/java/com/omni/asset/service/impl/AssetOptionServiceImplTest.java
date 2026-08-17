package com.omni.asset.service.impl;

import com.omni.asset.client.AuthInternalClient;
import com.omni.asset.client.SrmInternalClient;
import com.omni.asset.dto.AssetViews;
import com.omni.asset.mapper.AstAssetMapper;
import com.omni.asset.security.AssetTenantContext;
import com.omni.common.core.internal.InternalUserOptionDTO;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 资产候选项服务的租户边界与跨服务响应测试。 */
class AssetOptionServiceImplTest {

    /** 清理租户身份，避免线程复用污染其他测试。 */
    @AfterEach
    void clearContext() {
        AssetTenantContext.clear();
    }

    /** 用户候选仅接受当前租户且包含主组织的最小响应。 */
    @Test
    void shouldMapConsistentUserOption() {
        AuthInternalClient authClient = mock(AuthInternalClient.class);
        AstAssetMapper mapper = mock(AstAssetMapper.class);
        SrmInternalClient srmClient = mock(SrmInternalClient.class);
        InternalUserOptionDTO user = new InternalUserOptionDTO();
        user.setId(31L);
        user.setTenantId(1L);
        user.setPrimaryUnitId(9L);
        user.setUsername("employee");
        user.setNickname("员工");
        when(authClient.listUserOptions(1L, "员", 30)).thenReturn(R.ok(List.of(user)));
        AssetTenantContext.set(new AssetTenantContext.RequestIdentity(7L, 1L, "admin"));

        List<AssetViews.UserOptionVO> result =
                new AssetOptionServiceImpl(authClient, mapper, srmClient).listUsers("员", 30);

        assertThat(result).singleElement().satisfies(option -> {
            assertThat(option.getId()).isEqualTo(31L);
            assertThat(option.getPrimaryUnitId()).isEqualTo(9L);
            assertThat(option.getUsername()).isEqualTo("employee");
        });
    }

    /** 跨租户用户候选必须失败关闭。 */
    @Test
    void shouldRejectCrossTenantUserOption() {
        AuthInternalClient authClient = mock(AuthInternalClient.class);
        InternalUserOptionDTO user = new InternalUserOptionDTO();
        user.setId(31L);
        user.setTenantId(2L);
        user.setPrimaryUnitId(9L);
        when(authClient.listUserOptions(1L, null, 30)).thenReturn(R.ok(List.of(user)));
        AssetTenantContext.set(new AssetTenantContext.RequestIdentity(7L, 1L, "admin"));

        assertThatThrownBy(() -> new AssetOptionServiceImpl(
                authClient, mock(AstAssetMapper.class), mock(SrmInternalClient.class))
                .listUsers(null, 30))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不一致");
    }

    /** 未归属组织的 Portal 或社交登录用户不能作为资产责任人，且不应拖垮整个候选列表。 */
    @Test
    void shouldSkipUserWithoutPrimaryUnit() {
        AuthInternalClient authClient = mock(AuthInternalClient.class);
        InternalUserOptionDTO unassignedUser = new InternalUserOptionDTO();
        unassignedUser.setId(32L);
        unassignedUser.setTenantId(1L);
        unassignedUser.setUsername("social-user");
        when(authClient.listUserOptions(1L, null, 30)).thenReturn(R.ok(List.of(unassignedUser)));
        AssetTenantContext.set(new AssetTenantContext.RequestIdentity(7L, 1L, "admin"));

        List<AssetViews.UserOptionVO> result = new AssetOptionServiceImpl(
                authClient, mock(AstAssetMapper.class), mock(SrmInternalClient.class))
                .listUsers(null, 30);

        assertThat(result).isEmpty();
    }

    /** 供应商候选固定限定当前租户和已批准状态。 */
    @Test
    void shouldRequestApprovedSupplierOptionsForCurrentTenant() {
        AuthInternalClient authClient = mock(AuthInternalClient.class);
        AstAssetMapper mapper = mock(AstAssetMapper.class);
        SrmInternalClient srmClient = mock(SrmInternalClient.class);
        AssetViews.SupplierOptionVO supplier = new AssetViews.SupplierOptionVO();
        supplier.setId(11L);
        supplier.setName("云采供应商");
        when(srmClient.search(1L, 1L, "APPROVED", "云采", 30))
                .thenReturn(R.ok(List.of(supplier)));
        AssetTenantContext.set(new AssetTenantContext.RequestIdentity(7L, 1L, "admin"));

        List<AssetViews.SupplierOptionVO> result =
                new AssetOptionServiceImpl(authClient, mapper, srmClient)
                        .listSuppliers("云采", 30);

        assertThat(result).containsExactly(supplier);
        verify(srmClient).search(1L, 1L, "APPROVED", "云采", 30);
    }
}
