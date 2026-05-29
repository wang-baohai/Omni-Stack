package com.omni.auth.service.impl;

import com.omni.auth.dto.CaptchaResult;
import com.omni.auth.service.CaptchaService;
import com.omni.common.core.result.BusinessException;
import com.wf.captcha.SpecCaptcha;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Captcha service implementation using easy-captcha for image generation and Redis for storage.
 *
 * <p><b>Generation flow:</b></p>
 * <ol>
 *   <li>Create a {@link SpecCaptcha} (130x40 px, 4 alphanumeric characters)</li>
 *   <li>Generate a UUID as the captcha key</li>
 *   <li>Store the lowercase captcha text in Redis with key {@code captcha:{uuid}} and a TTL</li>
 *   <li>Return the key and base64-encoded PNG image to the caller</li>
 * </ol>
 *
 * <p><b>Validation flow:</b></p>
 * <ol>
 *   <li>Retrieve the stored captcha text from Redis by key</li>
 *   <li>Delete the key immediately (one-time use — prevents replay attacks)</li>
 *   <li>If the key is missing (expired), throw {@code BusinessException(400, "Captcha expired")}</li>
 *   <li>If the submitted code does not match (case-insensitive), throw
 *       {@code BusinessException(400, "Invalid captcha")}</li>
 * </ol>
 *
 * @see CaptchaService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaServiceImpl implements CaptchaService {

    /** Redis key prefix for captcha storage. Full key format: {@code captcha:{uuid}}. */
    private static final String CAPTCHA_PREFIX = "captcha:";

    private final StringRedisTemplate stringRedisTemplate;

    /** Captcha TTL in seconds, configurable via {@code auth.captcha.ttl-seconds}. Default: 300 (5 min). */
    @Value("${auth.captcha.ttl-seconds:300}")
    private int captchaTtlSeconds;

    /**
     * {@inheritDoc}
     *
     * <p>Generates a static PNG captcha using easy-captcha's {@link SpecCaptcha}.
     * The captcha text is lowercased before storage so that validation can use
     * case-insensitive comparison ({@code equalsIgnoreCase}).</p>
     */
    @Override
    public CaptchaResult generate() {
        // Create a 130x40 pixel captcha with 4 characters
        SpecCaptcha captcha = new SpecCaptcha(130, 40, 4);
        String text = captcha.text().toLowerCase();
        String key = UUID.randomUUID().toString();

        // Store the captcha text in Redis with a TTL for automatic expiration
        stringRedisTemplate.opsForValue()
                .set(CAPTCHA_PREFIX + key, text, captchaTtlSeconds, TimeUnit.SECONDS);

        log.debug("Captcha generated: key={}", key);
        return CaptchaResult.builder()
                .captchaKey(key)
                .captchaImage(captcha.toBase64())
                .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Validates the user-submitted captcha code against the stored value in Redis.
     * The captcha is <b>single-use</b>: the Redis key is deleted immediately after retrieval,
     * regardless of whether the code matches. This prevents replay attacks where the same
     * captcha could be used for multiple login attempts.</p>
     *
     * @throws BusinessException with code 400 if the captcha has expired (Redis key missing)
     *                           or the submitted code does not match the stored value
     */
    @Override
    public void validate(String captchaKey, String captchaCode) {
        String redisKey = CAPTCHA_PREFIX + captchaKey;

        // Retrieve stored text and delete the key atomically (one-time use)
        String stored = stringRedisTemplate.opsForValue().get(redisKey);
        stringRedisTemplate.delete(redisKey);

        if (stored == null) {
            // Key expired or never existed — captcha is no longer valid
            throw new BusinessException(400, "Captcha expired");
        }
        if (!stored.equalsIgnoreCase(captchaCode)) {
            // Case-insensitive comparison: user may type upper or lowercase
            throw new BusinessException(400, "Invalid captcha");
        }
    }
}
