package com.omni.procurement.controller;

import com.omni.common.core.operlog.OperLog;
import com.omni.common.core.operlog.OperType;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import com.omni.procurement.dto.ApprovalRouteRequests;
import com.omni.procurement.dto.ApprovalRouteViews;
import com.omni.procurement.entity.ProcApprovalRoute;
import com.omni.procurement.security.ProcDataScope;
import com.omni.procurement.service.ApprovalRouteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 请购审批路由配置控制器。
 *
 * @author Omni-Stack Team
 */
@Validated
@RestController
@RequestMapping("/api/procurement/approval-route")
@RequiredArgsConstructor
public class ApprovalRouteController {

    private final ApprovalRouteService routeService;

    /**
     * 分页查询审批路由。
     *
     * @param query 查询条件
     * @return 路由分页
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('procurement:approval-route:list')")
    @ProcDataScope(permissionCode = "procurement:approval-route:list")
    public R<PageResult<ApprovalRouteViews.RouteVO>> list(@Valid ApprovalRouteRequests.RouteQuery query) {
        return R.ok(routeService.page(query));
    }

    /**
     * 创建审批路由。
     *
     * @param request 创建请求
     * @return 新路由
     */
    @PostMapping
    @PreAuthorize("hasAuthority('procurement:approval-route:create')")
    @ProcDataScope(permissionCode = "procurement:approval-route:create")
    @OperLog(module = "采购审批路由", operType = OperType.CREATE,
            entityClass = ProcApprovalRoute.class, idExpr = "#result.data.id")
    public R<ApprovalRouteViews.RouteVO> create(
            @Valid @RequestBody ApprovalRouteRequests.CreateRouteRequest request) {
        return R.ok(routeService.create(request));
    }

    /**
     * 更新审批路由，路由编码保持不变。
     *
     * @param id 路由 ID
     * @param request 更新请求
     * @return 更新后路由
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('procurement:approval-route:update')")
    @ProcDataScope(permissionCode = "procurement:approval-route:update")
    @OperLog(module = "采购审批路由", operType = OperType.UPDATE,
            entityClass = ProcApprovalRoute.class, idExpr = "#id")
    public R<ApprovalRouteViews.RouteVO> update(
            @PathVariable Long id, @Valid @RequestBody ApprovalRouteRequests.UpdateRouteRequest request) {
        return R.ok(routeService.update(id, request));
    }

    /**
     * 删除审批路由。
     *
     * @param id 路由 ID
     * @param version 乐观锁版本
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('procurement:approval-route:delete')")
    @ProcDataScope(permissionCode = "procurement:approval-route:delete")
    @OperLog(module = "采购审批路由", operType = OperType.DELETE,
            entityClass = ProcApprovalRoute.class, idExpr = "#id")
    public R<Void> delete(@PathVariable Long id,
                          @RequestParam @Min(value = 0, message = "乐观锁版本不能小于 0") Integer version) {
        routeService.delete(id, version);
        return R.ok();
    }
}
