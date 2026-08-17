package com.omni.asset.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.asset.entity.AstInboxEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Asset 通用事件收件箱 Mapper。
 *
 * @author Omni-Stack Team
 */
public interface AstInboxEventMapper extends BaseMapper<AstInboxEvent> {

    /**
     * 按消费者与事件 ID 幂等登记事件。
     *
     * @param inbox 收件箱记录
     * @return 新增行数，重复时返回 0
     */
    @Insert("""
            INSERT IGNORE INTO ast_inbox_event
                (tenant_id, consumer_name, event_id, event_type, source_service,
                 aggregate_type, aggregate_id, payload, status, create_time, update_time)
            VALUES
                (#{tenantId}, #{consumerName}, #{eventId}, #{eventType}, #{sourceService},
                 #{aggregateType}, #{aggregateId}, #{payload}, #{status}, #{createTime}, #{updateTime})
            """)
    int insertIgnore(AstInboxEvent inbox);

    /**
     * 锁定指定消费者的事件记录。
     *
     * @param consumerName 消费者名称
     * @param eventId 事件 ID
     * @return 已锁定收件箱记录
     */
    @Select("""
            SELECT *
            FROM ast_inbox_event
            WHERE consumer_name = #{consumerName}
              AND event_id = #{eventId}
            FOR UPDATE
            """)
    @InterceptorIgnore(tenantLine = "true")
    AstInboxEvent selectForUpdate(@Param("consumerName") String consumerName,
                                  @Param("eventId") String eventId);

    /**
     * 条件更新已锁定事件的处理终态。
     *
     * @param inbox 携带身份键与处理终态的收件箱记录
     * @return 受影响行数
     */
    @Update("""
            UPDATE ast_inbox_event
            SET status = #{status},
                processed_time = #{processedTime},
                error_message = NULL,
                update_time = #{updateTime}
            WHERE id = #{id}
              AND tenant_id = #{tenantId}
              AND consumer_name = #{consumerName}
              AND event_id = #{eventId}
            """)
    int markProcessed(AstInboxEvent inbox);
}
