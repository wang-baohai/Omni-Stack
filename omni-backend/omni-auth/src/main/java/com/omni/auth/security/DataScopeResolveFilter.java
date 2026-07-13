package com.omni.auth.security;

import com.omni.auth.service.DataScopeService;
import com.omni.common.core.internal.InternalDataScopeDTO;
import com.omni.common.core.result.BusinessException;
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

/**
 * 数据范围解析过滤器，负责在每次请求时解析当前用户的数据权限范围并写入 {@link DataScopeContext}。
 *
 * <h3>在安全过滤器链中的位置</h3>
 * <p>本过滤器通过 {@code @Order(0)} 排在 Spring Security 过滤器链之后执行，
 * 假定 {@link GatewayPreAuthFilter} 已经将 Gateway 转发的用户身份请求头
 * （{@code X-User-Id}、{@code X-Tenant-Id}）注入到请求中。</p>
 *
 * <h3>核心职责</h3>
 * <ol>
 *   <li>从请求头中提取 {@code X-User-Id} 和 {@code X-Tenant-Id}</li>
 *   <li>查询用户所有角色，合并各角色的 {@code dataScope}，取最宽松的范围
 *       （ALL &gt; TENANT &gt; DEPT_AND_BELOW &gt; DEPT &gt; CUSTOM &gt; SELF）</li>
 *   <li>根据有效 scope 计算可访问的组织单元 ID 集合
 *       （DEPT 取主单元、DEPT_AND_BELOW 取主单元及后代、CUSTOM 取角色自定义单元及后代）</li>
 *   <li>将解析结果封装为 {@link DataScopeContext.DataScopeInfo} 写入 ThreadLocal，
 *       供 {@link DataPermissionHandlerImpl} 和在线用户内存过滤使用</li>
 *   <li>在 {@code finally} 块中清除 ThreadLocal，防止内存泄漏</li>
 * </ol>
 *
 * <h3>跳过条件</h3>
 * <p>当请求不包含 {@code X-User-Id} 头时（如未认证的公开接口），直接放行，不设置上下文。
 * 此时 {@link DataPermissionHandlerImpl} 不会追加任何数据权限过滤条件。</p>
 *
 * @author Omni-Stack Team
 * @see DataScopeContext
 * @see DataPermissionHandlerImpl
 * @see GatewayPreAuthFilter
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class DataScopeResolveFilter extends OncePerRequestFilter {

    /** 内部服务接口路径前缀，内部接口按自身显式参数解析数据范围 */
    private static final String INTERNAL_PATH_PREFIX = "/internal/";

    /** 数据范围解析服务 */
    private final DataScopeService dataScopeService;

    /**
     * 内部服务接口不建立普通请求级数据范围上下文。
     *
     * @param request HTTP 请求
     * @return 内部接口返回 true，其余请求返回 false
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    /**
     * 执行数据范围解析过滤逻辑。
     *
     * <p>从请求头中提取用户 ID 和租户 ID，调用 {@link #resolveAndSet(Long, Long)}
     * 解析数据范围并写入 {@link DataScopeContext}，在 {@code finally} 块中清除上下文。
     * 无 {@code X-User-Id} 头或解析失败时直接放行。</p>
     *
     * @param request     HTTP 请求，需包含 Gateway 注入的 {@code X-User-Id} 请求头
     * @param response    HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
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
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "无效的 X-User-Id");
            return;
        }

        String tenantIdHeader = request.getHeader("X-Tenant-Id");
        if (tenantIdHeader == null || tenantIdHeader.isBlank()) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "缺少 X-Tenant-Id");
            return;
        }

        Long tenantId;
        try {
            tenantId = Long.parseLong(tenantIdHeader);
        } catch (NumberFormatException e) {
            log.warn("无法解析 X-Tenant-Id: {}", tenantIdHeader);
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "无效的 X-Tenant-Id");
            return;
        }

        try {
            resolveAndSet(userId, tenantId);
            filterChain.doFilter(request, response);
        } catch (BusinessException e) {
            log.warn("数据范围解析失败：userId={}, tenantId={}, code={}, message={}",
                    userId, tenantId, e.getCode(), e.getMessage());
            int status = e.getCode() == 403
                    ? HttpServletResponse.SC_FORBIDDEN : HttpServletResponse.SC_BAD_REQUEST;
            writeError(response, status, e.getMessage());
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
        InternalDataScopeDTO resolved = dataScopeService.resolveDataScope(userId, tenantId);
        DataScopeContext.DataScopeInfo info = new DataScopeContext.DataScopeInfo();
        info.setUserId(resolved.getUserId());
        info.setTenantId(resolved.getTenantId());
        info.setPrimaryUnitId(resolved.getPrimaryUnitId());
        info.setEffectiveScope(resolved.getEffectiveScope());
        info.setAccessibleUnitIds(resolved.getAccessibleUnitIds());
        DataScopeContext.set(info);

        log.debug("数据范围解析完成：userId={}, effectiveScope={}, accessibleUnitIds={}",
                userId, resolved.getEffectiveScope(), resolved.getAccessibleUnitIds().size());
    }

    /**
     * 输出标准错误响应。
     *
     * @param response HTTP 响应
     * @param status   HTTP 状态码
     * @param message  错误消息
     * @throws IOException 写响应失败时抛出
     */
    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\""
                + escapeJson(message) + "\",\"data\":null}");
    }

    /**
     * 转义 JSON 字符串中的特殊字符。
     *
     * @param value 原始字符串
     * @return 可安全写入 JSON 字符串字面量的内容
     */
    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
