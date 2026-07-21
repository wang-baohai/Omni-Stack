package com.omni.srm.controller;

import com.omni.common.core.operlog.OperLog;
import com.omni.common.core.operlog.OperType;
import com.omni.common.core.result.R;
import com.omni.srm.dto.SrmRequests;
import com.omni.srm.dto.SrmViews;
import com.omni.srm.security.SrmDataScope;
import com.omni.srm.service.BankAccountService;
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

/** SRM 供应商银行账户控制器。 */
@RestController
@RequestMapping("/api/srm/supplier/{supplierId}/bank-account")
@RequiredArgsConstructor
public class BankAccountController {
    private final BankAccountService bankAccountService;

    /** 查询银行账户列表。 */
    @GetMapping({"", "/list"}) @PreAuthorize("hasAuthority('srm:bank-account:list')") @SrmDataScope(permissionCode = "srm:bank-account:list")
    public R<List<SrmViews.BankAccountVO>> list(@PathVariable Long supplierId) {
        return R.ok(bankAccountService.list(supplierId));
    }

    /** 查询银行账户详情。 */
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('srm:bank-account:list')") @SrmDataScope(permissionCode = "srm:bank-account:list")
    public R<SrmViews.BankAccountVO> get(@PathVariable Long supplierId, @PathVariable Long id) {
        return R.ok(bankAccountService.get(supplierId, id));
    }

    /** 创建银行账户。 */
    @PostMapping @PreAuthorize("hasAuthority('srm:bank-account:create')") @SrmDataScope(permissionCode = "srm:bank-account:create")
    @OperLog(module = "SRM银行账户", operType = OperType.CREATE, recordSnapshot = false, excludeFields = {"accountNo"})
    public R<SrmViews.BankAccountVO> create(@PathVariable Long supplierId,
                                            @Valid @RequestBody SrmRequests.CreateBankAccountRequest request) {
        return R.ok(bankAccountService.create(supplierId, request));
    }

    /** 更新银行账户。 */
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('srm:bank-account:update')") @SrmDataScope(permissionCode = "srm:bank-account:update")
    @OperLog(module = "SRM银行账户", operType = OperType.UPDATE, idExpr = "#id", recordSnapshot = false,
            excludeFields = {"accountNo"})
    public R<SrmViews.BankAccountVO> update(@PathVariable Long supplierId, @PathVariable Long id,
                                            @Valid @RequestBody SrmRequests.UpdateBankAccountRequest request) {
        return R.ok(bankAccountService.update(supplierId, id, request));
    }

    /** 删除银行账户。 */
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('srm:bank-account:delete')") @SrmDataScope(permissionCode = "srm:bank-account:delete")
    @OperLog(module = "SRM银行账户", operType = OperType.DELETE, idExpr = "#id")
    public R<Void> delete(@PathVariable Long supplierId, @PathVariable Long id, @RequestParam Integer version) {
        bankAccountService.delete(supplierId, id, version);
        return R.ok();
    }
}
