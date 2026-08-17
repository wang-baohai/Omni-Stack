package com.omni.asset.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 资产调拨与处置响应 DTO 集合。
 *
 * @author Omni-Stack Team
 */
public final class AssetOperationViews {

    private AssetOperationViews() {
    }

    /** 调拨列表与详情视图。 */
    @Data
    public static class TransferVO implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 申请 ID。 */ private Long id;
        /** 调拨单号。 */ private String transferNo;
        /** 资产 ID。 */ private Long assetId;
        /** 资产编号。 */ private String assetNo;
        /** 资产名称。 */ private String assetName;
        /** 原使用人 ID。 */ private Long fromUserId;
        /** 原使用部门 ID。 */ private Long fromUnitId;
        /** 目标使用人 ID。 */ private Long toUserId;
        /** 目标使用部门 ID。 */ private Long toUnitId;
        /** 原位置。 */ private String fromLocation;
        /** 目标位置。 */ private String toLocation;
        /** 原因。 */ private String reason;
        /** 申请状态。 */ private String status;
        /** 发起前资产状态。 */ private String previousAssetStatus;
        /** Workflow 启动状态。 */ private String workflowStartStatus;
        /** 流程实例 ID。 */ private String processInstanceId;
        /** 审批通过时间。 */ private LocalDateTime approvedTime;
        /** 业务完成时间。 */ private LocalDateTime completedTime;
        /** 乐观锁版本。 */ private Integer version;
        /** 创建时间。 */ private LocalDateTime createTime;
    }

    /** 处置列表与详情视图。 */
    @Data
    public static class DisposalVO implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 申请 ID。 */ private Long id;
        /** 处置单号。 */ private String disposalNo;
        /** 资产 ID。 */ private Long assetId;
        /** 资产编号。 */ private String assetNo;
        /** 资产名称。 */ private String assetName;
        /** DISCARD/SCRAP。 */ private String disposalType;
        /** 原因。 */ private String reason;
        /** 残值，JSON 中固定输出十进制字符串。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(
                using = tools.jackson.databind.ser.std.ToStringSerializer.class)
        private BigDecimal residualValue;
        /** 处置方式。 */ private String disposalMethod;
        /** 申请状态。 */ private String status;
        /** 发起前资产状态。 */ private String previousAssetStatus;
        /** Workflow 启动状态。 */ private String workflowStartStatus;
        /** 流程实例 ID。 */ private String processInstanceId;
        /** 审批通过时间。 */ private LocalDateTime approvedTime;
        /** 业务完成时间。 */ private LocalDateTime completedTime;
        /** 最终审批人 ID。 */ private Long finalApproverUserId;
        /** 最终审批意见。 */ private String finalApproverRemark;
        /** 乐观锁版本。 */ private Integer version;
        /** 创建时间。 */ private LocalDateTime createTime;
    }
}
