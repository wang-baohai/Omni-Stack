package com.omni.auth.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.auth.catalog.ModuleCatalogLoader;
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
import com.omni.auth.service.TenantLocalProvisioner;
import com.omni.common.core.result.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于默认租户自然键模板的 Auth 本地租户初始化实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthTenantTemplateProvisioner implements TenantLocalProvisioner {

    /** 默认模板租户 ID。 */
    private static final Long TEMPLATE_TENANT_ID = 1L;
    /** 系统初始化操作者。 */
    private static final String SYSTEM_OPERATOR = "system";

    /** 模块目录加载器。 */
    private final ModuleCatalogLoader moduleCatalogLoader;
    /** provisioning seed 目录加载器。 */
    private final ProvisioningSeedCatalogLoader seedCatalogLoader;
    /** 权限 Mapper。 */
    private final SysPermissionMapper permissionMapper;
    /** 角色 Mapper。 */
    private final SysRoleMapper roleMapper;
    /** 角色权限 Mapper。 */
    private final SysRolePermissionMapper rolePermissionMapper;
    /** 组织 Mapper。 */
    private final SysOrgUnitMapper orgUnitMapper;
    /** 用户 Mapper。 */
    private final SysUserMapper userMapper;
    /** 用户角色 Mapper。 */
    private final SysUserRoleMapper userRoleMapper;
    /** 用户组织 Mapper。 */
    private final SysUserUnitMapper userUnitMapper;
    /** XSS 配置 Mapper。 */
    private final SysXssConfigMapper xssConfigMapper;
    /** XSS 规则 Mapper。 */
    private final SysXssBlacklistRuleMapper xssRuleMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void provisionLocal(Long tenantId, String tenantName, String encodedAdminPassword) {
        validateCommand(tenantId, tenantName, encodedAdminPassword);
        Map<Long, SysPermission> permissions = provisionPermissions(tenantId);
        Map<Long, SysRole> roles = provisionRoles(tenantId);
        provisionRolePermissions(roles, permissions);
        SysOrgUnit rootUnit = provisionRootUnit(tenantId, tenantName);
        SysUser admin = provisionAdmin(tenantId, tenantName, encodedAdminPassword, rootUnit.getId());
        associateAdmin(admin.getId(), rootUnit.getId(), roles);
        provisionXss(tenantId);
        log.info("Auth 租户本地初始化完成: tenantId={}, permissionCount={}, roleCount={}",
                tenantId, permissions.size(), roles.size());
    }

    /**
     * 克隆所选模块权限树，新建节点之后不覆盖租户自定义字段。
     */
    private Map<Long, SysPermission> provisionPermissions(Long tenantId) {
        Set<String> roots = moduleCatalogLoader.catalog().modules().stream()
                .flatMap(module -> module.permissionRoots().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<SysPermission> templates = permissionMapper.selectList(
                new LambdaQueryWrapper<SysPermission>()
                        .eq(SysPermission::getTenantId, TEMPLATE_TENANT_ID)
                        .orderByAsc(SysPermission::getDepth, SysPermission::getId));
        List<SysPermission> selectedTemplates = templates.stream()
                .filter(permission -> belongsToRoots(permission.getPermissionCode(), roots))
                .toList();
        Map<String, SysPermission> existingByCode = permissionMapper.selectList(
                        new LambdaQueryWrapper<SysPermission>().eq(SysPermission::getTenantId, tenantId))
                .stream()
                .collect(Collectors.toMap(
                        SysPermission::getPermissionCode,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new));

        Map<Long, SysPermission> mapped = new LinkedHashMap<>(selectedTemplates.size());
        for (SysPermission template : selectedTemplates) {
            SysPermission target = existingByCode.get(template.getPermissionCode());
            if (target == null) {
                target = clonePermission(tenantId, template, mapped);
                permissionMapper.insert(target);
                target.setPath(buildPermissionPath(target, mapped));
                target.setUpdateBy(SYSTEM_OPERATOR);
                permissionMapper.updateById(target);
                existingByCode.put(target.getPermissionCode(), target);
            }
            mapped.put(template.getId(), target);
        }
        if (mapped.isEmpty()) {
            throw new BusinessException("默认租户没有可用于初始化的权限模板");
        }
        return mapped;
    }

    /**
     * 从模板克隆 seed 清单声明的默认角色。
     */
    private Map<Long, SysRole> provisionRoles(Long tenantId) {
        List<String> roleCodes = seedCatalogLoader.catalog().roleCodes();
        List<SysRole> templates = roleMapper.selectList(
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getTenantId, TEMPLATE_TENANT_ID)
                        .in(SysRole::getRoleCode, roleCodes));
        Map<String, SysRole> templatesByCode = templates.stream()
                .collect(Collectors.toMap(SysRole::getRoleCode, Function.identity()));
        Map<String, SysRole> existingByCode = roleMapper.selectList(
                        new LambdaQueryWrapper<SysRole>().eq(SysRole::getTenantId, tenantId))
                .stream()
                .collect(Collectors.toMap(
                        SysRole::getRoleCode,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new));

        Map<Long, SysRole> mapped = new LinkedHashMap<>(roleCodes.size());
        for (String roleCode : roleCodes) {
            SysRole template = templatesByCode.get(roleCode);
            if (template == null) {
                throw new BusinessException("默认租户缺少角色模板: " + roleCode);
            }
            SysRole target = existingByCode.get(roleCode);
            if (target == null) {
                target = cloneRole(tenantId, template);
                roleMapper.insert(target);
                existingByCode.put(roleCode, target);
            }
            mapped.put(template.getId(), target);
        }
        return mapped;
    }

    /**
     * 按模板自然键映射角色权限关联。
     */
    private void provisionRolePermissions(Map<Long, SysRole> roles, Map<Long, SysPermission> permissions) {
        for (Map.Entry<Long, SysRole> entry : roles.entrySet()) {
            List<Long> targetPermissionIds = rolePermissionMapper.selectPermissionIdsByRoleId(entry.getKey()).stream()
                    .map(permissions::get)
                    .filter(java.util.Objects::nonNull)
                    .map(SysPermission::getId)
                    .distinct()
                    .toList();
            if (!targetPermissionIds.isEmpty()) {
                rolePermissionMapper.batchInsertIgnore(entry.getValue().getId(), targetPermissionIds);
            }
        }
    }

    /**
     * 幂等创建根组织。
     */
    private SysOrgUnit provisionRootUnit(Long tenantId, String tenantName) {
        SysOrgUnit existing = orgUnitMapper.selectOne(
                new LambdaQueryWrapper<SysOrgUnit>()
                        .eq(SysOrgUnit::getTenantId, tenantId)
                        .eq(SysOrgUnit::getParentId, 0L)
                        .orderByAsc(SysOrgUnit::getId)
                        .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        SysOrgUnit root = new SysOrgUnit();
        root.setTenantId(tenantId);
        root.setParentId(0L);
        root.setName(tenantName);
        root.setType("ORG");
        root.setDepth(1);
        root.setSort(0);
        root.setStatus(1);
        root.setCreateBy(SYSTEM_OPERATOR);
        orgUnitMapper.insert(root);
        root.setPath("/" + root.getId() + "/");
        root.setUpdateBy(SYSTEM_OPERATOR);
        orgUnitMapper.updateById(root);
        return root;
    }

    /**
     * 幂等创建管理员，不覆盖已存在管理员的密码。
     */
    private SysUser provisionAdmin(
            Long tenantId, String tenantName, String encodedAdminPassword, Long rootUnitId) {
        SysUser admin = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getTenantId, tenantId)
                        .eq(SysUser::getUsername, "admin")
                        .orderByAsc(SysUser::getId)
                        .last("LIMIT 1"));
        if (admin == null) {
            admin = new SysUser();
            admin.setTenantId(tenantId);
            admin.setUsername("admin");
            admin.setPassword(encodedAdminPassword);
            admin.setNickname(tenantName + " Admin");
            admin.setGender(0);
            admin.setPrimaryUnitId(rootUnitId);
            admin.setStatus(1);
            admin.setCreateBy(SYSTEM_OPERATOR);
            userMapper.insert(admin);
            return admin;
        }
        if (admin.getPrimaryUnitId() == null) {
            admin.setPrimaryUnitId(rootUnitId);
            admin.setUpdateBy(SYSTEM_OPERATOR);
            userMapper.updateById(admin);
        }
        return admin;
    }

    /**
     * 关联管理员、超级管理员角色和根组织。
     */
    private void associateAdmin(Long adminId, Long rootUnitId, Map<Long, SysRole> roles) {
        SysRole superAdmin = roles.values().stream()
                .filter(role -> "SUPER_ADMIN".equals(role.getRoleCode()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("默认租户缺少 SUPER_ADMIN 角色模板"));
        userRoleMapper.insertIgnore(adminId, superAdmin.getId());
        userUnitMapper.insertIgnore(adminId, rootUnitId, 1);
    }

    /**
     * 幂等初始化 XSS 开关和规则模板。
     */
    private void provisionXss(Long tenantId) {
        Long configCount = xssConfigMapper.selectCount(
                new LambdaQueryWrapper<SysXssConfig>().eq(SysXssConfig::getTenantId, tenantId));
        if (configCount == 0) {
            SysXssConfig config = new SysXssConfig();
            config.setTenantId(tenantId);
            config.setEnabled(0);
            config.setCreateBy(SYSTEM_OPERATOR);
            xssConfigMapper.insert(config);
        }
        List<SysXssBlacklistRule> existing = new ArrayList<>(xssRuleMapper.selectList(
                new LambdaQueryWrapper<SysXssBlacklistRule>().eq(SysXssBlacklistRule::getTenantId, tenantId)));
        List<SysXssBlacklistRule> templates = xssRuleMapper.selectList(
                new LambdaQueryWrapper<SysXssBlacklistRule>()
                        .eq(SysXssBlacklistRule::getTenantId, TEMPLATE_TENANT_ID)
                        .orderByAsc(SysXssBlacklistRule::getSortOrder, SysXssBlacklistRule::getId));
        for (SysXssBlacklistRule template : templates) {
            boolean alreadyExists = existing.stream().anyMatch(rule -> sameRule(rule, template));
            if (!alreadyExists) {
                SysXssBlacklistRule target = cloneXssRule(tenantId, template);
                xssRuleMapper.insert(target);
                existing.add(target);
            }
        }
    }

    /**
     * 创建权限模板副本。
     */
    private static SysPermission clonePermission(
            Long tenantId, SysPermission template, Map<Long, SysPermission> mapped) {
        SysPermission target = new SysPermission();
        target.setTenantId(tenantId);
        target.setParentId(resolveParentId(template, mapped));
        target.setPermissionCode(template.getPermissionCode());
        target.setPermissionName(template.getPermissionName());
        target.setType(template.getType());
        target.setPath("");
        target.setDepth(template.getDepth());
        target.setSort(template.getSort());
        target.setStatus(1);
        target.setCreateBy(SYSTEM_OPERATOR);
        return target;
    }

    /**
     * 创建角色模板副本。
     */
    private static SysRole cloneRole(Long tenantId, SysRole template) {
        SysRole target = new SysRole();
        target.setTenantId(tenantId);
        target.setRoleCode(template.getRoleCode());
        target.setRoleName(template.getRoleName());
        target.setDataScope(template.getDataScope());
        target.setSort(template.getSort());
        target.setStatus(1);
        target.setCreateBy(SYSTEM_OPERATOR);
        return target;
    }

    /**
     * 创建 XSS 规则模板副本。
     */
    private static SysXssBlacklistRule cloneXssRule(Long tenantId, SysXssBlacklistRule template) {
        SysXssBlacklistRule target = new SysXssBlacklistRule();
        target.setTenantId(tenantId);
        target.setRuleName(template.getRuleName());
        target.setRuleType(template.getRuleType());
        target.setPattern(template.getPattern());
        target.setEnabled(template.getEnabled());
        target.setDescription(template.getDescription());
        target.setSortOrder(template.getSortOrder());
        target.setCreateBy(SYSTEM_OPERATOR);
        return target;
    }

    /**
     * 解析目标父权限 ID。
     */
    private static Long resolveParentId(SysPermission template, Map<Long, SysPermission> mapped) {
        if (template.getParentId() == null || template.getParentId() == 0L) {
            return 0L;
        }
        SysPermission parent = mapped.get(template.getParentId());
        if (parent == null) {
            throw new BusinessException("权限模板父节点未被所选模块包含: " + template.getPermissionCode());
        }
        return parent.getId();
    }

    /**
     * 构建目标权限物化路径。
     */
    private static String buildPermissionPath(SysPermission target, Map<Long, SysPermission> mapped) {
        if (target.getParentId() == null || target.getParentId() == 0L) {
            return "/" + target.getId() + "/";
        }
        String parentPath = mapped.values().stream()
                .filter(permission -> target.getParentId().equals(permission.getId()))
                .map(SysPermission::getPath)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElseThrow(() -> new BusinessException("无法构建权限物化路径: " + target.getPermissionCode()));
        return parentPath + target.getId() + "/";
    }

    /**
     * 判断权限是否属于所选根。
     */
    private static boolean belongsToRoots(String permissionCode, Set<String> roots) {
        if (!StringUtils.hasText(permissionCode)) {
            return false;
        }
        return roots.stream().anyMatch(root -> permissionCode.equals(root) || permissionCode.startsWith(root + ":"));
    }

    /**
     * 判断两条 XSS 规则是否具有相同自然键。
     */
    private static boolean sameRule(SysXssBlacklistRule left, SysXssBlacklistRule right) {
        return java.util.Objects.equals(left.getRuleType(), right.getRuleType())
                && java.util.Objects.equals(left.getPattern(), right.getPattern());
    }

    /**
     * 校验本地初始化命令。
     */
    private static void validateCommand(Long tenantId, String tenantName, String encodedAdminPassword) {
        if (tenantId == null || tenantId <= 0) {
            throw new BusinessException("租户 ID 无效");
        }
        if (!StringUtils.hasText(tenantName)) {
            throw new BusinessException("租户名称不能为空");
        }
        if (!StringUtils.hasText(encodedAdminPassword)) {
            throw new BusinessException("管理员密码哈希不能为空");
        }
    }
}
