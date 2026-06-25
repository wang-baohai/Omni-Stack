package com.omni.auth.service;

import com.omni.auth.entity.SysUser;

import java.util.List;

/**
 * JWT 令牌生成服务接口。
 * <p>根据用户信息、角色列表和权限列表生成签名的 JWT 访问令牌。</p>
 *
 * @author Omni-Stack Team
 * @see com.omni.auth.dto.LoginResult
 */
public interface JwtTokenService {

    /**
     * 为已认证用户生成签名的 JWT 访问令牌。
     *
     * @param user        已认证的用户实体
     * @param roles       用户角色编码列表
     * @param permissions 用户权限编码列表
     * @return 序列化后的 JWT 字符串
     */
    String generateToken(SysUser user, List<String> roles, List<String> permissions);
}
