package com.omni.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.workflow.entity.WfProcessStartRequest;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 流程启动幂等请求 Mapper。
 *
 * @author Omni-Stack Team
 */
public interface WfProcessStartRequestMapper extends BaseMapper<WfProcessStartRequest> {

    /**
     * 尝试插入启动预留记录。
     *
     * <p>两个唯一键中的任意一个冲突时均返回零，由服务层读取冲突记录并判断是幂等重放还是参数冲突。</p>
     *
     * @param request 启动请求记录
     * @return 受影响行数，成功插入为 1，唯一键冲突为 0
     */
    @Insert("INSERT IGNORE INTO wf_process_start_request "
            + "(tenant_id, request_id, business_type, business_key, model_version_id, start_user_id, "
            + "status, retry_count, create_time, update_time) "
            + "VALUES (#{tenantId}, #{requestId}, #{businessType}, #{businessKey}, #{modelVersionId}, "
            + "#{startUserId}, #{status}, #{retryCount}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertIgnore(WfProcessStartRequest request);

    /**
     * 按租户和请求 ID 查询。
     *
     * @param tenantId 租户 ID
     * @param requestId 请求 ID
     * @return 启动请求记录
     */
    @Select("SELECT * FROM wf_process_start_request "
            + "WHERE tenant_id = #{tenantId} AND request_id = #{requestId} LIMIT 1")
    WfProcessStartRequest selectByRequestId(@Param("tenantId") Long tenantId,
                                            @Param("requestId") String requestId);

    /**
     * 按租户和业务键查询。
     *
     * @param tenantId 租户 ID
     * @param businessType 业务类型
     * @param businessKey 业务主键
     * @return 启动请求记录
     */
    @Select("SELECT * FROM wf_process_start_request "
            + "WHERE tenant_id = #{tenantId} AND business_type = #{businessType} "
            + "AND business_key = #{businessKey} LIMIT 1")
    WfProcessStartRequest selectByBusinessKey(@Param("tenantId") Long tenantId,
                                              @Param("businessType") String businessType,
                                              @Param("businessKey") String businessKey);

    /**
     * 按租户和主键查询。
     *
     * @param tenantId 租户 ID
     * @param id 主键
     * @return 启动请求记录
     */
    @Select("SELECT * FROM wf_process_start_request "
            + "WHERE tenant_id = #{tenantId} AND id = #{id} LIMIT 1")
    WfProcessStartRequest selectByTenantAndId(@Param("tenantId") Long tenantId,
                                              @Param("id") Long id);

    /**
     * 原子抢占一条失败记录用于重试。
     *
     * @param tenantId 租户 ID
     * @param id 主键
     * @param now 当前时间
     * @return 受影响行数
     */
    @Update("UPDATE wf_process_start_request "
            + "SET status = 'RESERVED', retry_count = retry_count + 1, last_error = NULL, "
            + "failed_time = NULL, update_time = #{now} "
            + "WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'FAILED'")
    int reserveRetry(@Param("tenantId") Long tenantId,
                     @Param("id") Long id,
                     @Param("now") LocalDateTime now);

    /**
     * 将预留记录原子标记为已启动。
     *
     * @param tenantId 租户 ID
     * @param id 主键
     * @param processInstanceId Flowable 流程实例 ID
     * @param now 当前时间
     * @return 受影响行数
     */
    @Update("UPDATE wf_process_start_request "
            + "SET status = 'STARTED', process_instance_id = #{processInstanceId}, "
            + "started_time = #{now}, last_error = NULL, failed_time = NULL, update_time = #{now} "
            + "WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'RESERVED'")
    int markStarted(@Param("tenantId") Long tenantId,
                    @Param("id") Long id,
                    @Param("processInstanceId") String processInstanceId,
                    @Param("now") LocalDateTime now);

    /**
     * 将预留记录原子标记为失败。
     *
     * @param tenantId 租户 ID
     * @param id 主键
     * @param lastError 失败原因
     * @param now 当前时间
     * @return 受影响行数
     */
    @Update("UPDATE wf_process_start_request "
            + "SET status = 'FAILED', last_error = #{lastError}, failed_time = #{now}, "
            + "update_time = #{now} "
            + "WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'RESERVED'")
    int markFailed(@Param("tenantId") Long tenantId,
                   @Param("id") Long id,
                   @Param("lastError") String lastError,
                   @Param("now") LocalDateTime now);
}
