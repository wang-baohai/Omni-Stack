package com.omni.procurement.controller;

import com.omni.common.core.operlog.OperLog;
import com.omni.common.core.operlog.OperType;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import com.omni.procurement.dto.PurchaseOrderContracts;
import com.omni.procurement.dto.RfqRequests;
import com.omni.procurement.dto.RfqViews;
import com.omni.procurement.entity.ProcRfq;
import com.omni.common.service.datascope.ServiceDataScope;
import com.omni.procurement.service.RfqService;
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

import java.util.List;

/**
 * 询价单控制器。
 *
 * @author Omni-Stack Team
 */
@Validated
@RestController
@RequestMapping("/api/procurement/rfq")
@RequiredArgsConstructor
public class RfqController {

    private final RfqService rfqService;

    /**
     * 查询创建询价可选择的合格供应商。
     *
     * @param query 查询条件
     * @return 无 PII 供应商选项
     */
    @GetMapping("/supplier-options")
    @PreAuthorize("hasAnyAuthority('procurement:rfq:create', 'procurement:rfq:list')")
    public R<List<RfqViews.SupplierOption>> supplierOptions(
            @Valid RfqRequests.SupplierOptionQuery query) {
        return R.ok(rfqService.supplierOptions(query));
    }

    /**
     * 分页查询询价单。
     *
     * @param query 查询条件
     * @return 询价分页
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('procurement:rfq:list')")
    @ServiceDataScope(permissionCode = "procurement:rfq:list")
    public R<PageResult<RfqViews.Summary>> list(@Valid RfqRequests.Query query) {
        return R.ok(rfqService.page(query));
    }

    /**
     * 查询询价详情。
     *
     * @param id 询价单 ID
     * @return 询价详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('procurement:rfq:list')")
    @ServiceDataScope(permissionCode = "procurement:rfq:list")
    public R<RfqViews.Detail> get(@PathVariable @Positive Long id) {
        return R.ok(rfqService.get(id));
    }

    /**
     * 查询当前有效报价的比价快照。
     *
     * @param id 询价单 ID
     * @return 当前有效报价
     */
    @GetMapping("/{id}/comparison")
    @PreAuthorize("hasAuthority('procurement:rfq:list')")
    @ServiceDataScope(permissionCode = "procurement:rfq:list")
    public R<List<PurchaseOrderContracts.QuotationSnapshot>> comparison(
            @PathVariable @Positive Long id) {
        return R.ok(rfqService.comparison(id));
    }

    /**
     * 从已审批请购创建询价草稿。
     *
     * @param request 创建请求
     * @return 询价详情
     */
    @PostMapping
    @PreAuthorize("hasAuthority('procurement:rfq:create')")
    @ServiceDataScope(permissionCode = "procurement:rfq:create")
    @OperLog(module = "采购询价", operType = OperType.CREATE,
            entityClass = ProcRfq.class, idExpr = "#result.data.id")
    public R<RfqViews.Detail> create(@Valid @RequestBody RfqRequests.CreateRequest request) {
        return R.ok(rfqService.create(request));
    }

    /**
     * 更新询价草稿。
     *
     * @param id 询价单 ID
     * @param request 更新请求
     * @return 询价详情
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('procurement:rfq:update')")
    @ServiceDataScope(permissionCode = "procurement:rfq:update")
    @OperLog(module = "采购询价", operType = OperType.UPDATE,
            entityClass = ProcRfq.class, idExpr = "#id")
    public R<RfqViews.Detail> update(
            @PathVariable @Positive Long id,
            @Valid @RequestBody RfqRequests.UpdateRequest request) {
        return R.ok(rfqService.update(id, request));
    }

    /**
     * 删除询价草稿。
     *
     * @param id 询价单 ID
     * @param version 乐观锁版本
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('procurement:rfq:delete')")
    @ServiceDataScope(permissionCode = "procurement:rfq:delete")
    @OperLog(module = "采购询价", operType = OperType.DELETE,
            entityClass = ProcRfq.class, idExpr = "#id")
    public R<Void> delete(
            @PathVariable @Positive Long id,
            @RequestParam @Min(0) Integer version) {
        rfqService.delete(id, version);
        return R.ok();
    }

    /**
     * 显式发送询价。
     *
     * @param id 询价单 ID
     * @param command 乐观锁命令
     * @return 已发送询价详情
     */
    @PostMapping("/{id}/send")
    @PreAuthorize("hasAuthority('procurement:rfq:send')")
    @ServiceDataScope(permissionCode = "procurement:rfq:send")
    @OperLog(module = "采购询价", operType = OperType.UPDATE,
            entityClass = ProcRfq.class, idExpr = "#id")
    public R<RfqViews.Detail> send(
            @PathVariable @Positive Long id,
            @Valid @RequestBody RfqRequests.VersionCommand command) {
        return R.ok(rfqService.send(id, command.getVersion()));
    }

    /**
     * 选择当前报价版本定点并生成采购订单。
     *
     * @param id 询价单 ID
     * @param request 定点命令
     * @return RFQ 与采购订单定点结果
     */
    @PostMapping("/{id}/award")
    @PreAuthorize("hasAuthority('procurement:rfq:award')")
    @ServiceDataScope(permissionCode = "procurement:rfq:award")
    @OperLog(module = "采购询价", operType = OperType.UPDATE,
            entityClass = ProcRfq.class, idExpr = "#id")
    public R<RfqViews.AwardResult> award(
            @PathVariable @Positive Long id,
            @Valid @RequestBody RfqRequests.AwardRequest request) {
        return R.ok(rfqService.award(id, request));
    }

    /**
     * 取消草稿或已发送询价。
     *
     * @param id 询价单 ID
     * @param command 乐观锁命令
     * @return 已取消询价详情
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('procurement:rfq:cancel')")
    @ServiceDataScope(permissionCode = "procurement:rfq:cancel")
    @OperLog(module = "采购询价", operType = OperType.UPDATE,
            entityClass = ProcRfq.class, idExpr = "#id")
    public R<RfqViews.Detail> cancel(
            @PathVariable @Positive Long id,
            @Valid @RequestBody RfqRequests.VersionCommand command) {
        return R.ok(rfqService.cancel(id, command.getVersion()));
    }
}
