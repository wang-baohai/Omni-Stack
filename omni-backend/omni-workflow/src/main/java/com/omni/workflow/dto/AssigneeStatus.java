package com.omni.workflow.dto;

import java.io.Serializable;

/**
 * 逐人审批状态 DTO。
 * <p>用于会签（多实例）节点，展示每个审批人的独立审批状态。</p>
 *
 * @author Omni-Stack Team
 */
public record AssigneeStatus(
        /** 用户 ID */
        String userId,
        /** 用户昵称（已解析） */
        String userName,
        /** 状态：completed-已完成, active-待审批, auto-completed-自动通过（ANY 模式下被 completionCondition 跳过） */
        String status
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
