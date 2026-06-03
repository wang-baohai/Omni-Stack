package com.omni.auth.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.omni.auth.entity.SysUser;
import com.omni.auth.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 自定义 {@link UserDetailsService} 实现，从数据库加载用户信息供 Spring Security 认证使用。
 *
 * <h3>多租户登录机制</h3>
 * <p>该服务支持多租户认证，通过在用户名字符串中编码租户 ID 来实现。
 * 格式为 {@code "tenantId:username"}（如 {@code "1:admin"}）。解析逻辑如下：</p>
 * <ol>
 *   <li>如果用户名包含冒号（{@code :}），按第一个冒号分割：
 *       左侧解析为 {@code tenantId}（Long），右侧为实际用户名。</li>
 *   <li>如果不包含冒号，默认 {@code tenantId = 1}（单租户兼容模式）。</li>
 * </ol>
 *
 * <h3>用户查询</h3>
 * <p>从 {@code sys_user} 表中查询满足以下三个条件的用户：</p>
 * <ul>
 *   <li>{@code tenant_id} 匹配解析出的租户 ID</li>
 *   <li>{@code username} 匹配解析出的用户名</li>
 *   <li>{@code status = 1}（仅活跃用户可以认证）</li>
 * </ul>
 *
 * <h3>权限构建</h3>
 * <p>用户查询成功后，通过关联查询加载角色和权限，转换为 Spring Security 权限对象：</p>
 * <ul>
 *   <li>角色添加 {@code ROLE_} 前缀（如角色编码 {@code "ADMIN"} 变为权限 {@code "ROLE_ADMIN"}），
 *       支持 {@code hasRole("ADMIN")} 检查。</li>
 *   <li>权限编码原样添加（如 {@code "user:read"}），支持 {@code hasAuthority("user:read")} 检查。</li>
 * </ul>
 *
 * @see org.springframework.security.core.userdetails.UserDetailsService
 */
@Service
@RequiredArgsConstructor
public class OmniUserDetailsService implements UserDetailsService {

    /** 用户 Mapper */
    private final SysUserMapper sysUserMapper;

    /**
     * 根据（可选带租户前缀的）用户名加载用户。
     *
     * <p>此方法在认证过程中由 Spring Security 的认证提供者（如
     * {@code DaoAuthenticationProvider}）调用。</p>
     *
     * @param username 用户名，可选带租户 ID 前缀，格式为 {@code "tenantId:username"}。
     *                 无前缀时默认租户 ID 为 1。
     * @return 包含用户名、密码和权限列表的完整 {@link UserDetails} 对象
     * @throws UsernameNotFoundException 如果未找到匹配的活跃用户
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // --- 多租户解析："tenantId:username" 或纯 "username" ---
        // 示例："1:admin" -> tenantId=1, actualUsername="admin"
        //       "admin"   -> tenantId=1（默认值）, actualUsername="admin"
        Long tenantId = 1L;
        String actualUsername = username;
        if (username.contains(":")) {
            String[] parts = username.split(":", 2);
            tenantId = Long.valueOf(parts[0]);
            actualUsername = parts[1];
        }

        // 根据租户 + 用户名 + 活跃状态查询用户
        SysUser user = sysUserMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getTenantId, tenantId)
                .eq(SysUser::getUsername, actualUsername)
                .eq(SysUser::getStatus, 1));

        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + actualUsername);
        }

        // --- 从数据库加载角色和权限 ---
        List<String> roleCodes = sysUserMapper.selectRoleCodesByUserId(user.getId());
        List<String> permissions = sysUserMapper.selectPermissionsByUserId(user.getId());

        // 将角色编码转换为 ROLE_ 前缀的权限（如 "ADMIN" -> "ROLE_ADMIN"）
        List<SimpleGrantedAuthority> authorities = new ArrayList<>(roleCodes.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .collect(Collectors.toList()));

        // 添加权限编码作为普通权限（如 "user:read"）
        authorities.addAll(permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList()));

        // 构建 OmniUserDetails，携带 userId 和 tenantId 供 OAuth2 Token 签发使用
        return new OmniUserDetails(
                user.getId(),
                tenantId,
                user.getUsername(),
                user.getPassword(),
                authorities);
    }
}
