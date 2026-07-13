package com.omni.crm.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * CRM 领域事件统一信封。
 *
 * @author Omni-Stack Team
 */
@Data
@Builder
public class DomainEventEnvelope implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    /** 事件 ID */ private String eventId;
    /** 事件类型 */ private String eventType;
    /** 发生时间 */ private LocalDateTime occurredAt;
    /** 租户 ID */ private Long tenantId;
    /** 生产者 */ private String producer;
    /** 聚合类型 */ private String aggregateType;
    /** 聚合 ID */ private Long aggregateId;
    /** 聚合版本 */ private Integer aggregateVersion;
    /** 操作用户 ID */ private Long actorUserId;
    /** 关联 ID */ private String correlationId;
    /** 因果 ID */ private String causationId;
    /** 最小事件载荷 */ private Map<String, Object> payload;
}
