package com.omni.procurement.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 审批路由请求 DTO 集合。
 *
 * @author Omni-Stack Team
 */
public final class ApprovalRouteRequests {

    private ApprovalRouteRequests() {
    }

    /** 审批路由分页查询。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RouteQuery extends ProcPageQuery {
        @Serial private static final long serialVersionUID = 1L;
        /** 规则名称、技术编码或品类编码关键词。 */ @Size(max = 100) private String keyword;
        /** 精确品类编码或通配符 *。 */ @Size(max = 50) private String categoryCode;
        /** ACTIVE/INACTIVE。 */ @Pattern(regexp = "ACTIVE|INACTIVE") private String status;
    }

    /** 创建审批路由。 */
    @Data
    public static class CreateRouteRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 兼容旧调用方的规则编码输入；新调用方不得传入。 */
        @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]*")
        private String routeCode;
        /** 业务可读的规则名称；兼容期允许旧调用方不传。 */ @Size(max = 100) private String routeName;
        /** 精确品类编码或通配符 *。 */ @NotBlank @Size(max = 50) private String categoryCode;
        /** 金额下界，包含。 */
        @NotNull @Digits(integer = 15, fraction = 4)
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
                using = Jackson2DecimalStringDeserializer.class)
        @tools.jackson.databind.annotation.JsonDeserialize(
                using = Jackson3DecimalStringDeserializer.class)
        private BigDecimal minAmount;
        /** 金额上界，不包含；null 表示无上限。 */
        @Digits(integer = 15, fraction = 4)
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
                using = Jackson2DecimalStringDeserializer.class)
        @tools.jackson.databind.annotation.JsonDeserialize(
                using = Jackson3DecimalStringDeserializer.class)
        private BigDecimal maxAmount;
        /** 已发布工作流模型版本 ID。 */ @NotNull @Positive private Long modelVersionId;
        /** 兼容高级调用方的排序优先级；为空时由服务端生成。 */ @Min(0) private Integer priority;
        /** ACTIVE/INACTIVE。 */ @Pattern(regexp = "ACTIVE|INACTIVE") private String status = "ACTIVE";
    }

    /** 更新审批路由。 */
    @Data
    public static class UpdateRouteRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 乐观锁版本。 */ @NotNull @Min(0) private Integer version;
        /** 业务可读的规则名称；兼容期允许旧调用方不传。 */ @Size(max = 100) private String routeName;
        /** 精确品类编码或通配符 *。 */ @NotBlank @Size(max = 50) private String categoryCode;
        /** 金额下界，包含。 */
        @NotNull @Digits(integer = 15, fraction = 4)
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
                using = Jackson2DecimalStringDeserializer.class)
        @tools.jackson.databind.annotation.JsonDeserialize(
                using = Jackson3DecimalStringDeserializer.class)
        private BigDecimal minAmount;
        /** 金额上界，不包含；null 表示无上限。 */
        @Digits(integer = 15, fraction = 4)
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
                using = Jackson2DecimalStringDeserializer.class)
        @tools.jackson.databind.annotation.JsonDeserialize(
                using = Jackson3DecimalStringDeserializer.class)
        private BigDecimal maxAmount;
        /** 已发布工作流模型版本 ID。 */ @NotNull @Positive private Long modelVersionId;
        /** 兼容高级调用方的排序优先级；为空时保留既有值。 */ @Min(0) private Integer priority;
        /** ACTIVE/INACTIVE。 */ @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") private String status;
    }
}
