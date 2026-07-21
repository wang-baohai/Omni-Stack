package com.omni.srm.security;

import com.omni.common.core.internal.InternalDataScopeDTO;
import com.omni.common.core.result.BusinessException;

import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;

/**
 * SRM permission-aware 数据范围上下文。
 *
 * @author Omni-Stack Team
 */
public final class SrmDataScopeContext {

    private static final ThreadLocal<ScopeInfo> CONTEXT = new ThreadLocal<>();

    private SrmDataScopeContext() {
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
                dto.getPrimaryUnitId(), dto.getEffectiveScope(), unitIds));
    }

    /**
     * 恢复已保存的数据范围快照。
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
            throw new BusinessException(403, "缺少 SRM 数据权限上下文");
        }
        return info;
    }

    /**
     * 获取可空的数据范围，仅供 SQL 拦截器转换成拒绝条件。
     *
     * @return 数据范围或 null
     */
    public static ScopeInfo get() {
        return CONTEXT.get();
    }

    /**
     * 清理当前线程数据范围。
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 门户场景绕过数据权限。
     * <p>门户端点已通过 {@code srm_supplier_portal_user} 限定供应商归属，
     * 无需走 owner 维度的数据权限过滤。设置临时 TENANT 作用域后执行操作，finally 中还原。</p>
     *
     * @param action 需要绕过数据权限的操作
     * @param <T>    返回值类型
     * @return 操作结果
     */
    public static <T> T runAsPortal(Supplier<T> action) {
        ScopeInfo previous = CONTEXT.get();
        try {
            SrmTenantContext.RequestIdentity identity = SrmTenantContext.require();
            set(new ScopeInfo(identity.userId(), identity.tenantId(), "PORTAL",
                    null, "TENANT", Collections.emptySet()));
            return action.get();
        } finally {
            if (previous != null) {
                CONTEXT.set(previous);
            } else {
                CONTEXT.remove();
            }
        }
    }

    /**
     * 数据范围不可变快照。
     *
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     * @param permissionCode 权限码
     * @param primaryUnitId 主组织 ID
     * @param effectiveScope 有效范围
     * @param accessibleUnitIds 可访问组织 ID
     */
    public record ScopeInfo(Long userId, Long tenantId, String permissionCode, Long primaryUnitId,
                            String effectiveScope, Set<Long> accessibleUnitIds) {
    }
}
