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
 * 验证码服务实现，使用 easy-captcha 生成图片并通过 Redis 存储。
 *
 * <p><b>生成流程：</b></p>
 * <ol>
 *   <li>创建 {@link SpecCaptcha}（130x40 像素，4 位字母数字）</li>
 *   <li>生成 UUID 作为验证码 Key</li>
 *   <li>将小写的验证码文本存储在 Redis 中，键格式为 {@code captcha:{uuid}}，带 TTL</li>
 *   <li>返回 Key 和 Base64 编码的 PNG 图片</li>
 * </ol>
 *
 * <p><b>校验流程：</b></p>
 * <ol>
 *   <li>根据 Key 从 Redis 获取存储的验证码文本</li>
 *   <li>立即删除 Redis 键（一次性使用，防止重放攻击）</li>
 *   <li>如果键不存在（已过期），抛出 {@code BusinessException(400, "验证码已过期")}</li>
 *   <li>如果提交的验证码不匹配（不区分大小写），抛出 {@code BusinessException(400, "验证码错误")}</li>
 * </ol>
 *
 * @see CaptchaService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaServiceImpl implements CaptchaService {

    /** Redis 键前缀，完整键格式：{@code captcha:{uuid}} */
    private static final String CAPTCHA_PREFIX = "captcha:";

    /** Redis 操作模板 */
    private final StringRedisTemplate stringRedisTemplate;

    /** 验证码有效期（秒），通过配置项 {@code auth.captcha.ttl-seconds} 设置，默认 300 秒（5 分钟） */
    @Value("${auth.captcha.ttl-seconds:300}")
    private int captchaTtlSeconds;

    /**
     * {@inheritDoc}
     *
     * <p>使用 easy-captcha 的 {@link SpecCaptcha} 生成静态 PNG 验证码。
     * 验证码文本在存储前转为小写，以便校验时使用不区分大小写的比较方式。</p>
     */
    @Override
    public CaptchaResult generate() {
        // 创建 130x40 像素、4 位字符的验证码
        SpecCaptcha captcha = new SpecCaptcha(130, 40, 4);
        String text = captcha.text().toLowerCase();
        String key = UUID.randomUUID().toString();

        // 将验证码文本存储到 Redis，设置 TTL 自动过期
        stringRedisTemplate.opsForValue()
                .set(CAPTCHA_PREFIX + key, text, captchaTtlSeconds, TimeUnit.SECONDS);

        log.debug("验证码已生成: key={}", key);
        return CaptchaResult.builder()
                .captchaKey(key)
                .captchaImage(captcha.toBase64())
                .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>校验用户提交的验证码是否与 Redis 中存储的值匹配。
     * 验证码为<b>一次性使用</b>：Redis 键在获取后立即删除，
     * 无论验证码是否匹配，防止重放攻击。</p>
     *
     * @throws BusinessException 验证码已过期（Redis 键不存在）或验证码不匹配时抛出，错误码 400
     */
    @Override
    public void validate(String captchaKey, String captchaCode) {
        String redisKey = CAPTCHA_PREFIX + captchaKey;

        // 从 Redis 获取存储的验证码文本，并立即删除键（一次性使用）
        String stored = stringRedisTemplate.opsForValue().get(redisKey);
        stringRedisTemplate.delete(redisKey);

        if (stored == null) {
            // 键不存在，说明验证码已过期或从未生成
            throw new BusinessException(400, "验证码已过期");
        }
        if (!stored.equalsIgnoreCase(captchaCode)) {
            // 不区分大小写比较
            throw new BusinessException(400, "验证码错误");
        }
    }
}
