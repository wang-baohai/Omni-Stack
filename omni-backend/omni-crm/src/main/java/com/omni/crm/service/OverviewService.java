package com.omni.crm.service;

import com.omni.crm.dto.CrmViews;

import java.util.List;

/** CRM 基础看板服务。 */
public interface OverviewService {
    /** 查询看板摘要。 */ CrmViews.OverviewSummaryVO summary();
    /** 查询销售漏斗。 */ List<CrmViews.FunnelItemVO> funnel(Long pipelineId);
    /** 查询今日及逾期待跟进。 */ List<CrmViews.FollowupVO> followups(int limit);
}
