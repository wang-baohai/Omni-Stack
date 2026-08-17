package com.omni.procurement.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 带负责人快照、乐观锁和逻辑删除的采购授权实体。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class ProcOwnedEntity extends ProcTenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 负责人用户 ID。 */
    private Long ownerUserId;

    /** 负责人组织 ID。 */
    private Long ownerUnitId;

    /** 乐观锁版本。 */
    @Version
    private Integer version;

    /** 逻辑删除标记。 */
    @TableLogic
    private Integer deleted;
}
