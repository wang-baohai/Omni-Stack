package com.omni.auth.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.auth.config.OAuth2Properties;
import com.omni.auth.dto.GoogleUser;
import com.omni.auth.dto.ProviderUser;
import com.omni.common.core.result.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Google OAuth2 处理器。
 * <p>
 * 封装与 Google OAuth2 交互的全部细节：构建授权 URL、换取 Access Token、
 * 获取用户资料。使用 JDK 内置的 {@link HttpClient} 发起 HTTP 请求，
 * 无需引入额外依赖。
 * </p>
 *
 * <p>该处理器通过 Spring Bean 名称 {@code "google"} 注册，由 {@code OAuth2LoginService}
 * 通过 {@code Map<String, OAuth2ProviderHandler>} 自动注入并按需调用。
 * 由于 Google API 在国内网络环境下无法直接访问，本处理器配置了本地代理
 * {@code localhost:7897} 进行转发，使用时请确保代理服务已启动。
 * 用户名从邮箱 {@code @} 前缀派生，邮箱为空时 fallback 到 Google 用户唯一标识 {@code sub}。</p>
 *
 * @author Omni-Stack Team
 * @see OAuth2ProviderHandler
 * @see com.omni.auth.config.OAuth2Properties
 * @see com.omni.auth.dto.GoogleUser
 */
@Slf4j
@Component("google")
public class GoogleOAuth2Handler implements OAuth2ProviderHandler {

    /** Google OAuth2 授权页面 URL */
    private static final String GOOGLE_AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    /** Google OAuth2 Token 端点 URL */
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    /** Google 用户信息 API URL */
    private static final String GOOGLE_USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";
    /** 请求的 OAuth2 授权范围：用户 ID、基本资料和邮箱 */
    private static final String SCOPE = "openid profile email";
    /** HTTP 请求 User-Agent 标识 */
    private static final String USER_AGENT = "Omni-Stack/1.0";

    /** OAuth2 第三方登录配置属性（包含 Google clientId、clientSecret、redirectUri） */
    private final OAuth2Properties oauth2Properties;
    /** JSON 序列化/反序列化工具，用于解析 Google API 响应 */
    private final ObjectMapper objectMapper;
    /** JDK 内置 HTTP 客户端，通过本地代理（localhost:7897）向 Google API 发起请求 */
    private final HttpClient httpClient;

    /**
     * 构造 Google OAuth2 处理器。
     * <p>
     * 初始化 HTTP 客户端，配置连接超时、禁止自动跟随重定向，
     * 并通过本地代理（{@code localhost:7897}）访问 Google API。
     * </p>
     *
     * @param oauth2Properties OAuth2 第三方登录配置属性
     */
    public GoogleOAuth2Handler(OAuth2Properties oauth2Properties) {
        this.oauth2Properties = oauth2Properties;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .proxy(ProxySelector.of(new InetSocketAddress("localhost", 7897)))
                .build();
    }

    /**
     * {@inheritDoc}
     *
     * @return 提供商标识 {@code "google"}
     */
    @Override
    public String getProviderId() {
        return "google";
    }

    /**
     * 构建 Google 授权页面 URL。
     *
     * @param state HMAC 签名的 state 参数
     * @return 完整的授权 URL，浏览器应 302 重定向到此地址
     */
    @Override
    public String buildAuthorizationUrl(String state) {
        String clientId = oauth2Properties.getGoogle().getClientId();
        String redirectUri = oauth2Properties.getGoogle().getRedirectUri();
        return GOOGLE_AUTHORIZE_URL
                + "?client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code"
                + "&scope=" + encode(SCOPE)
                + "&state=" + encode(state);
    }

    /**
     * 使用授权码换取 Google Access Token。
     *
     * @param code Google 回调传入的授权码
     * @return Google Access Token 字符串
     * @throws BusinessException 网络错误、API 返回错误或解析失败时抛出
     */
    @Override
    public String exchangeCodeForAccessToken(String code) {
        try {
            String requestBody = "client_id=" + encode(oauth2Properties.getGoogle().getClientId())
                    + "&client_secret=" + encode(oauth2Properties.getGoogle().getClientSecret())
                    + "&code=" + encode(code)
                    + "&redirect_uri=" + encode(oauth2Properties.getGoogle().getRedirectUri())
                    + "&grant_type=authorization_code";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GOOGLE_TOKEN_URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("User-Agent", USER_AGENT)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Google token 交换失败: status={}, body={}", response.statusCode(), response.body());
                throw new BusinessException(502, "Google 授权码换取失败");
            }

            var jsonNode = objectMapper.readTree(response.body());
            String accessToken = jsonNode.path("access_token").asText(null);
            if (accessToken == null || accessToken.isBlank()) {
                String error = jsonNode.path("error").asText("unknown");
                String errorDesc = jsonNode.path("error_description").asText("");
                log.error("Google token 响应中缺少 access_token: error={}, description={}", error, errorDesc);
                throw new BusinessException(502, "Google 授权码换取失败: " + error);
            }

            log.info("Google access_token 获取成功");
            return accessToken;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google API 调用失败（换取 token）", e);
            throw new BusinessException(502, "Google API 调用失败");
        }
    }

    /**
     * 获取 Google 用户资料并映射为统一的 {@link ProviderUser}。
     *
     * @param accessToken Google Access Token
     * @return 归一化的用户信息 DTO
     * @throws BusinessException 网络错误、API 限流或解析失败时抛出
     */
    @Override
    public ProviderUser fetchUserProfile(String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GOOGLE_USERINFO_URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // 检查 API 限流（Google 使用 429 Too Many Requests）
            if (response.statusCode() == 429) {
                String retryAfter = response.headers().firstValue("Retry-After").orElse("unknown");
                log.warn("Google API 限流: Retry-After={}", retryAfter);
                throw new BusinessException(502, "Google API 请求频率超限");
            }

            if (response.statusCode() != 200) {
                log.error("Google 用户信息获取失败: status={}, body={}", response.statusCode(), response.body());
                throw new BusinessException(502, "Google 用户信息获取失败");
            }

            GoogleUser user = objectMapper.readValue(response.body(), GoogleUser.class);

            // 从邮箱 @ 前缀派生用户名，fallback 到 Google sub（用户 ID）
            String username = deriveUsername(user);

            log.info("Google 用户信息获取成功: sub={}, username={}", user.getSub(), username);
            return ProviderUser.builder()
                    .providerUserId(user.getSub())
                    .username(username)
                    .displayName(user.getName())
                    .email(user.getEmail())
                    .avatarUrl(user.getPicture())
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google API 调用失败（获取用户信息）", e);
            throw new BusinessException(502, "Google API 调用失败");
        }
    }

    /**
     * 从 Google 用户资料中派生用户名。
     * <p>
     * 优先使用邮箱 {@code @} 前缀（如 {@code john@gmail.com} → {@code john}），
     * 邮箱为空或格式异常时 fallback 到 Google 用户唯一标识 {@code sub}。
     * </p>
     *
     * @param user Google 用户资料
     * @return 派生的用户名（不含前缀）
     */
    private String deriveUsername(GoogleUser user) {
        String email = user.getEmail();
        if (email != null && email.contains("@")) {
            String prefix = email.substring(0, email.indexOf("@"));
            if (!prefix.isBlank()) {
                return prefix;
            }
        }
        log.warn("Google 用户邮箱为空或 @ 前缀为空，使用 sub 作为用户名: sub={}", user.getSub());
        return user.getSub();
    }

    /**
     * URL 编码（UTF-8）。
     *
     * @param value 待编码的字符串
     * @return URL 编码后的字符串
     */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
