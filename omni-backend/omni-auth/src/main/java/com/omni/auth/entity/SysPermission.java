package com.omni.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omni.common.core.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统权限实体。
 * <p>使用物化路径实现权限的层级结构（如菜单 -> 按钮 -> 操作）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class SysPermission extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;
    /** 父权限 ID */
    private Long parentId;
    /** 权限编码（如 user:read） */
    private String permissionCode;
    /** 权限名称 */
    private String permissionName;
    /** 权限类型 */
    private String type;
    /** 物化路径 */
    private String path;
    /** 树深度 */
    private Integer depth;
    /** 排序号 */
    private Integer sort;
    /** 状态（1-启用，0-禁用） */
    private Integer status;
}
