package com.omni.base.dto;

import lombok.Data;

/**
 * 更新字典数据请求（所有字段可选）。
 *
 * @author Omni-Stack Team
 */
@Data
public class UpdateDictDataRequest {

    /** 字典值 */
    private String dictValue;

    /** 字典标签 */
    private String dictLabel;

    /** 标签样式 */
    private String tagType;

    /** 备注 */
    private String remark;

    /** 排序 */
    private Integer sort;

    /** 状态：1=启用 0=禁用 */
    private Integer status;
}
