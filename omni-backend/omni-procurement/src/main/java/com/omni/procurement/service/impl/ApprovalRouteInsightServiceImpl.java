package com.omni.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import com.omni.procurement.client.WorkflowInternalClient;
import com.omni.procurement.domain.ApprovalRouteCoverageAnalyzer;
import com.omni.procurement.domain.ApprovalRoutePolicy;
import com.omni.procurement.domain.ApprovalRouteResolver;
import com.omni.procurement.dto.ApprovalRouteInsightRequests;
import com.omni.procurement.dto.ApprovalRouteInsightViews;
import com.omni.procurement.dto.WorkflowContracts;
import com.omni.procurement.entity.ProcApprovalRoute;
import com.omni.procurement.entity.ProcMaterialCategory;
import com.omni.procurement.mapper.ProcApprovalRouteMapper;
import com.omni.procurement.mapper.ProcMaterialCategoryMapper;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.procurement.service.ApprovalRouteInsightService;
import com.omni.procurement.service.ProcTenantInitializer;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 请购审批规则业务外观服务实现。
 *
 * @author Omni-Stack Team
 */
@Service
@RequiredArgsConstructor
public class ApprovalRouteInsightServiceImpl implements ApprovalRouteInsightService {

    private static final String WORKFLOW_CATEGORY = "purchase";
    private static final int RESOLVE_BATCH_SIZE = 200;

    private final ProcTenantInitializer tenantInitializer;
    private final ProcApprovalRouteMapper routeMapper;
    private final ProcMaterialCategoryMapper categoryMapper;
    private final WorkflowInternalClient workflowClient;
    private final ApprovalRouteResolver routeResolver;
    private final ApprovalRouteCoverageAnalyzer coverageAnalyzer;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<ApprovalRouteInsightViews.WorkflowOption> workflowOptions() {
        tenantInitializer.ensureInitialized();
        Long tenantId = ServiceIdentityContext.requireTenantId();
        try {
            R<List<WorkflowContracts.ModelVersionResponse>> response =
                    workflowClient.listPublishedModelVersions(tenantId, WORKFLOW_CATEGORY);
            if (response == null || response.getCode() != 200 || response.getData() == null) {
                throw new BusinessException(503, "Workflow 返回了无效的审批流程选项响应");
            }
            return response.getData().stream()
                    .filter(this::isAvailablePurchaseModel)
                    .map(this::toOption)
                    .toList();
        } catch (FeignException exception) {
            throw new BusinessException(503, "Workflow 审批流程选项服务暂时不可用");
        }
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public ApprovalRouteInsightViews.MatchPreview matchPreview(
            ApprovalRouteInsightRequests.MatchPreviewRequest request) {
        tenantInitializer.ensureInitialized();
        String categoryCode = ApprovalRoutePolicy.normalizeCategoryCode(request.getCategoryCode());
        ApprovalRouteResolver.Evaluation evaluation =
                routeResolver.evaluate(categoryCode, request.getTotalAmount());
        if (evaluation.outcome() == ApprovalRouteResolver.Outcome.NO_MATCH) {
            return ApprovalRouteInsightViews.MatchPreview.builder()
                    .outcome("NO_MATCH").categoryCode(categoryCode)
                    .actionMessage("当前品类和金额没有可用审批规则，请先补齐覆盖区间。")
                    .conflictingRouteIds(List.of()).build();
        }
        if (evaluation.outcome() == ApprovalRouteResolver.Outcome.AMBIGUOUS) {
            return ApprovalRouteInsightViews.MatchPreview.builder()
                    .outcome("AMBIGUOUS").categoryCode(categoryCode)
                    .effectiveCategoryCode(evaluation.effectiveCategoryCode())
                    .defaultRule(evaluation.defaultRule())
                    .actionMessage("当前输入同时命中多条规则，请先消除重叠区间。")
                    .conflictingRouteIds(evaluation.matches().stream()
                            .map(ProcApprovalRoute::getId).sorted().toList())
                    .build();
        }
        return matchedPreview(ServiceIdentityContext.requireTenantId(), categoryCode, evaluation);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public ApprovalRouteInsightViews.CoverageReport coverage() {
        tenantInitializer.ensureInitialized();
        return analyze(loadRoutes(), loadActiveCategories());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public ApprovalRouteInsightViews.ImpactReport impact(Long routeId) {
        tenantInitializer.ensureInitialized();
        if (routeId == null || routeId <= 0) {
            throw new BusinessException(400, "审批规则 ID 必须为正整数");
        }
        List<ProcApprovalRoute> routes = loadRoutes();
        ProcApprovalRoute target = routes.stream()
                .filter(route -> routeId.equals(route.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "审批规则不存在"));
        List<ProcMaterialCategory> categories = loadActiveCategories();
        ApprovalRouteInsightViews.CoverageReport before = analyze(routes, categories);
        ApprovalRouteInsightViews.CoverageReport after = analyze(routes.stream()
                .filter(route -> !routeId.equals(route.getId())).toList(), categories);
        long newGaps = Math.max(0, countSegments(after, "GAP") - countSegments(before, "GAP"));
        long newAmbiguous = Math.max(0,
                countSegments(after, "AMBIGUOUS") - countSegments(before, "AMBIGUOUS"));
        String actionMessage = newGaps > 0
                ? "该操作将新增 " + newGaps + " 个审批覆盖断档，请先配置替代规则。"
                : "未发现新增审批覆盖断档，仍请确认受影响品类和金额区间。";
        return ApprovalRouteInsightViews.ImpactReport.builder()
                .routeId(routeId).routeName(target.getRouteName()).coverage(after)
                .gapSegmentCount(newGaps).ambiguousSegmentCount(newAmbiguous)
                .actionMessage(actionMessage).build();
    }

    private ApprovalRouteInsightViews.MatchPreview matchedPreview(
            Long tenantId,
            String categoryCode,
            ApprovalRouteResolver.Evaluation evaluation) {
        ProcApprovalRoute route = evaluation.route();
        ApprovalRouteInsightViews.MatchPreview.MatchPreviewBuilder builder =
                ApprovalRouteInsightViews.MatchPreview.builder()
                        .routeId(route.getId()).routeName(route.getRouteName())
                        .routeCode(route.getRouteCode()).categoryCode(categoryCode)
                        .effectiveCategoryCode(evaluation.effectiveCategoryCode())
                        .defaultRule(evaluation.defaultRule()).minAmount(route.getMinAmount())
                        .maxAmount(route.getMaxAmount()).modelVersionId(route.getModelVersionId())
                        .conflictingRouteIds(List.of());
        try {
            R<WorkflowContracts.ApprovalPreviewResponse> response =
                    workflowClient.getApprovalPreview(tenantId, route.getModelVersionId());
            if (response == null || response.getCode() != 200 || response.getData() == null
                    || !isAvailablePurchaseModel(response.getData().getModelVersion())
                    || !route.getModelVersionId().equals(response.getData().getModelVersion().getId())) {
                return builder.outcome("WORKFLOW_UNAVAILABLE")
                        .actionMessage("规则已命中，但绑定流程当前不可验证，请联系流程管理员。")
                        .build();
            }
            WorkflowContracts.ModelVersionResponse model = response.getData().getModelVersion();
            return builder.outcome("MATCHED").modelName(model.getModelName())
                    .modelVersion(model.getVersion()).publishTime(model.getPublishTime())
                    .approvalGraph(response.getData())
                    .actionMessage(evaluation.defaultRule()
                            ? "已由默认规则兜底，将按所选审批流程发起。"
                            : "已命中品类专属规则，将按所选审批流程发起。")
                    .build();
        } catch (FeignException exception) {
            return builder.outcome("WORKFLOW_UNAVAILABLE")
                    .actionMessage("规则已命中，但 Workflow 暂时不可用，请稍后重试。")
                    .build();
        }
    }

    private ApprovalRouteInsightViews.CoverageReport analyze(
            List<ProcApprovalRoute> routes,
            List<ProcMaterialCategory> categories) {
        ModelResolution resolution = resolveModels(routes);
        return coverageAnalyzer.analyze(categories, routes, resolution.availability(), resolution.available());
    }

    private ModelResolution resolveModels(List<ProcApprovalRoute> routes) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        Set<Long> ids = new LinkedHashSet<>();
        routes.forEach(route -> {
            if (route.getModelVersionId() != null) {
                ids.add(route.getModelVersionId());
            }
        });
        if (ids.isEmpty()) {
            return new ModelResolution(Map.of(), true);
        }
        List<Long> orderedIds = new ArrayList<>(ids);
        Map<Long, String> availability = new HashMap<>();
        orderedIds.forEach(id -> availability.put(id, "NOT_FOUND"));
        try {
            for (int offset = 0; offset < orderedIds.size(); offset += RESOLVE_BATCH_SIZE) {
                List<Long> batch = orderedIds.subList(offset,
                        Math.min(offset + RESOLVE_BATCH_SIZE, orderedIds.size()));
                WorkflowContracts.ModelVersionResolveRequest request =
                        new WorkflowContracts.ModelVersionResolveRequest();
                request.setModelVersionIds(List.copyOf(batch));
                R<List<WorkflowContracts.ModelVersionResponse>> response =
                        workflowClient.resolveModelVersions(tenantId, request);
                if (response == null || response.getCode() != 200 || response.getData() == null) {
                    return unavailableResolution(orderedIds);
                }
                response.getData().forEach(model -> availability.put(model.getId(),
                        workflowAvailability(model)));
            }
            return new ModelResolution(availability, true);
        } catch (FeignException exception) {
            return unavailableResolution(orderedIds);
        }
    }

    private ModelResolution unavailableResolution(List<Long> ids) {
        Map<Long, String> availability = new HashMap<>();
        ids.forEach(id -> availability.put(id, "UNAVAILABLE"));
        return new ModelResolution(availability, false);
    }

    private String workflowAvailability(WorkflowContracts.ModelVersionResponse model) {
        if (model == null || model.getId() == null) {
            return "NOT_FOUND";
        }
        if (model.getCategory() != null && !WORKFLOW_CATEGORY.equals(model.getCategory())) {
            return "LEGACY_CATEGORY";
        }
        return model.getAvailability() == null ? "UNAVAILABLE" : model.getAvailability();
    }

    private boolean isAvailablePurchaseModel(WorkflowContracts.ModelVersionResponse model) {
        return model != null
                && model.getId() != null
                && WORKFLOW_CATEGORY.equals(model.getCategory())
                && "PUBLISHED".equals(model.getStatus())
                && "AVAILABLE".equals(model.getAvailability())
                && model.getProcessDefinitionId() != null
                && !model.getProcessDefinitionId().isBlank();
    }

    private ApprovalRouteInsightViews.WorkflowOption toOption(WorkflowContracts.ModelVersionResponse model) {
        return ApprovalRouteInsightViews.WorkflowOption.builder()
                .modelVersionId(model.getId()).modelId(model.getModelId())
                .modelKey(model.getModelKey()).modelName(model.getModelName())
                .category(model.getCategory()).version(model.getVersion())
                .publishTime(model.getPublishTime())
                .approvalPreviewVersion(model.getApprovalPreviewVersion()).build();
    }

    private List<ProcApprovalRoute> loadRoutes() {
        return routeMapper.selectList(new LambdaQueryWrapper<ProcApprovalRoute>()
                .eq(ProcApprovalRoute::getTenantId, ServiceIdentityContext.requireTenantId())
                .orderByAsc(ProcApprovalRoute::getPriority)
                .orderByAsc(ProcApprovalRoute::getId));
    }

    private List<ProcMaterialCategory> loadActiveCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<ProcMaterialCategory>()
                .eq(ProcMaterialCategory::getTenantId, ServiceIdentityContext.requireTenantId())
                .eq(ProcMaterialCategory::getStatus, 1)
                .orderByAsc(ProcMaterialCategory::getSort)
                .orderByAsc(ProcMaterialCategory::getId));
    }

    private long countSegments(ApprovalRouteInsightViews.CoverageReport report, String outcome) {
        return report.getCategories().stream().flatMap(category -> category.getSegments().stream())
                .filter(segment -> outcome.equals(segment.getOutcome())).count();
    }

    /** 批量模型解析结果。 */
    private record ModelResolution(Map<Long, String> availability, boolean available) {
    }
}
