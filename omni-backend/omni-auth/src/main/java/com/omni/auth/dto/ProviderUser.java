package com.omni.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一的第三方平台用户信息 DTO。
 * <p>
 * 由各 {@link com.omni.auth.oauth.OAuth2ProviderHandler} 实现内部映射，
 * 将不同第三方 API 的原始响应归一化为此通用结构，使上层编排逻辑与 provider 特定字段完全解耦。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 第三方平台唯一用户 ID（统一为字符串，兼容各平台的数字/字符串 ID） */
    private String providerUserId;

    /** 第三方平台登录名（如 GitHub 的 login、Gitee 的 login） */
    private String username;

    /** 显示名称（可能为 null） */
    private String displayName;

    /** 邮箱地址（用户设为隐私时可能为 null） */
    private String email;

    /** 头像 URL */
    private String avatarUrl;
}
