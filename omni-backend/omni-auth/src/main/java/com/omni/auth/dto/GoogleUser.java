package com.omni.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * Google 用户信息 DTO。
 * <p>
 * 对应 Google UserInfo API {@code GET https://www.googleapis.com/oauth2/v3/userinfo}
 * 返回的用户资料，仅映射社交登录流程所需的字段。
 * </p>
 * @author Omni-Stack Team
 * @see ProviderUser
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Google 用户唯一标识（如 "110248495921238986453"） */
    private String sub;
    /** 用户全名（如 "John Doe"） */
    private String name;
    /** 名（如 "John"） */
    @JsonProperty("given_name")
    private String givenName;
    /** 姓（如 "Doe"） */
    @JsonProperty("family_name")
    private String familyName;
    /** 头像 URL */
    private String picture;
    /** 邮箱地址 */
    private String email;
    /** 邮箱是否已验证 */
    @JsonProperty("email_verified")
    private Boolean emailVerified;
    /** 用户语言偏好（如 "en"） */
    private String locale;
}
