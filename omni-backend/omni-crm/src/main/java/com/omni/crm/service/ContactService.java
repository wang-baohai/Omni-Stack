package com.omni.crm.service;

import com.omni.common.core.result.PageResult;
import com.omni.crm.dto.CrmRequests;
import com.omni.crm.dto.CrmViews;

/** CRM 联系人应用服务。 */
public interface ContactService {
    /** 分页查询。 */ PageResult<CrmViews.ContactVO> list(CrmRequests.ContactQuery query);
    /** 查询客户联系人。 */ PageResult<CrmViews.ContactVO> listByCustomer(Long customerId, CrmRequests.ContactQuery query);
    /** 查询详情。 */ CrmViews.ContactVO get(Long id);
    /** 创建联系人。 */ CrmViews.ContactVO create(Long customerId, CrmRequests.CreateContactRequest request);
    /** 更新联系人。 */ CrmViews.ContactVO update(Long id, CrmRequests.UpdateContactRequest request);
    /** 删除联系人。 */ void delete(Long id, Integer version);
    /** 设置主要联系人。 */ CrmViews.ContactVO setPrimary(Long id, Integer version);
}
