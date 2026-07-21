package com.omni.srm.controller;

import com.omni.common.core.operlog.OperLog;
import com.omni.common.core.operlog.OperType;
import com.omni.common.core.result.R;
import com.omni.srm.dto.SrmRequests;
import com.omni.srm.dto.SrmViews;
import com.omni.srm.service.SupplierPortalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** SRM 供应商门户控制器。 */
@RestController
@RequestMapping("/api/srm/portal")
@RequiredArgsConstructor
public class PortalController {
    private final SupplierPortalService supplierPortalService;

    /** 供应商入驻。 */
    @PostMapping("/enroll") @PreAuthorize("hasAuthority('srm:portal:enroll')")
    @OperLog(module = "SRM门户入驻", operType = OperType.CREATE, recordSnapshot = false,
            excludeFields = {"inviteToken", "creditCode", "phone", "email"})
    public R<SrmViews.EnrollmentVO> enroll(@Valid @RequestBody SrmRequests.EnrollRequest request) {
        return R.ok(supplierPortalService.enroll(request));
    }

    /** 查询当前账号的入驻进度。 */
    @GetMapping("/enrollment")
    @PreAuthorize("hasAnyAuthority('srm:portal:enroll', 'srm:portal:profile')")
    public R<SrmViews.EnrollmentVO> getEnrollment() {
        return R.ok(supplierPortalService.getEnrollment());
    }

    /** 重试失败的角色分配。 */
    @PostMapping("/enrollment/retry")
    @PreAuthorize("hasAnyAuthority('srm:portal:enroll', 'srm:portal:profile')")
    @OperLog(module = "SRM门户入驻", operType = OperType.UPDATE, recordSnapshot = false)
    public R<SrmViews.EnrollmentVO> retryEnrollment() {
        return R.ok(supplierPortalService.retryEnrollment());
    }

    /** 查询门户企业信息。 */
    @GetMapping("/profile")
    @PreAuthorize("hasRole('SUPPLIER') and hasAuthority('srm:portal:profile')")
    public R<SrmViews.PortalProfileVO> getProfile() {
        return R.ok(supplierPortalService.getProfile());
    }

    /** 更新门户企业信息。 */
    @PutMapping("/profile")
    @PreAuthorize("hasRole('SUPPLIER') and hasAuthority('srm:portal:profile')")
    @OperLog(module = "SRM门户信息", operType = OperType.UPDATE, recordSnapshot = false,
            excludeFields = {"phone", "email"})
    public R<SrmViews.PortalProfileVO> updateProfile(@Valid @RequestBody SrmRequests.UpdateProfileRequest request) {
        return R.ok(supplierPortalService.updateProfile(request));
    }

    /** 将审核驳回的当前门户企业重新提交审核。 */
    @PostMapping("/profile/submit")
    @PreAuthorize("hasRole('SUPPLIER') and hasAuthority('srm:portal:profile')")
    @OperLog(module = "SRM门户信息", operType = OperType.UPDATE, recordSnapshot = false)
    public R<SrmViews.PortalProfileVO> resubmitProfile(
            @Valid @RequestBody SrmRequests.StatusRequest request) {
        return R.ok(supplierPortalService.resubmitProfile(request));
    }
}
