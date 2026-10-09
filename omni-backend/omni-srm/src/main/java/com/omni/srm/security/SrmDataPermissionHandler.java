package com.omni.srm.security;

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

import java.util.Map;
import java.util.Set;

/**
 * SRM 授权聚合根 owner 数据权限 SQL 处理器。
 *
 * @author Omni-Stack Team
 */
@Component
public class SrmDataPermissionHandler implements MultiDataPermissionHandler, DataScopeTablePolicy {

    private static final Set<String> OWNED_TABLES = Set.of("srm_supplier", "srm_evaluation");
    private static final Map<String, ParentRelation> CHILD_TABLES = Map.of(
            "srm_supplier_contact", new ParentRelation("srm_supplier", "supplier_id", "id"),
            "srm_supplier_qualification", new ParentRelation("srm_supplier", "supplier_id", "id"),
            "srm_supplier_bank_account", new ParentRelation("srm_supplier", "supplier_id", "id"),
            "srm_risk_indicator", new ParentRelation("srm_supplier", "supplier_id", "id"),
            "srm_risk_assessment", new ParentRelation("srm_supplier", "supplier_id", "id"),
            "srm_evaluation_item", new ParentRelation("srm_evaluation", "evaluation_id", "id"),
            "srm_quotation", new ParentRelation("srm_supplier", "supplier_id", "id"));

    private static final Map<String, NestedParentRelation> NESTED_CHILD_TABLES = Map.of(
            "srm_quotation_line", new NestedParentRelation(
                    "srm_quotation", "quotation_id", "id",
                    "srm_supplier", "supplier_id", "id"));

    /** {@inheritDoc} */
    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        if (table == null || table.getName() == null) {
            return null;
        }
        String tableName = table.getName().toLowerCase();
        if (!OWNED_TABLES.contains(tableName) && !CHILD_TABLES.containsKey(tableName)
                && !NESTED_CHILD_TABLES.containsKey(tableName)) {
            return null;
        }
        String alias = table.getAlias() == null ? table.getName() : table.getAlias().getName();
        ServiceDataScopeContext.ScopeInfo info = ServiceDataScopeContext.get();
        if (info == null || info.effectiveScope() == null) {
            return deny(alias);
        }
        if ("ALL".equals(info.effectiveScope()) || "TENANT".equals(info.effectiveScope())) {
            return null;
        }
        ParentRelation relation = CHILD_TABLES.get(tableName);
        if (relation != null) {
            return inherited(alias, relation, info);
        }
        NestedParentRelation nestedRelation = NESTED_CHILD_TABLES.get(tableName);
        if (nestedRelation != null) {
            return nestedInherited(alias, nestedRelation, info);
        }
        return owned(alias, info);
    }

    private Expression owned(String alias, ServiceDataScopeContext.ScopeInfo info) {
        return switch (info.effectiveScope()) {
            case "SELF" -> equals(alias, "owner_user_id", info.userId());
            case "DEPT" -> equals(alias, "owner_unit_id", info.primaryUnitId());
            case "DEPT_AND_BELOW", "CUSTOM" -> in(alias, "owner_unit_id", info.accessibleUnitIds());
            default -> deny(alias);
        };
    }

    private Expression inherited(String alias, ParentRelation relation, ServiceDataScopeContext.ScopeInfo info) {
        if (info.tenantId() == null) {
            return deny(alias);
        }
        String parentAlias = "srm_scope_parent";
        Expression owner = owned(parentAlias, info);
        if (owner == null) {
            return null;
        }
        String condition = "EXISTS (SELECT 1 FROM " + relation.parentTable() + " " + parentAlias
                + " WHERE " + parentAlias + "." + relation.parentColumn() + " = "
                + alias + "." + relation.childColumn()
                + " AND " + parentAlias + ".tenant_id = " + info.tenantId()
                + " AND " + parentAlias + ".deleted = 0 AND " + owner + ")";
        try {
            return CCJSqlParserUtil.parseCondExpression(condition);
        } catch (Exception exception) {
            throw new IllegalStateException("无法构建 SRM 子资源数据权限条件", exception);
        }
    }

    private Expression nestedInherited(String alias, NestedParentRelation relation,
                                       ServiceDataScopeContext.ScopeInfo info) {
        if (info.tenantId() == null) {
            return deny(alias);
        }
        String intermediateAlias = "srm_scope_intermediate";
        String parentAlias = "srm_scope_parent";
        Expression owner = owned(parentAlias, info);
        String condition = "EXISTS (SELECT 1 FROM " + relation.intermediateTable() + " "
                + intermediateAlias + " JOIN " + relation.parentTable() + " " + parentAlias
                + " ON " + parentAlias + "." + relation.parentColumn() + " = "
                + intermediateAlias + "." + relation.intermediateParentColumn()
                + " AND " + parentAlias + ".tenant_id = " + info.tenantId()
                + " AND " + parentAlias + ".deleted = 0"
                + " WHERE " + intermediateAlias + "." + relation.intermediateColumn() + " = "
                + alias + "." + relation.childColumn()
                + " AND " + intermediateAlias + ".tenant_id = " + info.tenantId()
                + " AND " + intermediateAlias + ".deleted = 0 AND " + owner + ")";
        try {
            return CCJSqlParserUtil.parseCondExpression(condition);
        } catch (Exception exception) {
            throw new IllegalStateException("无法构建 SRM 孙资源数据权限条件", exception);
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
     * 子资源到授权父表的关联关系。
     *
     * @param parentTable 父表
     * @param childColumn 子表外键列
     * @param parentColumn 父表主键列
     */
    private record ParentRelation(String parentTable, String childColumn, String parentColumn) {
    }

    /**
     * 孙资源经中间聚合再继承授权父表的关联关系。
     *
     * @param intermediateTable 中间聚合表
     * @param childColumn 孙表指向中间表的外键列
     * @param intermediateColumn 中间表主键列
     * @param parentTable 授权父表
     * @param intermediateParentColumn 中间表指向授权父表的外键列
     * @param parentColumn 授权父表主键列
     */
    private record NestedParentRelation(String intermediateTable, String childColumn,
                                        String intermediateColumn, String parentTable,
                                        String intermediateParentColumn, String parentColumn) {
    }
}
