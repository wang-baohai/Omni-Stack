package com.omni.asset.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.omni.common.core.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 资产调拨申请。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ast_transfer")
public class AstTransfer extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户 ID。 */
    private Long tenantId;
    /** 调拨单号。 */
    private String transferNo;
    /** 资产 ID。 */
    private Long assetId;
    /** 原使用人 ID。 */
    private Long fromUserId;
    /** 原使用部门 ID。 */
    private Long fromUnitId;
    /** 目标使用人 ID。 */
    private Long toUserId;
    /** 目标使用部门 ID。 */
    private Long toUnitId;
    /** 原位置。 */
    private String fromLocation;
    /** 目标位置。 */
    private String toLocation;
    /** 调拨原因。 */
    private String reason;
    /** 申请状态。 */
    private String status;
    /** 发起前资产状态。 */
    private String previousAssetStatus;
    /** 是否为活动申请。 */
    private Integer activeFlag;
    /** Workflow 模型版本 ID。 */
    private Long modelVersionId;
    /** Workflow 请求幂等 ID。 */
    private String workflowRequestId;
    /** Workflow 业务幂等键。 */
    private String workflowBusinessKey;
    /** Workflow 原始发起人用户 ID。 */
    private Long workflowStartUserId;
    /** Workflow 原始发起人用户名快照。 */
    private String workflowStartUserName;
    /** PENDING/STARTED/FAILED。 */
    private String workflowStartStatus;
    /** 流程实例 ID。 */
    private String processInstanceId;
    /** 审批通过时间。 */
    private LocalDateTime approvedTime;
    /** 业务完成时间。 */
    private LocalDateTime completedTime;
    /** 乐观锁版本。 */
    @Version
    private Integer version;
    /** 逻辑删除标记。 */
    @TableLogic
    private Integer deleted;
}
