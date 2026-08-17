package com.omni.asset.dto;

import com.omni.asset.entity.AstAsset;
import com.omni.asset.entity.AstAssetHistory;

/**
 * 资产实体与响应视图转换器。
 *
 * @author Omni-Stack Team
 */
public final class AssetViewAssembler {

    private AssetViewAssembler() {
    }

    /**
     * 转换资产视图。
     *
     * @param asset 资产实体
     * @return 资产视图
     */
    public static AssetViews.AssetVO asset(AstAsset asset) {
        AssetViews.AssetVO view = new AssetViews.AssetVO();
        view.setId(asset.getId());
        view.setAssetNo(asset.getAssetNo());
        view.setName(asset.getName());
        view.setCategoryCode(asset.getCategoryCode());
        view.setSpecification(asset.getSpecification());
        view.setBrand(asset.getBrand());
        view.setModel(asset.getModel());
        view.setSupplierId(asset.getSupplierId());
        view.setSupplierNameSnapshot(asset.getSupplierNameSnapshot());
        view.setSourcePoId(asset.getSourcePoId());
        view.setSourceGrId(asset.getSourceGrId());
        view.setSourceGrLineId(asset.getSourceGrLineId());
        view.setSourceUnitSequence(asset.getSourceUnitSequence());
        view.setSourcePoNo(asset.getSourcePoNo());
        view.setSourceGrNo(asset.getSourceGrNo());
        view.setPurchaseDate(asset.getPurchaseDate());
        view.setPurchaseAmount(asset.getPurchaseAmount());
        view.setCurrencyCode(asset.getCurrencyCode());
        view.setLocationCode(asset.getLocationCode());
        view.setStatus(asset.getStatus());
        view.setCurrentUserId(asset.getCurrentUserId());
        view.setCurrentUnitId(asset.getCurrentUnitId());
        view.setAllocatedTime(asset.getAllocatedTime());
        view.setActiveOperationType(asset.getActiveOperationType());
        view.setActiveOperationId(asset.getActiveOperationId());
        view.setWarrantyExpiryDate(asset.getWarrantyExpiryDate());
        view.setExpectedLifeYears(asset.getExpectedLifeYears());
        view.setRemark(asset.getRemark());
        view.setOwnerUserId(asset.getOwnerUserId());
        view.setOwnerUnitId(asset.getOwnerUnitId());
        view.setVersion(asset.getVersion());
        view.setCreateTime(asset.getCreateTime());
        view.setUpdateTime(asset.getUpdateTime());
        return view;
    }

    /**
     * 转换资产历史视图。
     *
     * @param history 历史实体
     * @return 历史视图
     */
    public static AssetViews.HistoryVO history(AstAssetHistory history) {
        AssetViews.HistoryVO view = new AssetViews.HistoryVO();
        view.setId(history.getId());
        view.setAssetId(history.getAssetId());
        view.setFromStatus(history.getFromStatus());
        view.setToStatus(history.getToStatus());
        view.setChangedByUserId(history.getChangedByUserId());
        view.setChangedTime(history.getChangedTime());
        view.setRemark(history.getRemark());
        return view;
    }
}
