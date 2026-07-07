package com.omni.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 组织树节点视图对象。
 *
 * @author Omni-Stack Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgTreeNodeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 组织单元 ID */
    private Long id;

    /** 父节点 ID */
    private Long parentId;

    /** 组织名称 */
    private String name;

    /** 组织类型: ORG/BRANCH/DEPT/WORKGROUP */
    private String type;

    /** 单元编码 */
    private String unitCode;

    /** 状态 */
    private Integer status;

    /** 子节点列表 */
    private List<OrgTreeNodeVO> children;
}
