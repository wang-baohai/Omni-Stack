package com.omni.procurement.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import com.omni.procurement.client.WorkflowInternalClient;
import com.omni.procurement.domain.RequisitionStateMachine;
import com.omni.procurement.dto.RequisitionRequests;
import com.omni.procurement.dto.RequisitionViews;
import com.omni.procurement.dto.WorkflowContracts;
import com.omni.procurement.entity.ProcMaterial;
import com.omni.procurement.entity.ProcMaterialCategory;
import com.omni.procurement.entity.ProcRequisition;
import com.omni.procurement.entity.ProcRequisitionLine;
import com.omni.procurement.mapper.ProcMaterialCategoryMapper;
import com.omni.procurement.mapper.ProcMaterialMapper;
import com.omni.procurement.mapper.ProcRequisitionLineMapper;
import com.omni.procurement.mapper.ProcRequisitionMapper;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.procurement.service.ProcTenantInitializer;
import com.omni.procurement.service.RequisitionWorkflowStateService;
import com.omni.procurement.service.support.ProcRecordAccessGuard;
import com.omni.procurement.workflow.RequisitionWorkflowCoordinator;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 请购编辑迁移和审批专用视图安全测试。 */
@ExtendWith(MockitoExtension.class)
class RequisitionServiceImplTest {

    @Mock private ProcTenantInitializer tenantInitializer;
    @Mock private ProcRequisitionMapper requisitionMapper;
    @Mock private ProcRequisitionLineMapper lineMapper;
    @Mock private ProcMaterialMapper materialMapper;
    @Mock private ProcMaterialCategoryMapper categoryMapper;
    @Mock private RequisitionWorkflowStateService workflowStateService;
    @Mock private RequisitionWorkflowCoordinator workflowCoordinator;
    @Mock private WorkflowInternalClient workflowInternalClient;

    private RequisitionServiceImpl service;

    /** 初始化 MyBatis-Plus Lambda 元数据。 */
    @BeforeAll
    static void initializeTableMetadata() {
        initialize(ProcRequisition.class, "ProcRequisitionMapper");
        initialize(ProcRequisitionLine.class, "ProcRequisitionLineMapper");
        initialize(ProcMaterial.class, "ProcMaterialMapper");
        initialize(ProcMaterialCategory.class, "ProcMaterialCategoryMapper");
    }

    /** 初始化服务和请求上下文。 */
    @BeforeEach
    void setUp() {
        service = new RequisitionServiceImpl(
                tenantInitializer, requisitionMapper, lineMapper, materialMapper, categoryMapper,
                new ProcRecordAccessGuard(), workflowStateService, workflowCoordinator,
                workflowInternalClient);
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 41L, "buyer"));
    }

    /** 清理请求上下文。 */
    @AfterEach
    void clearContext() {
        ServiceDataScopeContext.clear();
        ServiceIdentityContext.clear();
    }

    /** 未分配给当前用户的 taskId 不能触发完整审批业务视图读取。 */
    @Test
    void shouldValidateWorkflowAssignmentBeforeLoadingApprovalView() {
        ProcRequisition identity = approving();
        when(requisitionMapper.selectWorkflowIdentity(41L, 100L)).thenReturn(identity);
        WorkflowContracts.AssignmentResponse assignment = new WorkflowContracts.AssignmentResponse();
        assignment.setValid(false);
        assignment.setAssignmentType("NONE");
        when(workflowInternalClient.validateAssignment(any(), any())).thenReturn(R.ok(assignment));

        assertThatThrownBy(() -> service.approvalView(100L, "task-9"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(403);

        InOrder order = inOrder(requisitionMapper, workflowInternalClient);
        order.verify(requisitionMapper).selectWorkflowIdentity(41L, 100L);
        order.verify(workflowInternalClient).validateAssignment(any(), any());
        verify(requisitionMapper, never()).selectOne(any());
        verify(lineMapper, never()).selectList(any());
    }

    /** 合法任务必须校验当前业务键、用户和流程实例后才返回租户级只读视图。 */
    @Test
    void shouldLoadApprovalViewOnlyAfterExactAssignmentValidation() {
        ProcRequisition identity = approving();
        when(requisitionMapper.selectWorkflowIdentity(41L, 100L)).thenReturn(identity);
        WorkflowContracts.AssignmentResponse assignment = new WorkflowContracts.AssignmentResponse();
        assignment.setValid(true);
        assignment.setProcessInstanceId("pi-9");
        assignment.setAssignmentType("CANDIDATE");
        when(workflowInternalClient.validateAssignment(any(), any())).thenReturn(R.ok(assignment));
        when(requisitionMapper.selectOne(any())).thenReturn(identity);
        when(lineMapper.selectList(any())).thenReturn(List.of());

        RequisitionViews.ApprovalView result = service.approvalView(100L, "task-9");

        ArgumentCaptor<WorkflowContracts.AssignmentRequest> captor =
                ArgumentCaptor.forClass(WorkflowContracts.AssignmentRequest.class);
        verify(workflowInternalClient).validateAssignment(
                org.mockito.ArgumentMatchers.eq(41L), captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
        assertThat(captor.getValue().getBusinessKey()).isEqualTo("100:2");
        assertThat(result.getTaskId()).isEqualTo("task-9");
        assertThat(result.getRequisition().getId()).isEqualTo(100L);
        assertThat(ServiceDataScopeContext.get()).isNull();
    }

    /** 更新被拒绝请购必须显式回到 DRAFT 并清理上一轮 Workflow 快照。 */
    @Test
    void shouldMoveRejectedRequisitionBackToDraftAndClearWorkflowSnapshot() {
        ServiceDataScopeContext.set(new ServiceDataScopeContext.ScopeInfo(
                7L, 41L, "procurement:requisition:update", 12L, "SELF", Set.of(12L), null));
        ProcRequisition rejected = approving();
        rejected.setStatus(RequisitionStateMachine.REJECTED);
        rejected.setWorkflowStartStatus(RequisitionStateMachine.START_STARTED);
        rejected.setVersion(5);
        when(requisitionMapper.selectForUpdate(41L, 100L)).thenReturn(rejected);
        when(materialMapper.selectList(any())).thenReturn(List.of(material()));
        when(categoryMapper.selectList(any())).thenReturn(List.of(category()));
        when(requisitionMapper.update(any(), any())).thenReturn(1);
        when(lineMapper.update(any(), any())).thenReturn(1);
        when(lineMapper.insert(any(ProcRequisitionLine.class))).thenReturn(1);
        ProcRequisition refreshed = approving();
        refreshed.setStatus(RequisitionStateMachine.DRAFT);
        refreshed.setWorkflowStartStatus(RequisitionStateMachine.START_NOT_STARTED);
        refreshed.setWorkflowBusinessKey(null);
        refreshed.setProcessInstanceId(null);
        when(requisitionMapper.selectOne(any())).thenReturn(refreshed);
        when(lineMapper.selectList(any())).thenReturn(List.of());

        RequisitionViews.Detail result = service.update(100L, updateRequest());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<ProcRequisition>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(requisitionMapper).update(isNull(), captor.capture());
        String sqlSet = captor.getValue().getSqlSet();
        assertThat(sqlSet).contains("status", "workflow_start_status", "workflow_request_id",
                "workflow_business_key", "workflow_model_version_id", "process_instance_id",
                "workflow_completed_time");
        assertThat(result.getStatus()).isEqualTo(RequisitionStateMachine.DRAFT);
        assertThat(result.getWorkflowStartStatus()).isEqualTo(RequisitionStateMachine.START_NOT_STARTED);
    }

    private RequisitionRequests.UpdateRequest updateRequest() {
        RequisitionRequests.LineInput line = new RequisitionRequests.LineInput();
        line.setMaterialId(301L);
        line.setQuantity(new BigDecimal("2.000000"));
        line.setEstimatedUnitPrice(new BigDecimal("10.000000"));
        RequisitionRequests.UpdateRequest request = new RequisitionRequests.UpdateRequest();
        request.setVersion(5);
        request.setTitle("修改后的电脑采购");
        request.setLines(List.of(line));
        return request;
    }

    private ProcRequisition approving() {
        ProcRequisition requisition = new ProcRequisition();
        requisition.setId(100L);
        requisition.setTenantId(41L);
        requisition.setRequisitionNo("PR-41-100");
        requisition.setTitle("电脑采购");
        requisition.setRequesterUserId(7L);
        requisition.setRequesterUnitId(12L);
        requisition.setPrimaryCategoryCode("IT");
        requisition.setTotalAmount(new BigDecimal("20.0000"));
        requisition.setCurrencyCode("CNY");
        requisition.setStatus(RequisitionStateMachine.APPROVING);
        requisition.setWorkflowStartStatus(RequisitionStateMachine.START_STARTED);
        requisition.setApprovalAttempt(2);
        requisition.setWorkflowBusinessKey("100:2");
        requisition.setProcessInstanceId("pi-9");
        requisition.setVersion(5);
        requisition.setDeleted(0);
        return requisition;
    }

    private ProcMaterial material() {
        ProcMaterial material = new ProcMaterial();
        material.setId(301L);
        material.setTenantId(41L);
        material.setCategoryId(701L);
        material.setMaterialCode("NB-001");
        material.setMaterialName("笔记本电脑");
        material.setUnit("台");
        material.setStatus("ACTIVE");
        return material;
    }

    private ProcMaterialCategory category() {
        ProcMaterialCategory category = new ProcMaterialCategory();
        category.setId(701L);
        category.setTenantId(41L);
        category.setCategoryCode("IT");
        category.setStatus(1);
        return category;
    }

    private static void initialize(Class<?> entityType, String resource) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, resource);
        assistant.setCurrentNamespace("com.omni.procurement.test." + resource);
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
