package com.omni.procurement.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.procurement.domain.ApprovalRouteResolver;
import com.omni.procurement.domain.RequisitionStateMachine;
import com.omni.procurement.entity.ProcApprovalRoute;
import com.omni.procurement.entity.ProcMaterial;
import com.omni.procurement.entity.ProcMaterialCategory;
import com.omni.procurement.entity.ProcRequisition;
import com.omni.procurement.entity.ProcRequisitionLine;
import com.omni.procurement.mapper.ProcMaterialCategoryMapper;
import com.omni.procurement.mapper.ProcMaterialMapper;
import com.omni.procurement.mapper.ProcRequisitionLineMapper;
import com.omni.procurement.mapper.ProcRequisitionMapper;
import com.omni.procurement.security.ProcTenantContext;
import com.omni.procurement.service.support.ProcRecordAccessGuard;
import com.omni.procurement.workflow.RequisitionWorkflowCommand;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 请购审批轮次、物料快照与 Workflow 重试测试。 */
@ExtendWith(MockitoExtension.class)
class RequisitionWorkflowStateServiceImplTest {

    @Mock private ProcRequisitionMapper requisitionMapper;
    @Mock private ProcRequisitionLineMapper lineMapper;
    @Mock private ProcMaterialMapper materialMapper;
    @Mock private ProcMaterialCategoryMapper categoryMapper;
    @Mock private ApprovalRouteResolver routeResolver;
    @Mock private ReliableMessageRelay reliableMessageRelay;

    private RequisitionWorkflowStateServiceImpl service;

    /** 初始化 MyBatis-Plus Lambda 元数据。 */
    @BeforeAll
    static void initializeTableMetadata() {
        initialize(ProcRequisition.class, "ProcRequisitionMapper");
        initialize(ProcRequisitionLine.class, "ProcRequisitionLineMapper");
        initialize(ProcMaterial.class, "ProcMaterialMapper");
        initialize(ProcMaterialCategory.class, "ProcMaterialCategoryMapper");
    }

    /** 初始化服务和请求身份。 */
    @BeforeEach
    void setUp() {
        service = new RequisitionWorkflowStateServiceImpl(
                requisitionMapper, lineMapper, materialMapper, categoryMapper, routeResolver,
                new ProcRecordAccessGuard(), reliableMessageRelay);
        ProcTenantContext.set(new ProcTenantContext.RequestIdentity(7L, 41L, "buyer"));
    }

    /** 清理租户上下文。 */
    @AfterEach
    void clearContext() {
        ProcTenantContext.clear();
    }

    /** 新提交必须 attempt+1，并按当前活动物料刷新提交快照和审批路由。 */
    @Test
    void shouldCreateNewAttemptAndRefreshCurrentMaterialSnapshot() {
        ProcRequisition requisition = draft();
        when(requisitionMapper.selectForUpdate(41L, 100L)).thenReturn(requisition);
        when(lineMapper.selectList(any())).thenReturn(List.of(line("OLD_CATEGORY")));
        when(materialMapper.selectList(any())).thenReturn(List.of(material("ACTIVE")));
        when(categoryMapper.selectList(any())).thenReturn(List.of(category("IT_NEW", 1)));
        when(lineMapper.update(any(), any())).thenReturn(1);
        ProcApprovalRoute route = new ProcApprovalRoute();
        route.setModelVersionId(88L);
        when(routeResolver.resolve("IT_NEW", new BigDecimal("20.0000"))).thenReturn(route);
        when(requisitionMapper.update(any(), any())).thenReturn(1);

        RequisitionWorkflowCommand command = service.prepareSubmit(100L, 5);

        assertThat(command.approvalAttempt()).isEqualTo(3);
        assertThat(command.businessKey()).isEqualTo("100:3");
        assertThat(command.categoryCode()).isEqualTo("IT_NEW");
        assertThat(command.totalAmount()).isEqualByComparingTo("20.0000");
        assertThat(command.modelVersionId()).isEqualTo(88L);
        assertThat(command.requestId()).isNotBlank();
        verify(lineMapper).update(any(), any());
        verify(routeResolver).resolve("IT_NEW", new BigDecimal("20.0000"));
        verify(reliableMessageRelay).send(
                org.mockito.ArgumentMatchers.eq("procurement-domain-out-0"), any(),
                org.mockito.ArgumentMatchers.eq(41L), any());
    }

    /** 草稿创建后物料被停用时必须拒绝提交。 */
    @Test
    void shouldRejectSubmissionWhenMaterialWasDeactivated() {
        when(requisitionMapper.selectForUpdate(41L, 100L)).thenReturn(draft());
        when(lineMapper.selectList(any())).thenReturn(List.of(line("IT")));
        when(materialMapper.selectList(any())).thenReturn(List.of(material("INACTIVE")));
        when(categoryMapper.selectList(any())).thenReturn(List.of(category("IT", 1)));

        assertThatThrownBy(() -> service.prepareSubmit(100L, 5))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
        verify(lineMapper, never()).update(any(), any());
        verify(requisitionMapper, never()).update(any(), any());
        verify(routeResolver, never()).resolve(any(), any());
        verify(reliableMessageRelay, never()).send(any(), any(), any(), any());
    }

    /** FAILED 重试必须复用原 requestId/businessKey/modelVersionId 且不增加 attempt。 */
    @Test
    void shouldReusePersistedSnapshotWhenRetryingFailedStart() {
        ProcRequisition requisition = draft();
        requisition.setStatus(RequisitionStateMachine.SUBMITTED);
        requisition.setWorkflowStartStatus(RequisitionStateMachine.START_FAILED);
        requisition.setApprovalAttempt(3);
        requisition.setWorkflowRequestId("fixed-request");
        requisition.setWorkflowBusinessKey("100:3");
        requisition.setWorkflowModelVersionId(88L);
        requisition.setPrimaryCategoryCode("IT");
        requisition.setTotalAmount(new BigDecimal("20.0000"));
        when(requisitionMapper.selectForUpdate(41L, 100L)).thenReturn(requisition);
        when(requisitionMapper.update(any(), any())).thenReturn(1);

        RequisitionWorkflowCommand command = service.prepareRetry(100L, 5);

        assertThat(command.approvalAttempt()).isEqualTo(3);
        assertThat(command.requestId()).isEqualTo("fixed-request");
        assertThat(command.businessKey()).isEqualTo("100:3");
        assertThat(command.modelVersionId()).isEqualTo(88L);
        verify(routeResolver, never()).resolve(any(), any());
        verify(reliableMessageRelay, never()).send(any(), any(), any(), any());
    }

    private ProcRequisition draft() {
        ProcRequisition requisition = new ProcRequisition();
        requisition.setId(100L);
        requisition.setTenantId(41L);
        requisition.setRequisitionNo("PR-41-100");
        requisition.setTitle("电脑采购");
        requisition.setRequesterUserId(7L);
        requisition.setRequesterUnitId(12L);
        requisition.setCurrencyCode("CNY");
        requisition.setStatus(RequisitionStateMachine.DRAFT);
        requisition.setWorkflowStartStatus(RequisitionStateMachine.START_NOT_STARTED);
        requisition.setApprovalAttempt(2);
        requisition.setVersion(5);
        requisition.setDeleted(0);
        return requisition;
    }

    private ProcRequisitionLine line(String categoryCode) {
        ProcRequisitionLine line = new ProcRequisitionLine();
        line.setId(501L);
        line.setTenantId(41L);
        line.setRequisitionId(100L);
        line.setLineNo(1);
        line.setMaterialId(301L);
        line.setCategoryCode(categoryCode);
        line.setQuantity(new BigDecimal("2.000000"));
        line.setEstimatedUnitPrice(new BigDecimal("10.000000"));
        line.setDeleted(0);
        return line;
    }

    private ProcMaterial material(String status) {
        ProcMaterial material = new ProcMaterial();
        material.setId(301L);
        material.setTenantId(41L);
        material.setCategoryId(701L);
        material.setMaterialCode("NB-001");
        material.setMaterialName("笔记本电脑");
        material.setUnit("台");
        material.setStatus(status);
        return material;
    }

    private ProcMaterialCategory category(String code, int status) {
        ProcMaterialCategory category = new ProcMaterialCategory();
        category.setId(701L);
        category.setTenantId(41L);
        category.setCategoryCode(code);
        category.setStatus(status);
        return category;
    }

    private static void initialize(Class<?> entityType, String resource) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, resource);
        assistant.setCurrentNamespace("com.omni.procurement.test." + resource);
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
