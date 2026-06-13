package com.omni.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.omni.auth.entity.SysUser;
import com.omni.auth.mapper.SysUserMapper;
import com.omni.auth.mapper.SysUserRoleMapper;
import com.omni.auth.service.UserService;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户服务实现类。
 * <p>
 * 基于 MyBatis-Plus 的 ServiceImpl，提供用户的认证、查询和分页等操作。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements UserService {

    /** 用户 Mapper */
    private final SysUserMapper sysUserMapper;
    /** 用户角色关联 Mapper */
    private final SysUserRoleMapper sysUserRoleMapper;
    /** 密码编码器 */
    private final PasswordEncoder passwordEncoder;

    /**
     * {@inheritDoc}
     *
     * <p>认证流程：先根据用户名和租户查询用户，再验证 BCrypt 密码。</p>
     */
    @Override
    public SysUser authenticate(String username, String password, Long tenantId) {
        SysUser user = findByUsername(username, tenantId);
        if (user == null) {
            return null;
        }
        // 使用 BCrypt 验证密码
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return null;
        }
        return user;
    }

    /**
     * {@inheritDoc}
     *
     * <p>通过自定义 SQL 查询（带租户隔离和活跃状态过滤）。</p>
     */
    @Override
    public SysUser findByUsername(String username, Long tenantId) {
        return sysUserMapper.selectByUsernameAndTenantId(username, tenantId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>通过 {@code sys_user_role} 关联表查询。</p>
     */
    @Override
    public List<String> getUserRoles(Long userId) {
        return sysUserMapper.selectRoleCodesByUserId(userId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>通过三表关联查询（user_role -> role_permission -> permission）。</p>
     */
    @Override
    public List<String> getUserPermissions(Long userId) {
        return sysUserMapper.selectPermissionsByUserId(userId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>使用 MyBatis-Plus 分页插件，按租户 ID 过滤。</p>
     */
    @Override
    public PageResult<SysUser> listUsers(Long tenantId, int page, int size) {
        Page<SysUser> mpPage = sysUserMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getTenantId, tenantId));
        return new PageResult<>(mpPage.getRecords(), mpPage.getTotal(), mpPage.getSize(), mpPage.getCurrent());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SysUser getById(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
    }

    /**
     * {@inheritDoc}
     *
     * <p>先删除用户的全部角色关联，再逐条插入（全量替换策略）。</p>
     */
    @Override
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        sysUserRoleMapper.deleteByUserId(userId);
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                sysUserRoleMapper.insert(userId, roleId);
            }
        }
        log.info("已为用户 {} 分配 {} 个角色", userId, roleIds == null ? 0 : roleIds.size());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Long> getUserRoleIds(Long userId) {
        return sysUserRoleMapper.selectRoleIdsByUserId(userId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void toggleStatus(Long userId, Integer status) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        user.setStatus(status);
        sysUserMapper.updateById(user);
        log.info("已切换用户 {} 状态为 {}", user.getUsername(), status == 1 ? "启用" : "禁用");
    }
}
