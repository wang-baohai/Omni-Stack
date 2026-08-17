package com.omni.procurement.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Procurement 与 Workflow 的本地跨服务契约。
 *
 * @author Omni-Stack Team
 */
public final class WorkflowContracts {

    private WorkflowContracts() {
    }

    /** 已发布 Workflow 模型版本响应。 */
    @Data
    public static class ModelVersionResponse implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 模型版本 ID。 */ private Long id;
        /** 模型 ID。 */ private Long modelId;
        /** 模型标识（BPMN process id）。 */ private String modelKey;
        /** 流程业务分类。 */ private String category;
        /** 业务版本号。 */ private Integer version;
        /** Flowable 流程定义 ID。 */ private String processDefinitionId;
        /** PUBLISHED。 */ private String status;
    }

    /** 幂等启动流程请求。 */
    @Data
    public static class StartRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 请求幂等键。 */ private String requestId;
        /** 租户 ID。 */ private Long tenantId;
        /** 已发布流程模型版本 ID。 */ private Long modelVersionId;
        /** 业务类型。 */ private String businessType;
        /** 业务键。 */ private String businessKey;
        /** 发起人用户 ID。 */ private Long startUserId;
        /** 发起人用户名。 */ private String startUserName;
        /** 流程标题。 */ private String title;
        /** 流程变量。 */ private Map<String, Object> variables;
    }

    /** 幂等启动流程响应。 */
    @Data
    public static class StartResponse implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 请求幂等键。 */ private String requestId;
        /** 业务类型。 */ private String businessType;
        /** 业务键。 */ private String businessKey;
        /** Flowable 流程实例 ID。 */ private String processInstanceId;
        /** 是否为幂等重放。 */ private boolean replayed;
    }

    /** 任务处理资格校验请求。 */
    @Data
    public static class AssignmentRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 租户 ID。 */ private Long tenantId;
        /** Workflow 任务 ID。 */ private String taskId;
        /** 当前用户 ID。 */ private Long userId;
        /** 业务类型。 */ private String businessType;
        /** 业务键。 */ private String businessKey;
    }

    /** 任务处理资格校验响应。 */
    @Data
    public static class AssignmentResponse implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 是否具备处理资格。 */ private boolean valid;
        /** Flowable 流程实例 ID。 */ private String processInstanceId;
        /** ASSIGNEE/CANDIDATE/NONE。 */ private String assignmentType;
        /** 校验说明。 */ private String message;
    }

    /** Workflow 流程完成事件。 */
    @Data
    public static class ProcessCompletedEvent implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 事件 ID。 */ private String eventId;
        /** 事件类型。 */ private String eventType;
        /** 事件产生时间。 */ private LocalDateTime occurredAt;
        /** 租户 ID。 */ private Long tenantId;
        /** 生产者。 */ private String producer;
        /** 业务类型。 */ private String businessType;
        /** 业务键。 */ private String businessKey;
        /** 流程实例 ID。 */ private String processInstanceId;
        /** APPROVED/REJECTED/CANCELLED。 */ private String result;
        /** 流程完成时间。 */ private LocalDateTime completedTime;
    }
}
