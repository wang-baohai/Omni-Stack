package com.omni.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omni.common.core.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统租户实体，映射 {@code sys_tenant} 表。
 * <p>
 * 多租户架构的核心实体，每个租户拥有独立的用户、角色、权限和数据空间。
 * {@code tenantCode} 为租户唯一标识，{@code domain} 可用于自定义域名绑定。</p>
 *
 * @author Omni-Stack Team
 * @see SysUser
 * @see com.omni.common.core.model.BaseEntity
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
    /** 初始化状态，与业务启停状态相互独立。 */
    private TenantProvisionStatusEnum provisioningStatus;
    /** 最近一次初始化请求 ID。 */
    private String provisioningRequestId;
    /** 最近一次脱敏失败摘要。 */
    private String provisioningError;
}
