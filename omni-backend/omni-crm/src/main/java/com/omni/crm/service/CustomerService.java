package com.omni.crm.service;

import com.omni.common.core.result.PageResult;
import com.omni.crm.dto.CrmRequests;
import com.omni.crm.dto.CrmViews;

import java.util.List;

/** CRM 客户应用服务。 */
public interface CustomerService {
    /** 分页查询。 */ PageResult<CrmViews.CustomerVO> list(CrmRequests.CustomerQuery query);
    /** 查询详情。 */ CrmViews.CustomerVO get(Long id);
    /** 查询客户 360。 */ CrmViews.CustomerOverviewVO overview(Long id);
    /** 创建客户。 */ CrmViews.CustomerVO create(CrmRequests.CreateCustomerRequest request);
    /** 更新客户。 */ CrmViews.CustomerVO update(Long id, CrmRequests.UpdateCustomerRequest request);
    /** 删除客户。 */ void delete(Long id, Integer version);
    /** 重复检测。 */ List<CrmViews.DuplicateCandidateVO> duplicateCheck(CrmRequests.CustomerDuplicateRequest request);
    /** 变更普通状态。 */ CrmViews.CustomerVO changeStatus(Long id, CrmRequests.CustomerStatusRequest request);
    /** 加入黑名单。 */ CrmViews.CustomerVO blacklist(Long id, CrmRequests.VersionRequest request);
    /** 从黑名单恢复。 */ CrmViews.CustomerVO restoreFromBlacklist(Long id, CrmRequests.VersionRequest request);
    /** 转移负责人。 */ CrmViews.CustomerVO transfer(Long id, CrmRequests.TransferCustomerRequest request);
}
