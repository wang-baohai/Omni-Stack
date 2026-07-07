package com.omni.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 流程定义视图对象。
 * <p>用于替代直接返回 Flowable 内部实体，避免 Jackson 序列化时触发懒加载属性导致 NPE。</p>
 *
 * @author Omni-Stack Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessDefinitionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 流程定义 ID */
    private String id;

    /** 流程定义 Key（BPMN process id） */
    private String key;

    /** 流程名称 */
    private String name;

    /** 流程分类 */
    private String category;

    /** Flowable 引擎版本号 */
    private int version;

    /** 部署 ID */
    private String deploymentId;

    /** 资源名称 */
    private String resourceName;

    /** 是否挂起 */
    private boolean suspended;

    /** 租户 ID */
    private String tenantId;

    /** 流程描述 */
    private String description;
}
