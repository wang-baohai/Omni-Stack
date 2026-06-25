package com.omni.auth.security;

import com.nimbusds.jwt.SignedJWT;
import com.omni.auth.service.OnlineUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * OAuth2 令牌签发事件监听器，为所有 SAS（Spring Authorization Server）管理的登录流程
 * 记录在线用户状态。
 *
 * <p>监听 {@link AuthenticationSuccessEvent}，当认证类型为
 * {@link OAuth2AccessTokenAuthenticationToken} 时（即授权码、刷新令牌、设备码等 SAS 签发流程），
 * 解析已签名的 JWT 提取 {@code jti}，并委托 {@link OnlineUserService} 写入 Redis 在线标记。</p>
 *
 * <p>此监听器仅处理 SAS 端点（{@code /oauth2/token}）产生的事件。
 * 密码登录（{@code /api/auth/login}）和会话登录（{@code /api/auth/session-login}）
 * 产生的认证事件因类型不匹配会被自动忽略。</p>
 *
 * @author Omni-Stack Team
 * @see OnlineUserService#recordOnline(Long, String, String, long)
 * @see OmniUserDetails
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2TokenEventListener {

    /** 在线用户服务 */
    private final OnlineUserService onlineUserService;

    /** 访问令牌有效期（秒），与 SAS 签发的 JWT 过期时间保持一致 */
    @Value("${auth.token.access-token-ttl:900}")
    private long accessTokenTtl;

    /**
     * 处理认证成功事件：仅当认证类型为 {@link OAuth2AccessTokenAuthenticationToken}
     * 且主体为 {@link OmniUserDetails} 时，记录在线用户。
     *
     * @param event 认证成功事件
     */
    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        Authentication authentication = event.getAuthentication();

        if (!(authentication instanceof OAuth2AccessTokenAuthenticationToken tokenAuth)) {
            return;
        }

        Object principal = tokenAuth.getPrincipal();
        if (!(principal instanceof OmniUserDetails user)) {
            return;
        }

        try {
            String tokenValue = tokenAuth.getAccessToken().getTokenValue();
            String jti = SignedJWT.parse(tokenValue).getJWTClaimsSet().getJWTID();
            onlineUserService.recordOnline(user.getUserId(), user.getUsername(), jti, accessTokenTtl);
            log.debug("OAuth2 令牌签发后记录在线用户: userId={}, username={}", user.getUserId(), user.getUsername());
        } catch (Exception e) {
            log.warn("记录在线用户失败: {}", e.getMessage());
        }
    }
}
