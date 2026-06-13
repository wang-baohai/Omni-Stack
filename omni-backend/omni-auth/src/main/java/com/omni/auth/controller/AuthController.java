package com.omni.auth.controller;

import com.nimbusds.jwt.SignedJWT;
import com.omni.auth.dto.CaptchaResult;
import com.omni.auth.dto.LoginRequest;
import com.omni.auth.dto.LoginResult;
import com.omni.auth.dto.TenantOption;
import com.omni.auth.entity.SysUser;
import com.omni.auth.service.CaptchaService;
import com.omni.auth.service.JwtTokenService;
import com.omni.auth.service.OnlineUserService;
import com.omni.auth.service.TenantService;
import com.omni.auth.service.UserService;
import com.omni.common.core.result.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认证控制器，提供登录、会话登录、验证码和租户列表接口。
 *
 * <p>该控制器是用户认证流程的主要入口，所有端点均映射在 {@code /api/auth} 路径下，
 * 并已被网关的 {@code AuthFilter} 加入白名单，无需 JWT 令牌即可访问。</p>
 *
 * <h3>接口列表：</h3>
 * <ul>
 *   <li>{@code POST /api/auth/login} — 用户名 + 密码登录（JWT 模式），含验证码校验</li>
 *   <li>{@code POST /api/auth/session-login} — 用户名 + 密码登录（Session 模式），
 *       用于 OAuth2 授权码流程中创建服务端会话</li>
 *   <li>{@code GET  /api/auth/captcha} — 生成验证码图片用于登录验证</li>
 *   <li>{@code GET  /api/auth/tenants} — 获取活跃租户列表，供登录页租户选择器使用</li>
 * </ul>
 *
 * @see CaptchaService
 * @see JwtTokenService
 * @see UserService
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    /** 用户服务 */
    private final UserService userService;
    /** 验证码服务 */
    private final CaptchaService captchaService;
    /** 租户服务 */
    private final TenantService tenantService;
    /** JWT 令牌服务 */
    private final JwtTokenService jwtTokenService;
    /** 在线用户服务 */
    private final OnlineUserService onlineUserService;
    /** 认证管理器，用于 session-login 端点进行 Spring Security 认证 */
    private final AuthenticationManager authenticationManager;

    /** 访问令牌有效期（秒），从配置项 {@code auth.token.access-token-ttl} 读取，默认 900 秒 */
    @Value("${auth.token.access-token-ttl:900}")
    private long accessTokenTtl;

    /**
     * 使用用户名、密码、验证码和租户 ID 进行用户认证登录。
     *
     * <p><b>登录流程：</b></p>
     * <ol>
     *   <li>验证码校验 — 将用户输入的验证码与 Redis 中存储的值进行比对。
     *       验证码为一次性使用：无论校验成功或失败，都会从 Redis 中删除。
     *       过期或错误时抛出 {@code BusinessException(400)}。</li>
     *   <li>用户认证 — {@link UserService#authenticate} 根据 {@code (tenantId, username)}
     *       查询用户并验证 BCrypt 密码哈希。用户不存在或密码错误时返回 {@code null}。</li>
     *   <li>加载角色和权限 — 从 {@code sys_user_role}、{@code sys_role}、
     *       {@code sys_role_permission} 和 {@code sys_permission} 表中查询。</li>
     *   <li>生成 JWT — {@link JwtTokenService#generateToken} 使用 RSA 私钥签名 JWT，
     *       包含 {@code sub}（用户ID）、{@code tenant_id}、{@code username}、
     *       {@code roles} 和 {@code scope}（权限列表）等声明。</li>
     * </ol>
     *
     * @param request 登录请求，包含用户名、密码、租户ID、验证码Key和验证码Code；
     *                通过 Jakarta Bean Validation 注解进行参数校验
     * @return 成功时返回 {@code R<LoginResult>}，包含 JWT 访问令牌、令牌类型（"Bearer"）
     *         和过期时间（秒）；认证失败时返回 {@code R.fail(401, ...)}
     */
    @PostMapping("/login")
    public R<LoginResult> login(@Valid @RequestBody LoginRequest request) {
        // 第一步：校验验证码（一次性使用，存储在 Redis 中，带 TTL 自动过期）
        captchaService.validate(request.getCaptchaKey(), request.getCaptchaCode());

        // 第二步：根据租户范围内的用户名 + BCrypt 密码进行认证
        SysUser user = userService.authenticate(
                request.getUsername(), request.getPassword(), request.getTenantId());
        if (user == null) {
            return R.fail(401, "用户名或密码错误");
        }

        // 第三步：加载用户的角色和权限列表，用于填充 JWT claims
        List<String> roles = userService.getUserRoles(user.getId());
        List<String> permissions = userService.getUserPermissions(user.getId());

        // 第四步：生成签名的 JWT 访问令牌
        String token = jwtTokenService.generateToken(user, roles, permissions);

        // 第五步：记录在线用户（从 JWT 中解析 jti）
        try {
            String jti = SignedJWT.parse(token).getJWTClaimsSet().getJWTID();
            onlineUserService.recordOnline(user.getId(), user.getUsername(), jti, accessTokenTtl);
        } catch (Exception e) {
            log.warn("记录在线用户失败: {}", e.getMessage());
        }

        log.info("用户 '{}' 登录成功，租户={}", request.getUsername(), request.getTenantId());
        return R.ok(LoginResult.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(accessTokenTtl)
                .build());
    }

    /**
     * 会话模式登录，为 OAuth2 授权码流程创建服务端 HttpSession。
     *
     * <p>与 {@link #login(LoginRequest)} 不同，此端点不返回 JWT，而是将认证信息
     * 存入 {@link HttpSession}，使后续对 {@code /oauth2/authorize} 的请求能被
     * Spring Authorization Server 识别为已认证用户。</p>
     *
     * <p><b>流程：</b></p>
     * <ol>
     *   <li>验证码校验（一次性使用）</li>
     *   <li>通过 {@link AuthenticationManager} 进行多租户认证</li>
     *   <li>将 {@link Authentication} 存入 {@link SecurityContext} 并持久化到 HttpSession</li>
     *   <li>返回用户名供前端确认</li>
     * </ol>
     *
     * @param request     登录请求（同 {@link LoginRequest}）
     * @param httpRequest Servlet 请求对象，用于获取 HttpSession
     * @return 成功返回用户名；认证失败返回 {@code R.fail(401, ...)}
     */
    @PostMapping("/session-login")
    public R<String> sessionLogin(@Valid @RequestBody LoginRequest request,
                                  HttpServletRequest httpRequest) {
        // 验证码校验
        captchaService.validate(request.getCaptchaKey(), request.getCaptchaCode());

        // 构造多租户用户名格式 "tenantId:username"
        String tenantUsername = request.getTenantId() + ":" + request.getUsername();

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(tenantUsername, request.getPassword()));

            // 将认证信息存入 SecurityContext 并持久化到 HttpSession
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);

            HttpSession session = httpRequest.getSession(true);
            session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    securityContext);

            log.info("用户 '{}' 会话登录成功，租户={}，sessionId={}",
                    request.getUsername(), request.getTenantId(), session.getId());
            return R.ok(request.getUsername());
        } catch (AuthenticationException e) {
            log.warn("用户 '{}' 会话登录失败: {}", request.getUsername(), e.getMessage());
            return R.fail(401, "用户名或密码错误");
        }
    }

    /**
     * 生成验证码图片用于登录验证。
     *
     * <p>创建 130x40 像素、4 位字符的验证码图片（使用 easy-captcha 库）。
     * 验证码文本存储在 Redis 中，键格式为 {@code captcha:{uuid}}，带可配置的 TTL
     * （默认 5 分钟）。返回 Base64 编码的 PNG 图片给前端展示。</p>
     *
     * @return {@code R<CaptchaResult>}，包含验证码 Key（UUID）和 Base64 编码的验证码图片
     */
    @GetMapping("/captcha")
    public R<CaptchaResult> getCaptcha() {
        return R.ok(captchaService.generate());
    }

    /**
     * 获取所有活跃租户列表，供登录页租户选择器下拉框使用。
     *
     * <p>查询 {@code sys_tenant} 表中 {@code status = 1} 的租户，
     * 返回其 id、名称和编码。前端使用此列表填充登录表单的租户选择下拉框。</p>
     *
     * @return {@code R<List<TenantOption>>}，包含活跃租户选项列表
     */
    @GetMapping("/tenants")
    public R<List<TenantOption>> getTenants() {
        return R.ok(tenantService.listActiveTenants());
    }

    /**
     * 检查当前 HttpSession 是否存在有效的 Spring Security 认证信息。
     *
     * <p>用于 OAuth2 授权确认页面：当用户通过 SAS 重定向到此页面时，
     * 前端通过此接口判断是否已有 SAS 会话，有则跳过登录表单直接展示授权确认。</p>
     *
     * @return 已认证返回 {@code R.ok(username)}；未认证返回 {@code R.fail(401, ...)}
     */
    @GetMapping("/session-check")
    public R<String> sessionCheck() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return R.ok(auth.getName());
        }
        return R.fail(401, "未认证");
    }
}
