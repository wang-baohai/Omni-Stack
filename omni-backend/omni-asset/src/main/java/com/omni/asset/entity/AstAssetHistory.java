package com.omni.asset.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.omni.common.core.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 资产不可变变更历史。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ast_asset_history")
public class AstAssetHistory extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户 ID。 */
    private Long tenantId;

    /** 资产 ID。 */
    private Long assetId;

    /** 变更前状态，首次入库时为 null。 */
    private String fromStatus;

    /** 变更后状态。 */
    private String toStatus;

    /** 执行变更的用户 ID，系统任务使用 0。 */
    private Long changedByUserId;

    /** 变更时间。 */
    private LocalDateTime changedTime;

    /** 纯文本变更说明。 */
    private String remark;
}
