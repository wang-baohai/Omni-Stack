package com.omni.auth.service;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 在线用户视图对象，展示在线用户的基本信息和 Token 标识。
 * <p>包含用户 ID、用户名、JWT Token ID（jti）和主组织单元 ID。</p>
 *
 * @author Omni-Stack Team
 * @see OnlineUserService
 */
@Data
@Builder
public class OnlineUserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long userId;
    /** 用户名 */
    private String username;
    /** JWT Token ID */
    private String jti;
    /** 主组织单元 ID（用于数据权限过滤） */
    private Long primaryUnitId;
}
