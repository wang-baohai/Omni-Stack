package com.omni.base.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建字典数据请求。
 *
 * @author Omni-Stack Team
 */
@Data
public class CreateDictDataRequest {

    /** 字典类型编码 */
    @NotBlank(message = "字典类型编码不能为空")
    private String typeCode;

    /** 字典值 */
    @NotBlank(message = "字典值不能为空")
    private String dictValue;

    /** 字典标签 */
    @NotBlank(message = "字典标签不能为空")
    private String dictLabel;

    /** 标签样式：success/warning/danger/info/primary */
    private String tagType;

    /** 备注 */
    private String remark;

    /** 排序（默认 0） */
    private Integer sort;
}
