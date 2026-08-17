package com.omni.procurement.security;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 采购概览跨聚合根数据范围映射测试。 */
class OverviewDataScopeTest {

    private final ProcDataPermissionHandler handler = new ProcDataPermissionHandler();

    /** 每次测试后清理数据范围。 */
    @AfterEach
    void clearScope() {
        ProcDataScopeContext.clear();
    }

    /** 概览 SELF 范围必须按各业务列表的 requester/owner 列分别约束。 */
    @Test
    void shouldUseListScopeColumnsForEveryOverviewAggregate() {
        ProcDataScopeContext.set(new ProcDataScopeContext.ScopeInfo(
                17L, 3L, "procurement:overview:list", 8L, "SELF", Set.of(8L)));

        assertScopeColumn("proc_requisition", "requester_user_id = 17");
        assertScopeColumn("proc_rfq", "owner_user_id = 17");
        assertScopeColumn("proc_purchase_order", "owner_user_id = 17");
        assertScopeColumn("proc_goods_receipt", "owner_user_id = 17");
    }

    /** 概览部门范围必须按各业务列表的 requester/owner 组织列分别约束。 */
    @Test
    void shouldUseListUnitColumnsForDepartmentOverview() {
        ProcDataScopeContext.set(new ProcDataScopeContext.ScopeInfo(
                17L, 3L, "procurement:overview:list", 8L,
                "DEPT_AND_BELOW", Set.of(8L, 9L)));

        assertScopeColumn("proc_requisition", "requester_unit_id IN (8, 9)");
        assertScopeColumn("proc_rfq", "owner_unit_id IN (8, 9)");
        assertScopeColumn("proc_purchase_order", "owner_unit_id IN (8, 9)");
        assertScopeColumn("proc_goods_receipt", "owner_unit_id IN (8, 9)");
    }

    private void assertScopeColumn(String tableName, String condition) {
        Expression expression = handler.getSqlSegment(new Table(tableName), null,
                "com.omni.procurement.mapper.ProcOverviewMapper.select");
        assertThat(expression.toString()).contains(condition);
    }
}
