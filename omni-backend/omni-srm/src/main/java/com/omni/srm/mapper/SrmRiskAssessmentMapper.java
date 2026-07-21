package com.omni.srm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.srm.dto.SrmViews;
import com.omni.srm.entity.SrmRiskAssessment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * SRM 综合风险评估 Mapper。
 *
 * @author Omni-Stack Team
 */
public interface SrmRiskAssessmentMapper extends BaseMapper<SrmRiskAssessment> {

    /**
     * 分页查询每个供应商的最新风险评估。
     *
     * @param page 分页参数
     * @param riskLevel 风险等级，可空
     * @return 当前风险分页
     */
    @Select({
            "<script>",
            "SELECT assessment.supplier_id, supplier.name AS supplier_name,",
            "assessment.overall_level, assessment.assessment_time,",
            "COALESCE(red_indicator.red_indicator_count, 0) AS red_indicator_count",
            "FROM srm_risk_assessment assessment",
            "INNER JOIN (SELECT supplier_id, MAX(id) AS latest_id FROM srm_risk_assessment",
            "WHERE deleted = 0 GROUP BY supplier_id) latest ON latest.latest_id = assessment.id",
            "INNER JOIN srm_supplier supplier ON supplier.id = assessment.supplier_id AND supplier.deleted = 0",
            "LEFT JOIN (SELECT supplier_id, COUNT(*) AS red_indicator_count FROM srm_risk_indicator",
            "WHERE deleted = 0 AND risk_level = 'RED' GROUP BY supplier_id) red_indicator",
            "ON red_indicator.supplier_id = assessment.supplier_id",
            "WHERE assessment.deleted = 0",
            "<if test='riskLevel != null and riskLevel != \"\"'>",
            "AND assessment.overall_level = #{riskLevel}",
            "</if>",
            "ORDER BY CASE assessment.overall_level WHEN 'RED' THEN 3 WHEN 'YELLOW' THEN 2 ELSE 1 END DESC,",
            "assessment.assessment_time DESC, assessment.id DESC",
            "</script>"
    })
    Page<SrmViews.RiskSupplierSummaryVO> selectCurrentRiskPage(
            Page<SrmViews.RiskSupplierSummaryVO> page, @Param("riskLevel") String riskLevel);

    /**
     * 汇总每个供应商当前风险等级。
     *
     * @return 风险等级数量
     */
    @Select({
            "SELECT COALESCE(SUM(CASE WHEN assessment.overall_level = 'RED' THEN 1 ELSE 0 END), 0) AS red_count,",
            "COALESCE(SUM(CASE WHEN assessment.overall_level = 'YELLOW' THEN 1 ELSE 0 END), 0) AS yellow_count,",
            "COALESCE(SUM(CASE WHEN assessment.overall_level = 'GREEN' THEN 1 ELSE 0 END), 0) AS green_count",
            "FROM srm_risk_assessment assessment",
            "INNER JOIN (SELECT supplier_id, MAX(id) AS latest_id FROM srm_risk_assessment",
            "WHERE deleted = 0 GROUP BY supplier_id) latest ON latest.latest_id = assessment.id",
            "WHERE assessment.deleted = 0"
    })
    SrmViews.RiskDashboardVO selectCurrentRiskCounts();
}
