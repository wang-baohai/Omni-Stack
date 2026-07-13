package com.omni.crm.controller;

import com.omni.common.core.operlog.OperLog;
import com.omni.common.core.operlog.OperType;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import com.omni.crm.dto.CrmRequests;
import com.omni.crm.dto.CrmViews;
import com.omni.crm.security.CrmDataScope;
import com.omni.crm.service.CustomerService;
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

import java.util.List;

/** CRM 客户控制器。 */
@RestController
@RequestMapping("/api/crm/customer")
@RequiredArgsConstructor
public class CustomerController {
    private final CustomerService customerService;

    /** 分页查询客户。 */
    @GetMapping("/list") @PreAuthorize("hasAuthority('crm:customer:list')") @CrmDataScope(permissionCode = "crm:customer:list")
    public R<PageResult<CrmViews.CustomerVO>> list(@Valid CrmRequests.CustomerQuery query) { return R.ok(customerService.list(query)); }

    /** 查询客户详情。 */
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('crm:customer:list')") @CrmDataScope(permissionCode = "crm:customer:list")
    public R<CrmViews.CustomerVO> get(@PathVariable Long id) { return R.ok(customerService.get(id)); }

    /** 查询客户 360。 */
    @GetMapping("/{id}/overview") @PreAuthorize("hasAuthority('crm:customer:list')") @CrmDataScope(permissionCode = "crm:customer:list")
    public R<CrmViews.CustomerOverviewVO> overview(@PathVariable Long id) { return R.ok(customerService.overview(id)); }

    /** 创建客户。 */
    @PostMapping @PreAuthorize("hasAuthority('crm:customer:create')") @CrmDataScope(permissionCode = "crm:customer:create")
    @OperLog(module = "CRM客户", operType = OperType.CREATE, recordSnapshot = false, excludeFields = {"name"})
    public R<CrmViews.CustomerVO> create(@Valid @RequestBody CrmRequests.CreateCustomerRequest request) {
        return R.ok(customerService.create(request));
    }

    /** 更新客户。 */
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('crm:customer:update')") @CrmDataScope(permissionCode = "crm:customer:update")
    @OperLog(module = "CRM客户", operType = OperType.UPDATE, idExpr = "#id", recordSnapshot = false,
            excludeFields = {"name"})
    public R<CrmViews.CustomerVO> update(@PathVariable Long id,
                                         @Valid @RequestBody CrmRequests.UpdateCustomerRequest request) {
        return R.ok(customerService.update(id, request));
    }

    /** 删除客户。 */
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('crm:customer:delete')") @CrmDataScope(permissionCode = "crm:customer:delete")
    @OperLog(module = "CRM客户", operType = OperType.DELETE, idExpr = "#id")
    public R<Void> delete(@PathVariable Long id, @RequestParam Integer version) { customerService.delete(id, version); return R.ok(); }

    /** 检查客户重复候选。 */
    @PostMapping("/duplicate-check") @PreAuthorize("hasAuthority('crm:customer:list')") @CrmDataScope(permissionCode = "crm:customer:list")
    public R<List<CrmViews.DuplicateCandidateVO>> duplicate(@Valid @RequestBody CrmRequests.CustomerDuplicateRequest request) {
        return R.ok(customerService.duplicateCheck(request));
    }

    /** 变更普通客户状态。 */
    @PostMapping("/{id}/status") @PreAuthorize("hasAuthority('crm:customer:status')") @CrmDataScope(permissionCode = "crm:customer:status")
    @OperLog(module = "CRM客户", operType = OperType.UPDATE, idExpr = "#id")
    public R<CrmViews.CustomerVO> status(@PathVariable Long id,
                                         @Valid @RequestBody CrmRequests.CustomerStatusRequest request) {
        return R.ok(customerService.changeStatus(id, request));
    }

    /** 转移客户负责人。 */
    @PostMapping("/{id}/transfer") @PreAuthorize("hasAuthority('crm:customer:transfer')") @CrmDataScope(permissionCode = "crm:customer:transfer")
    @OperLog(module = "CRM客户转移", operType = OperType.UPDATE, idExpr = "#id")
    public R<CrmViews.CustomerVO> transfer(@PathVariable Long id,
                                           @Valid @RequestBody CrmRequests.TransferCustomerRequest request) {
        return R.ok(customerService.transfer(id, request));
    }

    /** 加入客户黑名单。 */
    @PostMapping("/{id}/blacklist") @PreAuthorize("hasAuthority('crm:customer:blacklist')") @CrmDataScope(permissionCode = "crm:customer:blacklist")
    @OperLog(module = "CRM客户黑名单", operType = OperType.UPDATE, idExpr = "#id")
    public R<CrmViews.CustomerVO> blacklist(@PathVariable Long id, @Valid @RequestBody CrmRequests.VersionRequest request) {
        return R.ok(customerService.blacklist(id, request));
    }

    /** 从黑名单恢复客户。 */
    @PostMapping("/{id}/restore-from-blacklist") @PreAuthorize("hasAuthority('crm:customer:blacklist')")
    @CrmDataScope(permissionCode = "crm:customer:blacklist")
    @OperLog(module = "CRM客户黑名单", operType = OperType.UPDATE, idExpr = "#id")
    public R<CrmViews.CustomerVO> restore(@PathVariable Long id, @Valid @RequestBody CrmRequests.VersionRequest request) {
        return R.ok(customerService.restoreFromBlacklist(id, request));
    }
}
