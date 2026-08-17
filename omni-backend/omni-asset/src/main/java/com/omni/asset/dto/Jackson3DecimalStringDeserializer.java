package com.omni.asset.dto;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.math.BigDecimal;

/**
 * Jackson 3 资产金额字符串反序列化器，禁止 JSON number 隐式转换。
 *
 * @author Omni-Stack Team
 */
public final class Jackson3DecimalStringDeserializer extends ValueDeserializer<BigDecimal> {

    /** {@inheritDoc} */
    @Override
    public BigDecimal deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return context.reportInputMismatch(BigDecimal.class, "资产金额必须使用 JSON 字符串");
        }
        String value = parser.getString().trim();
        if (value.isEmpty()) {
            return context.reportInputMismatch(BigDecimal.class, "资产金额字符串不能为空");
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return context.reportInputMismatch(BigDecimal.class, "资产金额字符串格式非法");
        }
    }
}
