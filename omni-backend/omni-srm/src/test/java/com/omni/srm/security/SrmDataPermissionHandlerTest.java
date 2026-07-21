package com.omni.srm.security;

import com.omni.common.core.internal.InternalDataScopeDTO;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** SRM 聚合根继承数据权限测试。 */
class SrmDataPermissionHandlerTest {

    private final SrmDataPermissionHandler handler = new SrmDataPermissionHandler();

    /** 清理线程数据范围。 */
    @AfterEach
    void clearScope() {
        SrmDataScopeContext.clear();
    }

    /** 缺少上下文时必须失败关闭。 */
    @Test
    void shouldFailClosedWithoutScope() {
        Expression expression = handler.getSqlSegment(new Table("srm_supplier_contact"), null, "mapper.select");
        assertThat(expression.toString()).contains("srm_supplier_contact.id = -1");
    }

    /** 联系人 SELF 范围必须通过供应商 owner 继承，不引用子表 owner 列。 */
    @Test
    void shouldInheritSupplierSelfScopeForContact() {
        SrmDataScopeContext.set(scope("SELF", Set.of()));
        Expression expression = handler.getSqlSegment(new Table("srm_supplier_contact"), null, "mapper.select");
        assertThat(expression.toString())
                .contains("EXISTS", "srm_supplier", "supplier_id", "owner_user_id = 12")
                .doesNotContain("srm_supplier_contact.owner_user_id");
    }

    /** 评估明细必须通过评估主表继承组织数据范围。 */
    @Test
    void shouldInheritEvaluationDepartmentScopeForItem() {
        SrmDataScopeContext.set(scope("DEPT_AND_BELOW", Set.of(8L, 9L)));
        Expression expression = handler.getSqlSegment(new Table("srm_evaluation_item"), null, "mapper.select");
        assertThat(expression.toString())
                .contains("srm_evaluation", "evaluation_id", "owner_unit_id IN (8, 9)")
                .doesNotContain("srm_evaluation_item.owner_unit_id");
    }

    /** DEPT 范围必须按主组织继承供应商范围。 */
    @Test
    void shouldInheritPrimaryDepartmentForBankAccount() {
        SrmDataScopeContext.set(scope("DEPT", Set.of()));
        Expression expression = handler.getSqlSegment(
                new Table("srm_supplier_bank_account"), null, "mapper.select");
        assertThat(expression.toString()).contains("srm_supplier", "owner_unit_id = 8");
    }

    /** TENANT 范围由 TenantLine 负责，不追加 owner 条件。 */
    @Test
    void shouldLeaveTenantScopeToTenantLine() {
        SrmDataScopeContext.set(scope("TENANT", Set.of()));
        assertThat(handler.getSqlSegment(new Table("srm_risk_indicator"), null, "mapper.select")).isNull();
    }

    /** ALL 范围同样只由 TenantLine 保证租户隔离。 */
    @Test
    void shouldLeaveAllScopeToTenantLine() {
        SrmDataScopeContext.set(scope("ALL", Set.of()));
        assertThat(handler.getSqlSegment(new Table("srm_supplier_bank_account"), null, "mapper.select")).isNull();
    }

    /** CUSTOM 范围必须通过供应商组织集合继承。 */
    @Test
    void shouldInheritCustomUnitsForRisk() {
        SrmDataScopeContext.set(scope("CUSTOM", Set.of(8L, 10L)));
        Expression expression = handler.getSqlSegment(new Table("srm_risk_assessment"), null, "mapper.select");
        assertThat(expression.toString()).contains("srm_supplier", "owner_unit_id IN (8, 10)");
    }

    /** CUSTOM 空组织集合必须在父聚合根上失败关闭。 */
    @Test
    void shouldFailClosedForEmptyCustomScope() {
        SrmDataScopeContext.set(scope("CUSTOM", Set.of()));
        Expression expression = handler.getSqlSegment(new Table("srm_supplier_qualification"), null, "mapper.select");
        assertThat(expression.toString()).contains("srm_scope_parent.id = -1");
    }

    private InternalDataScopeDTO scope(String effectiveScope, Set<Long> units) {
        InternalDataScopeDTO dto = new InternalDataScopeDTO();
        dto.setUserId(12L);
        dto.setTenantId(3L);
        dto.setPermissionCode("srm:test:list");
        dto.setPrimaryUnitId(8L);
        dto.setEffectiveScope(effectiveScope);
        dto.setAccessibleUnitIds(units);
        return dto;
    }
}
