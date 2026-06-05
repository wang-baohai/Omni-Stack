package com.omni.auth.service.impl;

import com.omni.auth.dto.GitHubUser;
import com.omni.auth.dto.LoginResult;
import com.omni.auth.entity.SysTenant;
import com.omni.auth.entity.SysUser;
import com.omni.auth.entity.SysUserOauthProvider;
import com.omni.auth.mapper.SysTenantMapper;
import com.omni.auth.mapper.SysUserMapper;
import com.omni.auth.mapper.SysUserOauthProviderMapper;
import com.omni.auth.oauth.GitHubOAuth2Handler;
import com.omni.auth.service.JwtTokenService;
import com.omni.auth.service.SocialLoginService;
import com.omni.auth.service.UserService;
import com.omni.auth.util.OAuth2StateUtils;
import com.omni.common.core.result.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 社交登录服务实现类。
 * <p>
 * 编排第三方 OAuth2 登录的完整流程：state 签名与验证、GitHub API 交互、
 * 本地用户查找或自动创建、JWT 令牌生成。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialLoginServiceImpl implements SocialLoginService {

    /** GitHub 提供商标识 */
    private static final String PROVIDER_GITHUB = "github";
    /** 第三方用户名前缀 */
    private static final String USERNAME_PREFIX = "gh_";

    /** GitHub OAuth2 处理器，负责构建授权 URL、换取 token、获取用户资料 */
    private final GitHubOAuth2Handler gitHubOAuth2Handler;
    /** OAuth2 state 参数工具，负责 HMAC-SHA256 签名生成与验证 */
    private final OAuth2StateUtils oAuth2StateUtils;
    /** 第三方身份关联 Mapper，查询和持久化用户与第三方身份的绑定关系 */
    private final SysUserOauthProviderMapper sysUserOauthProviderMapper;
    /** 用户 Mapper，查询和创建本地用户记录 */
    private final SysUserMapper sysUserMapper;
    /** 租户 Mapper，校验租户合法性 */
    private final SysTenantMapper sysTenantMapper;
    /** 用户服务，获取用户角色和权限列表 */
    private final UserService userService;
    /** JWT 令牌服务，签发包含用户身份和权限的 RS256 JWT */
    private final JwtTokenService jwtTokenService;

    /**
     * {@inheritDoc}
     *
     * <p>校验提供商和租户合法性，生成带 HMAC 签名的 state 参数，
     * 构建 GitHub 授权页面 URL 并返回。</p>
     */
    @Override
    public String initiateLogin(String provider, Long tenantId) {
        // 校验提供商
        if (!PROVIDER_GITHUB.equals(provider)) {
            throw new BusinessException(400, "不支持的登录方式: " + provider);
        }

        // 校验租户存在
        SysTenant tenant = sysTenantMapper.selectById(tenantId);
        if (tenant == null || tenant.getStatus() != 1) {
            throw new BusinessException(400, "指定的租户不存在");
        }

        // 生成签名 state 并构建授权 URL
        String state = oAuth2StateUtils.createState(tenantId);
        log.info("发起 GitHub 登录: tenantId={}", tenantId);
        return gitHubOAuth2Handler.buildAuthorizationUrl(state);
    }

    /**
     * {@inheritDoc}
     *
     * <p>完整回调处理流程：
     * <ol>
     *   <li>验证 state 参数，提取 tenantId</li>
     *   <li>用授权码换取 GitHub Access Token</li>
     *   <li>获取 GitHub 用户资料</li>
     *   <li>查找已有关联或自动创建新用户</li>
     *   <li>加载角色/权限，生成 JWT</li>
     * </ol>
     * </p>
     */
    @Override
    @Transactional
    public LoginResult handleCallback(String provider, String code, String state) {
        // 1. 验证 state 并提取租户 ID
        if (!PROVIDER_GITHUB.equals(provider)) {
            throw new BusinessException(400, "不支持的登录方式: " + provider);
        }
        Long tenantId = oAuth2StateUtils.extractTenantId(state);

        // 2. 用授权码换取 GitHub Access Token
        String accessToken = gitHubOAuth2Handler.exchangeCodeForAccessToken(code);

        // 3. 获取 GitHub 用户资料
        GitHubUser githubUser = gitHubOAuth2Handler.fetchUserProfile(accessToken);
        String providerUserId = String.valueOf(githubUser.getId());

        // 4. 查找已有的第三方身份关联
        SysUserOauthProvider oauthProvider = sysUserOauthProviderMapper
                .selectByProviderAndUserId(PROVIDER_GITHUB, providerUserId);

        SysUser user;
        if (oauthProvider != null) {
            // 已有绑定：加载本地用户
            user = sysUserMapper.selectById(oauthProvider.getUserId());
            if (user == null) {
                throw new BusinessException(500, "关联的本地用户不存在");
            }
            // 更新 access_token
            oauthProvider.setAccessToken(accessToken);
            oauthProvider.setProviderUsername(githubUser.getLogin());
            oauthProvider.setProviderEmail(githubUser.getEmail());
            oauthProvider.setProviderAvatar(githubUser.getAvatarUrl());
            sysUserOauthProviderMapper.updateById(oauthProvider);
            log.info("GitHub 用户匹配已有本地用户: userId={}, githubLogin={}", user.getId(), githubUser.getLogin());
        } else {
            // 首次登录：自动创建本地用户
            user = createNewUser(githubUser, accessToken, tenantId);
            log.info("GitHub 首次登录，已自动创建本地用户: userId={}, username={}", user.getId(), user.getUsername());
        }

        // 5. 校验用户状态
        if (user.getStatus() != 1) {
            throw new BusinessException(403, "用户已被禁用，请联系管理员");
        }

        // 6. 加载角色和权限
        List<String> roles = userService.getUserRoles(user.getId());
        List<String> permissions = userService.getUserPermissions(user.getId());

        // 7. 生成 JWT
        String jwt = jwtTokenService.generateToken(user, roles, permissions);

        return LoginResult.builder()
                .accessToken(jwt)
                .tokenType("Bearer")
                .expiresIn(900L)
                .build();
    }

    /**
     * 为首次第三方登录的用户创建本地用户和身份关联记录。
     *
     * @param githubUser  GitHub 用户信息
     * @param accessToken GitHub Access Token
     * @param tenantId    目标租户 ID
     * @return 创建的本地用户实体（含已回填的 ID）
     */
    private SysUser createNewUser(GitHubUser githubUser, String accessToken, Long tenantId) {
        String username = USERNAME_PREFIX + githubUser.getLogin();

        // 创建本地用户
        SysUser user = new SysUser();
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setPassword(null);
        user.setNickname(githubUser.getName() != null ? githubUser.getName() : githubUser.getLogin());
        user.setEmail(githubUser.getEmail());
        user.setAvatar(githubUser.getAvatarUrl());
        user.setGender(0);
        user.setStatus(1);

        try {
            sysUserMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 用户名冲突：fallback 为 gh_{login}_{githubUserId}
            log.warn("用户名冲突，使用 fallback 用户名: {}", username);
            username = USERNAME_PREFIX + githubUser.getLogin() + "_" + githubUser.getId();
            user.setUsername(username);
            sysUserMapper.insert(user);
        }

        // 创建第三方身份关联记录
        SysUserOauthProvider oauthProvider = new SysUserOauthProvider();
        oauthProvider.setUserId(user.getId());
        oauthProvider.setProvider(PROVIDER_GITHUB);
        oauthProvider.setProviderUserId(String.valueOf(githubUser.getId()));
        oauthProvider.setProviderUsername(githubUser.getLogin());
        oauthProvider.setProviderEmail(githubUser.getEmail());
        oauthProvider.setProviderAvatar(githubUser.getAvatarUrl());
        oauthProvider.setAccessToken(accessToken);

        try {
            sysUserOauthProviderMapper.insert(oauthProvider);
        } catch (DuplicateKeyException e) {
            // 并发竞态：另一个请求已经创建了关联，重新查询
            log.warn("第三方身份关联并发创建，重新查询: provider={}, providerUserId={}",
                    PROVIDER_GITHUB, githubUser.getId());
            oauthProvider = sysUserOauthProviderMapper
                    .selectByProviderAndUserId(PROVIDER_GITHUB, String.valueOf(githubUser.getId()));
            if (oauthProvider != null) {
                user = sysUserMapper.selectById(oauthProvider.getUserId());
            }
        }

        return user;
    }
}
