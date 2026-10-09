package com.omni.asset.security;

import com.omni.common.core.internal.InternalDataScopeDTO;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 资产聚合根和子资源数据权限测试。 */
class AssetDataPermissionHandlerTest {

    private final AssetDataPermissionHandler handler = new AssetDataPermissionHandler();

    /** 每次测试后清理数据范围。 */
    @AfterEach
    void clearScope() {
        ServiceDataScopeContext.clear();
    }

    /** 缺少上下文时资产根必须失败关闭。 */
    @Test
    void shouldFailClosedWithoutScope() {
        Expression expression = handler.getSqlSegment(new Table("ast_asset"), null, "mapper.select");

        assertThat(expression.toString()).contains("ast_asset.id = -1");
    }

    /** 管理列表的 SELF 范围必须使用资产管理员列。 */
    @Test
    void shouldUseOwnerForManagementSelfScope() {
        ServiceDataScopeContext.set(scope("asset:asset:list", "SELF", Set.of()));

        Expression expression = handler.getSqlSegment(new Table("ast_asset"), null, "mapper.select");

        assertThat(expression.toString())
                .contains("ast_asset.owner_user_id = 12")
                .doesNotContain("current_user_id");
    }

    /** 我的资产即使 Auth 返回 TENANT 也必须固定当前使用人。 */
    @Test
    void shouldFixMyAssetToCurrentUserEvenWithTenantScope() {
        ServiceDataScopeContext.set(scope("asset:asset:self", "TENANT", Set.of()));

        Expression expression = handler.getSqlSegment(new Table("ast_asset"), null, "mapper.select");

        assertThat(expression.toString()).contains("ast_asset.current_user_id = 12");
    }

    /** 领用和退还命令不能因 ALL 范围扩大到他人资产。 */
    @Test
    void shouldFixSelfCommandsToCurrentUser() {
        ServiceDataScopeContext.set(scope("asset:asset:accept", "ALL", Set.of()));
        assertThat(handler.getSqlSegment(new Table("ast_asset"), null, "mapper.select").toString())
                .contains("ast_asset.current_user_id = 12");

        ServiceDataScopeContext.set(scope("asset:asset:return", "TENANT", Set.of()));
        assertThat(handler.getSqlSegment(new Table("ast_asset"), null, "mapper.select").toString())
                .contains("ast_asset.current_user_id = 12");
    }

    /** SELF 资产使用人发起调拨时必须使用当前使用人维度。 */
    @Test
    void shouldUseCurrentUserForSelfTransferCreation() {
        ServiceDataScopeContext.set(scope("asset:transfer:create", "SELF", Set.of()));

        Expression expression = handler.getSqlSegment(new Table("ast_asset"), null, "mapper.select");

        assertThat(expression.toString()).contains("ast_asset.current_user_id = 12");
    }

    /** 资产历史必须通过同租户资产根继承管理部门范围。 */
    @Test
    void shouldInheritAssetDepartmentForHistory() {
        ServiceDataScopeContext.set(scope("asset:asset:list", "DEPT_AND_BELOW", Set.of(8L, 9L)));

        Expression expression = handler.getSqlSegment(
                new Table("ast_asset_history"), null, "mapper.select");

        assertThat(expression.toString())
                .contains("ast_asset", "asset_id", "tenant_id = 3", "owner_unit_id IN (8, 9)")
                .doesNotContain("ast_asset_history.owner_unit_id");
    }

    /** 调拨与处置子表必须通过资产根继承 owner 范围。 */
    @Test
    void shouldInheritOwnerForOperationChildren() {
        ServiceDataScopeContext.set(scope("asset:transfer:list", "DEPT", Set.of()));
        assertThat(handler.getSqlSegment(new Table("ast_transfer"), null, "mapper.select").toString())
                .contains("ast_asset", "asset_id", "owner_unit_id = 8");

        ServiceDataScopeContext.set(scope("asset:disposal:list", "SELF", Set.of()));
        assertThat(handler.getSqlSegment(new Table("ast_disposal"), null, "mapper.select").toString())
                .contains("ast_asset", "asset_id", "owner_user_id = 12");
    }

    /** Inbox 与 Outbox 是内部基础设施表，不追加用户数据权限。 */
    @Test
    void shouldIgnoreInfrastructureTables() {
        assertThat(handler.getSqlSegment(new Table("ast_inbox_event"), null, "mapper.select")).isNull();
        assertThat(handler.getSqlSegment(new Table("sys_mq_message"), null, "mapper.select")).isNull();
    }

    /** CUSTOM 空组织集合必须失败关闭。 */
    @Test
    void shouldFailClosedForEmptyCustomScope() {
        ServiceDataScopeContext.set(scope("asset:asset:list", "CUSTOM", Set.of()));

        Expression expression = handler.getSqlSegment(new Table("ast_asset"), null, "mapper.select");

        assertThat(expression.toString()).contains("ast_asset.id = -1");
    }

    private InternalDataScopeDTO scope(String permissionCode, String effectiveScope, Set<Long> units) {
        InternalDataScopeDTO dto = new InternalDataScopeDTO();
        dto.setUserId(12L);
        dto.setTenantId(3L);
        dto.setPermissionCode(permissionCode);
        dto.setPrimaryUnitId(8L);
        dto.setEffectiveScope(effectiveScope);
        dto.setAccessibleUnitIds(units);
        return dto;
    }
}
