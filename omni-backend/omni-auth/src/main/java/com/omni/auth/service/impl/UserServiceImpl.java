package com.omni.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.omni.auth.dto.CreateUserRequest;
import com.omni.auth.dto.RegisterRequest;
import com.omni.auth.entity.SysRole;
import com.omni.auth.entity.SysUser;
import com.omni.auth.mapper.SysRoleMapper;
import com.omni.auth.mapper.SysUserMapper;
import com.omni.auth.mapper.SysUserRoleMapper;
import com.omni.auth.service.CaptchaService;
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
    /** 角色 Mapper，查询默认角色用于新用户角色分配 */
    private final SysRoleMapper sysRoleMapper;
    /** 密码编码器 */
    private final PasswordEncoder passwordEncoder;
    /** 验证码服务，用于注册时校验验证码 */
    private final CaptchaService captchaService;

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
     * <p>使用 BCrypt 验证明文密码与存储哈希是否匹配。</p>
     */
    @Override
    public boolean verifyPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
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

    /**
     * {@inheritDoc}
     *
     * <p>创建流程：校验用户名唯一性 -> BCrypt 编码密码 -> 插入用户 -> 分配默认 USER 角色。</p>
     */
    @Override
    @Transactional
    public SysUser createUser(CreateUserRequest request) {
        // 校验用户名唯一性
        SysUser existing = findByUsername(request.getUsername(), request.getTenantId());
        if (existing != null) {
            throw new BusinessException(400, "用户名已存在");
        }

        // 构建用户实体
        SysUser user = new SysUser();
        user.setTenantId(request.getTenantId());
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setGender(request.getGender() != null ? request.getGender() : 0);
        user.setStatus(1);

        sysUserMapper.insert(user);

        // 分配默认 USER 角色
        assignDefaultRole(user.getId(), request.getTenantId(), user.getUsername());

        log.info("管理员创建用户成功: username={}, tenantId={}", request.getUsername(), request.getTenantId());
        return user;
    }

    /**
     * {@inheritDoc}
     *
     * <p>注册流程：验证码校验 -> 校验用户名唯一性 -> BCrypt 编码密码 -> 插入用户 -> 分配默认 USER 角色。</p>
     */
    @Override
    @Transactional
    public void registerUser(RegisterRequest request) {
        // 验证码校验
        captchaService.validate(request.getCaptchaKey(), request.getCaptchaCode());

        // 校验用户名唯一性
        Long tenantId = request.getTenantId();
        SysUser existing = findByUsername(request.getUsername(), tenantId);
        if (existing != null) {
            throw new BusinessException(400, "用户名已存在");
        }

        // 构建用户实体
        SysUser user = new SysUser();
        user.setTenantId(tenantId);
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setGender(0);
        user.setStatus(1);

        sysUserMapper.insert(user);

        // 分配默认 USER 角色
        assignDefaultRole(user.getId(), tenantId, user.getUsername());

        log.info("用户自助注册成功: username={}", request.getUsername());
    }

    /**
     * 为新用户分配默认 USER 角色。
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @param username 用户名（仅用于日志）
     */
    private void assignDefaultRole(Long userId, Long tenantId, String username) {
        SysRole defaultRole = sysRoleMapper.selectByTenantIdAndRoleCode(tenantId, "USER");
        if (defaultRole != null) {
            sysUserRoleMapper.insert(userId, defaultRole.getId());
        } else {
            log.warn("未找到租户 {} 的默认 USER 角色，跳过角色分配: username={}", tenantId, username);
        }
    }
}
