package com.omni.workflow.dto.internal;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Workflow 内部模型版本数据库投影，不直接作为接口响应。
 *
 * @author Omni-Stack Team
 */
@Data
public class InternalModelVersionProjection {

    /** 模型版本 ID。 */ private Long id;
    /** 模型 ID。 */ private Long modelId;
    /** 模型技术标识。 */ private String modelKey;
    /** 模型业务名称。 */ private String modelName;
    /** 模型分类。 */ private String category;
    /** 业务版本号。 */ private Integer version;
    /** 发布时间。 */ private LocalDateTime publishTime;
    /** 流程定义 ID。 */ private String processDefinitionId;
    /** 版本状态。 */ private String status;
    /** 模型状态。 */ private Integer modelStatus;
    /** 当前发布版本 ID。 */ private Long currentPublishedVersionId;
    /** BPMN XML，仅用于服务端安全预览解析。 */ private String bpmnXml;
}
