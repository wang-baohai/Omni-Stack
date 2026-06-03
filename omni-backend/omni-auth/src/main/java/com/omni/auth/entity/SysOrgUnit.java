package com.omni.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omni.common.core.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 组织单元实体，使用物化路径（Materialized Path）实现层级结构。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_org_unit")
public class SysOrgUnit extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;
    /** 父节点 ID */
    private Long parentId;
    /** 组织单元名称 */
    private String name;
    /** 组织单元类型 */
    private String type;
    /** 物化路径（如 /1/2/3/） */
    private String path;
    /** 树深度 */
    private Integer depth;
    /** 排序号 */
    private Integer sort;
    /** 状态（1-启用，0-禁用） */
    private Integer status;
}
