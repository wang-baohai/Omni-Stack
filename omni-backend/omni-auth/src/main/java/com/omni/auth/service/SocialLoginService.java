package com.omni.auth.service;

import com.omni.auth.dto.LoginResult;

/**
 * 社交登录服务接口。
 * <p>
 * 提供第三方 OAuth2 登录的发起和回调处理，支持 GitHub 等第三方提供商。
 * </p>
 * @author Omni-Stack Team
 * @see com.omni.auth.dto.LoginResult
 * @see com.omni.auth.oauth.OAuth2ProviderHandler
 */
public interface SocialLoginService {

    /**
     * 发起第三方登录。
     * <p>生成带 HMAC 签名的 state 参数，返回第三方授权页面 URL。</p>
     *
     * @param provider 提供商标识（如 "github"）
     * @param tenantId 租户 ID（由前端传入，用于新用户自动归入租户）
     * @return 第三方授权页面 URL，浏览器应 302 重定向到此地址
     */
    String initiateLogin(String provider, Long tenantId);

    /**
     * 处理第三方登录回调。
     * <p>
     * 验证 state、用授权码换取第三方 Access Token、获取用户资料、
     * 查找或自动创建本地用户、生成 JWT。
     * </p>
     *
     * @param provider 提供商标识（如 "github"）
     * @param code     第三方回调传入的授权码
     * @param state    HMAC 签名的 state 参数
     * @return 登录结果，包含 JWT 访问令牌
     */
    LoginResult handleCallback(String provider, String code, String state);
}
