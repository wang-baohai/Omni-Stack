package com.omni.srm.dto.quotation;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** Procurement 内部 RFQ 邀请摘要契约。 */
@Data
public class ProcurementRfqInvitationSummary implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户 ID。 */
    private Long tenantId;

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

    /** 受邀供应商 ID。 */
    private Long supplierId;

    /** 报价截止时间。 */
    private LocalDateTime quotationDeadline;

    /** RFQ 币种。 */
    private String currencyCode;

    /** 邀请时间。 */
    private LocalDateTime invitedTime;
}
