package com.omni.common.core.operlog;

/**
 * 操作类型枚举。
 * <p>定义操作日志支持的六种操作类型，用于 {@link OperLog#operType()} 属性。
 * 其中 CREATE/UPDATE/DELETE 会触发实体变更快照采集，
 * QUERY/EXPORT/IMPORT 仅记录请求上下文。</p>
 *
 * @author Omni-Stack Team
 * @see OperLog
 */
public enum OperType {

    /** 新增：触发实体变更快照采集，记录新记录的字段值 */
    CREATE,

    /** 修改：触发实体变更快照采集，记录修改前后的字段差异（oldValue/newValue） */
    UPDATE,

    /** 删除：触发实体变更快照采集，记录被删除记录的完整字段值 */
    DELETE,

    /** 查询：仅记录请求上下文（URL、参数、响应状态），不采集变更快照 */
    QUERY,

    /** 导出：仅记录请求上下文，通常用于数据导出操作审计 */
    EXPORT,

    /** 导入：仅记录请求上下文，通常用于数据导入操作审计 */
    IMPORT
}
