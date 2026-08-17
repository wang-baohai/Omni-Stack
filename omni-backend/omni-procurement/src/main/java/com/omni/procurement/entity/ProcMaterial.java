package com.omni.procurement.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 采购物料目录实体。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_material")
public class ProcMaterial extends ProcTenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 所属品类 ID。 */
    private Long categoryId;

    /** 租户内稳定物料编码，创建后不可修改。 */
    private String materialCode;

    /** 物料名称。 */
    private String materialName;

    /** 文本规格。 */
    private String specification;

    /** 计量单位。 */
    private String unit;

    /** 是否按离散单位纳入资产管理。 */
    private Boolean assetManaged;

    /** ACTIVE/INACTIVE。 */
    private String status;

    /** 乐观锁版本。 */
    @Version
    private Integer version;

    /** 逻辑删除标记。 */
    @TableLogic
    private Integer deleted;
}
