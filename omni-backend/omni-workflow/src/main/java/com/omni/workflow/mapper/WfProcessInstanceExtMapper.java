package com.omni.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.workflow.entity.WfProcessInstanceExt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 流程实例扩展表 Mapper。
 *
 * @author Omni-Stack Team
 */
@Mapper
public interface WfProcessInstanceExtMapper extends BaseMapper<WfProcessInstanceExt> {

    /**
     * 首次写入流程完成元数据。
     *
     * <p>仅当 {@code completion_event_id} 尚未写入时更新，作为并发场景下
     * 同一流程实例只产生一次完成事件的数据库门闩。</p>
     *
     * @param update 完成事件更新快照
     * @return 更新行数，1 表示获得发布权，0 表示已由其他请求发布
     */
    @Update("""
            UPDATE wf_process_instance_ext
               SET completion_result = #{completionResult},
                   status = #{status},
                   completed_time = #{completedTime},
                   completion_event_id = #{completionEventId},
                   update_time = #{updateTime}
             WHERE tenant_id = #{tenantId}
               AND process_instance_id = #{processInstanceId}
               AND completion_event_id IS NULL
            """)
    int markCompletionForEvent(CompletionEventUpdate update);

    /**
     * 流程完成事件原子更新快照。
     *
     * @param tenantId 租户 ID
     * @param processInstanceId Flowable 流程实例 ID
     * @param completionResult 完成结果
     * @param status 流程实例状态
     * @param completedTime 完成时间
     * @param completionEventId 完成事件 ID
     * @param updateTime 更新时间
     */
    record CompletionEventUpdate(Long tenantId,
                                 String processInstanceId,
                                 String completionResult,
                                 Integer status,
                                 LocalDateTime completedTime,
                                 String completionEventId,
                                 LocalDateTime updateTime) {
    }
}
