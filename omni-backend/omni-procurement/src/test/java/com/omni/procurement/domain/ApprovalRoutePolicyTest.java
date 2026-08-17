package com.omni.procurement.domain;

import com.omni.common.core.result.BusinessException;
import com.omni.procurement.entity.ProcApprovalRoute;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 审批路由半开区间和唯一选择测试。 */
class ApprovalRoutePolicyTest {

    /** 相邻半开区间不重叠，真正交叉才算冲突。 */
    @Test
    void shouldUseHalfOpenIntervalsForOverlap() {
        assertThat(ApprovalRoutePolicy.overlaps(decimal("0"), decimal("100"),
                decimal("100"), decimal("200"))).isFalse();
        assertThat(ApprovalRoutePolicy.overlaps(decimal("0"), decimal("100.0001"),
                decimal("100"), decimal("200"))).isTrue();
        assertThat(ApprovalRoutePolicy.overlaps(decimal("100"), null,
                decimal("999"), null)).isTrue();
    }

    /** 金额等于上界时应进入下一段。 */
    @Test
    void shouldMatchNextRouteAtUpperBoundary() {
        ProcApprovalRoute lower = route(1L, "IT_DEVICE", "0", "100");
        ProcApprovalRoute upper = route(2L, "IT_DEVICE", "100", null);

        ProcApprovalRoute selected = ApprovalRoutePolicy.select(
                "it_device", decimal("100"), List.of(lower, upper));

        assertThat(selected.getId()).isEqualTo(2L);
    }

    /** 精确品类匹配优先于通配路由。 */
    @Test
    void shouldPreferExactCategoryOverWildcard() {
        ProcApprovalRoute wildcard = route(1L, "*", "0", null);
        ProcApprovalRoute exact = route(2L, "OFFICE_SUPPLY", "0", null);

        ProcApprovalRoute selected = ApprovalRoutePolicy.select(
                "OFFICE_SUPPLY", decimal("50"), List.of(wildcard, exact));

        assertThat(selected.getId()).isEqualTo(2L);
    }

    /** 零匹配和多匹配都必须报告配置冲突。 */
    @Test
    void shouldRejectZeroOrMultipleMatches() {
        assertThatThrownBy(() -> ApprovalRoutePolicy.select("OTHER", decimal("10"), List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);

        List<ProcApprovalRoute> duplicate = List.of(
                route(1L, "OTHER", "0", "100"),
                route(2L, "OTHER", "0", "100"));
        assertThatThrownBy(() -> ApprovalRoutePolicy.select("OTHER", decimal("10"), duplicate))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
    }

    /** 模型版本必须为正整数且金额必须符合存储精度。 */
    @Test
    void shouldValidateDefinition() {
        assertThatThrownBy(() -> ApprovalRoutePolicy.validateDefinition(
                decimal("0"), decimal("100"), 0L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(400);
        assertThatThrownBy(() -> ApprovalRoutePolicy.validateDefinition(
                decimal("0.00001"), decimal("100"), 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(400);
    }

    private ProcApprovalRoute route(Long id, String categoryCode, String minAmount, String maxAmount) {
        ProcApprovalRoute route = new ProcApprovalRoute();
        route.setId(id);
        route.setCategoryCode(categoryCode);
        route.setMinAmount(decimal(minAmount));
        route.setMaxAmount(maxAmount == null ? null : decimal(maxAmount));
        route.setStatus(ApprovalRoutePolicy.ACTIVE);
        return route;
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
