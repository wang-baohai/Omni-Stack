package com.omni.procurement.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
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
        /** 模型业务名称。 */ private String modelName;
        /** 流程业务分类。 */ private String category;
        /** 业务版本号。 */ private Integer version;
        /** 发布时间。 */ private LocalDateTime publishTime;
        /** Flowable 流程定义 ID。 */ private String processDefinitionId;
        /** PUBLISHED。 */ private String status;
        /** 预览契约版本。 */ private Integer approvalPreviewVersion;
        /** AVAILABLE/NOT_CURRENT/UNAVAILABLE/MODEL_ARCHIVED/NOT_FOUND。 */ private String availability;
    }

    /** 批量解析 Workflow 模型版本请求。 */
    @Data
    public static class ModelVersionResolveRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 待解析版本 ID，单次不超过 200 个。 */ private List<Long> modelVersionIds;
    }

    /** 不含原始 BPMN 的安全审批图。 */
    @Data
    public static class ApprovalPreviewResponse implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 预览契约版本。 */ private Integer approvalPreviewVersion;
        /** 模型版本元数据。 */ private ModelVersionResponse modelVersion;
        /** 安全节点列表。 */ private List<ApprovalNode> nodes;
        /** 安全有向边列表。 */ private List<ApprovalEdge> edges;
        /** 是否存在分支。 */ private boolean hasBranches;
        /** 仅无环单路径时返回的业务步骤摘要。 */ private List<String> linearSummary;
    }

    /** 安全审批节点。 */
    @Data
    public static class ApprovalNode implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** BPMN 节点 ID。 */ private String id;
        /** 节点业务名称。 */ private String name;
        /** START/END/APPROVAL/GATEWAY/SERVICE。 */ private String type;
        /** 审批角色编码。 */ private String roleCode;
        /** ALL/ANY。 */ private String approvalMode;
        /** 业务化说明。 */ private String description;
    }

    /** 安全审批图有向边。 */
    @Data
    public static class ApprovalEdge implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** BPMN SequenceFlow ID。 */ private String id;
        /** 分支显示名。 */ private String name;
        /** 源节点 ID。 */ private String source;
        /** 目标节点 ID。 */ private String target;
        /** 是否为默认分支。 */ private boolean defaultBranch;
        /** 已脱敏条件摘要。 */ private String conditionSummary;
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
