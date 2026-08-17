package com.omni.asset.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
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
import java.time.LocalDate;

/**
 * 资产台账请求 DTO 集合。
 *
 * @author Omni-Stack Team
 */
public final class AssetRequests {

    private AssetRequests() {
    }

    /** 资产管理列表查询。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class AssetQuery extends AssetPageQuery {
        @Serial private static final long serialVersionUID = 1L;
        /** 资产编号、名称、品牌或型号关键词。 */ @Size(max = 100) private String keyword;
        /** 生命周期状态。 */
        @Pattern(regexp = "IN_STOCK|ALLOCATED|IN_USE|MAINTENANCE|TRANSFER|DISPOSAL_PENDING|DISPOSED|SCRAPPED")
        private String status;
        /** 品类编码。 */ @Size(max = 50) private String categoryCode;
        /** 管理部门 ID。 */ @Positive private Long ownerUnitId;
        /** 位置编码。 */ @Size(max = 100) private String locationCode;
    }

    /** 我的资产查询。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class MyAssetQuery extends AssetPageQuery {
        @Serial private static final long serialVersionUID = 1L;
        /** 资产编号或名称关键词。 */ @Size(max = 100) private String keyword;
        /** 生命周期状态。 */
        @Pattern(regexp = "ALLOCATED|IN_USE|MAINTENANCE|TRANSFER|DISPOSAL_PENDING")
        private String status;
        /** 品类编码。 */ @Size(max = 50) private String categoryCode;
    }

    /** 资产历史分页查询。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class HistoryQuery extends AssetPageQuery {
        @Serial private static final long serialVersionUID = 1L;
    }

    /** 手工入库请求。 */
    @Data
    public static class CreateAssetRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 资产名称。 */ @NotBlank @Size(max = 200) private String name;
        /** 品类编码。 */ @NotBlank @Size(max = 64) private String categoryCode;
        /** 规格。 */ @Size(max = 500) private String specification;
        /** 品牌。 */ @Size(max = 100) private String brand;
        /** 型号。 */ @Size(max = 100) private String model;
        /** 供应商 ID。 */ @Positive private Long supplierId;
        /** 供应商名称快照。 */ @Size(max = 200) private String supplierNameSnapshot;
        /** 采购日期。 */ private LocalDate purchaseDate;
        /** 采购原值，只接受 JSON 十进制字符串。 */
        @DecimalMin("0.00") @Digits(integer = 16, fraction = 2)
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = Jackson2DecimalStringDeserializer.class)
        @tools.jackson.databind.annotation.JsonDeserialize(using = Jackson3DecimalStringDeserializer.class)
        private BigDecimal purchaseAmount;
        /** ISO 4217 币种编码。 */
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") private String currencyCode;
        /** 位置编码。 */ @Size(max = 100) private String locationCode;
        /** 保修到期日。 */ private LocalDate warrantyExpiryDate;
        /** 预期使用年限。 */ @Min(1) @Max(200) private Integer expectedLifeYears;
        /** 纯文本备注。 */ @Size(max = 1000) private String remark;
        /** 资产管理员用户 ID。 */ @NotNull @Positive private Long ownerUserId;
        /** 资产管理部门 ID。 */ @NotNull @Positive private Long ownerUnitId;
    }

    /** 更新资产基础资料请求，不允许直接改变状态、使用人或位置。 */
    @Data
    public static class UpdateAssetRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 乐观锁版本。 */ @NotNull @Min(0) private Integer version;
        /** 资产名称。 */ @NotBlank @Size(max = 200) private String name;
        /** 品类编码。 */ @NotBlank @Size(max = 64) private String categoryCode;
        /** 规格。 */ @Size(max = 500) private String specification;
        /** 品牌。 */ @Size(max = 100) private String brand;
        /** 型号。 */ @Size(max = 100) private String model;
        /** 供应商 ID。 */ @Positive private Long supplierId;
        /** 供应商名称快照。 */ @Size(max = 200) private String supplierNameSnapshot;
        /** 采购日期。 */ private LocalDate purchaseDate;
        /** 采购原值，只接受 JSON 十进制字符串。 */
        @DecimalMin("0.00") @Digits(integer = 16, fraction = 2)
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = Jackson2DecimalStringDeserializer.class)
        @tools.jackson.databind.annotation.JsonDeserialize(using = Jackson3DecimalStringDeserializer.class)
        private BigDecimal purchaseAmount;
        /** ISO 4217 币种编码。 */
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") private String currencyCode;
        /** 保修到期日。 */ private LocalDate warrantyExpiryDate;
        /** 预期使用年限。 */ @Min(1) @Max(200) private Integer expectedLifeYears;
        /** 纯文本备注。 */ @Size(max = 1000) private String remark;
        /** 资产管理员用户 ID。 */ @NotNull @Positive private Long ownerUserId;
        /** 资产管理部门 ID。 */ @NotNull @Positive private Long ownerUnitId;
    }

    /** 分配资产请求。 */
    @Data
    public static class AllocateRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 乐观锁版本。 */ @NotNull @Min(0) private Integer version;
        /** 目标使用人 ID。 */ @NotNull @Positive private Long targetUserId;
        /** 目标使用部门 ID。 */ @NotNull @Positive private Long targetUnitId;
        /** 纯文本说明。 */ @Size(max = 500) private String remark;
    }

    /** 仅携带版本和说明的资产命令。 */
    @Data
    public static class VersionCommandRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 乐观锁版本。 */ @NotNull @Min(0) private Integer version;
        /** 纯文本说明。 */ @Size(max = 500) private String remark;
    }
}
