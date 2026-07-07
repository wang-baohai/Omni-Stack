package com.omni.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 部署流程请求 DTO。
 *
 * @author Omni-Stack Team
 */
@Data
public class DeployProcessRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 流程名称 */
    @NotBlank(message = "流程名称不能为空")
    private String name;

    /** 流程分类 */
    private String category;

    /** BPMN XML 内容 */
    @NotBlank(message = "BPMN XML 不能为空")
    private String bpmnXml;
}
