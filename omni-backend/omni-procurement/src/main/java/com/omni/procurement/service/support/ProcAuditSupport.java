package com.omni.procurement.service.support;

import com.omni.common.core.model.BaseEntity;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;

import java.time.LocalDateTime;

/**
 * 采购实体显式审计字段填充工具。
 *
 * @author Omni-Stack Team
 */
public final class ProcAuditSupport {

    private ProcAuditSupport() {
    }

    /**
     * 填充新增审计字段。
     *
     * @param entity 实体
     */
    public static void created(BaseEntity entity) {
        String operator = operator();
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateBy(operator);
        entity.setUpdateBy(operator);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
    }

    /**
     * 填充更新审计字段。
     *
     * @param entity 实体
     */
    public static void updated(BaseEntity entity) {
        entity.setUpdateBy(operator());
        entity.setUpdateTime(LocalDateTime.now());
    }

    private static String operator() {
        ServiceRequestIdentity identity = ServiceIdentityContext.require();
        return identity.username() == null || identity.username().isBlank()
                ? String.valueOf(identity.userId()) : identity.username();
    }
}
