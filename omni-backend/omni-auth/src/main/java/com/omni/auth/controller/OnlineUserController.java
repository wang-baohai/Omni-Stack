package com.omni.auth.controller;

import com.omni.auth.security.DataScopeContext;
import com.omni.auth.service.OnlineUserService;
import com.omni.auth.service.OnlineUserVO;
import com.omni.common.core.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 在线用户管理控制器。
 * <p>提供在线用户列表查询和强制踢出接口，路径映射在 {@code /api/auth/online}。
 * 列表查询支持基于 {@link DataScopeContext} 的数据权限内存过滤。</p>
 *
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
     * <p>根据当前用户的数据范围自动过滤：ALL/TENANT 返回全部，
     * DEPT/DEPT_AND_BELOW/CUSTOM 按组织单元过滤，SELF 仅返回自己。</p>
     *
     * @return 过滤后的在线用户列表
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
     * @param userId 用户 ID
     * @return 操作结果
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('system:online:kick')")
    public R<Void> kick(@PathVariable Long userId) {
        onlineUserService.kickUser(userId);
        return R.ok();
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
