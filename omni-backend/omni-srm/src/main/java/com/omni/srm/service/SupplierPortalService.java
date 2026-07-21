package com.omni.srm.service;

import com.omni.srm.dto.SrmRequests;
import com.omni.srm.dto.SrmViews;

/** SRM 供应商门户服务。 */
public interface SupplierPortalService {
    /** 供应商入驻。 */ SrmViews.EnrollmentVO enroll(SrmRequests.EnrollRequest request);
    /** 查询当前用户的入驻进度。 */ SrmViews.EnrollmentVO getEnrollment();
    /** 重试失败的角色分配 Saga。 */ SrmViews.EnrollmentVO retryEnrollment();
    /** 查询门户企业信息。 */ SrmViews.PortalProfileVO getProfile();
    /** 更新门户企业信息。 */ SrmViews.PortalProfileVO updateProfile(SrmRequests.UpdateProfileRequest request);
    /** 将审核驳回的当前门户企业重新提交审核。 */
    SrmViews.PortalProfileVO resubmitProfile(SrmRequests.StatusRequest request);
    /** 获取当前登录用户的门户供应商 ID，不存在则抛出业务异常。 */ Long getCurrentSupplierId();
}
