package com.omni.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omni.common.core.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统角色实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;
    /** 角色编码（如 ADMIN、USER） */
    private String roleCode;
    /** 角色名称 */
    private String roleName;
    /** 数据权限范围 */
    private String dataScope;
    /** 排序号 */
    private Integer sort;
    /** 状态（1-启用，0-禁用） */
    private Integer status;
}
