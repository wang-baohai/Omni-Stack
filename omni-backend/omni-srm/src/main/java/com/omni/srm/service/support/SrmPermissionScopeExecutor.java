package com.omni.srm.service.support;

import com.omni.common.core.internal.InternalDataScopeDTO;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import com.omni.srm.client.AuthInternalClient;
import com.omni.srm.security.SrmDataScopeContext;
import com.omni.srm.security.SrmTenantContext;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Supplier 360 等跨聚合查询使用的精确权限范围执行器。
 *
 * @author Omni-Stack Team
 */
@Component
@RequiredArgsConstructor
public class SrmPermissionScopeExecutor {

    private final AuthInternalClient authInternalClient;

    /**
     * 当前用户具备功能权限时，在该权限专属 dataScope 内执行查询；否则返回默认值。
     *
     * @param permissionCode 完整权限码
     * @param supplier 查询逻辑
     * @param deniedValue 无功能权限时的值
     * @param <T> 结果类型
     * @return 查询结果或默认值
     */
    public <T> T executeIfGranted(String permissionCode, Supplier<T> supplier, T deniedValue) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean granted = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> permissionCode.equals(authority.getAuthority()));
        if (!granted) {
            return deniedValue;
        }
        SrmDataScopeContext.ScopeInfo previous = SrmDataScopeContext.get();
        try {
            R<InternalDataScopeDTO> response = authInternalClient.resolveDataScope(
                    SrmTenantContext.require().userId(), SrmTenantContext.requireTenantId(), permissionCode);
            if (response == null || response.getCode() != 200 || response.getData() == null) {
                int code = response != null && response.getCode() == 403 ? 403 : 503;
                throw new BusinessException(code, code == 403 ? "跨聚合查询权限不足" : "权限服务暂时不可用");
            }
            InternalDataScopeDTO scope = response.getData();
            SrmTenantContext.RequestIdentity identity = SrmTenantContext.require();
            if (!identity.userId().equals(scope.getUserId()) || !identity.tenantId().equals(scope.getTenantId())
                    || !permissionCode.equals(scope.getPermissionCode()) || scope.getEffectiveScope() == null) {
                throw new BusinessException(403, "权限服务返回了不一致的跨聚合数据范围");
            }
            SrmDataScopeContext.set(scope);
            return supplier.get();
        } catch (FeignException exception) {
            throw new BusinessException(exception.status() == 403 ? 403 : 503,
                    exception.status() == 403 ? "跨聚合查询权限不足" : "权限服务暂时不可用");
        } finally {
            if (previous == null) {
                SrmDataScopeContext.clear();
            } else {
                SrmDataScopeContext.set(previous);
            }
        }
    }
}
