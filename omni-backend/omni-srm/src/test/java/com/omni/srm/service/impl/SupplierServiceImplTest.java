package com.omni.srm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.srm.dto.SrmRequests;
import com.omni.srm.entity.SrmEvaluation;
import com.omni.srm.entity.SrmRiskAssessment;
import com.omni.srm.entity.SrmRiskIndicator;
import com.omni.srm.entity.SrmSupplier;
import com.omni.srm.entity.SrmSupplierEnrollment;
import com.omni.srm.entity.SrmSupplierPortalUser;
import com.omni.srm.mapper.SrmEvaluationMapper;
import com.omni.srm.mapper.SrmRiskAssessmentMapper;
import com.omni.srm.mapper.SrmRiskIndicatorMapper;
import com.omni.srm.mapper.SrmSupplierBankAccountMapper;
import com.omni.srm.mapper.SrmSupplierContactMapper;
import com.omni.srm.mapper.SrmSupplierEnrollmentMapper;
import com.omni.srm.mapper.SrmSupplierMapper;
import com.omni.srm.mapper.SrmSupplierPortalUserMapper;
import com.omni.srm.mapper.SrmSupplierQualificationMapper;
import com.omni.srm.security.SrmTenantContext;
import com.omni.srm.service.RiskService;
import com.omni.srm.service.support.SrmOwnerEnricher;
import com.omni.srm.service.support.SrmOwnerResolver;
import com.omni.srm.service.support.SrmPermissionScopeExecutor;
import com.omni.srm.service.support.SrmRecordAccessGuard;
import com.omni.srm.service.support.SupplierRiskInitializer;
import com.omni.srm.workflow.SupplierWorkflowCoordinator;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/** 供应商管理员创建流程测试。 */
@ExtendWith(MockitoExtension.class)
class SupplierServiceImplTest {

    @Mock private SrmSupplierMapper supplierMapper;
    @Mock private SrmSupplierContactMapper contactMapper;
    @Mock private SrmSupplierQualificationMapper qualificationMapper;
    @Mock private SrmSupplierBankAccountMapper bankAccountMapper;
    @Mock private SrmEvaluationMapper evaluationMapper;
    @Mock private SrmRiskIndicatorMapper riskIndicatorMapper;
    @Mock private SrmRiskAssessmentMapper riskAssessmentMapper;
    @Mock private SrmSupplierEnrollmentMapper enrollmentMapper;
    @Mock private SrmSupplierPortalUserMapper portalUserMapper;
    @Mock private SrmRecordAccessGuard accessGuard;
    @Mock private SrmOwnerResolver ownerResolver;
    @Mock private SrmOwnerEnricher ownerEnricher;
    @Mock private SrmPermissionScopeExecutor scopeExecutor;
    @Mock private SupplierRiskInitializer riskInitializer;
    @Mock private ReliableMessageRelay reliableMessageRelay;
    @Mock private RiskService riskService;
    @Mock private SupplierWorkflowCoordinator workflowCoordinator;
    @InjectMocks private SupplierServiceImpl service;

    /** 初始化 MyBatis-Plus Lambda 元数据。 */
    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "srm-supplier-test");
        assistant.setCurrentNamespace("com.omni.srm.mapper.SrmSupplierMapper");
        TableInfoHelper.initTableInfo(assistant, SrmSupplier.class);
        MapperBuilderAssistant evaluationAssistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "srm-evaluation-owner-test");
        evaluationAssistant.setCurrentNamespace("com.omni.srm.mapper.SrmEvaluationMapper");
        TableInfoHelper.initTableInfo(evaluationAssistant, SrmEvaluation.class);
        initTableInfo(SrmRiskAssessment.class, "srm-risk-assessment-delete-test");
        initTableInfo(SrmRiskIndicator.class, "srm-risk-indicator-delete-test");
        initTableInfo(SrmSupplierEnrollment.class, "srm-enrollment-delete-test");
        initTableInfo(SrmSupplierPortalUser.class, "srm-portal-user-delete-test");
    }

    /** 清理租户上下文。 */
    @AfterEach
    void clearContext() {
        SrmTenantContext.clear();
    }

    /** 已进入正式生命周期的供应商必须保留审计历史，不能普通删除。 */
    @Test
    void shouldRejectDeleteForApprovedSupplier() {
        SrmTenantContext.set(new SrmTenantContext.RequestIdentity(7L, 3L, "buyer"));
        SrmSupplier supplier = supplier("APPROVED", 2);
        when(accessGuard.requireSupplier(10L)).thenReturn(supplier);
        when(supplierMapper.selectVisibleForUpdate(10L)).thenReturn(supplier);

        assertThatThrownBy(() -> service.delete(10L, 2))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);

        verify(supplierMapper, never()).update(ArgumentMatchers.isNull(), ArgumentMatchers.any());
    }

    /** 门户、评估或风险历史存在时不得通过删除隐藏历史。 */
    @Test
    void shouldRejectDeleteWhenSupplierHasEvaluationHistory() {
        SrmTenantContext.set(new SrmTenantContext.RequestIdentity(7L, 3L, "buyer"));
        SrmSupplier supplier = supplier("REJECTED", 1);
        when(accessGuard.requireSupplier(10L)).thenReturn(supplier);
        when(supplierMapper.selectVisibleForUpdate(10L)).thenReturn(supplier);
        when(evaluationMapper.selectCount(ArgumentMatchers.any())).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(10L, 1))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);

        verify(supplierMapper, never()).update(ArgumentMatchers.isNull(), ArgumentMatchers.any());
    }

    /** 尚未进入正式生命周期且没有业务历史时级联清理草稿聚合数据。 */
    @Test
    void shouldCascadeDeleteDraftSupplierAggregate() {
        SrmTenantContext.set(new SrmTenantContext.RequestIdentity(7L, 3L, "buyer"));
        SrmSupplier supplier = supplier("PENDING_REVIEW", 0);
        when(accessGuard.requireSupplier(10L)).thenReturn(supplier);
        when(supplierMapper.selectVisibleForUpdate(10L)).thenReturn(supplier);
        when(supplierMapper.update(ArgumentMatchers.isNull(), ArgumentMatchers.any())).thenReturn(1);

        service.delete(10L, 0);

        verify(contactMapper).softDeleteBySupplier(ArgumentMatchers.eq(10L),
                ArgumentMatchers.any(), ArgumentMatchers.eq("buyer"));
        verify(qualificationMapper).softDeleteBySupplier(ArgumentMatchers.eq(10L),
                ArgumentMatchers.any(), ArgumentMatchers.eq("buyer"));
        verify(bankAccountMapper).softDeleteBySupplier(ArgumentMatchers.eq(10L),
                ArgumentMatchers.any(), ArgumentMatchers.eq("buyer"));
        verify(riskIndicatorMapper).update(ArgumentMatchers.isNull(), ArgumentMatchers.any());
    }

    /** 删除接口的查询参数也必须执行版本边界校验。 */
    @Test
    void shouldRejectNegativeDeleteVersionBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.delete(10L, -1))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(400);

        verifyNoInteractions(accessGuard, supplierMapper);
    }

    /** 管理员创建必须直接待审核并初始化 owner、风险和 Outbox。 */
    @Test
    void shouldCreateAdministratorSupplierPendingReview() {
        SrmTenantContext.set(new SrmTenantContext.RequestIdentity(7L, 3L, "buyer"));
        when(ownerResolver.resolveForCreate(null, "srm:supplier:transfer"))
                .thenReturn(new SrmOwnerResolver.Owner(7L, 8L));
        when(supplierMapper.insert(ArgumentMatchers.any(SrmSupplier.class))).thenAnswer(invocation -> {
            SrmSupplier supplier = invocation.getArgument(0);
            supplier.setId(10L);
            return 1;
        });
        when(supplierMapper.update(ArgumentMatchers.isNull(), ArgumentMatchers.any())).thenReturn(1);
        when(ownerEnricher.enrichOne(ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
        SrmRequests.CreateSupplierRequest request = new SrmRequests.CreateSupplierRequest();
        request.setName("  ＡＣＭＥ   Trading  ");
        request.setSupplierType("MANUFACTURER");
        request.setCreditCode("  ab-123  ");

        service.create(request);

        ArgumentCaptor<SrmSupplier> captor = ArgumentCaptor.forClass(SrmSupplier.class);
        verify(supplierMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(captor.getValue().getName()).isEqualTo("ＡＣＭＥ   Trading");
        assertThat(captor.getValue().getNormalizedName()).isEqualTo("acme trading");
        assertThat(captor.getValue().getCreditCode()).isEqualTo("AB-123");
        assertThat(captor.getValue().getOwnerUserId()).isEqualTo(7L);
        assertThat(captor.getValue().getOwnerUnitId()).isEqualTo(8L);
        assertThat(captor.getValue().getAssignedTime()).isNotNull();
        verify(riskInitializer).initialize(3L, 10L);
        verify(reliableMessageRelay).send(
                ArgumentMatchers.eq("srm-domain-out-0"), ArgumentMatchers.any(),
                ArgumentMatchers.eq(3L), ArgumentMatchers.anyString());
        verify(workflowCoordinator).prepareAndStart(ArgumentMatchers.any(SrmSupplier.class));
    }

    /** 更新名称和信用代码时必须使用与创建、门户一致的规范化规则。 */
    @Test
    void shouldNormalizeNameAndCreditCodeWhenUpdating() {
        SrmTenantContext.set(new SrmTenantContext.RequestIdentity(7L, 3L, "buyer"));
        SrmSupplier current = supplier(10L, "APPROVED", 7L, 8L);
        when(accessGuard.requireSupplier(10L)).thenReturn(current);
        when(supplierMapper.update(ArgumentMatchers.isNull(), ArgumentMatchers.any())).thenReturn(1);
        when(ownerEnricher.enrichOne(ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
        SrmRequests.UpdateSupplierRequest request = new SrmRequests.UpdateSupplierRequest();
        request.setVersion(0);
        request.setName("  ＡＣＭＥ   Trading  ");
        request.setCreditCode("  ab-123  ");

        service.update(10L, request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<SrmSupplier>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(supplierMapper).update(ArgumentMatchers.isNull(), captor.capture());
        assertThat(captor.getValue().getParamNameValuePairs().values())
                .contains("ＡＣＭＥ   Trading", "acme trading", "AB-123");
    }

    /** 显式空信用代码表示清空字段，不能存为空串占用租户唯一键。 */
    @Test
    void shouldClearCreditCodeToNullWhenUpdatingBlankValue() {
        SrmTenantContext.set(new SrmTenantContext.RequestIdentity(7L, 3L, "buyer"));
        SrmSupplier current = supplier(10L, "APPROVED", 7L, 8L);
        when(accessGuard.requireSupplier(10L)).thenReturn(current);
        when(supplierMapper.update(ArgumentMatchers.isNull(), ArgumentMatchers.any())).thenReturn(1);
        when(ownerEnricher.enrichOne(ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
        SrmRequests.UpdateSupplierRequest request = new SrmRequests.UpdateSupplierRequest();
        request.setVersion(0);
        request.setCreditCode("   ");

        service.update(10L, request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<SrmSupplier>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(supplierMapper).update(ArgumentMatchers.isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSet()).contains("credit_code");
        assertThat(captor.getValue().getParamNameValuePairs()).containsValue(null);
    }

    /** 管理端提交不得绕过门户角色分配 Saga 推进 REGISTERING 记录。 */
    @Test
    void shouldRejectAdministrativeSubmitForRegisteringSupplier() {
        SrmSupplier current = supplier(10L, "REGISTERING", null, null);
        when(accessGuard.requireSupplier(10L)).thenReturn(current);
        SrmRequests.StatusRequest request = new SrmRequests.StatusRequest();
        request.setVersion(0);

        assertThatThrownBy(() -> service.submit(10L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
        verifyNoInteractions(supplierMapper);
    }

    /** 审批流程已迁移到工作流服务，approve/reject 不再由 SupplierService 提供。 */

    /** 并发创建撞上数据库信用代码唯一键时必须转换为业务 409。 */
    @Test
    void shouldTranslateDuplicateCreditCodeOnCreate() {
        SrmTenantContext.set(new SrmTenantContext.RequestIdentity(7L, 3L, "buyer"));
        when(ownerResolver.resolveForCreate(null, "srm:supplier:transfer"))
                .thenReturn(new SrmOwnerResolver.Owner(7L, 8L));
        when(supplierMapper.insert(ArgumentMatchers.any(SrmSupplier.class)))
                .thenThrow(new DuplicateKeyException("uk_srm_supplier_credit"));
        SrmRequests.CreateSupplierRequest request = new SrmRequests.CreateSupplierRequest();
        request.setName("ACME");
        request.setSupplierType("MANUFACTURER");
        request.setCreditCode("credit-1");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
        verifyNoInteractions(riskInitializer, reliableMessageRelay);
    }

    /** 并发更新撞上数据库信用代码唯一键时必须转换为业务 409。 */
    @Test
    void shouldTranslateDuplicateCreditCodeOnUpdate() {
        SrmTenantContext.set(new SrmTenantContext.RequestIdentity(7L, 3L, "buyer"));
        SrmSupplier current = supplier(10L, "APPROVED", 7L, 8L);
        when(accessGuard.requireSupplier(10L)).thenReturn(current);
        when(supplierMapper.update(ArgumentMatchers.isNull(), ArgumentMatchers.any()))
                .thenThrow(new DuplicateKeyException("uk_srm_supplier_credit"));
        SrmRequests.UpdateSupplierRequest request = new SrmRequests.UpdateSupplierRequest();
        request.setVersion(0);
        request.setCreditCode("credit-1");

        assertThatThrownBy(() -> service.update(10L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
    }

    /** 负责人转移必须走独立 transfer 权限范围，并直接返回更新快照。 */
    @Test
    void shouldTransferOwnerThroughDedicatedCommand() {
        SrmTenantContext.set(new SrmTenantContext.RequestIdentity(7L, 3L, "buyer"));
        SrmSupplier current = supplier(10L, "APPROVED", 7L, 8L);
        current.setName("ACME");
        when(accessGuard.requireSupplier(10L)).thenReturn(current);
        when(ownerResolver.resolveForTransfer(9L, "srm:supplier:transfer"))
                .thenReturn(new SrmOwnerResolver.Owner(9L, 10L));
        when(supplierMapper.update(ArgumentMatchers.isNull(), ArgumentMatchers.any())).thenReturn(1);
        when(ownerEnricher.enrichOne(ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
        SrmRequests.TransferOwnerRequest request = new SrmRequests.TransferOwnerRequest();
        request.setVersion(0);
        request.setOwnerUserId(9L);

        var result = service.transferOwner(10L, request);

        assertThat(result.getOwnerUserId()).isEqualTo(9L);
        assertThat(result.getOwnerUnitId()).isEqualTo(10L);
        assertThat(result.getVersion()).isEqualTo(1);
        verify(accessGuard, times(1)).requireSupplier(10L);
        verify(evaluationMapper).update(ArgumentMatchers.isNull(), ArgumentMatchers.any());
    }

    /** 普通 PUT 即使携带旧版 ownerUserId 字段也必须明确拒绝。 */
    @Test
    void shouldRejectOwnerChangeThroughOrdinaryUpdate() {
        SrmRequests.UpdateSupplierRequest request = new SrmRequests.UpdateSupplierRequest();
        request.setVersion(0);
        request.setOwnerUserId(9L);

        assertThatThrownBy(() -> service.update(10L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(400);

        verifyNoInteractions(accessGuard, ownerResolver, supplierMapper);
    }

    private SrmSupplier supplier(Long id, String status, Long ownerUserId, Long ownerUnitId) {
        SrmSupplier supplier = new SrmSupplier();
        supplier.setId(id);
        supplier.setTenantId(3L);
        supplier.setStatus(status);
        supplier.setOwnerUserId(ownerUserId);
        supplier.setOwnerUnitId(ownerUnitId);
        supplier.setVersion(0);
        supplier.setDeleted(0);
        return supplier;
    }

    private SrmSupplier supplier(String status, int version) {
        SrmSupplier supplier = supplier(10L, status, 7L, 8L);
        supplier.setVersion(version);
        return supplier;
    }

    private static void initTableInfo(Class<?> entityClass, String namespace) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), namespace);
        assistant.setCurrentNamespace(namespace);
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
