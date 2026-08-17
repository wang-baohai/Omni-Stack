package com.omni.procurement.dto;

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

/**
 * 物料目录请求 DTO 集合。
 *
 * @author Omni-Stack Team
 */
public final class MaterialRequests {

    private MaterialRequests() {
    }

    /** 物料分页查询。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class MaterialQuery extends ProcPageQuery {
        @Serial private static final long serialVersionUID = 1L;
        /** 编号或名称关键词。 */ @Size(max = 100) private String keyword;
        /** 品类 ID。 */ @Positive private Long categoryId;
        /** ACTIVE/INACTIVE。 */ @Pattern(regexp = "ACTIVE|INACTIVE") private String status;
        /** 是否纳入资产管理。 */ private Boolean assetManaged;
    }

    /** 创建物料品类。 */
    @Data
    public static class CreateCategoryRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 父品类 ID，0 表示顶级。 */ @NotNull @Min(0) private Long parentId;
        /** 稳定品类编码。 */
        @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]*")
        private String categoryCode;
        /** 品类名称。 */ @NotBlank @Size(max = 100) private String categoryName;
        /** 排序值。 */ @NotNull @Min(0) @Max(999999) private Integer sort = 0;
        /** 启用状态。 */ @NotNull @Min(0) @Max(1) private Integer status = 1;
    }

    /** 更新物料品类，品类编码不可修改。 */
    @Data
    public static class UpdateCategoryRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 乐观锁版本。 */ @NotNull @Min(0) private Integer version;
        /** 父品类 ID，0 表示顶级。 */ @NotNull @Min(0) private Long parentId;
        /** 品类名称。 */ @NotBlank @Size(max = 100) private String categoryName;
        /** 排序值。 */ @NotNull @Min(0) @Max(999999) private Integer sort;
        /** 启用状态。 */ @NotNull @Min(0) @Max(1) private Integer status;
    }

    /** 创建物料。 */
    @Data
    public static class CreateMaterialRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 品类 ID。 */ @NotNull @Positive private Long categoryId;
        /** 稳定物料编码。 */
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]*")
        private String materialCode;
        /** 物料名称。 */ @NotBlank @Size(max = 200) private String materialName;
        /** 文本规格。 */ @Size(max = 500) private String specification;
        /** 计量单位。 */ @NotBlank @Size(max = 20) private String unit;
        /** 是否纳入资产管理。 */ @NotNull private Boolean assetManaged;
        /** ACTIVE/INACTIVE。 */ @Pattern(regexp = "ACTIVE|INACTIVE") private String status = "ACTIVE";
    }

    /** 更新物料，物料编码不可修改。 */
    @Data
    public static class UpdateMaterialRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 乐观锁版本。 */ @NotNull @Min(0) private Integer version;
        /** 品类 ID。 */ @NotNull @Positive private Long categoryId;
        /** 物料名称。 */ @NotBlank @Size(max = 200) private String materialName;
        /** 文本规格。 */ @Size(max = 500) private String specification;
        /** 计量单位。 */ @NotBlank @Size(max = 20) private String unit;
        /** 是否纳入资产管理。 */ @NotNull private Boolean assetManaged;
        /** ACTIVE/INACTIVE。 */ @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") private String status;
    }
}
