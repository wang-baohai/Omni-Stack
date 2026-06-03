package com.omni.common.core.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 基础实体类，包含通用审计字段。
 * <p>
 * 所有业务实体应继承此类，自动获得主键、创建/更新时间、创建/更新人等公共字段。
 * </p>
 */
@Data
public abstract class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    private Long id;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 创建人 */
    private String createBy;
    /** 更新人 */
    private String updateBy;
}
