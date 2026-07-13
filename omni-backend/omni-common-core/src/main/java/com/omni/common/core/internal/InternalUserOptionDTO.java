package com.omni.common.core.internal;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 内部 API 用户候选项 DTO。
 * <p>仅包含负责人选择所需的最小字段，不传递手机号、邮箱等个人敏感信息。</p>
 *
 * @author Omni-Stack Team
 */
@Data
public class InternalUserOptionDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long id;

    /** 用户名 */
    private String username;

    /** 用户昵称 */
    private String nickname;

    /** 租户 ID */
    private Long tenantId;

    /** 主组织单元 ID */
    private Long primaryUnitId;

    /** 头像 URL */
    private String avatar;
}
