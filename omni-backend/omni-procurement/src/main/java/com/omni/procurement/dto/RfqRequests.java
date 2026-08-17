package com.omni.procurement.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 询价请求 DTO 集合。
 *
 * @author Omni-Stack Team
 */
public final class RfqRequests {

    private RfqRequests() {
    }

    /** 询价分页查询。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Query extends ProcPageQuery {
        @Serial private static final long serialVersionUID = 1L;
        /** 询价编号或标题关键词。 */ @Size(max = 100) private String keyword;
        /** 来源请购申请 ID。 */ @Positive private Long requisitionId;
        /** 询价状态。 */
        @Pattern(regexp = "DRAFT|SENT|CLOSED|AWARDED|CANCELLED")
        private String status;
        /** 截止时间起点。 */
        @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime deadlineFrom;
        /** 截止时间终点。 */
        @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime deadlineTo;
    }

    /** 合格供应商选项查询。 */
    @Data
    public static class SupplierOptionQuery implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 供应商编号或名称关键词。 */ @Size(max = 100) private String keyword;
        /** 供应品类编码。 */ @Size(max = 64) private String categoryCode;
        /** 返回数量上限。 */ @Min(1) @Max(100) private int limit = 50;
    }

    /** 创建询价。 */
    @Data
    public static class CreateRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 已审批请购申请 ID。 */ @NotNull @Positive private Long requisitionId;
        /** 询价标题。 */ @NotBlank @Size(max = 200) private String title;
        /** 报价截止时间。 */ @NotNull @Future private LocalDateTime quotationDeadline;
        /** 受邀供应商 ID。 */
        @NotEmpty @Size(max = 100)
        private List<@NotNull @Positive Long> supplierIds;
    }

    /** 更新草稿询价。 */
    @Data
    public static class UpdateRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 乐观锁版本。 */ @NotNull @Min(0) private Integer version;
        /** 询价标题。 */ @NotBlank @Size(max = 200) private String title;
        /** 报价截止时间。 */ @NotNull @Future private LocalDateTime quotationDeadline;
        /** 受邀供应商 ID。 */
        @NotEmpty @Size(max = 100)
        private List<@NotNull @Positive Long> supplierIds;
    }

    /** 带乐观锁版本的询价状态命令。 */
    @Data
    public static class VersionCommand implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 乐观锁版本。 */ @NotNull @Min(0) private Integer version;
    }

    /** 询价定点并生成采购订单命令。 */
    @Data
    public static class AwardRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** RFQ 乐观锁版本。 */ @NotNull @Min(0) private Integer rfqVersion;
        /** 选中的 SRM 报价 ID。 */ @NotNull @Positive private Long quotationId;
        /** 用户比价时看到的 SRM 报价版本。 */ @NotNull @Positive private Integer quotationVersion;
        /** 采购订单标题。 */ @NotBlank @Size(max = 200) private String title;
        /** 预计交付日期；为空时按报价最长交付天数计算。 */ private LocalDate expectedDeliveryDate;
        /** 收货地址。 */ @NotBlank @Size(max = 500) private String deliveryAddress;
        /** 收货联系人。 */ @NotBlank @Size(max = 100) private String contactName;
        /** 收货联系电话。 */ @NotBlank @Size(max = 50) private String contactPhone;
    }
}
