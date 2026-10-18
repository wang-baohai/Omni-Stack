package com.omni.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.auth.entity.SysUser;
import com.omni.auth.event.AuditEvent;
import com.omni.auth.mapper.SysUserMapper;
import com.omni.auth.security.DataScopeContext;
import com.omni.auth.service.OnlineUserService;
import com.omni.auth.service.OnlineUserVO;
import com.omni.common.core.result.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 在线用户服务实现类。
 * <p>基于 Redis 追踪在线用户状态。每个在线用户在 Redis 中有一个 key：
 * {@code online:{userId}}，value 为 {@code username|jti}，TTL 与 JWT 有效期一致。</p>
 * <p>管理查询和强制下线统一校验请求的数据范围，Redis 中的全局键不能作为授权依据。</p>
 *
 * @author Omni-Stack Team
 * @see com.omni.auth.service.OnlineUserService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineUserServiceImpl implements OnlineUserService {

    /** Redis key 前缀 */
    private static final String ONLINE_PREFIX = "online:";
    /** Token 黑名单 key 前缀 */
    private static final String BLACKLIST_PREFIX = "token:blacklist:";
    /** 在线用户管理支持的数据范围 */
    private static final Set<String> SUPPORTED_SCOPES =
            Set.of("ALL", "TENANT", "DEPT", "DEPT_AND_BELOW", "CUSTOM", "SELF");

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
     * <p>扫描在线键后按用户表中的租户和主组织校验可见性；
     * 非 ALL 查询显式限制租户，已删除或范围外的用户不会返回。</p>
     */
    @Override
    public List<OnlineUserVO> listOnlineUsers() {
        DataScopeContext.DataScopeInfo scope = requireDataScope();
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
        if (userIds.isEmpty()) {
            return List.of();
        }

        // Redis 不含租户边界，必须使用数据库中的归属信息校验每个候选用户。
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getId, userIds)
                .eq(!"ALL".equals(scope.getEffectiveScope()), SysUser::getTenantId, scope.getTenantId());
        Map<Long, SysUser> visibleUsers = sysUserMapper.selectList(query).stream()
                .filter(user -> canAccess(user, scope))
                .collect(Collectors.toMap(SysUser::getId, user -> user, (first, ignored) -> first));
        result.removeIf(vo -> !visibleUsers.containsKey(vo.getUserId()));
        for (OnlineUserVO vo : result) {
            vo.setPrimaryUnitId(visibleUsers.get(vo.getUserId()).getPrimaryUnitId());
        }
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>先校验目标用户的数据范围，再删除在线标记、将 jti 加入默认 24 小时黑名单，
     * 并发布 LOGOUT 审计事件；拒绝请求不会读写 Redis 或发布成功审计事件。</p>
     */
    @Override
    public void kickUser(Long userId, String operatorUsername, String ipAddress) {
        DataScopeContext.DataScopeInfo scope = requireDataScope();
        SysUser user = userId != null && userId > 0 ? sysUserMapper.selectById(userId) : null;
        if (!canAccess(user, scope)) {
            throw new BusinessException(403, "无权操作该在线用户");
        }

        String key = ONLINE_PREFIX + userId;
        String value = stringRedisTemplate.opsForValue().get(key);
        stringRedisTemplate.delete(key);
        String username = user.getUsername();
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

        // 发布 LOGOUT 审计事件（管理员强制踢出），归属使用已授权的目标用户。
        eventPublisher.publishEvent(AuditEvent.of(AuditEvent.LOGOUT)
                .tenantId(user.getTenantId())
                .userId(userId)
                .username(username != null ? username : String.valueOf(userId))
                .ipAddress(ipAddress)
                .description("管理员强制踢出用户下线")
                .createBy(operatorUsername)
                .extra("method", "admin_kick")
                .build());
        log.info("已强制踢出用户: userId={}, operator={}", userId, operatorUsername);
    }

    /** 读取可信请求数据范围，缺少身份、租户或有效范围时失败关闭。 */
    private DataScopeContext.DataScopeInfo requireDataScope() {
        DataScopeContext.DataScopeInfo scope = DataScopeContext.get();
        if (scope == null || scope.getUserId() == null || scope.getUserId() <= 0
                || scope.getTenantId() == null || scope.getTenantId() <= 0
                || scope.getEffectiveScope() == null
                || !SUPPORTED_SCOPES.contains(scope.getEffectiveScope())) {
            throw new BusinessException(403, "缺少有效的在线用户管理数据范围");
        }
        return scope;
    }

    /** 校验目标用户是否属于当前请求的数据范围，ALL 保留跨租户能力。 */
    private boolean canAccess(SysUser user, DataScopeContext.DataScopeInfo scope) {
        if (user == null) {
            return false;
        }
        if ("ALL".equals(scope.getEffectiveScope())) {
            return true;
        }
        if (!Objects.equals(user.getTenantId(), scope.getTenantId())) {
            return false;
        }
        return switch (scope.getEffectiveScope()) {
            case "TENANT" -> true;
            case "SELF" -> Objects.equals(user.getId(), scope.getUserId());
            case "DEPT", "DEPT_AND_BELOW", "CUSTOM" -> user.getPrimaryUnitId() != null
                    && scope.getAccessibleUnitIds() != null
                    && scope.getAccessibleUnitIds().contains(user.getPrimaryUnitId());
            default -> false;
        };
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
