package com.omni.common.core.operlog;

/**
 * 操作类型枚举。
 *
 * @author Omni-Stack Team
 */
public enum OperType {

    /** 新增 */
    CREATE,

    /** 修改 */
    UPDATE,

    /** 删除 */
    DELETE,

    /** 查询 */
    QUERY,

    /** 导出 */
    EXPORT,

    /** 导入 */
    IMPORT
}
