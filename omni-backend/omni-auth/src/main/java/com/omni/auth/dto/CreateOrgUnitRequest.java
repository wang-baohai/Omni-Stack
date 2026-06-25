package com.omni.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 创建组织单元请求 DTO。
 * <p>用于新增公司/部门/小组等组织节点，必须指定父节点 ID 和类型。</p>
 *
 * @author Omni-Stack Team
 * @see UpdateOrgUnitRequest
 * @see com.omni.auth.entity.SysOrgUnit
 */
@Data
public class CreateOrgUnitRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 父级组织单元 ID（根节点传 0） */
    @NotNull(message = "父级 ID 不能为空")
    private Long parentId;

    /** 组织单元名称 */
    @NotBlank(message = "名称不能为空")
    private String name;

    /** 类型：ORG / SUBSIDIARY / DEPT / TEAM */
    @NotBlank(message = "类型不能为空")
    private String type;

    /** 排序值 */
    private Integer sort;

    /** 状态：1-启用, 0-禁用 */
    private Integer status;
}
