package com.omni.auth.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.auth.dto.CreateOAuth2ClientRequest;
import com.omni.auth.dto.OAuth2ClientVO;
import com.omni.auth.dto.UpdateOAuth2ClientRequest;
import com.omni.auth.entity.OAuth2RegisteredClient;
import com.omni.auth.mapper.OAuth2ClientMapper;
import com.omni.auth.service.OAuth2ClientService;
import com.omni.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * OAuth2 客户端管理服务实现。
 * <p>使用 {@link RegisteredClientRepository} 进行 CRUD 操作，
 * 使用 MyBatis-Plus 进行分页查询。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2ClientServiceImpl implements OAuth2ClientService {

    /** OAuth2 客户端 Mapper */
    private final OAuth2ClientMapper oAuth2ClientMapper;

    /** SAS 客户端仓库 */
    private final RegisteredClientRepository registeredClientRepository;

    /** 密码编码器，用于编码客户端密钥 */
    private final PasswordEncoder passwordEncoder;

    /**
     * {@inheritDoc}
     *
     * <p>使用 MyBatis-Plus 分页插件查询 {@code oauth2_registered_client} 表，
     * 将实体流式转换为视图对象后封装为分页结果返回。</p>
     *
     * @param page 页码（从 1 开始）
     * @param size 每页数量
     * @return 包含客户端视图对象列表的分页结果
     */
    @Override
    public PageResult<OAuth2ClientVO> listClients(int page, int size) {
        IPage<OAuth2RegisteredClient> pageParam = new Page<>(page, size);
        IPage<OAuth2RegisteredClient> result = oAuth2ClientMapper.selectPage(pageParam, null);

        var voList = result.getRecords().stream()
                .map(this::entityToVO)
                .collect(Collectors.toList());

        PageResult<OAuth2ClientVO> pageResult = new PageResult<>();
        pageResult.setRecords(voList);
        pageResult.setTotal(result.getTotal());
        pageResult.setSize(result.getSize());
        pageResult.setCurrent(result.getCurrent());
        pageResult.setPages(result.getPages());
        return pageResult;
    }

    /**
     * {@inheritDoc}
     *
     * <p>根据内部 ID 查询数据库实体，不存在时返回 {@code null}。</p>
     *
     * @param id 客户端内部 ID（UUID）
     * @return 客户端视图对象，不存在时为 {@code null}
     */
    @Override
    public OAuth2ClientVO getClient(String id) {
        OAuth2RegisteredClient entity = oAuth2ClientMapper.selectById(id);
        if (entity == null) {
            return null;
        }
        return entityToVO(entity);
    }

    /**
     * {@inheritDoc}
     *
     * <p>创建流程：生成 clientId（未指定时使用 UUID）→ BCrypt 编码客户端密钥 →
     * 构建 {@link RegisteredClient} → 通过 SAS 仓库持久化 → 重新读取并转换为视图对象返回。</p>
     *
     * @param request 创建请求参数，包含客户端名称、认证方式、授权类型等
     * @return 创建后的客户端视图对象
     */
    @Override
    @Transactional
    public OAuth2ClientVO createClient(CreateOAuth2ClientRequest request) {
        String clientId = request.getClientId() != null && !request.getClientId().isBlank()
                ? request.getClientId()
                : UUID.randomUUID().toString();

        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientName(request.getClientName());

        // 客户端密钥：如果提供了则 BCrypt 编码，否则跳过
        if (request.getClientSecret() != null && !request.getClientSecret().isBlank()) {
            builder.clientSecret(passwordEncoder.encode(request.getClientSecret()));
        }

        // 认证方式
        for (String method : request.getAuthenticationMethods()) {
            builder.clientAuthenticationMethod(new ClientAuthenticationMethod(method));
        }

        // 授权类型
        for (String grantType : request.getGrantTypes()) {
            builder.authorizationGrantType(new AuthorizationGrantType(grantType));
        }

        // 回调地址
        if (request.getRedirectUris() != null) {
            request.getRedirectUris().forEach(builder::redirectUri);
        }

        // 登出后回调地址
        if (request.getPostLogoutRedirectUris() != null) {
            request.getPostLogoutRedirectUris().forEach(builder::postLogoutRedirectUri);
        }

        // 作用域
        for (String scope : request.getScopes()) {
            builder.scope(scope);
        }

        // 客户端设置
        builder.clientSettings(ClientSettings.builder()
                .requireAuthorizationConsent(request.isRequireConsent())
                .requireProofKey(request.isRequireProofKey())
                .build());

        RegisteredClient registeredClient = builder.build();
        registeredClientRepository.save(registeredClient);

        log.info("OAuth2 客户端 '{}' (clientId={}) 创建成功",
                request.getClientName(), clientId);

        return entityToVO(oAuth2ClientMapper.selectById(registeredClient.getId()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>更新策略：保留原有 clientId 和客户端密钥（不可逆），仅更新可变属性。
     * 客户端不存在时抛出 {@link IllegalArgumentException}。</p>
     *
     * @param id      客户端内部 ID（UUID）
     * @param request 更新请求参数
     * @return 更新后的客户端视图对象
     * @throws IllegalArgumentException 客户端不存在时
     */
    @Override
    @Transactional
    public OAuth2ClientVO updateClient(String id, UpdateOAuth2ClientRequest request) {
        RegisteredClient existing = registeredClientRepository.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("客户端不存在: " + id);
        }

        RegisteredClient.Builder builder = RegisteredClient.withId(id)
                .clientId(existing.getClientId())
                .clientName(request.getClientName());

        // 保留原有密钥
        if (existing.getClientSecret() != null) {
            builder.clientSecret(existing.getClientSecret());
        }

        // 认证方式
        for (String method : request.getAuthenticationMethods()) {
            builder.clientAuthenticationMethod(new ClientAuthenticationMethod(method));
        }

        // 授权类型
        for (String grantType : request.getGrantTypes()) {
            builder.authorizationGrantType(new AuthorizationGrantType(grantType));
        }

        // 回调地址
        if (request.getRedirectUris() != null) {
            request.getRedirectUris().forEach(builder::redirectUri);
        }

        // 登出后回调地址
        if (request.getPostLogoutRedirectUris() != null) {
            request.getPostLogoutRedirectUris().forEach(builder::postLogoutRedirectUri);
        }

        // 作用域
        for (String scope : request.getScopes()) {
            builder.scope(scope);
        }

        // 客户端设置
        builder.clientSettings(ClientSettings.builder()
                .requireAuthorizationConsent(request.isRequireConsent())
                .requireProofKey(request.isRequireProofKey())
                .build());

        RegisteredClient updated = builder.build();
        registeredClientRepository.save(updated);

        log.info("OAuth2 客户端 '{}' (id={}) 更新成功", request.getClientName(), id);

        return entityToVO(oAuth2ClientMapper.selectById(id));
    }

    /**
     * {@inheritDoc}
     *
     * <p>先通过 SAS 仓库校验客户端是否存在，不存在时抛出异常。
     * 存在时通过 Mapper 直接删除数据库记录。</p>
     *
     * @param id 客户端内部 ID（UUID）
     * @throws IllegalArgumentException 客户端不存在时
     */
    @Override
    @Transactional
    public void deleteClient(String id) {
        RegisteredClient existing = registeredClientRepository.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("客户端不存在: " + id);
        }
        oAuth2ClientMapper.deleteById(id);
        log.info("OAuth2 客户端 '{}' (id={}) 已删除", existing.getClientId(), id);
    }

    /**
     * 将数据库实体转换为视图对象。
     *
     * @param entity 数据库实体
     * @return 视图对象
     */
    private OAuth2ClientVO entityToVO(OAuth2RegisteredClient entity) {
        Set<String> authMethods = splitCommaSeparated(entity.getClientAuthenticationMethods());
        Set<String> grantTypes = splitCommaSeparated(entity.getAuthorizationGrantTypes());
        Set<String> redirectUris = splitCommaSeparated(entity.getRedirectUris());
        Set<String> postLogoutUris = splitCommaSeparated(entity.getPostLogoutRedirectUris());
        Set<String> scopes = splitCommaSeparated(entity.getScopes());

        // 解析 clientSettings JSON 提取 requireConsent 和 requireProofKey
        boolean requireConsent = parseBooleanFromJson(entity.getClientSettings(),
                "settings.client.require-authorization-consent");
        boolean requireProofKey = parseBooleanFromJson(entity.getClientSettings(),
                "settings.client.require-proof-key");

        // 脱敏密钥展示
        String maskedSecret = entity.getClientSecret() != null ? "******" : null;

        return OAuth2ClientVO.builder()
                .id(entity.getId())
                .clientId(entity.getClientId())
                .clientName(entity.getClientName())
                .clientSecret(maskedSecret)
                .authenticationMethods(authMethods)
                .grantTypes(grantTypes)
                .redirectUris(redirectUris)
                .postLogoutRedirectUris(postLogoutUris)
                .scopes(scopes)
                .requireConsent(requireConsent)
                .requireProofKey(requireProofKey)
                .createdAt(entity.getClientIdIssuedAt())
                .build();
    }

    /**
     * 将逗号分隔的字符串拆分为有序集合。
     *
     * @param value 逗号分隔的字符串
     * @return 有序集合，null 或空时返回空集合
     */
    private Set<String> splitCommaSeparated(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 从 SAS client_settings JSON 字符串中解析布尔值。
     *
     * @param json JSON 字符串
     * @param key  键名
     * @return 布尔值，解析失败返回 false
     */
    private boolean parseBooleanFromJson(String json, String key) {
        if (json == null || json.isBlank()) {
            return false;
        }
        // 简单解析：查找 "key":true 或 "key":false
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) {
            return false;
        }
        String remaining = json.substring(idx + search.length()).trim();
        return remaining.startsWith("true");
    }
}
