package com.omni.procurement.service;

import com.omni.common.core.result.PageResult;
import com.omni.procurement.dto.ApprovalRouteRequests;
import com.omni.procurement.dto.ApprovalRouteViews;

/**
 * 审批路由配置服务。
 *
 * @author Omni-Stack Team
 */
public interface ApprovalRouteService {

    /**
     * 分页查询审批路由。
     *
     * @param query 查询条件
     * @return 路由分页
     */
    PageResult<ApprovalRouteViews.RouteVO> page(ApprovalRouteRequests.RouteQuery query);

    /**
     * 创建审批路由。
     *
     * @param request 创建请求
     * @return 新路由
     */
    ApprovalRouteViews.RouteVO create(ApprovalRouteRequests.CreateRouteRequest request);

    /**
     * 更新审批路由。
     *
     * @param id 路由 ID
     * @param request 更新请求
     * @return 更新后路由
     */
    ApprovalRouteViews.RouteVO update(Long id, ApprovalRouteRequests.UpdateRouteRequest request);

    /**
     * 删除审批路由。
     *
     * @param id 路由 ID
     * @param version 乐观锁版本
     */
    void delete(Long id, Integer version);
}
