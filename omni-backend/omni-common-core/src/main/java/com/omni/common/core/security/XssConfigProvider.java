package com.omni.common.core.security;

/**
 * XSS 配置提供者接口。
 * <p>
 * 由具体服务模块（如 omni-auth）实现，为 XSS 过滤器提供租户级的防护配置。
 * 实现类应优先从 Redis 缓存读取，缓存未命中时回源数据库查询。
 * </p>
 */
public interface XssConfigProvider {

    /**
     * 获取指定租户的 XSS 防护配置。
     *
     * @param tenantId 租户ID
     * @return XSS 防护配置（包含全局开关和已启用的规则列表），不为 null
     */
    XssSettings getXssSettings(Long tenantId);
}
