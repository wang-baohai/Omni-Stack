package com.omni.workflow.security;

import com.omni.common.workflow.identity.UserGroupLookup;
import com.omni.common.workflow.tenant.TenantInfoHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 基于 RBAC 角色的 Flowable 用户组查询实现。
 * <p>
 * Flowable 的候选组使用角色编码表示，数据来源为 {@code omni_auth.sys_user_role} 与 {@code omni_auth.sys_role}。
 * </p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RbacUserGroupLookup implements UserGroupLookup {

    private final JdbcTemplate jdbcTemplate;

    /** {@inheritDoc} */
    @Override
    public List<String> getGroupsForUser(String userId) {
        try {
            String tenantId = TenantInfoHolder.getTenantId();
            if (tenantId != null && !tenantId.isBlank()) {
                return jdbcTemplate.queryForList("""
                        SELECT r.role_code
                        FROM omni_auth.sys_user_role ur
                        JOIN omni_auth.sys_role r ON ur.role_id = r.id
                        WHERE ur.user_id = ? AND r.tenant_id = ? AND r.status = 1
                        """, String.class, userId, Long.valueOf(tenantId));
            }
            return jdbcTemplate.queryForList("""
                    SELECT r.role_code
                    FROM omni_auth.sys_user_role ur
                    JOIN omni_auth.sys_role r ON ur.role_id = r.id
                    WHERE ur.user_id = ? AND r.status = 1
                    """, String.class, userId);
        } catch (DataAccessException | NumberFormatException e) {
            log.warn("查询 Flowable 候选组失败: userId={}, message={}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getAllGroups() {
        try {
            String tenantId = TenantInfoHolder.getTenantId();
            if (tenantId != null && !tenantId.isBlank()) {
                return jdbcTemplate.queryForList("""
                        SELECT role_code
                        FROM omni_auth.sys_role
                        WHERE tenant_id = ? AND status = 1
                        ORDER BY sort ASC, id ASC
                        """, String.class, Long.valueOf(tenantId));
            }
            return jdbcTemplate.queryForList("""
                    SELECT role_code
                    FROM omni_auth.sys_role
                    WHERE status = 1
                    ORDER BY sort ASC, id ASC
                    """, String.class);
        } catch (DataAccessException | NumberFormatException e) {
            log.warn("查询 Flowable 候选组列表失败: message={}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
