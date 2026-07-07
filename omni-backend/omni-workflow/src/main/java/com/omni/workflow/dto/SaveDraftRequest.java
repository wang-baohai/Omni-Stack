package com.omni.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 保存草稿请求。
 * <p>前端设计器保存时提交，包含 designerJson 和可选的 BPMN XML。</p>
 *
 * @author Omni-Stack Team
 */
@Data
public class SaveDraftRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 可视化设计器 JSON */
    @NotBlank(message = "设计器 JSON 不能为空")
    private String designerJson;

    /** BPMN XML（前端可选生成，后端也会生成） */
    private String bpmnXml;

    /** 模型名称（允许在草稿阶段修改） */
    private String modelName;

    /** 流程分类 */
    private String category;
}
