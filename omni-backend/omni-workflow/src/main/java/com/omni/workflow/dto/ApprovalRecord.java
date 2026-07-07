package com.omni.workflow.dto;

import java.io.Serial;
import java.io.Serializable;

/**
 * 审批记录条目。
 * <p>
 * 扁平化的逐人审批详情，用于管理员在流程实例页面查看每个审批人的
 * 操作结果和审批意见。</p>
 *
 * @param nodeName     节点名称
 * @param assigneeId   审批人用户 ID
 * @param assigneeName 审批人昵称
 * @param result       审批结果：approved / rejected / auto-approved / cancelled / pending
 * @param comment      审批意见（可为 null）
 * @param approvalTime 审批时间（可为 null）
 * @author Omni-Stack Team
 */
public record ApprovalRecord(
        String nodeName,
        String assigneeId,
        String assigneeName,
        String result,
        String comment,
        String approvalTime
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
