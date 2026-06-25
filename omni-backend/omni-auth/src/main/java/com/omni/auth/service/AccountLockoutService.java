package com.omni.auth.service;

/**
 * 账号锁定服务接口，基于 Redis 实现登录失败计数和账号锁定。
 *
 * @author Omni-Stack Team
 * @see com.omni.auth.service.impl.AccountLockoutServiceImpl
 */
public interface AccountLockoutService {

    /** 最大连续失败次数，达到此值后锁定账号 */
    int MAX_FAILURES = 10;

    /**
     * 记录一次登录失败，递增失败计数器。
     * 首次失败时设置 TTL 为 1 小时。
     *
     * @param tenantId 租户ID
     * @param username 用户名
     * @return 当前累计失败次数
     */
    int recordFailure(Long tenantId, String username);

    /**
     * 重置失败计数器（登录成功时调用）。
     *
     * @param tenantId 租户ID
     * @param username 用户名
     */
    void resetCount(Long tenantId, String username);

    /**
     * 检查账号是否已被锁定。
     *
     * @param tenantId 租户ID
     * @param username 用户名
     * @return 锁定返回 true
     */
    boolean isLocked(Long tenantId, String username);
}
