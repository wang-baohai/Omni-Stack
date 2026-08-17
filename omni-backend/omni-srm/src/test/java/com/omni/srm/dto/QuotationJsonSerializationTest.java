package com.omni.srm.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.srm.dto.quotation.ProcurementRfqInvitationLine;
import com.omni.srm.dto.quotation.QuotationLineVO;
import com.omni.srm.dto.quotation.QuotationLineRequest;
import com.omni.srm.dto.quotation.QuotationVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 报价金额和数量 JSON 精度保护测试。 */
class QuotationJsonSerializationTest {

    /** 所有可能超过 JavaScript 安全精度的十进制数必须输出为字符串。 */
    @Test
    void shouldSerializeDecimalViewsAsStrings() throws Exception {
        QuotationLineVO line = new QuotationLineVO();
        line.setUnitPrice(new BigDecimal("1234567890123.123456"));
        line.setQuantity(new BigDecimal("9876543210123.654321"));
        line.setLineAmount(new BigDecimal("999999999999999.9999"));
        QuotationVO quotation = new QuotationVO();
        quotation.setTotalAmount(new BigDecimal("999999999999999.9999"));
        quotation.setLines(List.of(line));

        JsonNode json = new ObjectMapper().valueToTree(quotation);

        assertThat(json.get("totalAmount").isTextual()).isTrue();
        assertThat(json.at("/lines/0/unitPrice").isTextual()).isTrue();
        assertThat(json.at("/lines/0/quantity").isTextual()).isTrue();
        assertThat(json.at("/lines/0/lineAmount").isTextual()).isTrue();
    }

    /** 门户邀请详情复用的 Procurement 数量快照也必须输出为字符串。 */
    @Test
    void shouldSerializeInvitationQuantityAsString() {
        ProcurementRfqInvitationLine line = new ProcurementRfqInvitationLine();
        line.setQuantity(new BigDecimal("9876543210123.654321"));

        JsonNode json = new ObjectMapper().valueToTree(line);

        assertThat(json.get("quantity").isTextual()).isTrue();
    }

    /** Spring Boot 4 实际 HTTP 使用的 Jackson 3 也必须输出十进制字符串。 */
    @Test
    void shouldSerializeDecimalViewsAsStringsWithJackson3() throws Exception {
        QuotationLineVO line = new QuotationLineVO();
        line.setUnitPrice(new BigDecimal("1234567890123.123456"));
        line.setQuantity(new BigDecimal("9876543210123.654321"));
        line.setLineAmount(new BigDecimal("999999999999999.9999"));
        QuotationVO quotation = new QuotationVO();
        quotation.setTotalAmount(new BigDecimal("999999999999999.9999"));
        quotation.setLines(List.of(line));

        String json = tools.jackson.databind.json.JsonMapper.builder().build()
                .writeValueAsString(quotation);

        assertThat(json).contains("\"totalAmount\":\"999999999999999.9999\"")
                .contains("\"unitPrice\":\"1234567890123.123456\"")
                .contains("\"quantity\":\"9876543210123.654321\"")
                .contains("\"lineAmount\":\"999999999999999.9999\"");
    }

    /** Jackson 3 HTTP 转换器输出邀请数量时也必须保留完整精度。 */
    @Test
    void shouldSerializeInvitationQuantityAsStringWithJackson3() throws Exception {
        ProcurementRfqInvitationLine line = new ProcurementRfqInvitationLine();
        line.setQuantity(new BigDecimal("9876543210123.654321"));

        String json = tools.jackson.databind.json.JsonMapper.builder().build()
                .writeValueAsString(line);

        assertThat(json).contains("\"quantity\":\"9876543210123.654321\"");
    }

    /** Jackson 2 报价单价必须只接受字符串，避免客户端浮点数先丢失精度。 */
    @Test
    void shouldRequireUnitPriceStringWithJackson2() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        QuotationLineRequest line = mapper.readValue(requestLineJson("\"123.456789\""),
                QuotationLineRequest.class);

        assertThat(line.getUnitPrice()).isEqualByComparingTo("123.456789");
        assertThatThrownBy(() -> mapper.readValue(requestLineJson("123.456789"),
                QuotationLineRequest.class)).hasMessageContaining("JSON 字符串");
    }

    /** Jackson 3 报价单价必须只接受字符串，保持实际 HTTP 边界一致。 */
    @Test
    void shouldRequireUnitPriceStringWithJackson3() throws Exception {
        tools.jackson.databind.json.JsonMapper mapper =
                tools.jackson.databind.json.JsonMapper.builder().build();

        QuotationLineRequest line = mapper.readValue(requestLineJson("\"123.456789\""),
                QuotationLineRequest.class);

        assertThat(line.getUnitPrice()).isEqualByComparingTo("123.456789");
        assertThatThrownBy(() -> mapper.readValue(requestLineJson("123.456789"),
                QuotationLineRequest.class)).hasMessageContaining("JSON 字符串");
    }

    private String requestLineJson(String unitPrice) {
        return """
                {
                  "rfqLineId": 1,
                  "unitPrice": %s,
                  "deliveryDays": 7
                }
                """.formatted(unitPrice);
    }
}
