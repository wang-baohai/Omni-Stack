package com.omni.auth.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.auth.config.OAuth2Properties;
import com.omni.auth.dto.GitHubUser;
import com.omni.auth.dto.ProviderUser;
import com.omni.common.core.result.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * GitHub OAuth2 处理器。
 * <p>
 * 封装与 GitHub OAuth2 交互的全部细节：构建授权 URL、换取 Access Token、
 * 获取用户资料。使用 JDK 内置的 {@link HttpClient} 发起 HTTP 请求，
 * 无需引入额外依赖。
 * </p>
 *
 * <p>该处理器通过 Spring Bean 名称 {@code "github"} 注册，由 {@code OAuth2LoginService}
 * 通过 {@code Map<String, OAuth2ProviderHandler>} 自动注入并按需调用。</p>
 *
 * @author Omni-Stack Team
 * @see OAuth2ProviderHandler
 * @see com.omni.auth.config.OAuth2Properties
 * @see com.omni.auth.dto.GitHubUser
 */
@Slf4j
@Component("github")
public class GitHubOAuth2Handler implements OAuth2ProviderHandler {

    /** GitHub OAuth2 授权页面 URL */
    private static final String GITHUB_AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    /** GitHub OAuth2 Token 端点 URL */
    private static final String GITHUB_TOKEN_URL = "https://github.com/login/oauth/access_token";
    /** GitHub 用户信息 API URL */
    private static final String GITHUB_USER_API = "https://api.github.com/user";
    /** 请求的 OAuth2 授权范围：读取用户资料和邮箱 */
    private static final String SCOPE = "read:user user:email";
    /** HTTP 请求 User-Agent 标识 */
    private static final String USER_AGENT = "Omni-Stack/1.0";

    /** OAuth2 第三方登录配置属性（包含 GitHub clientId、clientSecret、redirectUri） */
    private final OAuth2Properties oauth2Properties;
    /** JSON 序列化/反序列化工具，用于解析 GitHub API 响应 */
    private final ObjectMapper objectMapper;
    /** JDK 内置 HTTP 客户端，用于向 GitHub API 发起请求 */
    private final HttpClient httpClient;

    /**
     * 构造 GitHub OAuth2 处理器。
     *
     * @param oauth2Properties OAuth2 第三方登录配置属性
     */
    public GitHubOAuth2Handler(OAuth2Properties oauth2Properties) {
        this.oauth2Properties = oauth2Properties;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * {@inheritDoc}
     *
     * @return 提供商标识 {@code "github"}
     */
    @Override
    public String getProviderId() {
        return "github";
    }

    /**
     * 构建 GitHub 授权页面 URL。
     *
     * @param state HMAC 签名的 state 参数
     * @return 完整的授权 URL，浏览器应 302 重定向到此地址
     */
    @Override
    public String buildAuthorizationUrl(String state) {
        String clientId = oauth2Properties.getGithub().getClientId();
        String redirectUri = oauth2Properties.getGithub().getRedirectUri();
        return GITHUB_AUTHORIZE_URL
                + "?client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&scope=" + encode(SCOPE)
                + "&state=" + encode(state);
    }

    /**
     * 使用授权码换取 GitHub Access Token。
     *
     * @param code GitHub 回调传入的授权码
     * @return GitHub Access Token 字符串
     * @throws BusinessException 网络错误、API 返回错误或解析失败时抛出
     */
    @Override
    public String exchangeCodeForAccessToken(String code) {
        try {
            String requestBody = "client_id=" + encode(oauth2Properties.getGithub().getClientId())
                    + "&client_secret=" + encode(oauth2Properties.getGithub().getClientSecret())
                    + "&code=" + encode(code)
                    + "&redirect_uri=" + encode(oauth2Properties.getGithub().getRedirectUri());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_TOKEN_URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("GitHub token 交换失败: status={}, body={}", response.statusCode(), response.body());
                throw new BusinessException(502, "GitHub 授权码换取失败");
            }

            var jsonNode = objectMapper.readTree(response.body());
            String accessToken = jsonNode.path("access_token").asText(null);
            if (accessToken == null || accessToken.isBlank()) {
                String error = jsonNode.path("error").asText("unknown");
                log.error("GitHub token 响应中缺少 access_token: error={}", error);
                throw new BusinessException(502, "GitHub 授权码换取失败: " + error);
            }

            log.info("GitHub access_token 获取成功");
            return accessToken;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("GitHub API 调用失败（换取 token）", e);
            throw new BusinessException(502, "GitHub API 调用失败");
        }
    }

    /**
     * 获取 GitHub 用户资料并映射为统一的 {@link ProviderUser}。
     *
     * @param accessToken GitHub Access Token
     * @return 归一化的用户信息 DTO
     * @throws BusinessException 网络错误、API 限流或解析失败时抛出
     */
    @Override
    public ProviderUser fetchUserProfile(String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_USER_API))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // 检查 API 限流
            if (response.statusCode() == 403) {
                String remaining = response.headers().firstValue("X-RateLimit-Remaining").orElse("unknown");
                log.warn("GitHub API 限流: X-RateLimit-Remaining={}", remaining);
                throw new BusinessException(502, "GitHub API 请求频率超限");
            }

            if (response.statusCode() != 200) {
                log.error("GitHub 用户信息获取失败: status={}, body={}", response.statusCode(), response.body());
                throw new BusinessException(502, "GitHub 用户信息获取失败");
            }

            GitHubUser user = objectMapper.readValue(response.body(), GitHubUser.class);
            log.info("GitHub 用户信息获取成功: id={}, login={}", user.getId(), user.getLogin());
            return ProviderUser.builder()
                    .providerUserId(String.valueOf(user.getId()))
                    .username(user.getLogin())
                    .displayName(user.getName())
                    .email(user.getEmail())
                    .avatarUrl(user.getAvatarUrl())
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("GitHub API 调用失败（获取用户信息）", e);
            throw new BusinessException(502, "GitHub API 调用失败");
        }
    }

    /**
     * 对指定字符串进行 URL 编码（UTF-8）。
     *
     * @param value 待编码的字符串
     * @return URL 编码后的字符串
     */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
