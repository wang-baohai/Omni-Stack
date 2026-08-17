package com.omni.srm.dto.quotation;

import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 供应商报价头行完整视图。 */
@Data
public class QuotationVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 报价 ID。 */
    private Long id;

    /** RFQ ID。 */
    private Long rfqId;

    /** RFQ 编号快照。 */
    private String rfqNo;

    /** 供应商 ID。 */
    private Long supplierId;

    /** 供应商名称快照。 */
    private String supplierNameSnapshot;

    /** 报价时间。 */
    private LocalDateTime quotationTime;

    /** 报价有效期。 */
    private LocalDateTime validUntil;

    /** 总金额。 */
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(
            using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal totalAmount;

    /** 币种代码。 */
    private String currencyCode;

    /** 报价状态。 */
    private String status;

    /** 乐观锁版本。 */
    private Integer version;

    /** 报价行。 */
    private List<QuotationLineVO> lines;
}
