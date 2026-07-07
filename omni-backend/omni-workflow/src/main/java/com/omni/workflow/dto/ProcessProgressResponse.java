package com.omni.workflow.dto;

import java.io.Serializable;
import java.util.List;

/**
 * 流程进度响应 DTO。
 * <p>包含流程实例的活动节点状态信息，用于渲染流程全景图。</p>
 *
 * @author Omni-Stack Team
 */
public record ProcessProgressResponse(
        /** 已完成的活动列表 */
        List<ActivityInfo> completedActivities,
        /** 当前进行中的活动 ID 列表 */
        List<String> activeActivityIds,
        /** 所有活动列表（含状态标注） */
        List<ActivityInfo> allActivities
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
