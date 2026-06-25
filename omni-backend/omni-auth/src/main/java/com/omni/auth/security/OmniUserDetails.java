package com.omni.auth.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;

/**
 * 自定义 {@link UserDetails} 实现，在标准 Spring Security 用户信息基础上
 * 携带 {@code userId} 和 {@code tenantId}，供 OAuth2 Token 签发时直接使用。
 *
 * <p>通过 {@link OmniUserDetailsService} 在认证过程中构建，
 * 可从 {@code Authentication.getPrincipal()} 获取。</p>
 *
 * @author Omni-Stack Team
 * @see OmniUserDetailsService
 */
@Getter
public class OmniUserDetails implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private final Long userId;

    /** 租户 ID */
    private final Long tenantId;

    /** 用户名 */
    private final String username;

    /** BCrypt 加密后的密码 */
    private final String password;

    /** 权限列表（含 ROLE_ 前缀的角色和权限编码） */
    private final Collection<? extends GrantedAuthority> authorities;

    /**
     * 构造 OmniUserDetails。
     *
     * @param userId      用户 ID
     * @param tenantId    租户 ID
     * @param username    用户名
     * @param password    BCrypt 加密后的密码
     * @param authorities 权限列表
     */
    public OmniUserDetails(Long userId,
                           Long tenantId,
                           String username,
                           String password,
                           Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.username = username;
        this.password = password;
        this.authorities = authorities;
    }

    /**
     * 账户过期策略：当前版本所有账户永不过期。
     * 未来如需支持，可基于 {@code sys_user.status} 字段判断。
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 账户锁定策略：当前版本不启用账户锁定机制。
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * 凭证过期策略：当前版本凭证永不过期，密码变更由管理操作触发。
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 账户启用状态：当前版本所有账户默认启用。
     * 认证前已由 {@link OmniUserDetailsService} 过滤禁用用户。
     */
    @Override
    public boolean isEnabled() {
        return true;
    }
}
