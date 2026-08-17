package com.omni.workflow.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作流流程完成领域事件。
 *
 * <p>事件只携带跨服务状态推进所需的业务标识和结果，不包含流程变量、
 * 审批意见或其他可能含敏感信息的内容。</p>
 *
 * @author Omni-Stack Team
 */
@Data
@Builder
public class WorkflowProcessCompletedEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件 ID。 */
    private String eventId;
    /** 事件类型。 */
    private String eventType;
    /** 事件产生时间。 */
    private LocalDateTime occurredAt;
    /** 租户 ID。 */
    private Long tenantId;
    /** 事件生产者。 */
    private String producer;
    /** 外部业务类型。 */
    private String businessType;
    /** 外部业务主键。 */
    private String businessKey;
    /** Flowable 流程实例 ID。 */
    private String processInstanceId;
    /** 流程完成结果。 */
    private WorkflowCompletionResult result;
    /** 流程完成时间。 */
    private LocalDateTime completedTime;
}
