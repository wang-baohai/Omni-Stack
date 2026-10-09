package com.omni.common.service.client;

import com.omni.common.core.internal.InternalDataScopeDTO;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import com.omni.common.service.datascope.DataScopeResolver;
import com.omni.common.service.identity.ServiceRequestIdentity;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 通过 Auth 内部接口解析权威数据范围的默认实现。
 *
 * @author Omni-Stack Team
 */
@Slf4j
@RequiredArgsConstructor
public class AuthDataScopeResolver implements DataScopeResolver {

    private final AuthSecuritySettingsClient client;

    /** {@inheritDoc} */
    @Override
    public InternalDataScopeDTO resolve(ServiceRequestIdentity identity, String permissionCode) {
        try {
            R<InternalDataScopeDTO> response = client.resolveDataScope(
                    identity.userId(), identity.tenantId(), permissionCode);
            if (response == null || response.getCode() != 200 || response.getData() == null) {
                throw new BusinessException(403, "无法解析当前操作的数据权限");
            }
            return response.getData();
        } catch (FeignException.Forbidden exception) {
            log.warn("Auth 拒绝数据范围解析: userId={}, tenantId={}, permissionCode={}, status={}",
                    identity.userId(), identity.tenantId(), permissionCode, exception.status());
            throw new BusinessException(403, "当前用户不具备该操作的数据权限");
        } catch (FeignException exception) {
            log.warn("Auth 数据范围解析调用失败: userId={}, tenantId={}, permissionCode={}, status={}",
                    identity.userId(), identity.tenantId(), permissionCode, exception.status());
            throw new BusinessException(503, "权限服务暂时不可用");
        }
    }
}
