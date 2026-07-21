package com.omni.srm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.srm.dto.SrmRequests;
import com.omni.srm.dto.SrmViews;
import com.omni.srm.entity.SrmSupplier;
import com.omni.srm.entity.SrmSupplierEnrollment;
import com.omni.srm.entity.SrmSupplierPortalUser;
import com.omni.srm.mapper.SrmSupplierEnrollmentMapper;
import com.omni.srm.mapper.SrmSupplierMapper;
import com.omni.srm.mapper.SrmSupplierPortalUserMapper;
import com.omni.srm.security.SrmDataScopeContext;
import com.omni.srm.security.SrmTenantContext;
import com.omni.srm.service.PortalInviteService;
import com.omni.srm.service.support.SupplierRiskInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 门户入驻 Saga 发起服务测试。
 */
@ExtendWith(MockitoExtension.class)
class SupplierPortalServiceImplTest {

    @Mock private SrmSupplierMapper supplierMapper;
    @Mock private SrmSupplierPortalUserMapper portalUserMapper;
    @Mock private SrmSupplierEnrollmentMapper enrollmentMapper;
    @Mock private PortalInviteService portalInviteService;
    @Mock private SupplierRiskInitializer supplierRiskInitializer;
    @Mock private ReliableMessageRelay reliableMessageRelay;

    private SupplierPortalServiceImpl service;

    @BeforeEach
    void setUp() {
        initTableInfo(SrmSupplier.class);
        initTableInfo(SrmSupplierEnrollment.class);
        initTableInfo(SrmSupplierPortalUser.class);
        service = new SupplierPortalServiceImpl(supplierMapper, portalUserMapper, enrollmentMapper,
                portalInviteService, supplierRiskInitializer, reliableMessageRelay);
        SrmTenantContext.set(new SrmTenantContext.RequestIdentity(20L, 1L, "supplier-user"));
    }

    @AfterEach
    void tearDown() {
        SrmDataScopeContext.clear();
        SrmTenantContext.clear();
    }

    @Test
    void shouldCreatePendingEnrollmentWithoutPortalRelationOrInternalOwner() {
        when(enrollmentMapper.selectOne(any())).thenReturn(null);
        when(portalUserMapper.selectOne(any())).thenReturn(null);
        when(supplierMapper.selectOne(any())).thenReturn(null);
        when(portalInviteService.consumeInviteToken("invite-token")).thenReturn(8L);
        doAnswer(invocation -> {
            SrmSupplier supplier = invocation.getArgument(0);
            supplier.setId(10L);
            return 1;
        }).when(supplierMapper).insert(any(SrmSupplier.class));
        when(supplierMapper.update(eq(null), any())).thenReturn(1);
        when(enrollmentMapper.insert(any(SrmSupplierEnrollment.class))).thenReturn(1);

        SrmViews.EnrollmentVO result = service.enroll(enrollRequest());

        assertEquals("request-1", result.getRequestId());
        assertEquals(10L, result.getSupplierId());
        assertEquals("PENDING_ROLE_ASSIGN", result.getStatus());
        ArgumentCaptor<SrmSupplier> supplierCaptor = ArgumentCaptor.forClass(SrmSupplier.class);
        verify(supplierMapper).insert(supplierCaptor.capture());
        assertEquals("REGISTERING", supplierCaptor.getValue().getStatus());
        assertNull(supplierCaptor.getValue().getOwnerUserId());
        assertNull(supplierCaptor.getValue().getOwnerUnitId());
        verify(supplierRiskInitializer).initialize(1L, 10L);
        verify(portalUserMapper, never()).insert(any(SrmSupplierPortalUser.class));
        verify(reliableMessageRelay).send(eq("srm-domain-out-0"), any(), eq(1L), anyString());
    }

    @Test
    void shouldReturnOriginalEnrollmentForSameRequestWithoutConsumingInviteAgain() {
        SrmSupplierEnrollment enrollment = enrollment("PENDING_ROLE_ASSIGN");
        when(enrollmentMapper.selectOne(any())).thenReturn(enrollment);
        SrmSupplier supplier = supplier("REGISTERING");
        when(supplierMapper.selectOne(any())).thenReturn(supplier);

        SrmViews.EnrollmentVO result = service.enroll(enrollRequest());

        assertEquals("request-1", result.getRequestId());
        verify(portalInviteService, never()).consumeInviteToken(anyString());
        verify(supplierMapper, never()).insert(any(SrmSupplier.class));
    }

    @Test
    void shouldRetryFailedEnrollmentWithSameRequestId() {
        SrmSupplierEnrollment enrollment = enrollment("ROLE_ASSIGN_FAILED");
        enrollment.setRetryCount(1);
        when(enrollmentMapper.selectOne(any())).thenReturn(enrollment);
        when(enrollmentMapper.update(eq(null), any())).thenReturn(1);
        when(supplierMapper.update(eq(null), any())).thenReturn(1);
        when(supplierMapper.selectOne(any())).thenReturn(supplier("REGISTERING"));

        SrmViews.EnrollmentVO result = service.retryEnrollment();

        assertEquals("PENDING_ROLE_ASSIGN", result.getStatus());
        assertEquals(2, result.getRetryCount());
        verify(reliableMessageRelay).send(eq("srm-domain-out-0"), any(), eq(1L), anyString());
    }

    @Test
    void shouldRejectRetryBeforeNextRetryTime() {
        SrmSupplierEnrollment enrollment = enrollment("ROLE_ASSIGN_FAILED");
        enrollment.setNextRetryTime(LocalDateTime.now().plusMinutes(1));
        when(enrollmentMapper.selectOne(any())).thenReturn(enrollment);

        BusinessException exception = assertThrows(BusinessException.class, service::retryEnrollment);

        assertEquals(429, exception.getCode());
        verify(enrollmentMapper, never()).update(eq(null), any());
        verify(reliableMessageRelay, never()).send(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void shouldReturnConflictWhenConcurrentEnrollmentHitsUniqueConstraint() {
        when(enrollmentMapper.selectOne(any())).thenReturn(null);
        when(portalUserMapper.selectOne(any())).thenReturn(null);
        when(supplierMapper.selectOne(any())).thenReturn(null);
        when(portalInviteService.consumeInviteToken("invite-token")).thenReturn(8L);
        when(supplierMapper.insert(any(SrmSupplier.class)))
                .thenThrow(new DuplicateKeyException("uk_srm_supplier_credit"));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.enroll(enrollRequest()));

        assertEquals(409, exception.getCode());
        verify(enrollmentMapper, never()).insert(any(SrmSupplierEnrollment.class));
        verify(reliableMessageRelay, never()).send(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void shouldResubmitRejectedSupplierOwnedByCurrentPortalUser() {
        SrmSupplierPortalUser portalUser = portalUser();
        when(portalUserMapper.selectOne(any())).thenReturn(portalUser);
        SrmSupplier supplier = supplier("REJECTED");
        supplier.setName("Example Supplier");
        supplier.setVersion(4);
        when(supplierMapper.selectOne(any())).thenReturn(supplier);
        when(supplierMapper.update(eq(null), any())).thenReturn(1);
        SrmRequests.StatusRequest request = new SrmRequests.StatusRequest();
        request.setVersion(4);

        SrmViews.PortalProfileVO result = service.resubmitProfile(request);

        assertEquals("PENDING_REVIEW", result.getStatus());
        assertEquals(5, result.getVersion());
        verify(supplierMapper).update(eq(null), any());
    }

    @Test
    void shouldRejectPortalResubmitWhenSupplierIsNotRejected() {
        when(portalUserMapper.selectOne(any())).thenReturn(portalUser());
        when(supplierMapper.selectOne(any())).thenReturn(supplier("APPROVED"));
        SrmRequests.StatusRequest request = new SrmRequests.StatusRequest();
        request.setVersion(0);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.resubmitProfile(request));

        assertEquals(409, exception.getCode());
        verify(supplierMapper, never()).update(eq(null), any());
    }

    private SrmRequests.EnrollRequest enrollRequest() {
        SrmRequests.EnrollRequest request = new SrmRequests.EnrollRequest();
        request.setRequestId("request-1");
        request.setInviteToken("invite-token");
        request.setName("Example Supplier");
        request.setCreditCode("credit-1");
        return request;
    }

    private SrmSupplierEnrollment enrollment(String status) {
        SrmSupplierEnrollment enrollment = new SrmSupplierEnrollment();
        enrollment.setId(5L);
        enrollment.setTenantId(1L);
        enrollment.setSupplierId(10L);
        enrollment.setUserId(20L);
        enrollment.setRequestId("request-1");
        enrollment.setStatus(status);
        enrollment.setRetryCount(0);
        enrollment.setVersion(0);
        enrollment.setDeleted(0);
        return enrollment;
    }

    private SrmSupplier supplier(String status) {
        SrmSupplier supplier = new SrmSupplier();
        supplier.setId(10L);
        supplier.setTenantId(1L);
        supplier.setCreditCode("CREDIT-1");
        supplier.setStatus(status);
        supplier.setVersion(0);
        supplier.setDeleted(0);
        return supplier;
    }

    private SrmSupplierPortalUser portalUser() {
        SrmSupplierPortalUser portalUser = new SrmSupplierPortalUser();
        portalUser.setTenantId(1L);
        portalUser.setSupplierId(10L);
        portalUser.setUserId(20L);
        portalUser.setStatus("ACTIVE");
        portalUser.setVersion(0);
        portalUser.setDeleted(0);
        return portalUser;
    }

    private void initTableInfo(Class<?> entityClass) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "supplier-portal-test");
        assistant.setCurrentNamespace("supplier-portal-test");
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
