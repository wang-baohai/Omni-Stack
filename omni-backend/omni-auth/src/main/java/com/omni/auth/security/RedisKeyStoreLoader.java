package com.omni.auth.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 基于 Redis + AES 加密的 JWK 密钥加载器。
 * <p>
 * 将 RSA 密钥对（JWK JSON 格式）用 AES-256-GCM 加密后存入 Redis，
 * 实现多实例共享同一签名密钥，重启后 JWT 不失效。
 * </p>
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>启动时从 Redis 读取 {@code sys:jwk:keystore}</li>
 *   <li>如果有值：Base64 解码 -> AES-GCM 解密 -> 解析 JWK JSON -> 构建 JWKSource</li>
 *   <li>如果不存在（首次部署）：生成 RSA 2048 密钥对 -> 序列化为 JWK JSON -> AES-GCM 加密 -> Base64 编码 -> 存入 Redis</li>
 * </ol>
 *
 * <h3>Fallback 策略</h3>
 * <p>
 * 当 {@code jwk.encrypt-key} 未配置时（如无 Redis 的本地开发环境），
 * 退化为每次启动生成临时密钥（原有行为）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisKeyStoreLoader {

    /** Redis 中存储加密后 JWK 的 Key */
    private static final String REDIS_KEY = "sys:jwk:keystore";

    /** AES-GCM IV 长度（字节） */
    private static final int GCM_IV_LENGTH = 12;

    /** AES-GCM 认证标签长度（位） */
    private static final int GCM_TAG_LENGTH = 128;

    /** RSA 密钥长度 */
    private static final int RSA_KEY_SIZE = 2048;

    private final StringRedisTemplate redisTemplate;

    @Value("${jwk.encrypt-key:}")
    private String encryptKey;

    /**
     * 加载 JWK 密钥源。
     * <p>
     * 如果配置了 {@code jwk.encrypt-key} 且 Redis 中存在加密数据，则解密返回；
     * 如果 Redis 中不存在，则自动生成新密钥对并存入 Redis；
     * 如果未配置 encrypt-key，返回 null（调用方应 fallback 到临时密钥）。
     * </p>
     *
     * @return JWK 密钥源，或 null（未配置加密密钥时）
     * @throws Exception 加密/解密/密钥生成过程中的异常
     */
    public JWKSource<SecurityContext> loadJwkSource() throws Exception {
        if (encryptKey == null || encryptKey.isBlank()) {
            log.warn("未配置 jwk.encrypt-key，使用临时密钥（重启后 JWT 将失效）");
            return null;
        }

        SecretKey aesKey = deriveAesKey(encryptKey);

        // 尝试从 Redis 读取
        String encrypted = redisTemplate.opsForValue().get(REDIS_KEY);
        if (encrypted != null && !encrypted.isBlank()) {
            log.info("从 Redis 加载 JWK 密钥库 (key={})", REDIS_KEY);
            byte[] encryptedBytes = Base64.getDecoder().decode(encrypted);
            byte[] jwkJsonBytes = decrypt(encryptedBytes, aesKey);
            String jwkJson = new String(jwkJsonBytes, StandardCharsets.UTF_8);
            JWKSet jwkSet = JWKSet.parse(jwkJson);
            return new ImmutableJWKSet<>(jwkSet);
        }

        // Redis 中不存在，生成新密钥对并存入 Redis
        log.info("Redis 中未找到 JWK 密钥库，生成新的 RSA 密钥对并存入 Redis");
        JWKSet jwkSet = generateJwkSet();
        // nimbus-jose-jwt 10.x 破坏性变更：JWKSet.toString(true) 不再包含私钥参数。
        // 必须通过 RSAKey.toJSONObject()（单个密钥）手动构建 JWK Set JSON，
        // 否则存入 Redis 的密钥将丢失私钥，导致 JWT 签发失败。
        RSAKey rsaKey = (RSAKey) jwkSet.getKeys().get(0);
        String jwkJson = new JSONObject(Map.of("keys", List.of(rsaKey.toJSONObject()))).toString();
        byte[] encryptedBytes = encrypt(jwkJson.getBytes(StandardCharsets.UTF_8), aesKey);
        String encoded = Base64.getEncoder().encodeToString(encryptedBytes);
        redisTemplate.opsForValue().set(REDIS_KEY, encoded);
        log.info("JWK 密钥库已生成并加密存入 Redis (key={})", REDIS_KEY);

        return new ImmutableJWKSet<>(jwkSet);
    }

    /**
     * 生成 RSA 密钥对并封装为 JWKSet。
     *
     * @return JWKSet
     * @throws Exception 密钥生成异常
     */
    private JWKSet generateJwkSet() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(RSA_KEY_SIZE, new SecureRandom());
        KeyPair keyPair = keyGen.generateKeyPair();

        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();

        return new JWKSet(rsaKey);
    }

    /**
     * 从配置字符串派生 AES-256 密钥。
     * <p>
     * 使用 SHA-256 哈希将任意长度的 passphrase 转换为 32 字节（256 位）AES 密钥。
     * </p>
     *
     * @param passphrase 加密密码
     * @return AES-256 SecretKey
     * @throws Exception 哈希计算异常
     */
    private SecretKey deriveAesKey(String passphrase) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = digest.digest(passphrase.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * AES-256-GCM 加密。
     * <p>输出格式：IV (12 字节) + 密文 + 认证标签</p>
     *
     * @param plaintext 明文
     * @param key       AES 密钥
     * @return 加密后的字节数组
     * @throws Exception 加密异常
     */
    private byte[] encrypt(byte[] plaintext, SecretKey key) throws Exception {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

        byte[] ciphertext = cipher.doFinal(plaintext);

        // 拼接 IV + 密文
        byte[] result = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
        return result;
    }

    /**
     * AES-256-GCM 解密。
     * <p>输入格式：IV (12 字节) + 密文 + 认证标签</p>
     *
     * @param encrypted IV + 密文 + 标签
     * @param key       AES 密钥
     * @return 解密后的明文
     * @throws Exception 解密异常（包括认证失败）
     */
    private byte[] decrypt(byte[] encrypted, SecretKey key) throws Exception {
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(encrypted, 0, iv, 0, GCM_IV_LENGTH);

        byte[] ciphertext = new byte[encrypted.length - GCM_IV_LENGTH];
        System.arraycopy(encrypted, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

        return cipher.doFinal(ciphertext);
    }
}
