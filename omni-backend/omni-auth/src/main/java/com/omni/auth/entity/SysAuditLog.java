package com.omni.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 安全审计日志实体，映射 {@code sys_audit_log} 表。
 * <p>
 * 追加写入、不可变记录，不继承 {@code BaseEntity}（无 update_time/update_by）。
 * 用于记录用户登录、权限变更、账号锁定等安全相关事件，
 * 为安全审计和异常行为分析提供数据支撑。</p>
 *
 * <p>常见事件类型：{@code LOGIN_SUCCESS}、{@code LOGIN_FAILED}、{@code ACCOUNT_LOCKED}、
 * {@code PASSWORD_CHANGED}、{@code ROLE_ASSIGNED} 等。</p>
 *
 * @author Omni-Stack Team
 * @see com.omni.common.core.model.BaseEntity
 */
@Data
@TableName("sys_audit_log")
public class SysAuditLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 审计日志ID */
    @TableId(type = IdType.AUTO)
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

    /** 事件扩展字段（JSON） */
    private String extra;

    /** 操作人（用户名或system） */
    private String createBy;

    /** 事件发生时间 */
    private LocalDateTime createTime;
}
