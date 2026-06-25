package com.omni.auth.service;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 组织单元树节点视图对象，包含子节点嵌套列表。
 * <p>用于前端组织树渲染，支持递归展示公司/部门/小组层级结构。</p>
 *
 * @author Omni-Stack Team
 * @see OrgUnitService
 */
@Data
@Builder
public class OrgUnitTreeNode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 组织单元 ID */
    private Long id;
    /** 父级 ID */
    private Long parentId;
    /** 名称 */
    private String name;
    /** 类型：ORG / SUBSIDIARY / DEPT / TEAM */
    private String type;
    /** 物化路径 */
    private String path;
    /** 深度 */
    private Integer depth;
    /** 排序值 */
    private Integer sort;
    /** 状态：1-启用, 0-禁用 */
    private Integer status;
    /** 子节点列表 */
    private List<OrgUnitTreeNode> children;
}
