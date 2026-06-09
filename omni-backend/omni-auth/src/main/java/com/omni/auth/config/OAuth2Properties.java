package com.omni.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OAuth2 第三方登录配置属性。
 * <p>
 * 绑定 {@code auth.oauth2.*} 配置节点，提供 GitHub 等第三方提供商的
 * 凭证信息及全局安全参数。
 * </p>
 */
@Data
@ConfigurationProperties(prefix = "auth.oauth2")
public class OAuth2Properties {

    /** GitHub OAuth2 配置 */
    private GitHubProperties github = new GitHubProperties();

    /** Gitee OAuth2 配置 */
    private GiteeProperties gitee = new GiteeProperties();

    /** HMAC 签名密钥，用于 state 参数防篡改 */
    private String stateSecret = "omni-stack-oauth2-state-secret-change-in-production";

    /** 前端回调地址（社交登录成功后重定向目标） */
    private String frontendCallbackUrl = "http://localhost:3000/callback";

    /**
     * GitHub OAuth2 应用凭证。
     */
    @Data
    public static class GitHubProperties {
        /** GitHub OAuth2 App 的 Client ID */
        private String clientId;
        /** GitHub OAuth2 App 的 Client Secret */
        private String clientSecret;
        /** GitHub 回调地址（需与 GitHub OAuth App 的 Authorization Callback URL 一致） */
        private String redirectUri = "http://localhost:8100/api/auth/oauth2/github/callback";
    }

    /**
     * Gitee OAuth2 应用凭证。
     */
    @Data
    public static class GiteeProperties {
        /** Gitee OAuth2 应用的 Client ID */
        private String clientId;
        /** Gitee OAuth2 应用的 Client Secret */
        private String clientSecret;
        /** Gitee 回调地址（需与 Gitee OAuth2 应用的回调 URL 一致） */
        private String redirectUri = "http://localhost:8100/api/auth/oauth2/gitee/callback";
    }
}
