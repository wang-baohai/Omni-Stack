package com.omni.workflow.service;

import com.omni.workflow.dto.ApprovalRequest;

/**
 * 工作流审批操作服务接口。
 * <p>
 * 在通用审批能力外增加当前用户、租户和待办缓存同步校验，避免控制器直接调用 Flowable 任务操作。
 * </p>
 *
 * @author Omni-Stack Team
 */
public interface WorkflowApprovalService {

    /**
     * 完成当前审批任务。
     *
     * @param taskId   Flowable 任务 ID
     * @param request  审批请求
     * @param userId   当前用户 ID
     * @param tenantId 当前租户 ID
     */
    void complete(String taskId, ApprovalRequest request, Long userId, Long tenantId);

    /**
     * 增加当前审批节点处理人。
     *
     * @param taskId    Flowable 任务 ID
     * @param newUserId 新增处理人用户 ID
     * @param userId    当前用户 ID
     * @param tenantId  当前租户 ID
     */
    void addSigner(String taskId, String newUserId, Long userId, Long tenantId);

    /**
     * 移除当前审批节点处理人。
     *
     * @param taskId       Flowable 任务 ID
     * @param targetUserId 目标处理人用户 ID
     * @param userId       当前用户 ID
     * @param tenantId     当前租户 ID
     */
    void removeSigner(String taskId, String targetUserId, Long userId, Long tenantId);

    /**
     * 将当前审批任务委托给其他用户。
     *
     * @param taskId       Flowable 任务 ID
     * @param targetUserId 目标处理人用户 ID
     * @param userId       当前用户 ID
     * @param tenantId     当前租户 ID
     */
    void delegate(String taskId, String targetUserId, Long userId, Long tenantId);
}
