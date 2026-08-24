package com.omni.procurement.controller;

import com.omni.common.core.operlog.OperLog;
import com.omni.common.core.operlog.OperType;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import com.omni.procurement.dto.GoodsReceiptRequests;
import com.omni.procurement.dto.GoodsReceiptViews;
import com.omni.procurement.entity.ProcGoodsReceipt;
import com.omni.common.service.datascope.ServiceDataScope;
import com.omni.procurement.service.GoodsReceiptService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 收货单控制器。
 *
 * @author Omni-Stack Team
 */
@Validated
@RestController
@RequestMapping("/api/procurement/goods-receipt")
@RequiredArgsConstructor
public class GoodsReceiptController {

    private final GoodsReceiptService goodsReceiptService;

    /**
     * 分页查询收货单。
     *
     * @param query 查询条件
     * @return 收货单分页
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('procurement:goods-receipt:list')")
    @ServiceDataScope(permissionCode = "procurement:goods-receipt:list")
    public R<PageResult<GoodsReceiptViews.Summary>> list(
            @Valid GoodsReceiptRequests.Query query) {
        return R.ok(goodsReceiptService.page(query));
    }

    /**
     * 查询收货单详情。
     *
     * @param id 收货单 ID
     * @return 收货单详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('procurement:goods-receipt:list')")
    @ServiceDataScope(permissionCode = "procurement:goods-receipt:list")
    public R<GoodsReceiptViews.Detail> get(@PathVariable @Positive Long id) {
        return R.ok(goodsReceiptService.get(id));
    }

    /**
     * 创建收货草稿。
     *
     * @param request 创建请求
     * @return 收货单详情
     */
    @PostMapping
    @PreAuthorize("hasAuthority('procurement:goods-receipt:create')")
    @ServiceDataScope(permissionCode = "procurement:goods-receipt:create")
    @OperLog(module = "采购收货", operType = OperType.CREATE,
            entityClass = ProcGoodsReceipt.class, idExpr = "#result.data.id")
    public R<GoodsReceiptViews.Detail> create(
            @Valid @RequestBody GoodsReceiptRequests.CreateRequest request) {
        return R.ok(goodsReceiptService.create(request));
    }

    /**
     * 确认收货并更新订单累计收货状态。
     *
     * @param id 收货单 ID
     * @param command 乐观锁命令
     * @return 收货单详情
     */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('procurement:goods-receipt:confirm')")
    @ServiceDataScope(permissionCode = "procurement:goods-receipt:confirm")
    @OperLog(module = "采购收货", operType = OperType.UPDATE,
            entityClass = ProcGoodsReceipt.class, idExpr = "#id")
    public R<GoodsReceiptViews.Detail> confirm(
            @PathVariable @Positive Long id,
            @Valid @RequestBody GoodsReceiptRequests.VersionCommand command) {
        return R.ok(goodsReceiptService.confirm(id, command.getVersion()));
    }

    /**
     * 登记已确认收货行的后续质检结果。
     *
     * @param id 收货单 ID
     * @param command 质检结果命令
     * @return 收货单详情
     */
    @PostMapping("/{id}/quality-result")
    @PreAuthorize("hasAuthority('procurement:goods-receipt:confirm')")
    @ServiceDataScope(permissionCode = "procurement:goods-receipt:confirm")
    @OperLog(module = "采购收货", operType = OperType.UPDATE,
            entityClass = ProcGoodsReceipt.class, idExpr = "#id")
    public R<GoodsReceiptViews.Detail> qualityResult(
            @PathVariable @Positive Long id,
            @Valid @RequestBody GoodsReceiptRequests.QualityResultCommand command) {
        return R.ok(goodsReceiptService.updateQualityResult(id, command));
    }
}
