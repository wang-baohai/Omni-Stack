package com.omni.crm.controller;

import com.omni.common.core.operlog.OperLog;
import com.omni.common.core.operlog.OperType;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import com.omni.crm.dto.CrmRequests;
import com.omni.crm.dto.CrmViews;
import com.omni.common.service.datascope.ServiceDataScope;
import com.omni.crm.service.ContactService;
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

/** CRM 联系人控制器。 */
@RestController
@RequestMapping("/api/crm")
@RequiredArgsConstructor
public class ContactController {
    private final ContactService contactService;

    /** 分页查询联系人。 */
    @GetMapping("/contact/list") @PreAuthorize("hasAuthority('crm:contact:list')") @ServiceDataScope(permissionCode = "crm:contact:list")
    public R<PageResult<CrmViews.ContactVO>> list(@Valid CrmRequests.ContactQuery query) {
        return R.ok(contactService.list(query));
    }

    /** 查询指定客户的联系人。 */
    @GetMapping("/customer/{customerId}/contact/list") @PreAuthorize("hasAuthority('crm:contact:list')")
    @ServiceDataScope(permissionCode = "crm:contact:list")
    public R<PageResult<CrmViews.ContactVO>> listByCustomer(@PathVariable Long customerId,
                                                            @Valid CrmRequests.ContactQuery query) {
        return R.ok(contactService.listByCustomer(customerId, query));
    }

    /** 查询联系人详情。 */
    @GetMapping("/contact/{id}") @PreAuthorize("hasAuthority('crm:contact:list')") @ServiceDataScope(permissionCode = "crm:contact:list")
    public R<CrmViews.ContactVO> get(@PathVariable Long id) { return R.ok(contactService.get(id)); }

    /** 创建客户联系人。 */
    @PostMapping("/customer/{customerId}/contact") @PreAuthorize("hasAuthority('crm:contact:create')")
    @ServiceDataScope(permissionCode = "crm:contact:create")
    @OperLog(module = "CRM联系人", operType = OperType.CREATE, recordSnapshot = false, excludeFields = {"name"})
    public R<CrmViews.ContactVO> create(@PathVariable Long customerId,
                                        @Valid @RequestBody CrmRequests.CreateContactRequest request) {
        return R.ok(contactService.create(customerId, request));
    }

    /** 更新联系人。 */
    @PutMapping("/contact/{id}") @PreAuthorize("hasAuthority('crm:contact:update')") @ServiceDataScope(permissionCode = "crm:contact:update")
    @OperLog(module = "CRM联系人", operType = OperType.UPDATE, idExpr = "#id", recordSnapshot = false,
            excludeFields = {"name"})
    public R<CrmViews.ContactVO> update(@PathVariable Long id,
                                        @Valid @RequestBody CrmRequests.UpdateContactRequest request) {
        return R.ok(contactService.update(id, request));
    }

    /** 删除联系人。 */
    @DeleteMapping("/contact/{id}") @PreAuthorize("hasAuthority('crm:contact:delete')") @ServiceDataScope(permissionCode = "crm:contact:delete")
    @OperLog(module = "CRM联系人", operType = OperType.DELETE, idExpr = "#id")
    public R<Void> delete(@PathVariable Long id, @RequestParam Integer version) {
        contactService.delete(id, version); return R.ok();
    }

    /** 设置主要联系人。 */
    @PostMapping("/contact/{id}/primary") @PreAuthorize("hasAuthority('crm:contact:update')") @ServiceDataScope(permissionCode = "crm:contact:update")
    @OperLog(module = "CRM联系人", operType = OperType.UPDATE, idExpr = "#id")
    public R<CrmViews.ContactVO> primary(@PathVariable Long id, @Valid @RequestBody CrmRequests.VersionRequest request) {
        return R.ok(contactService.setPrimary(id, request.getVersion()));
    }
}
