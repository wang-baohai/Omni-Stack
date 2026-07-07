package com.omni.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 发起流程请求 DTO。
 *
 * @author Omni-Stack Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartProcessRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 流程定义 Key（BPMN 中的 process id，modelVersionId 未提供时必填） */
    private String processKey;

    /** 模型版本 ID（优先使用，走 startProcessInstanceById 路径） */
    private Long modelVersionId;

    /** 流程标题 */
    @NotBlank(message = "流程标题不能为空")
    private String title;

    /** 业务主键（外挂表单关联，可选） */
    private String businessKey;

    /** 流程分类 */
    private String category;

    /** 流程变量（JSON Schema 表单数据） */
    private Map<String, Object> variables;

    /** 模拟发起人用户 ID（调试用，可选） */
    private Long simulateUserId;

    /** 模拟发起人用户名（调试用，可选） */
    private String simulateUserName;
}
