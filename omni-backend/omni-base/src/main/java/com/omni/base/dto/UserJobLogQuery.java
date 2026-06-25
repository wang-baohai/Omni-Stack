package com.omni.base.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户任务执行日志查询参数。
 *
 * @author Omni-Stack Team
 */
@Data
public class UserJobLogQuery {

    /** 任务 ID */
    private Long jobId;

    /** 任务类型编码 */
    private String jobType;

    /** 状态过滤 */
    private Integer status;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;
}
