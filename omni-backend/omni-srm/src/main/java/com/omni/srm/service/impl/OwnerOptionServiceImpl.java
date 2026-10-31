package com.omni.srm.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.omni.common.core.internal.InternalUserOptionDTO;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import com.omni.srm.client.AuthInternalClient;
import com.omni.srm.entity.SrmSupplierPortalUser;
import com.omni.srm.mapper.SrmSupplierPortalUserMapper;
import com.omni.srm.dto.SrmViews;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.srm.service.OwnerOptionService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** SRM 负责人候选服务实现。 */
@Service
@RequiredArgsConstructor
public class OwnerOptionServiceImpl implements OwnerOptionService {

    private final AuthInternalClient authInternalClient;
    private final SrmSupplierPortalUserMapper portalUserMapper;

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
        List<InternalUserOptionDTO> candidates = response.getData().stream()
                .filter(user -> allowed(scope, user)).toList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        Set<Long> portalUserIds = portalUserMapper.selectList(Wrappers.<SrmSupplierPortalUser>lambdaQuery()
                        .select(SrmSupplierPortalUser::getUserId)
                        .eq(SrmSupplierPortalUser::getTenantId, ServiceIdentityContext.requireTenantId())
                        .eq(SrmSupplierPortalUser::getDeleted, 0)
                        .in(SrmSupplierPortalUser::getUserId, candidates.stream().map(InternalUserOptionDTO::getId).toList()))
                .stream().map(SrmSupplierPortalUser::getUserId).collect(Collectors.toSet());
        return candidates.stream().filter(user -> !portalUserIds.contains(user.getId())).limit(safeLimit).map(user -> {
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
