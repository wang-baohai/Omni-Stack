package com.omni.procurement.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import com.omni.procurement.client.WorkflowInternalClient;
import com.omni.procurement.domain.ApprovalRoutePolicy;
import com.omni.procurement.dto.ApprovalRouteRequests;
import com.omni.procurement.dto.WorkflowContracts;
import com.omni.procurement.entity.ProcApprovalRoute;
import com.omni.procurement.entity.ProcMaterialCategory;
import com.omni.procurement.mapper.ProcApprovalRouteMapper;
import com.omni.procurement.mapper.ProcMaterialCategoryMapper;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.procurement.service.ProcTenantInitializer;
import com.omni.procurement.service.support.ApprovalRouteCodeGenerator;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 审批路由服务的活动唯一和区间冲突测试。 */
@ExtendWith(MockitoExtension.class)
class ApprovalRouteServiceImplTest {

    @Mock private ProcTenantInitializer tenantInitializer;
    @Mock private ProcApprovalRouteMapper routeMapper;
    @Mock private ProcMaterialCategoryMapper categoryMapper;
    @Mock private WorkflowInternalClient workflowInternalClient;

    /** 初始化 MyBatis-Plus Lambda 元数据。 */
    @BeforeAll
    static void initializeTableMetadata() {
        initialize(ProcApprovalRoute.class, "ProcApprovalRouteMapper");
        initialize(ProcMaterialCategory.class, "ProcMaterialCategoryMapper");
    }

    /** 清理租户上下文。 */
    @AfterEach
    void clearContext() {
        ServiceIdentityContext.clear();
    }

    /** 同品类活动金额区间重叠时必须在写库前返回 409。 */
    @Test
    void shouldRejectOverlappingActiveRoute() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 41L, "buyer"));
        when(routeMapper.lockTenantConfig(41L)).thenReturn(1L);
        when(categoryMapper.selectOne(any())).thenReturn(activeCategory());
        stubPublishedVersion();
        when(routeMapper.selectCount(any())).thenReturn(0L);
        when(routeMapper.selectList(any())).thenReturn(List.of(existingRoute("0", "100")));
        ApprovalRouteServiceImpl service = new ApprovalRouteServiceImpl(
                tenantInitializer, routeMapper, categoryMapper, workflowInternalClient,
                new ApprovalRouteCodeGenerator());

        assertThatThrownBy(() -> service.create(createRequest("50", "200")))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
        verify(routeMapper, never()).insert(any(ProcApprovalRoute.class));
    }

    /** 并发插入触发数据库唯一键时必须翻译为稳定的 409。 */
    @Test
    void shouldTranslateConcurrentRouteCodeConflict() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 41L, "buyer"));
        when(routeMapper.lockTenantConfig(41L)).thenReturn(1L);
        when(categoryMapper.selectOne(any())).thenReturn(activeCategory());
        stubPublishedVersion();
        when(routeMapper.selectCount(any())).thenReturn(0L);
        when(routeMapper.selectList(any())).thenReturn(List.of());
        doThrow(new DuplicateKeyException("duplicate route code"))
                .when(routeMapper).insert(any(ProcApprovalRoute.class));
        ApprovalRouteServiceImpl service = new ApprovalRouteServiceImpl(
                tenantInitializer, routeMapper, categoryMapper, workflowInternalClient,
                new ApprovalRouteCodeGenerator());

        assertThatThrownBy(() -> service.create(createRequest("0", "100")))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
    }

    /** 创建必须先锁定租户配置行，再检查活动区间并写入。 */
    @Test
    void shouldLockTenantBeforeCreateOverlapCheckAndInsert() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 41L, "buyer"));
        when(routeMapper.lockTenantConfig(41L)).thenReturn(1L);
        when(categoryMapper.selectOne(any())).thenReturn(activeCategory());
        stubPublishedVersion();
        when(routeMapper.selectCount(any())).thenReturn(0L);
        when(routeMapper.selectList(any())).thenReturn(List.of());
        when(routeMapper.insert(any(ProcApprovalRoute.class))).thenReturn(1);
        ApprovalRouteServiceImpl service = new ApprovalRouteServiceImpl(
                tenantInitializer, routeMapper, categoryMapper, workflowInternalClient,
                new ApprovalRouteCodeGenerator());

        service.create(createRequest("0", "100"));

        org.mockito.InOrder order = inOrder(routeMapper);
        order.verify(routeMapper).lockTenantConfig(41L);
        order.verify(routeMapper).selectCount(any());
        order.verify(routeMapper).selectList(any());
        order.verify(routeMapper).insert(any(ProcApprovalRoute.class));
    }

    /** 更新同样必须在读取和重叠校验前锁定租户配置行。 */
    @Test
    void shouldLockTenantBeforeUpdateOverlapCheck() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 41L, "buyer"));
        ProcApprovalRoute current = existingRoute("0", "100");
        current.setId(5L);
        current.setTenantId(41L);
        current.setRouteCode("IT_DEFAULT");
        current.setCategoryCode("IT_DEVICE");
        current.setModelVersionId(10L);
        current.setPriority(10);
        current.setVersion(3);
        when(routeMapper.lockTenantConfig(41L)).thenReturn(1L);
        when(routeMapper.selectOne(any())).thenReturn(current);
        when(categoryMapper.selectOne(any())).thenReturn(activeCategory());
        stubPublishedVersion();
        when(routeMapper.selectCount(any())).thenReturn(0L);
        when(routeMapper.selectList(any())).thenReturn(List.of());
        when(routeMapper.update(
                org.mockito.ArgumentMatchers.<ProcApprovalRoute>isNull(),
                org.mockito.ArgumentMatchers.<Wrapper<ProcApprovalRoute>>any())).thenReturn(1);
        ApprovalRouteServiceImpl service = new ApprovalRouteServiceImpl(
                tenantInitializer, routeMapper, categoryMapper, workflowInternalClient,
                new ApprovalRouteCodeGenerator());

        service.update(5L, updateRequest());

        org.mockito.InOrder order = inOrder(routeMapper);
        order.verify(routeMapper).lockTenantConfig(41L);
        order.verify(routeMapper).selectOne(any());
        order.verify(routeMapper).selectCount(any());
        order.verify(routeMapper).selectList(any());
        order.verify(routeMapper).update(
                org.mockito.ArgumentMatchers.<ProcApprovalRoute>isNull(),
                org.mockito.ArgumentMatchers.<Wrapper<ProcApprovalRoute>>any());
    }

    /** 未发布或缺少流程定义的模型版本不得写入审批路由。 */
    @Test
    void shouldRejectWorkflowModelVersionWithoutPublishedDefinition() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 41L, "buyer"));
        when(categoryMapper.selectOne(any())).thenReturn(activeCategory());
        WorkflowContracts.ModelVersionResponse invalid = new WorkflowContracts.ModelVersionResponse();
        invalid.setId(12L);
        invalid.setStatus("PUBLISHED");
        invalid.setProcessDefinitionId("  ");
        when(workflowInternalClient.resolveModelVersions(
                org.mockito.ArgumentMatchers.eq(41L), any())).thenReturn(R.ok(List.of(invalid)));
        ApprovalRouteServiceImpl service = new ApprovalRouteServiceImpl(
                tenantInitializer, routeMapper, categoryMapper, workflowInternalClient,
                new ApprovalRouteCodeGenerator());

        assertThatThrownBy(() -> service.create(createRequest("0", "100")))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(400);

        verify(routeMapper, never()).lockTenantConfig(41L);
        verify(routeMapper, never()).insert(any(ProcApprovalRoute.class));
    }

    /** 新建规则不得绑定非 purchase 分类的遗留流程。 */
    @Test
    void shouldRejectLegacyWorkflowCategory() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 41L, "buyer"));
        when(categoryMapper.selectOne(any())).thenReturn(activeCategory());
        WorkflowContracts.ModelVersionResponse legacy = new WorkflowContracts.ModelVersionResponse();
        legacy.setId(12L);
        legacy.setCategory("PROCUREMENT_REQUISITION");
        legacy.setStatus("PUBLISHED");
        legacy.setAvailability("AVAILABLE");
        legacy.setProcessDefinitionId("legacy:1:12");
        when(workflowInternalClient.resolveModelVersions(
                org.mockito.ArgumentMatchers.eq(41L), any())).thenReturn(R.ok(List.of(legacy)));
        ApprovalRouteServiceImpl service = new ApprovalRouteServiceImpl(
                tenantInitializer, routeMapper, categoryMapper, workflowInternalClient,
                new ApprovalRouteCodeGenerator());

        assertThatThrownBy(() -> service.create(createRequest("0", "100")))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(400);
        verify(routeMapper, never()).lockTenantConfig(41L);
    }

    private ApprovalRouteRequests.CreateRouteRequest createRequest(String min, String max) {
        ApprovalRouteRequests.CreateRouteRequest request = new ApprovalRouteRequests.CreateRouteRequest();
        request.setRouteCode("IT_DEFAULT");
        request.setRouteName("IT 设备审批规则");
        request.setCategoryCode("IT_DEVICE");
        request.setMinAmount(new BigDecimal(min));
        request.setMaxAmount(new BigDecimal(max));
        request.setModelVersionId(12L);
        request.setPriority(10);
        request.setStatus(ApprovalRoutePolicy.ACTIVE);
        return request;
    }

    private ApprovalRouteRequests.UpdateRouteRequest updateRequest() {
        ApprovalRouteRequests.UpdateRouteRequest request = new ApprovalRouteRequests.UpdateRouteRequest();
        request.setVersion(3);
        request.setRouteName("IT 设备审批规则");
        request.setCategoryCode("IT_DEVICE");
        request.setMinAmount(new BigDecimal("100"));
        request.setMaxAmount(new BigDecimal("200"));
        request.setModelVersionId(12L);
        request.setPriority(20);
        request.setStatus(ApprovalRoutePolicy.ACTIVE);
        return request;
    }

    private ProcApprovalRoute existingRoute(String min, String max) {
        ProcApprovalRoute route = new ProcApprovalRoute();
        route.setMinAmount(new BigDecimal(min));
        route.setMaxAmount(new BigDecimal(max));
        route.setStatus(ApprovalRoutePolicy.ACTIVE);
        route.setRouteName("既有审批规则");
        return route;
    }

    private ProcMaterialCategory activeCategory() {
        ProcMaterialCategory category = new ProcMaterialCategory();
        category.setId(100L);
        category.setTenantId(41L);
        category.setCategoryCode("IT_DEVICE");
        category.setStatus(1);
        return category;
    }

    private void stubPublishedVersion() {
        WorkflowContracts.ModelVersionResponse response = new WorkflowContracts.ModelVersionResponse();
        response.setId(12L);
        response.setModelId(5L);
        response.setVersion(2);
        response.setCategory("purchase");
        response.setProcessDefinitionId("procurement-approval:2:12");
        response.setStatus("PUBLISHED");
        response.setAvailability("AVAILABLE");
        when(workflowInternalClient.resolveModelVersions(
                org.mockito.ArgumentMatchers.eq(41L), any())).thenReturn(R.ok(List.of(response)));
    }

    private static void initialize(Class<?> entityType, String mapperName) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "approval-route-service-" + mapperName);
        assistant.setCurrentNamespace("com.omni.procurement.mapper." + mapperName);
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
