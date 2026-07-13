package com.omni.crm.service;

import com.omni.common.core.result.PageResult;
import com.omni.crm.dto.CrmRequests;
import com.omni.crm.dto.CrmViews;

import java.util.List;

/** CRM 商机应用服务。 */
public interface OpportunityService {
    /** 分页查询。 */ PageResult<CrmViews.OpportunityVO> list(CrmRequests.OpportunityQuery query);
    /** 查询看板。 */ CrmViews.OpportunityBoardVO board(Long pipelineId, CrmRequests.OpportunityQuery query);
    /** 查询详情。 */ CrmViews.OpportunityVO get(Long id);
    /** 查询阶段历史。 */ List<CrmViews.StageHistoryVO> stageHistory(Long id);
    /** 创建商机。 */ CrmViews.OpportunityVO create(CrmRequests.CreateOpportunityRequest request);
    /** 更新商机。 */ CrmViews.OpportunityVO update(Long id, CrmRequests.UpdateOpportunityRequest request);
    /** 删除商机。 */ void delete(Long id, Integer version);
    /** 分配负责人。 */ CrmViews.OpportunityVO assign(Long id, CrmRequests.AssignRequest request);
    /** 迁移阶段。 */ CrmViews.OpportunityVO changeStage(Long id, CrmRequests.OpportunityStageRequest request);
    /** 重开商机。 */ CrmViews.OpportunityVO reopen(Long id, CrmRequests.VersionRequest request);
}
