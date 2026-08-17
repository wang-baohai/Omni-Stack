package com.omni.srm.dto.quotation;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Jackson 2 报价十进制字符串反序列化器，禁止 JSON number 隐式转换。
 *
 * @author Omni-Stack Team
 */
public final class Jackson2DecimalStringDeserializer extends JsonDeserializer<BigDecimal> {

    /** {@inheritDoc} */
    @Override
    public BigDecimal deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return context.reportInputMismatch(BigDecimal.class, "报价十进制字段必须使用 JSON 字符串");
        }
        String value = parser.getText().trim();
        if (value.isEmpty()) {
            return context.reportInputMismatch(BigDecimal.class, "报价十进制字符串不能为空");
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return context.reportInputMismatch(BigDecimal.class, "报价十进制字符串格式非法");
        }
    }
}
