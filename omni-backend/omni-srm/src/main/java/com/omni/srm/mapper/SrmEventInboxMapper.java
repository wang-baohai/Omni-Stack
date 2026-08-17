package com.omni.srm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.srm.entity.SrmEventInbox;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * SRM 领域事件收件箱 Mapper。
 *
 * @author Omni-Stack Team
 */
public interface SrmEventInboxMapper extends BaseMapper<SrmEventInbox> {

    /**
     * 幂等插入事件（忽略重复键）。
     *
     * @param inbox 事件记录
     * @return 影响行数
     */
    @Insert("INSERT IGNORE INTO srm_event_inbox (tenant_id, event_id, event_type, source_service, "
            + "aggregate_type, aggregate_id, payload, status, create_time, update_time) "
            + "VALUES (#{tenantId}, #{eventId}, #{eventType}, #{sourceService}, "
            + "#{aggregateType}, #{aggregateId}, #{payload}, #{status}, #{createTime}, #{updateTime})")
    int insertIgnore(SrmEventInbox inbox);

    /**
     * 按租户 + 事件 ID 锁定记录。
     *
     * @param tenantId 租户 ID
     * @param eventId  事件 ID
     * @return 事件记录
     */
    @Select("SELECT * FROM srm_event_inbox WHERE tenant_id = #{tenantId} AND event_id = #{eventId} FOR UPDATE")
    SrmEventInbox selectForUpdate(@Param("tenantId") Long tenantId, @Param("eventId") String eventId);
}
