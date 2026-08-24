package com.omni.asset.service.support;

import com.omni.asset.entity.AstAsset;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.common.core.result.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 资产本人命令与管理归属写范围守卫测试。 */
class AssetRecordAccessGuardTest {

    private final AssetRecordAccessGuard guard = new AssetRecordAccessGuard();

    /** 清理线程上下文。 */
    @AfterEach
    void clearContext() {
        ServiceIdentityContext.clear();
        ServiceDataScopeContext.clear();
    }

    /** 领用和退还只能由资产当前使用人执行。 */
    @Test
    void shouldRequireCurrentAssignedUser() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 31L, "employee"));
        AstAsset asset = new AstAsset();
        asset.setCurrentUserId(8L);

        assertThatThrownBy(() -> guard.requireAssignedToCurrentUser(asset))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(404);
    }

    /** 部门范围写入不得把管理归属移出可访问组织。 */
    @Test
    void shouldRejectOwnerOutsideDepartmentScope() {
        ServiceDataScopeContext.set(new ServiceDataScopeContext.ScopeInfo(
                7L, 31L, "asset:asset:update", 12L,
                "DEPT_AND_BELOW", Set.of(12L, 13L), null));

        assertThatCode(() -> guard.requireOwnerWritable(9L, 13L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.requireOwnerWritable(9L, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(403);
    }

    /** 租户级管理角色可以设置当前租户内任意管理部门。 */
    @Test
    void shouldAllowOwnerForTenantScope() {
        ServiceDataScopeContext.set(new ServiceDataScopeContext.ScopeInfo(
                7L, 31L, "asset:asset:create", 12L,
                "TENANT", Set.of(), null));

        assertThatCode(() -> guard.requireOwnerWritable(9L, 99L)).doesNotThrowAnyException();
    }
}
