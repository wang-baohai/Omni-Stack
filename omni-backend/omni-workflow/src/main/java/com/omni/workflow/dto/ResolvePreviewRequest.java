package com.omni.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 角色解析预览请求。
 * <p>前端传入分配配置 JSON，后端模拟解析出候选人列表。</p>
 *
 * @author Omni-Stack Team
 */
@Data
public class ResolvePreviewRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 分配类型: SCOPED_ROLE / SCOPED_ORG_ROLE */
    @NotBlank(message = "分配类型不能为空")
    private String assignmentType;

    /** 角色编码 */
    private String roleCode;

    /** 锚点类型（新）: START_USER_PRIMARY_UNIT / PARENT / CHILD_UNIT / SIBLING_UNIT / ABSOLUTE_UNIT; （旧，兼容）: PARENT_BY_TYPE / CHILD_BY_CODE / SIBLING_BY_CODE / PARENT_CHILDREN / DEPT_BY_CODE */
    private String anchorType;

    /** 锚点参数（如 targetType, childCode, unitId 等） */
    private Map<String, Object> anchorParams;

    /** 作用域模式: SAME_UNIT（新流程定义隐含 SAME_UNIT，此字段仅为兼容旧定义保留） / CHILDREN_ONLY / UNIT_AND_BELOW */
    private String scopeMode;

    /** 模拟发起人用户 ID（用于解析锚点） */
    private Long simulateUserId;
}
