package com.omni.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户角色作用域实体。
 * <p>
 * 表达"某用户在某组织范围内拥有某角色"的语义，
 * 用于工作流审批节点的动态候选人解析。</p>
 *
 * @author Omni-Stack Team
 */
@Data
@TableName("sys_user_role_scope")
public class SysUserRoleScope implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 用户 ID */
    private Long userId;

    /** 角色 ID */
    private Long roleId;

    /** 组织单元 ID */
    private Long unitId;

    /** 作用域模式: SAME_UNIT / UNIT_AND_BELOW */
    private String scopeMode;

    /** 状态: 0-禁用, 1-启用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
