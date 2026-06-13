package com.omni.auth.security;

import com.omni.auth.entity.SysOrgUnit;
import com.omni.auth.entity.SysRole;
import com.omni.auth.entity.SysUser;
import com.omni.auth.mapper.SysOrgUnitMapper;
import com.omni.auth.mapper.SysRoleDeptMapper;
import com.omni.auth.mapper.SysRoleMapper;
import com.omni.auth.mapper.SysUserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据范围解析过滤器。
 * <p>在 Spring Security 过滤器链之后执行，解析当前用户所有角色的
 * {@code dataScope}，取最宽松的范围，计算可访问的组织单元 ID 集合，
 * 将结果存入 {@link DataScopeContext} 供下游使用。</p>
 * <p>多角色合并规则：最宽松优先
 * （ALL &gt; TENANT &gt; DEPT_AND_BELOW &gt; DEPT &gt; CUSTOM &gt; SELF）。</p>
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class DataScopeResolveFilter extends OncePerRequestFilter {

    private final SysRoleMapper sysRoleMapper;
    private final SysOrgUnitMapper sysOrgUnitMapper;
    private final SysRoleDeptMapper sysRoleDeptMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        // 仅处理由 Gateway 转发的请求（包含 X-User-Id 头）
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader == null || userIdHeader.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        Long userId;
        try {
            userId = Long.parseLong(userIdHeader);
        } catch (NumberFormatException e) {
            log.warn("无法解析 X-User-Id: {}", userIdHeader);
            filterChain.doFilter(request, response);
            return;
        }

        String tenantIdHeader = request.getHeader("X-Tenant-Id");
        Long tenantId = null;
        if (tenantIdHeader != null && !tenantIdHeader.isBlank()) {
            try {
                tenantId = Long.parseLong(tenantIdHeader);
            } catch (NumberFormatException e) {
                log.warn("无法解析 X-Tenant-Id: {}", tenantIdHeader);
            }
        }

        try {
            resolveAndSet(userId, tenantId);
            filterChain.doFilter(request, response);
        } finally {
            DataScopeContext.clear();
        }
    }

    /**
     * 解析用户数据范围并存入 DataScopeContext。
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     */
    private void resolveAndSet(Long userId, Long tenantId) {
        List<SysRole> roles = sysRoleMapper.selectRolesByUserId(userId);

        // 无角色时默认 SELF（最严格）
        if (roles == null || roles.isEmpty()) {
            DataScopeContext.DataScopeInfo info = new DataScopeContext.DataScopeInfo();
            info.setUserId(userId);
            info.setTenantId(tenantId);
            info.setEffectiveScope("SELF");
            info.setAccessibleUnitIds(Set.of());
            DataScopeContext.set(info);
            return;
        }

        // 合并所有角色的 dataScope，取优先级数值最小的（最宽松）
        String widestScope = "SELF";
        int bestPriority = DataScopeContext.PRIORITY_SELF;
        for (SysRole role : roles) {
            String scope = role.getDataScope();
            if (scope != null) {
                int priority = DataScopeContext.priorityOf(scope);
                if (priority < bestPriority) {
                    bestPriority = priority;
                    widestScope = scope;
                }
            }
        }

        // 查询用户主组织单元 ID
        Long primaryUnitId = null;
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null) {
            primaryUnitId = user.getPrimaryUnitId();
        }

        // 根据有效 scope 解析可访问的组织单元 ID 集合
        Set<Long> accessibleUnitIds = resolveAccessibleUnitIds(roles, widestScope, primaryUnitId);

        DataScopeContext.DataScopeInfo info = new DataScopeContext.DataScopeInfo();
        info.setUserId(userId);
        info.setTenantId(tenantId);
        info.setPrimaryUnitId(primaryUnitId);
        info.setEffectiveScope(widestScope);
        info.setAccessibleUnitIds(accessibleUnitIds);
        DataScopeContext.set(info);

        log.debug("数据范围解析完成：userId={}, effectiveScope={}, accessibleUnitIds={}",
                userId, widestScope, accessibleUnitIds.size());
    }

    /**
     * 根据有效数据范围解析可访问的组织单元 ID 集合。
     *
     * @param roles         用户角色列表
     * @param effectiveScope 合并后的有效数据范围
     * @param primaryUnitId 用户主组织单元 ID
     * @return 可访问的组织单元 ID 集合
     */
    private Set<Long> resolveAccessibleUnitIds(List<SysRole> roles,
                                               String effectiveScope,
                                               Long primaryUnitId) {
        return switch (effectiveScope) {
            case "ALL", "TENANT", "SELF" -> Set.of();
            case "DEPT" -> {
                if (primaryUnitId != null) {
                    yield Set.of(primaryUnitId);
                }
                yield Set.of();
            }
            case "DEPT_AND_BELOW" -> {
                if (primaryUnitId != null) {
                    SysOrgUnit unit = sysOrgUnitMapper.selectById(primaryUnitId);
                    if (unit != null && unit.getPath() != null) {
                        List<Long> descendantIds = sysOrgUnitMapper.selectDescendantIdsByPath(unit.getPath());
                        yield new HashSet<>(descendantIds);
                    }
                }
                yield Set.of();
            }
            case "CUSTOM" -> {
                Set<Long> result = new HashSet<>();
                for (SysRole role : roles) {
                    if ("CUSTOM".equals(role.getDataScope())) {
                        List<Long> deptIds = sysRoleDeptMapper.selectDeptIdsByRoleId(role.getId());
                        for (Long deptId : deptIds) {
                            result.add(deptId);
                            // 扩展为包含后代节点
                            SysOrgUnit unit = sysOrgUnitMapper.selectById(deptId);
                            if (unit != null && unit.getPath() != null) {
                                List<Long> descendants = sysOrgUnitMapper.selectDescendantIdsByPath(unit.getPath());
                                result.addAll(descendants);
                            }
                        }
                    }
                }
                yield result;
            }
            default -> Set.of();
        };
    }
}
