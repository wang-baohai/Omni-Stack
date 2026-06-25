package com.omni.base.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 注册系统任务请求体。
 *
 * @author Omni-Stack Team
 */
@Data
public class RegisterSystemJobRequest {

    /** Handler 名称 */
    @NotBlank(message = "Handler 名称不能为空")
    private String handlerName;

    /** Cron 表达式 */
    @NotBlank(message = "Cron 表达式不能为空")
    private String cron;

    /** 任务参数（JSON 字符串，如 {"retentionDays":180}） */
    private String params;
}
