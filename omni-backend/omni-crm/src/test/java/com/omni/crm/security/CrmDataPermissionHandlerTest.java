package com.omni.crm.security;

import com.omni.common.core.internal.InternalDataScopeDTO;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** CRM SQL 数据权限处理器测试。 */
class CrmDataPermissionHandlerTest {

    private final CrmDataPermissionHandler handler = new CrmDataPermissionHandler();

    /** 每次测试清理线程上下文。 */
    @AfterEach
    void clear() {
        ServiceDataScopeContext.clear();
    }

    /** 缺少 scope 时必须生成拒绝条件。 */
    @Test
    void shouldFailClosedWithoutScope() {
        Expression expression = handler.getSqlSegment(new Table("crm_lead"), null, "mapper.select");
        assertThat(expression.toString()).contains("crm_lead.id = -1");
    }

    /** SELF 范围必须按 owner_user_id 过滤。 */
    @Test
    void shouldFilterSelfByOwner() {
        ServiceDataScopeContext.set(scope("SELF", Set.of()));
        Expression expression = handler.getSqlSegment(new Table("crm_customer"), null, "mapper.select");
        assertThat(expression.toString()).contains("owner_user_id = 12");
    }

    /** DEPT 范围必须仅按当前主组织过滤。 */
    @Test
    void shouldFilterDepartmentByPrimaryUnit() {
        ServiceDataScopeContext.set(scope("DEPT", Set.of()));
        Expression expression = handler.getSqlSegment(new Table("crm_lead"), null, "mapper.select");
        assertThat(expression.toString()).contains("owner_unit_id = 8");
    }

    /** DEPT_AND_BELOW 范围必须使用 Auth 返回的可访问组织集合。 */
    @Test
    void shouldFilterDepartmentTreeByAccessibleUnits() {
        ServiceDataScopeContext.set(scope("DEPT_AND_BELOW", Set.of(8L, 9L)));
        Expression expression = handler.getSqlSegment(new Table("crm_contact"), null, "mapper.select");
        assertThat(expression.toString()).contains("owner_unit_id IN (8, 9)");
    }

    /** CUSTOM 空组织集合必须拒绝全部记录。 */
    @Test
    void shouldDenyEmptyCustomScope() {
        ServiceDataScopeContext.set(scope("CUSTOM", Set.of()));
        Expression expression = handler.getSqlSegment(new Table("crm_opportunity"), null, "mapper.select");
        assertThat(expression.toString()).contains("id = -1");
    }

    /** TENANT 范围不追加 owner 条件，TenantLine 仍独立生效。 */
    @Test
    void shouldLeaveTenantScopeToTenantLine() {
        ServiceDataScopeContext.set(scope("TENANT", Set.of()));
        assertThat(handler.getSqlSegment(new Table("crm_activity"), null, "mapper.select")).isNull();
    }

    /** ALL 在普通 CRM API 中仍只表示当前租户全量，owner 层不追加条件。 */
    @Test
    void shouldLeaveAllScopeToTenantLine() {
        ServiceDataScopeContext.set(scope("ALL", Set.of()));
        assertThat(handler.getSqlSegment(new Table("crm_opportunity"), null, "mapper.select")).isNull();
    }

    private InternalDataScopeDTO scope(String effectiveScope, Set<Long> units) {
        InternalDataScopeDTO dto = new InternalDataScopeDTO(); dto.setUserId(12L); dto.setTenantId(3L);
        dto.setPermissionCode("crm:test:list"); dto.setPrimaryUnitId(8L); dto.setEffectiveScope(effectiveScope);
        dto.setAccessibleUnitIds(units); return dto;
    }
}
