package com.omni.common.service.internal;

import com.omni.common.core.result.BusinessException;

/**
 * 只接受正数显式租户 ID 的默认解析器。
 *
 * @author Omni-Stack Team
 */
public class FailClosedInternalTenantResolver implements InternalTenantResolver {

    /** {@inheritDoc} */
    @Override
    public Long requireExplicitTenantId(Long explicitTenantId) {
        if (explicitTenantId == null || explicitTenantId <= 0) {
            throw new BusinessException(403, "内部调用缺少合法的显式租户 ID");
        }
        return explicitTenantId;
    }
}
