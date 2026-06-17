package com.omni.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omni.common.core.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 字典类型实体，对应 {@code sys_dict_type} 表。
 * <p>每种字典类型代表一组枚举值的分类，如"用户性别"、"通用状态"。</p>
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_type")
public class SysDictType extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 字典类型编码（租户内唯一） */
    private String typeCode;

    /** 字典类型名称 */
    private String typeName;

    /** 备注 */
    private String remark;

    /** 排序 */
    private Integer sort;

    /** 状态：1=启用 0=禁用 */
    private Integer status;
}
