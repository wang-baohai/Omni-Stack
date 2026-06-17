package com.omni.base.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建字典类型请求。
 *
 * @author Omni-Stack Team
 */
@Data
public class CreateDictTypeRequest {

    /** 字典类型编码 */
    @NotBlank(message = "字典类型编码不能为空")
    private String typeCode;

    /** 字典类型名称 */
    @NotBlank(message = "字典类型名称不能为空")
    private String typeName;

    /** 备注 */
    private String remark;

    /** 排序（默认 0） */
    private Integer sort;
}
