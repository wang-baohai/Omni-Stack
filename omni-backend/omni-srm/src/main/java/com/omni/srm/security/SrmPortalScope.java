package com.omni.srm.security;

import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;

import java.util.Collections;
import java.util.function.Supplier;

/**
 * 在已由 PortalUser 关联限定供应商后，临时建立租户级数据范围。
 *
 * <p>该工具不维护独立 ThreadLocal，仅保存并恢复公共 Starter 的数据范围。门户调用仍必须先完成
 * {@code srm_supplier_portal_user} 的租户、用户和供应商三元校验。</p>
 *
 * @author Omni-Stack Team
 */
public final class SrmPortalScope {

    private SrmPortalScope() {
    }

    /**
     * 在门户租户范围内执行操作，并在 finally 中恢复原数据范围。
     *
     * @param action 门户操作
     * @param <T> 返回值类型
     * @return 操作结果
     */
    public static <T> T run(Supplier<T> action) {
        ServiceDataScopeContext.ScopeInfo previous = ServiceDataScopeContext.get();
        try {
            ServiceRequestIdentity identity = ServiceIdentityContext.require();
            ServiceDataScopeContext.set(new ServiceDataScopeContext.ScopeInfo(
                    identity.userId(), identity.tenantId(), "PORTAL", null,
                    "TENANT", Collections.emptySet(), null));
            return action.get();
        } finally {
            if (previous == null) {
                ServiceDataScopeContext.clear();
            } else {
                ServiceDataScopeContext.set(previous);
            }
        }
    }
}
