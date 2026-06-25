package com.omni.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理员创建用户请求参数。
 * <p>用于管理员在后台手动创建用户，与用户自助注册（{@link RegisterRequest}）不同，
 * 无需验证码校验，且必须指定租户 ID。</p>
 *
 * @author Omni-Stack Team
 * @see RegisterRequest
 * @see com.omni.auth.entity.SysUser
 */
@Data
public class CreateUserRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户名，不能为空 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码，不能为空且至少 6 个字符 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, message = "密码至少 6 个字符")
    private String password;

    /** 昵称（可选） */
    private String nickname;

    /** 邮箱地址（可选，需符合邮箱格式） */
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 手机号（可选） */
    private String phone;

    /** 性别：0-未知, 1-男, 2-女（可选） */
    private Integer gender;

    /** 租户 ID，不能为空 */
    @NotNull(message = "租户 ID 不能为空")
    private Long tenantId;
}
