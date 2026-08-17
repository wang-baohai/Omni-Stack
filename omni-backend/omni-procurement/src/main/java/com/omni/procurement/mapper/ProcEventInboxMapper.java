package com.omni.procurement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.procurement.entity.ProcEventInbox;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 采购领域事件收件箱 Mapper。
 *
 * @author Omni-Stack Team
 */
@Mapper
public interface ProcEventInboxMapper extends BaseMapper<ProcEventInbox> {

    /**
     * 以租户内事件唯一键登记 Inbox，重复事件返回 0。
     *
     * @param inbox Inbox 记录
     * @return 新增行数
     */
    @Insert("INSERT IGNORE INTO proc_event_inbox "
            + "(tenant_id, event_id, event_type, source_service, aggregate_type, aggregate_id, payload, "
            + "status, create_time, update_time) VALUES "
            + "(#{tenantId}, #{eventId}, #{eventType}, #{sourceService}, #{aggregateType}, #{aggregateId}, "
            + "#{payload}, #{status}, #{createTime}, #{updateTime})")
    int insertIgnore(ProcEventInbox inbox);

    /**
     * 锁定指定事件的 Inbox 记录。
     *
     * @param tenantId 租户 ID
     * @param eventId 事件 ID
     * @return Inbox 记录
     */
    @Select("SELECT * FROM proc_event_inbox WHERE tenant_id = #{tenantId} "
            + "AND event_id = #{eventId} FOR UPDATE")
    ProcEventInbox selectForUpdate(@Param("tenantId") Long tenantId,
                                   @Param("eventId") String eventId);
}
