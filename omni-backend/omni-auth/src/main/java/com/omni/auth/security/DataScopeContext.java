package com.omni.auth.security;

import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/**
 * 数据范围上下文（ThreadLocal）。
 * <p>存储当前请求用户的数据范围解析结果，供 MyBatis-Plus
 * {@code DataPermissionInnerInterceptor} 和在线用户内存过滤使用。</p>
 * <p>由 {@link DataScopeResolveFilter} 在每次请求开始时写入，
 * 在 {@code finally} 块中清除，避免 ThreadLocal 泄漏。</p>
 */
public final class DataScopeContext {

    private static final ThreadLocal<DataScopeInfo> CONTEXT = new ThreadLocal<>();

    private DataScopeContext() {
    }

    /**
     * 设置当前线程的数据范围信息。
     *
     * @param info 数据范围信息
     */
    public static void set(DataScopeInfo info) {
        CONTEXT.set(info);
    }

    /**
     * 获取当前线程的数据范围信息。
     *
     * @return 数据范围信息，未设置时返回 {@code null}
     */
    public static DataScopeInfo get() {
        return CONTEXT.get();
    }

    /**
     * 清除当前线程的数据范围信息，防止 ThreadLocal 泄漏。
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /** 数据范围优先级常量（数值越小越宽松） */
    public static final int PRIORITY_ALL = 1;
    public static final int PRIORITY_TENANT = 2;
    public static final int PRIORITY_DEPT_AND_BELOW = 3;
    public static final int PRIORITY_DEPT = 4;
    public static final int PRIORITY_CUSTOM = 5;
    public static final int PRIORITY_SELF = 6;

    /**
     * 根据 dataScope 字符串返回优先级数值。
     *
     * @param scope 数据范围字符串
     * @return 优先级数值，数值越小越宽松
     */
    public static int priorityOf(String scope) {
        if (scope == null) {
            return PRIORITY_SELF;
        }
        return switch (scope) {
            case "ALL" -> PRIORITY_ALL;
            case "TENANT" -> PRIORITY_TENANT;
            case "DEPT_AND_BELOW" -> PRIORITY_DEPT_AND_BELOW;
            case "DEPT" -> PRIORITY_DEPT;
            case "CUSTOM" -> PRIORITY_CUSTOM;
            case "SELF" -> PRIORITY_SELF;
            default -> PRIORITY_SELF;
        };
    }

    /**
     * 数据范围信息，包含当前用户 ID、租户 ID、主组织单元 ID、
     * 合并后的有效数据范围和可访问的组织单元 ID 集合。
     */
    @Getter
    @Setter
    public static class DataScopeInfo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 当前用户 ID */
        private Long userId;

        /** 当前租户 ID */
        private Long tenantId;

        /** 用户主组织单元 ID */
        private Long primaryUnitId;

        /** 合并后的有效数据范围 */
        private String effectiveScope;

        /** 可访问的组织单元 ID 集合（DEPT / DEPT_AND_BELOW / CUSTOM 时使用） */
        private Set<Long> accessibleUnitIds;
    }
}
