package com.omni.auth.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 更新角色请求 DTO。
 * <p>所有字段可选，{@code null} 表示不修改该字段。
 * {@code permissionIds} 非 {@code null} 时全量替换角色的权限关联。</p>
 *
 * @author Omni-Stack Team
 * @see CreateRoleRequest
 * @see com.omni.auth.entity.SysRole
 */
@Data
public class UpdateRoleRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色名称 */
    private String roleName;

    /** 数据范围：ALL / TENANT / DEPT_AND_BELOW / DEPT / SELF / CUSTOM */
    private String dataScope;

    /** 排序值 */
    private Integer sort;

    /** 状态：1-启用, 0-禁用 */
    private Integer status;

    /** 权限 ID 列表（非 null 时全量替换） */
    private List<Long> permissionIds;
}
