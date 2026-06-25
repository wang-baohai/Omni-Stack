package com.omni.auth.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;
import java.util.HashMap;
import java.util.Map;

/**
 * 安全审计事件，通过 Spring {@link ApplicationEvent} 机制发布。
 *
 * <p>各登录/管理入口发布此事件，由 {@link AuditEventListener} 异步写入数据库。</p>
 *
 * <p>采用 Builder 模式构建实例，支持记录租户 ID、用户信息、客户端 IP、
 * User-Agent 等上下文，同时提供 {@code extra} 字段用于扩展属性。
 * 事件类型通过静态常量定义，涵盖登录成功/失败、账号锁定、密码变更、
 * 角色分配等关键安全事件。</p>
 *
 * @author Omni-Stack Team
 * @see AuditEventListener
 * @see ApplicationEvent
 */
@Getter
public class AuditEvent extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    // ========== 事件类型常量 ==========

    /** 登录成功 */
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    /** 登录失败 */
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    /** 退出登录（管理员踢人） */
    public static final String LOGOUT = "LOGOUT";
    /** 账号锁定 */
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    /** 账号解锁 */
    public static final String ACCOUNT_UNLOCKED = "ACCOUNT_UNLOCKED";
    /** 密码变更 */
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    /** 用户创建 */
    public static final String USER_CREATED = "USER_CREATED";
    /** 用户删除 */
    public static final String USER_DELETED = "USER_DELETED";
    /** 用户状态变更 */
    public static final String USER_STATUS_CHANGED = "USER_STATUS_CHANGED";
    /** 角色分配 */
    public static final String ROLE_ASSIGNED = "ROLE_ASSIGNED";
    /** 角色撤销 */
    public static final String ROLE_REVOKED = "ROLE_REVOKED";

    /** 事件类型 */
    private final String eventType;
    /** 租户ID */
    private final Long tenantId;
    /** 操作目标用户ID */
    private final Long userId;
    /** 操作目标用户名 */
    private final String username;
    /** 客户端IP地址 */
    private final String ipAddress;
    /** 客户端User-Agent */
    private final String userAgent;
    /** 事件描述 */
    private final String description;
    /** 操作人（用户名或system） */
    private final String createBy;
    /** 事件扩展字段 */
    private final Map<String, Object> extra;

    private AuditEvent(Builder builder) {
        super(builder);
        this.eventType = builder.eventType;
        this.tenantId = builder.tenantId;
        this.userId = builder.userId;
        this.username = builder.username;
        this.ipAddress = builder.ipAddress;
        this.userAgent = builder.userAgent;
        this.description = builder.description;
        this.createBy = builder.createBy;
        this.extra = builder.extra;
    }

    /**
     * 创建审计事件构建器。
     *
     * @param eventType 事件类型常量
     * @return 构建器实例
     */
    public static Builder of(String eventType) {
        return new Builder(eventType);
    }

    /**
     * 审计事件构建器，支持链式调用设置事件各属性。
     */
    public static class Builder {
        private final String eventType;
        private Long tenantId;
        private Long userId;
        private String username;
        private String ipAddress;
        private String userAgent;
        private String description;
        private String createBy;
        private Map<String, Object> extra = new HashMap<>();

        private Builder(String eventType) {
            this.eventType = eventType;
        }

        /**
         * 设置租户 ID。
         *
         * @param tenantId 租户 ID
         * @return 当前构建器实例
         */
        public Builder tenantId(Long tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        /**
         * 设置操作目标用户 ID。
         *
         * @param userId 操作目标用户 ID
         * @return 当前构建器实例
         */
        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        /**
         * 设置操作目标用户名。
         *
         * @param username 操作目标用户名
         * @return 当前构建器实例
         */
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        /**
         * 设置客户端 IP 地址。
         *
         * @param ipAddress 客户端 IP 地址
         * @return 当前构建器实例
         */
        public Builder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        /**
         * 设置客户端 User-Agent 信息。
         *
         * @param userAgent 客户端 User-Agent 字符串
         * @return 当前构建器实例
         */
        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        /**
         * 设置事件描述信息。
         *
         * @param description 事件描述
         * @return 当前构建器实例
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * 设置操作人（用户名或 {@code "system"}）。
         *
         * @param createBy 操作人标识
         * @return 当前构建器实例
         */
        public Builder createBy(String createBy) {
            this.createBy = createBy;
            return this;
        }

        /**
         * 添加单个扩展字段。
         *
         * @param key   扩展字段键名
         * @param value 扩展字段值
         * @return 当前构建器实例
         */
        public Builder extra(String key, Object value) {
            this.extra.put(key, value);
            return this;
        }

        /**
         * 批量添加扩展字段。
         *
         * @param extra 扩展字段 Map，会被合并到现有扩展字段中
         * @return 当前构建器实例
         */
        public Builder extra(Map<String, Object> extra) {
            this.extra.putAll(extra);
            return this;
        }

        /**
         * 构建并返回 {@link AuditEvent} 实例。
         *
         * @return 构建完成的审计事件实例
         */
        public AuditEvent build() {
            return new AuditEvent(this);
        }
    }
}
