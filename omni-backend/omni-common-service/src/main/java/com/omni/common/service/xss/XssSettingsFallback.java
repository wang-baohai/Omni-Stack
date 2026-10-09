package com.omni.common.service.xss;

import com.omni.common.core.security.XssSettings;

/**
 * Auth 与 Redis 均不可用时的本地 XSS 安全基线。
 *
 * @author Omni-Stack Team
 */
@FunctionalInterface
public interface XssSettingsFallback {

    /**
     * 返回不依赖外部系统的安全设置。
     *
     * @return XSS 安全基线
     */
    XssSettings get();
}
