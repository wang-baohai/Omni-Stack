package com.omni.auth.controller;

import com.omni.auth.security.DataScopeContext;
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
import java.util.Set;

/**
 * 在线用户管理控制器。
 *
 * <p>提供在线用户列表查询和强制踢出接口，路径映射在 {@code /api/auth/online}。
 * 列表查询支持基于 {@link DataScopeContext} 的数据权限内存过滤。</p>
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
     * 获取当前在线用户列表。
     *
     * <p>根据当前用户的数据范围自动过滤：ALL/TENANT 返回全部，
     * DEPT/DEPT_AND_BELOW/CUSTOM 按组织单元过滤，SELF 仅返回自己。</p>
     * <!-- 权限码: system:online:list -->
     *
     * @return 过滤后的在线用户列表 {@code R<List<OnlineUserVO>>}
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:online:list')")
    public R<List<OnlineUserVO>> list() {
        List<OnlineUserVO> list = onlineUserService.listOnlineUsers();

        // 根据数据范围进行内存过滤
        DataScopeContext.DataScopeInfo scope = DataScopeContext.get();
        if (scope != null) {
            list = filterByDataScope(list, scope);
        }

        return R.ok(list);
    }

    /**
     * 强制踢出在线用户。
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

    /**
     * 根据数据范围对在线用户列表进行内存过滤。
     *
     * @param list  原始在线用户列表
     * @param scope 当前用户的数据范围信息
     * @return 过滤后的在线用户列表
     */
    private List<OnlineUserVO> filterByDataScope(List<OnlineUserVO> list,
                                                  DataScopeContext.DataScopeInfo scope) {
        String effectiveScope = scope.getEffectiveScope();
        if (effectiveScope == null) {
            return list;
        }

        return switch (effectiveScope) {
            // ALL / TENANT：返回全部在线用户
            case "ALL", "TENANT" -> list;
            // DEPT / DEPT_AND_BELOW / CUSTOM：按可访问的组织单元过滤
            case "DEPT", "DEPT_AND_BELOW", "CUSTOM" -> {
                Set<Long> accessibleUnitIds = scope.getAccessibleUnitIds();
                if (accessibleUnitIds != null && !accessibleUnitIds.isEmpty()) {
                    yield list.stream()
                            .filter(u -> u.getPrimaryUnitId() != null
                                    && accessibleUnitIds.contains(u.getPrimaryUnitId()))
                            .toList();
                }
                yield List.of();
            }
            // SELF：仅保留当前用户自己
            case "SELF" -> list.stream()
                    .filter(u -> u.getUserId().equals(scope.getUserId()))
                    .toList();
            default -> list;
        };
    }
}
