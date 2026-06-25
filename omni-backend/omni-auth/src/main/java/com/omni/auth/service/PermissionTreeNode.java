package com.omni.auth.service;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 权限树节点视图对象，包含子节点嵌套列表和选中状态。
 * <p>{@code checked} 字段用于前端权限分配树形控件，标记角色已分配的权限。</p>
 *
 * @author Omni-Stack Team
 * @see PermissionService
 */
@Data
@Builder
public class PermissionTreeNode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 权限 ID */
    private Long id;
    /** 父级 ID */
    private Long parentId;
    /** 权限编码 */
    private String permissionCode;
    /** 权限名称 */
    private String permissionName;
    /** 类型：DIRECTORY / MENU / API */
    private String type;
    /** 物化路径 */
    private String path;
    /** 深度 */
    private Integer depth;
    /** 排序值 */
    private Integer sort;
    /** 状态：1-启用, 0-禁用 */
    private Integer status;
    /** 是否已分配给角色 */
    private Boolean checked;
    /** 子节点列表 */
    private List<PermissionTreeNode> children;
}
