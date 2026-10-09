package com.omni.srm.service.impl;

import com.omni.common.core.internal.InternalUserOptionDTO;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import com.omni.srm.client.AuthInternalClient;
import com.omni.srm.dto.SrmViews;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.srm.service.OwnerOptionService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** SRM 负责人候选服务实现。 */
@Service
@RequiredArgsConstructor
public class OwnerOptionServiceImpl implements OwnerOptionService {

    private final AuthInternalClient authInternalClient;

    /** {@inheritDoc} */
    @Override
    public List<SrmViews.OwnerOptionVO> list(String keyword, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        R<List<InternalUserOptionDTO>> response;
        try {
            response = authInternalClient.listOwnerOptions(ServiceIdentityContext.requireTenantId(), keyword, 100);
        } catch (FeignException exception) {
            throw new BusinessException(503, "负责人目录暂时不可用");
        }
        if (response == null || response.getCode() != 200 || response.getData() == null) {
            throw new BusinessException(503, "负责人目录暂时不可用");
        }
        CrmScopeAdapter scope = CrmScopeAdapter.from(ServiceDataScopeContext.require());
        return response.getData().stream().filter(user -> allowed(scope, user)).limit(safeLimit).map(user -> {
            SrmViews.OwnerOptionVO vo = new SrmViews.OwnerOptionVO();
            vo.setUserId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
            vo.setUnitName(user.getPrimaryUnitId() != null ? String.valueOf(user.getPrimaryUnitId()) : null);
            return vo;
        }).toList();
    }

    private boolean allowed(CrmScopeAdapter scope, InternalUserOptionDTO user) {
        return switch (scope.effectiveScope()) {
            case "ALL", "TENANT" -> true;
            case "SELF" -> scope.userId().equals(user.getId());
            case "DEPT" -> scope.primaryUnitId() != null && scope.primaryUnitId().equals(user.getPrimaryUnitId());
            case "DEPT_AND_BELOW", "CUSTOM" -> scope.accessibleUnitIds() != null
                    && user.getPrimaryUnitId() != null && scope.accessibleUnitIds().contains(user.getPrimaryUnitId());
            default -> false;
        };
    }

    private record CrmScopeAdapter(String effectiveScope, Long userId, Long primaryUnitId,
                                    java.util.Set<Long> accessibleUnitIds) {
        static CrmScopeAdapter from(ServiceDataScopeContext.ScopeInfo info) {
            return new CrmScopeAdapter(info.effectiveScope(), info.userId(),
                    info.primaryUnitId(), info.accessibleUnitIds());
        }
    }
}
