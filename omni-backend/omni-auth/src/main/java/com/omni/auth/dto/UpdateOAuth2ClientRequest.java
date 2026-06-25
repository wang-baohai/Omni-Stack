package com.omni.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/**
 * 更新 OAuth2 客户端请求参数。
 * <p>用于修改已有 OAuth2 客户端配置，不包含 clientId/clientSecret（不可修改）。</p>
 *
 * @author Omni-Stack Team
 * @see CreateOAuth2ClientRequest
 * @see OAuth2ClientVO
 */
@Data
public class UpdateOAuth2ClientRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 客户端名称 */
    @NotBlank(message = "客户端名称不能为空")
    private String clientName;

    /** 认证方式 */
    @NotEmpty(message = "认证方式不能为空")
    private Set<String> authenticationMethods;

    /** 授权类型 */
    @NotEmpty(message = "授权类型不能为空")
    private Set<String> grantTypes;

    /** 回调地址列表 */
    private Set<String> redirectUris;

    /** 登出后回调地址列表 */
    private Set<String> postLogoutRedirectUris;

    /** 作用域列表 */
    @NotEmpty(message = "作用域不能为空")
    private Set<String> scopes;

    /** 是否要求授权确认 */
    private boolean requireConsent;

    /** 是否要求 PKCE */
    private boolean requireProofKey;
}
