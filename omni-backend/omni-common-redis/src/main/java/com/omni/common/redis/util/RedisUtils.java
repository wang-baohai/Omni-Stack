package com.omni.common.redis.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类。
 * <p>封装 {@link RedisTemplate} 常用操作，提供类型安全的缓存读写方法。
 * 通过构造器注入 {@code RedisTemplate<String, Object>}，支持单元测试 Mock。</p>
 * <p>支持的操作类型：</p>
 * <ul>
 *   <li>String 操作：{@link #set}/{@link #get}/{@link #increment}/{@link #decrement}</li>
 *   <li>键操作：{@link #delete}/{@link #hasKey}/{@link #expire}/{@link #keys}</li>
 *   <li>分布式锁：{@link #setIfAbsent} (SETNX + TTL)</li>
 *   <li>Hash 操作：{@link #opsForHash} 获取 HashOperations 对象</li>
 * </ul>
 *
 * @author Omni-Stack
 * @see RedisTemplate
 */
@Slf4j
@RequiredArgsConstructor
public class RedisUtils {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 设置缓存值，无过期时间。
     *
     * @param key   缓存键
     * @param value 缓存值
     */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 设置缓存值，指定过期时间。
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 过期时间
     * @param unit    时间单位
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /**
     * 获取缓存值并转换为指定类型。
     *
     * @param key   缓存键
     * @param clazz 目标类型
     * @param <T>   泛型类型
     * @return 缓存值，键不存在时返回 {@code null}
     */
    public <T> T get(String key, Class<T> clazz) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        return clazz.cast(value);
    }

    /**
     * 删除缓存键。
     *
     * @param key 缓存键
     * @return 是否成功删除
     */
    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    /**
     * 批量删除缓存键。
     *
     * @param keys 缓存键集合
     * @return 成功删除的键数量
     */
    public Long delete(Collection<String> keys) {
        return redisTemplate.delete(keys);
    }

    /**
     * 判断缓存键是否存在。
     *
     * @param key 缓存键
     * @return 是否存在
     */
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    /**
     * 设置缓存过期时间。
     *
     * @param key     缓存键
     * @param timeout 过期时间
     * @param unit    时间单位
     * @return 是否设置成功
     */
    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    /**
     * 获取缓存剩余过期时间。
     *
     * @param key  缓存键
     * @param unit 时间单位
     * @return 剩余时间（-1 表示永不过期，-2 表示键不存在）
     */
    public Long getExpire(String key, TimeUnit unit) {
        return redisTemplate.getExpire(key, unit);
    }

    /**
     * 自增操作。
     *
     * @param key   缓存键
     * @param delta 增量（必须大于 0）
     * @return 自增后的值
     */
    public Long increment(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    /**
     * 自减操作。
     *
     * @param key   缓存键
     * @param delta 减量（必须大于 0）
     * @return 自减后的值
     */
    public Long decrement(String key, long delta) {
        return redisTemplate.opsForValue().decrement(key, delta);
    }

    /**
     * 分布式锁：不存在时设置（SETNX + TTL）。
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 过期时间
     * @param unit    时间单位
     * @return 是否设置成功（{@code true} 表示获取锁成功）
     */
    public Boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
    }

    /**
     * 模糊查询缓存键。
     * <p><strong>生产环境慎用</strong>，数据量大时可能阻塞 Redis。</p>
     *
     * @param pattern 匹配模式（支持 {@code *} 和 {@code ?} 通配符）
     * @return 匹配的键集合
     */
    public Set<String> keys(String pattern) {
        return redisTemplate.keys(pattern);
    }

    /**
     * 获取 Hash 操作对象。
     *
     * @return Hash 操作接口
     */
    public HashOperations<String, Object, Object> opsForHash() {
        return redisTemplate.opsForHash();
    }
}
