package com.omni.base.dto;

import lombok.Data;

/**
 * 更新用户任务请求。
 *
 * @author Omni-Stack Team
 */
@Data
public class UpdateUserJobRequest {

    /** 任务名称 */
    private String jobName;

    /** Cron 表达式 */
    private String cronExpression;

    /** 任务参数 JSON */
    private String jobParams;
}
