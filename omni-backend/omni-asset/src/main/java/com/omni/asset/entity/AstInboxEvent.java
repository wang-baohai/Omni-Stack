package com.omni.asset.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Asset 通用跨服务事件收件箱。
 *
 * @author Omni-Stack Team
 */
@Data
@TableName("ast_inbox_event")
public class AstInboxEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID。 */
    @TableId
    private Long id;

    /** 租户 ID。 */
    private Long tenantId;

    /** 消费者名称。 */
    private String consumerName;

    /** 全局事件 ID。 */
    private String eventId;

    /** 带版本的事件类型。 */
    private String eventType;

    /** 来源服务。 */
    private String sourceService;

    /** 聚合类型。 */
    private String aggregateType;

    /** 聚合业务 ID。 */
    private String aggregateId;

    /** 完整原始事件 JSON。 */
    private String payload;

    /** RECEIVED/PROCESSED/IGNORED/FAILED。 */
    private String status;

    /** 处理完成时间。 */
    private LocalDateTime processedTime;

    /** 最后处理错误。 */
    private String errorMessage;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
