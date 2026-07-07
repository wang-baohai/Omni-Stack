package com.omni.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 发布结果。
 *
 * @author Omni-Stack Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 版本记录 ID */
    private Long versionId;

    /** 业务版本号 */
    private Integer businessVersion;

    /** Flowable 部署 ID */
    private String deploymentId;

    /** Flowable 流程定义 ID */
    private String processDefinitionId;

    /** Flowable 引擎版本号 */
    private Integer engineVersion;
}
