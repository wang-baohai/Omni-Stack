package com.omni.auth.service;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 审计日志视图对象，用于 API 响应。
 */
@Data
@Builder
public class AuditLogVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 审计日志ID */
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 事件类型 */
    private String eventType;

    /** 操作目标用户名 */
    private String username;

    /** 操作目标用户ID */
    private Long userId;

    /** 客户端IP地址 */
    private String ipAddress;

    /** 客户端User-Agent */
    private String userAgent;

    /** 事件描述 */
    private String description;

    /** 事件扩展字段 */
    private Map<String, Object> extra;

    /** 操作人 */
    private String createBy;

    /** 事件发生时间 */
    private LocalDateTime createTime;
}
