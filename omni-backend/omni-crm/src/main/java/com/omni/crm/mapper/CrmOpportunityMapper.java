package com.omni.crm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.omni.crm.dto.CrmViews;
import com.omni.crm.entity.CrmOpportunity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** CRM 商机 Mapper。 */
public interface CrmOpportunityMapper extends BaseMapper<CrmOpportunity> {

    /**
     * 在数据权限范围内锁定商机。
     *
     * @param id 商机 ID
     * @return 商机
     */
    @Select("SELECT * FROM crm_opportunity WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    CrmOpportunity selectVisibleForUpdate(@Param("id") Long id);

    /** 统计已授权客户下全部开放商机，只忽略子表 owner 条件并保留 TenantLine。 */
    @InterceptorIgnore(dataPermission = "true")
    @Select("SELECT COUNT(*) FROM crm_opportunity WHERE customer_id = #{customerId} AND status = 'OPEN' AND deleted = 0")
    Long countAllOpenByCustomer(@Param("customerId") Long customerId);

    /** 查询已授权客户下全部开放商机，用于转移前保存 owner 审计事实。 */
    @InterceptorIgnore(dataPermission = "true")
    @Select("SELECT * FROM crm_opportunity WHERE customer_id = #{customerId} AND status = 'OPEN' AND deleted = 0")
    List<CrmOpportunity> selectAllOpenByCustomer(@Param("customerId") Long customerId);

    /** 同步已授权客户下全部开放商机 owner 快照。 */
    @InterceptorIgnore(dataPermission = "true")
    @Update("UPDATE crm_opportunity SET owner_user_id = #{ownerUserId}, owner_unit_id = #{ownerUnitId}, "
            + "version = version + 1, update_time = #{now}, update_by = #{operator} "
            + "WHERE customer_id = #{customerId} AND status = 'OPEN' AND deleted = 0")
    int syncAllOpenOwnerByCustomer(@Param("customerId") Long customerId,
                                   @Param("ownerUserId") Long ownerUserId,
                                   @Param("ownerUnitId") Long ownerUnitId,
                                   @Param("now") LocalDateTime now,
                                   @Param("operator") String operator);

    /** 在联系人删除后解除商机主要联系人引用。 */
    @InterceptorIgnore(dataPermission = "true")
    @Update("UPDATE crm_opportunity SET primary_contact_id = NULL, version = version + 1, "
            + "update_time = #{now}, update_by = #{operator} "
            + "WHERE primary_contact_id = #{contactId} AND deleted = 0")
    int clearPrimaryContactReference(@Param("contactId") Long contactId,
                                     @Param("now") LocalDateTime now,
                                     @Param("operator") String operator);

    /** 在客户联系人整体删除后解除该客户商机的主要联系人引用。 */
    @InterceptorIgnore(dataPermission = "true")
    @Update("UPDATE crm_opportunity SET primary_contact_id = NULL, version = version + 1, "
            + "update_time = #{now}, update_by = #{operator} "
            + "WHERE customer_id = #{customerId} AND primary_contact_id IS NOT NULL AND deleted = 0")
    int clearPrimaryContactReferencesByCustomer(@Param("customerId") Long customerId,
                                                @Param("now") LocalDateTime now,
                                                @Param("operator") String operator);

    /**
     * 按状态聚合商机计数及金额。
     *
     * @return 各状态计数和金额
     */
    @Select("SELECT status, COUNT(*) AS count, COALESCE(SUM(amount), 0) AS amount "
            + "FROM crm_opportunity WHERE deleted = 0 GROUP BY status")
    List<CrmViews.StatusCountVO> countGroupByStatus();

    /**
     * 按管道阶段聚合商机计数及金额，用于漏斗展示。
     *
     * @param pipelineId 管道 ID
     * @return 各阶段计数和金额
     */
    @Select("SELECT stage_id, COUNT(*) AS count, COALESCE(SUM(amount), 0) AS amount "
            + "FROM crm_opportunity WHERE pipeline_id = #{pipelineId} AND deleted = 0 GROUP BY stage_id")
    List<CrmViews.FunnelAggVO> funnelAggByPipeline(@Param("pipelineId") Long pipelineId);

    /**
     * 统计开放商机的待跟进数。
     *
     * @param rangeStart 范围起始（含）
     * @param rangeEnd   范围结束（含）
     * @return 待跟进数
     */
    @Select("SELECT COUNT(*) FROM crm_opportunity WHERE deleted = 0 AND next_followup_time IS NOT NULL "
            + "AND next_followup_time >= #{rangeStart} AND next_followup_time <= #{rangeEnd} "
            + "AND status = 'OPEN'")
    long countFollowups(@Param("rangeStart") LocalDateTime rangeStart, @Param("rangeEnd") LocalDateTime rangeEnd);

    /**
     * 查询待跟进事项（线索 + 客户 + 商机），按跟进时间升序排列。
     *
     * @param rangeEnd 范围结束（含，今日结束）
     * @param limit    最大返回数
     * @return 待跟进事项
     */
    @Select("SELECT * FROM ("
            + "SELECT 'LEAD' AS rootType, id AS rootId, lead_no AS number, full_name AS name, "
            + "next_followup_time, owner_user_id FROM crm_lead WHERE deleted = 0 "
            + "AND next_followup_time IS NOT NULL AND next_followup_time <= #{rangeEnd} "
            + "AND status IN ('NEW', 'FOLLOWING', 'QUALIFIED') "
            + "UNION ALL "
            + "SELECT 'CUSTOMER' AS rootType, id AS rootId, customer_no AS number, name, "
            + "next_followup_time, owner_user_id FROM crm_customer WHERE deleted = 0 "
            + "AND next_followup_time IS NOT NULL AND next_followup_time <= #{rangeEnd} "
            + "AND status IN ('POTENTIAL', 'ACTIVE', 'DORMANT') "
            + "UNION ALL "
            + "SELECT 'OPPORTUNITY' AS rootType, id AS rootId, opportunity_no AS number, name, "
            + "next_followup_time, owner_user_id FROM crm_opportunity WHERE deleted = 0 "
            + "AND next_followup_time IS NOT NULL AND next_followup_time <= #{rangeEnd} "
            + "AND status = 'OPEN'"
            + ") t ORDER BY next_followup_time ASC LIMIT #{limit}")
    List<CrmViews.FollowupRowVO> selectFollowups(@Param("rangeEnd") LocalDateTime rangeEnd, @Param("limit") int limit);
}
