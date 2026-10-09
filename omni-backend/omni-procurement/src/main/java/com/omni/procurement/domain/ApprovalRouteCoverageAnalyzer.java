package com.omni.procurement.domain;

import com.omni.procurement.dto.ApprovalRouteInsightViews;
import com.omni.procurement.entity.ProcApprovalRoute;
import com.omni.procurement.entity.ProcMaterialCategory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 按真实解析优先级计算请购审批规则的金额覆盖。
 *
 * @author Omni-Stack Team
 */
@Component
public class ApprovalRouteCoverageAnalyzer {

    /**
     * 计算从 0 到无穷的品类覆盖报告。
     *
     * @param categories 当前租户有效品类
     * @param allRoutes 当前租户全部规则
     * @param modelAvailability 模型版本可用状态
     * @param workflowAvailable Workflow 服务是否可访问
     * @return 覆盖报告
     */
    public ApprovalRouteInsightViews.CoverageReport analyze(
            List<ProcMaterialCategory> categories,
            List<ProcApprovalRoute> allRoutes,
            Map<Long, String> modelAvailability,
            boolean workflowAvailable) {
        List<ProcApprovalRoute> activeRoutes = safe(allRoutes).stream()
                .filter(route -> ApprovalRoutePolicy.ACTIVE.equals(route.getStatus()))
                .toList();
        List<Long> invalidModelRouteIds = activeRoutes.stream()
                .filter(route -> !"AVAILABLE".equals(modelAvailability.get(route.getModelVersionId())))
                .map(ProcApprovalRoute::getId)
                .sorted()
                .toList();
        List<ApprovalRouteInsightViews.CategoryCoverage> coverage = safe(categories).stream()
                .filter(category -> Integer.valueOf(1).equals(category.getStatus()))
                .sorted(Comparator.comparing(ProcMaterialCategory::getSort,
                                Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ProcMaterialCategory::getCategoryCode))
                .map(category -> analyzeCategory(category, activeRoutes, modelAvailability))
                .toList();
        return ApprovalRouteInsightViews.CoverageReport.builder()
                .generatedAt(LocalDateTime.now())
                .workflowAvailability(workflowAvailable ? "AVAILABLE" : "UNAVAILABLE")
                .allRulesInactive(activeRoutes.isEmpty())
                .noDefaultRule(activeRoutes.stream().noneMatch(route ->
                        ApprovalRoutePolicy.WILDCARD_CATEGORY.equals(route.getCategoryCode())))
                .invalidModelRouteIds(invalidModelRouteIds)
                .categories(coverage)
                .build();
    }

    private ApprovalRouteInsightViews.CategoryCoverage analyzeCategory(
            ProcMaterialCategory category,
            List<ProcApprovalRoute> routes,
            Map<Long, String> availability) {
        List<BigDecimal> boundaries = boundaries(category.getCategoryCode(), routes);
        List<ApprovalRouteInsightViews.CoverageSegment> segments = new ArrayList<>();
        for (int index = 0; index < boundaries.size(); index++) {
            BigDecimal min = boundaries.get(index);
            BigDecimal max = index + 1 < boundaries.size() ? boundaries.get(index + 1) : null;
            ApprovalRouteResolver.Evaluation evaluation = ApprovalRouteResolver.evaluateCandidates(
                    category.getCategoryCode(), min, routes);
            appendMerged(segments, segment(min, max, evaluation, availability));
        }
        List<String> issues = new ArrayList<>();
        long gaps = segments.stream().filter(segment -> "GAP".equals(segment.getOutcome())).count();
        long ambiguous = segments.stream().filter(segment -> "AMBIGUOUS".equals(segment.getOutcome())).count();
        boolean invalidWorkflow = segments.stream().anyMatch(segment ->
                "COVERED".equals(segment.getOutcome())
                        && !"AVAILABLE".equals(segment.getWorkflowAvailability()));
        if (gaps > 0) {
            issues.add("存在 " + gaps + " 个未配置审批规则的金额区间");
        }
        if (ambiguous > 0) {
            issues.add("存在 " + ambiguous + " 个同时命中多条规则的金额区间");
        }
        if (invalidWorkflow) {
            issues.add("存在绑定流程失效、非当前版本或遗留分类的规则");
        }
        return ApprovalRouteInsightViews.CategoryCoverage.builder()
                .categoryCode(category.getCategoryCode())
                .categoryName(category.getCategoryName())
                .complete(gaps == 0 && ambiguous == 0 && !invalidWorkflow)
                .segments(segments)
                .issues(issues)
                .build();
    }

    private List<BigDecimal> boundaries(String categoryCode, List<ProcApprovalRoute> routes) {
        Set<BigDecimal> values = new LinkedHashSet<>();
        values.add(BigDecimal.ZERO);
        routes.stream()
                .filter(route -> categoryCode.equals(route.getCategoryCode())
                        || ApprovalRoutePolicy.WILDCARD_CATEGORY.equals(route.getCategoryCode()))
                .forEach(route -> {
                    if (route.getMinAmount() != null && route.getMinAmount().signum() >= 0) {
                        values.add(route.getMinAmount());
                    }
                    if (route.getMaxAmount() != null && route.getMaxAmount().signum() >= 0) {
                        values.add(route.getMaxAmount());
                    }
                });
        return values.stream().sorted().toList();
    }

    private ApprovalRouteInsightViews.CoverageSegment segment(
            BigDecimal min,
            BigDecimal max,
            ApprovalRouteResolver.Evaluation evaluation,
            Map<Long, String> availability) {
        if (evaluation.outcome() == ApprovalRouteResolver.Outcome.NO_MATCH) {
            return ApprovalRouteInsightViews.CoverageSegment.builder()
                    .minAmount(min).maxAmount(max).outcome("GAP").source("NONE")
                    .routeIds(List.of()).workflowAvailability("NOT_APPLICABLE").build();
        }
        List<Long> routeIds = evaluation.matches().stream()
                .map(ProcApprovalRoute::getId).sorted().toList();
        if (evaluation.outcome() == ApprovalRouteResolver.Outcome.AMBIGUOUS) {
            return ApprovalRouteInsightViews.CoverageSegment.builder()
                    .minAmount(min).maxAmount(max).outcome("AMBIGUOUS")
                    .source(evaluation.defaultRule() ? "DEFAULT" : "EXACT")
                    .routeIds(routeIds).workflowAvailability("CONFLICT").build();
        }
        ProcApprovalRoute route = evaluation.route();
        return ApprovalRouteInsightViews.CoverageSegment.builder()
                .minAmount(min).maxAmount(max).outcome("COVERED")
                .source(evaluation.defaultRule() ? "DEFAULT" : "EXACT")
                .routeIds(routeIds).routeName(route.getRouteName())
                .workflowAvailability(availability.getOrDefault(route.getModelVersionId(), "NOT_FOUND"))
                .build();
    }

    private void appendMerged(List<ApprovalRouteInsightViews.CoverageSegment> segments,
                              ApprovalRouteInsightViews.CoverageSegment candidate) {
        if (segments.isEmpty()) {
            segments.add(candidate);
            return;
        }
        ApprovalRouteInsightViews.CoverageSegment previous = segments.getLast();
        if (Objects.equals(previous.getMaxAmount(), candidate.getMinAmount())
                && Objects.equals(previous.getOutcome(), candidate.getOutcome())
                && Objects.equals(previous.getSource(), candidate.getSource())
                && Objects.equals(previous.getRouteIds(), candidate.getRouteIds())
                && Objects.equals(previous.getWorkflowAvailability(), candidate.getWorkflowAvailability())) {
            previous.setMaxAmount(candidate.getMaxAmount());
            return;
        }
        segments.add(candidate);
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
