package com.omni.procurement.security;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.persistence.DataScopeTablePolicy;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 采购聚合根及其子资源数据权限 SQL 处理器。
 *
 * @author Omni-Stack Team
 */
@Component
public class ProcDataPermissionHandler implements MultiDataPermissionHandler, DataScopeTablePolicy {

    private static final Map<String, ScopeColumns> ROOT_TABLES = Map.of(
            "proc_requisition", new ScopeColumns("requester_user_id", "requester_unit_id"),
            "proc_rfq", new ScopeColumns("owner_user_id", "owner_unit_id"),
            "proc_purchase_order", new ScopeColumns("owner_user_id", "owner_unit_id"),
            "proc_goods_receipt", new ScopeColumns("owner_user_id", "owner_unit_id"));

    private static final Map<String, ParentRelation> CHILD_TABLES = Map.of(
            "proc_requisition_line", new ParentRelation(
                    "proc_requisition", "requisition_id", "id",
                    new ScopeColumns("requester_user_id", "requester_unit_id")),
            "proc_rfq_line", new ParentRelation(
                    "proc_rfq", "rfq_id", "id",
                    new ScopeColumns("owner_user_id", "owner_unit_id")),
            "proc_rfq_supplier", new ParentRelation(
                    "proc_rfq", "rfq_id", "id",
                    new ScopeColumns("owner_user_id", "owner_unit_id")),
            "proc_purchase_order_line", new ParentRelation(
                    "proc_purchase_order", "po_id", "id",
                    new ScopeColumns("owner_user_id", "owner_unit_id")),
            "proc_goods_receipt_line", new ParentRelation(
                    "proc_goods_receipt", "goods_receipt_id", "id",
                    new ScopeColumns("owner_user_id", "owner_unit_id")));

    /** {@inheritDoc} */
    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        if (table == null || table.getName() == null) {
            return null;
        }
        String tableName = table.getName().toLowerCase(Locale.ROOT);
        ScopeColumns rootColumns = ROOT_TABLES.get(tableName);
        ParentRelation parentRelation = CHILD_TABLES.get(tableName);
        if (rootColumns == null && parentRelation == null) {
            return null;
        }
        String alias = table.getAlias() == null ? table.getName() : table.getAlias().getName();
        ServiceDataScopeContext.ScopeInfo scope = ServiceDataScopeContext.get();
        if (scope == null || scope.effectiveScope() == null) {
            return deny(alias);
        }
        if ("ALL".equals(scope.effectiveScope()) || "TENANT".equals(scope.effectiveScope())) {
            return null;
        }
        if (rootColumns != null) {
            return scoped(alias, rootColumns, scope);
        }
        return inherited(alias, parentRelation, scope);
    }

    private Expression scoped(String alias, ScopeColumns columns, ServiceDataScopeContext.ScopeInfo scope) {
        return switch (scope.effectiveScope()) {
            case "SELF" -> equals(alias, columns.selfColumn(), scope.userId());
            case "DEPT" -> equals(alias, columns.unitColumn(), scope.primaryUnitId());
            case "DEPT_AND_BELOW", "CUSTOM" -> in(alias, columns.unitColumn(), scope.accessibleUnitIds());
            default -> deny(alias);
        };
    }

    private Expression inherited(String alias, ParentRelation relation,
                                 ServiceDataScopeContext.ScopeInfo scope) {
        if (scope.tenantId() == null) {
            return deny(alias);
        }
        String parentAlias = "proc_scope_parent";
        Expression parentScope = scoped(parentAlias, relation.scopeColumns(), scope);
        String condition = "EXISTS (SELECT 1 FROM " + relation.parentTable() + " " + parentAlias
                + " WHERE " + parentAlias + "." + relation.parentColumn() + " = "
                + alias + "." + relation.childColumn()
                + " AND " + parentAlias + ".tenant_id = " + scope.tenantId()
                + " AND " + parentAlias + ".deleted = 0 AND " + parentScope + ")";
        try {
            return CCJSqlParserUtil.parseCondExpression(condition);
        } catch (Exception exception) {
            throw new IllegalStateException("无法构建采购子资源数据权限条件", exception);
        }
    }

    private Expression equals(String alias, String column, Long value) {
        if (value == null) {
            return deny(alias);
        }
        return new EqualsTo(new Column(alias + "." + column), new LongValue(value));
    }

    private Expression in(String alias, String column, Set<Long> values) {
        if (values == null || values.isEmpty()) {
            return deny(alias);
        }
        ParenthesedExpressionList<Expression> list = new ParenthesedExpressionList<>();
        values.stream().sorted().forEach(value -> list.add(new LongValue(value)));
        InExpression expression = new InExpression();
        expression.setLeftExpression(new Column(alias + "." + column));
        expression.setRightExpression(list);
        return expression;
    }

    private Expression deny(String alias) {
        return new EqualsTo(new Column(alias + ".id"), new LongValue(-1));
    }

    /**
     * 聚合根的数据范围列。
     *
     * @param selfColumn SELF 用户列
     * @param unitColumn 组织范围列
     */
    private record ScopeColumns(String selfColumn, String unitColumn) {
    }

    /**
     * 子资源到授权聚合根的关联关系。
     *
     * @param parentTable 聚合根表
     * @param childColumn 子表外键列
     * @param parentColumn 聚合根主键列
     * @param scopeColumns 聚合根数据范围列
     */
    private record ParentRelation(String parentTable, String childColumn, String parentColumn,
                                  ScopeColumns scopeColumns) {
    }
}
