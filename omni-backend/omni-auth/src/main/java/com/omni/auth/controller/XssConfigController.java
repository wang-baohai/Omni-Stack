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
 * <p>提供 XSS 全局开关管理、黑名单规则 CRUD 接口，路径映射在 {@code /api/auth/xss-config}。</p>
 *
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
     * @param tenantId 租户 ID（从请求头 {@code X-Tenant-Id} 获取）
     * @return XSS 防护设置视图对象
     */
    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('system:xssconfig:list')")
    public R<XssSettingsVO> getSettings(@RequestHeader("X-Tenant-Id") Long tenantId) {
        return R.ok(xssConfigService.getSettings(tenantId));
    }

    /**
     * 切换 XSS 防护全局开关。
     *
     * @param tenantId 租户 ID（从请求头 {@code X-Tenant-Id} 获取）
     * @param enabled  是否启用
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
     * @param tenantId 租户 ID（从请求头 {@code X-Tenant-Id} 获取）
     * @param page     页码（默认 1）
     * @param size     每页大小（默认 10）
     * @return 黑名单规则分页结果
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
     * @param tenantId 租户 ID（从请求头 {@code X-Tenant-Id} 获取）
     * @param request  创建请求
     * @return 创建的规则视图对象
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
     * @param id      规则 ID
     * @param request 更新请求
     * @return 更新后的规则视图对象
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
     * @param id 规则 ID
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
     * @param id      规则 ID
     * @param enabled 是否启用
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
