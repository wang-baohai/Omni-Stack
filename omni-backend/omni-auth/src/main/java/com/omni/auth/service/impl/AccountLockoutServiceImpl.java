package com.omni.auth.service.impl;

import com.omni.auth.event.AuditEvent;
import com.omni.auth.security.LoginPolicyProvider;
import com.omni.auth.service.AccountLockoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 账号锁定服务实现（D01），基于 Redis 原子脚本实现策略驱动的失败计数与固定窗口锁定。
 *
 * <p>Redis key 格式保持边界：{@code login:fail:{tenantId}:{username}}。</p>
 * <ul>
 *   <li>新窗口以 HASH 存储 count/threshold/window/policyVersion，并在创建时一次性 SET TTL；</li>
 *   <li>旧 STRING 计数（升级前在途）按旧阈值 10 与现有 TTL 读取/累加直至自然到期，禁止 DEL 迁移；</li>
 *   <li>同一 HASH 窗口保留创建时策略，不重设/清空/缩短在途窗口；</li>
 *   <li>达阈值时恰由落到阈值的那一次发布 ACCOUNT_LOCKED（原子计数保证仅一次）；</li>
 *   <li>异常类型或无有效窗口一律失败关闭，绝不放行。</li>
 * </ul>
 *
 * @author Omni-Stack Team
 * @see AccountLockoutService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountLockoutServiceImpl implements AccountLockoutService {

    private static final String KEY_PREFIX = "login:fail:";

    /**
     * 记录一次失败并返回 {count, threshold, ttlMillis, typeFlag}。
     * <p>RF04：先验证既存窗口的类型/数值/必要字段/TTL，不修改不合法窗口以“修好”；
     * 合法 STRING 保留原 TTL 与旧阈值，合法 HASH 保留创建时策略；缺字段/非数值/负值/无有效 TTL 一律失败关闭。</p>
     */
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> RECORD_SCRIPT = new DefaultRedisScript<>(
            "local t = redis.call('TYPE', KEYS[1]).ok\n"
            + "if t == 'string' then\n"
            + "  local ttl = redis.call('PTTL', KEYS[1])\n"
            + "  if ttl < 0 then return {-3, 0, -1, -3} end\n"
            + "  local c = tonumber(redis.call('GET', KEYS[1]))\n"
            + "  if c == nil or c < 0 then return {-3, 0, ttl, -3} end\n"
            + "  c = redis.call('INCR', KEYS[1])\n"
            + "  return {c, tonumber(ARGV[4]), ttl, 0}\n"
            + "elseif t == 'hash' then\n"
            + "  local ttl = redis.call('PTTL', KEYS[1])\n"
            + "  local c = tonumber(redis.call('HGET', KEYS[1], 'count'))\n"
            + "  local thr = tonumber(redis.call('HGET', KEYS[1], 'threshold'))\n"
            + "  if c == nil or c < 0 or thr == nil or thr <= 0 or ttl < 0 then return {-3, 0, -1, -3} end\n"
            + "  c = redis.call('HINCRBY', KEYS[1], 'count', 1)\n"
            + "  return {c, thr, ttl, 1}\n"
            + "elseif t == 'none' then\n"
            + "  redis.call('HSET', KEYS[1], 'count', 1, 'threshold', ARGV[1], 'window', ARGV[2], 'policyVersion', ARGV[3])\n"
            + "  redis.call('EXPIRE', KEYS[1], ARGV[2])\n"
            + "  return {1, tonumber(ARGV[1]), tonumber(ARGV[2]) * 1000, 1}\n"
            + "else\n"
            + "  return {-2, 0, -2, -2}\n"
            + "end",
            List.class);

    /**
     * 只读当前状态，返回 {count, threshold, ttlMillis, typeFlag}，不修改。
     * <p>typeFlag: 0=STRING合法, 1=HASH合法, -1=无窗口, -2=未知类型(失败关闭), -3=异常窗口(缺字段/非数值/无有效TTL, 失败关闭)。</p>
     */
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> READ_SCRIPT = new DefaultRedisScript<>(
            "local t = redis.call('TYPE', KEYS[1]).ok\n"
            + "if t == 'string' then\n"
            + "  local ttl = redis.call('PTTL', KEYS[1])\n"
            + "  local c = tonumber(redis.call('GET', KEYS[1]))\n"
            + "  if c == nil or c < 0 or ttl < 0 then return {0, 0, -1, -3} end\n"
            + "  return {c, tonumber(ARGV[1]), ttl, 0}\n"
            + "elseif t == 'hash' then\n"
            + "  local ttl = redis.call('PTTL', KEYS[1])\n"
            + "  local c = tonumber(redis.call('HGET', KEYS[1], 'count'))\n"
            + "  local thr = tonumber(redis.call('HGET', KEYS[1], 'threshold'))\n"
            + "  if c == nil or c < 0 or thr == nil or thr <= 0 or ttl < 0 then return {0, 0, -1, -3} end\n"
            + "  return {c, thr, ttl, 1}\n"
            + "elseif t == 'none' then\n"
            + "  return {0, tonumber(ARGV[1]), -2, -1}\n"
            + "else\n"
            + "  return {0, 0, -1, -2}\n"
            + "end",
            List.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final LoginPolicyProvider loginPolicyProvider;

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("unchecked")
    public int recordFailure(Long tenantId, String username) {
        LoginPolicyProvider.Effective policy = loginPolicyProvider.effective(tenantId);
        String key = buildKey(tenantId, username);
        List<Long> res = stringRedisTemplate.execute(RECORD_SCRIPT, Collections.singletonList(key),
                String.valueOf(policy.threshold()),
                String.valueOf(policy.windowSeconds()),
                String.valueOf(policy.version()),
                String.valueOf(MAX_FAILURES),
                String.valueOf(LoginPolicyProvider.DEFAULT_WINDOW_SECONDS));
        if (res == null || res.size() < 4) {
            // 脚本异常返回：失败关闭，视为已达阈值，不放行
            return MAX_FAILURES;
        }
        long count = res.get(0);
        long threshold = res.get(1);
        if (count < 0) {
            // 异常类型哨兵：失败关闭
            return (int) Math.max(MAX_FAILURES, threshold);
        }
        if (count == threshold) {
            long ttlMillis = res.get(2);
            int remainMinutes = ttlMillis > 0 ? (int) Math.max(1, ttlMillis / 60000) : 0;
            eventPublisher.publishEvent(AuditEvent.of(AuditEvent.ACCOUNT_LOCKED)
                    .tenantId(tenantId)
                    .username(username)
                    .description("用户在失败窗口内连续凭证错误达到 " + threshold + " 次，账号已被临时锁定")
                    .createBy("system")
                    .extra("fail_count", threshold)
                    .extra("lock_window_seconds", threshold > 0 ? (ttlMillis / 1000) : 0)
                    .build());
            log.warn("账户已被锁定: 用户 {} 在租户 {} 中达到阈值 {}（剩余约 {} 分钟）",
                    username, tenantId, threshold, remainMinutes);
        }
        return (int) count;
    }

    /** {@inheritDoc} */
    @Override
    public void resetCount(Long tenantId, String username) {
        stringRedisTemplate.delete(buildKey(tenantId, username));
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("unchecked")
    public boolean isLocked(Long tenantId, String username) {
        String key = buildKey(tenantId, username);
        List<Long> res = stringRedisTemplate.execute(READ_SCRIPT, Collections.singletonList(key),
                String.valueOf(MAX_FAILURES));
        if (res == null || res.size() < 4) {
            return true; // 脚本无返回：失败关闭
        }
        long typeFlag = res.get(3);
        if (typeFlag == -1) {
            return false; // 无窗口：未锁定
        }
        if (typeFlag < -1) {
            return true; // 未知类型或异常窗口(缺字段/非数值/无有效 TTL)：失败关闭
        }
        long count = res.get(0);
        long threshold = res.get(1);
        long ttlMillis = res.get(2);
        if (ttlMillis < 0 || count < 0) {
            return true; // 合法类型但读取时 TTL 失效/计数异常：失败关闭
        }
        return count >= threshold;
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("unchecked")
    public long getRemainingLockSeconds(Long tenantId, String username) {
        String key = buildKey(tenantId, username);
        List<Long> res = stringRedisTemplate.execute(READ_SCRIPT, Collections.singletonList(key),
                String.valueOf(MAX_FAILURES));
        if (res == null || res.size() < 4) {
            return 0;
        }
        long count = res.get(0);
        long threshold = res.get(1);
        long ttlMillis = res.get(2);
        if (count >= threshold && ttlMillis > 0) {
            return (long) Math.ceil(ttlMillis / 1000.0);
        }
        return 0;
    }

    private String buildKey(Long tenantId, String username) {
        return KEY_PREFIX + tenantId + ":" + username;
    }
}
