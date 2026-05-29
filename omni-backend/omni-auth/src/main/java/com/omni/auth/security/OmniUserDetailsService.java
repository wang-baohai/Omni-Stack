package com.omni.auth.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.omni.auth.entity.SysUser;
import com.omni.auth.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Custom {@link UserDetailsService} that loads users from the database for Spring Security.
 *
 * <h3>Multi-Tenant Login Mechanism</h3>
 * <p>This service supports multi-tenant authentication by encoding the tenant ID into the
 * username string. The format is {@code "tenantId:username"} (e.g., {@code "1:admin"}).
 * The parsing logic works as follows:</p>
 * <ol>
 *   <li>If the username contains a colon ({@code :}), split on the first colon:
 *       the left part is parsed as {@code tenantId} (Long), and the right part is the
 *       actual username.</li>
 *   <li>If no colon is present, default to {@code tenantId = 1} (single-tenant fallback).
 *       This ensures backward compatibility when the tenant prefix is omitted.</li>
 * </ol>
 *
 * <h3>User Lookup</h3>
 * <p>The user is queried from the {@code sys_user} table with three conditions:</p>
 * <ul>
 *   <li>{@code tenant_id} matches the parsed tenant ID</li>
 *   <li>{@code username} matches the parsed username</li>
 *   <li>{@code status = 1} (only active users can authenticate)</li>
 * </ul>
 *
 * <h3>Authority Construction</h3>
 * <p>After the user is found, their roles and permissions are loaded from the database
 * via join queries on {@code sys_user_role}, {@code sys_role}, {@code sys_role_permission},
 * and {@code sys_permission}. These are converted into Spring Security authorities:</p>
 * <ul>
 *   <li>Roles are prefixed with {@code ROLE_} (e.g., role code {@code "ADMIN"} becomes
 *       authority {@code "ROLE_ADMIN"}), enabling {@code hasRole("ADMIN")} checks.</li>
 *   <li>Permissions are added as-is (e.g., {@code "user:read"}), enabling
 *       {@code hasAuthority("user:read")} checks.</li>
 * </ul>
 *
 * @see org.springframework.security.core.userdetails.UserDetailsService
 */
@Service
@RequiredArgsConstructor
public class OmniUserDetailsService implements UserDetailsService {

    private final SysUserMapper sysUserMapper;

    /**
     * Load a user by the (optionally tenant-prefixed) username.
     *
     * <p>This method is called by Spring Security's authentication providers (e.g.,
     * {@code DaoAuthenticationProvider}) during the authentication process. It is
     * also used by the OAuth2 authorization server's token endpoint when processing
     * {@code password} grant type requests.</p>
     *
     * @param username the username, optionally prefixed with tenant ID as {@code "tenantId:username"}.
     *                 If no prefix is present, defaults to tenant ID 1.
     * @return a fully populated {@link UserDetails} object with username, password, and authorities
     * @throws UsernameNotFoundException if no active user is found for the given username and tenant
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // --- Multi-tenant parsing: "tenantId:username" or plain "username" ---
        // Example: "1:admin" -> tenantId=1, actualUsername="admin"
        //          "admin"   -> tenantId=1 (default), actualUsername="admin"
        Long tenantId = 1L;
        String actualUsername = username;
        if (username.contains(":")) {
            String[] parts = username.split(":", 2);
            tenantId = Long.valueOf(parts[0]);
            actualUsername = parts[1];
        }

        // Query the user by tenant + username, requiring active status
        SysUser user = sysUserMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getTenantId, tenantId)
                .eq(SysUser::getUsername, actualUsername)
                .eq(SysUser::getStatus, 1));

        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + actualUsername);
        }

        // --- Load roles and permissions from database via join queries ---
        List<String> roleCodes = sysUserMapper.selectRoleCodesByUserId(user.getId());
        List<String> permissions = sysUserMapper.selectPermissionsByUserId(user.getId());

        // Convert role codes to ROLE_-prefixed authorities (e.g., "ADMIN" -> "ROLE_ADMIN")
        List<SimpleGrantedAuthority> authorities = roleCodes.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .collect(Collectors.toList());

        // Add permission codes as plain authorities (e.g., "user:read")
        authorities.addAll(permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList()));

        // Build the Spring Security UserDetails with BCrypt-encoded password
        return User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(authorities)
                .build();
    }
}
