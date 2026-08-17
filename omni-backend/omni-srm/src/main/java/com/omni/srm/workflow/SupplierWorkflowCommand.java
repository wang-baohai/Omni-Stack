package com.omni.srm.workflow;

/**
 * 已在本地事务持久化的供应商准入审批启动快照。
 *
 * @param supplierId      供应商 ID
 * @param tenantId        租户 ID
 * @param supplierNo      供应商编号
 * @param supplierName    供应商名称
 * @param categoryCode    品类编码
 * @param approvalAttempt 审批轮次
 * @param requestId       Workflow 请求幂等键
 * @param businessKey     Workflow 业务键
 * @param modelVersionId  Workflow 模型版本 ID
 * @param startUserId     发起人用户 ID
 * @param startUserName   发起人用户名
 * @author Omni-Stack Team
 */
public record SupplierWorkflowCommand(Long supplierId, Long tenantId, String supplierNo,
                                      String supplierName, String categoryCode,
                                      Integer approvalAttempt, String requestId, String businessKey,
                                      Long modelVersionId, Long startUserId, String startUserName) {
}
