package com.omni.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录响应结果 DTO。
 * <p>包含 JWT 访问令牌及元数据，由 {@code /api/auth/login} 接口返回。
 * 前端存储 {@code accessToken} 后在后续请求的 {@code Authorization: Bearer} 头中携带。</p>
 *
 * @author Omni-Stack Team
 * @see LoginRequest
 */
@Data
@Builder
public class LoginResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** JWT 访问令牌字符串 */
    private String accessToken;
    /** 令牌类型，固定为 "Bearer" */
    private String tokenType;
    /** 令牌有效期（秒） */
    private long expiresIn;
}
