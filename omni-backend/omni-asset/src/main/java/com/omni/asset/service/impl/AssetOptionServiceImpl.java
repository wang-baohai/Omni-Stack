package com.omni.asset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.asset.client.AuthInternalClient;
import com.omni.asset.client.SrmInternalClient;
import com.omni.asset.domain.AssetStateMachine;
import com.omni.asset.dto.AssetViews;
import com.omni.asset.entity.AstAsset;
import com.omni.asset.mapper.AstAssetMapper;
import com.omni.asset.security.AssetTenantContext;
import com.omni.asset.service.AssetOptionService;
import com.omni.common.core.internal.InternalUserOptionDTO;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/** 资产操作候选项服务实现。 */
@Service
@RequiredArgsConstructor
public class AssetOptionServiceImpl implements AssetOptionService {

    private final AuthInternalClient authInternalClient;
    private final AstAssetMapper assetMapper;
    private final SrmInternalClient srmInternalClient;

    /** {@inheritDoc} */
    @Override
    public List<AssetViews.UserOptionVO> listUsers(String keyword, int limit) {
        requireLimit(limit);
        Long tenantId = AssetTenantContext.requireTenantId();
        R<List<InternalUserOptionDTO>> response;
        try {
            response = authInternalClient.listUserOptions(tenantId, keyword, limit);
        } catch (FeignException exception) {
            throw new BusinessException(503, "认证授权服务暂不可用");
        }
        if (response == null || response.getCode() != 200 || response.getData() == null) {
            throw new BusinessException(503, "认证授权服务返回了无效的用户候选响应");
        }
        return response.getData().stream()
                // Portal 或社交登录用户可能尚未归属组织，不能作为资产责任人。
                .filter(user -> user == null || user.getPrimaryUnitId() != null)
                .map(user -> toView(user, tenantId))
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<AssetViews.AssetOptionVO> listEligibleAssets(String keyword, int limit) {
        requireLimit(limit);
        Long tenantId = AssetTenantContext.requireTenantId();
        String trimmedKeyword = keyword == null ? null : keyword.trim();
        String normalizedKeyword = trimmedKeyword == null || trimmedKeyword.isEmpty()
                ? null : trimmedKeyword;
        List<AstAsset> assets = assetMapper.selectList(new LambdaQueryWrapper<AstAsset>()
                .eq(AstAsset::getTenantId, tenantId)
                .eq(AstAsset::getDeleted, 0)
                .isNull(AstAsset::getActiveOperationId)
                .isNull(AstAsset::getActiveOperationType)
                .in(AstAsset::getStatus, Set.of(
                        AssetStateMachine.IN_STOCK,
                        AssetStateMachine.ALLOCATED,
                        AssetStateMachine.IN_USE))
                .and(normalizedKeyword != null, query -> query
                        .like(AstAsset::getAssetNo, normalizedKeyword)
                        .or()
                        .like(AstAsset::getName, normalizedKeyword))
                .orderByDesc(AstAsset::getUpdateTime)
                .last("LIMIT " + limit));
        return assets.stream().map(this::toAssetView).toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<AssetViews.SupplierOptionVO> listSuppliers(String keyword, int limit) {
        requireLimit(limit);
        Long tenantId = AssetTenantContext.requireTenantId();
        try {
            R<List<AssetViews.SupplierOptionVO>> response = srmInternalClient.search(
                    tenantId, tenantId, "APPROVED", keyword, limit);
            if (response == null || response.getCode() != 200 || response.getData() == null) {
                throw new BusinessException(503, "供应商候选服务返回了无效响应");
            }
            return response.getData();
        } catch (FeignException exception) {
            throw new BusinessException(503, "供应商候选服务暂不可用");
        }
    }

    private void requireLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new BusinessException(400, "limit 必须在 1 到 100 之间");
        }
    }

    private AssetViews.UserOptionVO toView(InternalUserOptionDTO user, Long tenantId) {
        if (user == null || user.getId() == null || user.getPrimaryUnitId() == null
                || !tenantId.equals(user.getTenantId())) {
            throw new BusinessException(503, "认证授权服务返回了不一致的用户候选");
        }
        AssetViews.UserOptionVO view = new AssetViews.UserOptionVO();
        view.setId(user.getId());
        view.setUsername(user.getUsername());
        view.setNickname(user.getNickname());
        view.setPrimaryUnitId(user.getPrimaryUnitId());
        view.setAvatar(user.getAvatar());
        return view;
    }

    private AssetViews.AssetOptionVO toAssetView(AstAsset asset) {
        AssetViews.AssetOptionVO view = new AssetViews.AssetOptionVO();
        view.setId(asset.getId());
        view.setAssetNo(asset.getAssetNo());
        view.setName(asset.getName());
        view.setStatus(asset.getStatus());
        view.setCurrentUserId(asset.getCurrentUserId());
        view.setCurrentUnitId(asset.getCurrentUnitId());
        return view;
    }
}
