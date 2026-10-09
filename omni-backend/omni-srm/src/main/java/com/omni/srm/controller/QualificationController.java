package com.omni.srm.controller;

import com.omni.common.core.operlog.OperLog;
import com.omni.common.core.operlog.OperType;
import com.omni.common.core.result.R;
import com.omni.srm.dto.SrmRequests;
import com.omni.srm.dto.SrmViews;
import com.omni.common.service.datascope.ServiceDataScope;
import com.omni.srm.service.QualificationService;
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

/** SRM 供应商资质控制器。 */
@RestController
@RequestMapping("/api/srm/supplier/{supplierId}/qualification")
@RequiredArgsConstructor
public class QualificationController {
    private final QualificationService qualificationService;

    /** 查询资质列表。 */
    @GetMapping({"", "/list"}) @PreAuthorize("hasAuthority('srm:qualification:list')") @ServiceDataScope(permissionCode = "srm:qualification:list")
    public R<List<SrmViews.QualificationVO>> list(@PathVariable Long supplierId) {
        return R.ok(qualificationService.list(supplierId));
    }

    /** 查询资质详情。 */
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('srm:qualification:list')") @ServiceDataScope(permissionCode = "srm:qualification:list")
    public R<SrmViews.QualificationVO> get(@PathVariable Long supplierId, @PathVariable Long id) {
        return R.ok(qualificationService.get(supplierId, id));
    }

    /** 创建资质。 */
    @PostMapping @PreAuthorize("hasAuthority('srm:qualification:create')") @ServiceDataScope(permissionCode = "srm:qualification:create")
    @OperLog(module = "SRM资质", operType = OperType.CREATE, recordSnapshot = false)
    public R<SrmViews.QualificationVO> create(@PathVariable Long supplierId,
                                              @Valid @RequestBody SrmRequests.CreateQualificationRequest request) {
        return R.ok(qualificationService.create(supplierId, request));
    }

    /** 更新资质。 */
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('srm:qualification:update')") @ServiceDataScope(permissionCode = "srm:qualification:update")
    @OperLog(module = "SRM资质", operType = OperType.UPDATE, idExpr = "#id", recordSnapshot = false)
    public R<SrmViews.QualificationVO> update(@PathVariable Long supplierId, @PathVariable Long id,
                                              @Valid @RequestBody SrmRequests.UpdateQualificationRequest request) {
        return R.ok(qualificationService.update(supplierId, id, request));
    }

    /** 删除资质。 */
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('srm:qualification:delete')") @ServiceDataScope(permissionCode = "srm:qualification:delete")
    @OperLog(module = "SRM资质", operType = OperType.DELETE, idExpr = "#id")
    public R<Void> delete(@PathVariable Long supplierId, @PathVariable Long id, @RequestParam Integer version) {
        qualificationService.delete(supplierId, id, version);
        return R.ok();
    }
}
