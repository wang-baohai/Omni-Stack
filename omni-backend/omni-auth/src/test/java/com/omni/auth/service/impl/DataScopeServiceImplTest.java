package com.omni.auth.service.impl;

import com.omni.auth.entity.SysOrgUnit;
import com.omni.auth.entity.SysRole;
import com.omni.auth.entity.SysUser;
import com.omni.auth.mapper.SysOrgUnitMapper;
import com.omni.auth.mapper.SysRoleDeptMapper;
import com.omni.auth.mapper.SysRoleMapper;
import com.omni.auth.mapper.SysUserMapper;
import com.omni.common.core.internal.InternalDataScopeDTO;
import com.omni.common.core.result.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DataScopeServiceImpl} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class DataScopeServiceImplTest {

    /** 用户 Mapper */
    @Mock
    private SysUserMapper sysUserMapper;

    /** 角色 Mapper */
    @Mock
    private SysRoleMapper sysRoleMapper;

    /** 组织 Mapper */
    @Mock
    private SysOrgUnitMapper sysOrgUnitMapper;

    /** 自定义部门 Mapper */
    @Mock
    private SysRoleDeptMapper sysRoleDeptMapper;

    /** 被测服务 */
    private DataScopeServiceImpl service;

    /**
     * 初始化被测对象。
     */
    @BeforeEach
    void setUp() {
        service = new DataScopeServiceImpl(
                sysUserMapper, sysRoleMapper, sysOrgUnitMapper, sysRoleDeptMapper);
    }

    /**
     * 精确权限解析只应查询真正授予该权限的角色。
     */
    @Test
    void should_merge_only_roles_granting_permission() {
        SysUser user = user(7L, 2L, 10L);
        SysOrgUnit unit = unit(10L, 2L, "/10/");
        SysRole tenantRole = role(21L, "TENANT");
        SysRole selfRole = role(22L, "SELF");
        when(sysUserMapper.selectEnabledByIdAndTenantId(7L, 2L)).thenReturn(user);
        when(sysOrgUnitMapper.selectByIdAndTenantId(10L, 2L)).thenReturn(unit);
        when(sysRoleMapper.selectRolesGrantingPermission(7L, 2L, "crm:lead:update"))
                .thenReturn(List.of(selfRole, tenantRole));

        InternalDataScopeDTO result = service.resolveDataScope(7L, 2L, "crm:lead:update");

        assertThat(result.getEffectiveScope()).isEqualTo("TENANT");
        assertThat(result.getPermissionCode()).isEqualTo("crm:lead:update");
        assertThat(result.getPrimaryUnitId()).isEqualTo(10L);
        assertThat(result.getAccessibleUnitIds()).isEmpty();
        assertThat(result.getSecurityVersion()).isNotNull().isGreaterThanOrEqualTo(0L);
        verify(sysRoleMapper, never()).selectRolesByUserIdAndTenantId(7L, 2L);
    }

    /**
     * 用户没有任何角色授予目标权限时应拒绝解析。
     */
    @Test
    void should_reject_when_permission_is_not_granted() {
        when(sysUserMapper.selectEnabledByIdAndTenantId(7L, 2L)).thenReturn(user(7L, 2L, null));
        when(sysRoleMapper.selectRolesGrantingPermission(7L, 2L, "crm:lead:update"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.resolveDataScope(7L, 2L, "crm:lead:update"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(403));
    }

    /**
     * 用户与租户不匹配时必须在查询角色前失败关闭。
     */
    @Test
    void should_reject_when_user_does_not_belong_to_tenant() {
        when(sysUserMapper.selectEnabledByIdAndTenantId(7L, 3L)).thenReturn(null);

        assertThatThrownBy(() -> service.resolveDataScope(7L, 3L, "crm:lead:list"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(403));
        verify(sysRoleMapper, never()).selectRolesGrantingPermission(7L, 3L, "crm:lead:list");
    }

    /**
     * 部门及下级范围必须使用带租户条件的物化路径查询。
     */
    @Test
    void should_resolve_department_tree_with_tenant_filter() {
        SysUser user = user(7L, 2L, 10L);
        SysOrgUnit unit = unit(10L, 2L, "/10/");
        when(sysUserMapper.selectEnabledByIdAndTenantId(7L, 2L)).thenReturn(user);
        when(sysRoleMapper.selectRolesGrantingPermission(7L, 2L, "crm:lead:list"))
                .thenReturn(List.of(role(21L, "DEPT_AND_BELOW")));
        when(sysOrgUnitMapper.selectByIdAndTenantId(10L, 2L)).thenReturn(unit);
        when(sysOrgUnitMapper.selectDescendantIdsByTenantIdAndPath(2L, "/10/"))
                .thenReturn(List.of(10L, 11L, 12L));

        InternalDataScopeDTO result = service.resolveDataScope(7L, 2L, "crm:lead:list");

        assertThat(result.getAccessibleUnitIds()).containsExactlyInAnyOrderElementsOf(Set.of(10L, 11L, 12L));
        verify(sysOrgUnitMapper).selectDescendantIdsByTenantIdAndPath(2L, "/10/");
    }

    /**
     * 创建测试用户。
     *
     * @param id            用户 ID
     * @param tenantId      租户 ID
     * @param primaryUnitId 主组织单元 ID
     * @return 用户实体
     */
    private SysUser user(Long id, Long tenantId, Long primaryUnitId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setPrimaryUnitId(primaryUnitId);
        user.setStatus(1);
        return user;
    }

    /**
     * 创建测试角色。
     *
     * @param id        角色 ID
     * @param dataScope 数据范围
     * @return 角色实体
     */
    private SysRole role(Long id, String dataScope) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setDataScope(dataScope);
        role.setStatus(1);
        return role;
    }

    /**
     * 创建测试组织单元。
     *
     * @param id       组织单元 ID
     * @param tenantId 租户 ID
     * @param path     物化路径
     * @return 组织单元实体
     */
    private SysOrgUnit unit(Long id, Long tenantId, String path) {
        SysOrgUnit unit = new SysOrgUnit();
        unit.setId(id);
        unit.setTenantId(tenantId);
        unit.setPath(path);
        unit.setStatus(1);
        return unit;
    }
}
