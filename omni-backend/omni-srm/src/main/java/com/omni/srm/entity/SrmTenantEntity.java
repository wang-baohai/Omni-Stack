package com.omni.srm.entity;

import com.omni.common.core.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * SRM 租户实体公共字段。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class SrmTenantEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long tenantId;
}
