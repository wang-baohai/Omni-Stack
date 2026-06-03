package com.omni.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录请求参数。
 */
@Data
public class LoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户名，不能为空 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码，不能为空 */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 租户 ID，不能为 null */
    @NotNull(message = "租户 ID 不能为空")
    private Long tenantId;

    /** 验证码 Key（UUID），不能为空 */
    @NotBlank(message = "验证码 Key 不能为空")
    private String captchaKey;

    /** 验证码内容，不能为空 */
    @NotBlank(message = "验证码不能为空")
    private String captchaCode;
}
