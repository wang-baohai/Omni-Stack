package com.omni.common.security.xss;

import com.omni.common.core.security.XssSettings;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.jdk.StringDeserializer;
import tools.jackson.databind.deser.std.StdDeserializer;

import java.util.List;

/**
 * Jackson 3 字符串反序列化 XSS 防护。
 * <p>与 Jackson 2 实现共享请求级规则和净化器，保证两套 JSON 栈行为一致。</p>
 */
public class XssStringDeserializer3 extends StdDeserializer<String> {

    /** 创建 Jackson 3 字符串反序列化器。 */
    public XssStringDeserializer3() {
        super(String.class);
    }

    /**
     * 反序列化字符串并应用当前请求的 XSS 规则。
     *
     * @param parser JSON 解析器
     * @param context 反序列化上下文
     * @return 净化后的字符串
     * @throws JacksonException JSON 解析异常
     */
    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        String value = StringDeserializer.instance.deserialize(parser, context);
        List<XssSettings.XssRule> rules = XssRuleHolder.get();
        if (value == null || rules == null || rules.isEmpty()) {
            return value;
        }
        return XssSanitizer.sanitize(value, rules);
    }
}
