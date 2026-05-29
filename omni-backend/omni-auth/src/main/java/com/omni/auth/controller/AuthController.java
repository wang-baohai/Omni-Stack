package com.omni.auth.controller;

import com.omni.auth.dto.CaptchaResult;
import com.omni.auth.dto.LoginRequest;
import com.omni.auth.dto.LoginResult;
import com.omni.auth.dto.TenantOption;
import com.omni.auth.entity.SysUser;
import com.omni.auth.service.CaptchaService;
import com.omni.auth.service.JwtTokenService;
import com.omni.auth.service.TenantService;
import com.omni.auth.service.UserService;
import com.omni.common.core.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Authentication controller providing login, captcha, and tenant listing endpoints.
 *
 * <p>This controller serves as the primary entry point for the user authentication flow.
 * All three endpoints are mapped under {@code /api/auth} and are whitelisted by the
 * gateway's {@code AuthFilter}, meaning they do not require a JWT token to access.</p>
 *
 * <h3>Endpoints:</h3>
 * <ul>
 *   <li>{@code POST /api/auth/login} — Username + password login with captcha validation</li>
 *   <li>{@code GET  /api/auth/captcha} — Generate a captcha image for login verification</li>
 *   <li>{@code GET  /api/auth/tenants} — List active tenants for the login tenant selector</li>
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

    private final UserService userService;
    private final CaptchaService captchaService;
    private final TenantService tenantService;
    private final JwtTokenService jwtTokenService;

    /** Access token time-to-live in seconds, read from {@code auth.token.access-token-ttl}. */
    @Value("${auth.token.access-token-ttl:900}")
    private long accessTokenTtl;

    /**
     * Authenticate a user with username, password, captcha, and tenant ID.
     *
     * <p><b>Login flow:</b></p>
     * <ol>
     *   <li>Validate captcha — the captcha code is checked against the value stored in Redis.
     *       The captcha is single-use: it is deleted from Redis regardless of whether validation
     *       succeeds or fails. Throws {@code BusinessException(400)} if expired or incorrect.</li>
     *   <li>Authenticate user — {@link UserService#authenticate} looks up the user by
     *       {@code (tenantId, username)} and verifies the BCrypt password hash.
     *       Returns {@code null} if the user is not found or the password is wrong.</li>
     *   <li>Load roles and permissions — queried from {@code sys_user_role}, {@code sys_role},
     *       {@code sys_role_permission}, and {@code sys_permission} tables.</li>
     *   <li>Generate JWT — {@link JwtTokenService#generateToken} signs a JWT containing
     *       {@code sub} (userId), {@code tenant_id}, {@code username}, {@code roles}, and
     *       {@code scope} (permissions) using the RSA private key from the JWK source.</li>
     * </ol>
     *
     * @param request the login request containing username, password, tenantId, captchaKey,
     *                and captchaCode; validated via Jakarta Bean Validation annotations
     * @return {@code R<LoginResult>} with the JWT access token, token type ("Bearer"),
     *         and expiration time in seconds on success;
     *         {@code R.fail(401, ...)} if credentials are invalid
     */
    @PostMapping("/login")
    public R<LoginResult> login(@Valid @RequestBody LoginRequest request) {
        // Step 1: Validate captcha (one-time use, stored in Redis with TTL)
        captchaService.validate(request.getCaptchaKey(), request.getCaptchaCode());

        // Step 2: Authenticate user by tenant-scoped username + BCrypt password
        SysUser user = userService.authenticate(
                request.getUsername(), request.getPassword(), request.getTenantId());
        if (user == null) {
            return R.fail(401, "Invalid username or password");
        }

        // Step 3: Load the user's roles and permissions for JWT claims
        List<String> roles = userService.getUserRoles(user.getId());
        List<String> permissions = userService.getUserPermissions(user.getId());

        // Step 4: Generate a signed JWT access token
        String token = jwtTokenService.generateToken(user, roles, permissions);

        log.info("User '{}' logged in, tenant={}", request.getUsername(), request.getTenantId());
        return R.ok(LoginResult.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(accessTokenTtl)
                .build());
    }

    /**
     * Generate a captcha image for login verification.
     *
     * <p>Creates a 130x40 pixel captcha with 4 characters using the easy-captcha library.
     * The captcha text is stored in Redis under the key {@code captcha:{uuid}} with a
     * configurable TTL (default 5 minutes). The base64-encoded PNG image is returned
     * to the frontend for display.</p>
     *
     * @return {@code R<CaptchaResult>} containing the captcha key (UUID) and the
     *         base64-encoded captcha image
     */
    @GetMapping("/captcha")
    public R<CaptchaResult> getCaptcha() {
        return R.ok(captchaService.generate());
    }

    /**
     * List all active tenants for the login page tenant selector dropdown.
     *
     * <p>Queries the {@code sys_tenant} table for tenants with {@code status = 1}
     * and returns their id, name, and code. The frontend uses this list to populate
     * the tenant selection dropdown on the login form.</p>
     *
     * @return {@code R<List<TenantOption>>} containing the active tenant options
     */
    @GetMapping("/tenants")
    public R<List<TenantOption>> getTenants() {
        return R.ok(tenantService.listActiveTenants());
    }
}
