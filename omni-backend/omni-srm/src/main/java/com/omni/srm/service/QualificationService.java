package com.omni.srm.service;

import com.omni.srm.dto.SrmRequests;
import com.omni.srm.dto.SrmViews;

import java.util.List;

/** SRM 供应商资质服务。 */
public interface QualificationService {
    /** 查询供应商资质列表。 */ List<SrmViews.QualificationVO> list(Long supplierId);
    /** 查询资质详情。 */ SrmViews.QualificationVO get(Long supplierId, Long id);
    /** 创建资质。 */ SrmViews.QualificationVO create(Long supplierId, SrmRequests.CreateQualificationRequest request);
    /** 更新资质。 */ SrmViews.QualificationVO update(Long supplierId, Long id,
                                                     SrmRequests.UpdateQualificationRequest request);
    /** 删除资质。 */ void delete(Long supplierId, Long id, Integer version);
}
