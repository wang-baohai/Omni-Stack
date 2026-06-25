package com.omni.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omni.common.core.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统角色实体，映射 {@code sys_role} 表。
 * <p>
 * 支持多租户隔离，每个租户拥有独立的角色体系。
 * {@code roleCode} 字段存储角色编码（如 {@code ADMIN}、{@code USER}），
 * 与 JWT 中的 {@code roles} claim 对应。
 * {@code dataScope} 字段控制数据权限范围（如全部、本部门、本人等）。</p>
 *
 * @author Omni-Stack Team
 * @see SysPermission
 * @see SysUser
 * @see com.omni.common.core.model.BaseEntity
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
