package com.omni.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omni.common.core.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 字典数据实体，对应 {@code sys_dict_data} 表。
 * <p>每条记录代表某个字典类型下的一个枚举值，如"性别"类型下的"男"、"女"。</p>
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_data")
public class SysDictData extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 字典类型编码 */
    private String typeCode;

    /** 字典值（后端存储值） */
    private String dictValue;

    /** 字典标签（前端显示值） */
    private String dictLabel;

    /** 标签样式：success/warning/danger/info/primary */
    private String tagType;

    /** 备注 */
    private String remark;

    /** 排序 */
    private Integer sort;

    /** 状态：1=启用 0=禁用 */
    private Integer status;
}
