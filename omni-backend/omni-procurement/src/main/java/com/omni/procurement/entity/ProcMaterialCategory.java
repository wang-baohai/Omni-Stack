package com.omni.procurement.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 采购物料品类。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_material_category")
public class ProcMaterialCategory extends ProcTenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 父品类 ID，0 表示顶级品类。 */
    private Long parentId;

    /** 租户内稳定品类编码。 */
    private String categoryCode;

    /** 品类名称。 */
    private String categoryName;

    /** 排序值。 */
    private Integer sort;

    /** 启用状态，1 启用、0 停用。 */
    private Integer status;

    /** 乐观锁版本。 */
    @Version
    private Integer version;

    /** 逻辑删除标记。 */
    @TableLogic
    private Integer deleted;
}
