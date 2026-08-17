package com.omni.procurement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 收货单请求 DTO 集合。
 *
 * @author Omni-Stack Team
 */
public final class GoodsReceiptRequests {

    private GoodsReceiptRequests() {
    }

    /** 收货单分页查询。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Query extends ProcPageQuery {
        @Serial private static final long serialVersionUID = 1L;
        /** 收货单号或采购订单号关键词。 */ @Size(max = 100) private String keyword;
        /** 采购订单 ID。 */ @Positive private Long poId;
        /** 收货单状态。 */ @Pattern(regexp = "DRAFT|CONFIRMED") private String status;
        /** 收货时间起点。 */
        @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") private LocalDateTime receiveTimeFrom;
        /** 收货时间终点。 */
        @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") private LocalDateTime receiveTimeTo;
    }

    /** 创建收货草稿。 */
    @Data
    public static class CreateRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 采购订单 ID。 */ @NotNull @Positive private Long poId;
        /** 业务收货时间。 */ @NotNull private LocalDateTime receiveTime;
        /** 收货备注。 */ @Size(max = 500) private String remark;
        /** 收货行。 */ @Valid @NotEmpty @Size(max = 200) private List<LineInput> lines;
    }

    /** 收货行输入。 */
    @Data
    public static class LineInput implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 采购订单行 ID。 */ @NotNull @Positive private Long poLineId;
        /** 本次收货数量。 */
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 6)
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
                using = Jackson2DecimalStringDeserializer.class)
        @tools.jackson.databind.annotation.JsonDeserialize(
                using = Jackson3DecimalStringDeserializer.class)
        private BigDecimal receivedQuantity;
        /** 初始质检状态。 */
        @NotNull @Pattern(regexp = "PASS|FAIL|PENDING") private String qualityStatus;
        /** 行备注。 */ @Size(max = 500) private String remark;
    }

    /** 带乐观锁版本的收货命令。 */
    @Data
    public static class VersionCommand implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 乐观锁版本。 */ @NotNull @Min(0) private Integer version;
    }

    /** 后续质检结果命令。 */
    @Data
    public static class QualityResultCommand implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 收货单乐观锁版本。 */ @NotNull @Min(0) private Integer version;
        /** 本次需要从 PENDING 结转的行。 */
        @Valid @NotEmpty @Size(max = 200) private List<QualityResultLine> lines;
    }

    /** 单行后续质检结果。 */
    @Data
    public static class QualityResultLine implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 收货行 ID。 */ @NotNull @Positive private Long goodsReceiptLineId;
        /** 最终质检结果。 */ @NotNull @Pattern(regexp = "PASS|FAIL") private String qualityStatus;
    }
}
