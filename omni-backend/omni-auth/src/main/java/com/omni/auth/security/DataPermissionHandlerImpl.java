package com.omni.auth.security;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;

import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * MyBatis-Plus 数据权限处理器实现。
 * <p>根据 {@link DataScopeContext} 中存储的当前用户数据范围信息，
 * 为已注册的表自动追加 WHERE 条件。</p>
 * <p>通过 {@link ColumnResolver} 映射实现多表支持，每张表可定义独立的过滤规则。
 * 当 DataScopeContext 为空时（如登录请求），不添加任何过滤条件。</p>
 *
 * <p>当前支持的表：</p>
 * <ul>
 *   <li>{@code sys_user} — 按 {@code primary_unit_id} 或 {@code id} 过滤</li>
 *   <li>{@code sys_org_unit} — 按 {@code id} 过滤</li>
 *   <li>{@code sys_role} — 仅 TENANT/SELF 范围有实际意义，组织范围不过滤</li>
 * </ul>
 *
 * @author Omni-Stack Team
 * @see DataScopeContext
 * @see DataScopeResolveFilter
 */
@Slf4j
public class DataPermissionHandlerImpl implements MultiDataPermissionHandler {

    /**
     * 列解析器函数式接口。
     * <p>根据表别名和数据范围信息，生成该表对应的 WHERE 条件表达式。</p>
     */
    @FunctionalInterface
    interface ColumnResolver extends BiFunction<String, DataScopeContext.DataScopeInfo, Expression> {
    }

    /** 表名 -> 过滤规则映射 */
    private final Map<String, ColumnResolver> tableResolvers;

    public DataPermissionHandlerImpl() {
        this.tableResolvers = Map.of(
                "sys_user", this::resolveSysUser,
                "sys_org_unit", this::resolveSysOrgUnit,
                "sys_role", this::resolveSysRole
        );
    }

    /**
     * 针对每个表调用，根据数据范围生成 WHERE 条件。
     *
     * @param table             当前处理的表
     * @param where             原始 WHERE 表达式
     * @param mappedStatementId MyBatis Mapper 方法 ID
     * @return 追加的 WHERE 条件，{@code null} 表示不追加
     */
    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        DataScopeContext.DataScopeInfo info = DataScopeContext.get();
        if (info == null) {
            return null;
        }

        if (table == null || table.getName() == null) {
            return null;
        }

        ColumnResolver resolver = tableResolvers.get(table.getName());
        if (resolver == null) {
            return null;
        }

        String scope = info.getEffectiveScope();
        if (scope == null) {
            return null;
        }

        // ALL / TENANT：不追加额外条件（tenant_id 过滤已满足需求）
        if ("ALL".equals(scope) || "TENANT".equals(scope)) {
            return null;
        }

        String alias = table.getAlias() != null ? table.getAlias().getName() : table.getName();
        return resolver.apply(alias, info);
    }

    // ---- 各表过滤规则 ----

    /**
     * sys_user 过滤规则：
     * <ul>
     *   <li>SELF — {@code id = userId}</li>
     *   <li>DEPT / DEPT_AND_BELOW / CUSTOM — {@code primary_unit_id IN (accessibleUnitIds)}</li>
     * </ul>
     */
    private Expression resolveSysUser(String alias, DataScopeContext.DataScopeInfo info) {
        String scope = info.getEffectiveScope();
        return switch (scope) {
            case "SELF" -> buildEqualsCondition(alias, "id", info.getUserId());
            case "DEPT", "DEPT_AND_BELOW", "CUSTOM" ->
                    buildInCondition(alias, "primary_unit_id", info.getAccessibleUnitIds());
            default -> null;
        };
    }

    /**
     * sys_org_unit 过滤规则：
     * <ul>
     *   <li>SELF — {@code id = primaryUnitId}（只看自己的组织单元）</li>
     *   <li>DEPT / DEPT_AND_BELOW / CUSTOM — {@code id IN (accessibleUnitIds)}</li>
     * </ul>
     */
    private Expression resolveSysOrgUnit(String alias, DataScopeContext.DataScopeInfo info) {
        String scope = info.getEffectiveScope();
        return switch (scope) {
            case "SELF" -> buildEqualsCondition(alias, "id", info.getPrimaryUnitId());
            case "DEPT", "DEPT_AND_BELOW", "CUSTOM" ->
                    buildInCondition(alias, "id", info.getAccessibleUnitIds());
            default -> null;
        };
    }

    /**
     * sys_role 过滤规则：
     * <p>角色为系统级配置，SELF 范围返回不匹配条件（避免泄露），
     * 组织范围不做过滤（角色通常不按组织隔离）。</p>
     */
    private Expression resolveSysRole(String alias, DataScopeContext.DataScopeInfo info) {
        String scope = info.getEffectiveScope();
        return switch (scope) {
            // SELF: 角色是共享资源，SELF 范围不应查看角色列表
            case "SELF" -> buildEqualsCondition(alias, "id", -1L);
            // DEPT / DEPT_AND_BELOW / CUSTOM: 角色不按组织过滤，放行
            case "DEPT", "DEPT_AND_BELOW", "CUSTOM" -> null;
            default -> null;
        };
    }

    // ---- 通用 SQL 构建工具方法 ----

    /**
     * 构建等值条件：{@code alias.column = value}。
     * 当 value 为 null 时返回不匹配任何行的条件 {@code alias.id = -1}。
     */
    private Expression buildEqualsCondition(String alias, String column, Long value) {
        if (value == null) {
            return new EqualsTo(new Column(alias + ".id"), new LongValue(-1));
        }
        EqualsTo equalsTo = new EqualsTo();
        equalsTo.setLeftExpression(new Column(alias + "." + column));
        equalsTo.setRightExpression(new LongValue(value));
        return equalsTo;
    }

    /**
     * 构建 IN 条件：{@code alias.column IN (v1, v2, ...)}。
     * 当集合为空时返回不匹配任何行的条件 {@code alias.id = -1}。
     */
    private Expression buildInCondition(String alias, String column, Set<Long> values) {
        if (values == null || values.isEmpty()) {
            return new EqualsTo(new Column(alias + ".id"), new LongValue(-1));
        }

        ExpressionList<Expression> expressionList = new ExpressionList<>();
        for (Long v : values) {
            expressionList.add(new LongValue(v));
        }

        InExpression inExpression = new InExpression();
        inExpression.setLeftExpression(new Column(alias + "." + column));
        inExpression.setRightExpression(expressionList);
        return inExpression;
    }
}
