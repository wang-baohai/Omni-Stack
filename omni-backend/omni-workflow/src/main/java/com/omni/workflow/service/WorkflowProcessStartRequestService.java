package com.omni.workflow.service;

import com.omni.workflow.entity.WfProcessStartRequest;

import java.util.Optional;

/**
 * 流程启动幂等请求服务。
 *
 * @author Omni-Stack Team
 */
public interface WorkflowProcessStartRequestService {

    /**
     * 预留一个流程启动资格。
     *
     * <p>首次请求或成功抢占失败记录的调用方会获得执行资格；并发重放只能读取已有记录。</p>
     *
     * @param request 流程启动预留请求
     * @return 预留结果
     */
    Reservation reserve(StartReservationRequest request);

    /**
     * 按请求 ID 查询启动记录。
     *
     * @param tenantId 租户 ID
     * @param requestId 请求 ID
     * @return 启动记录
     */
    Optional<WfProcessStartRequest> findByRequestId(Long tenantId, String requestId);

    /**
     * 按业务键查询启动记录。
     *
     * @param tenantId 租户 ID
     * @param businessType 业务类型
     * @param businessKey 业务主键
     * @return 启动记录
     */
    Optional<WfProcessStartRequest> findByBusinessKey(Long tenantId,
                                                      String businessType,
                                                      String businessKey);

    /**
     * 原子抢占一条失败记录用于重试。
     *
     * @param tenantId 租户 ID
     * @param id 启动记录 ID
     * @return 预留结果
     */
    Reservation retry(Long tenantId, Long id);

    /**
     * 标记流程实例启动成功。
     *
     * @param tenantId 租户 ID
     * @param id 启动记录 ID
     * @param processInstanceId Flowable 流程实例 ID
     */
    void markStarted(Long tenantId, Long id, String processInstanceId);

    /**
     * 标记流程实例启动失败。
     *
     * @param tenantId 租户 ID
     * @param id 启动记录 ID
     * @param error 失败原因
     */
    void markFailed(Long tenantId, Long id, String error);

    /**
     * 启动资格预留结果。
     *
     * @param request 持久化的启动记录
     * @param acquired 当前调用是否获得流程启动资格
     * @param created 当前调用是否新建记录
     */
    record Reservation(WfProcessStartRequest request, boolean acquired, boolean created) {
    }

    /**
     * 流程启动预留请求。
     *
     * @param tenantId 租户 ID
     * @param requestId 请求 ID
     * @param businessType 业务类型
     * @param businessKey 业务主键
     * @param modelVersionId 流程模型版本 ID
     * @param startUserId 发起人用户 ID
     */
    record StartReservationRequest(Long tenantId,
                                   String requestId,
                                   String businessType,
                                   String businessKey,
                                   Long modelVersionId,
                                   Long startUserId) {
    }
}
