package com.omni.base.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建用户任务请求。
 *
 * @author Omni-Stack Team
 */
@Data
public class CreateUserJobRequest {

    /** 任务名称 */
    @NotBlank(message = "任务名称不能为空")
    private String jobName;

    /** 任务类型编码 */
    @NotBlank(message = "任务类型不能为空")
    private String jobType;

    /** Cron 表达式 */
    @NotBlank(message = "Cron 表达式不能为空")
    private String cronExpression;

    /** 任务参数 JSON */
    private String jobParams;
}
