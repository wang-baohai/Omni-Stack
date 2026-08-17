package com.omni.srm.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * SRM 与 Workflow 的本地跨服务契约。
 *
 * @author Omni-Stack Team
 */
public final class WorkflowContracts {

    /** 供应商准入业务类型。 */
    public static final String BUSINESS_TYPE = "SRM_SUPPLIER_ONBOARDING";

    private WorkflowContracts() {
    }

    /** 已发布 Workflow 模型版本响应。 */
    @Data
    public static class ModelVersionResponse implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        private Long id;
        private Long modelId;
        private String modelKey;
        private String category;
        private Integer version;
        private String processDefinitionId;
        private String status;
    }

    /** 幂等启动流程请求。 */
    @Data
    public static class StartRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        private String requestId;
        private Long tenantId;
        private Long modelVersionId;
        private String businessType;
        private String businessKey;
        private Long startUserId;
        private String startUserName;
        private String title;
        private Map<String, Object> variables;
    }

    /** 幂等启动流程响应。 */
    @Data
    public static class StartResponse implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        private String requestId;
        private String businessType;
        private String businessKey;
        private String processInstanceId;
        private boolean replayed;
    }

    /** Workflow 流程完成事件。 */
    @Data
    public static class ProcessCompletedEvent implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        private String eventId;
        private String eventType;
        private LocalDateTime occurredAt;
        private Long tenantId;
        private String producer;
        private String businessType;
        private String businessKey;
        private String processInstanceId;
        private String result;
        private LocalDateTime completedTime;
    }
}
