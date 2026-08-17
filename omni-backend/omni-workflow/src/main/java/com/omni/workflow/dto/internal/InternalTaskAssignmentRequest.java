package com.omni.workflow.dto.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 内部服务任务处理资格校验请求。
 *
 * @author Omni-Stack Team
 */
@Data
public class InternalTaskAssignmentRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 租户 ID。 */
    @NotNull(message = "租户 ID 不能为空")
    @Positive(message = "租户 ID 必须为正整数")
    private Long tenantId;

    /** Flowable 任务 ID。 */
    @NotBlank(message = "任务 ID 不能为空")
    @Size(max = 64, message = "任务 ID 长度不能超过 64")
    private String taskId;

    /** 待校验的用户 ID。 */
    @NotNull(message = "用户 ID 不能为空")
    @Positive(message = "用户 ID 必须为正整数")
    private Long userId;

    /** 业务类型。 */
    @NotBlank(message = "业务类型不能为空")
    @Size(max = 100, message = "业务类型长度不能超过 100")
    private String businessType;

    /** 业务主键。 */
    @NotBlank(message = "业务主键不能为空")
    @Size(max = 255, message = "业务主键长度不能超过 255")
    private String businessKey;
}
