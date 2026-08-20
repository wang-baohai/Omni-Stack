package com.omni.auth.service.impl;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.omni.auth.catalog.ModuleCatalog;
import com.omni.auth.catalog.ModuleCatalog.ModuleDefinition;
import com.omni.auth.catalog.ModuleCatalog.TenantProvisioningMode;
import com.omni.auth.catalog.ModuleCatalogLoader;
import com.omni.auth.catalog.ProvisioningSeedCatalog;
import com.omni.auth.catalog.ProvisioningSeedCatalog.SeedDefinition;
import com.omni.auth.catalog.ProvisioningSeedCatalogLoader;
import com.omni.auth.entity.SysOrgUnit;
import com.omni.auth.entity.SysPermission;
import com.omni.auth.entity.SysRole;
import com.omni.auth.entity.SysUser;
import com.omni.auth.entity.SysXssBlacklistRule;
import com.omni.auth.entity.SysXssConfig;
import com.omni.auth.mapper.SysOrgUnitMapper;
import com.omni.auth.mapper.SysPermissionMapper;
import com.omni.auth.mapper.SysRoleMapper;
import com.omni.auth.mapper.SysRolePermissionMapper;
import com.omni.auth.mapper.SysUserMapper;
import com.omni.auth.mapper.SysUserRoleMapper;
import com.omni.auth.mapper.SysUserUnitMapper;
import com.omni.auth.mapper.SysXssBlacklistRuleMapper;
import com.omni.auth.mapper.SysXssConfigMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Auth 本地租户初始化幂等性测试。
 */
@SuppressWarnings("unchecked")
class AuthTenantTemplateProvisionerTest {

    /**
     * 首次初始化必须从模板创建权限、角色、管理员和 XSS，密码哈希不离开用户写入边界。
     */
    @Test
    void should_provision_local_auth_data_from_natural_key_templates() {
        ModuleCatalogLoader moduleLoader = moduleLoader();
        ProvisioningSeedCatalogLoader seedLoader = seedLoader();
        SysPermissionMapper permissionMapper = mock(SysPermissionMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRolePermissionMapper rolePermissionMapper = mock(SysRolePermissionMapper.class);
        SysOrgUnitMapper orgMapper = mock(SysOrgUnitMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        SysUserUnitMapper userUnitMapper = mock(SysUserUnitMapper.class);
        SysXssConfigMapper xssConfigMapper = mock(SysXssConfigMapper.class);
        SysXssBlacklistRuleMapper xssRuleMapper = mock(SysXssBlacklistRuleMapper.class);
        SysPermission templatePermission = permission(10L, 1L, "system", "/10/");
        SysRole templateRole = role(20L, 1L, "SUPER_ADMIN");
        SysXssBlacklistRule templateRule = xssRule(50L, 1L);
        when(permissionMapper.selectList(any())).thenReturn(List.of(templatePermission), List.of());
        when(roleMapper.selectList(any())).thenReturn(List.of(templateRole), List.of());
        when(rolePermissionMapper.selectPermissionIdsByRoleId(20L)).thenReturn(List.of(10L));
        when(orgMapper.selectOne(any())).thenReturn(null);
        when(userMapper.selectOne(any())).thenReturn(null);
        when(xssConfigMapper.selectCount(any())).thenReturn(0L);
        when(xssRuleMapper.selectList(any())).thenReturn(List.of(), List.of(templateRule));
        assignIdOnInsert(permissionMapper, 11L);
        assignIdOnInsert(roleMapper, 21L);
        doAnswer(invocation -> {
            invocation.<SysOrgUnit>getArgument(0).setId(30L);
            return 1;
        }).when(orgMapper).insert(any(SysOrgUnit.class));
        doAnswer(invocation -> {
            invocation.<SysUser>getArgument(0).setId(40L);
            return 1;
        }).when(userMapper).insert(any(SysUser.class));

        AuthTenantTemplateProvisioner service = new AuthTenantTemplateProvisioner(
                moduleLoader, seedLoader, permissionMapper, roleMapper, rolePermissionMapper,
                orgMapper, userMapper, userRoleMapper, userUnitMapper, xssConfigMapper, xssRuleMapper);
        service.provisionLocal(9L, "第九租户", "$2a$10$encoded");

        ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(userCaptor.capture());
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("$2a$10$encoded");
        assertThat(userCaptor.getValue().getTenantId()).isEqualTo(9L);
        verify(rolePermissionMapper).batchInsertIgnore(21L, List.of(11L));
        verify(userRoleMapper).insertIgnore(40L, 21L);
        verify(userUnitMapper).insertIgnore(40L, 30L, 1);
        verify(xssConfigMapper).insert(any(SysXssConfig.class));
        verify(xssRuleMapper).insert(any(SysXssBlacklistRule.class));
    }

    /**
     * 重放初始化不得覆盖已存在管理员密码或租户自定义模板数据。
     */
    @Test
    void should_preserve_existing_admin_password_on_replay() {
        ModuleCatalogLoader moduleLoader = moduleLoader();
        ProvisioningSeedCatalogLoader seedLoader = seedLoader();
        SysPermissionMapper permissionMapper = mock(SysPermissionMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRolePermissionMapper rolePermissionMapper = mock(SysRolePermissionMapper.class);
        SysOrgUnitMapper orgMapper = mock(SysOrgUnitMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
        SysUserUnitMapper userUnitMapper = mock(SysUserUnitMapper.class);
        SysXssConfigMapper xssConfigMapper = mock(SysXssConfigMapper.class);
        SysXssBlacklistRuleMapper xssRuleMapper = mock(SysXssBlacklistRuleMapper.class);
        SysPermission templatePermission = permission(10L, 1L, "system", "/10/");
        SysPermission targetPermission = permission(11L, 9L, "system", "/11/");
        targetPermission.setPermissionName("租户自定义系统菜单");
        SysRole templateRole = role(20L, 1L, "SUPER_ADMIN");
        SysRole targetRole = role(21L, 9L, "SUPER_ADMIN");
        targetRole.setRoleName("租户自定义管理员");
        SysOrgUnit root = new SysOrgUnit();
        root.setId(30L);
        SysUser admin = new SysUser();
        admin.setId(40L);
        admin.setTenantId(9L);
        admin.setUsername("admin");
        admin.setPassword("existing-hash");
        admin.setPrimaryUnitId(30L);
        SysXssBlacklistRule templateRule = xssRule(50L, 1L);
        SysXssBlacklistRule targetRule = xssRule(51L, 9L);
        when(permissionMapper.selectList(any()))
                .thenReturn(List.of(templatePermission), List.of(targetPermission));
        when(roleMapper.selectList(any())).thenReturn(List.of(templateRole), List.of(targetRole));
        when(rolePermissionMapper.selectPermissionIdsByRoleId(20L)).thenReturn(List.of(10L));
        when(orgMapper.selectOne(any())).thenReturn(root);
        when(userMapper.selectOne(any())).thenReturn(admin);
        when(xssConfigMapper.selectCount(any())).thenReturn(1L);
        when(xssRuleMapper.selectList(any())).thenReturn(List.of(targetRule), List.of(templateRule));

        AuthTenantTemplateProvisioner service = new AuthTenantTemplateProvisioner(
                moduleLoader, seedLoader, permissionMapper, roleMapper, rolePermissionMapper,
                orgMapper, userMapper, userRoleMapper, userUnitMapper, xssConfigMapper, xssRuleMapper);
        service.provisionLocal(9L, "新名称", "$2a$10$new-hash");

        assertThat(admin.getPassword()).isEqualTo("existing-hash");
        assertThat(targetPermission.getPermissionName()).isEqualTo("租户自定义系统菜单");
        assertThat(targetRole.getRoleName()).isEqualTo("租户自定义管理员");
        verify(userMapper, never()).insert(any(SysUser.class));
        verify(userMapper, never()).updateById(any(SysUser.class));
        verify(permissionMapper, never()).insert(any(SysPermission.class));
        verify(roleMapper, never()).insert(any(SysRole.class));
        verify(xssRuleMapper, never()).insert(any(SysXssBlacklistRule.class));
    }

    /**
     * 创建最小模块目录加载器。
     */
    private static ModuleCatalogLoader moduleLoader() {
        ModuleCatalogLoader loader = mock(ModuleCatalogLoader.class);
        ModuleDefinition auth = new ModuleDefinition(
                "auth", "foundation", List.of(), TenantProvisioningMode.LOCAL,
                List.of("system"), List.of("auth-role-catalog"));
        when(loader.catalog()).thenReturn(new ModuleCatalog("1.0.0", List.of(auth)));
        return loader;
    }

    /**
     * 创建最小 seed 目录加载器。
     */
    private static ProvisioningSeedCatalogLoader seedLoader() {
        ProvisioningSeedCatalogLoader loader = mock(ProvisioningSeedCatalogLoader.class);
        SeedDefinition seed = new SeedDefinition(
                "auth-role-catalog", "auth", List.of("SUPER_ADMIN"));
        when(loader.catalog()).thenReturn(new ProvisioningSeedCatalog(Map.of(seed.id(), seed)));
        return loader;
    }

    /**
     * 创建权限测试数据。
     */
    private static SysPermission permission(Long id, Long tenantId, String code, String path) {
        SysPermission permission = new SysPermission();
        permission.setId(id);
        permission.setTenantId(tenantId);
        permission.setParentId(0L);
        permission.setPermissionCode(code);
        permission.setPermissionName("系统管理");
        permission.setType("menu");
        permission.setPath(path);
        permission.setDepth(1);
        permission.setSort(1);
        permission.setStatus(1);
        return permission;
    }

    /**
     * 创建角色测试数据。
     */
    private static SysRole role(Long id, Long tenantId, String code) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setTenantId(tenantId);
        role.setRoleCode(code);
        role.setRoleName("超级管理员");
        role.setDataScope("ALL");
        role.setSort(0);
        role.setStatus(1);
        return role;
    }

    /**
     * 创建 XSS 规则测试数据。
     */
    private static SysXssBlacklistRule xssRule(Long id, Long tenantId) {
        SysXssBlacklistRule rule = new SysXssBlacklistRule();
        rule.setId(id);
        rule.setTenantId(tenantId);
        rule.setRuleName("脚本标签");
        rule.setRuleType("HTML_TAG");
        rule.setPattern("script");
        rule.setEnabled(1);
        rule.setSortOrder(1);
        return rule;
    }

    /**
     * 为权限插入 Mock 分配 ID。
     */
    private static void assignIdOnInsert(SysPermissionMapper mapper, Long id) {
        doAnswer(invocation -> {
            invocation.<SysPermission>getArgument(0).setId(id);
            return 1;
        }).when(mapper).insert(any(SysPermission.class));
    }

    /**
     * 为角色插入 Mock 分配 ID。
     */
    private static void assignIdOnInsert(SysRoleMapper mapper, Long id) {
        doAnswer(invocation -> {
            invocation.<SysRole>getArgument(0).setId(id);
            return 1;
        }).when(mapper).insert(any(SysRole.class));
    }
}
