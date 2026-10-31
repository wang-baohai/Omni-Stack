package com.omni.srm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.common.core.internal.InternalUserOptionDTO;
import com.omni.common.core.result.R;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.srm.client.AuthInternalClient;
import com.omni.srm.entity.SrmSupplierPortalUser;
import com.omni.srm.mapper.SrmSupplierPortalUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 候选列表必须排除门户身份，保留原数据范围并在排除后应用数量上限。 */
class OwnerOptionServiceImplTest {
    @Mock private AuthInternalClient directory;
    @Mock private SrmSupplierPortalUserMapper portalUsers;
    @InjectMocks private OwnerOptionServiceImpl service;
    private AutoCloseable mocks;

    /** 初始化门户关联查询元数据。 */
    @BeforeAll
    static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "srm-owner-options"), SrmSupplierPortalUser.class);
    }

    /** 独立设置当前租户及原内部数据范围。 */
    @BeforeEach
    void prepare() {
        mocks = MockitoAnnotations.openMocks(this);
        ServiceIdentityContext.set(new ServiceRequestIdentity(218L, 1L, "internal-admin"));
        ServiceDataScopeContext.set(new ServiceDataScopeContext.ScopeInfo(218L, 1L, "srm:owner:list", 606L, "TENANT", Set.of(), null));
    }

    /** 清理当前线程的上下文。 */
    @AfterEach
    void clear() throws Exception {
        ServiceIdentityContext.clear(); ServiceDataScopeContext.clear(); mocks.close();
    }

    /** ACTIVE门户身份不能出现在内部负责人候选中。 */
    @Test
    void shouldExcludeActivePortalUserAndKeepInternalUser() {
        when(directory.listOwnerOptions(1L, "", 100)).thenReturn(R.ok(List.of(option(229L), option(215L))));
        when(portalUsers.selectList(any())).thenReturn(List.of(portal("ACTIVE")));
        assertThat(service.list("", 100)).extracting("userId").containsExactly(215L);
    }

    /** 暂停门户关联也不能把外部身份变成内部候选；数量上限在过滤后应用。 */
    @Test
    void shouldExcludeInactivePortalBeforeApplyingLimit() {
        when(directory.listOwnerOptions(1L, "", 100)).thenReturn(R.ok(List.of(option(229L), option(215L), option(216L))));
        when(portalUsers.selectList(any())).thenReturn(List.of(portal("INACTIVE")));
        assertThat(service.list("", 1)).extracting("userId").containsExactly(215L);
        verify(portalUsers).selectList(argThat(query -> {
            String sql = query.getSqlSegment();
            return sql.contains("tenant_id") && sql.contains("user_id") && sql.contains("deleted") && !sql.contains("status");
        }));
    }

    /** 无门户关联时保留合法目录结果。 */
    @Test
    void shouldKeepInternalOptions() {
        when(directory.listOwnerOptions(1L, "", 100)).thenReturn(R.ok(List.of(option(215L))));
        when(portalUsers.selectList(any())).thenReturn(List.of());
        assertThat(service.list("", 100)).extracting("userId").containsExactly(215L);
    }

    /** SELF范围之外的用户仍不能成为候选，空候选不触发无界门户查询。 */
    @Test
    void shouldKeepSelfScopeAndAvoidEmptyMembershipQuery() {
        ServiceDataScopeContext.set(new ServiceDataScopeContext.ScopeInfo(218L, 1L, "srm:owner:list", 606L, "SELF", Set.of(), null));
        when(directory.listOwnerOptions(1L, "", 100)).thenReturn(R.ok(List.of(option(215L))));
        assertThat(service.list("", 100)).isEmpty(); verifyNoInteractions(portalUsers);
    }

    private InternalUserOptionDTO option(Long id) {
        InternalUserOptionDTO user = new InternalUserOptionDTO(); user.setId(id); user.setTenantId(1L); user.setPrimaryUnitId(606L);
        user.setUsername("fixture_" + id); return user;
    }

    private SrmSupplierPortalUser portal(String status) {
        SrmSupplierPortalUser user = new SrmSupplierPortalUser(); user.setTenantId(1L); user.setUserId(229L); user.setStatus(status); user.setDeleted(0);
        return user;
    }
}
