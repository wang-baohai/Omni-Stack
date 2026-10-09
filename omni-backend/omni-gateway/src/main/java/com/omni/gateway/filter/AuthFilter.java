package com.omni.gateway.filter;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.omni.gateway.config.JwkKeyProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * Gateway 全局认证过滤器，负责 JWT 签名验证和用户身份传播。
 * <p>
 * 作为 Spring Cloud Gateway 的 {@link GlobalFilter}，该过滤器拦截所有经过网关的请求，
 * 对非公开路径执行 JWT 验证，并将解析出的用户身份信息通过 HTTP Header 注入到下游服务请求中。
 * </p>
 *
 * <h3>请求处理流程</h3>
 * <ol>
 *   <li><b>路径判断</b> — 公开路径（{@code /api/auth/}, {@code /actuator/}, {@code /oauth2/} 等）
 *       直接放行，不需要认证</li>
 *   <li><b>Token 提取</b> — 从 {@code Authorization: Bearer <token>} 头中提取 JWT 字符串</li>
 *   <li><b>公钥获取</b> — 通过 {@link JwkKeyProvider} 获取 RSA 公钥（带缓存）</li>
 *   <li><b>签名验证</b> — 使用 {@link RSASSAVerifier} 验证 JWT 的 RS256 签名</li>
 *   <li><b>过期检查</b> — 验证 JWT 的 {@code exp} claim 是否已过期</li>
 *   <li><b>黑名单检查</b> — 查询 Redis {@code token:blacklist:{jti}} 键，
 *       拒绝已被管理员强制踢出的 Token</li>
 *   <li><b>身份注入</b> — 从 JWT claims 中提取用户信息，注入到请求头中供下游服务使用：
 *       <ul>
 *         <li>{@code X-Gateway-Assertion} — HMAC-SHA256 签名断言（{@code userId|tenantId|username|issuedAt}），
 *             下游服务据此校验身份未被伪造</li>
 *         <li>{@code X-User-Id} — 用户 ID（sub claim）</li>
 *         <li>{@code X-Tenant-Id} — 租户 ID（tenant_id claim）</li>
 *         <li>{@code X-User-Name} — 用户名（username claim）</li>
 *         <li>{@code X-User-Roles} — 角色列表，逗号分隔（roles claim）</li>
 *         <li>{@code X-User-Scopes} — 权限范围（scope claim）</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h3>服务间身份信任边界</h3>
 * <p>
 * 仅靠 {@code X-Gateway-Forwarded} 与明文身份头无法区分“网关注入”与“攻击者直连服务端口伪造”，
 * 因此网关使用共享密钥（{@code omni.gateway.assertion-secret} / 环境变量
 * {@code GATEWAY_ASSERTION_SECRET}）对身份签发短时效 HMAC 断言。
 * 密钥缺失时（非 test profile）网关 fail-closed：不转发任何身份，直接返回 503。
 * </p>
 *
 * <h3>错误处理设计</h3>
 * <ul>
 *   <li>{@code onErrorResume} 仅捕获 {@link SecurityException}，避免下游路由错误
 *       （如服务不可用、连接超时等）被误报为 JWT 验证失败</li>
 *   <li>其他异常（如参数解析错误）不在此处处理，由 Gateway 默认错误处理机制负责</li>
 * </ul>
 *
 * <h3>执行优先级</h3>
 * <p>{@code order = -100}，确保在路由转发之前执行认证检查。</p>
 *
 * @see JwkKeyProvider RSA 公钥获取与缓存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFilter implements GlobalFilter, Ordered {

    /** HTTP 授权头名称 */
    private static final String AUTH_HEADER = "Authorization";
    /** Bearer Token 前缀 */
    private static final String BEARER_PREFIX = "Bearer ";
    /** Token 黑名单 Redis Key 前缀，与 omni-auth 的 OnlineUserServiceImpl 保持一致 */
    private static final String BLACKLIST_PREFIX = "token:blacklist:";
    /** Gateway 转发标记头，下游服务据此判断请求是否经过 Gateway */
    private static final String GATEWAY_FORWARDED_HEADER = "X-Gateway-Forwarded";
    private static final String GATEWAY_FORWARDED_VALUE = "true";
    /** 网关 HMAC 签名断言头，下游服务用它判定身份头未被伪造 */
    static final String GATEWAY_ASSERTION_HEADER = "X-Gateway-Assertion";
    /** 断言头格式：{@code Base64URL(payload) + ":" + Base64URL(HMAC-SHA256(payload))} */
    private static final String ASSERTION_SEPARATOR = ":";
    /** 断言 payload 字段分隔符：{@code userId|tenantId|username|issuedAtEpochSecond} */
    private static final String ASSERTION_PAYLOAD_SEPARATOR = "|";
    /** 断言签名算法 */
    private static final String ASSERTION_HMAC_ALGORITHM = "HmacSHA256";
    /** HMAC 密钥最小长度（字节），低于此强度视为弱密钥，fail-closed 拒绝签发。 */
    private static final int MIN_ASSERTION_SECRET_BYTES = 32;
    /** 所有只能由网关注入、不能信任客户端输入的身份头。 */
    private static final List<String> TRUSTED_IDENTITY_HEADERS = List.of(
            "X-User-Id", "X-Tenant-Id", "X-User-Name", "X-User-Roles", "X-User-Scopes",
            "X-Internal-Token", GATEWAY_FORWARDED_HEADER, GATEWAY_ASSERTION_HEADER);

    /** RSA 公钥提供者，用于获取 JWT 签名验证所需的公钥 */
    private final JwkKeyProvider jwkKeyProvider;
    /** Redis 响应式模板，用于查询 Token 黑名单 */
    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * 网关签名断言密钥。优先取配置项 {@code omni.gateway.assertion-secret}，
     * 未配置时回退环境变量 {@code GATEWAY_ASSERTION_SECRET}；两者都为空时 fail-closed。
     */
    @Value("${omni.gateway.assertion-secret:#{systemEnvironment['GATEWAY_ASSERTION_SECRET'] ?: ''}}")
    private String gatewayAssertionSecret;

    /** 当前激活 profile，包含 {@code test} 时允许密钥缺失（单测/切片测试场景）。 */
    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    /**
     * 过滤器主方法，拦截所有经过网关的请求。
     * <p>
     * 响应式 Mono 链路：
     * <pre>
     * getPublicKey()                          // Mono<RSAPublicKey>
     *   .flatMap(validateJwt)                 // Mono<JWTClaimsSet> — 签名 + 过期验证
     *   .flatMap(checkBlacklist)              // Mono<JWTClaimsSet> — Redis 黑名单检查
     *   .flatMap(claims -> injectHeaders)     // 注入 X-User-* 头并转发请求
     *   .onErrorResume(SecurityException)     // 仅捕获安全异常返回 401
     * </pre>
     * </p>
     *
     * @param exchange 当前请求上下文
     * @param chain    过滤器链
     * @return 请求处理完成的信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Step 1: 公开路径直接放行（登录、验证码、租户列表、健康检查、OAuth2 端点等）
        if (isPublicPath(path)) {
            // 公开路径仅剥离超大 Bearer JWT 的 Authorization 头，避免转发到下游 Tomcat 触发 400；
            // 但 OAuth2 confidential 客户端(client_secret_basic)需要在该头里携带客户端凭据访问
            // /oauth2/token 等端点，若一并剥离会导致 SAS 收到匿名请求并报 invalid_client，因此 Basic 头必须透传。
            ServerHttpRequest mutatedRequest = request.mutate()
                    .headers(h -> {
                        String authValue = h.getFirst(AUTH_HEADER);
                        if (authValue == null || authValue.startsWith(BEARER_PREFIX)) {
                            h.remove(AUTH_HEADER);
                        }
                        TRUSTED_IDENTITY_HEADERS.forEach(h::remove);
                        h.set(GATEWAY_FORWARDED_HEADER, GATEWAY_FORWARDED_VALUE);
                    })
                    .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        }

        // Step 2: 提取 Bearer Token，缺失或格式不正确直接返回 401
        String authHeader = request.getHeaders().getFirst(AUTH_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("Missing or invalid authorization token for path: {}", path);
            return writeUnauthorizedResponse(exchange, "请先登录");
        }

        // 截取 "Bearer " 之后的 JWT 字符串
        String token = authHeader.substring(BEARER_PREFIX.length());

        // Step 3-6: 响应式 JWT 验证链（签名 + 过期 + 黑名单 + 身份注入）
        return jwkKeyProvider.getPublicKey()
                .flatMap(publicKey -> validateJwt(token, publicKey))  // 签名验证 + 过期检查
                .flatMap(claims -> checkBlacklist(token, claims))     // Token 黑名单检查
                .flatMap(claims -> {
                    // Step 6: 将 JWT claims 注入请求头，传递给下游微服务
                    // 下游服务通过 @RequestHeader("X-User-Id") 等方式获取用户身份
                    // 移除原始 Authorization 头，避免转发超大 JWT 导致下游 Tomcat 拒绝请求
                    String secret = resolveAssertionSecret();
                    if (secret.isEmpty() && !isTestProfile()) {
                        // fail-closed：无密钥则无法签发可验证断言，宁可不转发身份也不能留下可伪造的信任链
                        log.error("网关签名断言密钥未配置（omni.gateway.assertion-secret / GATEWAY_ASSERTION_SECRET），"
                                + "拒绝为受保护请求签发身份断言: path={}", path);
                        return writeServiceUnavailableResponse(exchange, "网关身份签名未就绪，请联系管理员");
                    }
                    if (isWeakSecret(secret)) {
                        log.error("网关签名断言密钥强度没有达到 {} 字节最低要求，拒绝签发断言: path={}",
                                MIN_ASSERTION_SECRET_BYTES, path);
                        return writeServiceUnavailableResponse(exchange, "网关身份签名未就绪，请联系管理员");
                    }
                    String assertion;
                    try {
                        assertion = secret.isEmpty()
                                ? null
                                : buildGatewayAssertion(claims.getSubject(),
                                        getClaimAsString(claims, "tenant_id"),
                                        getClaimAsString(claims, "username"), secret);
                    } catch (IllegalStateException e) {
                        log.error("网关签名断言生成失败，拒绝转发身份: path={}, reason={}", path, e.getMessage());
                        return writeServiceUnavailableResponse(exchange, "网关身份签名未就绪，请联系管理员");
                    }
                    final String assertionHeader = assertion;
                    ServerHttpRequest mutatedRequest = request.mutate()
                            .headers(h -> {
                                h.remove(AUTH_HEADER);
                                TRUSTED_IDENTITY_HEADERS.forEach(h::remove);
                                h.set(GATEWAY_FORWARDED_HEADER, GATEWAY_FORWARDED_VALUE);
                                // 向后兼容：旧服务仍可读明文身份头；新服务必须验证 X-Gateway-Assertion
                                h.set("X-User-Id", claims.getSubject());
                                h.set("X-Tenant-Id", getClaimAsString(claims, "tenant_id"));
                                h.set("X-User-Name", getClaimAsString(claims, "username"));
                                h.set("X-User-Roles", getClaimAsString(claims, "roles"));
                                h.set("X-User-Scopes", getClaimAsString(claims, "scope"));
                                if (assertionHeader != null) {
                                    h.set(GATEWAY_ASSERTION_HEADER, assertionHeader);
                                }
                            })
                            .build();
                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                })
                // 仅捕获 SecurityException（签名无效、token 过期等），
                // 不捕获其他异常（如下游服务不可用），避免路由错误误报为 JWT 错误
                .onErrorResume(SecurityException.class, e -> {
                    log.warn("JWT validation failed for path: {}: {}", path, e.getMessage());
                    String msg = "JWT token expired".equals(e.getMessage())
                            ? "登录已过期，请重新登录"
                            : "认证失败，请重新登录";
                    return writeUnauthorizedResponse(exchange, msg);
                });
    }

    /**
     * 过滤器执行优先级。
     * <p>返回 -100 确保在大部分自定义过滤器之前执行，认证检查应在路由转发前完成。</p>
     *
     * @return 优先级值，越小越先执行
     */
    @Override
    public int getOrder() {
        return -100;
    }

    /**
     * 解析签名断言密钥：配置项优先，未配置时回退环境变量。
     *
     * @return 去空白后的密钥，未配置时返回空字符串
     */
    private String resolveAssertionSecret() {
        return gatewayAssertionSecret == null ? "" : gatewayAssertionSecret.trim();
    }

    /**
     * 判断当前是否为测试 profile（测试环境允许不配置密钥）。
     *
     * @return 激活 profile 包含 {@code test} 时返回 true
     */
    private boolean isTestProfile() {
        return activeProfiles != null && activeProfiles.toLowerCase().contains("test");
    }

    /**
     * 判断密钥是否过弱（非空但不足 {@value #MIN_ASSERTION_SECRET_BYTES} 字节）。
     *
     * @param secret 已trim 的密钥
     * @return 弱密钥返回 true；空密钥由 {@link #isTestProfile()} 分支单独处理
     */
    private boolean isWeakSecret(String secret) {
        return !secret.isEmpty()
                && secret.getBytes(StandardCharsets.UTF_8).length < MIN_ASSERTION_SECRET_BYTES;
    }

    /**
     * 生成 {@code X-Gateway-Assertion} 头值。
     * <p>
     * 格式：{@code Base64URL(payload) + ":" + Base64URL(HMAC-SHA256(payload, secret))}，
     * payload 为 {@code userId|tenantId|username|issuedAtEpochSecond}，均使用无填充 Base64。
     * 下游服务用同一密钥重算签名并校验时间戳窗口（±5 分钟）。
     * </p>
     *
     * @param userId   用户 ID（JWT sub）
     * @param tenantId 租户 ID
     * @param username 用户名
     * @param secret   共享签名密钥，调用方保证非空
     * @return 签名断言头值
     * @throws IllegalStateException 密钥不合法或 HMAC 不可用时抛出，由调用方 fail-closed 返回 503
     */
    String buildGatewayAssertion(String userId, String tenantId, String username, String secret) {
        String payload = userId + ASSERTION_PAYLOAD_SEPARATOR + tenantId + ASSERTION_PAYLOAD_SEPARATOR
                + (username == null ? "" : username) + ASSERTION_PAYLOAD_SEPARATOR
                + (System.currentTimeMillis() / 1000L);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + ASSERTION_SEPARATOR
                + encoder.encodeToString(hmacSha256(payload, secret));
    }

    /**
     * 计算 payload 的 HMAC-SHA256 签名。
     *
     * @param payload 待签名文本
     * @param secret  共享密钥
     * @return 签名字节数组
     */
    private byte[] hmacSha256(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(ASSERTION_HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ASSERTION_HMAC_ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 签名计算失败: " + e.getMessage(), e);
        }
    }

    /** 无需认证的公开路径（精确匹配） */
    private static final List<String> PUBLIC_EXACT_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/session-login",
            "/api/auth/register",
            "/api/auth/captcha",
            "/api/auth/tenants",
            "/favicon.ico"
    );

    /** 无需认证的公开路径前缀 */
    private static final List<String> PUBLIC_PREFIX_PATHS = List.of(
            "/api/auth/oauth2/",
            "/actuator/",
            "/oauth2/",
            "/.well-known/",
            "/login",
            "/error"
    );

    /**
     * 判断请求路径是否为公开路径（无需认证）。
     * <p>
     * 公开路径包括：
     * <ul>
     *   <li>精确匹配：登录、验证码、租户列表等认证接口</li>
     *   <li>前缀匹配：社交登录入口（{@code /api/auth/oauth2/}）、OAuth2 标准端点、Actuator</li>
     * </ul>
     * 注意：管理接口（{@code /api/auth/user/**}、{@code /api/auth/role/**} 等）
     * 不在公开路径中，必须携带有效的 JWT 令牌。
     * </p>
     *
     * @param path 请求 URI 路径
     * @return true 表示是公开路径，跳过认证
     */
    private boolean isPublicPath(String path) {
        for (String exact : PUBLIC_EXACT_PATHS) {
            if (path.equals(exact)) {
                return true;
            }
        }
        for (String prefix : PUBLIC_PREFIX_PATHS) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 验证 JWT 签名和过期时间，返回解析后的 Claims。
     * <p>
     * 验证步骤：
     * <ol>
     *   <li>解析 JWT 字符串为 {@link SignedJWT} 对象</li>
     *   <li>使用 RSA 公钥创建 {@link RSASSAVerifier}，验证 RS256 签名</li>
     *   <li>检查 {@code exp} claim 是否早于当前时间</li>
     * </ol>
     * 验证通过返回包含所有 claims 的 {@link JWTClaimsSet}；
     * 验证失败返回 {@code Mono.error(SecurityException)} 以触发 401 响应。
     * </p>
     *
     * @param token     JWT 字符串（不含 "Bearer " 前缀）
     * @param publicKey RSA 公钥，从 Auth 服务的 JWKS 端点获取
     * @return 包含 JWT claims 的 Mono，签名无效或过期时返回错误
     */
    private Mono<JWTClaimsSet> validateJwt(String token, RSAPublicKey publicKey) {
        try {
            // 解析 JWT 字符串（header.payload.signature 三段式结构）
            SignedJWT signedJWT = SignedJWT.parse(token);

            // 使用 RSA 公钥验证签名（RS256 = SHA-256 + RSA PKCS#1 v1.5）
            RSASSAVerifier verifier = new RSASSAVerifier(publicKey);
            if (!signedJWT.verify(verifier)) {
                log.warn("JWT signature verification failed");
                return Mono.error(new SecurityException("Invalid JWT signature"));
            }

            // 提取 claims 并检查过期时间
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (expiration == null) {
                log.warn("JWT token missing expiration claim");
                return Mono.error(new SecurityException("Invalid JWT claims"));
            }
            if (expiration.before(new Date())) {
                log.warn("JWT token expired");
                return Mono.error(new SecurityException("JWT token expired"));
            }
            if (!isPositiveLong(claims.getSubject())
                    || !isPositiveLong(getClaimAsString(claims, "tenant_id"))) {
                log.warn("JWT token missing valid user or tenant identity");
                return Mono.error(new SecurityException("Invalid JWT claims"));
            }

            return Mono.just(claims);
        } catch (SecurityException e) {
            return Mono.error(e);
        } catch (Exception e) {
            // JWT 解析异常（格式错误、Base64 解码失败等）
            log.warn("Failed to parse or verify JWT: {}", e.getMessage());
            return Mono.error(new SecurityException("Invalid JWT token", e));
        }
    }

    /**
     * 判断身份 claim 是否为正整数，避免把空值或非法身份传播给下游服务。
     *
     * @param value claim 字符串
     * @return 是正整数时返回 true
     */
    private boolean isPositiveLong(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            return Long.parseLong(value) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    /**
     * 检查 Token 是否已被加入黑名单（管理员强制踢出）。
     * <p>
     * 从 JWT claims 中提取 {@code jti}（JWT ID），查询 Redis 中是否存在
     * {@code token:blacklist:{jti}} 键。若存在则说明该 Token 已被管理员强制注销，
     * 返回 {@code SecurityException} 触发 401 响应。
     * </p>
     *
     * @param token  JWT 字符串（用于异常日志）
     * @param claims JWT Claims 对象
     * @return 验证通过的 claims，或黑名单命中时返回错误
     */
    private Mono<JWTClaimsSet> checkBlacklist(String token, JWTClaimsSet claims) {
        String jti = claims.getJWTID();
        if (jti == null || jti.isEmpty()) {
            // 无 jti 的 token 不参与黑名单检查（兼容旧 token）
            return Mono.just(claims);
        }
        return redisTemplate.hasKey(BLACKLIST_PREFIX + jti)
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        log.warn("Token has been blacklisted (jti={}), rejecting request", jti);
                        return Mono.<JWTClaimsSet>error(new SecurityException("Token has been revoked"));
                    }
                    return Mono.just(claims);
                });
    }

    /**
     * 向前端写入结构化 JSON 错误响应。
     * <p>
     * 响应格式与后端 {@code R.fail()} 保持一致：
     * {@code {"code":401,"message":"...","data":null}}。
     * 设置 {@code Content-Type: application/json} 响应头，
     * 使用 {@link DataBuffer} 将 JSON 字符串写入响应体。
     * </p>
     *
     * @param exchange 当前请求上下文
     * @param message  错误消息
     * @return 响应写入完成的 Mono 信号
     */
    private Mono<Void> writeUnauthorizedResponse(ServerWebExchange exchange, String message) {
        return writeJsonResponse(exchange, HttpStatus.UNAUTHORIZED, 401, message);
    }

    /**
     * 写入 503 结构化错误响应（签名能力未就绪）。
     *
     * @param exchange 当前请求上下文
     * @param message  错误消息
     * @return 响应写入完成的 Mono 信号
     */
    private Mono<Void> writeServiceUnavailableResponse(ServerWebExchange exchange, String message) {
        return writeJsonResponse(exchange, HttpStatus.SERVICE_UNAVAILABLE, 503, message);
    }

    /**
     * 以 {@code R.fail()} 一致的结构写回 JSON 错误响应。
     *
     * @param exchange 当前请求上下文
     * @param status   HTTP 状态码
     * @param code     业务错误码
     * @param message  错误消息
     * @return 响应写入完成的 Mono 信号
     */
    private Mono<Void> writeJsonResponse(ServerWebExchange exchange, HttpStatus status, int code, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String json = "{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}";
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(json.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * 从 JWT Claims 中提取指定 claim 的字符串值。
     * <p>
     * 处理两种数据类型：
     * <ul>
     *   <li>{@link List} — 用逗号连接为单个字符串（如 roles: ["admin","user"] -> "admin,user"）</li>
     *   <li>其他类型 — 直接调用 {@code toString()}</li>
     *   <li>null — 返回空字符串</li>
     * </ul>
     * </p>
     *
     * @param claims    JWT Claims 对象
     * @param claimName claim 名称（如 "tenant_id", "roles", "scope"）
     * @return claim 的字符串表示，null 时返回空字符串
     */
    private String getClaimAsString(JWTClaimsSet claims, String claimName) {
        Object value = claims.getClaim(claimName);
        if (value == null) {
            return "";
        }
        // List 类型的 claim（如 roles）用逗号分隔拼接
        if (value instanceof List) {
            return String.join(",", ((List<?>) value).stream().map(Object::toString).toList());
        }
        return value.toString();
    }
}
