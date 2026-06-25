package com.omni.common.security.xss;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.omni.common.core.security.XssSettings;

import java.io.IOException;
import java.util.List;

/**
 * XSS 感知的 String 反序列化器。
 * <p>
 * 包装 Jackson 原始的 {@link JsonDeserializer}，在反序列化完成后
 * 从 {@link XssRuleHolder} 获取当前请求的规则列表并执行净化。
 * 通过 {@link XssAutoConfiguration#xssJacksonModule()} 注册到 ObjectMapper，
 * 对所有 String 类型字段自动生效。
 * </p>
 *
 * @see XssRuleHolder
 * @see XssSanitizer
 */
public class XssStringDeserializer extends StdDeserializer<String> {

    private static final long serialVersionUID = 1L;

    /** 原始 String 反序列化器 */
    private final JsonDeserializer<?> delegate;

    /**
     * 构造方法。
     *
     * @param delegate 原始 String 反序列化器
     */
    public XssStringDeserializer(JsonDeserializer<?> delegate) {
        super(String.class);
        this.delegate = delegate;
    }

    /**
     * 反序列化并执行 XSS 净化。
     * <p>先委托原始反序列化器解析 JSON 值，若结果为 String 类型且当前请求存在 XSS 规则，
     * 则通过 {@link XssSanitizer#sanitize} 清洗。非 String 类型或未配置规则时原样返回。</p>
     *
     * @param p    JSON 解析器
     * @param ctxt 反序列化上下文
     * @return 反序列化并净化后的字符串值
     * @throws IOException JSON 解析异常
     */
    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        Object value = delegate.deserialize(p, ctxt);
        if (value instanceof String str) {
            List<XssSettings.XssRule> rules = XssRuleHolder.get();
            if (rules != null && !rules.isEmpty()) {
                return XssSanitizer.sanitize(str, rules);
            }
        }
        return (String) value;
    }
}
