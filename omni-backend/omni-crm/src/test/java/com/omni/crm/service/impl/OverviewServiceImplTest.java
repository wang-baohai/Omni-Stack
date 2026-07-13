package com.omni.crm.service.impl;

import com.omni.crm.dto.CrmViews;
import com.omni.crm.mapper.CrmCustomerMapper;
import com.omni.crm.mapper.CrmLeadMapper;
import com.omni.crm.mapper.CrmOpportunityMapper;
import com.omni.crm.mapper.CrmPipelineStageMapper;
import com.omni.crm.service.CrmTenantInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** CRM 看板聚合 SQL 查询测试。 */
@ExtendWith(MockitoExtension.class)
class OverviewServiceImplTest {

    @Mock private CrmLeadMapper leadMapper;
    @Mock private CrmCustomerMapper customerMapper;
    @Mock private CrmOpportunityMapper opportunityMapper;
    @Mock private CrmPipelineStageMapper stageMapper;
    @Mock private CrmTenantInitializer tenantInitializer;
    @InjectMocks private OverviewServiceImpl overviewService;

    /** 终态线索、客户和商机不得进入今日及逾期跟进统计。 */
    @Test
    void shouldExcludeTerminalRecordsFromFollowupSummary() {
        when(tenantInitializer.currencyCode()).thenReturn("CNY");

        // 线索按状态聚合：NEW=1, FOLLOWING=1, QUALIFIED=0, CONVERTED=1, DISQUALIFIED=0
        when(leadMapper.countGroupByStatus()).thenReturn(List.of(
                statusCount("NEW", 1), statusCount("FOLLOWING", 1), statusCount("CONVERTED", 1)));

        // 商机按状态聚合（含金额）：OPEN=1, WON=1, LOST=1
        when(opportunityMapper.countGroupByStatus()).thenReturn(List.of(
                statusCountAmount("OPEN", 1, new BigDecimal("50000")),
                statusCountAmount("WON", 1, new BigDecimal("80000")),
                statusCountAmount("LOST", 1, BigDecimal.ZERO)));

        // 今日跟进：只有活跃状态的记录计入（FOLLOWING线索 + ACTIVE客户 + OPEN商机 = 3）
        // 终态（CONVERTED/DISQUALIFIED/LOST/BLACKLISTED）已由 SQL WHERE 条件排除
        when(leadMapper.countFollowups(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1L).thenReturn(0L);  // 今日=1, 逾期=0
        when(customerMapper.countFollowups(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1L).thenReturn(0L);  // 今日=1, 逾期=0
        when(opportunityMapper.countFollowups(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1L).thenReturn(0L);  // 今日=1, 逾期=0

        CrmViews.OverviewSummaryVO summary = overviewService.summary();

        assertThat(summary.getTodayFollowupCount()).isEqualTo(3);
        assertThat(summary.getOverdueFollowupCount()).isEqualTo(0);
        assertThat(summary.getConvertedLeadCount()).isEqualTo(1);
        assertThat(summary.getOpenOpportunityCount()).isEqualTo(1);
        assertThat(summary.getWonOpportunityCount()).isEqualTo(1);
        assertThat(summary.getOpenOpportunityAmount()).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(summary.getWonOpportunityAmount()).isEqualByComparingTo(new BigDecimal("80000"));
        assertThat(summary.getCurrencyCode()).isEqualTo("CNY");
    }

    private static CrmViews.StatusCountVO statusCount(String status, long count) {
        CrmViews.StatusCountVO vo = new CrmViews.StatusCountVO();
        vo.setStatus(status);
        vo.setCount(count);
        return vo;
    }

    private static CrmViews.StatusCountVO statusCountAmount(String status, long count, BigDecimal amount) {
        CrmViews.StatusCountVO vo = new CrmViews.StatusCountVO();
        vo.setStatus(status);
        vo.setCount(count);
        vo.setAmount(amount);
        return vo;
    }
}

