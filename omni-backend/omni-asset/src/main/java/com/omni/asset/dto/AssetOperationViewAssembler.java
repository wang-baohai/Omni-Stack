package com.omni.asset.dto;

import com.omni.asset.entity.AstAsset;
import com.omni.asset.entity.AstDisposal;
import com.omni.asset.entity.AstTransfer;

/**
 * 调拨和处置实体视图转换器。
 *
 * @author Omni-Stack Team
 */
public final class AssetOperationViewAssembler {

    private AssetOperationViewAssembler() {
    }

    /**
     * 转换调拨视图。
     *
     * @param transfer 调拨申请
     * @param asset 关联资产
     * @return 调拨视图
     */
    public static AssetOperationViews.TransferVO transfer(AstTransfer transfer, AstAsset asset) {
        AssetOperationViews.TransferVO view = new AssetOperationViews.TransferVO();
        view.setId(transfer.getId());
        view.setTransferNo(transfer.getTransferNo());
        view.setAssetId(transfer.getAssetId());
        view.setAssetNo(asset == null ? null : asset.getAssetNo());
        view.setAssetName(asset == null ? null : asset.getName());
        view.setFromUserId(transfer.getFromUserId());
        view.setFromUnitId(transfer.getFromUnitId());
        view.setToUserId(transfer.getToUserId());
        view.setToUnitId(transfer.getToUnitId());
        view.setFromLocation(transfer.getFromLocation());
        view.setToLocation(transfer.getToLocation());
        view.setReason(transfer.getReason());
        view.setStatus(transfer.getStatus());
        view.setPreviousAssetStatus(transfer.getPreviousAssetStatus());
        view.setWorkflowStartStatus(transfer.getWorkflowStartStatus());
        view.setProcessInstanceId(transfer.getProcessInstanceId());
        view.setApprovedTime(transfer.getApprovedTime());
        view.setCompletedTime(transfer.getCompletedTime());
        view.setVersion(transfer.getVersion());
        view.setCreateTime(transfer.getCreateTime());
        return view;
    }

    /**
     * 转换处置视图。
     *
     * @param disposal 处置申请
     * @param asset 关联资产
     * @return 处置视图
     */
    public static AssetOperationViews.DisposalVO disposal(AstDisposal disposal, AstAsset asset) {
        AssetOperationViews.DisposalVO view = new AssetOperationViews.DisposalVO();
        view.setId(disposal.getId());
        view.setDisposalNo(disposal.getDisposalNo());
        view.setAssetId(disposal.getAssetId());
        view.setAssetNo(asset == null ? null : asset.getAssetNo());
        view.setAssetName(asset == null ? null : asset.getName());
        view.setDisposalType(disposal.getDisposalType());
        view.setReason(disposal.getReason());
        view.setResidualValue(disposal.getResidualValue());
        view.setDisposalMethod(disposal.getDisposalMethod());
        view.setStatus(disposal.getStatus());
        view.setPreviousAssetStatus(disposal.getPreviousAssetStatus());
        view.setWorkflowStartStatus(disposal.getWorkflowStartStatus());
        view.setProcessInstanceId(disposal.getProcessInstanceId());
        view.setApprovedTime(disposal.getApprovedTime());
        view.setCompletedTime(disposal.getCompletedTime());
        view.setFinalApproverUserId(disposal.getFinalApproverUserId());
        view.setFinalApproverRemark(disposal.getFinalApproverRemark());
        view.setVersion(disposal.getVersion());
        view.setCreateTime(disposal.getCreateTime());
        return view;
    }
}
