package com.omni.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/**
 * 创建 OAuth2 客户端请求参数。
 * <p>用于管理界面新增 OAuth2 客户端，支持 PKCE 和机密客户端两种模式。
 * 当 {@code clientId}/{@code clientSecret} 为空时自动生成。</p>
 *
 * @author Omni-Stack Team
 * @see UpdateOAuth2ClientRequest
 * @see OAuth2ClientVO
 */
@Data
public class CreateOAuth2ClientRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 客户端名称 */
    @NotBlank(message = "客户端名称不能为空")
    private String clientName;

    /** 客户端 ID（为空时自动生成） */
    private String clientId;

    /** 客户端密钥（为空时自动生成，仅非 PKCE 客户端需要） */
    private String clientSecret;

    /** 认证方式：none, client_secret_basic, client_secret_post */
    @NotEmpty(message = "认证方式不能为空")
    private Set<String> authenticationMethods;

    /** 授权类型：authorization_code, refresh_token, client_credentials */
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
