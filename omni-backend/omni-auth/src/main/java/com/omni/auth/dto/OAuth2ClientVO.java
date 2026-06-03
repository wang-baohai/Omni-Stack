package com.omni.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * OAuth2 客户端视图对象，用于管理页面展示。
 */
@Data
@Builder
public class OAuth2ClientVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 内部 ID */
    private String id;

    /** OAuth2 客户端 ID */
    private String clientId;

    /** 客户端名称 */
    private String clientName;

    /** 客户端密钥（脱敏展示，仅显示前缀） */
    private String clientSecret;

    /** 认证方式列表 */
    private Set<String> authenticationMethods;

    /** 授权类型列表 */
    private Set<String> grantTypes;

    /** 回调地址列表 */
    private Set<String> redirectUris;

    /** 登出后回调地址列表 */
    private Set<String> postLogoutRedirectUris;

    /** 作用域列表 */
    private Set<String> scopes;

    /** 是否要求授权确认 */
    private boolean requireConsent;

    /** 是否要求 PKCE */
    private boolean requireProofKey;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
