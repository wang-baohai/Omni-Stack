package com.omni.common.core.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * XSS 防护运行时配置值对象。
 * <p>
 * 封装租户级 XSS 防护的全局开关状态和已启用的黑名单规则列表，
 * 供 {@link XssConfigProvider#getXssSettings(Long)} 返回给 {@code XssFilter} 使用。
 * 每次 HTTP 请求时从 Redis 缓存读取，缓存未命中则回源数据库。
 * </p>
 *
 * @see XssConfigProvider
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XssSettings implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 全局开关：是否启用 XSS 防护 */
    private boolean enabled;

    /** 已启用的黑名单规则列表（仅包含 enabled=true 的规则） */
    private List<XssRule> rules;

    /**
     * XSS 黑名单规则。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class XssRule implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 规则ID */
        private Long id;

        /**
         * 规则类型：
         * <ul>
         *   <li>HTML_TAG — HTML 标签（如 script、iframe）</li>
         *   <li>EVENT_HANDLER — 事件处理器（如 onclick、onerror）</li>
         *   <li>DANGEROUS_PROTOCOL — 危险协议（如 javascript:）</li>
         *   <li>CUSTOM_PATTERN — 自定义正则表达式</li>
         * </ul>
         */
        private String ruleType;

        /** 匹配模式（标签名或正则表达式） */
        private String pattern;
    }
}
