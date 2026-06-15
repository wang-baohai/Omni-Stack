package com.omni.auth.service.impl;

import com.omni.auth.entity.SysUser;
import com.omni.auth.event.AuditEvent;
import com.omni.auth.mapper.SysUserMapper;
import com.omni.auth.service.OnlineUserService;
import com.omni.auth.service.OnlineUserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 在线用户服务实现类。
 * <p>基于 Redis 追踪在线用户状态。每个在线用户在 Redis 中有一个 key：
 * {@code online:{userId}}，value 为 {@code username|jti}，TTL 与 JWT 有效期一致。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineUserServiceImpl implements OnlineUserService {

    /** Redis key 前缀 */
    private static final String ONLINE_PREFIX = "online:";
    /** Token 黑名单 key 前缀 */
    private static final String BLACKLIST_PREFIX = "token:blacklist:";

    private final StringRedisTemplate stringRedisTemplate;
    private final SysUserMapper sysUserMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * {@inheritDoc}
     *
     * <p>在 Redis 中创建 {@code online:{userId}} key，value 为 {@code username|jti}。</p>
     */
    @Override
    public void recordOnline(Long userId, String username, String jti, long ttlSeconds) {
        String key = ONLINE_PREFIX + userId;
        String value = username + "|" + jti;
        stringRedisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
        log.debug("记录用户上线: userId={}, username={}", userId, username);
    }

    /**
     * {@inheritDoc}
     *
     * <p>扫描所有 {@code online:*} key，解析出在线用户信息列表，
     * 并批量查询用户的主组织单元 ID 用于数据权限过滤。</p>
     */
    @Override
    public List<OnlineUserVO> listOnlineUsers() {
        Set<String> keys = stringRedisTemplate.keys(ONLINE_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<OnlineUserVO> result = new ArrayList<>();
        List<Long> userIds = new ArrayList<>();
        for (String key : keys) {
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value == null) {
                continue;
            }
            Long userId = Long.parseLong(key.substring(ONLINE_PREFIX.length()));
            String[] parts = value.split("\\|", 2);
            result.add(OnlineUserVO.builder()
                    .userId(userId)
                    .username(parts[0])
                    .jti(parts.length > 1 ? parts[1] : "")
                    .build());
            userIds.add(userId);
        }

        // 批量查询用户的主组织单元 ID
        if (!userIds.isEmpty()) {
            Map<Long, Long> unitMap = sysUserMapper.selectBatchIds(userIds).stream()
                    .collect(Collectors.toMap(SysUser::getId,
                            u -> u.getPrimaryUnitId() != null ? u.getPrimaryUnitId() : 0L,
                            (a, b) -> a));
            for (OnlineUserVO vo : result) {
                Long unitId = unitMap.get(vo.getUserId());
                vo.setPrimaryUnitId(unitId != null && unitId != 0L ? unitId : null);
            }
        }

        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>删除在线标记并将 Token 的 jti 加入黑名单（TTL 与 Token 剩余有效期一致）。
     * 踢出成功后发布 LOGOUT 审计事件。</p>
     */
    @Override
    public void kickUser(Long userId, String operatorUsername, String ipAddress) {
        String key = ONLINE_PREFIX + userId;
        String value = stringRedisTemplate.opsForValue().get(key);
        stringRedisTemplate.delete(key);

        String username = null;

        if (value != null) {
            String[] parts = value.split("\\|", 2);
            username = parts[0];
            if (parts.length > 1 && !parts[1].isEmpty()) {
                // 将 jti 加入黑名单，默认 24 小时过期（与 JWT 最大有效期对齐）
                stringRedisTemplate.opsForValue().set(
                        BLACKLIST_PREFIX + parts[1], "1", 24, TimeUnit.HOURS);
                log.info("已将 Token jti={} 加入黑名单", parts[1]);
            }
        }

        // 查询用户租户 ID
        Long tenantId = null;
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null) {
            tenantId = user.getTenantId();
            if (username == null) {
                username = user.getUsername();
            }
        }

        // 发布 LOGOUT 审计事件（管理员强制踢出）
        eventPublisher.publishEvent(AuditEvent.of(AuditEvent.LOGOUT)
                .tenantId(tenantId)
                .userId(userId)
                .username(username != null ? username : String.valueOf(userId))
                .ipAddress(ipAddress)
                .description("管理员强制踢出用户下线")
                .createBy(operatorUsername)
                .extra("method", "admin_kick")
                .build());

        log.info("已强制踢出用户: userId={}, operator={}", userId, operatorUsername);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isOnline(Long userId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(ONLINE_PREFIX + userId));
    }

    /**
     * {@inheritDoc}
     *
     * <p>用户主动登出时调用，仅删除在线标记，不加入黑名单。</p>
     */
    @Override
    public void removeOnline(Long userId) {
        stringRedisTemplate.delete(ONLINE_PREFIX + userId);
        log.debug("已移除用户在线标记: userId={}", userId);
    }
}
