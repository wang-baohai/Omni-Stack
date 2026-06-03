package com.omni.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omni.common.core.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统租户实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_tenant")
public class SysTenant extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户编码 */
    private String tenantCode;
    /** 租户名称 */
    private String tenantName;
    /** 租户域名 */
    private String domain;
    /** 联系人姓名 */
    private String contactName;
    /** 联系人电话 */
    private String contactPhone;
    /** 状态（1-启用，0-禁用） */
    private Integer status;
}
