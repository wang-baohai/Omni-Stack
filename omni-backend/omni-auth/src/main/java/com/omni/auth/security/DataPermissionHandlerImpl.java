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

import java.util.Set;

/**
 * MyBatis-Plus 数据权限处理器实现。
 * <p>根据 {@link DataScopeContext} 中存储的当前用户数据范围信息，
 * 为 {@code sys_user} 表的查询自动追加 WHERE 条件。</p>
 * <p>实现 {@link MultiDataPermissionHandler} 以获得表级感知能力，
 * 仅作用于 {@code sys_user} 表，不影响其他表的查询。
 * 当 DataScopeContext 为空时（如登录请求），不添加任何过滤条件。</p>
 */
@Slf4j
public class DataPermissionHandlerImpl implements MultiDataPermissionHandler {

    /** 需要应用数据权限过滤的表名 */
    private static final String TARGET_TABLE = "sys_user";

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
        // 无上下文（如登录、公开接口）时不过滤
        if (info == null) {
            return null;
        }

        // 仅过滤 sys_user 表
        if (table == null || !TARGET_TABLE.equals(table.getName())) {
            return null;
        }

        String scope = info.getEffectiveScope();
        if (scope == null) {
            return null;
        }

        return switch (scope) {
            // ALL / TENANT：不追加额外条件（现有 tenant_id 过滤已满足需求）
            case "ALL", "TENANT" -> null;
            // SELF：仅查看自己的数据
            case "SELF" -> buildSelfCondition(info.getUserId());
            // DEPT / DEPT_AND_BELOW / CUSTOM：按可访问的组织单元过滤
            case "DEPT", "DEPT_AND_BELOW", "CUSTOM" -> buildUnitCondition(info.getAccessibleUnitIds());
            default -> null;
        };
    }

    /**
     * 构建 SELF 范围的 WHERE 条件：{@code sys_user.id = {userId}}。
     *
     * @param userId 当前用户 ID
     * @return SQL 表达式
     */
    private Expression buildSelfCondition(Long userId) {
        if (userId == null) {
            return new EqualsTo(new Column(TARGET_TABLE + ".id"), new LongValue(-1));
        }
        EqualsTo equalsTo = new EqualsTo();
        equalsTo.setLeftExpression(new Column(TARGET_TABLE + ".id"));
        equalsTo.setRightExpression(new LongValue(userId));
        return equalsTo;
    }

    /**
     * 构建组织单元范围的 WHERE 条件：{@code sys_user.primary_unit_id IN (id1, id2, ...)}。
     *
     * @param accessibleUnitIds 可访问的组织单元 ID 集合
     * @return SQL 表达式
     */
    private Expression buildUnitCondition(Set<Long> accessibleUnitIds) {
        if (accessibleUnitIds == null || accessibleUnitIds.isEmpty()) {
            // 无可访问单元时返回不匹配任何行的条件
            return new EqualsTo(new Column(TARGET_TABLE + ".id"), new LongValue(-1));
        }

        ExpressionList<Expression> expressionList = new ExpressionList<>();
        for (Long unitId : accessibleUnitIds) {
            expressionList.add(new LongValue(unitId));
        }

        InExpression inExpression = new InExpression();
        inExpression.setLeftExpression(new Column(TARGET_TABLE + ".primary_unit_id"));
        inExpression.setRightExpression(expressionList);
        return inExpression;
    }
}
