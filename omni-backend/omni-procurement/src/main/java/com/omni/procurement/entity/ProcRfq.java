package com.omni.procurement.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 询价单聚合根。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_rfq")
public class ProcRfq extends ProcOwnedEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户内询价单号。 */
    private String rfqNo;

    /** 来源请购申请 ID。 */
    private Long requisitionId;

    /** 询价标题。 */
    private String title;

    /** 报价截止时间。 */
    private LocalDateTime quotationDeadline;

    /** 请购币种快照。 */
    private String currencyCode;

    /** DRAFT/SENT/CLOSED/AWARDED/CANCELLED。 */
    private String status;

    /** 询价发送时间。 */
    private LocalDateTime sentTime;

    /** 定点供应商 ID。 */
    private Long awardedSupplierId;

    /** 中标报价 ID。 */
    private Long awardedQuotationId;

    /** 中标报价版本。 */
    private Integer awardedQuotationVersion;

    /** 定点时间。 */
    private LocalDateTime awardedTime;
}
