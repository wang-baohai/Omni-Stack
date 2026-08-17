package com.omni.workflow.dto;

/**
 * 工作流业务完成结果。
 *
 * @author Omni-Stack Team
 */
public enum WorkflowCompletionResult {
    /** 审批通过。 */
    APPROVED,
    /** 审批驳回。 */
    REJECTED,
    /** 流程取消或终止。 */
    CANCELLED
}
