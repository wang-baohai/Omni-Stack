package com.omni.crm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.crm.dto.CrmViews;
import com.omni.crm.entity.CrmPipelineStage;
import com.omni.crm.mapper.CrmCustomerMapper;
import com.omni.crm.mapper.CrmLeadMapper;
import com.omni.crm.mapper.CrmOpportunityMapper;
import com.omni.crm.mapper.CrmPipelineStageMapper;
import com.omni.crm.service.CrmTenantInitializer;
import com.omni.crm.service.OverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** CRM 基础看板服务实现（聚合 SQL 优化版）。 */
@Service
@RequiredArgsConstructor
public class OverviewServiceImpl implements OverviewService {

    private final CrmLeadMapper leadMapper;
    private final CrmCustomerMapper customerMapper;
    private final CrmOpportunityMapper opportunityMapper;
    private final CrmPipelineStageMapper stageMapper;
    private final CrmTenantInitializer tenantInitializer;

    /** {@inheritDoc} */
    @Override
    public CrmViews.OverviewSummaryVO summary() {
        tenantInitializer.ensureInitialized();
        String currencyCode = tenantInitializer.currencyCode();

        // 线索按状态聚合
        Map<String, Long> leadStatusCounts = leadMapper.countGroupByStatus().stream()
                .collect(Collectors.toMap(CrmViews.StatusCountVO::getStatus, CrmViews.StatusCountVO::getCount));
        long totalLeads = leadStatusCounts.values().stream().mapToLong(Long::longValue).sum();
        long convertedLeads = leadStatusCounts.getOrDefault("CONVERTED", 0L);

        // 商机按状态聚合（含金额）
        Map<String, CrmViews.StatusCountVO> oppStatusMap = opportunityMapper.countGroupByStatus().stream()
                .collect(Collectors.toMap(CrmViews.StatusCountVO::getStatus, v -> v));
        long openCount = getCount(oppStatusMap, "OPEN");
        BigDecimal openAmount = getAmount(oppStatusMap, "OPEN");
        long wonCount = getCount(oppStatusMap, "WON");
        BigDecimal wonAmount = getAmount(oppStatusMap, "WON");
        long lostCount = getCount(oppStatusMap, "LOST");

        // 待跟进统计：今日 + 逾期
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        // MySQL DATETIME 范围为 1000-01-01 ~ 9999-12-31，不可用 LocalDateTime.MIN
        LocalDateTime farPast = LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime overdueEnd = todayStart.minusSeconds(1);
        long todayFollowups = leadMapper.countFollowups(todayStart, todayEnd)
                + customerMapper.countFollowups(todayStart, todayEnd)
                + opportunityMapper.countFollowups(todayStart, todayEnd);
        long overdueFollowups = leadMapper.countFollowups(farPast, overdueEnd)
                + customerMapper.countFollowups(farPast, overdueEnd)
                + opportunityMapper.countFollowups(farPast, overdueEnd);

        CrmViews.OverviewSummaryVO vo = new CrmViews.OverviewSummaryVO();
        vo.setNewLeadCount(leadStatusCounts.getOrDefault("NEW", 0L));
        vo.setQualifiedLeadCount(leadStatusCounts.getOrDefault("QUALIFIED", 0L));
        vo.setConvertedLeadCount(convertedLeads);
        vo.setOpenOpportunityCount(openCount);
        vo.setOpenOpportunityAmount(openAmount);
        vo.setWonOpportunityCount(wonCount);
        vo.setWonOpportunityAmount(wonAmount);
        vo.setTodayFollowupCount(todayFollowups);
        vo.setOverdueFollowupCount(overdueFollowups);
        vo.setLeadConversionRate(rate(convertedLeads, totalLeads));
        vo.setOpportunityWinRate(rate(wonCount, wonCount + lostCount));
        vo.setCurrencyCode(currencyCode);
        return vo;
    }

    /** {@inheritDoc} */
    @Override
    public List<CrmViews.FunnelItemVO> funnel(Long pipelineId) {
        Long selected = pipelineId == null ? tenantInitializer.ensureInitialized() : pipelineId;
        String currencyCode = tenantInitializer.currencyCode();
        List<CrmPipelineStage> stages = stageMapper.selectList(new LambdaQueryWrapper<CrmPipelineStage>()
                .eq(CrmPipelineStage::getPipelineId, selected).eq(CrmPipelineStage::getStatus, 1)
                .orderByAsc(CrmPipelineStage::getSort));
        Map<Long, CrmViews.FunnelAggVO> aggMap = opportunityMapper.funnelAggByPipeline(selected).stream()
                .collect(Collectors.toMap(CrmViews.FunnelAggVO::getStageId, v -> v));
        return stages.stream().map(stage -> {
            CrmViews.FunnelItemVO vo = new CrmViews.FunnelItemVO();
            CrmViews.FunnelAggVO agg = aggMap.get(stage.getId());
            vo.setStageId(stage.getId());
            vo.setStageName(stage.getStageName());
            vo.setStageType(stage.getStageType());
            vo.setCount(agg != null ? agg.getCount() : 0);
            vo.setAmount(agg != null ? agg.getAmount() : BigDecimal.ZERO);
            vo.setCurrencyCode(currencyCode);
            return vo;
        }).toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<CrmViews.FollowupVO> followups(int limit) {
        tenantInitializer.ensureInitialized();
        int safeLimit = Math.max(1, Math.min(limit, 100));
        LocalDateTime rangeEnd = LocalDate.now().atTime(LocalTime.MAX);
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        return opportunityMapper.selectFollowups(rangeEnd, safeLimit).stream().map(row -> {
            CrmViews.FollowupVO vo = new CrmViews.FollowupVO();
            vo.setRootType(row.getRootType());
            vo.setRootId(row.getRootId());
            vo.setNumber(row.getNumber());
            vo.setName(row.getName());
            vo.setNextFollowupTime(row.getNextFollowupTime());
            vo.setOwnerUserId(row.getOwnerUserId());
            vo.setOverdue(row.getNextFollowupTime() != null && row.getNextFollowupTime().isBefore(todayStart));
            return vo;
        }).toList();
    }

    private long getCount(Map<String, CrmViews.StatusCountVO> map, String status) {
        CrmViews.StatusCountVO vo = map.get(status);
        return vo != null ? vo.getCount() : 0;
    }

    private BigDecimal getAmount(Map<String, CrmViews.StatusCountVO> map, String status) {
        CrmViews.StatusCountVO vo = map.get(status);
        return vo != null ? vo.getAmount() : BigDecimal.ZERO;
    }

    private BigDecimal rate(long numerator, long denominator) {
        return denominator == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(numerator * 100).divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
}

