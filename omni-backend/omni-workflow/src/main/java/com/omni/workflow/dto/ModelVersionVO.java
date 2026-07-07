package com.omni.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 模型版本视图对象。
 *
 * @author Omni-Stack Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelVersionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 版本记录 ID */
    private Long id;

    /** 业务版本号 */
    private Integer version;

    /** 版本状态 */
    private String status;

    /** BPMN XML SHA-256 */
    private String xmlSha256;

    /** Flowable 部署 ID */
    private String deploymentId;

    /** Flowable 流程定义 ID */
    private String processDefinitionId;

    /** Flowable 引擎版本号 */
    private Integer engineVersion;

    /** 发布人 */
    private String publishBy;

    /** 发布时间 */
    private LocalDateTime publishTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
