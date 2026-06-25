package com.omni.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omni.common.core.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统用户实体，映射 {@code sys_user} 表。
 * <p>
 * 支持多租户隔离，同一租户下用户名唯一。
 * 密码使用 BCrypt 加密存储，支持通过 {@code primaryUnitId} 关联主组织单元。
 * {@code status} 字段控制账号启用/禁用状态，禁用后无法登录。</p>
 *
 * @author Omni-Stack Team
 * @see SysTenant
 * @see SysOrgUnit
 * @see SysUserOauthProvider
 * @see com.omni.common.core.model.BaseEntity
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;
    /** 用户名 */
    private String username;
    /** 密码（BCrypt 加密） */
    private String password;
    /** 昵称 */
    private String nickname;
    /** 邮箱地址 */
    private String email;
    /** 手机号 */
    private String phone;
    /** 头像 URL */
    private String avatar;
    /** 性别（0-未知，1-男，2-女） */
    private Integer gender;
    /** 主组织单元 ID */
    private Long primaryUnitId;
    /** 状态（1-启用，0-禁用） */
    private Integer status;
}
