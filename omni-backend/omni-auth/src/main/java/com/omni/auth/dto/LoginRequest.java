package com.omni.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录请求参数。
 * <p>用于用户名/密码登录，包含用户名、密码、租户 ID 和验证码信息。
 * 验证码通过 {@code captchaKey} 关联 Redis 中存储的正确答案。</p>
 *
 * @author Omni-Stack Team
 * @see LoginResult
 * @see CaptchaResult
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
