package com.omni.common.core.internal;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/**
 * 内部 API 数据权限范围 DTO。
 * <p>
 * 由认证服务根据用户、租户和完整功能权限码解析，供业务微服务实施行级数据权限。
 * {@code securityVersion} 是当前授权结果的稳定指纹，可用于识别权限关系变化，
 * 业务服务不得自行扩大本 DTO 描述的数据范围。
 * </p>
 *
 * @author Omni-Stack Team
 */
@Data
public class InternalDataScopeDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long userId;

    /** 租户 ID */
    private Long tenantId;

    /** 本次解析对应的完整权限码；普通请求级解析时为空 */
    private String permissionCode;

    /** 用户主组织单元 ID */
    private Long primaryUnitId;

    /** 合并后的有效数据范围 */
    private String effectiveScope;

    /** 可访问组织单元 ID 集合 */
    private Set<Long> accessibleUnitIds;

    /** 当前授权结果的稳定版本指纹 */
    private Long securityVersion;
}
