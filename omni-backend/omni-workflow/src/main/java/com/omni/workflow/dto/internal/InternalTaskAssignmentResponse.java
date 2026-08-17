package com.omni.workflow.dto.internal;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 内部服务任务处理资格校验响应。
 *
 * @author Omni-Stack Team
 */
@Data
@Builder
public class InternalTaskAssignmentResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否具备处理资格。 */
    private boolean valid;

    /** 流程实例 ID，任务不存在时为空。 */
    private String processInstanceId;

    /** 资格类型：ASSIGNEE、CANDIDATE 或 NONE。 */
    private String assignmentType;

    /** 校验说明。 */
    private String message;
}
