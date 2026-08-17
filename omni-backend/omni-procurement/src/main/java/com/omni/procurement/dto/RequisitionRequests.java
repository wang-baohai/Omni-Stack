package com.omni.procurement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 请购申请请求 DTO 集合。
 *
 * @author Omni-Stack Team
 */
public final class RequisitionRequests {

    private RequisitionRequests() {
    }

    /** 请购分页查询。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Query extends ProcPageQuery {
        @Serial private static final long serialVersionUID = 1L;
        /** 单号或标题关键词。 */ @Size(max = 100) private String keyword;
        /** 请购状态。 */
        @Pattern(regexp = "DRAFT|SUBMITTED|APPROVING|APPROVED|REJECTED|CANCELLED")
        private String status;
        /** 品类编码。 */ @Size(max = 64) private String categoryCode;
    }

    /** 创建请购。 */
    @Data
    public static class CreateRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 请购标题。 */ @NotBlank @Size(max = 200) private String title;
        /** 请购原因。 */ @Size(max = 1000) private String reason;
        /** 请购明细。 */ @Valid @NotEmpty @Size(max = 200) private List<LineInput> lines;
    }

    /** 更新请购。 */
    @Data
    public static class UpdateRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 乐观锁版本。 */ @NotNull @Min(0) private Integer version;
        /** 请购标题。 */ @NotBlank @Size(max = 200) private String title;
        /** 请购原因。 */ @Size(max = 1000) private String reason;
        /** 请购明细。 */ @Valid @NotEmpty @Size(max = 200) private List<LineInput> lines;
    }

    /** 请购明细输入，仅接受物料 ID 和业务数量金额。 */
    @Data
    public static class LineInput implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 物料 ID。 */ @NotNull @Positive private Long materialId;
        /** 数量。 */
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 6)
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
                using = Jackson2DecimalStringDeserializer.class)
        @tools.jackson.databind.annotation.JsonDeserialize(
                using = Jackson3DecimalStringDeserializer.class)
        private BigDecimal quantity;
        /** 预估单价。 */
        @NotNull @DecimalMin(value = "0", inclusive = true) @Digits(integer = 13, fraction = 6)
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
                using = Jackson2DecimalStringDeserializer.class)
        @tools.jackson.databind.annotation.JsonDeserialize(
                using = Jackson3DecimalStringDeserializer.class)
        private BigDecimal estimatedUnitPrice;
        /** 行备注。 */ @Size(max = 500) private String remark;
    }

    /** 带乐观锁版本的状态命令。 */
    @Data
    public static class VersionCommand implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 乐观锁版本。 */ @NotNull @Min(0) private Integer version;
    }
}
