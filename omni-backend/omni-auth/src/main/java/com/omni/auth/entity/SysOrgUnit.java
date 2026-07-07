package com.omni.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omni.common.core.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 组织单元实体，映射 {@code sys_org_unit} 表。
 * <p>
 * 使用物化路径（Materialized Path）实现层级结构，
 * 支持公司/部门/小组等多级组织架构。
 * {@code path} 字段存储形如 {@code /1/2/3/} 的路径，
 * 用于高效查询子树和判断祖先关系。</p>
 *
 * @author Omni-Stack Team
 * @see com.omni.common.core.model.BaseEntity
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
    /** 单元编码（同父节点下唯一） */
    private String unitCode;
    /** 物化路径（如 /1/2/3/） */
    private String path;
    /** 树深度 */
    private Integer depth;
    /** 排序号 */
    private Integer sort;
    /** 状态（1-启用，0-禁用） */
    private Integer status;
}
