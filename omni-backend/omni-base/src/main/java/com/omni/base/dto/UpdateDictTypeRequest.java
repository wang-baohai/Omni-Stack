package com.omni.base.dto;

import lombok.Data;

/**
 * 更新字典类型请求（所有字段可选）。
 *
 * @author Omni-Stack Team
 */
@Data
public class UpdateDictTypeRequest {

    /** 字典类型名称 */
    private String typeName;

    /** 备注 */
    private String remark;

    /** 排序 */
    private Integer sort;

    /** 状态：1=启用 0=禁用 */
    private Integer status;
}
