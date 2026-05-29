package com.omni.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omni.common.core.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Organization unit entity with materialized path.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_org_unit")
public class SysOrgUnit extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long parentId;
    private String name;
    private String type;
    private String path;
    private Integer depth;
    private Integer sort;
    private Integer status;
}
