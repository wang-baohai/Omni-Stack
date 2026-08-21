package com.omni.procurement.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 请购审批规则试算请求集合。
 *
 * @author Omni-Stack Team
 */
public final class ApprovalRouteInsightRequests {

    private ApprovalRouteInsightRequests() {
    }

    /** 无副作用规则匹配请求。 */
    @Data
    public static class MatchPreviewRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 具体物料品类编码。 */ @NotBlank @Size(max = 50) private String categoryCode;
        /** 请购总金额，必须使用十进制字符串。 */
        @NotNull @Digits(integer = 15, fraction = 4)
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
                using = Jackson2DecimalStringDeserializer.class)
        @tools.jackson.databind.annotation.JsonDeserialize(
                using = Jackson3DecimalStringDeserializer.class)
        private BigDecimal totalAmount;
    }
}
