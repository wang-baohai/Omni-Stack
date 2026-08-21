package com.omni.common.service.persistence;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;

/**
 * 领域表数据权限 SQL 表达式策略。
 *
 * @author Omni-Stack Team
 */
@FunctionalInterface
public interface DataScopeTablePolicy {

    /**
     * 为目标表生成数据权限表达式。
     *
     * @param table 表
     * @param where 当前 WHERE 表达式
     * @param mappedStatementId Mapper 方法标识
     * @return 追加的数据权限表达式；不处理时返回 null
     */
    Expression getSqlSegment(Table table, Expression where, String mappedStatementId);
}
