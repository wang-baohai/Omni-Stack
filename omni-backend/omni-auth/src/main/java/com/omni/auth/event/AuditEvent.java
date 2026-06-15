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
     * 审计事件构建器。
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

        public Builder tenantId(Long tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder createBy(String createBy) {
            this.createBy = createBy;
            return this;
        }

        public Builder extra(String key, Object value) {
            this.extra.put(key, value);
            return this;
        }

        public Builder extra(Map<String, Object> extra) {
            this.extra.putAll(extra);
            return this;
        }

        public AuditEvent build() {
            return new AuditEvent(this);
        }
    }
}
