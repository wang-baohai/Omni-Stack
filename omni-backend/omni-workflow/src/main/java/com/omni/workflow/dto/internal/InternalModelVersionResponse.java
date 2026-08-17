package com.omni.workflow.dto.internal;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 内部服务流程模型版本响应。
 *
 * @author Omni-Stack Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class InternalModelVersionResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 流程模型版本 ID。 */
    private Long id;

    /** 流程模型 ID。 */
    private Long modelId;

    /** 模型标识（BPMN process id）。 */
    private String modelKey;

    /** 流程业务分类。 */
    private String category;

    /** 业务版本号。 */
    private Integer version;

    /** Flowable 流程定义 ID。 */
    private String processDefinitionId;

    /** 模型版本状态。 */
    private String status;
}
