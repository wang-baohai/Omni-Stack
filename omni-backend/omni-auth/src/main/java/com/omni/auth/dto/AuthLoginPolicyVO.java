package com.omni.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录保护策略视图对象（D01）。
 * <p>无持久化行时返回默认视图：{@code source=DEFAULT}、{@code version=0}，
 * {@code updateTime}/{@code updatedBy} 为 {@code null}。GET 不创建数据。</p>
 *
 * @author Omni-Stack Team
 */
@Data
@Builder
public class AuthLoginPolicyVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 窗口内锁定阈值 */
    private Integer failureThreshold;

    /** 固定失败窗口秒数 */
    private Integer failureWindowSeconds;

    /** 乐观并发版本；默认视图为 0 */
    private Long version;

    /** 配置来源：DEFAULT（内置默认）或 TENANT_OVERRIDE（本租户已保存） */
    private String source;

    /** 更新时间；默认视图为 null */
    private LocalDateTime updateTime;

    /** 最后更新操作者；默认视图为 null */
    private String updatedBy;
}
