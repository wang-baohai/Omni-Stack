package com.omni.procurement.service;

import com.omni.procurement.dto.RfqContracts;

/**
 * SRM 报价提交事件 Inbox 处理服务。
 *
 * @author Omni-Stack Team
 */
public interface QuotationSubmittedService {

    /**
     * 幂等处理报价提交事件。
     *
     * @param event SRM 领域事件信封
     * @return 是否推进了邀请报价版本
     */
    boolean handle(RfqContracts.QuotationSubmittedEvent event);
}
