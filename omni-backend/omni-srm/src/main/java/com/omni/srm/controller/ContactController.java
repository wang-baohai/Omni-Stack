package com.omni.srm.controller;

import com.omni.common.core.operlog.OperLog;
import com.omni.common.core.operlog.OperType;
import com.omni.common.core.result.R;
import com.omni.srm.dto.SrmRequests;
import com.omni.srm.dto.SrmViews;
import com.omni.srm.security.SrmDataScope;
import com.omni.srm.service.ContactService;
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

/** SRM 供应商联系人控制器。 */
@RestController
@RequestMapping("/api/srm/supplier/{supplierId}/contact")
@RequiredArgsConstructor
public class ContactController {
    private final ContactService contactService;

    /** 查询联系人列表。 */
    @GetMapping({"", "/list"}) @PreAuthorize("hasAuthority('srm:contact:list')") @SrmDataScope(permissionCode = "srm:contact:list")
    public R<List<SrmViews.ContactVO>> list(@PathVariable Long supplierId) {
        return R.ok(contactService.list(supplierId));
    }

    /** 查询联系人详情。 */
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('srm:contact:list')") @SrmDataScope(permissionCode = "srm:contact:list")
    public R<SrmViews.ContactVO> get(@PathVariable Long supplierId, @PathVariable Long id) {
        return R.ok(contactService.get(supplierId, id));
    }

    /** 创建联系人。 */
    @PostMapping @PreAuthorize("hasAuthority('srm:contact:create')") @SrmDataScope(permissionCode = "srm:contact:create")
    @OperLog(module = "SRM联系人", operType = OperType.CREATE, recordSnapshot = false,
            excludeFields = {"mobile", "phone", "email"})
    public R<SrmViews.ContactVO> create(@PathVariable Long supplierId,
                                        @Valid @RequestBody SrmRequests.CreateContactRequest request) {
        return R.ok(contactService.create(supplierId, request));
    }

    /** 更新联系人。 */
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('srm:contact:update')") @SrmDataScope(permissionCode = "srm:contact:update")
    @OperLog(module = "SRM联系人", operType = OperType.UPDATE, idExpr = "#id", recordSnapshot = false,
            excludeFields = {"mobile", "phone", "email"})
    public R<SrmViews.ContactVO> update(@PathVariable Long supplierId, @PathVariable Long id,
                                        @Valid @RequestBody SrmRequests.UpdateContactRequest request) {
        return R.ok(contactService.update(supplierId, id, request));
    }

    /** 删除联系人。 */
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('srm:contact:delete')") @SrmDataScope(permissionCode = "srm:contact:delete")
    @OperLog(module = "SRM联系人", operType = OperType.DELETE, idExpr = "#id")
    public R<Void> delete(@PathVariable Long supplierId, @PathVariable Long id, @RequestParam Integer version) {
        contactService.delete(supplierId, id, version);
        return R.ok();
    }
}
