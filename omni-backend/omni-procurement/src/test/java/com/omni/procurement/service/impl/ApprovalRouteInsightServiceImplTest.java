package com.omni.procurement.service.impl;

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
import com.omni.procurement.mapper.ProcApprovalRouteMapper;
import com.omni.procurement.mapper.ProcMaterialCategoryMapper;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.procurement.service.ProcTenantInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 请购审批规则业务外观契约测试。 */
@ExtendWith(MockitoExtension.class)
class ApprovalRouteInsightServiceImplTest {

    @Mock private ProcTenantInitializer tenantInitializer;
    @Mock private ProcApprovalRouteMapper routeMapper;
    @Mock private ProcMaterialCategoryMapper categoryMapper;
    @Mock private WorkflowInternalClient workflowClient;
    @Mock private ApprovalRouteResolver routeResolver;

    /** 清理租户上下文。 */
    @AfterEach
    void clearContext() {
        ServiceIdentityContext.clear();
    }

    /** 流程选项必须固定请求 purchase 且只暴露当前可用版本。 */
    @Test
    void shouldExposeOnlyAvailablePurchaseOptions() {
        useTenant();
        WorkflowContracts.ModelVersionResponse available = model("purchase", "AVAILABLE");
        WorkflowContracts.ModelVersionResponse legacy = model("PROCUREMENT_REQUISITION", "AVAILABLE");
        when(workflowClient.listPublishedModelVersions(41L, "purchase"))
                .thenReturn(R.ok(List.of(available, legacy)));

        List<ApprovalRouteInsightViews.WorkflowOption> options = service().workflowOptions();

        assertThat(options).singleElement().satisfies(option -> {
            assertThat(option.getModelVersionId()).isEqualTo(12L);
            assertThat(option.getCategory()).isEqualTo("purchase");
        });
        verify(workflowClient).listPublishedModelVersions(41L, "purchase");
    }

    /** 匹配试算必须复用解析器，并返回 Workflow 安全预览而非原始 BPMN。 */
    @Test
    void shouldUseResolverAndReturnSafePreview() {
        useTenant();
        ProcApprovalRoute route = route();
        when(routeResolver.evaluate("IT_DEVICE", new BigDecimal("10000.0000")))
                .thenReturn(new ApprovalRouteResolver.Evaluation(
                        ApprovalRouteResolver.Outcome.MATCHED, route,
                        "IT_DEVICE", false, List.of(route)));
        WorkflowContracts.ApprovalPreviewResponse preview = new WorkflowContracts.ApprovalPreviewResponse();
        preview.setModelVersion(model("purchase", "AVAILABLE"));
        preview.setNodes(List.of());
        preview.setEdges(List.of());
        when(workflowClient.getApprovalPreview(41L, 12L)).thenReturn(R.ok(preview));
        ApprovalRouteInsightRequests.MatchPreviewRequest request =
                new ApprovalRouteInsightRequests.MatchPreviewRequest();
        request.setCategoryCode("it_device");
        request.setTotalAmount(new BigDecimal("10000.0000"));

        ApprovalRouteInsightViews.MatchPreview result = service().matchPreview(request);

        assertThat(result.getOutcome()).isEqualTo("MATCHED");
        assertThat(result.getRouteId()).isEqualTo(8L);
        assertThat(result.getApprovalGraph()).isSameAs(preview);
        verify(routeResolver).evaluate("IT_DEVICE", new BigDecimal("10000.0000"));
    }

    /** 遗留分类模型不得伪装为当前可发起的 purchase 流程。 */
    @Test
    void shouldMarkLegacyCategoryPreviewUnavailable() {
        useTenant();
        ProcApprovalRoute route = route();
        when(routeResolver.evaluate(any(), any())).thenReturn(
                new ApprovalRouteResolver.Evaluation(ApprovalRouteResolver.Outcome.MATCHED,
                        route, "IT_DEVICE", false, List.of(route)));
        WorkflowContracts.ApprovalPreviewResponse preview = new WorkflowContracts.ApprovalPreviewResponse();
        preview.setModelVersion(model("PROCUREMENT_REQUISITION", "AVAILABLE"));
        when(workflowClient.getApprovalPreview(41L, 12L)).thenReturn(R.ok(preview));
        ApprovalRouteInsightRequests.MatchPreviewRequest request =
                new ApprovalRouteInsightRequests.MatchPreviewRequest();
        request.setCategoryCode("IT_DEVICE");
        request.setTotalAmount(BigDecimal.ONE);

        assertThat(service().matchPreview(request).getOutcome())
                .isEqualTo("WORKFLOW_UNAVAILABLE");
    }

    /** Workflow 返回无效选项响应时必须 503 失败关闭。 */
    @Test
    void shouldFailClosedWhenWorkflowOptionsResponseIsInvalid() {
        useTenant();
        when(workflowClient.listPublishedModelVersions(41L, "purchase")).thenReturn(null);

        assertThatThrownBy(() -> service().workflowOptions())
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(503);
    }

    private ApprovalRouteInsightServiceImpl service() {
        return new ApprovalRouteInsightServiceImpl(tenantInitializer, routeMapper, categoryMapper,
                workflowClient, routeResolver, new ApprovalRouteCoverageAnalyzer());
    }

    private void useTenant() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 41L, "buyer"));
    }

    private WorkflowContracts.ModelVersionResponse model(String category, String availability) {
        WorkflowContracts.ModelVersionResponse model = new WorkflowContracts.ModelVersionResponse();
        model.setId(12L);
        model.setModelId(2L);
        model.setModelKey("purchase-approval");
        model.setModelName("请购审批流程");
        model.setCategory(category);
        model.setVersion(3);
        model.setStatus("PUBLISHED");
        model.setProcessDefinitionId("purchase-approval:3:12");
        model.setAvailability(availability);
        return model;
    }

    private ProcApprovalRoute route() {
        ProcApprovalRoute route = new ProcApprovalRoute();
        route.setId(8L);
        route.setRouteCode("APR-01K12345678901234567890123");
        route.setRouteName("IT 设备审批");
        route.setCategoryCode("IT_DEVICE");
        route.setMinAmount(BigDecimal.ZERO);
        route.setMaxAmount(null);
        route.setModelVersionId(12L);
        route.setStatus(ApprovalRoutePolicy.ACTIVE);
        return route;
    }
}
