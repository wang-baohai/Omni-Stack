package com.omni.workflow.dto.internal;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 不包含原始 BPMN 的安全审批图预览。
 *
 * @author Omni-Stack Team
 */
@Data
@Builder
public class InternalApprovalPreviewResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 预览契约版本。 */ private Integer approvalPreviewVersion;
    /** 模型版本元数据。 */ private InternalPublishedModelVersionResponse modelVersion;
    /** 安全节点列表。 */ private List<Node> nodes;
    /** 安全有向边列表。 */ private List<Edge> edges;
    /** 是否存在分支。 */ private boolean hasBranches;
    /** 仅无环单路径时返回的业务步骤摘要。 */ private List<String> linearSummary;

    /** 安全审批节点。 */
    @Data
    @Builder
    public static class Node implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** BPMN 节点 ID。 */ private String id;
        /** 节点业务名称。 */ private String name;
        /** START/END/APPROVAL/GATEWAY/SERVICE。 */ private String type;
        /** UserTask 角色编码。 */ private String roleCode;
        /** UserTask 审批模式 ALL/ANY。 */ private String approvalMode;
        /** 业务化说明。 */ private String description;
    }

    /** 安全审批图有向边。 */
    @Data
    @Builder
    public static class Edge implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** BPMN SequenceFlow ID。 */ private String id;
        /** 分支显示名。 */ private String name;
        /** 源节点 ID。 */ private String source;
        /** 目标节点 ID。 */ private String target;
        /** 是否为网关默认分支。 */ private boolean defaultBranch;
        /** 已脱敏条件摘要。 */ private String conditionSummary;
    }
}
