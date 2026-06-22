package com.omni.base.service;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志视图对象。
 *
 * @author Omni-Stack Team
 */
@Data
@Builder
public class OperLogVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private String operUsername;
    private LocalDateTime operTime;
    private String module;
    private String operType;
    private String requestMethod;
    private String requestUrl;
    private String requestParams;
    private Integer responseStatus;
    private String ipAddress;
    private String userAgent;
    private Long executionTime;
    private String oldValue;
    private String newValue;
    private String errorMsg;
    private LocalDateTime createTime;
}
