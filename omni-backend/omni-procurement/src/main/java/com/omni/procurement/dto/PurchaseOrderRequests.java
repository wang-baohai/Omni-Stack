package com.omni.procurement.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 采购订单请求 DTO 集合。
 *
 * @author Omni-Stack Team
 */
public final class PurchaseOrderRequests {

    private PurchaseOrderRequests() {
    }

    /** 采购订单分页查询。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Query extends ProcPageQuery {
        @Serial private static final long serialVersionUID = 1L;
        /** 单号、标题或供应商名称关键词。 */ @Size(max = 100) private String keyword;
        /** 来源 RFQ ID。 */ @Positive private Long rfqId;
        /** 供应商 ID。 */ @Positive private Long supplierId;
        /** 订单状态。 */
        @Pattern(regexp = "DRAFT|SENT|CONFIRMED|PARTIAL_RECEIVED|RECEIVED|CLOSED|CANCELLED")
        private String status;
        /** 预计交付开始日期。 */ @DateTimeFormat(pattern = "yyyy-MM-dd") private LocalDate expectedDeliveryFrom;
        /** 预计交付结束日期。 */ @DateTimeFormat(pattern = "yyyy-MM-dd") private LocalDate expectedDeliveryTo;
    }

    /** 更新采购订单可变交付信息。 */
    @Data
    public static class UpdateRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 乐观锁版本。 */ @NotNull @Min(0) private Integer version;
        /** 订单标题。 */ @NotBlank @Size(max = 200) private String title;
        /** 预计交付日期。 */ private LocalDate expectedDeliveryDate;
        /** 收货地址。 */ @NotBlank @Size(max = 500) private String deliveryAddress;
        /** 收货联系人。 */ @NotBlank @Size(max = 100) private String contactName;
        /** 收货联系电话。 */ @NotBlank @Size(max = 50) private String contactPhone;
    }

    /** RFQ 定点生成订单时使用的交付条款。 */
    @Data
    public static class AwardTerms implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 订单标题。 */ @NotBlank @Size(max = 200) private String title;
        /** 预计交付日期；为空时按报价最长交付天数计算。 */ private LocalDate expectedDeliveryDate;
        /** 收货地址。 */ @NotBlank @Size(max = 500) private String deliveryAddress;
        /** 收货联系人。 */ @NotBlank @Size(max = 100) private String contactName;
        /** 收货联系电话。 */ @NotBlank @Size(max = 50) private String contactPhone;
    }

    /** 带乐观锁版本的订单命令。 */
    @Data
    public static class VersionCommand implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 乐观锁版本。 */ @NotNull @Min(0) private Integer version;
    }
}
