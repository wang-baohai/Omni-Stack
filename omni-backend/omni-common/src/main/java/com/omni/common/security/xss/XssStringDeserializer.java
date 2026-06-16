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
 * </p>
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
