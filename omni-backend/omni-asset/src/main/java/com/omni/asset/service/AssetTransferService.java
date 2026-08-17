package com.omni.asset.service;

import com.omni.asset.dto.AssetOperationRequests;
import com.omni.asset.dto.AssetOperationViews;
import com.omni.common.core.result.PageResult;

/**
 * 资产调拨服务。
 *
 * @author Omni-Stack Team
 */
public interface AssetTransferService {

    /**
     * 分页查询调拨申请。
     *
     * @param query 查询条件
     * @return 调拨分页
     */
    PageResult<AssetOperationViews.TransferVO> page(AssetOperationRequests.TransferQuery query);

    /**
     * 查询调拨详情。
     *
     * @param id 申请 ID
     * @return 调拨详情
     */
    AssetOperationViews.TransferVO get(Long id);

    /**
     * 校验 Workflow 任务分配后读取审批视图。
     *
     * @param id 申请 ID
     * @param taskId Workflow 任务 ID
     * @return 调拨详情
     */
    AssetOperationViews.TransferVO approvalView(Long id, String taskId);

    /**
     * 创建调拨并启动审批。
     *
     * @param request 创建请求
     * @return 调拨详情
     */
    AssetOperationViews.TransferVO create(AssetOperationRequests.CreateTransferRequest request);

    /**
     * 重试启动审批。
     *
     * @param id 申请 ID
     * @param version 乐观锁版本
     * @return 调拨详情
     */
    AssetOperationViews.TransferVO retryStart(Long id, Integer version);

    /**
     * 取消 Workflow 明确启动失败的申请。
     *
     * @param id 申请 ID
     * @param version 乐观锁版本
     * @return 调拨详情
     */
    AssetOperationViews.TransferVO cancel(Long id, Integer version);

    /**
     * 完成审批通过后的资产交接。
     *
     * @param id 申请 ID
     * @param version 乐观锁版本
     * @return 调拨详情
     */
    AssetOperationViews.TransferVO complete(Long id, Integer version);
}
