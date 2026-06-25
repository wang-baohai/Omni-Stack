package com.omni.base.dto;

import lombok.Data;

/**
 * 更新任务类型请求。
 *
 * @author Omni-Stack Team
 */
@Data
public class UpdateUserJobTypeRequest {

    /** 类型名称 */
    private String typeName;

    /** 描述 */
    private String description;

    /** 参数模板 JSON Schema */
    private String paramTemplate;
}
