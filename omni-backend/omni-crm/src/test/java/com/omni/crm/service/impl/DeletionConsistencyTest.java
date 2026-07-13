package com.omni.crm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.crm.dto.CrmRequests;
import com.omni.crm.entity.CrmContact;
import com.omni.crm.entity.CrmCustomer;
import com.omni.crm.entity.CrmOpportunity;
import com.omni.crm.mapper.CrmActivityMapper;
import com.omni.crm.mapper.CrmContactMapper;
import com.omni.crm.mapper.CrmCustomerMapper;
import com.omni.crm.mapper.CrmLeadConversionMapper;
import com.omni.crm.mapper.CrmLeadMapper;
import com.omni.crm.mapper.CrmOpportunityMapper;
import com.omni.crm.mapper.CrmOpportunityStageHistoryMapper;
import com.omni.crm.mapper.CrmOwnerChangeLogMapper;
import com.omni.crm.mapper.CrmPipelineStageMapper;
import com.omni.crm.security.CrmTenantContext;
import com.omni.crm.service.CrmTenantInitializer;
import com.omni.crm.service.support.CrmOwnerEnricher;
import com.omni.crm.service.support.CrmOwnerResolver;
import com.omni.crm.service.support.CrmPermissionScopeExecutor;
import com.omni.crm.service.support.CrmRecordAccessGuard;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

/** CRM 聚合删除一致性单元测试。 */
@ExtendWith(MockitoExtension.class)
class DeletionConsistencyTest {

    /** 初始化 Lambda Update 所需的 MyBatis-Plus 元数据。 */
    @BeforeAll
    static void initTableMetadata() {
        init(CrmCustomer.class);
        init(CrmContact.class);
        init(CrmOpportunity.class);
    }

    @Mock private CrmCustomerMapper customerMapper;
    @Mock private CrmContactMapper contactMapper;
    @Mock private CrmOpportunityMapper opportunityMapper;
    @Mock private CrmActivityMapper activityMapper;
    @Mock private CrmLeadConversionMapper conversionMapper;
    @Mock private CrmLeadMapper leadMapper;
    @Mock private CrmOwnerChangeLogMapper ownerLogMapper;
    @Mock private CrmOpportunityStageHistoryMapper historyMapper;
    @Mock private CrmPipelineStageMapper stageMapper;
    @Mock private CrmRecordAccessGuard accessGuard;
    @Mock private CrmOwnerResolver ownerResolver;
    @Mock private CrmOwnerEnricher ownerEnricher;
    @Mock private CrmPermissionScopeExecutor scopeExecutor;
    @Mock private CrmTenantInitializer tenantInitializer;
    @Mock private ReliableMessageRelay reliableMessageRelay;
    @InjectMocks private CustomerServiceImpl customerService;
    @InjectMocks private ContactServiceImpl contactService;
    @InjectMocks private OpportunityServiceImpl opportunityService;

    /** 清理租户请求上下文。 */
    @AfterEach
    void clear() {
        CrmTenantContext.clear();
    }

    /** 删除客户时同步清理联系人、客户活动，并解除保留商机上的联系人引用。 */
    @Test
    void shouldCleanCustomerChildrenAfterAuthorizedDelete() {
        identity();
        CrmCustomer customer = new CrmCustomer();
        customer.setId(10L);
        customer.setVersion(2);
        when(accessGuard.requireCustomer(10L)).thenReturn(customer);
        when(opportunityMapper.countAllOpenByCustomer(10L)).thenReturn(0L);
        when(customerMapper.update(any(), any())).thenReturn(1);

        customerService.delete(10L, 2);

        verify(activityMapper).clearContactReferencesByCustomer(eq(10L), any(), eq("sales"));
        verify(opportunityMapper).clearPrimaryContactReferencesByCustomer(eq(10L), any(), eq("sales"));
        verify(contactMapper).softDeleteByCustomer(eq(10L), any(), eq("sales"));
        verify(activityMapper).softDeleteByRoot(eq("CUSTOMER"), eq(10L), any(), eq("sales"));
    }

    /** 删除联系人时解除活动和商机上的联系人引用。 */
    @Test
    void shouldClearContactReferencesAfterAuthorizedDelete() {
        identity();
        CrmContact contact = new CrmContact();
        contact.setId(20L);
        contact.setVersion(3);
        when(accessGuard.requireContact(20L)).thenReturn(contact);
        when(contactMapper.update(any(), any())).thenReturn(1);

        contactService.delete(20L, 3);

        verify(activityMapper).clearContactReference(eq(20L), any(), eq("sales"));
        verify(opportunityMapper).clearPrimaryContactReference(eq(20L), any(), eq("sales"));
    }

    /** 删除开放商机时同步软删除其活动。 */
    @Test
    void shouldSoftDeleteOpportunityActivitiesAfterAuthorizedDelete() {
        identity();
        CrmOpportunity opportunity = new CrmOpportunity();
        opportunity.setId(30L);
        opportunity.setVersion(4);
        opportunity.setStatus("OPEN");
        when(accessGuard.requireOpportunity(30L)).thenReturn(opportunity);
        when(opportunityMapper.update(any(), any())).thenReturn(1);

        opportunityService.delete(30L, 4);

        verify(activityMapper).softDeleteByRoot(eq("OPPORTUNITY"), eq(30L), any(), eq("sales"));
    }

    /** 客户级联转移必须同步开放商机活动并逐条留下 owner 变更事实。 */
    @Test
    void shouldAuditEveryCascadedOpportunityOwnerChange() {
        identity();
        CrmCustomer customer = new CrmCustomer();
        customer.setId(10L); customer.setVersion(2); customer.setOwnerUserId(11L); customer.setOwnerUnitId(21L);
        CrmOpportunity opportunity = new CrmOpportunity();
        opportunity.setId(30L); opportunity.setOwnerUserId(12L); opportunity.setOwnerUnitId(22L);
        when(customerMapper.selectVisibleForUpdate(10L)).thenReturn(customer);
        when(accessGuard.requireCustomer(10L)).thenReturn(customer);
        when(ownerResolver.resolveForCommand(20L)).thenReturn(new CrmOwnerResolver.Owner(20L, 40L));
        when(customerMapper.update(any(), any())).thenReturn(1);
        when(opportunityMapper.selectAllOpenByCustomer(10L)).thenReturn(List.of(opportunity));
        when(ownerEnricher.enrichOne(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CrmRequests.TransferCustomerRequest request = new CrmRequests.TransferCustomerRequest();
        request.setOwnerUserId(20L); request.setVersion(2); request.setReason("团队调整");
        request.setCascadeOpenOpportunities(true);

        customerService.transfer(10L, request);

        verify(opportunityMapper).syncAllOpenOwnerByCustomer(eq(10L), eq(20L), eq(40L), any(), eq("sales"));
        verify(activityMapper).syncOwnerByOpportunityRoots(eq(List.of(30L)), eq(20L), eq(40L), any(), eq("sales"));
        verify(ownerLogMapper, times(2)).insert(any(com.omni.crm.entity.CrmOwnerChangeLog.class));
    }

    private static void init(Class<?> entityClass) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "crm-delete-test");
        assistant.setCurrentNamespace("com.omni.crm.delete." + entityClass.getSimpleName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }

    private void identity() {
        CrmTenantContext.set(new CrmTenantContext.RequestIdentity(12L, 3L, "sales"));
    }
}
