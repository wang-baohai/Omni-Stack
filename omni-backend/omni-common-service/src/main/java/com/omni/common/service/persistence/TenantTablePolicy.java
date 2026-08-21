package com.omni.common.service.persistence;

/**
 * 领域服务租户表判定策略。
 *
 * @author Omni-Stack Team
 */
@FunctionalInterface
public interface TenantTablePolicy {

    /**
     * 判断表是否应用 TenantLine。
     *
     * @param tableName 表名
     * @return 是否应用租户隔离
     */
    boolean appliesTo(String tableName);
}
