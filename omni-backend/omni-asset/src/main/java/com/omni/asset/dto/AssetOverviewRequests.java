package com.omni.asset.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 资产概览查询请求集合。
 *
 * @author Omni-Stack Team
 */
public final class AssetOverviewRequests {

    private AssetOverviewRequests() {
    }

    /** 资产分布维度。 */
    public enum DistributionDimension {
        /** 生命周期状态。 */ STATUS,
        /** 资产品类。 */ CATEGORY,
        /** 管理部门。 */ DEPARTMENT,
        /** 资产位置。 */ LOCATION
    }

    /** 分布查询参数。 */
    @Data
    public static class DistributionQuery implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 聚合维度。 */ @NotNull private DistributionDimension dimension;
        /** 最大返回行数。 */ @Min(1) @Max(100) private Integer limit = 20;
    }
}
