package com.omni.crm.service.impl;

import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.crm.dto.CrmRequests;
import com.omni.crm.dto.CrmViews;
import com.omni.crm.entity.CrmLead;
import com.omni.crm.entity.CrmLeadConversion;
import com.omni.crm.entity.CrmCustomer;
import com.omni.crm.entity.CrmContact;
import com.omni.crm.entity.CrmOpportunity;
import com.omni.crm.entity.CrmOpportunityStageHistory;
import com.omni.crm.entity.CrmPipelineStage;
import com.omni.crm.mapper.CrmActivityMapper;
import com.omni.crm.mapper.CrmContactMapper;
import com.omni.crm.mapper.CrmCustomerMapper;
import com.omni.crm.mapper.CrmLeadConversionMapper;
import com.omni.crm.mapper.CrmLeadMapper;
import com.omni.crm.mapper.CrmOpportunityMapper;
import com.omni.crm.mapper.CrmOpportunityStageHistoryMapper;
import com.omni.crm.mapper.CrmOwnerChangeLogMapper;
import com.omni.crm.mapper.CrmPipelineStageMapper;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.crm.service.CrmTenantInitializer;
import com.omni.crm.service.support.CrmOwnerEnricher;
import com.omni.crm.service.support.CrmOwnerResolver;
import com.omni.crm.service.support.CrmRecordAccessGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;

/** 线索转换幂等单元测试。 */
@ExtendWith(MockitoExtension.class)
class LeadServiceImplTest {

    /** 初始化纯单测使用的 MyBatis-Plus Lambda 元数据。 */
    @BeforeAll
    static void initTableMetadata() {
        init(CrmLead.class);
        init(CrmOpportunity.class);
        init(CrmPipelineStage.class);
    }

    @Mock private CrmLeadMapper leadMapper;
    @Mock private CrmLeadConversionMapper conversionMapper;
    @Mock private CrmCustomerMapper customerMapper;
    @Mock private CrmContactMapper contactMapper;
    @Mock private CrmOpportunityMapper opportunityMapper;
    @Mock private CrmOpportunityStageHistoryMapper historyMapper;
    @Mock private CrmPipelineStageMapper stageMapper;
    @Mock private CrmActivityMapper activityMapper;
    @Mock private CrmOwnerChangeLogMapper ownerLogMapper;
    @Mock private CrmRecordAccessGuard accessGuard;
    @Mock private CrmOwnerResolver ownerResolver;
    @Mock private CrmOwnerEnricher ownerEnricher;
    @Mock private CrmTenantInitializer tenantInitializer;
    @Mock private ReliableMessageRelay reliableMessageRelay;
    @InjectMocks private LeadServiceImpl leadService;

    /** 清理租户上下文。 */
    @AfterEach
    void clear() { ServiceIdentityContext.clear(); }

    /** 已存在 conversion 时返回原结果且不重复写业务或 Outbox。 */
    @Test
    void shouldReplayExistingConversionWithoutWrites() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(12L, 3L, "sales"));
        CrmLead lead = new CrmLead(); lead.setId(100L); lead.setStatus("CONVERTED"); lead.setVersion(4);
        CrmLeadConversion conversion = new CrmLeadConversion(); conversion.setId(200L); conversion.setLeadId(100L);
        conversion.setCustomerId(300L); conversion.setContactId(400L); conversion.setConvertedTime(LocalDateTime.now());
        when(leadMapper.selectVisibleForUpdate(100L)).thenReturn(lead);
        when(conversionMapper.selectOne(any())).thenReturn(conversion);
        CrmRequests.ConvertLeadRequest request = new CrmRequests.ConvertLeadRequest(); request.setVersion(1);

        CrmViews.ConversionResultVO result = leadService.convert(100L, request);

        assertThat(result.isIdempotentReplay()).isTrue();
        assertThat(result.getConversionId()).isEqualTo(200L);
        verify(customerMapper, never()).insert(any(CrmCustomer.class));
        verify(reliableMessageRelay, never()).send(any(), any(), any(), any());
    }

    /** 线索分配必须同步其活动 owner 快照。 */
    @Test
    void shouldSynchronizeLeadActivityOwnerOnAssign() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(12L, 3L, "sales"));
        CrmLead lead = new CrmLead(); lead.setId(100L); lead.setOwnerUserId(12L); lead.setOwnerUnitId(8L);
        lead.setVersion(2); lead.setStatus("FOLLOWING");
        when(accessGuard.requireLead(100L)).thenReturn(lead);
        when(ownerResolver.resolveForCommand(20L)).thenReturn(new CrmOwnerResolver.Owner(20L, 9L));
        when(leadMapper.update(any(), any())).thenReturn(1);
        when(ownerEnricher.enrichOne(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CrmRequests.AssignRequest request = new CrmRequests.AssignRequest(); request.setVersion(2); request.setOwnerUserId(20L);

        leadService.assign(100L, request);

        verify(activityMapper).syncOwnerByRoot(org.mockito.ArgumentMatchers.eq("LEAD"),
                org.mockito.ArgumentMatchers.eq(100L), org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.eq(9L), any(), org.mockito.ArgumentMatchers.eq("sales"));
    }

    /** 线索转换创建商机时必须同步写入首条阶段历史。 */
    @Test
    void shouldAppendInitialStageHistoryForConvertedOpportunity() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(12L, 3L, "sales"));
        CrmLead lead = new CrmLead();
        lead.setId(100L); lead.setTenantId(3L); lead.setOwnerUserId(12L); lead.setOwnerUnitId(8L);
        CrmCustomer customer = new CrmCustomer(); customer.setId(300L); customer.setName("示例客户");
        CrmContact contact = new CrmContact(); contact.setId(400L);
        CrmPipelineStage stage = new CrmPipelineStage();
        stage.setId(500L); stage.setStageType("OPEN"); stage.setProbability(BigDecimal.TEN);
        when(tenantInitializer.ensureInitialized()).thenReturn(600L);
        when(tenantInitializer.currencyCode()).thenReturn("CNY");
        when(stageMapper.selectOne(any())).thenReturn(stage);
        when(opportunityMapper.insert(any(CrmOpportunity.class))).thenAnswer(invocation -> {
            CrmOpportunity opportunity = invocation.getArgument(0);
            opportunity.setId(700L);
            return 1;
        });
        when(opportunityMapper.update(any(), any())).thenReturn(1);
        CrmRequests.ConvertLeadRequest request = new CrmRequests.ConvertLeadRequest();
        request.setCreateOpportunity(true);

        CrmOpportunity opportunity = ReflectionTestUtils.invokeMethod(
                leadService, "createOpportunity", lead, customer, contact, request);

        assertThat(opportunity).isNotNull();
        ArgumentCaptor<CrmOpportunityStageHistory> historyCaptor =
                ArgumentCaptor.forClass(CrmOpportunityStageHistory.class);
        verify(historyMapper).insert(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getOpportunityId()).isEqualTo(700L);
        assertThat(historyCaptor.getValue().getToStageId()).isEqualTo(500L);
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo("OPEN");
        assertThat(historyCaptor.getValue().getChangeReason()).isEqualTo("CONVERSION_CREATE");
    }

    private static void init(Class<?> entityClass) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "crm-test");
        assistant.setCurrentNamespace("com.omni.crm.test." + entityClass.getSimpleName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
