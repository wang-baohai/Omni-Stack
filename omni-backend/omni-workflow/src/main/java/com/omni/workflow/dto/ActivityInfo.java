package com.omni.workflow.dto;

import java.io.Serializable;
import java.util.List;

/**
 * 流程活动节点信息 DTO。
 * <p>描述流程中单个活动节点的运行状态和处理信息。</p>
 *
 * @author Omni-Stack Team
 */
public record ActivityInfo(
        /** 活动 ID（BPMN 元素 ID） */
        String activityId,
        /** 活动名称 */
        String activityName,
        /** 活动类型（userTask, exclusiveGateway, parallelGateway, serviceTask 等） */
        String activityType,
        /** 处理人用户 ID */
        String assignee,
        /** 处理人姓名（多人用顿号连接，非会签节点使用） */
        String assigneeName,
        /** 开始时间 */
        String startTime,
        /** 结束时间 */
        String endTime,
        /** 状态：completed-已完成, active-进行中, pending-未到达 */
        String status,
        /** 逐人审批状态列表（仅会签节点有值，null 表示非会签或 pending 节点） */
        List<AssigneeStatus> assigneeStatuses,
        /** 已完成审批人数（仅 active 会签节点有值，用于 BPMN 图进度徽章） */
        Integer completedCount,
        /** 总审批人数（仅 active 会签节点有值，用于 BPMN 图进度徽章） */
        Integer totalCount
) implements Serializable {

    private static final long serialVersionUID = 2L;
}
