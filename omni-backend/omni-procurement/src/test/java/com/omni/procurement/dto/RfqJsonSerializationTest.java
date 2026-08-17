package com.omni.procurement.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RFQ 内部行数量 JSON 字符串契约测试。 */
class RfqJsonSerializationTest {

    /** Jackson 2 必须将 RFQ 数量输出为字符串。 */
    @Test
    void shouldSerializeInvitationQuantityAsStringWithJackson2() {
        JsonNode json = new ObjectMapper().valueToTree(line());

        assertThat(json.get("quantity").isTextual()).isTrue();
        assertThat(json.get("quantity").asText()).isEqualTo("2.500000");
    }

    /** Spring Boot 4 的 Jackson 3 必须保持固定小数位字符串。 */
    @Test
    void shouldSerializeInvitationQuantityAsStringWithJackson3() throws Exception {
        String json = tools.jackson.databind.json.JsonMapper.builder().build()
                .writeValueAsString(line());

        assertThat(json).contains("\"quantity\":\"2.500000\"");
    }

    /** 比价接口中的报价金额在两套 Jackson 下都必须保持字符串精度。 */
    @Test
    void shouldSerializeComparisonAmountsAsStrings() throws Exception {
        PurchaseOrderContracts.QuotationSnapshot quotation = quotation();

        JsonNode jackson2 = new ObjectMapper().valueToTree(quotation);
        String jackson3 = tools.jackson.databind.json.JsonMapper.builder().build()
                .writeValueAsString(quotation);

        assertThat(jackson2.get("totalAmount").isTextual()).isTrue();
        assertThat(jackson2.get("lines").get(0).get("unitPrice").asText())
                .isEqualTo("88.120000");
        assertThat(jackson3).contains("\"totalAmount\":\"176.2400\"")
                .contains("\"unitPrice\":\"88.120000\"")
                .contains("\"quantity\":\"2.000000\"");
    }

    /** Jackson 2 必须只接受报价事件中的十进制字符串。 */
    @Test
    void shouldRequireQuotationEventAmountStringWithJackson2() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        RfqContracts.QuotationSubmittedPayload payload = mapper.readValue(
                "{\"totalAmount\":\"176.2400\"}",
                RfqContracts.QuotationSubmittedPayload.class);

        assertThat(payload.getTotalAmount()).isEqualByComparingTo("176.2400");
        assertThatThrownBy(() -> mapper.readValue(
                "{\"totalAmount\":176.2400}",
                RfqContracts.QuotationSubmittedPayload.class))
                .hasMessageContaining("JSON 字符串");
    }

    /** Jackson 3 必须只接受报价事件中的十进制字符串。 */
    @Test
    void shouldRequireQuotationEventAmountStringWithJackson3() throws Exception {
        tools.jackson.databind.json.JsonMapper mapper =
                tools.jackson.databind.json.JsonMapper.builder().build();

        RfqContracts.QuotationSubmittedPayload payload = mapper.readValue(
                "{\"totalAmount\":\"176.2400\"}",
                RfqContracts.QuotationSubmittedPayload.class);

        assertThat(payload.getTotalAmount()).isEqualByComparingTo("176.2400");
        assertThatThrownBy(() -> mapper.readValue(
                "{\"totalAmount\":176.2400}",
                RfqContracts.QuotationSubmittedPayload.class))
                .hasMessageContaining("JSON 字符串");
    }

    private RfqViews.InternalInvitationLine line() {
        RfqViews.InternalInvitationLine line = new RfqViews.InternalInvitationLine();
        line.setRfqLineId(91L);
        line.setQuantity(new BigDecimal("2.500000"));
        return line;
    }

    private PurchaseOrderContracts.QuotationSnapshot quotation() {
        PurchaseOrderContracts.QuotationLineSnapshot line =
                new PurchaseOrderContracts.QuotationLineSnapshot();
        line.setUnitPrice(new BigDecimal("88.120000"));
        line.setQuantity(new BigDecimal("2.000000"));
        line.setLineAmount(new BigDecimal("176.2400"));
        PurchaseOrderContracts.QuotationSnapshot quotation =
                new PurchaseOrderContracts.QuotationSnapshot();
        quotation.setTotalAmount(new BigDecimal("176.2400"));
        quotation.setLines(List.of(line));
        return quotation;
    }
}
