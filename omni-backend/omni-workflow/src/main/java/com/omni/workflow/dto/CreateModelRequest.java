package com.omni.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 创建流程模型请求。
 *
 * @author Omni-Stack Team
 */
@Data
public class CreateModelRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 模型标识（BPMN process id，英文+数字+短横线） */
    @NotBlank(message = "模型标识不能为空")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9-]*$", message = "模型标识必须以字母开头，仅含字母、数字、短横线")
    private String modelKey;

    /** 模型名称 */
    @NotBlank(message = "模型名称不能为空")
    private String modelName;

    /** 流程分类 */
    private String category;

    /** 初始设计器 JSON（可选） */
    private String designerJson;
}
