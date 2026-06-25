package com.omni.base.dto;

import lombok.Data;

/**
 * 任务类型查询参数。
 *
 * @author Omni-Stack Team
 */
@Data
public class UserJobTypeQuery {

    /** 类型编码（模糊匹配） */
    private String typeCode;

    /** 类型名称（模糊匹配） */
    private String typeName;

    /** 状态过滤 */
    private Integer status;
}
