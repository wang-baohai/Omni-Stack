package com.omni.srm.dto.quotation;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/** 供应商报价行提交请求。 */
@Data
public class QuotationLineRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Procurement 询价行 ID。 */
    @NotNull
    @Positive
    private Long rfqLineId;

    /** 供应商单价。 */
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @Digits(integer = 13, fraction = 6)
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
            using = Jackson2DecimalStringDeserializer.class)
    @tools.jackson.databind.annotation.JsonDeserialize(
            using = Jackson3DecimalStringDeserializer.class)
    private BigDecimal unitPrice;

    /** 交付天数。 */
    @NotNull
    @Min(0)
    @Max(3650)
    private Integer deliveryDays;

    /** 供应商备注。 */
    @Size(max = 500)
    private String remark;
}
