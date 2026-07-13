package com.omni.crm.service.support;

import com.omni.common.core.model.BaseEntity;
import com.omni.crm.security.CrmTenantContext;

import java.time.LocalDateTime;

/**
 * CRM 显式审计字段填充工具。
 *
 * @author Omni-Stack Team
 */
public final class CrmAuditSupport {

    private CrmAuditSupport() {
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
        String username = CrmTenantContext.require().username();
        return username == null || username.isBlank()
                ? String.valueOf(CrmTenantContext.require().userId()) : username;
    }
}
