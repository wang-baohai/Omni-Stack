package com.omni.asset.service.support;

import com.omni.asset.client.AuthInternalClient;
import com.omni.common.core.internal.InternalOrgDTO;
import com.omni.common.core.internal.InternalUserDTO;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 资产用户与组织引用完整性守卫。
 *
 * @author Omni-Stack Team
 */
@Component
@RequiredArgsConstructor
public class AssetIdentityGuard {

    private final AuthInternalClient authInternalClient;

    /**
     * 校验用户和组织均属于当前租户、处于启用状态，且用户主组织与目标组织一致。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param unitId 组织 ID
     */
    public void requireActiveUserInUnit(Long tenantId, Long userId, Long unitId) {
        try {
            InternalUserDTO user = requireUser(authInternalClient.getUser(userId, tenantId));
            InternalOrgDTO org = requireOrg(authInternalClient.getOrg(unitId, tenantId));
            if (!userId.equals(user.getId()) || !unitId.equals(org.getId())) {
                throw new BusinessException(503, "Auth 返回了不一致的用户或组织");
            }
            if (!tenantId.equals(user.getTenantId()) || !tenantId.equals(org.getTenantId())) {
                throw new BusinessException(403, "Auth 返回了跨租户用户或组织");
            }
            if (!Integer.valueOf(1).equals(user.getStatus())
                    || !Integer.valueOf(1).equals(org.getStatus())) {
                throw new BusinessException(409, "目标用户或组织已停用");
            }
            if (!Objects.equals(user.getPrimaryUnitId(), unitId)) {
                throw new BusinessException(409, "目标用户不属于指定组织");
            }
        } catch (FeignException exception) {
            throw new BusinessException(503, "认证授权服务暂不可用");
        }
    }

    private InternalUserDTO requireUser(R<InternalUserDTO> response) {
        if (response != null && response.getCode() == 200 && response.getData() != null) {
            return response.getData();
        }
        if (response != null && response.getCode() == 404) {
            throw new BusinessException(404, "目标用户不存在");
        }
        throw new BusinessException(503, "认证授权服务暂不可用");
    }

    private InternalOrgDTO requireOrg(R<InternalOrgDTO> response) {
        if (response != null && response.getCode() == 200 && response.getData() != null) {
            return response.getData();
        }
        if (response != null && response.getCode() == 404) {
            throw new BusinessException(404, "目标组织不存在");
        }
        throw new BusinessException(503, "认证授权服务暂不可用");
    }
}
