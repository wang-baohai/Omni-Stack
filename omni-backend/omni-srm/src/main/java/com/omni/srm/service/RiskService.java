package com.omni.srm.service;

import com.omni.common.core.result.PageResult;
import com.omni.srm.dto.SrmRequests;
import com.omni.srm.dto.SrmViews;

import java.util.List;

/** SRM 风险指标与评估服务。 */
public interface RiskService {
    /** 分页查询供应商当前风险。 */
    PageResult<SrmViews.RiskSupplierSummaryVO> listCurrentRisks(String riskLevel, int page, int size);
    /** 查询供应商风险聚合详情。 */ SrmViews.RiskProfileVO profile(Long supplierId);
    /** 查询供应商风险指标列表。 */ List<SrmViews.RiskIndicatorVO> listIndicators(Long supplierId);
    /** 更新风险指标。 */ SrmViews.RiskIndicatorVO updateIndicator(Long id, SrmRequests.UpdateRiskIndicatorRequest request);
    /** 创建综合风险评估。 */ SrmViews.RiskAssessmentVO createAssessment(Long supplierId, SrmRequests.CreateRiskAssessmentRequest request);
    /** 查询供应商风险评估历史。 */ List<SrmViews.RiskAssessmentVO> assessmentHistory(Long supplierId);
}
