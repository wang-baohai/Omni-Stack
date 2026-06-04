package com.omni.auth.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 设备授权模式 OAuth2 客户端初始化器。
 * <p>
 * 应用启动时检查数据库中是否存在设备专用客户端（{@code clientId = omni-device}），
 * 如果不存在则自动创建并持久化到 {@code oauth2_registered_client} 表中。
 * </p>
 *
 * <h3>默认客户端配置（设备授权公有客户端）</h3>
 * <ol>
 *   <li>认证方式: {@code NONE}（公有客户端，无需 clientSecret）</li>
 *   <li>授权类型: {@code urn:ietf:params:oauth:grant-type:device_code} + {@code refresh_token}</li>
 *   <li>作用域: {@code openid}、{@code profile}</li>
 *   <li>不要求 PKCE（设备流不使用 PKCE）</li>
 *   <li>不要求授权同意: {@code false}（用户点击"授权"按钮即视为同意，无需 SAS 额外同意表单）</li>
 * </ol>
 *
 * @author Omni-Stack Team
 * @see OAuth2ClientInitializer
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceClientInitializer implements ApplicationRunner {

    /** 设备授权客户端 ID */
    private static final String DEVICE_CLIENT_ID = "omni-device";

    /** OAuth2 客户端仓库，基于 JDBC 持久化 */
    private final RegisteredClientRepository registeredClientRepository;

    /**
     * 应用启动后执行：检查并初始化设备授权专用客户端。
     *
     * @param args 应用启动参数（未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        RegisteredClient existing = registeredClientRepository.findByClientId(DEVICE_CLIENT_ID);

        if (existing != null) {
            // 检查已有客户端配置是否需要修正（如 requireAuthorizationConsent 应为 false）
            if (existing.getClientSettings().isRequireAuthorizationConsent()) {
                log.info("设备授权客户端 '{}' 已存在但 requireAuthorizationConsent=true，正在修正为 false", DEVICE_CLIENT_ID);
                ClientSettings updatedSettings = ClientSettings.withSettings(
                                existing.getClientSettings().getSettings())
                        .requireAuthorizationConsent(false)
                        .build();
                RegisteredClient fixed = RegisteredClient.from(existing)
                        .clientSettings(updatedSettings)
                        .build();
                registeredClientRepository.save(fixed);
                log.info("设备授权客户端 '{}' 配置已修正", DEVICE_CLIENT_ID);
            } else {
                log.debug("设备授权客户端 '{}' 已存在且配置正确，跳过初始化", DEVICE_CLIENT_ID);
            }
            return;
        }

        // 设备授权模式使用标准 grant type URI
        AuthorizationGrantType deviceCodeGrant =
                new AuthorizationGrantType("urn:ietf:params:oauth:grant-type:device_code");

        RegisteredClient deviceClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(DEVICE_CLIENT_ID)
                // 公有客户端不需要 clientSecret
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(deviceCodeGrant)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                // 设备流不使用回调地址（无 redirectUri）
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(false)
                        .requireAuthorizationConsent(false)
                        .build())
                .build();

        registeredClientRepository.save(deviceClient);
        log.info("设备授权客户端 '{}' 初始化完成（grant_types: device_code + refresh_token）", DEVICE_CLIENT_ID);
    }
}
