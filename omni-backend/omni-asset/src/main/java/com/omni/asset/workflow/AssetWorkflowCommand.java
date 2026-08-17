package com.omni.asset.workflow;

import java.util.Map;

/**
 * 已持久化的 Asset Workflow 启动快照。
 *
 * @param operationType TRANSFER/DISPOSAL
 * @param operationId 申请 ID
 * @param tenantId 租户 ID
 * @param requestId Workflow 请求 ID
 * @param businessType Workflow 业务类型
 * @param businessKey Workflow 业务键
 * @param modelVersionId 模型版本 ID
 * @param startUserId 发起人 ID
 * @param startUserName 发起人名称
 * @param title 流程标题
 * @param variables 流程变量
 */
public record AssetWorkflowCommand(
        String operationType,
        Long operationId,
        Long tenantId,
        String requestId,
        String businessType,
        String businessKey,
        Long modelVersionId,
        Long startUserId,
        String startUserName,
        String title,
        Map<String, Object> variables) {
}
