package com.omni.auth.service.impl;

import com.omni.auth.dto.LoginResult;
import com.omni.auth.dto.ProviderUser;
import com.omni.auth.entity.SysRole;
import com.omni.auth.entity.SysTenant;
import com.omni.auth.entity.SysUser;
import com.omni.auth.entity.SysUserOauthProvider;
import com.omni.auth.event.AuditEvent;
import com.omni.auth.mapper.SysRoleMapper;
import com.omni.auth.mapper.SysTenantMapper;
import com.omni.auth.mapper.SysUserMapper;
import com.omni.auth.mapper.SysUserOauthProviderMapper;
import com.omni.auth.mapper.SysUserRoleMapper;
import com.omni.auth.oauth.OAuth2ProviderHandler;
import com.omni.auth.service.OnlineUserService;
import com.omni.auth.service.JwtTokenService;
import com.omni.auth.service.SocialLoginService;
import com.omni.auth.service.UserService;
import com.omni.auth.util.OAuth2StateUtils;
import com.omni.common.core.result.BusinessException;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 社交登录服务实现类。
 * <p>
 * 编排第三方 OAuth2 登录的完整流程：state 签名与验证、provider handler 调度、
 * 本地用户查找或自动创建、JWT 令牌生成。
 * 通过 {@link OAuth2ProviderHandler} 策略接口实现多 provider 支持，
 * 本类不包含任何 provider 特定逻辑。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialLoginServiceImpl implements SocialLoginService {

    /** 第三方 OAuth2 处理器映射，key 为 provider 名称（与 {@link OAuth2ProviderHandler#getProviderId()} 一致） */
    private final Map<String, OAuth2ProviderHandler> handlerMap;
    /** OAuth2 state 参数工具，负责 HMAC-SHA256 签名生成与验证 */
    private final OAuth2StateUtils oAuth2StateUtils;
    /** 第三方身份关联 Mapper，查询和持久化用户与第三方身份的绑定关系 */
    private final SysUserOauthProviderMapper sysUserOauthProviderMapper;
    /** 用户 Mapper，查询和创建本地用户记录 */
    private final SysUserMapper sysUserMapper;
    /** 租户 Mapper，校验租户合法性 */
    private final SysTenantMapper sysTenantMapper;
    /** 角色 Mapper，查询默认角色用于新用户角色分配 */
    private final SysRoleMapper sysRoleMapper;
    /** 用户角色关联 Mapper，为新用户分配默认角色 */
    private final SysUserRoleMapper sysUserRoleMapper;
    /** 用户服务，获取用户角色和权限列表 */
    private final UserService userService;
    /** JWT 令牌服务，签发包含用户身份和权限的 RS256 JWT */
    private final JwtTokenService jwtTokenService;
    /** 在线用户服务，记录用户在线状态到 Redis */
    private final OnlineUserService onlineUserService;
    /** Spring 事件发布器，用于发布审计事件 */
    private final ApplicationEventPublisher eventPublisher;

    /** 访问令牌有效期（秒），与 JWT 过期时间保持一致 */
    @Value("${auth.token.access-token-ttl:900}")
    private long accessTokenTtl;

    /**
     * {@inheritDoc}
     *
     * <p>校验提供商和租户合法性，生成带 HMAC 签名的 state 参数，
     * 委托对应 provider handler 构建授权页面 URL 并返回。</p>
     */
    @Override
    public String initiateLogin(String provider, Long tenantId) {
        // 校验提供商（通过 handlerMap 是否存在对应 key 判断）
        OAuth2ProviderHandler handler = handlerMap.get(provider);
        if (handler == null) {
            throw new BusinessException(400, "不支持的登录方式: " + provider);
        }

        // 校验租户存在
        SysTenant tenant = sysTenantMapper.selectById(tenantId);
        if (tenant == null || tenant.getStatus() != 1) {
            throw new BusinessException(400, "指定的租户不存在");
        }

        // 生成签名 state 并构建授权 URL
        String state = oAuth2StateUtils.createState(tenantId);
        log.info("发起 {} 登录: tenantId={}", provider, tenantId);
        return handler.buildAuthorizationUrl(state);
    }

    /**
     * {@inheritDoc}
     *
     * <p>完整回调处理流程：
     * <ol>
     *   <li>验证 state 参数，提取 tenantId</li>
     *   <li>委托 provider handler 用授权码换取 Access Token</li>
     *   <li>委托 provider handler 获取归一化用户资料</li>
     *   <li>查找已有关联或自动创建新用户</li>
     *   <li>加载角色/权限，生成 JWT</li>
     * </ol>
     * </p>
     */
    @Override
    @Transactional
    public LoginResult handleCallback(String provider, String code, String state) {
        // 1. 校验提供商并获取 handler
        OAuth2ProviderHandler handler = handlerMap.get(provider);
        if (handler == null) {
            throw new BusinessException(400, "不支持的登录方式: " + provider);
        }

        // 2. 验证 state 并提取租户 ID
        Long tenantId = oAuth2StateUtils.extractTenantId(state);

        // 3. 用授权码换取 Access Token
        String accessToken = handler.exchangeCodeForAccessToken(code);

        // 4. 获取归一化的第三方用户资料
        ProviderUser providerUser = handler.fetchUserProfile(accessToken);

        // 5. 查找已有的第三方身份关联
        SysUserOauthProvider oauthProvider = sysUserOauthProviderMapper
                .selectByProviderAndUserId(provider, providerUser.getProviderUserId());

        SysUser user;
        if (oauthProvider != null) {
            // 已有绑定：加载本地用户
            user = sysUserMapper.selectById(oauthProvider.getUserId());
            if (user == null) {
                throw new BusinessException(500, "关联的本地用户不存在");
            }
            // 更新 access_token 和最新的用户资料
            oauthProvider.setAccessToken(accessToken);
            oauthProvider.setProviderUsername(providerUser.getUsername());
            oauthProvider.setProviderEmail(providerUser.getEmail());
            oauthProvider.setProviderAvatar(providerUser.getAvatarUrl());
            sysUserOauthProviderMapper.updateById(oauthProvider);
            log.info("{} 用户匹配已有本地用户: userId={}, providerLogin={}",
                    provider, user.getId(), providerUser.getUsername());
        } else {
            // 首次登录：自动创建本地用户
            user = createNewUser(provider, providerUser, accessToken, tenantId);
            log.info("{} 首次登录，已自动创建本地用户: userId={}, username={}",
                    provider, user.getId(), user.getUsername());
        }

        // 6. 校验用户状态
        if (user.getStatus() != 1) {
            eventPublisher.publishEvent(AuditEvent.of(AuditEvent.LOGIN_FAILED)
                    .tenantId(tenantId)
                    .userId(user.getId())
                    .username(user.getUsername())
                    .description("社交登录失败: 用户已被禁用")
                    .createBy(user.getUsername())
                    .extra("reason", "account_disabled")
                    .extra("method", "social")
                    .extra("provider", provider)
                    .build());
            throw new BusinessException(403, "用户已被禁用，请联系管理员");
        }

        // 7. 加载角色和权限
        List<String> roles = userService.getUserRoles(user.getId());
        List<String> permissions = userService.getUserPermissions(user.getId());

        // 8. 生成 JWT
        String jwt = jwtTokenService.generateToken(user, roles, permissions);

        // 9. 记录在线用户（从 JWT 中解析 jti）
        try {
            String jti = SignedJWT.parse(jwt).getJWTClaimsSet().getJWTID();
            onlineUserService.recordOnline(user.getId(), user.getUsername(), jti, accessTokenTtl);
        } catch (Exception e) {
            log.warn("记录在线用户失败: {}", e.getMessage());
        }

        // 10. 发布社交登录成功审计事件
        eventPublisher.publishEvent(AuditEvent.of(AuditEvent.LOGIN_SUCCESS)
                .tenantId(tenantId)
                .userId(user.getId())
                .username(user.getUsername())
                .description(provider + " 社交登录成功")
                .createBy(user.getUsername())
                .extra("method", "social")
                .extra("provider", provider)
                .build());

        return LoginResult.builder()
                .accessToken(jwt)
                .tokenType("Bearer")
                .expiresIn(accessTokenTtl)
                .build();
    }

    /**
     * 根据 provider 名称返回自动注册时的用户名前缀。
     *
     * @param provider 提供商标识
     * @return 用户名前缀（如 github -> "gh_"，google -> "go_"，gitee -> "ge_"）
     */
    private static String getUsernamePrefix(String provider) {
        return switch (provider) {
            case "github" -> "gh_";
            case "google" -> "go_";
            case "gitee" -> "ge_";
            default -> provider + "_";
        };
    }

    /**
     * 为首次第三方登录的用户创建本地用户和身份关联记录。
     *
     * @param provider    提供商标识
     * @param providerUser 归一化的第三方用户信息
     * @param accessToken 第三方 Access Token
     * @param tenantId    目标租户 ID
     * @return 创建的本地用户实体（含已回填的 ID）
     */
    private SysUser createNewUser(String provider, ProviderUser providerUser, String accessToken, Long tenantId) {
        String prefix = getUsernamePrefix(provider);
        String username = prefix + providerUser.getUsername();

        // 创建本地用户
        SysUser user = new SysUser();
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setPassword(null);
        user.setNickname(providerUser.getDisplayName() != null ? providerUser.getDisplayName() : providerUser.getUsername());
        user.setEmail(providerUser.getEmail());
        user.setAvatar(providerUser.getAvatarUrl());
        user.setGender(0);
        user.setStatus(1);

        try {
            sysUserMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 用户名冲突：fallback 为 {prefix}{login}_{providerUserId}
            log.warn("用户名冲突，使用 fallback 用户名: {}", username);
            username = prefix + providerUser.getUsername() + "_" + providerUser.getProviderUserId();
            user.setUsername(username);
            sysUserMapper.insert(user);
        }

        // 创建第三方身份关联记录
        SysUserOauthProvider oauthProvider = new SysUserOauthProvider();
        oauthProvider.setUserId(user.getId());
        oauthProvider.setProvider(provider);
        oauthProvider.setProviderUserId(providerUser.getProviderUserId());
        oauthProvider.setProviderUsername(providerUser.getUsername());
        oauthProvider.setProviderEmail(providerUser.getEmail());
        oauthProvider.setProviderAvatar(providerUser.getAvatarUrl());
        oauthProvider.setAccessToken(accessToken);

        try {
            sysUserOauthProviderMapper.insert(oauthProvider);
        } catch (DuplicateKeyException e) {
            // 并发竞态：另一个请求已经创建了关联，重新查询
            log.warn("第三方身份关联并发创建，重新查询: provider={}, providerUserId={}",
                    provider, providerUser.getProviderUserId());
            oauthProvider = sysUserOauthProviderMapper
                    .selectByProviderAndUserId(provider, providerUser.getProviderUserId());
            if (oauthProvider != null) {
                user = sysUserMapper.selectById(oauthProvider.getUserId());
            }
        }

        // 分配默认 USER 角色（失败不阻塞用户创建）
        try {
            SysRole defaultRole = sysRoleMapper.selectByTenantIdAndRoleCode(tenantId, "USER");
            if (defaultRole != null) {
                sysUserRoleMapper.insert(user.getId(), defaultRole.getId());
            } else {
                log.warn("未找到租户 {} 的默认 USER 角色，跳过角色分配", tenantId);
            }
        } catch (Exception e) {
            log.warn("为用户 {} 分配默认角色失败: {}", user.getUsername(), e.getMessage());
        }

        // 发布用户创建审计事件
        eventPublisher.publishEvent(AuditEvent.of(AuditEvent.USER_CREATED)
                .tenantId(tenantId)
                .userId(user.getId())
                .username(user.getUsername())
                .description("社交登录首次登录，自动创建用户")
                .createBy("system")
                .extra("method", "social_first_login")
                .extra("provider", provider)
                .build());

        return user;
    }
}
