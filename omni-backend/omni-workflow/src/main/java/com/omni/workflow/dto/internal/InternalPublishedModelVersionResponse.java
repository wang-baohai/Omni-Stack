package com.omni.workflow.dto.internal;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 内部已发布审批模型版本视图。
 *
 * @author Omni-Stack Team
 */
@Data
@Builder
public class InternalPublishedModelVersionResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 模型版本 ID。 */ private Long id;
    /** 模型 ID。 */ private Long modelId;
    /** 模型技术标识。 */ private String modelKey;
    /** 模型业务名称。 */ private String modelName;
    /** 模型分类。 */ private String category;
    /** 业务版本号。 */ private Integer version;
    /** 发布时间。 */ private LocalDateTime publishTime;
    /** Flowable 流程定义 ID。 */ private String processDefinitionId;
    /** 版本状态。 */ private String status;
    /** 预览契约版本。 */ private Integer approvalPreviewVersion;
    /** AVAILABLE/NOT_CURRENT/UNAVAILABLE/MODEL_ARCHIVED/NOT_FOUND。 */ private String availability;
}
