package com.omni.crm.service.impl;

import com.omni.common.core.internal.InternalUserOptionDTO;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import com.omni.crm.client.AuthInternalClient;
import com.omni.crm.dto.CrmViews;
import com.omni.crm.security.CrmDataScopeContext;
import com.omni.crm.security.CrmTenantContext;
import com.omni.crm.service.OwnerOptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import feign.FeignException;

import java.util.List;

/** CRM 负责人候选服务实现。 */
@Service
@RequiredArgsConstructor
public class OwnerOptionServiceImpl implements OwnerOptionService {

    private final AuthInternalClient authInternalClient;

    /** {@inheritDoc} */
    @Override
    public List<CrmViews.OwnerOptionVO> list(String keyword, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        R<List<InternalUserOptionDTO>> response;
        try {
            response = authInternalClient.listOwnerOptions(CrmTenantContext.requireTenantId(), keyword, 100);
        } catch (FeignException exception) {
            throw new BusinessException(503, "负责人目录暂时不可用");
        }
        if (response == null || response.getCode() != 200 || response.getData() == null) {
            throw new BusinessException(503, "负责人目录暂时不可用");
        }
        CrmDataScopeContext.ScopeInfo scope = CrmDataScopeContext.require();
        return response.getData().stream().filter(user -> allowed(scope, user)).limit(safeLimit).map(user -> {
            CrmViews.OwnerOptionVO vo = new CrmViews.OwnerOptionVO(); vo.setId(user.getId()); vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname()); vo.setPrimaryUnitId(user.getPrimaryUnitId()); vo.setAvatar(user.getAvatar()); return vo;
        }).toList();
    }

    private boolean allowed(CrmDataScopeContext.ScopeInfo scope, InternalUserOptionDTO user) {
        return switch (scope.effectiveScope()) {
            case "ALL", "TENANT" -> true;
            case "SELF" -> scope.userId().equals(user.getId());
            case "DEPT" -> scope.primaryUnitId() != null && scope.primaryUnitId().equals(user.getPrimaryUnitId());
            case "DEPT_AND_BELOW", "CUSTOM" -> scope.accessibleUnitIds() != null
                    && user.getPrimaryUnitId() != null && scope.accessibleUnitIds().contains(user.getPrimaryUnitId());
            default -> false;
        };
    }
}
