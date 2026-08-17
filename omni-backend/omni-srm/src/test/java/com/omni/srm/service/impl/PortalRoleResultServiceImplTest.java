package com.omni.srm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.srm.dto.PortalRoleResultEvent;
import com.omni.srm.entity.SrmSupplier;
import com.omni.srm.entity.SrmSupplierEnrollment;
import com.omni.srm.entity.SrmSupplierPortalUser;
import com.omni.srm.mapper.SrmSupplierEnrollmentMapper;
import com.omni.srm.mapper.SrmSupplierMapper;
import com.omni.srm.mapper.SrmSupplierPortalUserMapper;
import com.omni.srm.security.SrmTenantContext;
import com.omni.srm.workflow.SupplierWorkflowCoordinator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 门户角色分配结果 Saga 测试。
 */
@ExtendWith(MockitoExtension.class)
class PortalRoleResultServiceImplTest {

    @Mock private SrmSupplierEnrollmentMapper enrollmentMapper;
    @Mock private SrmSupplierPortalUserMapper portalUserMapper;
    @Mock private SrmSupplierMapper supplierMapper;
    @Mock private SupplierWorkflowCoordinator workflowCoordinator;

    private PortalRoleResultServiceImpl service;

    @BeforeEach
    void setUp() {
        initTableInfo(SrmSupplier.class);
        initTableInfo(SrmSupplierEnrollment.class);
        initTableInfo(SrmSupplierPortalUser.class);
        service = new PortalRoleResultServiceImpl(enrollmentMapper, portalUserMapper, supplierMapper, workflowCoordinator);
        SrmTenantContext.set(new SrmTenantContext.RequestIdentity(20L, 1L, "portal-role-saga"));
    }

    @AfterEach
    void tearDown() {
        SrmTenantContext.clear();
    }

    @Test
    void shouldCreatePortalRelationOnlyAfterRoleAssignmentSuccess() {
        when(enrollmentMapper.selectOne(any())).thenReturn(enrollment("PENDING_ROLE_ASSIGN"));
        when(portalUserMapper.selectOne(any())).thenReturn(null);
        when(portalUserMapper.insert(any(SrmSupplierPortalUser.class))).thenReturn(1);
        when(supplierMapper.selectOne(any())).thenReturn(supplier("REGISTERING"));
        when(supplierMapper.update(eq(null), any())).thenReturn(1);
        when(enrollmentMapper.update(eq(null), any())).thenReturn(1);

        service.handle(event("SUCCESS", null));

        verify(portalUserMapper).insert(any(SrmSupplierPortalUser.class));
        verify(supplierMapper).update(eq(null), any());
        verify(enrollmentMapper).update(eq(null), any());
    }

    @Test
    void shouldNotDuplicatePortalRelationForCompletedEventReplay() {
        when(enrollmentMapper.selectOne(any())).thenReturn(enrollment("COMPLETED"));

        service.handle(event("SUCCESS", null));

        verify(portalUserMapper, never()).insert(any(SrmSupplierPortalUser.class));
        verify(supplierMapper, never()).update(eq(null), any());
        verify(enrollmentMapper, never()).update(eq(null), any());
    }

    @Test
    void shouldReactivateExistingPortalRelationForSameSupplier() {
        when(enrollmentMapper.selectOne(any())).thenReturn(enrollment("PENDING_ROLE_ASSIGN"));
        SrmSupplierPortalUser portalUser = new SrmSupplierPortalUser();
        portalUser.setId(7L);
        portalUser.setTenantId(1L);
        portalUser.setSupplierId(10L);
        portalUser.setUserId(20L);
        portalUser.setStatus("INACTIVE");
        portalUser.setVersion(2);
        portalUser.setDeleted(0);
        when(portalUserMapper.selectOne(any())).thenReturn(portalUser);
        when(portalUserMapper.update(eq(null), any())).thenReturn(1);
        when(supplierMapper.selectOne(any())).thenReturn(supplier("REGISTERING"));
        when(supplierMapper.update(eq(null), any())).thenReturn(1);
        when(enrollmentMapper.update(eq(null), any())).thenReturn(1);

        service.handle(event("SUCCESS", null));

        verify(portalUserMapper, never()).insert(any(SrmSupplierPortalUser.class));
        verify(portalUserMapper).update(eq(null), any());
        verify(enrollmentMapper).update(eq(null), any());
    }

    @Test
    void shouldRequestMqRedeliveryWhenConcurrentPortalInsertLosesUniqueRace() {
        when(enrollmentMapper.selectOne(any())).thenReturn(enrollment("PENDING_ROLE_ASSIGN"));
        when(portalUserMapper.selectOne(any())).thenReturn(null);
        when(portalUserMapper.insert(any(SrmSupplierPortalUser.class)))
                .thenThrow(new DuplicateKeyException("uk_srm_portal_user"));

        assertThrows(IllegalStateException.class, () -> service.handle(event("SUCCESS", null)));

        verify(supplierMapper, never()).update(eq(null), any());
        verify(enrollmentMapper, never()).update(eq(null), any());
    }

    @Test
    void shouldMarkEnrollmentAndSupplierFailedWithoutPortalRelation() {
        when(enrollmentMapper.selectOne(any())).thenReturn(enrollment("PENDING_ROLE_ASSIGN"));
        when(supplierMapper.selectOne(any())).thenReturn(supplier("REGISTERING"));
        when(supplierMapper.update(eq(null), any())).thenReturn(1);
        when(enrollmentMapper.update(eq(null), any())).thenReturn(1);

        service.handle(event("FAILED", "USER_NOT_FOUND_OR_DISABLED"));

        verify(portalUserMapper, never()).insert(any(SrmSupplierPortalUser.class));
        verify(supplierMapper).update(eq(null), any());
        verify(enrollmentMapper).update(eq(null), any());
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
        supplier.setStatus(status);
        supplier.setVersion(0);
        supplier.setDeleted(0);
        return supplier;
    }

    private PortalRoleResultEvent event(String result, String errorCode) {
        PortalRoleResultEvent event = new PortalRoleResultEvent();
        event.setEventId("event-1");
        event.setEventType("SUCCESS".equals(result)
                ? "auth.portal-role.assigned.v1" : "auth.portal-role.assign-failed.v1");
        event.setRequestId("request-1");
        event.setTenantId(1L);
        event.setSupplierId(10L);
        event.setUserId(20L);
        event.setRoleCode("SUPPLIER");
        event.setResult(result);
        event.setErrorCode(errorCode);
        return event;
    }

    private void initTableInfo(Class<?> entityClass) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "portal-role-result-test");
        assistant.setCurrentNamespace("portal-role-result-test");
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
