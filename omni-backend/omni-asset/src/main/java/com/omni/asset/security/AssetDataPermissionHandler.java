package com.omni.asset.security;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 资产聚合根及其子资源数据权限 SQL 处理器。
 *
 * @author Omni-Stack Team
 */
public class AssetDataPermissionHandler implements MultiDataPermissionHandler {

    private static final ScopeColumns MANAGEMENT_COLUMNS =
            new ScopeColumns("owner_user_id", "owner_unit_id");

    private static final Set<String> FIXED_CURRENT_USER_PERMISSIONS = Set.of(
            "asset:asset:self", "asset:asset:accept", "asset:asset:return");

    private static final Map<String, ScopeColumns> ROOT_TABLES = Map.of(
            "ast_asset", MANAGEMENT_COLUMNS);

    private static final Map<String, ParentRelation> CHILD_TABLES = Map.of(
            "ast_asset_history", new ParentRelation("ast_asset", "asset_id", "id"),
            "ast_transfer", new ParentRelation("ast_asset", "asset_id", "id"),
            "ast_disposal", new ParentRelation("ast_asset", "asset_id", "id"));

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
        AssetDataScopeContext.ScopeInfo scope = AssetDataScopeContext.get();
        if (scope == null || scope.effectiveScope() == null || scope.permissionCode() == null) {
            return deny(alias);
        }
        if (rootColumns != null) {
            return rootScope(alias, rootColumns, scope);
        }
        return inherited(alias, parentRelation, scope);
    }

    private Expression rootScope(String alias, ScopeColumns columns,
                                 AssetDataScopeContext.ScopeInfo scope) {
        if (usesCurrentUser(scope)) {
            return equals(alias, "current_user_id", scope.userId());
        }
        if ("ALL".equals(scope.effectiveScope()) || "TENANT".equals(scope.effectiveScope())) {
            return null;
        }
        return managementScope(alias, columns, scope);
    }

    private Expression managementScope(String alias, ScopeColumns columns,
                                       AssetDataScopeContext.ScopeInfo scope) {
        return switch (scope.effectiveScope()) {
            case "SELF" -> equals(alias, columns.selfColumn(), scope.userId());
            case "DEPT" -> equals(alias, columns.unitColumn(), scope.primaryUnitId());
            case "DEPT_AND_BELOW", "CUSTOM" -> in(alias, columns.unitColumn(), scope.accessibleUnitIds());
            default -> deny(alias);
        };
    }

    private Expression inherited(String alias, ParentRelation relation,
                                 AssetDataScopeContext.ScopeInfo scope) {
        if (scope.tenantId() == null) {
            return deny(alias);
        }
        String parentAlias = "asset_scope_parent";
        Expression parentScope;
        if (usesCurrentUser(scope)) {
            parentScope = equals(parentAlias, "current_user_id", scope.userId());
        } else if ("ALL".equals(scope.effectiveScope()) || "TENANT".equals(scope.effectiveScope())) {
            parentScope = null;
        } else {
            parentScope = managementScope(parentAlias, MANAGEMENT_COLUMNS, scope);
        }
        StringBuilder condition = new StringBuilder("EXISTS (SELECT 1 FROM ")
                .append(relation.parentTable()).append(' ').append(parentAlias)
                .append(" WHERE ").append(parentAlias).append('.').append(relation.parentColumn())
                .append(" = ").append(alias).append('.').append(relation.childColumn())
                .append(" AND ").append(parentAlias).append(".tenant_id = ").append(scope.tenantId())
                .append(" AND ").append(parentAlias).append(".deleted = 0");
        if (parentScope != null) {
            condition.append(" AND ").append(parentScope);
        }
        condition.append(')');
        try {
            return CCJSqlParserUtil.parseCondExpression(condition.toString());
        } catch (Exception exception) {
            throw new IllegalStateException("无法构建资产子资源数据权限条件", exception);
        }
    }

    private boolean usesCurrentUser(AssetDataScopeContext.ScopeInfo scope) {
        return FIXED_CURRENT_USER_PERMISSIONS.contains(scope.permissionCode())
                || ("asset:transfer:create".equals(scope.permissionCode())
                && "SELF".equals(scope.effectiveScope()));
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
     * 子资源到资产聚合根的关联关系。
     *
     * @param parentTable 聚合根表
     * @param childColumn 子表资产外键列
     * @param parentColumn 聚合根主键列
     */
    private record ParentRelation(String parentTable, String childColumn, String parentColumn) {
    }
}
