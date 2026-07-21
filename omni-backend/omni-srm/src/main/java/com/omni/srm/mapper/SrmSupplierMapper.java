package com.omni.srm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.srm.dto.SrmViews;
import com.omni.srm.entity.SrmSupplier;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * SRM 供应商 Mapper。
 *
 * @author Omni-Stack Team
 */
public interface SrmSupplierMapper extends BaseMapper<SrmSupplier> {

    /**
     * 在数据权限范围内锁定供应商。
     *
     * @param id 供应商 ID
     * @return 供应商
     */
    @Select("SELECT * FROM srm_supplier WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    SrmSupplier selectVisibleForUpdate(@Param("id") Long id);

    /**
     * 统计各状态供应商数量。
     *
     * @return 概览统计
     */
    @Select({
            "SELECT COUNT(*) AS total_suppliers,",
            "COALESCE(SUM(CASE WHEN status = 'APPROVED' THEN 1 ELSE 0 END), 0) AS approved_count,",
            "COALESCE(SUM(CASE WHEN status = 'PENDING_REVIEW' THEN 1 ELSE 0 END), 0) AS pending_review_count,",
            "COALESCE(SUM(CASE WHEN status = 'SUSPENDED' THEN 1 ELSE 0 END), 0) AS suspended_count,",
            "COALESCE(SUM(CASE WHEN status = 'BLACKLISTED' THEN 1 ELSE 0 END), 0) AS blacklisted_count,",
            "COALESCE(SUM(CASE WHEN status = 'ELIMINATED' THEN 1 ELSE 0 END), 0) AS eliminated_count,",
            "COALESCE(SUM(CASE WHEN level_code = 'STRATEGIC' THEN 1 ELSE 0 END), 0) AS strategic_count,",
            "COALESCE(SUM(CASE WHEN level_code = 'PREFERRED' THEN 1 ELSE 0 END), 0) AS preferred_count,",
            "COALESCE(SUM(CASE WHEN level_code = 'QUALIFIED' THEN 1 ELSE 0 END), 0) AS qualified_count",
            "FROM srm_supplier WHERE deleted = 0"
    })
    SrmViews.OverviewSummaryVO selectOverviewSummary();
}
