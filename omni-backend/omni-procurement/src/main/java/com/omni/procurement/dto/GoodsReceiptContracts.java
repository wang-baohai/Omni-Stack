package com.omni.procurement.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 收货聚合内部查询与领域事件契约。
 *
 * @author Omni-Stack Team
 */
public final class GoodsReceiptContracts {

    private GoodsReceiptContracts() {
    }

    /** 已确认累计收货数量行。 */
    @Data
    public static class ReceivedTotal implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 采购订单行 ID。 */ private Long poLineId;
        /** 已确认累计数量。 */ private BigDecimal totalQuantity;
    }

    /** Procurement 领域事件信封。 */
    @Data
    @Builder
    public static class DomainEvent implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 事件 ID。 */ private String eventId;
        /** 事件类型。 */ private String eventType;
        /** 发生时间。 */ private LocalDateTime occurredAt;
        /** 租户 ID。 */ private Long tenantId;
        /** 不含 PII 的业务载荷。 */ private Map<String, Object> payload;
    }
}
