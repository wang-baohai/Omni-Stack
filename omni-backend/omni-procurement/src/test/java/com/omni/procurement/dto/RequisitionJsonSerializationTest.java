package com.omni.procurement.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 请购金额和数量 JSON 精度保护测试。 */
class RequisitionJsonSerializationTest {

    /** Jackson 2 必须把所有高精度十进制字段输出为字符串。 */
    @Test
    void shouldSerializeAllDecimalsAsStringsWithJackson2() {
        JsonNode json = new ObjectMapper().valueToTree(detail());

        assertThat(json.get("totalAmount").isTextual()).isTrue();
        JsonNode line = json.get("lines").get(0);
        assertThat(line.get("quantity").isTextual()).isTrue();
        assertThat(line.get("estimatedUnitPrice").isTextual()).isTrue();
        assertThat(line.get("estimatedTotalPrice").isTextual()).isTrue();
    }

    /** Spring Boot 4 的 Jackson 3 必须保持固定小数位字符串。 */
    @Test
    void shouldSerializeAllDecimalsAsStringsWithJackson3() throws Exception {
        String json = tools.jackson.databind.json.JsonMapper.builder().build()
                .writeValueAsString(detail());

        assertThat(json).contains("\"totalAmount\":\"123456789012345.1234\"")
                .contains("\"quantity\":\"2.000000\"")
                .contains("\"estimatedUnitPrice\":\"10.123456\"")
                .contains("\"estimatedTotalPrice\":\"20.2469\"");
    }

    /** Jackson 2 必须只接受字符串数量和单价。 */
    @Test
    void shouldRequireLineDecimalStringsWithJackson2() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        RequisitionRequests.LineInput line = mapper.readValue(
                lineJson("\"2.000000\"", "\"10.123456\""), RequisitionRequests.LineInput.class);

        assertThat(line.getQuantity()).isEqualByComparingTo("2.000000");
        assertThat(line.getEstimatedUnitPrice()).isEqualByComparingTo("10.123456");
        assertThatThrownBy(() -> mapper.readValue(
                lineJson("2.000000", "\"10.123456\""), RequisitionRequests.LineInput.class))
                .hasMessageContaining("JSON 字符串");
        assertThatThrownBy(() -> mapper.readValue(
                lineJson("\"2.000000\"", "10.123456"), RequisitionRequests.LineInput.class))
                .hasMessageContaining("JSON 字符串");
    }

    /** Jackson 3 必须只接受字符串数量和单价。 */
    @Test
    void shouldRequireLineDecimalStringsWithJackson3() throws Exception {
        tools.jackson.databind.json.JsonMapper mapper =
                tools.jackson.databind.json.JsonMapper.builder().build();

        RequisitionRequests.LineInput line = mapper.readValue(
                lineJson("\"2.000000\"", "\"10.123456\""), RequisitionRequests.LineInput.class);

        assertThat(line.getQuantity()).isEqualByComparingTo("2.000000");
        assertThat(line.getEstimatedUnitPrice()).isEqualByComparingTo("10.123456");
        assertThatThrownBy(() -> mapper.readValue(
                lineJson("2.000000", "\"10.123456\""), RequisitionRequests.LineInput.class))
                .hasMessageContaining("JSON 字符串");
        assertThatThrownBy(() -> mapper.readValue(
                lineJson("\"2.000000\"", "10.123456"), RequisitionRequests.LineInput.class))
                .hasMessageContaining("JSON 字符串");
    }

    private RequisitionViews.Detail detail() {
        RequisitionViews.Detail detail = new RequisitionViews.Detail();
        detail.setTotalAmount(new BigDecimal("123456789012345.1234"));
        RequisitionViews.Line line = new RequisitionViews.Line();
        line.setQuantity(new BigDecimal("2.000000"));
        line.setEstimatedUnitPrice(new BigDecimal("10.123456"));
        line.setEstimatedTotalPrice(new BigDecimal("20.2469"));
        detail.setLines(List.of(line));
        return detail;
    }

    private String lineJson(String quantity, String estimatedUnitPrice) {
        return """
                {
                  "materialId":301,
                  "quantity":%s,
                  "estimatedUnitPrice":%s,
                  "remark":"测试"
                }
                """.formatted(quantity, estimatedUnitPrice);
    }
}
