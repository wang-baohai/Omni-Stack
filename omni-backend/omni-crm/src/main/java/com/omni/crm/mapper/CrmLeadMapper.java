package com.omni.crm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.crm.dto.CrmViews;
import com.omni.crm.entity.CrmLead;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** CRM 线索 Mapper。 */
public interface CrmLeadMapper extends BaseMapper<CrmLead> {

    /**
     * 在租户和数据权限拦截下锁定线索。
     *
     * @param id 线索 ID
     * @return 可见线索
     */
    @Select("SELECT * FROM crm_lead WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    CrmLead selectVisibleForUpdate(@Param("id") Long id);

    /**
     * 按状态聚合线索计数。
     *
     * @return 各状态计数
     */
    @Select("SELECT status, COUNT(*) AS count FROM crm_lead WHERE deleted = 0 GROUP BY status")
    List<CrmViews.StatusCountVO> countGroupByStatus();

    /**
     * 统计指定时间范围内活跃线索的待跟进数。
     *
     * @param rangeStart 范围起始（含）
     * @param rangeEnd   范围结束（含）
     * @return 待跟进数
     */
    @Select("SELECT COUNT(*) FROM crm_lead WHERE deleted = 0 AND next_followup_time IS NOT NULL "
            + "AND next_followup_time >= #{rangeStart} AND next_followup_time <= #{rangeEnd} "
            + "AND status IN ('NEW', 'FOLLOWING', 'QUALIFIED')")
    long countFollowups(@Param("rangeStart") LocalDateTime rangeStart, @Param("rangeEnd") LocalDateTime rangeEnd);
}
