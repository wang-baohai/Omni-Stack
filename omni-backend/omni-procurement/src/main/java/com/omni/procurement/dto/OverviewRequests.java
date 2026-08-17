package com.omni.procurement.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 采购概览请求 DTO 集合。
 *
 * @author Omni-Stack Team
 */
public final class OverviewRequests {

    private OverviewRequests() {
    }

    /** 支出分析维度。 */
    public enum SpendDimension {
        /** 物料品类。 */
        CATEGORY,
        /** 供应商。 */
        SUPPLIER,
        /** 采购订单负责部门。 */
        DEPARTMENT
    }

    /** 支出分析查询条件。 */
    @Data
    public static class SpendAnalysisQuery implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 聚合维度。 */
        @NotNull(message = "支出分析维度不能为空")
        private SpendDimension dimension;

        /** 最大返回条数。 */
        @Min(value = 1, message = "返回条数不能小于 1")
        @Max(value = 100, message = "返回条数不能超过 100")
        private Integer limit = 20;
    }
}
