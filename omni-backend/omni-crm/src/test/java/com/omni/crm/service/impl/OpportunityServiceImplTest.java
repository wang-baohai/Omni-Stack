package com.omni.crm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.crm.dto.CrmRequests;
import com.omni.crm.entity.CrmOpportunity;
import com.omni.crm.entity.CrmPipelineStage;
import com.omni.crm.mapper.CrmActivityMapper;
import com.omni.crm.mapper.CrmContactMapper;
import com.omni.crm.mapper.CrmCustomerMapper;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 商机命令和看板查询边界测试。 */
@ExtendWith(MockitoExtension.class)
class OpportunityServiceImplTest {

    /** 初始化 Lambda Query 和 Update 所需的 MyBatis-Plus 元数据。 */
    @BeforeAll
    static void initTableMetadata() {
        init(CrmOpportunity.class);
        init(CrmPipelineStage.class);
    }

    @Mock private CrmOpportunityMapper opportunityMapper;
    @Mock private CrmPipelineStageMapper stageMapper;
    @Mock private CrmOpportunityStageHistoryMapper historyMapper;
    @Mock private CrmCustomerMapper customerMapper;
    @Mock private CrmContactMapper contactMapper;
    @Mock private CrmActivityMapper activityMapper;
    @Mock private CrmOwnerChangeLogMapper ownerLogMapper;
    @Mock private CrmRecordAccessGuard accessGuard;
    @Mock private CrmOwnerResolver ownerResolver;
    @Mock private CrmTenantInitializer tenantInitializer;
    @Mock private CrmOwnerEnricher ownerEnricher;
    @Mock private ReliableMessageRelay reliableMessageRelay;
    @InjectMocks private OpportunityServiceImpl opportunityService;

    /** 清理租户请求上下文。 */
    @AfterEach
    void clear() {
        ServiceIdentityContext.clear();
    }

    /** 赢单命令必须通过保留租户隔离的专用 Mapper 激活潜客。 */
    @Test
    void shouldActivatePotentialCustomerAfterWin() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(12L, 3L, "sales"));
        CrmOpportunity opportunity = opportunity();
        CrmPipelineStage from = stage(10L, "OPEN", 10);
        CrmPipelineStage won = stage(20L, "WON", 50);
        when(opportunityMapper.selectVisibleForUpdate(30L)).thenReturn(opportunity);
        when(stageMapper.selectOne(any())).thenReturn(from, won);
        when(opportunityMapper.update(any(), any())).thenReturn(1);
        when(accessGuard.requireOpportunity(30L)).thenReturn(opportunity);
        when(ownerEnricher.enrichOne(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CrmRequests.OpportunityStageRequest request = new CrmRequests.OpportunityStageRequest();
        request.setVersion(4);
        request.setStageId(20L);
        request.setReason("客户确认签约");

        opportunityService.changeStage(30L, request);

        verify(customerMapper).activatePotentialAfterOpportunityWin(eq(40L), any(), eq("sales"));
    }

    /** 看板查询必须通过分页拦截器将单次结果限制为一百条。 */
    @Test
    void shouldUseBoundedPageForBoard() {
        CrmPipelineStage stage = stage(10L, "OPEN", 10);
        when(stageMapper.selectList(any())).thenReturn(List.of(stage));
        Page<CrmOpportunity> result = new Page<>(1, 100, false);
        result.setRecords(List.of(opportunity()));
        when(opportunityMapper.selectPage(
                org.mockito.ArgumentMatchers.<Page<CrmOpportunity>>any(),
                org.mockito.ArgumentMatchers.<Wrapper<CrmOpportunity>>any())).thenReturn(result);
        when(ownerEnricher.enrich(any())).thenAnswer(invocation -> invocation.getArgument(0));

        opportunityService.board(5L, new CrmRequests.OpportunityQuery());

        verify(opportunityMapper).selectPage(
                org.mockito.ArgumentMatchers.<Page<CrmOpportunity>>any(),
                org.mockito.ArgumentMatchers.<Wrapper<CrmOpportunity>>any());
        verify(opportunityMapper, never()).selectList(any());
    }

    private CrmOpportunity opportunity() {
        CrmOpportunity opportunity = new CrmOpportunity();
        opportunity.setId(30L);
        opportunity.setCustomerId(40L);
        opportunity.setPipelineId(5L);
        opportunity.setStageId(10L);
        opportunity.setStatus("OPEN");
        opportunity.setVersion(4);
        return opportunity;
    }

    private CrmPipelineStage stage(Long id, String type, int sort) {
        CrmPipelineStage stage = new CrmPipelineStage();
        stage.setId(id);
        stage.setPipelineId(5L);
        stage.setStageType(type);
        stage.setSort(sort);
        stage.setProbability(BigDecimal.valueOf(sort));
        stage.setStatus(1);
        return stage;
    }

    private static void init(Class<?> entityClass) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "crm-opportunity-test");
        assistant.setCurrentNamespace("com.omni.crm.opportunity." + entityClass.getSimpleName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
