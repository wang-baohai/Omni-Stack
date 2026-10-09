package com.omni.procurement.domain;

import com.omni.procurement.dto.ApprovalRouteInsightViews;
import com.omni.procurement.entity.ProcApprovalRoute;
import com.omni.procurement.entity.ProcMaterialCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 审批规则覆盖区间归并测试。 */
class ApprovalRouteCoverageAnalyzerTest {

    private final ApprovalRouteCoverageAnalyzer analyzer = new ApprovalRouteCoverageAnalyzer();

    /** 精确规则断档必须由默认规则补齐并保持精确规则优先。 */
    @Test
    void shouldFillExactGapsWithDefaultRule() {
        List<ProcApprovalRoute> routes = List.of(
                route(1L, "*", "0", null, 91L),
                route(2L, "IT_DEVICE", "0", "100", 92L),
                route(3L, "IT_DEVICE", "200", "300", 93L));

        ApprovalRouteInsightViews.CategoryCoverage category = analyzer.analyze(
                List.of(category()), routes,
                Map.of(91L, "AVAILABLE", 92L, "AVAILABLE", 93L, "AVAILABLE"), true)
                .getCategories().getFirst();

        assertThat(category.isComplete()).isTrue();
        assertThat(category.getSegments()).extracting(
                        ApprovalRouteInsightViews.CoverageSegment::getSource)
                .containsExactly("EXACT", "DEFAULT", "EXACT", "DEFAULT");
        assertThat(category.getSegments().get(1).getMinAmount()).isEqualByComparingTo("100");
        assertThat(category.getSegments().get(1).getMaxAmount()).isEqualByComparingTo("200");
        assertThat(category.getSegments().getLast().getMaxAmount()).isNull();
    }

    /** 历史脏数据重复命中必须明确报告，不能按 priority 静默选择。 */
    @Test
    void shouldReportAmbiguousDirtyRanges() {
        List<ProcApprovalRoute> routes = List.of(
                route(1L, "IT_DEVICE", "0", "100.0000", 91L),
                route(2L, "IT_DEVICE", "0", "100.0000", 92L));

        ApprovalRouteInsightViews.CategoryCoverage category = analyzer.analyze(
                List.of(category()), routes,
                Map.of(91L, "AVAILABLE", 92L, "AVAILABLE"), true)
                .getCategories().getFirst();

        assertThat(category.isComplete()).isFalse();
        assertThat(category.getSegments()).extracting(
                        ApprovalRouteInsightViews.CoverageSegment::getOutcome)
                .containsExactly("AMBIGUOUS", "GAP");
        assertThat(category.getSegments().getFirst().getRouteIds()).containsExactly(1L, 2L);
    }

    /** 绑定模型失效时区间仍可诊断，但不得被标记为完整有效覆盖。 */
    @Test
    void shouldMarkInvalidWorkflowModel() {
        ProcApprovalRoute route = route(1L, "*", "0", null, 91L);

        ApprovalRouteInsightViews.CoverageReport report = analyzer.analyze(
                List.of(category()), List.of(route), Map.of(91L, "LEGACY_CATEGORY"), true);

        assertThat(report.getInvalidModelRouteIds()).containsExactly(1L);
        assertThat(report.getCategories().getFirst().isComplete()).isFalse();
        assertThat(report.getCategories().getFirst().getSegments().getFirst()
                .getWorkflowAvailability()).isEqualTo("LEGACY_CATEGORY");
    }

    private ProcMaterialCategory category() {
        ProcMaterialCategory category = new ProcMaterialCategory();
        category.setId(10L);
        category.setCategoryCode("IT_DEVICE");
        category.setCategoryName("IT 设备");
        category.setSort(1);
        category.setStatus(1);
        return category;
    }

    private ProcApprovalRoute route(Long id, String categoryCode, String min,
                                    String max, Long modelVersionId) {
        ProcApprovalRoute route = new ProcApprovalRoute();
        route.setId(id);
        route.setRouteName("规则 " + id);
        route.setCategoryCode(categoryCode);
        route.setMinAmount(new BigDecimal(min));
        route.setMaxAmount(max == null ? null : new BigDecimal(max));
        route.setModelVersionId(modelVersionId);
        route.setStatus(ApprovalRoutePolicy.ACTIVE);
        return route;
    }
}
