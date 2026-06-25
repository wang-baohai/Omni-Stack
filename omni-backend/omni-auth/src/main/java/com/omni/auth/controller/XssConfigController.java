package com.omni.auth.controller;

import com.omni.auth.dto.BlacklistRuleVO;
import com.omni.auth.dto.CreateXssRuleRequest;
import com.omni.auth.dto.UpdateXssRuleRequest;
import com.omni.auth.dto.XssSettingsVO;
import com.omni.auth.service.XssConfigService;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * XSS 防护配置控制器。
 *
 * <p>提供 XSS 全局开关管理、黑名单规则 CRUD 接口，路径映射在 {@code /api/auth/xss-config}。
 * 支持 XSS 防护的全局启用/禁用、黑名单规则的增删改查及单条规则状态切换。</p>
 *
 * <h3>接口列表：</h3>
 * <ul>
 *   <li>{@code GET    /api/auth/xss-config/settings} — 获取 XSS 防护设置（权限码：{@code system:xssconfig:list}）</li>
 *   <li>{@code PUT    /api/auth/xss-config/toggle} — 切换全局开关（权限码：{@code system:xssconfig:update}）</li>
 *   <li>{@code GET    /api/auth/xss-config/rules/list} — 分页查询黑名单规则（权限码：{@code system:xssconfig:list}）</li>
 *   <li>{@code POST   /api/auth/xss-config/rules} — 创建黑名单规则（权限码：{@code system:xssconfig:create}）</li>
 *   <li>{@code PUT    /api/auth/xss-config/rules/{id}} — 更新黑名单规则（权限码：{@code system:xssconfig:update}）</li>
 *   <li>{@code DELETE /api/auth/xss-config/rules/{id}} — 删除黑名单规则（权限码：{@code system:xssconfig:delete}）</li>
 *   <li>{@code PUT    /api/auth/xss-config/rules/{id}/toggle} — 切换规则启用状态（权限码：{@code system:xssconfig:update}）</li>
 * </ul>
 *
 * @author Omni-Stack Team
 * @see XssConfigService
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/xss-config")
@RequiredArgsConstructor
public class XssConfigController {

    private final XssConfigService xssConfigService;

    /**
     * 获取 XSS 防护设置（全局开关 + 全部规则列表）。
     *
     * <!-- 权限码: system:xssconfig:list -->
     *
     * @param tenantId 租户 ID（从请求头 {@code X-Tenant-Id} 获取）
     * @return XSS 防护设置视图对象 {@code R<XssSettingsVO>}
     */
    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('system:xssconfig:list')")
    public R<XssSettingsVO> getSettings(@RequestHeader("X-Tenant-Id") Long tenantId) {
        return R.ok(xssConfigService.getSettings(tenantId));
    }

    /**
     * 切换 XSS 防护全局开关。
     *
     * <!-- 权限码: system:xssconfig:update -->
     *
     * @param tenantId 租户 ID（从请求头 {@code X-Tenant-Id} 获取）
     * @param enabled  是否启用（{@code true} 启用，{@code false} 禁用）
     * @return 操作结果
     */
    @PutMapping("/toggle")
    @PreAuthorize("hasAuthority('system:xssconfig:update')")
    public R<Void> toggleGlobal(@RequestHeader("X-Tenant-Id") Long tenantId,
                                @RequestParam boolean enabled) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        xssConfigService.toggleGlobal(tenantId, enabled, username);
        return R.ok();
    }

    /**
     * 分页查询 XSS 黑名单规则列表。
     *
     * <!-- 权限码: system:xssconfig:list -->
     *
     * @param tenantId 租户 ID（从请求头 {@code X-Tenant-Id} 获取）
     * @param page     页码（默认 1）
     * @param size     每页大小（默认 10）
     * @return 黑名单规则分页结果 {@code R<PageResult<BlacklistRuleVO>>}
     */
    @GetMapping("/rules/list")
    @PreAuthorize("hasAuthority('system:xssconfig:list')")
    public R<PageResult<BlacklistRuleVO>> listRules(@RequestHeader("X-Tenant-Id") Long tenantId,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size) {
        return R.ok(xssConfigService.listRules(tenantId, page, size));
    }

    /**
     * 创建 XSS 黑名单规则。
     *
     * <!-- 权限码: system:xssconfig:create -->
     *
     * @param tenantId 租户 ID（从请求头 {@code X-Tenant-Id} 获取）
     * @param request  创建请求（含规则名称、匹配模式等）
     * @return 创建的规则视图对象 {@code R<BlacklistRuleVO>}
     */
    @PostMapping("/rules")
    @PreAuthorize("hasAuthority('system:xssconfig:create')")
    public R<BlacklistRuleVO> createRule(@RequestHeader("X-Tenant-Id") Long tenantId,
                                         @Valid @RequestBody CreateXssRuleRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return R.ok(xssConfigService.createRule(tenantId, request, username));
    }

    /**
     * 更新 XSS 黑名单规则。
     *
     * <!-- 权限码: system:xssconfig:update -->
     *
     * @param id      规则 ID（路径变量）
     * @param request 更新请求
     * @return 更新后的规则视图对象 {@code R<BlacklistRuleVO>}
     */
    @PutMapping("/rules/{id}")
    @PreAuthorize("hasAuthority('system:xssconfig:update')")
    public R<BlacklistRuleVO> updateRule(@PathVariable Long id,
                                         @Valid @RequestBody UpdateXssRuleRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return R.ok(xssConfigService.updateRule(id, request, username));
    }

    /**
     * 删除 XSS 黑名单规则。
     *
     * <!-- 权限码: system:xssconfig:delete -->
     *
     * @param id 规则 ID（路径变量）
     * @return 操作结果
     */
    @DeleteMapping("/rules/{id}")
    @PreAuthorize("hasAuthority('system:xssconfig:delete')")
    public R<Void> deleteRule(@PathVariable Long id) {
        xssConfigService.deleteRule(id);
        return R.ok();
    }

    /**
     * 切换单条 XSS 黑名单规则的启用状态。
     *
     * <!-- 权限码: system:xssconfig:update -->
     *
     * @param id      规则 ID（路径变量）
     * @param enabled 是否启用（{@code true} 启用，{@code false} 禁用）
     * @return 操作结果
     */
    @PutMapping("/rules/{id}/toggle")
    @PreAuthorize("hasAuthority('system:xssconfig:update')")
    public R<Void> toggleRule(@PathVariable Long id,
                              @RequestParam boolean enabled) {
        xssConfigService.toggleRule(id, enabled);
        return R.ok();
    }
}
