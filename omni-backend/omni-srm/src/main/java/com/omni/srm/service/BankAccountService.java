package com.omni.srm.service;

import com.omni.srm.dto.SrmRequests;
import com.omni.srm.dto.SrmViews;

import java.util.List;

/** SRM 供应商银行账户服务。 */
public interface BankAccountService {
    /** 查询供应商银行账户列表。 */ List<SrmViews.BankAccountVO> list(Long supplierId);
    /** 查询银行账户详情。 */ SrmViews.BankAccountVO get(Long supplierId, Long id);
    /** 创建银行账户。 */ SrmViews.BankAccountVO create(Long supplierId, SrmRequests.CreateBankAccountRequest request);
    /** 更新银行账户。 */ SrmViews.BankAccountVO update(Long supplierId, Long id,
                                                       SrmRequests.UpdateBankAccountRequest request);
    /** 删除银行账户。 */ void delete(Long supplierId, Long id, Integer version);
}
