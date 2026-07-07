package com.omni.workflow.service;

import com.omni.common.core.result.PageResult;
import com.omni.workflow.dto.ApprovalRecord;
import com.omni.workflow.dto.ProcessProgressResponse;
import com.omni.workflow.dto.StartProcessRequest;
import com.omni.workflow.entity.WfProcessInstanceExt;

import java.util.List;

/**
 * 流程实例服务接口。
 * <p>
 * 封装 Flowable {@link org.flowable.engine.RuntimeService} 和
 * {@link org.flowable.engine.HistoryService}，提供流程实例的发起、
 * 查询、终止等操作。</p>
 *
 * @author Omni-Stack Team
 */
public interface ProcessInstanceService {

    /**
     * 发起流程实例。
     * <p>
     * 根据流程定义 Key 启动新实例，同时写入扩展表记录业务信息。</p>
     *
     * @param request   发起请求
     * @param userId    发起人用户 ID
     * @param userName  发起人用户名
     * @param tenantId  租户 ID
     * @return 流程实例 ID
     */
    String start(StartProcessRequest request, Long userId, String userName, Long tenantId);

    /**
     * 查询"我发起的"流程实例列表（分页）。
     *
     * @param userId   发起人用户 ID
     * @param tenantId 租户 ID
     * @param title    流程标题（模糊查询，可选）
     * @param status   状态（可选：0-已终止, 1-进行中, 2-已完成）
     * @param page     页码
     * @param size     每页数量
     * @return 分页结果
     */
    PageResult<WfProcessInstanceExt> myInitiated(Long userId, Long tenantId,
                                                  String title, Integer status,
                                                  int page, int size);

    /**
     * 查询"我已办的"流程实例列表（分页）。
     * <p>
     * 通过 Flowable HistoryService 查询当前用户参与过的流程实例。</p>
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @param title    流程标题（模糊查询，可选）
     * @param page     页码
     * @param size     每页数量
     * @return 分页结果
     */
    PageResult<WfProcessInstanceExt> myCompleted(Long userId, Long tenantId,
                                                  String title,
                                                  int page, int size);

    /**
     * 终止流程实例。
     * <p>仅允许发起人终止自己发起的且尚在运行中的流程。</p>
     *
     * @param processInstanceId 流程实例 ID
     * @param reason            终止原因
     */
    void terminate(String processInstanceId, String reason, Long userId, Long tenantId);

    /**
     * 管理员查询所有流程实例（分页）。
     *
     * @param tenantId 租户 ID
     * @param title    流程标题（可选）
     * @param status   状态（可选）
     * @param page     页码
     * @param size     每页数量
     * @return 分页结果
     */
    PageResult<WfProcessInstanceExt> listAll(Long tenantId, String title,
                                              Integer status, int page, int size);

    /**
     * 获取流程实例的流转进度信息。
     * <p>返回已完成、进行中、未到达的活动节点，用于渲染流程全景图。</p>
     *
     * @param processInstanceId 流程实例 ID
     * @return 流程进度信息
     */
    ProcessProgressResponse getProgress(String processInstanceId);

    /**
     * 获取流程实例的审批记录列表。
     * <p>
     * 逐人扁平化展示：每个 userTask 节点的每个审批人各一行，
     * 包含审批结果（通过/驳回/自动通过/已取消/待审批）和审批意见。</p>
     *
     * @param processInstanceId 流程实例 ID
     * @return 审批记录列表
     */
    List<ApprovalRecord> getApprovalRecords(String processInstanceId);
}
