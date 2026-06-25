package com.omni.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户自助注册请求参数。
 * <p>用于未登录用户自主注册账号，需通过验证码校验。
 * 与管理员创建用户（{@link CreateUserRequest}）相比，额外要求验证码字段。</p>
 *
 * @author Omni-Stack Team
 * @see CreateUserRequest
 * @see LoginRequest
 */
@Data
public class RegisterRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户名，3-32 个字符 */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度 3-32 个字符")
    private String username;

    /** 密码，6-64 个字符 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度 6-64 个字符")
    private String password;

    /** 昵称（可选） */
    private String nickname;

    /** 邮箱地址（可选，需符合邮箱格式） */
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 租户 ID */
    @NotNull(message = "租户不能为空")
    private Long tenantId;

    /** 验证码 Key（UUID），不能为空 */
    @NotBlank(message = "验证码 Key 不能为空")
    private String captchaKey;

    /** 验证码内容，不能为空 */
    @NotBlank(message = "验证码不能为空")
    private String captchaCode;
}
