package com.omni.procurement.security;

import com.omni.common.core.internal.InternalDataScopeDTO;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 采购聚合根和子资源数据权限测试。 */
class ProcDataPermissionHandlerTest {

    private final ProcDataPermissionHandler handler = new ProcDataPermissionHandler();

    /** 每次测试后清理数据范围。 */
    @AfterEach
    void clearScope() {
        ProcDataScopeContext.clear();
    }

    /** 缺少上下文时授权聚合根必须失败关闭。 */
    @Test
    void shouldFailClosedWithoutScope() {
        Expression expression = handler.getSqlSegment(
                new Table("proc_requisition"), null, "mapper.select");

        assertThat(expression.toString()).contains("proc_requisition.id = -1");
    }

    /** 请购 SELF 范围必须使用申请人列。 */
    @Test
    void shouldUseRequesterForRequisitionSelfScope() {
        ProcDataScopeContext.set(scope("SELF", Set.of()));

        Expression expression = handler.getSqlSegment(
                new Table("proc_requisition"), null, "mapper.select");

        assertThat(expression.toString())
                .contains("proc_requisition.requester_user_id = 12")
                .doesNotContain("owner_user_id");
    }

    /** 请购行必须通过同租户请购根继承申请部门范围。 */
    @Test
    void shouldInheritRequisitionUnitsForLine() {
        ProcDataScopeContext.set(scope("DEPT_AND_BELOW", Set.of(8L, 9L)));

        Expression expression = handler.getSqlSegment(
                new Table("proc_requisition_line"), null, "mapper.select");

        assertThat(expression.toString())
                .contains("proc_requisition", "requisition_id", "tenant_id = 3",
                        "requester_unit_id IN (8, 9)")
                .doesNotContain("proc_requisition_line.requester_unit_id");
    }

    /** RFQ 供应商邀请必须通过 RFQ owner 继承 SELF 范围。 */
    @Test
    void shouldInheritRfqOwnerForSupplierInvitation() {
        ProcDataScopeContext.set(scope("SELF", Set.of()));

        Expression expression = handler.getSqlSegment(
                new Table("proc_rfq_supplier"), null, "mapper.select");

        assertThat(expression.toString())
                .contains("proc_rfq", "rfq_id", "owner_user_id = 12")
                .doesNotContain("proc_rfq_supplier.owner_user_id");
    }

    /** 采购订单行必须通过订单根继承 DEPT 范围。 */
    @Test
    void shouldInheritPurchaseOrderDepartmentForLine() {
        ProcDataScopeContext.set(scope("DEPT", Set.of()));

        Expression expression = handler.getSqlSegment(
                new Table("proc_purchase_order_line"), null, "mapper.select");

        assertThat(expression.toString())
                .contains("proc_purchase_order", "po_id", "owner_unit_id = 8");
    }

    /** 收货行 CUSTOM 空组织集合必须在聚合根上失败关闭。 */
    @Test
    void shouldFailClosedForEmptyGoodsReceiptCustomScope() {
        ProcDataScopeContext.set(scope("CUSTOM", Set.of()));

        Expression expression = handler.getSqlSegment(
                new Table("proc_goods_receipt_line"), null, "mapper.select");

        assertThat(expression.toString()).contains("proc_scope_parent.id = -1");
    }

    /** 物料、品类、路由和租户配置均为租户共享，不追加 owner 条件。 */
    @Test
    void shouldLeaveSharedTablesToTenantLine() {
        assertThat(handler.getSqlSegment(new Table("proc_material"), null, "mapper.select")).isNull();
        assertThat(handler.getSqlSegment(
                new Table("proc_material_category"), null, "mapper.select")).isNull();
        assertThat(handler.getSqlSegment(
                new Table("proc_approval_route"), null, "mapper.select")).isNull();
        assertThat(handler.getSqlSegment(
                new Table("proc_tenant_config"), null, "mapper.select")).isNull();
    }

    /** TENANT 和 ALL 范围仅由 TenantLine 负责租户隔离。 */
    @Test
    void shouldLeaveTenantAndAllScopeToTenantLine() {
        ProcDataScopeContext.set(scope("TENANT", Set.of()));
        assertThat(handler.getSqlSegment(new Table("proc_rfq"), null, "mapper.select")).isNull();

        ProcDataScopeContext.set(scope("ALL", Set.of()));
        assertThat(handler.getSqlSegment(
                new Table("proc_goods_receipt"), null, "mapper.select")).isNull();
    }

    private InternalDataScopeDTO scope(String effectiveScope, Set<Long> units) {
        InternalDataScopeDTO dto = new InternalDataScopeDTO();
        dto.setUserId(12L);
        dto.setTenantId(3L);
        dto.setPermissionCode("procurement:test:list");
        dto.setPrimaryUnitId(8L);
        dto.setEffectiveScope(effectiveScope);
        dto.setAccessibleUnitIds(units);
        return dto;
    }
}
