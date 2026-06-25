package com.omni.auth.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

/**
 * 审计日志查询参数 DTO。
 * <p>用于审计日志管理页面的分页查询，支持按事件类型、用户名、时间范围筛选。</p>
 *
 * @author Omni-Stack Team
 * @see com.omni.auth.entity.SysAuditLog
 */
@Data
public class AuditLogQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件类型筛选 */
    private String eventType;

    /** 用户名模糊搜索 */
    private String username;

    /** 开始时间 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 结束时间 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 页码（默认 1） */
    private int page = 1;

    /** 每页大小（默认 10） */
    private int size = 10;
}
