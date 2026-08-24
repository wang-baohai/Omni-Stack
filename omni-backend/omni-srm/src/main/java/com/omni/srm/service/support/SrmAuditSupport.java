package com.omni.srm.service.support;

import com.omni.common.core.model.BaseEntity;
import com.omni.common.service.identity.ServiceIdentityContext;

import java.time.LocalDateTime;

/**
 * SRM 显式审计字段填充工具。
 *
 * @author Omni-Stack Team
 */
public final class SrmAuditSupport {

    private SrmAuditSupport() {
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
        String username = ServiceIdentityContext.require().username();
        return username == null || username.isBlank()
                ? String.valueOf(ServiceIdentityContext.require().userId()) : username;
    }
}
