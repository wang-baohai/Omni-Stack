package com.omni.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录记录查询参数（D02）。
 * <p>服务端强制可信租户与固定事件集合 {@code LOGIN_SUCCESS/LOGIN_FAILED}，
 * 客户端不得覆盖 eventType/tenantId。默认近 7 天；单次跨度不超过 90 天，起止成对且 start≤end。</p>
 *
 * @author Omni-Stack Team
 */
@Data
public class LoginRecordQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 页码（≥1，默认 1） */
    @Min(value = 1, message = "page 必须≥1")
    private int page = 1;

    /** 每页大小（1~100，默认 20） */
    @Min(value = 1, message = "size 必须在 1~100")
    @Max(value = 100, message = "size 必须在 1~100")
    private int size = 20;

    /** 用户名模糊过滤（≤64） */
    private String username;

    /** 结果过滤：SUCCESS | FAILURE */
    private String result;

    /** 方式过滤：PASSWORD | SESSION | SOCIAL | UNKNOWN */
    private String method;

    /** 起始时间（可选，需与结束时间成对） */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 结束时间（可选，需与起始时间成对） */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
