package com.omni.common.security.xss;

import com.omni.common.core.security.XssSettings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.List;

/**
 * XSS 感知的请求包装器。
 * <p>
 * 重写 {@link #getParameter(String)} 和 {@link #getParameterValues(String)}，
 * 对查询参数和表单参数执行 {@link XssSanitizer} 净化。
 * </p>
 */
public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private final List<XssSettings.XssRule> rules;

    /**
     * 构造方法。
     *
     * @param request 原始请求
     * @param rules   当前租户的 XSS 黑名单规则
     */
    public XssHttpServletRequestWrapper(HttpServletRequest request, List<XssSettings.XssRule> rules) {
        super(request);
        this.rules = rules;
    }

    @Override
    public String getParameter(String name) {
        String value = super.getParameter(name);
        return XssSanitizer.sanitize(value, rules);
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values == null) {
            return null;
        }
        String[] sanitized = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            sanitized[i] = XssSanitizer.sanitize(values[i], rules);
        }
        return sanitized;
    }
}
