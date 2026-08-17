package com.omni.procurement.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 物料目录响应 DTO 集合。
 *
 * @author Omni-Stack Team
 */
public final class MaterialViews {

    private MaterialViews() {
    }

    /** 物料品类树节点。 */
    @Data
    public static class CategoryVO implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 主键 ID。 */ private Long id;
        /** 父品类 ID。 */ private Long parentId;
        /** 品类编码。 */ private String categoryCode;
        /** 品类名称。 */ private String categoryName;
        /** 排序值。 */ private Integer sort;
        /** 启用状态。 */ private Integer status;
        /** 乐观锁版本。 */ private Integer version;
        /** 子品类。 */ private List<CategoryVO> children = new ArrayList<>();
    }

    /** 物料详情。 */
    @Data
    public static class MaterialVO implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 主键 ID。 */ private Long id;
        /** 品类 ID。 */ private Long categoryId;
        /** 品类编码。 */ private String categoryCode;
        /** 品类名称。 */ private String categoryName;
        /** 物料编码。 */ private String materialCode;
        /** 物料名称。 */ private String materialName;
        /** 文本规格。 */ private String specification;
        /** 计量单位。 */ private String unit;
        /** 是否纳入资产管理。 */ private Boolean assetManaged;
        /** ACTIVE/INACTIVE。 */ private String status;
        /** 乐观锁版本。 */ private Integer version;
        /** 创建时间。 */ private LocalDateTime createTime;
        /** 更新时间。 */ private LocalDateTime updateTime;
    }
}
