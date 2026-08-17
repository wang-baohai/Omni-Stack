package com.omni.srm.service;

import com.omni.srm.dto.quotation.QuotationInvitationDetailVO;
import com.omni.srm.dto.quotation.QuotationInvitationSummaryVO;
import com.omni.srm.dto.quotation.QuotationSubmitRequest;
import com.omni.srm.dto.quotation.QuotationVO;

import java.util.List;

/**
 * SRM 供应商报价服务。
 *
 * @author Omni-Stack Team
 */
public interface QuotationService {

    /**
     * 查询当前门户供应商收到的 RFQ 邀请。
     *
     * @return 邀请摘要列表
     */
    List<QuotationInvitationSummaryVO> listPortalInvitations();

    /**
     * 查询当前门户供应商的 RFQ 邀请详情。
     *
     * @param rfqId RFQ ID
     * @return 邀请详情
     */
    QuotationInvitationDetailVO getPortalInvitation(Long rfqId);

    /**
     * 幂等提交或更新当前门户供应商报价。
     *
     * @param request 报价请求
     * @return 保存后的服务端报价快照
     */
    QuotationVO submit(QuotationSubmitRequest request);

    /**
     * 为 Procurement 返回指定 RFQ 的有效报价头行快照。
     *
     * @param tenantId 租户 ID
     * @param rfqId RFQ ID
     * @return 有效报价快照列表
     */
    List<QuotationVO> listValidByRfq(Long tenantId, Long rfqId);
}
