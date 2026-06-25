package com.omni.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * OAuth2 注册客户端实体，映射 {@code oauth2_registered_client} 表。
 * <p>
 * 由 Spring Authorization Server 的 {@code JdbcRegisteredClientRepository} 管理，
 * 此实体仅用于 MyBatis-Plus 分页查询和管理界面展示。
 * 客户端的创建和修改应通过 {@link com.omni.auth.config.OAuth2ClientInitializer} 或
 * SAS 提供的 {@code RegisteredClientRepository} 接口进行。</p>
 *
 * <p>表结构特点：主键为 UUID 字符串，{@code client_settings} 和 {@code token_settings} 以 JSON 格式存储。</p>
 *
 * @author Omni-Stack Team
 * @see com.omni.auth.config.OAuth2ClientInitializer
 * @see com.omni.auth.config.DeviceClientInitializer
 */
@Data
@TableName("oauth2_registered_client")
public class OAuth2RegisteredClient implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（UUID 字符串） */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** OAuth2 客户端 ID */
    private String clientId;

    /** 客户端 ID 签发时间 */
    private LocalDateTime clientIdIssuedAt;

    /** 客户端密钥（加密存储） */
    private String clientSecret;

    /** 客户端密钥过期时间 */
    private LocalDateTime clientSecretExpiresAt;

    /** 客户端名称 */
    private String clientName;

    /** 认证方式（逗号分隔） */
    private String clientAuthenticationMethods;

    /** 授权类型（逗号分隔） */
    private String authorizationGrantTypes;

    /** 回调地址（逗号分隔） */
    private String redirectUris;

    /** 登出后回调地址（逗号分隔） */
    private String postLogoutRedirectUris;

    /** 作用域（逗号分隔） */
    private String scopes;

    /** 客户端设置（JSON） */
    private String clientSettings;

    /** 令牌设置（JSON） */
    private String tokenSettings;
}
