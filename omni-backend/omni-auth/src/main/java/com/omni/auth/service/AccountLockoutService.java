package com.omni.auth.service;

/**
 * 账号锁定服务接口，基于 Redis 实现登录失败计数和账号锁定。
 *
 * <p>D01 起锁定阈值与固定窗口由 {@code LoginPolicyProvider} 从 Auth DB 读取的租户策略驱动，
 * 不再写死为常量；无策略行时回退内置默认（阈值 {@link #MAX_FAILURES}、窗口 3600 秒）。
 * 每个新失败窗口在 Redis 中一次性原子记录 count/threshold/window/policyVersion 并设置 TTL；
 * 同一窗口保留创建时策略，不重设/清空/缩短在途窗口。旧 STRING 计数按其现有 TTL 读到自然到期。</p>
 *
 * @author Omni-Stack Team
 * @see com.omni.auth.service.impl.AccountLockoutServiceImpl
 */
public interface AccountLockoutService {

    /** 内置默认最大连续失败次数（无策略行时使用；与历史常量一致） */
    int MAX_FAILURES = 10;

    /**
     * 记录一次凭证登录失败（仅密码/会话密码认证调用），递增当前窗口计数。
     *
     * @param tenantId 租户ID
     * @param username 用户名
     * @return 当前窗口累计失败次数
     */
    int recordFailure(Long tenantId, String username);

    /**
     * 重置失败计数器（登录成功时调用，仅清本租户本用户）。
     *
     * @param tenantId 租户ID
     * @param username 用户名
     */
    void resetCount(Long tenantId, String username);

    /**
     * 检查账号是否已被锁定（当前窗口计数是否达到窗口创建时锁定的阈值）。
     *
     * @param tenantId 租户ID
     * @param username 用户名
     * @return 锁定返回 true
     */
    boolean isLocked(Long tenantId, String username);

    /**
     * 返回当前锁定窗口的剩余秒数，用于准确提示（不写死“1小时后”）。
     *
     * @param tenantId 租户ID
     * @param username 用户名
     * @return 剩余秒数；无窗口或未锁定返回 0
     */
    long getRemainingLockSeconds(Long tenantId, String username);
}
