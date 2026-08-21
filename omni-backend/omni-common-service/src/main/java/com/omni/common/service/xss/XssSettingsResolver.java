package com.omni.common.service.xss;

import com.omni.common.core.security.XssSettings;

/**
 * 从 Auth 权威接口回源租户 XSS 设置。
 *
 * @author Omni-Stack Team
 */
@FunctionalInterface
public interface XssSettingsResolver {

    /**
     * 获取权威 XSS 设置。
     *
     * @param tenantId 租户 ID
     * @return XSS 设置
     */
    XssSettings resolve(Long tenantId);
}
