package com.omni.workflow.service;

import com.omni.workflow.dto.internal.InternalModelVersionResponse;
import com.omni.workflow.dto.internal.InternalStartProcessRequest;
import com.omni.workflow.dto.internal.InternalStartProcessResponse;
import com.omni.workflow.dto.internal.InternalTaskAssignmentRequest;
import com.omni.workflow.dto.internal.InternalTaskAssignmentResponse;

/**
 * Workflow 内部服务接口。
 *
 * @author Omni-Stack Team
 */
public interface InternalWorkflowService {

    /**
     * 查询并校验租户内已发布的流程模型版本。
     *
     * @param tenantId       租户 ID
     * @param modelVersionId 流程模型版本 ID
     * @return 已发布的流程模型版本
     */
    InternalModelVersionResponse getModelVersion(Long tenantId, Long modelVersionId);

    /**
     * 按业务分类查询租户当前已发布模型版本。
     *
     * @param tenantId 租户 ID
     * @param category 业务分类
     * @return 当前已发布模型版本
     */
    InternalModelVersionResponse getCurrentPublishedModelVersion(Long tenantId, String category);

    /**
     * 以指定业务身份发起流程。
     *
     * @param headerTenantId 请求头租户 ID
     * @param request        发起流程请求
     * @return 发起结果
     */
    InternalStartProcessResponse start(Long headerTenantId, InternalStartProcessRequest request);

    /**
     * 校验用户是否可以处理指定业务流程任务。
     *
     * @param headerTenantId 请求头租户 ID
     * @param request        任务校验请求
     * @return 校验结果
     */
    InternalTaskAssignmentResponse validateAssignment(
            Long headerTenantId, InternalTaskAssignmentRequest request);
}
