package com.omni.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.omni.auth.mapper.SysUserMapper;
import com.omni.auth.security.OmniUserDetails;
import com.omni.auth.security.OmniUserDetailsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextPersistenceFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.UUID;

/**
 * Spring Authorization Server 授权服务器配置。
 * <p>
 * 配置 OAuth2 授权服务器端点、令牌签发和 JWT 签名。
 * 在 SAS 7（Spring Security 7）中，配置类位于
 * {@code org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization} 包下。
 * </p>
 */
@Slf4j
@Configuration
@EnableWebSecurity
public class AuthorizationServerConfig {

    /** 前端登录页 URL，SAS 未认证时重定向到此地址 */
    @Value("${auth.frontend.login-url:http://localhost:3000/login?oauth2=true}")
    private String frontendLoginUrl;

    /** 授权服务器 issuer 地址 */
    @Value("${auth.issuer:http://localhost:8100}")
    private String issuer;

    /**
     * OAuth2 授权服务器端点的请求匹配器。
     * <p>
     * 使用显式路径匹配，因为 {@code getEndpointsMatcher()} 需要
     * 通过 {@code HttpSecurity} 生命周期初始化配置器。
     * </p>
     */
    private static RequestMatcher oauth2EndpointsMatcher() {
        return new OrRequestMatcher(
                PathPatternRequestMatcher.pathPattern("/oauth2/authorize"),
                PathPatternRequestMatcher.pathPattern("/oauth2/token"),
                PathPatternRequestMatcher.pathPattern("/oauth2/jwks"),
                PathPatternRequestMatcher.pathPattern("/.well-known/openid-configuration"),
                PathPatternRequestMatcher.pathPattern("/oauth2/token/introspect"),
                PathPatternRequestMatcher.pathPattern("/oauth2/token/revocation"),
                PathPatternRequestMatcher.pathPattern("/oauth2/register"),
                PathPatternRequestMatcher.pathPattern("/connect/register"),
                PathPatternRequestMatcher.pathPattern("/connect/logout"),
                PathPatternRequestMatcher.pathPattern("/userinfo"),
                PathPatternRequestMatcher.pathPattern("/oauth2/par"),
                PathPatternRequestMatcher.pathPattern("/oauth2/device_authorization"),
                PathPatternRequestMatcher.pathPattern("/oauth2/device_verification")
        );
    }

    /**
     * OAuth2 授权服务器安全过滤器链。
     * <p>仅匹配 OAuth2 授权服务器端点（authorize、token、jwks 等）。</p>
     *
     * @param http HttpSecurity 配置对象
     * @return 构建完成的安全过滤器链
     * @throws Exception 配置过程中的异常
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        RequestMatcher endpointsMatcher = oauth2EndpointsMatcher();
        // 仅匹配 OAuth2 端点的请求
        http.securityMatcher(endpointsMatcher);
        OAuth2AuthorizationServerConfigurer authServerConfigurer = new OAuth2AuthorizationServerConfigurer();
        // 显式启用 OpenID Connect 1.0（SAS 7.x 要求显式开启 oidc）
        authServerConfigurer.oidc(Customizer.withDefaults());
        http.with(authServerConfigurer, Customizer.withDefaults());

        // 启用基于 HttpSession 的安全上下文持久化，确保 sessionLogin 创建的认证信息
        // 在后续 /oauth2/authorize 请求中被 SecurityContextHolderFilter 正确读取
        http.securityContext(context -> context
                .requireExplicitSave(false)
                .securityContextRepository(new HttpSessionSecurityContextRepository()));

        // 添加预认证过滤器：未认证用户访问 /oauth2/authorize 时重定向到前端登录页
        // 必须在 SecurityContextPersistenceFilter 之后执行，以确保 HttpSession 中的认证信息已被加载
        http.addFilterAfter(new OncePerRequestFilter() {
            private static final RequestMatcher AUTHORIZE_MATCHER =
                    PathPatternRequestMatcher.pathPattern("/oauth2/authorize");

            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain)
                    throws ServletException, IOException {
                if (AUTHORIZE_MATCHER.matches(request)) {
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    if (auth == null || !auth.isAuthenticated()
                            || "anonymousUser".equals(auth.getPrincipal())) {
                        log.info("Unauthenticated authorize request (session={}), redirecting to login",
                                request.getRequestedSessionId());
                        response.sendRedirect(frontendLoginUrl);
                        return;
                    }
                    log.info("Authenticated authorize request, principal='{}'", auth.getName());
                }
                filterChain.doFilter(request, response);
            }
        }, SecurityContextPersistenceFilter.class);

        // 配置异常处理：未认证时重定向到前端登录页（携带 oauth2=true 标记）
        http.exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint(frontendLoginUrl),
                        endpointsMatcher
                )
        );

        return http.build();
    }

    /**
     * 默认安全过滤器链，处理非 OAuth2 端点的请求。
     * <p>匹配所有不属于 OAuth2 授权服务器端点的请求。</p>
     *
     * @param http HttpSecurity 配置对象
     * @return 构建完成的安全过滤器链
     * @throws Exception 配置过程中的异常
     */
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        // 匹配非 OAuth2 端点的所有请求
        http.securityMatcher(new NegatedRequestMatcher(oauth2EndpointsMatcher()));

        http
                .authorizeHttpRequests(auth -> auth
                        // 认证相关接口、健康检查和错误页面允许匿名访问
                        .requestMatchers("/api/auth/**", "/actuator/**", "/error").permitAll()
                        // 其他所有请求需要认证
                        .anyRequest().authenticated()
                )
                // 启用基于 HttpSession 的安全上下文持久化
                .securityContext(context -> context
                        .requireExplicitSave(false)
                        .securityContextRepository(new HttpSessionSecurityContextRepository()))
                // 禁用表单登录（使用自定义登录接口）
                .formLogin(AbstractHttpConfigurer::disable)
                // 禁用 CSRF（JWT 无状态认证不需要 CSRF 防护）
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /**
     * JWK 密钥源配置，用于 JWT 签名。
     * <p>
     * 启动时生成 RSA 密钥对。生产环境应使用持久化的密钥库。
     * </p>
     *
     * @return JWK 密钥源
     * @throws Exception 密钥生成过程中的异常
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource() throws Exception {
        // 生成 2048 位 RSA 密钥对
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        // 构建 RSA JWK（包含公钥和私钥），生成随机 Key ID
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();

        // 封装为不可变的 JWK Set
        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    /**
     * JWT 解码器，用于验证已签名的 JWT 令牌。
     *
     * @param jwkSource JWK 密钥源
     * @return JWT 解码器实例
     */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    /**
     * 认证管理器，用于自定义登录端点进行用户名/密码认证。
     * <p>使用 {@link OmniUserDetailsService} 加载用户，{@link PasswordEncoder} 验证密码。</p>
     *
     * @param userDetailsService 自定义用户详情服务
     * @param passwordEncoder    密码编码器
     * @return 认证管理器
     */
    @Bean
    public AuthenticationManager authenticationManager(
            OmniUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /**
     * OAuth2 访问令牌自定义器，为 SAS 签发的 JWT 添加业务 claims。
     * <p>
     * 确保 OAuth2 授权码流程签发的 JWT 与自定义 {@code /api/auth/login} 签发的 JWT
     * 具有相同的 claims 结构（{@code sub}、{@code tenant_id}、{@code username}、
     * {@code roles}、{@code scope}），使 Gateway AuthFilter 无需额外适配。
     * </p>
     *
     * @param sysUserMapper 用户 Mapper，用于查询角色和权限
     * @return 令牌自定义器
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> accessTokenCustomizer(SysUserMapper sysUserMapper) {
        return context -> {
            if (!context.getTokenType().getValue().equals("access_token")) {
                return;
            }
            Object principal = context.getPrincipal().getPrincipal();
            if (principal instanceof OmniUserDetails user) {
                context.getClaims().claim("sub", String.valueOf(user.getUserId()));
                context.getClaims().claim("tenant_id", user.getTenantId());
                context.getClaims().claim("username", user.getUsername());

                List<String> roles = sysUserMapper.selectRoleCodesByUserId(user.getUserId());
                context.getClaims().claim("roles", roles);

                List<String> permissions = sysUserMapper.selectPermissionsByUserId(user.getUserId());
                context.getClaims().claim("scope", String.join(" ", permissions));
            }
        };
    }

    /**
     * OAuth2 客户端注册仓库，基于 JDBC 持久化到数据库。
     * <p>
     * 使用 Spring Authorization Server 内置的 {@link JdbcRegisteredClientRepository}，
     * 将客户端配置存储到 {@code oauth2_registered_client} 表中。
     * 默认客户端数据由 {@link com.omni.auth.config.OAuth2ClientInitializer} 在应用启动时初始化。
     * </p>
     *
     * @param jdbcOperations Spring JDBC 操作接口（由 Spring Boot 自动配置的 JdbcTemplate 提供）
     * @return 基于 JDBC 的客户端注册仓库
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcOperations jdbcOperations) {
        return new JdbcRegisteredClientRepository(jdbcOperations);
    }

    /**
     * OAuth2 授权记录服务，基于 JDBC 持久化到数据库。
     * <p>
     * SAS 7 将 PKCE code_verifier 验证提前到客户端认证阶段（{@code PublicClientAuthenticationProvider}），
     * 需要通过授权码查找对应的 {@code OAuth2Authorization} 记录来验证 PKCE 参数。
     * 必须显式配置此 Bean，否则 SAS 可能使用内存实现，导致跨请求的授权记录丢失。
     * </p>
     *
     * @param jdbcOperations Spring JDBC 操作接口
     * @param registeredClientRepository 客户端仓库，用于关联客户端信息
     * @return 基于 JDBC 的授权记录服务
     */
    @Bean
    public OAuth2AuthorizationService authorizationService(
            JdbcOperations jdbcOperations,
            RegisteredClientRepository registeredClientRepository) {
        // SAS 7 使用 Jackson 3.x (tools.jackson.databind) 序列化 OAuth2Authorization 记录。
        // 默认的 PolymorphicTypeValidator 仅信任 Spring Security 内置类型，
        // 自定义的 OmniUserDetails 作为 principal 存储在授权记录中时，
        // 反序列化会被拒绝。需要显式添加为可信类型。
        BasicPolymorphicTypeValidator.Builder ptvBuilder = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(OmniUserDetails.class.getName());

        JsonMapper jsonMapper = JsonMapper.builder()
                .addModules(SecurityJacksonModules.getModules(getClass().getClassLoader(), ptvBuilder))
                .build();

        JdbcOAuth2AuthorizationService service =
                new JdbcOAuth2AuthorizationService(jdbcOperations, registeredClientRepository);
        service.setAuthorizationRowMapper(
                new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationRowMapper(
                        registeredClientRepository, jsonMapper));
        service.setAuthorizationParametersMapper(
                new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationParametersMapper(jsonMapper));
        return service;
    }

    /**
     * OAuth2 授权同意记录服务，基于 JDBC 持久化到数据库。
     *
     * @param jdbcOperations Spring JDBC 操作接口
     * @param registeredClientRepository 客户端仓库
     * @return 基于 JDBC 的授权同意服务
     */
    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(
            JdbcOperations jdbcOperations,
            RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcOperations, registeredClientRepository);
    }

    /**
     * 授权服务器设置，配置 issuer 地址用于 JWT 签发。
     *
     * @return 授权服务器设置
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer(issuer)
                .build();
    }
}
