package com.omni.srm.dto.quotation;

import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 供应商门户 RFQ 邀请摘要视图。 */
@Data
public class QuotationInvitationSummaryVO implements Serializable {

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

    /** 已提交报价 ID。 */
    private Long quotationId;

    /** 已提交报价版本。 */
    private Integer quotationVersion;

    /** 已提交报价状态。 */
    private String quotationStatus;

    /** 已提交报价总金额。 */
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(
            using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal totalAmount;

    /** 已提交报价有效期。 */
    private LocalDateTime validUntil;
}
