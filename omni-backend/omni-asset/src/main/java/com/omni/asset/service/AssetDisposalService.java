package com.omni.asset.service;

import com.omni.asset.dto.AssetOperationRequests;
import com.omni.asset.dto.AssetOperationViews;
import com.omni.common.core.result.PageResult;

/**
 * 资产处置服务。
 *
 * @author Omni-Stack Team
 */
public interface AssetDisposalService {

    /**
     * 分页查询处置申请。
     *
     * @param query 查询条件
     * @return 处置分页
     */
    PageResult<AssetOperationViews.DisposalVO> page(AssetOperationRequests.DisposalQuery query);

    /**
     * 查询处置详情。
     *
     * @param id 申请 ID
     * @return 处置详情
     */
    AssetOperationViews.DisposalVO get(Long id);

    /**
     * 校验 Workflow 任务分配后读取审批视图。
     *
     * @param id 申请 ID
     * @param taskId Workflow 任务 ID
     * @return 处置详情
     */
    AssetOperationViews.DisposalVO approvalView(Long id, String taskId);

    /**
     * 创建处置并启动审批。
     *
     * @param request 创建请求
     * @return 处置详情
     */
    AssetOperationViews.DisposalVO create(AssetOperationRequests.CreateDisposalRequest request);

    /**
     * 重试启动审批。
     *
     * @param id 申请 ID
     * @param version 乐观锁版本
     * @return 处置详情
     */
    AssetOperationViews.DisposalVO retryStart(Long id, Integer version);

    /**
     * 取消 Workflow 明确启动失败的申请。
     *
     * @param id 申请 ID
     * @param version 乐观锁版本
     * @return 处置详情
     */
    AssetOperationViews.DisposalVO cancel(Long id, Integer version);

    /**
     * 完成审批通过后的实物处置。
     *
     * @param id 申请 ID
     * @param version 乐观锁版本
     * @return 处置详情
     */
    AssetOperationViews.DisposalVO complete(Long id, Integer version);
}
