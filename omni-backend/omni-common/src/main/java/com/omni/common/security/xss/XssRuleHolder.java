package com.omni.common.security.xss;

import com.omni.common.core.security.XssSettings;

import java.util.List;

/**
 * XSS 规则 ThreadLocal 持有者。
 * <p>
 * {@link XssFilter} 在请求开始时设置当前租户的黑名单规则，
 * {@link XssStringDeserializer} 和 {@link XssHttpServletRequestWrapper}
 * 通过此持有者获取规则执行净化。请求结束时在 finally 块中清理。
 * </p>
 */
public final class XssRuleHolder {

    private static final ThreadLocal<List<XssSettings.XssRule>> HOLDER = new ThreadLocal<>();

    private XssRuleHolder() {}

    /** 设置当前线程的 XSS 规则列表 */
    public static void set(List<XssSettings.XssRule> rules) {
        HOLDER.set(rules);
    }

    /** 获取当前线程的 XSS 规则列表 */
    public static List<XssSettings.XssRule> get() {
        return HOLDER.get();
    }

    /** 清理当前线程的 XSS 规则列表，防止内存泄漏 */
    public static void clear() {
        HOLDER.remove();
    }
}
