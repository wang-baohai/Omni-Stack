package com.omni.asset.service;

import com.omni.asset.dto.AssetOperationRequests;
import com.omni.asset.workflow.AssetWorkflowCommand;

/**
 * 资产操作 Workflow 本地状态服务。
 *
 * @author Omni-Stack Team
 */
public interface AssetOperationWorkflowStateService {

    /**
     * 创建调拨申请并原子占用资产。
     *
     * @param request 创建请求
     * @return 启动快照
     */
    AssetWorkflowCommand prepareTransfer(AssetOperationRequests.CreateTransferRequest request);

    /**
     * 创建处置申请并原子占用资产。
     *
     * @param request 创建请求
     * @return 启动快照
     */
    AssetWorkflowCommand prepareDisposal(AssetOperationRequests.CreateDisposalRequest request);

    /**
     * 准备重试既有申请，复用原请求与业务键。
     *
     * @param operationType 操作类型
     * @param id 申请 ID
     * @param version 乐观锁版本
     * @return 原启动快照
     */
    AssetWorkflowCommand prepareRetry(String operationType, Long id, Integer version);

    /**
     * 独立事务确认 Workflow 已启动。
     *
     * @param command 启动快照
     * @param processInstanceId 流程实例 ID
     */
    void markStarted(AssetWorkflowCommand command, String processInstanceId);

    /**
     * 独立事务标记 Workflow 启动失败。
     *
     * @param command 启动快照
     */
    void markFailed(AssetWorkflowCommand command);
}
