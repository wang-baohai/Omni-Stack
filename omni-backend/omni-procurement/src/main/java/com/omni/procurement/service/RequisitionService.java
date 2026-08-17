package com.omni.procurement.service;

import com.omni.common.core.result.PageResult;
import com.omni.procurement.dto.RequisitionRequests;
import com.omni.procurement.dto.RequisitionViews;

/**
 * 请购申请服务。
 *
 * @author Omni-Stack Team
 */
public interface RequisitionService {

    /**
     * 分页查询请购。
     *
     * @param query 查询条件
     * @return 请购分页
     */
    PageResult<RequisitionViews.Summary> page(RequisitionRequests.Query query);

    /**
     * 查询普通请购详情。
     *
     * @param id 请购 ID
     * @return 请购详情
     */
    RequisitionViews.Detail get(Long id);

    /**
     * 校验 Workflow 任务分配后查询审批专用视图。
     *
     * @param id 请购 ID
     * @param taskId Workflow 任务 ID
     * @return 审批视图
     */
    RequisitionViews.ApprovalView approvalView(Long id, String taskId);

    /**
     * 创建请购草稿。
     *
     * @param request 创建请求
     * @return 请购详情
     */
    RequisitionViews.Detail create(RequisitionRequests.CreateRequest request);

    /**
     * 更新请购内容；被拒绝请购更新成功后回到草稿。
     *
     * @param id 请购 ID
     * @param request 更新请求
     * @return 请购详情
     */
    RequisitionViews.Detail update(Long id, RequisitionRequests.UpdateRequest request);

    /**
     * 删除请购草稿。
     *
     * @param id 请购 ID
     * @param version 乐观锁版本
     */
    void delete(Long id, Integer version);

    /**
     * 提交请购并在本地事务提交后启动 Workflow。
     *
     * @param id 请购 ID
     * @param version 乐观锁版本
     * @return 审批中的请购详情
     */
    RequisitionViews.Detail submit(Long id, Integer version);

    /**
     * 使用原幂等快照重试 Workflow 启动。
     *
     * @param id 请购 ID
     * @param version 乐观锁版本
     * @return 审批中的请购详情
     */
    RequisitionViews.Detail retryStart(Long id, Integer version);

    /**
     * 取消草稿或 Workflow 启动失败的请购。
     *
     * @param id 请购 ID
     * @param version 乐观锁版本
     * @return 取消后的请购详情
     */
    RequisitionViews.Detail cancel(Long id, Integer version);
}
