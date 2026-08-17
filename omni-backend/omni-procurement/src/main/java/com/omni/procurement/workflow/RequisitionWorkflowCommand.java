package com.omni.procurement.workflow;

import java.math.BigDecimal;

/**
 * 已在本地事务持久化的请购审批启动快照。
 *
 * @param requisitionId 请购 ID
 * @param tenantId 租户 ID
 * @param requisitionNo 请购单号
 * @param title 请购标题
 * @param requesterUserId 申请人用户 ID
 * @param requesterUnitId 申请人组织 ID
 * @param categoryCode 唯一品类编码
 * @param totalAmount 服务端重算总金额
 * @param currencyCode 币种
 * @param approvalAttempt 审批轮次
 * @param requestId Workflow 请求幂等键
 * @param businessKey Workflow 业务键
 * @param modelVersionId Workflow 模型版本 ID
 * @author Omni-Stack Team
 */
public record RequisitionWorkflowCommand(Long requisitionId, Long tenantId, String requisitionNo,
                                         String title, Long requesterUserId, Long requesterUnitId,
                                         String categoryCode, BigDecimal totalAmount, String currencyCode,
                                         Integer approvalAttempt, String requestId, String businessKey,
                                         Long modelVersionId) {
}
