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
 * 仅处理 URL 查询参数和 {@code application/x-www-form-urlencoded} 表单参数，
 * JSON Body 的净化由 {@link XssStringDeserializer} 在反序列化阶段完成。
 * </p>
 *
 * @see XssFilter
 * @see XssSanitizer
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

    /**
     * 获取单个查询参数并执行 XSS 净化。
     * <p>委托父类获取原始值后，通过 {@link XssSanitizer#sanitize} 按黑名单规则清洗。</p>
     *
     * @param name 参数名
     * @return 净化后的参数值，参数不存在时返回 null
     */
    @Override
    public String getParameter(String name) {
        String value = super.getParameter(name);
        return XssSanitizer.sanitize(value, rules);
    }

    /**
     * 获取参数值数组并逐一执行 XSS 净化。
     * <p>委托父类获取原始值数组后，对每个元素通过 {@link XssSanitizer#sanitize} 清洗。</p>
     *
     * @param name 参数名
     * @return 净化后的参数值数组，参数不存在时返回 null
     */
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
