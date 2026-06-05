package com.omni.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.auth.config.OAuth2Properties;
import com.omni.auth.dto.LoginResult;
import com.omni.auth.service.SocialLoginService;
import com.omni.common.core.result.BusinessException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 社交登录控制器。
 * <p>
 * 提供第三方 OAuth2 登录的发起和回调处理端点。
 * 两个端点均返回 HTTP 302 重定向（非标准 {@code R<T>} 响应），
 * 因为前端通过 {@code window.location.href} 触发浏览器导航。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/oauth2")
@RequiredArgsConstructor
public class SocialLoginController {

    /** 社交登录服务，编排第三方 OAuth2 登录的完整流程 */
    private final SocialLoginService socialLoginService;
    /** OAuth2 第三方登录配置属性（包含前端回调地址等） */
    private final OAuth2Properties oauth2Properties;
    /** JSON 解析工具，用于从 JWT 中提取用户名 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 发起第三方登录。
     * <p>
     * 校验提供商和租户合法性，生成 HMAC 签名的 state 参数，
     * 302 重定向到第三方授权页面（如 GitHub）。
     * </p>
     *
     * @param provider 提供商标识（路径变量，如 "github"）
     * @param tenantId 租户 ID（前端传入的查询参数）
     * @param response HTTP 响应，用于发送 302 重定向
     * @throws IOException 重定向失败时抛出
     */
    @GetMapping("/{provider}")
    public void initiateLogin(@PathVariable String provider,
                              @RequestParam("tenant_id") Long tenantId,
                              HttpServletResponse response) throws IOException {
        log.info("发起第三方登录: provider={}, tenantId={}", provider, tenantId);
        String authorizeUrl = socialLoginService.initiateLogin(provider, tenantId);
        response.sendRedirect(authorizeUrl);
    }

    /**
     * 处理第三方登录回调。
     * <p>
     * 接收第三方（如 GitHub）的回调请求，验证 state、换取 Access Token、
     * 获取用户资料、查找或创建本地用户、生成 JWT。
     * 成功时重定向到前端回调页面（URL fragment 携带 JWT），
     * 失败时重定向到登录页面（查询参数携带错误信息）。
     * </p>
     *
     * @param provider 提供商标识（路径变量）
     * @param code     第三方授权码
     * @param state    HMAC 签名的 state 参数
     * @param error    第三方错误码（用户拒绝授权时第三方会传入此参数）
     * @param response HTTP 响应，用于发送 302 重定向
     * @throws IOException 重定向失败时抛出
     */
    @GetMapping("/{provider}/callback")
    public void handleCallback(@PathVariable String provider,
                               @RequestParam(value = "code", required = false) String code,
                               @RequestParam(value = "state", required = false) String state,
                               @RequestParam(value = "error", required = false) String error,
                               HttpServletResponse response) throws IOException {
        String frontendBaseUrl = oauth2Properties.getFrontendCallbackUrl()
                .replace("/callback", "");

        // 处理第三方授权拒绝
        if (error != null && !error.isBlank()) {
            log.info("第三方授权被用户拒绝: provider={}, error={}", provider, error);
            response.sendRedirect(frontendBaseUrl + "/login?error=user_denied");
            return;
        }

        // 校验必要参数
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            log.warn("第三方回调参数缺失: code={}, state={}", code != null ? "***" : "null", state != null ? "***" : "null");
            response.sendRedirect(frontendBaseUrl + "/login?error=invalid_callback");
            return;
        }

        try {
            log.info("处理第三方登录回调: provider={}", provider);
            LoginResult loginResult = socialLoginService.handleCallback(provider, code, state);

            // 从 JWT 中提取用户名（与前端 callback 页面的逻辑一致）
            String username = extractUsernameFromJwt(loginResult.getAccessToken());

            // 重定向到前端回调页面，JWT 放在 URL fragment 中
            String redirectUrl = oauth2Properties.getFrontendCallbackUrl()
                    + "#token=" + encode(loginResult.getAccessToken())
                    + "&username=" + encode(username != null ? username : "");
            response.sendRedirect(redirectUrl);
        } catch (BusinessException e) {
            log.error("第三方登录失败: provider={}, error={}", provider, e.getMessage());
            String message = URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
            response.sendRedirect(frontendBaseUrl + "/login?error=social_login_failed&message=" + message);
        }
    }

    /**
     * 从 JWT payload 中提取用户名（不做签名验证）。
     */
    private String extractUsernameFromJwt(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode json = objectMapper.readTree(payload);
            JsonNode usernameNode = json.get("username");
            if (usernameNode != null && !usernameNode.isNull()) {
                return usernameNode.asText();
            }
            JsonNode subNode = json.get("sub");
            return subNode != null ? subNode.asText() : null;
        } catch (Exception e) {
            log.warn("从 JWT 中提取用户名失败", e);
            return null;
        }
    }

    /**
     * URL 编码。
     */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
