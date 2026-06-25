package com.omni.base.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建任务类型请求。
 *
 * @author Omni-Stack Team
 */
@Data
public class CreateUserJobTypeRequest {

    /** 类型编码 */
    @NotBlank(message = "类型编码不能为空")
    private String typeCode;

    /** 类型名称 */
    @NotBlank(message = "类型名称不能为空")
    private String typeName;

    /** 描述 */
    private String description;

    /** 参数模板 JSON Schema */
    private String paramTemplate;
}
