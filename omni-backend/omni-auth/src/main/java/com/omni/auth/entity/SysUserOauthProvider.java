package com.omni.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omni.common.core.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户第三方身份关联实体。
 * <p>
 * 记录本地用户与第三方 OAuth2 提供者（如 GitHub、Google 等）的绑定关系，
 * 支持一个本地用户关联多个第三方身份，每个第三方身份唯一对应一个本地用户。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_oauth_provider")
public class SysUserOauthProvider extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 本地用户 ID */
    private Long userId;
    /** 提供商标识（github/google/wechat/gitee） */
    private String provider;
    /** 第三方用户 ID */
    private String providerUserId;
    /** 第三方用户名 */
    private String providerUsername;
    /** 第三方邮箱 */
    private String providerEmail;
    /** 第三方头像 URL */
    private String providerAvatar;
    /** 第三方访问令牌 */
    private String accessToken;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
