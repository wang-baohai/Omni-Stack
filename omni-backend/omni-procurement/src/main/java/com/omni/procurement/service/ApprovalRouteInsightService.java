package com.omni.procurement.service;

import com.omni.procurement.dto.ApprovalRouteInsightRequests;
import com.omni.procurement.dto.ApprovalRouteInsightViews;

import java.util.List;

/**
 * 请购审批规则选择、试算和风险分析服务。
 *
 * @author Omni-Stack Team
 */
public interface ApprovalRouteInsightService {

    /** @return 当前可绑定的采购审批流程 */
    List<ApprovalRouteInsightViews.WorkflowOption> workflowOptions();

    /** @param request 试算输入 @return 无副作用匹配结果 */
    ApprovalRouteInsightViews.MatchPreview matchPreview(
            ApprovalRouteInsightRequests.MatchPreviewRequest request);

    /** @return 当前全部启用品类的审批覆盖报告 */
    ApprovalRouteInsightViews.CoverageReport coverage();

    /** @param routeId 模拟排除的规则 ID @return 排除后的影响 */
    ApprovalRouteInsightViews.ImpactReport impact(Long routeId);
}
