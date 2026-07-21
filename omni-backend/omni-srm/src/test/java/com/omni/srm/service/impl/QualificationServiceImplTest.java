package com.omni.srm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.common.core.result.BusinessException;
import com.omni.srm.dto.SrmRequests;
import com.omni.srm.entity.SrmSupplier;
import com.omni.srm.entity.SrmSupplierQualification;
import com.omni.srm.mapper.SrmSupplierQualificationMapper;
import com.omni.srm.security.SrmTenantContext;
import com.omni.srm.service.RiskService;
import com.omni.srm.service.support.SrmRecordAccessGuard;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 供应商资质日期不变量测试。 */
@ExtendWith(MockitoExtension.class)
class QualificationServiceImplTest {

    @Mock private SrmSupplierQualificationMapper qualificationMapper;
    @Mock private SrmRecordAccessGuard accessGuard;
    @Mock private RiskService riskService;

    /** 初始化 MyBatis-Plus Lambda 元数据。 */
    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "srm-qualification-test");
        assistant.setCurrentNamespace("com.omni.srm.mapper.SrmSupplierQualificationMapper");
        TableInfoHelper.initTableInfo(assistant, SrmSupplierQualification.class);
    }

    /** 清理租户上下文。 */
    @AfterEach
    void clearContext() {
        SrmTenantContext.clear();
    }

    /** 到期日早于签发日时必须拒绝持久化。 */
    @Test
    void shouldRejectExpiryDateBeforeIssueDate() {
        when(accessGuard.requireSupplier(10L)).thenReturn(supplier(10L, "PENDING_REVIEW"));
        SrmRequests.CreateQualificationRequest request = new SrmRequests.CreateQualificationRequest();
        request.setQualificationName("质量体系认证");
        request.setIssueDate(LocalDate.of(2026, 7, 17));
        request.setExpiryDate(LocalDate.of(2026, 7, 16));
        QualificationServiceImpl service = new QualificationServiceImpl(
                qualificationMapper, accessGuard, riskService);

        assertThatThrownBy(() -> service.create(10L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(400);
        verify(qualificationMapper, never()).insert(
                org.mockito.ArgumentMatchers.any(SrmSupplierQualification.class));
        verify(riskService, never()).createAssessment(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(SrmRequests.CreateRiskAssessmentRequest.class));
    }

    /** 资质创建成功后必须在同一事务链路中刷新证书风险和综合风险。 */
    @Test
    void shouldRecalculateCertificateRiskAfterCreate() {
        SrmTenantContext.set(new SrmTenantContext.RequestIdentity(7L, 3L, "buyer"));
        when(accessGuard.requireSupplier(10L)).thenReturn(supplier(10L, "APPROVED"));
        SrmRequests.CreateQualificationRequest request = new SrmRequests.CreateQualificationRequest();
        request.setQualificationName("质量体系认证");
        request.setExpiryDate(LocalDate.of(2026, 8, 1));
        QualificationServiceImpl service = new QualificationServiceImpl(
                qualificationMapper, accessGuard, riskService);

        var result = service.create(10L, request);

        ArgumentCaptor<SrmRequests.CreateRiskAssessmentRequest> captor =
                ArgumentCaptor.forClass(SrmRequests.CreateRiskAssessmentRequest.class);
        verify(riskService).createAssessment(eq(10L), captor.capture());
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(captor.getValue().getRemark()).contains("资质创建后自动重算");
    }

    /** 资质更新成功后必须刷新证书风险。 */
    @Test
    void shouldRecalculateCertificateRiskAfterUpdate() {
        SrmTenantContext.set(new SrmTenantContext.RequestIdentity(7L, 3L, "buyer"));
        when(accessGuard.requireSupplier(10L)).thenReturn(supplier(10L, "APPROVED"));
        when(accessGuard.requireQualification(5L)).thenReturn(qualification(5L, 10L));
        when(qualificationMapper.update(
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any())).thenReturn(1);
        SrmRequests.UpdateQualificationRequest request = new SrmRequests.UpdateQualificationRequest();
        request.setVersion(0);
        request.setExpiryDate(LocalDate.of(2026, 8, 1));
        QualificationServiceImpl service = new QualificationServiceImpl(
                qualificationMapper, accessGuard, riskService);

        service.update(10L, 5L, request);

        verify(riskService).createAssessment(
                eq(10L), org.mockito.ArgumentMatchers.any(SrmRequests.CreateRiskAssessmentRequest.class));
    }

    /** 资质删除成功后必须刷新证书风险。 */
    @Test
    void shouldRecalculateCertificateRiskAfterDelete() {
        SrmTenantContext.set(new SrmTenantContext.RequestIdentity(7L, 3L, "buyer"));
        when(accessGuard.requireSupplier(10L)).thenReturn(supplier(10L, "APPROVED"));
        when(accessGuard.requireQualification(5L)).thenReturn(qualification(5L, 10L));
        when(qualificationMapper.update(
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any())).thenReturn(1);
        QualificationServiceImpl service = new QualificationServiceImpl(
                qualificationMapper, accessGuard, riskService);

        service.delete(10L, 5L, 0);

        verify(riskService).createAssessment(
                eq(10L), org.mockito.ArgumentMatchers.any(SrmRequests.CreateRiskAssessmentRequest.class));
    }

    /** 未准入供应商维护资质时不得提前生成综合风险评估。 */
    @Test
    void shouldNotCreateAssessmentBeforeSupplierApproval() {
        SrmTenantContext.set(new SrmTenantContext.RequestIdentity(7L, 3L, "buyer"));
        when(accessGuard.requireSupplier(10L)).thenReturn(supplier(10L, "PENDING_REVIEW"));
        SrmRequests.CreateQualificationRequest request = new SrmRequests.CreateQualificationRequest();
        request.setQualificationName("质量体系认证");
        QualificationServiceImpl service = new QualificationServiceImpl(
                qualificationMapper, accessGuard, riskService);

        service.create(10L, request);

        verify(riskService, never()).createAssessment(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(SrmRequests.CreateRiskAssessmentRequest.class));
    }

    private SrmSupplierQualification qualification(Long id, Long supplierId) {
        SrmSupplierQualification qualification = new SrmSupplierQualification();
        qualification.setId(id);
        qualification.setSupplierId(supplierId);
        qualification.setVersion(0);
        qualification.setDeleted(0);
        return qualification;
    }

    private SrmSupplier supplier(Long id, String status) {
        SrmSupplier supplier = new SrmSupplier();
        supplier.setId(id);
        supplier.setStatus(status);
        return supplier;
    }
}
