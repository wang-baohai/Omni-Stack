package com.omni.srm.dto.quotation;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/** 供应商门户 RFQ 邀请详情视图。 */
@Data
public class QuotationInvitationDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** RFQ ID。 */
    private Long rfqId;

    /** RFQ 编号。 */
    private String rfqNo;

    /** RFQ 标题。 */
    private String title;

    /** RFQ 状态。 */
    private String status;

    /** 当前供应商邀请状态。 */
    private String invitationStatus;

    /** 报价截止时间。 */
    private LocalDateTime quotationDeadline;

    /** RFQ 币种。 */
    private String currencyCode;

    /** 邀请时间。 */
    private LocalDateTime invitedTime;

    /** RFQ 行快照。 */
    private List<ProcurementRfqInvitationLine> lines;

    /** 当前有效报价；未报价时为 null。 */
    private QuotationVO currentQuotation;
}
