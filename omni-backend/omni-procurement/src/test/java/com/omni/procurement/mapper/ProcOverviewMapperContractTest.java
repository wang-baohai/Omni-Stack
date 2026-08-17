package com.omni.procurement.mapper;

import com.omni.procurement.dto.OverviewViews;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** 采购概览 Mapper 聚合 SQL 契约测试。 */
class ProcOverviewMapperContractTest {

    /** 所有概览金额字段必须显式按 JSON 十进制字符串序列化。 */
    @Test
    void shouldSerializeEveryOverviewAmountAsDecimalString() throws Exception {
        assertDecimalStringAnnotations(
                OverviewViews.CurrencyAmount.class.getDeclaredField("amount"));
        assertDecimalStringAnnotations(
                OverviewViews.SpendItem.class.getDeclaredField("amount"));
    }

    /** 支出 SQL 必须按币种分组，限定已确认状态，并在 Mapper 层排序限量。 */
    @Test
    void shouldGroupEverySpendQueryByCurrencyAndAmount() throws Exception {
        assertSpendSql("selectCategorySpend", "proc_purchase_order_line", "category_code");
        assertSpendSql("selectSupplierSpend", "proc_purchase_order", "supplier_id");
        assertSpendSql("selectDepartmentSpend", "proc_purchase_order", "owner_unit_id");
    }

    /** 摘要查询必须直接命中四个受数据权限保护的聚合根。 */
    @Test
    void shouldQueryEveryProtectedAggregateRootForSummary() throws Exception {
        assertThat(sql("countPendingApprovalRequisitions"))
                .contains("proc_requisition", "status = 'APPROVING'");
        assertThat(sql("countWaitingQuotationRfqs"))
                .contains("proc_rfq", "proc_rfq_supplier", "status = 'INVITED'");
        assertThat(sql("selectPurchaseOrderStatusCounts"))
                .contains("proc_purchase_order", "GROUP BY");
        assertThat(sql("countDraftGoodsReceipts"))
                .contains("proc_goods_receipt", "status = 'DRAFT'");
        assertThat(sql("selectCommittedAmountsByCurrency"))
                .contains("proc_purchase_order", "GROUP BY purchase_order.currency_code")
                .doesNotContain("SUM(CASE WHEN currency_code");
    }

    private void assertSpendSql(String methodName, String tableName,
                                String dimensionColumn) throws Exception {
        String sql = sql(methodName, int.class);
        assertThat(sql)
                .contains(tableName, dimensionColumn,
                        "purchase_order.currency_code", "SUM(", "GROUP BY", "amount DESC")
                .contains("'CONFIRMED'", "'PARTIAL_RECEIVED'", "'RECEIVED'", "'CLOSED'")
                .contains("LIMIT #{limit}")
                .doesNotContain("'DRAFT'", "'CANCELLED'");
    }

    private String sql(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = ProcOverviewMapper.class.getMethod(methodName, parameterTypes);
        Select select = method.getAnnotation(Select.class);
        assertThat(select).isNotNull();
        return String.join(" ", Arrays.stream(select.value()).toList());
    }

    private void assertDecimalStringAnnotations(java.lang.reflect.Field field) {
        assertThat(field.getAnnotation(
                com.fasterxml.jackson.databind.annotation.JsonSerialize.class)).isNotNull();
        assertThat(field.getAnnotation(
                tools.jackson.databind.annotation.JsonSerialize.class)).isNotNull();
    }
}
