package com.omni.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;

/**
 * SAS 授权同意（consent）浏览器端点跨站 Origin 校验过滤器。
 *
 * <p>背景（WP-015 G01 运行时实证）：Spring Authorization Server 的
 * {@code OAuth2AuthorizationServerConfigurer} 默认对全部 OAuth2 端点忽略 CSRF，
 * 导致 {@code POST /oauth2/authorize}（consent 提交）与
 * {@code POST /oauth2/device_verification}（设备验证提交）可被跨站伪造：
 * 已登录受害者的 JSESSIONID 会被浏览器自动附带，攻击者页面可诱导受害者会话
 * 批准攻击者自取 user_code 的设备，进而以受害者身份换取 access_token。</p>
 *
 * <p>防护策略（RFC 6819 §5.2.2.3 Origin Header 校验的最小实现）：
 * 浏览器发起的跨站 POST 必然携带 {@code Origin} 头，因此：</p>
 * <ul>
 *   <li>请求携带 {@code Origin}：其 scheme/host/port 必须与网关外部地址
 *       （{@code X-Forwarded-Host} 优先，其次 {@code Host}）一致，否则拒绝（403）；</li>
 *   <li>请求未携带 {@code Origin}：视为非浏览器客户端（curl、服务间调用、
 *       依赖固定凭据的自动化测试），无「受害者 Cookie 自动附带」威胁，放行，
 *       同时不破坏 device flow 的既有消费链路。</li>
 * </ul>
 *
 * <p>该过滤器仅作用于上述两个 consent 写端点，不涉及 JWT Bearer 业务 API
 * （无状态认证天然免疫 CSRF，保持原样），也不影响 token/introspect/revoke 等
 * 客户端凭证端点。</p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
public class SasConsentOriginFilter extends OncePerRequestFilter {

    /** 需要校验的 consent 写端点：授权同意提交与设备验证提交（POST）。 */
    private static final RequestMatcher CONSENT_POST_MATCHER = new AndRequestMatcher(
            new OrRequestMatcher(
                    PathPatternRequestMatcher.pathPattern("/oauth2/authorize"),
                    PathPatternRequestMatcher.pathPattern("/oauth2/device_verification")),
            request -> HttpMethod.POST.name().equals(request.getMethod()));

    /** 串行版本号（继承 Serializable 语义的过滤器链组件约定）。 */
    private static final long serialVersionUID = 1L;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!CONSENT_POST_MATCHER.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            // 非浏览器客户端（无受害者 Cookie 自动附带威胁），放行
            filterChain.doFilter(request, response);
            return;
        }
        String expectedHost = firstNonBlank(
                request.getHeader("X-Forwarded-Host"), request.getHeader("Host"));
        String expectedScheme = firstNonBlank(
                request.getHeader("X-Forwarded-Proto"), request.isSecure() ? "https" : "http");
        if (!originMatches(origin, expectedScheme, expectedHost)) {
            log.warn("拒绝跨站 OAuth2 consent 提交: origin={}, expected={}://{}, uri={}",
                    origin, expectedScheme, expectedHost, request.getRequestURI());
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Cross-site OAuth2 consent request rejected");
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 校验 Origin 是否与网关外部地址一致（scheme、host、port 全匹配）。
     *
     * @param origin   浏览器 Origin 头（如 {@code http://localhost:8102}）
     * @param scheme   期望的协议（X-Forwarded-Proto 或请求协议）
     * @param hostPort 期望的主机端口（X-Forwarded-Host 或 Host，可能带端口）
     * @return 一致返回 {@code true}；解析失败视为不一致
     */
    static boolean originMatches(String origin, String scheme, String hostPort) {
        if (scheme == null || scheme.isBlank() || hostPort == null || hostPort.isBlank()) {
            return false;
        }
        try {
            URI originUri = URI.create(origin.trim());
            // X-Forwarded-Host 可能是多值（h1,h2），取第一段
            String firstHost = hostPort.split(",")[0].trim();
            URI expectedUri = URI.create(scheme.trim().toLowerCase() + "://" + firstHost);
            if (originUri.getHost() == null || expectedUri.getHost() == null) {
                return false;
            }
            if (!originUri.getScheme().equalsIgnoreCase(expectedUri.getScheme())
                    || !originUri.getHost().equalsIgnoreCase(expectedUri.getHost())) {
                return false;
            }
            return normalizedPort(originUri) == normalizedPort(expectedUri);
        } catch (IllegalArgumentException ex) {
            log.debug("Origin 解析失败，按不一致处理: origin={}", origin);
            return false;
        }
    }

    /** 归一化端口：显式端口优先；未声明端口按 scheme 默认值（http=80、https=443）。 */
    private static int normalizedPort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    /** 返回第一个非空字符串；全部为空返回 {@code null}。 */
    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
