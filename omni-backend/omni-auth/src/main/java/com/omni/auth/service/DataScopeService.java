package com.omni.auth.service;

import com.omni.common.core.internal.InternalDataScopeDTO;

/**
 * 数据权限范围解析服务。
 * <p>统一承载认证服务请求过滤和跨服务内部接口的数据范围解析规则。</p>
 *
 * @author Omni-Stack Team
 */
public interface DataScopeService {

    /**
     * 根据用户在租户内的全部启用角色解析请求级数据范围。
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @return 数据权限范围
     */
    InternalDataScopeDTO resolveDataScope(Long userId, Long tenantId);

    /**
     * 根据真正授予完整权限码的角色解析数据范围。
     *
     * @param userId         用户 ID
     * @param tenantId       租户 ID
     * @param permissionCode 完整权限码
     * @return 精确到权限码的数据权限范围
     */
    InternalDataScopeDTO resolveDataScope(Long userId, Long tenantId, String permissionCode);
}
