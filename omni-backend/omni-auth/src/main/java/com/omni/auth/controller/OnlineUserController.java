package com.omni.auth.controller;

import com.omni.auth.service.OnlineUserService;
import com.omni.auth.service.OnlineUserVO;
import com.omni.common.core.result.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 在线用户管理控制器。
 *
 * <p>提供在线用户列表查询和强制踢出接口，路径映射在 {@code /api/auth/online}。
 * 功能权限由方法注解控制，租户及数据范围由在线用户服务统一校验。</p>
 *
 * <h3>接口列表：</h3>
 * <ul>
 *   <li>{@code GET    /api/auth/online/list} — 获取当前在线用户列表（权限码：{@code system:online:list}）</li>
 *   <li>{@code DELETE /api/auth/online/{userId}} — 强制踢出在线用户（权限码：{@code system:online:kick}）</li>
 * </ul>
 *
 * @author Omni-Stack Team
 * @see OnlineUserService
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/online")
@RequiredArgsConstructor
public class OnlineUserController {

    private final OnlineUserService onlineUserService;

    /**
     * 获取当前数据范围内的在线用户列表。
     *
     * <p>ALL 可跨租户，TENANT 仅限本租户，组织范围在本租户内按主组织过滤，
     * SELF 仅限本人；缺少有效数据范围时服务拒绝查询。</p>
     * <!-- 权限码: system:online:list -->
     *
     * @return 过滤后的在线用户列表 {@code R<List<OnlineUserVO>>}
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:online:list')")
    public R<List<OnlineUserVO>> list() {
        return R.ok(onlineUserService.listOnlineUsers());
    }

    /**
     * 强制踢出数据范围内的在线用户。
     *
     * <!-- 权限码: system:online:kick -->
     *
     * @param userId      用户 ID（路径变量）
     * @param httpRequest HTTP 请求，用于提取操作人 IP
     * @return 操作结果
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('system:online:kick')")
    public R<Void> kick(@PathVariable Long userId,
                        HttpServletRequest httpRequest) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String operator = auth != null ? auth.getName() : "anonymous";
        String ip = extractClientIp(httpRequest);
        onlineUserService.kickUser(userId, operator, ip);
        return R.ok();
    }

    /**
     * 提取客户端 IP 地址。
     * <p>优先读取 X-Forwarded-For 头（取第一个值），回退到 remoteAddr。</p>
     */
    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
