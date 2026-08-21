package com.omni.common.service.datascope;

import com.omni.common.core.internal.InternalDataScopeDTO;
import com.omni.common.service.identity.ServiceRequestIdentity;

/**
 * 向权威身份服务解析完整权限码对应的数据范围。
 *
 * @author Omni-Stack Team
 */
@FunctionalInterface
public interface DataScopeResolver {

    /**
     * 解析数据范围；失败时必须抛出 403 或 503，禁止扩大为全量范围。
     *
     * @param identity 请求身份
     * @param permissionCode 完整权限码
     * @return 权威数据范围
     */
    InternalDataScopeDTO resolve(ServiceRequestIdentity identity, String permissionCode);
}
