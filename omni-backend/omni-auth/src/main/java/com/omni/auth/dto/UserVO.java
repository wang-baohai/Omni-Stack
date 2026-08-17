package com.omni.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户管理查询视图。
 * <p>该类型有意不包含密码哈希或其他认证材料。</p>
 */
@Data
@Builder
public class UserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID。 */ private Long id;
    /** 租户 ID。 */ private Long tenantId;
    /** 登录账号。 */ private String username;
    /** 昵称。 */ private String nickname;
    /** 邮箱。 */ private String email;
    /** 手机号。 */ private String phone;
    /** 头像地址。 */ private String avatar;
    /** 性别。 */ private Integer gender;
    /** 主组织单元 ID。 */ private Long primaryUnitId;
    /** 账号状态。 */ private Integer status;
    /** 创建时间。 */ private LocalDateTime createTime;
    /** 更新时间。 */ private LocalDateTime updateTime;
}
