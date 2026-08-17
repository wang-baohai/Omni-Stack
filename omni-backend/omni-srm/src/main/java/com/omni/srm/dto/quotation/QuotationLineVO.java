package com.omni.srm.dto.quotation;

import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/** 供应商报价行视图。 */
@Data
public class QuotationLineVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 报价行 ID。 */
    private Long id;

    /** RFQ 行 ID。 */
    private Long rfqLineId;

    /** 物料编码快照。 */
    private String materialCode;

    /** 物料名称快照。 */
    private String materialName;

    /** 计量单位快照。 */
    private String unit;

    /** 单价。 */
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(
            using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal unitPrice;

    /** 数量快照。 */
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(
            using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal quantity;

    /** 行金额。 */
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(
            using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal lineAmount;

    /** 交付天数。 */
    private Integer deliveryDays;

    /** 供应商备注。 */
    private String remark;
}
