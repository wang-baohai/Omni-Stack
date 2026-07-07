package com.omni.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 审批操作请求 DTO。
 *
 * @author Omni-Stack Team
 */
@Data
public class ApprovalRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否通过（true=通过, false=驳回） */
    private boolean approved;

    /** 审批意见 */
    private String comment;

    /** 附加流程变量 */
    private Map<String, Object> variables;
}
