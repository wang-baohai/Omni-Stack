package com.omni.common.core.internal;

import lombok.Data;

import java.io.Serializable;

/**
 * 内部 API 用户信息 DTO。
 * <p>用于服务间调用时传递用户基本信息，避免跨库查询。</p>
 *
 * @author Omni-Stack Team
 */
@Data
public class InternalUserDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long id;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 租户 ID */
    private Long tenantId;

    /** 主组织单元 ID */
    private Long primaryUnitId;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 头像 URL */
    private String avatar;

    /** 状态（1-启用，0-禁用） */
    private Integer status;
}
