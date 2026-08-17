package com.omni.procurement.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 采购收货单聚合根。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_goods_receipt")
public class ProcGoodsReceipt extends ProcOwnedEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户内收货单编号。 */
    private String grNo;

    /** 采购订单 ID。 */
    private Long poId;

    /** 收货人用户 ID。 */
    private Long receiverUserId;

    /** 业务收货时间。 */
    private LocalDateTime receiveTime;

    /** 收货备注。 */
    private String remark;

    /** DRAFT/CONFIRMED。 */
    private String status;

    /** 确认时间。 */
    private LocalDateTime confirmedTime;

    /** 确认 Outbox 事件 ID。 */
    private String confirmedEventId;
}
