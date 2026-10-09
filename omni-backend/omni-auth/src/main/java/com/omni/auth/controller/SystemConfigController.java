package com.omni.auth.controller;

import com.omni.auth.dto.AuthLoginPolicyVO;
import com.omni.auth.dto.LoginPolicyUpdateRequest;
import com.omni.auth.service.LoginPolicyService;
import com.omni.common.core.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统配置控制器（D01）。
 * <p>本期仅提供租户级“登录保护”配置分组，唯一配置对象为 Auth 的 {@code sys_auth_login_policy}。
 * 不提供任意 key/value、不关闭锁定/验证码、不修改基础秘密。</p>
 * <ul>
 *   <li>{@code GET /api/auth/system-config/login-policy} — 权限码 {@code system:config:list}</li>
 *   <li>{@code PUT /api/auth/system-config/login-policy} — 权限码 {@code system:config:update}</li>
 * </ul>
 *
 * @author Omni-Stack Team
 * @see LoginPolicyService
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/system-config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final LoginPolicyService loginPolicyService;

    /**
     * 读取本租户登录保护策略。无持久化行时返回默认视图，不创建数据。
     *
     * @param tenantId 可信租户 ID（请求头 {@code X-Tenant-Id}）
     * @return 策略视图
     */
    @GetMapping("/login-policy")
    @PreAuthorize("hasAuthority('system:config:list')")
    public R<AuthLoginPolicyVO> getLoginPolicy(@RequestHeader("X-Tenant-Id") Long tenantId) {
        return R.ok(loginPolicyService.getPolicy(tenantId));
    }

    /**
     * 更新本租户登录保护策略（乐观并发）。陈旧版本返回 code409。
     *
     * @param tenantId 可信租户 ID（请求头 {@code X-Tenant-Id}）
     * @param request  更新请求
     * @return 更新后的策略视图
     */
    @PutMapping("/login-policy")
    @PreAuthorize("hasAuthority('system:config:update')")
    public R<AuthLoginPolicyVO> updateLoginPolicy(@RequestHeader("X-Tenant-Id") Long tenantId,
                                                  @Valid @RequestBody LoginPolicyUpdateRequest request) {
        String operator = SecurityContextHolder.getContext().getAuthentication().getName();
        return R.ok(loginPolicyService.updatePolicy(tenantId, request, operator));
    }
}
