package com.omni.auth.controller.internal;

import com.omni.auth.service.XssConfigService;
import com.omni.common.core.result.R;
import com.omni.common.core.security.XssSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部 XSS 配置接口。
 * <p>直接返回认证服务数据库中的权威运行时设置，不使用 Redis 缓存判断配置状态。</p>
 *
 * @author Omni-Stack Team
 */
@RestController
@RequestMapping("/internal/xss")
@RequiredArgsConstructor
public class InternalXssController {

    /** XSS 配置服务 */
    private final XssConfigService xssConfigService;

    /**
     * 获取指定租户的权威 XSS 运行时设置。
     *
     * @param tenantId 租户 ID
     * @return XSS 运行时设置
     */
    @GetMapping("/settings")
    public R<XssSettings> getSettings(@RequestParam Long tenantId) {
        return R.ok(xssConfigService.getAuthoritativeSettings(tenantId));
    }
}
