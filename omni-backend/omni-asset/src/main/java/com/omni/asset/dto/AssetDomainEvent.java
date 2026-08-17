package com.omni.asset.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 资产领域事件信封。
 *
 * @author Omni-Stack Team
 */
@Data
@Builder
public class AssetDomainEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件 ID。 */
    private String eventId;

    /** 事件类型。 */
    private String eventType;

    /** 事件产生时间。 */
    private LocalDateTime occurredAt;

    /** 租户 ID。 */
    private Long tenantId;

    /** 生产者服务名。 */
    private String producer;

    /** 不含 PII 的业务载荷。 */
    private Map<String, Object> payload;
}
