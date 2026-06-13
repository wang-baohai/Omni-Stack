package com.omni.auth.service.impl;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.omni.auth.entity.SysUser;
import com.omni.auth.service.JwtTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.interfaces.RSAPrivateKey;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * JWT 令牌服务实现，使用授权服务器的 {@link JWKSource} 中的 RSA 密钥对进行令牌签名。
 *
 * <p>该服务为已认证用户生成 JWT 访问令牌。令牌使用 RS256（RSA + SHA-256）算法签名，
 * 私钥来自 {@code AuthorizationServerConfig} 中配置的内存 JWK 密钥源。
 * 同一 RSA 密钥对通过 {@code /oauth2/jwks} 端点对外暴露公钥，供网关验证令牌签名。</p>
 *
 * <h3>JWT Claims 结构：</h3>
 * <pre>
 * {
 *   "sub": "1",                        // 用户 ID（字符串）
 *   "tenant_id": 1,                    // 租户 ID（数字）
 *   "username": "admin",               // 用户名（字符串）
 *   "roles": ["ADMIN", "USER"],        // 角色编码（数组）
 *   "scope": ["user:read", ...],       // 权限编码（数组）
 *   "iat": 1748512800,                // 签发时间（epoch 秒）
 *   "exp": 1748513700                 // 过期时间（iat + TTL）
 * }
 * </pre>
 *
 * <h3>密钥缓存：</h3>
 * <p>RSA 私钥和 Key ID（{@code kid}）在首次调用 {@link #generateToken} 时从 JWK 密钥源加载，
 * 并缓存到应用生命周期结束。由于 JWK 密钥源在启动时生成一次密钥对（内存中，临时性的），
 * 密钥在应用运行期间不会变化。使用 {@code volatile} 字段的双重检查锁定模式确保线程安全的延迟初始化。</p>
 *
 * @see JwtTokenService
 * @see com.omni.auth.config.AuthorizationServerConfig#jwkSource()
 */
@Slf4j
@Service
public class JwtTokenServiceImpl implements JwtTokenService {

    /** JWK 密钥源，包含 RSA 密钥对 */
    private final JWKSource<SecurityContext> jwkSource;
    /** 访问令牌有效期（秒） */
    private final long accessTokenTtlSeconds;

    /** 缓存的 RSA 私钥，首次使用时从 JWKSource 加载 */
    private volatile RSAPrivateKey cachedPrivateKey;

    /** 缓存的 Key ID（{@code kid}），与 JWK 密钥源的密钥标识符匹配 */
    private volatile String cachedKeyId;

    /**
     * 构造函数，注入 JWK 密钥源和令牌 TTL 配置。
     *
     * @param jwkSource            包含 RSA 密钥对的 JWK 密钥源（来自 AuthorizationServerConfig）
     * @param accessTokenTtlSeconds 访问令牌有效期（秒），默认 900（15 分钟）
     */
    public JwtTokenServiceImpl(
            JWKSource<SecurityContext> jwkSource,
            @Value("${auth.token.access-token-ttl:900}") long accessTokenTtlSeconds) {
        this.jwkSource = jwkSource;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    /**
     * {@inheritDoc}
     *
     * <p>通过以下步骤生成签名的 JWT 访问令牌：</p>
     * <ol>
     *   <li>从 JWKSource 加载 RSA 私钥（首次调用后缓存）</li>
     *   <li>构建 {@link JWTClaimsSet}，包含用户身份和授权声明</li>
     *   <li>创建 {@link JWSHeader}，指定算法 RS256 和 Key ID</li>
     *   <li>使用 {@link RSASSASigner} 和 RSA 私钥签名 JWT</li>
     *   <li>序列化并返回紧凑的 JWT 字符串</li>
     * </ol>
     *
     * @param user        已认证的用户实体（提供 id、tenantId、username）
     * @param roles       用户角色编码列表（如 ["ADMIN", "USER"]）
     * @param permissions 用户权限编码列表（如 ["user:read", "user:write"]）
     * @return 序列化后的 JWT 字符串，使用 RS256 签名
     */
    @Override
    public String generateToken(SysUser user, List<String> roles, List<String> permissions) {
        // 延迟加载 RSA 私钥
        loadKeyIfNeeded();

        Date now = new Date();
        Date exp = new Date(now.getTime() + accessTokenTtlSeconds * 1000);

        // 构建 JWT Claims：身份 + 授权 + 时间
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(user.getId().toString())       // sub: 用户 ID（字符串格式）
                .jwtID(UUID.randomUUID().toString())    // jti: 唯一标识，用于 Token 黑名单
                .claim("tenant_id", user.getTenantId()) // tenant_id: 多租户隔离
                .claim("username", user.getUsername())   // username: 显示和审计用途
                .claim("roles", roles)                   // roles: 角色编码列表
                .claim("scope", permissions)             // scope: 细粒度权限编码
                .issueTime(now)                          // iat: 令牌签发时间戳
                .expirationTime(exp)                     // exp: 令牌过期时间戳
                .build();

        // 构建 JWS 头部：RS256 算法 + Key ID（用于验证端匹配密钥）
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(cachedKeyId)
                .build();

        // 使用 RSA 私钥签名 JWT
        SignedJWT signedJWT = new SignedJWT(header, claims);
        try {
            signedJWT.sign(new RSASSASigner(cachedPrivateKey));
        } catch (Exception e) {
            throw new RuntimeException("JWT 签名失败", e);
        }

        log.info("JWT 已生成: 用户={}, 租户={}", user.getUsername(), user.getTenantId());
        return signedJWT.serialize();
    }

    /**
     * 延迟加载 RSA 私钥（首次调用时从 JWKSource 获取）。
     *
     * <p>使用 {@code volatile} 字段的双重检查锁定模式，确保线程安全的一次性加载。
     * 查询 JWK 密钥源的所有密钥，提取第一个 RSA 密钥。
     * 私钥和 Key ID 均被缓存。</p>
     *
     * <p>{@code kid}（Key ID）包含在 JWS 头部中，使验证方（如网关的 {@code AuthFilter}）
     * 可以在多个密钥存在时（如密钥轮转）从 JWK Set 中匹配正确的公钥。</p>
     */
    private void loadKeyIfNeeded() {
        if (cachedPrivateKey != null) {
            return;
        }
        synchronized (this) {
            if (cachedPrivateKey != null) {
                return;
            }
            try {
                // 使用全匹配选择器从 JWK 密钥源查询所有密钥
                List<JWK> keys = jwkSource.get(
                        new JWKSelector(new JWKMatcher.Builder().build()),
                        null);
                // 提取第一个 RSA 密钥（授权服务器启动时只生成一个密钥对）
                RSAKey rsaKey = (RSAKey) keys.get(0);
                cachedPrivateKey = rsaKey.toRSAPrivateKey();
                cachedKeyId = rsaKey.getKeyID();
                log.info("RSA 私钥已从 JWKSource 加载, kid={}", cachedKeyId);
            } catch (Exception e) {
                throw new RuntimeException("从 JWKSource 加载 RSA 密钥失败", e);
            }
        }
    }
}
