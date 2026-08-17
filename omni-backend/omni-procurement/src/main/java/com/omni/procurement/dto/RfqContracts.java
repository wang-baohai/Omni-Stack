package com.omni.procurement.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 询价跨服务事件契约。
 *
 * @author Omni-Stack Team
 */
public final class RfqContracts {

    private RfqContracts() {
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
        /** 不含 PII 的事件载荷。 */ private Map<String, Object> payload;
    }

    /** SRM 报价提交事件信封。 */
    @Data
    public static class QuotationSubmittedEvent implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 事件 ID。 */ private String eventId;
        /** 事件类型。 */ private String eventType;
        /** 发生时间。 */ private LocalDateTime occurredAt;
        /** 租户 ID。 */ private Long tenantId;
        /** 生产者。 */ private String producer;
        /** 聚合类型。 */ private String aggregateType;
        /** 报价聚合 ID。 */ private Long aggregateId;
        /** 报价聚合版本。 */ private Integer aggregateVersion;
        /** 操作用户 ID。 */ private Long actorUserId;
        /** 关联 ID。 */ private String correlationId;
        /** 因果事件 ID。 */ private String causationId;
        /** 报价业务载荷。 */ private QuotationSubmittedPayload payload;
    }

    /** SRM 报价提交事件业务载荷。 */
    @Data
    public static class QuotationSubmittedPayload implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 幂等提交请求 ID。 */ private String requestId;
        /** 报价 ID。 */ private Long quotationId;
        /** 报价版本。 */ private Integer quotationVersion;
        /** RFQ ID。 */ private Long rfqId;
        /** RFQ 编号。 */ private String rfqNo;
        /** 供应商 ID。 */ private Long supplierId;
        /** 报价状态。 */ private String status;
        /** 报价总额，跨服务事件仅接受十进制字符串。 */
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
                using = Jackson2DecimalStringDeserializer.class)
        @tools.jackson.databind.annotation.JsonDeserialize(
                using = Jackson3DecimalStringDeserializer.class)
        private BigDecimal totalAmount;
        /** 币种。 */ private String currencyCode;
        /** 报价有效期。 */ private LocalDateTime validUntil;
    }
}
