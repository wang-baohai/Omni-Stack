package com.omni.auth.service;

import com.omni.auth.entity.SysUser;

import java.util.List;

/**
 * JWT token generation service.
 */
public interface JwtTokenService {

    /**
     * Generate a signed JWT access token for the authenticated user.
     *
     * @param user        the authenticated user
     * @param roles       user's role codes
     * @param permissions user's permission codes
     * @return serialized JWT string
     */
    String generateToken(SysUser user, List<String> roles, List<String> permissions);
}
