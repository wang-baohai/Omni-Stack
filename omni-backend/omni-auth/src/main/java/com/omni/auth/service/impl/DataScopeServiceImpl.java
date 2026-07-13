package com.omni.auth.service.impl;

import com.omni.auth.entity.SysOrgUnit;
import com.omni.auth.entity.SysRole;
import com.omni.auth.entity.SysUser;
import com.omni.auth.mapper.SysOrgUnitMapper;
import com.omni.auth.mapper.SysRoleDeptMapper;
import com.omni.auth.mapper.SysRoleMapper;
import com.omni.auth.mapper.SysUserMapper;
import com.omni.auth.security.DataScopeContext;
import com.omni.auth.service.DataScopeService;
import com.omni.common.core.internal.InternalDataScopeDTO;
import com.omni.common.core.result.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 数据权限范围解析服务实现。
 * <p>
 * 先验证启用用户确实属于目标租户，再按调用模式查询全部角色或真正授予指定权限的角色，
 * 最后按项目既定优先级合并数据范围并展开组织单元集合。
 * </p>
 *
 * @author Omni-Stack Team
 */
@Service
@RequiredArgsConstructor
public class DataScopeServiceImpl implements DataScopeService {

    /** 用户数据访问组件 */
    private final SysUserMapper sysUserMapper;

    /** 角色数据访问组件 */
    private final SysRoleMapper sysRoleMapper;

    /** 组织单元数据访问组件 */
    private final SysOrgUnitMapper sysOrgUnitMapper;

    /** 角色自定义组织范围数据访问组件 */
    private final SysRoleDeptMapper sysRoleDeptMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public InternalDataScopeDTO resolveDataScope(Long userId, Long tenantId) {
        SysUser user = requireEnabledTenantUser(userId, tenantId);
        List<SysRole> roles = sysRoleMapper.selectRolesByUserIdAndTenantId(userId, tenantId);
        if (roles == null) {
            roles = List.of();
        }
        return buildDataScope(user, tenantId, null, roles);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public InternalDataScopeDTO resolveDataScope(Long userId, Long tenantId, String permissionCode) {
        SysUser user = requireEnabledTenantUser(userId, tenantId);
        String normalizedPermissionCode = normalizePermissionCode(permissionCode);
        List<SysRole> roles = sysRoleMapper.selectRolesGrantingPermission(
                userId, tenantId, normalizedPermissionCode);
        if (roles == null || roles.isEmpty()) {
            throw new BusinessException(403, "用户未被授予权限: " + normalizedPermissionCode);
        }
        return buildDataScope(user, tenantId, normalizedPermissionCode, roles);
    }

    /**
     * 校验用户处于启用状态且属于目标租户。
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @return 已验证的用户实体
     */
    private SysUser requireEnabledTenantUser(Long userId, Long tenantId) {
        validatePositiveId(userId, "用户 ID");
        validatePositiveId(tenantId, "租户 ID");
        SysUser user = sysUserMapper.selectEnabledByIdAndTenantId(userId, tenantId);
        if (user == null) {
            throw new BusinessException(403, "用户不存在、已禁用或不属于指定租户");
        }
        return user;
    }

    /**
     * 规范化并校验完整权限码。
     *
     * @param permissionCode 原始权限码
     * @return 去除首尾空白的权限码
     */
    private String normalizePermissionCode(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            throw new BusinessException(400, "permissionCode 不能为空");
        }
        String normalized = permissionCode.trim();
        if (normalized.length() > 200) {
            throw new BusinessException(400, "permissionCode 长度不能超过 200 个字符");
        }
        return normalized;
    }

    /**
     * 构建解析结果。
     *
     * @param user           已验证用户
     * @param tenantId       租户 ID
     * @param permissionCode 权限码，普通请求级解析时为空
     * @param roles          本次参与合并的角色
     * @return 数据权限范围 DTO
     */
    private InternalDataScopeDTO buildDataScope(SysUser user,
                                                Long tenantId,
                                                String permissionCode,
                                                List<SysRole> roles) {
        String effectiveScope = resolveWidestScope(roles);
        SysOrgUnit primaryUnit = resolveEnabledPrimaryUnit(user.getPrimaryUnitId(), tenantId);
        Long primaryUnitId = primaryUnit == null ? null : primaryUnit.getId();
        Set<Long> accessibleUnitIds = resolveAccessibleUnitIds(
                roles, effectiveScope, primaryUnit, tenantId);

        InternalDataScopeDTO result = new InternalDataScopeDTO();
        result.setUserId(user.getId());
        result.setTenantId(tenantId);
        result.setPermissionCode(permissionCode);
        result.setPrimaryUnitId(primaryUnitId);
        result.setEffectiveScope(effectiveScope);
        result.setAccessibleUnitIds(accessibleUnitIds);
        result.setSecurityVersion(calculateSecurityVersion(result, roles));
        return result;
    }

    /**
     * 按最宽松优先规则合并角色数据范围。
     *
     * @param roles 参与合并的角色
     * @return 有效数据范围
     */
    private String resolveWidestScope(List<SysRole> roles) {
        String widestScope = "SELF";
        int bestPriority = DataScopeContext.PRIORITY_SELF;
        for (SysRole role : roles) {
            int priority = DataScopeContext.priorityOf(role.getDataScope());
            if (priority < bestPriority) {
                bestPriority = priority;
                widestScope = role.getDataScope();
            }
        }
        return widestScope;
    }

    /**
     * 查询并验证用户主组织单元属于目标租户且处于启用状态。
     *
     * @param primaryUnitId 主组织单元 ID
     * @param tenantId      租户 ID
     * @return 有效主组织单元，无有效主组织时返回 null
     */
    private SysOrgUnit resolveEnabledPrimaryUnit(Long primaryUnitId, Long tenantId) {
        if (primaryUnitId == null) {
            return null;
        }
        SysOrgUnit unit = sysOrgUnitMapper.selectByIdAndTenantId(primaryUnitId, tenantId);
        if (unit == null || !Integer.valueOf(1).equals(unit.getStatus())) {
            return null;
        }
        return unit;
    }

    /**
     * 根据有效数据范围计算可访问组织单元集合。
     *
     * @param roles          参与合并的角色
     * @param effectiveScope 有效数据范围
     * @param primaryUnit    已验证的主组织单元
     * @param tenantId       租户 ID
     * @return 不可变的可访问组织单元 ID 集合
     */
    private Set<Long> resolveAccessibleUnitIds(List<SysRole> roles,
                                               String effectiveScope,
                                               SysOrgUnit primaryUnit,
                                               Long tenantId) {
        return switch (effectiveScope) {
            case "DEPT" -> primaryUnit == null ? Set.of() : Set.of(primaryUnit.getId());
            case "DEPT_AND_BELOW" -> resolveUnitTree(primaryUnit, tenantId);
            case "CUSTOM" -> resolveCustomUnitTrees(roles, tenantId);
            case "ALL", "TENANT", "SELF" -> Set.of();
            default -> Set.of();
        };
    }

    /**
     * 展开一个组织单元的当前节点及启用后代节点。
     *
     * @param unit     组织单元
     * @param tenantId 租户 ID
     * @return 组织单元 ID 集合
     */
    private Set<Long> resolveUnitTree(SysOrgUnit unit, Long tenantId) {
        if (unit == null || unit.getPath() == null || unit.getPath().isBlank()) {
            return Set.of();
        }
        List<Long> unitIds = sysOrgUnitMapper.selectDescendantIdsByTenantIdAndPath(
                tenantId, unit.getPath());
        if (unitIds == null || unitIds.isEmpty()) {
            return Set.of(unit.getId());
        }
        Set<Long> result = new HashSet<>(unitIds);
        result.add(unit.getId());
        return Set.copyOf(result);
    }

    /**
     * 展开所有 CUSTOM 角色配置的组织单元树。
     *
     * @param roles    参与合并的角色
     * @param tenantId 租户 ID
     * @return 自定义组织单元 ID 集合
     */
    private Set<Long> resolveCustomUnitTrees(List<SysRole> roles, Long tenantId) {
        Set<Long> result = new HashSet<>();
        for (SysRole role : roles) {
            if (!"CUSTOM".equals(role.getDataScope())) {
                continue;
            }
            List<Long> deptIds = sysRoleDeptMapper.selectDeptIdsByRoleId(role.getId());
            if (deptIds == null) {
                continue;
            }
            for (Long deptId : deptIds) {
                SysOrgUnit unit = sysOrgUnitMapper.selectByIdAndTenantId(deptId, tenantId);
                result.addAll(resolveUnitTree(unit, tenantId));
            }
        }
        return result.isEmpty() ? Set.of() : Set.copyOf(result);
    }

    /**
     * 计算授权结果的稳定指纹。
     * <p>用户组织、参与角色、角色范围、权限码或可访问组织集合变化时，指纹随之变化。</p>
     *
     * @param dataScope 数据范围结果
     * @param roles     参与合并的角色
     * @return 非负的稳定版本指纹
     */
    private Long calculateSecurityVersion(InternalDataScopeDTO dataScope, List<SysRole> roles) {
        List<String> roleParts = new ArrayList<>(roles.size());
        for (SysRole role : roles) {
            roleParts.add(role.getId() + ":" + role.getDataScope());
        }
        roleParts.sort(String::compareTo);

        List<Long> unitIds = new ArrayList<>(dataScope.getAccessibleUnitIds());
        unitIds.sort(Long::compareTo);
        String source = dataScope.getUserId() + "|" + dataScope.getTenantId() + "|"
                + dataScope.getPermissionCode() + "|" + dataScope.getPrimaryUnitId() + "|"
                + dataScope.getEffectiveScope() + "|" + roleParts + "|" + unitIds;
        long fingerprint = UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).getMostSignificantBits();
        return fingerprint & Long.MAX_VALUE;
    }

    /**
     * 校验 ID 为正整数。
     *
     * @param id        待校验 ID
     * @param fieldName 字段名称
     */
    private void validatePositiveId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new BusinessException(400, fieldName + "必须为正整数");
        }
    }
}
