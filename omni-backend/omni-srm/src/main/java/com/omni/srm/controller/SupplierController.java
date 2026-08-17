package com.omni.srm.controller;

import com.omni.common.core.operlog.OperLog;
import com.omni.common.core.operlog.OperType;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import com.omni.srm.dto.SrmRequests;
import com.omni.srm.dto.SrmViews;
import com.omni.srm.security.SrmDataScope;
import com.omni.srm.service.SupplierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** SRM 供应商控制器。 */
@RestController
@RequestMapping("/api/srm/supplier")
@RequiredArgsConstructor
public class SupplierController {
    private final SupplierService supplierService;

    /** 分页查询供应商。 */
    @GetMapping("/list") @PreAuthorize("hasAuthority('srm:supplier:list')") @SrmDataScope(permissionCode = "srm:supplier:list")
    public R<PageResult<SrmViews.SupplierVO>> list(@Valid SrmRequests.SupplierQuery query) {
        return R.ok(supplierService.list(query));
    }

    /** 查询供应商详情。 */
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('srm:supplier:list')") @SrmDataScope(permissionCode = "srm:supplier:list")
    public R<SrmViews.SupplierDetailVO> get(@PathVariable Long id) {
        return R.ok(supplierService.get(id));
    }

    /** 查询供应商 360。 */
    @GetMapping("/{id}/overview") @PreAuthorize("hasAuthority('srm:supplier:list')") @SrmDataScope(permissionCode = "srm:supplier:list")
    public R<SrmViews.SupplierOverviewVO> overview(@PathVariable Long id) {
        return R.ok(supplierService.overview(id));
    }

    /** 创建供应商。 */
    @PostMapping @PreAuthorize("hasAuthority('srm:supplier:create')") @SrmDataScope(permissionCode = "srm:supplier:create")
    @OperLog(module = "SRM供应商", operType = OperType.CREATE, recordSnapshot = false, excludeFields = {"creditCode", "phone", "email"})
    public R<SrmViews.SupplierVO> create(@Valid @RequestBody SrmRequests.CreateSupplierRequest request) {
        return R.ok(supplierService.create(request));
    }

    /** 更新供应商。 */
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('srm:supplier:update')") @SrmDataScope(permissionCode = "srm:supplier:update")
    @OperLog(module = "SRM供应商", operType = OperType.UPDATE, idExpr = "#id", recordSnapshot = false,
            excludeFields = {"creditCode", "phone", "email"})
    public R<SrmViews.SupplierVO> update(@PathVariable Long id,
                                         @Valid @RequestBody SrmRequests.UpdateSupplierRequest request) {
        return R.ok(supplierService.update(id, request));
    }

    /** 转移供应商负责人。 */
    @PostMapping("/{id}/transfer")
    @PreAuthorize("hasAuthority('srm:supplier:transfer')")
    @SrmDataScope(permissionCode = "srm:supplier:transfer")
    @OperLog(module = "SRM供应商负责人", operType = OperType.UPDATE, idExpr = "#id")
    public R<SrmViews.SupplierVO> transferOwner(
            @PathVariable Long id, @Valid @RequestBody SrmRequests.TransferOwnerRequest request) {
        return R.ok(supplierService.transferOwner(id, request));
    }

    /** 删除供应商。 */
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('srm:supplier:delete')") @SrmDataScope(permissionCode = "srm:supplier:delete")
    @OperLog(module = "SRM供应商", operType = OperType.DELETE, idExpr = "#id")
    public R<Void> delete(@PathVariable Long id, @RequestParam Integer version) {
        supplierService.delete(id, version);
        return R.ok();
    }

    /** 提交审核（被驳回后重新提交，启动新一轮工作流）。 */
    @PostMapping("/{id}/submit") @PreAuthorize("hasAuthority('srm:supplier:create')") @SrmDataScope(permissionCode = "srm:supplier:create")
    @OperLog(module = "SRM供应商", operType = OperType.UPDATE, idExpr = "#id")
    public R<SrmViews.SupplierVO> submit(@PathVariable Long id, @Valid @RequestBody SrmRequests.StatusRequest request) {
        return R.ok(supplierService.submit(id, request));
    }

    /** 撤回审批流程。 */
    @PostMapping("/{id}/withdraw") @PreAuthorize("hasAuthority('srm:supplier:withdraw')") @SrmDataScope(permissionCode = "srm:supplier:withdraw")
    @OperLog(module = "SRM供应商审批", operType = OperType.UPDATE, idExpr = "#id")
    public R<SrmViews.SupplierVO> withdraw(@PathVariable Long id, @Valid @RequestBody SrmRequests.StatusRequest request) {
        return R.ok(supplierService.withdraw(id, request));
    }

    /** 取消审批流程。 */
    @PostMapping("/{id}/cancel") @PreAuthorize("hasAuthority('srm:supplier:cancel')") @SrmDataScope(permissionCode = "srm:supplier:cancel")
    @OperLog(module = "SRM供应商审批", operType = OperType.UPDATE, idExpr = "#id")
    public R<SrmViews.SupplierVO> cancel(@PathVariable Long id, @Valid @RequestBody SrmRequests.StatusRequest request) {
        return R.ok(supplierService.cancel(id, request));
    }

    /** 冻结供应商。 */
    @PostMapping("/{id}/suspend") @PreAuthorize("hasAuthority('srm:supplier:suspend')") @SrmDataScope(permissionCode = "srm:supplier:suspend")
    @OperLog(module = "SRM供应商冻结", operType = OperType.UPDATE, idExpr = "#id")
    public R<SrmViews.SupplierVO> suspend(@PathVariable Long id, @Valid @RequestBody SrmRequests.StatusRequest request) {
        return R.ok(supplierService.suspend(id, request));
    }

    /** 恢复暂停合作的供应商。 */
    @PostMapping("/{id}/resume") @PreAuthorize("hasAuthority('srm:supplier:resume')") @SrmDataScope(permissionCode = "srm:supplier:resume")
    @OperLog(module = "SRM供应商恢复", operType = OperType.UPDATE, idExpr = "#id")
    public R<SrmViews.SupplierVO> resume(@PathVariable Long id, @Valid @RequestBody SrmRequests.StatusRequest request) {
        return R.ok(supplierService.resume(id, request));
    }

    /** 使用黑名单专用权限恢复供应商。 */
    @PostMapping({"/{id}/restore-from-blacklist", "/{id}/restore"})
    @PreAuthorize("hasAuthority('srm:supplier:restore')")
    @SrmDataScope(permissionCode = "srm:supplier:restore")
    @OperLog(module = "SRM供应商黑名单", operType = OperType.UPDATE, idExpr = "#id")
    public R<SrmViews.SupplierVO> restoreFromBlacklist(
            @PathVariable Long id, @Valid @RequestBody SrmRequests.StatusRequest request) {
        return R.ok(supplierService.restoreFromBlacklist(id, request));
    }

    /** 加入黑名单。 */
    @PostMapping("/{id}/blacklist") @PreAuthorize("hasAuthority('srm:supplier:blacklist')") @SrmDataScope(permissionCode = "srm:supplier:blacklist")
    @OperLog(module = "SRM供应商黑名单", operType = OperType.UPDATE, idExpr = "#id")
    public R<SrmViews.SupplierVO> blacklist(@PathVariable Long id, @Valid @RequestBody SrmRequests.StatusRequest request) {
        return R.ok(supplierService.blacklist(id, request));
    }

    /** 淘汰供应商。 */
    @PostMapping("/{id}/eliminate") @PreAuthorize("hasAuthority('srm:supplier:eliminate')") @SrmDataScope(permissionCode = "srm:supplier:eliminate")
    @OperLog(module = "SRM供应商淘汰", operType = OperType.UPDATE, idExpr = "#id")
    public R<SrmViews.SupplierVO> eliminate(@PathVariable Long id, @Valid @RequestBody SrmRequests.StatusRequest request) {
        return R.ok(supplierService.eliminate(id, request));
    }
}
