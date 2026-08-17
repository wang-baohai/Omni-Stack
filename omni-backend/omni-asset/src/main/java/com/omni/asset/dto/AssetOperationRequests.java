package com.omni.asset.dto;

import jakarta.validation.constraints.DecimalMin;
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
 * 资产调拨与处置请求 DTO 集合。
 *
 * @author Omni-Stack Team
 */
public final class AssetOperationRequests {

    private AssetOperationRequests() {
    }

    /** 调拨列表查询。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TransferQuery extends AssetPageQuery {
        @Serial private static final long serialVersionUID = 1L;
        /** 单号、资产编号或资产名称关键词。 */ @Size(max = 100) private String keyword;
        /** 申请状态。 */
        @Pattern(regexp = "PENDING_APPROVAL|START_FAILED|APPROVED|REJECTED|COMPLETED|CANCELLED")
        private String status;
    }

    /** 处置列表查询。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class DisposalQuery extends AssetPageQuery {
        @Serial private static final long serialVersionUID = 1L;
        /** 单号、资产编号或资产名称关键词。 */ @Size(max = 100) private String keyword;
        /** 处置类型。 */ @Pattern(regexp = "DISCARD|SCRAP") private String disposalType;
        /** 申请状态。 */
        @Pattern(regexp = "PENDING_APPROVAL|START_FAILED|APPROVED|REJECTED|COMPLETED|CANCELLED")
        private String status;
    }

    /** 创建调拨申请。 */
    @Data
    public static class CreateTransferRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 资产 ID。 */ @NotNull @Positive private Long assetId;
        /** 目标使用人 ID。 */ @NotNull @Positive private Long toUserId;
        /** 目标使用部门 ID。 */ @NotNull @Positive private Long toUnitId;
        /** 目标位置。 */ @Size(max = 100) private String toLocation;
        /** 调拨原因。 */ @NotBlank @Size(max = 1000) private String reason;
        /** 服务端按业务分类解析的已发布 Workflow 模型版本 ID。 */
        @com.fasterxml.jackson.annotation.JsonProperty(
                access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
        private Long modelVersionId;
    }

    /** 创建处置申请。 */
    @Data
    public static class CreateDisposalRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 资产 ID。 */ @NotNull @Positive private Long assetId;
        /** DISCARD/SCRAP。 */ @NotNull @Pattern(regexp = "DISCARD|SCRAP") private String disposalType;
        /** 处置原因。 */ @NotBlank @Size(max = 1000) private String reason;
        /** 残值，只接受 JSON 十进制字符串。 */
        @DecimalMin("0.00") @Digits(integer = 16, fraction = 2)
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = Jackson2DecimalStringDeserializer.class)
        @tools.jackson.databind.annotation.JsonDeserialize(using = Jackson3DecimalStringDeserializer.class)
        private BigDecimal residualValue;
        /** 处置方式。 */ @Size(max = 500) private String disposalMethod;
        /** 服务端按业务分类解析的已发布 Workflow 模型版本 ID。 */
        @com.fasterxml.jackson.annotation.JsonProperty(
                access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
        private Long modelVersionId;
    }

    /** 携带乐观锁版本的操作命令。 */
    @Data
    public static class VersionCommand implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 乐观锁版本。 */ @NotNull @Min(0) private Integer version;
    }
}
