package com.omni.procurement.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 收货数量 JSON 字符串精度契约测试。 */
class GoodsReceiptJsonSerializationTest {

    /** Jackson 2 必须将收货响应中的高精度数量输出为字符串。 */
    @Test
    void shouldSerializeReceiptQuantitiesAsStringsWithJackson2() {
        JsonNode json = new ObjectMapper().valueToTree(receiptLine());

        assertThat(json.get("orderedQuantity").isTextual()).isTrue();
        assertThat(json.get("receivedQuantity").isTextual()).isTrue();
    }

    /** Spring Boot 4 的 Jackson 3 必须保持固定小数位字符串。 */
    @Test
    void shouldSerializeReceiptQuantitiesAsStringsWithJackson3() throws Exception {
        String json = tools.jackson.databind.json.JsonMapper.builder().build()
                .writeValueAsString(receiptLine());

        assertThat(json).contains("\"orderedQuantity\":\"2.000000\"")
                .contains("\"receivedQuantity\":\"1.250000\"");
    }

    /** Jackson 2 必须接受字符串收货数量并拒绝 JSON number。 */
    @Test
    void shouldRequireReceivedQuantityStringWithJackson2() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        GoodsReceiptRequests.LineInput line = mapper.readValue(
                lineJson("\"1.250000\""), GoodsReceiptRequests.LineInput.class);

        assertThat(line.getReceivedQuantity()).isEqualByComparingTo("1.250000");
        assertThatThrownBy(() -> mapper.readValue(
                lineJson("1.250000"), GoodsReceiptRequests.LineInput.class))
                .hasMessageContaining("JSON 字符串");
    }

    /** Jackson 3 必须接受字符串收货数量并拒绝 JSON number。 */
    @Test
    void shouldRequireReceivedQuantityStringWithJackson3() throws Exception {
        tools.jackson.databind.json.JsonMapper mapper =
                tools.jackson.databind.json.JsonMapper.builder().build();

        GoodsReceiptRequests.LineInput line = mapper.readValue(
                lineJson("\"1.250000\""), GoodsReceiptRequests.LineInput.class);

        assertThat(line.getReceivedQuantity()).isEqualByComparingTo("1.250000");
        assertThatThrownBy(() -> mapper.readValue(
                lineJson("1.250000"), GoodsReceiptRequests.LineInput.class))
                .hasMessageContaining("JSON 字符串");
    }

    private GoodsReceiptViews.Line receiptLine() {
        GoodsReceiptViews.Line line = new GoodsReceiptViews.Line();
        line.setOrderedQuantity(new BigDecimal("2.000000"));
        line.setReceivedQuantity(new BigDecimal("1.250000"));
        return line;
    }

    private String lineJson(String receivedQuantity) {
        return """
                {
                  "poLineId":811,
                  "receivedQuantity":%s,
                  "qualityStatus":"PASS"
                }
                """.formatted(receivedQuantity);
    }
}
