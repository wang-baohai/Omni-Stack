package com.omni.auth.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 更新组织单元请求 DTO。
 * <p>所有字段可选，{@code null} 表示不修改该字段。不支持修改 {@code parentId}（防止树结构混乱）。</p>
 *
 * @author Omni-Stack Team
 * @see CreateOrgUnitRequest
 * @see com.omni.auth.entity.SysOrgUnit
 */
@Data
public class UpdateOrgUnitRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 组织单元名称 */
    private String name;

    /** 类型：ORG / SUBSIDIARY / DEPT / TEAM */
    private String type;

    /** 排序值 */
    private Integer sort;

    /** 状态：1-启用, 0-禁用 */
    private Integer status;
}
