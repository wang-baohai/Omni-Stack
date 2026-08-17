package com.omni.procurement.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 审批路由金额 JSON 精度保护测试。 */
class ApprovalRouteJsonSerializationTest {

    /** Jackson 2 必须将金额输出为字符串。 */
    @Test
    void shouldSerializeAmountsAsStringsWithJackson2() {
        ApprovalRouteViews.RouteVO route = route();

        JsonNode json = new ObjectMapper().valueToTree(route);

        assertThat(json.get("minAmount").isTextual()).isTrue();
        assertThat(json.get("maxAmount").isTextual()).isTrue();
    }

    /** Spring Boot 4 的 Jackson 3 也必须将金额输出为字符串。 */
    @Test
    void shouldSerializeAmountsAsStringsWithJackson3() throws Exception {
        String json = tools.jackson.databind.json.JsonMapper.builder().build()
                .writeValueAsString(route());

        assertThat(json).contains("\"minAmount\":\"123456789012345.1234\"")
                .contains("\"maxAmount\":\"999999999999999.9999\"");
    }

    /** Jackson 2 必须只接受字符串金额并拒绝 JSON number。 */
    @Test
    void shouldRequireAmountStringsWithJackson2() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        ApprovalRouteRequests.CreateRouteRequest request = mapper.readValue(
                createRequestJson("\"123456789012345.1234\"", "\"999999999999999.9999\""),
                ApprovalRouteRequests.CreateRouteRequest.class);

        assertThat(request.getMinAmount()).isEqualByComparingTo("123456789012345.1234");
        assertThat(request.getMaxAmount()).isEqualByComparingTo("999999999999999.9999");
        assertThatThrownBy(() -> mapper.readValue(
                createRequestJson("123456789012345.1234", "\"999999999999999.9999\""),
                ApprovalRouteRequests.CreateRouteRequest.class))
                .hasMessageContaining("JSON 字符串");
    }

    /** Jackson 3 必须只接受字符串金额并拒绝 JSON number。 */
    @Test
    void shouldRequireAmountStringsWithJackson3() throws Exception {
        tools.jackson.databind.json.JsonMapper mapper =
                tools.jackson.databind.json.JsonMapper.builder().build();

        ApprovalRouteRequests.UpdateRouteRequest request = mapper.readValue(
                updateRequestJson("\"0.0000\"", "\"100.0000\""),
                ApprovalRouteRequests.UpdateRouteRequest.class);

        assertThat(request.getMinAmount()).isEqualByComparingTo("0.0000");
        assertThat(request.getMaxAmount()).isEqualByComparingTo("100.0000");
        assertThatThrownBy(() -> mapper.readValue(
                updateRequestJson("\"0.0000\"", "100.0000"),
                ApprovalRouteRequests.UpdateRouteRequest.class))
                .hasMessageContaining("JSON 字符串");
    }

    private ApprovalRouteViews.RouteVO route() {
        ApprovalRouteViews.RouteVO route = new ApprovalRouteViews.RouteVO();
        route.setMinAmount(new BigDecimal("123456789012345.1234"));
        route.setMaxAmount(new BigDecimal("999999999999999.9999"));
        return route;
    }

    private String createRequestJson(String minAmount, String maxAmount) {
        return """
                {
                  "routeCode":"IT_DEFAULT",
                  "categoryCode":"IT_DEVICE",
                  "minAmount":%s,
                  "maxAmount":%s,
                  "modelVersionId":12,
                  "priority":10,
                  "status":"ACTIVE"
                }
                """.formatted(minAmount, maxAmount);
    }

    private String updateRequestJson(String minAmount, String maxAmount) {
        return """
                {
                  "version":1,
                  "categoryCode":"IT_DEVICE",
                  "minAmount":%s,
                  "maxAmount":%s,
                  "modelVersionId":12,
                  "priority":10,
                  "status":"ACTIVE"
                }
                """.formatted(minAmount, maxAmount);
    }
}
