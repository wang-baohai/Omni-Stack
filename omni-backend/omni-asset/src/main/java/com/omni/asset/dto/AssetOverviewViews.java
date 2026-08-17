package com.omni.asset.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 资产概览响应视图集合。
 *
 * @author Omni-Stack Team
 */
public final class AssetOverviewViews {

    private AssetOverviewViews() {
    }

    /** 资产摘要。 */
    @Data
    public static class Summary implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 资产总数。 */ private Long totalCount;
        /** 在库数量。 */ private Long inStockCount;
        /** 待领用数量。 */ private Long allocatedCount;
        /** 使用中数量。 */ private Long inUseCount;
        /** 维修中数量。 */ private Long maintenanceCount;
        /** 调拨中数量。 */ private Long transferCount;
        /** 待处置数量。 */ private Long disposalPendingCount;
        /** 已丢弃或报废数量。 */ private Long terminalCount;
        /** 按币种独立统计的资产原值。 */ private List<CurrencyAmount> amountsByCurrency;
    }

    /** 状态计数行。 */
    @Data
    public static class StatusCount implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 生命周期状态。 */ private String status;
        /** 资产数量。 */ private Long count;
    }

    /** 币种金额。 */
    @Data
    public static class CurrencyAmount implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** ISO 4217 币种。 */ private String currencyCode;
        /** 资产原值。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(
                using = tools.jackson.databind.ser.std.ToStringSerializer.class)
        private BigDecimal amount;
    }

    /** 分布聚合行。 */
    @Data
    public static class DistributionItem implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 聚合维度。 */ private AssetOverviewRequests.DistributionDimension dimension;
        /** 维度键。 */ private String dimensionKey;
        /** 维度显示名，MVP 使用稳定编码或 ID 文本。 */ private String dimensionName;
        /** 状态维度的状态编码。 */ private String status;
        /** 币种；无原值的资产可能为空。 */ private String currencyCode;
        /** 资产数量。 */ private Long count;
        /** 资产原值。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(
                using = tools.jackson.databind.ser.std.ToStringSerializer.class)
        private BigDecimal amount;
    }
}
