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

/**
 * JWT token service that signs tokens using the RSA key pair from the authorization server's
 * {@link JWKSource}.
 *
 * <p>This service generates JWT access tokens for authenticated users. The tokens are signed
 * with RS256 (RSA + SHA-256) using the private key from the in-memory JWK source configured
 * in {@code AuthorizationServerConfig}. The same RSA key pair is exposed via the
 * {@code /oauth2/jwks} endpoint so that the gateway can verify token signatures.</p>
 *
 * <h3>JWT Claims Structure:</h3>
 * <pre>
 * {
 *   "sub": "1",                        // user ID (string)
 *   "tenant_id": 1,                    // tenant ID (number)
 *   "username": "admin",               // username (string)
 *   "roles": ["ADMIN", "USER"],        // role codes (array)
 *   "scope": ["user:read", ...],       // permission codes (array)
 *   "iat": 1748512800,                // issued-at (epoch seconds)
 *   "exp": 1748513700                 // expiration (iat + TTL)
 * }
 * </pre>
 *
 * <h3>Key Caching:</h3>
 * <p>The RSA private key and key ID ({@code kid}) are loaded from the JWK source on the
 * first call to {@link #generateToken} and cached for the lifetime of the application.
 * Since the JWK source generates the key pair once at startup (in-memory, ephemeral),
 * the key does not change during the application's lifetime. A double-checked locking
 * pattern with {@code volatile} fields ensures thread-safe lazy initialization.</p>
 *
 * @see JwtTokenService
 * @see com.omni.auth.config.AuthorizationServerConfig#jwkSource()
 */
@Slf4j
@Service
public class JwtTokenServiceImpl implements JwtTokenService {

    private final JWKSource<SecurityContext> jwkSource;
    private final long accessTokenTtlSeconds;

    /** Cached RSA private key for signing. Loaded once from JWKSource on first use. */
    private volatile RSAPrivateKey cachedPrivateKey;

    /** Cached key ID ({@code kid}) matching the JWK source's key identifier. */
    private volatile String cachedKeyId;

    /**
     * Construct with the JWK source and token TTL configuration.
     *
     * @param jwkSource            the JWK source containing the RSA key pair (from AuthorizationServerConfig)
     * @param accessTokenTtlSeconds access token TTL in seconds (default 900 = 15 minutes)
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
     * <p>Generates a signed JWT access token with the following steps:</p>
     * <ol>
     *   <li>Load the RSA private key from JWKSource (cached after first call)</li>
     *   <li>Build a {@link JWTClaimsSet} with user identity and authorization claims</li>
     *   <li>Create a {@link JWSHeader} with algorithm RS256 and the key ID</li>
     *   <li>Sign the JWT using {@link RSASSASigner} with the RSA private key</li>
     *   <li>Serialize and return the compact JWT string</li>
     * </ol>
     *
     * @param user        the authenticated user entity (provides id, tenantId, username)
     * @param roles       the user's role codes (e.g., ["ADMIN", "USER"])
     * @param permissions the user's permission codes (e.g., ["user:read", "user:write"])
     * @return the serialized JWT string, signed with RS256
     */
    @Override
    public String generateToken(SysUser user, List<String> roles, List<String> permissions) {
        loadKeyIfNeeded();

        Date now = new Date();
        Date exp = new Date(now.getTime() + accessTokenTtlSeconds * 1000);

        // Build JWT claims: identity + authorization + temporal
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(user.getId().toString())       // sub: user ID as string
                .claim("tenant_id", user.getTenantId()) // tenant_id: for multi-tenant isolation
                .claim("username", user.getUsername())   // username: for display and audit
                .claim("roles", roles)                   // roles: role codes for authorization
                .claim("scope", permissions)             // scope: fine-grained permission codes
                .issueTime(now)                          // iat: token issued-at timestamp
                .expirationTime(exp)                     // exp: token expiration timestamp
                .build();

        // Build JWS header with RS256 algorithm and key ID for verification matching
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(cachedKeyId)
                .build();

        // Sign the JWT with the RSA private key
        SignedJWT signedJWT = new SignedJWT(header, claims);
        try {
            signedJWT.sign(new RSASSASigner(cachedPrivateKey));
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign JWT", e);
        }

        log.info("JWT generated for user={}, tenant={}", user.getUsername(), user.getTenantId());
        return signedJWT.serialize();
    }

    /**
     * Load the RSA private key from JWKSource on first call (lazy initialization).
     *
     * <p>Uses double-checked locking with {@code volatile} fields to ensure thread-safe
     * one-time loading. The JWK source's key set is queried with a match-all selector,
     * and the first RSA key is extracted. Both the private key and the key ID are cached.</p>
     *
     * <p>The {@code kid} (key ID) is included in the JWS header so that verifiers (e.g.,
     * the gateway's {@code AuthFilter}) can match the correct public key from the JWK Set
     * when multiple keys exist (e.g., during key rotation).</p>
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
                // Query all keys from the JWK source using a match-all selector
                List<JWK> keys = jwkSource.get(
                        new JWKSelector(new JWKMatcher.Builder().build()),
                        null);
                // Extract the first RSA key (the authorization server generates exactly one)
                RSAKey rsaKey = (RSAKey) keys.get(0);
                cachedPrivateKey = rsaKey.toRSAPrivateKey();
                cachedKeyId = rsaKey.getKeyID();
                log.info("RSA private key loaded from JWKSource, kid={}", cachedKeyId);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load RSA key from JWKSource", e);
            }
        }
    }
}
