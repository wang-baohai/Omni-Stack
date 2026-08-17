package com.omni.procurement.controller;

import com.omni.common.core.operlog.OperLog;
import com.omni.common.core.operlog.OperType;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import com.omni.procurement.dto.PurchaseOrderRequests;
import com.omni.procurement.dto.PurchaseOrderViews;
import com.omni.procurement.entity.ProcPurchaseOrder;
import com.omni.procurement.security.ProcDataScope;
import com.omni.procurement.service.PurchaseOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
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
 * 采购订单控制器。
 *
 * @author Omni-Stack Team
 */
@Validated
@RestController
@RequestMapping("/api/procurement/purchase-order")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    /**
     * 分页查询采购订单。
     *
     * @param query 查询条件
     * @return 采购订单分页
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('procurement:purchase-order:list')")
    @ProcDataScope(permissionCode = "procurement:purchase-order:list")
    public R<PageResult<PurchaseOrderViews.Summary>> list(
            @Valid PurchaseOrderRequests.Query query) {
        return R.ok(purchaseOrderService.page(query));
    }

    /**
     * 查询采购订单详情。
     *
     * @param id 采购订单 ID
     * @return 采购订单详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('procurement:purchase-order:list')")
    @ProcDataScope(permissionCode = "procurement:purchase-order:list")
    public R<PurchaseOrderViews.Detail> get(@PathVariable @Positive Long id) {
        return R.ok(purchaseOrderService.get(id));
    }

    /**
     * 更新草稿采购订单交付信息。
     *
     * @param id 采购订单 ID
     * @param request 更新请求
     * @return 更新后详情
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('procurement:purchase-order:update')")
    @ProcDataScope(permissionCode = "procurement:purchase-order:update")
    @OperLog(module = "采购订单", operType = OperType.UPDATE,
            entityClass = ProcPurchaseOrder.class, idExpr = "#id")
    public R<PurchaseOrderViews.Detail> update(
            @PathVariable @Positive Long id,
            @Valid @RequestBody PurchaseOrderRequests.UpdateRequest request) {
        return R.ok(purchaseOrderService.update(id, request));
    }

    /**
     * 删除草稿采购订单。
     *
     * @param id 采购订单 ID
     * @param version 乐观锁版本
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('procurement:purchase-order:delete')")
    @ProcDataScope(permissionCode = "procurement:purchase-order:delete")
    @OperLog(module = "采购订单", operType = OperType.DELETE,
            entityClass = ProcPurchaseOrder.class, idExpr = "#id")
    public R<Void> delete(
            @PathVariable @Positive Long id,
            @RequestParam @Min(0) Integer version) {
        purchaseOrderService.delete(id, version);
        return R.ok();
    }

    /**
     * 发送采购订单。
     *
     * @param id 采购订单 ID
     * @param command 乐观锁命令
     * @return 更新后详情
     */
    @PostMapping("/{id}/send")
    @PreAuthorize("hasAuthority('procurement:purchase-order:send')")
    @ProcDataScope(permissionCode = "procurement:purchase-order:send")
    @OperLog(module = "采购订单", operType = OperType.UPDATE,
            entityClass = ProcPurchaseOrder.class, idExpr = "#id")
    public R<PurchaseOrderViews.Detail> send(
            @PathVariable @Positive Long id,
            @Valid @RequestBody PurchaseOrderRequests.VersionCommand command) {
        return R.ok(purchaseOrderService.send(id, command.getVersion()));
    }

    /**
     * 确认采购订单。
     *
     * @param id 采购订单 ID
     * @param command 乐观锁命令
     * @return 更新后详情
     */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('procurement:purchase-order:confirm')")
    @ProcDataScope(permissionCode = "procurement:purchase-order:confirm")
    @OperLog(module = "采购订单", operType = OperType.UPDATE,
            entityClass = ProcPurchaseOrder.class, idExpr = "#id")
    public R<PurchaseOrderViews.Detail> confirm(
            @PathVariable @Positive Long id,
            @Valid @RequestBody PurchaseOrderRequests.VersionCommand command) {
        return R.ok(purchaseOrderService.confirm(id, command.getVersion()));
    }

    /**
     * 取消尚未收货的采购订单。
     *
     * @param id 采购订单 ID
     * @param command 乐观锁命令
     * @return 更新后详情
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('procurement:purchase-order:cancel')")
    @ProcDataScope(permissionCode = "procurement:purchase-order:cancel")
    @OperLog(module = "采购订单", operType = OperType.UPDATE,
            entityClass = ProcPurchaseOrder.class, idExpr = "#id")
    public R<PurchaseOrderViews.Detail> cancel(
            @PathVariable @Positive Long id,
            @Valid @RequestBody PurchaseOrderRequests.VersionCommand command) {
        return R.ok(purchaseOrderService.cancel(id, command.getVersion()));
    }
}
