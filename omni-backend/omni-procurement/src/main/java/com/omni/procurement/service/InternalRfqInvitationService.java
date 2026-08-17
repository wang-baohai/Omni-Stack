package com.omni.procurement.service;

import com.omni.procurement.dto.RfqViews;

import java.util.List;

/**
 * 提供给 SRM 的询价邀请只读服务。
 *
 * @author Omni-Stack Team
 */
public interface InternalRfqInvitationService {

    /**
     * 查询供应商已经收到的邀请，包含已截止的历史邀请。
     *
     * @param tenantId 租户 ID
     * @param supplierId 供应商 ID
     * @return 邀请摘要
     */
    List<RfqViews.InternalInvitationSummary> list(Long tenantId, Long supplierId);

    /**
     * 查询一个已发送邀请及其行快照。
     *
     * @param tenantId 租户 ID
     * @param rfqId 询价单 ID
     * @param supplierId 供应商 ID
     * @return 邀请详情
     */
    RfqViews.InternalInvitationDetail get(Long tenantId, Long rfqId, Long supplierId);
}
