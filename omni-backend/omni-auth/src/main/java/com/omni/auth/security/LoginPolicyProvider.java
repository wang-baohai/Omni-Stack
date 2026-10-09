package com.omni.auth.security;

import com.omni.auth.entity.SysAuthLoginPolicy;
import com.omni.auth.mapper.SysAuthLoginPolicyMapper;
import com.omni.common.core.result.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 登录保护策略读取 provider（D01，单一来源）。
 * <p>认证需要时从 Auth DB 读取本租户生效策略；<b>本期不引入内存/Redis 缓存</b>，
 * 以避免两个配置源与缓存失效竞态。</p>
 * <ul>
 *   <li>没有持久化行 → 返回内置默认（阈值 10 / 窗口 3600 秒）。</li>
 *   <li>DB 查询故障不是“行不存在”，抛出受控依赖错误使登录失败关闭，绝不静默放行。</li>
 * </ul>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginPolicyProvider {

    /** 内置默认阈值（与历史常量一致） */
    public static final int DEFAULT_THRESHOLD = 10;
    /** 内置默认窗口秒数（与历史 1 小时一致） */
    public static final int DEFAULT_WINDOW_SECONDS = 3600;

    private final SysAuthLoginPolicyMapper policyMapper;

    /**
     * 读取本租户生效策略。
     *
     * @param tenantId 租户 ID
     * @return 生效策略值；无行时为默认（version=0）
     * @throws BusinessException 依赖故障时（失败关闭，不放行）
     */
    public Effective effective(Long tenantId) {
        SysAuthLoginPolicy row;
        try {
            row = policyMapper.selectById(tenantId);
        } catch (Exception e) {
            // 查询故障不等于无配置：失败关闭，禁止静默放行
            log.error("读取登录保护策略失败，登录保护将失败关闭: tenantId={}", tenantId, e);
            throw new BusinessException(503, "登录保护策略暂时不可用，请稍后重试");
        }
        if (row == null) {
            return new Effective(DEFAULT_THRESHOLD, DEFAULT_WINDOW_SECONDS, 0L, false);
        }
        return new Effective(row.getFailureThreshold(), row.getFailureWindowSeconds(),
                row.getVersion() == null ? 0L : row.getVersion(), true);
    }

    /**
     * 生效策略值对象。
     *
     * @param threshold     锁定阈值
     * @param windowSeconds 固定窗口秒数
     * @param version       策略版本（默认 0）
     * @param override      是否为租户覆盖（否则默认）
     */
    public record Effective(int threshold, int windowSeconds, long version, boolean override) {
    }
}
