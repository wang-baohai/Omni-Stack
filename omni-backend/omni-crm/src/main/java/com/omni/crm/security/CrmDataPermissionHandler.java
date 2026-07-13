package com.omni.crm.security;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;

import java.util.Set;

/**
 * CRM 授权聚合根 owner 数据权限 SQL 处理器。
 *
 * @author Omni-Stack Team
 */
public class CrmDataPermissionHandler implements MultiDataPermissionHandler {

    private static final Set<String> AUTHORIZED_TABLES = Set.of(
            "crm_lead", "crm_customer", "crm_contact", "crm_opportunity", "crm_activity");

    /** {@inheritDoc} */
    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        if (table == null || table.getName() == null
                || !AUTHORIZED_TABLES.contains(table.getName().toLowerCase())) {
            return null;
        }
        String alias = table.getAlias() == null ? table.getName() : table.getAlias().getName();
        CrmDataScopeContext.ScopeInfo info = CrmDataScopeContext.get();
        if (info == null || info.effectiveScope() == null) {
            return deny(alias);
        }
        return switch (info.effectiveScope()) {
            case "ALL", "TENANT" -> null;
            case "SELF" -> equals(alias, "owner_user_id", info.userId());
            case "DEPT" -> equals(alias, "owner_unit_id", info.primaryUnitId());
            case "DEPT_AND_BELOW", "CUSTOM" -> in(alias, "owner_unit_id", info.accessibleUnitIds());
            default -> deny(alias);
        };
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
}
