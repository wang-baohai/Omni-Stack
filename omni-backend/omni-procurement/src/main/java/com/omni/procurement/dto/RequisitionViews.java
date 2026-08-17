package com.omni.procurement.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 请购申请响应 DTO 集合。
 *
 * @author Omni-Stack Team
 */
public final class RequisitionViews {

    private RequisitionViews() {
    }

    /** 请购列表摘要。 */
    @Data
    public static class Summary implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 主键。 */ private Long id;
        /** 请购单号。 */ private String requisitionNo;
        /** 标题。 */ private String title;
        /** 申请人用户 ID。 */ private Long requesterUserId;
        /** 申请人组织 ID。 */ private Long requesterUnitId;
        /** 唯一品类编码。 */ private String primaryCategoryCode;
        /** 总金额。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(
                using = tools.jackson.databind.ser.std.ToStringSerializer.class)
        private BigDecimal totalAmount;
        /** 币种。 */ private String currencyCode;
        /** 状态。 */ private String status;
        /** Workflow 启动状态。 */ private String workflowStartStatus;
        /** 审批轮次。 */ private Integer approvalAttempt;
        /** 乐观锁版本。 */ private Integer version;
        /** 创建时间。 */ private LocalDateTime createTime;
        /** 更新时间。 */ private LocalDateTime updateTime;
    }

    /** 请购详情。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Detail extends Summary {
        @Serial private static final long serialVersionUID = 1L;
        /** 请购原因。 */ private String reason;
        /** Workflow 业务键。 */ private String workflowBusinessKey;
        /** Workflow 模型版本 ID。 */ private Long workflowModelVersionId;
        /** 流程实例 ID。 */ private String processInstanceId;
        /** 审批通过时间。 */ private LocalDateTime approvedTime;
        /** Workflow 完成时间。 */ private LocalDateTime workflowCompletedTime;
        /** 明细行。 */ private List<Line> lines;
    }

    /** 请购明细响应。 */
    @Data
    public static class Line implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 主键。 */ private Long id;
        /** 行号。 */ private Integer lineNo;
        /** 物料 ID。 */ private Long materialId;
        /** 物料编码快照。 */ private String materialCode;
        /** 物料名称快照。 */ private String materialName;
        /** 品类编码快照。 */ private String categoryCode;
        /** 计量单位快照。 */ private String unit;
        /** 数量。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(
                using = tools.jackson.databind.ser.std.ToStringSerializer.class)
        private BigDecimal quantity;
        /** 预估单价。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(
                using = tools.jackson.databind.ser.std.ToStringSerializer.class)
        private BigDecimal estimatedUnitPrice;
        /** 预估行金额。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @tools.jackson.databind.annotation.JsonSerialize(
                using = tools.jackson.databind.ser.std.ToStringSerializer.class)
        private BigDecimal estimatedTotalPrice;
        /** 行备注。 */ private String remark;
        /** 乐观锁版本。 */ private Integer version;
    }

    /** 审批任务专用只读业务视图。 */
    @Data
    public static class ApprovalView implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** Workflow 任务 ID。 */ private String taskId;
        /** 请购详情。 */ private Detail requisition;
    }
}
