package com.omni.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.omni.auth.dto.CreateRoleRequest;
import com.omni.auth.dto.UpdateRoleRequest;
import com.omni.auth.entity.SysRole;
import com.omni.auth.mapper.SysRoleDeptMapper;
import com.omni.auth.mapper.SysRoleMapper;
import com.omni.auth.mapper.SysRolePermissionMapper;
import com.omni.auth.service.RoleService;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色服务实现类。
 * <p>提供角色 CRUD、权限分配和 CUSTOM 数据范围部门分配操作。</p>
 *
 * @author Omni-Stack Team
 * @see com.omni.auth.service.RoleService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements RoleService {

    private final SysRoleMapper sysRoleMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final SysRoleDeptMapper sysRoleDeptMapper;

    /**
     * {@inheritDoc}
     *
     * <p>使用 MyBatis-Plus 分页插件，按租户 ID 过滤并按 sort 排序。</p>
     */
    @Override
    public PageResult<SysRole> listRoles(Long tenantId, int page, int size) {
        Page<SysRole> mpPage = sysRoleMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getTenantId, tenantId)
                        .orderByAsc(SysRole::getSort));
        return new PageResult<>(mpPage.getRecords(), mpPage.getTotal(), mpPage.getSize(), mpPage.getCurrent());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SysRole getById(Long id) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(404, "角色不存在");
        }
        return role;
    }

    /**
     * {@inheritDoc}
     *
     * <p>创建角色后可选择性地分配初始权限。</p>
     */
    @Override
    @Transactional
    public SysRole createRole(Long tenantId, CreateRoleRequest request) {
        SysRole role = new SysRole();
        role.setTenantId(tenantId);
        role.setRoleCode(request.getRoleCode());
        role.setRoleName(request.getRoleName());
        role.setDataScope(request.getDataScope());
        role.setSort(request.getSort() != null ? request.getSort() : 0);
        role.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        sysRoleMapper.insert(role);

        // 创建时可选分配初始权限
        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            sysRolePermissionMapper.batchInsert(role.getId(), request.getPermissionIds());
        }
        log.info("已创建角色: {} ({})", role.getRoleName(), role.getRoleCode());
        return role;
    }

    /**
     * {@inheritDoc}
     *
     * <p>仅更新非 null 字段。permissionIds 非 null 时全量替换权限关联。</p>
     */
    @Override
    @Transactional
    public SysRole updateRole(Long id, UpdateRoleRequest request) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(404, "角色不存在");
        }
        if (request.getRoleName() != null) {
            role.setRoleName(request.getRoleName());
        }
        if (request.getDataScope() != null) {
            role.setDataScope(request.getDataScope());
        }
        if (request.getSort() != null) {
            role.setSort(request.getSort());
        }
        if (request.getStatus() != null) {
            role.setStatus(request.getStatus());
        }
        sysRoleMapper.updateById(role);

        // 权限 ID 列表非 null 时全量替换
        if (request.getPermissionIds() != null) {
            sysRolePermissionMapper.deleteByRoleId(id);
            if (!request.getPermissionIds().isEmpty()) {
                sysRolePermissionMapper.batchInsert(id, request.getPermissionIds());
            }
        }
        log.info("已更新角色: {}", role.getRoleName());
        return role;
    }

    /**
     * {@inheritDoc}
     *
     * <p>删除角色时同步清理权限关联和部门关联。</p>
     */
    @Override
    @Transactional
    public void deleteRole(Long id) {
        sysRolePermissionMapper.deleteByRoleId(id);
        sysRoleDeptMapper.deleteByRoleId(id);
        sysRoleMapper.deleteById(id);
        log.info("已删除角色 ID: {}", id);
    }

    /**
     * {@inheritDoc}
     *
     * <p>先删除角色的全部部门关联，再批量插入（全量替换策略）。</p>
     */
    @Override
    @Transactional
    public void assignDepts(Long roleId, List<Long> deptIds) {
        sysRoleDeptMapper.deleteByRoleId(roleId);
        if (deptIds != null && !deptIds.isEmpty()) {
            sysRoleDeptMapper.batchInsert(roleId, deptIds);
        }
        log.info("已为角色 {} 分配 {} 个数据范围部门", roleId, deptIds == null ? 0 : deptIds.size());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Long> getRoleDeptIds(Long roleId) {
        return sysRoleDeptMapper.selectDeptIdsByRoleId(roleId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>查询租户下所有启用状态的角色，用于下拉选项。</p>
     */
    @Override
    public List<SysRole> listAllRoles(Long tenantId) {
        return sysRoleMapper.selectList(
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getTenantId, tenantId)
                        .eq(SysRole::getStatus, 1)
                        .orderByAsc(SysRole::getSort));
    }
}
