package com.omni.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户基础资料更新请求。
 * <p>仅包含允许由用户管理接口修改的字段，账号、密码、租户、状态和审计字段必须走专用流程。</p>
 */
@Data
public class UpdateUserRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 昵称。 */
    @Size(max = 100, message = "昵称长度不能超过 100 个字符")
    private String nickname;

    /** 邮箱地址。 */
    @Email(message = "邮箱格式不正确")
    @Size(max = 255, message = "邮箱长度不能超过 255 个字符")
    private String email;

    /** 手机号。 */
    @Size(max = 32, message = "手机号长度不能超过 32 个字符")
    private String phone;

    /** 头像地址。 */
    @Size(max = 500, message = "头像地址长度不能超过 500 个字符")
    private String avatar;

    /** 性别：0-未知，1-男，2-女。 */
    @Min(value = 0, message = "性别值必须在 0 到 2 之间")
    @Max(value = 2, message = "性别值必须在 0 到 2 之间")
    private Integer gender;

    /** 主组织单元 ID。 */
    @Positive(message = "主组织单元 ID 必须为正整数")
    private Long primaryUnitId;
}
