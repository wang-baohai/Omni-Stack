package com.omni.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 创建角色请求 DTO。
 * <p>用于新增系统角色，支持创建时直接分配初始权限列表。角色编码在同一租户下唯一。</p>
 *
 * @author Omni-Stack Team
 * @see UpdateRoleRequest
 * @see com.omni.auth.entity.SysRole
 */
@Data
public class CreateRoleRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色编码（唯一标识） */
    @NotBlank(message = "角色编码不能为空")
    private String roleCode;

    /** 角色名称 */
    @NotBlank(message = "角色名称不能为空")
    private String roleName;

    /** 数据范围：ALL / TENANT / DEPT_AND_BELOW / DEPT / SELF / CUSTOM */
    @NotBlank(message = "数据范围不能为空")
    private String dataScope;

    /** 排序值 */
    private Integer sort;

    /** 状态：1-启用, 0-禁用 */
    private Integer status;

    /** 初始权限 ID 列表（可选） */
    private List<Long> permissionIds;
}
