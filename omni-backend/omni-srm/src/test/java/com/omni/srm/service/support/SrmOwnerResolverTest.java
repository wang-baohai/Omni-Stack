package com.omni.srm.service.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.common.core.internal.InternalDataScopeDTO;
import com.omni.common.core.internal.InternalUserDTO;
import com.omni.common.core.result.BusinessException;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 内部负责人不得混入已有供应商门户身份，且合法内部身份与原数据范围仍可用。 */
class SrmOwnerResolverTest {
    @Mock private AuthInternalClient directory;
    @Mock private SrmSupplierPortalUserMapper portalUsers;
    @InjectMocks private SrmOwnerResolver resolver;
    private AutoCloseable mocks;

    /** 初始化真实查询包装器的实体元数据。 */
    @BeforeAll
    static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "srm-owner-boundary"), SrmSupplierPortalUser.class);
    }

    /** 为每例建立独立身份与依赖。 */
    @BeforeEach
    void prepare() {
        mocks = MockitoAnnotations.openMocks(this);
        ServiceIdentityContext.set(new ServiceRequestIdentity(218L, 1L, "internal-admin"));
        ServiceDataScopeContext.set(new ServiceDataScopeContext.ScopeInfo(218L, 1L, "srm:supplier:transfer", 606L, "TENANT", Set.of(), null));
    }

    /** 不向后续测试泄漏身份或授权上下文。 */
    @AfterEach
    void clear() throws Exception {
        ServiceIdentityContext.clear(); ServiceDataScopeContext.clear(); SecurityContextHolder.clearContext(); mocks.close();
    }

    /** 门户账号即使存在主组织，也不能作为内部负责人。 */
    @Test
    void shouldRejectPortalIdentityWithPrimaryOrganization() {
        when(portalUsers.selectCount(any())).thenReturn(1L);
        when(directory.getUser(229L, 1L)).thenReturn(R.ok(user(229L)));
        assertThatThrownBy(() -> resolver.resolveForCommand(229L)).isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(403);
        verify(portalUsers).selectCount(argThat(query -> {
            String sql = query.getSqlSegment();
            return sql.contains("tenant_id") && sql.contains("user_id") && sql.contains("deleted") && !sql.contains("status");
        }));
    }

    /** 创建时默认自己为负责人也必须执行门户身份拒绝。 */
    @Test
    void shouldRejectPortalIdentityAsDefaultCreateOwner() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(229L, 1L, "supplier-portal"));
        when(portalUsers.selectCount(any())).thenReturn(1L);
        when(directory.getUser(229L, 1L)).thenReturn(R.ok(user(229L)));
        assertThatThrownBy(() -> resolver.resolveForCreate(null, "srm:supplier:transfer"))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(403);
    }

    /** 正式转移入口也不能把门户身份写入owner。 */
    @Test
    void shouldRejectPortalIdentityForTransfer() {
        transferScope();
        when(portalUsers.selectCount(any())).thenReturn(1L);
        when(directory.getUser(229L, 1L)).thenReturn(R.ok(user(229L)));
        assertThatThrownBy(() -> resolver.resolveForTransfer(229L, "srm:supplier:transfer"))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(403);
    }

    /** 无门户关联的合法内部负责人保持可用，不以全部拒绝满足负例。 */
    @Test
    void shouldKeepValidInternalOwner() {
        when(portalUsers.selectCount(any())).thenReturn(0L);
        when(directory.getUser(215L, 1L)).thenReturn(R.ok(user(215L)));
        assertThat(resolver.resolveForCommand(215L)).isEqualTo(new SrmOwnerResolver.Owner(215L, 606L));
    }

    /** 新增门户检查不能放宽原组织范围。 */
    @Test
    void shouldStillRejectInternalOwnerOutsideDepartment() {
        ServiceDataScopeContext.set(new ServiceDataScopeContext.ScopeInfo(218L, 1L, "srm:supplier:transfer", 605L, "DEPT", Set.of(605L), null));
        when(portalUsers.selectCount(any())).thenReturn(0L);
        when(directory.getUser(215L, 1L)).thenReturn(R.ok(user(215L)));
        assertThatThrownBy(() -> resolver.resolveForCommand(215L)).isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(403);
    }

    /** 合法内部转移仍需既有的功能权限及独立数据范围。 */
    @Test
    void shouldKeepValidInternalTransfer() {
        transferScope();
        when(portalUsers.selectCount(any())).thenReturn(0L);
        when(directory.getUser(215L, 1L)).thenReturn(R.ok(user(215L)));
        assertThat(resolver.resolveForTransfer(215L, "srm:supplier:transfer"))
                .isEqualTo(new SrmOwnerResolver.Owner(215L, 606L));
    }

    private void transferScope() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("internal-admin", null,
                List.of(new SimpleGrantedAuthority("srm:supplier:transfer"))));
        InternalDataScopeDTO scope = new InternalDataScopeDTO(); scope.setUserId(218L); scope.setTenantId(1L);
        scope.setPermissionCode("srm:supplier:transfer"); scope.setEffectiveScope("TENANT");
        when(directory.resolveDataScope(218L, 1L, "srm:supplier:transfer")).thenReturn(R.ok(scope));
    }

    private InternalUserDTO user(Long id) {
        InternalUserDTO user = new InternalUserDTO(); user.setId(id); user.setTenantId(1L); user.setStatus(1); user.setPrimaryUnitId(606L);
        return user;
    }
}
