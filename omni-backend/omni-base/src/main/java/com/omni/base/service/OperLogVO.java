package com.omni.base.service;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志视图对象。
 * <p>用于 {@link com.omni.base.controller.OperLogController} 返回给前端的脱敏数据，
 * 由 {@link com.omni.base.service.impl.OperLogServiceImpl} 从 {@link com.omni.base.entity.SysOperLog} 转换而来。</p>
 *
 * @author Omni-Stack Team
 * @see com.omni.base.entity.SysOperLog
 */
@Data
@Builder
public class OperLogVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 操作人用户名 */
    private String operUsername;

    /** 操作时间 */
    private LocalDateTime operTime;

    /** 模块名称 */
    private String module;

    /** 操作类型（CREATE/UPDATE/DELETE/QUERY 等） */
    private String operType;

    /** HTTP 请求方法（GET/POST/PUT/DELETE） */
    private String requestMethod;

    /** 请求 URL */
    private String requestUrl;

    /** 请求参数（JSON） */
    private String requestParams;

    /** 响应状态码 */
    private Integer responseStatus;

    /** 客户端 IP 地址 */
    private String ipAddress;

    /** 客户端 User-Agent */
    private String userAgent;

    /** 执行耗时（毫秒） */
    private Long executionTime;

    /** 变更前值（JSON，仅 UPDATE/DELETE 有值） */
    private String oldValue;

    /** 变更后值（JSON，仅 CREATE/UPDATE 有值） */
    private String newValue;

    /** 异常信息（仅发生异常时有值） */
    private String errorMsg;

    /** 记录创建时间 */
    private LocalDateTime createTime;
}
