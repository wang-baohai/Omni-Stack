package com.omni.asset.service.support;

import com.omni.asset.entity.AstAsset;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.core.result.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 资产写命令行级访问守卫。
 *
 * @author Omni-Stack Team
 */
@Component
public class AssetRecordAccessGuard {

    /**
     * 要求受数据权限保护的查询结果存在。
     *
     * @param value 查询结果
     * @param message 不存在提示
     * @param <T> 结果类型
     * @return 原查询结果
     */
    public <T> T requireVisible(T value, String message) {
        if (value == null) {
            throw new BusinessException(404, message);
        }
        return value;
    }

    /**
     * 要求条件写入命中唯一一行。
     *
     * @param affected 受影响行数
     * @param message 冲突提示
     */
    public void requireAffected(int affected, String message) {
        if (affected != 1) {
            throw new BusinessException(409, message);
        }
    }

    /**
     * 强制资产当前分配给调用人，避免管理数据范围绕过本人命令边界。
     *
     * @param asset 资产
     */
    public void requireAssignedToCurrentUser(AstAsset asset) {
        Long currentUserId = ServiceIdentityContext.require().userId();
        if (!currentUserId.equals(asset.getCurrentUserId())) {
            throw new BusinessException(404, "资产不存在或未分配给当前用户");
        }
    }

    /**
     * 校验新管理归属位于当前操作的数据范围内。
     *
     * @param ownerUserId 管理员用户 ID
     * @param ownerUnitId 管理部门 ID
     */
    public void requireOwnerWritable(Long ownerUserId, Long ownerUnitId) {
        ServiceDataScopeContext.ScopeInfo scope = ServiceDataScopeContext.require();
        String effectiveScope = scope.effectiveScope();
        if ("ALL".equals(effectiveScope) || "TENANT".equals(effectiveScope)) {
            return;
        }
        if ("SELF".equals(effectiveScope) && scope.userId().equals(ownerUserId)
                && scope.primaryUnitId() != null && scope.primaryUnitId().equals(ownerUnitId)) {
            return;
        }
        requireUnitWritable(ownerUnitId);
    }

    /**
     * 校验业务目标部门位于当前操作的数据范围内。
     *
     * @param unitId 目标部门 ID
     */
    public void requireUnitWritable(Long unitId) {
        ServiceDataScopeContext.ScopeInfo scope = ServiceDataScopeContext.require();
        String effectiveScope = scope.effectiveScope();
        boolean allowed = switch (effectiveScope) {
            case "ALL", "TENANT" -> true;
            case "DEPT" -> unitId != null && unitId.equals(scope.primaryUnitId());
            case "DEPT_AND_BELOW", "CUSTOM" -> contains(scope.accessibleUnitIds(), unitId);
            default -> false;
        };
        if (!allowed) {
            throw new BusinessException(403, "目标部门超出当前数据权限范围");
        }
    }

    private boolean contains(Set<Long> values, Long value) {
        return values != null && value != null && values.contains(value);
    }
}
