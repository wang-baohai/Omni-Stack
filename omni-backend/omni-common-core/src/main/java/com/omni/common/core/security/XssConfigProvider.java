package com.omni.common.core.security;

/**
 * XSS 配置提供者 SPI 接口。
 * <p>
 * 由具体服务模块（如 omni-auth、omni-base）实现，为 XSS 过滤器提供租户级的防护配置。
 * 实现类应优先从 Redis 缓存读取，缓存未命中时回源数据库查询并回填缓存。
 * </p>
 * <p>调用时机：每次 HTTP 请求经过 {@code XssFilter} 时，通过此接口获取当前租户的防护配置，
 * 决定是否启用 XSS 清洗以及使用哪些黑名单规则。</p>
 *
 * @see XssSettings
 */
public interface XssConfigProvider {

    /**
     * 获取指定租户的 XSS 防护配置。
     * <p>返回的配置包含全局开关和已启用的黑名单规则列表。
     * 当 {@code enabled=false} 时，XSS 过滤器将跳过所有清洗逻辑。</p>
     *
     * @param tenantId 租户 ID，从请求头 {@code X-Tenant-Id} 提取
     * @return XSS 防护配置（包含全局开关和已启用的规则列表），不为 null
     */
    XssSettings getXssSettings(Long tenantId);
}
