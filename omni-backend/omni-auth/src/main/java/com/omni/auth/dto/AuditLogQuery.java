package com.omni.auth.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计日志查询参数 DTO。
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
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 页码（默认 1） */
    private int page = 1;

    /** 每页大小（默认 10） */
    private int size = 10;
}
