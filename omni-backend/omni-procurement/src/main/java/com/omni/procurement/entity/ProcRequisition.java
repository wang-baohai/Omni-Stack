package com.omni.procurement.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 请购申请聚合根。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_requisition")
public class ProcRequisition extends ProcOwnedEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户内请购单号。 */
    private String requisitionNo;

    /** 请购标题。 */
    private String title;

    /** 申请人用户 ID。 */
    private Long requesterUserId;

    /** 申请人主组织 ID 快照。 */
    private Long requesterUnitId;

    /** 请购原因。 */
    private String reason;

    /** 当前明细的唯一品类编码。 */
    private String primaryCategoryCode;

    /** 服务端重算的总金额。 */
    private BigDecimal totalAmount;

    /** 租户默认币种。 */
    private String currencyCode;

    /** DRAFT/SUBMITTED/APPROVING/APPROVED/REJECTED/CANCELLED。 */
    private String status;

    /** 当前审批轮次。 */
    private Integer approvalAttempt;

    /** Workflow 启动请求幂等键。 */
    private String workflowRequestId;

    /** Workflow 业务键，格式为 requisitionId:approvalAttempt。 */
    private String workflowBusinessKey;

    /** 本轮选定的已发布流程模型版本 ID。 */
    private Long workflowModelVersionId;

    /** Workflow 流程实例 ID。 */
    private String processInstanceId;

    /** NOT_STARTED/PENDING/FAILED/STARTED。 */
    private String workflowStartStatus;

    /** 审批通过时间。 */
    private LocalDateTime approvedTime;

    /** Workflow 审批完成时间。 */
    private LocalDateTime workflowCompletedTime;
}
