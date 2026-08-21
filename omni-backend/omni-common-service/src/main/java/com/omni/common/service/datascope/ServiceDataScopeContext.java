package com.omni.common.service.datascope;

import com.omni.common.core.internal.InternalDataScopeDTO;
import com.omni.common.core.result.BusinessException;

import java.util.Collections;
import java.util.Set;

/**
 * 业务服务 permission-aware 数据范围上下文。
 *
 * @author Omni-Stack Team
 */
public final class ServiceDataScopeContext {

    private static final ThreadLocal<ScopeInfo> CONTEXT = new ThreadLocal<>();

    private ServiceDataScopeContext() {
    }

    /**
     * 写入 Auth 权威解析结果。
     *
     * @param dto 权威数据范围
     */
    public static void set(InternalDataScopeDTO dto) {
        Set<Long> unitIds = dto.getAccessibleUnitIds() == null
                ? Collections.emptySet() : Set.copyOf(dto.getAccessibleUnitIds());
        CONTEXT.set(new ScopeInfo(dto.getUserId(), dto.getTenantId(), dto.getPermissionCode(),
                dto.getPrimaryUnitId(), dto.getEffectiveScope(), unitIds, dto.getSecurityVersion()));
    }

    /**
     * 恢复显式保存的数据范围快照。
     *
     * @param info 数据范围快照
     */
    public static void set(ScopeInfo info) {
        CONTEXT.set(info);
    }

    /**
     * 获取数据范围，缺失时失败关闭。
     *
     * @return 数据范围
     */
    public static ScopeInfo require() {
        ScopeInfo info = CONTEXT.get();
        if (info == null) {
            throw new BusinessException(403, "缺少服务数据权限上下文");
        }
        return info;
    }

    /**
     * 获取可空数据范围，仅供 SQL 拦截策略生成拒绝条件。
     *
     * @return 数据范围或 null
     */
    public static ScopeInfo get() {
        return CONTEXT.get();
    }

    /**
     * 清理当前线程上下文。
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 不可变数据范围快照。
     *
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     * @param permissionCode 权限码
     * @param primaryUnitId 主组织 ID
     * @param effectiveScope 有效范围
     * @param accessibleUnitIds 可访问组织 ID
     * @param securityVersion 授权指纹
     */
    public record ScopeInfo(Long userId, Long tenantId, String permissionCode, Long primaryUnitId,
                            String effectiveScope, Set<Long> accessibleUnitIds, Long securityVersion) {
    }
}
