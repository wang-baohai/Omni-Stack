package com.omni.auth.service;

import java.util.List;

/**
 * 在线用户服务接口，提供基于 Redis 的在线用户追踪和强制下线操作。
 */
public interface OnlineUserService {

    /**
     * 记录用户上线（在 Redis 中创建在线标记）。
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param jti      JWT Token ID
     * @param ttlSeconds Token 有效期（秒）
     */
    void recordOnline(Long userId, String username, String jti, long ttlSeconds);

    /**
     * 获取当前在线用户列表。
     *
     * @return 在线用户信息列表
     */
    List<OnlineUserVO> listOnlineUsers();

    /**
     * 强制踢出用户（删除 Redis 在线标记 + 加入 Token 黑名单）。
     *
     * @param userId 用户 ID
     */
    void kickUser(Long userId);

    /**
     * 检查用户是否在线。
     *
     * @param userId 用户 ID
     * @return 是否在线
     */
    boolean isOnline(Long userId);

    /**
     * 用户登出时清除在线记录。
     *
     * @param userId 用户 ID
     */
    void removeOnline(Long userId);
}
