package com.omni.srm.service;

import com.omni.srm.dto.SrmViews;

import java.util.List;

/**
 * SRM 风险指标配置服务。
 * <p>管理指标类型、评分标准和得分阈值。</p>
 *
 * @author Omni-Stack Team
 */
public interface RiskIndicatorConfigService {

    /** 查询所有启用的指标类型及其评分标准。 */
    List<SrmViews.RiskIndicatorTypeVO> listIndicatorTypes();

    /** 创建指标类型。 */
    SrmViews.RiskIndicatorTypeVO createIndicatorType(SrmViews.RiskIndicatorTypeVO request);

    /** 更新指标类型。 */
    SrmViews.RiskIndicatorTypeVO updateIndicatorType(Long id, SrmViews.RiskIndicatorTypeVO request);

    /** 删除指标类型。 */
    void deleteIndicatorType(Long id);

    /** 创建评分标准。 */
    SrmViews.RiskCriterionVO createCriterion(SrmViews.RiskCriterionVO request);

    /** 更新评分标准。 */
    SrmViews.RiskCriterionVO updateCriterion(Long id, SrmViews.RiskCriterionVO request);

    /** 删除评分标准。 */
    void deleteCriterion(Long id);

    /** 查询得分阈值列表。 */
    List<SrmViews.RiskScoreThresholdVO> listThresholds();

    /** 批量更新得分阈值。 */
    List<SrmViews.RiskScoreThresholdVO> updateThresholds(List<SrmViews.RiskScoreThresholdVO> requests);
}
