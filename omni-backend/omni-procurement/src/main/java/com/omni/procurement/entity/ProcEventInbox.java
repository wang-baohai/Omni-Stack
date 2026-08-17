package com.omni.procurement.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 采购跨服务领域事件幂等收件箱。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc_event_inbox")
public class ProcEventInbox extends ProcTenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 全局事件 ID。 */
    private String eventId;

    /** 事件类型。 */
    private String eventType;

    /** 来源服务。 */
    private String sourceService;

    /** 聚合类型。 */
    private String aggregateType;

    /** 聚合业务 ID。 */
    private String aggregateId;

    /** 原始事件 JSON。 */
    private String payload;

    /** RECEIVED/PROCESSED/IGNORED/FAILED。 */
    private String status;

    /** 处理完成时间。 */
    private LocalDateTime processedTime;

    /** 最后处理错误。 */
    private String errorMessage;
}
