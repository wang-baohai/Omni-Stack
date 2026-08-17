package com.omni.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 跨服务流程启动幂等请求实体。
 *
 * <p>请求 ID 和业务键分别在租户内唯一，用于在调用超时、响应丢失和并发重试时
 * 复用同一条启动记录，避免重复创建 Flowable 流程实例。</p>
 *
 * @author Omni-Stack Team
 */
@Data
@TableName("wf_process_start_request")
public class WfProcessStartRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 已预留，允许当前持有者启动流程。 */
    public static final String STATUS_RESERVED = "RESERVED";

    /** 流程实例已经启动。 */
    public static final String STATUS_STARTED = "STARTED";

    /** 最近一次启动失败，可再次预留重试。 */
    public static final String STATUS_FAILED = "FAILED";

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID。 */
    private Long tenantId;

    /** 调用方请求 ID。 */
    private String requestId;

    /** 业务类型。 */
    private String businessType;

    /** 业务主键。 */
    private String businessKey;

    /** 流程模型版本 ID。 */
    private Long modelVersionId;

    /** 发起人用户 ID。 */
    private Long startUserId;

    /** 启动状态。 */
    private String status;

    /** Flowable 流程实例 ID。 */
    private String processInstanceId;

    /** 已执行的重试次数。 */
    private Integer retryCount;

    /** 最近一次失败原因。 */
    private String lastError;

    /** 流程启动成功时间。 */
    private LocalDateTime startedTime;

    /** 最近一次启动失败时间。 */
    private LocalDateTime failedTime;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
