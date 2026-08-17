package com.omni.asset.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 资产金额 JSON 字符串精度契约测试。 */
class AssetJsonSerializationTest {

    /** Jackson 2 必须把资产金额输出为字符串。 */
    @Test
    void shouldSerializePurchaseAmountAsStringWithJackson2() {
        JsonNode json = new ObjectMapper().valueToTree(assetView());

        assertThat(json.get("purchaseAmount").isTextual()).isTrue();
        assertThat(json.get("purchaseAmount").asText()).isEqualTo("1234567890123456.78");
    }

    /** Jackson 3 必须把资产金额输出为精确字符串。 */
    @Test
    void shouldSerializePurchaseAmountAsStringWithJackson3() throws Exception {
        String json = tools.jackson.databind.json.JsonMapper.builder().build()
                .writeValueAsString(assetView());

        assertThat(json).contains("\"purchaseAmount\":\"1234567890123456.78\"");
    }

    /** Jackson 2 只接受 JSON 字符串金额。 */
    @Test
    void shouldRejectPurchaseAmountNumberWithJackson2() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        AssetRequests.CreateAssetRequest request = mapper.readValue(
                createJson("\"1200.00\""), AssetRequests.CreateAssetRequest.class);

        assertThat(request.getPurchaseAmount()).isEqualByComparingTo("1200.00");
        assertThatThrownBy(() -> mapper.readValue(
                createJson("1200.00"), AssetRequests.CreateAssetRequest.class))
                .hasMessageContaining("JSON 字符串");
    }

    /** Jackson 3 只接受 JSON 字符串金额。 */
    @Test
    void shouldRejectPurchaseAmountNumberWithJackson3() throws Exception {
        tools.jackson.databind.json.JsonMapper mapper =
                tools.jackson.databind.json.JsonMapper.builder().build();

        AssetRequests.CreateAssetRequest request = mapper.readValue(
                createJson("\"1200.00\""), AssetRequests.CreateAssetRequest.class);

        assertThat(request.getPurchaseAmount()).isEqualByComparingTo("1200.00");
        assertThatThrownBy(() -> mapper.readValue(
                createJson("1200.00"), AssetRequests.CreateAssetRequest.class))
                .hasMessageContaining("JSON 字符串");
    }

    /** Jackson 2 必须把处置残值输出为字符串并拒绝 JSON number 输入。 */
    @Test
    void shouldKeepDisposalResidualValueAsDecimalStringWithJackson2() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AssetOperationViews.DisposalVO view = new AssetOperationViews.DisposalVO();
        view.setResidualValue(new BigDecimal("9999999999999999.99"));

        JsonNode json = mapper.valueToTree(view);
        AssetOperationRequests.CreateDisposalRequest request = mapper.readValue(
                disposalJson("\"1200.50\""), AssetOperationRequests.CreateDisposalRequest.class);

        assertThat(json.get("residualValue").isTextual()).isTrue();
        assertThat(json.get("residualValue").asText()).isEqualTo("9999999999999999.99");
        assertThat(request.getResidualValue()).isEqualByComparingTo("1200.50");
        assertThatThrownBy(() -> mapper.readValue(
                disposalJson("1200.50"), AssetOperationRequests.CreateDisposalRequest.class))
                .hasMessageContaining("JSON 字符串");
    }

    /** Jackson 3 必须把处置残值输出为字符串并拒绝 JSON number 输入。 */
    @Test
    void shouldKeepDisposalResidualValueAsDecimalStringWithJackson3() throws Exception {
        tools.jackson.databind.json.JsonMapper mapper =
                tools.jackson.databind.json.JsonMapper.builder().build();
        AssetOperationViews.DisposalVO view = new AssetOperationViews.DisposalVO();
        view.setResidualValue(new BigDecimal("1200.50"));

        String json = mapper.writeValueAsString(view);
        AssetOperationRequests.CreateDisposalRequest request = mapper.readValue(
                disposalJson("\"1200.50\""), AssetOperationRequests.CreateDisposalRequest.class);

        assertThat(json).contains("\"residualValue\":\"1200.50\"");
        assertThat(request.getResidualValue()).isEqualByComparingTo("1200.50");
        assertThatThrownBy(() -> mapper.readValue(
                disposalJson("1200.50"), AssetOperationRequests.CreateDisposalRequest.class))
                .hasMessageContaining("JSON 字符串");
    }

    /** Jackson 2 必须拒绝收货事件使用 JSON number 传输数量或金额。 */
    @Test
    void shouldRequireReceiptDecimalStringsWithJackson2() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        ProcurementAssetContracts.GoodsReceiptEvent event = mapper.readValue(
                receiptEventJson("\"2.000000\"", "\"6000.000000\"", "\"12000.0000\""),
                ProcurementAssetContracts.GoodsReceiptEvent.class);

        assertThat(event.getPayload().getLines().getFirst().getReceivedQuantity())
                .isEqualByComparingTo("2.000000");
        assertThatThrownBy(() -> mapper.readValue(
                receiptEventJson("2.000000", "\"6000.000000\"", "\"12000.0000\""),
                ProcurementAssetContracts.GoodsReceiptEvent.class))
                .hasMessageContaining("JSON 字符串");
    }

    /** Jackson 3 必须拒绝收货事件使用 JSON number 传输数量或金额。 */
    @Test
    void shouldRequireReceiptDecimalStringsWithJackson3() throws Exception {
        tools.jackson.databind.json.JsonMapper mapper =
                tools.jackson.databind.json.JsonMapper.builder().build();

        ProcurementAssetContracts.GoodsReceiptEvent event = mapper.readValue(
                receiptEventJson("\"2.000000\"", "\"6000.000000\"", "\"12000.0000\""),
                ProcurementAssetContracts.GoodsReceiptEvent.class);

        assertThat(event.getPayload().getLines().getFirst().getUnitPrice())
                .isEqualByComparingTo("6000.000000");
        assertThatThrownBy(() -> mapper.readValue(
                receiptEventJson("\"2.000000\"", "6000.000000", "\"12000.0000\""),
                ProcurementAssetContracts.GoodsReceiptEvent.class))
                .hasMessageContaining("JSON 字符串");
    }

    private AssetViews.AssetVO assetView() {
        AssetViews.AssetVO view = new AssetViews.AssetVO();
        view.setPurchaseAmount(new BigDecimal("1234567890123456.78"));
        return view;
    }

    private String createJson(String amount) {
        return """
                {
                  "name":"商务笔记本",
                  "categoryCode":"IT_DEVICE",
                  "purchaseAmount":%s,
                  "currencyCode":"CNY",
                  "ownerUserId":7,
                  "ownerUnitId":12
                }
                """.formatted(amount);
    }

    private String disposalJson(String residualValue) {
        return """
                {
                  "assetId":10,
                  "disposalType":"SCRAP",
                  "reason":"达到使用年限",
                  "residualValue":%s,
                  "modelVersionId":42
                }
                """.formatted(residualValue);
    }

    private String receiptEventJson(String quantity, String unitPrice, String totalPrice) {
        return """
                {
                  "eventId":"c30a93f8-9e15-4f6d-8bc1-0066059ffba2",
                  "eventType":"procurement.goods-receipt.confirmed.v1",
                  "tenantId":1,
                  "payload":{
                    "lines":[{
                      "receivedQuantity":%s,
                      "unitPrice":%s,
                      "totalPrice":%s
                    }]
                  }
                }
                """.formatted(quantity, unitPrice, totalPrice);
    }
}
