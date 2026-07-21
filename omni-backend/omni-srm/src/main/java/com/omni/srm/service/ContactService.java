package com.omni.srm.service;

import com.omni.srm.dto.SrmRequests;
import com.omni.srm.dto.SrmViews;

import java.util.List;

/** SRM 供应商联系人服务。 */
public interface ContactService {
    /** 查询供应商联系人列表。 */ List<SrmViews.ContactVO> list(Long supplierId);
    /** 查询联系人详情。 */ SrmViews.ContactVO get(Long supplierId, Long id);
    /** 创建联系人。 */ SrmViews.ContactVO create(Long supplierId, SrmRequests.CreateContactRequest request);
    /** 更新联系人。 */ SrmViews.ContactVO update(Long supplierId, Long id, SrmRequests.UpdateContactRequest request);
    /** 删除联系人。 */ void delete(Long supplierId, Long id, Integer version);
}
