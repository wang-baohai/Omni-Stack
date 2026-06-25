package com.omni.auth.oauth;

import com.omni.auth.dto.ProviderUser;

/**
 * OAuth2 第三方登录处理器策略接口。
 * <p>
 * 每个第三方平台（GitHub、Google、Gitee 等）提供各自的实现类，通过 Spring 的
 * {@code Map<String, OAuth2ProviderHandler>} 自动注入机制，
 * 按 bean 名称（即 {@link #getProviderId()} 返回值）进行分发。
 * </p>
 * <p>
 * 实现类须使用 {@code @Component("providerName")} 注解，
 * 其中 {@code providerName} 与 {@link #getProviderId()} 返回值一致。
 * </p>
 *
 * @author Omni-Stack Team
 * @see GitHubOAuth2Handler
 * @see GiteeOAuth2Handler
 * @see GoogleOAuth2Handler
 * @see com.omni.auth.dto.ProviderUser
 */
public interface OAuth2ProviderHandler {

    /**
     * 返回提供商标识（如 {@code "github"}、{@code "google"}、{@code "gitee"}）。
     * <p>
     * 该值同时作为 Spring bean 名称，用于 {@code Map<String, T>} 注入时的 key 匹配。
     * </p>
     *
     * @return 提供商标识字符串
     */
    String getProviderId();

    /**
     * 构建第三方授权页面 URL。
     *
     * @param state HMAC 签名的 state 参数，用于 CSRF 防护
     * @return 完整的授权 URL，浏览器应 302 重定向到此地址
     */
    String buildAuthorizationUrl(String state);

    /**
     * 使用授权码换取第三方 Access Token。
     *
     * @param code 第三方回调传入的授权码
     * @return Access Token 字符串
     * @throws com.omni.common.core.result.BusinessException 网络错误、API 返回错误或解析失败时抛出
     */
    String exchangeCodeForAccessToken(String code);

    /**
     * 获取第三方用户资料并映射为统一的 {@link ProviderUser}。
     *
     * @param accessToken 第三方 Access Token
     * @return 归一化的用户信息 DTO
     * @throws com.omni.common.core.result.BusinessException 网络错误、API 限流或解析失败时抛出
     */
    ProviderUser fetchUserProfile(String accessToken);
}
