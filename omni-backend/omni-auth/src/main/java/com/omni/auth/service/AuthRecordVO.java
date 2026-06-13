package com.omni.auth.service;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 授权记录视图对象，展示 OAuth2 授权的基本信息。
 */
@Data
@Builder
public class AuthRecordVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 授权记录 ID */
    private String id;
    /** 客户端 ID */
    private String registeredClientId;
    /** 主体（用户标识） */
    private String principalName;
    /** 授权类型 */
    private String authorizationGrantType;
    /** 已授权 Scope */
    private String authorizedScopes;
    /** 创建时间 */
    private LocalDateTime createdAt;
}
