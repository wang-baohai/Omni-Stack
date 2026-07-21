package com.omni.srm.service;

import com.omni.common.core.result.PageResult;
import com.omni.srm.dto.SrmRequests;
import com.omni.srm.dto.SrmViews;

/** SRM 供应商应用服务。 */
public interface SupplierService {
    /** 分页查询。 */ PageResult<SrmViews.SupplierVO> list(SrmRequests.SupplierQuery query);
    /** 查询详情。 */ SrmViews.SupplierDetailVO get(Long id);
    /** 查询供应商 360。 */ SrmViews.SupplierOverviewVO overview(Long id);
    /** 创建供应商。 */ SrmViews.SupplierVO create(SrmRequests.CreateSupplierRequest request);
    /** 更新供应商。 */ SrmViews.SupplierVO update(Long id, SrmRequests.UpdateSupplierRequest request);
    /** 转移负责人。 */ SrmViews.SupplierVO transferOwner(Long id, SrmRequests.TransferOwnerRequest request);
    /** 删除供应商。 */ void delete(Long id, Integer version);
    /** 提交审核。 */ SrmViews.SupplierVO submit(Long id, SrmRequests.StatusRequest request);
    /** 审核通过。 */ SrmViews.SupplierVO approve(Long id, SrmRequests.StatusRequest request);
    /** 审核驳回。 */ SrmViews.SupplierVO reject(Long id, SrmRequests.StatusRequest request);
    /** 冻结。 */ SrmViews.SupplierVO suspend(Long id, SrmRequests.StatusRequest request);
    /** 解冻恢复。 */ SrmViews.SupplierVO resume(Long id, SrmRequests.StatusRequest request);
    /** 从黑名单恢复。 */ SrmViews.SupplierVO restoreFromBlacklist(Long id, SrmRequests.StatusRequest request);
    /** 加入黑名单。 */ SrmViews.SupplierVO blacklist(Long id, SrmRequests.StatusRequest request);
    /** 淘汰。 */ SrmViews.SupplierVO eliminate(Long id, SrmRequests.StatusRequest request);
}
