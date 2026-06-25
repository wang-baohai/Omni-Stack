package com.omni.auth.service.impl;

import com.omni.auth.event.AuditEvent;
import com.omni.auth.service.AccountLockoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 账号锁定服务实现，基于 Redis INCR + TTL 实现失败计数和自动锁定。
 *
 * <p>Redis key 格式：{@code login:fail:{tenantId}:{username}}，TTL 1 小时。
 * 达到 {@link #MAX_FAILURES} 阈值时发布 {@link AuditEvent#ACCOUNT_LOCKED} 审计事件。</p>
 *
 * @author Omni-Stack Team
 * @see AccountLockoutService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountLockoutServiceImpl implements AccountLockoutService {

    private static final String KEY_PREFIX = "login:fail:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    /** {@inheritDoc} */
    @Override
    public int recordFailure(Long tenantId, String username) {
        String key = buildKey(tenantId, username);
        Long count = stringRedisTemplate.opsForValue().increment(key);

        // 首次失败时设置 TTL
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, 1, TimeUnit.HOURS);
        }

        // 达到阈值时发布账号锁定事件
        if (count != null && count == (long) MAX_FAILURES) {
            eventPublisher.publishEvent(AuditEvent.of(AuditEvent.ACCOUNT_LOCKED)
                    .tenantId(tenantId)
                    .username(username)
                    .description("用户连续登录失败 " + MAX_FAILURES + " 次，账号已被锁定")
                    .createBy("system")
                    .extra("fail_count", MAX_FAILURES)
                    .extra("lock_duration_minutes", 60)
                    .build());
            log.warn("[SMS占位] 账户已被锁定: 用户 {} 在租户 {} 中连续失败 {} 次",
                    username, tenantId, MAX_FAILURES);
        }

        return count != null ? count.intValue() : 0;
    }

    /** {@inheritDoc} */
    @Override
    public void resetCount(Long tenantId, String username) {
        stringRedisTemplate.delete(buildKey(tenantId, username));
    }

    /** {@inheritDoc} */
    @Override
    public boolean isLocked(Long tenantId, String username) {
        String key = buildKey(tenantId, username);
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null) {
            return false;
        }
        try {
            return Integer.parseInt(value) >= MAX_FAILURES;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String buildKey(Long tenantId, String username) {
        return KEY_PREFIX + tenantId + ":" + username;
    }
}
