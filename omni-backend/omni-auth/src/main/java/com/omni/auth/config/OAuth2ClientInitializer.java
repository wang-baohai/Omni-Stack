package com.omni.auth.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * OAuth2 默认客户端初始化器。
 * <p>
 * 应用启动时检查数据库中是否存在默认的 OAuth2 客户端（{@code clientId = omni-frontend}），
 * 如果不存在则自动创建并持久化到 {@code oauth2_registered_client} 表中。
 * </p>
 *
 * <h3>初始化逻辑</h3>
 * <ol>
 *   <li>通过 {@link RegisteredClientRepository#findByClientId(String)} 查询是否已有 {@code omni-frontend} 客户端</li>
 *   <li>如果已存在，检查是否缺少 {@code 127.0.0.1} 的 redirect URI，缺少则追加更新</li>
 *   <li>如果不存在，构建默认客户端配置（同时注册 {@code localhost} 和 {@code 127.0.0.1} 两个回调地址）并持久化</li>
 * </ol>
 *
 * <h3>默认客户端配置（PKCE 公有客户端）</h3>
 * <ul>
 *   <li>{@code clientId}: {@code omni-frontend}</li>
 *   <li>认证方式: {@code NONE}（PKCE 公有客户端，无需 clientSecret）</li>
 *   <li>授权类型: authorization_code, refresh_token</li>
 *   <li>回调地址: 从 {@code auth.frontend.callback-url} 配置读取，同时注册 {@code localhost} 和 {@code 127.0.0.1} 版本</li>
 *   <li>作用域: openid, profile</li>
 *   <li>要求 PKCE: true</li>
 *   <li>要求授权确认: false（第一方客户端自动批准）</li>
 * </ul>
 *
 * @see AuthorizationServerConfig#registeredClientRepository 配置 JdbcRegisteredClientRepository
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2ClientInitializer implements ApplicationRunner {

    /** 默认客户端 ID（用于查询和创建） */
    private static final String DEFAULT_CLIENT_ID = "omni-frontend";

    /** OAuth2 客户端仓库，基于 JDBC 持久化 */
    private final RegisteredClientRepository registeredClientRepository;

    /** 前端回调地址，从配置读取 */
    @Value("${auth.frontend.callback-url:http://localhost:3000/callback}")
    private String callbackUrl;

    /**
     * 应用启动后执行：检查并初始化默认的 OAuth2 客户端。
     *
     * @param args 应用启动参数（未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        RegisteredClient existing = registeredClientRepository.findByClientId(DEFAULT_CLIENT_ID);
        String ipCallbackUrl = callbackUrl.replace("localhost", "127.0.0.1");

        if (existing != null) {
            // 如果已存在但缺少 127.0.0.1 的 redirect URI，则更新
            if (!existing.getRedirectUris().contains(ipCallbackUrl)) {
                RegisteredClient updated = RegisteredClient.from(existing)
                        .redirectUri(ipCallbackUrl)
                        .build();
                registeredClientRepository.save(updated);
                log.info("Updated OAuth2 client '{}' with additional redirect URI: {}",
                        DEFAULT_CLIENT_ID, ipCallbackUrl);
            } else {
                log.debug("Default OAuth2 client '{}' already up-to-date, skipping initialization",
                        DEFAULT_CLIENT_ID);
            }
            return;
        }

        RegisteredClient defaultClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(DEFAULT_CLIENT_ID)
                // PKCE 公有客户端不需要 clientSecret
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                // 同时注册 localhost 和 127.0.0.1 两个回调地址
                .redirectUri(callbackUrl)
                .redirectUri(ipCallbackUrl)
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .build();

        registeredClientRepository.save(defaultClient);
        log.info("Default OAuth2 client '{}' (PKCE) initialized with redirect URIs: [{}, {}]",
                DEFAULT_CLIENT_ID, callbackUrl, ipCallbackUrl);
    }
}
